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
package rx.observers;

import rx.Observer;
import rx.exceptions.*;
import rx.internal.operators.NotificationLite;

/**
 * Enforces single-threaded, serialized, ordered execution of {@link #onNext}, {@link #onCompleted}, and
 * {@link #onError}.
 * <p>
 * When multiple threads are emitting and/or notifying they will be serialized by:
 * </p><ul>
 * <li>Allowing only one thread at a time to emit</li>
 * <li>Adding notifications to a queue if another thread is already emitting</li>
 * <li>Not holding any locks or blocking any threads while emitting</li>
 * </ul>
 *
 * @param <T>
 *          the type of items expected to be observed by the {@code Observer}
 */
public class SerializedObserver<T> implements Observer<T> {
    private final Observer<? super T> actual;

    private boolean emitting;
    /** Set to true if a terminal event was received. */
    private volatile boolean terminated;
    /** If not null, it indicates more work. */
    private FastList queue;

    static final class FastList {
        Object[] array;
        int size;

        public void add(Object o) {
            int s = size;
            Object[] a = array;
            if (a == null) {
                a = new Object[16];
                array = a;
            } else if (s == a.length) {
                Object[] array2 = new Object[s + (s >> 2)];
                System.arraycopy(a, 0, array2, 0, s);
                a = array2;
                array = a;
            }
            a[s] = o;
            size = s + 1;
        }
    }

    public SerializedObserver(Observer<? super T> s) {
        this.actual = s;
    }

    @Override
    public void onNext(T t) {
        if (terminated) {
            return;
        }
//        发射循环 第一个同步方法判断是否 已经结束或者正在发射
//        如果已经有线程正在发射则执行 入队操作 然后入队线程返回 由当前正在发射的线程继续执行发射操作
//        没有则标记当前线程进入法神状态 发射当前元素
        synchronized (this) {
            if (terminated) {
                return;
            }
            if (emitting) {
                FastList list = queue;
                if (list == null) {
                    list = new FastList();
                    queue = list;
                }
                list.add(NotificationLite.next(t));
                return;
            }
            emitting = true;
        }
//        没有线程正在发射 则直接发射当前元素
        try {
            actual.onNext(t);
        } catch (Throwable e) {
            terminated = true;
            Exceptions.throwOrReport(e, actual, t);
            return;
        }
//        发射完当前元素之后lock this 判断队列当前元素是否为空
//                                 1.为空则返回（发射当前元素的时候没有其他线程插入元素）
//                                 2.不为空则发射当前元素期间（有元素被插入队列，当前发射线程继续发射元素）
//                                   一次性循环发射完成队列中的所有元素
//        由 this 锁住的fastlist 保证队列的线程安全性
        for (;;) {
            FastList list;
            synchronized (this) {
                list = queue;
                if (list == null) {
                    emitting = false;
                    return;
                }
                queue = null;
            }
            for (Object o : list.array) {
                if (o == null) {
                    break;
                }
                try {
                    if (NotificationLite.accept(actual, o)) {
                        terminated = true;
                        return;
                    }
                } catch (Throwable e) {
                    terminated = true;
                    Exceptions.throwIfFatal(e);
                    actual.onError(OnErrorThrowable.addValueAsLastCause(e, t));
                    return;
                }
            }
        }
    }

    @Override
    public void onError(final Throwable e) {
        Exceptions.throwIfFatal(e);
        if (terminated) {
            return;
        }
        synchronized (this) {
            if (terminated) {
                return;
            }
            terminated = true;
            if (emitting) {
                /*
                 * FIXME: generally, errors jump the queue but this wasn't true
                 * for SerializedObserver and may break existing expectations.
                 */
                FastList list = queue;
                if (list == null) {
                    list = new FastList();
                    queue = list;
                }
                list.add(NotificationLite.error(e));
                return;
            }
            emitting = true;
        }
        actual.onError(e);
    }

    @Override
    public void onCompleted() {
        if (terminated) {
            return;
        }
        synchronized (this) {
            if (terminated) {
                return;
            }
            terminated = true;
            if (emitting) {
                FastList list = queue;
                if (list == null) {
                    list = new FastList();
                    queue = list;
                }
                list.add(NotificationLite.completed());
                return;
            }
            emitting = true;
        }
        actual.onCompleted();
    }
}
