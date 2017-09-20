
package com.qihoo.videocloud;

import android.app.Application;
import android.os.StrictMode;

import com.qihoo.livecloud.sdk.DebugUtils;
import com.qihoo.livecloud.sdk.QHVCSdk;
import com.qihoo.livecloud.sdk.QHVCSdkConfig;
import com.qihoo.livecloud.sdk.QHVCServerAddress;
import com.qihoo.livecloud.tools.Constants;
import com.qihoo.livecloud.tools.Stats;
import com.qihoo.livecloudrefactor.R;
import com.qihoo.videocloud.beauty.BeautyHelper;
import com.qihoo.videocloud.debug.Setting;

import org.xutils.x;

/**
 * Created by liuyanqing on 2016/11/10.
 */

public class VideoCloudApplication extends Application {
    private static final boolean DEBUG = true;

    private static VideoCloudApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        useStrictMode();
        x.Ext.init(this);
        x.Ext.setDebug(true); // 是否输出debug日志, 开启debug会影响性能.
        //        CrashReport.initCrashReport(getApplicationContext(), "3c9411c88e", true);

        QHVCSdkInit();
    }

    public static VideoCloudApplication getInstance() {
        return instance;
    }

    private void QHVCSdkInit() {

        // 调试信息
        DebugUtils debugUtils = new DebugUtils();
        debugUtils.setWriteLogs(true)
                .setPlayerLogLevel(Constants.ELogLevel.LOG_LEVEL_DEBUG)
                .setTransportLogLevel(Constants.ELogLevel.LOG_LEVEL_DEBUG);

        // 播放器配置
        QHVCSdkConfig.Builder builder = new QHVCSdkConfig.Builder(this)
                .setBusinessId(getResources().getString(R.string.config_bid))
                .setAppVersion("0.0")
                .setUserId(getResources().getString(R.string.config_uid))
                .setDebugUtils(debugUtils);

        // 服务端地址配置
        QHVCServerAddress serverAddress = Setting.readServerAddress();
        if (serverAddress != null) {
            builder.setServerAddress(serverAddress);
        }

        QHVCSdk.getInstance().init(builder.build());

        setStatsURL();/*设置打点地址*/

        BeautyHelper.initFaceUAndBeauty(this);/*美颜初始化和FaceU鉴权*/
    }

    /**
     * 设置打点地址
     */
    private static void setStatsURL() {
        QHVCSdkConfig qhvcSdkConfig = QHVCSdk.getInstance().getConfig();
        Stats.test_setNotifyUrl(qhvcSdkConfig.getStatUrl(), qhvcSdkConfig.getFeedbackUrl(), qhvcSdkConfig.getMicUrl(), qhvcSdkConfig.getControlUrl());
    }

    public void applicationExit() {
        onTerminate();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    private void useStrictMode() {
        if (DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    //                    .penaltyDeath()
                    .penaltyLog()
                    .build());

            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    //                    .penaltyDeath()
                    .penaltyLog()
                    .build());
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        QHVCSdk.getInstance().destroy();
    }

}
