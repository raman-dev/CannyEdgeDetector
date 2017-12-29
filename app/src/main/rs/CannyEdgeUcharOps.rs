#pragma version(1)
#pragma rs java_package_name(com.example.ramandeep.cannyedgedetector2)
#pragma rs_fp_relaxed

const float4 grayScaleValues = (float4){0.2126,0.7152,0.0722,1};
uchar threshold = 8;

rs_allocation rgba_out;
rs_allocation gradient;

int x_max;
int y_max;

void init(){}

uchar RS_KERNEL canny_threshold(const uchar in){
    if(in > threshold){
        return 255;
    }else{
        return 0;
    }
}

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

uchar RS_KERNEL non_max_suppression(const uchar in,const float angle,const uint32_t x,const uint32_t y){
       //check if the current pixel is the
       //brightest along its gradient direction
       //between the two pixels that are in the same direction
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

uchar RS_KERNEL gradient_magnitude(const uchar gx,const uchar gy){
    float a = rsUnpackColor8888((uchar4){gx,0,0,255}).x;
    float b = rsUnpackColor8888((uchar4){gy,0,0,255}).x;
    float value = sqrt(a*a + b*b);
    return rsPackColorTo8888(value,0,0,1.0f).x;
}

float RS_KERNEL gradient_direction(const uchar gx,const uchar gy){
    //if gx is 0 then
    //make a = some value greater than 0
    //so as to avoid division by 0

    float a = rsUnpackColor8888((uchar4){gx,0,0,255}).x;
    float b = rsUnpackColor8888((uchar4){gy,0,0,255}).x;

    return atan2pi(b,a)*180.0f;
}