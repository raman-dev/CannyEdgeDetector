package com.example.ramandeep.cannyedgedetector;

import android.app.Fragment;

/**
 * Created by Ramandeep on 2018-04-18.
 */

import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;


public class CannyEdgeFragment extends Fragment implements View.OnTouchListener, SurfaceHolder.Callback {


    public static final String TAG = "MyApp";
    private GestureDetector gestureDetector;
    private GestureDetector.SimpleOnGestureListener simpleOnGestureListener;

    private CameraOperationManager cameraOperationManager;
    private CameraFrameProcessor cameraFrameProcessor;
    private BackgroundTask cameraFrameProcessInitTask;

    private static final float SWIPE_RANGE = 75f;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraOperationManager = new CameraOperationManager(getContext());
        cameraFrameProcessor = new CameraFrameProcessor();

        simpleOnGestureListener = new CustomGestureListener();
        gestureDetector = new GestureDetector(getContext(),simpleOnGestureListener);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.canny_frag,container,false);

        SurfaceView displaySurfaceView = view.findViewById(R.id.displaySurfaceView);
        displaySurfaceView.getHolder().addCallback(this);
        view.setOnTouchListener(this);

        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        int displayOrientation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        cameraFrameProcessor.setCameraOrientation(cameraOperationManager.getCameraOrientation());
        cameraFrameProcessor.setDisplayOrientation(displayOrientation);
    }

    @Override
    public void onStart() {
        Log.i(TAG,"onStart!");
        cameraOperationManager.onStart();
        cameraFrameProcessInitTask = new BackgroundTask("CameraFrameProcInitTask");
        super.onStart();
    }

    @Override
    public void onResume() {
        Log.i(TAG,"onResume!");
        super.onResume();
        cameraOperationManager.onResume(getActivity(),this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.i(TAG,"onRequestPermissionsResult!");
        if(requestCode == CameraOperationManager.CAMERA_REQUEST_CODE){
            if(grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED){
                getActivity().finish();
            }
        }else{
            super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        }
    }

    @Override
    public void onPause() {
        Log.i(TAG,"onPause!");
        cameraOperationManager.onPause();
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.i(TAG,"onStop!");
        cameraOperationManager.onStop();
        cameraFrameProcessInitTask.CloseTask();
        cameraFrameProcessor.CloseBackgroundThreads();
        cameraFrameProcessor.DestroyRenderScript();
        super.onStop();
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        gestureDetector.onTouchEvent(motionEvent);
        return true;
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.i(TAG,"surfaceCreated!");
        Rect frame = surfaceHolder.getSurfaceFrame();
        int width = frame.width();
        int height = frame.height();
        if(cameraOperationManager.matchDisplayAndCameraResolution(width,height)){
            cameraFrameProcessInitTask.submitRunnable(new InitRunnable(width,height,surfaceHolder.getSurface()));
        }else{
            throw new RuntimeException("Camera cannot output display size frames!");
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        Log.i(TAG,"surfaceChanged!");


    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        Log.i(TAG,"surfaceDestroyed!");
    }

    /**
     * Gesture listener for picking up single screen taps for taking a picture and edge detecting
     * and swipes for returning to camera preview
     */
    private class CustomGestureListener extends GestureDetector.SimpleOnGestureListener{
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            float diff = Math.abs(e1.getX() - e2.getX());
            if(diff >= SWIPE_RANGE) {
                cameraOperationManager.showPreview();
                return true;

            }
            return super.onFling(e1,e2,velocityX,velocityY);
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            cameraOperationManager.takePicture();
            return true;
        }
    }

    /**
     * Runnable to initialize cameraFrameProcessor and cameraOpManager off the main ui thread
     */
    private class InitRunnable implements Runnable{
        private int width;
        private int height;
        private Surface surface;

        public InitRunnable(int width,int height,Surface surface){
            this.width = width;
            this.height = height;
            this.surface = surface;
        }

        @Override
        public void run() {
            cameraFrameProcessor.setDimensions(width,height);
            cameraFrameProcessor.init(getContext());
            cameraFrameProcessor.setOutputSurface(surface);

            cameraOperationManager.addOutputSurfaceBlocking(cameraFrameProcessor.getCameraOutputSurface());
            cameraOperationManager.addOutputSurfaceBlocking(cameraFrameProcessor.getCameraCannyOutputSurface());
            cameraOperationManager.releaseInitWaitLock();
        }
    }
}
