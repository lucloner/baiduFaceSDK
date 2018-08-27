
#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <android/log.h>

#define REGISTER_CLASS "com/orbbec/Native/DepthUtils"

#define LOG_TAG "DepthUtils-Jni"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG, __VA_ARGS__)

typedef unsigned char uint8_t;
int* m_histogram;
enum { HISTSIZE = 0xFFFF, };

typedef struct {
    uint8_t r;
    uint8_t g;
    uint8_t b;
}RGB888Pixel;

int ConventToRGBA(uint8_t* src, uint8_t* dst,  int w, int h, int strideInBytes){
    for (int y = 0; y < h; ++y)
    {
        uint8_t* pTexture = dst +  (y*w*4);
        const RGB888Pixel* pData = (const RGB888Pixel*)(src + y * strideInBytes);
        for (int x = 0; x < w; ++x, ++pData, pTexture += 4)
        {
            pTexture[0] = pData->r;
            pTexture[1] = pData->g;
            pTexture[2] = pData->b;
            pTexture[3] = 255;
        }

    }

    return 0;
}

int ConventFromDepthToRGBA(short* src, int* dst,  int w, int h, int strideInBytes){

    // Calculate the accumulative histogram (the yellow display...)
    if (m_histogram == NULL) {
        m_histogram = new int[HISTSIZE];
    }
    memset(m_histogram, 0, HISTSIZE * sizeof(int));

    int nNumberOfPoints = 0;
    unsigned int value;
    int Size = w * h;
    for (int i = 0; i < Size; ++i) {
        value =src[i];
        if (value != 0) {
            m_histogram[value]++;
            nNumberOfPoints++;
        }
    }

    int nIndex;
    for (nIndex = 1; nIndex < HISTSIZE; nIndex++) {
        m_histogram[nIndex] += m_histogram[nIndex - 1];
    }

    if (nNumberOfPoints != 0) {
        for (nIndex = 1; nIndex < HISTSIZE; nIndex++) {
            m_histogram[nIndex] = (unsigned int)(256 * (1.0f - ((float)m_histogram[nIndex] / nNumberOfPoints)));
        }
    }

    for (int y = 0; y < h; ++y) {
        uint8_t* rgb = (uint8_t*) (dst + y * w);
        short* pView = src + y * w;
        for (int x = 0; x < w; ++x, rgb += 4, pView++) {
            value = m_histogram[*pView];
            rgb[0] =value;
            rgb[1] = value;
            rgb[2] = 0x00;
            rgb[3] = 0xff;
        }
    }
    return 0;
}


jint ConvertTORGBA(JNIEnv* env, jobject obj, jobject src, jobject dst, jint w, jint h, jint strideInBytes){

    if(src == nullptr || dst == nullptr){
        return -1;
    }
    short * srcBuf = (short *)env->GetDirectBufferAddress(src);

    int* dstBuf = (int*)env->GetDirectBufferAddress(dst);

    ConventFromDepthToRGBA(srcBuf, dstBuf, w, h, strideInBytes);

    return 0;
}

jint RGB888TORGBA(JNIEnv* env, jobject obj, jobject src, jobject dst, jint w, jint h, jint strideInBytes){

    if(src == nullptr || dst == nullptr){
        return -1;
    }
    uint8_t* srcBuf = (uint8_t*)env->GetDirectBufferAddress(src);

    uint8_t* dstBuf = (uint8_t*)env->GetDirectBufferAddress(dst);

    ConventToRGBA(srcBuf, dstBuf, w, h, strideInBytes);

    return 0;
}




JNINativeMethod jniMethods[] = {
        { "ConvertTORGBA",                      "(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;III)I",                      (void*)&ConvertTORGBA},
        { "RGB888TORGBA",                      "(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;III)I",                      (void*)&RGB888TORGBA},
};

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env;
    jclass gCallbackClass = nullptr;

    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }

    jclass clz     = env ->FindClass(REGISTER_CLASS);
    gCallbackClass = (jclass)env ->NewGlobalRef(clz);
    env ->RegisterNatives(clz, jniMethods, sizeof(jniMethods) / sizeof(JNINativeMethod));
    env ->DeleteLocalRef(clz);
    LOGD("DepthUtils JNI_OnLoad");
    return JNI_VERSION_1_6;
}
