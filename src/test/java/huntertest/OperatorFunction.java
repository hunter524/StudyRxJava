package huntertest;

import huntertest.util.CollectionsUtil;
import huntertest.util.ThreadInfoUtil;
import org.junit.Before;
import org.junit.Test;
import rx.*;
import rx.functions.*;
import rx.internal.producers.SingleProducer;
import rx.observables.AsyncOnSubscribe;
import rx.observables.ConnectableObservable;
import rx.observables.SyncOnSubscribe;
import rx.observers.Observers;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;
import rx.subjects.AsyncSubject;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;
import rx.subscriptions.Subscriptions;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 为了理解每个操作符的用处
 */
public class OperatorFunction {


    Observable<Integer> just1to7;
    Observable<String> just1to7Str;
    TestSubscriber<Integer> testSubscriberInteger;
    private TestSubscriber<String> testSubscriberStr;

    @Before
    public void initObservableAndObserver() {
        just1to7 = Observable.just(1, 2, 3, 4, 5, 6, 7);
        testSubscriberInteger = new TestSubscriber<Integer>() {
            @Override
            public void onNext(Integer integer) {
                super.onNext(integer);
                System.out.println("onNext from test Subscriber:" + String.valueOf(integer));
            }
        };
        testSubscriberStr = new TestSubscriber<String>() {
            @Override
            public void onNext(String s) {
                super.onNext(s);
                System.out.println("onNext from test Subscriber:" + s);
            }
        };

        just1to7Str = Observable.just("1", "2", "3", "4", "5", "6", "7");
    }

    /**
     * 多个Observable 发射数据有延迟，取最早发射数据的那个Observable然后发送该Observable的所有数据
     * 解除订阅其他的Observable
     * <p>
     * 如果多个数据发送的Observable不是同一种类型，则发射他们共同的父类
     * <p>
     * amb 和 ambWith
     */
    @Test
    public void amb() {
        SyncOnSubscribe<Integer, String> syncOnSubscribe = new SyncOnSubscribe<Integer, String>() {
            @Override
            protected Integer generateState() {
                return 0;
            }

            @Override
            protected Integer next(Integer state, Observer<? super String> observer) {
                ThreadInfoUtil.printThreadInfo("syncOnSubscribe ThreadInfo:");
                observer.onNext("first observable:" + state + "  Produce onNext Thread Info:" + ThreadInfoUtil.getThreadInfo());
                if (10 == state) {
                    observer.onCompleted();
                }
                return ++state;
            }
        };

        Observable<String> delay1sObservable = Observable
                .create(syncOnSubscribe)
                .delay(1, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.io(), true);

        Observable<String> delay2sObservable = Observable
                .create(syncOnSubscribe)
                .delay(2, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.computation(), true);

        Action1<String> onNext = new Action1<String>() {
            @Override
            public void call(String s) {
                System.out.println("onNext:" + s);
            }
        };

        Observable
                .amb(delay1sObservable, delay2sObservable)
                .subscribe(onNext);


        Observable<Integer> delay1sInteger = Observable
                .create(new SyncOnSubscribe<Integer, Integer>() {
                    @Override
                    protected Integer generateState() {
                        return 0;
                    }

                    @Override
                    protected Integer next(Integer state, Observer<? super Integer> observer) {
                        observer.onNext(state);
                        if (10 == state) {
                            observer.onCompleted();
                        }
                        return ++state;
                    }
                })
                .delay(1, TimeUnit.SECONDS);

        Observable<BigDecimal> delay2sBigDecimal = Observable
                .create(new SyncOnSubscribe<BigDecimal, BigDecimal>() {
                    @Override
                    protected BigDecimal generateState() {
                        return BigDecimal.ZERO;
                    }

                    @Override
                    protected BigDecimal next(BigDecimal state, Observer<? super BigDecimal> observer) {
                        observer.onNext(state);
                        if (BigDecimal
                                .valueOf(10)
                                .compareTo(state) == 0) {
                            observer.onCompleted();
                        }
                        return state.add(BigDecimal.valueOf(1));
                    }
                })
                .delay(2, TimeUnit.SECONDS);

        Observable
                .amb(delay1sInteger, delay2sBigDecimal)
                .subscribe(new Action1<Number>() {
                    //            找到共同的父类 实际返回的是Integer的类
                    @Override
                    public void call(Number number) {
                        System.out.println("number is:" + number.longValue());
                        System.out.println("number type is :" + number.getClass());
                    }
                });

        ThreadInfoUtil.printThreadInfo("subscribe End ");
        ThreadInfoUtil.quietSleepThread(3, TimeUnit.SECONDS);
    }

    @Test
    public void buffer() {
//        Observable.range(0,200).buffer(new Func0<Observable<?>>() {
//            @Override
//            public Observable<?> call() {
//                return ;
//            }
//        })
    }

    /**
     * 将一个Observable发射的多个同一类型的数据转换成为Observable只发射该数据集合的List操作
     */
    @Test
    public void toList() {
        Observable
                .just(1, 2, 3, 4)
                .toList()
                .subscribe(new Action1<List<Integer>>() {
                    @Override
                    public void call(List<Integer> integers) {
                        Object[] objects = integers.toArray();
                        String array = Arrays.deepToString(objects);
                        System.out.println("toList :" + array);
                    }
                });
    }

    @Test
    public void toMap() {
        Observable
                .just("a", "bb", "ccc", "dddd")
                .toMap(new Func1<String, Integer>() {
                    @Override
                    public Integer call(String s) {
                        return s.length();
                    }
                }, new Func1<String, String>() {
                    @Override
                    public String call(String s) {
                        return s + s;
                    }
                })
                .subscribe(new Action1<Map<Integer, String>>() {
                    @Override
                    public void call(Map<Integer, String> integerStringMap) {
//                此处应该是HashMap
                        System.out.println("integerStringMap is :" + integerStringMap
                                .getClass()
                                .getCanonicalName());
//                打印map的内容
                        System.out.println("integerStringMap content is:" + integerStringMap.toString());

                    }
                });
    }

    @Test
    public void takeXXX() {
        Observable<Integer> just1To7 = Observable.just(1, 2, 3, 4, 5, 6, 7);
        TestSubscriber<Integer> subscriber = new TestSubscriber<Integer>();
        just1To7
                .takeUntil(new Func1<Integer, Boolean>() {
                    @Override
                    public Boolean call(Integer integer) {
                        return integer == 4;
                    }
                })
                .subscribe(subscriber);
        List<Integer> onNextEvents = subscriber.getOnNextEvents();
        CollectionsUtil.printList(onNextEvents);
    }

    @Test
    public void take() {
        Observable<String> delayed = just1to7Str
                .map(new Func1<String, String>() {
                    @Override
                    public String call(String s) {
                        return s + ":map";
                    }
                })
                .delay(2, TimeUnit.SECONDS);
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        Observable
                .mergeDelayError(delayed, just1to7Str)
                .take(2, TimeUnit.SECONDS)
                .subscribe(subscriber);
        List<String> onNextEvents = subscriber.getOnNextEvents();
//        预期延迟两秒发射的元素均无法获取到
        CollectionsUtil.printList(onNextEvents);
    }

    /**
     * 每次获得一次数据将数据累加之后返回
     * 预期结果 1|2|3|4|5|6|7
     */
    @Test
    public void reduce() {
        just1to7Str
                .reduce(new Func2<String, String, String>() {
                    @Override
                    public String call(String s, String s2) {
                        return s + "|" + s2;
                    }
                })
                .subscribe(testSubscriberStr);
    }

    @Test
    public void concatWith() {
        Observable
                .just(1)
                .concatWith(Observable.just(2))
                .subscribe(testSubscriberInteger);
    }

    @Test
    // TODO: 18-1-3 流程分析 总是绕晕了！！！
//    适用于将结果进行摊平的场景
    public void flatMap() {
        List<Integer> integers1 = Arrays.asList(1, 11, 111, 1111, 11111);
        List<Integer> integers2 = Arrays.asList(2, 22, 222, 2222, 22222);
        TestSubscriber<Integer> testSubscriber = new TestSubscriber<Integer>();
//        发射 初始的list 取得结果 再发射 将 多个Observable进行merge操作
//        先map 再merge
        Observable
                .just(integers1, integers2)
                .flatMap(new Func1<List<Integer>, Observable<Integer>>() {
                    @Override
                    public Observable<Integer> call(List<Integer> integers) {
                        return Observable.from(integers);
                    }
                })
                .subscribe(testSubscriber);
        List<Integer> onNextEvents = testSubscriber.getOnNextEvents();
        CollectionsUtil.printList(onNextEvents);
    }


    @Test
    public void switchOnNext() {
//        Observable.switchOnNext()
    }

    @Test
    public void observableDefer() {
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        Observable
                .defer(() -> Observable.just("1", "2"))
                .subscribe(subscriber);
        List<String> onNextEvents = subscriber.getOnNextEvents();
        CollectionsUtil.printList(onNextEvents);
    }

    @Test
    public void merge() {
        Observable<String> str1 = Observable
                .just("1")
                .delay(1, TimeUnit.SECONDS);
        Observable<String> str2 = Observable
                .just("2")
                .delay(3, TimeUnit.SECONDS);
        Observable<String> str3 = Observable
                .just("3")
                .delay(5, TimeUnit.SECONDS)
                .timeout(4, TimeUnit.SECONDS);
        Observable<String> str4 = Observable
                .just("4")
                .delay(2, TimeUnit.SECONDS);
        Observable<String> str5 = Observable
                .just("5")
                .delay(4, TimeUnit.SECONDS);
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        Observable
                .mergeDelayError(str1, str2, str3, str4, str5)
                .subscribe(subscriber);


        ThreadInfoUtil.quietSleepThread(6, TimeUnit.SECONDS);
        //        预期只有 1 4 2 5
        CollectionsUtil.printList(subscriber.getOnNextEvents());
//        预期有一个TimeOutException
        List<Throwable> onErrorEvents = subscriber.getOnErrorEvents();
        CollectionsUtil.printList(onErrorEvents);
    }

    @Test
    public void single() {
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        Single
                .just("1")
                .subscribe(subscriber);
        List<String> onNextEvents = subscriber.getOnNextEvents();
        CollectionsUtil.printList(onNextEvents);

        Single
                .just("1")
                .concatWith(Observable
                        .just("2")
                        .toSingle());

    }

    @Test
    public void subject() {
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        AsyncSubject<String> strAsynSubj = AsyncSubject.create();/*AsyncSubject 只发射其订阅者的最后一个数据*/
        Subject
                .just("1", "2", "3")
                .subscribe(strAsynSubj);
        strAsynSubj.subscribe(subscriber);

        List<String> onNextEvents = subscriber.getOnNextEvents();

        CollectionsUtil.printList(onNextEvents);

    }

    /**
     * 异步的Observable强制转换成同步 阻塞的Observable 内部还有各种策略保证异步相同步的转换
     */
    @Test
    public void toBlocking() {
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        just1to7Str
                .toBlocking()
                .first(new Func1<String, Boolean>() {
                    @Override
                    public Boolean call(String s) {
                        System.out.println("call s:" + s);
                        return s.equals("7");
                    }
                });
//        阻塞到数据全部发送完成 或者 抛出异常
        just1to7Str
                .toBlocking()
                .forEach(new Action1<String>() {
                    @Override
                    public void call(String s) {

                    }
                });
        List<String> onNextEvents = subscriber.getOnNextEvents();
        CollectionsUtil.printList(onNextEvents);

        /*转换成上游的Single*//*上游的Single*/
        Single
                .just("1")
                .map(value -> "2")
                .flatMap(value -> /*Observable.just("3").mergeWith(Observable.just("4")).toSingle()*/Single.just("3"))
                .compose(stringSingle -> {
                    String value = stringSingle
                            .toBlocking()
                            .value();

                    return Single.just(Integer.valueOf(value) + 100);
                })
                .subscribe(new SingleSubscriber<Integer>() {
                    @Override
                    public void onSuccess(Integer integer) {
                        System.out.println("Single s 1 compose to Integer:" + integer.toString());
                    }

                    @Override
                    public void onError(Throwable error) {

                    }
                });

        Single
                .just("1")
                .zipWith(Single.just("2"), (a, b) -> {
                    return a + b;
                })
                .subscribe(new SingleSubscriber<String>() {
                    @Override
                    public void onSuccess(String s) {
                        System.out.println(s);
                    }

                    @Override
                    public void onError(Throwable error) {

                    }
                });

    }

    /**
     * 预期有顺序的链接观察者 即使不同观察者的发射顺序是未知的
     * 异步操作符可能什么也没接受到就已经结束了
     * 链接两个操作时订阅者 不切换线程 则默认在concat的线程执行onXXX操作
     */
    @Test
    public void concat() {
        Observable<String> delay1 = just1to7Str.delay(1, TimeUnit.SECONDS);
        Observable<String> delay2 = just1to7Str
                .map(value -> value + "2")
                .delay(2, TimeUnit.SECONDS);
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        delay2
                .concatWith(delay1)
                .subscribe(subscriber);
        List<String> onNextEvents = subscriber.getOnNextEvents();

        ThreadInfoUtil.quietSleepThread(3100, TimeUnit.MILLISECONDS);
//        预期 12 22 32 42 52 62 72 1 2 3 4 5 6 7
        String lastThread = subscriber
                .getLastSeenThread()
                .toString();
        System.out.println("lastThread:" + lastThread);
        CollectionsUtil.printList(onNextEvents);
    }

    @Test
    public void fromXXX() {
        TestSubscriber<String> callableSubscriber = new TestSubscriber<>();
        Observable
                .fromCallable(() -> "from callable!")
                .subscribe(callableSubscriber);
        List<String> onNextEvents1 = callableSubscriber.getOnNextEvents();
        CollectionsUtil.printList(onNextEvents1);

        TestSubscriber<String> subscriberSyncOnSubscribe = new TestSubscriber<>();
        Observable
                .create(new SyncOnSubscribe<Integer, String>() {
                    @Override
                    protected Integer generateState() {
                        return Integer.valueOf(1);
                    }

                    @Override
                    protected Integer next(Integer state, Observer<? super String> observer) {
                        observer.onNext(state.toString());
                        if (state == 10) {
                            observer.onCompleted();
                        }
                        return ++state;
                    }
                })
                .subscribe(subscriberSyncOnSubscribe);
        List<String> onNextEvents = subscriberSyncOnSubscribe.getOnNextEvents();
        CollectionsUtil.printList(onNextEvents);
    }


    @Test
    public void analyseCallChain() {
        TestSubscriber<Object> subscriber1 = new TestSubscriber<>();
        Observable
                .just("1")
                .map(str -> str + str)
                .lift(subscriber -> new Subscriber<String>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onNext(String s) {
                        SingleProducer<String> stringSingleProducer = new SingleProducer<>(subscriber, "5555");
                        subscriber.setProducer(stringSingleProducer);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .subscribe(subscriber1);

        ThreadInfoUtil.quietSleepThread(3, TimeUnit.SECONDS);
        List<Object> onNextEvents = subscriber1.getOnNextEvents();
//        预期应该只有5555
        CollectionsUtil.printList(onNextEvents);
    }


    @Test
    public void analyseZip() {
        Observable<Integer> just1Delay1 = Observable
                .just(1, 1, 1)
                .delay(3, TimeUnit.SECONDS)
                .observeOn(Schedulers.computation());

        Observable<Integer> just2Delay2 = Observable
                .just(2, 2)
                .delay(2, TimeUnit.SECONDS)
                .observeOn(Schedulers.io());

        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        Observable
                .zip(just1Delay1, just2Delay2, new Func2<Integer, Integer, Integer>() {
                    @Override
                    public Integer call(Integer integer, Integer integer2) {
//                ！！！预期在延迟时间最长的线程
                        ThreadInfoUtil.printThreadInfo("zip func:");
                        return integer + integer2;
                    }
                })
                .observeOn(Schedulers.io())
                .subscribe(subscriber);
        ThreadInfoUtil.quietSleepThread(3, TimeUnit.SECONDS);

        List<Integer> onNextEvents = subscriber.getOnNextEvents();
        CollectionsUtil.printList(onNextEvents);/*预期持有的元素为待合并的最少的元素*/

    }

    @Test
    public void analyseBackPressure() {
        Observable
                .create(new Observable.OnSubscribe<String>() {
                    @Override
                    public void call(Subscriber<? super String> subscriber) {
                        Integer i = 0;
                        while (true) {
                            System.out.println("call onNext i:" + i.toString());
                            subscriber.onNext((i++).toString());
                            if (i > 100000) {
                                System.out.println("called than 10_0000");
                                break;
                            }
                            if (subscriber.isUnsubscribed()) {/*抛出异常之后child会解除订阅关系，发射者需要关注订阅者是否已经解除订阅*/
                                System.out.println("child is UnSubscribed");
                                break;
                            }
                        }
                    }
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(Schedulers.io())/*预期此处会抛出背压异常*/
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();/*MissingBackPressureException异常会抛出在onError中*/
                    }

                    @Override
                    public void onNext(String s) {
                        System.out.println("onNext s:" + s);
                        ThreadInfoUtil.quietSleepThread(1, TimeUnit.SECONDS);
                    }
                });
        ThreadInfoUtil.quietSleepThread(10, TimeUnit.SECONDS);
    }


    /**
     * create 使用AsyncOnSubscribe返回给观察者的是一个Observable，并且每次回调的Observable可能是一个异步的Observable
     * 同时返回给onNext 的Observable 如果有延时 始终等待第一个延时的先发送完数据之后，再启动第二个，因此延时始终是叠加的
     * 2 + 1 + 3
     */
    @Test
    public void create() {
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        Observable
                .create(new AsyncOnSubscribe<Integer, String>() {
                    @Override
                    protected Integer generateState() {
                        return 0;
                    }

                    @Override
                    protected Integer next(Integer state, long requested, Observer<Observable<? extends String>> observer) {
                        if (state == 0) {
                            observer.onNext(Observable
                                    .just("1", "2")
                                    .delay(2, TimeUnit.SECONDS, Schedulers.io()));
                        } else if (state == 1) {
                            observer.onNext(Observable
                                    .just("11", "22")
                                    .delay(1, TimeUnit.SECONDS, Schedulers.io()));
                        } else if (state == 2) {
                            observer.onNext(Observable
                                    .just("111", "222")
                                    .delay(3, TimeUnit.SECONDS, Schedulers.io()));
                        } else {
                            observer.onCompleted();
                        }
                        return ++state;
                    }
                })
                .subscribe(subscriber);
//        ThreadInfoUtil.quietSleepThread(5,TimeUnit.SECONDS);
        boolean awaitValueCount = subscriber.awaitValueCount(2, 1500, TimeUnit.MILLISECONDS);
//        预期 延时大的还是先发射，延时小的最终反而是最后到达
        System.out.println("awaitValueCount:" + awaitValueCount);/*预期是false*/

//        实际只有两个值
        awaitValueCount = subscriber.awaitValueCount(4, 600, TimeUnit.MILLISECONDS);
        System.out.println("awaitValueCount:" + awaitValueCount);/*再等 600 预期是true*/


        awaitValueCount = subscriber.awaitValueCount(4, 1000, TimeUnit.MILLISECONDS);/*3.1s四个值*/
        System.out.println("awaitValueCount:" + awaitValueCount);/*再等 600 预期是true*/

        awaitValueCount = subscriber.awaitValueCount(6, 3000, TimeUnit.MILLISECONDS);/*6.1s四个值*/
        System.out.println("awaitValueCount:" + awaitValueCount);/*再等 600 预期是true*/

        CollectionsUtil.printList(subscriber.getOnNextEvents());
    }

    @Test
    public void testPublishSubjectBackPressure() {
        PublishSubject<String> publishSubject = PublishSubject.create();
        Observable
                .create(new Observable.OnSubscribe<String>() {
                    private Scheduler.Worker worker;
                    private int i;

                    @Override
                    public void call(Subscriber<? super String> subscriber) {
                        worker = Schedulers
                                .io()
                                .createWorker();
                        while (i < 1025) {
                            worker.schedule(new Action0() {
                                @Override
                                public void call() {
                                    subscriber.onNext("next:" + (++i));
                                }
                            }, 1, TimeUnit.SECONDS);
                            if (i == 1024) {
                                System.out.println("call onCompleted!");
                                subscriber.onCompleted();
                                break;
                            }
                            System.out.println("call onNext!");
                        }
                    }
                })
                .subscribe(publishSubject);

        publishSubject.subscribe(new Subscriber<String>() {


            @Override
            public void onStart() {
                super.onStart();
                request(128);
            }

            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();/*！会抛出MissingBackpressureException 请求的少于发射的数据个数，Publish会默认抛出异常！*/
            }

            @Override
            public void onNext(String s) {
                System.out.println("subscriber onNext:" + s);
            }
        });

        System.out.println("Subscribed!");
        ThreadInfoUtil.quietSleepThread(200, TimeUnit.SECONDS);
    }

    /**
     * 同步的amb使用amb操作附谁在前面谁获得发射权
     */
    @Test
    public void testSyncAmb() {
        Observable<Integer> first = Observable
                .just(1, 2, 3, 4, 5, 6)
                .doOnNext(System.out::println);
        Observable<Integer> second = Observable
                .just(21, 22, 23, 24, 25, 26)
                .doOnNext(System.out::println);

        Observable
                .amb(first, second)
                .subscribe(i -> System.out.println("onNext:" + i));

        System.out.println("===========second first========");

        Observable
                .amb(second, first)
                .subscribe(i -> System.out.println("onNext:" + i));

    }

    /**
     * 判断所有发射的数据是否满足这个条件
     * all 只要有一个元素不符合条件,则发射一个false （并且解除上游的订阅关系，请求发上游不再发射数据），所有元素都符合条件，则发射一个true。
     *      如果发射的是empty（只调用一次onComplete）则返回true
     * any 只要一个数据符合条件，则向下游发射一个true（并且解除上游订阅关系），所有数据都不符合条件则发射一个false
     *      如果发射empty则返回false
     */
    @Test
    public void testAllAndAny() {
//        预期只返回一个true，判断所有元素是否满足指定条件
        just1to7
                .doOnNext(new Action1<Integer>() {
                    @Override
                    public void call(Integer integer) {
                        System.out.println("doOnNext:" + integer);
                    }
                })
                .all(i -> i < 8)
                .subscribe(System.out::println);
//        预期只返回一个false 只要有一个元素不符合条件，则后续元素会被进行解除订阅的操作
        just1to7
                .doOnNext(new Action1<Integer>() {
                    @Override
                    public void call(Integer integer) {
                        System.out.println("doOnNext:" + integer);
                    }
                })
                .all(i -> i < 5)
                .subscribe(System.out::println);
//        all操作符只接收到一个Complete会发射什么数据？ 返回true
        Observable
                .empty()
                .doOnNext(o-> System.out.println("doOnNext"+o))
                .doOnCompleted(()-> System.out.println("doOnCompleted"))
                .all(o -> {
                    System.out.println("all:"+o);
                    return o == null;
                })
                .subscribe(b-> System.out.println("onNext:"+b));
        System.out.println("================any =============");
        //        预期只返回一个false，判断是否存在一个元素是否满足指定条件
        just1to7
                .doOnNext(new Action1<Integer>() {
                    @Override
                    public void call(Integer integer) {
                        System.out.println("doOnNext:" + integer);
                    }
                })
                .exists(/*i -> i < 1*/i -> i > 6)
                .subscribe(System.out::println);
//        预期只返回一个true 只要有一个元素符合条件，则后续元素会被进行解除订阅的操作
        just1to7
                .doOnNext(new Action1<Integer>() {
                    @Override
                    public void call(Integer integer) {
                        System.out.println("doOnNext:" + integer);
                    }
                })
                .exists(i -> i < 5)
                .subscribe(System.out::println);
//        any操作符只接收到一个Complete会发射什么数据？ 返回false
        Observable
                .empty()
                .doOnNext(o-> System.out.println("doOnNext"+o))
                .doOnCompleted(()-> System.out.println("doOnCompleted"))
                .exists(o -> {
                    System.out.println("all:"+o);
                    return o == null;
                })
                .subscribe(b-> System.out.println("onNext:"+b));
    }

    /**
     * {@link rx.observables.ConnectableObservable}
     * Publish产出ConnectableObservable之后，在connect之前订阅的Subscriber均能收到相同的完整的数据序列
     * 在connect之后订阅的Subscriber只能接收到部分数据序列，connect之后才会触发ConnectableObservable发射数据
     * 不同的订阅者收到的数据是相同的数据，避免了cold的Observable每次被不同的Subscriber产生的数据不同的问题
     *
     */
    @Test
    public void testPublishConnect(){
        Observable<String> timeLocate = Observable
                .create((Subscriber<? super String> subscriber) -> {
                    int i = 0;
                    while (i < 10) {
                        subscriber.onNext("produce i:" + i + "time:" + System.currentTimeMillis());
                        ++i;
                    }
                    subscriber.onCompleted();
                });
        ConnectableObservable<String> published = timeLocate
                .publish();

        Observable<String> skiped = published.skip(1);
        published.zipWith(skiped,(a,b)->a+"||"+b).subscribe(System.out::println, Throwable::printStackTrace,()-> System.out.println("onCompleted!"));
        Subscription connect1 = published.connect();
        ThreadInfoUtil.quietSleepThread(1,TimeUnit.SECONDS);
        connect1.unsubscribe();
        System.out.println("connnect twice!");
//        再次connect之后需要重新设置一个新的Subscriber，因为Subscriber调用onComplete 或者 onError之后便不会再去调用 onNext
//        两次调用connect返回的不是相同的数据
        published.zipWith(skiped,(a,b)->a+"||"+b).subscribe(System.out::println, Throwable::printStackTrace,()-> System.out.println("onCompleted!"));
        Subscription connect2 = published.connect();
        ThreadInfoUtil.quietSleepThread(1,TimeUnit.SECONDS);

//        cache 1 与 cache2 重发的带有时间戳的数据是一样的
        System.out.println("===========test cache 1=================");
        Observable<String> cached = timeLocate.cache();
        cached.subscribe(System.out::println);

        System.out.println("===========test cache 2=================");
        cached.subscribe(System.out::println);


        System.out.println("==============test PublishSubscriber unSubscribe===========================");
        ConnectableObservable<String> publish = Observable
                .interval(0, 1, TimeUnit.SECONDS)
                .map(s -> s + "|" + System.currentTimeMillis())
                .publish();
        publish.subscribe(s -> System.out.println("first unsb:" + s), e -> System.out.println("first onError:"+e.getMessage()),() -> System.out.println("first onCompleted"));
        publish.subscribe(s -> System.out.println("second unsb:" + s),e -> System.out.println("second onError:"+e.getMessage()),() -> System.out.println("second onCompleted"));
        Subscription[] sp1 = new Subscription[1];
        publish.connect(sp->{
            sp1[0] = sp;
        });
//        解除订阅之后两秒之间并没有发射任何数据(并且不会触发onError 或者 onComplete回调只是默默的停止向订阅者发送数据了
//        即使重新订阅也不会再继续向下发送数据
        ThreadInfoUtil.quietSleepThread(5,TimeUnit.SECONDS);
        sp1[0].unsubscribe();
        System.out.println("unSubscribed:"+System.currentTimeMillis());
        ThreadInfoUtil.quietSleepThread(2,TimeUnit.SECONDS);
        System.out.println("reconnect:"+System.currentTimeMillis());
        publish.connect();
        ThreadInfoUtil.quietSleepThread(2,TimeUnit.SECONDS);
        System.out.println("END:"+System.currentTimeMillis());


    }

    /**
     * 预期两个Replay的观察者最终都是在订阅者线程
     */
    @Test
    public void testReplay(){
        Observable<Integer> just1to7Io = just1to7.subscribeOn(Schedulers.io());
        ConnectableObservable<Integer> replayCp = just1to7Io
                .replay(Schedulers.computation());
        replayCp.subscribe((integer -> ThreadInfoUtil.printThreadInfo("cp1 next i :" + integer)));
        replayCp.connect();

        ConnectableObservable<Integer> replayIO = just1to7Io.replay();
        replayIO
                .observeOn(Schedulers.computation()).subscribe((integer -> ThreadInfoUtil.printThreadInfo("cp2 next i :"+integer)));
        replayIO.connect();
//Replay在connect之后链接依旧可以收到完整的事件序列，不使用ObservableOn切换线程，则默认在Replay指定的线程
        replayIO.subscribe(integer -> ThreadInfoUtil.printThreadInfo("cp3 next i:"+integer));

        ThreadInfoUtil.quietSleepThread(1,TimeUnit.SECONDS);
    }

    /**
     * recount 只有当最后一个解除订阅之后才去解除上游的订阅
     */
    // 实际上并没有解除上游的interval的订阅操作(因为使用了lift操作附,且没有把parent加入child中,导致下游解注册了并没有将上游解注册)
    @Test
    public void testRefConnect(){
        Observable<Long> refCount = Observable
                .interval(1, TimeUnit.SECONDS)
                .map(l->{
                    System.out.println("map l:"+l);
                    return l;
                })
                .doOnUnsubscribe(()->{
                    System.out.println("upStream unSubscribe!");
                })
//                .lift((Observable.Operator<Long, Long>) child -> {
//                    Subscriber<Long> parent = new Subscriber<Long>() {
//
//                        @Override
//                        public void onCompleted() {
//                            child.onCompleted();
//                        }
//
//                        @Override
//                        public void onError(Throwable e) {
//                            child.onError(e);
//                        }
//
//                        @Override
//                        public void onNext(Long aLong) {
//                            child.onNext(aLong);
//                        }
//                    };
//                    parent.add(Subscriptions.create(new Action0() {
//                        @Override
//                        public void call() {
//                            System.out.println("parent unSubscribe!");
//                        }
//                    }));
//                    child.add(parent);
//                    return parent;
//                })
                .publish()
                .refCount();

        Subscription first = refCount.subscribe(l -> {
            System.out.println("first :" + l);
        });

        ThreadInfoUtil.quietSleepThread(3,TimeUnit.SECONDS);

        Subscription second = refCount.subscribe(l -> {
            System.out.println("second :" + l);
        });

        ThreadInfoUtil.quietSleepThread(3,TimeUnit.SECONDS);

        first.unsubscribe();
        System.out.println("=====unSubscribe First:=======");
        ThreadInfoUtil.quietSleepThread(3,TimeUnit.SECONDS);

        second.unsubscribe();
        System.out.println("=====unSubscribe Second:=======");
        ThreadInfoUtil.quietSleepThread(3,TimeUnit.SECONDS);
    }

    /**
     * replay ConnectObservable的策略是,当第二个订阅者请求的数量大于缓存的数量时,则继续向上游进行请求
     */
    @Test
    public void replay(){
        ConnectableObservable<Integer> replay = Observable
                .range(1, 10)
                .replay(1);
        replay.connect();
//第一个收到 1 , 2 两个数据
        replay.subscribe(new Subscriber<Integer>() {
            @Override
            public void onStart() {
                request(2);
            }

            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(Integer integer) {
                System.out.println("first requet 2:"+integer);
            }
        });
//第二个收到2,3 两个数据 (缓存的数据只有一个 2)
        replay.subscribe(new Subscriber<Integer>() {
            @Override
            public void onStart() {
                request(2);
            }

            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(Integer integer) {
                System.out.println("second requet 2:"+integer);
            }
        });
    }


}