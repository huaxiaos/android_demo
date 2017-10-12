
package com.qihoo.videocloud.player.preview;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.CheckBox;
import android.widget.SeekBar;

import com.qihoo.livecloud.tools.Logger;
import com.qihoo.livecloudrefactor.R;

import java.io.File;
import java.io.IOException;

public class MediaPlayerActivity extends Activity implements SurfaceHolder.Callback,
        MediaPlayer.OnPreparedListener, View.OnClickListener {
    private static final String TAG = MediaPlayerActivity.class.getSimpleName();
    private static final String VIDEO_PATH = new File(Environment.getExternalStorageDirectory(), "1.mp4").getAbsolutePath();

    private SurfaceHolder mHolder;
    private MediaPlayer mMediaPlayer;
    private SurfaceView mSurfaceView;

    private int mCurrentTime = 0;
    private CheckBox mPlayPause;

    private long mBeginTick = 0;
    private int mProgress = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mediaplayer);
        mSurfaceView = (SurfaceView) findViewById(R.id.surface);
        mPlayPause = (CheckBox) findViewById(R.id.cb_play_pause);
        mPlayPause.setOnClickListener(this);

        ((SeekBar) findViewById(R.id.seekbar)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Logger.d(TAG, "onProgressChanged seekBar " + " progress=" + progress + " fromUser=" + fromUser);
                mProgress = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mProgress = 0;
                Logger.d(TAG, "onStartTrackingTouch seekBar=" + seekBar);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

                Logger.d(TAG, "onStopTrackingTouch seekBar=" + seekBar + " mProgress=" + mProgress);
                if (mMediaPlayer != null) {
                    mMediaPlayer.seekTo(mMediaPlayer.getDuration() * mProgress / 100);
                }
            }
        });

        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnPreparedListener(this);
        try {
            mMediaPlayer.setDataSource(VIDEO_PATH);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mMediaPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
                Logger.d(TAG, "onInfo. what: " + what + " extra: " + extra);
                if (MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START == what) {
                    long endTick = System.currentTimeMillis();
                    Logger.d(TAG, "onInfo. MEDIA_INFO_VIDEO_RENDERING_START what: " + what + " extra: " + extra + " use tick: " + (endTick - mBeginTick));
                }
                return false;
            }
        });
        mMediaPlayer.prepareAsync();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
        Logger.d(TAG, "surface changed");
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Logger.d(TAG, "surface created");
        mMediaPlayer.setDisplay(holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Logger.d(TAG, "surface destroyed");
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        // Get the dimensions of the video
        int videoWidth = mp.getVideoWidth();
        int videoHeight = mp.getVideoHeight();

        // Get the width of the screen
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        Logger.d(TAG, "screen width " + screenWidth);

        // Get the SurfaceView layout parameters
        android.view.ViewGroup.LayoutParams lp = mSurfaceView.getLayoutParams();

        // Set the width of the SurfaceView to the width of the screen
        lp.width = screenWidth;

        // Set the height of the SurfaceView to match the aspect ratio of the
        // video
        // be sure to cast these as floats otherwise the calculation will likely
        // be 0
        lp.height = (int) (((float) videoHeight / (float) videoWidth) * (float) screenWidth);

        // Commit the layout parameters
        mSurfaceView.setLayoutParams(lp);
        play();
    }

    @Override
    protected void onResume() {
        Logger.d(TAG, "current time on resume " + mCurrentTime);
        super.onResume();
    }

    @Override
    protected void onPause() {
        pause();
        super.onPause();
    }

    private void play() {
        mPlayPause.setEnabled(true);
        mPlayPause.setChecked(true);
        mMediaPlayer.seekTo(mCurrentTime);
        mMediaPlayer.start();
        mBeginTick = System.currentTimeMillis();
    }

    private void pause() {
        if (mMediaPlayer.isPlaying()) {
            mPlayPause.setChecked(false);
            mCurrentTime = mMediaPlayer.getCurrentPosition();
            Logger.d(TAG, "current time on pause " + mCurrentTime);
            mMediaPlayer.pause();
        }
    }

    public void playPause(View view) {
        CheckBox playPause = (CheckBox) view;
        if (playPause.isChecked()) {
            try {
                play();
            } catch (IllegalStateException e) {
                Logger.d(TAG, "Illegal State Exception", e);
            }
        } else {
            if (mMediaPlayer.isPlaying()) {
                pause();
            }
        }
    }

    @Override
    protected void onDestroy() {
        try {
            mMediaPlayer.release();
            mMediaPlayer = null;
        } catch (Exception e) {
        }
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.cb_play_pause: {
                playPause(v);
            }
                break;

            default:
                break;
        }
    }
}
