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

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import rx.*;
import rx.exceptions.Exceptions;
import rx.functions.Action0;
import rx.internal.util.*;
import rx.plugins.*;
import rx.subscriptions.*;

import static rx.internal.util.PlatformDependent.ANDROID_API_VERSION_IS_NOT_ANDROID;

/**
 * Represents a Scheduler.Worker that runs on its own unique and single-threaded ScheduledExecutorService
 * created via Executors.
 */
//NewThreadScheduler 直接使用的是NewThreadWorker
// EventLoopScheduler使用的是其自己继承的子类PoolWorker（然而并没有做什么事情）
// CachedThreadScheduler 使用的是其子类ThreadWorker添加了过期时间的功能

// 该NewThreadWorker只持有线程池，同时负责任务的调度

// 如果是用自己的Executor创建的调度器 则是使用的是ExecutorScheduler
public class NewThreadWorker extends Scheduler.Worker implements Subscription {
    private final ScheduledExecutorService executor;
    volatile boolean isUnsubscribed;
    /** The purge frequency in milliseconds. */
    private static final String FREQUENCY_KEY = "rx.scheduler.jdk6.purge-frequency-millis";
    /** Force the use of purge (true/false). */
    private static final String PURGE_FORCE_KEY = "rx.scheduler.jdk6.purge-force";
    private static final String PURGE_THREAD_PREFIX = "RxSchedulerPurge-";
    private static final boolean SHOULD_TRY_ENABLE_CANCEL_POLICY;
    /** The purge frequency in milliseconds. */
    public static final int PURGE_FREQUENCY;
    private static final ConcurrentHashMap<ScheduledThreadPoolExecutor, ScheduledThreadPoolExecutor> EXECUTORS;
//    仅仅判断是否是第一次启动？
    private static final AtomicReference<ScheduledExecutorService> PURGE;
    /**
     * Improves performance of {@link #tryEnableCancelPolicy(ScheduledExecutorService)}.
     * Also, it works even for inheritance: {@link Method} of base class can be invoked on the instance of child class.
     */
    private static volatile Object cachedSetRemoveOnCancelPolicyMethod;

    /**
     * Possible value of {@link #cachedSetRemoveOnCancelPolicyMethod} which means that cancel policy is not supported.
     */
    private static final Object SET_REMOVE_ON_CANCEL_POLICY_METHOD_NOT_SUPPORTED = new Object();

    static {
        EXECUTORS = new ConcurrentHashMap<ScheduledThreadPoolExecutor, ScheduledThreadPoolExecutor>();
        PURGE = new AtomicReference<ScheduledExecutorService>();
        PURGE_FREQUENCY = Integer.getInteger(FREQUENCY_KEY, 1000);

        // Forces the use of purge even if setRemoveOnCancelPolicy is available
        final boolean purgeForce = Boolean.getBoolean(PURGE_FORCE_KEY);

        final int androidApiVersion = PlatformDependent.getAndroidApiVersion();

        // According to http://developer.android.com/reference/java/util/concurrent/ScheduledThreadPoolExecutor.html#setRemoveOnCancelPolicy(boolean)
        // setRemoveOnCancelPolicy available since Android API 21
        SHOULD_TRY_ENABLE_CANCEL_POLICY = !purgeForce
                && (androidApiVersion == ANDROID_API_VERSION_IS_NOT_ANDROID || androidApiVersion >= 21);
    }
    /**
     * Registers the given executor service and starts the purge thread if not already started.
     * <p>{@code public} visibility reason: called from other package(s) within RxJava
     * @param service a scheduled thread pool executor instance
     */
    public static void registerExecutor(ScheduledThreadPoolExecutor service) {
        do {
            ScheduledExecutorService exec = PURGE.get();
            if (exec != null) {
                break;
            }
            exec = Executors.newScheduledThreadPool(1, new RxThreadFactory(PURGE_THREAD_PREFIX));
            if (PURGE.compareAndSet(null, exec)) {
                exec.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        purgeExecutors();
                    }
                }, PURGE_FREQUENCY, PURGE_FREQUENCY, TimeUnit.MILLISECONDS);

                break;
            } else {
                exec.shutdownNow();
            }
        } while (true);

        EXECUTORS.putIfAbsent(service, service);
    }
    /**
     * Deregisters the executor service.
     * <p>{@code public} visibility reason: called from other package(s) within RxJava
     * @param service a scheduled thread pool executor instance
     */
    public static void deregisterExecutor(ScheduledExecutorService service) {
        EXECUTORS.remove(service);
    }

    /** Purges each registered executor and eagerly evicts shutdown executors. */
    static void purgeExecutors() {/*清理线程池中已经被取消的任务 如果线程池被关闭则移除引用方便垃圾回收*/
        try {
            // This prevents map.keySet to compile to a Java 8+ KeySetView return type
            // and cause NoSuchMethodError on Java 6-7 runtimes.
            Map<ScheduledThreadPoolExecutor, ScheduledThreadPoolExecutor> map = EXECUTORS;
            Iterator<ScheduledThreadPoolExecutor> it = map.keySet().iterator();
            while (it.hasNext()) {
                ScheduledThreadPoolExecutor exec = it.next();
                if (!exec.isShutdown()) {
                    exec.purge();
                } else {
                    it.remove();
                }
            }
        } catch (Throwable t) {
            Exceptions.throwIfFatal(t);
            RxJavaHooks.onError(t);
        }
    }

    /**
     * Tries to enable the Java 7+ setRemoveOnCancelPolicy.
     * <p>{@code public} visibility reason: called from other package(s) within RxJava.
     * If the method returns false, the {@link #registerExecutor(ScheduledThreadPoolExecutor)} may
     * be called to enable the backup option of purging the executors.
     * @param executor the executor to call setRemoveOnCancelPolicy if available.
     * @return true if the policy was successfully enabled
     */
    public static boolean tryEnableCancelPolicy(ScheduledExecutorService executor) {
        if (SHOULD_TRY_ENABLE_CANCEL_POLICY) { // NOPMD
            final boolean isInstanceOfScheduledThreadPoolExecutor = executor instanceof ScheduledThreadPoolExecutor;

            Method methodToCall;

            if (isInstanceOfScheduledThreadPoolExecutor) {
                final Object localSetRemoveOnCancelPolicyMethod = cachedSetRemoveOnCancelPolicyMethod;

                if (localSetRemoveOnCancelPolicyMethod == SET_REMOVE_ON_CANCEL_POLICY_METHOD_NOT_SUPPORTED) {
                    return false;
                }

                if (localSetRemoveOnCancelPolicyMethod == null) {
                    Method method = findSetRemoveOnCancelPolicyMethod(executor);

                    cachedSetRemoveOnCancelPolicyMethod = method != null
                            ? method
                            : SET_REMOVE_ON_CANCEL_POLICY_METHOD_NOT_SUPPORTED;

                    methodToCall = method;
                } else {
                    methodToCall = (Method) localSetRemoveOnCancelPolicyMethod;
                }
            } else {
                methodToCall = findSetRemoveOnCancelPolicyMethod(executor);
            }

            if (methodToCall != null) {
                try {
                    methodToCall.invoke(executor, true);
                    return true;
                } catch (InvocationTargetException e) {
                    RxJavaHooks.onError(e);
                } catch (IllegalAccessException e) {
                    RxJavaHooks.onError(e);
                } catch (IllegalArgumentException e) {
                    RxJavaHooks.onError(e);
                }
            }
        }

        return false;
    }

    /**
     * Tries to find {@code "setRemoveOnCancelPolicy(boolean)"} method in the class of passed executor.
     *
     * @param executor whose class will be used to search for required method.
     * @return {@code "setRemoveOnCancelPolicy(boolean)"} {@link Method}
     * or {@code null} if required {@link Method} was not found.
     */
//    ScheduledThreadPoolExecutor 1.7 之前cancel掉的任务 不会从队列中取消
//    1.7之后提供了一个标志位 被cancel的任务会从队列中移除掉
//    抛出异常 比 遍历所有方法耗时更长
    static Method findSetRemoveOnCancelPolicyMethod(ScheduledExecutorService executor) {
        // The reason for the loop is to avoid NoSuchMethodException being thrown on JDK 6
        // which is more costly than looping through ~70 methods.
        for (final Method method : executor.getClass().getMethods()) {
            if (method.getName().equals("setRemoveOnCancelPolicy")) {
                final Class<?>[] parameterTypes = method.getParameterTypes();

                if (parameterTypes.length == 1 && parameterTypes[0] == Boolean.TYPE) {
                    return method;
                }
            }
        }

        return null;
    }

    /* package */
    public NewThreadWorker(ThreadFactory threadFactory) {
        ScheduledExecutorService exec = Executors.newScheduledThreadPool(1, threadFactory);
        // Java 7+: cancelled future tasks can be removed from the executor thus avoiding memory leak
//        true 则不加入线程Map 1.7以下兼容代码 手动移除队列 避免内存泄露
        boolean cancelSupported = tryEnableCancelPolicy(exec);
        if (!cancelSupported && exec instanceof ScheduledThreadPoolExecutor) {
            registerExecutor((ScheduledThreadPoolExecutor)exec);
        }
        executor = exec;
    }

    @Override
    public Subscription schedule(final Action0 action) {
        return schedule(action, 0, null);
    }

    @Override
    public Subscription schedule(final Action0 action, long delayTime, TimeUnit unit) {
        if (isUnsubscribed) {
            return Subscriptions.unsubscribed();
        }
        return scheduleActual(action, delayTime, unit);
    }

    /**
     * Schedules the given action by wrapping it into a ScheduledAction on the
     * underlying ExecutorService, returning the ScheduledAction.
     * @param action the action to wrap and schedule
     * @param delayTime the delay in execution
     * @param unit the time unit of the delay
     * @return the wrapper ScheduledAction
     */
    public ScheduledAction scheduleActual(final Action0 action, long delayTime, TimeUnit unit) {
        Action0 decoratedAction = RxJavaHooks.onScheduledAction(action);
        ScheduledAction run = new ScheduledAction(decoratedAction);
        Future<?> f;
        if (delayTime <= 0) {
            f = executor.submit(run);
        } else {
            f = executor.schedule(run, delayTime, unit);
        }
        run.add(f);

        return run;
    }
    public ScheduledAction  scheduleActual(final Action0 action, long delayTime, TimeUnit unit, CompositeSubscription parent) {
        Action0 decoratedAction = RxJavaHooks.onScheduledAction(action);
        ScheduledAction run = new ScheduledAction(decoratedAction, parent);
        parent.add(run);

        Future<?> f;
        if (delayTime <= 0) {
            f = executor.submit(run);
        } else {
            f = executor.schedule(run, delayTime, unit);
        }
        run.add(f);

        return run;
    }

    public ScheduledAction scheduleActual(final Action0 action, long delayTime, TimeUnit unit, SubscriptionList parent) {
        Action0 decoratedAction = RxJavaHooks.onScheduledAction(action);
        ScheduledAction run = new ScheduledAction(decoratedAction, parent);
        parent.add(run);

        Future<?> f;
        if (delayTime <= 0) {
            f = executor.submit(run);
        } else {
            f = executor.schedule(run, delayTime, unit);
        }
        run.add(f);

        return run;
    }
//关闭线程池取消注册 直接关闭线程池则可以取消该线程池中已经提交的所有任务
// 与 ExecutorScheduler中ExecutorSchedulerWorker的区别，
// 因为ExecutorSchedulerWorker使用的是外部提供的线程池，因此我们无法也不能直接关闭线程池从而取消已经提交的任务（导致外部的任务被异常取消）
// 因此我们需要追踪或者记录已经提交任务，在取消时只取消已经提交的任务
    @Override
    public void unsubscribe() {
        isUnsubscribed = true;
        executor.shutdownNow();
        deregisterExecutor(executor);
    }

    @Override
    public boolean isUnsubscribed() {
        return isUnsubscribed;
    }
}
