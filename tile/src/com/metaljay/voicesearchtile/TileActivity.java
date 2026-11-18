package com.metaljay.voicesearchtile;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Opens the Voice Search settings screen and closes itself. Shows nothing of
 * its own, so pressing the tile feels like opening the settings directly.
 */
public class TileActivity extends Activity {

    private static final ComponentName SETTINGS = new ComponentName(
            "com.metaljay.stremiovoice", "com.metaljay.stremiovoice.ChooserActivity");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            startActivity(new Intent().setComponent(SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        } catch (RuntimeException e) {
            // The Magisk add-on has been removed: this tile can be uninstalled too.
            Toast.makeText(this, "The Voice Search add-on isn't installed.", Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
