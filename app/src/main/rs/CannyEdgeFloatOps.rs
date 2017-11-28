#pragma version(1)
#pragma rs java_package_name(com.example.ramandeep.cannyedgedetector2)
#pragma rs_fp_relaxed

const float4 grayScaleValues = (float4){0.2126,0.7152,0.0722,1};
uchar threshold = 5;

rs_allocation rgba_out;
rs_allocation gradient_magnitude_and_direction;

int x_max;
int y_max;

uchar RS_KERNEL rgba_to_gray_flat(const uchar4 in){
    float4 pixel = rsUnpackColor8888(in);
    float value = dot(pixel,grayScaleValues) - 1;

    pixel.x = value;
    pixel.y = value;
    pixel.z = value;

    return rsPackColorTo8888(pixel).x;
}


