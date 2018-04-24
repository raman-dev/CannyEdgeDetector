package com.example.ramandeep.cannyedgedetector;

import android.content.Context;
import android.graphics.ImageFormat;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptGroup;
import android.renderscript.ScriptIntrinsicBlur;
import android.renderscript.ScriptIntrinsicColorMatrix;
import android.renderscript.ScriptIntrinsicConvolve3x3;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.view.Surface;

import java.util.concurrent.Semaphore;

/**
 * Created by Ramandeep on 2017-11-20.
 * Process frames from the camera
 */

public class CameraFrameProcessor {

    private RenderScript renderScriptCxt = null;

    private ScriptIntrinsicYuvToRGB intrinsicYuvToRGB_preview;
    private ScriptIntrinsicYuvToRGB intrinsicYuvToRGB_canny_edge;
    private ScriptIntrinsicBlur intrinsicBlur;
    private ScriptIntrinsicColorMatrix intrinsicColorMatrix;
    private ScriptIntrinsicConvolve3x3 convolve3x3_dx;
    private ScriptIntrinsicConvolve3x3 convolve3x3_dy;
    private ScriptC_CameraFrameProc frameProc;

    private ScriptGroup.Builder2 canny_edge_detection_builder;
    private ScriptGroup.Builder2 camera_preview_builder;
    private ScriptGroup canny_edge_detector;
    private ScriptGroup camera_preview;

    private Allocation camera_input = null;
    private Allocation camera_canny_input = null;
    private Allocation rgba_out = null;

    private BackgroundTask ioTask = null;//use this for surface data io
    private BackgroundTask initTask = null;//use this thread for initializing

    private int x = -1;
    private int y = -1;
    private int h_max = 20;
    private int h_min = 4;
    private int camera_orientation = -1;
    private int display_orientation = -1;

    private Type rgba_input_type;
    private Type rgba_output_type;
    private Type direction_type;
    private Type camera_input_type;
    private Type flat_type;
    private Semaphore initLock;
    private boolean swapDimensions = false;

    private Runnable ProcessPreviewFrame = new Runnable() {
        @Override
        public void run() {
            camera_input.ioReceive();
            camera_preview.execute(0);
            rgba_out.ioSend();
        }
    };

    private Runnable CannyEdgeDetectFrame = new Runnable() {
        @Override
        public void run() {
            camera_canny_input.ioReceive();//get data from camera
            canny_edge_detector.execute(0);
            rgba_out.ioSend();//send data to display
        }
    };
    private Runnable initRunnable = new Runnable() {
        @Override
        public void run() {
            initRenderScript();
            initLock.release();
        }
    };

    public CameraFrameProcessor(){
    }

    /*
    * Set the x y dimensions of the allocations.
    * These dimensions should be display dimensions
    * */
    public void setDimensions(int x,int y){
        this.x = x;
        this.y = y;
    }

    /*
    * Set the orientation of the camera. The orientation
    * will be one of 0,90,180,270;values represent the rotation from
    * the devices natural orientation
    * */
    public void setCameraOrientation(int camera_orientation) {
        this.camera_orientation = camera_orientation;
    }

    /*
    * Represents the devices rotation from its natural orientation
    * */
    public void setDisplayOrientation(int display_orientation) {
        this.display_orientation = display_orientation;
    }

    public void setDisplaySurface(Surface surface){
        rgba_out.setSurface(surface);
    }

    /*
    * A surface from an allocation for which the camera
    * can use to output image data.
    * */
    public Surface getCameraOutputSurface(){
        return camera_input.getSurface();
    }

    /**
     * Return the surface for the camera to output data
     * @return The surface for canny input data
     */
    public Surface getCameraCannyOutputSurface() {
        return camera_canny_input.getSurface();
    }

    /**
     * Initialize all allocations and scripts
     * @param context
     */
    public void init(Context context) {
        renderScriptCxt = RenderScript.create(context);
        ioTask = new BackgroundTask("IOTask");
        initTask = new BackgroundTask("RenderScriptInitTask");
        initLock = new Semaphore(1);
        try {
            initLock.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        initTask.submitRunnable(initRunnable);
        System.out.println("Name =>"+Thread.currentThread().getName());
        try {
            initLock.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void initRenderScript(){
        intrinsicYuvToRGB_preview = ScriptIntrinsicYuvToRGB.create(renderScriptCxt,Element.U8_4(renderScriptCxt));
        intrinsicYuvToRGB_canny_edge = ScriptIntrinsicYuvToRGB.create(renderScriptCxt,Element.U8_4(renderScriptCxt));
        intrinsicBlur = ScriptIntrinsicBlur.create(renderScriptCxt,Element.U8(renderScriptCxt));
        intrinsicColorMatrix = ScriptIntrinsicColorMatrix.create(renderScriptCxt);

        convolve3x3_dx = ScriptIntrinsicConvolve3x3.create(renderScriptCxt,Element.U8(renderScriptCxt));
        convolve3x3_dy = ScriptIntrinsicConvolve3x3.create(renderScriptCxt,Element.U8(renderScriptCxt));

        frameProc = new ScriptC_CameraFrameProc(renderScriptCxt);
        canny_edge_detection_builder = new ScriptGroup.Builder2(renderScriptCxt);
        camera_preview_builder = new ScriptGroup.Builder2(renderScriptCxt);
        initAllocations();
    }

    /**
     * Initialize rgba_in,rgba_out and camera_in allocations
     * used in edge detection and camera preview rotation, if necessary
     */
    private void initAllocations(){
        //need an allocation for camera input
        //intermediate for yuv to rgba
        //and display one for flipping
        initTypes();

        camera_input = Allocation.createTyped(renderScriptCxt,camera_input_type,Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);
        camera_canny_input = Allocation.createTyped(renderScriptCxt,camera_input_type,Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);

        rgba_out = Allocation.createTyped(renderScriptCxt, rgba_output_type,Allocation.USAGE_IO_OUTPUT | Allocation.USAGE_SCRIPT);

        camera_input.setOnBufferAvailableListener(new Allocation.OnBufferAvailableListener() {
            @Override
            public void onBufferAvailable(Allocation allocation) {
                ioTask.submitRunnable(ProcessPreviewFrame);
            }
        });

        camera_canny_input.setOnBufferAvailableListener(new Allocation.OnBufferAvailableListener() {
            @Override
            public void onBufferAvailable(Allocation allocation) {
                ioTask.submitRunnable(CannyEdgeDetectFrame);
            }
        });

        initScripts();
    }

    /**
     * Initialize all the types used in allocation creation
     * using the dimensions from call to setDimensions(x,y)
     */
    private void initTypes() {
        System.out.println("camera_orientation = "+camera_orientation);
        System.out.println("display_orientation = "+display_orientation);
        swapDimensions = false;
        if(camera_orientation != display_orientation){
            if(Math.abs(camera_orientation - display_orientation) != 180){
                swapDimensions = true;
            }
        }
        if(swapDimensions){
            //swap the dimensions so it rotates upright
            initTypes(y,x);
        }
        else{
            initTypes(x,y);
        }

    }

    private void initTypes(int width, int height) {
        Type.Builder camera_input_type_builder = new Type.Builder(renderScriptCxt, Element.U8(renderScriptCxt));//yuv is u8 for each element
        camera_input_type_builder.setYuvFormat(ImageFormat.YUV_420_888);
        camera_input_type_builder.setX(width);
        camera_input_type_builder.setY(height);

        camera_input_type = camera_input_type_builder.create();
        rgba_input_type = Type.createXY(renderScriptCxt,Element.U8_4(renderScriptCxt),width,height);
        flat_type = Type.createXY(renderScriptCxt,Element.U8(renderScriptCxt),width,height);
        direction_type = Type.createXY(renderScriptCxt, Element.F32(renderScriptCxt),width,height);
        if(swapDimensions){
            rgba_output_type = Type.createXY(renderScriptCxt,Element.U8_4(renderScriptCxt),height,width);
        }else{
            rgba_output_type = Type.createXY(renderScriptCxt,Element.U8_4(renderScriptCxt),width,height);
        }

    }

    private void initScripts(){
        intrinsicYuvToRGB_preview.setInput(camera_input);
        intrinsicYuvToRGB_canny_edge.setInput(camera_canny_input);
        intrinsicColorMatrix.setGreyscale();
        intrinsicBlur.setRadius(5f);
        //
        float[] filter_dx = {1f,0f,-1f,
                1f,0f,-1f,
                1f,0f,-1f};
        float[] filter_dy = {1f, 1f, 1f,
                0f, 0f, 0f,
                -1f,-1f,-1f};
        convolve3x3_dx.setCoefficients(filter_dx);
        convolve3x3_dy.setCoefficients(filter_dy);
        //yuv->rgba
        frameProc.set_display_x_max(x);
        frameProc.set_display_y_max(y);
        //display is rotated
        if(swapDimensions) {
            frameProc.set_camera_y_max(x);
            frameProc.set_camera_x_max(y);
        }else{
            frameProc.set_camera_y_max(y);
            frameProc.set_camera_x_max(x);
        }
        frameProc.set_h_max(h_max);
        frameProc.set_h_min(h_min);
        frameProc.set_rgba_out(rgba_out);
        //rgba->rotated_rgba
        //rotated_rgba->send out to display
        camera_preview_builder.addInput();
        //yuv->rgba
        ScriptGroup.Closure yuv_to_rgba = camera_preview_builder.addKernel(intrinsicYuvToRGB_preview.getKernelID(), rgba_input_type);
        ScriptGroup.Future rgba = yuv_to_rgba.getReturn();
        //flip_rgba
        ScriptGroup.Closure rgba_to_flip_rgba = camera_preview_builder.addKernel(frameProc.getKernelID_rotate_90_ccw(), rgba_output_type,rgba);
        ScriptGroup.Future flip_future = rgba_to_flip_rgba.getReturn();
        camera_preview = camera_preview_builder.create("CameraPreviewProcess",flip_future);
        initCannyScripts();
    }

    /**
     * Create the canny edge detection kernels
     */
    private void initCannyScripts() {
        canny_edge_detection_builder.addInput();
        //yuv->rgba
        ScriptGroup.Closure yuv_to_rgba = canny_edge_detection_builder.addKernel(intrinsicYuvToRGB_canny_edge.getKernelID(),rgba_input_type);
        ScriptGroup.Future rgba = yuv_to_rgba.getReturn();
        //rgba->gray_scale
        ScriptGroup.Closure gray_closure = canny_edge_detection_builder.addKernel(intrinsicColorMatrix.getKernelID(),rgba_input_type,rgba);
        ScriptGroup.Future gray = gray_closure.getReturn();
        //gray->gray_flat
        ScriptGroup.Closure gray_flat_closure = canny_edge_detection_builder.addKernel(frameProc.getKernelID_rgba_to_flat(),flat_type,gray);
        ScriptGroup.Future gray_flat = gray_flat_closure.getReturn();
        //gray_flat->blur_flat
        ScriptGroup.Binding blur_input_binding = new ScriptGroup.Binding(intrinsicBlur.getFieldID_Input(),gray_flat);
        ScriptGroup.Closure blur_flat_closure = canny_edge_detection_builder.addKernel(intrinsicBlur.getKernelID(),flat_type,blur_input_binding);
        ScriptGroup.Future blur_flat = blur_flat_closure.getReturn();
        //blur_flat->grad_x
        ScriptGroup.Binding grad_x_input_binding = new ScriptGroup.Binding(convolve3x3_dx.getFieldID_Input(),blur_flat);
        ScriptGroup.Closure grad_x_closure = canny_edge_detection_builder.addKernel(convolve3x3_dx.getKernelID(),flat_type,grad_x_input_binding);
        ScriptGroup.Future grad_x = grad_x_closure.getReturn();
        //blur_flat->grad_y
        ScriptGroup.Binding grad_y_input_binding = new ScriptGroup.Binding(convolve3x3_dy.getFieldID_Input(),blur_flat);
        ScriptGroup.Closure grad_y_closure = canny_edge_detection_builder.addKernel(convolve3x3_dy.getKernelID(),flat_type,grad_y_input_binding);
        ScriptGroup.Future grad_y = grad_y_closure.getReturn();
        //grad_x + grad_y->grad_mag
        ScriptGroup.Closure grad_mag_closure = canny_edge_detection_builder.addKernel(frameProc.getKernelID_gradient_magnitude(),flat_type,grad_x,grad_y);
        ScriptGroup.Future grad_mag = grad_mag_closure.getReturn();
        //grad_x + grad_y->grad_direction
        ScriptGroup.Closure grad_direction_closure = canny_edge_detection_builder.addKernel(frameProc.getKernelID_gradient_direction(),direction_type,grad_x,grad_y);
        ScriptGroup.Future grad_direction = grad_direction_closure.getReturn();
        //grad_mag+grad_direction->non_max_suppressed
        ScriptGroup.Binding gradient_global_binding = new ScriptGroup.Binding(frameProc.getFieldID_gradient(),grad_mag);
        ScriptGroup.Closure non_max_suppressed_closure = canny_edge_detection_builder.addKernel(frameProc.getKernelID_non_max_suppression(),flat_type,grad_mag,grad_direction,gradient_global_binding);
        ScriptGroup.Future non_max_suppressed = non_max_suppressed_closure.getReturn();
        //non_max_suppressed->hysteresis_thresholding in 2 parts
        ScriptGroup.Closure hthresh_closure_1 = canny_edge_detection_builder.addKernel(frameProc.getKernelID_hthreshold_part1(),flat_type,non_max_suppressed);
        ScriptGroup.Future hysteresis_threshold_1 = hthresh_closure_1.getReturn();
        //
        ScriptGroup.Binding hthresh_1_binding = new ScriptGroup.Binding(frameProc.getFieldID_hthresh1(),hysteresis_threshold_1);
        ScriptGroup.Closure hthresh_closure_2 = canny_edge_detection_builder.addKernel(frameProc.getKernelID_hthreshold_part2(),flat_type,hysteresis_threshold_1,hthresh_1_binding);
        ScriptGroup.Future hysteresis_threshold = hthresh_closure_2.getReturn();
        //unflatten
        ScriptGroup.Closure flat_to_rgba_closure = canny_edge_detection_builder.addKernel(frameProc.getKernelID_flat_to_rgba(),rgba_input_type,hysteresis_threshold);
        ScriptGroup.Future flat_to_rgba = flat_to_rgba_closure.getReturn();
        //flip_rgba
        if(swapDimensions) {
            ScriptGroup.Closure rgba_to_flip_rgba = canny_edge_detection_builder.addKernel(frameProc.getKernelID_rotate_90_ccw(), rgba_output_type, flat_to_rgba);
            ScriptGroup.Future flip_future = rgba_to_flip_rgba.getReturn();
            canny_edge_detector = canny_edge_detection_builder.create("CannyEdgeDetector", flip_future);
        }else {
            canny_edge_detector = canny_edge_detection_builder.create("CannyEdgeDetector", flat_to_rgba);
        }
    }

    public void CloseBackgroundThreads() {
        ioTask.CloseTask();
    }

    /*
    * Destroy all render script objects and
    *
    * */
    public void DestroyRenderScript(){
        //destroy renderscript
        rgba_out.destroy();

        RenderScript.releaseAllContexts();
    }


}

