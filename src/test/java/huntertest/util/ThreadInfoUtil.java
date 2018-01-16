package huntertest.util;

import java.util.concurrent.TimeUnit;

/**
 * tips：1、{@link Thread#getAllStackTraces()} 可以获取所有当前正在运行的线程的方法栈调用信息。
 */
public class ThreadInfoUtil {
    public static final void printThreadInfo(String prefix){
        System.out.println(prefix+":"+getThreadInfo());
    }

    public static final String getThreadInfo(){
        return Thread.currentThread().toString();
    }

    public static final void printThreadInfo(){
        printThreadInfo("Thread Info");
    }

    public static final boolean quietSleepThread(long time, TimeUnit timeUnit){
        try {
            timeUnit.sleep(time);
            return true;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }
}
