/*
 * Copyright (C) 2018 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.aip.callback;

import com.baidu.aip.entity.LivenessModel;

public interface ILivenessCallBack {
    public void onCallback(LivenessModel livenessModel);

    public void onTip(int code, String msg);

    public void onCanvasRectCallback(LivenessModel livenessModel);
}
