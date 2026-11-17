package com.xaviourzone.wristarcade;

import android.graphics.Canvas;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class TicTacToe extends Game {

    static final int[][] LINES = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};

    final int[] b = new int[9];
    int[] winLine;

    TicTacToe() { reset(); }

    @Override String name() { return "Tic-Tac-Toe"; }

    @Override
    void reset() {
        Arrays.fill(b, -1);
        turn = starter;
        over = false;
        winner = NONE;
        winLine = null;
    }

    @Override
    void draw(Canvas c, float W) {
        float m = W * 0.1f, cell = (W - 2 * m) / 3;
        panel(c, new RectF(m * 0.5f, m * 0.5f, W - m * 0.5f, W - m * 0.5f), W * 0.06f);
        for (int i = 1; i < 3; i++) {
            line(c, m + cell * i, m + cell * 0.1f, m + cell * i, W - m - cell * 0.1f, W * 0.008f, GRID, false);
            line(c, m + cell * 0.1f, m + cell * i, W - m - cell * 0.1f, m + cell * i, W * 0.008f, GRID, false);
        }
        for (int i = 0; i < 9; i++) {
            float cx = m + cell * (i % 3 + 0.5f), cy = m + cell * (i / 3 + 0.5f), r = cell * 0.3f;
            if (b[i] == P1) {
                line(c, cx - r, cy - r, cx + r, cy + r, W * 0.022f, CYAN, true);
                line(c, cx + r, cy - r, cx - r, cy + r, W * 0.022f, CYAN, true);
            } else if (b[i] == P2) {
                ring(c, cx, cy, r, W * 0.022f, MAGENTA, true);
            }
        }
        if (winLine != null) {
            int a = winLine[0], z = winLine[2];
            line(c, m + cell * (a % 3 + 0.5f), m + cell * (a / 3 + 0.5f),
                    m + cell * (z % 3 + 0.5f), m + cell * (z / 3 + 0.5f), W * 0.014f, GOLD, true);
        }
    }

    @Override
    boolean tap(float x, float y, float W) {
        float m = W * 0.1f, cell = (W - 2 * m) / 3;
        int col = (int) Math.floor((x - m) / cell), row = (int) Math.floor((y - m) / cell);
        if (col < 0 || col > 2 || row < 0 || row > 2) return false;
        int i = row * 3 + col;
        if (b[i] != -1) return false;
        place(i);
        return true;
    }

    void place(int i) {
        b[i] = turn;
        int[] wl = winOf(b);
        if (wl != null) {
            winLine = wl;
            end(turn);
        } else if (full(b)) {
            end(DRAW);
        } else {
            next();
        }
    }

    static int[] winOf(int[] b) {
        for (int[] l : LINES) if (b[l[0]] != -1 && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]]) return l;
        return null;
    }

    static boolean full(int[] b) {
        for (int v : b) if (v == -1) return false;
        return true;
    }

    @Override
    Object think() {
        int[] s = b.clone();
        int me = turn;
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < 9; i++) if (s[i] == -1) free.add(i);
        if (free.isEmpty()) return null;
        // Always take a win.
        for (int i : free) { s[i] = me; boolean w = winOf(s) != null; s[i] = -1; if (w) return i; }
        if (level == 0) {
            // Block sometimes.
            if (rnd.nextInt(2) == 0) for (int i : free) { s[i] = 1 - me; boolean w = winOf(s) != null; s[i] = -1; if (w) return i; }
            return free.get(rnd.nextInt(free.size()));
        }
        if (level == 1 && rnd.nextInt(10) < 3) {
            for (int i : free) { s[i] = 1 - me; boolean w = winOf(s) != null; s[i] = -1; if (w) return i; }
            return free.get(rnd.nextInt(free.size()));
        }
        int best = -1, bestScore = Integer.MIN_VALUE;
        for (int i : free) {
            s[i] = me;
            int sc = -negamax(s, 1 - me, 1);
            s[i] = -1;
            if (sc > bestScore || (sc == bestScore && rnd.nextBoolean())) { bestScore = sc; best = i; }
        }
        return best;
    }

    /** Score from the point of view of player 'pl' to move. */
    private int negamax(int[] s, int pl, int depth) {
        if (winOf(s) != null) return -(10 - depth); // previous player won
        if (full(s)) return 0;
        int best = Integer.MIN_VALUE;
        for (int i = 0; i < 9; i++) {
            if (s[i] != -1) continue;
            s[i] = pl;
            best = Math.max(best, -negamax(s, 1 - pl, depth + 1));
            s[i] = -1;
        }
        return best;
    }

    @Override
    void play(Object move) {
        int i = (Integer) move;
        if (b[i] == -1) place(i);
    }
}
