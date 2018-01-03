package huntertest;

import huntertest.util.CollectionsUtil;
import huntertest.util.ThreadInfoUtil;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Observer;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.observables.SyncOnSubscribe;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 为了理解每个操作符的用处
 */
public class OperatorFunction {


    Observable<Integer> just1to7;
    Observable<String> just1to7Str;
    TestSubscriber<Integer> testSubscriberInteger;
    private TestSubscriber<String> testSubscriberStr;

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

        just1to7Str = Observable.just("1","2","3","4","5","6","7");
    }

    /**
     * 多个Observable 发射数据有延迟，取最早发射数据的那个Observable然后发送该Observable的所有数据
     * 解除订阅其他的Observable
     *
     * 如果多个数据发送的Observable不是同一种类型，则发射他们共同的父类
     *
     * amb 和 ambWith
     */
    @Test
    public void amb(){
        SyncOnSubscribe<Integer, String> syncOnSubscribe = new SyncOnSubscribe<Integer, String>() {
            @Override
            protected Integer generateState() {
                return 0;
            }

            @Override
            protected Integer next(Integer state, Observer<? super String> observer) {
                ThreadInfoUtil.printThreadInfo("syncOnSubscribe ThreadInfo:");
                observer.onNext("first observable:" + state+"  Produce onNext Thread Info:"+ThreadInfoUtil.getThreadInfo());
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
                System.out.println("onNext:"+s);
            }
        };

        Observable.amb(delay1sObservable,delay2sObservable).subscribe(onNext);




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
                        if (BigDecimal.valueOf(10).compareTo(state) == 0) {
                            observer.onCompleted();
                        }
                        return state.add(BigDecimal.valueOf(1));
                    }
                })
                .delay(2, TimeUnit.SECONDS);

        Observable.amb(delay1sInteger,delay2sBigDecimal).subscribe(new Action1<Number>() {
//            找到共同的父类 实际返回的是Integer的类
            @Override
            public void call(Number number) {
                System.out.println("number is:"+number.longValue());
                System.out.println("number type is :"+number.getClass());
            }
        });

        ThreadInfoUtil.printThreadInfo("subscribe End ");
        ThreadInfoUtil.quietSleepThread(3,TimeUnit.SECONDS);
    }

    @Test
    public void buffer(){
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
    public void toList(){
        Observable.just(1,2,3,4).toList().subscribe(new Action1<List<Integer>>() {
            @Override
            public void call(List<Integer> integers) {
                Object[] objects = integers.toArray();
                String array = Arrays.deepToString(objects);
                System.out.println("toList :"+array);
            }
        });
    }

    @Test
    public void toMap(){
        Observable.just("a","bb","ccc","dddd").toMap(new Func1<String, Integer>() {
            @Override
            public Integer call(String s) {
                return s.length();
            }
        }, new Func1<String, String>() {
            @Override
            public String call(String s) {
                return s+s;
            }
        }).subscribe(new Action1<Map<Integer, String>>() {
            @Override
            public void call(Map<Integer, String> integerStringMap) {
//                此处应该是HashMap
                System.out.println("integerStringMap is :"+integerStringMap.getClass().getCanonicalName());
//                打印map的内容
                System.out.println("integerStringMap content is:"+integerStringMap.toString());

            }
        });
    }

    @Test
    public void takeXXX(){
        Observable<Integer> just1To7 = Observable.just(1, 2, 3, 4, 5, 6, 7);
        TestSubscriber<Integer> subscriber = new TestSubscriber<Integer>();
        just1To7.takeUntil(new Func1<Integer, Boolean>() {
            @Override
            public Boolean call(Integer integer) {
                return integer == 4;
            }
        }).subscribe(subscriber);
        List<Integer> onNextEvents = subscriber.getOnNextEvents();
        CollectionsUtil.printList(onNextEvents);
    }

    /**
     * 每次获得一次数据将数据累加之后返回
     * 预期结果 1|2|3|4|5|6|7
     *
     */
    @Test
    public void reduce(){
        just1to7Str.reduce(new Func2<String, String, String>() {
            @Override
            public String call(String s, String s2) {
                return s+"|"+s2;
            }
        }).subscribe(testSubscriberStr);
    }

    @Test
    public void concatWith(){
        Observable.just(1).concatWith(Observable.just(2)).subscribe(testSubscriberInteger);
    }

    @Test
    // TODO: 18-1-3 流程分析 总是绕晕了！！！
//    适用于将结果进行摊平的场景
    public void flatMap(){
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


}
