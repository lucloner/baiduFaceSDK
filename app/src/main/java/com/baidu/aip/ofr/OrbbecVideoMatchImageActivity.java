/*
 * Copyright (C) 2018 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.aip.ofr;

import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
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

import com.baidu.aip.ImageFrame;
import com.baidu.aip.api.FaceApi;
import com.baidu.aip.callback.ILivenessCallBack;
import com.baidu.aip.entity.ARGBImg;
import com.baidu.aip.entity.LivenessModel;
import com.baidu.aip.manager.FaceDetector;
import com.baidu.aip.manager.FaceEnvironment;
import com.baidu.aip.manager.FaceLiveness;
import com.baidu.aip.manager.FaceSDKManager;
import com.baidu.aip.utils.FeatureUtils;
import com.baidu.aip.utils.ImageUtils;
import com.baidu.idl.facesdk.FaceInfo;
import com.orbbec.obDepth2.HomeKeyListener;
import com.orbbec.view.OpenGLView;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.usb.UsbDevice;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class OrbbecVideoMatchImageActivity extends Activity implements View.OnClickListener, OpenNIHelper
        .DeviceOpenListener,
        ActivityCompat.OnRequestPermissionsResultCallback {

    private static String TAG = "OrbbecVideoMatchImageActivity";
    private static final int PICK_PHOTO = 100;
    private TextView tipTv;
    private HomeKeyListener mHomeListener;
    private Activity mContext;
    private boolean m_pause = false;
    private ExecutorService es;
    private Future future;
    private boolean success = false;
    private byte[] photoFeature = new byte[2048];
    private volatile boolean photoFeatureFinish = false;
    private volatile boolean matching = false;

    private TextView detectDurationTv;
    private TextView rgbLivenssDurationTv;
    private TextView rgbLivenessScoreTv;
    private TextView depthLivenssDurationTv;
    private TextView depthLivenessScoreTv;
    private TextView matchScoreTv;
    private ImageView photoIv;
    private Button pickPhotoFromAlbum;

    private OpenGLView mDepthGLView;
    private OpenGLView mRgbGLView;

    private boolean initOk = false;
    private Device device;
    private Thread thread;
    private OpenNIHelper mOpenNIHelper;
    private VideoStream depthStream;
    private VideoStream rgbStream;

    private int mWidth = com.orbbec.utils.GlobalDef.RESOLUTION_X;
    private int mHeight = com.orbbec.utils.GlobalDef.RESOLUTION_Y;
    private final int DEPTH_NEED_PERMISSION = 33;
    private Object sync = new Object();
    private boolean exit = false;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_orbbec_video_match_image);
        findView();
        mContext = this;

        // registerHomeListener();

        es = Executors.newSingleThreadExecutor();
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        matchScoreTv = (TextView) findViewById(R.id.match_score_tv);
        photoIv = (ImageView) findViewById(R.id.pick_from_album_iv);
        pickPhotoFromAlbum = (Button) findViewById(R.id.pick_from_album_btn);

        pickPhotoFromAlbum.setOnClickListener(this);
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

    }

    @Override
    public void onDestroy() {
        if (initOk) {
            exit = true;
            if (thread != null) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            long time1 = System.currentTimeMillis();
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
            }

        }
        rgbStream = VideoStream.create(this.device, SensorType.COLOR);
        List<VideoMode> mColorVideoModes = rgbStream.getSensorInfo().getSupportedVideoModes();

        for (VideoMode mode : mColorVideoModes) {
            int X = mode.getResolutionX();
            int Y = mode.getResolutionY();
            int fps = mode.getFps();

            if (X == mWidth && Y == mHeight && mode.getPixelFormat() == PixelFormat.RGB888) {
                rgbStream.setVideoMode(mode);
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

                        if (photoFeatureFinish) {
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
                Toast.makeText(mContext, "Permission Grant", Toast.LENGTH_SHORT).show();
            } else {
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

        if (livenessSuccess && photoFeatureFinish) {
            asyncMath(photoFeature, model.getFaceInfo(), model.getImageFrame());
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
                        // TODO
                        finish();
                    }

                    @Override
                    public void onHomeLongPressed() {
                        // TODO
                    }
                });
        mHomeListener.startWatch();
    }

    private void unRegisterHomeListener() {
        if (mHomeListener != null) {
            mHomeListener.stopWatch();
        }
    }

    @Override
    public void onClick(View v) {
        if (v == pickPhotoFromAlbum) {
            // 视频检测有人脸追踪功能，不能和相册图片人脸同时进行。进行相册图片人脸检测时，关闭视频流人脸检测
            photoFeatureFinish = false;
            Intent intent = new Intent(Intent.ACTION_PICK,
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, PICK_PHOTO);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode == 0) {
            return ;
        }

        if (requestCode == PICK_PHOTO && (data != null && data.getData() != null)) {
            Uri uri = ImageUtils.geturi(data, this);
            pickPhotoFeature(uri);
        }
    }


    private void pickPhotoFeature(final Uri imageUri) {
        future = Executors.newSingleThreadExecutor().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            photoIv.setImageBitmap(bitmap);
                        }
                    });
                    ARGBImg argbImg = FeatureUtils.getImageInfo(bitmap);
                    int ret = FaceSDKManager.getInstance().getFaceFeature().faceFeature(argbImg, photoFeature);
                    // 如果要求比较严格，可以ret FaceDetector.DETECT_CODE_OK和 FaceDetector.DETECT_CODE_HIT_LAST
                    if (ret == FaceDetector.NO_FACE_DETECTED) {
                        toast("未检测到人脸，可能原因：人脸太小（必须大于最小检测人脸minFaceSize），或者人脸角度太大，人脸不是朝上");
                        clearTip();
                    } else if ( ret != 512) {
                        toast("抽取特征失败");
                        clearTip();
                    } else if (ret == 512) {
                        photoFeatureFinish = true;

                    } else {
                        toast("未检测到人脸");
                        clearTip();
                    }

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void clearTip() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                detectDurationTv.setText("");
                rgbLivenessScoreTv.setText("");
                rgbLivenssDurationTv.setText("" );
                depthLivenessScoreTv.setText("");
                depthLivenssDurationTv.setText("");
                matchScoreTv.setText("");
            }
        });
    }

    private void asyncMath(final byte[] photoFeature, final FaceInfo faceInfo, final ImageFrame imageFrame) {
        if (future != null && !future.isDone()) {
            return;
        }
        future = es.submit(new Runnable() {
            @Override
            public void run() {
                match(photoFeature, faceInfo, imageFrame);
            }
        });
    }

    private void match(final byte[] photoFeature, FaceInfo faceInfo, ImageFrame imageFrame) {

        if (faceInfo == null) {
            return;
        }

        float raw  = Math.abs(faceInfo.headPose[0]);
        float patch  = Math.abs(faceInfo.headPose[1]);
        float roll  = Math.abs(faceInfo.headPose[2]);
        //人脸的三个角度大于15不进行识别  角度越小，人脸越正，比对时分数越高
        if (raw > 15 || patch > 15 ||  roll > 15) {
            return;
        }

        matching =  true;
        int[] argb = imageFrame.getArgb();
        int rows = imageFrame.getHeight();
        int cols = imageFrame.getWidth();
        int[] landmarks = faceInfo.landmarks;
        final float score = FaceApi.getInstance().match(photoFeature, argb, rows, cols, landmarks);
        matching =  false;
        Log.i("wtf", "score:" + score);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                matchScoreTv.setText("比对得分:" + score);
            }
        });

    }

    private void toast(final String tip) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(OrbbecVideoMatchImageActivity.this, tip, Toast.LENGTH_SHORT).show();
            }
        });
    }


}
