package com.xaviourzone.powerface;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Draws a {@link Face}: previews, gallery thumbnails, the editor canvas (tap to select,
 * drag to move, pinch to resize) and the full-screen power-mode face.
 */
public class FaceView extends View {

    public interface Listener {
        void onSelect(int index);
        /** Called before a drag / pinch starts so the editor can save an undo step. */
        void onEditStart();
        void onChanged();
    }

    private Face face;
    private boolean editMode;
    private int selected = -1;
    private Listener listener;

    // Live data
    public int battery = 80;
    public boolean charging;
    public int steps = 0;
    public int stepGoal = 8000;
    public float temp = Float.NaN;
    public boolean fahrenheit;
    /** Fixed sample values for gallery thumbnails so every design looks complete. */
    public boolean demoData;

    private float shiftX, shiftY;
    private int maxBgSize = 720;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<RectF> bounds = new ArrayList<>();
    private Bitmap bg;
    private String bgPath;
    private float lastX, lastY;
    private boolean scaling, snapX, snapY;
    private final ScaleGestureDetector scaler;

    static final Typeface[] FONTS = {
            Typeface.create("sans-serif-thin", Typeface.NORMAL),
            Typeface.create("sans-serif-light", Typeface.NORMAL),
            Typeface.create("sans-serif", Typeface.NORMAL),
            Typeface.create("sans-serif-medium", Typeface.NORMAL),
            Typeface.create("sans-serif", Typeface.BOLD),
            Typeface.create("sans-serif-black", Typeface.NORMAL),
            Typeface.create("sans-serif-condensed", Typeface.BOLD),
            Typeface.create("monospace", Typeface.NORMAL),
            Typeface.create("serif", Typeface.NORMAL),
    };
    static final String[] FONT_NAMES = {"Thin", "Light", "Regular", "Medium", "Bold", "Black", "Condensed", "Mono", "Serif"};

    public FaceView(Context c) {
        super(c);
        // Glow (shadow layers) on shapes needs software rendering before Android 9.
        if (Build.VERSION.SDK_INT < 28) setLayerType(LAYER_TYPE_SOFTWARE, null);
        outline.setStyle(Paint.Style.STROKE);
        outline.setColor(0xFFFFFFFF);
        outline.setStrokeWidth(3f);
        outline.setPathEffect(new DashPathEffect(new float[]{12f, 8f}, 0f));
        guide.setColor(0x88FF2BD6);
        guide.setStrokeWidth(2f);
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector d) {
                if (!editMode || selected < 0) return false;
                scaling = true;
                if (listener != null) listener.onEditStart();
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector d) {
                if (face == null || selected < 0 || selected >= face.elements.size()) return false;
                Face.Element e = face.elements.get(selected);
                float f = d.getScaleFactor();
                e.size = clamp(e.size * f, 0.01f, 0.7f);
                if (usesBox(e)) {
                    e.w = clamp(e.w * f, 0.01f, 1.2f);
                    e.h = clamp(e.h * f, 0.003f, 1.2f);
                }
                invalidate();
                return true;
            }
        });
    }

    static boolean usesBox(Face.Element e) {
        if (Face.Element.SHAPE.equals(e.type)) {
            return Face.Element.S_BOX.equals(e.variant) || Face.Element.S_FRAME.equals(e.variant)
                    || Face.Element.S_LINE.equals(e.variant);
        }
        return Face.Element.V_BAR.equals(e.variant)
                && (Face.Element.BATTERY.equals(e.type) || Face.Element.STEPS.equals(e.type));
    }

    public void setFace(Face f) {
        face = f;
        if (f == null || selected >= f.elements.size()) selected = -1;
        loadBg();
        invalidate();
    }

    public Face getFace() { return face; }
    public void setEditMode(boolean on) { editMode = on; invalidate(); }
    public void setListener(Listener l) { listener = l; }
    public int getSelected() { return selected; }
    public void setSelected(int i) { selected = i; invalidate(); }

    /** Smaller photo bitmap for gallery thumbnails. */
    public void setThumbnail(boolean thumb) { maxBgSize = thumb ? 240 : 720; bgPath = null; loadBg(); }

    /** Small pixel shift used against AMOLED burn-in. */
    public void setShift(float x, float y) { shiftX = x; shiftY = y; invalidate(); }

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

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas canvas) {
        float W = getWidth();
        if (face == null || W <= 0) return;
        drawBackground(canvas, W);

        canvas.save();
        canvas.translate(shiftX, shiftY);
        bounds.clear();
        for (Face.Element e : face.elements) {
            paint.reset();
            paint.setAntiAlias(true);
            RectF r;
            try {
                r = drawElement(canvas, e, W);
            } catch (Exception ex) {
                r = new RectF();
            }
            r.offset(shiftX, shiftY);
            bounds.add(r);
        }
        canvas.restore();

        if (editMode) {
            if (snapX) canvas.drawLine(W / 2, 0, W / 2, W, guide);
            if (snapY) canvas.drawLine(0, W / 2, W, W / 2, guide);
            if (selected >= 0 && selected < bounds.size()) {
                RectF r = new RectF(bounds.get(selected));
                r.inset(-6, -6);
                canvas.drawRoundRect(r, 10, 10, outline);
            }
        }
    }

    private void drawBackground(Canvas c, float W) {
        paint.reset();
        if (Face.BG_LINEAR.equals(face.bgType)) {
            paint.setShader(new LinearGradient(0, 0, W, W, face.bgColor, face.bgColor2, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, W, paint);
        } else if (Face.BG_RADIAL.equals(face.bgType)) {
            paint.setShader(new RadialGradient(W / 2, W * 0.45f, W * 0.75f, face.bgColor2, face.bgColor,
                    Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, W, paint);
        } else {
            c.drawColor(face.bgColor);
        }
        paint.setShader(null);
        if (bg != null) {
            int bw = bg.getWidth(), bh = bg.getHeight();
            int side = Math.min(bw, bh);
            Rect src = new Rect((bw - side) / 2, (bh - side) / 2, (bw + side) / 2, (bh + side) / 2);
            paint.reset();
            paint.setFilterBitmap(true);
            c.drawBitmap(bg, src, new RectF(0, 0, W, W), paint);
            paint.reset();
            paint.setColor(0xFF000000);
            paint.setAlpha(Math.max(0, Math.min(235, face.bgDim * 255 / 100)));
            c.drawRect(0, 0, W, W, paint);
        }
    }

    private RectF drawElement(Canvas c, Face.Element e, float W) {
        String t = e.type;
        if (Face.Element.ANALOG.equals(t)) return drawAnalog(c, e, W);
        if (Face.Element.SHAPE.equals(t)) return drawShape(c, e, W);
        if (Face.Element.BATTERY.equals(t) || Face.Element.STEPS.equals(t)) {
            if (Face.Element.V_RING.equals(e.variant)) return drawRing(c, e, W, true);
            if (Face.Element.V_ARC.equals(e.variant)) return drawRing(c, e, W, false);
            if (Face.Element.V_BAR.equals(e.variant)) return drawBar(c, e, W);
        }
        return drawText(c, e, W, textFor(e), e.size * W, e.x * W, e.y * W);
    }

    private void applyGlow(Face.Element e, float W) {
        if (e.glow > 0f) {
            int gc = e.glowColor != 0 ? e.glowColor : e.color;
            paint.setShadowLayer(Math.max(1f, e.glow * W), 0, 0, gc);
        }
    }

    private void applyColor(Face.Element e, float x0, float y0, float x1, float y1) {
        if (e.color2 != 0) {
            paint.setShader(new LinearGradient(x0, y0, x1, y1, e.color, e.color2, Shader.TileMode.CLAMP));
        } else {
            paint.setShader(null);
            paint.setColor(e.color);
        }
        paint.setAlpha(e.alpha);
    }

    private RectF drawText(Canvas c, Face.Element e, float W, String s, float textSize, float cx, float cy) {
        if (e.upper) s = s.toUpperCase(Locale.getDefault());
        paint.setTypeface(FONTS[Math.max(0, Math.min(FONTS.length - 1, e.font))]);
        paint.setTextSize(Math.max(4f, textSize));
        paint.setTextAlign(Paint.Align.CENTER);
        if (Build.VERSION.SDK_INT >= 21) paint.setLetterSpacing(e.spacing);
        if (e.italic) paint.setTextSkewX(-0.22f);
        if (e.outline) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1.5f, textSize * 0.035f));
        }
        float tw = paint.measureText(s);
        applyColor(e, cx - tw / 2, cy - textSize / 2, cx + tw / 2, cy + textSize / 2);
        applyGlow(e, W);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = cy - (fm.ascent + fm.descent) / 2f;
        c.drawText(s, cx, baseline, paint);
        paint.setShader(null);
        paint.clearShadowLayer();
        paint.setTextSkewX(0f);
        paint.setStyle(Paint.Style.FILL);
        float half = textSize * 0.55f;
        return new RectF(cx - tw / 2, cy - half, cx + tw / 2, cy + half);
    }

    private float valueFrac(Face.Element e) {
        if (Face.Element.BATTERY.equals(e.type)) return battery() / 100f;
        int goal = stepGoal > 0 ? stepGoal : 8000;
        return Math.min(1f, steps() / (float) goal);
    }

    private int battery() { return demoData ? 76 : battery; }
    private int steps() { return demoData ? 6420 : steps; }
    private float temp() { return demoData ? 27f : temp; }

    private RectF drawRing(Canvas c, Face.Element e, float W, boolean withLabel) {
        float cx = e.x * W, cy = e.y * W, r = e.size * W;
        float stroke = withLabel ? Math.max(3f, r * 0.17f) : Math.max(2f, e.stroke * W);
        RectF oval = new RectF(cx - r + stroke / 2, cy - r + stroke / 2, cx + r - stroke / 2, cy + r - stroke / 2);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(e.color);
        paint.setAlpha(Math.min(e.alpha, 50));
        c.drawArc(oval, 0, 360, false, paint);
        float frac = valueFrac(e);
        if (frac > 0) {
            applyColor(e, cx - r, cy - r, cx + r, cy + r);
            applyGlow(e, W);
            c.drawArc(oval, -90, 360 * frac, false, paint);
            paint.clearShadowLayer();
        }
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        if (withLabel) {
            String label = Face.Element.BATTERY.equals(e.type)
                    ? (isCharging() ? "⚡" : "") + battery() + "%"
                    : (steps() >= 10000 ? String.format(Locale.US, "%.1fk", steps() / 1000f) : String.valueOf(steps()));
            Face.Element plain = e.copy();
            plain.color = 0xFFFFFFFF; plain.color2 = 0; plain.glow = 0; plain.outline = false; plain.alpha = 255;
            drawText(c, plain, W, label, r * 0.46f, cx, cy);
        }
        return new RectF(cx - r, cy - r, cx + r, cy + r);
    }

    private boolean isCharging() { return !demoData && charging; }

    private RectF drawBar(Canvas c, Face.Element e, float W) {
        float cx = e.x * W, cy = e.y * W, bw = e.w * W, bh = Math.max(2f, e.h * W);
        RectF track = new RectF(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(e.color);
        paint.setAlpha(Math.min(e.alpha, 45));
        c.drawRoundRect(track, bh / 2, bh / 2, paint);
        float frac = valueFrac(e);
        if (frac > 0) {
            RectF fill = new RectF(track.left, track.top, track.left + Math.max(bh, bw * frac), track.bottom);
            applyColor(e, track.left, cy, track.right, cy);
            applyGlow(e, W);
            c.drawRoundRect(fill, bh / 2, bh / 2, paint);
            paint.clearShadowLayer();
            paint.setShader(null);
        }
        return track;
    }

    private RectF drawShape(Canvas c, Face.Element e, float W) {
        float cx = e.x * W, cy = e.y * W;
        String v = e.variant;
        RectF b;
        if (Face.Element.S_CIRCLE.equals(v) || Face.Element.S_RING.equals(v) || Face.Element.S_ARC.equals(v)) {
            float r = e.size * W;
            b = new RectF(cx - r, cy - r, cx + r, cy + r);
        } else {
            b = new RectF(cx - e.w * W / 2, cy - e.h * W / 2, cx + e.w * W / 2, cy + e.h * W / 2);
        }
        applyColor(e, b.left, b.top, b.right, b.bottom);
        applyGlow(e, W);
        float sw = Math.max(1.5f, e.stroke * W);
        if (Face.Element.S_CIRCLE.equals(v)) {
            c.drawCircle(cx, cy, b.width() / 2, paint);
        } else if (Face.Element.S_RING.equals(v)) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(sw);
            c.drawCircle(cx, cy, b.width() / 2 - sw / 2, paint);
        } else if (Face.Element.S_ARC.equals(v)) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(sw);
            paint.setStrokeCap(Paint.Cap.ROUND);
            RectF o = new RectF(b);
            o.inset(sw / 2, sw / 2);
            float sweep = Math.max(10f, Math.min(360f, e.sweep));
            c.drawArc(o, 90 + (360 - sweep) / 2, sweep, false, paint);
        } else if (Face.Element.S_FRAME.equals(v)) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(sw);
            RectF o = new RectF(b);
            o.inset(sw / 2, sw / 2);
            float rad = e.size * W;
            c.drawRoundRect(o, rad, rad, paint);
        } else if (Face.Element.S_LINE.equals(v)) {
            float rad = Math.min(b.width(), b.height()) / 2;
            c.drawRoundRect(b, rad, rad, paint);
        } else { // box
            float rad = e.size * W;
            c.drawRoundRect(b, rad, rad, paint);
        }
        paint.clearShadowLayer();
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        return b;
    }

    private RectF drawAnalog(Canvas c, Face.Element e, float W) {
        float cx = e.x * W, cy = e.y * W, r = e.size * W;
        int accent = e.color2 != 0 ? e.color2 : e.color;
        String v = e.variant == null ? Face.Element.A_CLASSIC : e.variant;
        paint.setStrokeCap(Paint.Cap.ROUND);

        if (Face.Element.A_NUMBERS.equals(v)) {
            // 60 minute dots + numerals
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(e.color);
            for (int i = 0; i < 60; i++) {
                if (i % 5 == 0) continue;
                double a = Math.toRadians(i * 6);
                paint.setAlpha(110);
                c.drawCircle(cx + (float) Math.sin(a) * r * 0.95f, cy - (float) Math.cos(a) * r * 0.95f, r * 0.008f + 1, paint);
            }
            Face.Element num = e.copy();
            num.color2 = 0; num.glow = e.glow * 0.6f; num.outline = false; num.font = 3;
            for (int i = 1; i <= 12; i++) {
                double a = Math.toRadians(i * 30);
                boolean major = i % 3 == 0;
                num.alpha = major ? 255 : 150;
                drawText(c, num, W, String.valueOf(i), r * (major ? 0.16f : 0.11f),
                        cx + (float) Math.sin(a) * r * 0.80f, cy - (float) Math.cos(a) * r * 0.80f);
            }
        } else {
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(e.color);
            boolean minimal = Face.Element.A_MINIMAL.equals(v);
            for (int i = 0; i < 12; i++) {
                boolean major = i % 3 == 0;
                if (minimal && !major) continue;
                double a = Math.toRadians(i * 30);
                paint.setStrokeWidth(major ? r * 0.035f : r * 0.018f);
                paint.setAlpha(major ? 255 : 140);
                applyGlow(e, W);
                float r1 = r * (major ? 0.78f : 0.86f), r2 = r * 0.95f;
                c.drawLine(cx + (float) Math.sin(a) * r1, cy - (float) Math.cos(a) * r1,
                        cx + (float) Math.sin(a) * r2, cy - (float) Math.cos(a) * r2, paint);
                paint.clearShadowLayer();
            }
        }

        Calendar cal = Calendar.getInstance();
        float min = demoData ? 9 : cal.get(Calendar.MINUTE);
        float hour = (demoData ? 10 : cal.get(Calendar.HOUR)) + min / 60f;

        paint.setStyle(Paint.Style.STROKE);
        paint.setAlpha(255);
        double ha = Math.toRadians(hour * 30);
        paint.setStrokeWidth(r * 0.06f);
        paint.setColor(e.color);
        applyGlow(e, W);
        c.drawLine(cx, cy, cx + (float) Math.sin(ha) * r * 0.5f, cy - (float) Math.cos(ha) * r * 0.5f, paint);

        double ma = Math.toRadians(min * 6);
        paint.setStrokeWidth(r * 0.04f);
        paint.setColor(accent);
        if (e.glow > 0) paint.setShadowLayer(Math.max(1f, e.glow * W), 0, 0, accent);
        c.drawLine(cx, cy, cx + (float) Math.sin(ma) * r * 0.80f, cy - (float) Math.cos(ma) * r * 0.80f, paint);
        paint.clearShadowLayer();

        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy, r * 0.05f, paint);
        paint.setColor(0xFF000000);
        c.drawCircle(cx, cy, r * 0.02f, paint);
        return new RectF(cx - r, cy - r, cx + r, cy + r);
    }

    String textFor(Face.Element e) {
        Date now = new Date();
        if (demoData) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 10);
            cal.set(Calendar.MINUTE, 9);
            now = cal.getTime();
        }
        Locale l = Locale.getDefault();
        switch (e.type) {
            case Face.Element.TIME:
                return new SimpleDateFormat(e.h24 ? "HH:mm" : "h:mm", l).format(now);
            case Face.Element.HOUR:
                return new SimpleDateFormat(e.h24 ? "HH" : "h", l).format(now);
            case Face.Element.MINUTE:
                return new SimpleDateFormat("mm", l).format(now);
            case Face.Element.AMPM:
                return new SimpleDateFormat("a", l).format(now);
            case Face.Element.DATE:
                try {
                    return new SimpleDateFormat(e.fmt == null || e.fmt.isEmpty() ? "EEE, d MMM" : e.fmt, l).format(now);
                } catch (IllegalArgumentException ex) {
                    return new SimpleDateFormat("EEE, d MMM", l).format(now);
                }
            case Face.Element.BATTERY:
                return (isCharging() ? "⚡" : "") + battery() + "%";
            case Face.Element.TEMP: {
                float tv = temp();
                if (Float.isNaN(tv)) return "--°";
                float t = fahrenheit ? tv * 9f / 5f + 32f : tv;
                return Math.round(t) + "°";
            }
            case Face.Element.STEPS:
                return steps() + " steps";
            case Face.Element.TEXT:
                return e.text == null || e.text.isEmpty() ? "Text" : e.text;
            default:
                return "";
        }
    }

    // ------------------------------------------------------------------ editing touch

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!editMode || face == null) return super.onTouchEvent(ev);
        scaler.onTouchEvent(ev);
        float W = getWidth();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                scaling = false;
                int hit = hitTest(ev.getX(), ev.getY());
                selected = hit;
                lastX = ev.getX();
                lastY = ev.getY();
                if (listener != null) {
                    if (hit >= 0) listener.onEditStart();
                    listener.onSelect(hit);
                }
                getParent().requestDisallowInterceptTouchEvent(hit >= 0);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!scaling && ev.getPointerCount() == 1 && selected >= 0
                        && selected < face.elements.size() && W > 0) {
                    Face.Element e = face.elements.get(selected);
                    e.x = clamp(e.x + (ev.getX() - lastX) / W, 0f, 1f);
                    e.y = clamp(e.y + (ev.getY() - lastY) / W, 0f, 1f);
                    lastX = ev.getX();
                    lastY = ev.getY();
                    // Magnet to the centre lines.
                    snapX = Math.abs(e.x - 0.5f) < 0.015f;
                    snapY = Math.abs(e.y - 0.5f) < 0.015f;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (selected >= 0 && selected < face.elements.size()) {
                    Face.Element e = face.elements.get(selected);
                    if (snapX) e.x = 0.5f;
                    if (snapY) e.y = 0.5f;
                }
                snapX = snapY = false;
                scaling = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                if (selected >= 0 && listener != null) listener.onChanged();
                invalidate();
                return true;
        }
        return true;
    }

    /** Topmost element under the point; small items get a bigger touch area. */
    private int hitTest(float x, float y) {
        float slop = 14 * getResources().getDisplayMetrics().density;
        for (int i = bounds.size() - 1; i >= 0; i--) {
            RectF r = new RectF(bounds.get(i));
            // Large background shapes are only grabbed on their edge area to keep things under them selectable.
            r.inset(-slop, -slop);
            if (r.contains(x, y)) {
                Face.Element e = face.elements.get(i);
                if (Face.Element.SHAPE.equals(e.type) && bounds.get(i).width() > getWidth() * 0.7f) {
                    // let smaller items on top of a big frame/background win
                    int inner = hitTestBelow(i, x, y, slop);
                    if (inner >= 0) return inner;
                }
                return i;
            }
        }
        return -1;
    }

    private int hitTestBelow(int above, float x, float y, float slop) {
        for (int i = above - 1; i >= 0; i--) {
            RectF r = new RectF(bounds.get(i));
            r.inset(-slop, -slop);
            if (r.contains(x, y) && bounds.get(i).width() < getWidth() * 0.7f) return i;
        }
        return -1;
    }

    static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
