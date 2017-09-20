
package com.qihoo.videocloud.beauty;

import android.content.Context;
import android.util.Base64;

import com.qihoo.livecloud.faceu.QHVCFaceModelsManager;
import com.qihoo.livecloud.livekit.api.QHVCBeautyInitCallBack;
import com.qihoo.livecloud.livekit.api.QHVCFaceUInitCallBack;
import com.qihoo.livecloud.livekit.api.QHVCLiveKitAdvanced;
import com.qihoo.livecloud.recorder.logUtil.RecorderLogger;
import com.qihoo.livecloud.tools.Stats;
import com.qihoo.videocloud.utils.AESUtil;
import com.qihoo.videocloud.utils.AndroidUtil;

import java.util.Random;

/**
 * Created by LeiXiaojun on 2017/9/20.
 */
public class BeautyHelper {

    private static final String TAG = "LiveCLoudBeauty";

    //应用申请的AK
    private static final String AK = "F4JMDIrIk2Gs4sOpjX_NxEXhsWZknIyW6Q8uZInLoX89jyjVjUlJqRaQNVvpbIcp";

    //警告：应用申请的SK，严禁将SK放到客户端，此处放客户端仅用于演示
    private static final String SK = "Ra6GftT-QpQu_GpmjAdPJ5fSy9ti2l_MNvaerPybF_89jyjVjUlJqRaQNVvpbIcp";

    /**
     * 美颜和FaceU鉴权
     */
    public static void initFaceUAndBeauty(final Context context) {
        //预加载Faceu需要的model文件
        QHVCFaceModelsManager.copyAndUnzipModelFiles(context);

        //云端鉴权
        String privateKey = Stats.getak().substring(0, 16);
        String ak = AESUtil.decode(AK, privateKey);
        String sk = AESUtil.decode(SK, privateKey);

        String timeStamp = String.valueOf(System.currentTimeMillis() / 1000);
        String random = String.valueOf(new Random().nextInt(100000));

        String token = getSign(context, ak, sk, timeStamp, random, true);
        QHVCLiveKitAdvanced.initFaceULibs(context, ak, timeStamp, random, token, new QHVCFaceUInitCallBack() {
            @Override
            public void onCallback(int info) {
                RecorderLogger.i(TAG, "LiveCloud-----FaceU鉴权：info " + info);
                if (info == AUTHENT_OK) {
                    //TODO
                }
            }
        });

        token = getSign(context, ak, sk, timeStamp, random, false);
        QHVCLiveKitAdvanced.initBeautyLibs(context, ak, timeStamp, random, token, new QHVCBeautyInitCallBack() {
            @Override
            public void onCallback(int info) {
                RecorderLogger.i(TAG, "LiveCloud-----美颜鉴权：info " + info);
                if (info == AUTHENT_OK) {
                    //TODO
                }
            }
        });
    }

    //警告：应将SIGN的计算放到业务服务器，此处放客户端仅用于演示
    private static String getSign(Context context, String ak, String sk, String timeStamp, String random, boolean isFaceSDK) {
        String data;
        if (isFaceSDK) {
            data = ak + "humanface"
                    + context.getPackageName() + "android" + random
                    + timeStamp;
        } else {
            data = ak + "meiyan"
                    + context.getPackageName() + "android" + random
                    + timeStamp;
        }

        byte[] data_enc = AndroidUtil.hmacSha1(data, sk);
        return Base64.encodeToString(data_enc, Base64.NO_WRAP);
    }
}
