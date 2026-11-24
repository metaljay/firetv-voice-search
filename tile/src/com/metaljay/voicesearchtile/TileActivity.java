package com.metaljay.voicesearchtile;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

/**
 * The app's one front door. When the add-on's app is there, it opens the Voice Search settings
 * screen and closes itself, showing nothing of its own (so pressing the tile feels like opening the
 * settings directly). When it isn't, it opens the setup screens instead. Both ways it closes at
 * once, which is why this activity is translucent and keeps no history.
 */
public class TileActivity extends Activity {

    static final String MAIN_PACKAGE = "com.metaljay.stremiovoice";
    private static final ComponentName SETTINGS = new ComponentName(
            MAIN_PACKAGE, "com.metaljay.stremiovoice.ChooserActivity");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent go = new Intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (mainAppPresent()) {
            go.setComponent(SETTINGS);
        } else {
            go.setComponent(new ComponentName(this, SetupActivity.class));
        }
        try {
            startActivity(go);
        } catch (RuntimeException e) {
            // The settings screen can't be opened (the add-on is half removed): offer the setup instead.
            startActivity(new Intent(this, SetupActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        }
        finish();
    }

    /** The add-on's app is installed. Needs no root: the package list says it. */
    private boolean mainAppPresent() {
        try {
            getPackageManager().getPackageInfo(MAIN_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
