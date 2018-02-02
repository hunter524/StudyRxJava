package com.github.hunter524;

import com.github.hunter524.util.ThreadInfoUtil;
import io.reactivex.*;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.TestObserver;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.UnicastSubject;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Assert;
import org.junit.Test;

import java.lang.management.ThreadInfo;
import java.util.concurrent.TimeUnit;

public class HelloRxjava2 {

//    预期发射者 与 接受者在一个线程中 且可以正常的结束发射者的发射行为
//    fix rxjava1中 订阅者和发射者在同一个线程中时，订阅者无法结束发射者的发射行为的问题
// （rxjava1 中Subscription是在订阅完成之后才返回的）
    @Test
    public void hellorxjava2(){
        Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> emitter) throws Exception {
                Integer i = 0;
                while (true){
                    ThreadInfoUtil.printThreadInfo("subscribe:");
                    if (!emitter.isDisposed()){
                        emitter.onNext((i++).toString());
                    }
                    else {
                        break;
                    }
                }
            }
        }).subscribe(new Observer<String>() {
            Disposable mDisposable;
            @Override
            public void onSubscribe(Disposable d) {
                mDisposable = d;
            }

            @Override
            public void onNext(String s) {
                ThreadInfoUtil.printThreadInfo("onNext:");
                System.out.println("onNext:"+s);
                if (s.equals("20")){
                    mDisposable.dispose();
                }
            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
            }

            @Override
            public void onComplete() {
                System.out.println("onComplete:");
            }
        });
    }

    @Test
    public void testObserveOn(){
        Observable.range(1,30).observeOn(Schedulers.io()).subscribe(new Observer<Integer>() {
            @Override
            public void onSubscribe(Disposable d) {
                System.out.println(d.getClass().getCanonicalName());
            }

            @Override
            public void onNext(Integer integer) {
                System.out.println("onNext Value:"+integer);
            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onComplete() {

            }
        });
        ThreadInfoUtil.quietSleepThread(2, TimeUnit.SECONDS);
    }

    /**
     * 与 http://static.blog.piasy.com/AdvancedRxJava/2016/10/04/subjects-part-2/ 对UnicastSubject的行为描述不符合
     */
    @Test
    public void testUnicastSubject(){
        UnicastSubject<Integer> unicastSubject = UnicastSubject.<Integer>create();
        Observable.just(1,2,3,4,5).subscribe(unicastSubject);
        unicastSubject.blockingIterable().iterator().forEachRemaining(System.out::println);
//        预期 第一次订阅结束之后 第二次订阅也会抛出异常 测试通过
        TestObserver<Integer> observer = new TestObserver<>();
        unicastSubject.subscribe(observer);
        observer.assertErrorMessage("Only a single observer allowed.");
    }
}
