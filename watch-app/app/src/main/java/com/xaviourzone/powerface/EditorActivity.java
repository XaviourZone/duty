package com.xaviourzone.powerface;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Face editor made for a small square screen.
 * Tabs: Add (new items) · Layers (select / reorder / delete) · Style (selected item) · Background.
 * Tap an item on the face to select it, drag to move, pinch to resize. Undo any step.
 */
public class EditorActivity extends Activity implements FaceView.Listener {

    private static final int PICK_PHOTO = 7;
    private static final int TAB_ADD = 0, TAB_LAYERS = 1, TAB_STYLE = 2, TAB_BG = 3;

    static final int[] COLORS = {
            0xFFFFFFFF, 0xFFE8F6FF, 0xFF00E5FF, 0xFF5AC8FA, 0xFF0091FF, 0xFF7C4DFF, 0xFFB388FF, 0xFFFF2BD6,
            0xFFFF9EC4, 0xFFFF2E63, 0xFFFF3D00, 0xFFFF8A00, 0xFFFFC400, 0xFFFFD166, 0xFFB2FF59, 0xFF00FF88,
            0xFF2BFFC6, 0xFF1DE9B6, 0xFF8E8E93, 0xFF3A3A40};
    static final int[] BG_COLORS = {
            0xFF000000, 0xFF04010C, 0xFF16042E, 0xFF0B0424, 0xFF00040E, 0xFF001738, 0xFF00203F, 0xFF010C0B,
            0xFF00190B, 0xFF120304, 0xFF2A0A20, 0xFF240600, 0xFF1C1033, 0xFF1F0E26, 0xFF111114, 0xFF2C2C30};
    static final String[] DATE_FORMATS = {"EEE, d MMM", "EEEE", "EEE d", "d MMM", "d MMMM", "EEEE, d MMMM", "dd.MM", "d", "EEE", "MMMM", "yyyy"};

    private Face face;
    private FaceView fv;
    private TextView status;
    private LinearLayout tabsRow, panel;
    private int tab = TAB_ADD;
    private final Deque<String> undo = new ArrayDeque<>();
    private String savedJson = "";
    private boolean isNew;

    // ------------------------------------------------------------------ setup

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String id = getIntent().getStringExtra("id");
        Face src = FaceStore.find(this, id);
        if (src != null) {
            face = src.copy(src.isBuiltIn() ? FaceStore.newId() : src.id);
            if (src.isBuiltIn()) {
                face.name = src.name + " (mine)";
                isNew = true;
            }
        } else {
            face = new Face();
            face.id = FaceStore.newId();
            face.name = "My face " + (FaceStore.mine(this).size() + 1);
            isNew = true;
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Ui.screenBg());
        LinearLayout col = Ui.column(this, 10);
        scroll.addView(col);

        // Top bar
        LinearLayout top = Ui.row(this);
        top.setGravity(Gravity.CENTER);
        Button back = Ui.chip(this, "← Back");
        back.setOnClickListener(v -> onBackPressed());
        Button undoBtn = Ui.chip(this, "↶ Undo");
        undoBtn.setOnClickListener(v -> undo());
        Button save = Ui.chip(this, "✓ Save");
        save.setBackground(Ui.accent(this));
        save.setOnClickListener(v -> askNameAndSave(false));
        top.addView(back);
        top.addView(undoBtn);
        top.addView(save);
        col.addView(top);

        int side = (int) (Ui.screenSide(this) * 0.72f);
        fv = new FaceView(this);
        fv.setEditMode(true);
        fv.setListener(this);
        fv.setFace(face);
        PowerUtils.fillPreview(this, fv);
        LinearLayout.LayoutParams fvLp = new LinearLayout.LayoutParams(side, side);
        fvLp.topMargin = Ui.dp(this, 8);
        col.addView(fv, fvLp);

        status = Ui.text(this, "", 12, Ui.GREY, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        col.addView(status, matchWrap());

        HorizontalScrollView tabsScroll = new HorizontalScrollView(this);
        tabsScroll.setHorizontalScrollBarEnabled(false);
        tabsRow = Ui.row(this);
        tabsScroll.addView(tabsRow);
        col.addView(tabsScroll, matchWrap());

        panel = Ui.column(this, 0);
        panel.setGravity(Gravity.START);
        col.addView(panel, matchWrap());

        setContentView(scroll);
        savedJson = json();
        showTab(face.elements.isEmpty() ? TAB_ADD : TAB_LAYERS);
        updateStatus();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void showTab(int t) {
        tab = t;
        tabsRow.removeAllViews();
        String[] names = {"+ Add", "Layers", "Style", "Background"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            Button c = Ui.chip(this, names[i]);
            if (i == t) c.setBackground(Ui.accent(this));
            c.setOnClickListener(v -> showTab(idx));
            tabsRow.addView(c);
        }
        panel.removeAllViews();
        switch (t) {
            case TAB_ADD: buildAdd(); break;
            case TAB_LAYERS: buildLayers(); break;
            case TAB_STYLE: buildStyle(); break;
            default: buildBackground(); break;
        }
    }

    private void updateStatus() {
        int i = fv.getSelected();
        if (i >= 0 && i < face.elements.size()) {
            status.setText("Selected: " + nameOf(face.elements.get(i)) + " • drag to move, pinch to resize");
        } else if (face.elements.isEmpty()) {
            status.setText("Empty face. Tap + Add to place items.");
        } else {
            status.setText("Tap an item on the face to select it");
        }
    }

    // ------------------------------------------------------------------ undo

    private void pushUndo() {
        try {
            String s = face.toJson().toString();
            if (!undo.isEmpty() && undo.peek().equals(s)) return;
            undo.push(s);
            while (undo.size() > 50) undo.removeLast();
        } catch (JSONException ignored) {
        }
    }

    private String json() {
        try {
            return face.toJson().toString();
        } catch (JSONException e) {
            return "";
        }
    }

    private void undo() {
        if (undo.isEmpty()) { toast("Nothing to undo"); return; }
        try {
            String id = face.id, name = face.name;
            face = Face.fromJson(new JSONObject(undo.pop()));
            face.id = id;
            face.name = name;
            fv.setSelected(-1);
            fv.setFace(face);
            showTab(tab == TAB_STYLE ? TAB_LAYERS : tab);
            updateStatus();
        } catch (JSONException ignored) {
        }
    }

    /** Change something discrete: save an undo step, apply, redraw, refresh the panel. */
    private void mutate(Runnable r) {
        pushUndo();
        r.run();
        fv.invalidate();
        showTab(tab);
        updateStatus();
    }

    private Face.Element sel() {
        int i = fv.getSelected();
        return i >= 0 && i < face.elements.size() ? face.elements.get(i) : null;
    }

    // ------------------------------------------------------------------ Add tab

    private void buildAdd() {
        hint("Tap to add. New items appear in the middle and are selected.");
        String[] names = {
                "Time", "Hour", "Minute", "AM/PM", "Date", "Weekday", "Day no.",
                "Battery %", "Battery ring", "Battery bar", "Battery arc",
                "Steps", "Steps ring", "Steps bar", "Temperature", "Text", "Analog dial",
                "Circle", "Ring", "Glass box", "Frame", "Line", "Arc"};
        Runnable[] actions = new Runnable[names.length];
        int i = 0;
        actions[i++] = () -> add(txt(Face.Element.TIME, 0.24f, 0xFFFFFFFF, 1));
        actions[i++] = () -> add(txt(Face.Element.HOUR, 0.3f, 0xFF00E5FF, 5));
        actions[i++] = () -> add(txt(Face.Element.MINUTE, 0.3f, 0xFFFF2BD6, 0));
        actions[i++] = () -> add(txt(Face.Element.AMPM, 0.05f, 0xFFFFFFFF, 3));
        actions[i++] = () -> add(dateEl("EEE, d MMM", false));
        actions[i++] = () -> add(dateEl("EEEE", true));
        actions[i++] = () -> add(dateEl("d", false));
        actions[i++] = () -> add(txt(Face.Element.BATTERY, 0.06f, 0xFF00FF88, 3));
        actions[i++] = () -> add(meter(Face.Element.BATTERY, Face.Element.V_RING, 0xFF00FF88));
        actions[i++] = () -> add(meter(Face.Element.BATTERY, Face.Element.V_BAR, 0xFF00FF88));
        actions[i++] = () -> add(meter(Face.Element.BATTERY, Face.Element.V_ARC, 0xFF00FF88));
        actions[i++] = () -> add(txt(Face.Element.STEPS, 0.055f, 0xFFB388FF, 3));
        actions[i++] = () -> add(meter(Face.Element.STEPS, Face.Element.V_RING, 0xFFB388FF));
        actions[i++] = () -> add(meter(Face.Element.STEPS, Face.Element.V_BAR, 0xFFB388FF));
        actions[i++] = () -> add(txt(Face.Element.TEMP, 0.08f, 0xFF5AC8FA, 3));
        actions[i++] = this::addText;
        actions[i++] = () -> {
            Face.Element e = txt(Face.Element.ANALOG, 0.42f, 0xFFFFFFFF, 3);
            e.variant = Face.Element.A_CLASSIC;
            e.color2 = 0xFF00E5FF;
            add(e);
        };
        actions[i++] = () -> add(shapeEl(Face.Element.S_CIRCLE));
        actions[i++] = () -> add(shapeEl(Face.Element.S_RING));
        actions[i++] = () -> add(shapeEl(Face.Element.S_BOX));
        actions[i++] = () -> add(shapeEl(Face.Element.S_FRAME));
        actions[i++] = () -> add(shapeEl(Face.Element.S_LINE));
        actions[i] = () -> add(shapeEl(Face.Element.S_ARC));
        grid(panel, names, actions, 3);
    }

    private Face.Element txt(String type, float size, int color, int font) {
        Face.Element e = new Face.Element();
        e.type = type; e.size = size; e.color = color; e.font = font;
        return e;
    }

    private Face.Element dateEl(String fmt, boolean caps) {
        Face.Element e = txt(Face.Element.DATE, 0.055f, 0xFFFFFFFF, 2);
        e.fmt = fmt;
        if (caps) { e.upper = true; e.spacing = 0.2f; }
        return e;
    }

    private Face.Element meter(String type, String variant, int color) {
        Face.Element e = txt(type, Face.Element.V_ARC.equals(variant) ? 0.44f : 0.11f, color, 3);
        e.variant = variant;
        e.glow = 0.015f;
        if (Face.Element.V_BAR.equals(variant)) { e.w = 0.6f; e.h = 0.035f; }
        if (Face.Element.V_ARC.equals(variant)) e.stroke = 0.014f;
        return e;
    }

    private Face.Element shapeEl(String v) {
        Face.Element e = new Face.Element();
        e.type = Face.Element.SHAPE;
        e.variant = v;
        e.color = 0xFF00E5FF;
        e.glow = 0.02f;
        switch (v) {
            case Face.Element.S_CIRCLE: e.size = 0.15f; e.color = 0x3300E5FF; e.glow = 0.08f; break;
            case Face.Element.S_RING: e.size = 0.3f; e.stroke = 0.01f; break;
            case Face.Element.S_BOX: e.w = 0.45f; e.h = 0.25f; e.size = 0.05f; e.color = 0x2600E5FF; e.glow = 0f; break;
            case Face.Element.S_FRAME: e.w = 0.93f; e.h = 0.93f; e.size = 0.16f; e.stroke = 0.011f; e.color2 = 0xFFFF2BD6; break;
            case Face.Element.S_LINE: e.w = 0.4f; e.h = 0.007f; break;
            default: e.size = 0.44f; e.stroke = 0.012f; e.sweep = 270f; break;
        }
        return e;
    }

    private void add(Face.Element e) {
        pushUndo();
        face.elements.add(e);
        fv.setFace(face);
        fv.setSelected(face.elements.size() - 1);
        showTab(TAB_STYLE);
        updateStatus();
    }

    private void addText() {
        Face.Element e = txt(Face.Element.TEXT, 0.07f, 0xFFFFFFFF, 3);
        e.text = "Hello";
        add(e);
        askText(e);
    }

    // ------------------------------------------------------------------ Layers tab

    private void buildLayers() {
        if (face.elements.isEmpty()) {
            hint("No items yet. Use + Add.");
            return;
        }
        hint("Top of the list is drawn in front. Tap a name to edit it.");
        int selected = fv.getSelected();
        for (int i = face.elements.size() - 1; i >= 0; i--) {
            final int idx = i;
            Face.Element e = face.elements.get(i);
            LinearLayout row = Ui.row(this);
            row.setPadding(Ui.dp(this, 10), Ui.dp(this, 2), Ui.dp(this, 4), Ui.dp(this, 2));
            row.setBackground(idx == selected ? Ui.accent(this) : Ui.glass(this, 14));
            LinearLayout.LayoutParams rlp = matchWrap();
            rlp.topMargin = Ui.dp(this, 6);
            panel.addView(row, rlp);

            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(e.color | 0xFF000000);
            dot.setBackground(g);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(this, 12), Ui.dp(this, 12));
            dlp.rightMargin = Ui.dp(this, 8);
            row.addView(dot, dlp);

            TextView name = Ui.text(this, nameOf(e), 14, 0xFFFFFFFF, false);
            name.setSingleLine(true);
            name.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            name.setOnClickListener(v -> { fv.setSelected(idx); showTab(TAB_STYLE); updateStatus(); });
            row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Button up = Ui.chip(this, "▲");
            up.setOnClickListener(v -> mutate(() -> move(idx, idx + 1)));
            Button down = Ui.chip(this, "▼");
            down.setOnClickListener(v -> mutate(() -> move(idx, idx - 1)));
            Button del = Ui.chip(this, "✕");
            del.setTextColor(0xFFFF6B6B);
            del.setOnClickListener(v -> mutate(() -> deleteAt(idx)));
            row.addView(up);
            row.addView(down);
            row.addView(del);
        }
        Button clear = Ui.button(this, "Delete all items", false);
        clear.setTextColor(0xFFFF6B6B);
        clear.setOnClickListener(v -> new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setMessage("Remove every item from this face?")
                .setPositiveButton("Delete all", (d, w) -> mutate(() -> { face.elements.clear(); fv.setSelected(-1); }))
                .setNegativeButton("Cancel", null).show());
        panel.addView(clear);
    }

    private void move(int from, int to) {
        if (to < 0 || to >= face.elements.size()) return;
        Face.Element e = face.elements.remove(from);
        face.elements.add(to, e);
        fv.setSelected(to);
    }

    private void deleteAt(int idx) {
        if (idx < 0 || idx >= face.elements.size()) return;
        face.elements.remove(idx);
        fv.setSelected(-1);
        fv.setFace(face);
    }

    // ------------------------------------------------------------------ Style tab

    private void buildStyle() {
        final Face.Element e = sel();
        if (e == null) {
            hint("Tap an item on the face (or in Layers) to style it.");
            return;
        }
        final int idx = fv.getSelected();
        boolean isText = !Face.Element.SHAPE.equals(e.type) && !Face.Element.ANALOG.equals(e.type)
                && !(isMeter(e) && !Face.Element.V_TEXT.equals(e.variant));

        // Header: name + duplicate + delete
        LinearLayout head = Ui.row(this);
        TextView n = Ui.text(this, nameOf(e), 15, 0xFFFFFFFF, true);
        head.addView(n, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button dup = Ui.chip(this, "Duplicate");
        dup.setOnClickListener(v -> mutate(() -> {
            Face.Element c = e.copy();
            c.x = FaceView.clamp(c.x + 0.04f, 0f, 1f);
            c.y = FaceView.clamp(c.y + 0.04f, 0f, 1f);
            face.elements.add(c);
            fv.setSelected(face.elements.size() - 1);
        }));
        Button del = Ui.chip(this, "🗑 Delete");
        del.setTextColor(0xFFFF6B6B);
        del.setOnClickListener(v -> mutate(() -> deleteAt(idx)));
        head.addView(dup);
        head.addView(del);
        panel.addView(head, matchWrap());

        // Size & position
        section("Size & position");
        LinearLayout r1 = toolRow();
        chip(r1, "Smaller", () -> mutate(() -> scale(e, 1 / 1.1f)));
        chip(r1, "Bigger", () -> mutate(() -> scale(e, 1.1f)));
        chip(r1, "Centre ↔", () -> mutate(() -> e.x = 0.5f));
        chip(r1, "Centre ↕", () -> mutate(() -> e.y = 0.5f));
        chip(r1, "←", () -> mutate(() -> e.x = FaceView.clamp(e.x - 0.01f, 0f, 1f)));
        chip(r1, "→", () -> mutate(() -> e.x = FaceView.clamp(e.x + 0.01f, 0f, 1f)));
        chip(r1, "↑", () -> mutate(() -> e.y = FaceView.clamp(e.y - 0.01f, 0f, 1f)));
        chip(r1, "↓", () -> mutate(() -> e.y = FaceView.clamp(e.y + 0.01f, 0f, 1f)));
        if (FaceView.usesBox(e)) {
            LinearLayout r2 = toolRow();
            chip(r2, "Width −", () -> mutate(() -> e.w = FaceView.clamp(e.w / 1.1f, 0.005f, 1.2f)));
            chip(r2, "Width +", () -> mutate(() -> e.w = FaceView.clamp(e.w * 1.1f, 0.005f, 1.2f)));
            chip(r2, "Height −", () -> mutate(() -> e.h = FaceView.clamp(e.h / 1.1f, 0.003f, 1.2f)));
            chip(r2, "Height +", () -> mutate(() -> e.h = FaceView.clamp(e.h * 1.1f, 0.003f, 1.2f)));
        }

        // Colours
        section("Colour");
        swatches(COLORS, e.color & 0xFFFFFF, false, c -> mutate(() -> e.color = (c & 0x00FFFFFF) | (e.color & 0xFF000000)));
        section("Gradient (second colour)");
        swatches(COLORS, e.color2, true, c -> mutate(() -> e.color2 = c));

        // Glow + opacity
        section("Glow");
        slider(Math.round(e.glow / 0.08f * 100), 100, p -> { e.glow = p / 100f * 0.08f; fv.invalidate(); });
        section("Opacity");
        int curAlpha = Face.Element.SHAPE.equals(e.type) ? (e.color >>> 24) : e.alpha;
        slider(curAlpha, 255, p -> {
            int a = Math.max(10, p);
            if (Face.Element.SHAPE.equals(e.type)) e.color = (e.color & 0x00FFFFFF) | (a << 24);
            else e.alpha = a;
            fv.invalidate();
        });

        // Type-specific options
        if (isText || Face.Element.ANALOG.equals(e.type)) {
            section("Font");
            LinearLayout fr = toolRow();
            for (int i = 0; i < FaceView.FONT_NAMES.length; i++) {
                final int f = i;
                Button c = chip(fr, FaceView.FONT_NAMES[i], () -> mutate(() -> e.font = f));
                if (e.font == i) c.setBackground(Ui.accent(this));
            }
        }
        if (isText) {
            section("Text style");
            LinearLayout ts = toolRow();
            toggleChip(ts, "Italic", e.italic, () -> mutate(() -> e.italic = !e.italic));
            toggleChip(ts, "Outline", e.outline, () -> mutate(() -> e.outline = !e.outline));
            toggleChip(ts, "CAPS", e.upper, () -> mutate(() -> e.upper = !e.upper));
            chip(ts, "Spacing −", () -> mutate(() -> e.spacing = Math.max(-0.1f, e.spacing - 0.05f)));
            chip(ts, "Spacing +", () -> mutate(() -> e.spacing = Math.min(0.6f, e.spacing + 0.05f)));
        }
        switch (e.type) {
            case Face.Element.TIME:
            case Face.Element.HOUR: {
                section("Clock");
                LinearLayout r = toolRow();
                toggleChip(r, "24 hour", e.h24, () -> mutate(() -> e.h24 = true));
                toggleChip(r, "12 hour", !e.h24, () -> mutate(() -> e.h24 = false));
                break;
            }
            case Face.Element.DATE: {
                section("Date format");
                LinearLayout r = toolRow();
                for (final String fmt : DATE_FORMATS) {
                    String sample = fv.textFor(withFmt(e, fmt));
                    toggleChip(r, sample, fmt.equals(e.fmt), () -> mutate(() -> e.fmt = fmt));
                }
                break;
            }
            case Face.Element.BATTERY:
            case Face.Element.STEPS: {
                section("Show as");
                LinearLayout r = toolRow();
                String[] vs = {Face.Element.V_TEXT, Face.Element.V_RING, Face.Element.V_BAR, Face.Element.V_ARC};
                String[] ls = {"Text", "Ring", "Bar", "Arc"};
                for (int i = 0; i < vs.length; i++) {
                    final String v = vs[i];
                    toggleChip(r, ls[i], v.equals(e.variant), () -> mutate(() -> setMeterVariant(e, v)));
                }
                if (Face.Element.V_ARC.equals(e.variant)) strokeRow(e);
                break;
            }
            case Face.Element.ANALOG: {
                section("Dial style");
                LinearLayout r = toolRow();
                String[] vs = {Face.Element.A_CLASSIC, Face.Element.A_MINIMAL, Face.Element.A_NUMBERS};
                String[] ls = {"Classic", "Minimal", "Numbers"};
                for (int i = 0; i < vs.length; i++) {
                    final String v = vs[i];
                    toggleChip(r, ls[i], v.equals(e.variant), () -> mutate(() -> e.variant = v));
                }
                break;
            }
            case Face.Element.SHAPE: {
                section("Shape");
                LinearLayout r = toolRow();
                String[] vs = {Face.Element.S_CIRCLE, Face.Element.S_RING, Face.Element.S_BOX,
                        Face.Element.S_FRAME, Face.Element.S_LINE, Face.Element.S_ARC};
                String[] ls = {"Circle", "Ring", "Box", "Frame", "Line", "Arc"};
                for (int i = 0; i < vs.length; i++) {
                    final String v = vs[i];
                    toggleChip(r, ls[i], v.equals(e.variant), () -> mutate(() -> e.variant = v));
                }
                if (!Face.Element.S_CIRCLE.equals(e.variant) && !Face.Element.S_LINE.equals(e.variant)
                        && !Face.Element.S_BOX.equals(e.variant)) strokeRow(e);
                if (Face.Element.S_ARC.equals(e.variant)) {
                    LinearLayout a = toolRow();
                    chip(a, "Arc shorter", () -> mutate(() -> e.sweep = Math.max(20f, e.sweep - 20f)));
                    chip(a, "Arc longer", () -> mutate(() -> e.sweep = Math.min(360f, e.sweep + 20f)));
                }
                if (Face.Element.S_BOX.equals(e.variant) || Face.Element.S_FRAME.equals(e.variant)) {
                    LinearLayout a = toolRow();
                    chip(a, "Corners −", () -> mutate(() -> e.size = Math.max(0f, e.size - 0.02f)));
                    chip(a, "Corners +", () -> mutate(() -> e.size = Math.min(0.5f, e.size + 0.02f)));
                }
                break;
            }
            case Face.Element.TEXT: {
                section("Text");
                LinearLayout r = toolRow();
                chip(r, "Edit text", () -> askText(e));
                break;
            }
            default:
                break;
        }

        section("Layer order");
        LinearLayout lo = toolRow();
        chip(lo, "Bring to front", () -> mutate(() -> move(idx, face.elements.size() - 1)));
        chip(lo, "Send to back", () -> mutate(() -> move(idx, 0)));
    }

    private static boolean isMeter(Face.Element e) {
        return Face.Element.BATTERY.equals(e.type) || Face.Element.STEPS.equals(e.type);
    }

    private Face.Element withFmt(Face.Element e, String fmt) {
        Face.Element c = e.copy();
        c.fmt = fmt;
        c.upper = false;
        return c;
    }

    private void setMeterVariant(Face.Element e, String v) {
        String old = e.variant;
        e.variant = v;
        if (v.equals(old)) return;
        switch (v) {
            case Face.Element.V_RING: e.size = 0.11f; break;
            case Face.Element.V_BAR: e.w = 0.6f; e.h = 0.035f; break;
            case Face.Element.V_ARC: e.size = 0.44f; e.stroke = 0.014f; break;
            default: e.size = 0.06f; break;
        }
    }

    private void strokeRow(final Face.Element e) {
        LinearLayout s = toolRow();
        chip(s, "Thinner", () -> mutate(() -> e.stroke = Math.max(0.002f, e.stroke / 1.25f)));
        chip(s, "Thicker", () -> mutate(() -> e.stroke = Math.min(0.1f, e.stroke * 1.25f)));
    }

    private void scale(Face.Element e, float f) {
        e.size = FaceView.clamp(e.size * f, 0.01f, 0.7f);
        if (FaceView.usesBox(e)) {
            e.w = FaceView.clamp(e.w * f, 0.005f, 1.2f);
            e.h = FaceView.clamp(e.h * f, 0.003f, 1.2f);
        }
    }

    // ------------------------------------------------------------------ Background tab

    private void buildBackground() {
        section("Type");
        LinearLayout r = toolRow();
        String[] ts = {Face.BG_SOLID, Face.BG_LINEAR, Face.BG_RADIAL};
        String[] ls = {"Solid", "Gradient", "Glow"};
        for (int i = 0; i < ts.length; i++) {
            final String t = ts[i];
            toggleChip(r, ls[i], t.equals(face.bgType), () -> mutate(() -> face.bgType = t));
        }
        section(Face.BG_RADIAL.equals(face.bgType) ? "Edge colour" : "Colour");
        swatches(BG_COLORS, face.bgColor, false, c -> mutate(() -> face.bgColor = c));
        if (!Face.BG_SOLID.equals(face.bgType)) {
            section(Face.BG_RADIAL.equals(face.bgType) ? "Centre glow colour" : "Second colour");
            int[] both = new int[BG_COLORS.length + COLORS.length];
            System.arraycopy(BG_COLORS, 0, both, 0, BG_COLORS.length);
            for (int i = 0; i < COLORS.length; i++) both[BG_COLORS.length + i] = (COLORS[i] & 0x00FFFFFF) | 0x66000000;
            swatches(both, face.bgColor2, false, c -> mutate(() -> face.bgColor2 = c));
        }
        section("Photo from gallery");
        LinearLayout p = toolRow();
        chip(p, "Choose photo", this::pickPhoto);
        chip(p, "Remove photo", () -> mutate(() -> { face.bgImage = null; fv.reloadBg(); }));
        if (face.bgImage != null) {
            section("Darken photo (saves battery)");
            slider(face.bgDim, 90, v -> { face.bgDim = v; fv.invalidate(); });
        }
        hint("Tip: black areas are switched off on the AMOLED screen, so darker faces last longer.");
    }

    // ------------------------------------------------------------------ small UI helpers

    private interface IntCb { void on(int v); }

    private void hint(String s) {
        TextView t = Ui.text(this, s, 12, Ui.GREY, false);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 4));
        panel.addView(t, matchWrap());
    }

    private void section(String title) {
        TextView t = Ui.text(this, title, 12, 0xFFB8B8C8, true);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), 0, Ui.dp(this, 4));
        panel.addView(t, matchWrap());
    }

    private LinearLayout toolRow() {
        HorizontalScrollView h = new HorizontalScrollView(this);
        h.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.row(this);
        h.addView(row);
        panel.addView(h, matchWrap());
        return row;
    }

    private Button chip(LinearLayout row, String label, Runnable r) {
        Button b = Ui.chip(this, label);
        b.setOnClickListener(v -> r.run());
        row.addView(b);
        return b;
    }

    private void toggleChip(LinearLayout row, String label, boolean on, Runnable r) {
        Button b = chip(row, label, r);
        if (on) b.setBackground(Ui.accent(this));
    }

    private void grid(LinearLayout parent, String[] names, Runnable[] actions, int cols) {
        LinearLayout row = null;
        for (int i = 0; i < names.length; i++) {
            if (i % cols == 0) {
                row = Ui.row(this);
                LinearLayout.LayoutParams lp = matchWrap();
                lp.topMargin = Ui.dp(this, 6);
                parent.addView(row, lp);
            }
            Button b = Ui.chip(this, names[i]);
            final Runnable a = actions[i];
            b.setOnClickListener(v -> a.run());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f);
            lp.rightMargin = Ui.dp(this, 6);
            row.addView(b, lp);
        }
        // pad the last row so buttons keep the same width
        if (row != null) {
            for (int i = names.length % cols; i != 0 && i < cols; i++) {
                View spacer = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
                lp.rightMargin = Ui.dp(this, 6);
                row.addView(spacer, lp);
            }
        }
    }

    private void swatches(int[] colors, int current, boolean withNone, IntCb cb) {
        LinearLayout row = toolRow();
        int size = Ui.dp(this, 32);
        if (withNone) {
            Button none = Ui.chip(this, "None");
            if (current == 0) none.setBackground(Ui.accent(this));
            none.setOnClickListener(v -> cb.on(0));
            row.addView(none);
        }
        for (final int c : colors) {
            View s = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(c);
            boolean on = (c & 0xFFFFFF) == (current & 0xFFFFFF) && (current != 0 || !withNone);
            g.setStroke(Ui.dp(this, on ? 3 : 1), on ? 0xFFFFFFFF : 0x55FFFFFF);
            s.setBackground(g);
            s.setOnClickListener(v -> cb.on(c));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.rightMargin = Ui.dp(this, 8);
            row.addView(s, lp);
        }
    }

    private void slider(int value, int max, final IntCb cb) {
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        s.setProgress(Math.max(0, Math.min(max, value)));
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean user) { if (user) cb.on(p); }
            @Override public void onStartTrackingTouch(SeekBar b) { pushUndo(); }
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        panel.addView(s, matchWrap());
    }

    static String nameOf(Face.Element e) {
        switch (e.type) {
            case Face.Element.TIME: return "Time";
            case Face.Element.HOUR: return "Hour";
            case Face.Element.MINUTE: return "Minute";
            case Face.Element.AMPM: return "AM/PM";
            case Face.Element.DATE: return "Date";
            case Face.Element.BATTERY: return "Battery" + variantName(e.variant);
            case Face.Element.STEPS: return "Steps" + variantName(e.variant);
            case Face.Element.TEMP: return "Temperature";
            case Face.Element.TEXT: return "Text “" + e.text + "”";
            case Face.Element.ANALOG: return "Analog dial";
            case Face.Element.SHAPE:
                return e.variant.substring(0, 1).toUpperCase() + e.variant.substring(1) + " shape";
            default: return e.type;
        }
    }

    private static String variantName(String v) {
        if (Face.Element.V_RING.equals(v)) return " ring";
        if (Face.Element.V_BAR.equals(v)) return " bar";
        if (Face.Element.V_ARC.equals(v)) return " arc";
        return "";
    }

    private void askText(final Face.Element e) {
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_TEXT);
        in.setText(e.text);
        in.setSelection(in.getText().length());
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Text")
                .setView(in)
                .setPositiveButton("OK", (d, w) -> mutate(() -> e.text = in.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ save / back

    private void askNameAndSave(final boolean thenFinish) {
        final EditText in = new EditText(this);
        in.setSingleLine(true);
        in.setText(face.name);
        in.setSelection(in.getText().length());
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Face name")
                .setView(in)
                .setPositiveButton("Save", (d, w) -> {
                    String n = in.getText().toString().trim();
                    face.name = n.isEmpty() ? "My face" : n;
                    FaceStore.upsert(this, face);
                    savedJson = json();
                    isNew = false;
                    toast("Saved to My faces");
                    if (thenFinish) finish();
                })
                .setNeutralButton("Save & Apply", (d, w) -> {
                    String n = in.getText().toString().trim();
                    face.name = n.isEmpty() ? "My face" : n;
                    FaceStore.upsert(this, face);
                    FaceStore.setCurrent(this, face.id);
                    savedJson = json();
                    startActivity(new Intent(this, AodActivity.class));
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (!isNew && json().equals(savedJson)) { super.onBackPressed(); return; }
        if (isNew && face.elements.isEmpty() && face.bgImage == null) { super.onBackPressed(); return; }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setMessage("Save your changes?")
                .setPositiveButton("Save", (d, w) -> askNameAndSave(true))
                .setNegativeButton("Discard", (d, w) -> finish())
                .setNeutralButton("Keep editing", null)
                .show();
    }

    // ------------------------------------------------------------------ photo

    private void pickPhoto() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "Choose a photo"), PICK_PHOTO);
        } catch (Exception e) {
            toast("No gallery app found");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_PHOTO || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        status.setText("Loading photo…");
        new Thread(() -> {
            final String path = copyPhoto(uri);
            runOnUiThread(() -> {
                if (path == null) { toast("Could not open that photo"); updateStatus(); return; }
                pushUndo();
                face.bgImage = path;
                fv.reloadBg();
                showTab(TAB_BG);
                updateStatus();
            });
        }).start();
    }

    private String copyPhoto(Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            int sample = 1;
            while (o.outWidth / (sample * 2) >= 1024 && o.outHeight / (sample * 2) >= 1024) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            Bitmap bmp = BitmapFactory.decodeStream(in, null, o2);
            in.close();
            if (bmp == null) return null;
            File out = new File(getFilesDir(), "bg_" + face.id + "_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fos = new FileOutputStream(out);
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();
            bmp.recycle();
            return out.getAbsolutePath();
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ FaceView.Listener

    @Override
    public void onSelect(int index) {
        updateStatus();
        if (index >= 0) showTab(TAB_STYLE);
        else if (tab == TAB_STYLE) showTab(TAB_LAYERS);
    }

    @Override
    public void onEditStart() {
        pushUndo();
    }

    @Override
    public void onChanged() {
        updateStatus();
    }
}
