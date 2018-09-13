/*
 * Copyright (C) 2018 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.aip.ofr;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import com.baidu.aip.ImageFrame;
import com.baidu.aip.callback.ILivenessCallBack;
import com.baidu.aip.entity.LivenessModel;
import com.baidu.aip.face.FaceCropper;
import com.baidu.aip.manager.FaceEnvironment;
import com.baidu.aip.manager.FaceLiveness;
import com.baidu.aip.utils.FileUitls;
import com.baidu.aip.utils.ImageUtils;
import com.baidu.idl.facesdk.FaceInfo;
import com.baidu.idl.facesdk.FaceSDK;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
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

public class RgbIrLivenessActivity extends Activity implements ILivenessCallBack {

    private static final String TAG = "RgbIrLivenessActivity";
    // 图片越大，性能消耗越大，也可以选择640*480， 1280*720
    private static final int PREFER_WIDTH = 640;
    private static final int PERFER_HEIGH = 480;
    Preview[] mPreview;
    Camera[] mCamera;
    private int mCameraNum;
    private ImageView testIv;

    private TextView tipTv;
    private TextView detectDurationTv;
    private TextView rgbLivenssDurationTv;
    private TextView rgbLivenessScoreTv;
    private TextView irLivenssDurationTv;
    private TextView irLivenessScoreTv;

    private volatile int[] niRargb;
    private volatile int[] rgbData;
    private volatile byte[] irData;
    private int camemra1DataMean;
    private int camemra2DataMean;
    private volatile boolean camemra1IsRgb = false;
    private volatile boolean rgbOrIrConfirm = false;
    private int source;
    private boolean success;

    // textureView用于绘制人脸框等。
    private TextureView textureView;
    private TextureView textureViewOne;
    private SurfaceView surface_one, surface_two;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rgb_ir_liveness);
        FaceSDK.initModel(this);
        findView();

        FaceLiveness.getInstance().setLivenessCallBack(this);

        Intent intent = getIntent();
        if (intent != null) {
            source = intent.getIntExtra("source", -1);
        }
    }

    private void findView() {
        textureViewOne=findViewById(R.id.texture_view_one);
        textureViewOne.setOpaque(false);
        textureView = findViewById(R.id.texture_view);
        textureView.setOpaque(false);
        surface_one = findViewById(R.id.surface_one);
        surface_two = findViewById(R.id.surface_two);
        tipTv = (TextView) findViewById(R.id.tip_tv);
        detectDurationTv = (TextView) findViewById(R.id.detect_duration_tv);
        rgbLivenssDurationTv = (TextView) findViewById(R.id.rgb_liveness_duration_tv);
        rgbLivenessScoreTv = (TextView) findViewById(R.id.rgb_liveness_score_tv);
        irLivenssDurationTv = (TextView) findViewById(R.id.ir_liveness_duration_tv);
        irLivenessScoreTv = (TextView) findViewById(R.id.ir_liveness_score_tv);
        testIv = (ImageView) findViewById(R.id.test_iv);
        mCameraNum = Camera.getNumberOfCameras();
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
//            surfaViews[i] = new SurfaceView(this);
//            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
//                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
//            // lp.setMargins(10, 10, 10, 10);
//            surfaViews[i].setLayoutParams(lp);
//            ((LinearLayout) findViewById(R.id.camera_layout)).addView(surfaViews[i]);
//
//            mPreview[i] = new Preview(this, surfaViews[i]);
//            mPreview[i].setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
//            ((RelativeLayout) findViewById(R.id.layout)).addView(mPreview[i]);
//        }
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
                    mPreview[i].release();
                    mCamera[i].release();
                    mCamera[i] = null;
                }
            }
        }
        super.onPause();
    }


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

    @Override
    public void onCanvasRectCallback(LivenessModel livenessModel) {
        if ((livenessModel.getLiveType() & MASK_RGB) == MASK_RGB) {
            showFrame(livenessModel.getImageFrame(), livenessModel.getTrackFaceInfo());
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
                    irLivenessScoreTv.setText("IR活体得分：" + livenessModel.getIrLivenessScore());
                    irLivenssDurationTv.setText("IR活体耗时：" + livenessModel.getIrLivenessDuration());
                }

            }

        });
    }


    private void toast(final String tip) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(RgbIrLivenessActivity.this, tip, Toast.LENGTH_LONG).show();
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


