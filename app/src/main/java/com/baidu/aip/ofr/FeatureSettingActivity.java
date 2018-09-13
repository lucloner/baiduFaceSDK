package com.baidu.aip.ofr;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.baidu.aip.ofr.utils.GlobalFaceTypeModel;
import com.baidu.aip.utils.PreferencesUtil;

public class FeatureSettingActivity extends AppCompatActivity implements View.OnClickListener {

    private RadioButton rbRecognizeLive;
    private RadioButton rbRecognizeIdPhoto;
    private RadioGroup rgModel;
    private Button confirmBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feature_setting);
        initView();
        int modelType= PreferencesUtil.getInt(GlobalFaceTypeModel.TYPE_MODEL,GlobalFaceTypeModel.RECOGNIZE_LIVE);
        defaultModel(modelType);
    }

    private void initView() {
        rbRecognizeLive=findViewById(R.id.rb_recognize_live);
        rbRecognizeIdPhoto=findViewById(R.id.rb_recognize_id_photo);
        rgModel=findViewById(R.id.rg_model);
        confirmBtn=findViewById(R.id.confirm_btn);
        confirmBtn.setOnClickListener(this);

        rgModel.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int i) {
                switch (i){
                    case R.id.rb_recognize_live:
                        PreferencesUtil.putInt(GlobalFaceTypeModel.TYPE_MODEL, GlobalFaceTypeModel.RECOGNIZE_LIVE);
                        break;
                    case R.id.rb_recognize_id_photo:
                        PreferencesUtil.putInt(GlobalFaceTypeModel.TYPE_MODEL, GlobalFaceTypeModel.RECOGNIZE_ID_PHOTO);
                        break;
                }
            }
        });
    }

    private void defaultModel(int modelType) {
        if (modelType == GlobalFaceTypeModel.RECOGNIZE_LIVE) {
            rbRecognizeLive.setChecked(true);
        } else if (modelType == GlobalFaceTypeModel.RECOGNIZE_ID_PHOTO) {
            rbRecognizeIdPhoto.setChecked(true);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.confirm_btn:
                finish();
                break;
        }
    }
}
