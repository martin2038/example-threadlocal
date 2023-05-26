/**
 * Martin
 * Copyright (c) 2021-2023 All Rights Reserved.
 */
package martin.example.threadlocal;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author Martin.C
 * @version 2023/05/17 16:45
 */
public class MartinThreadLocalTest {


    static class SomeData {
        static final AtomicInteger GC_CNT = new AtomicInteger();
        // 5MB
        private byte[] data = new byte[5 << 20];

        @Override
        protected void finalize() throws Throwable {
            System.out.println(this + " finalize :" + GC_CNT.incrementAndGet());
        }
    }

    final static ExecutorService EXES =  Executors.newFixedThreadPool(5);

    static final int DATA_TOTAl = 50;
    static  final  ThreadLocal<SomeData>  GLOBAL = new ThreadLocal<>();
    public static void main(String[] args) throws Exception {
        // http://concurrent.redspider.group/article/03/21.html

        // http://concurrent.redspider.group/article/03/imgs/ThreadLocal%E6%A8%A1%E5%9E%8B.png
        // http://concurrent.redspider.group/article/03/imgs/ThreadLocal%E6%A6%82%E5%BF%B5%E5%9B%BE2.png
        //testMoreThreadMem__Leak();

        //testCluade(GLOBAL);
        testMem__Leak_Balance();

        //testNThreadMemKeep();

        //testBestPractice();
    }



    static void testCluade(ThreadLocal<SomeData> tl) throws IOException, InterruptedException {
        var t = new Thread() {
            public void run() {
                tl.set(new SomeData()); // Store Foo in ThreadLocal
                System.out.println( Thread.currentThread().getName() + " -> :" + tl.get());
            }
        };
        t.start();
        Thread.sleep(5000);
        System.gc();
        System.out.println("GC Called.");
        System.in.read();
    }

    static void testMem__Leak_Balance() throws IOException, InterruptedException {

        //var cnt = new CountDownLatch(DATA_TOTAl);
        var i = new AtomicInteger();
        while (true){
            EXES.execute(() -> {
                var local = new ThreadLocal<SomeData>();
                local.set(new SomeData());

                System.out.println( Thread.currentThread().getName() + " -> :" + local.get());
                System.out.println("has unused data" + (i.incrementAndGet() - SomeData.GC_CNT.get()));
            });
        }
    }


    static void testMoreThreadMem__Leak() throws IOException, InterruptedException {
        var cnt = new CountDownLatch(DATA_TOTAl);
        for (int i = 0; i < DATA_TOTAl; ++i) {
            EXES.execute(() -> {
                var local= new ThreadLocal<SomeData>();
                local.set(new SomeData());

                System.out.println( Thread.currentThread().getName() + " -> :"+ local.get());
                cnt.countDown();
            });
        }
        cnt.await();
        System.gc();
        //等下GC完成
        Thread.sleep(2000);
        System.out.println("begin to expungeStaleEntry indirectly , current has " + (DATA_TOTAl - SomeData.GC_CNT.get()) +" unused.");
        indirectlyExpungeStaleEntry();
    }

    static void indirectlyExpungeStaleEntry() throws InterruptedException {
        // A. 共享会造成固定位置扫描，部分地方可能一直扫描不到
        var local = new ThreadLocal<Integer>();
        var maxTry = 10;
        var GC_CNT = SomeData.GC_CNT;
        while (maxTry>0){
            if(GC_CNT.get() == DATA_TOTAl){
                System.out.println("You are luck! ALL data cleaned by expungeStaleEntry.Now is no leak.Bye");
                System.exit(0);
            }
            var cleaned = GC_CNT.get();
            // B. 换成局部变量，更有机会去扫描expungeStaleEntry
            // var local = new ThreadLocal<Integer>();
            for (int i = 0; i < 50; ++i) {
                //调用get也会触发expungeStaleEntry
                EXES.execute(() -> {local.get();});
            }
            System.gc();
            //等下GC完成
            Thread.sleep(2000);
            maxTry --;
            System.out.println("try to indirect expungeStaleEntry "+ maxTry +" itor:  cleaned "+( GC_CNT.get() - cleaned) +" data, still has "+(DATA_TOTAl - GC_CNT.get()) +" unused.");
        }
        System.out.println("mem leaked with "+(DATA_TOTAl - GC_CNT.get()) +" unused.");
        //System.exit(0);
    }

    static void testNThreadMemKeep() throws InterruptedException {
        var local= new ThreadLocal<SomeData>();
        for (int i = 0; i < DATA_TOTAl; ++i) {
            EXES.execute(() -> {
                local.set(new SomeData());
                System.out.println( Thread.currentThread().getName() + " -> :"+ local.get());
            });
        }

        while (true){
            var left =  SomeData.GC_CNT.get();
            System.gc();
            //等下GC完成
            Thread.sleep(2000);
            if(SomeData.GC_CNT.get() == left){
                System.out.println("current has " + (DATA_TOTAl - left) + " cached data, which must the same with thread cnt.");
                break;
            }
        }
        //执行完后，如果不再进行任何ThreadLocal操作，这几个线程里面的内存会一直存在。
    }

    static void testBestPractice() throws InterruptedException {
        for (int i = 0; i < DATA_TOTAl; ++i) {
            EXES.execute(new BestPractice());
        }

        var taskQueen = ((ThreadPoolExecutor) EXES).getQueue();
        while (true) {
            if (null != taskQueen.peek()) {
                Thread.sleep(10);
                continue;
            }
            System.gc();
            //等下GC完成
            Thread.sleep(2000);
            if (SomeData.GC_CNT.get() == DATA_TOTAl) {
                System.out.println("ALL data cleaned by explicit remove.Bye");
                //exe.shutdown();

                System.exit(0);
            }
        }
    }

    static class BestPractice implements Runnable{
        static final ThreadLocal<SomeData> LOCAL = new ThreadLocal<>();
        @Override
        public void run() {
            var data = new SomeData();
            try {
                LOCAL.set(data);
                System.out.println("call some method in this Thread. name end：" + Thread.currentThread().getName() + " -> :"+ LOCAL.get());
            }finally {
                LOCAL.remove();
            }
        }
    }

}