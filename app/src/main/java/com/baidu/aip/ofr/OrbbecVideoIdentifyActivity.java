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
import com.baidu.aip.db.DBManager;
import com.baidu.aip.entity.Feature;
import com.baidu.aip.entity.IdentifyRet;
import com.baidu.aip.entity.LivenessModel;
import com.baidu.aip.entity.User;
import com.baidu.aip.manager.FaceEnvironment;
import com.baidu.aip.manager.FaceLiveness;
import com.baidu.aip.utils.FileUitls;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.ActivityCompat;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class OrbbecVideoIdentifyActivity extends Activity implements OpenNIHelper.DeviceOpenListener,
        ActivityCompat.OnRequestPermissionsResultCallback {

    private static final int FEATURE_DATAS_UNREADY = 1;
    private static final int IDENTITY_IDLE = 2;
    private static final int IDENTITYING = 3;

    ImageView mImageView;
    private TextView mPrompt;
    private HomeKeyListener mHomeListener;
    private HandlerThread mHThread;
    private Handler mHandler;
    private Activity mContext;
    private final int CREATE_OPENNI = 0x001;
    private boolean m_InitOk = false;
    private boolean m_pause = false;

    private TextView mMessageTv;
    private TextView rgbLivenessTipTv;
    private TextView depthLivenessTipTv;

    private ImageView testView;
    private TextView userOfMaxSocre;

    private ImageView matchAvatorIv;
    private TextView matchUserTv;
    private TextView scoreTv;
    private TextView facesetsCountTv;
    private TextView detectDurationTv;
    private TextView rgbLivenssDurationTv;
    private TextView rgbLivenessScoreTv;
    private TextView depthLivenssDurationTv;
    private TextView depthLivenessScoreTv;
    private TextView featureDurationTv;

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

    private String groupId = "";

    private volatile int identityStatus = FEATURE_DATAS_UNREADY;
    private String userIdOfMaxScore = "";
    private float maxScore = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_orbbec_video_identity);
        findView();
        mContext = this;

        registerHomeListener();

        Intent intent = getIntent();
        if (intent != null) {
            groupId = intent.getStringExtra("group_id");
        }

        DBManager.getInstance().init(this);
        loadFeature2Memery();

    }

    private void findView() {
        mDepthGLView = (OpenGLView) findViewById(R.id.depthGlView);
        mRgbGLView = (OpenGLView) findViewById(R.id.rgbGlView);

        mMessageTv = (TextView) findViewById(R.id.message);
        rgbLivenessTipTv = (TextView) findViewById(R.id.rgb_liveness_tip_tv);
        depthLivenessTipTv = (TextView) findViewById(R.id.depth_liveness_tip_tv);

        matchAvatorIv = (ImageView) findViewById(R.id.match_avator_iv);
        matchUserTv = (TextView) findViewById(R.id.match_user_tv);
        scoreTv = (TextView) findViewById(R.id.score_tv);
        facesetsCountTv = (TextView) findViewById(R.id.facesets_count_tv);
        detectDurationTv = (TextView) findViewById(R.id.detect_duration_tv);
        rgbLivenssDurationTv = (TextView) findViewById(R.id.rgb_liveness_duration_tv);
        rgbLivenessScoreTv = (TextView) findViewById(R.id.rgb_liveness_score_tv);
        depthLivenssDurationTv = (TextView) findViewById(R.id.depth_liveness_duration_tv);
        depthLivenessScoreTv = (TextView) findViewById(R.id.depth_liveness_score_tv);
        featureDurationTv = (TextView) findViewById(R.id.feature_duration_tv);

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
                        mMessageTv.setText(msg);
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

        if (livenessSuccess) {
            asyncIdentity(model.getImageFrame(), model.getFaceInfo());
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


    private ExecutorService es = Executors.newSingleThreadExecutor();
    private void loadFeature2Memery() {
        if (identityStatus != FEATURE_DATAS_UNREADY) {
            return;
        }
        es.submit(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                // android.os.Process.setThreadPriority (-4);
                FaceApi.getInstance().loadFacesFromDB(groupId);
                toast("人脸数据加载完成，即将开始1：N");
                int count = FaceApi.getInstance().getGroup2Facesets().get(groupId).size();
                displayTip("底库人脸个数：" + count, facesetsCountTv);
                identityStatus = IDENTITY_IDLE;
            }
        });
    }

    private void asyncIdentity(final ImageFrame imageFrame, final FaceInfo faceInfo) {
        if (identityStatus != IDENTITY_IDLE) {
            return;
        }

        es.submit(new Runnable() {
            @Override
            public void run() {
                identity(imageFrame, faceInfo);
            }
        });
    }


    private void identity(ImageFrame imageFrame, FaceInfo faceInfo) {


        if (imageFrame == null || faceInfo == null) {
            return;
        }

        float raw  = Math.abs(faceInfo.headPose[0]);
        float patch  = Math.abs(faceInfo.headPose[1]);
        float roll  = Math.abs(faceInfo.headPose[2]);
        // 人脸的三个角度大于20不进行识别
        if (raw > 20 || patch > 20 ||  roll > 20) {
            return;
        }

        identityStatus = IDENTITYING;

        long starttime = System.currentTimeMillis();
        int[] argb = imageFrame.getArgb();
        int rows = imageFrame.getHeight();
        int cols = imageFrame.getWidth();
        int[] landmarks = faceInfo.landmarks;
        IdentifyRet identifyRet = FaceApi.getInstance().identity(argb, rows, cols, landmarks, groupId);


        displayUserOfMaxScore(identifyRet.getUserId(), identifyRet.getScore());
        identityStatus = IDENTITY_IDLE;
        displayTip("特征抽取对比耗时：" + (System.currentTimeMillis() - starttime), featureDurationTv);
    }

    private void displayUserOfMaxScore(final String userId, final float score) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (userIdOfMaxScore.equals(userId) ) {
                    if (score < maxScore) {
                        return;
                    } else {
                        maxScore = score;
                        scoreTv.setText("" + score);
                        return;
                    }
                } else {
                    userIdOfMaxScore = userId;
                    maxScore = score;
                }

                scoreTv.setText("" + score);
                User user = FaceApi.getInstance().getUserInfo(groupId, userId);
                if (user == null) {
                    return;
                }
                matchUserTv.setText(user.getUserInfo());
                List<Feature> featureList = user.getFeatureList();
                if (featureList != null && featureList.size() > 0) {
                    File faceDir = FileUitls.getFaceDirectory();
                    if (faceDir != null && faceDir.exists()) {
                        File file = new File(faceDir, featureList.get(0).getImageName());
                        if (file != null && file.exists()) {
                            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                            matchAvatorIv.setImageBitmap(bitmap);
                        }
                    }
                }
            }
        });
    }

    private void registerHomeListener() {
        mHomeListener = new HomeKeyListener(this);
        mHomeListener.setOnHomePressedListener(new HomeKeyListener.OnHomePressedListener() {

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


    private void displayTip(final String text, final TextView textView) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textView.setText(text);
            }
        });
    }

    private void toast(final String tip) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(OrbbecVideoIdentifyActivity.this, tip, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean shouldUpload = true;

    // 上传一帧至服务器进行，人脸识别。
    private void upload(final Bitmap face) {

        shouldUpload = false;
        try {
            final File file = File.createTempFile(UUID.randomUUID().toString() + "", ".jpg");
            // 人脸识别不需要整张图片。可以对人脸区别进行裁剪。减少流量消耗和，网络传输占用的时间消耗。
            ImageUtils.resize(face, file, 200, 200);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
