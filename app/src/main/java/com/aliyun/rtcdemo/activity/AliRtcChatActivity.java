package com.aliyun.rtcdemo.activity;

import static com.alivc.rtc.AliRtcEngine.AliRtcRenderMode.AliRtcRenderModeAuto;
import static com.alivc.rtc.AliRtcEngine.AliRtcVideoTrack.AliRtcVideoTrackBoth;
import static com.alivc.rtc.AliRtcEngine.AliRtcVideoTrack.AliRtcVideoTrackCamera;
import static com.alivc.rtc.AliRtcEngine.AliRtcVideoTrack.AliRtcVideoTrackNo;
import static com.alivc.rtc.AliRtcEngine.AliRtcVideoTrack.AliRtcVideoTrackScreen;
import static org.webrtc.alirtcInterface.ErrorCodeEnum.ERR_ICE_CONNECTION_HEARTBEAT_TIMEOUT;
import static org.webrtc.alirtcInterface.ErrorCodeEnum.ERR_SESSION_REMOVED;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.alivc.rtc.AliRtcAuthInfo;
import com.alivc.rtc.AliRtcEngine;
import com.alivc.rtc.AliRtcEngineEventListener;
import com.alivc.rtc.AliRtcEngineNotify;
import com.alivc.rtc.AliRtcRemoteUserInfo;
import com.aliyun.rtcdemo.PreferenceUtil;
import com.aliyun.rtcdemo.R;
import com.aliyun.rtcdemo.UserInfo;
import com.aliyun.rtcdemo.adapter.BaseRecyclerViewAdapter;
import com.aliyun.rtcdemo.adapter.ChartUserAdapter;
import com.aliyun.rtcdemo.bean.ChartUserBean;
import com.faceunity.core.enumeration.CameraFacingEnum;
import com.faceunity.core.enumeration.FUAIProcessorEnum;
import com.faceunity.core.enumeration.FUInputTextureEnum;
import com.faceunity.core.enumeration.FUTransformMatrixEnum;
import com.faceunity.core.faceunity.FURenderKit;
import com.faceunity.core.utils.CameraUtils;
import com.faceunity.nama.FURenderer;
import com.faceunity.nama.data.FaceUnityDataFactory;
import com.faceunity.nama.listener.FURendererListener;
import com.faceunity.nama.ui.FaceUnityView;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.sdk.SophonSurfaceView;

/**
 * ??????????????????activity
 */
public class AliRtcChatActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = AliRtcChatActivity.class.getName();


    public static final int CAMERA = 1001;
    public static final int SCREEN = 1002;

    public static final String[] VIDEO_INFO_KEYS = {"Width", "Height", "FPS", "LossRate"};

    private static final int PERMISSION_REQ_ID = 0x0002;

    private static final String[] REQUESTED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    /**
     * ???????????????view
     */
    private SophonSurfaceView mLocalView;
    /**
     * SDK?????????????????????????????????????????????
     */
    private AliRtcEngine mAliRtcEngine;

    /**
     * ????????????User???Adapter
     */
    private ImageView mIvAudio;
    private TextView mTvFPS;
    private ChartUserAdapter mUserListAdapter;
    private FURenderer mFURenderer;
    private SensorManager mSensorManager;
    private String mUserId;
    private int mSkipFrames = -1;
    private boolean enableFU = false;
    private boolean enableAudio = true;
    private FaceUnityDataFactory mFaceUnityDataFactory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.alirtc_activity_chat);
        mUserId = getIntent().getStringExtra("userId");
        String isOpen = PreferenceUtil.getString(this, PreferenceUtil.KEY_FACEUNITY_ISON);
        if (TextUtils.isEmpty(isOpen) || PreferenceUtil.OFF.equals(isOpen)) {
            enableFU = false;
        } else {
            enableFU = true;
        }
        // ?????????????????????view
        initView();
        if (checkSelfPermission(REQUESTED_PERMISSIONS[0], PERMISSION_REQ_ID) &&
                checkSelfPermission(REQUESTED_PERMISSIONS[1], PERMISSION_REQ_ID) &&
                checkSelfPermission(REQUESTED_PERMISSIONS[2], PERMISSION_REQ_ID)) {
            // ???????????????????????????????????????
            initRTCEngineAndStartPreview();
        }
    }

    private boolean checkSelfPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, requestCode);
            return false;
        }

        return true;
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
                return;
            }
            initRTCEngineAndStartPreview();
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

    private void initView() {
        mLocalView = findViewById(R.id.sf_local_view);
        mTvFPS = findViewById(R.id.tv_fps);
        mIvAudio = findViewById(R.id.iv_audio);
        // ????????????User???Adapter
        mUserListAdapter = new ChartUserAdapter();
        RecyclerView chartUserListView = findViewById(R.id.chart_content_userlist);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        chartUserListView.setLayoutManager(layoutManager);
        chartUserListView.addItemDecoration(new BaseRecyclerViewAdapter.DividerGridItemDecoration(
                getResources().getDrawable(R.drawable.chart_content_userlist_item_divider)));
        DefaultItemAnimator anim = new DefaultItemAnimator();
        anim.setSupportsChangeAnimations(false);
        chartUserListView.setItemAnimator(anim);
        chartUserListView.setAdapter(mUserListAdapter);
        mUserListAdapter.setOnSubConfigChangeListener(mOnSubConfigChangeListener);

        FaceUnityView beautyControlView = findViewById(R.id.faceunity_view);
        if (enableFU) {
            // ????????? FaceUnity ?????? SDK
            FURenderer.getInstance().setup(this);
            mFURenderer = FURenderer.getInstance();
            int cameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;

            mFURenderer.setInputTextureMatrix((cameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT ? FUTransformMatrixEnum.CCROT0 : FUTransformMatrixEnum.CCROT0_FLIPVERTICAL));
            mFURenderer.setInputBufferMatrix((cameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT ? FUTransformMatrixEnum.CCROT0 : FUTransformMatrixEnum.CCROT0_FLIPVERTICAL));
            mFURenderer.setOutputMatrix((cameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT ? FUTransformMatrixEnum.CCROT0_FLIPVERTICAL : FUTransformMatrixEnum.CCROT0));

            mFURenderer.setInputTextureType(FUInputTextureEnum.FU_ADM_FLAG_COMMON_TEXTURE);
            mFURenderer.setCameraFacing(cameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT ? CameraFacingEnum.CAMERA_FRONT : CameraFacingEnum.CAMERA_BACK);
            mFURenderer.setInputOrientation(CameraUtils.INSTANCE.getCameraOrientation(cameraFacing));
            mFURenderer.setMarkFPSEnable(true);

            mFaceUnityDataFactory = new FaceUnityDataFactory(0);
            beautyControlView.bindDataFactory(mFaceUnityDataFactory);
            mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            beautyControlView.setVisibility(View.GONE);
        }

    }

    private FURendererListener mFURendererListener = new FURendererListener() {
        @Override
        public void onPrepare() {
            mFaceUnityDataFactory.bindCurrentRenderer();
        }

        @Override
        public void onTrackStatusChanged(FUAIProcessorEnum type, int status) {
            Log.e(TAG, "onTrackStatusChanged: ?????????: " + status);
        }

        @Override
        public void onFpsChanged(double fps, double callTime) {
            String fpsStr = String.format("fps: %.2f, time: %d", fps, (int) callTime);
            Log.e(TAG, "onFpsChanged: fps " + fpsStr);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTvFPS.setText(fpsStr);
                }
            });
        }

        @Override
        public void onRelease() {

        }
    };

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.iv_switch_camera:
                int cameraType = mAliRtcEngine.getCurrentCameraDirection().getValue();
                mAliRtcEngine.switchCamera();
                if (mFURenderer != null) {
                    cameraType = Camera.CameraInfo.CAMERA_FACING_FRONT - cameraType;
                    mFURenderer.setCameraFacing(cameraType == Camera.CameraInfo.CAMERA_FACING_FRONT ? CameraFacingEnum.CAMERA_FRONT : CameraFacingEnum.CAMERA_BACK);
                    mFURenderer.setInputOrientation(CameraUtils.INSTANCE.getCameraOrientation(cameraType));

                    mFURenderer.setInputTextureMatrix((cameraType == Camera.CameraInfo.CAMERA_FACING_FRONT ? FUTransformMatrixEnum.CCROT0 : FUTransformMatrixEnum.CCROT0_FLIPVERTICAL));
                    mFURenderer.setInputBufferMatrix((cameraType == Camera.CameraInfo.CAMERA_FACING_FRONT ? FUTransformMatrixEnum.CCROT0 : FUTransformMatrixEnum.CCROT0_FLIPVERTICAL));
                    mFURenderer.setOutputMatrix((cameraType == Camera.CameraInfo.CAMERA_FACING_FRONT ? FUTransformMatrixEnum.CCROT0_FLIPVERTICAL : FUTransformMatrixEnum.CCROT0));

                    mSkipFrames = 2;
                }
                break;
            case R.id.iv_leave:
                onBackPressed();
                break;
            case R.id.iv_audio:
                enableAudio = !enableAudio;
                setLocalAudioEnable(enableAudio);
                break;
        }
    }

    private void initRTCEngineAndStartPreview() {

        //?????????????????????H5
        AliRtcEngine.setH5CompatibleMode(0);
        // ?????????????????????
        if (mAliRtcEngine == null) {
            JSONObject extraJson = new JSONObject();
            try {
                extraJson.putOpt("user_specified_video_preprocess", "TRUE");
            } catch (JSONException e) {
                Log.e(TAG, "initValues: ", e);
            }
            //?????????,???????????????????????????
            if (enableFU) {
                mAliRtcEngine = AliRtcEngine.getInstance(getApplicationContext(), extraJson.toString());
            } else {
                mAliRtcEngine = AliRtcEngine.getInstance(getApplicationContext());
            }
            //???????????????????????????
            mAliRtcEngine.setRtcEngineEventListener(mEventListener);
            //?????????????????????????????????
            mAliRtcEngine.setRtcEngineNotify(mEngineNotify);
            // ?????????????????????
            initLocalView();
            //????????????
            startPreview();
            if (enableFU) {
                registerTexturePreObserver();
            }
            //????????????
            joinChannel();
        }
    }

    /**
     * ??????????????????????????????
     */
    private void setLocalAudioEnable(boolean enable) {
        enableAudio = enable;
        mAliRtcEngine.muteLocalMic(enableAudio, AliRtcEngine.AliRtcMuteLocalAudioMode.AliRtcMuteAudioModeDefault);
        mIvAudio.setImageResource(enable ? R.drawable.selector_meeting_mute : R.drawable.selector_meeting_unmute);
    }


    private void registerTexturePreObserver() {
        mAliRtcEngine.registerLocalVideoTextureObserver(new AliRtcEngine.AliRtcTextureObserver() {
            @Override
            public void onTextureCreate(long l) {
                Log.i(TAG, "onTextureCreate uid:" + Thread.currentThread().getId());
                mFURenderer.prepareRenderer(mFURendererListener);
            }

            @Override
            public int onTextureUpdate(int texId, int width, int height, AliRtcEngine.AliRtcVideoSample aliRtcVideoSample) {
                Log.v(TAG, "onTexture textId:" + texId + ", width:" + width + ", height:" + height + ", rotate:" + aliRtcVideoSample.rotate);
                if (mSkipFrames > 0) {
                    mSkipFrames--;
                    FURenderKit.getInstance().clearCacheResource();
                }
                return mFURenderer.onDrawFrameSingleInput(texId, width, height);
            }

            @Override
            public void onTextureDestroy() {
                Log.i(TAG, "onTextureDestroy tid:" + Thread.currentThread().getId());
                mFURenderer.release();
            }
        });
    }


    private void startPreview() {
        if (mAliRtcEngine == null) {
            return;
        }
        try {
            mAliRtcEngine.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ??????????????????????????????view
     */
    private void initLocalView() {
        mLocalView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        mLocalView.setZOrderOnTop(false);
        mLocalView.setZOrderMediaOverlay(false);
        AliRtcEngine.AliRtcVideoCanvas aliVideoCanvas = new AliRtcEngine.AliRtcVideoCanvas();
        aliVideoCanvas.view = mLocalView;
        aliVideoCanvas.renderMode = AliRtcRenderModeAuto;
        if (mAliRtcEngine != null) {
            mAliRtcEngine.setLocalViewConfig(aliVideoCanvas, AliRtcVideoTrackCamera);
        }
    }

    private void joinChannel() {
        if (mAliRtcEngine == null) {
            return;
        }
        //?????????????????????????????????????????????????????????:https://help.aliyun.com/document_detail/159037.html?spm=a2c4g.11186623.6.580.2a223b03USksim
        AliRtcAuthInfo userInfo = new AliRtcAuthInfo();
        String roomId = getIntent().getStringExtra("roomId");
        UserInfo.injectUserInfo(roomId, mUserId, userInfo);
        String userName = "benyq" + System.currentTimeMillis() / 1000;
        /*
         *???????????????????????????????????????joinChannel????????????
         *??????1    true?????????????????????false??????????????????
         *??????2    true?????????????????????false??????????????????
         */
//        mAliRtcEngine.setAutoPublishSubscribe(true, true);
        // ?????????????????????1:???????????? ??????2:?????????
        mAliRtcEngine.joinChannel(userInfo, userName);

    }

    private void addRemoteUser(String uid) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //????????????????????????
                if (null == mAliRtcEngine) {
                    return;
                }
                AliRtcRemoteUserInfo remoteUserInfo = mAliRtcEngine.getUserInfo(uid);
                if (remoteUserInfo != null) {
                    mUserListAdapter.updateData(convertRemoteUserToUserData(remoteUserInfo), true);
                }
            }
        });
    }

    private void removeRemoteUser(String uid) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mUserListAdapter.removeData(uid, true);
            }
        });
    }

    private void updateRemoteDisplay(String uid, AliRtcEngine.AliRtcAudioTrack at, AliRtcEngine.AliRtcVideoTrack vt) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (null == mAliRtcEngine) {
                    return;
                }
                AliRtcRemoteUserInfo remoteUserInfo = mAliRtcEngine.getUserInfo(uid);
                // ???????????????????????????????????????????????????????????????????????????????????????
                if (remoteUserInfo == null) {
                    // remote user exit room
                    Log.e(TAG, "updateRemoteDisplay remoteUserInfo = null, uid = " + uid);
                    return;
                }
                //change
                AliRtcEngine.AliRtcVideoCanvas cameraCanvas = remoteUserInfo.getCameraCanvas();
                AliRtcEngine.AliRtcVideoCanvas screenCanvas = remoteUserInfo.getScreenCanvas();
                //????????????
                if (vt == AliRtcVideoTrackNo) {
                    //???????????????
                    cameraCanvas = null;
                    screenCanvas = null;
                } else if (vt == AliRtcVideoTrackCamera) {
                    //?????????
                    screenCanvas = null;
                    cameraCanvas = createCanvasIfNull(cameraCanvas);
                    //SDK???????????????????????????view
                    mAliRtcEngine.setRemoteViewConfig(cameraCanvas, uid, AliRtcVideoTrackCamera);
                } else if (vt == AliRtcVideoTrackScreen) {
                    //?????????
                    cameraCanvas = null;
                    screenCanvas = createCanvasIfNull(screenCanvas);
                    //SDK???????????????????????????view
                    mAliRtcEngine.setRemoteViewConfig(screenCanvas, uid, AliRtcVideoTrackScreen);
                } else if (vt == AliRtcVideoTrackBoth) {
                    //??????
                    cameraCanvas = createCanvasIfNull(cameraCanvas);
                    //SDK???????????????????????????view
                    mAliRtcEngine.setRemoteViewConfig(cameraCanvas, uid, AliRtcVideoTrackCamera);
                    screenCanvas = createCanvasIfNull(screenCanvas);
                    //SDK???????????????????????????view
                    mAliRtcEngine.setRemoteViewConfig(screenCanvas, uid, AliRtcVideoTrackScreen);
                } else {
                    return;
                }
                ChartUserBean chartUserBean = convertRemoteUserInfo(remoteUserInfo, cameraCanvas, screenCanvas);
                mUserListAdapter.updateData(chartUserBean, true);

            }
        });
    }

    private ChartUserBean convertRemoteUserToUserData(AliRtcRemoteUserInfo remoteUserInfo) {
        String uid = remoteUserInfo.getUserID();
        ChartUserBean ret = mUserListAdapter.createDataIfNull(uid);
        ret.mUserId = uid;
        ret.mUserName = remoteUserInfo.getDisplayName();
        ret.mIsCameraFlip = false;
        ret.mIsScreenFlip = false;
        return ret;
    }

    private AliRtcEngine.AliRtcVideoCanvas createCanvasIfNull(AliRtcEngine.AliRtcVideoCanvas canvas) {
        if (canvas == null || canvas.view == null) {
            //??????canvas???Canvas???SophonSurfaceView??????????????????
            canvas = new AliRtcEngine.AliRtcVideoCanvas();
            SophonSurfaceView surfaceView = new SophonSurfaceView(this);
            surfaceView.setZOrderOnTop(true);
            surfaceView.setZOrderMediaOverlay(true);
            canvas.view = surfaceView;
            //renderMode?????????????????????Auto???Stretch???Fill???Crop???????????????Auto?????????
            canvas.renderMode = AliRtcRenderModeAuto;
        }
        return canvas;
    }

    private ChartUserBean convertRemoteUserInfo(AliRtcRemoteUserInfo remoteUserInfo,
                                                AliRtcEngine.AliRtcVideoCanvas cameraCanvas,
                                                AliRtcEngine.AliRtcVideoCanvas screenCanvas) {
        String uid = remoteUserInfo.getUserID();
        ChartUserBean ret = mUserListAdapter.createDataIfNull(uid);
        ret.mUserId = remoteUserInfo.getUserID();

        ret.mUserName = remoteUserInfo.getDisplayName();

        ret.mCameraSurface = cameraCanvas != null ? (SophonSurfaceView) cameraCanvas.view : null;
        ret.mIsCameraFlip = cameraCanvas != null && cameraCanvas.mirrorMode == AliRtcEngine.AliRtcRenderMirrorMode.AliRtcRenderMirrorModeAllEnabled;

        ret.mScreenSurface = screenCanvas != null ? (SophonSurfaceView) screenCanvas.view : null;
        ret.mIsScreenFlip = screenCanvas != null && screenCanvas.mirrorMode == AliRtcEngine.AliRtcRenderMirrorMode.AliRtcRenderMirrorModeAllEnabled;

        return ret;
    }

    /**
     * ????????????????????????????????????
     *
     * @param error ?????????
     */
    private void processOccurError(int error) {
        switch (error) {
            case ERR_ICE_CONNECTION_HEARTBEAT_TIMEOUT:
            case ERR_SESSION_REMOVED:
                noSessionExit(error);
                break;
            default:
                break;
        }
    }

    /**
     * ????????????
     *
     * @param error ?????????
     */
    private void noSessionExit(int error) {
        runOnUiThread(() -> new AlertDialog.Builder(AliRtcChatActivity.this)
                .setTitle("ErrorCode : " + error)
                .setMessage("??????????????????????????????")
                .setPositiveButton("??????", (dialog, which) -> {
                    dialog.dismiss();
                    onBackPressed();
                })
                .create()
                .show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAliRtcEngine != null) {
            mAliRtcEngine.destroy();
        }
        if (null != mSensorManager) {
            mSensorManager.unregisterListener(this);
        }
    }

    /**
     * ????????????????????????(???????????????????????????)
     */
    private AliRtcEngineEventListener mEventListener = new AliRtcEngineEventListener() {

        /**
         * ?????????????????????
         * @param result ?????????
         */
        @Override
        public void onJoinChannelResult(int result, String channel, int elapsed) {
            runOnUiThread(() -> {
                if (result == 0) {
                    showToast("??????????????????");
                } else {
                    showToast("?????????????????? ?????????: " + result);
                }
            });
        }

        /**
         * ?????????????????????
         * @param error ?????????
         */
        @Override
        public void onOccurError(int error, String message) {
            super.onOccurError(error, message);
            //????????????
            processOccurError(error);
        }
    };

    /**
     * SDK????????????(???????????????????????????)
     */
    private AliRtcEngineNotify mEngineNotify = new AliRtcEngineNotify() {

        /**
         * ????????????????????????
         * @param uid userId
         */
        @Override
        public void onRemoteUserOnLineNotify(String uid, int elapsed) {
            addRemoteUser(uid);
        }

        /**
         * ????????????????????????
         * @param uid userId
         */
        @Override
        public void onRemoteUserOffLineNotify(String uid, AliRtcEngine.AliRtcUserOfflineReason reason) {
            removeRemoteUser(uid);
        }

        /**
         * ??????????????????????????????????????????
         * @param s userid
         * @param aliRtcAudioTrack ?????????
         * @param aliRtcVideoTrack ?????????
         */
        @Override
        public void onRemoteTrackAvailableNotify(String s, AliRtcEngine.AliRtcAudioTrack aliRtcAudioTrack,
                                                 AliRtcEngine.AliRtcVideoTrack aliRtcVideoTrack) {
            updateRemoteDisplay(s, aliRtcAudioTrack, aliRtcVideoTrack);
        }
    };

    private ChartUserAdapter.OnSubConfigChangeListener mOnSubConfigChangeListener = new ChartUserAdapter.OnSubConfigChangeListener() {
        @Override
        public void onFlipView(String uid, int flag, boolean flip) {
            AliRtcRemoteUserInfo userInfo = mAliRtcEngine.getUserInfo(uid);
            switch (flag) {
                case CAMERA:
                    if (userInfo != null) {
                        AliRtcEngine.AliRtcVideoCanvas cameraCanvas = userInfo.getCameraCanvas();
                        if (cameraCanvas != null) {
                            cameraCanvas.mirrorMode = flip ? AliRtcEngine.AliRtcRenderMirrorMode.AliRtcRenderMirrorModeAllEnabled : AliRtcEngine.AliRtcRenderMirrorMode.AliRtcRenderMirrorModeAllDisable;
                            mAliRtcEngine.setRemoteViewConfig(cameraCanvas, uid, AliRtcVideoTrackCamera);
                        }
                    }
                    break;
                case SCREEN:
                    if (userInfo != null) {
                        AliRtcEngine.AliRtcVideoCanvas screenCanvas = userInfo.getScreenCanvas();
                        if (screenCanvas != null) {
                            screenCanvas.mirrorMode = flip ? AliRtcEngine.AliRtcRenderMirrorMode.AliRtcRenderMirrorModeAllEnabled : AliRtcEngine.AliRtcRenderMirrorMode.AliRtcRenderMirrorModeAllDisable;
                            mAliRtcEngine.setRemoteViewConfig(screenCanvas, uid, AliRtcVideoTrackScreen);
                        }
                    }
                    break;
            }
        }

        @Override
        public void onShowVideoInfo(String uid, int flag) {
        }
    };

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            if (Math.abs(x) > 3 || Math.abs(y) > 3) {
                if (Math.abs(x) > Math.abs(y)) {
                    mFURenderer.setDeviceOrientation(x > 0 ? 0 : 180);
                } else {
                    mFURenderer.setDeviceOrientation(y > 0 ? 90 : 270);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
