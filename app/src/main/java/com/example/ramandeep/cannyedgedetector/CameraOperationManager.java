package com.example.ramandeep.cannyedgedetector;

/**
 * Created by Ramandeep on 2018-04-18.
 */

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Surface;

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;

public class CameraOperationManager {

    private CameraManager cameraManager;
    private CameraDevice mCameraDevice;


    private CameraDevice.StateCallback mCameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            Log.i(TAG, "CameraOpened!");
            mCameraDevice = cameraDevice;
            //create capture session but wait until a valid surface is available
            try {
                surfaceList.add(blockingQueue.take());
                Log.i(TAG, "list size =" + surfaceList.size());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            try {
                surfaceList.add(blockingQueue.take());
                Log.i(TAG, "list size =" + surfaceList.size());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            while (blockingQueue.size() > 0) {
                surfaceList.add(blockingQueue.poll());
            }

            if (mCameraDevice != null) {
                try {
                    mCameraDevice.createCaptureSession(surfaceList, mCaptureSessionCallback, cameraThreadHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            Log.i(TAG, "CameraClosed!");
            mCameraDevice = null;
            if (mCameraCaptureSession != null) {
                mCameraCaptureSession.close();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            cameraDevice.close();
        }
    };
    private CameraCaptureSession mCameraCaptureSession = null;
    private CameraCaptureSession.StateCallback mCaptureSessionCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            mCameraCaptureSession = cameraCaptureSession;
            startCameraPreview();
            Log.i(TAG, "CameraCaptureSession Configured!");
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
            Log.i(TAG, "CameraCaptureSession Closed!");
            super.onClosed(session);
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
            Log.i(TAG, "CameraCaptureSession Not Configured!");
        }
    };
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

    private int cameraOrientation;
    private String backCameraID;


    private void startCameraPreview() {
        try {
            previewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        previewRequestBuilder.addTarget(surfaceList.get(0));
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);


        try {
            singleImageRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        singleImageRequestBuilder.addTarget(surfaceList.get(1));
        singleImageRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
        singleImageRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);

        try {
            mCameraCaptureSession.setRepeatingRequest(previewRequestBuilder.build(), mCaptureCallback, cameraThreadHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CaptureRequest.Builder previewRequestBuilder = null;
    private CaptureRequest.Builder singleImageRequestBuilder = null;

    private ArrayList<Surface> surfaceList;

    private static final String TAG = "CamOpManager";

    private Handler cameraThreadHandler;
    private HandlerThread cameraThread;
    private ArrayBlockingQueue<Surface> blockingQueue;

    static final int CAMERA_REQUEST_CODE = 99;

    private static final int MAX_CAMERA_OUTPUT_SURFACES = 3;

    public CameraOperationManager(Context context) {

        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        findBackCamera();
        surfaceList = new ArrayList<>();
        blockingQueue = new ArrayBlockingQueue<>(MAX_CAMERA_OUTPUT_SURFACES);
    }

    /**
     * Check if a back camera exists;if a back facing camera exists then set backCameraID to the back camera
     * id and get the cameras orientation relative to phone natural orientation.
     */
    private void findBackCamera() {
        String[] cameraIDs = null;
        //get all camera ids
        try {
            cameraIDs = cameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        //find the back camera
        for (int i = 0; i < cameraIDs.length; i++) {
            CameraCharacteristics cameraCharacteristics = null;
            try {
                cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraIDs[i]);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            //check for back facing camera
            if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK) {
                backCameraID = cameraIDs[i];
                cameraOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            }
        }
    }

    /**
     * Use this method to add the first surface so the capture session
     * waits to configure until the surface is ready
     *
     * @param surface
     */
    public void addOutputSurfaceBlocking(Surface surface) {
        if (!blockingQueue.contains(surface)) {
            blockingQueue.add(surface);
        }
    }

    /**
     * Capture a single image by stopping the preview capture request and
     * Sending single image capture request.
     */
    public void takePicture() {
        //check if session is null for any calls to take picture
        //before session is configured
        if(mCameraCaptureSession!=null) {
            try {
                mCameraCaptureSession.stopRepeating();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            try {
                mCameraCaptureSession.capture(singleImageRequestBuilder.build(), mCaptureCallback, cameraThreadHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public void onStart() {
        startCameraThread();
    }

    public void onResume(Activity activity, Fragment fragment) {
        openCamera(activity, fragment);
    }

    public void onPause() {
        closeCamera();
    }

    public void onStop() {
        clearOutputSurface();
        stopCameraThread();
    }

    /**
     * Open camera with camera manager
     */
    private void openCamera(Activity activity, Fragment fragment) {

        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            fragment.requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
            return;
        }
        try {
            cameraManager.openCamera(backCameraID, mCameraStateCallback, cameraThreadHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    /**
     * Close the camera and end the camera capture session
     */
    private void closeCamera() {
        if (mCameraCaptureSession != null) {
            //frees up surfaces for use by other drawing apis
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
        }
    }

    /**
     * Stop the camera preview by removing the repeating request
     */
    private void stopPreview() {
        try {
            mCameraCaptureSession.stopRepeating();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Initialize and start the camera thread.
     */
    private void startCameraThread() {
        cameraThread = new HandlerThread("CameraOperationThread");
        cameraThread.start();
        cameraThreadHandler = new Handler(cameraThread.getLooper());
    }

    /**
     * Stop the camera thread.
     */
    private void stopCameraThread() {
        if (cameraThreadHandler != null) {
            cameraThreadHandler.removeCallbacks(null);
            cameraThreadHandler = null;
        }
        if (cameraThread != null) {
            cameraThread.quitSafely();
            cameraThread = null;
        }
    }

    /**
     * Remove any surfaces from output list and blocking queue
     * when activity is no longer visible.
     */
    public void clearOutputSurface() {
        surfaceList.clear();
        blockingQueue.clear();
    }

    public int getCameraOrientation() {
        return cameraOrientation;
    }

    public void showPreview() {
        try {
            mCameraCaptureSession.setRepeatingRequest(previewRequestBuilder.build(), mCaptureCallback, cameraThreadHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}

