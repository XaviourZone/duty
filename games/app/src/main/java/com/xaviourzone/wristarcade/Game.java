package com.xaviourzone.wristarcade;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;

import java.util.Random;

/**
 * Base class for every game. Turn-based games implement tap / think / play;
 * real-time games (Pong) override animated / tick / onTouch.
 *
 * Threading: think() runs on a background thread and must only read state
 * (copy what it needs). play() runs on the UI thread and changes state.
 */
abstract class Game {

    static final int P1 = 0, P2 = 1, NONE = -1, DRAW = 2;
    static final int CYAN = 0xFF00E5FF, MAGENTA = 0xFFFF2BD6, WHITE = 0xFFFFFFFF, GRID = 0x40FFFFFF;
    static final int GOLD = 0xFFFFD166, GREEN = 0xFF00FF88;

    boolean vsCpu = true;
    /** 0 easy, 1 normal, 2 hard. */
    int level = 1;
    int turn = P1;
    int starter = P1;
    int winner = NONE;
    boolean over;
    final Random rnd = new Random();
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    abstract String name();

    /** Start a new round. Must set turn = starter and clear over / winner. */
    abstract void reset();

    abstract void draw(Canvas c, float W);

    /** Human tap at (x, y) in pixels of a W x W board. Return true if anything changed. */
    abstract boolean tap(float x, float y, float W);

    /** Pick the computer's move (background thread, read-only). */
    abstract Object think();

    /** Apply the computer's move (UI thread). */
    abstract void play(Object move);

    boolean cpuTurn() { return vsCpu && !over && turn == P2; }

    boolean animated() { return false; }

    void tick(float dt) {}

    /** Real-time games handle raw touches here. */
    boolean onTouch(MotionEvent e, float W) { return false; }

    /** Some games (Memory) need a pause before continuing; return > 0 to wait. */
    long delayMs() { return 0; }

    void afterDelay() {}

    String who(int pl) {
        if (pl == P1) return vsCpu ? "You" : "Player 1";
        return vsCpu ? "CPU" : "Player 2";
    }

    String status() {
        if (over) return winner == DRAW ? "Draw!" : who(winner) + (winner == P1 && vsCpu ? " win! 🎉" : " wins!");
        return who(turn) + (turn == P1 && vsCpu ? "r turn" : "'s turn");
    }

    void end(int w) {
        over = true;
        winner = w;
    }

    void next() { turn = 1 - turn; }

    static int color(int pl) { return pl == P1 ? CYAN : MAGENTA; }

    void newRound() {
        starter = 1 - starter;
        reset();
    }

    // ------------------------------------------------------------------ drawing helpers

    void clean() {
        p.reset();
        p.setAntiAlias(true);
    }

    void disc(Canvas c, float x, float y, float r, int color) {
        clean();
        p.setColor(color);
        p.setShadowLayer(r * 0.7f, 0, 0, color);
        c.drawCircle(x, y, r, p);
        p.clearShadowLayer();
    }

    void ring(Canvas c, float x, float y, float r, float width, int color, boolean glow) {
        clean();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setColor(color);
        if (glow) p.setShadowLayer(width * 1.6f, 0, 0, color);
        c.drawCircle(x, y, r, p);
        p.clearShadowLayer();
    }

    void line(Canvas c, float x1, float y1, float x2, float y2, float width, int color, boolean glow) {
        clean();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(width);
        p.setColor(color);
        if (glow) p.setShadowLayer(width * 1.6f, 0, 0, color);
        c.drawLine(x1, y1, x2, y2, p);
        p.clearShadowLayer();
    }

    void text(Canvas c, String s, float x, float y, float size, int color, boolean bold) {
        clean();
        p.setColor(color);
        p.setTextSize(size);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(bold);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(s, x, y - (fm.ascent + fm.descent) / 2f, p);
    }

    /** Glass panel behind a board. */
    void panel(Canvas c, RectF r, float radius) {
        clean();
        p.setColor(0x1AFFFFFF);
        c.drawRoundRect(r, radius, radius, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, radius * 0.06f));
        p.setColor(0x33FFFFFF);
        c.drawRoundRect(r, radius, radius, p);
    }

    void fillRect(Canvas c, RectF r, float radius, int color) {
        clean();
        p.setColor(color);
        c.drawRoundRect(r, radius, radius, p);
    }

    /** Pulsing highlight used for hints / last move (no animation loop needed). */
    void hint(Canvas c, float x, float y, float r, int color) {
        clean();
        p.setColor(color);
        p.setAlpha(110);
        c.drawCircle(x, y, r, p);
    }
}
