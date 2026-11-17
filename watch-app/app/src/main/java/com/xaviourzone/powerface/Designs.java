package com.xaviourzone.powerface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 100 built-in designs for the square 720x720 M99 screen:
 * 10 layouts x 10 glowing colour themes. Built in code, so they cost no storage.
 */
final class Designs {

    static final String[] LAYOUTS = {
            "Giant", "Orbit", "Rings", "Dashboard", "Minimal", "Split", "Neon Frame", "Sport", "Analog Pro", "Grid"};

    /** A colour theme. */
    static final class Theme {
        final String name;
        final int bg1, bg2, c1, c2, c3, text, dim;

        Theme(String name, int bg1, int bg2, int c1, int c2, int c3, int text, int dim) {
            this.name = name; this.bg1 = bg1; this.bg2 = bg2; this.c1 = c1; this.c2 = c2; this.c3 = c3;
            this.text = text; this.dim = dim;
        }
    }

    static final Theme[] THEMES = {
            new Theme("Cyber",   0xFF04010C, 0xFF16042E, 0xFF00E5FF, 0xFFFF2BD6, 0xFF7C4DFF, 0xFFFFFFFF, 0xFF8F86B8),
            new Theme("Aurora",  0xFF010C0B, 0xFF0B0424, 0xFF2BFFC6, 0xFF8A5CFF, 0xFF00B3FF, 0xFFFFFFFF, 0xFF7FA8A0),
            new Theme("Sunset",  0xFF120304, 0xFF2A0A20, 0xFFFF8A00, 0xFFFF2E63, 0xFFFFC857, 0xFFFFF4EC, 0xFFB08A80),
            new Theme("Ice",     0xFF000611, 0xFF00203F, 0xFFE8F6FF, 0xFF5AC8FA, 0xFF0A84FF, 0xFFFFFFFF, 0xFF7E97AE),
            new Theme("Lava",    0xFF0A0000, 0xFF240600, 0xFFFF3D00, 0xFFFFC400, 0xFFFF6E40, 0xFFFFF3E0, 0xFFB0806A),
            new Theme("Matrix",  0xFF000803, 0xFF00190B, 0xFF00FF88, 0xFFB2FF59, 0xFF1DE9B6, 0xFFE8FFF1, 0xFF6F9F80),
            new Theme("Royal",   0xFF07050E, 0xFF1C1033, 0xFFFFD166, 0xFFFF9F1C, 0xFFB388FF, 0xFFFFF8E7, 0xFFA89A7A),
            new Theme("Sakura",  0xFF0F050A, 0xFF1F0E26, 0xFFFF9EC4, 0xFFC3A6FF, 0xFFFFD1DC, 0xFFFFF5F8, 0xFFB09AA6),
            new Theme("Ocean",   0xFF00040E, 0xFF001738, 0xFF0091FF, 0xFF00F0FF, 0xFF64FFDA, 0xFFFFFFFF, 0xFF7690B0),
            new Theme("Mono",    0xFF000000, 0xFF111114, 0xFFFFFFFF, 0xFF9A9AA2, 0xFFD0D0D8, 0xFFFFFFFF, 0xFF6E6E76),
    };

    private static List<Face> cache;

    private Designs() {}

    static synchronized List<Face> all() {
        if (cache == null) {
            List<Face> list = new ArrayList<>();
            for (int l = 0; l < LAYOUTS.length; l++) {
                for (Theme t : THEMES) {
                    Face f = build(l, t);
                    f.id = "d_" + l + "_" + t.name.toLowerCase();
                    f.name = LAYOUTS[l] + " · " + t.name;
                    f.category = LAYOUTS[l];
                    list.add(f);
                }
            }
            cache = Collections.unmodifiableList(list);
        }
        return cache;
    }

    static Face byId(String id) {
        for (Face f : all()) if (f.id.equals(id)) return f;
        return null;
    }

    // ------------------------------------------------------------------ builders

    private static Face.Element text(String type, float x, float y, float size, int color, int color2, int font, float glow) {
        Face.Element e = new Face.Element();
        e.type = type; e.x = x; e.y = y; e.size = size; e.color = color; e.color2 = color2;
        e.font = font; e.glow = glow;
        return e;
    }

    private static Face.Element date(String fmt, float x, float y, float size, int color, int font) {
        Face.Element e = text(Face.Element.DATE, x, y, size, color, 0, font, 0f);
        e.fmt = fmt;
        return e;
    }

    private static Face.Element label(String s, float x, float y, float size, int color) {
        Face.Element e = text(Face.Element.TEXT, x, y, size, color, 0, 3, 0f);
        e.text = s; e.upper = true; e.spacing = 0.15f;
        return e;
    }

    private static Face.Element shape(String variant, float x, float y, float size, int color, int color2, float glow) {
        Face.Element e = new Face.Element();
        e.type = Face.Element.SHAPE; e.variant = variant; e.x = x; e.y = y; e.size = size;
        e.color = color; e.color2 = color2; e.glow = glow;
        return e;
    }

    private static Face.Element box(String variant, float x, float y, float w, float h, float corner, int color, int color2, float glow) {
        Face.Element e = shape(variant, x, y, corner, color, color2, glow);
        e.w = w; e.h = h;
        return e;
    }

    private static Face.Element meter(String type, String variant, float x, float y, float size, int color, int color2, float glow) {
        Face.Element e = text(type, x, y, size, color, color2, 3, glow);
        e.variant = variant;
        return e;
    }

    private static Face base(Theme t, String bgType) {
        Face f = new Face();
        f.bgType = bgType;
        f.bgColor = t.bg1;
        f.bgColor2 = t.bg2;
        return f;
    }

    private static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static Face build(int layout, Theme t) {
        Face f;
        List<Face.Element> el;
        switch (layout) {
            case 0: { // Giant: huge stacked hour / minute with a soft glowing orb
                f = base(t, Face.BG_LINEAR);
                el = f.elements;
                Face.Element orb = shape(Face.Element.S_CIRCLE, 0.5f, 0.48f, 0.30f, alpha(t.c1, 28), 0, 0.12f);
                orb.glowColor = alpha(t.c1, 120);
                el.add(orb);
                Face.Element hr = text(Face.Element.HOUR, 0.5f, 0.31f, 0.36f, t.c1, t.c2, 5, 0.035f);
                el.add(hr);
                Face.Element mn = text(Face.Element.MINUTE, 0.5f, 0.66f, 0.36f, t.c2, 0, 5, 0.03f);
                mn.outline = true;
                el.add(mn);
                Face.Element d = date("EEE d MMM", 0.5f, 0.915f, 0.05f, t.text, 3);
                d.upper = true; d.spacing = 0.18f;
                el.add(d);
                Face.Element b = text(Face.Element.BATTERY, 0.5f, 0.075f, 0.042f, t.dim, 0, 3, 0f);
                el.add(b);
                return f;
            }
            case 1: { // Orbit: glowing ring + battery arc + minimal analog
                f = base(t, Face.BG_RADIAL);
                el = f.elements;
                Face.Element ring = shape(Face.Element.S_RING, 0.5f, 0.5f, 0.47f, alpha(t.c1, 120), 0, 0.03f);
                ring.stroke = 0.006f;
                el.add(ring);
                Face.Element arc = meter(Face.Element.BATTERY, Face.Element.V_ARC, 0.5f, 0.5f, 0.43f, t.c2, t.c1, 0.025f);
                arc.stroke = 0.014f;
                el.add(arc);
                Face.Element dr = shape(Face.Element.S_RING, 0.69f, 0.5f, 0.058f, t.c2, 0, 0.015f);
                dr.stroke = 0.006f;
                el.add(dr);
                el.add(date("d", 0.69f, 0.5f, 0.05f, t.text, 3));
                Face.Element tp = text(Face.Element.TEMP, 0.5f, 0.70f, 0.045f, t.dim, 0, 2, 0f);
                el.add(tp);
                Face.Element dn = date("EEE", 0.31f, 0.5f, 0.042f, t.c1, 3);
                dn.upper = true; dn.spacing = 0.12f;
                el.add(dn);
                Face.Element a = text(Face.Element.ANALOG, 0.5f, 0.5f, 0.38f, t.text, t.c1, 2, 0.02f);
                a.variant = Face.Element.A_MINIMAL;
                el.add(a);
                return f;
            }
            case 2: { // Rings: time over three activity-style rings
                f = base(t, Face.BG_LINEAR);
                el = f.elements;
                el.add(text(Face.Element.TIME, 0.5f, 0.27f, 0.25f, t.text, t.c1, 1, 0.025f));
                Face.Element d = date("EEEE, d MMM", 0.5f, 0.46f, 0.048f, t.dim, 2);
                el.add(d);
                el.add(meter(Face.Element.STEPS, Face.Element.V_RING, 0.21f, 0.74f, 0.13f, t.c1, t.c3, 0.02f));
                el.add(meter(Face.Element.BATTERY, Face.Element.V_RING, 0.5f, 0.74f, 0.13f, t.c2, t.c1, 0.02f));
                Face.Element tr = shape(Face.Element.S_RING, 0.79f, 0.74f, 0.13f, t.c3, t.c2, 0.02f);
                tr.stroke = 0.022f;
                el.add(tr);
                el.add(text(Face.Element.TEMP, 0.79f, 0.74f, 0.07f, t.text, 0, 3, 0f));
                return f;
            }
            case 3: { // Dashboard: big time + three glass tiles
                f = base(t, Face.BG_LINEAR);
                el = f.elements;
                el.add(text(Face.Element.TIME, 0.5f, 0.23f, 0.27f, t.c1, t.c2, 4, 0.03f));
                Face.Element d = date("EEE, d MMM", 0.5f, 0.41f, 0.05f, t.text, 2);
                el.add(d);
                float[] xs = {0.185f, 0.5f, 0.815f};
                for (float x : xs) {
                    Face.Element tile = box(Face.Element.S_BOX, x, 0.71f, 0.28f, 0.34f, 0.06f, alpha(t.c1, 26), 0, 0f);
                    el.add(tile);
                    Face.Element fr = box(Face.Element.S_FRAME, x, 0.71f, 0.28f, 0.34f, 0.06f, alpha(t.c1, 90), 0, 0f);
                    fr.stroke = 0.004f;
                    el.add(fr);
                }
                el.add(meter(Face.Element.BATTERY, Face.Element.V_RING, 0.185f, 0.67f, 0.085f, t.c1, 0, 0.015f));
                el.add(label("Battery", 0.185f, 0.82f, 0.028f, t.dim));
                el.add(meter(Face.Element.STEPS, Face.Element.V_RING, 0.5f, 0.67f, 0.085f, t.c2, 0, 0.015f));
                el.add(label("Steps", 0.5f, 0.82f, 0.028f, t.dim));
                el.add(text(Face.Element.TEMP, 0.815f, 0.67f, 0.08f, t.c3, 0, 3, 0.015f));
                el.add(label("Temp", 0.815f, 0.82f, 0.028f, t.dim));
                return f;
            }
            case 4: { // Minimal: thin time, glowing underline. Very little lit = best battery.
                f = base(t, Face.BG_SOLID);
                f.bgColor = 0xFF000000;
                el = f.elements;
                Face.Element tm = text(Face.Element.TIME, 0.5f, 0.44f, 0.26f, t.text, 0, 0, 0.012f);
                tm.glowColor = t.c1;
                el.add(tm);
                Face.Element ln = box(Face.Element.S_LINE, 0.5f, 0.595f, 0.34f, 0.007f, 0f, t.c1, t.c2, 0.02f);
                el.add(ln);
                Face.Element d = date("EEEE", 0.5f, 0.68f, 0.045f, t.c1, 2);
                d.upper = true; d.spacing = 0.3f;
                el.add(d);
                el.add(text(Face.Element.BATTERY, 0.5f, 0.86f, 0.036f, t.dim, 0, 2, 0f));
                return f;
            }
            case 5: { // Split: two-tone hour | minute
                f = base(t, Face.BG_LINEAR);
                el = f.elements;
                Face.Element bar = meter(Face.Element.BATTERY, Face.Element.V_BAR, 0.5f, 0.13f, 0.1f, t.c2, t.c1, 0.01f);
                bar.w = 0.30f; bar.h = 0.022f;
                el.add(bar);
                el.add(text(Face.Element.HOUR, 0.27f, 0.45f, 0.30f, t.c1, 0, 5, 0.03f));
                el.add(text(Face.Element.MINUTE, 0.73f, 0.45f, 0.30f, t.c2, 0, 0, 0.02f));
                Face.Element v = box(Face.Element.S_LINE, 0.5f, 0.45f, 0.006f, 0.26f, 0f, alpha(t.text, 120), 0, 0f);
                el.add(v);
                Face.Element d = date("EEE d MMM", 0.5f, 0.74f, 0.05f, t.text, 3);
                d.upper = true; d.spacing = 0.12f;
                el.add(d);
                el.add(text(Face.Element.TEMP, 0.5f, 0.86f, 0.045f, t.dim, 0, 2, 0f));
                return f;
            }
            case 6: { // Neon Frame: glowing rounded border following the square screen
                f = base(t, Face.BG_SOLID);
                f.bgColor = t.bg1;
                el = f.elements;
                Face.Element fr = box(Face.Element.S_FRAME, 0.5f, 0.5f, 0.93f, 0.93f, 0.17f, t.c1, t.c2, 0.035f);
                fr.stroke = 0.011f;
                el.add(fr);
                Face.Element d = date("EEEE", 0.5f, 0.24f, 0.05f, t.c2, 3);
                d.upper = true; d.spacing = 0.25f;
                el.add(d);
                Face.Element tm = text(Face.Element.TIME, 0.5f, 0.46f, 0.25f, t.text, 0, 2, 0.03f);
                tm.glowColor = t.c1;
                el.add(tm);
                el.add(date("d MMMM", 0.5f, 0.66f, 0.045f, t.dim, 2));
                el.add(text(Face.Element.BATTERY, 0.33f, 0.80f, 0.045f, t.c1, 0, 3, 0.01f));
                el.add(text(Face.Element.TEMP, 0.67f, 0.80f, 0.045f, t.c2, 0, 3, 0.01f));
                return f;
            }
            case 7: { // Sport: italic bold time + progress bars
                f = base(t, Face.BG_LINEAR);
                el = f.elements;
                Face.Element d = date("EEE d", 0.5f, 0.11f, 0.05f, t.text, 4);
                d.italic = true; d.upper = true;
                el.add(d);
                Face.Element tm = text(Face.Element.TIME, 0.5f, 0.34f, 0.30f, t.c1, t.c2, 5, 0.03f);
                tm.italic = true;
                el.add(tm);
                Face.Element sb = meter(Face.Element.STEPS, Face.Element.V_BAR, 0.5f, 0.60f, 0.1f, t.c1, t.c2, 0.015f);
                sb.w = 0.78f; sb.h = 0.045f;
                el.add(sb);
                Face.Element st = text(Face.Element.STEPS, 0.5f, 0.69f, 0.05f, t.text, 0, 4, 0f);
                st.italic = true;
                el.add(st);
                Face.Element bb = meter(Face.Element.BATTERY, Face.Element.V_BAR, 0.5f, 0.82f, 0.1f, t.c3, 0, 0.01f);
                bb.w = 0.78f; bb.h = 0.03f;
                el.add(bb);
                Face.Element bt = text(Face.Element.BATTERY, 0.5f, 0.90f, 0.036f, t.dim, 0, 3, 0f);
                el.add(bt);
                return f;
            }
            case 8: { // Analog Pro: numerals, two complications, accent hands
                f = base(t, Face.BG_RADIAL);
                el = f.elements;
                el.add(meter(Face.Element.BATTERY, Face.Element.V_RING, 0.30f, 0.5f, 0.075f, t.c2, 0, 0.012f));
                el.add(meter(Face.Element.STEPS, Face.Element.V_RING, 0.70f, 0.5f, 0.075f, t.c1, 0, 0.012f));
                Face.Element d = date("EEE d", 0.5f, 0.70f, 0.045f, t.c1, 3);
                d.upper = true; d.spacing = 0.1f;
                el.add(d);
                el.add(text(Face.Element.TEMP, 0.5f, 0.30f, 0.045f, t.dim, 0, 2, 0f));
                Face.Element a = text(Face.Element.ANALOG, 0.5f, 0.5f, 0.47f, t.text, t.c1, 3, 0.012f);
                a.variant = Face.Element.A_NUMBERS;
                el.add(a);
                return f;
            }
            default: { // Grid: time + 2x2 glass tiles
                f = base(t, Face.BG_LINEAR);
                el = f.elements;
                el.add(text(Face.Element.TIME, 0.5f, 0.19f, 0.22f, t.text, t.c1, 3, 0.02f));
                float[][] pos = {{0.265f, 0.52f}, {0.735f, 0.52f}, {0.265f, 0.80f}, {0.735f, 0.80f}};
                for (float[] p : pos) {
                    el.add(box(Face.Element.S_BOX, p[0], p[1], 0.43f, 0.25f, 0.05f, alpha(t.c1, 42), alpha(t.c2, 30), 0f));
                    Face.Element fr = box(Face.Element.S_FRAME, p[0], p[1], 0.43f, 0.25f, 0.05f, alpha(t.c1, 70), 0, 0f);
                    fr.stroke = 0.004f;
                    el.add(fr);
                }
                Face.Element d = date("EEE d", 0.265f, 0.52f, 0.06f, t.text, 3);
                d.upper = true;
                el.add(d);
                el.add(text(Face.Element.TEMP, 0.735f, 0.52f, 0.075f, t.c3, 0, 3, 0.012f));
                el.add(text(Face.Element.STEPS, 0.265f, 0.80f, 0.042f, t.c1, 0, 3, 0f));
                el.add(text(Face.Element.BATTERY, 0.735f, 0.80f, 0.065f, t.c2, 0, 3, 0.012f));
                return f;
            }
        }
    }
}
