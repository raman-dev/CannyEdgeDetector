package com.example.ramandeep.cannyedgedetector;

import android.os.Handler;
import android.os.HandlerThread;


/**
 * Created by Ramandeep on 2017-11-12.
 */

public class BackgroundTask {
    private HandlerThread thread;
    private Handler threadHandler;

    private String taskName;


    public BackgroundTask(String taskName){
        this.taskName = taskName;
        thread = new HandlerThread(taskName);
        thread.start();
        threadHandler = new Handler(thread.getLooper());
    }

    public void submitRunnableWithDelay(Runnable runnable,long delay){
        threadHandler.postDelayed(runnable,delay);
    }

    public void submitRunnable(Runnable runnable){
        threadHandler.post(runnable);
    }

    public void close(){
        if(threadHandler != null){
            threadHandler.removeCallbacks(null);
        }
        if(thread != null){
            if(thread.quit()){
                thread = null;
                threadHandler = null;
                System.out.println(taskName + " Ended.");
            }
        }
    }

    //remove all cancel all tasks
    public void clear() {
        threadHandler.removeCallbacks(null);
    }
}
