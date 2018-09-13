/*
 * Copyright (C) 2018 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.aip.ofr;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.TextureView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.aip.ImageFrame;
import com.baidu.aip.callback.ILivenessCallBack;
import com.baidu.aip.entity.LivenessModel;
import com.baidu.aip.face.FaceCropper;
import com.baidu.aip.iminect.ColorSurfaceView;
import com.baidu.aip.iminect.Jni;
import com.baidu.aip.manager.FaceEnvironment;
import com.baidu.aip.manager.FaceLiveness;
import com.baidu.aip.manager.FaceSDKManager;
import com.baidu.aip.utils.FileUitls;
import com.baidu.aip.utils.ImageUtils;
import com.baidu.idl.facesdk.FaceInfo;
import com.baidu.idl.facesdk.FaceTracker;
import com.hjimi.api.iminect.ImiCamera;
import com.hjimi.api.iminect.ImiCameraFrame;
import com.hjimi.api.iminect.ImiCameraFrameMode;
import com.hjimi.api.iminect.ImiCameraPixelFormat;
import com.hjimi.api.iminect.ImiDevice;
import com.hjimi.api.iminect.ImiDeviceAttribute;
import com.hjimi.api.iminect.ImiDeviceState;
import com.hjimi.api.iminect.ImiFrameMode;
import com.hjimi.api.iminect.ImiFrameType;
import com.hjimi.api.iminect.ImiImageFrame;
import com.hjimi.api.iminect.ImiNect;
import com.hjimi.api.iminect.ImiPixelFormat;
import com.hjimi.api.iminect.Utils;
import com.orbbec.obDepth2.HomeKeyListener;
import com.orbbec.view.OpenGLView;

import org.openni.Device;
import org.openni.VideoStream;
import org.openni.android.OpenNIHelper;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.baidu.aip.manager.FaceLiveness.MASK_RGB;

/**
 * 自动检测获取人脸
 */
public class IminectLivenessDetectActivity extends AppCompatActivity {

    private static String TAG = "IminectLivenessDetectActivity";
    ImageView mImageView;
    private TextView tipTv;
    private HomeKeyListener mHomeListener;
    private HandlerThread mHThread;
    private Handler mHandler;
    private Activity mContext;
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
    private final int depthNeedPermission = 33;
    private Object sync = new Object();
    private boolean exit = false;
    private ColorSurfaceView mColorSurfaceView;
    private ColorSurfaceView mDepthSurfaceView;
    private TextView rgbLivenessResult;
    private TextView depthLivenessResult;
    boolean rgblivenessSuccess = false;
    private static ImiNect m_ImiNect = null;
    private static MainListener mMainListener = null;
    private ImiDevice mImiDevice0 = null;
    private ImiDeviceAttribute mDeviceAttribute = null;
    private boolean mIsExitLoop = false;
    private ByteBuffer mColorBuffer;
    private ByteBuffer mDepthBuffer;
    private boolean mIsDigging = false;
    private ImiPixelFormat mColorPixel;

    private ImiCamera mCamera = null;

    private int expectUserFrameWidth = 640;
    private int expectUserFrameHeight = 480;

    private static final int DEVICE_OPEN_FALIED = 1;
    private static final int DEVICE_DISCONNECT = 2;

    private static final int REQUEST_CAMERA_CODE = 0x007;
    // textureView用于绘制人脸框等。
    private TextureView textureView;


    private ImiDeviceState deviceState = ImiDeviceState.IMI_DEVICE_STATE_CONNECT;


    private Handler mainHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case DEVICE_OPEN_FALIED:
                    break;
                case DEVICE_DISCONNECT:
                    showMessageDialog();
                    break;
                default:
                    break;
            }
        }
    };

    private void showMessageDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("The device is not connected!!!");
        builder.setPositiveButton("quit", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int arg1) {
                dialog.dismiss();
                finish();
            }
        });
        builder.show();
    }

    class MainListener implements ImiCamera.OpenCameraListener, ImiDevice.OpenDeviceListener,
            ImiDevice.DeviceStateListener {

        @Override
        public void onOpenCameraSuccess() {
            mCamera = m_ImiNect.Camera();

            m_ImiNect.Device().open(mMainListener);
        }

        @Override
        public void onOpenCameraFailed(String errorMsg) {
            mainHandler.sendEmptyMessage(DEVICE_OPEN_FALIED);
        }

        @Override
        public void onOpenDeviceSuccess() {
            mImiDevice0 = m_ImiNect.Device();
            mDeviceAttribute = mImiDevice0.getAttribute();

            try {
                setUserExpectMode(expectUserFrameWidth, expectUserFrameHeight);
            } catch (Exception e) {
                Log.e(TAG, "setUserExpectMode: falied, invalid frame mode");
            }
            mImiDevice0.setImageRegistration(true); // set image registration.
            new Thread(new ColorViewRefreshRunnable()).start();
            FaceLiveness.getInstance().setLivenessCallBack(new ILivenessCallBack() {
                @Override
                public void onCallback(LivenessModel livenessModel) {
                    checkResult(livenessModel);
                }

                @Override
                public void onTip(int code, final String msg) {
                    Log.i("wtf", "onCallback" + msg);
                }

                @Override
                public void onCanvasRectCallback(LivenessModel livenessModel) {
                    if ((livenessModel.getLiveType() & MASK_RGB) == MASK_RGB) {
                        showFrame(livenessModel.getImageFrame(), livenessModel.getTrackFaceInfo());
                    }
                }
            });
        }

        @Override
        public void onOpenDeviceFailed(String errorMsg) {
            mainHandler.sendEmptyMessage(DEVICE_OPEN_FALIED);
        }

        @Override
        public void onDeviceStateChanged(String deviceUri, ImiDeviceState state) {
            if (state == ImiDeviceState.IMI_DEVICE_STATE_DISCONNECT) {
                // device disconnect.
                // Toast.makeText(MainActivity.this, s+" DISCONNECT", Toast.LENGTH_SHORT).show();
                if (mDeviceAttribute != null && mDeviceAttribute.getUri().equals(deviceUri)) {
                    deviceState = ImiDeviceState.IMI_DEVICE_STATE_DISCONNECT;
                    mainHandler.sendEmptyMessage(DEVICE_DISCONNECT);
                }
            } else if (state == ImiDeviceState.IMI_DEVICE_STATE_CONNECT) {
                // Toast.makeText(MainActivity.this, s+" CONNECT", Toast.LENGTH_SHORT).show();
                if (mDeviceAttribute != null && mDeviceAttribute.getUri().equals(deviceUri)) {
                    deviceState = ImiDeviceState.IMI_DEVICE_STATE_CONNECT;
                }
            }
        }
    }

    private void setUserExpectMode(int width, int height) {
        ImiFrameMode frameMode = new ImiFrameMode(ImiPixelFormat.IMI_PIXEL_FORMAT_DEP_16BIT, width, height);
        mImiDevice0.setFrameMode(ImiFrameType.DEPTH, frameMode);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_iminct_liveness_detect);
        findView();

        mContext = this;

        // registerHomeListener();
        es = Executors.newSingleThreadExecutor();

        Intent intent = getIntent();
        if (intent != null) {
            source = intent.getIntExtra("source", -1);
        }
    }


    private void findView() {

        textureView=findViewById(R.id.texture_view);

        tipTv = (TextView) findViewById(R.id.message);

        mDepthSurfaceView = (ColorSurfaceView) findViewById(R.id.depthGlView);
        mColorSurfaceView = (ColorSurfaceView) findViewById(R.id.rgbGlView);

        detectDurationTv = (TextView) findViewById(R.id.detect_duration_tv);
        rgbLivenssDurationTv = (TextView) findViewById(R.id.rgb_liveness_duration_tv);
        rgbLivenessScoreTv = (TextView) findViewById(R.id.rgb_liveness_score_tv);
        depthLivenssDurationTv = (TextView) findViewById(R.id.depth_liveness_duration_tv);
        depthLivenessScoreTv = (TextView) findViewById(R.id.depth_liveness_score_tv);

        m_ImiNect = ImiNect.create(IminectLivenessDetectActivity.this);

        mMainListener = new MainListener();

        m_ImiNect.Device().addDeviceStateListener(mMainListener);

        if (isCameraPermission(IminectLivenessDetectActivity.this, REQUEST_CAMERA_CODE)) {
            m_ImiNect.Camera().open(mMainListener);
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
        mIsExitLoop = true;

        if (mImiDevice0 != null) {
            mImiDevice0.closeStream(ImiFrameType.DEPTH);
            mImiDevice0.close();
            mImiDevice0 = null;
        }

        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.close();
            mCamera = null;
        }
        m_ImiNect.destroy();
        finish();
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


    public boolean isCameraPermission(Activity context, int requestCode) {
        if (Build.VERSION.SDK_INT >= 23) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(context, new String[]{Manifest.permission.CAMERA,
                        Manifest.permission.READ_EXTERNAL_STORAGE}, requestCode);
                Toast.makeText(this, "requestPermissions Camera Permission",
                        Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        Toast.makeText(this, "Already have Camera Permission", Toast.LENGTH_SHORT).show();
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == depthNeedPermission) {

            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permission Grant");
                Toast.makeText(mContext, "Permission Grant", Toast.LENGTH_SHORT).show();
            } else {
                Log.d(TAG, "Permission Denied");
                Toast.makeText(mContext, "Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private class ColorViewRefreshRunnable implements Runnable {

        @Override
        public void run() {
            try {
                ImiCameraFrameMode frameMode = new ImiCameraFrameMode(
                        ImiCameraPixelFormat.IMI_CAMERA_PIXEL_FORMAT_RGB888,
                        640, 480, 30);
                mCamera.startPreview(frameMode);

                ImiFrameMode depthFrameMode = new ImiFrameMode(ImiPixelFormat.IMI_PIXEL_FORMAT_DEP_16BIT,
                        640, 480);
                mImiDevice0.setFrameMode(ImiFrameType.DEPTH, depthFrameMode);
                mImiDevice0.openStream(ImiFrameType.DEPTH);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
            ImiCameraFrame colorFrame = null;
            ImiImageFrame depthFrame = null;
            ByteBuffer depthByteBuf = null;
            ByteBuffer colorByteBuf = null;
            Jni jni = new Jni();

            while (!mIsExitLoop) {

                if (depthFrame == null) {
                    depthFrame = mImiDevice0.readNextFrame(ImiFrameType.DEPTH, 40);
                }

                if (colorFrame == null) {
                    colorFrame = mCamera.readNextFrame(40);
                }
                if (null == depthFrame || null == colorFrame) {
                    continue;
                }

                mColorBuffer = colorFrame.getData();
                mDepthBuffer = depthFrame.getData();
                ByteBuffer depthframeData;
                depthframeData = Utils.depth2RGB888(depthFrame, true, false);
                int rgbLen = mColorBuffer.remaining();
                byte[] rgbByte = new byte[rgbLen];
                mColorBuffer.get(rgbByte);
                final Bitmap bitmap = ImageUtils.RGB2Bitmap(rgbByte, 640, 480);
                mColorSurfaceView.updateVertices(mColorBuffer);
                mDepthSurfaceView.updateVertices(depthframeData);
                int depthLen = mDepthBuffer.remaining();
                byte[] depthByte = new byte[depthLen];
                mDepthBuffer.get(depthByte);
                FaceLiveness.getInstance().setRgbBitmap(bitmap);
                FaceLiveness.getInstance().setDepthData(depthByte);
                FaceLiveness.getInstance().livenessCheck(640, 480, 0X0101);
                colorFrame = null;
                depthFrame = null;
            }
        }
    }

    ;


    private void checkResult(LivenessModel model) {

        if (model == null) {
            clearTip();
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
                    Log.d(TAG, FaceLiveness.MASK_IR + "");
                }

                if ((type & FaceLiveness.MASK_DEPTH) == FaceLiveness.MASK_DEPTH) {
                    depthLivenessScoreTv.setText("Depth活体得分：" + livenessModel.getDepthLivenessScore());
                    depthLivenssDurationTv.setText("Depth活体耗时：" + livenessModel.getDetphtLivenessDuration());
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
                rgbLivenssDurationTv.setText("");
                depthLivenessScoreTv.setText("");
                depthLivenssDurationTv.setText("");
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
                Toast.makeText(IminectLivenessDetectActivity.this, tip, Toast.LENGTH_SHORT).show();
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
        } else if (faceInfo.mCenter_x > bitMapWidth * 3 / 4) {
            tipTv.setText("人脸在屏幕中太靠右");
            return false;
        } else if (faceInfo.mCenter_x < bitMapWidth / 4) {
            tipTv.setText("人脸在屏幕中太靠左");
            // clearInfo();
            return false;
        } else if (faceInfo.mCenter_y > bitMapHeight * 3 / 4) {
            tipTv.setText("人脸在屏幕中太靠下");
            // clearInfo();
            return false;
        } else if (faceInfo.mCenter_x < bitMapHeight / 4) {
            tipTv.setText("人脸在屏幕中太靠上");
            // clearInfo();
            return false;
        }

        return true;
    }

    private Paint paint = new Paint();

    {
        paint.setColor(Color.YELLOW);
        paint.setStyle(Paint.Style.STROKE);
        paint.setTextSize(30);
    }

    RectF rectF = new RectF();

    /**
     * 绘制人脸框。
     */
    private void showFrame(ImageFrame imageFrame, FaceInfo[] faceInfos) {
        Canvas canvas = textureView.lockCanvas();
        if (canvas == null) {
            textureView.unlockCanvasAndPost(canvas);
            return;
        }
        if (faceInfos == null || faceInfos.length == 0) {
            // 清空canvas
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            textureView.unlockCanvasAndPost(canvas);
            return;
        }
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        FaceInfo faceInfo = faceInfos[0];

        rectF.set(getFaceRectTwo(faceInfo, imageFrame));

        // 检测图片的坐标和显示的坐标不一样，需要转换。
        // mPreview[typeIndex].mapFromOriginalRect(rectF);

        float yaw = Math.abs(faceInfo.headPose[0]);
        float patch = Math.abs(faceInfo.headPose[1]);
        float roll = Math.abs(faceInfo.headPose[2]);
        if (yaw > 20 || patch > 20 || roll > 20) {
            // 不符合要求，绘制黄框
            paint.setColor(Color.YELLOW);

            String text = "请正视屏幕";
            float width = paint.measureText(text) + 50;
            float x = rectF.centerX() - width / 2;
            paint.setColor(Color.RED);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawText(text, x + 25, rectF.top - 20, paint);
            paint.setColor(Color.YELLOW);

        } else {
            // 符合检测要求，绘制绿框
            paint.setColor(Color.GREEN);
        }
        paint.setStyle(Paint.Style.STROKE);
        // 绘制框
        canvas.drawRect(rectF, paint);
        textureView.unlockCanvasAndPost(canvas);
    }

    public Rect getFaceRectTwo(FaceInfo faceInfo, ImageFrame frame) {
        Rect rect = new Rect();
        int[] points = new int[8];
        faceInfo.getRectPoints(points);
        int left = points[2];
        int top = points[3];
        int right = points[6];
        int bottom = points[7];
//        int previewWidth=surfaViews[typeIndex].getWidth();
//        int previewHeight=surfaViews[typeIndex].getHeight();
        int previewWidth = mColorSurfaceView.getWidth();
        int previewHeight = mColorSurfaceView.getHeight();
        float scaleW = 1.0f * previewWidth / frame.getWidth();
        float scaleH = 1.0f * previewHeight / frame.getHeight();
        int width = (right - left);
        int height = (bottom - top);
        left = (int) ((faceInfo.mCenter_x - width/2) * scaleW);
        top = (int) ((faceInfo.mCenter_y - height/2) * scaleW);
//        left = (int) ((faceInfo.mCenter_x)* scaleW);
//        top =  (int) ((faceInfo.mCenter_y) * scaleW);
        rect.top = top < 0 ? 0 : top;
        rect.left = left < 0 ? 0 : left;
        rect.right = (left + width) > frame.getWidth() ? frame.getWidth() : (left + width);
        rect.bottom = (top + height) > frame.getHeight() ? frame.getHeight() : (top + height);
        return rect;
    }

}
