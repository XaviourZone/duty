package com.xaviourzone.powerface;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Draws a {@link Face}. Used for previews, in the editor (edit mode: tap to select,
 * drag to move) and full screen in power mode.
 */
public class FaceView extends View {

    public interface Listener {
        void onSelect(int index);
        void onChanged();
    }

    private Face face;
    private boolean editMode;
    private int selected = -1;
    private Listener listener;

    // Live data
    public int battery = 80;
    public boolean charging;
    public int steps;
    public int stepGoal = 8000;
    public float temp = Float.NaN;
    public boolean fahrenheit;

    private float shiftX, shiftY;
    private int maxBgSize = 720;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<RectF> bounds = new ArrayList<>();
    private Bitmap bg;
    private String bgPath;
    private float lastX, lastY;

    private static final Typeface THIN = Typeface.create("sans-serif-thin", Typeface.NORMAL);
    private static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);

    public FaceView(Context c) {
        super(c);
        outline.setStyle(Paint.Style.STROKE);
        outline.setColor(0xFF8E7BFF);
        outline.setStrokeWidth(3f);
        outline.setPathEffect(new DashPathEffect(new float[]{12f, 8f}, 0f));
    }

    public void setFace(Face f) {
        face = f;
        if (selected >= (f == null ? 0 : f.elements.size())) selected = -1;
        loadBg();
        invalidate();
    }

    public Face getFace() { return face; }

    public void setEditMode(boolean on) { editMode = on; invalidate(); }

    public void setListener(Listener l) { listener = l; }

    public int getSelected() { return selected; }

    public void setSelected(int i) { selected = i; invalidate(); }

    /** Use a smaller background bitmap for list thumbnails to save memory. */
    public void setThumbnail(boolean thumb) { maxBgSize = thumb ? 300 : 720; bgPath = null; loadBg(); }

    /** Small pixel shift used against AMOLED burn-in. */
    public void setShift(float x, float y) { shiftX = x; shiftY = y; invalidate(); }

    /** Call after changing the face photo. */
    public void reloadBg() { bgPath = null; loadBg(); invalidate(); }

    private void loadBg() {
        String path = face == null ? null : face.bgImage;
        if (path == null) { bg = null; bgPath = null; return; }
        if (path.equals(bgPath) && bg != null) return;
        bgPath = path;
        bg = decode(path, maxBgSize);
    }

    static Bitmap decode(String path, int max) {
        if (path == null || !new File(path).exists()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= max && o.outHeight / (sample * 2) >= max) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        try {
            return BitmapFactory.decodeFile(path, o2);
        } catch (OutOfMemoryError e) {
            return null;
        }
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
        float W = getWidth();
        if (face == null || W <= 0) return;

        // Background colour + optional darkened photo (centre-cropped).
        canvas.drawColor(face.bgColor);
        if (bg != null) {
            int bw = bg.getWidth(), bh = bg.getHeight();
            int side = Math.min(bw, bh);
            Rect src = new Rect((bw - side) / 2, (bh - side) / 2, (bw + side) / 2, (bh + side) / 2);
            paint.reset();
            paint.setFilterBitmap(true);
            canvas.drawBitmap(bg, src, new RectF(0, 0, W, getHeight()), paint);
            paint.reset();
            paint.setColor(0xFF000000);
            paint.setAlpha(Math.max(0, Math.min(230, face.bgDim * 255 / 100)));
            canvas.drawRect(0, 0, W, getHeight(), paint);
        }

        canvas.save();
        canvas.translate(shiftX, shiftY);
        bounds.clear();
        for (Face.Element e : face.elements) {
            paint.reset();
            paint.setAntiAlias(true);
            RectF r;
            if (Face.Element.ANALOG.equals(e.type)) r = drawAnalog(canvas, e, W);
            else if (e.ring && (Face.Element.BATTERY.equals(e.type) || Face.Element.STEPS.equals(e.type)))
                r = drawRing(canvas, e, W);
            else r = drawText(canvas, e, W, textFor(e), e.size * W, e.x * W, e.y * W);
            bounds.add(r);
        }
        canvas.restore();

        if (editMode && selected >= 0 && selected < bounds.size()) {
            RectF r = new RectF(bounds.get(selected));
            r.inset(-8, -8);
            canvas.drawRoundRect(r, 12, 12, outline);
        }
    }

    private Typeface typeface(int font) {
        return font == 0 ? THIN : font == 2 ? BOLD : LIGHT;
    }

    private RectF drawText(Canvas c, Face.Element e, float W, String s, float textSize, float cx, float cy) {
        paint.setTypeface(typeface(e.font));
        paint.setTextSize(Math.max(6f, textSize));
        paint.setTextAlign(Paint.Align.CENTER);
        float tw = paint.measureText(s);
        if (e.color2 != 0) {
            paint.setShader(new LinearGradient(cx - tw / 2, cy, cx + tw / 2, cy,
                    e.color, e.color2, Shader.TileMode.CLAMP));
        } else {
            paint.setColor(e.color);
        }
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = cy - (fm.ascent + fm.descent) / 2f;
        c.drawText(s, cx, baseline, paint);
        paint.setShader(null);
        float half = textSize * 0.6f;
        return new RectF(cx - tw / 2 + shiftX, cy - half + shiftY, cx + tw / 2 + shiftX, cy + half + shiftY);
    }

    private RectF drawRing(Canvas c, Face.Element e, float W) {
        float cx = e.x * W, cy = e.y * W, r = e.size * W;
        float stroke = Math.max(3f, r * 0.18f);
        float frac;
        String label;
        if (Face.Element.BATTERY.equals(e.type)) {
            frac = battery / 100f;
            label = (charging ? "⚡" : "") + battery + "%";
        } else {
            frac = stepGoal > 0 ? Math.min(1f, steps / (float) stepGoal) : 0f;
            label = steps >= 10000 ? (steps / 1000) + "k" : String.valueOf(steps);
        }
        RectF oval = new RectF(cx - r + stroke / 2, cy - r + stroke / 2, cx + r - stroke / 2, cy + r - stroke / 2);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(e.color);
        paint.setAlpha(55);
        c.drawArc(oval, 0, 360, false, paint);
        paint.setAlpha(255);
        if (e.color2 != 0) {
            paint.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r, e.color, e.color2, Shader.TileMode.CLAMP));
        }
        if (frac > 0) c.drawArc(oval, -90, 360 * frac, false, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        Face.Element plain = e.copy();
        plain.color = 0xFFFFFFFF;
        plain.color2 = 0;
        drawText(c, plain, W, label, r * 0.48f, cx, cy);
        return new RectF(cx - r + shiftX, cy - r + shiftY, cx + r + shiftX, cy + r + shiftY);
    }

    private RectF drawAnalog(Canvas c, Face.Element e, float W) {
        float cx = e.x * W, cy = e.y * W, r = e.size * W;
        int accent = e.color2 != 0 ? e.color2 : e.color;

        // Faint glass dial
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(e.color);
        paint.setAlpha(18);
        c.drawCircle(cx, cy, r, paint);

        // Hour ticks
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(e.color);
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(i * 30);
            boolean major = i % 3 == 0;
            paint.setStrokeWidth(major ? r * 0.035f : r * 0.02f);
            paint.setAlpha(major ? 255 : 150);
            float r1 = r * (major ? 0.80f : 0.86f), r2 = r * 0.94f;
            c.drawLine(cx + (float) Math.sin(a) * r1, cy - (float) Math.cos(a) * r1,
                    cx + (float) Math.sin(a) * r2, cy - (float) Math.cos(a) * r2, paint);
        }
        paint.setAlpha(255);

        Calendar cal = Calendar.getInstance();
        float min = cal.get(Calendar.MINUTE);
        float hour = cal.get(Calendar.HOUR) + min / 60f;

        // Hour hand
        double ha = Math.toRadians(hour * 30);
        paint.setStrokeWidth(r * 0.06f);
        paint.setColor(e.color);
        c.drawLine(cx, cy, cx + (float) Math.sin(ha) * r * 0.5f, cy - (float) Math.cos(ha) * r * 0.5f, paint);

        // Minute hand
        double ma = Math.toRadians(min * 6);
        paint.setStrokeWidth(r * 0.04f);
        paint.setColor(accent);
        c.drawLine(cx, cy, cx + (float) Math.sin(ma) * r * 0.78f, cy - (float) Math.cos(ma) * r * 0.78f, paint);

        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy, r * 0.05f, paint);
        paint.setColor(0xFF000000);
        c.drawCircle(cx, cy, r * 0.02f, paint);
        return new RectF(cx - r + shiftX, cy - r + shiftY, cx + r + shiftX, cy + r + shiftY);
    }

    String textFor(Face.Element e) {
        switch (e.type) {
            case Face.Element.TIME:
                return new SimpleDateFormat(e.h24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(new Date());
            case Face.Element.DATE:
                return new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(new Date());
            case Face.Element.BATTERY:
                return (charging ? "⚡" : "") + battery + "%";
            case Face.Element.TEMP:
                if (Float.isNaN(temp)) return "--°";
                float t = fahrenheit ? temp * 9f / 5f + 32f : temp;
                return Math.round(t) + "°";
            case Face.Element.STEPS:
                return steps + " steps";
            case Face.Element.TEXT:
                return e.text == null || e.text.isEmpty() ? "Text" : e.text;
            default:
                return "";
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!editMode || face == null) return super.onTouchEvent(ev);
        float W = getWidth();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                int hit = -1;
                float slop = 16 * getResources().getDisplayMetrics().density;
                for (int i = bounds.size() - 1; i >= 0; i--) {
                    RectF r = new RectF(bounds.get(i));
                    r.inset(-slop, -slop);
                    if (r.contains(ev.getX(), ev.getY())) { hit = i; break; }
                }
                selected = hit;
                lastX = ev.getX();
                lastY = ev.getY();
                if (listener != null) listener.onSelect(hit);
                getParent().requestDisallowInterceptTouchEvent(hit >= 0);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (selected >= 0 && selected < face.elements.size() && W > 0) {
                    Face.Element e = face.elements.get(selected);
                    e.x = clamp(e.x + (ev.getX() - lastX) / W);
                    e.y = clamp(e.y + (ev.getY() - lastY) / W);
                    lastX = ev.getX();
                    lastY = ev.getY();
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                if (selected >= 0 && listener != null) listener.onChanged();
                return true;
        }
        return true;
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
