package com.xaviourzone.wristarcade;

import android.graphics.Canvas;
import android.graphics.RectF;

class ConnectFour extends Game {

    static final int R = 6, C = 7;
    final int[][] b = new int[R][C];
    int[][] winCells;
    int lastR = -1, lastC = -1;

    ConnectFour() { reset(); }

    @Override String name() { return "Connect Four"; }

    @Override
    void reset() {
        for (int[] row : b) java.util.Arrays.fill(row, -1);
        turn = starter; over = false; winner = NONE; winCells = null; lastR = lastC = -1;
    }

    private float cell(float W) { return W * 0.92f / C; }
    private float left(float W) { return W * 0.04f; }
    private float top(float W) { return (W - cell(W) * R) / 2f + W * 0.03f; }

    @Override
    void draw(Canvas c, float W) {
        float cs = cell(W), l = left(W), t = top(W);
        panel(c, new RectF(l - cs * 0.1f, t - cs * 0.1f, l + cs * C + cs * 0.1f, t + cs * R + cs * 0.1f), cs * 0.4f);
        // turn indicator above the board
        if (!over) disc(c, W / 2, t - cs * 0.55f, cs * 0.18f, color(turn));
        for (int r = 0; r < R; r++) {
            for (int col = 0; col < C; col++) {
                float cx = l + cs * (col + 0.5f), cy = t + cs * (r + 0.5f);
                if (b[r][col] == -1) {
                    ring(c, cx, cy, cs * 0.38f, cs * 0.04f, GRID, false);
                } else {
                    disc(c, cx, cy, cs * 0.38f, color(b[r][col]));
                    if (r == lastR && col == lastC) ring(c, cx, cy, cs * 0.18f, cs * 0.04f, 0x99FFFFFF, false);
                }
            }
        }
        if (winCells != null) {
            for (int[] wc : winCells) ring(c, l + cs * (wc[1] + 0.5f), t + cs * (wc[0] + 0.5f), cs * 0.44f, cs * 0.06f, GOLD, true);
        }
    }

    @Override
    boolean tap(float x, float y, float W) {
        int col = (int) Math.floor((x - left(W)) / cell(W));
        if (col < 0 || col >= C || b[0][col] != -1) return false;
        drop(col);
        return true;
    }

    static int rowFor(int[][] s, int col) {
        for (int r = R - 1; r >= 0; r--) if (s[r][col] == -1) return r;
        return -1;
    }

    void drop(int col) {
        int r = rowFor(b, col);
        if (r < 0) return;
        b[r][col] = turn;
        lastR = r; lastC = col;
        int[][] w = winAt(b, r, col);
        if (w != null) { winCells = w; end(turn); }
        else if (fullBoard(b)) end(DRAW);
        else next();
    }

    static boolean fullBoard(int[][] s) {
        for (int col = 0; col < C; col++) if (s[0][col] == -1) return false;
        return true;
    }

    static int[][] winAt(int[][] s, int r, int col) {
        int pl = s[r][col];
        int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int[] d : dirs) {
            int[][] cells = new int[7][];
            int n = 0;
            for (int k = -3; k <= 3; k++) {
                int rr = r + d[0] * k, cc = col + d[1] * k;
                if (rr >= 0 && rr < R && cc >= 0 && cc < C && s[rr][cc] == pl) {
                    cells[n++] = new int[]{rr, cc};
                    if (n >= 4) return new int[][]{cells[n - 4], cells[n - 3], cells[n - 2], cells[n - 1]};
                } else {
                    n = 0;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ AI

    @Override
    Object think() {
        int[][] s = new int[R][];
        for (int r = 0; r < R; r++) s[r] = b[r].clone();
        int me = turn;
        int depth = level == 0 ? 1 : level == 1 ? 4 : 6;
        if (level == 0 && rnd.nextInt(3) == 0) {
            int col;
            do { col = rnd.nextInt(C); } while (s[0][col] != -1);
            return col;
        }
        int[] order = {3, 2, 4, 1, 5, 0, 6};
        int best = -1, bestScore = Integer.MIN_VALUE;
        for (int col : order) {
            int r = rowFor(s, col);
            if (r < 0) continue;
            s[r][col] = me;
            int sc;
            if (winAt(s, r, col) != null) sc = 1_000_000;
            else sc = -negamax(s, 1 - me, depth - 1, Integer.MIN_VALUE + 1, Integer.MAX_VALUE);
            s[r][col] = -1;
            if (sc > bestScore) { bestScore = sc; best = col; }
        }
        return best;
    }

    private int negamax(int[][] s, int pl, int depth, int alpha, int beta) {
        if (fullBoard(s)) return 0;
        if (depth <= 0) return evaluate(s, pl);
        int[] order = {3, 2, 4, 1, 5, 0, 6};
        int best = Integer.MIN_VALUE + 1;
        for (int col : order) {
            int r = rowFor(s, col);
            if (r < 0) continue;
            s[r][col] = pl;
            int sc;
            if (winAt(s, r, col) != null) sc = 100_000 + depth;
            else sc = -negamax(s, 1 - pl, depth - 1, -beta, -alpha);
            s[r][col] = -1;
            if (sc > best) best = sc;
            if (best > alpha) alpha = best;
            if (alpha >= beta) break;
        }
        return best;
    }

    /** Heuristic from pl's point of view. */
    static int evaluate(int[][] s, int pl) {
        int score = 0;
        for (int r = 0; r < R; r++) {
            if (s[r][3] == pl) score += 3;
            else if (s[r][3] == 1 - pl) score -= 3;
        }
        int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int r = 0; r < R; r++) {
            for (int col = 0; col < C; col++) {
                for (int[] d : dirs) {
                    int er = r + d[0] * 3, ec = col + d[1] * 3;
                    if (er < 0 || er >= R || ec < 0 || ec >= C) continue;
                    int mine = 0, theirs = 0;
                    for (int k = 0; k < 4; k++) {
                        int v = s[r + d[0] * k][col + d[1] * k];
                        if (v == pl) mine++;
                        else if (v == 1 - pl) theirs++;
                    }
                    if (theirs == 0) score += mine == 3 ? 6 : mine == 2 ? 2 : 0;
                    if (mine == 0) score -= theirs == 3 ? 8 : theirs == 2 ? 2 : 0;
                }
            }
        }
        return score;
    }

    @Override
    void play(Object move) {
        int col = (Integer) move;
        if (col >= 0 && b[0][col] == -1) drop(col);
    }
}
