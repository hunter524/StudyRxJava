package huntertest;

public class ClassUtil {

    public static void printClassName(Object object,String prefix){
        Class<?> aClass = object.getClass();
        String canonicalName = aClass.getCanonicalName();
        System.out.println(prefix+":"+canonicalName);
    }
    public static void printClassName(Object object){
        printClassName(object,object.toString());
    }
}