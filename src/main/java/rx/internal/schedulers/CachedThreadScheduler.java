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
import java.util.concurrent.atomic.*;

import rx.*;
import rx.functions.Action0;
import rx.internal.util.RxThreadFactory;
import rx.subscriptions.*;
//同EventLoopsScheduler NewThreadScheduler 一样 全局只有一个实例被 Schedulers持有
//该调度器的线程保活时间默认是60s
public final class CachedThreadScheduler extends Scheduler implements SchedulerLifecycle {
    private static final long KEEP_ALIVE_TIME;
    private static final TimeUnit KEEP_ALIVE_UNIT = TimeUnit.SECONDS;

    static final ThreadWorker SHUTDOWN_THREADWORKER;

    static final CachedWorkerPool NONE;

    final ThreadFactory threadFactory;

    final AtomicReference<CachedWorkerPool> pool;/*Worker的缓冲池*/

    static {
        SHUTDOWN_THREADWORKER = new ThreadWorker(RxThreadFactory.NONE);
        SHUTDOWN_THREADWORKER.unsubscribe();

        NONE = new CachedWorkerPool(null, 0, null);
        NONE.shutdown();

        KEEP_ALIVE_TIME = Integer.getInteger("rx.io-scheduler.keepalive", 60);
    }
//Pool在被构建的时候 会启动周期性任务调度的线程池 调度自己移除过时的Worker的引用 每次Worker被重新引用然后释放时会更新该时间
    static final class CachedWorkerPool {
        private final ThreadFactory threadFactory;
        private final long keepAliveTime;
        private final ConcurrentLinkedQueue<ThreadWorker> expiringWorkerQueue;/*必须是线程安全的链表队列*/
        private final CompositeSubscription allWorkers;/*所有的Worker*/
        private final ScheduledExecutorService evictorService;/*清理过时Worker任务的线程池*/
        private final Future<?> evictorTask;/*定期清理 过时的Worker Runnable*/

        CachedWorkerPool(final ThreadFactory threadFactory, long keepAliveTime, TimeUnit unit) {
            this.threadFactory = threadFactory;
            this.keepAliveTime = unit != null ? unit.toNanos(keepAliveTime) : 0L;
            this.expiringWorkerQueue = new ConcurrentLinkedQueue<ThreadWorker>();
            this.allWorkers = new CompositeSubscription();

            ScheduledExecutorService evictor = null;
            Future<?> task = null;
            if (unit != null) {
                evictor = Executors.newScheduledThreadPool(1, new ThreadFactory() {
                    @Override public Thread newThread(Runnable r) {
                        Thread thread = threadFactory.newThread(r);
                        thread.setName(thread.getName() + " (Evictor)");
                        return thread;
                    }
                });
                NewThreadWorker.tryEnableCancelPolicy(evictor);
                task = evictor.scheduleWithFixedDelay(
                        new Runnable() {
                            @Override
                            public void run() {
                                evictExpiredWorkers();
                            }
                        }, this.keepAliveTime, this.keepAliveTime, TimeUnit.NANOSECONDS
                );
            }
            evictorService = evictor;
            evictorTask = task;
        }

        ThreadWorker get() {
            if (allWorkers.isUnsubscribed()) {
                return SHUTDOWN_THREADWORKER;
            }
            while (!expiringWorkerQueue.isEmpty()) {
                ThreadWorker threadWorker = expiringWorkerQueue.poll();
                if (threadWorker != null) {
                    return threadWorker;
                }
            }

            // No cached worker found, so create a new one.
            ThreadWorker w = new ThreadWorker(threadFactory);
            allWorkers.add(w);
            return w;
        }

        void release(ThreadWorker threadWorker) {
            // Refresh expire time before putting worker back in pool
            threadWorker.setExpirationTime(now() + keepAliveTime);

            expiringWorkerQueue.offer(threadWorker);
        }
//从缓冲空闲的Worker队列中移除 超时的Worker
        void evictExpiredWorkers() {
            if (!expiringWorkerQueue.isEmpty()) {
                long currentTimestamp = now();

                for (ThreadWorker threadWorker : expiringWorkerQueue) {
                    if (threadWorker.getExpirationTime() <= currentTimestamp) {
                        if (expiringWorkerQueue.remove(threadWorker)) {
                            allWorkers.remove(threadWorker);
                        }
                    } else {
                        // Queue is ordered with the worker that will expire first in the beginning, so when we
                        // find a non-expired worker we can stop evicting.
                        break;
                    }
                }
            }
        }

        long now() {
            return System.nanoTime();
        }
//Scheduler取消时需要取消定时清理任务
        void shutdown() {
            try {
                if (evictorTask != null) {
                    evictorTask.cancel(true);
                }
                if (evictorService != null) {
                    evictorService.shutdownNow();
                }
            } finally {
                allWorkers.unsubscribe();
            }
        }
    }

    public CachedThreadScheduler(ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
        this.pool = new AtomicReference<CachedWorkerPool>(NONE);
        start();
    }
//启动时设置CachedWorkerPool 一个实例只能被启动一次
//    重复启动只会导致pool被创建之后立刻被关闭
    @Override
    public void start() {
        CachedWorkerPool update =
            new CachedWorkerPool(threadFactory, KEEP_ALIVE_TIME, KEEP_ALIVE_UNIT);
        if (!pool.compareAndSet(NONE, update)) {
            update.shutdown();
        }
    }
    @Override
    public void shutdown() {
        for (;;) {
            CachedWorkerPool curr = pool.get();
            if (curr == NONE) {
                return;
            }
            if (pool.compareAndSet(curr, NONE)) {
                curr.shutdown();
                return;
            }
        }
    }
//用EventLoopWorker包装一层 ThreadWorker
    @Override
    public Worker createWorker() {
        return new EventLoopWorker(pool.get());
    }
//同时实现了Subscription接口便于调度任务的取消，同时该任务只能取消一次，取消任务同时释放ThreadWorker
//    用同一个Worker调度的任务保证先进先出
    static final class EventLoopWorker extends Scheduler.Worker implements Action0 {
        private final CompositeSubscription innerSubscription = new CompositeSubscription();/*持有该Worker调度过的所有任务*/
        private final CachedWorkerPool pool;
        private final ThreadWorker threadWorker;
        final AtomicBoolean once;/*Worker只能被取消一次*/

        EventLoopWorker(CachedWorkerPool pool) {
            this.pool = pool;
            this.once = new AtomicBoolean();
            this.threadWorker = pool.get();
        }

        @Override
        public void unsubscribe() {
            if (once.compareAndSet(false, true)) {
                // unsubscribe should be idempotent, so only do this once

                // Release the worker _after_ the previous action (if any) has completed
//                System.out.println("EventLoopWorker unsubscribe!");
                threadWorker.schedule(this);
            }
//            持有当前待调度任务的Future封装 ScheduledAction
//            取消当前提交的所有任务但是不取消后面的NewThreadWorker
//            NewThreadWorker一旦取消则异味着当前线程池的所有任务均被取消（因为其他Worker（EventLoopScheduler内部则是共同持有同一个NewThreadWorker的可能在共用这个NewThreadWorker
            innerSubscription.unsubscribe();
        }
//解订阅的时候才会释放自己 同时释放Worker 将Worker添加到待过期的队列中
        @Override
        public void call() {
            pool.release(threadWorker);
        }

        @Override
        public boolean isUnsubscribed() {
            return innerSubscription.isUnsubscribed();
        }

        @Override
        public Subscription schedule(Action0 action) {
            return schedule(action, 0, null);
        }

        @Override
        public Subscription schedule(final Action0 action, long delayTime, TimeUnit unit) {
            if (innerSubscription.isUnsubscribed()) {/*取消 订阅和提交任务存在竞态条件 CompositeSubscription中解决了 该竞太条件*/
                // don't schedule, we are unsubscribed
                return Subscriptions.unsubscribed();
            }

//            System.out.println("call CachedThreadScheduler EventLoopWorker schedule");

            ScheduledAction s = threadWorker.scheduleActual(new Action0() {/*实际返回的是包装了ScheduledThreadPoolExecutor返回的FutureTask的Action*/
                @Override
                public void call() {
                    if (isUnsubscribed()) {
                        return;
                    }
                    action.call();
                }
            }, delayTime, unit);
            innerSubscription.add(s);
            s.addParent(innerSubscription);
            return s;
        }
    }

    static final class ThreadWorker extends NewThreadWorker {
        private long expirationTime;

        ThreadWorker(ThreadFactory threadFactory) {
            super(threadFactory);
            this.expirationTime = 0L;
        }

        public long getExpirationTime() {
            return expirationTime;
        }
//Worker的缓存时间超时会被移除掉
        public void setExpirationTime(long expirationTime) {
            this.expirationTime = expirationTime;
        }
    }
}
