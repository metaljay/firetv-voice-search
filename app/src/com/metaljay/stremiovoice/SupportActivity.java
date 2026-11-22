package com.metaljay.stremiovoice;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import io.nayuki.qrcodegen.QrCode;

/**
 * "Support Voice Search": a thank-you line and a QR code for the Buy Me a Coffee page, to scan with
 * a phone. Opened from the settings screen. Nothing is sent and nothing goes over the network:
 * the code is drawn from a fixed address.
 */
public class SupportActivity extends Activity {

    private static final String PAGE = "https://buymeacoffee.com/metaljay";
    private static final String SHORT = "buymeacoffee.com/metaljay";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ChooserActivity.BACKGROUND);
        root.setPadding(dp(96), dp(28), dp(96), dp(24));

        TextView title = text("Support Voice Search", 34, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView line = text("If Voice Search is useful to you, you can buy me a coffee. Thank you!", 20, ChooserActivity.GREY);
        line.setPadding(0, dp(10), 0, dp(14));
        root.addView(line);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row);
        try {
            row.addView(new QrView(this, QrCode.encodeText(PAGE, QrCode.Ecc.MEDIUM)),
                    new LinearLayout.LayoutParams(dp(300), dp(300)));
        } catch (RuntimeException e) {
            // The address is short and fixed, so this can't happen; the text below still works.
        }
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(dp(32), 0, 0, 0);
        row.addView(side, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        side.addView(text("Scan the code with your phone's camera, or go to:", 18, ChooserActivity.GREY));
        TextView address = text(SHORT, 26, Color.WHITE);
        address.setPadding(0, dp(8), 0, dp(20));
        side.addView(address);
        View done = button("Done", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        side.addView(done);
        done.requestFocus();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(ChooserActivity.BACKGROUND);
        scroll.addView(root);
        setContentView(scroll);
    }

    private View button(String label, View.OnClickListener onClick) {
        TextView b = text(label, 22, Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(48), dp(14), dp(48), dp(14));
        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(ChooserActivity.CARD);
        bg.setCornerRadius(dp(16));
        b.setBackground(bg);
        b.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        b.setFocusable(true);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                bg.setStroke(hasFocus ? dp(4) : 0, Color.WHITE);
                bg.setColor(hasFocus ? ChooserActivity.ACCENT : ChooserActivity.CARD);
                ((TextView) v).setTextColor(hasFocus ? ChooserActivity.BACKGROUND : Color.WHITE);
            }
        });
        b.setOnClickListener(onClick);
        return b;
    }

    private TextView text(String s, int sp, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        return v;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
