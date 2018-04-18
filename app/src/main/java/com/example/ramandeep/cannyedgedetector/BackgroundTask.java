package com.example.ramandeep.cannyedgedetector;

import android.os.Handler;
import android.os.HandlerThread;


/**
 * Created by Ramandeep on 2017-11-12.
 */

public class BackgroundTask {
    private Handler handler;
    private HandlerThread handlerThread;
    private String name;

    public BackgroundTask(String name){
        this.name = name;
        handlerThread = new HandlerThread(name);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    /*
    * Post a runnable to the thread.
    * */
    public void submitRunnable(Runnable runnable){
        handler.post(runnable);
    }

    /*
    * Remove callbacks to the thread handler and
    * stop the thread.
    * */
    public void CloseTask(){
        if(handler!=null){
            handler.removeCallbacks(null);
            handler = null;
        }
        if(handlerThread!=null){
            handlerThread.quitSafely();
            try {
                handlerThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            handlerThread = null;
            System.out.println(name+"Closed!");
        }
    }
}
