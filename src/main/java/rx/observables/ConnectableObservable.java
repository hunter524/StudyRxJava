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
package rx.observables;

import rx.*;
import rx.functions.*;
import rx.internal.operators.*;

/**
 * A {@code ConnectableObservable} resembles an ordinary {@link Observable}, except that it does not begin
 * emitting items when it is subscribed to, but only when its {@link #connect} method is called. In this way you
 * can wait for all intended {@link Subscriber}s to {@link Observable#subscribe} to the {@code Observable}
 * before the {@code Observable} begins emitting items.
 * <p>
 * <img width="640" height="510" src="https://github.com/ReactiveX/RxJava/wiki/images/rx-operators/publishConnect.png" alt="">
 *
 * @see <a href="https://github.com/ReactiveX/RxJava/wiki/Connectable-Observable-Operators">RxJava Wiki:
 *      Connectable Observable Operators</a>
 * @param <T>
 *          the type of items emitted by the {@code ConnectableObservable}
 */
//ConnectableObservable与Subject的区别：
//    1、Subject既是Observable也是Observer，向其上游订阅（数据源）向其下游（订阅者）订阅数据。
//    2、ConnectableObservable是存储上游的onSubscriber，可以被多个订阅者订阅，并且当只有调用connect方法之后才向下游发送数据

//    实现ConnectableObservable接口的有:
//    1.OperatorPublish(Observable中的publish操作附返回该类型,publish之后返回的ConnectObservable,在connect之前的订阅者可以接收到完整的事件序列,connect之后的订阅者只能接收到订阅之后的后续事件)
//!!! 2.OperatorMultiCast(Observable中没有操作附直接与其对应,而是依赖使用者自己构建,connect 之后 的订阅者能接收到什么事件,依赖于使用者传入的SubjectFactory返回的Subject类型)
//    3.OperatorReplay(Observable的replay操作附返回该类型,重放connect之后接收到的所有的订阅事件)

//    warning: connect当subscribe之后再解除unSubscribe 不会导致订阅者数量的改变,当触发达到订阅者的数量之后依旧会自动链接

public abstract class ConnectableObservable<T> extends Observable<T> {

    protected ConnectableObservable(OnSubscribe<T> onSubscribe) {
        super(onSubscribe);
    }

    /**
     * Instructs the {@code ConnectableObservable} to begin emitting the items from its underlying
     * {@link Observable} to its {@link Subscriber}s.
     * <p>
     * To disconnect from a synchronous source, use the {@link #connect(rx.functions.Action1)} method.
     *
     * @return the subscription representing the connection
     * @see <a href="http://reactivex.io/documentation/operators/connect.html">ReactiveX documentation: Connect</a>
     */
//    用数组避免final的问题
    public final Subscription connect() {
        final Subscription[] out = new Subscription[1];
        connect(new Action1<Subscription>() {
            @Override
            public void call(Subscription t1) {
                out[0] = t1;
            }
        });
        return out[0];
    }
    /**
     * Instructs the {@code ConnectableObservable} to begin emitting the items from its underlying
     * {@link Observable} to its {@link Subscriber}s.
     *
     * @param connection
     *          the action that receives the connection subscription before the subscription to source happens
     *          allowing the caller to synchronously disconnect a synchronous source
     * @see <a href="http://reactivex.io/documentation/operators/connect.html">ReactiveX documentation: Connect</a>
     */
    public abstract void connect(Action1<? super Subscription> connection);

    /**
     * Returns an {@code Observable} that stays connected to this {@code ConnectableObservable} as long as there
     * is at least one subscription to this {@code ConnectableObservable}.
     *
     * @return a {@link Observable}
     * @see <a href="http://reactivex.io/documentation/operators/refcount.html">ReactiveX documentation: RefCount</a>
     */
    public Observable<T> refCount() {
        return unsafeCreate(new OnSubscribeRefCount<T>(this));
    }

    /**
     * Returns an Observable that automatically connects to this ConnectableObservable
     * when the first Subscriber subscribes.
     *
     * @return an Observable that automatically connects to this ConnectableObservable
     *         when the first Subscriber subscribes
     * @since 1.2
     */
    public Observable<T> autoConnect() {
        return autoConnect(1);
    }
    /**
     * Returns an Observable that automatically connects to this ConnectableObservable
     * when the specified number of Subscribers subscribe to it.
     *
     * @param numberOfSubscribers the number of subscribers to await before calling connect
     *                            on the ConnectableObservable. A non-positive value indicates
     *                            an immediate connection.
     * @return an Observable that automatically connects to this ConnectableObservable
     *         when the specified number of Subscribers subscribe to it
     * @since 1.2
     */
    public Observable<T> autoConnect(int numberOfSubscribers) {
        return autoConnect(numberOfSubscribers, Actions.empty());
    }

    /**
     * Returns an Observable that automatically connects to this ConnectableObservable
     * when the specified number of Subscribers subscribe to it and calls the
     * specified callback with the Subscription associated with the established connection.
     *
     * @param numberOfSubscribers the number of subscribers to await before calling connect
     *                            on the ConnectableObservable. A non-positive value indicates
     *                            an immediate connection.
     * @param connection the callback Action1 that will receive the Subscription representing the
     *                   established connection
     * @return an Observable that automatically connects to this ConnectableObservable
     *         when the specified number of Subscribers subscribe to it and calls the
     *         specified callback with the Subscription associated with the established connection
     * @since 1.2
     */
//    返回一个Observable当订阅者数量达到设定值时自动connect向下进行数据的发射
//    如果传入的数量<=0,则此方法同connect方法
    public Observable<T> autoConnect(int numberOfSubscribers, Action1<? super Subscription> connection) {
        if (numberOfSubscribers <= 0) {
            this.connect(connection);
            return this;
        }
        return unsafeCreate(new OnSubscribeAutoConnect<T>(this, numberOfSubscribers, connection));
    }
}
