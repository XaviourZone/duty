package com.xaviourzone.wristarcade;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Game menu: mode (vs CPU / 2 players), difficulty, and the 10 games with live thumbnails. */
public class MainActivity extends Activity {

    private SharedPreferences sp;
    private LinearLayout modeRow, levelRow;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("arcade", MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Ui.screenBg());
        LinearLayout col = Ui.column(this, 12);
        scroll.addView(col);

        col.addView(Ui.text(this, "Wrist Arcade", 22, 0xFFFFFFFF, true));
        col.addView(Ui.text(this, "10 games • vs CPU or 2 players", 12, Ui.GREY, false));

        modeRow = Ui.row(this);
        modeRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 10);
        col.addView(modeRow, lp);
        levelRow = Ui.row(this);
        levelRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = Ui.dp(this, 6);
        col.addView(levelRow, lp2);
        buildChips();

        // Game cards, two per row, each with a live thumbnail of the board.
        int cardW = (Ui.screenSide(this) - Ui.dp(this, 24 + 10)) / 2;
        LinearLayout row = null;
        for (int i = 0; i < Games.NAMES.length; i++) {
            if (i % 2 == 0) {
                row = Ui.row(this);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rl.topMargin = Ui.dp(this, 10);
                col.addView(row, rl);
            }
            final int idx = i;
            LinearLayout card = Ui.column(this, 8);
            card.setBackground(Ui.glass(this, 20));
            GameView thumb = new GameView(this);
            thumb.setThumbnail(true);
            thumb.setGame(Games.create(i));
            int ts = cardW - Ui.dp(this, 16);
            card.addView(thumb, new LinearLayout.LayoutParams(ts, ts));
            TextView name = Ui.text(this, Games.NAMES[i], 13, 0xFFFFFFFF, true);
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, Ui.dp(this, 4), 0, 0);
            card.addView(name);
            TextView blurb = Ui.text(this, Games.BLURBS[i], 10, Ui.GREY, false);
            blurb.setGravity(Gravity.CENTER);
            card.addView(blurb);
            card.setOnClickListener(v -> open(idx));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(cardW, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i % 2 == 0) cl.rightMargin = Ui.dp(this, 10);
            row.addView(card, cl);
        }
        setContentView(scroll);
    }

    private void buildChips() {
        modeRow.removeAllViews();
        levelRow.removeAllViews();
        boolean cpu = sp.getBoolean("vsCpu", true);
        Button a = Ui.chip(this, "🤖 vs CPU");
        Button b = Ui.chip(this, "👥 2 Players");
        if (cpu) a.setBackground(Ui.accent(this)); else b.setBackground(Ui.accent(this));
        a.setOnClickListener(v -> { sp.edit().putBoolean("vsCpu", true).apply(); buildChips(); });
        b.setOnClickListener(v -> { sp.edit().putBoolean("vsCpu", false).apply(); buildChips(); });
        modeRow.addView(a);
        modeRow.addView(b);
        if (!cpu) return;
        String[] levels = {"Easy", "Normal", "Hard"};
        int cur = sp.getInt("level", 1);
        for (int i = 0; i < 3; i++) {
            final int l = i;
            Button c = Ui.chip(this, levels[i]);
            if (i == cur) c.setBackground(Ui.accent(this));
            c.setOnClickListener(v -> { sp.edit().putInt("level", l).apply(); buildChips(); });
            levelRow.addView(c);
        }
    }

    private void open(int game) {
        Intent i = new Intent(this, GameActivity.class);
        i.putExtra("game", game);
        i.putExtra("vsCpu", sp.getBoolean("vsCpu", true));
        i.putExtra("level", sp.getInt("level", 1));
        startActivity(i);
    }
}
