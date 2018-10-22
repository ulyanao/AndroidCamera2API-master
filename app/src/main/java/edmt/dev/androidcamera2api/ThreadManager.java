package edmt.dev.androidcamera2api;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadManager {
    //Static instance of the ThreadManager - singleton
    private static ThreadManager sInstance = new ThreadManager();
    //Instance of the ThreadPoolExecuter - it is the thread pool we are using
    private ThreadPoolExecutor mDecoderThreadPool;

    private ThreadManager() {
        //With the initialization of the class, we set up the thread pool
        //The number of cores we can use
        int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
        //The time a thread keeps alive if idle
        TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.NANOSECONDS;
        int KEEP_ALIVE_TIME = 1;
        //The type of queue we use for new incoming tasks to the thread pool
        BlockingQueue<Runnable> mDecodeWorkQueue = new ArrayBlockingQueue<>(10);
        //The thread pool is set up according to the defined parameters
        mDecoderThreadPool = new ThreadPoolExecutor(NUMBER_OF_CORES, NUMBER_OF_CORES, KEEP_ALIVE_TIME, KEEP_ALIVE_TIME_UNIT, mDecodeWorkQueue, new FrameThreadFactory());
    }

    //The static method to access the singleton of the class
    public static ThreadManager getInstance() {
        return sInstance;
    }

    //The method, which returns the thread pool we have set up
    public ThreadPoolExecutor getmDecoderThreadPool() {
        return mDecoderThreadPool;
    }

    //The definition of the threads we use for the thread pool
    class FrameThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread t = new Thread(runnable);
            //We set the priority of the threads we use for the thread pool
            t.setPriority(Thread.NORM_PRIORITY + 1);
            return t;
        }
    }
}