/**
 * Copyright (c) 2016-present, RxJava Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package io.reactivex.subjects;

import io.reactivex.*;
import io.reactivex.annotations.*;

/**
 * Represents an {@link Observer} and an {@link Observable} at the same time, allowing
 * multicasting events from a single source to multiple child {@code Observer}s.
 * <p>
 * All methods except the {@link #onSubscribe(io.reactivex.disposables.Disposable)}, {@link #onNext(Object)},
 * {@link #onError(Throwable)} and {@link #onComplete()} are thread-safe.
 * Use {@link #toSerialized()} to make these methods thread-safe as well.
 *
 * @param <T> the item value type
 */
//Subject既是 Observable 也是 Observer 链接了订阅者和发射者
// 作为订阅者接收其他Observable发送的事件序列，同时也可以作为发射者，将事件*多播*的下发给自己的订阅者

//Subject主要有以下几种：
//1.AsyncSubject （onComplete 以前的订阅者 和 以后的订阅者 只有在Observable 发送onComplete之后才能收到最后一个事件 和 onComplete,如果Observable 发送了onError则只能收到onError*read the code 源码就是这个逻辑*）
//2.BehaviorSubject （订阅者只能接收到 订阅之前的最近的一个事件 及其后续的事件序列 *最近的如果是onComplete 或者onError 则订阅者只能收到 onComplete 和 onError）
//3.PublishSubject （只是实现了多播功能，订阅者不能接收到完整的事件，订阅者只能接收到订阅之后的事件，订阅者订阅之前Observable发送的事件无法被接收到.onError 或者onComplete之后订阅只能收到onError或者onComplete事件）
//4.ReplaySubject （订阅者可以接收到 Observable 之前发射的所有事件序列
// *容量为1的ReplaySubject与BehaviorSubject存在的区别是：在onComplete或者onError之后订阅，Replay还是可以收到一个onNext事件，然后紧接着一个onComplete或者onError。
// 而BehaviorSubject只能收到一个onComplete或者onError而无法收到onNext事件。*）
//5.SerializedSubject （保证对onXXX访问进行串行化的访问 上面四种类型的Subject均不保证对 Observable对Subject的 onXXX方法进行串行的访问）
//6.UnicastSubject　（ReplaySubject的单播模式,只允许一个订阅者,订阅者可以接收到订阅之前的完整的事件序列)
public abstract class Subject<T> extends Observable<T> implements Observer<T> {
    /**
     * Returns true if the subject has any Observers.
     * <p>The method is thread-safe.
     * @return true if the subject has any Observers
     */
    public abstract boolean hasObservers();

    /**
     * Returns true if the subject has reached a terminal state through an error event.
     * <p>The method is thread-safe.
     * @return true if the subject has reached a terminal state through an error event
     * @see #getThrowable()
     * @see #hasComplete()
     */
    public abstract boolean hasThrowable();

    /**
     * Returns true if the subject has reached a terminal state through a complete event.
     * <p>The method is thread-safe.
     * @return true if the subject has reached a terminal state through a complete event
     * @see #hasThrowable()
     */
    public abstract boolean hasComplete();

    /**
     * Returns the error that caused the Subject to terminate or null if the Subject
     * hasn't terminated yet.
     * <p>The method is thread-safe.
     * @return the error that caused the Subject to terminate or null if the Subject
     * hasn't terminated yet
     */
    @Nullable
    public abstract Throwable getThrowable();

    /**
     * Wraps this Subject and serializes the calls to the onSubscribe, onNext, onError and
     * onComplete methods, making them thread-safe.
     * <p>The method is thread-safe.
     * @return the wrapped and serialized subject
     */
    @NonNull
    public final Subject<T> toSerialized() {
        if (this instanceof SerializedSubject) {
            return this;
        }
        return new SerializedSubject<T>(this);
    }
}
