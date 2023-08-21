package com.aliyun.rtcdemo;

import android.content.Context;
import android.support.multidex.MultiDexApplication;

import com.faceunity.nama.FUConfig;
import com.faceunity.nama.utils.FuDeviceUtils;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;

/**
 * 程序入口
 */
public class AliRtcApplication extends MultiDexApplication {

    private static AliRtcApplication sInstance;

    protected RefWatcher refWatcher;

    public static AliRtcApplication getInstance(){
        return sInstance;
    }

    public static RefWatcher getRefWatcher(Context context){
        AliRtcApplication application = (AliRtcApplication) context.getApplicationContext();
        return application.refWatcher;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return;
        }

        FUConfig.DEVICE_LEVEL = FuDeviceUtils.judgeDeviceLevelGPU();
        refWatcher = LeakCanary.install(this);
        // Normal app init code...
    }
}
