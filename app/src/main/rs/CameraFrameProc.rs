#pragma version(1)
#pragma rs java_package_name(com.example.ramandeep.cannyedgedetector)
#pragma rs_fp_relaxed

int display_x_max;
int display_y_max;

int camera_x_max;
int camera_y_max;

uchar threshold = 8;
rs_allocation gradient;
rs_allocation rgba_out;

uchar RS_KERNEL rgba_to_flat(const uchar4 in){
    return in.x;
}

uchar4 RS_KERNEL flat_to_rgba(const uchar in){
    return (uchar4){in,in,in,255};
}

//rotate by 90 degrees ccw
void RS_KERNEL rotate_90_cw(const uchar4 in, const uint32_t x,const uint32_t y){
    uint32_t nx = display_y_max - y;
    uint32_t ny = x;
    rsSetElementAt_uchar4(rgba_out,in,nx,ny);//set the element at
}

//rotate by 90 degrees cw
void RS_KERNEL rotate_90_ccw(const uchar4 in, const uint32_t x,const uint32_t y){
    uint32_t nx = display_x_max - y;
    uint32_t ny = x;
    rsSetElementAt_uchar4(rgba_out,in,nx,ny);//set the element at
}

//gradient magnitude kernel

uchar RS_KERNEL gradient_magnitude(uchar gx,uchar gy){
    float4 x = rsUnpackColor8888((uchar){gx,0,0,255});
    float4 y = rsUnpackColor8888((uchar){gy,0,0,255});

    float gx2= x.r*x.r;
    float gy2 = y.r*y.r;

    float grad_mag = sqrt(gx2 + gy2);
    return rsPackColorTo8888(grad_mag,0,0,1.0f).r;
}

float RS_KERNEL gradient_direction(const uchar4 gx,const uchar4 gy){
    //if gx is 0 then
    //make a = some value greater than 0
    //so as to avoid division by 0

    float a = rsUnpackColor8888(gx).x;
    float b = rsUnpackColor8888(gy).x;

    return atan2pi(b,a)*180.0f;
}

uchar RS_KERNEL canny_threshold(const uchar in){
    if(in > threshold){
        return 255;
    }else{
      return 0;
    }
}

static uchar getBottomRightTopLeft(const uchar in, const uint32_t x,const uint32_t y){
    int right_x = x + 1;
    int bottom_y = y + 1;

    int left_x = x - 1;
    int top_y = y - 1;

    uchar top_left_val = 0;
    uchar bottom_right_val = 0;

    //if left_x is greater than 0 and top_y is greater than 0 then
    //get the value else top_left_val will remain 0
    if(left_x >= 0 && top_y >= 0){
        top_left_val = *(uchar*)rsGetElementAt(gradient,left_x,top_y);
    }
    //if right_x is less than camera_x_max and bottom_y is less than camera_y_max
    //then get the value else bottom_right_val will remain 0
    if(right_x < camera_x_max && bottom_y < camera_y_max){
        bottom_right_val = *(uchar*)rsGetElementAt(gradient,right_x,bottom_y);
    }
    if(in >= top_left_val && in >= bottom_right_val ){
        return in;
    }
    else{
        return 0;
    }
}

static uchar getTopRightBottomLeft(const uchar in, const uint32_t x, const uint32_t y){
    int right_x = x + 1;
    int top_y = y - 1;

    int left_x = x-1;
    int bottom_y = y+1;

    uchar top_right_val = 0;
    uchar bottom_left_val = 0;
    //if rightx is less than camera_x_max
    //and top is greater than 0 then
    if(right_x < camera_x_max && top_y >= 0){
        top_right_val = *(uchar*)rsGetElementAt(gradient,right_x,top_y);
    }
    //if left_x is greater then 0 and bottom_y is less than camera_y_max
    //get the value else it will remain 0
    if(left_x >= 0 && bottom_y < camera_y_max ){
        bottom_left_val = *(uchar*)rsGetElementAt(gradient,left_x,bottom_y);
    }
    if(in >= top_right_val && in >= bottom_left_val ){
        return in;
    }
    else{
        return 0;
    }
}

static uchar getTopBottom(const uchar in, const uint32_t x, const uint32_t y){
    int top = y - 1;
    int bottom = y + 1;

    uchar top_val = 0;
    uchar bottom_val = 0;

    //if top runs off the top then top_val will remain 0
    if(top >= 0){
        top_val = *(uchar*)rsGetElementAt(gradient,x,top);
    }
    //if the bottom runs off the last row then bottom_val will remain 0
    if(bottom < camera_y_max){
        bottom_val = *(uchar*)rsGetElementAt(gradient,x,bottom);
    }
    //check if the in value is the largest between the top and bottom
    if(in >= top_val && in >= bottom_val ){
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

   //if right is greater than or equal to x_max then right_val will remain 0
   if(right < camera_x_max ){
      right_val = *(uchar*)rsGetElementAt(gradient,right,y);
   }
   //if left is less than 0 then top_val will remain 0
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

