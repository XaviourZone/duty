package com.xaviourzone.powerface;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small helpers for the glass + blue/purple gradient look, built in code. */
final class Ui {

    static final int BLUE = 0xFF2F80ED;
    static final int PURPLE = 0xFF8E2DE2;
    static final int GREY = 0xFF9E9EAE;

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable screenBg() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0B1A3A, 0xFF1E0F45, 0xFF000000});
    }

    static GradientDrawable glass(Context c, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x22FFFFFF);
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(Math.max(1, dp(c, 1)), 0x33FFFFFF);
        return g;
    }

    static GradientDrawable accent(Context c) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{BLUE, PURPLE});
        g.setCornerRadius(dp(c, 100));
        return g;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** Full-width pill button. */
    static Button button(Context c, String label, boolean primary) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(0xFFFFFFFF);
        b.setBackground(primary ? accent(c) : glass(c, 100));
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 50));
        lp.topMargin = dp(c, 10);
        b.setLayoutParams(lp);
        return b;
    }

    /** Small pill used in rows of editor tools. */
    static Button chip(Context c, String label) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(0xFFFFFFFF);
        b.setBackground(glass(c, 100));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(c, 10), 0, dp(c, 10), 0);
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(c, 40));
        lp.rightMargin = dp(c, 6);
        b.setLayoutParams(lp);
        return b;
    }

    static LinearLayout column(Context c, int padDp) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = dp(c, padDp);
        l.setPadding(p, p, p, p);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static void immersive(Activity a) {
        a.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    /** Smaller side of the screen in pixels (the M99 is 720x720). */
    static int screenSide(Context c) {
        int w = c.getResources().getDisplayMetrics().widthPixels;
        int h = c.getResources().getDisplayMetrics().heightPixels;
        return Math.min(w, h);
    }
}
