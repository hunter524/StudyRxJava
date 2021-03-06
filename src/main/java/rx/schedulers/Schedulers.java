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
package rx.schedulers;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import rx.Scheduler;
import rx.internal.schedulers.*;
import rx.plugins.*;

/**
 * Static factory methods for creating Schedulers.
 * <p>
 * System configuration properties:
 * <table border='1'>
 * <tr><td>Property name</td><td>Description</td><td>Default</td></tr>
 * <tr>
 * <td>{@code rx.io-scheduler.keepalive}</td><td>time (in seconds) to keep an unused backing
 *     thread-pool</td><td>60</td>
 * </tr>
 * <tr>
 * <td>{@code rx.scheduler.max-computation-threads}</td><td>number of threads the
 *     computation scheduler uses (between 1 and number of available processors)</td><td>
 *     number of available processors.</td>
 * </tr>
 * <tr>
 * <td>{@code rx.scheduler.jdk6.purge-frequency-millis}</td><td>time (in milliseconds) between calling
 *     purge on any active backing thread-pool on a Java 6 runtime</td><td>1000</td>
 * </tr>
 * <tr>
 * <td>{@code rx.scheduler.jdk6.purge-force}</td><td> boolean forcing the call to purge on any active
 *     backing thread-pool</td><td>false</td>
 * </tr>
 * </ul>
 * </table>
 */
// 1.CachedThreadScheduler（io） 缓存调度器，无限线程池模式，当默认空闲60s的时候，回收调度器和线程池资源
// 2.EventLoopsScheduler（Computation）计算任务调度器，最大线程池数量是cpu个数，此时计算密集型任务避免线程切换，长期高负载时吞吐量最大
//   始终缓存cpu个数的Scheduler，Scheduler循环被需要调度者使用，任务在提交到线程池之后等待
// 3.TrampolineScheduler 蹦床调度器 ，当使用该调度器时同一个createWorker的Worker使用同一个优先级队列，任务执行入队操作，入队后执行哪个由优先级决定。
//   同时任务在哪个线程被执行也是未知的，通常由队列漏首先获得漏权利的线程决定。
// 4.ImmediateScheduler 通常在当前线程进行任务调度，不切换线程。
// 5.NewThreadScheduler 每次构建一个新的NewThreadWorker返回。每次使用完成需要去解除订阅，否则会导致Executor内存泄露
// 6.from 用户自己管理Executor的声明周期，Worker只管调度任务提交进入Executor。使用的是ExecutorScheduler 和 ExecutorSchedulerWorker

public final class Schedulers {

    private final Scheduler computationScheduler;
    private final Scheduler ioScheduler;
    private final Scheduler newThreadScheduler;

    private static final AtomicReference<Schedulers> INSTANCE = new AtomicReference<Schedulers>();

    private static Schedulers getInstance() {
//        cas 结合AtomicReference 实现单列的安全性
        for (;;) {
            Schedulers current = INSTANCE.get();
            if (current != null) {
                return current;
            }
            current = new Schedulers();
            if (INSTANCE.compareAndSet(null, current)) {
                return current;
            } else {
                current.shutdownInstance();
            }
        }
    }

    private Schedulers() {
        @SuppressWarnings("deprecation")
        RxJavaSchedulersHook hook = RxJavaPlugins.getInstance().getSchedulersHook();

        Scheduler c = hook.getComputationScheduler();
        if (c != null) {
            computationScheduler = c;
        } else {
            computationScheduler = RxJavaSchedulersHook.createComputationScheduler();
        }

        Scheduler io = hook.getIOScheduler();
        if (io != null) {
            ioScheduler = io;
        } else {
            ioScheduler = RxJavaSchedulersHook.createIoScheduler();
        }

        Scheduler nt = hook.getNewThreadScheduler();
        if (nt != null) {
            newThreadScheduler = nt;
        } else {
            newThreadScheduler = RxJavaSchedulersHook.createNewThreadScheduler();
        }
    }

    /**
     * Creates and returns a {@link Scheduler} that executes work immediately on the current thread.
     *
     * @return a {@link Scheduler} that executes work immediately
     */
    public static Scheduler immediate() {
        return rx.internal.schedulers.ImmediateScheduler.INSTANCE;
    }

    /**
     * Creates and returns a {@link Scheduler} that queues work on the current thread to be executed after the
     * current work completes.
     *
     * @return a {@link Scheduler} that queues work on the current thread
     */
    public static Scheduler trampoline() {
        return rx.internal.schedulers.TrampolineScheduler.INSTANCE;
    }

    /**
     * Creates and returns a {@link Scheduler} that creates a new {@link Thread} for each unit of work.
     * <p>
     * Unhandled errors will be delivered to the scheduler Thread's {@link java.lang.Thread.UncaughtExceptionHandler}.
     *
     * @return a {@link Scheduler} that creates new threads
     */
    public static Scheduler newThread() {
        return RxJavaHooks.onNewThreadScheduler(getInstance().newThreadScheduler);
    }

    /**
     * Creates and returns a {@link Scheduler} intended for computational work.
     * <p>
     * This can be used for event-loops, processing callbacks and other computational work.
     * <p>
     * Do not perform IO-bound work on this scheduler. Use {@link #io()} instead.
     * <p>
     * Unhandled errors will be delivered to the scheduler Thread's {@link java.lang.Thread.UncaughtExceptionHandler}.
     *
     * @return a {@link Scheduler} meant for computation-bound work
     */
    public static Scheduler computation() {
        return RxJavaHooks.onComputationScheduler(getInstance().computationScheduler);
    }

    /**
     * Creates and returns a {@link Scheduler} intended for IO-bound work.
     * <p>
     * The implementation is backed by an {@link Executor} thread-pool that will grow as needed.
     * <p>
     * This can be used for asynchronously performing blocking IO.
     * <p>
     * Do not perform computational work on this scheduler. Use {@link #computation()} instead.
     * <p>
     * Unhandled errors will be delivered to the scheduler Thread's {@link java.lang.Thread.UncaughtExceptionHandler}.
     *
     * @return a {@link Scheduler} meant for IO-bound work
     */
    public static Scheduler io() {
        return RxJavaHooks.onIOScheduler(getInstance().ioScheduler);
    }

    /**
     * Creates and returns a {@code TestScheduler}, which is useful for debugging. It allows you to test
     * schedules of events by manually advancing the clock at whatever pace you choose.
     *
     * @return a {@code TestScheduler} meant for debugging
     */
    public static TestScheduler test() { // NOPMD
        return new TestScheduler();
    }

    /**
     * Converts an {@link Executor} into a new Scheduler instance.
     *
     * @param executor
     *          the executor to wrap
     * @return the new Scheduler wrapping the Executor
     */
    public static Scheduler from(Executor executor) {
        return new ExecutorScheduler(executor);
    }

    /**
     * Resets the current {@link Schedulers} instance.
     * This will re-init the cached schedulers on the next usage,
     * which can be useful in testing.
     * @since 1.3
     */
    public static void reset() {
        Schedulers s = INSTANCE.getAndSet(null);
        if (s != null) {
            s.shutdownInstance();
        }
    }

    /**
     * Starts those standard Schedulers which support the SchedulerLifecycle interface.
     * <p>The operation is idempotent and thread-safe.
     */
    public static void start() {
        Schedulers s = getInstance();

        s.startInstance();

        synchronized (s) {
            GenericScheduledExecutorService.INSTANCE.start();
        }
    }
    /**
     * Shuts down those standard Schedulers which support the SchedulerLifecycle interface.
     * <p>The operation is idempotent and thread-safe.
     */
    public static void shutdown() {
        Schedulers s = getInstance();
        s.shutdownInstance();

        synchronized (s) {
            GenericScheduledExecutorService.INSTANCE.shutdown();
        }
    }

    /**
     * Start the instance-specific schedulers.
     */
    synchronized void startInstance() { // NOPMD
        if (computationScheduler instanceof SchedulerLifecycle) {
            ((SchedulerLifecycle) computationScheduler).start();
        }
        if (ioScheduler instanceof SchedulerLifecycle) {
            ((SchedulerLifecycle) ioScheduler).start();
        }
        if (newThreadScheduler instanceof SchedulerLifecycle) {
            ((SchedulerLifecycle) newThreadScheduler).start();
        }
    }

    /**
     * Start the instance-specific schedulers.
     */
    synchronized void shutdownInstance() { // NOPMD
        if (computationScheduler instanceof SchedulerLifecycle) {
            ((SchedulerLifecycle) computationScheduler).shutdown();
        }
        if (ioScheduler instanceof SchedulerLifecycle) {
            ((SchedulerLifecycle) ioScheduler).shutdown();
        }
        if (newThreadScheduler instanceof SchedulerLifecycle) {
            ((SchedulerLifecycle) newThreadScheduler).shutdown();
        }
    }
}
