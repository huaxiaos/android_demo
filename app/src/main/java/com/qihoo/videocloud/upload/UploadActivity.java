
package com.qihoo.videocloud.upload;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.qihoo.livecloud.sdk.QHVCSdk;
import com.qihoo.livecloud.tools.Logger;
import com.qihoo.livecloud.tools.MD5;
import com.qihoo.livecloud.tools.Stats;
import com.qihoo.livecloud.tools.URLSafeBase64;
import com.qihoo.livecloud.upload.LiveCloudUpload;
import com.qihoo.livecloud.upload.LiveCloudUploadConfig;
import com.qihoo.livecloud.upload.LiveCloudUploadEvent;
import com.qihoo.livecloud.upload.OnUploadListener;
import com.qihoo.livecloud.upload.utils.UploadError;
import com.qihoo.livecloudrefactor.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Random;

public class UploadActivity extends Activity implements View.OnClickListener {

    private final static String TAG = "UploadActivity";

    private final static String BUCKET = "videotest";

    private EditText channelIdEditText;
    private EditText urlEditText;

    private ProgressDialog mProgressDialog;

    private LiveCloudUploadEvent uploadEvent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initData();
        initView();
    }

    private void initView() {
        setContentView(R.layout.upload_activty_layout);

        findViewById(R.id.upload_header_left_icon).setOnClickListener(this);
        findViewById(R.id.upload_start).setOnClickListener(this);

        channelIdEditText = (EditText) findViewById(R.id.upload_channel_id);
        urlEditText = (EditText) findViewById(R.id.upload_url);
    }

    private void initData() {

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.upload_header_left_icon:
                finish();
                break;
            case R.id.upload_start:/*开始上传*/
                File file;
                String s = urlEditText.getText().toString().trim();
                if (!TextUtils.isEmpty(s)) {
                    file = new File(s);
                } else {
                    return;
                }

                int parallelNum = LiveCloudUpload.getParallel(file.length());
                //警告：token的计算应该由业务服务端生成，严禁将ak、sk放到客户端，此处放客户端仅用于演示
                String token;
                if (parallelNum == 0) { // 表单上传
                    token = getFormToken(file);
                } else { // 分片上传
                    token = getBlockToken(file, parallelNum);
                }
                mProgressDialog = createdProgressDialog();
                mProgressDialog.show();
                uploadEvent = LiveCloudUpload.uploadFile(file, token, getUploadConfig(file), blockUploadListener);
                break;
        }
    }

    /**
     * 警告：token的计算应该由业务服务端生成，严禁将ak、sk放到客户端，此处放客户端仅用于演示
     */
    private String getFormToken(File file) {
        String strategyJson = formToken(file.getName());
        Logger.e(TAG, strategyJson);
        String safeEncode = URLSafeBase64.encodeToString(strategyJson);
        Logger.e(TAG, safeEncode);
        String sign = MD5.encryptMD5(safeEncode + Stats.getsk());
        return Stats.getak() + ":" + sign + ":" + safeEncode;
    }

    private String formToken(String fileName) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("bucket", BUCKET);
            jsonObject.put("object", fileName);
            jsonObject.put("deadline", System.currentTimeMillis() + 3600 * 1000);
            jsonObject.put("insertOnly", 0);
            return jsonObject.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * 警告：token的计算应该由业务服务端生成，严禁将ak、sk放到客户端，此处放客户端仅用于演示
     */
    private String getBlockToken(File file, int parallelNum) {
        String strategyJson = toGetTokenJson(file, parallelNum);
        Logger.d(TAG, strategyJson);
        String safeEncode = URLSafeBase64.encodeToString(strategyJson);
        Logger.d(TAG, safeEncode);
        String sign = MD5.encryptMD5(safeEncode + Stats.getsk());
        return Stats.getak() + ":" + sign + ":" + safeEncode;
    }

    private String toGetTokenJson(File file, int parallelNum) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.putOpt("bucket", BUCKET)
                    .putOpt("object", file.getName())
                    .putOpt("fsize", file.length())
                    .putOpt("parallel", parallelNum)
                    .putOpt("insertOnly", 0);
            return jsonObject.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "";
    }

    private OnUploadListener blockUploadListener = new OnUploadListener() {
        @Override
        public void onSuccess(String result) {
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
                showToast("上传成功！");
            }
            Logger.e(TAG, "ian, onSuccess, result: " + result);
        }

        @Override
        public void onProgress(long total, long progress) {
            if (mProgressDialog != null) {
                int ps = (int) (progress * 100 / total);
                mProgressDialog.setProgress(ps);
            }
            Logger.e(TAG, "ian, total=" + total + " progress=" + progress);
        }

        @Override
        public void onBlockProgress(int total, int currBlock) {
            Logger.d(TAG, "ian, totalBlock: " + total + ", currBlock: " + currBlock);
        }

        @Override
        public void onFailed(UploadError uploadError) {
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
                showToast("上传失败！");
            }
            Logger.e(TAG, "ian, " + uploadError.toString());
        }
    };

    private LiveCloudUploadConfig getUploadConfig(File file) {
        LiveCloudUploadConfig config = new LiveCloudUploadConfig();

        String cid = channelIdEditText.getText().toString();
        config.setCid(cid);

        String uid = QHVCSdk.getInstance().getConfig().getUserId();
        config.setUid(uid);

        config.setVer(QHVCSdk.getInstance().getConfig().getAppVersion());

        String sid = MD5.encryptMD5(String.valueOf(System.currentTimeMillis()) + String.valueOf(new Random().nextInt()));
        config.setSid(sid);

        config.setMid(QHVCSdk.getInstance().getConfig().getMachineId());

        config.setNet(QHVCSdk.getInstance().getConfig().getNetworkType());

        String rid = "";
        if (file != null) {
            rid = file.getAbsolutePath();
        }
        config.setRid(rid);

        return config;
    }

    private ProgressDialog createdProgressDialog() {
        ProgressDialog dialog = new ProgressDialog(this);
        //设置进度条风格，风格为圆形，旋转的
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        //设置ProgressDialog 标题
        dialog.setTitle("上传文件");
        //设置ProgressDialog 提示信息
        dialog.setMessage("文件上传中");
        //设置ProgressDialog 标题图标
        dialog.setIcon(android.R.drawable.ic_dialog_alert);
        //设置ProgressDialog的最大进度
        dialog.setMax(100);
        //设置ProgressDialog 的一个Button
        dialog.setButton("取消上传", new ProgressDialog.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                cancelUpload();
                dialog.dismiss();
            }
        });
        //设置ProgressDialog 是否可以按退回按键取消
        dialog.setCancelable(false);
        //设置ProgressDialog的当前进度
        dialog.setProgress(1);

        return dialog;
    }

    /**
     * 取消上传
     */
    private void cancelUpload() {
        if (uploadEvent != null) {
            uploadEvent.cancel();
        }
    }

    private void showToast(final String content) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(UploadActivity.this, content, Toast.LENGTH_LONG).show();
            }
        });
    }
}
