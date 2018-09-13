#include "digColor.h"

#define  LOG_TAG    "Richard"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifdef __cplusplus
extern "C" {
#endif

const char* CLASS_NAME = "com/baidu/aip/iminect/Jni";
jobject colorObj = NULL;
jmethodID callbackId = NULL;
bool isObjCreate = false;

/*
* JNI registration.
*/
static JNINativeMethod gMethods[] = {
	/* name, signature, funcPtr */
	{ "digColorPerson", "(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;II)V", (void*) digColor }
};

int registerNativeFuntions(JNIEnv* env,const char* className)
{
	jclass cls = env->FindClass(className);
	return  env->RegisterNatives(cls, gMethods, NELEM(gMethods));
}

jint JNI_OnLoad(JavaVM*  vm, void* reserved)
{
	JNIEnv* env = NULL;
	if (vm->GetEnv((void**)&env, JNI_VERSION_1_4) != JNI_OK) {
		LOGD("[Richard] GetEnv failed!");
		return -1;
	}

	registerNativeFuntions(env,CLASS_NAME);
	isObjCreate = false;
	return JNI_VERSION_1_4;
}

void digColor(JNIEnv *env, jobject obj, jobject color, jobject depth, jint width, jint height)
{
	jbyte *colorBuffer = (jbyte*)env->GetDirectBufferAddress(color);
	jbyte *depthBuffer = (jbyte*)env->GetDirectBufferAddress(depth);
	if(colorBuffer == NULL || depthBuffer == NULL) {
		LOGD("[Richard]LINE_47: JNI buffer is NULL");
		return;
	}

    uint16_t* pDepth = (uint16_t*)depthBuffer;
	if( width == 640 && height == 480 ) {
		int num  = 640 * 480;
	    for(int i = 0; i < num; i++) {
    		if(pDepth[i] == 0) {
    			colorBuffer[i * 3] = 0;
    			colorBuffer[i * 3 + 1] = 0;
    			colorBuffer[i * 3 + 2] = 0;
    		}
    	}

	}

	// 回调java方法
	if(!isObjCreate) {
		colorObj = (jobject) env->NewGlobalRef(obj);
		jclass cls = env->GetObjectClass(colorObj);
		callbackId = env->GetMethodID(cls, "updateVertices", "()V");
		if (callbackId == NULL) {
			LOGD("[Richard]LINE_70: JNI methodID is NULL");
			return;
		}

		isObjCreate = true;
	}

	env->CallVoidMethod(colorObj, callbackId);
	//LOGD("[Richard]LINE_76: ======jni exit digColor======");
}

#ifdef __cplusplus
}
#endif

