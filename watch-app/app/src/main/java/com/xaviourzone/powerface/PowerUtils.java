package com.xaviourzone.powerface;

import android.Manifest;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Battery, steps, weather and the power-saving actions. */
final class PowerUtils {

    private static final long WEATHER_EVERY_MS = 60L * 60L * 1000L;

    private PowerUtils() {}

    /** Reads the sticky battery broadcast into the view. */
    static void applyBattery(FaceView fv, Intent i) {
        if (i == null) return;
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        if (level >= 0 && scale > 0) fv.battery = Math.round(level * 100f / scale);
        fv.charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
    }

    /** Fills a preview with the latest known values (no sensors started). */
    static void fillPreview(Context c, FaceView fv) {
        applyBattery(fv, c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)));
        SharedPreferences sp = FaceStore.prefs(c);
        fv.temp = sp.getFloat("temp", Float.NaN);
        fv.fahrenheit = sp.getBoolean(FaceStore.K_FAHRENHEIT, false);
        fv.stepGoal = sp.getInt(FaceStore.K_STEP_GOAL, 8000);
        fv.steps = today().equals(sp.getString("stepDay", "")) ? (int) sp.getLong("stepLast", 0) : 0;
    }

    static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    /**
     * The step sensor counts since the last reboot. Keep a per-day baseline so the
     * face shows today's steps, and survive reboots by carrying the last value.
     */
    static int todaySteps(Context c, long totalSinceBoot) {
        SharedPreferences sp = FaceStore.prefs(c);
        String today = today();
        long base;
        if (!today.equals(sp.getString("stepDay", "")) || !sp.contains("stepBase")) {
            base = totalSinceBoot;
        } else {
            base = sp.getLong("stepBase", totalSinceBoot);
            if (totalSinceBoot < base) {
                // Rebooted: the counter restarted from zero.
                base = totalSinceBoot - sp.getLong("stepLast", 0);
            }
        }
        long steps = Math.max(0, totalSinceBoot - base);
        sp.edit().putString("stepDay", today).putLong("stepBase", base).putLong("stepLast", steps).apply();
        return (int) steps;
    }

    /** Stops cached background processes of every other app. Run off the main thread. */
    static int killBackground(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        PackageManager pm = c.getPackageManager();
        if (am == null) return 0;
        int n = 0;
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (ApplicationInfo ai : apps) {
            if (ai.packageName.equals(c.getPackageName())) continue;
            try {
                am.killBackgroundProcesses(ai.packageName);
                n++;
            } catch (Exception ignored) {
            }
        }
        return n;
    }

    /** Returns true if Wi-Fi was on and is now being switched off. */
    @SuppressWarnings("deprecation")
    static boolean wifiOff(Context c) {
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.isWifiEnabled()) return wm.setWifiEnabled(false);
        } catch (Exception ignored) {
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    static void wifiOn(Context c) {
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) wm.setWifiEnabled(true);
        } catch (Exception ignored) {
        }
    }

    /** Returns true if Bluetooth was on and is now being switched off. */
    @SuppressWarnings({"deprecation", "MissingPermission"})
    static boolean bluetoothOff() {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a != null && a.isEnabled()) return a.disable();
        } catch (Exception ignored) {
        }
        return false;
    }

    @SuppressWarnings({"deprecation", "MissingPermission"})
    static void bluetoothOn() {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a != null) a.enable();
        } catch (Exception ignored) {
        }
    }

    static boolean hasLocationPermission(Context c) {
        if (Build.VERSION.SDK_INT < 23) return true;
        return c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Updates the cached temperature from Open-Meteo (free, no key) at most once an hour.
     * Blocking: call from a background thread. Returns the cached value either way.
     */
    @SuppressWarnings("MissingPermission")
    static float updateWeather(Context c, boolean force) {
        SharedPreferences sp = FaceStore.prefs(c);
        float cached = sp.getFloat("temp", Float.NaN);
        long last = sp.getLong("tempTime", 0);
        if (!force && System.currentTimeMillis() - last < WEATHER_EVERY_MS) return cached;
        if (!hasLocationPermission(c)) return cached;

        Location best = null;
        try {
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                for (String p : new String[]{LocationManager.NETWORK_PROVIDER,
                        LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
                    try {
                        Location l = lm.getLastKnownLocation(p);
                        if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (best != null) {
            sp.edit().putFloat("lat", (float) best.getLatitude()).putFloat("lon", (float) best.getLongitude()).apply();
        }
        if (!sp.contains("lat")) return cached;

        HttpURLConnection conn = null;
        try {
            String url = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m",
                    sp.getFloat("lat", 0f), sp.getFloat("lon", 0f));
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            if (conn.getResponseCode() != 200) return cached;
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            float t = (float) new JSONObject(sb.toString()).getJSONObject("current").getDouble("temperature_2m");
            sp.edit().putFloat("temp", t).putLong("tempTime", System.currentTimeMillis()).apply();
            return t;
        } catch (Exception e) {
            return cached;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
