package com.xaviourzone.wristarcade;

import android.graphics.Canvas;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class Reversi extends Game {

    static final int N = 8;
    static final int[] DIRS = {-9, -8, -7, -1, 1, 7, 8, 9};
    static final int[] WEIGHTS = {
            100, -20, 10, 5, 5, 10, -20, 100,
            -20, -50, -2, -2, -2, -2, -50, -20,
            10, -2, 1, 1, 1, 1, -2, 10,
            5, -2, 1, 0, 0, 1, -2, 5,
            5, -2, 1, 0, 0, 1, -2, 5,
            10, -2, 1, 1, 1, 1, -2, 10,
            -20, -50, -2, -2, -2, -2, -50, -20,
            100, -20, 10, 5, 5, 10, -20, 100};

    final int[] b = new int[64];
    int last = -1;
    String note = "";

    Reversi() { reset(); }

    @Override String name() { return "Reversi"; }

    @Override
    void reset() {
        Arrays.fill(b, -1);
        b[27] = P2; b[36] = P2; b[28] = P1; b[35] = P1;
        turn = starter; over = false; winner = NONE; last = -1; note = "";
    }

    private float cell(float W) { return W * 0.86f / N; }
    private float off(float W) { return W * 0.07f; }

    @Override
    void draw(Canvas c, float W) {
        float cs = cell(W), o = off(W);
        panel(c, new RectF(o - cs * 0.15f, o - cs * 0.15f, o + cs * N + cs * 0.15f, o + cs * N + cs * 0.15f), cs * 0.4f);
        for (int i = 1; i < N; i++) {
            line(c, o + cs * i, o, o + cs * i, o + cs * N, W * 0.003f, GRID, false);
            line(c, o, o + cs * i, o + cs * N, o + cs * i, W * 0.003f, GRID, false);
        }
        List<Integer> moves = over ? new ArrayList<Integer>() : moves(b, turn);
        boolean showHints = !(vsCpu && turn == P2);
        for (int i = 0; i < 64; i++) {
            float cx = o + cs * (i % N + 0.5f), cy = o + cs * (i / N + 0.5f);
            if (b[i] != -1) {
                disc(c, cx, cy, cs * 0.38f, color(b[i]));
                if (i == last) ring(c, cx, cy, cs * 0.14f, cs * 0.05f, 0xAAFFFFFF, false);
            } else if (showHints && moves.contains(i)) {
                hint(c, cx, cy, cs * 0.1f, color(turn));
            }
        }
    }

    int count(int pl) {
        int n = 0;
        for (int v : b) if (v == pl) n++;
        return n;
    }

    @Override
    String status() {
        String score = " • " + count(P1) + "–" + count(P2);
        if (over) return super.status() + score;
        return (note.isEmpty() ? "" : note + " ") + super.status() + score;
    }

    @Override
    boolean tap(float x, float y, float W) {
        float cs = cell(W), o = off(W);
        int col = (int) Math.floor((x - o) / cs), row = (int) Math.floor((y - o) / cs);
        if (col < 0 || col >= N || row < 0 || row >= N) return false;
        int i = row * N + col;
        if (flips(b, i, turn).isEmpty()) return false;
        move(i);
        return true;
    }

    static List<Integer> flips(int[] s, int i, int pl) {
        List<Integer> out = new ArrayList<>();
        if (s[i] != -1) return out;
        for (int d : DIRS) {
            List<Integer> run = new ArrayList<>();
            int j = i;
            while (true) {
                int col = j % N;
                j += d;
                if (j < 0 || j >= 64) { run.clear(); break; }
                int nc = j % N;
                if (Math.abs(nc - col) > 1) { run.clear(); break; } // wrapped around a row edge
                if (s[j] == 1 - pl) run.add(j);
                else if (s[j] == pl) break;
                else { run.clear(); break; }
            }
            out.addAll(run);
        }
        return out;
    }

    static List<Integer> moves(int[] s, int pl) {
        List<Integer> m = new ArrayList<>();
        for (int i = 0; i < 64; i++) if (s[i] == -1 && !flips(s, i, pl).isEmpty()) m.add(i);
        return m;
    }

    static void apply(int[] s, int i, int pl) {
        for (int f : flips(s, i, pl)) s[f] = pl;
        s[i] = pl;
    }

    void move(int i) {
        apply(b, i, turn);
        last = i;
        note = "";
        next();
        if (moves(b, turn).isEmpty()) {
            if (moves(b, 1 - turn).isEmpty()) {
                int a = count(P1), z = count(P2);
                end(a > z ? P1 : z > a ? P2 : DRAW);
            } else {
                note = who(turn) + " can't move •";
                next();
            }
        }
    }

    // ------------------------------------------------------------------ AI

    @Override
    Object think() {
        int[] s = b.clone();
        int me = turn;
        List<Integer> ms = moves(s, me);
        if (ms.isEmpty()) return null;
        if (level == 0) {
            if (rnd.nextInt(3) == 0) return ms.get(rnd.nextInt(ms.size()));
            int best = ms.get(0), bs = Integer.MIN_VALUE;
            for (int m : ms) {
                int sc = flips(s, m, me).size() + rnd.nextInt(3);
                if (sc > bs) { bs = sc; best = m; }
            }
            return best;
        }
        int depth = level == 1 ? 1 : 4;
        int best = ms.get(0), bs = Integer.MIN_VALUE;
        for (int m : ms) {
            int[] t = s.clone();
            apply(t, m, me);
            int sc = -search(t, 1 - me, depth - 1, Integer.MIN_VALUE + 1, Integer.MAX_VALUE);
            if (sc > bs || (sc == bs && rnd.nextBoolean())) { bs = sc; best = m; }
        }
        return best;
    }

    private int search(int[] s, int pl, int depth, int alpha, int beta) {
        List<Integer> ms = moves(s, pl);
        if (depth <= 0) return eval(s, pl);
        if (ms.isEmpty()) {
            if (moves(s, 1 - pl).isEmpty()) {
                int d = 0;
                for (int v : s) d += v == pl ? 1 : v == 1 - pl ? -1 : 0;
                return d * 1000;
            }
            return -search(s, 1 - pl, depth - 1, -beta, -alpha);
        }
        int best = Integer.MIN_VALUE + 1;
        for (int m : ms) {
            int[] t = s.clone();
            apply(t, m, pl);
            int sc = -search(t, 1 - pl, depth - 1, -beta, -alpha);
            if (sc > best) best = sc;
            if (best > alpha) alpha = best;
            if (alpha >= beta) break;
        }
        return best;
    }

    static int eval(int[] s, int pl) {
        int sc = 0;
        for (int i = 0; i < 64; i++) {
            if (s[i] == pl) sc += WEIGHTS[i];
            else if (s[i] == 1 - pl) sc -= WEIGHTS[i];
        }
        sc += 5 * (moves(s, pl).size() - moves(s, 1 - pl).size());
        return sc;
    }

    @Override
    void play(Object move) {
        int i = (Integer) move;
        if (!flips(b, i, turn).isEmpty()) move(i);
    }
}
