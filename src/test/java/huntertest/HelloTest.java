package huntertest;

import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.plugins.RxJavaHooks;
import rx.schedulers.Schedulers;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class HelloTest {

    @Test
    public void analysisJustCallFlow() {
        int sum = 0;
        int month = 5275;
        for(int i=1;i<=12;i++){
            sum = sum+month*i;
        }
        System.out.println(sum/12*15);
    }

    @Before
    public void configRxjava(){
        RxJavaHooks.setOnObservableStart(new Func2<Observable, Observable.OnSubscribe, Observable.OnSubscribe>() {
            private int callTimes = 0;

            @Override
            public Observable.OnSubscribe call(Observable observable, Observable.OnSubscribe onSubscribe) {
                ClassUtil.printClassName(onSubscribe);
                ClassUtil.printClassName(observable);
                callTimes++;
                System.out.println("callTimes:"+callTimes);
                return onSubscribe;
            }
        });
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
                        ClassUtil.printClassName(subscriber,"subscriber @@@  "+subscriber);

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
        ClassUtil.printClassName(subscription,"subscription @@@"+subscription);
        ThreadInfoUtil.quietSleepThread(1, TimeUnit.SECONDS);
    }

    @Test
    public void testUnSubscribeMapChain(){
        final Observable<String> mapChain = Observable
                .create(new Observable.OnSubscribe<String>() {
                    @Override
                    public void call(Subscriber<? super String> subscriber) {
                        for (int i=0;i<10;i++){
                            subscriber.onNext("create:"+i);
                            System.out.println("isInterrupted:"+Thread.currentThread().isInterrupted());
                            if (!Thread.currentThread().isInterrupted()){
                                ThreadInfoUtil.quietSleepThread(1,TimeUnit.SECONDS);
                            }
                        }
                        subscriber.onCompleted();
                    }
                })
                .map(new Func1<String, String>() {
                    @Override
                    public String call(String s) {
                        System.out.println("map func 1 "+s);
                        return s;
                    }
                })
                .map(new Func1<String, String>() {
                    @Override
                    public String call(String s) {
                        System.out.println("map func 2 "+s);
                        return s;
                    }
                });
        final Subscription[] subscription = new Subscription[1];
        new Thread(){
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
                subscription[0] = mapChain.subscribeOn(Schedulers.io()).subscribe(subscriber);
                String arrayDeepStr = Arrays.deepToString(subscription);
                System.out.println("arrayDeepStr:"+arrayDeepStr);
//                持有的是包装完的SafeSubscriber
                ClassUtil.printClassName(subscription[0],"subscription@@@@@"+subscription[0]);
                ClassUtil.printClassName(subscriber,"subscriber@@@@@"+subscriber);
            }
        }.start();

        new Thread(){
            @Override
            public void run() {
                ThreadInfoUtil.quietSleepThread(2,TimeUnit.SECONDS);
                subscription[0].unsubscribe();
            }
        }.start();

        ThreadInfoUtil.quietSleepThread(10,TimeUnit.SECONDS);
    }
}
