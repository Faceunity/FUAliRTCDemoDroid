package com.aliyun.rtcdemo.activity;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
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
import com.aliyun.rtcdemo.profile.CSVUtils;
import com.aliyun.rtcdemo.profile.Constant;
import com.faceunity.core.entity.FUCameraConfig;
import com.faceunity.core.entity.FURenderFrameData;
import com.faceunity.core.entity.FURenderInputData;
import com.faceunity.core.entity.FURenderOutputData;
import com.faceunity.core.enumeration.FUAIProcessorEnum;
import com.faceunity.core.enumeration.FUTransformMatrixEnum;
import com.faceunity.core.faceunity.FUAIKit;
import com.faceunity.core.faceunity.FURenderKit;
import com.faceunity.core.listener.OnGlRendererListener;
import com.faceunity.core.model.facebeauty.FaceBeautyBlurTypeEnum;
import com.faceunity.core.renderer.CameraRenderer;
import com.faceunity.nama.FUConfig;
import com.faceunity.nama.FURenderer;
import com.faceunity.nama.data.FaceUnityDataFactory;
import com.faceunity.nama.ui.FaceUnityView;
import com.faceunity.nama.utils.FuDeviceUtils;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.sdk.SophonSurfaceView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static com.alivc.rtc.AliRtcEngine.AliRtcAudioTrack.AliRtcAudioTrackNo;
import static com.alivc.rtc.AliRtcEngine.AliRtcRenderMode.AliRtcRenderModeAuto;
import static com.alivc.rtc.AliRtcEngine.AliRtcVideoTrack.AliRtcVideoTrackBoth;
import static com.alivc.rtc.AliRtcEngine.AliRtcVideoTrack.AliRtcVideoTrackCamera;
import static com.alivc.rtc.AliRtcEngine.AliRtcVideoTrack.AliRtcVideoTrackNo;
import static com.alivc.rtc.AliRtcEngine.AliRtcVideoTrack.AliRtcVideoTrackScreen;
import static org.webrtc.alirtcInterface.ErrorCodeEnum.ERR_ICE_CONNECTION_HEARTBEAT_TIMEOUT;
import static org.webrtc.alirtcInterface.ErrorCodeEnum.ERR_SESSION_REMOVED;

/**
 * 音视频通话的activity
 */
public class AliRtcCustomChatActivity extends AppCompatActivity {
    private static final String TAG = AliRtcCustomChatActivity.class.getName();


    public static final int CAMERA = 1001;
    public static final int SCREEN = 1002;

    public static final String[] VIDEO_INFO_KEYS = {"Width", "Height", "FPS", "LossRate"};

    /**
     * 本地流播放view
     */
    private GLSurfaceView mLocalView;
    /**
     * SDK提供的对音视频通话处理的引擎类
     */
    private AliRtcEngine mAliRtcEngine;

    /**
     * 承载远程User的Adapter
     */
    private ImageView mIvAudio;
    private TextView mTvFPS;
    private TextView mTvDetectFace;
    private ChartUserAdapter mUserListAdapter;
    private String mUserId;
    private boolean enableFU = false;
    private boolean enableAudio = true;
    private CameraRenderer mCameraRenderer;
    private FaceUnityDataFactory mFaceUnityDataFactory;
    private Handler mHandler = null;
    private int mSkipFrame;
    private CSVUtils mCSVUtils;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.alirtc_custom_activity_chat);

        HandlerThread handlerThread = new HandlerThread("postFrame");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());

        mUserId = getIntent().getStringExtra("userId");
        // 初始化界面上的view
        initView();
        initRTCEngineAndStartPreview();
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

        mLocalView = findViewById(R.id.gl_local_view);
        mTvFPS = findViewById(R.id.tv_fps);
        mTvDetectFace = findViewById(R.id.tv_detect_face);
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

        String isOpen = PreferenceUtil.getString(this, PreferenceUtil.KEY_FACEUNITY_ISON);
        if (TextUtils.isEmpty(isOpen) || PreferenceUtil.OFF.equals(isOpen)) {
            enableFU = false;
        } else {
            enableFU = true;
        }
        FaceUnityView beautyControlView = findViewById(R.id.faceunity_view);
        if (enableFU) {
            // 初始化 FaceUnity 美颜 SDK
            FURenderer.getInstance().setup(this);
            mFaceUnityDataFactory = new FaceUnityDataFactory(-1);
            beautyControlView.bindDataFactory(mFaceUnityDataFactory);
        }else {
            beautyControlView.setVisibility(View.GONE);
        }
    }

    /* CameraRenderer 回调*/
    private final OnGlRendererListener mOnGlRendererListener = new OnGlRendererListener() {

        private int width;//数据宽
        private int height;//数据高
        private long mFuCallStartTime = 0; //渲染前时间锚点（用于计算渲染市场）

        private int mCurrentFrameCnt = 0;
        private int mMaxFrameCnt = 10;
        private long mLastOneHundredFrameTimeStamp = 0;
        private long mOneHundredFrameFUTime = 0;

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
        public void onSurfaceCreated() {
            if (enableFU) {
                initCsvUtil(AliRtcCustomChatActivity.this);
                mFaceUnityDataFactory.bindCurrentRenderer();
            }
        }

        @Override
        public void onSurfaceChanged(int width, int height) {
        }

        @Override
        public void onRenderBefore(FURenderInputData inputData) {
            mFuCallStartTime = System.nanoTime();
            inputData.getRenderConfig().setNeedBufferReturn(true);

            if (FUConfig.DEVICE_LEVEL > FuDeviceUtils.DEVICE_LEVEL_MID) {
                cheekFaceNum();
            }
        }


        @Override
        public void onRenderAfter(@NonNull FURenderOutputData outputData, @NotNull FURenderFrameData frameData) {
            long renderTime = System.nanoTime() - mFuCallStartTime;
            if (mCSVUtils != null) {
                mCSVUtils.writeCsv(null, renderTime);
            }
            if (mSkipFrame -- < 0) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        width = outputData.getImage().getWidth();
                        height = outputData.getImage().getHeight();
                        AliRtcEngine.AliRtcRawDataFrame rawDataFrame
                                = new AliRtcEngine.AliRtcRawDataFrame();
                        rawDataFrame.format = AliRtcEngine.AliRtcVideoFormat.AliRtcVideoFormatNV21;// 支持NV21和I420
                        rawDataFrame.frame = outputData.getImage().getBuffer();
                        rawDataFrame.width = width;
                        rawDataFrame.height = height;
                        rawDataFrame.lineSize[0] = width;
                        rawDataFrame.lineSize[1] = width ;
                        rawDataFrame.lineSize[2] = width ;
                        rawDataFrame.lineSize[3] = 0;
                        rawDataFrame.rotation = 180;
                        rawDataFrame.videoFrameLength = rawDataFrame.frame.length;
                        mAliRtcEngine.pushExternalVideoFrame(rawDataFrame, AliRtcEngine.AliRtcVideoTrack.AliRtcVideoTrackCamera);
                    }
                });
            }
        }

        @Override
        public void onDrawFrameAfter() {
            if (enableFU) {
                benchmarkFPS();
                trackStatus();
            }
        }

        @Override
        public void onSurfaceDestroy() {
            if (mCSVUtils != null) {
                mCSVUtils.close();
            }
            GLES20.glClearColor(0, 0, 0, 255);
            FURenderKit.getInstance().release();
        }

        /*渲染FPS日志*/
        private void benchmarkFPS() {

            mOneHundredFrameFUTime += System.nanoTime() - mFuCallStartTime;
            if (++mCurrentFrameCnt == mMaxFrameCnt) {
                mCurrentFrameCnt = 0;
                double fps = ((double) mMaxFrameCnt) * 1000000000L / (System.nanoTime() - mLastOneHundredFrameTimeStamp);
                long renderTime =  mOneHundredFrameFUTime / mMaxFrameCnt / 1000000L;
                mLastOneHundredFrameTimeStamp = System.nanoTime();
                mOneHundredFrameFUTime = 0;
                String fpsStr = String.format("fps: %.2f, time: %d", fps, (int)renderTime);
                Log.e(TAG, "onFpsChanged: fps " + fpsStr);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTvFPS.setText(fpsStr);
                    }
                });
            }
        }

        /*AI识别数目检测*/
        private void trackStatus() {
            FUAIProcessorEnum fuaiProcessorEnum = FURenderer.getInstance().getAIProcess();
            int trackCount;
            if (fuaiProcessorEnum == FUAIProcessorEnum.HAND_GESTURE_PROCESSOR) {
                trackCount = FUAIKit.getInstance().handProcessorGetNumResults();
            } else if (fuaiProcessorEnum == FUAIProcessorEnum.HUMAN_PROCESSOR) {
                trackCount = FUAIKit.getInstance().humanProcessorGetNumResults();
            } else {
                trackCount = FUAIKit.getInstance().isTracking();
            }
            if (mTvDetectFace != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTvDetectFace.setVisibility(trackCount > 0 ? View.GONE : View.VISIBLE);
                        if (fuaiProcessorEnum == FUAIProcessorEnum.FACE_PROCESSOR) {
                            mTvDetectFace.setText(R.string.toast_not_detect_face);
                        }else if (fuaiProcessorEnum == FUAIProcessorEnum.HUMAN_PROCESSOR) {
                            mTvDetectFace.setText(R.string.toast_not_detect_body);
                        }
                    }
                });
            }
        }

    };

    private void initCsvUtil(Context context) {
        mCSVUtils = new CSVUtils(context);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
        String dateStrDir = format.format(new Date(System.currentTimeMillis()));
        dateStrDir = dateStrDir.replaceAll("-", "").replaceAll("_", "");
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault());
        String dateStrFile = df.format(new Date());
        String filePath = Constant.filePath + dateStrDir + File.separator + "excel-" + dateStrFile + ".csv";
        Log.d(TAG, "initLog: CSV file path:" + filePath);
        StringBuilder headerInfo = new StringBuilder();
        headerInfo.append("version：").append(FURenderer.getInstance().getVersion()).append(CSVUtils.COMMA)
                .append("机型：").append(android.os.Build.MANUFACTURER).append(android.os.Build.MODEL)
                .append("处理方式：双输入返回buffer").append(CSVUtils.COMMA);
        mCSVUtils.initHeader(filePath, headerInfo);
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.iv_switch_camera:
                mCameraRenderer.switchCamera();
                mSkipFrame = 3;
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
            //实例化,必须在主线程进行。
            mAliRtcEngine = AliRtcEngine.getInstance(getApplicationContext());
            //设置事件的回调监听
            mAliRtcEngine.setRtcEngineEventListener(mEventListener);
            //设置接受通知事件的回调
            mAliRtcEngine.setRtcEngineNotify(mEngineNotify);
            mAliRtcEngine.muteLocalMic(true, AliRtcEngine.AliRtcMuteLocalAudioMode.AliRtcMuteAudioModeDefault);
            mAliRtcEngine.setExternalVideoSource(true, false, AliRtcEngine.AliRtcVideoTrack.AliRtcVideoTrackCamera, AliRtcEngine.AliRtcRenderMode.AliRtcRenderModeAuto);
            mCameraRenderer = new CameraRenderer(mLocalView, new FUCameraConfig(), mOnGlRendererListener);
            if (!enableFU) {
                mCameraRenderer.setFURenderSwitch(false);
            }
            // 初始化本地视图
            mLocalView.setKeepScreenOn(true);
            //加入频道
            joinChannel();
        }
    }

    @Override
    protected void onResume() {
        mCameraRenderer.onResume();
        super.onResume();
    }

    @Override
    protected void onPause() {
        mCameraRenderer.onPause();
        super.onPause();
    }

    /**
     * 设置本地音频的可用性
     */
    private void setLocalAudioEnable(boolean enable) {
        enableAudio = enable;
        mAliRtcEngine.muteLocalMic(enableAudio, AliRtcEngine.AliRtcMuteLocalAudioMode.AliRtcMuteAudioModeDefault);
        mIvAudio.setImageResource(enable ? R.drawable.selector_meeting_mute : R.drawable.selector_meeting_unmute);
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

    private void joinChannel() {
        if (mAliRtcEngine == null) {
            return;
        }
        //从控制台生成的鉴权信息，具体内容请查阅:https://help.aliyun.com/document_detail/146833.html
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
        runOnUiThread(() -> new AlertDialog.Builder(AliRtcCustomChatActivity.this)
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
        if (mHandler != null) {
            mHandler.getLooper().quitSafely();
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

}
