package com.xaviourzone.wristarcade;

import android.graphics.Canvas;
import android.graphics.RectF;

import java.util.HashMap;
import java.util.Map;

/**
 * Nim with 4 rows (1, 3, 5, 7 sticks). Take any number of sticks from one row.
 * Whoever takes the LAST stick loses.
 * Tap a stick to select it and every stick to its right, tap again to take them.
 */
class Nim extends Game {

    static final int[] START = {1, 3, 5, 7};
    final int[] rows = new int[4];
    int selRow = -1, selFrom = -1;
    String note = "";

    Nim() { reset(); }

    @Override String name() { return "Nim"; }

    @Override
    void reset() {
        System.arraycopy(START, 0, rows, 0, 4);
        selRow = selFrom = -1;
        turn = starter; over = false; winner = NONE; note = "";
    }

    @Override
    String status() {
        if (over) return super.status();
        if (selRow >= 0) return "Tap again to take " + (rows[selRow] - selFrom);
        return (note.isEmpty() ? "" : note + " ") + super.status();
    }

    private float rowY(int r, float W) { return W * (0.2f + 0.2f * r); }
    private float stickX(int r, int k, float W) {
        float gap = W * 0.11f;
        return W / 2 + (k - (START[r] - 1) / 2f) * gap;
    }

    @Override
    void draw(Canvas c, float W) {
        panel(c, new RectF(W * 0.04f, W * 0.08f, W * 0.96f, W * 0.92f), W * 0.06f);
        for (int r = 0; r < 4; r++) {
            float y = rowY(r, W);
            for (int k = 0; k < START[r]; k++) {
                float x = stickX(r, k, W);
                if (k >= rows[r]) {
                    line(c, x, y - W * 0.06f, x, y + W * 0.06f, W * 0.012f, 0x1AFFFFFF, false);
                    continue;
                }
                boolean sel = r == selRow && k >= selFrom;
                int col = sel ? GOLD : 0xFFFFB86C;
                line(c, x, y - W * 0.06f, x, y + W * 0.06f, W * 0.022f, col, true);
                disc(c, x, y - W * 0.07f, W * 0.018f, sel ? GOLD : 0xFFFF3D00);
            }
        }
        if (!over) text(c, "Last stick loses", W / 2, W * 0.95f, W * 0.035f, 0x88FFFFFF, false);
    }

    @Override
    boolean tap(float x, float y, float W) {
        for (int r = 0; r < 4; r++) {
            if (Math.abs(y - rowY(r, W)) > W * 0.09f) continue;
            int bestK = -1;
            float bd = W * 0.06f;
            for (int k = 0; k < rows[r]; k++) {
                float d = Math.abs(x - stickX(r, k, W));
                if (d < bd) { bd = d; bestK = k; }
            }
            if (bestK < 0) return false;
            if (r == selRow && bestK >= selFrom) {
                take(r, rows[r] - selFrom);
            } else {
                selRow = r;
                selFrom = bestK;
            }
            return true;
        }
        if (selRow >= 0) { selRow = selFrom = -1; return true; }
        return false;
    }

    void take(int r, int n) {
        rows[r] -= n;
        note = who(turn) + " took " + n + " •";
        selRow = selFrom = -1;
        int total = 0;
        for (int v : rows) total += v;
        if (total == 0) { end(1 - turn); return; }   // took the last stick: lose
        next();
    }

    // ------------------------------------------------------------------ AI: exact solve (only 2x4x6x8 states)

    private final Map<Integer, Boolean> memo = new HashMap<>();

    private static int key(int[] r) { return ((r[0] * 4 + r[1]) * 6 + r[2]) * 8 + r[3]; }

    /** True if the player to move from this position wins (last stick loses). */
    private boolean wins(int[] r) {
        int total = r[0] + r[1] + r[2] + r[3];
        if (total == 0) return true; // opponent took the last stick
        int k = key(r);
        Boolean m = memo.get(k);
        if (m != null) return m;
        boolean res = false;
        for (int i = 0; i < 4 && !res; i++) {
            for (int n = 1; n <= r[i] && !res; n++) {
                r[i] -= n;
                if (!wins(r)) res = true;
                r[i] += n;
            }
        }
        memo.put(k, res);
        return res;
    }

    @Override
    Object think() {
        int[] r = rows.clone();
        boolean smart = level == 2 || (level == 1 && rnd.nextInt(10) < 6) || (level == 0 && rnd.nextInt(10) < 2);
        synchronized (memo) {
            if (smart) {
                for (int i = 0; i < 4; i++) {
                    for (int n = 1; n <= r[i]; n++) {
                        r[i] -= n;
                        boolean good = !wins(r);
                        r[i] += n;
                        if (good) return new int[]{i, n};
                    }
                }
            }
        }
        // random legal move (avoid taking the very last stick if possible)
        int total = r[0] + r[1] + r[2] + r[3];
        for (int tries = 0; tries < 50; tries++) {
            int i = rnd.nextInt(4);
            if (r[i] == 0) continue;
            int n = 1 + rnd.nextInt(r[i]);
            if (n == total && total > 1) continue;
            return new int[]{i, n};
        }
        for (int i = 0; i < 4; i++) if (r[i] > 0) return new int[]{i, 1};
        return null;
    }

    @Override
    void play(Object move) {
        int[] m = (int[]) move;
        if (rows[m[0]] >= m[1] && m[1] > 0) take(m[0], m[1]);
    }
}
