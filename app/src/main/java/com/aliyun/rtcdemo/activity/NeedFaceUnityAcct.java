package com.aliyun.rtcdemo.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;

import com.aliyun.rtcdemo.PreferenceUtil;
import com.aliyun.rtcdemo.R;


public class NeedFaceUnityAcct extends AppCompatActivity {

    //是否使用 FaceUnity 美颜
    private boolean mIsOn = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_faceunity);
        final Button button = (Button) findViewById(R.id.btn_set);
        String isOpen = PreferenceUtil.getString(this, PreferenceUtil.KEY_FACEUNITY_ISON);
        if (TextUtils.isEmpty(isOpen) || PreferenceUtil.OFF.equals(isOpen)) {
            mIsOn = false;
        } else {
            mIsOn = true;
        }
        button.setText(mIsOn ? "On" : "Off");

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsOn = !mIsOn;
                button.setText(mIsOn ? "On" : "Off");
            }
        });

        Button btnToMain = (Button) findViewById(R.id.btn_to_main);
        btnToMain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(NeedFaceUnityAcct.this, AliRtcLoginActivity.class);
                PreferenceUtil.persistString(NeedFaceUnityAcct.this, PreferenceUtil.KEY_FACEUNITY_ISON,
                        mIsOn ? PreferenceUtil.ON : PreferenceUtil.OFF);
                startActivity(intent);
                finish();
            }
        });

    }
}
