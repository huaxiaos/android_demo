
package com.qihoo.videocloud.player.preview;

import static com.qihoo.videocloud.player.PlayConstant.SHOW_MODEL_LAND;
import static com.qihoo.videocloud.player.PlayConstant.SHOW_MODEL_PORT;
import static com.qihoo.videocloud.player.PlayConstant.SHOW_MODEL_PORT_SMALL;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.qihoo.livecloud.sdk.QHVCSdk;
import com.qihoo.livecloud.tools.NetUtil;
import com.qihoo.livecloud.view.LiveCloudPlayView;
import com.qihoo.livecloudrefactor.R;
import com.qihoo.videocloud.IQHVCPlayer;
import com.qihoo.videocloud.IQHVCPlayerAdvanced;
import com.qihoo.videocloud.QHVCPlayer;
import com.qihoo.videocloud.player.LogAdapter;
import com.qihoo.videocloud.player.PlayConstant;
import com.qihoo.videocloud.utils.NoDoubleClickListener;
import com.qihoo.videocloud.widget.ViewHeader;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VodSeekUpdateFrameActivity extends Activity {

    private static final String TAG = VodSeekUpdateFrameActivity.class.getSimpleName();

    private @PlayConstant.ShowModel int currentShowModel = SHOW_MODEL_PORT_SMALL;

    private IQHVCPlayerAdvanced qhvcPlayer;
    private String url;
    private String channelId;
    private String businessId;
    private boolean autoDecoded;

    private LiveCloudPlayView playView;
    private RelativeLayout rlPlayerContainer;
    private ViewHeader viewHeaderMine;
    private ImageView btnPlay;
    private TextView tvPlayTime;

    private SeekBar sbProgress;
    private int currentProgress;

    private TextView tvDuration;
    private View ivZoom;
    private ListView lvLog;
    private LogAdapter logAdapter;
    private List<String> logList = new ArrayList<>();

    private int videoWidth;
    private int videoHeight;

    private Map<String, Object> mediaInformationMap;
    private long downloadBitratePerSecond;//下行码率
    private long videoBitratePerSecond;// 视频码率
    private long videoFrameRatePerSecond;//视频帧率

    //-----------------------------------  pause seek update  -------------------------------------
    private static final int MSG_AFTER_SEEK_UPDATE_FRAME_PAUSE = 100;// 暂停时seek   start后 pause

    private static final int AFTER_SEEK_PAUSE_DELAY = 60;
    private float cacheVolume = 1;

    private static final int STATUS_INVALID = -1;
    private static final int STATUS_SEEK_COMPLETE_UPDATE_FRAME = 0;
    private int myStatus = STATUS_INVALID;

    private static class MyHandler extends Handler {
        private final WeakReference<VodSeekUpdateFrameActivity> mActivity;

        public MyHandler(VodSeekUpdateFrameActivity activity) {
            mActivity = new WeakReference<VodSeekUpdateFrameActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.i(TAG, "MyHandler handleMessage msg.what: " + msg.what);
            VodSeekUpdateFrameActivity activity = mActivity.get();
            if (activity != null) {
                switch (msg.what) {
                    case MSG_AFTER_SEEK_UPDATE_FRAME_PAUSE: {
                        if (activity.qhvcPlayer != null) {
                            if (activity.myStatus == STATUS_SEEK_COMPLETE_UPDATE_FRAME) {

                                if (activity.qhvcPlayer.isPlaying()) {
                                    try {
                                        activity.qhvcPlayer.pause();
                                        activity.myStatus = STATUS_INVALID;

                                    } catch (Exception e) {
                                        Log.e(TAG, e.getMessage());
                                    }
                                } else {
                                    if (activity.mHandler != null) {
                                        Log.e(TAG, "handle. send msg...");
                                        activity.mHandler.sendMessageDelayed(Message.obtain(activity.mHandler, MSG_AFTER_SEEK_UPDATE_FRAME_PAUSE), AFTER_SEEK_PAUSE_DELAY);
                                    }
                                }

                            }
                        }
                    }
                        break;

                    default:
                        break;
                }
            }
        }
    }

    private final MyHandler mHandler = new MyHandler(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemNavigationBar();
        super.onCreate(savedInstanceState);

        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        setContentView(R.layout.activity_vod);

        initView();
        initData();
        vod();
    }

    private void initData() {
        Intent i = getIntent();
        businessId = i.getStringExtra("businessId");
        if (!TextUtils.isEmpty(businessId)) {
            QHVCSdk.getInstance().getConfig().setBusinessId(businessId);
        }

        channelId = i.getStringExtra("channelId");
        url = i.getStringExtra("url");
        autoDecoded = i.getBooleanExtra("autoDecoded", Boolean.FALSE);
    }

    private void initView() {
        rlPlayerContainer = (RelativeLayout) findViewById(R.id.rl_player_container);
        playView = (LiveCloudPlayView) findViewById(R.id.playView);
        viewHeaderMine = (ViewHeader) findViewById(R.id.viewHeaderMine);
        viewHeaderMine.setLeftText("点播");
        viewHeaderMine.getLeftIcon().setOnClickListener(new NoDoubleClickListener() {
            @Override
            public void onNoDoubleClick(View v) {
                playerClose();
                finish();
            }
        });

        lvLog = (ListView) findViewById(R.id.lv_log);
        if (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            logAdapter = new LogAdapter(this, logList, R.color.white);
        } else {
            logAdapter = new LogAdapter(this, logList, R.color.color_666666);
        }
        lvLog.setAdapter(logAdapter);

        btnPlay = (ImageView) findViewById(R.id.btn_play);
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (myStatus != STATUS_INVALID) {
                    Log.w(TAG, "seek update frame ing... not respond!");
                    return;
                }

                if (qhvcPlayer != null && qhvcPlayer.isPlaying()) {
                    cacheVolume = qhvcPlayer.getVolume();
                    qhvcPlayer.setVolume(0);
                    Log.e(TAG, "get volume: " + qhvcPlayer.getVolume());
                    qhvcPlayer.pause();

                    btnPlay.setImageDrawable(null);
                    btnPlay.setImageDrawable(getResources().getDrawable(R.drawable.play));
                } else if (qhvcPlayer != null && qhvcPlayer.isPaused()) {
                    Log.e(TAG, " set volume: " + cacheVolume);
                    qhvcPlayer.setVolume(cacheVolume);
                    qhvcPlayer.start();

                    btnPlay.setImageDrawable(null);
                    btnPlay.setImageDrawable(getResources().getDrawable(R.drawable.pause));
                }
            }
        });

        tvPlayTime = (TextView) findViewById(R.id.tv_play_time);
        sbProgress = (SeekBar) findViewById(R.id.sb_progress);
        sbProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentProgress = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                currentProgress = 0;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

                if (qhvcPlayer != null) {

                    if (qhvcPlayer.isPaused()) {

                        if (myStatus == STATUS_INVALID) {
                            Log.e(TAG, "seek to");
                            qhvcPlayer.seekTo((qhvcPlayer.getDuration() * currentProgress) / 100);
                        }
                    } else {
                        if (myStatus == STATUS_INVALID) {
                            Log.e(TAG, " seek to");
                            qhvcPlayer.seekTo((qhvcPlayer.getDuration() * currentProgress) / 100);
                        } else {
                            Log.e(TAG, "not seek. status: " + myStatus);
                        }
                    }
                }
            }
        });

        tvDuration = (TextView) findViewById(R.id.tv_duration);

        ivZoom = findViewById(R.id.iv_zoom);
        ivZoom.setOnClickListener(new NoDoubleClickListener() {
            @Override
            public void onNoDoubleClick(View v) {
                if (currentShowModel == SHOW_MODEL_PORT_SMALL) {

                    if (videoWidth != 0 && videoHeight != 0) {

                        Log.v(TAG, "width: " + videoWidth + " height: " + videoHeight);
                        if (videoHeight > videoWidth) {

                            currentShowModel = SHOW_MODEL_PORT;
                            portZoomIn();
                        } else {

                            currentShowModel = SHOW_MODEL_LAND;
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                        }
                    } else {

                        Toast.makeText(VodSeekUpdateFrameActivity.this, "cannot zoom. width: " + videoWidth + " height: " + videoHeight, Toast.LENGTH_SHORT).show();
                    }
                } else if (currentShowModel == SHOW_MODEL_PORT) {

                    currentShowModel = SHOW_MODEL_PORT_SMALL;
                    portZoomOut();
                } else if (currentShowModel == SHOW_MODEL_LAND) {

                    currentShowModel = SHOW_MODEL_PORT_SMALL;
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
                // SHOW_MODEL_PORT have not zoom
            }
        });
    }

    private void vod() {

        qhvcPlayer = new QHVCPlayer(this);
        qhvcPlayer.setDisplay(playView);

        try {
            Map<String, Object> options = new HashMap<>();
            //            options.put(IQHVCPlayerAdvanced.KEY_OPTION_MUTE, true);
            //            options.put(IQHVCPlayerAdvanced.KEY_OPTION_POSITION, 30 * 1000);
            //            options.put(IQHVCPlayerAdvanced.KEY_OPTION_PLAY_MODE, IQHVCPlayerAdvanced.PLAYMODE_LOWLATENCY);
            //            options.put(IQHVCPlayerAdvanced.KEY_OPTION_RENDER_MODE, IQHVCPlayerAdvanced.RENDER_MODE_FULL);
            if (autoDecoded) {
                options.put(IQHVCPlayerAdvanced.KEY_OPTION_DECODE_MODE, IQHVCPlayerAdvanced.LIVECLOUD_SMART_DECODE_MODE);
            } else {
                options.put(IQHVCPlayerAdvanced.KEY_OPTION_DECODE_MODE, IQHVCPlayerAdvanced.LIVECLOUD_SOFT_DECODE_MODE);
            }
            qhvcPlayer.setDataSource(IQHVCPlayer.PLAYTYPE_VOD, url, channelId, "", options);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            Toast.makeText(this, "数据源异常", Toast.LENGTH_SHORT).show();
            return;
        }

        qhvcPlayer.setOnPreparedListener(new IQHVCPlayer.OnPreparedListener() {
            @Override
            public void onPrepared() {
                qhvcPlayer.start();
            }
        });
        qhvcPlayer.setOnVideoSizeChangedListener(new IQHVCPlayer.OnVideoSizeChangedListener() {
            @Override
            public void onVideoSizeChanged(int handle, int width, int height) {
                videoWidth = width;
                videoHeight = height;
            }
        });
        qhvcPlayer.setOnInfoListener(new IQHVCPlayer.OnInfoListener() {
            @Override
            public void onInfo(int handle, int what, int extra) {
                Log.w(TAG, "onInfo handle: " + handle + " what: " + what + " extra: " + extra);
            }
        });
        qhvcPlayer.setOnBufferingEventListener(new IQHVCPlayer.OnBufferingEventListener() {
            @Override
            public void onBufferingStart(int handle) {

                Log.w(TAG, "buffering event. start");
            }

            @Override
            public void onBufferingProgress(int handle, int progress) {
                Log.v(TAG, "buffering event. progress: " + progress);
            }

            @Override
            public void onBufferingStop(int handle) {
                Log.w(TAG, "buffering event. stop");

            }
        });
        qhvcPlayer.setOnErrorListener(new IQHVCPlayer.OnErrorListener() {
            @Override
            public boolean onError(int handle, int what, int extra) {
                return false;
            }
        });
        qhvcPlayer.setOnSeekCompleteListener(new IQHVCPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(int handle) {

                Log.e(TAG, "seek complete");

                if (qhvcPlayer != null) {
                    if (qhvcPlayer.isPaused()) {

                        if (myStatus == STATUS_INVALID) {
                            myStatus = STATUS_SEEK_COMPLETE_UPDATE_FRAME;

                            qhvcPlayer.start();

                            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_AFTER_SEEK_UPDATE_FRAME_PAUSE), AFTER_SEEK_PAUSE_DELAY);
                        }
                    } else {
                        Log.e(TAG, "not in pause status.");
                    }
                }
            }
        });
        qhvcPlayer.setOnCompletionListener(new IQHVCPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(int handle) {

            }
        });
        qhvcPlayer.setOnPlayerNetStatsListener(new IQHVCPlayerAdvanced.OnPlayerNetStatsListener() {
            @Override
            public void onPlayerNetStats(int handle, long dvbps, long dabps, long dvfps, long dafps, long fps, long bitrate, long param1, long param2, long param3) {

                //Log.w(TAG, "dvbps: "  + dvbps + " dabps: " + dabps + " dvfps: " + dvfps + " dafps: " + dafps + " fps: " + fps +" bitrate: " +bitrate);

                downloadBitratePerSecond = dvbps + dabps;
                videoBitratePerSecond = bitrate;
                videoFrameRatePerSecond = fps;
            }
        });
        qhvcPlayer.setOnProgressChangeListener(new IQHVCPlayer.onProgressChangeListener() {
            @Override
            public void onProgressChange(int handle, final int total, final int progress) {
                if (progress != 0) {
                    sbProgress.setProgress(progress * 100 / total);
                } else {
                    sbProgress.setProgress(0);
                }

                tvPlayTime.setText(getTimeString(progress));
                tvDuration.setText(getTimeString(total));

                showLog();
            }
        });
        qhvcPlayer.setOnBufferingUpdateListener(new IQHVCPlayer.OnBufferingUpdateListener() {
            @Override
            public void onBufferingUpdate(int handle, int percent) {
                Log.w(TAG, "buffering: " + percent + " volume: " + qhvcPlayer.getVolume());
            }
        });

        try {
            qhvcPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            Toast.makeText(this, "prepareAsync 异常", Toast.LENGTH_SHORT).show();
            return;
        }
    }

    // 纵向显示-- 放大
    private void portZoomIn() {
        ViewGroup.LayoutParams layoutParams = rlPlayerContainer.getLayoutParams();
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        rlPlayerContainer.setLayoutParams(layoutParams);
        rlPlayerContainer.postInvalidate();

        ViewGroup.LayoutParams videolayoutParams = playView.getLayoutParams();
        videolayoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        videolayoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        playView.setLayoutParams(videolayoutParams);
        qhvcPlayer.setDisplay(playView);

        //        lvLog.setPadding(30, 140, 0, 0);
        logAdapter.setTextColorResId(R.color.white);
        logAdapter.notifyDataSetChanged();
    }

    // 纵向显示-- 缩小
    private void portZoomOut() {

        DisplayMetrics dm = getResources().getDisplayMetrics();
        float density = dm.density; // 屏幕密度（像素比例：0.75/1.0/1.5/2.0）
        int densityDPI = dm.densityDpi; // 屏幕密度（每寸像素：120/160/240/320）

        ViewGroup.LayoutParams layoutParams = rlPlayerContainer.getLayoutParams();
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.height = (int) (density * 183.3);//ViewGroup.LayoutParams.MATCH_PARENT;
        rlPlayerContainer.setLayoutParams(layoutParams);
        rlPlayerContainer.postInvalidate();

        ViewGroup.LayoutParams videolayoutParams = playView.getLayoutParams();
        videolayoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        videolayoutParams.height = (int) (density * 183.3);//ViewGroup.LayoutParams.MATCH_PARENT;
        playView.setLayoutParams(videolayoutParams);
        qhvcPlayer.setDisplay(playView);

        //        lvLog.setPadding(30, 198, 0, 0);
        //        lvLog.setPadding(0, 0, 0, 0);
        logAdapter.setTextColorResId(R.color.color_666666);
        logAdapter.notifyDataSetChanged();
        lvLog.postInvalidate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void playerClose() {
        if (qhvcPlayer != null) {
            qhvcPlayer.stop();
            qhvcPlayer = null;
        }
    }

    private void showLog() {
        if (mediaInformationMap == null || mediaInformationMap.isEmpty()) {
            mediaInformationMap = qhvcPlayer.getMediaInformation();
        }

        logList.clear();

        logList.add("版本号: " + QHVCSdk.getInstance().getVersion());
        logList.add("播放url: " + url);
        logList.add("分辨率: " + videoWidth + "*" + videoHeight);
        logList.add("码率: " + videoBitratePerSecond / 1024 + "k");
        logList.add("帧率: " + videoFrameRatePerSecond);
        if (mediaInformationMap != null && !mediaInformationMap.isEmpty()) {
            logList.add("音频格式: " + mediaInformationMap.get(QHVCPlayer.KEY_MEDIA_INFO_AUDIO_FORMAT_STRING));
            logList.add("音频采样率: " + mediaInformationMap.get(QHVCPlayer.KEY_MEDIA_INFO_AUDIO_SAMPLE_RATE_INT));
            logList.add("音频轨道: " + mediaInformationMap.get(QHVCPlayer.KEY_MEDIA_INFO_AUDIO_CHANNEL_INT));
            logList.add("视频编码格式: " + mediaInformationMap.get(QHVCPlayer.KEY_MEDIA_INFO_VIDEO_FORMAT_STRING));
        } else {
            logList.add("音频格式: ");
            logList.add("音频采样率: ");
            logList.add("音频轨道: ");
            logList.add("视频编码格式: ");
        }
        logList.add("下行流量: " + downloadBitratePerSecond / 1024 + "k");
        logList.add("网络类型: " + NetUtil.getNetWorkTypeToString(this));

        logAdapter.setList(logList);
        logAdapter.notifyDataSetChanged();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {

        super.onConfigurationChanged(newConfig);

        Log.w(TAG, "onConfigurationChanged");
        playView.stopRender();
        setContentView(R.layout.activity_vod);
        initView();
        qhvcPlayer.setDisplay(playView);
    }

    private void hideSystemNavigationBar() {
        if (Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) {
            View view = this.getWindow().getDecorView();
            view.setSystemUiVisibility(View.GONE);
        } else if (Build.VERSION.SDK_INT >= 19) {
            View decorView = getWindow().getDecorView();
            //            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            //                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN;
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            decorView.setSystemUiVisibility(uiOptions);
        }
    }

    public static final SimpleDateFormat DATE_FORMAT_hhmmss = new SimpleDateFormat("HH:mm:ss");
    public static final SimpleDateFormat DATE_FORMAT_mmss = new SimpleDateFormat("mm:ss");

    public static String getTimeString(long timeInMillis) {
        if (timeInMillis >= 3600000) {
            return DATE_FORMAT_hhmmss.format(new Date(timeInMillis));
        } else {
            return DATE_FORMAT_mmss.format(new Date(timeInMillis));
        }
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed()");

        playerClose();
        super.onBackPressed();
    }

}
