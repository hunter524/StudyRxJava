/**
 * Copyright 2016 Netflix, Inc.
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

import rx.Observable.*;
import rx.Subscriber;
import rx.exceptions.Exceptions;
import rx.plugins.RxJavaHooks;

/**
 * Transforms the downstream Subscriber into a Subscriber via an operator
 * callback and calls the parent OnSubscribe.call() method with it.
 * @param <T> the source value type
 * @param <R> the result value type
 */
//Lift转换Operator操作
public final class OnSubscribeLift<T, R> implements OnSubscribe<R> {

    final OnSubscribe<T> parent;

    final Operator<? extends R, ? super T> operator;
//上游链的调用者是Parent（构建调用链时使用该方法）
    public OnSubscribeLift(OnSubscribe<T> parent, Operator<? extends R, ? super T> operator) {
        this.parent = parent;
        this.operator = operator;
    }
//订阅之后由下游向上游回溯call 因此传入的是下游的Subscriber
//该OnSubscribexxx 获取Operator转换后的Subscriber交给上游调用
    @Override
    public void call(Subscriber<? super R> o) {
        try {
            /*st交给上游调用 上游调用st st调用下游 从而进行转换*/
            Subscriber<? super T> st = RxJavaHooks.onObservableLift(operator).call(o);
            try {
                // new Subscriber created and being subscribed with so 'onStart' it
                st.onStart();
                parent.call(st);/*如果上游是回溯的终点则调用当前的subscriber向下发送事件*/
            } catch (Throwable e) {
                // localized capture of errors rather than it skipping all operators
                // and ending up in the try/catch of the subscribe method which then
                // prevents onErrorResumeNext and other similar approaches to error handling
                Exceptions.throwIfFatal(e);
                st.onError(e);
            }
        } catch (Throwable e) {
            Exceptions.throwIfFatal(e);
            // if the lift function failed all we can do is pass the error to the final Subscriber
            // as we don't have the operator available to us
            o.onError(e);
        }
    }
}