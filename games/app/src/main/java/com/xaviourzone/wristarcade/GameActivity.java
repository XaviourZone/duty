package com.xaviourzone.wristarcade;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One game: top bar, status line, the square board, and a running scoreboard. */
public class GameActivity extends Activity implements GameView.Listener {

    private GameView view;
    private TextView status, tally;
    private final int[] wins = new int[3]; // P1, P2, draws
    private boolean counted;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int idx = getIntent().getIntExtra("game", 0);
        Game g = Games.create(idx);
        g.vsCpu = getIntent().getBooleanExtra("vsCpu", true);
        g.level = getIntent().getIntExtra("level", 1);
        g.reset();

        LinearLayout col = Ui.column(this, 6);
        col.setBackground(Ui.screenBg());

        LinearLayout top = Ui.row(this);
        Button back = Ui.chip(this, "←");
        back.setOnClickListener(v -> finish());
        top.addView(back);
        TextView title = Ui.text(this, g.name(), 15, 0xFFFFFFFF, true);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button again = Ui.chip(this, "⟳ New");
        again.setOnClickListener(v -> { counted = false; view.restart(); });
        top.addView(again);
        col.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        status = Ui.text(this, "", 13, 0xFFFFFFFF, true);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 2));
        col.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        view = new GameView(this);
        view.setListener(this);
        col.addView(view, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        tally = Ui.text(this, "", 11, Ui.GREY, false);
        tally.setGravity(Gravity.CENTER);
        col.addView(tally, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(col);
        view.setGame(g);
    }

    @Override
    public void onGameChanged() {
        Game g = view.getGame();
        if (g == null) return;
        status.setText(g.status());
        int color = g.over ? (g.winner == Game.DRAW ? 0xFFFFFFFF : Game.color(g.winner)) : Game.color(g.turn);
        if (g.animated() && !g.over) color = 0xFFFFFFFF;
        status.setTextColor(color);
        if (g.over && !counted) {
            counted = true;
            wins[g.winner == Game.DRAW ? 2 : g.winner]++;
            buzz();
        } else if (!g.over) {
            counted = false;
        }
        tally.setText(g.who(Game.P1) + " " + wins[0] + "  •  " + g.who(Game.P2) + " " + wins[1]
                + "  •  Draws " + wins[2] + (g.over ? "   (tap board to play again)" : ""));
    }

    @SuppressWarnings("deprecation")
    private void buzz() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(120);
        } catch (Exception ignored) {
        }
    }
}
