package com.baidu.aip.manager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.FloatRange;
import android.support.annotation.IntRange;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.baidu.aip.ui.Activation;
import com.baidu.aip.utils.FileUitls;
import com.baidu.aip.utils.PreferencesUtil;
import com.baidu.idl.facesdk.FaceConfig;
import com.baidu.idl.facesdk.FaceSDK;
import com.baidu.idl.license.AndroidLicenser;

public class FaceSDKManager {

    public static final int SDK_UNACTIVATION = 1;
    public static final int SDK_UNINIT = 2;
    public static final int SDK_INITING = 3;
    public static final int SDK_INITED = 4;
    public static final int SDK_FAIL = 5;

    public static final String LICENSE_NAME = "idl-license.face-android";
    private FaceDetector faceDetector;
    private FaceFeature faceFeature;
    private Context context;
    private SdkInitListener sdkInitListener;
    private volatile int initStatus = SDK_UNACTIVATION;
    private Handler handler = new Handler(Looper.getMainLooper());

    private FaceSDKManager() {
        faceDetector = new FaceDetector();
        faceFeature = new FaceFeature();
    }

    private static class HolderClass {
        private static final FaceSDKManager instance = new FaceSDKManager();
    }

    public static FaceSDKManager getInstance() {
        return HolderClass.instance;
    }

    public int initStatus() {
        return initStatus;
    }

    public void setSdkInitListener(SdkInitListener sdkInitListener) {
        this.sdkInitListener = sdkInitListener;
    }

    public FaceDetector getFaceDetector() {
        return faceDetector;
    }

    public FaceFeature getFaceFeature() {
        return faceFeature;
    }


    /**
     * FaceSDK 初始化，用户可以根据自己的需求实例化FaceTracker 和 FaceRecognize
     * @param context
     */
    public void init(final Context context) {
        this.context = context;
        if (!check()) {
            initStatus = SDK_UNACTIVATION;
            return;
        }
        PreferencesUtil.initPrefs(context.getApplicationContext());
        // final String key = "faceexample-face-android";
        final String key = PreferencesUtil.getString("activate_key", "");
        if (TextUtils.isEmpty(key)) {
            Toast.makeText(context, "激活序列号为空, 请先激活", Toast.LENGTH_SHORT).show();
            return;
        }

        initStatus = SDK_INITING;
        ExecutorService es = Executors.newSingleThreadExecutor();
        es.submit(new Runnable() {
            @Override
            public void run() {
                if (sdkInitListener != null) {
                    sdkInitListener.initStart();
                }
                Log.e("FaceSDK" , "初始化授权");
                FaceSDK.initLicense(context, key, LICENSE_NAME , false);
                if (!sdkInitStatus()) {
                    return;
                }
                Log.e("FaceSDK" , "初始化sdk");
                faceDetector.init(context);
                faceFeature.init(context);
                initLiveness(context);
            }
        });
    }

    /**
     * 初始化 活体检测
     *
     * @param context
     */
    private void initLiveness(Context context) {
        FaceSDK.livenessSilentInit(context, FaceSDK.LivenessTypeId.LIVEID_VIS);
        FaceSDK.livenessSilentInit(context, FaceSDK.LivenessTypeId.LIVEID_IR);
        FaceSDK.livenessSilentInit(context, FaceSDK.LivenessTypeId.LIVEID_DEPTH);
    }

    private boolean sdkInitStatus() {
        boolean success = false;
        int status = FaceSDK.getAuthorityStatus();
        if (status == AndroidLicenser.ErrorCode.SUCCESS.ordinal()) {
            initStatus = SDK_INITED;
            success = true;
            faceDetector.setInitStatus(initStatus);
            Log.e("FaceSDK" , "授权成功");
            if (sdkInitListener != null) {
                sdkInitListener.initSuccess();
            }

        } else if (status == AndroidLicenser.ErrorCode.LICENSE_EXPIRED.ordinal()){
            initStatus = SDK_FAIL;
            // FileUitls.deleteLicense(context, LICENSE_NAME);
            Log.e("FaceSDK" , "授权过期");
            if (sdkInitListener != null) {
                sdkInitListener.initFail(status, "授权过期");
            }
            showActivation();
        } else {
            initStatus = SDK_FAIL;
            // FileUitls.deleteLicense(context, LICENSE_NAME);
            Log.e("FaceSDK" , "授权失败" + status);
            if (sdkInitListener != null) {
                sdkInitListener.initFail(status, "授权失败");
            }
            showActivation();
        }
        return success;
    }


    public boolean check() {
        if (!FileUitls.checklicense(context, LICENSE_NAME)) {
            showActivation();
            return false;
        } else {
            return true;
        }
    }

    public void showActivation() {
        if (FaceSDK.getAuthorityStatus() == AndroidLicenser.ErrorCode.SUCCESS.ordinal()) {
            Toast.makeText(context, "已经激活成功", Toast.LENGTH_LONG).show();
            // return;
        }
        final Activation activation = new Activation(context);
        activation.setActivationCallback(new Activation.ActivationCallback() {
            @Override
            public void callback(boolean success) {
                initStatus = SDK_UNINIT;
                Log.i("wtf", "activation callback");
                init(context);
            }
        });
        handler.post(new Runnable() {
            @Override
            public void run() {
                activation.show();
            }
        });

    }


    public interface SdkInitListener {

        public void initStart();
        public void initSuccess();
        public void initFail(int errorCode, String msg);
    }


}