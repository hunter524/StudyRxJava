/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rx.internal.operators;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

import rx.*;
import rx.Observable.Operator;
import rx.exceptions.MissingBackpressureException;
import rx.functions.Action0;
import rx.internal.schedulers.*;
import rx.internal.util.RxRingBuffer;
import rx.internal.util.atomic.SpscAtomicArrayQueue;
import rx.internal.util.unsafe.*;
import rx.plugins.RxJavaHooks;
import rx.schedulers.Schedulers;

/**
 * Delivers events on the specified {@code Scheduler} asynchronously via an unbounded buffer.
 *
 * <img width="640" src="https://github.com/ReactiveX/RxJava/wiki/images/rx-operators/observeOn.png" alt="">
 *
 * @param <T>
 *            the transmitted value type
 */
//observeOn onXXX（Error Completed Next）三个方法是在观察者线程调用
// TODO: 18-1-3 涉及线程安全问题元素的合理发布
public final class OperatorObserveOn<T> implements Operator<T, T> {

    private final Scheduler scheduler;
    private final boolean delayError;
    private final int bufferSize;

    /**
     * @param scheduler the scheduler to use
     * @param delayError delay errors until all normal events are emitted in the other thread?
     */
    public OperatorObserveOn(Scheduler scheduler, boolean delayError) {
        this(scheduler, delayError, RxRingBuffer.SIZE);
    }

    /**
     * @param scheduler the scheduler to use
     * @param delayError delay errors until all normal events are emitted in the other thread?
     * @param bufferSize for the buffer feeding the Scheduler workers, defaults to {@code RxRingBuffer.MAX} if <= 0
     */
    public OperatorObserveOn(Scheduler scheduler, boolean delayError, int bufferSize) {
        this.scheduler = scheduler;
        this.delayError = delayError;
        this.bufferSize = (bufferSize > 0) ? bufferSize : RxRingBuffer.SIZE;
    }

    @Override
    public Subscriber<? super T> call(Subscriber<? super T> child) {
        if (scheduler instanceof ImmediateScheduler) {
            // avoid overhead, execute directly
            return child;
        } else if (scheduler instanceof TrampolineScheduler) {
            // avoid overhead, execute directly
            return child;
        } else {
            ObserveOnSubscriber<T> parent = new ObserveOnSubscriber<T>(scheduler, child, delayError, bufferSize);
            parent.init();
            return parent;/*在Lift内部这个是返回给{OnSubscribeLift}
            同时返回给上游的OnSubscribe#call方法
            call向下发送事件时则优先发送给parent，parent 则进行线程切换
            */
        }
    }

    public static <T> Operator<T, T> rebatch(final int n) {
        return new Operator<T, T>() {
            @Override
            public Subscriber<? super T> call(Subscriber<? super T> child) {
                ObserveOnSubscriber<T> parent = new ObserveOnSubscriber<T>(Schedulers.immediate(), child, false, n);
                parent.init();
                return parent;
            }
        };
    }
//onXXX方法在指定的线程调用
    /** Observe through individual queue per observer. */
    static final class ObserveOnSubscriber<T> extends Subscriber<T> implements Action0 {
        final Subscriber<? super T> child;
        final Scheduler.Worker recursiveScheduler;/*对应传入的Scheduler的的Worker*/
        final boolean delayError;
        final Queue<Object> queue;/*队列的长度是缓存的大小 如128*/
        /** The emission threshold that should trigger a replenishing request. */
        final int limit;/*消耗掉当前元素的3/4时，则再向生产者请求3/4的元素*/

        // the status of the current stream
        volatile boolean finished;

        final AtomicLong requested = new AtomicLong();

        final AtomicLong counter = new AtomicLong();

        /**
         * The single exception if not null, should be written before setting finished (release) and read after
         * reading finished (acquire).
         */
        Throwable error;

        /** Remembers how many elements have been emitted before the requests run out. */
        long emitted;

        // do NOT pass the Subscriber through to couple the subscription chain ... unsubscribing on the parent should
        // not prevent anything downstream from consuming, which will happen if the Subscription is chained
        public ObserveOnSubscriber(Scheduler scheduler, Subscriber<? super T> child, boolean delayError, int bufferSize) {
            this.child = child;
            this.recursiveScheduler = scheduler.createWorker();
            this.delayError = delayError;
            int calculatedSize = (bufferSize > 0) ? bufferSize : RxRingBuffer.SIZE;
            // this formula calculates the 75% of the bufferSize, rounded up to the next integer
            this.limit = calculatedSize - (calculatedSize >> 2);
            if (UnsafeAccess.isUnsafeAvailable()) {
                queue = new SpscArrayQueue<Object>(calculatedSize);
            } else {
                queue = new SpscAtomicArrayQueue<Object>(calculatedSize);
            }
            // signal that this is an async operator capable of receiving this many
            request(calculatedSize);
        }
// 此处 会根据下面的Subscriber设置 必须在调用 super.setProducer(p); 方法之前设置request的元素
        void init() {
            // don't want this code in the constructor because `this` can escape through the
            // setProducer call
            Subscriber<? super T> localChild = child;

            localChild.setProducer(new Producer() {

                @Override
                public void request(long n) {
                    if (n > 0L) {
                        BackpressureUtils.getAndAddRequest(requested, n);
                        schedule();
                    }
                }

            });
            localChild.add(recursiveScheduler);/*把当前的调用关系加入下面的Subscriber*/
            localChild.add(this);
        }
//现将元素执行入队操作 然后调度向下切换到指定 线程进行发射
        @Override
        public void onNext(final T t) {
            if (isUnsubscribed() || finished) {
                return;
            }
            if (!queue.offer(NotificationLite.next(t))) {
                onError(new MissingBackpressureException());
                return;
            }
            schedule();
        }

        @Override
        public void onCompleted() {
            if (isUnsubscribed() || finished) {
                return;
            }
            finished = true;
            schedule();
        }

        @Override
        public void onError(final Throwable e) {
            if (isUnsubscribed() || finished) {
                RxJavaHooks.onError(e);
                return;
            }
            error = e;
            finished = true;
            schedule();
        }
//切换到指定线程进行数据的发送
        protected void schedule() {
            if (counter.getAndIncrement() == 0) {
                recursiveScheduler.schedule(this);
            }
        }

        // only execute this from schedule()
        @Override
        public void call() {
            long missed = 1L;
            long currentEmission = emitted;

            // these are accessed in a tight loop around atomics so
            // loading them into local variables avoids the mandatory re-reading
            // of the constant fields
            final Queue<Object> q = this.queue;
            final Subscriber<? super T> localChild = this.child;

            // requested and counter are not included to avoid JIT issues with register spilling
            // and their access is is amortized because they are part of the outer loop which runs
            // less frequently (usually after each bufferSize elements)

            for (;;) {
                long requestAmount = requested.get();

                while (requestAmount != currentEmission) {
                    boolean done = finished;
                    Object v = q.poll();
                    boolean empty = v == null;

                    if (checkTerminated(done, empty, localChild, q)) {
                        return;
                    }

                    if (empty) {
                        break;
                    }

                    localChild.onNext(NotificationLite.<T>getValue(v));

                    currentEmission++;
//                    当已经发射的元素 等于当前计算得到的limit时则会再去请求元素 请求128 - 128/4 个元素
                    if (currentEmission == limit) {
                        requestAmount = BackpressureUtils.produced(requested, currentEmission);
                        request(currentEmission);
                        currentEmission = 0L;
                    }
                }

                if (requestAmount == currentEmission) {
                    if (checkTerminated(finished, q.isEmpty(), localChild, q)) {
                        return;
                    }
                }

                emitted = currentEmission;
                missed = counter.addAndGet(-missed);
                if (missed == 0L) {
                    break;
                }
            }
        }

        boolean checkTerminated(boolean done, boolean isEmpty, Subscriber<? super T> a, Queue<Object> q) {
            if (a.isUnsubscribed()) {
                q.clear();
                return true;
            }

            if (done) {
                if (delayError) {
                    if (isEmpty) {
                        Throwable e = error;
                        try {
                            if (e != null) {
                                a.onError(e);
                            } else {
                                a.onCompleted();
                            }
                        } finally {
                            recursiveScheduler.unsubscribe();
                        }
                    }
                } else {
                    Throwable e = error;
                    if (e != null) {
                        q.clear();
                        try {
                            a.onError(e);
                        } finally {
                            recursiveScheduler.unsubscribe();
                        }
                        return true;
                    } else
                    if (isEmpty) {
                        try {
                            a.onCompleted();
                        } finally {
                            recursiveScheduler.unsubscribe();
                        }
                        return true;
                    }
                }

            }

            return false;
        }
    }
}