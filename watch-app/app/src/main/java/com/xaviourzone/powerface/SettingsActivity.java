package com.xaviourzone.powerface;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** What power mode does when a face is applied. */
public class SettingsActivity extends Activity {

    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = FaceStore.prefs(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Ui.screenBg());
        LinearLayout col = Ui.column(this, 16);
        scroll.addView(col);
        col.addView(Ui.text(this, "Power settings", 20, 0xFFFFFFFF, true));

        LinearLayout card = Ui.column(this, 14);
        card.setBackground(Ui.glass(this, 24));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 12);
        col.addView(card, lp);

        toggle(card, FaceStore.K_KILL_APPS, "Close background apps", true);
        toggle(card, FaceStore.K_WIFI_OFF, "Turn Wi-Fi off (back on when you exit)", false);
        toggle(card, FaceStore.K_BT_OFF, "Turn Bluetooth off (back on when you exit)", false);
        toggle(card, FaceStore.K_PIN, "Lock to the face (screen pinning)", false);
        toggle(card, FaceStore.K_KEEP_ON, "Keep screen always on", true);
        toggle(card, FaceStore.K_TAP_WAKE, "Tap to brighten for 5 seconds", true);
        toggle(card, FaceStore.K_SWIPE, "Swipe to change face in power mode", true);
        toggle(card, FaceStore.K_FAHRENHEIT, "Temperature in °F", false);

        // Brightness used while the face is shown
        final TextView bLabel = Ui.text(this, "", 14, 0xFFFFFFFF, false);
        bLabel.setPadding(0, Ui.dp(this, 14), 0, 0);
        card.addView(bLabel, fullWidth());
        SeekBar bright = new SeekBar(this);
        bright.setMax(100);
        int cur = sp.getInt(FaceStore.K_BRIGHTNESS, 15);
        bright.setProgress(cur);
        bLabel.setText("Face brightness: " + cur + "%");
        bright.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                int v = Math.max(1, p);
                bLabel.setText("Face brightness: " + v + "%");
                sp.edit().putInt(FaceStore.K_BRIGHTNESS, v).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        card.addView(bright, fullWidth());

        // Daily step goal for the steps ring
        final TextView gLabel = Ui.text(this, "", 14, 0xFFFFFFFF, false);
        gLabel.setPadding(0, Ui.dp(this, 14), 0, 0);
        card.addView(gLabel, fullWidth());
        SeekBar goal = new SeekBar(this);
        goal.setMax(30); // x 1000 steps
        int g = sp.getInt(FaceStore.K_STEP_GOAL, 8000) / 1000;
        goal.setProgress(g);
        gLabel.setText("Step goal: " + (g * 1000));
        goal.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                int v = Math.max(1, p) * 1000;
                gLabel.setText("Step goal: " + v);
                sp.edit().putInt(FaceStore.K_STEP_GOAL, v).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        card.addView(goal, fullWidth());

        TextView help = Ui.text(this,
                "In power mode: long-press the face to exit, swipe left/right to change face (favourites and your own faces).\n"
                        + "Tip: dark faces with few bright pixels use the least battery on the AMOLED screen.",
                12, Ui.GREY, false);
        help.setPadding(Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 4), 0);
        col.addView(help, fullWidth());

        setContentView(scroll);
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void toggle(LinearLayout parent, final String key, String label, boolean def) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(0xFFFFFFFF);
        s.setTextSize(14);
        s.setChecked(sp.getBoolean(key, def));
        s.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        s.setOnCheckedChangeListener((v, on) -> sp.edit().putBoolean(key, on).apply());
        parent.addView(s, fullWidth());
    }
}
