
package com.qihoo.videocloud.localserver.setting;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;

import com.qihoo.livecloudrefactor.R;

import net.qihoo.videocloud.LocalServer;

/**
 * Created by LeiXiaojun on 2017/8/21.
 */

public class LocalServerSettingActivity extends Activity implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.localserver_setting_activity);

        initView();
    }

    private void initView() {
        Switch togglePrecacheInMobile = (Switch) findViewById(R.id.toggle_precache_in_mobile);
        togglePrecacheInMobile.setChecked(LocalServer.isEnablePrecacheInMobileNetwork());
        togglePrecacheInMobile.setOnCheckedChangeListener(this);

        Switch toggleEnableCache = (Switch) findViewById(R.id.toggle_enable_cache);
        toggleEnableCache.setChecked(LocalServerSettingConfig.ENABLE_CACHE_WHEN_PAUSE);
        toggleEnableCache.setOnCheckedChangeListener(this);

        findViewById(R.id.clear_cache).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.clear_cache: {
                LocalServer.clearCache();
                break;
            }
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        switch (compoundButton.getId()) {
            case R.id.toggle_precache_in_mobile: {
                LocalServer.enablePrecacheInMobileNetwork(isChecked);
                break;
            }
            case R.id.toggle_enable_cache: {
                LocalServerSettingConfig.ENABLE_CACHE_WHEN_PAUSE = isChecked;
                break;
            }
        }
    }
}
