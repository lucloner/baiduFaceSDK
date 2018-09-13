/*
 * Copyright (C) 2018 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.aip.ofr;

import com.baidu.aip.ofr.utils.GlobalFaceTypeModel;
import com.baidu.aip.utils.PreferencesUtil;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

public class LivenessSettingActivity extends Activity implements View.OnClickListener {

    public static final int TYPE_NO_LIVENSS = 1;
    public static final int TYPE_RGB_LIVENSS = 2;
    public static final int TYPE_RGB_IR_LIVENSS = 3;
    public static final int TYPE_RGB_DEPTH_LIVENSS = 4;
    public static final int TYPE_RGB_IR_DEPTH_LIVENSS = 5;
    public static final String TYPE_LIVENSS = "TYPE_LIVENSS";
    private RadioButton radioButton1;
    private RadioButton radioButton2;
    private RadioButton radioButton3;
    private RadioButton radioButton4;
    private RadioButton radioButton5;
    private RadioGroup livenessRg;
    private Button confirmBtn;
    private int livenessType = TYPE_NO_LIVENSS;

    private RadioGroup rgCamera;
    private RadioButton rbOrbbec;
    private RadioButton rbIminect;
    private RadioButton rbOrbbecPro;

    private LinearLayout linearCamera;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.liveness_setting_layout);
        findView();

        int livenessType = PreferencesUtil.getInt(TYPE_LIVENSS, TYPE_NO_LIVENSS);
        int cameraType = PreferencesUtil.getInt(GlobalFaceTypeModel.TYPE_CAMERA, GlobalFaceTypeModel.ORBBEC);
        defaultLiveness(livenessType);
        defaultCamera(cameraType);
    }

    private void findView() {
        linearCamera = findViewById(R.id.linear_camera);
        radioButton1 = (RadioButton) findViewById(R.id.no_liveness_rb);
        radioButton2 = (RadioButton) findViewById(R.id.rgb_liveness_rb);
        radioButton3 = (RadioButton) findViewById(R.id.rgb_depth_liveness_rb);
        radioButton4 = (RadioButton) findViewById(R.id.rgb_ir_liveness_rb);
        radioButton5 = (RadioButton) findViewById(R.id.rgb_ir_depth_liveness_rb);
        livenessRg = (RadioGroup) findViewById(R.id.liveness_rg);
        confirmBtn = (Button) findViewById(R.id.confirm_btn);
        confirmBtn.setOnClickListener(this);
        rgCamera = findViewById(R.id.rg_camera);
        rbOrbbec = findViewById(R.id.rb_orbbec);
        rbIminect = findViewById(R.id.rb_iminect);
        rbOrbbecPro = findViewById(R.id.rb_orbbec_pro);

        livenessRg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup rg, int checkedId) {
                switch (checkedId) {
                    case R.id.no_liveness_rb:
                        linearCamera.setVisibility(View.GONE);
                        PreferencesUtil.putInt(TYPE_LIVENSS, TYPE_NO_LIVENSS);
                        break;
                    case R.id.rgb_liveness_rb:
                        linearCamera.setVisibility(View.GONE);
                        PreferencesUtil.putInt(TYPE_LIVENSS, TYPE_RGB_LIVENSS);
                        break;
                    case R.id.rgb_ir_liveness_rb:
                        linearCamera.setVisibility(View.GONE);
                        PreferencesUtil.putInt(TYPE_LIVENSS, TYPE_RGB_IR_LIVENSS);
                        break;
                    case R.id.rgb_depth_liveness_rb:
                        linearCamera.setVisibility(View.VISIBLE);
                        PreferencesUtil.putInt(TYPE_LIVENSS, TYPE_RGB_DEPTH_LIVENSS);
                        break;
                    case R.id.rgb_ir_depth_liveness_rb:
                        linearCamera.setVisibility(View.GONE);
                        PreferencesUtil.putInt(TYPE_LIVENSS, TYPE_RGB_IR_DEPTH_LIVENSS);
                        break;
                }
            }
        });
        rgCamera.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int i) {
                switch (i) {
                    case R.id.rb_orbbec:
                        PreferencesUtil.putInt(GlobalFaceTypeModel.TYPE_CAMERA, GlobalFaceTypeModel.ORBBEC);
                        break;
                    case R.id.rb_iminect:
                        PreferencesUtil.putInt(GlobalFaceTypeModel.TYPE_CAMERA, GlobalFaceTypeModel.IMIMECT);
                        break;
                    case R.id.rb_orbbec_pro:
                        PreferencesUtil.putInt(GlobalFaceTypeModel.TYPE_CAMERA, GlobalFaceTypeModel.ORBBECPRO);
                        break;
                }
            }
        });
    }

    private void defaultLiveness(int livenessType) {
        if (livenessType == TYPE_NO_LIVENSS) {
            radioButton1.setChecked(true);
        } else if (livenessType == TYPE_RGB_LIVENSS) {
            radioButton2.setChecked(true);
        } else if (livenessType == TYPE_RGB_DEPTH_LIVENSS) {
            radioButton3.setChecked(true);
        } else if (livenessType == TYPE_RGB_IR_LIVENSS) {
            radioButton4.setChecked(true);
        } else if (livenessType == TYPE_RGB_IR_DEPTH_LIVENSS) {
            radioButton5.setChecked(true);
        }
    }

    private void defaultCamera(int cameraType) {
        if (cameraType == GlobalFaceTypeModel.ORBBEC) {
            rbOrbbec.setChecked(true);
        } else if (cameraType == GlobalFaceTypeModel.IMIMECT) {
            rbIminect.setChecked(true);
        } else if (cameraType == GlobalFaceTypeModel.ORBBECPRO) {
            rbOrbbecPro.setChecked(true);
        }
    }

    @Override
    public void onClick(View v) {
        if (v == confirmBtn) {
            finish();
        }
    }
}
