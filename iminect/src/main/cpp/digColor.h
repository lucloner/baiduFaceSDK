#ifndef _DIG_COLOR_H
#define _DIG_COLOR_H

#include <jni.h>
#include <stdio.h>
#include <android/log.h>

#ifndef NELEM
#define NELEM(x) ((int)(sizeof(x) / sizeof((x)[0])))
#endif

#ifdef __cplusplus
extern "C" {
#endif

void digColor(JNIEnv *env, jobject obj, jobject color, jobject depth, jint width, jint height);

jint JNI_OnLoad(JavaVM*  vm, void* reserved);

int registerNativeFuntions(JNIEnv* env,const char* className);

#ifdef __cplusplus
}
#endif

#endif
