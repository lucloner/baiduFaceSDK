/*
 * Copyright (C) 2018 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.aip.ofr;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.baidu.aip.ImageFrame;
import com.baidu.aip.api.FaceApi;
import com.baidu.aip.db.DBManager;
import com.baidu.aip.entity.Feature;
import com.baidu.aip.entity.IdentifyRet;
import com.baidu.aip.entity.User;
import com.baidu.aip.face.CameraImageSource;
import com.baidu.aip.face.FaceDetectManager;
import com.baidu.aip.face.PreviewView;
import com.baidu.aip.face.camera.CameraView;
import com.baidu.aip.face.camera.ICameraControl;
import com.baidu.aip.manager.FaceEnvironment;
import com.baidu.aip.manager.FaceLiveness;
import com.baidu.aip.manager.FaceSDKManager;
import com.baidu.aip.utils.FileUitls;
import com.baidu.aip.utils.PreferencesUtil;
import com.baidu.idl.facesdk.FaceInfo;
import com.baidu.idl.facesdk.FaceTracker;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class RgbVideoIdentityActivity extends Activity implements View.OnClickListener {

    private static final int FEATURE_DATAS_UNREADY = 1;
    private static final int IDENTITY_IDLE = 2;
    private static final int IDENTITYING = 3;
    // 预览View;
    private PreviewView previewView;
    // textureView用于绘制人脸框等。
    private TextureView textureView;
    // 用于检测人脸。
    private FaceDetectManager faceDetectManager;

    // 为了方便调式。
    private ImageView testView;
    private TextView userOfMaxSocre;
    private ImageView matchAvatorIv;
    private TextView matchUserTv;
    private TextView scoreTv;
    private TextView facesetsCountTv;
    private TextView detectDurationTv;
    private TextView rgbLivenssDurationTv;
    private TextView rgbLivenessScoreTv;
    private TextView featureDurationTv;
    private Handler handler = new Handler();
    private String groupId = "";

    private volatile int identityStatus = FEATURE_DATAS_UNREADY;
    private String userIdOfMaxScore = "";
    private float maxScore = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_identify);
        findView();

        init();
        addListener();
        DBManager.getInstance().init(this);
        loadFeature2Memery();
    }

    private void findView() {
        testView = (ImageView) findViewById(R.id.test_view);
        userOfMaxSocre = (TextView) findViewById(R.id.user_of_max_score_tv);
        previewView = (PreviewView) findViewById(R.id.preview_view);
        textureView = (TextureView) findViewById(R.id.texture_view);
        matchAvatorIv = (ImageView) findViewById(R.id.match_avator_iv);
        matchUserTv = (TextView) findViewById(R.id.match_user_tv);
        scoreTv = (TextView) findViewById(R.id.score_tv);
        facesetsCountTv = (TextView) findViewById(R.id.facesets_count_tv);
        detectDurationTv = (TextView) findViewById(R.id.detect_duration_tv);
        rgbLivenssDurationTv = (TextView) findViewById(R.id.rgb_liveness_duration_tv);
        rgbLivenessScoreTv = (TextView) findViewById(R.id.rgb_liveness_score_tv);
        featureDurationTv = (TextView) findViewById(R.id.feature_duration_tv);
    }

    private void init() {
        Intent intent = getIntent();
        if (intent != null) {
            groupId = intent.getStringExtra("group_id");
        }

        faceDetectManager = new FaceDetectManager(getApplicationContext());
        // 从系统相机获取图片帧。
        final CameraImageSource cameraImageSource = new CameraImageSource(this);
        // 图片越小检测速度越快，闸机场景640 * 480 可以满足需求。实际预览值可能和该值不同。和相机所支持的预览尺寸有关。
        // 可以通过 camera.getParameters().getSupportedPreviewSizes()查看支持列表。
        cameraImageSource.getCameraControl().setPreferredPreviewSize(1280, 720);
        // cameraImageSource.getCameraControl().setPreferredPreviewSize(640, 480);

        // 设置最小人脸，该值越小，检测距离越远，该值越大，检测性能越好。范围为80-200
        FaceSDKManager.getInstance().getFaceDetector().setMinFaceSize(100);
        // FaceSDKManager.getInstance().getFaceDetector().setNumberOfThreads(4);
        // 设置预览
        cameraImageSource.setPreviewView(previewView);
        // 设置图片源
        faceDetectManager.setImageSource(cameraImageSource);
        // 设置人脸过滤角度，角度越小，人脸越正，比对时分数越高
        faceDetectManager.getFaceFilter().setAngle(20);

        textureView.setOpaque(false);
        // 不需要屏幕自动变黑。
        textureView.setKeepScreenOn(true);

        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        if (isPortrait) {
            previewView.setScaleType(PreviewView.ScaleType.FIT_WIDTH);
            // 相机坚屏模式
            cameraImageSource.getCameraControl().setDisplayOrientation(CameraView.ORIENTATION_PORTRAIT);
        } else {
            previewView.setScaleType(PreviewView.ScaleType.FIT_HEIGHT);
            // 相机横屏模式
            cameraImageSource.getCameraControl().setDisplayOrientation(CameraView.ORIENTATION_HORIZONTAL);
        }

        setCameraType(cameraImageSource);
    }

    private void setCameraType(CameraImageSource cameraImageSource) {
        // TODO 选择使用前置摄像头
        // cameraImageSource.getCameraControl().setCameraFacing(ICameraControl.CAMERA_FACING_FRONT);

        // TODO 选择使用usb摄像头
        cameraImageSource.getCameraControl().setCameraFacing(ICameraControl.CAMERA_USB);
        // 如果不设置，人脸框会镜像，显示不准
        previewView.getTextureView().setScaleX(-1);

        // TODO 选择使用后置摄像头
//        cameraImageSource.getCameraControl().setCameraFacing(ICameraControl.CAMERA_FACING_BACK);
//        previewView.getTextureView().setScaleX(-1);
    }

    private void addListener() {
        // 设置回调，回调人脸检测结果。
        faceDetectManager.setOnFaceDetectListener(new FaceDetectManager.OnFaceDetectListener() {
            @Override
            public void onDetectFace(int retCode, FaceInfo[] infos, ImageFrame frame) {
                // TODO 显示检测的图片。用于调试，如果人脸sdk检测的人脸需要朝上，可以通过该图片判断
                final Bitmap bitmap =
                        Bitmap.createBitmap(frame.getArgb(), frame.getWidth(), frame.getHeight(), Bitmap.Config
                                .ARGB_8888);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        testView.setImageBitmap(bitmap);
                    }
                });
                if ( retCode == FaceTracker.ErrCode.OK.ordinal() && infos != null) {
                    asyncIdentity(frame, infos);
                }
                showFrame(frame, infos);

            }
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        // 开始检测
        faceDetectManager.start();
        faceDetectManager.setUseDetect(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 结束检测。
        faceDetectManager.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        faceDetectManager.stop();
    }

    @Override
    public void onClick(View v) {

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

    private void asyncIdentity(final ImageFrame imageFrame, final FaceInfo[] faceInfos) {
        if (identityStatus != IDENTITY_IDLE) {
            return;
        }

        es.submit(new Runnable() {
            @Override
            public void run() {
                if (faceInfos == null || faceInfos.length == 0) {
                    return;
                }
                int liveType = PreferencesUtil.getInt(LivenessSettingActivity.TYPE_LIVENSS, LivenessSettingActivity
                        .TYPE_NO_LIVENSS);
                if (liveType ==  LivenessSettingActivity.TYPE_NO_LIVENSS) {
                    identity(imageFrame, faceInfos[0]);
                } else if (liveType ==  LivenessSettingActivity.TYPE_RGB_LIVENSS) {

                    if (rgbLiveness(imageFrame, faceInfos[0]) > FaceEnvironment.LIVENESS_RGB_THRESHOLD) {
                        identity(imageFrame, faceInfos[0]);
                    } else {
                        // toast("rgb活体分数过低");
                    }
                }
            }
        });
    }

    private float rgbLiveness(ImageFrame imageFrame, FaceInfo faceInfo) {

        long starttime = System.currentTimeMillis();
        final float rgbScore = FaceLiveness.getInstance().rgbLiveness(imageFrame.getArgb(), imageFrame
                .getWidth(), imageFrame.getHeight(), faceInfo.landmarks);
        final long duration = System.currentTimeMillis() - starttime;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                rgbLivenssDurationTv.setVisibility(View.VISIBLE);
                rgbLivenessScoreTv.setVisibility(View.VISIBLE);
                rgbLivenssDurationTv.setText("RGB活体耗时：" + duration);
                rgbLivenessScoreTv.setText("RGB活体得分：" + rgbScore);
            }
        });

        return rgbScore;
    }

    private void identity(ImageFrame imageFrame, FaceInfo faceInfo) {


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
        displayTip("特征抽取对比耗时:" + (System.currentTimeMillis() - starttime), featureDurationTv) ;
    }

    private void append(Map<String, Float> userId2Score, String userId, float score) {
        if (userId2Score.size() <= 5) {
            userId2Score.put(userId, score);
            return;
        }

        userId2Score.put(userId, score);
        Iterator iterator = userId2Score.entrySet().iterator();
        String min = "";
        float minVal = 0;
        while (iterator.hasNext()) {
            Map.Entry<String, Float>  entry = (Map.Entry<String, Float>)iterator.next();
            if (TextUtils.isEmpty(min)) {
                min = entry.getKey();
                minVal = entry.getValue();
                continue;
            }
            float scoreTmp = entry.getValue();
            if (scoreTmp < minVal) {
                min = entry.getKey();
                minVal = entry.getValue();
            }
        }
        userId2Score.remove(min);
    }

    public Map<String, Float> sortMapByValue(Map<String, Float> oriMap, String userId, int score) {
        if (oriMap == null || oriMap.isEmpty()) {
            return null;
        }
        Map<String, Float> sortedMap = new LinkedHashMap<String, Float>();
        List<Map.Entry<String, Float>> entryList = new ArrayList<Map.Entry<String, Float>>(oriMap.entrySet());
        Collections.sort(entryList, new MapValueComparator());

        Iterator<Map.Entry<String, Float>> iter = entryList.iterator();
        Map.Entry<String, Float> tmpEntry = null;
        while (iter.hasNext()) {
            tmpEntry = iter.next();
            sortedMap.put(tmpEntry.getKey(), tmpEntry.getValue());
        }
        return sortedMap;
    }

    private void displayUserOfMaxScore(final String userId, final float score) {

        handler.post(new Runnable() {
            @Override
            public void run() {

                if (score < 80) {
                    scoreTv.setText("");
                    matchUserTv.setText("");
                    matchAvatorIv.setImageBitmap(null);
                    return;
                }

                if (userIdOfMaxScore.equals(userId) ) {
                    if (score < maxScore) {
                        scoreTv.setText("" + score);
                    } else {
                        maxScore = score;
                        userOfMaxSocre.setText("userId：" + userId + "\nscore：" + score);
                        scoreTv.setText(String.valueOf(maxScore));
                    }
                    if (matchUserTv.getText().toString().length() > 0) {
                        return;
                    }
                } else {
                    userIdOfMaxScore = userId;
                    maxScore = score;
                }


                scoreTv.setText(String.valueOf(maxScore));
                User user = FaceApi.getInstance().getUserInfo(groupId, userId);
                if (user == null) {
                    return;
                }
                matchUserTv.setText(user.getUserInfo());
                List<Feature> featureList = user.getFeatureList();
                if (featureList != null && featureList.size() > 0) {
                    // featureTv.setText(new String(featureList.get(0).getFeature()));
                    File faceDir = FileUitls.getFaceDirectory();
                    if (faceDir != null && faceDir.exists()) {
                        File file = new File(faceDir, featureList.get(0).getImageName());
                        if (file != null && file.exists()) {
                            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                            matchAvatorIv.setImageBitmap(bitmap);
                        }
                    }
                }
//                List<Feature>  featureList = DBManager.getInstance().queryFeatureByUeserId(userId);
//                if (featureList != null && featureList.size() > 0) {
//                    File faceDir = FileUitls.getFaceDirectory();
//                    if (faceDir != null && faceDir.exists()) {
//                        File file = new File(faceDir, featureList.get(0).getImageName());
//                        if (file != null && file.exists()) {
//                            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
//                            testView.setImageBitmap(bitmap);
//                        }
//                    }
//                }
            }
        });
    }

    private void toast(final String text) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(RgbVideoIdentityActivity.this, text, Toast.LENGTH_LONG).show();
            }
        });
    }



    private void displayTip(final String text, final TextView textView) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                textView.setText(text);
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
     *
     */
    private void showFrame(ImageFrame imageFrame, FaceInfo[] faceInfos) {
        Canvas canvas = textureView.lockCanvas();
        if (canvas == null) {
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


        rectF.set(getFaceRect(faceInfo, imageFrame));

        // 检测图片的坐标和显示的坐标不一样，需要转换。
        previewView.mapFromOriginalRect(rectF);

        float yaw  = Math.abs(faceInfo.headPose[0]);
        float patch  = Math.abs(faceInfo.headPose[1]);
        float roll  = Math.abs(faceInfo.headPose[2]);
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

    class MapValueComparator implements Comparator<Map.Entry<String, Float>> {

        @Override
        public int compare(java.util.Map.Entry<String, Float> me1, java.util.Map.Entry<String, Float> me2) {

            return me1.getValue().compareTo(me2.getValue());
        }
    }

    /**
     * 获取人脸框区域。
     *
     * @return 人脸框区域
     */
    // TODO padding?
    public Rect getFaceRect(FaceInfo faceInfo, ImageFrame frame) {
        Rect rect = new Rect();
        int[] points = new int[8];
        faceInfo.getRectPoints(points);

        int left = points[2];
        int top = points[3];
        int right = points[6];
        int bottom = points[7];

        //            int width = (right - left) * 4 / 3;
        //            int height = (bottom - top) * 4 / 3;
        //
        //            left = getInfo().mCenter_x - width / 2;
        //            top = getInfo().mCenter_y - height / 2;
        //
        //            rect.top = top;
        //            rect.left = left;
        //            rect.right = left + width;
        //            rect.bottom = top + height;

        //            int width = (right - left) * 4 / 3;
        //            int height = (bottom - top) * 5 / 3;
        int width = (right - left);
        int height = (bottom - top);

        //            left = getInfo().mCenter_x - width / 2;
        //            top = getInfo().mCenter_y - height * 2 / 3;
        left = (int) (faceInfo.mCenter_x - width / 2);
        top = (int) (faceInfo.mCenter_y - height  / 2);


        rect.top = top < 0 ? 0 : top;
        rect.left = left < 0 ? 0 : left;
        rect.right = (left + width) > frame.getWidth() ? frame.getWidth() : (left + width) ;
        rect.bottom = (top + height) > frame.getHeight() ? frame.getHeight() : (top + height);

        return rect;
    }
}
