package com.xaviourzone.powerface;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Gallery: your faces + 100 designs, filter by family / theme / favourites. */
public class FacesActivity extends Activity {

    private static final String ALL = "All", MINE = "Mine", FAVS = "♥ Favourites";

    private String category = ALL;
    private String theme = ALL;
    private LinearLayout catRow, themeRow;
    private TextView count;
    private final List<Face> shown = new ArrayList<>();
    private Adapter adapter;
    private String currentId;
    private Set<String> favs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (getIntent().getBooleanExtra("mine", false)) category = MINE;

        LinearLayout root = Ui.column(this, 8);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackground(Ui.screenBg());

        LinearLayout top = Ui.row(this);
        TextView title = Ui.text(this, "Faces", 20, 0xFFFFFFFF, true);
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button create = Ui.chip(this, "+ New");
        create.setBackground(Ui.accent(this));
        create.setOnClickListener(v -> startActivity(new Intent(this, EditorActivity.class)));
        top.addView(create);
        root.addView(top, fullWidth());

        catRow = scrollRow(root);
        themeRow = scrollRow(root);
        count = Ui.text(this, "", 11, Ui.GREY, false);
        count.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), 0, Ui.dp(this, 2));
        root.addView(count, fullWidth());

        GridView grid = new GridView(this);
        int cols = Math.max(2, getResources().getDisplayMetrics().widthPixels / Ui.dp(this, 170));
        grid.setNumColumns(cols);
        grid.setVerticalSpacing(Ui.dp(this, 8));
        grid.setHorizontalSpacing(Ui.dp(this, 8));
        grid.setSelector(android.R.color.transparent);
        adapter = new Adapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> openPreview(shown.get(pos)));
        root.addView(grid, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout scrollRow(LinearLayout parent) {
        HorizontalScrollView h = new HorizontalScrollView(this);
        h.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 6), 0, 0);
        h.addView(row);
        parent.addView(h, fullWidth());
        return row;
    }

    private void refresh() {
        currentId = FaceStore.current(this).id;
        favs = FaceStore.favourites(this);

        catRow.removeAllViews();
        List<String> cats = new ArrayList<>();
        cats.add(ALL); cats.add(MINE); cats.add(FAVS);
        for (String l : Designs.LAYOUTS) cats.add(l);
        for (final String c : cats) {
            Button chip = Ui.chip(this, c);
            if (c.equals(category)) chip.setBackground(Ui.accent(this));
            chip.setOnClickListener(v -> { category = c; refresh(); });
            catRow.addView(chip);
        }
        themeRow.removeAllViews();
        List<String> themes = new ArrayList<>();
        themes.add(ALL);
        for (Designs.Theme t : Designs.THEMES) themes.add(t.name);
        for (final String t : themes) {
            Button chip = Ui.chip(this, t.equals(ALL) ? "All colours" : t);
            if (t.equals(theme)) chip.setBackground(Ui.accent(this));
            chip.setOnClickListener(v -> { theme = t; refresh(); });
            themeRow.addView(chip);
        }

        shown.clear();
        for (Face f : FaceStore.all(this)) {
            boolean catOk = ALL.equals(category)
                    || (MINE.equals(category) && !f.isBuiltIn())
                    || (FAVS.equals(category) && favs.contains(f.id))
                    || category.equals(f.category);
            boolean themeOk = ALL.equals(theme) || !f.isBuiltIn() || f.name.endsWith(theme);
            if (catOk && themeOk) shown.add(f);
        }
        count.setText(shown.isEmpty()
                ? (MINE.equals(category) ? "No faces of your own yet. Tap + New, or open a design and tap Customize."
                : FAVS.equals(category) ? "No favourites yet. Open a face and tap ♡." : "Nothing here")
                : shown.size() + " faces • tap one to preview");
        adapter.notifyDataSetChanged();
    }

    private void openPreview(final Face f) {
        LinearLayout box = Ui.column(this, 12);
        FaceView big = new FaceView(this);
        big.setFace(f);
        PowerUtils.fillPreview(this, big);
        int side = (int) (Ui.screenSide(this) * 0.62f);
        box.addView(big, new LinearLayout.LayoutParams(side, side));
        TextView name = Ui.text(this, f.name, 15, 0xFFFFFFFF, true);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        box.addView(name, fullWidth());

        final AlertDialog[] dlg = new AlertDialog[1];
        Button apply = Ui.button(this, "⚡ Apply", true);
        apply.setOnClickListener(v -> {
            FaceStore.setCurrent(this, f.id);
            dlg[0].dismiss();
            startActivity(new Intent(this, AodActivity.class));
        });
        box.addView(apply);

        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rlp = fullWidth();
        rlp.topMargin = Ui.dp(this, 8);
        box.addView(row, rlp);

        Button edit = Ui.chip(this, f.isBuiltIn() ? "Customize" : "Edit");
        edit.setOnClickListener(v -> {
            dlg[0].dismiss();
            Intent i = new Intent(this, EditorActivity.class);
            i.putExtra("id", f.id);
            startActivity(i);
        });
        row.addView(edit);

        final boolean fav = favs.contains(f.id);
        Button heart = Ui.chip(this, fav ? "♥ Saved" : "♡ Favourite");
        heart.setOnClickListener(v -> {
            FaceStore.setFavourite(this, f.id, !fav);
            dlg[0].dismiss();
            refresh();
        });
        row.addView(heart);

        Button use = Ui.chip(this, "Select");
        use.setOnClickListener(v -> {
            FaceStore.setCurrent(this, f.id);
            dlg[0].dismiss();
            refresh();
        });
        row.addView(use);

        if (!f.isBuiltIn()) {
            LinearLayout row2 = Ui.row(this);
            row2.setGravity(Gravity.CENTER);
            box.addView(row2, rlp);
            Button copy = Ui.chip(this, "Copy");
            copy.setOnClickListener(v -> {
                Face c = f.copy(FaceStore.newId());
                c.name = f.name + " copy";
                FaceStore.upsert(this, c);
                dlg[0].dismiss();
                refresh();
            });
            row2.addView(copy);
            Button del = Ui.chip(this, "🗑 Delete");
            del.setTextColor(0xFFFF6B6B);
            del.setOnClickListener(v -> new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setMessage("Delete \"" + f.name + "\"?")
                    .setPositiveButton("Delete", (d, w) -> {
                        FaceStore.delete(this, f.id);
                        dlg[0].dismiss();
                        refresh();
                    })
                    .setNegativeButton("Cancel", null).show());
            row2.addView(del);
        }

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(box);
        dlg[0] = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
                .setView(sv)
                .create();
        dlg[0].show();
    }

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            LinearLayout cell;
            FaceView fv;
            TextView name;
            if (convert == null) {
                cell = Ui.column(FacesActivity.this, 6);
                cell.setLayoutParams(new AbsListView.LayoutParams(
                        AbsListView.LayoutParams.MATCH_PARENT, AbsListView.LayoutParams.WRAP_CONTENT));
                fv = new FaceView(FacesActivity.this);
                fv.setThumbnail(true);
                fv.demoData = true;
                fv.setId(View.generateViewId());
                cell.addView(fv, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                name = Ui.text(FacesActivity.this, "", 11, 0xFFFFFFFF, false);
                name.setGravity(Gravity.CENTER);
                name.setSingleLine(true);
                name.setPadding(0, Ui.dp(FacesActivity.this, 4), 0, 0);
                cell.addView(name, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                cell.setTag(new Object[]{fv, name});
            } else {
                cell = (LinearLayout) convert;
            }
            Object[] tag = (Object[]) cell.getTag();
            fv = (FaceView) tag[0];
            name = (TextView) tag[1];
            Face f = shown.get(pos);
            fv.setFace(f);
            boolean cur = f.id.equals(currentId);
            boolean fav = favs.contains(f.id);
            name.setText((cur ? "✓ " : "") + (fav ? "♥ " : "") + f.name);
            cell.setBackground(cur ? Ui.accent(FacesActivity.this) : Ui.glass(FacesActivity.this, 18));
            if (cur) ((android.graphics.drawable.GradientDrawable) cell.getBackground())
                    .setCornerRadius(Ui.dp(FacesActivity.this, 18));
            return cell;
        }
    }
}
