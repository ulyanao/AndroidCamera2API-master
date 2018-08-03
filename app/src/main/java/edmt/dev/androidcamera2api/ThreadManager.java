package edmt.dev.androidcamera2api;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadManager {
    //Instance of the Manager
    private static ThreadManager sInstance = new ThreadManager();
    //The ThreadPoolExecutor
    private ThreadPoolExecutor mDecoderThreadPool;

    private ThreadManager() {
        //Initialization of ThreadPool
        int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
        TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.NANOSECONDS;
        int KEEP_ALIVE_TIME = 1;
        BlockingQueue<Runnable> mDecodeWorkQueue = new ArrayBlockingQueue<>(10);
        mDecoderThreadPool = new ThreadPoolExecutor(NUMBER_OF_CORES, NUMBER_OF_CORES, KEEP_ALIVE_TIME, KEEP_ALIVE_TIME_UNIT, mDecodeWorkQueue,new FrameThreadFactory());
    }

    public static ThreadManager getInstance() {
        return sInstance;
    }

    public ThreadPoolExecutor getmDecoderThreadPool() {
        return mDecoderThreadPool;
    }

    class FrameThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread t = new Thread(runnable);
            t.setPriority(Thread.NORM_PRIORITY+1);
            return t;
        }
    }

}
