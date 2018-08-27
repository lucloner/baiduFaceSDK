/*
 * Copyright (C) 2018 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.aip.ofr;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import org.openni.Device;
import org.openni.DeviceInfo;
import org.openni.OpenNI;
import org.openni.PixelFormat;
import org.openni.SensorType;
import org.openni.VideoMode;
import org.openni.VideoStream;
import org.openni.android.OpenNIHelper;

import com.baidu.aip.callback.ILivenessCallBack;
import com.baidu.aip.entity.LivenessModel;
import com.baidu.aip.face.FaceCropper;
import com.baidu.aip.manager.FaceEnvironment;
import com.baidu.aip.manager.FaceLiveness;
import com.baidu.aip.manager.FaceSDKManager;
import com.baidu.aip.utils.FileUitls;
import com.baidu.aip.utils.ImageUtils;
import com.baidu.idl.facesdk.FaceInfo;
import com.baidu.idl.facesdk.FaceTracker;
import com.orbbec.obDepth2.HomeKeyListener;
import com.orbbec.view.OpenGLView;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 自动检测获取人脸
 */
public class OrbbecLivenessDetectActivity extends Activity implements OpenNIHelper.DeviceOpenListener,
        ActivityCompat.OnRequestPermissionsResultCallback {

    private static String TAG = "OpenniLivenessDetect";
    ImageView mImageView;
    private TextView tipTv;
    private HomeKeyListener mHomeListener;
    private HandlerThread mHThread;
    private Handler mHandler;
    private Activity mContext;
    private final int CREATE_OPENNI = 0x001;
    private boolean m_InitOk = false;
    private boolean m_pause = false;
    private ExecutorService es;
    private Future future;
    private boolean success = false;
    private int source;

    private boolean initOk = false;
    private Device device;
    private Thread thread;
    private OpenNIHelper mOpenNIHelper;
    private VideoStream depthStream;
    private VideoStream rgbStream;


    private TextView detectDurationTv;
    private TextView rgbLivenssDurationTv;
    private TextView rgbLivenessScoreTv;
    private TextView depthLivenssDurationTv;
    private TextView depthLivenessScoreTv;

    private OpenGLView mDepthGLView;
    private OpenGLView mRgbGLView;

    private int mWidth = com.orbbec.utils.GlobalDef.RESOLUTION_X;
    private int mHeight = com.orbbec.utils.GlobalDef.RESOLUTION_Y;
    private final int DEPTH_NEED_PERMISSION = 33;
    private Object sync = new Object();
    private boolean exit = false;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_orbbec_liveness_detect);
        findView();

        mContext = this;

        registerHomeListener();
        es = Executors.newSingleThreadExecutor();

        Intent intent = getIntent();
        if (intent != null) {
            source = intent.getIntExtra("source", -1);
        }
    }


    private void findView() {

        tipTv = (TextView) findViewById(R.id.message);

        mDepthGLView = (OpenGLView) findViewById(R.id.depthGlView);
        mRgbGLView = (OpenGLView) findViewById(R.id.rgbGlView);

        detectDurationTv = (TextView) findViewById(R.id.detect_duration_tv);
        rgbLivenssDurationTv = (TextView) findViewById(R.id.rgb_liveness_duration_tv);
        rgbLivenessScoreTv = (TextView) findViewById(R.id.rgb_liveness_score_tv);
        depthLivenssDurationTv = (TextView) findViewById(R.id.depth_liveness_duration_tv);
        depthLivenessScoreTv = (TextView) findViewById(R.id.depth_liveness_score_tv);

        mOpenNIHelper = new OpenNIHelper(this);
        mOpenNIHelper.requestDeviceOpen(this);
    }

    private void init(UsbDevice device) {
        OpenNI.setLogAndroidOutput(false);
        OpenNI.setLogMinSeverity(0);
        OpenNI.initialize();

        List<DeviceInfo> opennilist = OpenNI.enumerateDevices();
        if (opennilist.size() <= 0) {
            Toast.makeText(this, " openni enumerateDevices 0 devices", Toast.LENGTH_LONG).show();
            return;
        }

        this.device = null;
        //Find device ID
        for (int i = 0; i < opennilist.size(); i++) {
            if (opennilist.get(i).getUsbProductId() == device.getProductId()) {
                this.device = Device.open();
                break;
            }
        }

        if (this.device == null) {
            Toast.makeText(this, " openni open devices failed: " + device.getDeviceName(),
                    Toast.LENGTH_LONG).show();
            return;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (initOk) {
            exit = true;
            if (thread != null) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (depthStream != null) {
                depthStream.stop();
            }
            if (rgbStream != null) {
                rgbStream.stop();
            }

            if (device != null) {
                device.close();
            }
        }
        if (mOpenNIHelper != null) {
            mOpenNIHelper.shutdown();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

    }

    @Override
    public void onDeviceOpened(UsbDevice device) {
        init(device);

        depthStream = VideoStream.create(this.device, SensorType.DEPTH);
        List<VideoMode> mVideoModes = depthStream.getSensorInfo().getSupportedVideoModes();

        for (VideoMode mode : mVideoModes) {
            int X = mode.getResolutionX();
            int Y = mode.getResolutionY();
            int fps = mode.getFps();

            if (X == mWidth && Y == mHeight && mode.getPixelFormat() == PixelFormat.DEPTH_1_MM) {
                depthStream.setVideoMode(mode);
                Log.v(TAG, " setmode");
            }

        }
        rgbStream = VideoStream.create(this.device, SensorType.COLOR);
        List<VideoMode> mColorVideoModes = rgbStream.getSensorInfo().getSupportedVideoModes();

        for (VideoMode mode : mColorVideoModes) {
            int X = mode.getResolutionX();
            int Y = mode.getResolutionY();
            int fps = mode.getFps();

            Log.d(TAG, " support resolution: " + X + " x " + Y + " fps: " + fps + ", (" + mode.getPixelFormat() + ")");
            if (X == mWidth && Y == mHeight && mode.getPixelFormat() == PixelFormat.RGB888) {
                rgbStream.setVideoMode(mode);
                Log.v(TAG, " setmode");
            }
        }

        startThread();
        FaceLiveness.getInstance().setLivenessCallBack(new ILivenessCallBack() {
            @Override
            public void onCallback(LivenessModel livenessModel) {
                checkResult(livenessModel);
            }

            @Override
            public void onTip(int code, final String msg) {
                Log.i("wtf", "onCallback" + msg);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tipTv.setText(msg);
                    }
                });
            }
        });
    }

    @Override
    public void onDeviceOpenFailed(String msg) {
        showAlertAndExit("Open Device failed: " + msg);
    }

    void startThread() {
        initOk = true;
        thread = new Thread() {

            @Override
            public void run() {

                List<VideoStream> streams = new ArrayList<VideoStream>();

                streams.add(depthStream);
                streams.add(rgbStream);

                depthStream.start();
                rgbStream.start();

                while (!exit) {

                    try {
                        OpenNI.waitForAnyStream(streams, 2000);

                    } catch (TimeoutException e) {
                        e.printStackTrace();
                        continue;
                    }

                    synchronized (sync) {

                        mDepthGLView.update(depthStream, com.orbbec.utils.GlobalDef.TYPE_DEPTH);
                        mRgbGLView.update(rgbStream, com.orbbec.utils.GlobalDef.TYPE_COLOR);

                        ByteBuffer depthByteBuf = depthStream.readFrame().getData();
                        ByteBuffer colorByteBuf = rgbStream.readFrame().getData();
                        int depthLen = depthByteBuf.remaining();
                        int rgbLen = colorByteBuf.remaining();

                        byte[] depthByte = new byte[depthLen];
                        byte[] rgbByte = new byte[rgbLen];

                        depthByteBuf.get(depthByte);
                        colorByteBuf.get(rgbByte);

                        final Bitmap bitmap = ImageUtils.RGB2Bitmap(rgbByte, mWidth, mHeight);

                        FaceLiveness.getInstance().setRgbBitmap(bitmap);
                        FaceLiveness.getInstance().setDepthData(depthByte);
                        FaceLiveness.getInstance().livenessCheck(mWidth, mHeight, 0X0101);
                    }
                }
            }
        };

        thread.start();
    }

    private void showAlertAndExit(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message);
        builder.setNeutralButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        builder.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == DEPTH_NEED_PERMISSION) {

            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permission Grant");
                Toast.makeText(mContext, "Permission Grant", Toast.LENGTH_SHORT).show();
            } else {
                Log.d(TAG, "Permission Denied");
                Toast.makeText(mContext, "Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void checkResult(LivenessModel model) {

        if (model == null) {
            return;
        }

        displayResult(model);
        int type = model.getLiveType();
        boolean livenessSuccess = false;
        // 同一时刻都通过才认为活体通过，开发者也可以根据自己的需求修改策略
        if ((type & FaceLiveness.MASK_RGB) == FaceLiveness.MASK_RGB) {
            livenessSuccess = (model.getRgbLivenessScore() > FaceEnvironment.LIVENESS_RGB_THRESHOLD) ? true : false;
        }
        if ((type & FaceLiveness.MASK_IR) == FaceLiveness.MASK_IR) {
            boolean irScore = (model.getIrLivenessScore() > FaceEnvironment.LIVENESS_IR_THRESHOLD) ? true : false;
            if (!irScore) {
                livenessSuccess = false;
            } else {
                livenessSuccess &= irScore;
            }
        }
        if ((type & FaceLiveness.MASK_DEPTH) == FaceLiveness.MASK_DEPTH) {
            boolean depthScore = (model.getDepthLivenessScore() > FaceEnvironment.LIVENESS_DEPTH_THRESHOLD) ? true :
                    false;
            if (!depthScore) {
                livenessSuccess = false;
            } else {
                livenessSuccess &= depthScore;
            }
        }

        if (livenessSuccess) {
            Bitmap bitmap = FaceCropper.getFace(model.getImageFrame().getArgb(),
                    model.getFaceInfo(), model.getImageFrame().getWidth());
            if (source == RegActivity.SOURCE_REG) {
                // 注册来源保存到注册人脸目录
                File faceDir = FileUitls.getFaceDirectory();
                if (faceDir != null) {
                    String imageName = UUID.randomUUID().toString();
                    File file = new File(faceDir, imageName);
                    // 压缩人脸图片至300 * 300，减少网络传输时间
                    ImageUtils.resize(bitmap, file, 300, 300);
                    Intent intent = new Intent();
                    intent.putExtra("file_path", file.getAbsolutePath());
                    setResult(Activity.RESULT_OK, intent);
                    success = true;
                    finish();
                } else {
                    toast("注册人脸目录未找到");
                }
            } else {
                try {
                    // 其他来源保存到临时目录
                    final File file = File.createTempFile(UUID.randomUUID().toString() + "", ".jpg");
                    // 人脸识别不需要整张图片。可以对人脸区别进行裁剪。减少流量消耗和，网络传输占用的时间消耗。
                    ImageUtils.resize(bitmap, file, 300, 300);Intent intent = new Intent();
                    intent.putExtra("file_path", file.getAbsolutePath());
                    setResult(Activity.RESULT_OK, intent);
                    success = true;
                    finish();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
    }

    private void displayResult(final LivenessModel livenessModel) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int type = livenessModel.getLiveType();
                detectDurationTv.setText("人脸检测耗时：" + livenessModel.getRgbDetectDuration());
                if ((type & FaceLiveness.MASK_RGB) == FaceLiveness.MASK_RGB) {
                    rgbLivenessScoreTv.setText("RGB活体得分：" + livenessModel.getRgbLivenessScore());
                    rgbLivenssDurationTv.setText("RGB活体耗时：" + livenessModel.getRgbLivenessDuration());
                }

                if ((type & FaceLiveness.MASK_IR) == FaceLiveness.MASK_IR) {

                }

                if ((type & FaceLiveness.MASK_DEPTH) == FaceLiveness.MASK_DEPTH) {
                    depthLivenessScoreTv.setText("Depth活体得分：" + livenessModel.getDepthLivenessScore());
                    depthLivenssDurationTv.setText("Depth活体耗时：" + livenessModel.getDetphtLivenessDuration());
                }
            }

        });
    }

    private void registerHomeListener() {
        mHomeListener = new HomeKeyListener(this);
        mHomeListener
                .setOnHomePressedListener(new HomeKeyListener.OnHomePressedListener() {

                    @Override
                    public void onHomePressed() {
                        finish();
                    }

                    @Override
                    public void onHomeLongPressed() {
                    }
                });
        mHomeListener.startWatch();
    }

    private void unRegisterHomeListener() {
        if (mHomeListener != null) {
            mHomeListener.stopWatch();
        }
    }

    private void toast(final String tip) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(OrbbecLivenessDetectActivity.this, tip, Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void aysncDetect(final int[] argb, final int height, final int width) {

        if (success || (future != null && !future.isDone())) {
            return;
        }

        future = es.submit(new Runnable() {
            @Override
            public void run() {
                detect(argb, height, width);
            }
        });
    }

    private void detect(int[] argb, int height, int width) {
        Log.i("wtf", "detect--" + argb.length);
        int errorCode = FaceSDKManager.getInstance().getFaceDetector().detect(argb, width, height);
        Log.i("wtf", "detect--" + errorCode);
        if (errorCode == FaceTracker.ErrCode.OK.ordinal() || errorCode == FaceTracker.ErrCode.DATA_HIT_LAST.ordinal()) {
            FaceInfo[] trackedfaces = FaceSDKManager.getInstance().getFaceDetector().getTrackedFaces();
            if (trackedfaces != null && trackedfaces.length > 0) {
                FaceInfo faceInfo = trackedfaces[0];

                if (filter(faceInfo, width, height)) {
                    Bitmap bitmap = FaceCropper.getFace(argb, faceInfo, width);
                    try {
                        final File file = File.createTempFile(UUID.randomUUID().toString() + "", ".jpg");
                        ImageUtils.resize(bitmap, file, 300, 300);

                        Intent intent = new Intent();
                        intent.putExtra("file_path", file.getAbsolutePath());
                        setResult(Activity.RESULT_OK, intent);
                        success = true;
                        finish();

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private boolean filter(FaceInfo faceInfo, int bitMapWidth, int bitMapHeight) {

        if (faceInfo.mConf < 0.6) {
            tipTv.setText("人脸置信度太低");
            return false;
        }

        float[] headPose = faceInfo.headPose;
        // Log.i("wtf", "headpose->" + headPose[0] + " " + headPose[1] + " " + headPose[2]);
        if (Math.abs(headPose[0]) > 15 || Math.abs(headPose[1]) > 15 || Math.abs(headPose[2]) > 15) {
            tipTv.setText("人脸置角度太大，请正对屏幕");
            return false;
        }

        // 判断人脸大小，若人脸超过屏幕二分一，则提示文案“人脸离手机太近，请调整与手机的距离”；
        // 若人脸小于屏幕三分一，则提示“人脸离手机太远，请调整与手机的距离”
        float ratio = (float) faceInfo.mWidth / (float) bitMapHeight;
        // Log.i("liveness_ratio", "ratio=" + ratio);
        if (ratio > 0.6) {
            tipTv.setText("人脸离屏幕太近，请调整与屏幕的距离");
            // clearInfo();
            return false;
        } else if (ratio < 0.2) {
            tipTv.setText("人脸离屏幕太远，请调整与屏幕的距离");
            // clearInfo();
            return false;
        } else if (faceInfo.mCenter_x > bitMapWidth * 3 / 4 ) {
            tipTv.setText("人脸在屏幕中太靠右");
            return false;
        } else if (faceInfo.mCenter_x < bitMapWidth / 4 ) {
            tipTv.setText("人脸在屏幕中太靠左");
            // clearInfo();
            return false;
        } else if (faceInfo.mCenter_y > bitMapHeight * 3 / 4 ) {
            tipTv.setText("人脸在屏幕中太靠下");
            // clearInfo();
            return false;
        } else if (faceInfo.mCenter_x < bitMapHeight / 4 ) {
            tipTv.setText("人脸在屏幕中太靠上");
            // clearInfo();
            return false;
        }

        return true;
    }

}
