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

package io.reactivex.internal.operators.observable;

import io.reactivex.internal.functions.ObjectHelper;
import java.util.concurrent.Callable;

import io.reactivex.*;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.internal.disposables.EmptyDisposable;
//根据http://static.blog.piasy.com/AdvancedRxJava/2016/10/02/the-reactive-streams-api-part-4/
// 根据reactive stream的规范,此处应该使用SubscriptionArbiter 当才开始订阅时返回给child实际订阅者的应该是sa
// 当工厂创建实际的Observable无异常时， 用创建的ob订阅一个创建的Subscriber然后调用真实的订阅者child ，同时替换sa中的Subscription
// child实际持有的还是sa 只是sa在Observable的创建过程 和 创建完成的订阅过程中 Subscription已经被替换了

//由于实际的Observable中subscribe并不存在Subscriber的订阅者
// 因此该出的实现为创建Observable时如果出现异常 回调Observer onSubscribe 提供一个EmptyDisposable，并且回调error
//如果没有异常则正常执行订阅，正常返回Observable订阅的Disposable
public final class ObservableDefer<T> extends Observable<T> {
    final Callable<? extends ObservableSource<? extends T>> supplier;
    public ObservableDefer(Callable<? extends ObservableSource<? extends T>> supplier) {
        this.supplier = supplier;
    }
    @Override
    public void subscribeActual(Observer<? super T> s) {
        ObservableSource<? extends T> pub;
        try {
            pub = ObjectHelper.requireNonNull(supplier.call(), "null ObservableSource supplied");
        } catch (Throwable t) {
            Exceptions.throwIfFatal(t);
            EmptyDisposable.error(t, s);/*Observable创建工厂 创建Observable时的异常捕捉*/
            return;
        }

        pub.subscribe(s);
    }
}
