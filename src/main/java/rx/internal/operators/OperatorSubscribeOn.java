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

import rx.*;
import rx.Observable.OnSubscribe;
import rx.Scheduler.Worker;
import rx.functions.Action0;

/**
 * Subscribes Observers on the specified {@code Scheduler}.
 * <p>
 * <img width="640" src="https://github.com/ReactiveX/RxJava/wiki/images/rx-operators/subscribeOn.png" alt="">
 *
 * @param <T> the value type of the actual source
 */
//Subscribe向上订阅的call方法在 subscribeOn线程调用
//    通常其他的OnSubscribe 命名方式是 OnSubscribeXXX
//    只有subscribeOn 和 observeOn 执行的是 OperatorSubscribeOn（其实是OnSubscribe） 和 OperatorObserverOn（实际是lift操作）
//    lift操作其实时由OnSubscribeLift操作进行包装Operator的
//    lift使用OnSubscribeLift 创建一个Observable 返回构建调用链

//    subscribeOn使用的是OnSubscribe创建Observable的方式
//    ObserveOn使用的是lift加上OperatorObserveOn 多了一层OnSubscribeLift包装Operator的操作

public final class OperatorSubscribeOn<T> implements OnSubscribe<T> {

    final Scheduler scheduler;
    final Observable<T> source;
    final boolean requestOn;

    public OperatorSubscribeOn(Observable<T> source, Scheduler scheduler, boolean requestOn) {
        this.scheduler = scheduler;
        this.source = source;
        this.requestOn = requestOn;
    }

    @Override
    public void call(final Subscriber<? super T> subscriber/*下游的订阅者*/) {
        final Worker inner = scheduler.createWorker();

        SubscribeOnSubscriber<T> parent = new SubscribeOnSubscriber<T>(subscriber, requestOn, inner, source);
//        Subscriber是从下向call 实际订阅之后是从上向下 因此当前的Subscriber是上面的Parent
        subscriber.add(parent);
        subscriber.add(inner);/*调度关系也会被加入下游的订阅者中，这样只要切换了操作符，即可取消订阅关系*/

        inner.schedule(parent)/*调度call方法进入指定的线程*/;
    }

    static final class SubscribeOnSubscriber<T> extends Subscriber<T> implements Action0 {

        final Subscriber<? super T> actual;

        final boolean requestOn;

        final Worker worker;

        Observable<T> source;

        Thread t;

        SubscribeOnSubscriber(Subscriber<? super T> actual, boolean requestOn, Worker worker, Observable<T> source) {
            this.actual = actual;
            this.requestOn = requestOn;
            this.worker = worker;
            this.source = source;
        }

        @Override
        public void onNext(T t) {
            actual.onNext(t);
        }

        @Override
        public void onError(Throwable e) {
            try {
                actual.onError(e);
            } finally {
                worker.unsubscribe();
            }
        }

        @Override
        public void onCompleted() {
            try {
                actual.onCompleted();
            } finally {
                worker.unsubscribe();
            }
        }

        @Override
        public void call() {
            Observable<T> src = source;
            source = null;
            t = Thread.currentThread();
            src.unsafeSubscribe(this);/*src代表上游的Observable，Subscribe call方法不停的向上调用*/
        }

        @Override
        public void setProducer(final Producer p) {
//            向线程切换的操作符设置Producer时，会将Producer包装一层设置给真实的订阅者
//            包装完成的功能是将 下面进行request请求 转换到正确的subscribeOn线程（不进行转换则发射操作会在错误的线程进行）
            actual.setProducer(new Producer() {
                @Override
                public void request(final long n) {
                    if (t == Thread.currentThread() || !requestOn) {
                        p.request(n);
                    } else {
//                        将request也要调度到当前的Worker线程上面来
                        worker.schedule(new Action0() {
                            @Override
                            public void call() {
                                p.request(n);
                            }
                        });
                    }
                }
            });
        }
    }
}