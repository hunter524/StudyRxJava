/**
 * Copyright 2014 Netflix, Inc.
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
package rx.internal.operators;

import java.util.concurrent.TimeUnit;

import rx.*;
import rx.Observable.Operator;
import rx.Scheduler.Worker;
import rx.functions.Action0;
import rx.observers.SerializedSubscriber;

/**
 * Takes values from the source until the specific time elapses.
 *
 * @param <T>
 *            the result value type
 */
public final class OperatorTakeTimed<T> implements Operator<T, T> {
    final long time;
    final TimeUnit unit;
    final Scheduler scheduler;

    public OperatorTakeTimed(long time, TimeUnit unit, Scheduler scheduler) {
        this.time = time;
        this.unit = unit;
        this.scheduler = scheduler;
    }

    @Override
    public Subscriber<? super T> call(Subscriber<? super T> child) {
        Worker worker = scheduler.createWorker();
//        同时将定时调度任务加入 子订阅者的订阅关系中 子的订阅者关系取消之后也会取消该定时调度任务
        child.add(worker);

        TakeSubscriber<T> ts = new TakeSubscriber<T>(new SerializedSubscriber<T>(child));
        worker.schedule(ts, time, unit);
        return ts;
    }
    /** Subscribed to source and scheduled on a worker. */
    static final class TakeSubscriber<T> extends Subscriber<T> implements Action0 {
        final Subscriber<? super T> child;
        public TakeSubscriber(Subscriber<? super T> child) {
            super(child);
            this.child = child;
        }

        @Override
        public void onNext(T t) {
            child.onNext(t);
        }

        @Override
        public void onError(Throwable e) {
            child.onError(e);
            unsubscribe();
        }

        @Override
        public void onCompleted() {
            child.onCompleted();
            unsubscribe();
        }
//timed take 限时的take 延迟调度自己，到达指定时间之后调用onComplete并且解除订阅关系
        @Override
        public void call() {
            onCompleted();
        }


    }
}
