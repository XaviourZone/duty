package com.xaviourzone.powerface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** One watch face: a background plus a list of elements placed on it. */
public class Face {

    public String id;
    public String name;
    public int bgColor = 0xFF000000;
    /** Absolute path of a photo copied into the app's private storage, or null. */
    public String bgImage;
    /** How much the photo is darkened, 0..90 (higher = darker = less battery on AMOLED). */
    public int bgDim = 45;
    public List<Element> elements = new ArrayList<>();

    /** One thing drawn on the face. Position and size are fractions of the face width. */
    public static class Element {
        public static final String TIME = "time";
        public static final String DATE = "date";
        public static final String BATTERY = "battery";
        public static final String TEMP = "temp";
        public static final String STEPS = "steps";
        public static final String TEXT = "text";
        public static final String ANALOG = "analog";

        public String type = TIME;
        /** Centre of the element, 0..1 across and down the face. */
        public float x = 0.5f;
        public float y = 0.5f;
        /** Text height, or ring / dial radius, as a fraction of the face width. */
        public float size = 0.1f;
        public int color = 0xFFFFFFFF;
        /** Second colour for a gradient; 0 means a single colour. */
        public int color2 = 0;
        /** 0 = thin, 1 = regular, 2 = bold. */
        public int font = 1;
        /** Battery and steps can be drawn as a progress ring instead of text. */
        public boolean ring = false;
        public boolean h24 = true;
        public String text = "";

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("type", type);
            o.put("x", (double) x);
            o.put("y", (double) y);
            o.put("size", (double) size);
            o.put("color", color);
            o.put("color2", color2);
            o.put("font", font);
            o.put("ring", ring);
            o.put("h24", h24);
            o.put("text", text);
            return o;
        }

        static Element fromJson(JSONObject o) {
            Element e = new Element();
            e.type = o.optString("type", TIME);
            e.x = (float) o.optDouble("x", 0.5);
            e.y = (float) o.optDouble("y", 0.5);
            e.size = (float) o.optDouble("size", 0.1);
            e.color = o.optInt("color", 0xFFFFFFFF);
            e.color2 = o.optInt("color2", 0);
            e.font = o.optInt("font", 1);
            e.ring = o.optBoolean("ring", false);
            e.h24 = o.optBoolean("h24", true);
            e.text = o.optString("text", "");
            return e;
        }

        public Element copy() {
            Element e = new Element();
            e.type = type; e.x = x; e.y = y; e.size = size; e.color = color; e.color2 = color2;
            e.font = font; e.ring = ring; e.h24 = h24; e.text = text;
            return e;
        }
    }

    public static Element el(String type, float x, float y, float size, int color) {
        Element e = new Element();
        e.type = type; e.x = x; e.y = y; e.size = size; e.color = color;
        return e;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("bgColor", bgColor);
        o.put("bgImage", bgImage == null ? "" : bgImage);
        o.put("bgDim", bgDim);
        JSONArray arr = new JSONArray();
        for (Element e : elements) arr.put(e.toJson());
        o.put("elements", arr);
        return o;
    }

    public static Face fromJson(JSONObject o) {
        Face f = new Face();
        f.id = o.optString("id", FaceStore.newId());
        f.name = o.optString("name", "Face");
        f.bgColor = o.optInt("bgColor", 0xFF000000);
        String img = o.optString("bgImage", "");
        f.bgImage = img.isEmpty() ? null : img;
        f.bgDim = o.optInt("bgDim", 45);
        JSONArray arr = o.optJSONArray("elements");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject eo = arr.optJSONObject(i);
                if (eo != null) f.elements.add(Element.fromJson(eo));
            }
        }
        return f;
    }

    /** Deep copy with a new id (used by Duplicate and by the editor's working copy). */
    public Face copy(String newId) {
        Face f = new Face();
        f.id = newId;
        f.name = name;
        f.bgColor = bgColor;
        f.bgImage = bgImage;
        f.bgDim = bgDim;
        for (Element e : elements) f.elements.add(e.copy());
        return f;
    }
}
