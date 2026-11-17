package com.xaviourzone.wristarcade;

import android.graphics.Canvas;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Memory Match: 16 cards, 8 pairs. Find a pair to score and go again.
 * The CPU remembers cards it has seen (better memory on harder levels).
 */
class Memory extends Game {

    static final String[] SYMBOLS = {"★", "♥", "♦", "♣", "♠", "☀", "☾", "⚡"};
    static final int[] COLORS = {0xFFFFD166, 0xFFFF2E63, 0xFF00E5FF, 0xFF00FF88, 0xFFB388FF, 0xFFFF8A00, 0xFFE8F6FF, 0xFFFFEB3B};

    final int[] card = new int[16];
    final int[] owner = new int[16];    // -1 hidden in play, else player who matched it
    final int[] score = new int[2];
    int first = -1, second = -1;
    boolean pendingHide;
    final Map<Integer, Integer> cpuMemory = new HashMap<>();

    Memory() { reset(); }

    @Override String name() { return "Memory Match"; }

    @Override
    void reset() {
        List<Integer> deck = new ArrayList<>();
        for (int i = 0; i < 8; i++) { deck.add(i); deck.add(i); }
        Collections.shuffle(deck, rnd);
        for (int i = 0; i < 16; i++) { card[i] = deck.get(i); owner[i] = -1; }
        score[0] = score[1] = 0;
        first = second = -1;
        pendingHide = false;
        synchronized (cpuMemory) { cpuMemory.clear(); }
        turn = starter; over = false; winner = NONE;
    }

    @Override
    String status() {
        return super.status() + " • " + score[P1] + "–" + score[P2];
    }

    private RectF rect(int i, float W) {
        float m = W * 0.06f, g = (W - 2 * m) / 4, pad = g * 0.07f;
        int r = i / 4, c = i % 4;
        return new RectF(m + g * c + pad, m + g * r + pad, m + g * (c + 1) - pad, m + g * (r + 1) - pad);
    }

    private boolean faceUp(int i) { return owner[i] != -1 || i == first || i == second; }

    @Override
    void draw(Canvas c, float W) {
        for (int i = 0; i < 16; i++) {
            RectF r = rect(i, W);
            if (faceUp(i)) {
                int col = owner[i] != -1 ? color(owner[i]) : WHITE;
                fillRect(c, r, r.width() * 0.16f, (col & 0x00FFFFFF) | 0x26000000);
                clean();
                p.setStyle(android.graphics.Paint.Style.STROKE);
                p.setStrokeWidth(W * 0.006f);
                p.setColor(col);
                p.setShadowLayer(W * 0.012f, 0, 0, col);
                c.drawRoundRect(r, r.width() * 0.16f, r.width() * 0.16f, p);
                p.clearShadowLayer();
                text(c, SYMBOLS[card[i]], r.centerX(), r.centerY(), r.height() * 0.5f, COLORS[card[i]], false);
            } else {
                fillRect(c, r, r.width() * 0.16f, 0x22FFFFFF);
                text(c, "?", r.centerX(), r.centerY(), r.height() * 0.35f, 0x44FFFFFF, true);
            }
        }
    }

    @Override
    boolean tap(float x, float y, float W) {
        if (pendingHide) return false;
        for (int i = 0; i < 16; i++) {
            if (rect(i, W).contains(x, y)) {
                if (faceUp(i)) return false;
                flip(i);
                return true;
            }
        }
        return false;
    }

    void flip(int i) {
        remember(i);
        if (first == -1) { first = i; return; }
        second = i;
        if (card[first] == card[second]) {
            owner[first] = owner[second] = turn;
            score[turn]++;
            first = second = -1;
            if (score[0] + score[1] == 8) end(score[0] > score[1] ? P1 : score[1] > score[0] ? P2 : DRAW);
            // match: same player goes again
        } else {
            pendingHide = true;
        }
    }

    private void remember(int i) {
        double keep = level == 0 ? 0.35 : level == 1 ? 0.7 : 1.0;
        synchronized (cpuMemory) {
            if (rnd.nextDouble() < keep) cpuMemory.put(i, card[i]);
        }
    }

    @Override long delayMs() { return pendingHide ? 900 : 0; }

    @Override
    void afterDelay() {
        if (!pendingHide) return;
        pendingHide = false;
        first = second = -1;
        next();
    }

    @Override
    boolean cpuTurn() { return super.cpuTurn() && !pendingHide; }

    // ------------------------------------------------------------------ AI

    @Override
    Object think() {
        Map<Integer, Integer> mem;
        synchronized (cpuMemory) { mem = new HashMap<>(cpuMemory); }
        List<Integer> hidden = new ArrayList<>();
        for (int i = 0; i < 16; i++) if (owner[i] == -1 && i != first) hidden.add(i);
        if (hidden.isEmpty()) return null;
        if (first == -1) {
            // A known pair?
            for (int a : hidden) for (int b : hidden) {
                if (a < b && mem.containsKey(a) && mem.containsKey(b) && mem.get(a).equals(mem.get(b))) return a;
            }
            // Otherwise flip an unknown card if there is one.
            List<Integer> unknown = new ArrayList<>();
            for (int i : hidden) if (!mem.containsKey(i)) unknown.add(i);
            List<Integer> from = unknown.isEmpty() ? hidden : unknown;
            return from.get(rnd.nextInt(from.size()));
        }
        // Second card: the match of the first one if remembered.
        int want = card[first];
        for (int i : hidden) if (mem.containsKey(i) && mem.get(i) == want) return i;
        List<Integer> unknown = new ArrayList<>();
        for (int i : hidden) if (!mem.containsKey(i)) unknown.add(i);
        List<Integer> from = unknown.isEmpty() ? hidden : unknown;
        return from.get(rnd.nextInt(from.size()));
    }

    @Override
    void play(Object move) {
        int i = (Integer) move;
        if (!faceUp(i) && !pendingHide) flip(i);
    }
}
