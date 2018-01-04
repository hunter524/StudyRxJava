package huntertest;

import huntertest.util.ThreadInfoUtil;

import java.util.Calendar;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class JavaTest {
    public static void main(String[] args) {
        countDownLatch();
        new Thread(()->{
            System.out.println("lambda test time:"+ Calendar.getInstance().getTime());
        }).start();
        ThreadInfoUtil.quietSleepThread(1,TimeUnit.SECONDS);
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
}
