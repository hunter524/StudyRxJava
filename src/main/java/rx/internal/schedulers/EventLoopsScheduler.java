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
package rx.internal.schedulers;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import rx.*;
import rx.functions.Action0;
import rx.internal.util.*;
import rx.subscriptions.*;
//computation 的 Scheduler 基于java并发编程实战 Page 84 脚注cpu密集型任务最大吞吐量的状态是 线程个数等于 Ncpu或者 比CPU个数多一个
public final class EventLoopsScheduler extends Scheduler implements SchedulerLifecycle {
    /**
     * Key to setting the maximum number of computation scheduler threads.
     * Zero or less is interpreted as use available. Capped by available.
     */
    static final String KEY_MAX_THREADS = "rx.scheduler.max-computation-threads";
    /** The maximum number of computation scheduler threads. */
    static final int MAX_THREADS;
    static {
        int maxThreads = Integer.getInteger(KEY_MAX_THREADS, 0);
        int cpuCount = Runtime.getRuntime().availableProcessors();
        int max;
        if (maxThreads <= 0 || maxThreads > cpuCount) {
            max = cpuCount;
        } else {
            max = maxThreads;
        }
        MAX_THREADS = max;
    }

    static final PoolWorker SHUTDOWN_WORKER;
    static {
        SHUTDOWN_WORKER = new PoolWorker(RxThreadFactory.NONE);
        SHUTDOWN_WORKER.unsubscribe();
    }

    /** This will indicate no pool is active. */
    static final FixedSchedulerPool NONE = new FixedSchedulerPool(null, 0);

    final ThreadFactory threadFactory;

    final AtomicReference<FixedSchedulerPool> pool;
//全局只有一个Pool 被EventLoopScheduler持有
//    当Computation的Worker占满时，任务无法被执行是因为任务提交到了 周期性调度的线程池中 之后任务无法被调度执行
    static final class FixedSchedulerPool {
        final int cores;

        final PoolWorker[] eventLoops;
        long n;/*记录当前请求该pool的次数 便于对PoolWorker的循环使用*/
//初始化Pool的时候一次初始化所有的PoolWorker放入数组备用
        FixedSchedulerPool(ThreadFactory threadFactory, int maxThreads) {
            // initialize event loops
            this.cores = maxThreads;
            this.eventLoops = new PoolWorker[maxThreads];
            for (int i = 0; i < maxThreads; i++) {
                this.eventLoops[i] = new PoolWorker(threadFactory);
            }
        }

        public PoolWorker getEventLoop() {
            int c = cores;
            if (c == 0) {
                return SHUTDOWN_WORKER;
            }
            // simple round robin, improvements to come
            return eventLoops[(int)(n++ % c)];
        }

        public void shutdown() {
            for (PoolWorker w : eventLoops) {
                w.unsubscribe();
            }
        }
    }

    /**
     * Create a scheduler with pool size equal to the available processor
     * count and using least-recent worker selection policy.
     * @param threadFactory the factory to use with the executors
     */
    public EventLoopsScheduler(ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
        this.pool = new AtomicReference<FixedSchedulerPool>(NONE);
        start();
    }

    @Override
    public Worker createWorker() {
        return new EventLoopWorker(pool.get().getEventLoop());
    }

    @Override
    public void start() {
        FixedSchedulerPool update = new FixedSchedulerPool(threadFactory, MAX_THREADS);
        if (!pool.compareAndSet(NONE, update)) {
            update.shutdown();
        }
    }

    @Override
    public void shutdown() {
        for (;;) {
            FixedSchedulerPool curr = pool.get();
            if (curr == NONE) {
                return;
            }
            if (pool.compareAndSet(curr, NONE)) {
                curr.shutdown();
                return;
            }
        }
    }

    /**
     * Schedules the action directly on one of the event loop workers
     * without the additional infrastructure and checking.
     * @param action the action to schedule
     * @return the subscription
     */
    public Subscription scheduleDirect(Action0 action) {
       PoolWorker pw = pool.get().getEventLoop();
       return pw.scheduleActual(action, -1, TimeUnit.NANOSECONDS);
    }
//每次EventLoopsScheduler createWork 均创建一个Worker（EventLoopWorker）
//每个任务对应不同的EventLoopWorker 但是EventLoopWorker 可能是持有相同的PoolWorker（NewThreadWorker）
//NewThreadWorker真正的持有线程池去调度任务
    static final class EventLoopWorker extends Scheduler.Worker {
        private final SubscriptionList serial = new SubscriptionList();
        private final CompositeSubscription timed = new CompositeSubscription();
        private final SubscriptionList both = new SubscriptionList(serial, timed);
        private final PoolWorker poolWorker;

        EventLoopWorker(PoolWorker poolWorker) {
            this.poolWorker = poolWorker;

        }

        @Override
        public void unsubscribe() {
            both.unsubscribe();
        }

        @Override
        public boolean isUnsubscribed() {
            return both.isUnsubscribed();
        }

        @Override
        public Subscription schedule(final Action0 action) {
            if (isUnsubscribed()) {
                return Subscriptions.unsubscribed();
            }

            return poolWorker.scheduleActual(new Action0() {
                @Override
                public void call() {
                    if (isUnsubscribed()) {
                        return;
                    }
                    action.call();
                }
            }, 0, null, serial);
        }

        @Override
        public Subscription schedule(final Action0 action, long delayTime, TimeUnit unit) {
            if (isUnsubscribed()) {
                return Subscriptions.unsubscribed();
            }

            return poolWorker.scheduleActual(new Action0() {
                @Override
                public void call() {
                    if (isUnsubscribed()) {
                        return;
                    }
                    action.call();
                }
            }, delayTime, unit, timed);
        }
    }

    static final class PoolWorker extends NewThreadWorker {
        PoolWorker(ThreadFactory threadFactory) {
            super(threadFactory);
        }
    }
}
