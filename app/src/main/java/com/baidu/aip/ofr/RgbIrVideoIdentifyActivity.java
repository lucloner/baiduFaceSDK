/*
 * Copyright (C) 2018 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.aip.ofr;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.baidu.aip.ImageFrame;
import com.baidu.aip.api.FaceApi;
import com.baidu.aip.callback.ILivenessCallBack;
import com.baidu.aip.entity.Feature;
import com.baidu.aip.entity.IdentifyRet;
import com.baidu.aip.entity.LivenessModel;
import com.baidu.aip.entity.User;
import com.baidu.aip.manager.FaceEnvironment;
import com.baidu.aip.manager.FaceLiveness;
import com.baidu.aip.ofr.utils.GlobalFaceTypeModel;
import com.baidu.aip.utils.FileUitls;
import com.baidu.aip.utils.PreferencesUtil;
import com.baidu.idl.facesdk.FaceInfo;
import com.baidu.idl.facesdk.FaceSDK;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import static com.baidu.aip.manager.FaceLiveness.MASK_RGB;

public class RgbIrVideoIdentifyActivity extends Activity implements ILivenessCallBack {

    private static final String TAG = "FaceRgbIrLiveness";
    private static final int FEATURE_DATAS_UNREADY = 1;
    private static final int IDENTITY_IDLE = 2;
    private static final int IDENTITYING = 3;
    // 图片越大，性能消耗越大，也可以选择640*480， 1280*720
    private static final int PREFER_WIDTH = 640;
    private static final int PERFER_HEIGH = 480;

    Preview[] mPreview;
    Camera[] mCamera;
    private int mCameraNum;
    private ImageView testIv;

    private TextView tipTv;

    private ImageView matchAvatorIv;
    private TextView matchUserTv;
    private TextView scoreTv;
    private TextView facesetsCountTv;
    private TextView detectDurationTv;
    private TextView rgbLivenssDurationTv;
    private TextView rgbLivenessScoreTv;
    private TextView irLivenssDurationTv;
    private TextView irLivenessScoreTv;
    private TextView featureDurationTv;

    private String groupId = "";

    private volatile int identityStatus = FEATURE_DATAS_UNREADY;
    private String userIdOfMaxScore = "";
    private float maxScore = 0;

    private volatile int[] niRargb;
    private volatile int[] rgbData;
    private volatile byte[] irData;
    private int camemra1DataMean;
    private int camemra2DataMean;
    private volatile boolean camemra1IsRgb = false;
    private volatile boolean rgbOrIrConfirm = false;

    // textureView用于绘制人脸框等。
    private TextureView textureView;
    private TextureView textureViewOne;
    private SurfaceView surface_one, surface_two;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rgb_ir_video_identify);
        FaceSDK.initModel(this);
        findView();

        FaceLiveness.getInstance().setLivenessCallBack(this);
        loadFeature2Memery();

        Intent intent = getIntent();
        if (intent != null) {
            groupId = intent.getStringExtra("group_id");
        }
    }

    @SuppressLint("WrongViewCast")
    private void findView() {
        textureViewOne=findViewById(R.id.texture_view_one);
        textureViewOne.setOpaque(false);
        textureView = findViewById(R.id.texture_view);
        textureView.setOpaque(false);
        surface_one = findViewById(R.id.surface_one);
        surface_two = findViewById(R.id.surface_two);
        tipTv = (TextView) findViewById(R.id.tip_tv);
        matchAvatorIv = (ImageView) findViewById(R.id.match_avator_iv);
        matchUserTv = (TextView) findViewById(R.id.match_user_tv);
        scoreTv = (TextView) findViewById(R.id.score_tv);
        facesetsCountTv = (TextView) findViewById(R.id.facesets_count_tv);
        detectDurationTv = (TextView) findViewById(R.id.detect_duration_tv);
        rgbLivenssDurationTv = (TextView) findViewById(R.id.rgb_liveness_duration_tv);
        rgbLivenessScoreTv = (TextView) findViewById(R.id.rgb_liveness_score_tv);
        irLivenssDurationTv = (TextView) findViewById(R.id.ir_liveness_duration_tv);
        irLivenessScoreTv = (TextView) findViewById(R.id.ir_liveness_score_tv);
        featureDurationTv = (TextView) findViewById(R.id.feature_duration_tv);

        mCameraNum = Camera.getNumberOfCameras();
        testIv = (ImageView) findViewById(R.id.test_iv);

        if (mCameraNum != 2) {
            Toast.makeText(this, "未检测到2个摄像头", Toast.LENGTH_LONG).show();
            return;
        }else {
            SurfaceView[] surfaViews = new SurfaceView[mCameraNum];
            mPreview = new Preview[mCameraNum];
            mCamera = new Camera[mCameraNum];
            mPreview[0] = new Preview(this, surface_one);
            mPreview[1] = new Preview(this, surface_two);
        }

//        for (int i = 0; i < mCameraNum; i++) {
////            surfaViews[i] = new SurfaceView(this);
////            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
////                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
////            // lp.setMargins(10, 10, 10, 10);
////            surfaViews[i].setLayoutParams(lp);
////            ((LinearLayout) findViewById(R.id.camera_layout)).addView(surfaViews[i]);
////
////            mPreview[i] = new Preview(this, surfaViews[i]);
////            mPreview[i].setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
////            ((RelativeLayout) findViewById(R.id.layout)).addView(mPreview[i]);
////        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (mCameraNum != 2) {
            Toast.makeText(this, "未检测到2个摄像头", Toast.LENGTH_LONG).show();
            return;
        }else {
            mCamera[0] = Camera.open(0);
            mCamera[1] = Camera.open(1);
            mPreview[0].setCamera(mCamera[0], PREFER_WIDTH, PERFER_HEIGH);
            mPreview[1].setCamera(mCamera[1], PREFER_WIDTH, PERFER_HEIGH);

            mCamera[0].setPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    if (rgbOrIrConfirm) {
                        choiceRgbOrIrType(0, data);
                    } else if (camemra1DataMean == 0) {
                        rgbOrIr(0, data);
                    }
                }
            });
            mCamera[1].setPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    if (rgbOrIrConfirm) {
                        choiceRgbOrIrType(1, data);
                    } else if (camemra2DataMean == 0) {
                        rgbOrIr(1, data);
                    }

                }
            });
        }
        if (textureView != null) {
            textureView.setOpaque(false);
        }
        if (textureViewOne!=null){
            textureViewOne.setOpaque(false);
        }
    }

    private synchronized void rgbOrIr(int index, byte[] data) {
        byte[] tmp = new byte[PREFER_WIDTH * PERFER_HEIGH];
        System.arraycopy(data, 0, tmp, 0, PREFER_WIDTH * PERFER_HEIGH);
        int count = 0;
        int total = 0;
        for (int i = 0; i < PREFER_WIDTH * PERFER_HEIGH; i = i + 100) {
            total += byteToInt(tmp[i]);
            count++;
        }

        if (index == 0) {
            camemra1DataMean = total / count;
        } else {
            camemra2DataMean = total / count;
        }
        if (camemra1DataMean != 0 && camemra2DataMean != 0) {
            if (camemra1DataMean > camemra2DataMean) {
                camemra1IsRgb = true;
            } else {
                camemra1IsRgb = false;
            }
            rgbOrIrConfirm = true;
        }
    }

    public int byteToInt(byte b) {
        //Java 总是把 byte 当做有符处理；我们可以通过将其和 0xFF 进行二进制与得到它的无符值
        return b & 0xFF;
    }

    private void choiceRgbOrIrType(int index, byte[] data) {
        // camera1如果为rgb数据，调用dealRgb，否则为Ir数据，调用Ir
        if (index == 0) {
            if (camemra1IsRgb) {
                dealRgb(data);
            } else {
                dealIr(data);
            }
        } else {
            if (camemra1IsRgb) {
                dealIr(data);
            } else {
                dealRgb(data);
            }
        }
    }

    private void dealRgb(byte[] data) {
        if (rgbData == null) {
            int[] argb = new int[PREFER_WIDTH * PERFER_HEIGH];
            FaceSDK.getARGBFromYUVimg(data, argb, PREFER_WIDTH, PERFER_HEIGH, 0, 0);

            rgbData = argb;
            checkData();
            final Bitmap bitmap = Bitmap.createBitmap(argb, PREFER_WIDTH, PERFER_HEIGH, Bitmap.Config.ARGB_8888);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    testIv.setImageBitmap(bitmap);
                }
            });
        }
    }

    private void dealIr(byte[] data) {
        if (irData == null) {
            // int[] argb = new int[640 * 480];
            // FaceSDK.getARGBFromYUVimg(data, argb, 480, 640, 0, 0);

            niRargb = new int[PREFER_WIDTH * PERFER_HEIGH];
            FaceSDK.getARGBFromYUVimg(data, niRargb, PERFER_HEIGH, PREFER_WIDTH, 0, 0);

            byte[] ir = new byte[PREFER_WIDTH * PERFER_HEIGH];
            System.arraycopy(data, 0, ir, 0, PREFER_WIDTH * PERFER_HEIGH);
            irData = ir;
            checkData();
        }
    }

    private synchronized void checkData() {
        if (rgbData != null && irData != null) {
            FaceLiveness.getInstance().setNirRgbInt(niRargb);
            FaceLiveness.getInstance().setRgbInt(rgbData);
            FaceLiveness.getInstance().setIrData(irData);
            FaceLiveness.getInstance().livenessCheck(PREFER_WIDTH, PERFER_HEIGH, 0x0011);
            rgbData = null;
            irData = null;
        }
    }

    @Override
    protected void onPause() {
        for (int i = 0; i < mCameraNum; i++) {
            if (mCameraNum>=2){
                if (mCamera[i] != null) {
                    mCamera[i].setPreviewCallback(null);
                    mCamera[i].stopPreview();
                    mPreview[i].setCamera(null, PREFER_WIDTH, PERFER_HEIGH);
                    mCamera[i].release();
                    mCamera[i] = null;
                }
            }
        }
        super.onPause();
    }


    @Override
    public void onCallback(LivenessModel livenessModel) {
        if ((livenessModel.getLiveType() & MASK_RGB) == MASK_RGB) {
            showFrame(livenessModel.getImageFrame(), livenessModel.getTrackFaceInfo());
        }
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

    @Override
    public void onCanvasRectCallback(LivenessModel livenessModel) {
        if ((livenessModel.getLiveType() & MASK_RGB) == MASK_RGB) {
            showFrame(livenessModel.getImageFrame(), livenessModel.getTrackFaceInfo());
        }
    }

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
                    irLivenessScoreTv.setText("IR活体得分：" + livenessModel.getIrLivenessScore());
                    irLivenssDurationTv.setText("IR活体耗时：" + livenessModel.getIrLivenessDuration());
                }

            }

        });
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
                Toast.makeText(RgbIrVideoIdentifyActivity.this, tip, Toast.LENGTH_LONG).show();
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
                irLivenessScoreTv.setText("");
                irLivenssDurationTv.setText("");
                featureDurationTv.setText("");
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

        float raw = Math.abs(faceInfo.headPose[0]);
        float patch = Math.abs(faceInfo.headPose[1]);
        float roll = Math.abs(faceInfo.headPose[2]);
        // 人脸的三个角度大于20不进行识别
        if (raw > 15 || patch > 15 || roll > 15) {
            return;
        }

        identityStatus = IDENTITYING;

        long starttime = System.currentTimeMillis();
        int[] argb = imageFrame.getArgb();
        int rows = imageFrame.getHeight();
        int cols = imageFrame.getWidth();
        int[] landmarks = faceInfo.landmarks;
        int type = PreferencesUtil.getInt(GlobalFaceTypeModel.TYPE_MODEL, GlobalFaceTypeModel.RECOGNIZE_LIVE);
        IdentifyRet identifyRet = null;
        if (type == GlobalFaceTypeModel.RECOGNIZE_LIVE) {
            identifyRet = FaceApi.getInstance().identity(argb, rows, cols, landmarks, groupId);
        } else if (type == GlobalFaceTypeModel.RECOGNIZE_ID_PHOTO) {
            identifyRet = FaceApi.getInstance().identityForIDPhoto(argb, rows, cols, landmarks, groupId);
        }

        displayUserOfMaxScore(identifyRet.getUserId(), identifyRet.getScore());
        identityStatus = IDENTITY_IDLE;
        displayTip("特征抽取对比耗时：" + (System.currentTimeMillis() - starttime), featureDurationTv);
    }

    private void displayUserOfMaxScore(final String userId, final float score) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (score < 80) {
                    scoreTv.setText("");
                    matchUserTv.setText("");
                    matchAvatorIv.setImageBitmap(null);
                    return;
                }

                if (userIdOfMaxScore.equals(userId)) {
                    if (score < maxScore) {
                        scoreTv.setText("" + score);
                    } else {
                        maxScore = score;
                        scoreTv.setText("" + score);
                    }
                    if (matchUserTv.getText().toString().length() > 0) {
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
        if (camemra1IsRgb){
            Canvas canvas2 = textureViewOne.lockCanvas();
            if (canvas2 == null) {
                textureViewOne.unlockCanvasAndPost(canvas2);
                return;
            }
            if (faceInfos == null || faceInfos.length == 0) {
                // 清空canvas
                canvas2.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                textureViewOne.unlockCanvasAndPost(canvas2);
                return;
            }
            canvas2.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            FaceInfo faceInfo2 = faceInfos[0];
            rectF.set(getFaceRectTwo(faceInfo2, imageFrame));
            // 检测图片的坐标和显示的坐标不一样，需要转换。
            // mPreview[typeIndex].mapFromOriginalRect(rectF);
            float yaw2 = Math.abs(faceInfo2.headPose[0]);
            float patch2 = Math.abs(faceInfo2.headPose[1]);
            float roll2 = Math.abs(faceInfo2.headPose[2]);
            if (yaw2 > 20 || patch2 > 20 || roll2 > 20) {
                // 不符合要求，绘制黄框
                paint.setColor(Color.YELLOW);

                String text = "请正视屏幕";
                float width = paint.measureText(text) + 50;
                float x = rectF.centerX() - width / 2;
                paint.setColor(Color.RED);
                paint.setStyle(Paint.Style.FILL);
                canvas2.drawText(text, x + 25, rectF.top - 20, paint);
                paint.setColor(Color.YELLOW);
            } else {
                // 符合检测要求，绘制绿框
                paint.setColor(Color.GREEN);
            }
            paint.setStyle(Paint.Style.STROKE);
            // 绘制框
            canvas2.drawRect(rectF, paint);
            textureViewOne.unlockCanvasAndPost(canvas2);


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
            textureView.unlockCanvasAndPost(canvas);

        }else{
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


            Canvas canvas2 = textureViewOne.lockCanvas();
            if (canvas2 == null) {
                textureViewOne.unlockCanvasAndPost(canvas2);
                return;
            }
            if (faceInfos == null || faceInfos.length == 0) {
                // 清空canvas
                canvas2.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                textureViewOne.unlockCanvasAndPost(canvas2);
                return;
            }
            canvas2.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            textureViewOne.unlockCanvasAndPost(canvas2);
        }
    }
    /**
     * 获取人脸框区域。
     *
     * @return 人脸框区域
     */
    // TODO padding?
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
        int previewWidth;
        int previewHeight;

//        previewWidth = surface_one.getWidth();
//        previewHeight = surface_one.getHeight();
        if (camemra1IsRgb){
            previewWidth = surface_one.getMeasuredWidth();
            previewHeight = surface_one.getMeasuredHeight();
        }else{
            previewWidth = surface_two.getMeasuredWidth();
            previewHeight = surface_two.getMeasuredHeight();
        }

        float scaleW = 1.0f * previewWidth / frame.getWidth();
        float scaleH = 1.0f * previewHeight / frame.getHeight();
        int width = (right - left);
//        int width= (int) faceInfo.mWidth;
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


