package huntertest;

import huntertest.util.CollectionsUtil;
import huntertest.util.ThreadInfoUtil;
import rx.Producer;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Observable;
import java.util.concurrent.*;

public class JavaTest {
    public static void main(String[] args) {
//        countDownLatch();
//        exchange();
//        ThreadInfoUtil.quietSleepThread(1,TimeUnit.SECONDS);
//        dameThread();
//        interruptAndWait();
//        threadJoin();
//        throwWithOutCatchHasFinally();
//        ExecutorService executorService = Executors.newCachedThreadPool();
//        executorService.execute(new Runnable() {
//            @Override
//            public void run() {
//                throw new IllegalArgumentException();
//            }
//        });
//        ThreadInfoUtil.quietSleepThread(2,TimeUnit.SECONDS);
//        System.out.println("end ");
//        ThreadInfoUtil.quietSleepThread(2,TimeUnit.SECONDS);
//        executorService.submit(new Runnable() {
//            @Override
//            public void run() {
//                System.out.println("next run!");
//            }
//        });
        new Thread(()->{
            throw new IllegalArgumentException();
        }).start();
        ThreadInfoUtil.quietSleepThread(1,TimeUnit.SECONDS);
        System.out.println("end!");
    }

    public static final void maxArray(){
        Integer[] integers = new Integer[Integer.MAX_VALUE&0x0fffffff];
        System.out.println(integers.length);
    }
//Harness
    public static final void countDownLatch(){
        final CountDownLatch countDownLatch = new CountDownLatch(2);
        new Thread(){
            @Override
            public void run() {
                super.run();
                try {
                    System.out.println("t1 countDown wait!");
                    countDownLatch.await();
                    System.out.println("t1 countDown end!");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }.start();

        Thread thread2 = new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    System.out.println("t2 countDown wait!");
                    countDownLatch.await();
                    System.out.println("t2 countDown end!");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    this.interrupt();
                    System.out.println("re interrupt :"+this.isInterrupted());
                }
            }
        };
        thread2.start();

        ThreadInfoUtil.quietSleepThread(1, TimeUnit.SECONDS);
        System.out.println("count down 1");
        countDownLatch.countDown();

//        尝试中断Thread2 中断会导致Thread 抛出异常(wait 终止并且抛出异常）
        thread2.interrupt();

        ThreadInfoUtil.quietSleepThread(1, TimeUnit.SECONDS);
        System.out.println("count down 2");
        countDownLatch.countDown();

        ThreadInfoUtil.quietSleepThread(1,TimeUnit.SECONDS);

    }

    public static final void exchange(){
        CyclicBarrier cyclicBarrier = new CyclicBarrier(2);
//        list在两个线程之间安全的交换
        Exchanger<List<Integer>> arrayListExchanger = new Exchanger<>();
        new Thread(()->{

            List<Integer> integers = Arrays.asList(1, 2, 3, 4, 5, 6, 7);
            System.out.println("thread 1 exchange");
            try {
                cyclicBarrier.await();
                integers = arrayListExchanger.exchange(integers);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (BrokenBarrierException e) {
                e.printStackTrace();
            }
            System.out.println("thread 1 after exchange!");
            CollectionsUtil.printList(integers);

        }).start();

        new Thread(()->{
            List<Integer> integers = Arrays.asList(8, 9, 10,11, 12, 13,14,20000);
            System.out.println("thread 2 exchange");
            try {
                cyclicBarrier.await();
                integers = arrayListExchanger.exchange(integers);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (BrokenBarrierException e) {
                e.printStackTrace();
            }
            System.out.println("thread 2 after exchange!");
            CollectionsUtil.printList(integers);

        }).start();
    }

    /**
     * 非Dame线程不结束 虚拟机不退出
     */
    public static final void dameThread(){
        new Thread(()->{
            boolean daemon = Thread
                    .currentThread()
                    .isDaemon();
            System.out.println("Sub isDame:"+daemon+"Time:"+Calendar.getInstance().getTime());
            ThreadInfoUtil.quietSleepThread(5,TimeUnit.SECONDS);
            System.out.println("Sub Thread End!"+"Time:"+Calendar.getInstance().getTime());
        }).start();
        boolean daemon = Thread
                .currentThread()
                .isDaemon();
        ThreadInfoUtil.printThreadInfo("Main Thread");
//        测试非Dema线程不退出 主线程不退出
        System.out.println("isDame:"+daemon+"Time:"+Calendar.getInstance().getTime());
    }


    /**
     * 先interrupt再wait
     */
    public static final void interruptAndWait(){
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Object wait = new Object();
        new Thread(()->{
            try {
                countDownLatch.await();
                Thread.currentThread().interrupt();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            synchronized (wait){
                try {
                    wait.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    System.out.println("wait interrupted");
                }
            }

        }).start();
        countDownLatch.countDown();
    }

    public static final void threadJoin(){
        Thread thread = new Thread(() -> {
            long l = System.currentTimeMillis();
            long current;
            while ((current = System.currentTimeMillis()) - l < 1000) {
                System.out.println("current :" + current);
            }
            System.out.println("SubThread End!");
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("MainThread End!");
    }

    public static final void throwWithOutCatchHasFinally(){
        try {
            throw new IllegalArgumentException();
        }
        finally {
            System.out.println("finally");
        }
    }

    public static <T> void  getGeneric(){
//        T[] a = {1};
//        T t = a[0];
    }

    public static <T extends rx.Observable & rx.Observer & Producer>  void genericMultiyExtends(T t){
        t.request(2);
    }
}
