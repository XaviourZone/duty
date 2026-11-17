package com.xaviourzone.powerface;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Saves faces and settings in SharedPreferences. */
public final class FaceStore {

    public static final String PREFS = "powerface";

    // Settings keys
    public static final String K_KEEP_ON = "keepOn";
    public static final String K_KILL_APPS = "killApps";
    public static final String K_WIFI_OFF = "wifiOff";
    public static final String K_BT_OFF = "btOff";
    public static final String K_PIN = "pin";
    public static final String K_FAHRENHEIT = "fahrenheit";
    public static final String K_TAP_WAKE = "tapWake";
    public static final String K_BRIGHTNESS = "brightness";
    public static final String K_STEP_GOAL = "stepGoal";

    private FaceStore() {}

    public static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String newId() {
        return "f" + System.currentTimeMillis();
    }

    public static List<Face> load(Context c) {
        SharedPreferences sp = prefs(c);
        String raw = sp.getString("faces", null);
        List<Face> list = new ArrayList<>();
        if (raw != null) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    if (arr.optJSONObject(i) != null) list.add(Face.fromJson(arr.getJSONObject(i)));
                }
            } catch (JSONException ignored) {
            }
        }
        if (list.isEmpty()) {
            list = presets();
            save(c, list);
            sp.edit().putString("current", list.get(0).id).apply();
        }
        return list;
    }

    public static void save(Context c, List<Face> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Face f : list) arr.put(f.toJson());
        } catch (JSONException ignored) {
        }
        prefs(c).edit().putString("faces", arr.toString()).apply();
    }

    public static Face find(List<Face> list, String id) {
        if (id == null) return null;
        for (Face f : list) if (id.equals(f.id)) return f;
        return null;
    }

    public static Face current(Context c) {
        List<Face> list = load(c);
        Face f = find(list, prefs(c).getString("current", null));
        return f != null ? f : list.get(0);
    }

    public static void setCurrent(Context c, String id) {
        prefs(c).edit().putString("current", id).apply();
    }

    /** Insert a new face or replace the one with the same id. */
    public static void upsert(Context c, Face face) {
        List<Face> list = load(c);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(face.id)) {
                list.set(i, face);
                save(c, list);
                return;
            }
        }
        list.add(face);
        save(c, list);
    }

    public static void delete(Context c, String id) {
        List<Face> list = load(c);
        Face gone = find(list, id);
        if (gone == null || list.size() <= 1) return;
        list.remove(gone);
        save(c, list);
        // Remove the photo only if no other face still uses it.
        if (gone.bgImage != null) {
            boolean used = false;
            for (Face f : list) if (gone.bgImage.equals(f.bgImage)) used = true;
            if (!used) //noinspection ResultOfMethodCallIgnored
                new File(gone.bgImage).delete();
        }
        if (id.equals(prefs(c).getString("current", null))) setCurrent(c, list.get(0).id);
    }

    /** The 5 built-in faces. */
    static List<Face> presets() {
        List<Face> list = new ArrayList<>();
        final int WHITE = 0xFFFFFFFF;

        // 1. Neon Glass: thin gradient time, battery ring, temperature
        Face f = new Face();
        f.id = "preset1"; f.name = "Neon Glass";
        Face.Element t = Face.el(Face.Element.TIME, 0.5f, 0.42f, 0.30f, 0xFF4FC3F7);
        t.color2 = 0xFFB388FF; t.font = 0;
        f.elements.add(t);
        f.elements.add(Face.el(Face.Element.DATE, 0.5f, 0.64f, 0.065f, 0xFFB0BEC5));
        Face.Element b = Face.el(Face.Element.BATTERY, 0.25f, 0.84f, 0.09f, 0xFF7C4DFF);
        b.ring = true;
        f.elements.add(b);
        f.elements.add(Face.el(Face.Element.TEMP, 0.75f, 0.84f, 0.075f, 0xFF4FC3F7));
        list.add(f);

        // 2. Minimal: big thin white time, almost nothing lit
        f = new Face();
        f.id = "preset2"; f.name = "Minimal";
        t = Face.el(Face.Element.TIME, 0.5f, 0.48f, 0.27f, WHITE);
        t.font = 0;
        f.elements.add(t);
        f.elements.add(Face.el(Face.Element.DATE, 0.5f, 0.70f, 0.06f, 0xFF8E8E93));
        f.elements.add(Face.el(Face.Element.BATTERY, 0.5f, 0.13f, 0.05f, 0xFF636366));
        list.add(f);

        // 3. Analog Classic
        f = new Face();
        f.id = "preset3"; f.name = "Analog Classic";
        Face.Element a = Face.el(Face.Element.ANALOG, 0.5f, 0.5f, 0.44f, WHITE);
        a.color2 = 0xFF7C4DFF;
        f.elements.add(a);
        f.elements.add(Face.el(Face.Element.DATE, 0.5f, 0.70f, 0.05f, 0xFFB388FF));
        f.elements.add(Face.el(Face.Element.BATTERY, 0.5f, 0.30f, 0.045f, 0xFF8E8E93));
        list.add(f);

        // 4. Sport: bold gradient time, steps + battery rings
        f = new Face();
        f.id = "preset4"; f.name = "Sport";
        t = Face.el(Face.Element.TIME, 0.5f, 0.40f, 0.28f, 0xFFFF9800);
        t.color2 = 0xFFFF4081; t.font = 2;
        f.elements.add(t);
        f.elements.add(Face.el(Face.Element.DATE, 0.5f, 0.13f, 0.06f, WHITE));
        Face.Element s = Face.el(Face.Element.STEPS, 0.27f, 0.78f, 0.11f, 0xFF00E676);
        s.ring = true;
        f.elements.add(s);
        b = Face.el(Face.Element.BATTERY, 0.73f, 0.78f, 0.11f, 0xFFFF9800);
        b.ring = true;
        f.elements.add(b);
        list.add(f);

        // 5. Info Stack: everything at a glance
        f = new Face();
        f.id = "preset5"; f.name = "Info Stack";
        t = Face.el(Face.Element.TIME, 0.5f, 0.24f, 0.22f, WHITE);
        t.color2 = 0xFFB388FF;
        f.elements.add(t);
        f.elements.add(Face.el(Face.Element.DATE, 0.5f, 0.42f, 0.06f, 0xFF80CBC4));
        f.elements.add(Face.el(Face.Element.TEMP, 0.5f, 0.56f, 0.08f, 0xFF4FC3F7));
        f.elements.add(Face.el(Face.Element.STEPS, 0.5f, 0.70f, 0.06f, 0xFFB388FF));
        f.elements.add(Face.el(Face.Element.BATTERY, 0.5f, 0.84f, 0.06f, 0xFF69F0AE));
        list.add(f);

        return list;
    }
}
