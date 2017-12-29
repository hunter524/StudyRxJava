/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package rx.internal.producers;

import java.util.Queue;
import java.util.concurrent.atomic.*;

import rx.*;
import rx.exceptions.*;
import rx.internal.operators.BackpressureUtils;
import rx.internal.util.atomic.SpscLinkedAtomicQueue;
import rx.internal.util.unsafe.*;

/**
 * Producer that holds an unbounded (or custom) queue, handles terminal events,
 * enqueues values and relays them to a child subscriber on request.
 *
 * @param <T> the value type
 */
public final class QueuedProducer<T> extends AtomicLong implements Producer, Observer<T> {

    /** */
    private static final long serialVersionUID = 7277121710709137047L;

    final Subscriber<? super T> child;
    final Queue<Object> queue;
    final AtomicInteger wip;

    Throwable error;
    volatile boolean done;

    static final Object NULL_SENTINEL = new Object();

    /**
     * Constructs an instance with the target child subscriber and an Spsc Linked (Atomic) Queue
     * as the queue implementation.
     * @param child the target child subscriber
     */
    public QueuedProducer(Subscriber<? super T> child) {
        this(child, UnsafeAccess.isUnsafeAvailable()
                ? new SpscLinkedQueue<Object>() : new SpscLinkedAtomicQueue<Object>());
    }
    /**
     * Constructs an instance with the target child subscriber and a custom queue implementation
     * @param child the target child subscriber
     * @param queue the queue to use
     */
    public QueuedProducer(Subscriber<? super T> child, Queue<Object> queue) {
        this.child = child;
        this.queue = queue;
        this.wip = new AtomicInteger();
    }

    @Override
    public void request(long n) {
        if (n < 0) {
            throw new IllegalArgumentException("n >= 0 required");
        }
        if (n > 0) {
            BackpressureUtils.getAndAddRequest(this, n);
            drain();
        }
    }

    /**
     * Offers a value to this producer and tries to emit any queued values
     * if the child requests allow it.
     * @param value the value to enqueue and attempt to drain
     * @return true if the queue accepted the offer, false otherwise
     */
    public boolean offer(T value) {
        if (value == null) {
            if (!queue.offer(NULL_SENTINEL)) {
                return false;
            }
        } else {
            if (!queue.offer(value)) {
                return false;
            }
        }
//        如果入对成功则开始执行队列漏操作
        drain();
        return true;
    }

    @Override
    public void onNext(T value) {
        if (!offer(value)) {
            onError(new MissingBackpressureException());
        }
    }

    @Override
    public void onError(Throwable e) {
        error = e;
        done = true;
        drain();
    }

    @Override
    public void onCompleted() {
        done = true;
        drain();
    }

    private boolean checkTerminated(boolean isDone,
            boolean isEmpty) {
        if (child.isUnsubscribed()) {
            return true;
        }
        if (isDone) {
            Throwable e = error;
            if (e != null) {
                queue.clear();
                child.onError(e);
                return true;
            } else
            if (isEmpty) {
                child.onCompleted();
                return true;
            }
        }
        return false;
    }

    private void drain() {
        if (wip.getAndIncrement() == 0) {
            final Subscriber<? super T> c = child;
            final Queue<Object> q = queue;

            do {
//                先检查done 再判断队列为空是为了是为了避免漏掉一些数据
//                因为 drain() 函数可能被并发调用（被 onXXX() 函数或者 request() 函数调用）
//                检查done 和 isEmpty并非原子操作（存在竟态条件）
//                如果先检查 isEmpty可能存在 当前队列为空 后来加入元素 再后来done置位为true导致 中间加入的元素被遗漏发射
                if (checkTerminated(done, q.isEmpty())) {    // (1)
                    return;
                }
//设置的是队列漏的标志位
                wip.lazySet(1);
//此处获取的是当前的请求的数据 当前请求的数据不为0且存在数据时才会将数据漏给订阅者
                long r = get();
                long e = 0;
//队列漏的优化 一次消耗所有元素
                while (r != 0) {
                    boolean d = done;
                    Object v = q.poll();
                    if (checkTerminated(d, v == null)) {
                        return;
                    } else
                    if (v == null) {
                        break;
                    }

                    try {
//JCTools的队列不能放入null 元素 但是rxjava1 允许onNext传入null元素，因此需要对null元素进行标记
                        if (v == NULL_SENTINEL) {
                            c.onNext(null);
                        } else {
                            @SuppressWarnings("unchecked")
                            T t = (T)v;
                            c.onNext(t);
                        }
                    } catch (Throwable ex) {
                        Exceptions.throwOrReport(ex, c, v != NULL_SENTINEL ? v : null);
                        return;
                    }
                    r--;
                    e++;
                }
// 此处设置的是已经发送的数据
                if (e != 0 && get() != Long.MAX_VALUE) {
                    addAndGet(-e);
                }
            } while (wip.decrementAndGet() != 0);
        }
    }
}