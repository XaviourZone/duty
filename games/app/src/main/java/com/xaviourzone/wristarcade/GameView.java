package com.xaviourzone.wristarcade;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

/**
 * Square view that hosts one {@link Game}: forwards taps, runs the computer's
 * moves on a background thread, handles pauses and the real-time loop.
 */
class GameView extends View {

    interface Listener {
        void onGameChanged();
    }

    private Game game;
    private Listener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean busy;        // CPU thinking or a pause is running
    private int generation;      // bumps on reset so stale CPU moves are dropped
    private long lastFrame;
    private boolean thumbnail;

    GameView(Context c) {
        super(c);
        if (Build.VERSION.SDK_INT < 28) setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    void setGame(Game g) {
        game = g;
        generation++;
        busy = false;
        lastFrame = 0;
        invalidate();
        afterChange();
    }

    Game getGame() { return game; }

    void setListener(Listener l) { listener = l; }

    /** Thumbnails just draw the starting position, no input. */
    void setThumbnail(boolean t) { thumbnail = t; }

    void restart() {
        if (game == null) return;
        generation++;
        busy = false;
        game.newRound();
        lastFrame = 0;
        invalidate();
        afterChange();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = MeasureSpec.getSize(hSpec);
        int size;
        if (MeasureSpec.getMode(hSpec) == MeasureSpec.UNSPECIFIED || h == 0) size = w;
        else if (MeasureSpec.getMode(wSpec) == MeasureSpec.UNSPECIFIED || w == 0) size = h;
        else size = Math.min(w, h);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (game == null) return;
        float W = getWidth();
        if (!thumbnail && game.animated() && !game.over) {
            long now = System.nanoTime();
            float dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1e9f);
            lastFrame = now;
            game.tick(dt);
            if (game.over) {
                if (listener != null) listener.onGameChanged();
            } else {
                postInvalidateOnAnimation();
            }
        }
        game.draw(canvas, W);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (game == null || thumbnail) return false;
        float W = getWidth();
        if (game.animated() && !game.over) {
            if (game.onTouch(e, W)) {
                invalidate();
                return true;
            }
        }
        if (e.getActionMasked() != MotionEvent.ACTION_DOWN) return true;
        if (game.over) {
            restart();
            return true;
        }
        if (busy || game.cpuTurn()) return true;
        if (game.tap(e.getX(), e.getY(), W)) {
            invalidate();
            afterChange();
        }
        return true;
    }

    /** After any move: tell the screen, then pause / let the CPU move if needed. */
    private void afterChange() {
        if (listener != null) listener.onGameChanged();
        if (game == null) return;
        if (game.animated() && !game.over) {
            lastFrame = 0;
            postInvalidateOnAnimation();
            return;
        }
        long delay = game.delayMs();
        if (delay > 0) {
            busy = true;
            final int gen = generation;
            ui.postDelayed(() -> {
                if (gen != generation) return;
                busy = false;
                game.afterDelay();
                invalidate();
                afterChange();
            }, delay);
            return;
        }
        if (game.cpuTurn()) cpuMove();
    }

    private void cpuMove() {
        busy = true;
        final int gen = generation;
        final Game g = game;
        final long start = System.currentTimeMillis();
        new Thread(() -> {
            Object move;
            try {
                move = g.think();
            } catch (Exception ex) {
                move = null;
            }
            final Object m = move;
            // Keep a short "thinking" pause so the player can follow the CPU's move.
            long wait = Math.max(0, 450 - (System.currentTimeMillis() - start));
            ui.postDelayed(() -> {
                if (gen != generation || g != game) return;
                busy = false;
                if (m != null && !g.over) g.play(m);
                invalidate();
                afterChange();
            }, wait);
        }).start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        generation++;
        ui.removeCallbacksAndMessages(null);
    }
}
