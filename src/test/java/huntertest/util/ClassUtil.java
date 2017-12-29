package huntertest.util;

import java.lang.reflect.Field;

public class ClassUtil {

    public static void printClassName(Object object, String prefix) {
        Class<?> aClass = object.getClass();
        String canonicalName = aClass.getCanonicalName();
        System.out.println(prefix + ":" + canonicalName);
    }

    public static void printClassName(Object object) {
        printClassName(object, object.toString());
    }


    /**
     * @param clazz
     * @param fieldName
     * @return not found will return null
     */
    public static Field quietGetField(Class<?> clazz, String fieldName) {
        Field field = null;
        try {
            field = clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        return field;
    }

    @SuppressWarnings("unchecked cast")
    public static <T> T quietGetValue(Object object, Field field) {
        Object o = null;
        try {
            field.setAccessible(true);
            o = field.get(object);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        if (null == o) {
            return null;
        }
        return (T) o;
    }
}