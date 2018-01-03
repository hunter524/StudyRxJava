package huntertest;

import huntertest.util.CollectionsUtil;
import huntertest.util.ThreadInfoUtil;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.functions.Func2;
import rx.internal.producers.SingleProducer;
import rx.observers.TestSubscriber;

import java.util.Vector;
import java.util.concurrent.TimeUnit;

public class OperatorErrorImplement {

    Observable<Integer> just1to7;
    Observable<String> just1to7Str;
    TestSubscriber<Integer> testSubscriber;

    @Before
    public void initObservableAndObserver(){
        just1to7 = Observable.just(1,2,3,4,5,6,7);
        testSubscriber = new TestSubscriber<Integer>(){
            @Override
            public void onNext(Integer integer) {
                super.onNext(integer);
                System.out.println("onNext from test Subscriber:"+String.valueOf(integer));
            }
        };
        just1to7Str = Observable.just("1","2","3","4","5","6","7");
    }


    /**
     * https://blog.piasy.com/AdvancedRxJava/2016/06/25/pitfalls-of-operator-implementations-2/
     * 并没有出现不等请求就直接发射的现象 emmit with out request
     *
     */
    @Test
    public void missRequestButSenValue(){
        Observable.Operator<String, Integer> missRequestOp = new Observable.Operator<String, Integer>() {

            @Override
            public Subscriber<? super Integer>/*上游持有的*/ call(final Subscriber<? super String> subscriber/*下游订阅的*/) {
                return new Subscriber<Integer>() {
                    @Override
                    public void onCompleted() {
                        System.out.println("missRequestOp child subscriber is:"+subscriber+"class is:"+subscriber.getClass().getCanonicalName());
                        subscriber.onNext("next before missRequestOp onCompleted!");
                        subscriber.onCompleted();
                    }

                    @Override
                    public void onError(Throwable e) {
                        subscriber.onError(e);
                    }

                    @Override
                    public void onNext(Integer integer) {
                        subscriber.onNext(String.valueOf(integer));
                    }
                };
            }
        };

        Observable<Integer> hotObservable = just1to7.delay(2,TimeUnit.SECONDS)
                .publish()
                .autoConnect();

//        没有订阅者即发射最后一个数据 并不会出现
        System.out.println("start lift");
        hotObservable.lift(missRequestOp)/*.subscribe(new TestSubscriber<String>(){
            @Override
            public void onNext(String s) {
                super.onNext(s);
                System.out.println("onNext from test Subscriber after lift:"+s);
            }
        })*/;
        hotObservable.subscribe(testSubscriber);

        ThreadInfoUtil.quietSleepThread(3, TimeUnit.SECONDS);

    }

    /**
     *操作符中共享状态（shared state in the Operator （多次订阅使用的是一个Operator会导致状态错误）
     */
    @Test
    public void sharedStateInOperator() {
        Observable.Operator<Integer, String> countOp = new Observable.Operator<Integer, String>() {

//            int count;/*错误的共享状态*/
            @Override
            public Subscriber<? super String> call(final Subscriber<? super Integer> subscriber) {
                return new Subscriber<String>() {
                    int count;/*正确的计数变量应该置于此处*/
                    @Override
                    public void onCompleted() {
                        subscriber.setProducer(new SingleProducer<Integer>(subscriber,count));
                    }

                    @Override
                    public void onError(Throwable e) {
                        subscriber.onError(e);
                    }

                    @Override
                    public void onNext(String s) {
                        count++;
                    }
                };
            }
        };
        Observable<Integer> countObservable = just1to7Str.lift(countOp);
//        下面订阅三次 copy代码三次 预期结果应该是 7 14 21 使用错误的共享状态 正确的共享状态结果是 7 7 7
        countObservable.subscribe(new Observer<Integer>() {
            @Override
            public void onCompleted() {
                System.out.println("onCompleted subscriber 1");
            }

            @Override
            public void onError(Throwable e) {
                System.out.println("onError subscriber 1");
            }

            @Override
            public void onNext(Integer integer) {
                System.out.println("onNext subscriber 2 value is:"+String.valueOf(integer));
            }
        });

        countObservable.subscribe(new Observer<Integer>() {
            @Override
            public void onCompleted() {
                System.out.println("onCompleted subscriber 2");
            }

            @Override
            public void onError(Throwable e) {
                System.out.println("onError subscriber 2");
            }

            @Override
            public void onNext(Integer integer) {
                System.out.println("onNext subscriber 2 value is:"+String.valueOf(integer));
            }
        });

        countObservable.subscribe(new Observer<Integer>() {
            @Override
            public void onCompleted() {
                System.out.println("onCompleted subscriber 3");
            }

            @Override
            public void onError(Throwable e) {
                System.out.println("onError subscriber 3");
            }

            @Override
            public void onNext(Integer integer) {
                System.out.println("onNext subscriber 3 value is:"+String.valueOf(integer));
            }
        });

    }

    /**
     * Observable 链条中的共享状态(shared state in an Observable Chain)
     *
     */
    @Test
    public void sharedStatesInObservableChain(){
        Observable<Vector<Integer>> reduceObservable = just1to7.reduce(new Vector<Integer>(), new Func2<Vector<Integer>, Integer, Vector<Integer>>() {
            @Override
            public Vector<Integer> call(Vector<Integer> integers, Integer integer) {
                integers.add(integer);
                return integers;
            }
        });

        reduceObservable.subscribe(new TestSubscriber<Vector<Integer>>(){
            @Override
            public void onNext(Vector<Integer> integers) {
                super.onNext(integers);
                System.out.println("subscriber 1 call onNext");
                CollectionsUtil.printList(integers);
            }
        });

        reduceObservable.subscribe(new TestSubscriber<Vector<Integer>>(){
            @Override
            public void onNext(Vector<Integer> integers) {
                super.onNext(integers);
                System.out.println("subscriber 2 call onNext");
                CollectionsUtil.printList(integers);
            }
        });

        reduceObservable.subscribe(new TestSubscriber<Vector<Integer>>(){
            @Override
            public void onNext(Vector<Integer> integers) {
                super.onNext(integers);
                System.out.println("subscriber 3 call onNext");
                CollectionsUtil.printList(integers);
            }
        });

    }
}
