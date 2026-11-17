package com.xaviourzone.wristarcade;

import android.graphics.Canvas;
import android.graphics.RectF;

import java.util.Arrays;

/** Five in a row on an 11x11 board. */
class Gomoku extends Game {

    static final int N = 11;
    final int[] b = new int[N * N];
    int last = -1;
    int[] winCells;

    Gomoku() { reset(); }

    @Override String name() { return "Gomoku"; }

    @Override
    void reset() {
        Arrays.fill(b, -1);
        turn = starter; over = false; winner = NONE; last = -1; winCells = null;
    }

    private float cell(float W) { return W * 0.9f / N; }
    private float off(float W) { return W * 0.05f; }

    @Override
    void draw(Canvas c, float W) {
        float cs = cell(W), o = off(W);
        panel(c, new RectF(o - cs * 0.1f, o - cs * 0.1f, o + cs * N + cs * 0.1f, o + cs * N + cs * 0.1f), cs * 0.4f);
        for (int i = 0; i < N; i++) {
            float pos = o + cs * (i + 0.5f);
            line(c, pos, o + cs * 0.5f, pos, o + cs * (N - 0.5f), W * 0.003f, GRID, false);
            line(c, o + cs * 0.5f, pos, o + cs * (N - 0.5f), pos, W * 0.003f, GRID, false);
        }
        disc(c, o + cs * (N / 2 + 0.5f), o + cs * (N / 2 + 0.5f), cs * 0.08f, 0x66FFFFFF);
        for (int i = 0; i < N * N; i++) {
            if (b[i] == -1) continue;
            float cx = o + cs * (i % N + 0.5f), cy = o + cs * (i / N + 0.5f);
            disc(c, cx, cy, cs * 0.4f, color(b[i]));
            if (i == last) ring(c, cx, cy, cs * 0.14f, cs * 0.06f, 0xAAFFFFFF, false);
        }
        if (winCells != null) {
            for (int i : winCells) ring(c, o + cs * (i % N + 0.5f), o + cs * (i / N + 0.5f), cs * 0.46f, cs * 0.07f, GOLD, true);
        }
    }

    @Override
    boolean tap(float x, float y, float W) {
        float cs = cell(W), o = off(W);
        int col = (int) Math.floor((x - o) / cs), row = (int) Math.floor((y - o) / cs);
        if (col < 0 || col >= N || row < 0 || row >= N) return false;
        int i = row * N + col;
        if (b[i] != -1) return false;
        place(i);
        return true;
    }

    void place(int i) {
        b[i] = turn;
        last = i;
        int[] w = five(b, i);
        if (w != null) { winCells = w; end(turn); return; }
        boolean full = true;
        for (int v : b) if (v == -1) { full = false; break; }
        if (full) { end(DRAW); return; }
        next();
    }

    static final int[][] DIRS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

    static int[] five(int[] s, int i) {
        int pl = s[i], r = i / N, c = i % N;
        for (int[] d : DIRS) {
            int[] cells = new int[9];
            int n = 0;
            for (int k = -4; k <= 4; k++) {
                int rr = r + d[0] * k, cc = c + d[1] * k;
                if (rr >= 0 && rr < N && cc >= 0 && cc < N && s[rr * N + cc] == pl) {
                    cells[n++] = rr * N + cc;
                    if (n == 5) return Arrays.copyOf(cells, 5);
                } else {
                    n = 0;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ AI (pattern scoring)

    @Override
    Object think() {
        int[] s = b.clone();
        int me = turn;
        boolean empty = true;
        for (int v : s) if (v != -1) { empty = false; break; }
        if (empty) return (N / 2) * N + N / 2;
        double defence = level == 0 ? 0.5 : level == 1 ? 0.8 : 0.9;
        java.util.List<double[]> cands = new java.util.ArrayList<>();
        for (int i = 0; i < N * N; i++) {
            if (s[i] != -1 || !near(s, i)) continue;
            double sc = potential(s, i, me) + potential(s, i, 1 - me) * defence;
            if (level == 0) sc *= 0.6 + rnd.nextDouble() * 0.8;
            else sc += rnd.nextDouble();
            cands.add(new double[]{sc, i});
        }
        if (cands.isEmpty()) return null;
        java.util.Collections.sort(cands, (x, y) -> Double.compare(y[0], x[0]));
        if (level == 2 && cands.get(0)[0] < 900_000) {
            // Look one move ahead for forks: two ways to make five at once wins the game.
            int limit = Math.min(12, cands.size());
            for (int k = 0; k < limit; k++) {
                int i = (int) cands.get(k)[1];
                s[i] = me;
                int mine = threats(s, me);
                s[i] = 1 - me;
                int theirs = threats(s, 1 - me);
                s[i] = -1;
                if (mine >= 2) cands.get(k)[0] += 500_000;
                if (theirs >= 2) cands.get(k)[0] += 300_000;
            }
            java.util.Collections.sort(cands, (x, y) -> Double.compare(y[0], x[0]));
        }
        return (int) cands.get(0)[1];
    }

    /** Number of empty cells where pl would complete five right now. */
    private static int threats(int[] s, int pl) {
        int n = 0;
        for (int j = 0; j < N * N; j++) {
            if (s[j] != -1) continue;
            s[j] = pl;
            if (five(s, j) != null) n++;
            s[j] = -1;
        }
        return n;
    }

    private static boolean near(int[] s, int i) {
        int r = i / N, c = i % N;
        for (int dr = -2; dr <= 2; dr++) for (int dc = -2; dc <= 2; dc++) {
            int rr = r + dr, cc = c + dc;
            if (rr >= 0 && rr < N && cc >= 0 && cc < N && s[rr * N + cc] != -1) return true;
        }
        return false;
    }

    static final double[] WINDOW = {0, 1, 12, 150, 2_500, 1_000_000};

    /**
     * How good it is for pl to play at i: every 5-cell window through i that the
     * opponent has not blocked scores by how many of pl's stones it would hold.
     * Windows also see gaps like X X _ X X.
     */
    static double potential(int[] s, int i, int pl) {
        int r = i / N, c = i % N;
        double total = 0;
        for (int[] d : DIRS) {
            for (int startK = -4; startK <= 0; startK++) {
                int cnt = 0;
                boolean blocked = false;
                for (int k = startK; k < startK + 5; k++) {
                    int rr = r + d[0] * k, cc = c + d[1] * k;
                    if (rr < 0 || rr >= N || cc < 0 || cc >= N) { blocked = true; break; }
                    int v = k == 0 ? pl : s[rr * N + cc];
                    if (v == 1 - pl) { blocked = true; break; }
                    if (v == pl) cnt++;
                }
                if (!blocked) total += WINDOW[Math.min(5, cnt)];
            }
        }
        return total;
    }

    @Override
    void play(Object move) {
        int i = (Integer) move;
        if (i >= 0 && b[i] == -1) place(i);
    }
}
