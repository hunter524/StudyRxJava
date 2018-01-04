package huntertest;

import huntertest.util.ClassUtil;
import huntertest.util.CollectionsUtil;
import huntertest.util.ThreadInfoUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import rx.*;
import rx.Observable;
import rx.Observer;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.internal.producers.SingleDelayedProducer;
import rx.internal.schedulers.NewThreadWorker;
import rx.observables.AsyncOnSubscribe;
import rx.observables.SyncOnSubscribe;
import rx.observers.SerializedObserver;
import rx.observers.TestSubscriber;
import rx.plugins.RxJavaHooks;
import rx.schedulers.Schedulers;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class HelloTest {


    static Observable<String> observableJust123 = Observable.just("1", "2", "3");
    static Action1<String> onNextActionPrintThreadInfo = new Action1<String>() {
        @Override
        public void call(String s) {
            ThreadInfoUtil.printThreadInfo("onNextActionPrintThreadInfo:");
            System.out.println("onNextActionPrintThreadInfo:" + s);
        }
    };

    static Action1<String> onNextActionPrintThreadInfoAndSleep2S = new Action1<String>() {
        @Override
        public void call(String s) {
            ThreadInfoUtil.printThreadInfo("onNextActionPrintThreadInfo:");
            System.out.println("onNextActionPrintThreadInfo:" + s);
            ThreadInfoUtil.quietSleepThread(2, TimeUnit.SECONDS);
        }
    };

    static Subscriber<String> nRequestSubScriber = new Subscriber<String>() {
        int lastRequest = 1;
        int onNextTimeEveryRequest = 0;

        @Override
        public void onStart() {
            request(lastRequest);
        }

        @Override
        public void onCompleted() {

        }

        @Override
        public void onError(Throwable e) {

        }

        @Override
        public void onNext(String s) {
            ThreadInfoUtil.printThreadInfo("onNextActionPrintThreadInfo:");
            System.out.println("onNextActionPrintThreadInfo:" + s);
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String formatTime = simpleDateFormat.format(Calendar
                    .getInstance()
                    .getTime());
            System.out.println(formatTime);
            ++onNextTimeEveryRequest;
            if (onNextTimeEveryRequest == lastRequest){
                formatTime = simpleDateFormat.format(Calendar.getInstance().getTime());
                System.out.println("sleep start Time:"+formatTime);
                ThreadInfoUtil.quietSleepThread(2, TimeUnit.SECONDS);
                formatTime = simpleDateFormat.format(Calendar.getInstance().getTime());
                System.out.println("sleep end Time:"+formatTime);
                onNextTimeEveryRequest = 0;
//                如果不request则这条调用链则很快会结束
                request(++lastRequest);
            }

        }
    };


    static class MapFuncJustPrintString implements Func1<String, String> {
        String mPrefix = "";

        public MapFuncJustPrintString(String mPrefix) {
            this.mPrefix = mPrefix;
        }

        @Override
        public String call(String s) {
            String result = mPrefix + s;
            System.out.println(result);
            return result;
        }
    }

    Observable<Integer> just1to7;
    Observable<String> just1to7Str;
    TestSubscriber<Integer> testSubscriberInteger;
    TestSubscriber<String> testSubscriberStr;

    TestSubscriber<String> testSubscriberStrSlowProcced;


    @Before
    public void configRxjava() {
//        设置start hook
//        RxJavaHooks.setOnObservableStart(new Func2<Observable, Observable.OnSubscribe, Observable.OnSubscribe>() {
//            private int callTimes = 0;
//
//            @Override
//            public Observable.OnSubscribe call(Observable observable, Observable.OnSubscribe onSubscribe) {
//                ClassUtil.printClassName(onSubscribe);
//                ClassUtil.printClassName(observable);
//                callTimes++;
//                System.out.println("callTimes:"+callTimes);
//                return onSubscribe;
//            }
//        });

//        设置Computation的Scheduler的最大线程数量（没有地方可以设置最大的Computation的线程数量）
//        System.getProperties().setProperty(EventLoopsScheduler.)
        RxJavaHooks.setOnObservableSubscribeError(new Func1<Throwable, Throwable>() {
            @Override
            public Throwable call(Throwable throwable) {
                Throwable stackTrace = new Throwable();
                System.out.println("=====================call onError Hook=======================");
                stackTrace.printStackTrace();
                System.out.println("=====================call onError Hook=======================");
                return throwable;
            }
        });
    }

    @Before
    public void initObservableAndObserver(){
        just1to7 = Observable.just(1,2,3,4,5,6,7);
        testSubscriberInteger = new TestSubscriber<Integer>(){
            @Override
            public void onNext(Integer integer) {
                super.onNext(integer);
                System.out.println("onNext from test Subscriber:"+String.valueOf(integer));
            }
        };
        testSubscriberStr = new TestSubscriber<String>() {
            @Override
            public void onNext(String s) {
                super.onNext(s);
                System.out.println("onNext from test Subscriber:"+s);
            }
        };

        testSubscriberStrSlowProcced = new TestSubscriber<String>() {
            @Override
            public void onNext(String s) {
                super.onNext(s);
                System.out.println("onNext from test Subscriber:"+s);
                ThreadInfoUtil.printThreadInfo("onNext from test Subscriber");
                ThreadInfoUtil.quietSleepThread(1,TimeUnit.SECONDS);
            }

            @Override
            public void onError(Throwable e) {
                super.onError(e);
                System.out.println("onError message is:"+e.getClass().getCanonicalName());
                e.printStackTrace(System.out);
            }
        };

        just1to7Str = Observable.just("1","2","3","4","5","6","7");
    }

    @Test
    public void analysisJustCallFlow() {
        int sum = 0;
        int month = 5275;
        for (int i = 1; i <= 12; i++) {
            sum = sum + month * i;
        }
        System.out.println(sum / 12 * 15);
    }

    @Test
    public void analysisCreateCallFlow() {
        Subscriber<String> subscriber = new Subscriber<String>() {

            @Override
            public void onStart() {
                ThreadInfoUtil.printThreadInfo("onStart");
                System.out.println("call onStart");
            }

            @Override
            public void onCompleted() {
                ThreadInfoUtil.printThreadInfo("onCompleted");
                System.out.println("call onCompleted!");
            }

            @Override
            public void onError(Throwable e) {
                ThreadInfoUtil.printThreadInfo("onError");
                System.out.println("call onError!");
                e.printStackTrace();
            }

            @Override
            public void onNext(String s) {
                ThreadInfoUtil.printThreadInfo("onNext");
                System.out.println("onNext :" + s);
            }
        };
        Subscription subscription = Observable
                .create(new Observable.OnSubscribe<String>() {
                    @Override
                    public void call(Subscriber<? super String> subscriber) {
                        System.out.println();
                        System.out.println("============================");
                        ClassUtil.printClassName(subscriber, "subscriber @@@  " + subscriber);

                        ThreadInfoUtil.printThreadInfo("call");
                        ClassUtil.printClassName(subscriber);
                        subscriber.onStart();
                        subscriber.onNext("5555");
                        subscriber.onNext("4444");
                        subscriber.onNext("3333");
                        subscriber.onNext("2222");
                        subscriber.onCompleted();
                        System.out.println("============================");
                    }
                })
//                .subscribeOn(Schedulers.io())
//                .map(new Func1<String, String>() {
//                    @Override
//                    public String call(String s) {
//                        return s;
//                    }
//                })
                .subscribe(subscriber);
        ClassUtil.printClassName(subscription, "subscription @@@" + subscription);
        ThreadInfoUtil.quietSleepThread(1, TimeUnit.SECONDS);
    }

    @Test
    public void testUnSubscribeMapChain() {
        final Observable<String> mapChain = Observable
                .create(new Observable.OnSubscribe<String>() {
                    @Override
                    public void call(Subscriber<? super String> subscriber) {
                        for (int i = 0; i < 10; i++) {
                            subscriber.onNext("create:" + i);
                            System.out.println("isInterrupted:" + Thread
                                    .currentThread()
                                    .isInterrupted());
                            if (!Thread
                                    .currentThread()
                                    .isInterrupted()) {
                                ThreadInfoUtil.quietSleepThread(1, TimeUnit.SECONDS);
                            }
                        }
                        subscriber.onCompleted();
                    }
                })
                .map(new Func1<String, String>() {
                    @Override
                    public String call(String s) {
                        System.out.println("map func 1 " + s);
                        return s;
                    }
                })
                .map(new Func1<String, String>() {
                    @Override
                    public String call(String s) {
                        System.out.println("map func 2 " + s);
                        return s;
                    }
                });
        final Subscription[] subscription = new Subscription[1];
        new Thread() {
            @Override
            public void run() {
                Subscriber<String> subscriber = new Subscriber<String>() {
                    @Override
                    public void onCompleted() {
                        System.out.println("onCompleted");
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println("onError:" + e.toString());
                    }

                    @Override
                    public void onNext(String s) {
                        System.out.println("onNext：" + s);
                    }
                };
                subscription[0] = mapChain
                        .subscribeOn(Schedulers.io())
                        .subscribe(subscriber);
                String arrayDeepStr = Arrays.deepToString(subscription);
                System.out.println("arrayDeepStr:" + arrayDeepStr);
//                持有的是包装完的SafeSubscriber
                ClassUtil.printClassName(subscription[0], "subscription@@@@@" + subscription[0]);
                ClassUtil.printClassName(subscriber, "subscriber@@@@@" + subscriber);
            }
        }.start();

        new Thread() {
            @Override
            public void run() {
                ThreadInfoUtil.quietSleepThread(2, TimeUnit.SECONDS);
                subscription[0].unsubscribe();
            }
        }.start();

        ThreadInfoUtil.quietSleepThread(10, TimeUnit.SECONDS);
    }

    @Test
    public void analyseFlatMapOperator() {
        Observable
                .just("1", "2")
                .flatMap(new Func1<String, Observable<?>>() {
                    @Override
                    public Observable<String> call(String s) {
                        List<String> string1 = Arrays.asList("11", "12", "13");
                        List<String> string2 = Arrays.asList("21", "22", "23");
                        if ("1".equals(s)) {
                            return Observable.from(string1);
                        } else {
                            return Observable.from(string2);
                        }

                    }
                })
                .doOnNext(new Action1<Object>() {
                    @Override
                    public void call(Object o) {
                        System.out.println("doOnNext:" + o);
                    }
                })
                .subscribe(new Observer<Object>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onNext(Object o) {
                        System.out.println("Observer onNext" + o);
                    }
                });
    }

    @Test
    public void testMerge() {
        Observable
                .just("1")
                .mergeWith(Observable.just("2"))
                .subscribe(new Action1<String>() {
                    @Override
                    public void call(String s) {
                        System.out.println("merge with call onNext:" + s);
                    }
                });

        Observable
                .merge(Observable.just(1), Observable.just("1"))
                .subscribe(new Action1<Serializable>() {
                    @Override
                    public void call(Serializable serializable) {
                        System.out.println(serializable
                                .getClass()
                                .getCanonicalName());
                    }
                });
//zip是压缩成一个
        Observable
                .zip(Observable.just(1), Observable.just("1"), new Func2<Integer, String, String>() {
                    @Override
                    public String call(Integer integer, String s) {
                        return "S:" + s + "I:" + integer.toString();
                    }
                })
                .subscribe(new Action1<String>() {
                    @Override
                    public void call(String s) {
                        System.out.println("Action call:" + s);
                    }
                });
//merge是合并相同的元素之后 发射它们共同的父类
        Observable
                .merge(Observable.just(BigDecimal.valueOf(200)), Observable.just(BigInteger.valueOf(300)))
                .subscribe(new Action1<Number>() {
                    @Override
                    public void call(Number number) {

                    }
                });
    }

    @Test
    public void ananlyseProducer() {
        Observable
                .range(1, 2000000)
                .map(new Func1<Integer, Integer>() {
                    @Override
                    public Integer call(Integer integer) {
                        System.out.println("map call :" + integer);
                        return integer;
                    }
                })
                .take(2)
                .lift(new Observable.Operator<Boolean, Integer>() {
                    @Override
                    public Subscriber<? super Integer> call(final Subscriber<? super Boolean> subscriber) {
                        return new Subscriber<Integer>() {
                            @Override
                            public void onCompleted() {
                                subscriber.onCompleted();
                            }

                            @Override
                            public void onError(Throwable e) {
                                subscriber.onError(e);
                            }

                            @Override
                            public void onNext(Integer integer) {
                                subscriber.onNext((integer & 1) == 0);
                            }
                        };
                    }
                })
                .take(10)
                .subscribe(new Action1<Boolean>() {
                    @Override
                    public void call(Boolean aBoolean) {
                        System.out.println(aBoolean);
                    }
                });

        Observable
                .just("1")
                .subscribe(new Action1<String>() {
                    @Override
                    public void call(String s) {
                        System.out.println(s);
                    }
                });
    }

    //    为什么都在Computation这个Thread 执行订阅操作
    @Test
    public void analyseSubscribeOnAndFlatMap() {
//        第一次打印之后 sleep 20s 测试 发射者循环始终在一个线程中（处于线程1中执行 2 的发射操作）
        Subscriber<String> subscriber = new Subscriber<String>() {
            boolean alreadySleep = false;

            @Override
            public void onCompleted() {
                System.out.println("onCompleted!");
            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
            }

            @Override
            public void onNext(String s) {
                ThreadInfoUtil.printThreadInfo("Observer s is :" + s + "  Observer Thread is:");
                if (!alreadySleep) {
                    ThreadInfoUtil.quietSleepThread(20, TimeUnit.SECONDS);
                    alreadySleep = true;
                }
                System.out.println();
            }
        };


        Observable
                .just("1", "2")
                .flatMap(new Func1<String, Observable<String>>() {
                    @Override
                    public Observable<String> call(final String s) {
                        if ("1".equals(s)) {
                            return Observable
                                    .interval(1, TimeUnit.SECONDS)
                                    .map(new Func1<Long, String>() {
                                        @Override
                                        public String call(Long aLong) {
                                            ThreadInfoUtil.printThreadInfo("1 map Thread is:");
                                            System.out.println();
                                            return "1 map origin S is:" + s + "time is :" + aLong;
                                        }
                                    })
//                            .take(20)
                                    .subscribeOn(Schedulers.io());
                        } else {
                            return Observable
                                    .interval(1, TimeUnit.SECONDS)
                                    .map(new Func1<Long, String>() {
                                        @Override
                                        public String call(Long aLong) {
                                            ThreadInfoUtil.printThreadInfo("2 map Thread is:");
                                            System.out.println();
                                            return "2 map origin S is:" + s + "time is :" + aLong;
                                        }
                                    })
//                            .take(20)
                                    .subscribeOn(Schedulers.io());
                        }
                    }
                })
                .subscribe(new SerializedObserver<String>(subscriber));
//        阻止测试主线程退出
        ThreadInfoUtil.quietSleepThread(30, TimeUnit.SECONDS);
    }

    @Test
    public void getProperties() {
        Collection<Object> values = System
                .getProperties()
                .values();
        ArrayList<Object> property = new ArrayList<Object>(values);
        Object[] objects = property.toArray();
        String deepToString = Arrays.deepToString(objects);
        System.out.println(deepToString);
    }

    @Test
    public void ananlyseThreadPool() {
        Observable<String> stringObservable = Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                subscriber.onNext("11111");
//                ThreadInfoUtil.quietSleepThread(10,TimeUnit.SECONDS);
                int i = 0;
                while (i < 10) {
                    subscriber.onNext("11111");
                    System.out.println("11111");
                    i++;
                }

            }
        });
        Action1<String> onNext = new Action1<String>() {
            @Override
            public void call(String s) {
            }
        };
        stringObservable
                .subscribeOn(Schedulers.io())
                .subscribe(onNext);
        stringObservable
                .subscribeOn(Schedulers.io())
                .subscribe(onNext);
        stringObservable
                .subscribeOn(Schedulers.io())
                .subscribe(onNext);
        stringObservable
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .map(new Func1<String, String>() {
                    @Override
                    public String call(String s) {
                        ThreadInfoUtil.printThreadInfo("Map:");
                        ThreadInfoUtil.quietSleepThread(10, TimeUnit.SECONDS);
                        return s;
                    }
                })
                .subscribe(onNext);
        Field executors = ClassUtil.quietGetField(NewThreadWorker.class, "EXECUTORS");
        if (null != executors) {
            ConcurrentHashMap<ScheduledThreadPoolExecutor, ScheduledThreadPoolExecutor> inMapExecutors = ClassUtil.quietGetValue(null, executors);
            System.out.println("NewThreadWorker executors size:" + inMapExecutors.size());
        }
        ThreadInfoUtil.quietSleepThread(100, TimeUnit.SECONDS);
    }

    /**
     * Computation的后台线程 默认与Cpu个数相同，当全部占满时其他任务只能等待
     * 有其他任务空闲出线程后才可以执行
     * {@link rx.internal.schedulers.EventLoopsScheduler}
     */
    @Test
    public void analyseComputeScheduler() {
        int processors = Runtime
                .getRuntime()
                .availableProcessors();
        System.out.println("processors :" + processors);
        Observable<String> sleepObservable = observableJust123.map(new Func1<String, String>() {
            @Override
            public String call(String s) {
                ThreadInfoUtil.quietSleepThread(10, TimeUnit.SECONDS);
                return s;
            }
        });
        sleepObservable
                .map(new MapFuncJustPrintString("map 1 "))
                .subscribeOn(Schedulers.computation())
                .subscribe(onNextActionPrintThreadInfo);
        sleepObservable
                .map(new MapFuncJustPrintString("map 2 "))
                .subscribeOn(Schedulers.computation())
                .subscribe(onNextActionPrintThreadInfo);
        observableJust123
                .map(new Func1<String, String>() {
                    @Override
                    public String call(String s) {
                        return "last s";
                    }
                })
                .subscribeOn(Schedulers.computation())
                .subscribe(onNextActionPrintThreadInfo);
        sleepObservable
                .map(new MapFuncJustPrintString("map 3 "))
                .subscribeOn(Schedulers.computation())
                .subscribe(onNextActionPrintThreadInfo);
        sleepObservable
                .map(new MapFuncJustPrintString("map 4 "))
                .subscribeOn(Schedulers.computation())
                .subscribe(onNextActionPrintThreadInfo);
        sleepObservable
                .map(new MapFuncJustPrintString("map 5 "))
                .subscribeOn(Schedulers.computation())
                .subscribe(onNextActionPrintThreadInfo);
        sleepObservable
                .map(new MapFuncJustPrintString("map 6 "))
                .subscribeOn(Schedulers.computation())
                .subscribe(onNextActionPrintThreadInfo);

        ThreadInfoUtil.quietSleepThread(30, TimeUnit.SECONDS);
    }


    @Test
    public void rename() {
        File file = new File("/home/hunter/文档/鹏华");
        File[] files = file.listFiles();
        for (File imgFile : files) {
            if (imgFile
                    .getName()
                    .endsWith(".jpg")) {
                imgFile.renameTo(new File(imgFile
                        .getAbsolutePath()
                        .replace(".jpg", ".png")));
            }
        }
    }


    @Test
    public void testProducerAndRequest() {
        // TODO: 17-12-25 request 和Producer的流程依旧没有分析通透
        Observable
                .create(new Observable.OnSubscribe<String>() {
                    @Override
                    public void call(final Subscriber<? super String> subscriber) {

                        subscriber.setProducer(new Producer() {
                            int emmitItemCount = 0;
                            @Override
                            public void request(long n) {
                                System.out.println("request n:"+n);
                                for (int i = 0; i < n; i++) {
                                    String emmitS = "request n:" + n + "  emmitCount:" + emmitItemCount;
                                    System.out.println(emmitS);
                                    subscriber.onNext(emmitS);
                                    ++emmitItemCount;
                                }
                            }
                        });
                    }
                })
                .subscribe(nRequestSubScriber);

//        Observable
//                .create(new SyncOnSubscribe<Integer, String>() {
//                    //            初始化的状态值
//                    @Override
//                    protected Integer generateState() {
//                        return 0;
//                    }
//
//                    //每次迭代完成之后产生新的状态值 并且更新状态值
//                    @Override
//                    protected Integer next(Integer state, Observer<? super String> observer) {
//                        String nextValue = "Next From Producer: " + Integer.toString(state);
//                        System.out.println(nextValue);
//                        observer.onNext(nextValue);
//                        Integer integer = ++state;
//                        return integer;
//                    }
//                })
//                .subscribeOn(Schedulers.computation())
////                observeOn操作符 默认每次请求128个数据进行缓冲
//                .observeOn(Schedulers.io(), 1)
//                .subscribe(new Subscriber<String>() {
//                    int lastRequested = 1;
//                    int exhausted = 0;
//
//                    @Override
//                    public void onStart() {
//                        super.onStart();
////                        request(lastRequested);
////                        exhausted = 0;
//                    }
//
//                    @Override
//                    public void onCompleted() {
//
//                    }
//
//                    @Override
//                    public void onError(Throwable e) {
//
//                    }
//
//                    @Override
//                    public void onNext(String s) {
//                        System.out.println("onNext:  " + s);
//                        ThreadInfoUtil.quietSleepThread(1, TimeUnit.SECONDS);
//                        exhausted++;
//                        if (exhausted == lastRequested) {
//                            lastRequested = lastRequested * 2;
//                            exhausted = 0;
//                            request(lastRequested);
//                        }
//                    }
//                });

//        Observable
//                .range(1, 200)
//                .map(new Func1<Integer, String>() {
//                    @Override
//                    public String call(Integer integer) {
//                        return Integer.toString(integer);
//                    }
//                })
//                .map(new MapFuncJustPrintString("map:"))
//                .subscribeOn(Schedulers.io()).observeOn(Schedulers.computation()).subscribe(onNextActionPrintThreadInfoAndSleep2S);

//        ThreadInfoUtil.quietSleepThread(30, TimeUnit.SECONDS);
    }

    @Test
    public void ananlyseLiftAndUnSubscribe() {
        final Observable.Operator<String, Integer> int2StringOpWithOutUnSubscribe = new Observable.Operator<String, Integer>() {

            @Override
            public Subscriber<? super Integer> call(final Subscriber<? super String> subscriber) {
                return new Subscriber<Integer>() {
                    @Override
                    public void onCompleted() {
                        subscriber.onCompleted();
                    }

                    @Override
                    public void onError(Throwable e) {
                        subscriber.onError(e);
                    }

                    @Override
                    public void onNext(Integer integer) {
                        System.out.println("call int2StringOp onNext:" + Integer.toString(integer));
                        subscriber.onNext(Integer.toString(integer));
                    }
                };
            }
        };

        final Observable.Operator<String, Integer> int2StringOpWithUnSubscribe = new Observable.Operator<String, Integer>() {

            @Override
            public/*返回给上游的观察者*/ Subscriber<? super Integer> call(final Subscriber<? super String> subscriber/*下游观察者*/) {
//                返回的 Subscriber 传入 下游的订阅关系
//                解注册时是从下游向上游解注册 则该订阅关系并不会丢失 从而可以使上游进行订阅关系的解注册
                return new Subscriber<Integer>(subscriber) {
                    @Override
                    public void onCompleted() {
                        subscriber.onCompleted();
                    }

                    @Override
                    public void onError(Throwable e) {
                        subscriber.onError(e);
                    }

                    @Override
                    public void onNext(Integer integer) {
                        System.out.println("call int2StringOp onNext:" + Integer.toString(integer));
                        subscriber.onNext(Integer.toString(integer));
                    }
                };
            }
        };

//        take 的解订阅操作并没有传递到上一层 lift上游的操作符 依旧在发射数据
        Observable
                .range(0, 200)
                .map(new Func1<Integer, Integer>() {
                    @Override
                    public Integer call(Integer integer) {
                        System.out.println("I am in the up of lift int2String :" + integer);
                        return integer;
                    }
                })
                .lift(int2StringOpWithUnSubscribe)
                .map(new MapFuncJustPrintString("Lift Map: "))
                .take(2)
                .subscribeOn(Schedulers.computation())
                .observeOn(Schedulers.io())
                .subscribe(onNextActionPrintThreadInfo);
        ThreadInfoUtil.quietSleepThread(20, TimeUnit.SECONDS);
    }

    @Test
    public void analyseForgetToRequestMore() {
        Observable.Operator<Integer, Integer> evenFilter = new Observable.Operator<Integer, Integer>() {
            //承接上下游数据转换的任务
            @Override
            public Subscriber<? super Integer> call(final Subscriber<? super Integer> subscriber) {
                return new Subscriber<Integer>(subscriber/*
                 1、如果该处订阅关系没有建立
                 则上游无法取消订阅
                 依旧会发送两个数据 同时调用下游
                 2、如果该处订阅关系建立，同时下游take 了指定数据，忘记request更多数据则会导致无数据

                */) {
                    @Override
                    public void onCompleted() {
                        subscriber.onCompleted();
                    }

                    @Override
                    public void onError(Throwable e) {
                        /*中间订阅者如果不透传调用下游订阅者的 onError则下游始终收不到onError回调*/
//                        e.printStackTrace();
                        // TODO: 17-12-26 为什么此处调用完onError整个调用链就解除注册了？
                        System.out.println("lift onError!");
//                        subscriber.onError(e);
                    }

                    @Override
                    public void onNext(Integer integer) {
                        if ((integer & 0x1) == 0) {
                            subscriber.onNext(integer);
                        } else {
                            request(1);/*如果订阅关系（取消订阅的关系）建立但是遗漏继续request则只获得一个数据*/
                        }
                    }
                };
            }
        };

        Observable.Operator<Integer, Integer> intPrintOperator = new Observable.Operator<Integer, Integer>() {

            @Override
            public Subscriber<? super Integer> call(final Subscriber<? super Integer> subscriber) {
                return new Subscriber<Integer>() {
                    @Override
                    public void onCompleted() {
                        subscriber.onCompleted();
                    }

                    @Override
                    public void onError(Throwable e) {
                        subscriber.onError(e);
                    }

                    @Override
                    public void onNext(Integer integer) {
                        System.out.println("lift Print:"+integer);
                        if (integer == 1){
                            throw new IllegalArgumentException("test throw chain!");
                        }
                        subscriber.onNext(integer);
                    }
                };
            }
        };
        Observable
                .range(1, 2)
                /*map 会解除上游的注册不再发送*/
/*                .map(new Func1<Integer, Integer>() {
                    @Override
                    public Integer call(Integer integer) {
                        if (integer == 1){
                            throw new IllegalArgumentException("test throw!");
                        }
                        return integer;
                    }
                })*/
                .lift(intPrintOperator)
                .lift(evenFilter)
//                .take(1)
                .unsafeSubscribe(new Subscriber<Integer>() {
                    @Override
                    public void onCompleted() {
                        System.out.println("onCompleted");
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                        System.out.println("tail onError!");
                    }

                    @Override
                    public void onNext(Integer integer) {
                        System.out.println("onNext Integer: " + integer);
                    }
                });
    }

    /**
     * 实际测试 requestMore 0 则 不会收到数据
     *         requestMore 1 则 会收到1个数据
     *
     *         0 两次request 最后只收到一个数据
     *         1 两个request 最后也只收到一个数据
     *
     * 以上结论 与 https://blog.piasy.com/AdvancedRxJava/2016/06/04/operator-concurrency-primitives-4/
     * 不符合
     *
     */
    @Test
    public void testjustSingleProducer(){
        Observable<String> source = Observable.just("1");
        TestSubscriber<String> testSubscriber = new TestSubscriber<String>();
        testSubscriber.requestMore(1);

        source.unsafeSubscribe(testSubscriber);

        List<String> onNextEvents = testSubscriber.getOnNextEvents();
        String s = Arrays.deepToString(onNextEvents.toArray());
        System.out.println(s);
        testSubscriber.unsubscribe();

        System.out.println("-----");

        source.unsafeSubscribe(testSubscriber);
        List<String> onNextEvents1 = testSubscriber.getOnNextEvents();
        String s1 = Arrays.deepToString(onNextEvents1.toArray());
        System.out.println(s1);

        Observable<String> singleDelayObservable = Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                /*解决set数据 和 request 的竟态条件的关系 setValue 和 request（setProducer 操作）
                 处于两个线程 保证始终有一个会执行发射操作
                 同时保证了执行一次发射之后不会再执行发射操作
                 竟态条件解决发射数据是哪个线程的责任的问题*/
                final SingleDelayedProducer<String> delayedProducer = new SingleDelayedProducer<String>(subscriber);
                System.out.println("singleProducer submit time:"+Calendar.getInstance());
                Schedulers.io().createWorker().schedule(new Action0() {
                            @Override
                            public void call() {
                                System.out.println();
                                System.out.println("Start Sleep:"+Calendar.getInstance().toString());
                                ThreadInfoUtil.quietSleepThread(1, TimeUnit.SECONDS);
                                System.out.println("End Sleep:"+Calendar.getInstance().toString());
//                                此处 NRNV HRNV 转变HV（has value）
                                Assert.assertEquals(delayedProducer.get(),0);/*NRNV*/ /*step1*/
                                delayedProducer.setValue("ss");
                                Assert.assertEquals(delayedProducer.get(),1);/*HRHV*/ /*step3 emit*/

                            }
                        });
//                此处会去调用request NRNV NRHV 到HR（has request 的转换）
                subscriber.setProducer(delayedProducer);
                Assert.assertEquals(delayedProducer.get(),2);/*HRNV*/ /*step2*/
//                最终当转换成HRHV的时候则执行发射操作
            }
        });

        Action1<String> onNext = new Action1<String>() {
            @Override
            public void call(String s) {
                System.out.println();
                System.out.println("onNextTime:" + Calendar
                        .getInstance()
                        .toString());
                ThreadInfoUtil.printThreadInfo("onNext:=> s is " + s);
            }
        };
        singleDelayObservable.subscribe(onNext);
// 订阅两次不会执行发射操作

        ThreadInfoUtil.quietSleepThread(5,TimeUnit.SECONDS);
    }

    @Test
    public void numOverFlow() {
        int maxValue = Integer.MAX_VALUE;
        int x = ++maxValue;
        System.out.println(x);
        System.out.println(Integer.toBinaryString(x));
    }

    //=======================main 方法===============================
    public static void main(String[] args) {
//        amb多个Observable谁先发射 则发射完第一个发射的Observable的所有序列

// 延迟 1s发射第一个数据
        Observable<Integer> integerObservableDelay1s = Observable
                .create(new Observable.OnSubscribe<Integer>() {
                    @Override
                    public void call(Subscriber<? super Integer> subscriber) {
                        ThreadInfoUtil.quietSleepThread(1,TimeUnit.SECONDS);
                        int numBase = 1000;
                        for (int i = 0; i < 10; i++) {
                            subscriber.onNext(numBase + i);
                        }
//                        subscriber.onCompleted();
                    }
                })
                .delay(0,TimeUnit.SECONDS).subscribeOn(Schedulers.io());
// 延迟 2s发射第一个数据
        Observable<Integer> integerObservableDelay2S = Observable
                .create(new Observable.OnSubscribe<Integer>() {
                    @Override
                    public void call(Subscriber<? super Integer> subscriber) {
//                        amb会在第一个Observable发送数据之后cancel 当前（第二个Observable）导致 interruptedException
//                        cancel会立即调用cancel interrupted
                        ThreadInfoUtil.quietSleepThread(2,TimeUnit.SECONDS);
                        int numBase = 2000;
                        for (int i = 0; i < 10; i++) {
                            subscriber.onNext(numBase + i);
                        }
//                        subscriber.onCompleted();
                    }
                })
                .delay(0,TimeUnit.SECONDS).subscribeOn(Schedulers.io());

        Observable.amb(integerObservableDelay1s,integerObservableDelay2S).subscribe(new Action1<Integer>() {
            @Override
            public void call(Integer integer) {
                System.out.println("onNext is:"+integer);
            }
        });

        ThreadInfoUtil.quietSleepThread(3,TimeUnit.SECONDS);
    }

    static class UnsafeClass {
        public int n;

        public void assertsN() {
            if (n != n) {/* Doug Lea java并发编程书说该处存在问题*/
                throw new IllegalStateException("n !- n");
            }
        }
    }

    static void mainTestUnSafe(){
        final UnsafeClass unsafeClass = new UnsafeClass();
        new Thread() {
            @Override
            public void run() {
                while (true) {
                    ++unsafeClass.n;
                }
            }
        }.start();
        new Thread() {
            @Override
            public void run() {
                while (true) {
                    unsafeClass.assertsN();
                }
            }
        }.start();
    }

    /**
     *
     */
    // TODO: 18-1-2 hunter 初步了解 request和设置Producer的流程 后面需要将该流程进行细化分析
    @Test(timeout = 60000)/*强制退出避免后台占用资源*/
    public void analyseProducerAndRequestAgain(){
        Observable<String> asyncObservable = Observable.create(new AsyncOnSubscribe<Integer, String>() {

            @Override
            protected Integer generateState() {
                return 1;
            }
//observer 的onNext只能调用一次 AsyncOnSubscribe 调用则会出现该种状态
            @Override
            protected Integer next(Integer state, long requested, Observer<Observable<? extends String>> observer) {
                System.out.println("asyncObservable requested :"+requested);
                List<String> listStr = new LinkedList<String>();
                System.out.println("call onNext requested:"+requested/*+" current:"+i*/);
                for (int i = 0; i < requested; i++) {
                    String requestedValue = "|" + state + " i:" + i;
                    listStr.add(requestedValue);
                }
                observer.onNext(Observable.from(listStr));
                return ++state;
            }
        });

//        asyncObservable.subscribeOn(Schedulers.computation()).subscribeOn(Schedulers.io()).subscribe(testSubscriberStr);

//        预期由于Computation默认请求128 预期onBackpressureBuffer会请求两次
//        实际请求了long的最大值
//        onBackPressureBuffer 超出下面线程的线程的消费能力的时候默认会 抛出异常 但是存在其他异常
//        使用溢出时放弃最新数据依然会导致订阅链抛出异常被终止订阅
        asyncObservable
//
                .subscribeOn(Schedulers.computation())
//                该操作符每次请求最大个数 Long的最大值
//                .onBackpressureBuffer(256, new Action0() {
//                    @Override
//                    public void call() {
//                        System.out.println("over flow!");
//                    }
//                }, BackpressureOverflow.ON_OVERFLOW_DROP_LATEST)

//该操作符会首次默认请求128个元素 缓存128个元素 后面会请求 128 - 128/4 = 96个元素进行缓存 ObserveOnSubscriber
                .observeOn(Schedulers.io())
                .subscribe(new Subscriber<String>() {

                    @Override
                    public void onStart() {
                        super.onStart();
                        System.out.println("call subscriber onStart！ request 1");
                        request(1);
                    }

                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println("onError message:"+e.getMessage());
                    }

                    @Override
                    public void onNext(String s) {
                        System.out.println("Lazy subscriber onNext s:" + s);
                        ThreadInfoUtil.quietSleepThread(100, TimeUnit.MILLISECONDS);
//                        如果此处不再请求数据则调用链 会停止发送数据 但是调用链未触发onError 或者onCompleted 导致调用链结束
                        request(1);
                    }

                    @Override
                    public void setProducer(Producer p) {
//                        该处设置请求 1 个元素 如果不设置则一次会请求 最大的元素进行发射
//                        设置当前Subscriber的时候默认不请求元素，当订阅者开始调用start发送数据时请求 1个数据 每次onNext请求结束后请求一个数据
                        System.out.println("call subscriber setProducer！ request 0");
                        request(0);
                        super.setProducer(p);
                    }
                });

        ThreadInfoUtil.quietSleepThread(60,TimeUnit.SECONDS);
        System.exit(0);
    }

    /**
     * call chain 保证了对象发布的线程安全性 当调用下一个操作符合时 上一个操作符完全执行完毕不再调用
     */
    @Test
    public void analysedoOnSubscribe(){
        just1to7.doOnSubscribe(new Action0() {
            @Override
            public void call() {
                System.out.println("call doOnSubscribe!");
            }
        }).doOnNext(new Action1<Integer>() {
            @Override
            public void call(Integer integer) {
                System.out.println("call doOnNext first:"+integer);
            }
        }).map(new Func1<Integer, Integer>() {
            @Override
            public Integer call(Integer integer) {
                return integer*2;
            }
        }).doOnNext(new Action1<Integer>() {
            @Override
            public void call(Integer integer) {
                System.out.println("call doOnNext second:"+integer);
            }
        }).subscribe(testSubscriberInteger);
    }

    @Test
    public void analyseZipBackPressure(){
//        使用同步SyncOnSubscribe是安全的
        SyncOnSubscribe<String, String> syncOnSubscribe = new SyncOnSubscribe<String, String>() {
            int i = 0;

            @Override
            protected String generateState() {
                return "infinite";
            }

            @Override
            protected String next(String state, Observer<? super String> observer) {
                observer.onNext(state + (++i));
                return state;
            }
        };
//        使用自定义的OnSubscribe 则会触发背压检测
//        背压的定义是：生产者的生产速度大于消费者的消费速度，使中转容器容量满了
        Observable.OnSubscribe<String> infiniteOnSubscribe = new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
//                使用该种方式会导致上游一直发送数据 无法停止 即使下游已经由于背压问题抛出异常导致解除订阅了
                int i = 0;
                while (true) {
                    ++i;
                    subscriber.onNext("i:" + i);
                }
            }
        };
        Observable<String> infinityObservable = Observable.create(infiniteOnSubscribe);
//        预期会报出 MissingBackpressureException异常
        Observable
                .zip(infinityObservable, infinityObservable, new Func2<String, String, String>() {
                    @Override
                    public String call(String s, String s2) {
                        return s + s2;
                    }
                })
                .subscribeOn(Schedulers.computation())
//                太大会导致 ConcurrentCircularArrayQueue 堆溢出
                .observeOn(Schedulers.io(),Integer.MAX_VALUE>>6)
                .subscribe(testSubscriberStrSlowProcced);

        ThreadInfoUtil.quietSleepThread(20,TimeUnit.SECONDS);
    }

    @Test
    public void testProducerArbiter(){
        TestSubscriber<Integer> firstSubscriber = new TestSubscriber<Integer>();
        Observable<Integer> source = Observable
                .range(1, 10)
                .lift(new ThenOperatorError<Integer>(Observable.range(11, 90)));
        source.subscribe(firstSubscriber);
        System.out.println("=== first Subscriber ===");
        CollectionsUtil.printList(firstSubscriber.getOnNextEvents());

        System.out.println("=== second Subscriber ===");
        TestSubscriber<Integer> sencondSubsriber = new TestSubscriber<Integer>();
//        如果直接request 20 会导致两个Observable都发送 20个数据 第一个Observable由于只有10个数据 因此只发送10个数据？
//        第二个Observable 会发送20个数据
        sencondSubsriber.requestMore(20);
        source.subscribe(sencondSubsriber);
        CollectionsUtil.printList(sencondSubsriber.getOnNextEvents());


    }


    @Test
    public void lambdaTest(){
        new Thread(()->{
            System.out.println("success java unit test lambda!");
        }).start();
        ThreadInfoUtil.quietSleepThread(1,TimeUnit.SECONDS);
    }

    class ThenOperatorError<T> implements Observable.Operator<T,T>{

        private Observable<? extends T> other;

        public ThenOperatorError(Observable<? extends T> other) {
            this.other = other;
        }

        @Override
        public Subscriber<? super T> call(final Subscriber<? super T> subscriber) {
            Subscriber<T> parent = new Subscriber<T>() {
                @Override
                public void onCompleted() {
                    other.subscribe(subscriber);
                }

                @Override
                public void onError(Throwable e) {
                    subscriber.onError(e);
                    unsubscribe();
                }

                @Override
                public void onNext(T t) {
                    subscriber.onNext(t);
                }
            };
            subscriber.add(parent);
            return parent;
        }
    }
}
