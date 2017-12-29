package com.example.ramandeep.cannyedgedetector;

/**
 * Created by Ramandeep on 2017-11-12.
 */

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Size;
import android.view.Surface;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static android.hardware.camera2.CameraCharacteristics.LENS_FACING;
import static android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK;
import static android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP;

public class CameraOperator {

    public static final int CAMERA_REQUEST_CODE = 99;
    public static final String BACK_CAMERA = "BackCamera";

    private CameraManager cameraManager;
    private String[] cameraIdList;
    private String backCameraId;//will be set when looked for once
    private HashMap<String, CameraCharacteristics> cameraNameCharMap;
    private List<Surface> surfaceList;
    private CountDownLatch displayWaitLatch;

    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraCaptureSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest.Builder singleImageRequestBuilder;

    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
        }
    };
    private CameraCaptureSession.StateCallback captureSessionCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            mCameraCaptureSession = cameraCaptureSession;
            try {
                mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                singleImageRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            System.out.println("CaptureSession onConfigured!");
            initTask.submitRunnable(cameraPreviewRunnable);
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

            cameraCaptureSession.close();
        }

    };
    private CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            cameraOpen = true;
            initTask.submitRunnable(createCaptureSessionRunnable);
            System.out.println("Camera Opened!");
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            super.onClosed(camera);
            cameraOpen = false;
            System.out.println("Camera Closed!");
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
            cameraOpen = false;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int errorCode) {
            cameraDevice.close();
            cameraOpen = false;
            switch (errorCode) {
                case ERROR_CAMERA_IN_USE:
                    System.out.println("ERROR_CAMERA_IN_USE");
                    break;
                case ERROR_MAX_CAMERAS_IN_USE:
                    System.out.println("ERROR_CAMERA_MAX_CAMERAS_IN_USE");
                    break;
                case ERROR_CAMERA_DISABLED:
                    System.out.println("ERROR_CAMERA_DISABLED");
                    break;
                case ERROR_CAMERA_DEVICE:
                    System.out.println("ERROR_CAMERA_DEVICE");
                    break;
                case ERROR_CAMERA_SERVICE:
                    System.out.println("ERROR_CAMERA_SERVICE");
                    break;
            }
        }
    };

    private Runnable createCaptureSessionRunnable = new Runnable()
    {
        @Override
        public void run() {
            try {
                System.out.println("Waiting to Create CaptureSession...");
                displayWaitLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try {
                System.out.println("Creating CaptureSession...");
                mCameraDevice.createCaptureSession(surfaceList,captureSessionCallback,cameraThreadHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    };

    private BackgroundTask initTask;
    private Handler cameraThreadHandler;
    private HandlerThread cameraThread;

    private boolean useBackCamera = false;
    private boolean cameraOpen = false;

    private Runnable cameraPreviewRunnable = new Runnable() {
        @Override
        public void run() {
            startCameraPreview();
        }
    };

    public CameraOperator(Context context) {

        initTask = new BackgroundTask("InitThread");
        cameraThread = new HandlerThread("CameraOperationThread");
        cameraThread.start();
        cameraThreadHandler = new Handler(cameraThread.getLooper());
        displayWaitLatch = new CountDownLatch(1);

        cameraManager = (CameraManager) context.getSystemService(context.CAMERA_SERVICE);
        cameraIdList = null;
        try {
            cameraIdList = cameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        cameraNameCharMap = new HashMap<>();
        surfaceList = new ArrayList<>();

        //yuv->rgba
        //rgba->flat_gray
        //flat_gray->flat_blur
        //flat_blur->flat_grad_x
        //flat_grad_x->rgba
    }

    public void openBackCamera(Activity activity,Fragment fragment) {
        //check if there is a back camera
        if (hasBackCamera()) {
            //open if possible
            openCamera(backCameraId,activity,fragment);
        }
    }

    private void openCamera(String cameraId, Activity activity,Fragment fragment) {
        try {
            //check if permission for the camera is granted
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                //if not granted request permission
                fragment.requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA_REQUEST_CODE);
                return;
            }
            cameraManager.openCamera(cameraId, cameraStateCallback, cameraThreadHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private boolean hasBackCamera() {
        if(!cameraNameCharMap.isEmpty()){
            //if does not contain it will look for a back camera and then
            //return result
            if(cameraNameCharMap.containsKey(BACK_CAMERA)){
                return true;
            }
        }
        CameraCharacteristics cameraCharacteristics = null;
        for (int i = 0; i < cameraIdList.length; i++) {
            try {
                cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraIdList[i]);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            if(cameraCharacteristics.get(LENS_FACING) == LENS_FACING_BACK){
                cameraNameCharMap.put(BACK_CAMERA,cameraCharacteristics);
                backCameraId = cameraIdList[i];
                HashMap<String,Integer> imageFormatMap = new HashMap<>();
                getStaticConstantNameValueMap(imageFormatMap,ImageFormat.class.getFields());

                StreamConfigurationMap streamConfigMap = cameraCharacteristics.get(SCALER_STREAM_CONFIGURATION_MAP);
                checkOutputFormats(imageFormatMap, streamConfigMap.getOutputFormats(),"OutputFormat: ");
                Size[] sizes = streamConfigMap.getOutputSizes(ImageFormat.YUV_420_888);
                //System.out.println(Arrays.toString(sizes));
                return true;
            }
        }
        return false;
    }
    /*
    * Close the open camera.
    * */
    private void closeCamera() {
        if(mCameraCaptureSession != null){
            mCameraCaptureSession.close();
        }
        if(mCameraDevice!=null){
            mCameraDevice.close();
        }
    }

    /*
    * Set Usage Flag
    * */
    public void useCamera(String message) {
        if(message == BACK_CAMERA){
            useBackCamera = true;
        }
    }

    private void checkOutputFormats(HashMap<String,Integer> formatMap,int[] outputFormats,String outputString) {
        Object[] formatKeys = formatMap.keySet().toArray();
        for (int i = 0; i < formatKeys.length; i++) {
            String formatName = (String)formatKeys[i];
            int value = formatMap.get(formatName);
            if(Arrays.binarySearch(outputFormats,value) >= 0){
                //System.out.println(outputString+" "+formatName);
            }
        }
    }
    private void getStaticConstantNameValueMap(HashMap<String, Integer> nameValueMap, Field[] fields) {
        for (int i = 0; i < fields.length; i++) {
            //only if the value is static
            if(Modifier.isStatic(fields[i].getModifiers())){
                try {
                    nameValueMap.put(fields[i].getName(),fields[i].getInt(null));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

        }
    }

    /*
    * If a camera is open the start the
    * camera preview
    * */
    private void startCameraPreview() {
        //the first surface in the surfacelist is the display
        mPreviewRequestBuilder.addTarget(surfaceList.get(0));
        //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE,CameraMetadata.CONTROL_MODE_AUTO);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        try {
            mCameraCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),mCaptureCallback,cameraThreadHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        singleImageRequestBuilder.addTarget(surfaceList.get(1));
        singleImageRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_AUTO);
        singleImageRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);
        singleImageRequestBuilder.set(CaptureRequest.FLASH_MODE,CaptureRequest.FLASH_MODE_OFF);
    }


    public void onStart(Activity activity,Fragment fragment) {
        //if usage = back camera
        if(useBackCamera){
            if(!cameraOpen){System.out.println("Opening Back Camera!");
                openBackCamera(activity,fragment);
            }
        }
    }

    public void onResume(){
    }

    public void onPause(){
        //close any open cameras
        if(cameraOpen){//check for an open camera
            if(useBackCamera){//check if using the back camera
                System.out.println("Closing Back Camera!");
                closeCamera();
            }
        }
    }

    public void onStop() {
    }

    public void onDestroy(){
        initTask.close();
        if(cameraThreadHandler!=null){
            cameraThreadHandler.removeCallbacks(null);
            cameraThreadHandler = null;
        }
        if(cameraThread !=null){
            if(cameraThread.quit()){
                System.out.println("CameraOperationThread Ended.");
            }
            cameraThread = null;
        }
    }

    public int getCameraOrientation(String camera) {
        switch(camera){
            case BACK_CAMERA:
                CameraCharacteristics cameraCharacteristics = null;
                try {
                    cameraCharacteristics = cameraManager.getCameraCharacteristics(backCameraId);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
                int rotation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                System.out.println("Camera_SensorOrientation = "+rotation);
                return rotation;
        }
        return -1;
    }

    /*
    * Count-down latch for creating a
    * capture session
    * */
    public void displayWaitLatchCountDown() {
        displayWaitLatch.countDown();
    }

    /*
    * New countdown latch for when
    * */
    public void newCountDownLatch(){
        displayWaitLatch = new CountDownLatch(1);
    }

    /*
    * Add an output surface to the surface list
    * that the camera will output to
    * */
    public void addOutputSurface(Surface surface) {
        if(!surfaceList.contains(surface)){
            surfaceList.add(surface);
        }
    }

    /*
    * Stop the camera preview
    * */
    public void stopPreview(){
        try {
            mCameraCaptureSession.stopRepeating();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void captureSingleImage() {
        try {
            mCameraCaptureSession.capture(singleImageRequestBuilder.build(),mCaptureCallback,cameraThreadHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void startPreview() {
        try {
            mCameraCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),mCaptureCallback,cameraThreadHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}
