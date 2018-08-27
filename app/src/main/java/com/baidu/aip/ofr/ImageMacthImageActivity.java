/*
 * Copyright (C) 2018 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.aip.ofr;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.aip.api.FaceApi;
import com.baidu.aip.entity.ARGBImg;

import com.baidu.aip.manager.FaceSDKManager;
import com.baidu.aip.utils.ImageUtils;
import com.baidu.aip.utils.PreferencesUtil;
import com.baidu.idl.facesdk.FaceInfo;

import java.io.FileNotFoundException;
import java.util.concurrent.Executors;

/**
 * 静态两张图片比对,基于FaceTrack 依赖特征点的方式获取人脸特征值获取比对
 */

public class ImageMacthImageActivity extends Activity implements View.OnClickListener {

    private static final int PICK_PHOTO_FRIST = 100;
    private static final int PICK_VIDEO_FRIST = 101;
    private static final int PICK_PHOTO_SECOND = 102;
    private static final int PICK_VIDEO_SECOND = 103;
    private ImageView firstIv;
    private ImageView secondIv;
    private TextView scoreIv;
    private Button firstPickFromPhoto;
    private Button firstPickFromVideo;
    private Button secondPickFromPhoto;
    private Button secondPickFromVideo;

    private Button compareBtn;

    private byte[] firstFeature = new byte[2048];
    private byte[] secondFeature = new byte[2048];
    private volatile boolean firstFeatureFinished = false;
    private volatile boolean secondFeatureFinished = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_image_two);

        findView();
    }


    private void findView() {
        firstIv = (ImageView) findViewById(R.id.img_first);
        secondIv = (ImageView) findViewById(R.id.img_second);
        scoreIv = (TextView) findViewById(R.id.score_tv);

        firstPickFromPhoto = (Button) findViewById(R.id.first_pick_from_photo);
        firstPickFromVideo = (Button) findViewById(R.id.first_pick_from_video);

        secondPickFromPhoto = (Button) findViewById(R.id.second_pick_from_photo);
        secondPickFromVideo = (Button) findViewById(R.id.second_pick_from_video);


        compareBtn = (Button) findViewById(R.id.compare_btn);
        compareBtn.setOnClickListener(this);
        firstPickFromPhoto.setOnClickListener(this);
        firstPickFromVideo.setOnClickListener(this);
        secondPickFromPhoto.setOnClickListener(this);
        secondPickFromVideo.setOnClickListener(this);
    }


    private ARGBImg getImageInfo(Bitmap bmp) {
        ARGBImg argbImg = new ARGBImg();
        if (bmp != null) {
            int[] argbData = new int[bmp.getWidth() * bmp.getHeight()];
            bmp.getPixels(argbData, 0, bmp.getWidth(),
                    0, 0, bmp.getWidth(), bmp.getHeight());
            argbImg = new ARGBImg(argbData, bmp.getWidth(), bmp.getHeight(), 0, 0);
        }
        return argbImg;
    }

    private boolean faceFeature(ARGBImg argbImg, byte[] feature) {
        FaceSDKManager.getInstance().getFaceDetector().clearTrackedFaces();
        FaceSDKManager.getInstance().getFaceDetector().detect(argbImg.data, argbImg.width, argbImg.height);
        FaceInfo[] faceInfos = FaceSDKManager.getInstance().getFaceDetector().getTrackedFaces();
        if (faceInfos != null && faceInfos.length > 0) {
            FaceInfo faceInfo = faceInfos[0];
            if (faceInfo != null) {
                if ( FaceSDKManager.getInstance().getFaceFeature().extractFeature(argbImg.data,
                        argbImg.height, argbImg.width, feature, faceInfo.landmarks) != -1) {
                    return true;
                }
            }
        }
        FaceSDKManager.getInstance().getFaceDetector().clearTrackedFaces();
        return false;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.first_pick_from_photo:
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, PICK_PHOTO_FRIST);
                break;
            case R.id.first_pick_from_video:
                int type = PreferencesUtil.getInt(LivenessSettingActivity.TYPE_LIVENSS, LivenessSettingActivity
                        .TYPE_NO_LIVENSS);
                if (type == LivenessSettingActivity.TYPE_NO_LIVENSS) {
                    Toast.makeText(this, "当前活体策略：无活体", Toast.LENGTH_LONG).show();
                    intent = new Intent(this, RgbDetectActivity.class);
                    startActivityForResult(intent, PICK_VIDEO_FRIST);
                } else if (type == LivenessSettingActivity.TYPE_RGB_LIVENSS) {
                    Toast.makeText(this, "当前活体策略：RGB活体", Toast.LENGTH_LONG).show();
                    intent = new Intent(this, RgbDetectActivity.class);
                    startActivityForResult(intent, PICK_VIDEO_FRIST);
                } else if (type == LivenessSettingActivity.TYPE_RGB_IR_LIVENSS) {
                    Toast.makeText(this, "当前活体策略：RGB+IR活体", Toast.LENGTH_LONG).show();
                    intent = new Intent(this, RgbIrLivenessActivity.class);
                    startActivityForResult(intent, PICK_VIDEO_FRIST);
                } else if (type == LivenessSettingActivity.TYPE_RGB_DEPTH_LIVENSS) {
                    Toast.makeText(this, "当前活体策略：RGB+Depth活体，需要使用奥比中光双目摄像头", Toast.LENGTH_LONG).show();
                    intent = new Intent(this, OrbbecLivenessDetectActivity.class);
                    // intent = new Intent(this, OpenniLivenessDetectActivity.class);
                    startActivityForResult(intent, PICK_VIDEO_FRIST);
                }
                break;
            case R.id.compare_btn:
                match();
                break;
            case R.id.second_pick_from_photo:
                Intent intent1 = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media
                        .EXTERNAL_CONTENT_URI);
                startActivityForResult(intent1, PICK_PHOTO_SECOND);

                break;
            case R.id.second_pick_from_video:
                type = PreferencesUtil.getInt(LivenessSettingActivity.TYPE_LIVENSS, LivenessSettingActivity
                        .TYPE_NO_LIVENSS);
                if (type == LivenessSettingActivity.TYPE_NO_LIVENSS) {
                    Toast.makeText(this, "当前活体策略：无活体", Toast.LENGTH_LONG).show();
                    intent = new Intent(this, RgbDetectActivity.class);
                    startActivityForResult(intent, PICK_VIDEO_SECOND);
                } else if (type == LivenessSettingActivity.TYPE_RGB_LIVENSS) {
                    Toast.makeText(this, "当前活体策略：RGB活体", Toast.LENGTH_LONG).show();
                    intent = new Intent(this, RgbDetectActivity.class);
                    startActivityForResult(intent, PICK_VIDEO_SECOND);
                } else if (type == LivenessSettingActivity.TYPE_RGB_IR_LIVENSS) {
                    Toast.makeText(this, "当前活体策略：RGB+IR活体", Toast.LENGTH_LONG).show();
                    intent = new Intent(this, RgbIrLivenessActivity.class);
                    startActivityForResult(intent, PICK_VIDEO_SECOND);
                } else if (type == LivenessSettingActivity.TYPE_RGB_DEPTH_LIVENSS) {
                    Toast.makeText(this, "当前活体策略：RGB+Depth活体,需要使用奥比中光双目摄像头", Toast.LENGTH_LONG).show();
                    intent = new Intent(this, OrbbecLivenessDetectActivity.class);
                    startActivityForResult(intent, PICK_VIDEO_SECOND);
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {


        if (requestCode == PICK_PHOTO_FRIST && (data != null && data.getData() != null)) {
            Uri uri1 = ImageUtils.geturi(data, this);

            try {
                final Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri1));
                firstIv.setImageBitmap(bitmap);
                syncFeature(bitmap, firstFeature, 1);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

        } else if (requestCode == PICK_PHOTO_SECOND && (data != null && data.getData() != null)) {
            Uri uri2 = ImageUtils.geturi(data, this);

            try {
                final Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri2));
                secondIv.setImageBitmap(bitmap);
                syncFeature(bitmap, secondFeature, 2);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else if (requestCode == PICK_VIDEO_FRIST && (data != null )) {
            String faceImagePath = data.getStringExtra("file_path");

            Bitmap bitmap = BitmapFactory.decodeFile(faceImagePath);
            firstIv.setImageBitmap(bitmap);
            syncFeature(bitmap, firstFeature, 1);
        } else if (requestCode == PICK_VIDEO_SECOND && (data != null)) {
            String faceImagePath = data.getStringExtra("file_path");

            Bitmap bitmap = BitmapFactory.decodeFile(faceImagePath);
            secondIv.setImageBitmap(bitmap);
            syncFeature(bitmap, secondFeature, 2);
        }
    }

    Handler handler = new Handler();
    private void delay(final Bitmap bitmap, final byte[] feature, final int index) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                syncFeature(bitmap, feature, index);
            }
        }, 2000);
    }
    private void syncFeature(final Bitmap bitmap, final byte[] feature, final int index)  {

        Executors.newSingleThreadExecutor().submit(new Runnable() {
            @Override
            public void run() {
                final int ret = FaceApi.getInstance().getFeature(bitmap, feature, 50);
                Log.i("wtf", "ret:" + ret);
                if (ret == 512 && index == 1) {
                    firstFeatureFinished = true;
                } else if (ret == 512 && index == 2) {
                    secondFeatureFinished = true;
                }
                if (ret == 512) {
                    toast("图片" + index + "特征抽取成功");
                } else if (ret == -100) {
                    toast("未完成人脸比对，可能原因，图片1为空");
                } else if (ret == -101) {
                    toast("未完成人脸比对，可能原因，图片2为空");
                } else if (ret == -102) {
                    toast("未完成人脸比对，可能原因，图片1未检测到人脸");
                } else if (ret == -103) {
                    toast("未完成人脸比对，可能原因，图片2未检测到人脸");
                } else {
                    toast( "未完成人脸比对，可能原因，"
                            + "人脸太小（小于sdk初始化设置的最小检测人脸）"
                            + "人脸不是朝上，sdk不能检测出人脸");
                }
            }
        });

    }

    private void match() {
        if (!firstFeatureFinished) {
            toast("图片1特征没有抽取成功");
            return;
        }

        if (!secondFeatureFinished) {
            toast("图片2特征没有抽取成功");
            return;
        }

        float score = FaceApi.getInstance().match(firstFeature, secondFeature);
        scoreIv.setText("相似度：" + score);
    }


    private void toast(final String tip) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ImageMacthImageActivity.this, tip, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
