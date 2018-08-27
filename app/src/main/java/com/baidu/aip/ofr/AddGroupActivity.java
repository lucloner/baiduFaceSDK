/*
 * Copyright (C) 2018 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.aip.ofr;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.baidu.aip.api.FaceApi;
import com.baidu.aip.db.DBManager;
import com.baidu.aip.entity.Group;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class AddGroupActivity extends Activity implements View.OnClickListener{

    private EditText addGroupEt;
    private Button addGroupBtn;
    private Button backBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_group);

        findView();
        addListener();
    }

    private void findView() {
        addGroupEt = (EditText) findViewById(R.id.add_group_et);
        addGroupBtn = (Button) findViewById(R.id.add_group_btn);
        backBtn = (Button) findViewById(R.id.back_btn);
    }

    private void addListener() {
        addGroupBtn.setOnClickListener(this);
        backBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {

        if (v == addGroupBtn) {
            String groupId = addGroupEt.getText().toString().trim();
            if (TextUtils.isEmpty(groupId)) {
                Toast.makeText(this, "组名不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            Pattern pattern = Pattern.compile("^[0-9a-zA-Z_-]{1,}$");
            Matcher matcher = pattern.matcher(groupId);
            if (!matcher.matches()) {
                Toast.makeText(this, "groupId、字母、下划线中的一个或者多个组合", Toast.LENGTH_SHORT).show();
                return ;
            }

            Group group = new Group();
            group.setGroupId(groupId);
            boolean ret = FaceApi.getInstance().groupAdd(group);

            Toast.makeText(this, "添加" + (ret ? "成功" : "失败"), Toast.LENGTH_SHORT).show();
            finish();
        } else if (v == backBtn) {
            finish();
        }
    }
}
