#pragma version(1)
#pragma rs java_package_name(com.example.ramandeep.cannyedgedetector2)
#pragma rs_fp_relaxed

const float4 grayScaleValues = (float4){0.2126,0.7152,0.0722,1};
uchar threshold = 5;

rs_allocation rgba_out;
rs_allocation gradient;
rs_allocation gradient_mag_direction;

int x_max;
int y_max;

static uchar getBottomRightTopLeft(const uchar in, const uint32_t x,const uint32_t y){
    int bottom_rightx = x + 1;
    int bottom_righty = y + 1;

    int top_leftx = x - 1;
    int top_lefty = y - 1;

    uchar top_left_val = 0;
    uchar bottom_right_val = 0;

    if(top_leftx >= 0 && top_lefty >= 0){
        top_left_val = *(uchar*)rsGetElementAt(gradient,top_leftx,top_lefty);
    }
    if(bottom_rightx < x_max && bottom_righty < y_max){
        bottom_right_val = *(uchar*)rsGetElementAt(gradient,bottom_rightx,bottom_righty);
    }
    if(in >= top_left_val && in >= bottom_right_val ){
        return in;
    }
    else{
        return 0;
    }
}

static uchar getTopRightBottomLeft(const uchar in, const uint32_t x, const uint32_t y){
    int top_rightx = x + 1;
    int top_righty = y - 1;

    int bottom_leftx = x-1;
    int bottom_lefty = y+1;

    uchar top_right_val = 0;
    uchar bottom_left_val = 0;

    if(top_rightx < x_max && top_righty >= 0){
        top_right_val = *(uchar*)rsGetElementAt(gradient,top_rightx,top_righty);
    }
    if(bottom_leftx >= 0 && bottom_lefty < y_max){
        bottom_left_val = *(uchar*)rsGetElementAt(gradient,bottom_leftx,bottom_lefty);
    }
    if(in >= top_right_val && in >= bottom_left_val ){
        return in;
    }
    else{
        return (uchar){0};
    }
}

static uchar getTopBottom(const uchar in, const uint32_t x, const uint32_t y){
    int top = y - 1;
    int bottom = y + 1;

    uchar top_val = 0;
    uchar bottom_val = 0;

    if(top <= 0){
        top_val = *(uchar*)rsGetElementAt(gradient,x,top);
    }
    if(bottom > y_max){
        bottom_val = *(uchar*)rsGetElementAt(gradient,x,bottom);
    }
    if(in >= top && in >= bottom ){
        return in;
    }
    else{
        return 0;
    }
}

//compare pixel value with values parallel to its gradient direction
//if it is the largest of the three then it will be kept if not it
//will be suppressed
static uchar getRightLeft(const uchar in,const uint32_t x,const uint32_t y){

   int right = x + 1;
   int left = x - 1;
   uchar right_val = 0;
   uchar left_val = 0;

   if(right < x_max){
      right_val = *(uchar*)rsGetElementAt(gradient,right,y);
   }
   if(left >= 0){
      left_val = *(uchar*)rsGetElementAt(gradient,left,y);
   }
   if(in >= left_val && in >= right_val){
      return in;
   }
   else{
      return 0;
   }
}

static float getBottomRightTopLeft_alt(const float in, const uint32_t x,const uint32_t y){
    int bottom_rightx = x + 1;
    int bottom_righty = y + 1;

    int top_leftx = x - 1;
    int top_lefty = y - 1;

    float top_left_val = 0;
    float bottom_right_val = 0;

    if(top_leftx >= 0 && top_lefty >= 0){
        top_left_val = (*(float2*)rsGetElementAt(gradient_mag_direction,top_leftx,top_lefty)).x;
    }
    if(bottom_rightx < x_max && bottom_righty < y_max){
        bottom_right_val = (*(float2*)rsGetElementAt(gradient_mag_direction,bottom_rightx,bottom_righty)).x;
    }
    if(in >= top_left_val && in >= bottom_right_val ){
        return in;
    }
    else{
        return 0;
    }
}

static float getTopRightBottomLeft_alt(const float in, const uint32_t x, const uint32_t y){
    int top_rightx = x + 1;
    int top_righty = y - 1;

    int bottom_leftx = x-1;
    int bottom_lefty = y+1;

    float top_right_val = 0;
    float bottom_left_val = 0;

    if(top_rightx < x_max && top_righty >= 0){
        top_right_val = (*(float2*)rsGetElementAt(gradient_mag_direction,top_rightx,top_righty)).x;
    }
    if(bottom_leftx >= 0 && bottom_lefty < y_max){
        bottom_left_val = (*(float2*)rsGetElementAt(gradient_mag_direction,bottom_leftx,bottom_lefty)).x;
    }
    if(in >= top_right_val && in >= bottom_left_val ){
        return in;
    }
    else{
        return 0;
    }
}

static float getTopBottom_alt(const float in, const uint32_t x, const uint32_t y){
    int top = y - 1;
    int bottom = y + 1;

    float top_val = 0;
    float bottom_val = 0;

    if(top <= 0){
        top_val = (*(float2*)rsGetElementAt(gradient_mag_direction,x,top)).x;
    }
    if(bottom > y_max){
        bottom_val = (*(float2*)rsGetElementAt(gradient_mag_direction,x,bottom)).x;
    }
    if(in >= top && in >= bottom ){
        return in;
    }
    else{
        return 0;
    }
}

static float getRightLeft_alt(const float in,const uint32_t x,const uint32_t y){

   int right = x + 1;
   int left = x - 1;
   float right_val = 0;
   float left_val = 0;

   if(right < x_max){
      right_val = (*(float2*)rsGetElementAt(gradient_mag_direction,right,y)).x;
   }
   if(left >= 0){
      left_val = (*(float2*)rsGetElementAt(gradient_mag_direction,left,y)).x;
   }
   if(in >= left_val && in >= right_val){
      return in;
   }
   else{
      return 0;
   }
}

float RS_KERNEL non_max_suppress_alt(const float2 in, const uint32_t x,const uint32_t y){
        int flag = (int)in.y/30.0f;
        float magnitude = in.x;
        switch(flag)
        {
            case 0:
            //right-left
              return getRightLeft_alt(magnitude,x,y);
            case 1:
            //bottomright-topleft
              return getBottomRightTopLeft_alt(magnitude,x,y);
            case 2:
            //bottom-top
              return getTopBottom_alt(magnitude,x,y);
            case 3:
            //bottomleft-topright
              return getTopRightBottomLeft_alt(magnitude,x,y);
            case 4:
            //left-right
             return getRightLeft_alt(magnitude,x,y);
            case 5:
            //topleft-bottomright
              return getBottomRightTopLeft_alt(magnitude,x,y);
            case 6:
            //top-bottom
              return getTopBottom_alt(magnitude,x,y);
            case 7:
            //topright-bottomleft
              return getTopRightBottomLeft_alt(magnitude,x,y);
        }
       return 0;
}

uchar RS_KERNEL non_max_suppression(const uchar in,const float angle,const uint32_t x,const uint32_t y){
       //check if the current pixel is the
       //brightest along its gradient direction
       //between the two pixels that are in the same direction
       //TODO: divide angle by 30 and then switch case from 0-7
        int flag = (int)angle/30.0f;
        switch(flag)
        {
            case 0:
            //right-left
              return getRightLeft(in,x,y);
            case 1:
            //bottomright-topleft
              return getBottomRightTopLeft(in,x,y);
            case 2:
            //bottom-top
              return getTopBottom(in,x,y);
            case 3:
            //bottomleft-topright
              return getTopRightBottomLeft(in,x,y);
            case 4:
            //left-right
             return getRightLeft(in,x,y);
            case 5:
            //topleft-bottomright
              return getBottomRightTopLeft(in,x,y);
            case 6:
            //top-bottom
              return getTopBottom(in,x,y);
            case 7:
            //topright-bottomleft
              return getTopRightBottomLeft(in,x,y);
        }
       return 0;
}

/*
Calculate the gradient
*/
uchar RS_KERNEL gradient_flat(const uchar gradx,const uchar grady){
    float gx_float = rsUnpackColor8888((uchar4){gradx,0,0,255}).x;
    float gy_float = rsUnpackColor8888((uchar4){grady,0,0,255}).x;
    //TODO: remember to test with non float value
    /*float a = gradx*gradx;
    float b = grady*grady;
    float value = sqrt(a+b);*/

    float gx_squared = gx_float*gx_float;
    float gy_squared = gy_float*gy_float;
    float value = sqrt(gx_squared+gy_squared);

    return rsPackColorTo8888((float4){value,value,value,1.0f}).x;
}

/*
Get the gradient direction
*/
float RS_KERNEL gradient_direction(const uchar gradx, const uchar grady){
    float gx_float = rsUnpackColor8888((uchar4){gradx,0,0,255}).x;
    float gy_float = rsUnpackColor8888((uchar4){grady,0,0,255}).x;
    return atan(gy_float/gx_float);
}

/*
Get gradient magnitude and direction
*/

float2 RS_KERNEL gradient_magnitude_and_direction(const uchar gradx,const uchar grady){
    float gx_float = rsUnpackColor8888((uchar4){gradx,0,0,255}).x;
    float gy_float = rsUnpackColor8888((uchar4){grady,0,0,255}).x;

    float gx_squared = gx_float*gx_float;
    float gy_squared = gy_float*gy_float;
     //             magnitude                   direction
    return (float2){sqrt(gx_squared+gy_squared),atan(gy_float/gx_float)};

}

uchar4 RS_KERNEL float_to_rgba(const float in){
    return rsPackColorTo8888(in,in,in,1.0f);
}

/*
Return max brightness value if above threshold
*/
uchar RS_KERNEL canny_threshold(const uchar in){
    if(in > threshold){
        return 255;
    }else{
        return 0;
    }
}

void RS_KERNEL copy_to_rgba(const uchar4 in,uint32_t x, uint32_t y){
    uint32_t nx = 1440 - y;
    uint32_t ny = x;
    rsSetElementAt_uchar4(rgba_out,in,nx,ny);
}

void RS_KERNEL copy_dimension_flipped(const uchar4 in,uint32_t x, uint32_t y){
    uint32_t nx = 1440 - y;
    uint32_t ny = x;

    rsSetElementAt_uchar4(rgba_out,in,nx,ny);
}

uchar4 RS_KERNEL flat_to_rgba(const uchar in){
    return (uchar4){in,in,in,255};
}

uchar RS_KERNEL rgbaToGrayScale_flat(const uchar4 in){
    float4 pixel = rsUnpackColor8888(in);
    float value = dot(pixel,grayScaleValues) - 1;

    pixel.x = value;
    pixel.y = value;
    pixel.z = value;

    return rsPackColorTo8888(pixel).x;
}

uchar4 RS_KERNEL rgbaToGreyScale(const uchar4 in){
    float4 pixel = rsUnpackColor8888(in);
    float value = dot(pixel,grayScaleValues) - 1;

    pixel.x = value;
    pixel.y = value;
    pixel.z = value;

    return rsPackColorTo8888(pixel);
}

uchar4 RS_KERNEL rgbaRedChannel(const uchar4 in){
    return (uchar4){in.r,0,0,in.a};
}

uchar4 RS_KERNEL rgbaGreenChannel(const uchar4 in){
    return (uchar4){0,in.g,0,in.a};
}

uchar4 RS_KERNEL rgbaBlueChannel(const uchar4 in){
    return (uchar4){0,0,in.b,in.a};
}