package com.xaviourzone.wristarcade;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;

/**
 * Neon Pong. Player 1 is the bottom paddle, Player 2 / CPU the top paddle.
 * Drag anywhere in your half to move. First to 5.
 */
class Pong extends Game {

    static final int TARGET = 5;
    static final float PW = 0.24f, PH = 0.025f, BALL = 0.022f;
    static final float Y1 = 0.90f, Y2 = 0.10f;

    float p1 = 0.5f, p2 = 0.5f;          // paddle centres (fractions)
    float bx, by, vx, vy;                // ball (fractions, per second)
    final int[] score = new int[2];
    float serve;                         // seconds until serve
    int serveTo = P1;

    Pong() { reset(); }

    @Override String name() { return "Pong"; }
    @Override boolean animated() { return true; }
    @Override boolean cpuTurn() { return false; }

    @Override
    void reset() {
        score[0] = score[1] = 0;
        p1 = p2 = 0.5f;
        serveTo = starter;
        over = false; winner = NONE; turn = starter;
        center();
    }

    private void center() {
        bx = 0.5f; by = 0.5f; vx = 0; vy = 0;
        serve = 1.0f;
    }

    @Override
    String status() {
        String s = (vsCpu ? "You " : "P1 ") + score[P1] + " – " + score[P2] + (vsCpu ? " CPU" : " P2");
        if (over) return super.status() + " • " + s;
        return s;
    }

    @Override
    void tick(float dt) {
        if (over || dt <= 0) return;
        // CPU paddle
        if (vsCpu) {
            float speed = level == 0 ? 0.55f : level == 1 ? 0.85f : 1.25f;
            float target = vy < 0 ? bx : 0.5f;
            if (level < 2 && vy < 0 && by > 0.6f) target = 0.5f; // reacts late on easier levels
            float d = target - p2;
            float step = speed * dt;
            p2 += Math.max(-step, Math.min(step, d));
            p2 = clamp(p2, PW / 2, 1 - PW / 2);
        }
        if (serve > 0) {
            serve -= dt;
            if (serve <= 0) {
                double ang = (rnd.nextDouble() - 0.5) * 0.9;
                float sp = 0.55f;
                vx = (float) Math.sin(ang) * sp;
                vy = (float) Math.cos(ang) * sp * (serveTo == P1 ? 1 : -1);
            }
            return;
        }
        bx += vx * dt;
        by += vy * dt;
        if (bx < BALL) { bx = BALL; vx = Math.abs(vx); }
        if (bx > 1 - BALL) { bx = 1 - BALL; vx = -Math.abs(vx); }
        // bottom paddle
        if (vy > 0 && by + BALL >= Y1 - PH / 2 && by < Y1 + PH && Math.abs(bx - p1) <= PW / 2 + BALL) {
            bounce(p1, -1);
            by = Y1 - PH / 2 - BALL;
        }
        // top paddle
        if (vy < 0 && by - BALL <= Y2 + PH / 2 && by > Y2 - PH && Math.abs(bx - p2) <= PW / 2 + BALL) {
            bounce(p2, 1);
            by = Y2 + PH / 2 + BALL;
        }
        if (by > 1.02f) point(P2);
        else if (by < -0.02f) point(P1);
    }

    private void bounce(float paddle, int dir) {
        float off = (bx - paddle) / (PW / 2);           // -1..1
        float speed = (float) Math.min(1.6, Math.hypot(vx, vy) * 1.06);
        double ang = off * 1.0;                          // up to ~57 degrees
        vx = (float) Math.sin(ang) * speed;
        vy = (float) Math.cos(ang) * speed * dir;
    }

    private void point(int pl) {
        score[pl]++;
        if (score[pl] >= TARGET) {
            end(pl);
            return;
        }
        serveTo = 1 - pl;
        center();
    }

    @Override
    boolean onTouch(MotionEvent e, float W) {
        for (int i = 0; i < e.getPointerCount(); i++) {
            float x = e.getX(i) / W, y = e.getY(i) / W;
            if (y > 0.5f) p1 = clamp(x, PW / 2, 1 - PW / 2);
            else if (!vsCpu) p2 = clamp(x, PW / 2, 1 - PW / 2);
        }
        return true;
    }

    @Override
    void draw(Canvas c, float W) {
        panel(c, new RectF(W * 0.01f, W * 0.01f, W * 0.99f, W * 0.99f), W * 0.06f);
        // centre line
        for (float x = 0.05f; x < 0.96f; x += 0.06f) line(c, W * x, W / 2, W * (x + 0.03f), W / 2, W * 0.004f, GRID, false);
        text(c, String.valueOf(score[P2]), W * 0.9f, W * 0.42f, W * 0.08f, (MAGENTA & 0x00FFFFFF) | 0x88000000, true);
        text(c, String.valueOf(score[P1]), W * 0.9f, W * 0.58f, W * 0.08f, (CYAN & 0x00FFFFFF) | 0x88000000, true);
        line(c, W * (p1 - PW / 2), W * Y1, W * (p1 + PW / 2), W * Y1, W * PH, CYAN, true);
        line(c, W * (p2 - PW / 2), W * Y2, W * (p2 + PW / 2), W * Y2, W * PH, MAGENTA, true);
        disc(c, W * bx, W * by, W * BALL, WHITE);
        if (over) {
            text(c, winner == P1 ? (vsCpu ? "YOU WIN" : "P1 WINS") : (vsCpu ? "CPU WINS" : "P2 WINS"), W / 2, W * 0.42f, W * 0.08f, GOLD, true);
            text(c, "tap to play again", W / 2, W * 0.58f, W * 0.04f, WHITE, false);
        } else if (serve > 0 && score[0] + score[1] == 0) {
            text(c, "drag in your half", W / 2, W * 0.62f, W * 0.04f, 0xAAFFFFFF, false);
        }
    }

    @Override boolean tap(float x, float y, float W) { return false; }
    @Override Object think() { return null; }
    @Override void play(Object move) {}

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
