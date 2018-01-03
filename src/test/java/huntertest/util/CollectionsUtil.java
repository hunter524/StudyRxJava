package huntertest.util;

import java.util.Arrays;
import java.util.List;

public class CollectionsUtil {
    public static <T> String getArrayValueToString(T[] array) {
        String deepToString = Arrays.deepToString(array);
        return deepToString;
    }

    public static <T> String getListValueToString(List<T> list) {
        Object[] objects = list.toArray();
        String arrayValueToString = getArrayValueToString(objects);
        return arrayValueToString;
    }

    public static <T> void printList(List<T> list) {
        String listValueToString = getListValueToString(list);
        System.out.println(listValueToString);
    }

    public static <T> void printArray(T[] arrary){
        String arrayValueToString = getArrayValueToString(arrary);
        System.out.println(arrayValueToString);
    }
}