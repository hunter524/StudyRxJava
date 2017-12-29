package huntertest;


import java.io.*;

public class JavaShell {
    public static void main(String[] args) {
        Runtime runtime = Runtime.getRuntime();
        try {
            ProcessBuilder command = new ProcessBuilder().command("git version");
            Process exec = command.start();
            OutputStream outputStream = exec.getOutputStream();
            InputStream inputStream = exec.getInputStream();
            InputStream errorStream = exec.getErrorStream();
            PrintWriter printWriter = new PrintWriter(outputStream);
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            InputStreamReader inputStreamReader1 = new InputStreamReader(errorStream);
            BufferedReader errorReader = new BufferedReader(inputStreamReader);
            final BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
//            new Thread(){
//                @Override
//                public void run() {
//                    super.run();
//                    try {
//                        String s;
//                        while ((s = bufferedReader.readLine())!=null){
//                            System.out.println(s);
//                        }
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }.start();
            int i = exec.waitFor();
            System.out.println("end exe :"+i);
            char[] chars = new char[512];
            while (bufferedReader.read(chars) !=-1){
                System.out.println(new String(chars));
            }
            while (errorReader.read(chars) !=-1){
                System.out.println(new String(chars));
            }
            exec.waitFor();
            System.out.println("end exe :"+i);


        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
