package com.aliyun.rtcdemo.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.alivc.rtc.AliRtcEngine;
import com.aliyun.rtcdemo.R;

/**
 * 登录activity
 */
public class AliRtcLoginActivity extends AppCompatActivity {
    private static final String TAG = "AliRtcLoginActivity";

    private TextView mSdkVersion;
    private RadioButton mRbTest, mRbTest2;
    private EditText mEtRoomId;
    private static final String[] REQUESTED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private static final int PERMISSION_REQ_ID = 0x0002;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.alirtc_activity_login);

        initView();
        initData();

        if (checkSelfPermission(REQUESTED_PERMISSIONS[0], PERMISSION_REQ_ID) &&
                checkSelfPermission(REQUESTED_PERMISSIONS[1], PERMISSION_REQ_ID) &&
                checkSelfPermission(REQUESTED_PERMISSIONS[2], PERMISSION_REQ_ID)) {
        }

    }

    private boolean checkSelfPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, requestCode);
            return false;
        }

        return true;
    }

    private void initView() {
        mSdkVersion = findViewById(R.id.tv_sdk_version);
        mEtRoomId = findViewById(R.id.et_room_id);

        mRbTest = findViewById(R.id.rbTest);
        mRbTest2 = findViewById(R.id.rbTest2);
    }

    private void initData() {
        mSdkVersion.setText(AliRtcEngine.getSdkVersion());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == PERMISSION_REQ_ID) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED ||
                    grantResults[1] != PackageManager.PERMISSION_GRANTED ||
                    grantResults[2] != PackageManager.PERMISSION_GRANTED) {
                showToast("Need permissions " + Manifest.permission.RECORD_AUDIO +
                        "/" + Manifest.permission.CAMERA + "/" + Manifest.permission.WRITE_EXTERNAL_STORAGE);
                finish();
            }
        }
    }

    public void onClick(View view) {
        Intent intent;
        switch (view.getId()) {
            case R.id.bt_AuthInfo_ali:
                intent = new Intent(this, AliRtcChatActivity.class);
                doCreateChannel(intent);
                break;
            case R.id.bt_AuthInfo_custom:
                intent = new Intent(this, AliRtcCustomChatActivity.class);
                doCreateChannel(intent);
                break;
            default:
                break;
        }
    }

    private void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void doCreateChannel(Intent intent) {
        String roomId = mEtRoomId.getText().toString().trim();
        if (TextUtils.isEmpty(roomId)) {
            Toast.makeText(this, "房间号不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        Bundle b = new Bundle();
        String userId;
        if (mRbTest.isChecked()) {
            userId = "test";
        } else {
            userId = "test2";
        }
        //userId
        b.putString("userId", userId);
        b.putString("roomId", roomId);
        intent.putExtras(b);
        startActivity(intent);
    }

}
