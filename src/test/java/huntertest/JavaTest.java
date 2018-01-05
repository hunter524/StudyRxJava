package huntertest;

import huntertest.util.CollectionsUtil;
import huntertest.util.ThreadInfoUtil;
import org.mockito.cglib.core.CollectionUtils;

import java.util.*;
import java.util.concurrent.*;

public class JavaTest {
    public static void main(String[] args) {
//        countDownLatch();
//        exchange();
//        ThreadInfoUtil.quietSleepThread(1,TimeUnit.SECONDS);
        dameThread();
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
}
