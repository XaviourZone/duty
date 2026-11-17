package com.xaviourzone.powerface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** One watch face: a background plus a list of elements (layers) drawn in order. */
public class Face {

    public static final String BG_SOLID = "solid";
    public static final String BG_LINEAR = "linear";
    public static final String BG_RADIAL = "radial";

    public String id;
    public String name;
    /** Gallery family for built-in designs ("Giant", "Orbit"…); "Mine" for your own. */
    public String category = "Mine";
    public String bgType = BG_SOLID;
    public int bgColor = 0xFF000000;
    public int bgColor2 = 0xFF000000;
    /** Absolute path of a photo in the app's private storage, or null. */
    public String bgImage;
    /** How much the photo is darkened, 0..90. */
    public int bgDim = 45;
    public List<Element> elements = new ArrayList<>();

    public boolean isBuiltIn() { return id != null && id.startsWith("d_"); }

    /** One layer. Positions and sizes are fractions of the face width (face is square). */
    public static class Element {
        // Text-like
        public static final String TIME = "time";
        public static final String HOUR = "hour";
        public static final String MINUTE = "minute";
        public static final String AMPM = "ampm";
        public static final String DATE = "date";
        public static final String BATTERY = "battery";
        public static final String STEPS = "steps";
        public static final String TEMP = "temp";
        public static final String TEXT = "text";
        // Graphics
        public static final String ANALOG = "analog";
        public static final String SHAPE = "shape";

        // Variants
        public static final String V_TEXT = "text";
        public static final String V_RING = "ring";
        public static final String V_BAR = "bar";
        public static final String V_ARC = "arc";
        public static final String A_CLASSIC = "classic";
        public static final String A_MINIMAL = "minimal";
        public static final String A_NUMBERS = "numbers";
        public static final String S_CIRCLE = "circle";
        public static final String S_RING = "ring";
        public static final String S_BOX = "box";
        public static final String S_FRAME = "frame";
        public static final String S_LINE = "line";
        public static final String S_ARC = "arc";

        public String type = TIME;
        public String variant = V_TEXT;
        /** Date pattern for DATE, e.g. "EEE, d MMM". */
        public String fmt = "";
        public String text = "";
        public float x = 0.5f, y = 0.5f;
        /** Text height, or radius for rings / dials / circles, or corner radius for boxes. */
        public float size = 0.1f;
        /** Width / height for bars, boxes, frames, lines. */
        public float w = 0.5f, h = 0.04f;
        /** Stroke width for rings, frames, arcs. */
        public float stroke = 0.012f;
        /** Arc length in degrees for arc shapes. */
        public float sweep = 270f;
        public int color = 0xFFFFFFFF;
        /** Gradient end colour; 0 = none. For analog dials: minute-hand colour. */
        public int color2 = 0;
        /** Glow colour; 0 = same as color. */
        public int glowColor = 0;
        /** Glow radius as a fraction of the face width (0 = off). */
        public float glow = 0f;
        public int alpha = 255;
        /** 0 thin, 1 light, 2 regular, 3 medium, 4 bold, 5 black, 6 condensed, 7 mono, 8 serif. */
        public int font = 1;
        public boolean italic, outline, upper;
        public float spacing = 0f;
        public boolean h24 = true;

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("type", type); o.put("variant", variant); o.put("fmt", fmt); o.put("text", text);
            o.put("x", (double) x); o.put("y", (double) y); o.put("size", (double) size);
            o.put("w", (double) w); o.put("h", (double) h); o.put("stroke", (double) stroke);
            o.put("sweep", (double) sweep);
            o.put("color", color); o.put("color2", color2); o.put("glowColor", glowColor);
            o.put("glow", (double) glow); o.put("alpha", alpha); o.put("font", font);
            o.put("italic", italic); o.put("outline", outline); o.put("upper", upper);
            o.put("spacing", (double) spacing); o.put("h24", h24);
            return o;
        }

        static Element fromJson(JSONObject o) {
            Element e = new Element();
            e.type = o.optString("type", TIME);
            e.variant = o.optString("variant", o.optBoolean("ring", false) ? V_RING : V_TEXT);
            e.fmt = o.optString("fmt", "");
            e.text = o.optString("text", "");
            e.x = (float) o.optDouble("x", 0.5); e.y = (float) o.optDouble("y", 0.5);
            e.size = (float) o.optDouble("size", 0.1);
            e.w = (float) o.optDouble("w", 0.5); e.h = (float) o.optDouble("h", 0.04);
            e.stroke = (float) o.optDouble("stroke", 0.012);
            e.sweep = (float) o.optDouble("sweep", 270);
            e.color = o.optInt("color", 0xFFFFFFFF); e.color2 = o.optInt("color2", 0);
            e.glowColor = o.optInt("glowColor", 0);
            e.glow = (float) o.optDouble("glow", 0);
            e.alpha = o.optInt("alpha", 255);
            int font = o.optInt("font", 1);
            // v1 used 0 thin, 1 light, 2 bold
            if (!o.has("variant") && font == 2) font = 4;
            e.font = font;
            e.italic = o.optBoolean("italic", false);
            e.outline = o.optBoolean("outline", false);
            e.upper = o.optBoolean("upper", false);
            e.spacing = (float) o.optDouble("spacing", 0);
            e.h24 = o.optBoolean("h24", true);
            return e;
        }

        public Element copy() {
            try {
                return fromJson(toJson());
            } catch (JSONException ex) {
                return new Element();
            }
        }
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("category", category);
        o.put("bgType", bgType);
        o.put("bgColor", bgColor);
        o.put("bgColor2", bgColor2);
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
        f.category = o.optString("category", "Mine");
        f.bgType = o.optString("bgType", BG_SOLID);
        f.bgColor = o.optInt("bgColor", 0xFF000000);
        f.bgColor2 = o.optInt("bgColor2", f.bgColor);
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

    /** Deep copy with a new id. */
    public Face copy(String newId) {
        try {
            Face f = fromJson(toJson());
            f.id = newId;
            return f;
        } catch (JSONException e) {
            Face f = new Face();
            f.id = newId;
            return f;
        }
    }
}
