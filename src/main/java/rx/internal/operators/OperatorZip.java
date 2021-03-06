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

import java.util.concurrent.atomic.AtomicLong;

import rx.*;
import rx.Observable.Operator;
import rx.exceptions.*;
import rx.functions.*;
import rx.internal.util.RxRingBuffer;
import rx.subscriptions.CompositeSubscription;

/**
 * Returns an Observable that emits the results of a function applied to sets of items emitted, in
 * sequence, by two or more other Observables.
 * <p>
 * <img width="640" src="https://github.com/ReactiveX/RxJava/wiki/images/rx-operators/zip.png" alt="">
 * <p>
 * The zip operation applies this function in strict sequence, so the first item emitted by the new
 * Observable will be the result of the function applied to the first item emitted by each zipped
 * Observable; the second item emitted by the new Observable will be the result of the function
 * applied to the second item emitted by each zipped Observable; and so forth.
 * <p>
 * The resulting Observable returned from zip will invoke <code>onNext</code> as many times as the
 * number of <code>onNext</code> invocations of the source Observable that emits the fewest items.
 *
 * @param <R>
 *            the result type
 */

//zip操作符的ZipFunction在tick操作时执行，tick是多个等待zip的Observable被多个InnerSubscriber订阅时均会执行的操作
//要确保zipFunction在同一个线程执行，则需要确保多个待合并的Observable其订阅者在同一个线程
public final class OperatorZip<R> implements Operator<R, Observable<?>[]> {
    /*
     * Raw types are used so we can use a single implementation for all arities such as zip(t1, t2) and zip(t1, t2, t3) etc.
     * The types will be cast on the edges so usage will be the type-safe but the internals are not.
     */

    final FuncN<? extends R> zipFunction;

    public OperatorZip(FuncN<? extends R> f) {
        this.zipFunction = f;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public OperatorZip(Func2 f) {
        this.zipFunction = Functions.fromFunc(f);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public OperatorZip(Func3 f) {
        this.zipFunction = Functions.fromFunc(f);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public OperatorZip(Func4 f) {
        this.zipFunction = Functions.fromFunc(f);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public OperatorZip(Func5 f) {
        this.zipFunction = Functions.fromFunc(f);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public OperatorZip(Func6 f) {
        this.zipFunction = Functions.fromFunc(f);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public OperatorZip(Func7 f) {
        this.zipFunction = Functions.fromFunc(f);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public OperatorZip(Func8 f) {
        this.zipFunction = Functions.fromFunc(f);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public OperatorZip(Func9 f) {
        this.zipFunction = Functions.fromFunc(f);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Subscriber<? super Observable[]> call(final Subscriber<? super R> child) {
        final Zip<R> zipper = new Zip<R>(child, zipFunction);
        final ZipProducer<R> producer = new ZipProducer<R>(zipper);
        final ZipSubscriber subscriber = new ZipSubscriber(child, zipper, producer);

        child.add(subscriber);
        child.setProducer(producer);

        return subscriber;
    }

    @SuppressWarnings("rawtypes")
    final class ZipSubscriber extends Subscriber<Observable[]> {

        final Subscriber<? super R> child;
        final Zip<R> zipper;
        final ZipProducer<R> producer;

        boolean started;

        public ZipSubscriber(Subscriber<? super R> child, Zip<R> zipper, ZipProducer<R> producer) {
            this.child = child;
            this.zipper = zipper;
            this.producer = producer;
        }

        @Override
        public void onCompleted() {
            if (!started) {
                // this means we have not received a valid onNext before termination so we emit the onCompleted
                child.onCompleted();
            }
        }

        @Override
        public void onError(Throwable e) {
            child.onError(e);
        }

        @Override
        public void onNext(Observable[] observables) {
            if (observables == null || observables.length == 0) {
                child.onCompleted();
            } else {
                started = true;
                zipper.start(observables, producer);/*接收到上游下发的Observable数组*/
            }
        }

    }

    static final class ZipProducer<R> extends AtomicLong implements Producer {
        /** */
        private static final long serialVersionUID = -1216676403723546796L;

        final Zip<R> zipper;

        public ZipProducer(Zip<R> zipper) {
            this.zipper = zipper;
        }

        @Override
        public void request(long n) {
            BackpressureUtils.getAndAddRequest(this, n);
            // try and claim emission if no other threads are doing so
            zipper.tick();
        }

    }
//合并操作，执行订阅上游下发的Observable数组 start方法
    static final class Zip<R> extends AtomicLong {
        /** */
        private static final long serialVersionUID = 5995274816189928317L;

        final Observer<? super R> child;
        private final FuncN<? extends R> zipFunction;
        private final CompositeSubscription childSubscription = new CompositeSubscription()/*多个待zip的内部Observable的订阅关系*/;

        static final int THRESHOLD = (int) (RxRingBuffer.SIZE * 0.7);
        int emitted; // not volatile/synchronized as accessed inside COUNTER_UPDATER block

        /* initialized when started in `start` */
        private volatile Object[] subscribers;
        private AtomicLong requested;

        public Zip(final Subscriber<? super R> child, FuncN<? extends R> zipFunction) {
            this.child = child;
            this.zipFunction = zipFunction;
            child.add(childSubscription);
        }

        @SuppressWarnings("unchecked")
        public void start(@SuppressWarnings("rawtypes") Observable[] os, AtomicLong requested/*记录已经请求的数量*/) {
            final Object[] subscribers = new Object[os.length];
            for (int i = 0; i < os.length; i++) {
                InnerSubscriber io = new InnerSubscriber()/*内部的Observable的订阅者*/;
                subscribers[i] = io;
                childSubscription.add(io);
            }

            this.requested = requested;
            this.subscribers = subscribers; // full memory barrier: release all above

            for (int i = 0; i < os.length; i++) {
                os[i].unsafeSubscribe((InnerSubscriber) subscribers[i]);
            }
        }

        /**
         * check if we have values for each and emit if we do
         *
         * This will only allow one thread at a time to do the work, but ensures via `counter` increment/decrement
         * that there is always once who acts on each `tick`. Same concept as used in OperationObserveOn.
         *
         */
//        合并缓存的数据 调用合并方法 向下游继续发送数据
        @SuppressWarnings("unchecked")
        void tick() {
            final Object[] subscribers = this.subscribers;
            if (subscribers == null) {
                // nothing yet to do (initial request from Producer)
                return;
            }
            if (getAndIncrement() == 0) {
                final int length = subscribers.length;
                final Observer<? super R> child = this.child;
                final AtomicLong requested = this.requested;
                do {
                    while (true) {
                        // peek for a potential onCompleted event
                        final Object[] vs = new Object[length];
                        boolean allHaveValues = true;
                        for (int i = 0; i < length; i++) {
                            RxRingBuffer buffer = ((InnerSubscriber) subscribers[i]).items;
                            Object n = buffer.peek();

                            if (n == null) {
                                allHaveValues = false;
                                continue;
                            }

                            if (buffer.isCompleted(n)) {/*如果zip的多个Observable有一个下发了onCompleted则整条链均解除订阅关系*/
                                child.onCompleted();
                                // we need to unsubscribe from all children since children are
                                // independently subscribed
                                childSubscription.unsubscribe();
                                return;
                            } else {
                                vs[i] = buffer.getValue(n);
                            }
                        }
                        // we only emit if requested > 0 and have all values available
                        if (allHaveValues && requested.get() > 0) {
                            try {
                                // all have something so emit
                                child.onNext(zipFunction.call(vs));
                                // we emitted so decrement the requested counter
                                requested.decrementAndGet();
                                emitted++;
                            } catch (Throwable e) {
                                Exceptions.throwOrReport(e, child, vs);
                                return;
                            }
                            // now remove them
                            for (Object obj : subscribers) {
                                RxRingBuffer buffer = ((InnerSubscriber) obj).items;
                                buffer.poll();
                                // eagerly check if the next item on this queue is an onComplete
                                if (buffer.isCompleted(buffer.peek())) {
                                    // it is an onComplete so shut down
                                    child.onCompleted();
                                    // we need to unsubscribe from all children since children are independently subscribed
                                    childSubscription.unsubscribe();
                                    return;
                                }
                            }
                            if (emitted > THRESHOLD) {
                                for (Object obj : subscribers) {
                                    ((InnerSubscriber) obj).requestMore(emitted);
                                }
                                emitted = 0;
                            }
                        } else {
                            break;
                        }
                    }
                } while (decrementAndGet() > 0);
            }

        }

        // used to observe each Observable we are zipping together
        // it collects all items in an internal queue
        @SuppressWarnings("rawtypes")

//        Zip的内部订阅者 会订阅前面几个的Observable缓存结果
//        当多个Observable的第1,2,...个结果均发出之后调用一次合并方法向下游再发送一次数据

//        zip操作符会有被压操作 防止发射数据过多导致 观察者来不及处理的问题（为Zip的非静态内部类）
        final class InnerSubscriber extends Subscriber {
            // Concurrent* since we need to read it from across threads
            final RxRingBuffer items = RxRingBuffer.getSpmcInstance();/*每一个Subscriber内部缓存了一个zip操作符发射出来的数据*/

            @Override
            public void onStart() {
                request(RxRingBuffer.SIZE);
            }

            public void requestMore(long n) {
                request(n);
            }

            @Override
            public void onCompleted() {
                items.onCompleted();
                tick();
            }

            @Override
            public void onError(Throwable e) {
                // emit error immediately and shut down
                child.onError(e);
            }

            @Override
            public void onNext(Object t) {
                try {
                    // TODO: 18-1-3 hunter testCode
//                    zip下游抛出异常 上游还在发送数据
//                    System.out.println("capacity:"+items.capacity() +"  items available："+items.available());
//                    end
                    items.onNext(t);
                } catch (MissingBackpressureException e) {
                    onError(e);
                }
                tick();/*外部类Zip的方法*/
            }
        }
    }

}
