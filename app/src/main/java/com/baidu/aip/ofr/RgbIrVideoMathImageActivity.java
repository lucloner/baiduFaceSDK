/*
 * Copyright (C) 2018 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.aip.ofr;

import java.io.FileNotFoundException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import com.baidu.idl.facesdk.FaceSDK;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class RgbIrVideoMathImageActivity extends Activity implements ILivenessCallBack, View.OnClickListener {
	
	private static final String TAG = "RgbIrVideoMathImageActivity";
	// 图片越大，性能消耗越大，也可以选择640*480， 1280*720
	private static final int PREFER_WIDTH = 1280;
	private static final int PERFER_HEIGH = 720;
	private static final int PICK_PHOTO = 100;
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
	private TextView featureDurationTv;

	private TextView matchScoreTv;
	private ImageView photoIv;
	private Button pickPhotoFromAlbum;
	private ExecutorService es = Executors.newSingleThreadExecutor();
	private Future future;
	private boolean success = false;
	private byte[] photoFeature = new byte[2048];
	private volatile boolean photoFeatureFinish = false;
	private volatile boolean matching = false;

	private volatile int[] rgbData;
	private volatile byte[] irData;
	private int camemra1DataMean;
	private int camemra2DataMean;
	private volatile boolean camemra1IsRgb = false;
	private volatile boolean rgbOrIrConfirm = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rgb_ir_video_match_image);
		findView();

		FaceLiveness.getInstance().setLivenessCallBack(this);

	}

	@SuppressLint("WrongViewCast")
	private void findView()  {
		tipTv = (TextView) findViewById(R.id.tip_tv);
		detectDurationTv = (TextView) findViewById(R.id.detect_duration_tv);
		rgbLivenssDurationTv = (TextView) findViewById(R.id.rgb_liveness_duration_tv);
		rgbLivenessScoreTv = (TextView) findViewById(R.id.rgb_liveness_score_tv);
		irLivenssDurationTv = (TextView) findViewById(R.id.ir_liveness_duration_tv);
		irLivenessScoreTv = (TextView) findViewById(R.id.ir_liveness_score_tv);

		detectDurationTv = (TextView) findViewById(R.id.detect_duration_tv);
		rgbLivenssDurationTv = (TextView) findViewById(R.id.rgb_liveness_duration_tv);
		rgbLivenessScoreTv = (TextView) findViewById(R.id.rgb_liveness_score_tv);
		irLivenssDurationTv = (TextView) findViewById(R.id.ir_liveness_duration_tv);
		irLivenessScoreTv = (TextView) findViewById(R.id.ir_liveness_score_tv);
		matchScoreTv = (TextView) findViewById(R.id.match_score_tv);
		photoIv = (ImageView) findViewById(R.id.pick_from_album_iv);
		pickPhotoFromAlbum = (Button) findViewById(R.id.pick_from_album_btn);

		pickPhotoFromAlbum.setOnClickListener(this);

        mCameraNum = Camera.getNumberOfCameras();

        testIv = (ImageView) findViewById(R.id.test_iv);
        SurfaceView[] surfaViews = new SurfaceView[mCameraNum];
        mPreview = new Preview[mCameraNum];
        mCamera = new Camera[mCameraNum];
        for (int i = 0; i < mCameraNum; i++) {
            surfaViews[i] = new SurfaceView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
            // lp.setMargins(10, 10, 10, 10);
            surfaViews[i].setLayoutParams(lp);
            ((LinearLayout) findViewById(R.id.camera_layout)).addView(surfaViews[i]);

            mPreview[i] = new Preview(this, surfaViews[i]);
            mPreview[i].setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            ((RelativeLayout) findViewById(R.id.layout)).addView(mPreview[i]);
        }
	}


	@Override
	protected void onResume() {
		super.onResume();
		if (mCameraNum != 2) {
			Toast.makeText(this, "未检测到2个摄像头", Toast.LENGTH_LONG).show();
			return;
		}
		mCamera[0] = Camera.open(0);
		mCamera[1] = Camera.open(1);
		mPreview[0].setCamera(mCamera[0], PREFER_WIDTH, PERFER_HEIGH);
		mPreview[1].setCamera(mCamera[1], PREFER_WIDTH, PERFER_HEIGH);
		mCamera[0].setPreviewCallback(new Camera.PreviewCallback() {
			@Override
			public void onPreviewFrame(byte[] data, Camera camera) {
				if (rgbOrIrConfirm) {
					choiceRgbOrIrType(0, data);
				} else if (camemra1DataMean == 0){
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

	private synchronized void rgbOrIr(int index, byte[] data) {
		byte[] tmp = new byte[PREFER_WIDTH * PERFER_HEIGH];
		System.arraycopy(data, 0 ,  tmp, 0, PREFER_WIDTH * PERFER_HEIGH);
		int count = 0;
		int total = 0;
		for (int i = 0; i < PREFER_WIDTH * PERFER_HEIGH; i = i + 100) {
			total +=  byteToInt(tmp[i]);
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

	// 判断camera数据类型，rgb活体和ir活体调用接口不同，需要区分，通过选取一点数量点的取平均值比较，大的为rgb，小的为ir
	private void choiceRgbOrIrType(int index, byte[] data) {
		if (!photoFeatureFinish) {
			return;
		}
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

			byte[] ir = new byte[PREFER_WIDTH * PERFER_HEIGH];
			System.arraycopy(data, 0 ,  ir, 0, PREFER_WIDTH * PERFER_HEIGH);
			irData = ir;
			checkData();
		}
	}

	private synchronized void checkData() {
		if (rgbData != null && irData != null) {
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
	        if (mCamera[i] != null) {
				mCamera[i].setPreviewCallback(null);
	            mCamera[i].stopPreview();
	            // mPreview[i].setCamera(null);
	            mCamera[i].release();
	            mCamera[i] = null;
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
					irLivenessScoreTv.setText("IR活体得分：" + livenessModel.getIrLivenessScore());
					irLivenssDurationTv.setText("IR活体耗时：" + livenessModel.getIrLivenessDuration());
				}
			}

		});
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
					int ret = FaceSDKManager.getInstance().getFaceFeature().faceFeature(argbImg, photoFeature, 50);
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
				irLivenessScoreTv.setText("");
				irLivenssDurationTv.setText("");
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
				Toast.makeText(RgbIrVideoMathImageActivity.this, tip, Toast.LENGTH_SHORT).show();
			}
		});
	}

}


