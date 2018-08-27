/*
 * Copyright (C) 2018 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.aip.ui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.Executors;

import org.json.JSONException;
import org.json.JSONObject;

import com.baidu.aip.manager.FaceSDKManager;
import com.baidu.aip.utils.FileUitls;
import com.baidu.aip.utils.NetRequest;
import com.baidu.aip.utils.PreferencesUtil;
import com.baidu.idl.license.AndroidLicenser;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.ReplacementTransformationMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class Activation {

    private Context context;
    private Button activateBtn;
    private Button backBtn;
    private TextView deviceIdTv;
    private EditText keyEt;
    private String device = "";
    private Dialog activationDialog;
    private Handler handler = new Handler(Looper.getMainLooper());
    private ActivationCallback activationCallback;
    private int lastKeyLen = 0;

    public Activation(Context context) {
        this.context = context;
    }

    public void setActivationCallback(ActivationCallback callback) {
        this.activationCallback = callback;
    }

    public void show() {

        PreferencesUtil.initPrefs(context.getApplicationContext());
        activationDialog = new Dialog(context);

        activationDialog.setTitle("设备激活");
        activationDialog.setContentView(initView());
        activationDialog.setCancelable(false);
        activationDialog.show();
        addLisenter();
    }


    private LinearLayout initView(){

        device = AndroidLicenser.get_device_id(context.getApplicationContext());

        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rootParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        rootParams.gravity = Gravity.CENTER;
        root.setBackgroundColor(Color.WHITE);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);

        TextView titleTv = new TextView(context);
        titleTv.setText("设备激活");
        titleTv.setTextSize(dip2px(10));

        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.gravity = Gravity.CENTER;
        titleParams.topMargin = dip2px(10);
        titleParams.rightMargin = dip2px(30);
        titleParams.leftMargin = dip2px(30);


        deviceIdTv = new TextView(context);
        deviceIdTv.setText(device);

        LinearLayout.LayoutParams deviceIdParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        deviceIdParams.gravity = Gravity.CENTER;
        deviceIdParams.topMargin = dip2px(40);
        deviceIdParams.rightMargin = dip2px(30);
        deviceIdParams.leftMargin = dip2px(30);

        keyEt = new EditText(context);
        keyEt.setHint("输入序列号");
        keyEt.setText(PreferencesUtil.getString("activate_key", ""));
        // keyEt.setText("VMVY-PLkd-OsJN-veIc");

        LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        keyParams.gravity = Gravity.CENTER;
        keyParams.topMargin = dip2px(40);
        keyParams.rightMargin = dip2px(30);
        keyParams.leftMargin = dip2px(30);
        keyEt.setTransformationMethod(new AllCapTransformationMethod(true));
        keyEt.setWidth(dip2px(260));

        LinearLayout.LayoutParams activateParams = new LinearLayout.LayoutParams(dip2px(260), dip2px(48));
        activateParams.gravity = Gravity.CENTER;
        activateParams.topMargin = dip2px(40);
        activateParams.rightMargin = dip2px(40);
        activateParams.leftMargin = dip2px(40);
        activateBtn = new Button(context);
        // activateBtn.setId(100);
        activateBtn.setText("激      活");

        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dip2px(260), dip2px(48));
        backParams.gravity = Gravity.CENTER;
        backParams.topMargin = dip2px(5);;
        backParams.bottomMargin = dip2px(20);
        backParams.rightMargin = dip2px(40);
        backParams.leftMargin = dip2px(40);
        backBtn = new Button(context);
        // activateBtn.setId(100);
        backBtn.setText("返      回");

        root.addView(titleTv, titleParams);
        root.addView(deviceIdTv, deviceIdParams);
        root.addView(keyEt, keyParams);
        root.addView(activateBtn, activateParams);
        root.addView(backBtn, backParams);
        return root;
    }

    private void addLisenter() {
        keyEt.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.toString().length() > 19) {
                    keyEt.setText(s.toString().substring(0, 19));
                    keyEt.setSelection(keyEt.getText().length());
                    lastKeyLen = s.length();
                    return;
                }
                if (s.toString().length() < lastKeyLen ) {
                    lastKeyLen = s.length();
                    return;
                }
                String text = s.toString().trim();
                if (keyEt.getSelectionStart() < text.length()) {
                    return;
                }
                if (text.length() == 4 || text.length() == 9 || text.length() == 14) {
                    keyEt.setText(text + "-");
                    keyEt.setSelection(keyEt.getText().length());
                }

                lastKeyLen = s.length();
            }
        });
        activateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String key = keyEt.getText().toString().trim().toUpperCase();
                if (TextUtils.isEmpty(key)) {
                    Toast.makeText(context, "序列号不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                request(key);
            }
        });

        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (activationDialog != null) {
                    activationDialog.dismiss();
                }
            }
        });

    }
    private void toast(final String text) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, text, Toast.LENGTH_LONG).show();
            }
        });
    }



    private int dip2px(int dip) {
        Resources resources = context.getResources();
        int px = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dip, resources.getDisplayMetrics());

        return px;
    }

    private void request(final String key) {
        Executors.newSingleThreadExecutor().submit(new Runnable() {
            @Override
            public void run() {
                netRequest(key);
            }
        });
    }
    private void netRequest(final String key) {
        if(NetRequest.isConnected(context)) {
            boolean success = NetRequest.request(new NetRequest.RequestAdapter() {

//                public String getURL() {
//                    return "http://10.94.234.54:8087/activation/key/activate";
//                }

                public String getURL() {
                    return "https://ai.baidu.com/activation/key/activate";
                }

                public String getRequestString() {
                    try {
                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("deviceId", device);
                        jsonObject.put("key", key);
                        jsonObject.put("platformType", 2);
                        jsonObject.put("version", "3.4.2");

                        return jsonObject.toString();
                    } catch (JSONException var10) {
                        var10.printStackTrace();
                        return null;
                    }
                }

                public void parseResponse(InputStream in) throws IOException, JSONException {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];

                    try {
                        int e;
                        while ((e = in.read(buffer)) > 0) {
                            out.write(buffer, 0, e);
                        }
                        out.flush();
                        JSONObject json = new JSONObject(new String(out.toByteArray(), "UTF-8"));
                        Log.i("wtf", "netRequest->" + json.toString());
                        int errorCode = json.optInt("error_code");
                        if (errorCode != 0) {
                            String errorMsg = json.optString("error_msg");
                            toast(errorMsg);
                        } else {
                            parse(json, key);
                        }
                    } catch (Exception e) {
                        toast("激活失败");
                    } finally {
                        if(out != null) {
                            try {
                                out.close();
                            } catch (IOException var12) {
                                var12.printStackTrace();
                            }
                        }
                    }
                }
            });

//            if (!success) {
//                toast("激活失败");
//            }
        } else {
            toast("没有连接网络");
        }
    }

    private void parse(JSONObject json, String key) {
        boolean success = false;
        JSONObject result = json.optJSONObject("result");
        if (result != null) {
            String license = result.optString("license");
            if (!TextUtils.isEmpty(license)) {
                String[] licenses = license.split(",");
                if (licenses != null && licenses.length == 2) {
                    PreferencesUtil.putString("activate_key", key);
                    ArrayList<String> list = new ArrayList<>();
                    list.add(licenses[0]);
                    list.add(licenses[1]);
                    success = FileUitls.c(context, FaceSDKManager.LICENSE_NAME, list);
                }
            }
        }

        if (success) {
            toast("激活成功");
            if (activationCallback != null) {
                activationCallback.callback(true);
                activationDialog.dismiss();
            }
        } else {
            toast("激活失败");
        }
    }

    public interface ActivationCallback {

        public void callback(boolean success);
    }

    public class AllCapTransformationMethod extends ReplacementTransformationMethod {

        private char[] lower = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q',
                'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
        private char[] upper = {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q',
                'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
        private boolean allUpper = false;

        public AllCapTransformationMethod(boolean needUpper) {
            this.allUpper = needUpper;
        }

        @Override
        protected char[] getOriginal() {
            if (allUpper) {
                return lower;
            } else {
                return upper;
            }
        }

        @Override
        protected char[] getReplacement() {
            if (allUpper) {
                return upper;
            } else {
                return lower;
            }
        }
    }

}
