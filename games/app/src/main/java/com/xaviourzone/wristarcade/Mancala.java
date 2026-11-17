package com.xaviourzone.wristarcade;

import android.graphics.Canvas;
import android.graphics.RectF;

/**
 * Mancala (Kalah, 6 pits x 4 seeds).
 * Pits 0..5 = Player 1 (bottom, left to right), 6 = Player 1 store (right),
 * pits 7..12 = Player 2 (top, right to left), 13 = Player 2 store (left).
 * Last seed in your store = go again. Last seed in an empty pit of yours captures the opposite pit.
 */
class Mancala extends Game {

    final int[] b = new int[14];
    int last = -1;
    String note = "";

    Mancala() { reset(); }

    @Override String name() { return "Mancala"; }

    @Override
    void reset() {
        for (int i = 0; i < 14; i++) b[i] = (i == 6 || i == 13) ? 0 : 4;
        turn = starter; over = false; winner = NONE; last = -1; note = "";
    }

    @Override
    String status() {
        String s = " • " + b[6] + "–" + b[13];
        if (over) return super.status() + s;
        return (note.isEmpty() ? "" : note + " ") + super.status() + s;
    }

    /** Centre of a pit / store in pixels. */
    private float[] pos(int i, float W) {
        if (i >= 0 && i <= 5) return new float[]{W * (0.2f + 0.12f * i), W * 0.66f};
        if (i >= 7 && i <= 12) return new float[]{W * (0.2f + 0.12f * (12 - i)), W * 0.34f};
        if (i == 6) return new float[]{W * 0.932f, W * 0.5f};
        return new float[]{W * 0.068f, W * 0.5f};
    }

    @Override
    void draw(Canvas c, float W) {
        panel(c, new RectF(W * 0.02f, W * 0.18f, W * 0.98f, W * 0.82f), W * 0.08f);
        // stores
        for (int st : new int[]{6, 13}) {
            float[] p0 = pos(st, W);
            int pl = st == 6 ? P1 : P2;
            RectF r = new RectF(p0[0] - W * 0.045f, W * 0.24f, p0[0] + W * 0.045f, W * 0.76f);
            fillRect(c, r, W * 0.05f, (color(pl) & 0x00FFFFFF) | 0x30000000);
            text(c, String.valueOf(b[st]), p0[0], p0[1], W * 0.06f, color(pl), true);
        }
        boolean[] legal = new boolean[14];
        if (!over && !(vsCpu && turn == P2)) for (int i : pitsOf(turn)) legal[i] = b[i] > 0;
        for (int i = 0; i < 14; i++) {
            if (i == 6 || i == 13) continue;
            float[] p0 = pos(i, W);
            int pl = i < 6 ? P1 : P2;
            float r = W * 0.055f;
            ring(c, p0[0], p0[1], r, W * 0.006f, legal[i] ? color(pl) : GRID, legal[i]);
            if (i == last) ring(c, p0[0], p0[1], r * 1.2f, W * 0.004f, GOLD, false);
            // seeds as small dots in a circle (up to 12), then the count
            int n = Math.min(b[i], 12);
            for (int k = 0; k < n; k++) {
                double a = Math.PI * 2 * k / Math.max(1, n);
                float rr = n == 1 ? 0 : r * 0.55f;
                disc(c, p0[0] + (float) Math.cos(a) * rr, p0[1] + (float) Math.sin(a) * rr, r * 0.13f,
                        (color(pl) & 0x00FFFFFF) | 0x99000000);
            }
            text(c, String.valueOf(b[i]), p0[0], p0[1] + (i < 6 ? r * 1.6f : -r * 1.6f), W * 0.04f, WHITE, true);
        }
        text(c, vsCpu ? "CPU" : "P2", W / 2, W * 0.12f, W * 0.04f, MAGENTA, true);
        text(c, vsCpu ? "YOU" : "P1", W / 2, W * 0.88f, W * 0.04f, CYAN, true);
    }

    static int[] pitsOf(int pl) {
        return pl == P1 ? new int[]{0, 1, 2, 3, 4, 5} : new int[]{7, 8, 9, 10, 11, 12};
    }

    @Override
    boolean tap(float x, float y, float W) {
        for (int i : pitsOf(turn)) {
            float[] p0 = pos(i, W);
            if (Math.hypot(x - p0[0], y - p0[1]) < W * 0.06f && b[i] > 0) {
                move(i);
                return true;
            }
        }
        return false;
    }

    /** Sow from pit i for player pl. Returns true if pl moves again. */
    static boolean sow(int[] s, int i, int pl) {
        int seeds = s[i];
        s[i] = 0;
        int j = i;
        int skip = pl == P1 ? 13 : 6;
        while (seeds > 0) {
            j = (j + 1) % 14;
            if (j == skip) continue;
            s[j]++;
            seeds--;
        }
        int store = pl == P1 ? 6 : 13;
        if (j == store) return true;
        boolean mine = pl == P1 ? j <= 5 : (j >= 7 && j <= 12);
        if (mine && s[j] == 1 && s[12 - j] > 0) {
            s[store] += s[12 - j] + 1;
            s[12 - j] = 0;
            s[j] = 0;
        }
        return false;
    }

    static boolean sideEmpty(int[] s, int pl) {
        for (int i : pitsOf(pl)) if (s[i] > 0) return false;
        return true;
    }

    static void sweep(int[] s) {
        for (int i = 0; i <= 5; i++) { s[6] += s[i]; s[i] = 0; }
        for (int i = 7; i <= 12; i++) { s[13] += s[i]; s[i] = 0; }
    }

    void move(int i) {
        boolean again = sow(b, i, turn);
        last = i;
        note = again ? who(turn) + " go again •" : "";
        if (sideEmpty(b, P1) || sideEmpty(b, P2)) {
            sweep(b);
            end(b[6] > b[13] ? P1 : b[13] > b[6] ? P2 : DRAW);
            return;
        }
        if (!again) next();
    }

    // ------------------------------------------------------------------ AI

    @Override
    Object think() {
        int[] s = b.clone();
        int me = turn;
        int depth = level == 0 ? 1 : level == 1 ? 4 : 8;
        int best = -1, bs = Integer.MIN_VALUE;
        for (int i : pitsOf(me)) {
            if (s[i] == 0) continue;
            int[] t = s.clone();
            boolean again = sow(t, i, me);
            int sc = again ? search(t, me, me, depth - 1, Integer.MIN_VALUE + 1, Integer.MAX_VALUE)
                    : search(t, 1 - me, me, depth - 1, Integer.MIN_VALUE + 1, Integer.MAX_VALUE);
            if (level == 0) sc += rnd.nextInt(6);
            if (sc > bs || (sc == bs && rnd.nextBoolean())) { bs = sc; best = i; }
        }
        return best;
    }

    /** Minimax with alpha-beta, score = my store - their store. */
    private int search(int[] s, int toMove, int me, int depth, int alpha, int beta) {
        if (sideEmpty(s, P1) || sideEmpty(s, P2)) {
            int[] t = s.clone();
            sweep(t);
            return score(t, me) * 10;
        }
        if (depth <= 0) return score(s, me);
        boolean max = toMove == me;
        int best = max ? Integer.MIN_VALUE + 1 : Integer.MAX_VALUE;
        for (int i : pitsOf(toMove)) {
            if (s[i] == 0) continue;
            int[] t = s.clone();
            boolean again = sow(t, i, toMove);
            int sc = search(t, again ? toMove : 1 - toMove, me, depth - 1, alpha, beta);
            if (max) { best = Math.max(best, sc); alpha = Math.max(alpha, best); }
            else { best = Math.min(best, sc); beta = Math.min(beta, best); }
            if (alpha >= beta) break;
        }
        return best;
    }

    static int score(int[] s, int me) {
        return me == P1 ? s[6] - s[13] : s[13] - s[6];
    }

    @Override
    void play(Object move) {
        int i = (Integer) move;
        if (i >= 0 && b[i] > 0) move(i);
    }
}
