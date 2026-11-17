package com.xaviourzone.powerface;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Start screen: preview of the current face + Apply / Customize / Create new. */
public class MainActivity extends Activity {

    private FaceView preview;
    private TextView faceName;
    private TextView report;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Ui.screenBg());
        scroll.setFillViewport(true);
        LinearLayout col = Ui.column(this, 18);
        col.setGravity(Gravity.CENTER);
        scroll.addView(col);

        TextView title = Ui.text(this, "PowerFace", 22, 0xFFFFFFFF, true);
        col.addView(title);
        col.addView(Ui.text(this, "Battery-saving watch faces", 13, Ui.GREY, false));

        // Glass card with live preview of the selected face
        LinearLayout card = Ui.column(this, 10);
        card.setBackground(Ui.glass(this, 28));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = Ui.dp(this, 14);
        col.addView(card, cardLp);

        int side = Math.min((int) (Ui.screenSide(this) * 0.62f), Ui.dp(this, 260));
        preview = new FaceView(this);
        card.addView(preview, new LinearLayout.LayoutParams(side, side));
        faceName = Ui.text(this, "", 15, 0xFFFFFFFF, true);
        faceName.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(faceName);

        Button apply = Ui.button(this, "⚡  Apply", true);
        apply.setOnClickListener(v -> startActivity(new Intent(this, AodActivity.class)));
        col.addView(apply);

        Button gallery = Ui.button(this, "Faces gallery (100+)", false);
        gallery.setOnClickListener(v -> startActivity(new Intent(this, FacesActivity.class)));
        col.addView(gallery);

        Button customize = Ui.button(this, "Customize this face", false);
        customize.setOnClickListener(v -> {
            Intent i = new Intent(this, EditorActivity.class);
            i.putExtra("id", FaceStore.current(this).id);
            startActivity(i);
        });
        col.addView(customize);

        Button create = Ui.button(this, "+  Create new face", false);
        create.setOnClickListener(v -> startActivity(new Intent(this, EditorActivity.class)));
        col.addView(create);

        Button settings = Ui.button(this, "Power settings", false);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        col.addView(settings);

        report = Ui.text(this, "", 12, Ui.GREY, false);
        report.setGravity(Gravity.CENTER);
        report.setPadding(Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6), 0);
        col.addView(report, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(scroll);
        askPermissionsOnce();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Face f = FaceStore.current(this);
        preview.setFace(f);
        PowerUtils.fillPreview(this, preview);
        preview.invalidate();
        faceName.setText(f.name);
        report.setText(PowerUtils.lastSessionReport(this));
        // Refresh temperature in the background if it is older than an hour.
        new Thread(() -> {
            final float t = PowerUtils.updateWeather(getApplicationContext(), false);
            runOnUiThread(() -> { preview.temp = t; preview.invalidate(); });
        }).start();
    }

    private void askPermissionsOnce() {
        if (Build.VERSION.SDK_INT < 23) return;
        SharedPreferences sp = FaceStore.prefs(this);
        if (sp.getBoolean("askedPerms", false)) return;
        sp.edit().putBoolean("askedPerms", true).apply();
        requestPermissions(new String[]{
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION}, 1);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        new Thread(() -> {
            final float t = PowerUtils.updateWeather(getApplicationContext(), true);
            runOnUiThread(() -> { preview.temp = t; preview.invalidate(); });
        }).start();
    }
}
