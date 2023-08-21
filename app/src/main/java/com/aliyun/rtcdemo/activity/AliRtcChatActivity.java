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
import com.faceunity.core.faceunity.FUAIKit;
import com.faceunity.core.faceunity.FURenderKit;
import com.faceunity.core.model.facebeauty.FaceBeautyBlurTypeEnum;
import com.faceunity.core.utils.CameraUtils;
import com.faceunity.nama.FUConfig;
import com.faceunity.nama.FURenderer;
import com.faceunity.nama.data.FaceUnityDataFactory;
import com.faceunity.nama.listener.FURendererListener;
import com.faceunity.nama.ui.FaceUnityView;
import com.faceunity.nama.utils.FuDeviceUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.sdk.SophonSurfaceView;

/**
 * 音视频通话的activity
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
     * 本地流播放view
     */
    private SophonSurfaceView mLocalView;
    /**
     * SDK提供的对音视频通话处理的引擎类
     */
    private AliRtcEngine mAliRtcEngine;

    /**
     * 承载远程User的Adapter
     */
    private ImageView mIvAudio;
    private TextView mTvFPS;
    private ChartUserAdapter mUserListAdapter;
    private TextView mTrackingText;
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
        // 初始化界面上的view
        initView();
        if (checkSelfPermission(REQUESTED_PERMISSIONS[0], PERMISSION_REQ_ID) &&
                checkSelfPermission(REQUESTED_PERMISSIONS[1], PERMISSION_REQ_ID) &&
                checkSelfPermission(REQUESTED_PERMISSIONS[2], PERMISSION_REQ_ID)) {
            // 初始化引擎以及打开预览界面
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
        // 承载远程User的Adapter
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
        mTrackingText = findViewById(R.id.iv_face_detect);
        if (enableFU) {
            // 初始化 FaceUnity 美颜 SDK
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

            mFaceUnityDataFactory = new FaceUnityDataFactory(-1);
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
            Log.e(TAG, "onTrackStatusChanged: 人脸数: " + status);
            runOnUiThread(() -> {
                mTrackingText.setText(type == FUAIProcessorEnum.FACE_PROCESSOR ? R.string.toast_not_detect_face : R.string.toast_not_detect_body);
                mTrackingText.setVisibility(status > 0 ? View.INVISIBLE : View.VISIBLE);
            });
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

        //默认不开启兼容H5
        AliRtcEngine.setH5CompatibleMode(0);
        // 防止初始化过多
        if (mAliRtcEngine == null) {
            JSONObject extraJson = new JSONObject();
            try {
                extraJson.putOpt("user_specified_video_preprocess", "TRUE");
            } catch (JSONException e) {
                Log.e(TAG, "initValues: ", e);
            }
            //实例化,必须在主线程进行。
            if (enableFU) {
                mAliRtcEngine = AliRtcEngine.getInstance(getApplicationContext(), extraJson.toString());
            } else {
                mAliRtcEngine = AliRtcEngine.getInstance(getApplicationContext());
            }
            //设置事件的回调监听
            mAliRtcEngine.setRtcEngineEventListener(mEventListener);
            //设置接受通知事件的回调
            mAliRtcEngine.setRtcEngineNotify(mEngineNotify);
            // 初始化本地视图
            initLocalView();
            //开启预览
            startPreview();
            if (enableFU) {
                registerTexturePreObserver();
            }
            //加入频道
            joinChannel();
        }
    }

    /**
     * 设置本地音频的可用性
     */
    private void setLocalAudioEnable(boolean enable) {
        enableAudio = enable;
        mAliRtcEngine.muteLocalMic(enableAudio, AliRtcEngine.AliRtcMuteLocalAudioMode.AliRtcMuteAudioModeDefault);
        mIvAudio.setImageResource(enable ? R.drawable.selector_meeting_mute : R.drawable.selector_meeting_unmute);
    }


    private void registerTexturePreObserver() {
        mAliRtcEngine.registerLocalVideoTextureObserver(new AliRtcEngine.AliRtcTextureObserver() {

            private void cheekFaceNum() {
                //根据有无人脸 + 设备性能 判断开启的磨皮类型
                float faceProcessorGetConfidenceScore = FUAIKit.getInstance().getFaceProcessorGetConfidenceScore(0);
                if (faceProcessorGetConfidenceScore >= 0.95) {
                    //高端手机并且检测到人脸开启均匀磨皮，人脸点位质
                    if (FURenderKit.getInstance().getFaceBeauty() != null && FURenderKit.getInstance().getFaceBeauty().getBlurType() != FaceBeautyBlurTypeEnum.EquallySkin) {
                        FURenderKit.getInstance().getFaceBeauty().setBlurType(FaceBeautyBlurTypeEnum.EquallySkin);
                        FURenderKit.getInstance().getFaceBeauty().setEnableBlurUseMask(true);
                    }
                } else {
                    if (FURenderKit.getInstance().getFaceBeauty() != null && FURenderKit.getInstance().getFaceBeauty().getBlurType() != FaceBeautyBlurTypeEnum.FineSkin) {
                        FURenderKit.getInstance().getFaceBeauty().setBlurType(FaceBeautyBlurTypeEnum.FineSkin);
                        FURenderKit.getInstance().getFaceBeauty().setEnableBlurUseMask(false);
                    }
                }
            }


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
                if (FUConfig.DEVICE_LEVEL > FuDeviceUtils.DEVICE_LEVEL_MID) {
                    cheekFaceNum();
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
     * 设置本地的预览视图的view
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
        //从控制台生成的鉴权信息，具体内容请查阅:https://help.aliyun.com/document_detail/159037.html?spm=a2c4g.11186623.6.580.2a223b03USksim
        AliRtcAuthInfo userInfo = new AliRtcAuthInfo();
        String roomId = getIntent().getStringExtra("roomId");
        UserInfo.injectUserInfo(roomId, mUserId, userInfo);
        String userName = "benyq" + System.currentTimeMillis() / 1000;
        /*
         *设置自动发布和订阅，只能在joinChannel之前设置
         *参数1    true表示自动发布；false表示手动发布
         *参数2    true表示自动订阅；false表示手动订阅
         */
//        mAliRtcEngine.setAutoPublishSubscribe(true, true);
        // 加入频道，参数1:鉴权信息 参数2:用户名
        mAliRtcEngine.joinChannel(userInfo, userName);

    }

    private void addRemoteUser(String uid) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //判断用户是否在线
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
                // 如果没有，说明已经退出了或者不存在。则不需要添加，并且删除
                if (remoteUserInfo == null) {
                    // remote user exit room
                    Log.e(TAG, "updateRemoteDisplay remoteUserInfo = null, uid = " + uid);
                    return;
                }
                //change
                AliRtcEngine.AliRtcVideoCanvas cameraCanvas = remoteUserInfo.getCameraCanvas();
                AliRtcEngine.AliRtcVideoCanvas screenCanvas = remoteUserInfo.getScreenCanvas();
                //视频情况
                if (vt == AliRtcVideoTrackNo) {
                    //没有视频流
                    cameraCanvas = null;
                    screenCanvas = null;
                } else if (vt == AliRtcVideoTrackCamera) {
                    //相机流
                    screenCanvas = null;
                    cameraCanvas = createCanvasIfNull(cameraCanvas);
                    //SDK内部提供进行播放的view
                    mAliRtcEngine.setRemoteViewConfig(cameraCanvas, uid, AliRtcVideoTrackCamera);
                } else if (vt == AliRtcVideoTrackScreen) {
                    //屏幕流
                    cameraCanvas = null;
                    screenCanvas = createCanvasIfNull(screenCanvas);
                    //SDK内部提供进行播放的view
                    mAliRtcEngine.setRemoteViewConfig(screenCanvas, uid, AliRtcVideoTrackScreen);
                } else if (vt == AliRtcVideoTrackBoth) {
                    //多流
                    cameraCanvas = createCanvasIfNull(cameraCanvas);
                    //SDK内部提供进行播放的view
                    mAliRtcEngine.setRemoteViewConfig(cameraCanvas, uid, AliRtcVideoTrackCamera);
                    screenCanvas = createCanvasIfNull(screenCanvas);
                    //SDK内部提供进行播放的view
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
            //创建canvas，Canvas为SophonSurfaceView或者它的子类
            canvas = new AliRtcEngine.AliRtcVideoCanvas();
            SophonSurfaceView surfaceView = new SophonSurfaceView(this);
            surfaceView.setZOrderOnTop(true);
            surfaceView.setZOrderMediaOverlay(true);
            canvas.view = surfaceView;
            //renderMode提供四种模式：Auto、Stretch、Fill、Crop，建议使用Auto模式。
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
     * 特殊错误码回调的处理方法
     *
     * @param error 错误码
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
     * 错误处理
     *
     * @param error 错误码
     */
    private void noSessionExit(int error) {
        runOnUiThread(() -> new AlertDialog.Builder(AliRtcChatActivity.this)
                .setTitle("ErrorCode : " + error)
                .setMessage("发生错误，请退出房间")
                .setPositiveButton("确定", (dialog, which) -> {
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
     * 用户操作回调监听(回调接口都在子线程)
     */
    private AliRtcEngineEventListener mEventListener = new AliRtcEngineEventListener() {

        /**
         * 加入房间的回调
         * @param result 结果码
         */
        @Override
        public void onJoinChannelResult(int result, String channel, int elapsed) {
            runOnUiThread(() -> {
                if (result == 0) {
                    showToast("加入频道成功");
                } else {
                    showToast("加入频道失败 错误码: " + result);
                }
            });
        }

        /**
         * 出现错误的回调
         * @param error 错误码
         */
        @Override
        public void onOccurError(int error, String message) {
            super.onOccurError(error, message);
            //错误处理
            processOccurError(error);
        }
    };

    /**
     * SDK事件通知(回调接口都在子线程)
     */
    private AliRtcEngineNotify mEngineNotify = new AliRtcEngineNotify() {

        /**
         * 远端用户上线通知
         * @param uid userId
         */
        @Override
        public void onRemoteUserOnLineNotify(String uid, int elapsed) {
            addRemoteUser(uid);
        }

        /**
         * 远端用户下线通知
         * @param uid userId
         */
        @Override
        public void onRemoteUserOffLineNotify(String uid, AliRtcEngine.AliRtcUserOfflineReason reason) {
            removeRemoteUser(uid);
        }

        /**
         * 远端用户发布音视频流变化通知
         * @param s userid
         * @param aliRtcAudioTrack 音频流
         * @param aliRtcVideoTrack 相机流
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
