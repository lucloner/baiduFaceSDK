/*
 * Copyright (C) 2018 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.aip.manager;

import com.baidu.aip.entity.ARGBImg;
import com.baidu.aip.entity.Feature;
import com.baidu.idl.facesdk.FaceInfo;
import com.baidu.idl.facesdk.FaceRecognize;
import com.baidu.idl.facesdk.FaceSDK;
import com.baidu.idl.facesdk.FaceTracker;

import android.content.Context;
import android.util.Log;

public class FaceFeature {

    private FaceRecognize faceRecognize;
    private FeatureCallbak callbak;

    public void init(Context context) {
        if (faceRecognize == null) {
            faceRecognize = new FaceRecognize(context);
            faceRecognize.initModel(FaceSDK.RecognizeType.RECOGNIZE_LIVE);
//            faceRecognize.initModel(FaceSDK.RecognizeType.RECOGNIZE_NIR);
        }
    }

    public int extractFeature(int[] argb, int height, int width, byte[] feature, int[] landmarks) {

        if (faceRecognize == null) {
            return -1;
        }
        int ret = faceRecognize.extractFeature(argb, height, width, FaceSDK.ImgType.ARGB, feature, landmarks, FaceSDK.RecognizeType.RECOGNIZE_LIVE);
       // Log.i("wtf", "extractFeature ret->" + ret);
        return ret;
//        if (callbak != null) {
//            callbak.callback(feature);
//        }
    }

    public int faceFeature(ARGBImg argbImg, byte[] feature) {

        FaceSDKManager.getInstance().getFaceDetector().clearTrackedFaces();
        int ret = FaceSDKManager.getInstance().getFaceDetector().detect(argbImg.data, argbImg.width, argbImg.height);
        // Log.i("wtf", "feature detect from image->" + ret + " " + argbImg.width + " " + argbImg.height);

        FaceInfo[] faceInfos = FaceSDKManager.getInstance().getFaceDetector().getTrackedFaces();
        // Log.i("wtf", "feature detect from image faceInfos ->" + faceInfos);
        if (faceInfos != null && faceInfos.length > 0) {
            FaceInfo faceInfo = faceInfos[0];
            // Log.i("wtf", "feature detect from image faceInfos ->" + faceInfo);
            // 可以ret FaceDetector.DETECT_CODE_OK和 FaceDetector.DETECT_CODE_HIT_LAST才进行特征抽取
            // if (faceInfo != null && (ret == FaceDetector.DETECT_CODE_OK || ret == FaceDetector
            // .DETECT_CODE_HIT_LAST)) {
            if (faceInfo != null && (ret != FaceDetector.NO_FACE_DETECTED && ret != FaceDetector.UNKNOW_TYPE)) {
                return faceRecognize.extractFeature(argbImg.data, argbImg.height, argbImg.width, FaceSDK.ImgType.ARGB,
                        feature, faceInfo.landmarks, FaceSDK.RecognizeType.RECOGNIZE_LIVE);
            }
        }
        FaceSDKManager.getInstance().getFaceDetector().clearTrackedFaces();
        return ret;
    }

    public float getFaceFeatureDistance(byte[] firstFeature, byte[] secondFeature) {
        return faceRecognize.getFaceSimilarity(firstFeature, secondFeature, FaceSDK.RecognizeType.RECOGNIZE_LIVE);
    }

    public float getFaceFeatureDistance(byte[] firstFeature, byte[] secondFeature, FaceSDK.RecognizeType type) {
        return faceRecognize.getFaceSimilarity(firstFeature, secondFeature, type);
    }


    public interface FeatureCallbak {

        public void callback(byte[] feature);

    }
}
