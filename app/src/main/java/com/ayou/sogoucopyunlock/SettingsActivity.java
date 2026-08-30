package com.ayou.sogoucopyunlock;

import android.app.Activity;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Switch;

public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        bindSwitch(R.id.switch_copy_limit, Settings.KEY_COPY_LIMIT);
        bindSwitch(R.id.switch_toolbar, Settings.KEY_TOOLBAR);
        bindSwitch(R.id.switch_phrase_length, Settings.KEY_PHRASE_LENGTH);
        bindSwitch(R.id.switch_clipboard_move, Settings.KEY_CLIPBOARD_MOVE);
        bindSwitch(R.id.switch_clipboard_history, Settings.KEY_CLIPBOARD_HISTORY);
        bindSwitch(R.id.switch_debug, Settings.KEY_DEBUG);
    }

    private void bindSwitch(int viewId, final String key) {
        final Switch sw = findViewById(viewId);
        boolean defaultValue = !Settings.KEY_DEBUG.equals(key);
        sw.setChecked(Settings.getValue(this, key, defaultValue));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Settings.setValue(SettingsActivity.this, key, isChecked);
            }
        });
    }
}
