## 对接第三方 Demo 的 faceunity 模块

本工程是第三方 Demo 依赖的 faceunity 模块，每次升级 SDK 时会优先在这里改动，然后同步到各个第三方 Demo 中。

当前的 Nama SDK 版本是 **8.7.0**。

--------

## 集成方法

### 一、添加 SDK

将 faceunity 模块添加到工程中，下面是对库文件的说明。

- assets/sticker 文件夹下 \*.bundle 是特效贴纸文件。
- assets/makeup 文件夹下 \*.bundle 是美妆素材文件。
- com/faceunity/nama/authpack.java 是鉴权证书文件，必须提供有效的证书才能运行 Demo，请联系技术支持获取。

通过 Maven 依赖最新版 SDK：`implementation 'com.faceunity:nama:7.2.0'`，方便升级，推荐使用。

其中，AAR 包含以下内容：

```
    +libs                                  
      -nama.jar                        // JNI 接口
    +assets
      +graphic                         // 图形效果道具
        -body_slim.bundle              // 美体道具
        -controller.bundle             // Avatar 道具
        -face_beautification.bundle    // 美颜道具
        -face_makeup.bundle            // 美妆道具
        -fuzzytoonfilter.bundle        // 动漫滤镜道具
        -fxaa.bundle                   // 3D 绘制抗锯齿
        -tongue.bundle                 // 舌头跟踪数据包
      +model                           // 算法能力模型
        -ai_face_processor.bundle      // 人脸识别AI能力模型，需要默认加载
        -ai_face_processor_lite.bundle // 人脸识别AI能力模型，轻量版
        -ai_hand_processor.bundle      // 手势识别AI能力模型
        -ai_human_processor.bundle     // 人体点位AI能力模型
    +jni                               // CNama fuai 库
      +armeabi-v7a
        -libCNamaSDK.so
        -libfuai.so
      +arm64-v8a
        -libCNamaSDK.so
        -libfuai.so
      +x86
        -libCNamaSDK.so
        -libfuai.so
      +x86_64
        -libCNamaSDK.so
        -libfuai.so
```

如需指定应用的 so 架构，请修改 app 模块 build.gradle：

```groovy
android {
    // ...
    defaultConfig {
        // ...
        ndk {
            abiFilters 'armeabi-v7a', 'arm64-v8a'
        }
    }
}
```

如需剔除不必要的 assets 文件，请修改 app 模块 build.gradle：

```groovy
android {
    // ...
    applicationVariants.all { variant ->
        variant.mergeAssetsProvider.configure {
            doLast {
                delete(fileTree(dir: outputDir, includes: ['model/ai_face_processor_lite.bundle',
                                                           'model/ai_hand_processor.bundle',
                                                           'graphics/controller.bundle',
                                                           'graphics/fuzzytoonfilter.bundle',
                                                           'graphics/fxaa.bundle',
                                                           'graphics/tongue.bundle']))
            }
        }
    }
}
```


### 二、使用 SDK

#### 1. 初始化

调用 `FURenderer` 类的  `setup` 方法初始化 SDK，可以在工作线程调用，应用启动后仅需调用一次。

在AliRtcChatActivity中进行初始化。

#### 2.创建

调用 `FURenderer` 类的  `onSurfaceCreated` 方法在 SDK 使用前加载必要的资源。

在 AliRtcChatActivity类中，实现AliTextureObserver接口，在onTextureCreate回调方法中执行FURenderer.onSurfaceCreated();

```
        mAliRtcEngine.RegisterTexturePreObserver("", new ALI_RTC_INTERFACE.AliTextureObserver() {
            @Override
            public void onTextureCreate(String callId, long context) {
                mFURenderer.onSurfaceCreated();
            }

            @Override
            public int onTexture(String callId, int texId, int width, int height, int stride, int rotate, long extraData) {
                return mFURenderer.onDrawFrameSingleInput(texId, width, height);
            }

            @Override
            public void onTextureDestroy(String callId) {
                mFURenderer.onSurfaceDestroyed();
            }
        });
```

#### 3. 图像处理

调用 `FURenderer` 类的  `onDrawFrameXXX` 方法进行图像处理，有许多重载方法适用于不同数据类型的需求。

在 AliRtcChatActivity类中，实现AliTextureObserver接口，在onTexture回调方法中执行美颜操作（代码见上一小节）。

onDrawFrameSingleInput 是单输入，输入图像buffer数组或者纹理Id，输出纹理Id
onDrawFrameDualInput 双输入，输入图像buffer数组与纹理Id，输出纹理Id。性能上，双输入优于单输入

在onDrawFrameSingleInput 与onDrawFrameDualInput 方法内，在执行底层方法之前，都会执行prepareDrawFrame()方法(执行各个特效模块的任务，将美颜参数传给底层)。

阿里云视频通话支持单输入： onDrawFrameSingleInput 

#### 4. 销毁

调用 `FURenderer` 类的  `onSurfaceDestroyed` 方法在 SDK 结束前释放占用的资源。

在 AliRtcChatActivity类中，实现AliTextureObserver接口，在onTextureDestroy回调方法中执行FURenderer.onSurfaceDestroyed();

#### 5. 切换相机

调用 `FURenderer` 类 的  `onCameraChanged` 方法，用于重新为 SDK 设置参数。

该demo并未实现切换相机的功能，但FURenderer.onCameraChanged()在切换相机之后调用

#### 6. 旋转手机

调用 `FURenderer` 类 的  `onDeviceOrientationChanged` 方法，用于重新为 SDK 设置参数。

使用方法：AliRtcChatActivity中可见

```java
1.implements SensorEventListener
2. initView()    
mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);

3.
@Override
protected void onDestroy() {
    super.onDestroy();
    // 清理相关资源
    if (null != mSensorManager) {
        mSensorManager.unregisterListener(this);
    }
}
4. 
//实现接口
@Override
public void onSensorChanged(SensorEvent event) {
    //具体代码见 AliRtcChatActivity 类
}

```

**注意：** 上面一系列方法的使用，可以前往对应类查看，参考该代码示例接入即可。

### 三、接口介绍

- IFURenderer 是核心接口，提供了创建、销毁、处理等功能。使用时通过 FURenderer.Builder 创建合适的 FURenderer 实例即可。
- IModuleManager 是模块管理接口，用于创建和销毁各个功能模块，FURenderer 是其实现类。
- IFaceBeautyModule 是美颜模块的接口，用于调整美颜参数。使用时通过 FURenderer 拿到 FaceBeautyModule 实例，调用里面的接口方法即可。
- IStickerModule 是贴纸模块的接口，用于加载贴纸效果。使用时通过 FURenderer 拿到 StickerModule 实例，调用里面的接口方法即可。
- IMakeModule 是美妆模块的接口，用于加载美妆效果。使用时通过 FURenderer 拿到 MakeupModule 实例，调用里面的接口方法即可。
- IBodySlimModule 是美体模块的接口，用于调整美体参数。使用时通过 FURenderer 拿到 BodySlimModule 实例，调用里面的接口方法即可。

关于 SDK 的更多详细说明，请参看 **[FULiveDemoDroid](https://github.com/Faceunity/FULiveDemoDroid/)**。如有对接问题，请联系技术支持。