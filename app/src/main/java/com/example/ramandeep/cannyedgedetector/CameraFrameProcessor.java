package com.example.ramandeep.cannyedgedetector;

import android.content.Context;
import android.graphics.ImageFormat;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptGroup;
import android.renderscript.ScriptIntrinsicBlur;
import android.renderscript.ScriptIntrinsicConvolve3x3;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.view.Surface;

import com.example.ramandeep.cannyedgedetector2.ScriptC_CannyEdgeUcharOps;
import com.example.ramandeep.cannyedgedetector2.ScriptC_FrameProcessor;

/**
 * Created by Ramandeep on 2017-11-20.
 * Process frames from the camera
 */

public class CameraFrameProcessor {

    private int width = -1;//width of display surface
    private int height = -1;//height of display surface
    private int display_orientation = -1;
    private int camera_orientation = -1;

    private boolean swapXY = false;

    private RenderScript renderScript;
    private ScriptIntrinsicYuvToRGB intrinsicYuvToRGB;//used for conversion from camera yuv to rgb
    private ScriptIntrinsicBlur intrinsicBlur;//used to smooth image
    private ScriptIntrinsicConvolve3x3 convolve3x3_grady;
    private ScriptIntrinsicConvolve3x3 convolve3x3_gradx;
    private ScriptC_FrameProcessor frameProcessor;//custom script to process frames
    private ScriptC_CannyEdgeUcharOps cannyEdgeUcharOps;

    private Allocation camera_input;//frames are produced by the camera into camera_input
    private Allocation camera_ce_input;//get a single frame from the camera to edge detect
    private Allocation rgba_out;//this allocation is used to output to the display

    private Type rgba_in_type;//the type that is used in for processing
    private Type rgba_out_type;//the type that the display takes
    private Type rgba_flat_type;//u8 flat
    private Type flat_float_type;

    private Type.Builder camera_input_type_builder;

    private ScriptGroup.Builder2 builder2;
    private ScriptGroup.Builder2 ce_builder2;
    private ScriptGroup normalScriptGroup;
    private ScriptGroup edgeDetectScriptGroup;

    private BackgroundTask ioTask;
    private Runnable FrameAvailableRunnable = new Runnable() {
        @Override
        public void run() {
            camera_input.ioReceive();
            normalScriptGroup.execute(0);
            rgba_out.ioSend();
        }
    };

    private Runnable EdgeDetectFrameRunnable = new Runnable() {
        @Override
        public void run() {
            camera_ce_input.ioReceive();
            edgeDetectScriptGroup.execute(0);
            rgba_out.ioSend();
        }
    };

    public CameraFrameProcessor(Context context){
        ioTask = new BackgroundTask("CameraFrameProcessorTask");
        renderScript = RenderScript.create(context);

        intrinsicYuvToRGB = ScriptIntrinsicYuvToRGB.create(renderScript,Element.U8_4(renderScript));
        intrinsicBlur = ScriptIntrinsicBlur.create(renderScript,Element.U8(renderScript));
        convolve3x3_grady = ScriptIntrinsicConvolve3x3.create(renderScript,Element.U8(renderScript));
        convolve3x3_gradx = ScriptIntrinsicConvolve3x3.create(renderScript,Element.U8(renderScript));

        frameProcessor = new ScriptC_FrameProcessor(renderScript);
        cannyEdgeUcharOps = new ScriptC_CannyEdgeUcharOps(renderScript);

        builder2 = new ScriptGroup.Builder2(renderScript);
        ce_builder2 = new ScriptGroup.Builder2(renderScript);

        camera_input_type_builder = new Type.Builder(renderScript, Element.U8(renderScript));//for yuv type
        camera_input_type_builder.setYuvFormat(ImageFormat.YUV_420_888);//we know camera outputs yuv_420_888

        float[] prewitt_dy = {
                1f, 1f, 1f,
                0f, 0f, 0f,
                -1f, -1f, -1f
        };
        convolve3x3_grady.setCoefficients(prewitt_dy);
        float[] prewitt_dx = {
                1f, 0f, -1f,
                1f, 0f, -1f,
                1f, 0f, -1f
        };
        convolve3x3_gradx.setCoefficients(prewitt_dx);
        intrinsicBlur.setRadius(10f);
    }

    /*
    * Frame Dimensions are determined by the display surface size
    * if they need to be flipped user must specify
    * */
    public void setFrameDimensions(int width,int height){
        this.width = width;
        this.height = height;
    }

    /*
    * Set the display display_orientation
    * */
    public void setDisplayOrientation(int display_orientation){
        this.display_orientation = display_orientation;
    }

    /*
    * Set the camera display_orientation
    * */
    public void setCameraOrientation(int camera_orientation){
        this.camera_orientation = camera_orientation;
    }

    /*
    * Initialize allocations to process frames
    * */
    public void InitAllocations(){
        /*
        * If the orientations are known then we can know
        * whether or not to flip dimensions or not
        * */
        if(camera_orientation!=-1 && display_orientation != -1){
            swapXY = checkSensorAndDisplayAlignment();
            //if not aligned then xy needs to be swapped
            if(!swapXY){
                //do not flip xy with width and height
                initTypes(width,height);
            }
            else{
                //flip xy with width and height
                initTypes(height,width);
            }
            //output type will always match the display width and height
            System.out.println("width,height = "+width+", "+height);
            rgba_out_type = Type.createXY(renderScript,Element.U8_4(renderScript),width,height);
        }else{
            throw new RuntimeException("Orientations not set!");
        }
        //after types are determined
        camera_input = Allocation.createTyped(renderScript, camera_input_type_builder.create(),Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);
        //camera_input.setName("camera_input");
        camera_ce_input = Allocation.createTyped(renderScript,camera_input_type_builder.create(),Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);
        rgba_out = Allocation.createTyped(renderScript,rgba_out_type,Allocation.USAGE_IO_OUTPUT | Allocation.USAGE_SCRIPT);
        //when the frame is data is available receive and process it
        camera_input.setOnBufferAvailableListener(new Allocation.OnBufferAvailableListener() {
            @Override
            public void onBufferAvailable(Allocation allocation) {
                //allocation.ioReceive();
                ioTask.submitRunnable(FrameAvailableRunnable);
            }
        });
        camera_ce_input.setOnBufferAvailableListener(new Allocation.OnBufferAvailableListener() {
            @Override
            public void onBufferAvailable(Allocation allocation) {
                //allocation.ioReceive();
                ioTask.submitRunnable(EdgeDetectFrameRunnable);
            }
        });

        //initialize allocations
        initScripts();
    }

    private void initTypes(int width, int height) {
        camera_input_type_builder.setX(width);
        camera_input_type_builder.setY(height);
        rgba_in_type = Type.createXY(renderScript,Element.U8_4(renderScript),width,height);
        rgba_flat_type = Type.createXY(renderScript,Element.U8(renderScript),width,height);
        flat_float_type = Type.createXY(renderScript,Element.F32(renderScript),width,height);
        frameProcessor.set_x_max(width);
        frameProcessor.set_y_max(height);
        cannyEdgeUcharOps.set_x_max(width);
        cannyEdgeUcharOps.set_y_max(height);
    }

    private void initScripts() {
        builder2.addInput();
        ScriptGroup.Binding yuv_input_binding = new ScriptGroup.Binding(intrinsicYuvToRGB.getFieldID_Input(),camera_input);
        ScriptGroup.Binding rgba_flip_xy_output_binding = new ScriptGroup.Binding(frameProcessor.getFieldID_rgba_out(),rgba_out);
        //yuv->rgba
        ScriptGroup.Closure yuv_to_rgba_closure = builder2.addKernel(intrinsicYuvToRGB.getKernelID(),rgba_in_type,yuv_input_binding);
        ScriptGroup.Future yuv_to_rgba_future = yuv_to_rgba_closure.getReturn();
        //rgba-> rgba_flip_xy
        ScriptGroup.Closure rgba_to_flip_xy_closure = builder2.addKernel(frameProcessor.getKernelID_copy_dimension_flipped(),rgba_out_type,yuv_to_rgba_future,rgba_flip_xy_output_binding);
        ScriptGroup.Future rgba_to_flip_xy_future = rgba_to_flip_xy_closure.getReturn();
        normalScriptGroup = builder2.create("normal_script_group",rgba_to_flip_xy_future);
        initCEScripts();
    }

    private void initCEScripts() {
        ce_builder2.addInput();
        ScriptGroup.Binding yuv_input_binding = new ScriptGroup.Binding(intrinsicYuvToRGB.getFieldID_Input(),camera_ce_input);
        ScriptGroup.Binding rgba_flip_xy_output_binding = new ScriptGroup.Binding(frameProcessor.getFieldID_rgba_out(),rgba_out);
        //yuv->rgba
        ScriptGroup.Closure yuv_to_rgba_closure = ce_builder2.addKernel(intrinsicYuvToRGB.getKernelID(),rgba_in_type,yuv_input_binding);
        ScriptGroup.Future yuv_to_rgba_future = yuv_to_rgba_closure.getReturn();
        //rgba->gray_flat
        ScriptGroup.Closure rgba_to_gray_flat_closure = ce_builder2.addKernel(frameProcessor.getKernelID_rgbaToGrayScale_flat(),rgba_flat_type,yuv_to_rgba_future);
        ScriptGroup.Future rgba_to_gray_flat_future = rgba_to_gray_flat_closure.getReturn();
        //gray_flat->gray_smooth_flat
        ScriptGroup.Binding gray_flat_to_gray_smooth_input_binding = new ScriptGroup.Binding(intrinsicBlur.getFieldID_Input(),rgba_to_gray_flat_future);
        ScriptGroup.Closure gray_flat_to_gray_smooth_closure = ce_builder2.addKernel(intrinsicBlur.getKernelID(),rgba_flat_type,gray_flat_to_gray_smooth_input_binding);
        ScriptGroup.Future gray_flat_to_gray_smooth_future = gray_flat_to_gray_smooth_closure.getReturn();
        //gray_smooth->grad_y
        ScriptGroup.Binding grad_y_input_binding = new ScriptGroup.Binding(convolve3x3_grady.getFieldID_Input(),gray_flat_to_gray_smooth_future);
        ScriptGroup.Closure grad_y_closure = ce_builder2.addKernel(convolve3x3_grady.getKernelID(),rgba_flat_type,grad_y_input_binding);
        ScriptGroup.Future grad_y_future = grad_y_closure.getReturn();
        //gray_smooth->grad_x
        ScriptGroup.Binding grad_x_input_binding = new ScriptGroup.Binding(convolve3x3_gradx.getFieldID_Input(),gray_flat_to_gray_smooth_future);
        ScriptGroup.Closure grad_x_closure = ce_builder2.addKernel(convolve3x3_gradx.getKernelID(),rgba_flat_type,grad_x_input_binding);
        ScriptGroup.Future grad_x_future = grad_x_closure.getReturn();
        //grad x + grad y = grad
        ScriptGroup.Closure gradient_magnitude_closure = ce_builder2.addKernel(cannyEdgeUcharOps.getKernelID_gradient_magnitude(),rgba_flat_type,grad_x_future,grad_y_future);
        ScriptGroup.Future gradient_magnitude_future = gradient_magnitude_closure.getReturn();
        //grad x + grad y = grad_direction
        ScriptGroup.Closure gradient_direction_closure = ce_builder2.addKernel(cannyEdgeUcharOps.getKernelID_gradient_direction(),flat_float_type,grad_x_future,grad_y_future);
        ScriptGroup.Future gradient_direction_future = gradient_direction_closure.getReturn();
        //grad_magnitude + gradient_direction -> non_max_suppression
        ScriptGroup.Binding gradient_magnitude_binding = new ScriptGroup.Binding(cannyEdgeUcharOps.getFieldID_gradient(),gradient_magnitude_future);
        ScriptGroup.Closure non_max_suppression_closure= ce_builder2.addKernel(cannyEdgeUcharOps.getKernelID_non_max_suppression(),rgba_flat_type,gradient_magnitude_future,gradient_direction_future,gradient_magnitude_binding);
        ScriptGroup.Future non_max_suppression_future = non_max_suppression_closure.getReturn();
        //non_max_suppression->threshold
        ScriptGroup.Closure threshold_closure = ce_builder2.addKernel(cannyEdgeUcharOps.getKernelID_canny_threshold(),rgba_flat_type,non_max_suppression_future);
        ScriptGroup.Future threshold_future = threshold_closure.getReturn();
        //flat_type->rgba
        ScriptGroup.Closure flat_to_rgba_closure = ce_builder2.addKernel(frameProcessor.getKernelID_flat_to_rgba(),rgba_in_type,threshold_future);
        ScriptGroup.Future flat_to_rgba_future = flat_to_rgba_closure.getReturn();
        //rgba->rgba_flip_xy
        ScriptGroup.Closure rgba_to_flip_xy_closure = ce_builder2.addKernel(frameProcessor.getKernelID_copy_dimension_flipped(),rgba_out_type,flat_to_rgba_future,rgba_flip_xy_output_binding);
        ScriptGroup.Future rgba_to_flip_xy_future = rgba_to_flip_xy_closure.getReturn();
        edgeDetectScriptGroup = ce_builder2.create("edge_detect_sg",rgba_to_flip_xy_future);
    }


    /*
    * Return the input surface that the frame
    * processor will use as its input
    * */
    public Surface getInputSurface(){
        return camera_input.getSurface();
    }

    public Surface getEdgeDetectInputSurface(){
        return camera_ce_input.getSurface();
    }
    /*
    * Set the output surface to send data to
    * */
    public void setOutputSurface(Surface surface) {
        rgba_out.setSurface(surface);
    }

    private boolean checkSensorAndDisplayAlignment() {
        if(camera_orientation != display_orientation){
            //find out which way to flip if we have to
            //align output image to sensor
            switch(display_orientation){
                case Surface.ROTATION_0:
                case Surface.ROTATION_180:
                    //if the display is perpendicular to the camera
                    //then xy must be swapped
                    if(camera_orientation == 90 || camera_orientation == 270) {
                        return true;
                    }
                    break;
                case Surface.ROTATION_90:
                case Surface.ROTATION_270:
                    if(camera_orientation == 0 || camera_orientation == 180) {
                        return true;
                    }
                    break;
            }
        }
        return false;
    }

    public void onPause() {
        //stop processing frames
        ioTask.clear();
    }

    /*
    * Should be called from fragment to cleanup stuff
    * */
    public void onDestroy(){
        //stop processing and end thread
        ioTask.close();
        //destroy allocations
        camera_input.destroy();
        camera_ce_input.destroy();
        //rgba_in.destroy();
        rgba_out.destroy();
        //destroy scripts
        frameProcessor.destroy();
        intrinsicYuvToRGB.destroy();
        normalScriptGroup.destroy();
        if(edgeDetectScriptGroup != null){
            edgeDetectScriptGroup.destroy();
        }
        renderScript.destroy();
    }
}
