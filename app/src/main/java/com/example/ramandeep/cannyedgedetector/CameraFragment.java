package com.example.ramandeep.cannyedgedetector;

import android.app.Fragment;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import static com.example.ramandeep.cannyedgedetector.CameraOperator.BACK_CAMERA;
import static com.example.ramandeep.cannyedgedetector.CameraOperator.CAMERA_REQUEST_CODE;

/**
 * Created by Ramandeep on 2017-11-19.
 */

public class CameraFragment extends Fragment implements SurfaceHolder.Callback, View.OnTouchListener{

    private final static float SWIPE_RANGE = 100f;

    private CameraOperator cameraOperator;
    private CameraFrameProcessor cameraFrameProcessor;
    private SurfaceView displaySurfaceView;
    private int display_orientation;

    private GestureDetector gestureDetector;
    private GestureDetector.SimpleOnGestureListener simpleGestureListener;

    private BackgroundTask cameraInitTask;
    private boolean previewOn = true;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        cameraInitTask = new BackgroundTask("SurfaceInitTask");
        cameraOperator = new CameraOperator(getContext());
        cameraOperator.useCamera(BACK_CAMERA);
        cameraFrameProcessor = new CameraFrameProcessor(getContext());

        simpleGestureListener = new CustomGestureListener();
        gestureDetector = new GestureDetector(getContext(),simpleGestureListener);

        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.camera_frag,container,false);
        displaySurfaceView = view.findViewById(R.id.displaySurfaceView);
        displaySurfaceView.getHolder().addCallback(this);
        view.setOnTouchListener(this);
        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        //after the height and width are determined
        //the allocations can be set with the correct sizes
        //and depending on sensor display_orientation and display display_orientation
        //we can determine whether to flip or not flip coordinates
        display_orientation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        cameraFrameProcessor.setDisplayOrientation(display_orientation);
    }

    @Override
    public void onStart() {
        super.onStart();
        cameraOperator.onStart(getActivity(),this);
        int camera_orientation = cameraOperator.getCameraOrientation(BACK_CAMERA);
        cameraFrameProcessor.setCameraOrientation(camera_orientation);
    }

    @Override
    public void onResume(){
        super.onResume();
        cameraOperator.onResume();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,@NonNull String[] permissions,@NonNull int[] grantResults){
        System.out.println("CameraFragment: onRequestPermissionsResult!");
        switch(requestCode){
            case CAMERA_REQUEST_CODE:
                cameraOperator.openBackCamera(getActivity(),this);
                break;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        cameraOperator.onPause();
        cameraFrameProcessor.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        cameraOperator.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cameraOperator.onDestroy();
        cameraFrameProcessor.onDestroy();
        cameraInitTask.close();
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        System.out.println("DisplaySurface surfaceCreated!");
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        System.out.println("DisplaySurface surfaceChanged!");
        cameraInitTask.submitRunnable(new CustomRunnable(surfaceHolder,width,height));
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        cameraOperator.newCountDownLatch();
    }

    public void edgeDetect() {
        //capture an image
        cameraOperator.stopPreview();
        previewOn = false;
        cameraOperator.captureSingleImage();
    }

    public void showPreview(){
        cameraOperator.startPreview();
        previewOn = true;
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        gestureDetector.onTouchEvent(motionEvent);
        return true;
    }

    private class CustomGestureListener extends GestureDetector.SimpleOnGestureListener{
        public CustomGestureListener(){
            super();
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY){
            float diff = Math.abs(e1.getX() - e2.getX());
            if(!previewOn && diff >= SWIPE_RANGE) {
                showPreview();
                return true;

            }
            return super.onFling(e1,e2,velocityX,velocityY);
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            edgeDetect();
            return true;
        }
    }
    private class CustomRunnable implements Runnable{
        private SurfaceHolder surfaceHolder;
        private int width =-1;
        private int height =-1;

        private CustomRunnable(SurfaceHolder surfaceHolder,int width, int height){
            this.surfaceHolder = surfaceHolder;
            this.width = width;
            this.height = height;
        }
        @Override
        public void run() {
            cameraFrameProcessor.setFrameDimensions(width,height);
            cameraFrameProcessor.InitAllocations();
            cameraFrameProcessor.setOutputSurface(surfaceHolder.getSurface());
            cameraOperator.addOutputSurface(cameraFrameProcessor.getInputSurface());
            cameraOperator.addOutputSurface(cameraFrameProcessor.getEdgeDetectInputSurface());
            cameraOperator.displayWaitLatchCountDown();
        }
    }
}
