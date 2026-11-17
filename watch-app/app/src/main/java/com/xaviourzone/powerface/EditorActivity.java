package com.xaviourzone.powerface;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Create or edit a face: add elements, drag them, change size/colour/font, set a photo. */
public class EditorActivity extends Activity implements FaceView.Listener {

    private static final int PICK_PHOTO = 7;

    private static final int[] COLORS = {
            0xFFFFFFFF, 0xFF4FC3F7, 0xFF2F80ED, 0xFF7C4DFF, 0xFFB388FF, 0xFFFF4081,
            0xFFFF9800, 0xFFFFEB3B, 0xFF69F0AE, 0xFF00BFA5, 0xFFFF5252, 0xFF8E8E93};
    private static final int[] BG_COLORS = {
            0xFF000000, 0xFF0B1A3A, 0xFF1A0B2E, 0xFF0F2A20, 0xFF2A1010, 0xFF1C1C1E};

    private Face face;
    private FaceView fv;
    private TextView status;
    private EditText nameBox;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String id = getIntent().getStringExtra("id");
        Face existing = FaceStore.find(FaceStore.load(this), id);
        if (existing != null) {
            face = existing.copy(existing.id);
        } else {
            face = new Face();
            face.id = FaceStore.newId();
            face.name = "My face " + (FaceStore.load(this).size() + 1);
            Face.Element t = Face.el(Face.Element.TIME, 0.5f, 0.45f, 0.26f, 0xFF4FC3F7);
            t.color2 = 0xFFB388FF;
            face.elements.add(t);
            face.elements.add(Face.el(Face.Element.DATE, 0.5f, 0.66f, 0.06f, 0xFFFFFFFF));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Ui.screenBg());
        LinearLayout col = Ui.column(this, 12);
        scroll.addView(col);

        int side = (int) (Ui.screenSide(this) * 0.80f);
        fv = new FaceView(this);
        fv.setEditMode(true);
        fv.setListener(this);
        fv.setFace(face);
        PowerUtils.fillPreview(this, fv);
        col.addView(fv, new LinearLayout.LayoutParams(side, side));

        status = Ui.text(this, "Tap an item to select, drag to move", 12, Ui.GREY, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 4));
        col.addView(status);

        // Add elements
        section(col, "Add");
        LinearLayout add = toolRow(col);
        addChip(add, "Time", () -> addElement(Face.el(Face.Element.TIME, 0.5f, 0.45f, 0.22f, 0xFFFFFFFF)));
        addChip(add, "Date", () -> addElement(Face.el(Face.Element.DATE, 0.5f, 0.65f, 0.06f, 0xFFFFFFFF)));
        addChip(add, "Battery", () -> addElement(Face.el(Face.Element.BATTERY, 0.5f, 0.15f, 0.06f, 0xFF69F0AE)));
        addChip(add, "Temp", () -> addElement(Face.el(Face.Element.TEMP, 0.5f, 0.80f, 0.07f, 0xFF4FC3F7)));
        addChip(add, "Steps", () -> addElement(Face.el(Face.Element.STEPS, 0.5f, 0.85f, 0.06f, 0xFFB388FF)));
        addChip(add, "Analog", () -> addElement(Face.el(Face.Element.ANALOG, 0.5f, 0.5f, 0.42f, 0xFFFFFFFF)));
        addChip(add, "Text", this::addText);

        // Edit the selected element
        section(col, "Selected item");
        LinearLayout sel = toolRow(col);
        addChip(sel, "A−", () -> withSelected(e -> e.size = Math.max(0.02f, e.size / 1.12f)));
        addChip(sel, "A+", () -> withSelected(e -> e.size = Math.min(0.6f, e.size * 1.12f)));
        addChip(sel, "Colour", () -> withSelected(e -> e.color = next(COLORS, e.color)));
        addChip(sel, "Gradient", () -> withSelected(e -> e.color2 = e.color2 == 0 ? 0xFFB388FF
                : nextOrNone(COLORS, e.color2)));
        addChip(sel, "Font", () -> withSelected(e -> e.font = (e.font + 1) % 3));
        addChip(sel, "Ring", () -> withSelected(e -> {
            if (Face.Element.BATTERY.equals(e.type) || Face.Element.STEPS.equals(e.type)) {
                e.ring = !e.ring;
                if (e.ring && e.size < 0.07f) e.size = 0.1f;
            } else toast("Ring works for Battery and Steps");
        }));
        addChip(sel, "12/24h", () -> withSelected(e -> {
            if (Face.Element.TIME.equals(e.type)) e.h24 = !e.h24;
            else toast("Only for Time");
        }));
        addChip(sel, "Centre", () -> withSelected(e -> e.x = 0.5f));
        addChip(sel, "Edit text", () -> withSelected(e -> {
            if (Face.Element.TEXT.equals(e.type)) askText(e);
            else toast("Only for Text");
        }));
        addChip(sel, "Delete", () -> {
            int i = fv.getSelected();
            if (i < 0) { toast("Select an item first"); return; }
            face.elements.remove(i);
            fv.setSelected(-1);
            fv.setFace(face);
        });

        // Background
        section(col, "Background");
        LinearLayout bg = toolRow(col);
        addChip(bg, "Photo", this::pickPhoto);
        addChip(bg, "No photo", () -> { face.bgImage = null; fv.reloadBg(); });
        addChip(bg, "Colour", () -> { face.bgColor = next(BG_COLORS, face.bgColor); fv.invalidate(); });
        addChip(bg, "Darker", () -> { face.bgDim = Math.min(90, face.bgDim + 10); fv.invalidate(); });
        addChip(bg, "Brighter", () -> { face.bgDim = Math.max(0, face.bgDim - 10); fv.invalidate(); });

        // Name + save
        section(col, "Name");
        nameBox = new EditText(this);
        nameBox.setText(face.name);
        nameBox.setTextColor(0xFFFFFFFF);
        nameBox.setSingleLine(true);
        nameBox.setBackground(Ui.glass(this, 14));
        nameBox.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        col.addView(nameBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Button save = Ui.button(this, "Save", false);
        save.setOnClickListener(v -> { save(); finish(); });
        col.addView(save);
        Button saveApply = Ui.button(this, "⚡  Save & Apply", true);
        saveApply.setOnClickListener(v -> {
            save();
            FaceStore.setCurrent(this, face.id);
            startActivity(new Intent(this, AodActivity.class));
            finish();
        });
        col.addView(saveApply);

        setContentView(scroll);
    }

    // ----- helpers -----

    private interface Edit { void apply(Face.Element e); }

    private void withSelected(Edit edit) {
        int i = fv.getSelected();
        if (i < 0 || i >= face.elements.size()) { toast("Select an item first"); return; }
        edit.apply(face.elements.get(i));
        fv.invalidate();
    }

    private void addElement(Face.Element e) {
        face.elements.add(e);
        fv.setFace(face);
        fv.setSelected(face.elements.size() - 1);
        status.setText("Added. Drag it where you want.");
    }

    private void addText() {
        Face.Element e = Face.el(Face.Element.TEXT, 0.5f, 0.3f, 0.07f, 0xFFFFFFFF);
        e.text = "Hello";
        addElement(e);
        askText(e);
    }

    private void askText(final Face.Element e) {
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_TEXT);
        in.setText(e.text);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Text")
                .setView(in)
                .setPositiveButton("OK", (d, w) -> { e.text = in.getText().toString(); fv.invalidate(); })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static int next(int[] arr, int cur) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == cur) return arr[(i + 1) % arr.length];
        return arr[0];
    }

    /** Cycles through colours and then "no gradient". */
    private static int nextOrNone(int[] arr, int cur) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == cur) return i + 1 < arr.length ? arr[i + 1] : 0;
        return 0;
    }

    private void section(LinearLayout col, String title) {
        TextView t = Ui.text(this, title, 13, Ui.GREY, true);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), 0, Ui.dp(this, 6));
        col.addView(t, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout toolRow(LinearLayout col) {
        HorizontalScrollView h = new HorizontalScrollView(this);
        h.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.row(this);
        h.addView(row);
        col.addView(h, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void addChip(LinearLayout row, String label, Runnable r) {
        Button b = Ui.chip(this, label);
        b.setOnClickListener(v -> r.run());
        row.addView(b);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void save() {
        String n = nameBox.getText().toString().trim();
        face.name = n.isEmpty() ? "My face" : n;
        FaceStore.upsert(this, face);
        toast("Saved");
    }

    // ----- photo from gallery -----

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
                if (path == null) { toast("Could not open that photo"); status.setText(""); return; }
                face.bgImage = path;
                fv.reloadBg();
                status.setText("Photo set. Use Darker to save battery.");
            });
        }).start();
    }

    /** Copies the photo into private storage, scaled down to about 1024px, as JPEG. */
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

    // ----- FaceView.Listener -----

    @Override
    public void onSelect(int index) {
        if (index < 0) {
            status.setText("Tap an item to select, drag to move");
        } else {
            String type = face.elements.get(index).type;
            status.setText("Selected: " + type + ". Drag to move.");
        }
    }

    @Override
    public void onChanged() {
        // Positions are already updated in the face object.
    }
}
