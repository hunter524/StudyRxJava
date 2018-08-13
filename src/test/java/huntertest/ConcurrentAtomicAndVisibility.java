package huntertest;

import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrentAtomicAndVisibility {

    static Unsafe S_UNSAFE;
    static volatile int S_VISIBILITY_INDICATE = 0;
    static volatile long S_OFFSET_OF_S_VISIBILITY_INDICATE;

    static {
//        try {
//            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
//            theUnsafe.setAccessible(true);
//            Constructor<?> constructor = Unsafe.class.getDeclaredConstructors()[0];
//            constructor.setAccessible(true);
//            S_UNSAFE = (Unsafe) constructor.newInstance();
////            S_UNSAFE = (Unsafe) theUnsafe.get(null);
////            S_OFFSET_OF_S_VISIBILITY_INDICATE = S_UNSAFE.staticFieldOffset(ConcurrentAtomicAndVisibility.class.getDeclaredField("S_VISIBILITY_INDICATE"));
//        } catch (NoSuchFieldException e) {
//            e.printStackTrace();
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//        } catch (InstantiationException e) {
//            e.printStackTrace();
//        } catch (InvocationTargetException e) {
//            e.printStackTrace();
//        }
    }

    static int j = 0;
    static volatile int i = 0;/*volatile 与非volatile也无法保证内存的可见性*/

    static volatile IntegerAdd integerAdd = new IntegerAdd();

    static Object lock = new Object();

    static class IntegerAdd{
        volatile int i =0;
        public void increase(){
            i = i+1;
        }

        public int getI() {
            return i;
        }
    }
    static AtomicInteger k = new AtomicInteger();

    static volatile boolean stop = false;
    static int inVisibleJ = 0;

    public static void main(String[] args) throws InterruptedException {
        new Thread(()->{
            int i = 0;
            while (i<100000){
                System.out.print("i:"+i+"|");
                i++;
            }
            stop = true;
            System.out.println("Thread 1 InVisible J :"+inVisibleJ);
        }).start();

        new Thread(()->{
            while (!stop){
                inVisibleJ++;
                System.out.print("J:"+inVisibleJ+"|");
            }
            System.out.println("Thread 2 InVisible J :"+inVisibleJ);

        }).start();

//        concurrentAndVisibilityTestCase1();
    }



    private static void concurrentAndVisibilityTestCase1() throws InterruptedException {
        Thread thread1 = new Thread(() -> {
            int i1 = 0;
            while (i1 < 1000000) {
                while (true){
                    int expected = /*S_UNSAFE.getInt(S_OFFSET_OF_S_VISIBILITY_INDICATE)*/k.get();
                    boolean b = /*S_UNSAFE.compareAndSwapInt(null, S_OFFSET_OF_S_VISIBILITY_INDICATE, expected, expected + 1)*/ k.compareAndSet(expected,expected+1);
                    if (b){
                        break;
                    }
                }
                int j = i;
                synchronized (lock){
                    i = j+1;
                    System.out.print("tread1 i:" + j+"\t");
                }


//                integerAdd.increase();
//                System.out.print("tread1 i:" + integerAdd.getI());

                i1++;

                while (true){
                    int expected = /*S_UNSAFE.getInt(S_OFFSET_OF_S_VISIBILITY_INDICATE)*/k.get();
                    boolean b = /*S_UNSAFE.compareAndSwapInt(null, S_OFFSET_OF_S_VISIBILITY_INDICATE, expected, expected + 1)*/ k.compareAndSet(expected,expected+1);
                    if (b){
                        break;
                    }
                }
//                k.set(0);
            }
            System.out.println("\n thread 1 i:" + i);

            System.out.println("\n thread 1 integerAdd:" + integerAdd.getI());
        });
        thread1.start();
        Thread thread2 = new Thread(() -> {

            int i1 = 0;
            while (i1 < 1000000) {
//
                while (true){
                    int expected = /*S_UNSAFE.getInt(S_OFFSET_OF_S_VISIBILITY_INDICATE)*/k.get();
                    boolean b = /*S_UNSAFE.compareAndSwapInt(null, S_OFFSET_OF_S_VISIBILITY_INDICATE, expected, expected + 1)*/ k.compareAndSet(expected,expected+1);
                    if (b){
                        break;
                    }
                }

                int j = i;
                synchronized (lock){
                    System.out.print("tread2 i:" + j+"\t");
                    i =  j + 1;
                }

//                integerAdd.increase();

//                System.out.print("tread1 i:" + integerAdd.getI());

                i1++;

                while (true){
                    int expected = /*S_UNSAFE.getInt(S_OFFSET_OF_S_VISIBILITY_INDICATE)*/k.get();
                    boolean b = /*S_UNSAFE.compareAndSwapInt(null, S_OFFSET_OF_S_VISIBILITY_INDICATE, expected, expected + 1)*/ k.compareAndSet(expected,expected+1);
                    if (b){
                        break;
                    }
                }
//                k.set(0);
            }
            System.out.println("\nthread 2 i:" + i);
            System.out.println("\n thread 2 integerAdd:" + integerAdd.getI());
        });
        thread2.start();

        thread1.join();
        thread2.join();
        System.out.println("k:"+k.get());
        System.out.println("i:" + i);
    }

    static class Constructor{
        int i;

        public Constructor() {

            new Inner();
//            new CInner();
            class CInner{
                public int get(){
                    return i;
                }
            }
        }

        class Inner{
            public int get(){
                return i;
//                new Constructor.this.Inner();
            }
        }
    }
}
