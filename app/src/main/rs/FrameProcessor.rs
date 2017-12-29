#pragma version(1)
#pragma rs java_package_name(com.example.ramandeep.cannyedgedetector2)
#pragma rs_fp_relaxed

const float4 grayScaleValues = (float4){0.2126,0.7152,0.0722,1};

rs_allocation rgba_out;

int x_max;
int y_max;

void init(){
}

uchar4 RS_KERNEL float_to_rgba(const float in){
    return rsPackColorTo8888(in,in,in,1.0f);
}

void RS_KERNEL copy_dimension_flipped(const uchar4 in,uint32_t x, uint32_t y){
    uint32_t nx = y_max - y;
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
    pixel.y = 0;
    pixel.z = 0;

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