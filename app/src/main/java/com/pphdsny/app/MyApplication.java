package com.pphdsny.app;

import android.app.Application;

import com.alibaba.android.arouter.launcher.ARouter;

/**
 * Created by wangpeng on 2019/3/4.
 */
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ARouter.init(this);
    }
}
