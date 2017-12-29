package huntertest;

import huntertest.util.ThreadInfoUtil;
import org.junit.Test;
import rx.Observable;
import rx.Observer;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.observables.SyncOnSubscribe;
import rx.schedulers.Schedulers;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

/**
 * 为了理解每个操作符的用处
 */
public class OperatorFunction {

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
}
