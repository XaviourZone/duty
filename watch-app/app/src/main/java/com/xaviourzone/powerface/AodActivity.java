package com.xaviourzone.powerface;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

/**
 * Power mode: shows only the selected face, dim, redrawn once a minute.
 * Closes background apps and (optionally) turns Wi-Fi / Bluetooth off until you exit.
 * Long-press anywhere to exit.
 */
public class AodActivity extends Activity implements SensorEventListener {

    private static final long BOOST_MS = 5000;

    private FaceView fv;
    private SharedPreferences sp;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private GestureDetector gestures;
    private SensorManager sensors;
    private Sensor stepSensor;

    private volatile boolean turnedWifiOff, turnedBtOff;
    private boolean pinned, restored, receiving;
    private int shiftStep;

    private final Runnable dimAgain = () -> setBrightness(baseBrightness());

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(a)) {
                PowerUtils.applyBattery(fv, i);
                fv.invalidate();
            } else {
                // TIME_TICK once per minute (or time/zone changed): move pixels a bit, redraw.
                burnInShift();
                maybeUpdateWeather();
            }
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = FaceStore.prefs(this);

        if (sp.getBoolean(FaceStore.K_KEEP_ON, true)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        //noinspection deprecation
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        fv = new FaceView(this);
        fv.setFace(FaceStore.current(this));
        PowerUtils.fillPreview(this, fv);
        root.addView(fv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        setContentView(root);

        setBrightness(baseBrightness());

        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) { boost(); return true; }
            @Override public void onLongPress(MotionEvent e) { confirmExit(); }
        });

        sensors = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensors != null) stepSensor = sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        startPowerSaving();
        Toast.makeText(this, "Power mode on. Long-press to exit.", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
        fv.setFace(FaceStore.current(this));
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(receiver, f);
        receiving = true;
        if (stepSensor != null) {
            // Batch step updates for up to a minute: the sensor hub does the work, the CPU sleeps.
            if (Build.VERSION.SDK_INT >= 19) {
                sensors.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL, 60_000_000);
            } else {
                sensors.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
        fv.invalidate();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (receiving) {
            unregisterReceiver(receiver);
            receiving = false;
        }
        if (sensors != null) sensors.unregisterListener(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.immersive(this);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        gestures.onTouchEvent(ev);
        return true;
    }

    @Override
    public void onBackPressed() {
        Toast.makeText(this, "Long-press the face to exit power mode", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        restore();
        super.onDestroy();
    }

    // ----- power saving -----

    private void startPowerSaving() {
        if (sp.getBoolean(FaceStore.K_PIN, false) && Build.VERSION.SDK_INT >= 21) {
            try {
                startLockTask();
                pinned = true;
            } catch (Exception ignored) {
            }
        }
        final boolean kill = sp.getBoolean(FaceStore.K_KILL_APPS, true);
        final boolean wifi = sp.getBoolean(FaceStore.K_WIFI_OFF, false);
        final boolean bt = sp.getBoolean(FaceStore.K_BT_OFF, false);
        final Context app = getApplicationContext();
        new Thread(() -> {
            // Grab fresh weather first, while the network is still on.
            final float t = PowerUtils.updateWeather(app, false);
            handler.post(() -> { fv.temp = t; fv.invalidate(); });
            if (kill) PowerUtils.killBackground(app);
            if (wifi) turnedWifiOff = PowerUtils.wifiOff(app);
            if (bt) turnedBtOff = PowerUtils.bluetoothOff();
        }).start();
    }

    private void restore() {
        if (restored) return;
        restored = true;
        final Context app = getApplicationContext();
        if (turnedWifiOff) PowerUtils.wifiOn(app);
        if (turnedBtOff) PowerUtils.bluetoothOn();
        if (pinned && Build.VERSION.SDK_INT >= 21) {
            try {
                stopLockTask();
            } catch (Exception ignored) {
            }
        }
    }

    private void confirmExit() {
        setBrightness(0.6f);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setMessage("Exit power mode?")
                .setPositiveButton("Exit", (d, w) -> { restore(); finish(); })
                .setNegativeButton("Stay", (d, w) -> setBrightness(baseBrightness()))
                .setOnCancelListener(d -> setBrightness(baseBrightness()))
                .show();
    }

    // ----- screen -----

    private float baseBrightness() {
        return Math.max(0.01f, sp.getInt(FaceStore.K_BRIGHTNESS, 15) / 100f);
    }

    private void setBrightness(float v) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = v;
        getWindow().setAttributes(lp);
    }

    private void boost() {
        if (!sp.getBoolean(FaceStore.K_TAP_WAKE, true)) return;
        setBrightness(Math.max(0.6f, baseBrightness()));
        handler.removeCallbacks(dimAgain);
        handler.postDelayed(dimAgain, BOOST_MS);
    }

    /** Moves the face a few pixels each minute so the AMOLED does not burn in. */
    private void burnInShift() {
        int[][] pattern = {{0, 0}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
        int[] p = pattern[shiftStep++ % pattern.length];
        float d = 3 * getResources().getDisplayMetrics().density;
        fv.setShift(p[0] * d, p[1] * d);
    }

    private void maybeUpdateWeather() {
        // Only wake the network once an hour.
        if (System.currentTimeMillis() - sp.getLong("tempTime", 0) < 60L * 60L * 1000L) return;
        final Context app = getApplicationContext();
        new Thread(() -> {
            final float t = PowerUtils.updateWeather(app, false);
            handler.post(() -> { fv.temp = t; fv.invalidate(); });
        }).start();
    }

    // ----- steps -----

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() == Sensor.TYPE_STEP_COUNTER && e.values.length > 0) {
            fv.steps = PowerUtils.todaySteps(this, (long) e.values[0]);
            fv.invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor s, int accuracy) {}
}
