package huntertest.util;

public class MemoryUtil {
    public static void printUsedMemory(){
        long totalMemory = Runtime
                .getRuntime()
                .totalMemory();
        long freeMemory = Runtime
                .getRuntime()
                .freeMemory();
        System.out.println("used"+(totalMemory-freeMemory)+"B");
    }
}
