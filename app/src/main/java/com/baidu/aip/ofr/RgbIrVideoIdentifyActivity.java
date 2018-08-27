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
import com.baidu.aip.utils.FileUitls;
import com.baidu.idl.facesdk.FaceInfo;
import com.baidu.idl.facesdk.FaceSDK;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

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
        setContentView(R.layout.activity_rgb_ir_video_identify);
		findView();

		FaceLiveness.getInstance().setLivenessCallBack(this);
		loadFeature2Memery();

		Intent intent = getIntent();
		if (intent != null) {
			groupId = intent.getStringExtra("group_id");
		}
	}

	@SuppressLint("WrongViewCast")
	private void findView()  {
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
	            mPreview[i].setCamera(null,  PREFER_WIDTH, PERFER_HEIGH);
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
		if (raw > 15 || patch > 15 ||  roll > 15) {
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
}


