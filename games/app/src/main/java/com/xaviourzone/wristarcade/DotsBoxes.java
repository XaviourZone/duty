package com.xaviourzone.wristarcade;

import android.graphics.Canvas;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Dots & Boxes on a 4x4 grid of boxes. Close a box to score and move again.
 * Lines 0..19 are horizontal (row r 0..4, col c 0..3 -> r*4+c),
 * lines 20..39 are vertical (row r 0..3, col c 0..4 -> 20 + r*5+c).
 */
class DotsBoxes extends Game {

    static final int B = 4, LINES = 40;
    final int[] line = new int[LINES];   // -1 free, else player who drew it
    final int[] box = new int[B * B];    // -1 open, else owner
    final int[] score = new int[2];
    int last = -1;

    DotsBoxes() { reset(); }

    @Override String name() { return "Dots & Boxes"; }

    @Override
    void reset() {
        Arrays.fill(line, -1);
        Arrays.fill(box, -1);
        score[0] = score[1] = 0;
        turn = starter; over = false; winner = NONE; last = -1;
    }

    @Override
    String status() {
        return super.status() + " • " + score[P1] + "–" + score[P2];
    }

    private float m(float W) { return W * 0.12f; }
    private float gap(float W) { return (W - 2 * m(W)) / B; }

    /** Endpoints of a line in pixels: x1, y1, x2, y2. */
    private float[] ends(int id, float W) {
        float m = m(W), g = gap(W);
        if (id < 20) {
            int r = id / 4, c = id % 4;
            return new float[]{m + g * c, m + g * r, m + g * (c + 1), m + g * r};
        }
        int k = id - 20, r = k / 5, c = k % 5;
        return new float[]{m + g * c, m + g * r, m + g * c, m + g * (r + 1)};
    }

    @Override
    void draw(Canvas c, float W) {
        float m = m(W), g = gap(W);
        panel(c, new RectF(m * 0.4f, m * 0.4f, W - m * 0.4f, W - m * 0.4f), W * 0.05f);
        for (int i = 0; i < B * B; i++) {
            if (box[i] == -1) continue;
            int r = i / B, col = i % B;
            RectF rr = new RectF(m + g * col + g * 0.12f, m + g * r + g * 0.12f, m + g * (col + 1) - g * 0.12f, m + g * (r + 1) - g * 0.12f);
            fillRect(c, rr, g * 0.12f, (color(box[i]) & 0x00FFFFFF) | 0x40000000);
            text(c, box[i] == P1 ? (vsCpu ? "YOU" : "P1") : (vsCpu ? "CPU" : "P2"), rr.centerX(), rr.centerY(), g * 0.22f, color(box[i]), true);
        }
        for (int id = 0; id < LINES; id++) {
            float[] e = ends(id, W);
            if (line[id] == -1) {
                line(c, e[0], e[1], e[2], e[3], W * 0.006f, 0x22FFFFFF, false);
            } else {
                line(c, e[0], e[1], e[2], e[3], W * (id == last ? 0.02f : 0.014f), color(line[id]), true);
            }
        }
        for (int r = 0; r <= B; r++) for (int col = 0; col <= B; col++) disc(c, m + g * col, m + g * r, W * 0.014f, WHITE);
    }

    @Override
    boolean tap(float x, float y, float W) {
        int best = -1;
        float bd = gap(W) * 0.38f;
        for (int id = 0; id < LINES; id++) {
            if (line[id] != -1) continue;
            float[] e = ends(id, W);
            float mx = (e[0] + e[2]) / 2, my = (e[1] + e[3]) / 2;
            float d = (float) Math.hypot(x - mx, y - my);
            if (d < bd) { bd = d; best = id; }
        }
        if (best < 0) return false;
        draw(best);
        return true;
    }

    /** Lines around box i: top, bottom, left, right. */
    static int[] sides(int i) {
        int r = i / B, c = i % B;
        return new int[]{r * 4 + c, (r + 1) * 4 + c, 20 + r * 5 + c, 20 + r * 5 + c + 1};
    }

    static int countSides(int[] ln, int i) {
        int n = 0;
        for (int s : sides(i)) if (ln[s] != -1) n++;
        return n;
    }

    /** Boxes that use this line. */
    static List<Integer> boxesOf(int id) {
        List<Integer> out = new ArrayList<>();
        if (id < 20) {
            int r = id / 4, c = id % 4;
            if (r > 0) out.add((r - 1) * B + c);
            if (r < B) out.add(r * B + c);
        } else {
            int k = id - 20, r = k / 5, c = k % 5;
            if (c > 0) out.add(r * B + c - 1);
            if (c < B) out.add(r * B + c);
        }
        return out;
    }

    void draw(int id) {
        line[id] = turn;
        last = id;
        boolean scored = false;
        for (int bx : boxesOf(id)) {
            if (box[bx] == -1 && countSides(line, bx) == 4) {
                box[bx] = turn;
                score[turn]++;
                scored = true;
            }
        }
        if (score[0] + score[1] == B * B) {
            end(score[0] > score[1] ? P1 : score[1] > score[0] ? P2 : DRAW);
            return;
        }
        if (!scored) next();
    }

    // ------------------------------------------------------------------ AI

    @Override
    Object think() {
        int[] ln = line.clone();
        List<Integer> free = new ArrayList<>();
        for (int id = 0; id < LINES; id++) if (ln[id] == -1) free.add(id);
        if (free.isEmpty()) return null;
        // 1. Complete a box when possible.
        if (level > 0 || rnd.nextBoolean()) {
            for (int id : free) for (int bx : boxesOf(id)) if (countSides(ln, bx) == 3) return id;
        }
        if (level == 0) return free.get(rnd.nextInt(free.size()));
        // 2. A safe line that does not give a box away.
        List<Integer> safe = new ArrayList<>();
        for (int id : free) {
            boolean ok = true;
            for (int bx : boxesOf(id)) if (countSides(ln, bx) == 2) ok = false;
            if (ok) safe.add(id);
        }
        if (!safe.isEmpty()) return safe.get(rnd.nextInt(safe.size()));
        if (level == 1) return free.get(rnd.nextInt(free.size()));
        // 3. Hard: give away the smallest chain.
        int best = free.get(0), bestLoss = Integer.MAX_VALUE;
        for (int id : free) {
            int[] t = ln.clone();
            t[id] = 0;
            int loss = greedyTake(t);
            if (loss < bestLoss) { bestLoss = loss; best = id; }
        }
        return best;
    }

    /** How many boxes the opponent can grab in a row after this position. */
    private static int greedyTake(int[] t) {
        int taken = 0;
        boolean again = true;
        while (again) {
            again = false;
            for (int bx = 0; bx < B * B; bx++) {
                if (countSides(t, bx) == 3) {
                    for (int s : sides(bx)) if (t[s] == -1) t[s] = 1;
                    taken++;
                    again = true;
                }
            }
        }
        return taken;
    }

    @Override
    void play(Object move) {
        int id = (Integer) move;
        if (line[id] == -1) draw(id);
    }
}
