package com.xaviourzone.powerface;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Your own faces (SharedPreferences) + the 100 built-in designs + favourites + settings. */
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
    public static final String K_SWIPE = "swipeFaces";

    private FaceStore() {}

    public static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String newId() {
        return "f" + System.currentTimeMillis();
    }

    /** Only your own faces. */
    public static List<Face> mine(Context c) {
        SharedPreferences sp = prefs(c);
        String raw = sp.getString("faces", null);
        List<Face> list = new ArrayList<>();
        if (raw != null) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    if (arr.optJSONObject(i) == null) continue;
                    Face f = Face.fromJson(arr.getJSONObject(i));
                    // v1 shipped 5 basic presets; v2 replaces them with the design gallery.
                    if (f.id.startsWith("preset")) continue;
                    f.category = "Mine";
                    list.add(f);
                }
            } catch (JSONException ignored) {
            }
        }
        return list;
    }

    /** Your faces first, then the 100 designs. */
    public static List<Face> all(Context c) {
        List<Face> list = mine(c);
        list.addAll(Designs.all());
        return list;
    }

    private static void saveMine(Context c, List<Face> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Face f : list) arr.put(f.toJson());
        } catch (JSONException ignored) {
        }
        prefs(c).edit().putString("faces", arr.toString()).apply();
    }

    public static Face find(Context c, String id) {
        if (id == null) return null;
        if (id.startsWith("d_")) return Designs.byId(id);
        for (Face f : mine(c)) if (id.equals(f.id)) return f;
        return null;
    }

    public static Face current(Context c) {
        Face f = find(c, prefs(c).getString("current", null));
        return f != null ? f : Designs.all().get(0);
    }

    public static void setCurrent(Context c, String id) {
        prefs(c).edit().putString("current", id).apply();
    }

    /** Insert or replace one of your faces. Built-in designs are never overwritten. */
    public static void upsert(Context c, Face face) {
        if (face.isBuiltIn()) face.id = newId();
        face.category = "Mine";
        List<Face> list = mine(c);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(face.id)) {
                list.set(i, face);
                saveMine(c, list);
                return;
            }
        }
        list.add(0, face);
        saveMine(c, list);
    }

    public static void delete(Context c, String id) {
        List<Face> list = mine(c);
        Face gone = null;
        for (Face f : list) if (f.id.equals(id)) gone = f;
        if (gone == null) return;
        list.remove(gone);
        saveMine(c, list);
        if (gone.bgImage != null) {
            boolean used = false;
            for (Face f : list) if (gone.bgImage.equals(f.bgImage)) used = true;
            if (!used) {
                //noinspection ResultOfMethodCallIgnored
                new File(gone.bgImage).delete();
            }
        }
        setFavourite(c, id, false);
        if (id.equals(prefs(c).getString("current", null))) setCurrent(c, Designs.all().get(0).id);
    }

    // ----- favourites -----

    public static Set<String> favourites(Context c) {
        return new HashSet<>(prefs(c).getStringSet("favs", new HashSet<String>()));
    }

    public static boolean isFavourite(Context c, String id) {
        return favourites(c).contains(id);
    }

    public static void setFavourite(Context c, String id, boolean fav) {
        Set<String> s = favourites(c);
        if (fav) s.add(id); else s.remove(id);
        prefs(c).edit().putStringSet("favs", s).apply();
    }

    /** Faces you can swipe through in power mode: favourites + your own, or everything. */
    public static List<Face> swipeList(Context c) {
        Set<String> favs = favourites(c);
        List<Face> list = new ArrayList<>();
        for (Face f : all(c)) if (favs.contains(f.id) || !f.isBuiltIn()) list.add(f);
        if (list.size() < 2) list = all(c);
        return list;
    }
}
