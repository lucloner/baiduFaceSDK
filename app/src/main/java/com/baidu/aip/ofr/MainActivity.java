package com.baidu.aip.ofr;

import java.util.List;

import com.baidu.aip.api.FaceApi;
import com.baidu.aip.db.DBManager;
import com.baidu.aip.entity.Group;
import com.baidu.aip.manager.FaceSDKManager;
import com.baidu.aip.utils.PreferencesUtil;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity implements View.OnClickListener{

    private Button imageMatchBtn;
    private Button videoMatchImageBtn;
    private Button videoIdentifyBtn;
    private Button userGroupManagerBtn;
    private Button livenessSettingBtn;
    private Button deviceActivateBtn;
    private Button rgbIrBtn;
    private int count;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageMatchBtn = (Button) findViewById(R.id.image_match_image_btn);
        videoMatchImageBtn = (Button) findViewById(R.id.video_match_image_btn);
        videoIdentifyBtn = (Button) findViewById(R.id.video_identify_faces_btn);
        userGroupManagerBtn = (Button) findViewById(R.id.user_groud_manager_btn);
        livenessSettingBtn = (Button) findViewById(R.id.liveness_setting_btn);
        deviceActivateBtn = (Button) findViewById(R.id.device_activate_btn);
        rgbIrBtn = (Button) findViewById(R.id.rgb_ir_btn);

        imageMatchBtn.setOnClickListener(this);
        videoMatchImageBtn.setOnClickListener(this);
        videoIdentifyBtn.setOnClickListener(this);
        userGroupManagerBtn.setOnClickListener(this);
        livenessSettingBtn.setOnClickListener(this);
        deviceActivateBtn.setOnClickListener(this);
        deviceActivateBtn.setOnClickListener(this);
        rgbIrBtn.setOnClickListener(this);


        PreferencesUtil.initPrefs(this);
        // 使用人脸1：n时使用
        DBManager.getInstance().init(this);
        livnessTypeTip ();
//        FaceEnvironment faceEnvironment = new FaceEnvironment();
//        // 模糊度范围 (0-1) 推荐小于0.7
//        faceEnvironment.setBlurrinessThreshold(FaceEnvironment.VALUE_BLURNESS);
//        // 光照范围 (0-1) 推荐大于40
//        faceEnvironment.setIlluminationThreshold(FaceEnvironment.VALUE_BLURNESS);
//        // 人脸yaw,pitch,row 角度，范围（-45，45），推荐-15-15
//        faceEnvironment.setPitch(FaceEnvironment.VALUE_HEAD_PITCH);
//        faceEnvironment.setRoll(FaceEnvironment.VALUE_HEAD_ROLL);
//        faceEnvironment.setYaw(FaceEnvironment.VALUE_HEAD_YAW);
//        // 最小检测人脸（在图片人脸能够被检测到最小值）80-200， 越小越耗性能，推荐120-200
//        faceEnvironment.setMinFaceSize(FaceEnvironment.VALUE_MIN_FACE_SIZE);
//        // 人脸置信度（0-1）推荐大于0.6
//        faceEnvironment.setNotFaceThreshold(FaceEnvironment.VALUE_NOT_FACE_THRESHOLD);
//        // 人脸遮挡范围 （0-1） 推荐小于0.5
//        faceEnvironment.setOcclulationThreshold(FaceEnvironment.VALUE_OCCLUSION);
//        // 是否进行质量检测,开启会降低性能
//        faceEnvironment.setCheckQuality(false);
//        FaceSDKManager.getInstance().getFaceDetector().setFaceEnvironment(faceEnvironment);
        FaceSDKManager.getInstance().init(this);
        FaceSDKManager.getInstance().setSdkInitListener(new FaceSDKManager.SdkInitListener() {
            @Override
            public void initStart() {
                toast("sdk init start");
            }

            @Override
            public void initSuccess() {
                toast("sdk init success");
            }

            @Override
            public void initFail(int errorCode, String msg) {
                toast("sdk init fail:" + msg);
            }
        });

    }

    @Override
    public void onClick(View v) {
        if (v == deviceActivateBtn){
            FaceSDKManager.getInstance().showActivation();
            return;
        }

        if (FaceSDKManager.getInstance().initStatus() == FaceSDKManager.SDK_UNACTIVATION) {
            toast("SDK还未激活，请先激活");
            return;
        } else if (FaceSDKManager.getInstance().initStatus() == FaceSDKManager.SDK_UNINIT) {
            toast("SDK还未初始化完成，请先初始化");
            return;
        } else if (FaceSDKManager.getInstance().initStatus() == FaceSDKManager.SDK_INITING) {
            toast("SDK正在初始化，请稍后再试");
            return;
        }
        if (v == imageMatchBtn) {
            Intent intent = new Intent(this, ImageMacthImageActivity.class);
            startActivity(intent);
        } else if (v == videoMatchImageBtn) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager
                    .PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, 100);
                return;
            }
            choiceMatchType();
        } else if (v == videoIdentifyBtn) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager
                    .PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, 100);
                return;
            }
            showSingleAlertDialog();
        } else if (v == userGroupManagerBtn) {
            Intent intent = new Intent(this, UserGroupManagerActivity.class);
            startActivity(intent);
        } else if (v == livenessSettingBtn) {
            Intent intent = new Intent(this, LivenessSettingActivity.class);
            startActivity(intent);
        } else if (v == rgbIrBtn) {
            Intent intent = new Intent(this, RgbIrLivenessActivity.class);
            startActivity(intent);
        }
    }

    private AlertDialog alertDialog;
    private String[] items;
    public void showSingleAlertDialog() {

        List<Group> groupList = FaceApi.getInstance().getGroupList(0, 1000);
        if (groupList.size() <= 0) {
            Toast.makeText(this, "还没有分组，请创建分组并添加用户", Toast.LENGTH_SHORT).show();
            return;
        }
        items = new String[groupList.size()];
        for (int i = 0; i < groupList.size(); i++) {
            Group group = groupList.get(i);
            items[i] = group.getGroupId();
        }

        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
        alertBuilder.setTitle("请选择分组groupID");
        alertBuilder.setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface arg0, int index) {
                Toast.makeText(MainActivity.this, items[index], Toast.LENGTH_SHORT).show();

                choiceIdentityType(items[index]);
                alertDialog.dismiss();
            }
        });

        alertDialog = alertBuilder.create();
        alertDialog.show();
    }

    private void choiceMatchType() {
        int type = PreferencesUtil.getInt(LivenessSettingActivity.TYPE_LIVENSS, LivenessSettingActivity
                .TYPE_NO_LIVENSS);
        if (type == LivenessSettingActivity.TYPE_NO_LIVENSS) {
            Toast.makeText(this, "当前活体策略：无活体", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(MainActivity.this, RgbVideoMatchImageActivity.class);
            startActivity(intent);
        } else if (type == LivenessSettingActivity.TYPE_RGB_LIVENSS) {
            Toast.makeText(this, "当前活体策略：单目RGB活体", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(MainActivity.this, RgbVideoMatchImageActivity.class);
            startActivity(intent);
        } else if (type == LivenessSettingActivity.TYPE_RGB_IR_LIVENSS) {
            Toast.makeText(this, "当前活体策略：双目RGB+IR活体", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(MainActivity.this, RgbIrVideoMathImageActivity.class);
            startActivity(intent);
        } else if (type == LivenessSettingActivity.TYPE_RGB_DEPTH_LIVENSS) {
            Toast.makeText(this, "当前活体策略：双目RGB+Depth活体", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(MainActivity.this, OrbbecVideoMatchImageActivity.class);
            startActivity(intent);
        }
    }

    private void choiceIdentityType(String groupId) {
        int type = PreferencesUtil.getInt(LivenessSettingActivity.TYPE_LIVENSS, LivenessSettingActivity
                .TYPE_NO_LIVENSS);
        if (type == LivenessSettingActivity.TYPE_NO_LIVENSS) {
            Toast.makeText(this, "当前活体策略：无活体", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(MainActivity.this, RgbVideoIdentityActivity.class);
            intent.putExtra("group_id", groupId);
            startActivity(intent);
        } else if (type == LivenessSettingActivity.TYPE_RGB_LIVENSS) {
            Toast.makeText(this, "当前活体策略：单目RGB活体", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(MainActivity.this, RgbVideoIdentityActivity.class);
            intent.putExtra("group_id", groupId);
            startActivity(intent);
        } else if (type == LivenessSettingActivity.TYPE_RGB_IR_LIVENSS) {
            Toast.makeText(this, "当前活体策略：双目RGB+IR活体", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(MainActivity.this, RgbIrVideoIdentifyActivity.class);
            intent.putExtra("group_id", groupId);
            startActivity(intent);
        } else if (type == LivenessSettingActivity.TYPE_RGB_DEPTH_LIVENSS) {
            Toast.makeText(this, "当前活体策略：双目RGB+Depth活体", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(MainActivity.this, OrbbecVideoIdentifyActivity.class);
            intent.putExtra("group_id", groupId);
            startActivity(intent);
        }
    }

    private void livnessTypeTip () {
        int type = PreferencesUtil.getInt(LivenessSettingActivity.TYPE_LIVENSS, LivenessSettingActivity
                .TYPE_NO_LIVENSS);

        if (type == LivenessSettingActivity.TYPE_NO_LIVENSS) {
            Toast.makeText(this, "当前活体策略：无活体, 请选用普通USB摄像头", Toast.LENGTH_LONG).show();
        } else if (type == LivenessSettingActivity.TYPE_RGB_LIVENSS) {
            Toast.makeText(this, "当前活体策略：单目RGB活体, 请选用普通USB摄像头", Toast.LENGTH_LONG).show();
        } else if (type == LivenessSettingActivity.TYPE_RGB_IR_LIVENSS) {
            Toast.makeText(this, "当前活体策略：双目RGB+IR活体, 请选用RGB+IR摄像头，如：迪威泰双目摄像头",
                    Toast.LENGTH_LONG).show();
        } else if (type == LivenessSettingActivity.TYPE_RGB_DEPTH_LIVENSS) {
            Toast.makeText(this, "当前活体策略：双目RGB+Depth活体，请选用RGB+Depth摄像头，"
                    + "如：如奥比中光mini双目摄像头", Toast.LENGTH_LONG).show();
        }
    }

    private void toast(final String text) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, text, Toast.LENGTH_LONG).show();
            }
        });
    }

    private Handler handler = new Handler(Looper.getMainLooper());
}
