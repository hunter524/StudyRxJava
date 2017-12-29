package huntertest.util;

import java.util.concurrent.TimeUnit;

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
