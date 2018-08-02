package edmt.dev.androidcamera2api;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadManager {
    //Instance of the Manager
    private static ThreadManager sInstance = new ThreadManager();
    // Gets the number of available cores
    private static int NUMBER_OF_CORES;
    // Sets the amount of time an idle thread waits before terminating
    private static final int KEEP_ALIVE_TIME = 1;
    // Sets the Time Unit to seconds
    private static TimeUnit KEEP_ALIVE_TIME_UNIT;
    //The ThreadPoolExecutor
    private ThreadPoolExecutor mDecoderThreadPool;

    private ThreadManager() {
        KEEP_ALIVE_TIME_UNIT =  TimeUnit.NANOSECONDS;
        NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
        BlockingQueue<Runnable> mDecodeWorkQueue = new LinkedBlockingQueue<>();
        mDecoderThreadPool = new ThreadPoolExecutor(NUMBER_OF_CORES,NUMBER_OF_CORES,KEEP_ALIVE_TIME,KEEP_ALIVE_TIME_UNIT, mDecodeWorkQueue);
    }

    static public void processFrame(Runnable runnable) {
        sInstance.mDecoderThreadPool.execute(runnable);
    }

}
