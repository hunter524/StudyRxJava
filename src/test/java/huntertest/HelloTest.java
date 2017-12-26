package huntertest;

import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.internal.schedulers.NewThreadWorker;
import rx.observables.SyncOnSubscribe;
import rx.observers.SerializedObserver;
import rx.plugins.RxJavaHooks;
import rx.schedulers.Schedulers;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Field;
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
        @Override
        public void onStart() {
            super.onStart();
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
            ThreadInfoUtil.quietSleepThread(2, TimeUnit.SECONDS);
            request(2);
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
//        Observable
//                .create(new Observable.OnSubscribe<String>() {
//                    @Override
//                    public void call(final Subscriber<? super String> subscriber) {
//
//                        subscriber.setProducer(new Producer() {
//                            int emmitItemCount = 0;
//                            @Override
//                            public void request(long n) {
//                                System.out.println("request n:"+n);
//                                for (int i = 0; i < n; i++) {
//                                    String emmitS = "request n:" + n + "  emmitCount:" + emmitItemCount;
//                                    System.out.println(emmitS);
//                                    subscriber.onNext(emmitS);
//                                    emmitItemCount++;
//                                }
//                            }
//                        });
//                    }
//                })
//                .subscribeOn(Schedulers.io())
//                .observeOn(Schedulers.computation())
//                .subscribe(nRequestSubScriber);

        Observable
                .create(new SyncOnSubscribe<Integer, String>() {
                    //            初始化的状态值
                    @Override
                    protected Integer generateState() {
                        return 0;
                    }

                    //每次迭代完成之后产生新的状态值 并且更新状态值
                    @Override
                    protected Integer next(Integer state, Observer<? super String> observer) {
                        String nextValue = "Next From Producer: " + Integer.toString(state);
                        System.out.println(nextValue);
                        observer.onNext(nextValue);
                        Integer integer = ++state;
                        return integer;
                    }
                })
                .subscribeOn(Schedulers.computation())
//                observeOn操作符 默认每次请求128个数据进行缓冲
                .observeOn(Schedulers.io(), 1)
                .subscribe(new Subscriber<String>() {
                    int lastRequested = 1;
                    int exhausted = 0;

                    @Override
                    public void onStart() {
                        super.onStart();
//                        request(lastRequested);
//                        exhausted = 0;
                    }

                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onNext(String s) {
                        System.out.println("onNext:  " + s);
                        ThreadInfoUtil.quietSleepThread(1, TimeUnit.SECONDS);
                        exhausted++;
                        if (exhausted == lastRequested) {
                            lastRequested = lastRequested * 2;
                            exhausted = 0;
                            request(lastRequested);
                        }
                    }
                });

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

        ThreadInfoUtil.quietSleepThread(30, TimeUnit.SECONDS);
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


    @Test
    public void numOverFlow() {
        int maxValue = Integer.MAX_VALUE;
        int x = ++maxValue;
        System.out.println(x);
        System.out.println(Integer.toBinaryString(x));
    }

    //=======================main 方法===============================
    public static void main(String[] args) {
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
       Collections.synchronizedList(new ArrayList<String>());
    }

    static class UnsafeClass {
        public int n;

        public void assertsN() {
            if (n != n) {
                throw new IllegalStateException("n !- n");
            }
        }
    }
}
