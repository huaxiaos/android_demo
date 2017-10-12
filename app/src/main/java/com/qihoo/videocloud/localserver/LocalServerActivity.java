
package com.qihoo.videocloud.localserver;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.qihoo.livecloud.sdk.QHVCSdk;
import com.qihoo.livecloud.sdk.QHVCSdkConfig;
import com.qihoo.livecloud.tools.Logger;
import com.qihoo.livecloudrefactor.R;
import com.qihoo.videocloud.localserver.data.LocalServerVideoList;
import com.qihoo.videocloud.localserver.player.LocalServerPlayerActivity;
import com.qihoo.videocloud.localserver.setting.LocalServerSettingActivity;
import com.qihoo.videocloud.utils.DirUtils;

import net.qihoo.videocloud.LocalServer;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by LeiXiaojun on 2017/9/21.
 */
public class LocalServerActivity extends Activity implements View.OnClickListener {

    private static final int PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE = 0X0001;

    protected ImageLoader mImageLoader = ImageLoader.getInstance();
    private boolean mInstanceStateSaved;

    private AtomicBoolean mInitLocalServer = new AtomicBoolean(false);
    private LocalServerVideoListAdapter mLocalServerVideoListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.localserver_activity);

        findViewById(R.id.header_left_icon).setOnClickListener(this);
        findViewById(R.id.header_right_icon).setOnClickListener(this);

        mLocalServerVideoListAdapter = new LocalServerVideoListAdapter(this, mImageLoader, LocalServerVideoList.getList());
        mLocalServerVideoListAdapter.notifyDataSetChanged();

        ListView mListView = (ListView) findViewById(R.id.vod_list);
        mListView.setAdapter(mLocalServerVideoListAdapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!mInitLocalServer.get()) {
                    Toast.makeText(LocalServerActivity.this, "LocalServer尚未初始化！", Toast.LENGTH_SHORT).show();
                    return;
                }

                int curPlayPos = -1;
                if (mLocalServerVideoListAdapter != null) {
                    curPlayPos = mLocalServerVideoListAdapter.stopPlay();
                }
                Intent intent = new Intent(LocalServerActivity.this, LocalServerPlayerActivity.class);
                intent.putExtra("list", (Serializable) mLocalServerVideoListAdapter.getObjects());
                intent.putExtra("id", position);
                if (curPlayPos > 0) {
                    intent.putExtra("curPlayPos", curPlayPos);
                }
                LocalServerActivity.this.startActivity(intent);
            }
        });
        mListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                switch (scrollState) {
                    //停止滚动
                    case AbsListView.OnScrollListener.SCROLL_STATE_IDLE: {
                        mLocalServerVideoListAdapter.setScrollState(false);

                    }
                        break;
                    //滚动做出了抛的动作
                    case AbsListView.OnScrollListener.SCROLL_STATE_FLING: {
                        mLocalServerVideoListAdapter.setScrollState(true);

                    }
                        break;
                    //正在滚动
                    case AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL: {
                        mLocalServerVideoListAdapter.setScrollState(true);

                    }
                        break;
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (totalItemCount <= 1) {
                    return;
                }
                if (visibleItemCount > 0) {
                    mLocalServerVideoListAdapter.setVisibleItem(firstVisibleItem, visibleItemCount);
                }
                onScrollCheck(view, totalItemCount, mLocalServerVideoListAdapter.getCount());
            }

            protected void onScrollCheck(AbsListView view, int totalItemCount, int dataCount) {

                int headCount = ((ListView) view).getHeaderViewsCount();
                int footerCount = ((ListView) view).getFooterViewsCount();

                if (mLocalServerVideoListAdapter != null && totalItemCount > (headCount + footerCount)) {
                    int position = mLocalServerVideoListAdapter.getCurIndex();
                    if (position != -1) {
                        int startIndex = view.getFirstVisiblePosition() - headCount;
                        int endIndex = view.getLastVisiblePosition() - headCount;
                        if (view.getLastVisiblePosition() > dataCount + headCount - 1) {
                            endIndex = dataCount - 1;
                        }
                        if (position < startIndex || position > endIndex) {
                            mLocalServerVideoListAdapter.stopPlay();
                        }
                    }
                }
            }
        });

        if (checkSelfPermissionAndRequest(Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE)) {
            initLocalServer();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!mInstanceStateSaved) {
            mImageLoader.stop();
        }
        unInitLocalServer();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        mInstanceStateSaved = true;
    }

    public boolean checkSelfPermissionAndRequest(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] {
                            permission
                    },
                    requestCode);
            return false;
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initLocalServer();
                } else {
                    Toast.makeText(this, "您拒绝了SD卡存储权限！", Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.header_left_icon: {
                playerStop();
                finish();
                break;
            }
            case R.id.header_right_icon: {
                Intent intent = new Intent(this, LocalServerSettingActivity.class);
                startActivity(intent);
                break;
            }
        }
    }

    private void initLocalServer() {
        if (!mInitLocalServer.get()) {
            mInitLocalServer.set(true);

            QHVCSdkConfig qhvcSdkConfig = QHVCSdk.getInstance().getConfig();

            LocalServer.setLogLevel(LocalServer.LOG_DEBUG);
            boolean ret = LocalServer.initialize(this, DirUtils.getLocalServerCacheDir(), qhvcSdkConfig.getMachineId(), qhvcSdkConfig.getBusinessId());
            if (!ret) {
                Logger.e("LocalServer", "LocalServer初始化失败！");
                Toast.makeText(this, "LocalServer初始化失败！", Toast.LENGTH_SHORT).show();
            }
            LocalServer.setCacheSize(50);
        }
    }

    private void unInitLocalServer() {
        if (mInitLocalServer.get()) {
            mInitLocalServer.set(false);
            LocalServer.destroy();
        }
    }

    @Override
    public void onBackPressed() {
        Logger.d("LocalServer", "onBackPressed()");
        playerStop();
        super.onBackPressed();
    }

    private void playerStop() {
        if (mLocalServerVideoListAdapter != null) {
            mLocalServerVideoListAdapter.stopPlay();
            mLocalServerVideoListAdapter.cancelPrecacheAll();
        }
    }
}
