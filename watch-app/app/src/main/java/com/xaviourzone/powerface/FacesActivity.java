package com.xaviourzone.powerface;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** "Customize": every saved face with Apply / Edit / Copy / Delete. */
public class FacesActivity extends Activity {

    private LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Ui.screenBg());
        LinearLayout col = Ui.column(this, 16);
        scroll.addView(col);

        col.addView(Ui.text(this, "Your faces", 20, 0xFFFFFFFF, true));

        Button create = Ui.button(this, "+  Create new face", true);
        create.setOnClickListener(v -> startActivity(new Intent(this, EditorActivity.class)));
        col.addView(create);

        list = Ui.column(this, 0);
        col.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuild();
    }

    private void rebuild() {
        list.removeAllViews();
        List<Face> faces = FaceStore.load(this);
        String current = FaceStore.current(this).id;
        int thumb = Math.min((int) (Ui.screenSide(this) * 0.5f), Ui.dp(this, 200));

        for (final Face f : faces) {
            boolean inUse = f.id.equals(current);
            LinearLayout card = Ui.column(this, 12);
            card.setBackground(inUse ? Ui.accent(this) : Ui.glass(this, 26));
            if (inUse) ((android.graphics.drawable.GradientDrawable) card.getBackground())
                    .setCornerRadius(Ui.dp(this, 26));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(this, 12);
            list.addView(card, lp);

            FaceView fv = new FaceView(this);
            fv.setThumbnail(true);
            fv.setFace(f);
            PowerUtils.fillPreview(this, fv);
            card.addView(fv, new LinearLayout.LayoutParams(thumb, thumb));
            fv.setOnClickListener(v -> apply(f));

            TextView name = Ui.text(this, (inUse ? "✓ " : "") + f.name, 15, 0xFFFFFFFF, true);
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 4));
            card.addView(name);

            LinearLayout row = Ui.row(this);
            row.setGravity(Gravity.CENTER);
            card.addView(row);
            Button apply = Ui.chip(this, "Apply");
            apply.setOnClickListener(v -> apply(f));
            Button edit = Ui.chip(this, "Edit");
            edit.setOnClickListener(v -> {
                Intent i = new Intent(this, EditorActivity.class);
                i.putExtra("id", f.id);
                startActivity(i);
            });
            Button copy = Ui.chip(this, "Copy");
            copy.setOnClickListener(v -> {
                Face c = f.copy(FaceStore.newId());
                c.name = f.name + " copy";
                FaceStore.upsert(this, c);
                rebuild();
            });
            Button del = Ui.chip(this, "Delete");
            del.setOnClickListener(v -> confirmDelete(f));
            row.addView(apply);
            row.addView(edit);
            row.addView(copy);
            row.addView(del);
        }
    }

    private void apply(Face f) {
        FaceStore.setCurrent(this, f.id);
        startActivity(new Intent(this, AodActivity.class));
    }

    private void confirmDelete(Face f) {
        if (FaceStore.load(this).size() <= 1) {
            Toast.makeText(this, "Keep at least one face", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setMessage("Delete \"" + f.name + "\"?")
                .setPositiveButton("Delete", (d, w) -> { FaceStore.delete(this, f.id); rebuild(); })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
