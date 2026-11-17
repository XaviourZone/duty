package com.xaviourzone.wristarcade;

import android.graphics.Canvas;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/**
 * Checkers (8x8, captures are compulsory, multi-jumps, kings).
 * Player 1 starts at the bottom and moves up.
 */
class Checkers extends Game {

    static final int N = 8;
    // 0 empty, 1 P1 man, 2 P1 king, 3 P2 man, 4 P2 king
    final int[] b = new int[64];
    int selected = -1;
    int quiet;            // moves since the last capture (draw after 60)
    int[] lastPath;

    static final class Move {
        final int[] path;
        final int[] caps;
        Move(int[] path, int[] caps) { this.path = path; this.caps = caps; }
        int from() { return path[0]; }
        int to() { return path[path.length - 1]; }
    }

    Checkers() { reset(); }

    @Override String name() { return "Checkers"; }

    @Override
    void reset() {
        for (int i = 0; i < 64; i++) {
            int r = i / N, c = i % N;
            b[i] = 0;
            if ((r + c) % 2 == 1) {
                if (r < 3) b[i] = 3;
                else if (r > 4) b[i] = 1;
            }
        }
        turn = starter; over = false; winner = NONE; selected = -1; quiet = 0; lastPath = null;
    }

    static int owner(int v) { return v == 1 || v == 2 ? P1 : v == 3 || v == 4 ? P2 : NONE; }
    static boolean king(int v) { return v == 2 || v == 4; }

    private float cell(float W) { return W * 0.9f / N; }
    private float off(float W) { return W * 0.05f; }

    @Override
    void draw(Canvas c, float W) {
        float cs = cell(W), o = off(W);
        panel(c, new RectF(o - cs * 0.12f, o - cs * 0.12f, o + cs * N + cs * 0.12f, o + cs * N + cs * 0.12f), cs * 0.3f);
        for (int i = 0; i < 64; i++) {
            int r = i / N, col = i % N;
            if ((r + col) % 2 == 1) {
                fillRect(c, new RectF(o + cs * col, o + cs * r, o + cs * (col + 1), o + cs * (r + 1)), cs * 0.08f, 0x22FFFFFF);
            }
        }
        List<Move> legal = over ? new ArrayList<Move>() : moves(b, turn);
        boolean human = !(vsCpu && turn == P2);
        if (lastPath != null) {
            for (int k = 0; k + 1 < lastPath.length; k++) {
                int a = lastPath[k], z = lastPath[k + 1];
                line(c, o + cs * (a % N + 0.5f), o + cs * (a / N + 0.5f), o + cs * (z % N + 0.5f), o + cs * (z / N + 0.5f),
                        cs * 0.06f, 0x55FFFFFF, false);
            }
        }
        for (int i = 0; i < 64; i++) {
            if (b[i] == 0) continue;
            float cx = o + cs * (i % N + 0.5f), cy = o + cs * (i / N + 0.5f);
            int pl = owner(b[i]);
            disc(c, cx, cy, cs * 0.36f, color(pl));
            ring(c, cx, cy, cs * 0.24f, cs * 0.04f, 0x55000000, false);
            if (king(b[i])) text(c, "♛", cx, cy, cs * 0.42f, 0xFF101018, true);
            if (i == selected) ring(c, cx, cy, cs * 0.44f, cs * 0.06f, GOLD, true);
        }
        if (human && !over) {
            for (Move m : legal) {
                if (selected == -1) {
                    float cx = o + cs * (m.from() % N + 0.5f), cy = o + cs * (m.from() / N + 0.5f);
                    ring(c, cx, cy, cs * 0.42f, cs * 0.025f, 0x88FFFFFF, false);
                } else if (m.from() == selected) {
                    hint(c, o + cs * (m.to() % N + 0.5f), o + cs * (m.to() / N + 0.5f), cs * 0.16f, GOLD);
                }
            }
        }
    }

    @Override
    String status() {
        if (over) return super.status();
        boolean mustJump = false;
        for (Move m : moves(b, turn)) if (m.caps.length > 0) mustJump = true;
        return super.status() + (mustJump ? " • must jump!" : "");
    }

    @Override
    boolean tap(float x, float y, float W) {
        float cs = cell(W), o = off(W);
        int col = (int) Math.floor((x - o) / cs), row = (int) Math.floor((y - o) / cs);
        if (col < 0 || col >= N || row < 0 || row >= N) return false;
        int i = row * N + col;
        List<Move> legal = moves(b, turn);
        if (owner(b[i]) == turn) {
            for (Move m : legal) if (m.from() == i) { selected = i; return true; }
            selected = -1;
            return true;
        }
        if (selected >= 0) {
            for (Move m : legal) {
                if (m.from() == selected && m.to() == i) { doMove(m); return true; }
            }
        }
        return false;
    }

    void doMove(Move m) {
        apply(b, m);
        lastPath = m.path;
        selected = -1;
        quiet = m.caps.length > 0 ? 0 : quiet + 1;
        next();
        if (moves(b, turn).isEmpty()) end(1 - turn);
        else if (quiet >= 60) end(DRAW);
    }

    static void apply(int[] s, Move m) {
        int v = s[m.from()];
        s[m.from()] = 0;
        for (int cp : m.caps) s[cp] = 0;
        int to = m.to();
        if (v == 1 && to / N == 0) v = 2;
        if (v == 3 && to / N == N - 1) v = 4;
        s[to] = v;
    }

    static List<Move> moves(int[] s, int pl) {
        List<Move> caps = new ArrayList<>();
        List<Move> simple = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            if (owner(s[i]) != pl) continue;
            int[][] dirs = dirsFor(s[i]);
            int[] path = {i};
            jumps(s, i, s[i], i, path, new int[0], caps);
            for (int[] d : dirs) {
                int r = i / N + d[0], c = i % N + d[1];
                if (r >= 0 && r < N && c >= 0 && c < N && s[r * N + c] == 0) {
                    simple.add(new Move(new int[]{i, r * N + c}, new int[0]));
                }
            }
        }
        return caps.isEmpty() ? simple : caps;
    }

    static int[][] dirsFor(int v) {
        if (king(v)) return new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        return owner(v) == P1 ? new int[][]{{-1, -1}, {-1, 1}} : new int[][]{{1, -1}, {1, 1}};
    }

    private static void jumps(int[] s, int origin, int piece, int at, int[] path, int[] caps, List<Move> out) {
        boolean extended = false;
        int pl = owner(piece);
        boolean crowned = !king(piece) && ((pl == P1 && at / N == 0) || (pl == P2 && at / N == N - 1));
        if (!crowned) {
            for (int[] d : dirsFor(piece)) {
                int mr = at / N + d[0], mc = at % N + d[1];
                int lr = at / N + 2 * d[0], lc = at % N + 2 * d[1];
                if (lr < 0 || lr >= N || lc < 0 || lc >= N) continue;
                int mid = mr * N + mc, land = lr * N + lc;
                if (owner(s[mid]) != 1 - pl || contains(caps, mid)) continue;
                if (s[land] != 0 && land != origin) continue;
                int[] np = append(path, land);
                int[] nc = append(caps, mid);
                jumps(s, origin, piece, land, np, nc, out);
                extended = true;
            }
        }
        if (!extended && caps.length > 0) out.add(new Move(path, caps));
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) if (x == v) return true;
        return false;
    }

    private static int[] append(int[] a, int v) {
        int[] r = new int[a.length + 1];
        System.arraycopy(a, 0, r, 0, a.length);
        r[a.length] = v;
        return r;
    }

    // ------------------------------------------------------------------ AI

    @Override
    Object think() {
        int[] s = b.clone();
        int me = turn;
        List<Move> ms = moves(s, me);
        if (ms.isEmpty()) return null;
        if (level == 0 && rnd.nextInt(2) == 0) return ms.get(rnd.nextInt(ms.size()));
        int depth = level == 0 ? 1 : level == 1 ? 3 : 6;
        Move best = ms.get(0);
        int bs = Integer.MIN_VALUE;
        for (Move m : ms) {
            int[] t = s.clone();
            apply(t, m);
            int sc = -search(t, 1 - me, depth - 1, Integer.MIN_VALUE + 1, Integer.MAX_VALUE);
            if (sc > bs || (sc == bs && rnd.nextBoolean())) { bs = sc; best = m; }
        }
        return best;
    }

    private int search(int[] s, int pl, int depth, int alpha, int beta) {
        List<Move> ms = moves(s, pl);
        if (ms.isEmpty()) return -100_000 - depth;
        if (depth <= 0) return eval(s, pl);
        int best = Integer.MIN_VALUE + 1;
        for (Move m : ms) {
            int[] t = s.clone();
            apply(t, m);
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
            int v = s[i];
            if (v == 0) continue;
            int r = i / N, c = i % N;
            int val = king(v) ? 175 : 100;
            if (!king(v)) val += owner(v) == P1 ? (7 - r) * 4 : r * 4;   // advancing
            if (c == 0 || c == 7) val += 4;                              // safe edges
            sc += owner(v) == pl ? val : -val;
        }
        return sc;
    }

    @Override
    void play(Object move) {
        Move m = (Move) move;
        for (Move l : moves(b, turn)) {
            if (l.from() == m.from() && l.to() == m.to() && l.caps.length == m.caps.length) { doMove(l); return; }
        }
    }
}
