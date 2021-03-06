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
package rx.internal.schedulers;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import rx.*;
import rx.functions.Action0;
import rx.plugins.RxJavaHooks;
import rx.subscriptions.*;

/**
 * Scheduler that wraps an Executor instance and establishes the Scheduler contract upon it.
 * <p>
 * Note that thread-hopping is unavoidable with this kind of Scheduler as we don't know about the underlying
 * threading behavior of the executor.
 */
//ExecutorScheduler
public final class ExecutorScheduler extends Scheduler {
    final Executor executor;
    public ExecutorScheduler(Executor executor) {
        this.executor = executor;
    }

    @Override
    public Worker createWorker() {
        return new ExecutorSchedulerWorker(executor);
    }

    /** Worker that schedules tasks on the executor indirectly through a trampoline mechanism. */
    static final class ExecutorSchedulerWorker extends Scheduler.Worker implements Runnable {
        final Executor executor;
        // TODO: use a better performing structure for task tracking
        final CompositeSubscription tasks;
        // TODO: use MpscLinkedQueue once available
        final ConcurrentLinkedQueue<ScheduledAction> queue;
        final AtomicInteger wip;

        final ScheduledExecutorService service;/*辅助完成周期性任务的调度*/
        /*用自己具有周期性调度任务能力的线程池执行一次周期性调度任务，当周期来临再真正的调用用户自己设置的线程池调度真实任务*/

        public ExecutorSchedulerWorker(Executor executor) {
            this.executor = executor;
            this.queue = new ConcurrentLinkedQueue<ScheduledAction>();/*存放用户提交过来的任务，在自己的run方法中取用执行*/
            this.wip = new AtomicInteger();
            this.tasks = new CompositeSubscription();
            this.service = GenericScheduledExecutorService.getInstance();
        }
//队列漏
        @Override
        public Subscription schedule(Action0 action) {
            if (isUnsubscribed()) {
                return Subscriptions.unsubscribed();
            }

            action = RxJavaHooks.onScheduledAction(action);

            ScheduledAction ea = new ScheduledAction(action, tasks);
            tasks.add(ea);
            queue.offer(ea);
            if (wip.getAndIncrement() == 0) {
                try {
                    // note that since we schedule the emission of potentially multiple tasks
                    // there is no clear way to cancel this schedule from individual tasks
                    // so even if executor is an ExecutorService, we can't associate the future
                    // returned by submit() with any particular ScheduledAction

//                    为了保证顺序提交过来的任务是按照提交顺序执行
//                    向用户设置的线程池提交的任务只要ExecutorSchedulerWorker自己（自己的run方法）
//                    因为如果把所有用户提交过来的任务都向线程池提交则这些任务无法保证按照提交顺序执行（可能存在并发执行）
//                    除非像系统的默认的调度器一样，使用的是单线程的ScheduledThreadPoolExecutor
                    executor.execute(this);/*向线程池提交任务，可能由于线程池的拒绝策略导致线程池抛出拒绝服务异常*/
                } catch (RejectedExecutionException t) {
                    // cleanup if rejected
                    tasks.remove(ea);
                    wip.decrementAndGet();
                    // report the error to the plugin
                    RxJavaHooks.onError(t);
                    // throw it to the caller
                    throw t;
                }
            }

            return ea;
        }

        @Override
        public void run() {
            do {
                if (tasks.isUnsubscribed()) {
                    queue.clear();
                    return;
                }
//                此处获取的sa即为schedule时提交的ea 执行前先检查sa的状态，查看是否已经被取消，如果已经被取消则放弃执行
//                同时还需要检查当前Worker是否已经被放弃执行，如果当前Worker已经被解除订阅，则关联当前Worker的所有任务放弃执行（清空任务队列操作）
                ScheduledAction sa = queue.poll();
                if (sa == null) {
                    return;
                }
                if (!sa.isUnsubscribed()) {
                    if (!tasks.isUnsubscribed()) {
                        sa.run();
                    } else {
                        queue.clear();
                        return;
                    }
                }
            } while (wip.decrementAndGet() != 0);/*队列漏 只有当把队列从0增加到1的线程才有资格执行任务 当前任务执行完成之后检查是否有并行的任务提交过来*/
        }
//相比 http://static.blog.piasy.com/AdvancedRxJava/2016/08/19/schedulers-2/
//缺少了判断用户提供的线程池 是否是ScheduledThreadPoolExecutor，如果是则直接使用当前线程池执行，而避免使用调度线程池
        @Override
        public Subscription schedule(Action0 action, long delayTime, TimeUnit unit) {
            if (delayTime <= 0) {
                return schedule(action);
            }
            if (isUnsubscribed()) {
                return Subscriptions.unsubscribed();
            }

            final Action0 decorated = RxJavaHooks.onScheduledAction(action);

            final MultipleAssignmentSubscription first = new MultipleAssignmentSubscription();
            final MultipleAssignmentSubscription mas = new MultipleAssignmentSubscription();
            mas.set(first);
            tasks.add(mas);
            final Subscription removeMas = Subscriptions.create(new Action0() {
                @Override
                public void call() {
                    tasks.remove(mas);
                }
            });

            ScheduledAction ea = new ScheduledAction(new Action0() {
                @Override
                public void call() {
                    if (mas.isUnsubscribed()) {
                        return;
                    }
                    // schedule the real action non-delayed
                    Subscription s2 = schedule(decorated);/*此处的任务调度延时并不精准（响应性低），因为如果此时调度这个任务，并且顺序执行，可能存在于前面的任务调度占用时间过长 从而导致该处延时不准确*/
                    mas.set(s2);
                    // unless the worker is unsubscribed, we should get a new ScheduledAction
                    if (s2.getClass() == ScheduledAction.class) {
                        // when this ScheduledAction completes, we need to remove the
                        // MAS referencing the whole setup to avoid leaks
                        ((ScheduledAction)s2).add(removeMas);
                    }
                }
            });
            // This will make sure if ea.call() gets executed before this line
            // we don't override the current task in mas.
            first.set(ea);
            // we don't need to add ea to tasks because it will be tracked through mas/first


            try {
                Future<?> f = service.schedule(ea, delayTime, unit);
                ea.add(f);
            } catch (RejectedExecutionException t) {
                // report the rejection to plugins
                RxJavaHooks.onError(t);
                throw t;
            }

            /*
             * This allows cancelling either the delayed schedule or the actual schedule referenced
             * by mas and makes sure mas is removed from the tasks composite to avoid leaks.
             */
            return removeMas;
        }

        @Override
        public boolean isUnsubscribed() {
            return tasks.isUnsubscribed();
        }

        @Override
        public void unsubscribe() {
            tasks.unsubscribe();
            queue.clear();
        }

    }
}
