package com.cleanhome.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Calendar;

/**
 * Allocation-free custom Canvas View drawing a cozy, premium Fox Cub mascot
 * with time-adaptive palettes, 18-second seamless story loops, and time-specific OK reactions.
 * Virtual design canvas: 220 x 160 (scales crisply to any Android TV resolution).
 */
public class FoxMascotView extends View {

    public enum TimePeriod {
        MORNING,  // 05:00 - 10:59
        MIDDAY,   // 11:00 - 16:59
        EVENING,  // 17:00 - 19:59
        NIGHT     // 20:00 - 04:59
    }

    private enum EyeState {
        OPEN, HAPPY, SLEEPY, CLOSED
    }

    private static final float VIRTUAL_W = 220f;
    private static final float VIRTUAL_H = 160f;
    private static final float CX = 110f;
    private static final float GROUND_Y = 149f;

    private static final long FRAME_INTERVAL_MS = 36L; // ~28 FPS smooth & lightweight on Android TV
    private static final float LOOP_DURATION_SEC = 18.0f;
    private static final float OK_DURATION_SEC = 2.3f;

    // Pre-allocated Paints (ZERO allocations in onDraw)
    private final Paint furPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint furShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint creamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerEarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blushPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyeHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint groundShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint propPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint propGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Pre-allocated Paths & Rects
    private final Path bodyPath = new Path();
    private final Path chestPath = new Path();
    private final Path tailPath = new Path();
    private final Path tailTipPath = new Path();
    private final Path headPath = new Path();
    private final Path cheekMaskPath = new Path();
    private final Path earPath = new Path();
    private final Path earTipPath = new Path();
    private final Path innerEarPath = new Path();
    private final Path strokePath = new Path();
    private final Path propPath = new Path();
    private final RectF tempRect = new RectF();

    // State
    private TimePeriod currentPeriod = TimePeriod.MIDDAY;
    private TimePeriod palettePeriod = null;
    private boolean isAnimating = false;
    private long startTimeMs = 0L;
    private long okTriggerTimeMs = -1L;

    // Sparkle burst offsets for Morning OK interaction
    private static final float[] SPARKLE_ANGLES = { -140f, -105f, -75f, -40f };

    private final Runnable frameTicker = new Runnable() {
        @Override
        public void run() {
            if (!isAnimating) return;
            invalidate();
            postDelayed(this, FRAME_INTERVAL_MS);
        }
    };

    public FoxMascotView(Context context) {
        super(context);
        init();
    }

    public FoxMascotView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FoxMascotView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        furPaint.setStyle(Paint.Style.FILL);
        furShadowPaint.setStyle(Paint.Style.FILL);
        creamPaint.setStyle(Paint.Style.FILL);
        accentPaint.setStyle(Paint.Style.FILL);
        innerEarPaint.setStyle(Paint.Style.FILL);
        blushPaint.setStyle(Paint.Style.FILL);

        eyeHighlightPaint.setStyle(Paint.Style.FILL);
        eyeHighlightPaint.setColor(0xE8FFFFFF);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        groundShadowPaint.setStyle(Paint.Style.FILL);
        propPaint.setStyle(Paint.Style.FILL);
        propGlowPaint.setStyle(Paint.Style.FILL);

        startTimeMs = SystemClock.uptimeMillis();
        refreshTimePeriodFromClock();
        updatePaletteIfNeeded();
    }

    public void setTimePeriod(TimePeriod period) {
        if (period != null && this.currentPeriod != period) {
            this.currentPeriod = period;
            updatePaletteIfNeeded();
            invalidate();
        }
    }

    public void refreshTimePeriodFromClock() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        TimePeriod detected;
        if (hour >= 5 && hour <= 10) {
            detected = TimePeriod.MORNING;
        } else if (hour >= 11 && hour <= 16) {
            detected = TimePeriod.MIDDAY;
        } else if (hour >= 17 && hour <= 19) {
            detected = TimePeriod.EVENING;
        } else {
            detected = TimePeriod.NIGHT;
        }
        setTimePeriod(detected);
    }

    /** Trigger time-specific 2.3s OK button reaction. */
    public void triggerOkInteraction() {
        okTriggerTimeMs = SystemClock.uptimeMillis();
        invalidate();
    }

    public void startAnimationLoop() {
        if (meadow || isAnimating) return;
        isAnimating = true;
        removeCallbacks(frameTicker);
        post(frameTicker);
    }

    public void stopAnimationLoop() {
        isAnimating = false;
        removeCallbacks(frameTicker);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isShown()) startAnimationLoop();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimationLoop();
        super.onDetachedFromWindow();
    }

    @Override
    public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        if (visible) startAnimationLoop(); else stopAnimationLoop();
    }

    private void updatePaletteIfNeeded() {
        if (palettePeriod == currentPeriod) return;
        palettePeriod = currentPeriod;

        int furLight, furDark, furDeep, cream, innerEar, accent, blush, shadowColor;

        switch (currentPeriod) {
            case MORNING:
                furLight = 0xFFF79B59;
                furDark = 0xFFDC6E37;
                furDeep = 0xFFC55B2A;
                cream = 0xFFFFF5E8;
                innerEar = 0xFFF7C9AE;
                accent = 0xFF2F201D;
                blush = 0x55FF8E72;
                shadowColor = 0x38140C10;
                break;
            case MIDDAY:
                furLight = 0xFFF4904E;
                furDark = 0xFFD66731;
                furDeep = 0xFFBC5425;
                cream = 0xFFFFF3E4;
                innerEar = 0xFFF4C4A8;
                accent = 0xFF2D1E1B;
                blush = 0x4DFF8A6E;
                shadowColor = 0x3A0E1622;
                break;
            case EVENING:
                furLight = 0xFFEA844A;
                furDark = 0xFFC25B2D;
                furDeep = 0xFFA44722;
                cream = 0xFFF5E0C8;
                innerEar = 0xFFE3B097;
                accent = 0xFF2A1C1A;
                blush = 0x45FF8666;
                shadowColor = 0x44160B18;
                break;
            case NIGHT:
            default:
                // Muted moonlit tones — never glaring in a dark living room
                furLight = 0xFFB06D4F;
                furDark = 0xFF7D4735;
                furDeep = 0xFF633629;
                cream = 0xFFC9BFAF;
                innerEar = 0xFF9C7A6B;
                accent = 0xFF231A19;
                blush = 0x26E0806E;
                shadowColor = 0x55060812;
                break;
        }

        furPaint.setShader(new LinearGradient(
                CX, 40f, CX, GROUND_Y,
                furLight, furDark, Shader.TileMode.CLAMP));
        furShadowPaint.setColor(furDeep);
        creamPaint.setColor(cream);
        innerEarPaint.setColor(innerEar);
        accentPaint.setColor(accent);
        strokePaint.setColor(accent);
        blushPaint.setColor(blush);
        groundShadowPaint.setShader(new RadialGradient(
                0f, 0f, 58f,
                shadowColor, 0x00000000, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        long now = meadow ? meadowFrameMs : SystemClock.uptimeMillis();
        float elapsedSec = Math.max(0L, now - startTimeMs) / 1000f;
        float loopT = elapsedSec % LOOP_DURATION_SEC;

        float okProgress = -1f;
        if (okTriggerTimeMs > 0L) {
            float okSec = (now - okTriggerTimeMs) / 1000f;
            if (okSec >= 0f && okSec <= OK_DURATION_SEC) {
                okProgress = okSec / OK_DURATION_SEC;
            } else {
                okTriggerTimeMs = -1L;
            }
        }

        float scale = Math.min(w / VIRTUAL_W, h / VIRTUAL_H);
        float dx = (w - VIRTUAL_W * scale) * 0.5f;
        float dy = (h - VIRTUAL_H * scale) * 0.5f;

        canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);

        if (bedtimeStartMs >= 0 && now < bedtimeStartMs + BEDTIME_MS) {
            drawBedtime(canvas, Math.max(0L, now - bedtimeStartMs) / 1000f);
            canvas.restore();
            return;
        }
        bedtimeStartMs = -1;
        if (meadow ? meadowPose == FoxHourlySchedule.FoxPose.CURLED_SLEEP : currentPeriod == TimePeriod.NIGHT) {
            drawSleepingNightFox(canvas, loopT, okProgress, -1f);
        } else {
            if ((meadowHour == 11 || meadowHour == 8) && loopT > 11.5f && loopT < 15f) {
                float step = (loopT - 11.5f) / 3.5f;
                canvas.translate(-10f * bellCurve(step), -3f * (float)Math.abs(Math.sin(step * Math.PI * 3)) * bellCurve(step));
            }
            drawAwakeSittingFox(canvas, elapsedSec, loopT, okProgress, -1f);
        }

        drawForeground(canvas, loopT, okProgress);
        canvas.restore();
    }

    // =========================================================================
    // AWAKE SITTING FOX (Morning, Midday, Evening)
    // =========================================================================
    private void drawAwakeSittingFox(Canvas canvas, float elapsedSec, float loopT, float okP, float bedtime) {
        // Base organic breathing
        float breath = (float) Math.sin(elapsedSec * 2.1f);
        float bodyScaleY = 1.0f + breath * 0.018f;
        float bodyScaleX = 1.0f - breath * 0.010f;
        float hopOffsetY = 0f;
        float torsoLean = 0f, headX = 0f, faceX = 0f, faceY = 0f;
        float headTiltDeg = 0f;
        float headOffsetY = breath * 0.9f;
        float leftEarDeg = (float) Math.sin(elapsedSec * 1.4f) * 2.2f;
        float rightEarDeg = -leftEarDeg;
        float tailWagDeg = (float) Math.sin(elapsedSec * 1.8f) * 4.5f;
        float yawnAmount = 0f;
        EyeState eyeState = EyeState.OPEN;

        // Natural periodic blink (~every 4.2s)
        float blinkCycle = elapsedSec % 4.2f;
        float blinkClose = 0f;
        if (blinkCycle > 3.95f) {
            float p = (blinkCycle - 3.95f) / 0.25f;
            blinkClose = (float) Math.sin(p * Math.PI);
        }

        // The butterfly, dandelion and lantern retain their original synchronized choreography.
        if (meadow && meadowHour != 7 && meadowHour != 11 && meadowHour != 18) {
            float watch = bellCurve((loopT - 2f) / 12f);
            float settle = bellCurve((loopT - 7f) / 9f);
            switch (bedtime >= 0 ? 21 : meadowHour) {
                case 5:
                    bodyScaleY += .11f * watch;
                    bodyScaleX -= .055f * watch;
                    headOffsetY -= 7f * watch;
                    yawnAmount = bellCurve((loopT - 3f) / 5f);
                    eyeState = watch > .55f ? EyeState.OPEN : EyeState.SLEEPY;
                    break;
                case 6: {
                    float perch = heldPose(loopT - 2f, 2f, 1.2f, 2f);
                    torsoLean = -4f * perch;
                    headX = faceX = -2.5f * perch;
                    headOffsetY = lerp(headOffsetY, -5.5f, perch);
                    bodyScaleY = lerp(bodyScaleY, 1.11f, perch);
                    tailWagDeg = -10f + tailWagDeg * .09f;
                    blinkClose = Math.max(blinkClose, bellCurve((loopT - 4.1f) / .72f));
                    break;
                }
                case 8:
                    hopOffsetY -= 7f * doubleHop(clamp01((loopT - 4f) / 3f));
                    eyeState = watch > .5f ? EyeState.HAPPY : EyeState.OPEN;
                    break;
                case 9: {
                    // On this shared timeline the lead bird is at 44% X, then 59% X.
                    float beat = loopT - 4.70f;
                    float alert = heldPose(beat, .18f, 1.18f, .75f);
                    float turn = easeInOutSine((beat - .78f) / .18f);
                    headTiltDeg = lerp(-9f, 10f, turn) * alert;
                    torsoLean = lerp(-3f, 3.5f, turn) * alert;
                    faceX = lerp(-3f, 3f, turn) * alert;
                    faceY = lerp(-1.5f, -2f, turn) * alert;
                    bodyScaleY = lerp(bodyScaleY, 1.04f, alert);
                    leftEarDeg = lerp(leftEarDeg, 10f, alert) - 6f * bellCurve((beat - .53f) / .25f);
                    rightEarDeg = lerp(rightEarDeg, -10f, alert);
                    break;
                }
                case 10:
                    tailWagDeg += 13f * watch * (float) Math.sin(loopT * 3.2f);
                    rightEarDeg += 6f * watch;
                    break;
                case 12:
                    bodyScaleY += .06f * watch;
                    blinkClose = Math.max(blinkClose, bellCurve((loopT - 4f) / 2f));
                    hopOffsetY -= 6f * bellCurve((loopT - 10f) / 1.2f);
                    break;
                case 13: {
                    float gaze = heldPose(loopT - 3f, 1.2f, 1.5f, 1.6f);
                    bodyScaleY = lerp(bodyScaleY, .93f, gaze);
                    bodyScaleX = lerp(bodyScaleX, 1.06f, gaze);
                    headOffsetY = lerp(headOffsetY, 3f, gaze);
                    faceY = -3.5f * gaze;
                    leftEarDeg = lerp(leftEarDeg, -11f, gaze);
                    rightEarDeg = lerp(rightEarDeg, 11f, gaze);
                    tailWagDeg = lerp(tailWagDeg, 16f, gaze);
                    blinkClose = Math.max(blinkClose, bellCurve((loopT - 4.45f) / .9f));
                    break;
                }
                case 14:
                    hopOffsetY -= 11f * doubleHop(clamp01((loopT - 5f) / 3f));
                    headTiltDeg = 8f * watch;
                    break;
                case 15:
                    headTiltDeg = -9f * watch;
                    leftEarDeg -= 10f * watch;
                    rightEarDeg -= 5f * watch;
                    break;
                case 16:
                    tailWagDeg *= .5f;
                    headOffsetY += 3f * settle;
                    blinkClose = Math.max(blinkClose, bellCurve((loopT - 9f) / 2f));
                    break;
                case 17:
                    eyeState = EyeState.SLEEPY;
                    headTiltDeg = 8f * watch;
                    headOffsetY -= 2f * watch;
                    break;
                case 19:
                    headOffsetY += 5f * settle;
                    headTiltDeg = 5f * settle;
                    eyeState = settle > .8f ? EyeState.CLOSED : EyeState.SLEEPY;
                    break;
                case 20:
                    headTiltDeg = -7f * watch;
                    bodyScaleY += .025f * watch;
                    eyeState = watch > .7f ? EyeState.HAPPY : EyeState.SLEEPY;
                    break;
                case 21:
                    yawnAmount = bellCurve((loopT - 5f) / 7f);
                    leftEarDeg -= 11f * settle;
                    rightEarDeg += 11f * settle;
                    headOffsetY += 5f * settle;
                    eyeState = yawnAmount > .25f ? EyeState.CLOSED : EyeState.SLEEPY;
                    break;
            }
        } else if (currentPeriod == TimePeriod.MORNING) {
            // 0..4.5s: Cozy morning stretch + tiny yawn
            if (loopT < 4.5f) {
                float s = bellCurve(loopT / 4.5f);
                bodyScaleY += s * 0.075f;
                bodyScaleX -= s * 0.045f;
                headOffsetY -= s * 4.5f;
                leftEarDeg -= s * 8f;
                rightEarDeg += s * 8f;
                yawnAmount = bellCurve(clamp01((loopT - 1.0f) / 2.4f));
                if (yawnAmount > 0.35f) eyeState = EyeState.CLOSED;
            }
            // 4.5..11.0s: Warm morning mote/butterfly drifts by; curious tracking
            else if (loopT < 11.0f) {
                float p = (loopT - 4.5f) / 6.5f;
                headTiltDeg = -9f * (float) Math.sin(p * Math.PI);
                leftEarDeg += 6f * (float) Math.sin(p * Math.PI * 2f);
            }
            // 11.0..15.0s: Joyful little hop & wag as mote floats away
            else if (loopT < 15.0f) {
                float p = (loopT - 11.0f) / 4.0f;
                float hop = bellCurve(clamp01(p * 1.6f));
                hopOffsetY = -8.5f * hop;
                tailWagDeg += (float) Math.sin(p * Math.PI * 6f) * 11f * (1f - p);
                if (p < 0.72f) eyeState = EyeState.HAPPY;
            }
        } else if (currentPeriod == TimePeriod.MIDDAY) {
            // 3.5..11.5s: Sparkle drops, fox nudges it in an arc
            if (loopT >= 3.5f && loopT < 11.5f) {
                float p = (loopT - 3.5f) / 8.0f;
                if (p < NUDGE_START) {
                    float lookP = p / NUDGE_START;
                    headTiltDeg = -10f * easeInOutSine(lookP);
                } else if (p < NUDGE_END) {
                    float nudgeP = (p - NUDGE_START) / (NUDGE_END - NUDGE_START);
                    hopOffsetY = -7.5f * (float) Math.sin(nudgeP * Math.PI);
                    headTiltDeg = lerp(-10f, 8f, easeInOutSine(nudgeP));
                    tailWagDeg += 10f * (float) Math.sin(nudgeP * Math.PI * 2f);
                    eyeState = EyeState.HAPPY;
                } else {
                    float followP = (p - NUDGE_END) / (1f - NUDGE_END);
                    headTiltDeg = 8f * (1f - easeInOutSine(followP));
                }
            } else if (loopT >= 11.5f && loopT < 14.5f) {
                float p = (loopT - 11.5f) / 3.0f;
                tailWagDeg += (float) Math.sin(p * Math.PI * 4f) * 8f * (1f - p);
            }
        } else if (currentPeriod == TimePeriod.EVENING) {
            eyeState = EyeState.SLEEPY;
            tailWagDeg *= 0.55f;
            // 0..6.5s: Warm firefly drifts slowly; soft lazy tracking
            if (loopT < 6.5f) {
                float p = loopT / 6.5f;
                headTiltDeg = (float) Math.sin(p * Math.PI * 1.5f) * 6.5f * bellCurve(p);
            }
            // 6.5..13.5s: Cozy yawn & gentle head droop
            else if (loopT < 13.5f) {
                float p = (loopT - 6.5f) / 7.0f;
                yawnAmount = bellCurve(clamp01((p - 0.1f) / 0.45f)) * 0.9f;
                float droop = bellCurve(clamp01((p - 0.4f) / 0.6f));
                headOffsetY += droop * 3.5f;
                leftEarDeg -= droop * 7f;
                rightEarDeg += droop * 7f;
                if (yawnAmount > 0.3f || droop > 0.5f) eyeState = EyeState.CLOSED;
            }
        }

        // --- Time-Specific OK Interaction Overrides ---
        if (okP >= 0f) {
            float env = bellCurve(okP);
            if (meadowHour == 5 || meadowHour == 8 || meadowHour == 10 || meadowHour == 12
                    || meadowHour == 14 || meadowHour == 15 || meadowHour == 16) {
                eyeState = EyeState.HAPPY;
                yawnAmount = 0f;
                tailWagDeg += 12f * env * (float) Math.sin(okP * Math.PI * 8f);
                switch (meadowHour) {
                    case 5:
                        bodyScaleY += .09f * env;
                        headOffsetY -= 5f * env;
                        break;
                    case 8: case 10:
                        hopOffsetY -= 12f * doubleHop(okP);
                        break;
                    case 12: case 14:
                        hopOffsetY -= 17f * bellCurve(okP / .75f);
                        break;
                    default:
                        headTiltDeg = lerp(headTiltDeg, -10f, env);
                }
            } else if (currentPeriod == TimePeriod.MORNING) {
                // Fresh Hello: energetic hop + happy eyes + golden sparkle burst
                hopOffsetY -= 12f * (float) Math.sin(clamp01(okP * 1.4f) * Math.PI);
                tailWagDeg += (float) Math.sin(okP * Math.PI * 8f) * 15f * env;
                leftEarDeg += 7f * env;
                rightEarDeg -= 7f * env;
                eyeState = EyeState.HAPPY;
                yawnAmount = 0f;
            } else if (currentPeriod == TimePeriod.MIDDAY) {
                // Playful Check-in: curious head tilt + lively wag + micro bounce
                float holdEnd = .28f + .280f / OK_DURATION_SEC;
                float tilt = okP < .28f ? easeInOutSine(okP / .28f)
                        : 1f - easeInOutSine((okP - holdEnd) / (1f - holdEnd));
                headTiltDeg = lerp(headTiltDeg, 13.5f, tilt);
                hopOffsetY -= 4.5f * (float) Math.sin(okP * Math.PI * 2f) * env;
                tailWagDeg += (float) Math.sin(okP * Math.PI * 7f) * 14f * env;
                eyeState = okP < 0.7f ? EyeState.HAPPY : EyeState.OPEN;
                yawnAmount = 0f;
            } else if (currentPeriod == TimePeriod.EVENING) {
                // Cozy Wind-Down: slow warm smile + gentle head lean + warm firefly bloom
                headTiltDeg = lerp(headTiltDeg, meadowHour >= 20 ? -11f : -6f, env);
                headOffsetY += 1.5f * env;
                eyeState = EyeState.HAPPY;
                yawnAmount = 0f;
            }
        }

        if (bedtime >= 0f) {
            float drop = easeInOutSine(bedtime / .75f);
            float fold = easeInOutSine((bedtime - .35f) / .40f);
            bodyScaleY = lerp(bodyScaleY, .87f, drop) - .09f * fold;
            bodyScaleX = lerp(bodyScaleX, 1.09f, drop) + .09f * fold;
            headOffsetY = lerp(headOffsetY, 7f, drop) + 13f * fold;
            headX = lerp(headX, -11f, fold);
            headTiltDeg = lerp(headTiltDeg, -10f, fold);
            leftEarDeg = lerp(leftEarDeg, -10f, drop);
            rightEarDeg = lerp(rightEarDeg, 10f, drop);
            // Canvas Y grows down. Positive rotation lowers the right-facing plume.
            tailWagDeg = lerp(tailWagDeg, 28f, fold);
            blinkClose = Math.max(blinkClose, drop);
            if (drop > .85f) eyeState = EyeState.CLOSED;
            yawnAmount *= 1f - drop;
        }

        // 1. Soft Ground Contact Shadow (shrinks slightly when hopping)
        float shadowScale = 1.0f - clamp01(-hopOffsetY / 32f) * 0.25f;
        canvas.save();
        canvas.translate(CX, GROUND_Y + 1f);
        canvas.scale(shadowScale, 0.24f);
        canvas.drawCircle(0f, 0f, 52f, groundShadowPaint);
        canvas.restore();

        // Master body group transform (anchored at ground)
        canvas.save();
        canvas.translate(0f, hopOffsetY + GROUND_Y - 142f);
        canvas.scale(1.13f, 1.13f, CX, 142f);
        boolean planted = meadowHour == 6 || meadowHour == 9 || meadowHour == 13 || bedtime >= 0f;
        canvas.save();
        canvas.scale(bodyScaleX, bodyScaleY, CX, planted ? 143f : GROUND_Y);
        if (torsoLean != 0f) canvas.rotate(torsoLean, CX, 143f);

        // 2. Fluffy Tail (behind body, pivoted at hip)
        drawSittingTail(canvas, tailWagDeg);

        // 3. Cozy Pear-Shaped Body
        bodyPath.reset();
        bodyPath.moveTo(CX, 92f);
        bodyPath.cubicTo(132f, 92f, 141f, 118f, 136f, 138f);
        bodyPath.cubicTo(133f, 143f, 87f, 143f, 84f, 138f);
        bodyPath.cubicTo(79f, 118f, 88f, 92f, CX, 92f);
        bodyPath.close();
        canvas.drawPath(bodyPath, furPaint);

        // 4. Creamy Fluffy Chest Bib
        chestPath.reset();
        chestPath.moveTo(CX, 99f);
        chestPath.cubicTo(123f, 100f, 127f, 120f, 118f, 133f);
        chestPath.cubicTo(114f, 137f, 106f, 137f, 102f, 133f);
        chestPath.cubicTo(93f, 120f, 97f, 100f, CX, 99f);
        chestPath.close();
        canvas.drawPath(chestPath, creamPaint);

        // 5. Cute Tucked Dark Front Paws
        if (!planted) drawPaws(canvas);

        // 6. Expressive Head Group (pivoted at neck)
        canvas.save();
        canvas.translate(headX, headOffsetY);
        canvas.rotate(headTiltDeg, CX, 94f);
        drawFoxHead(canvas, leftEarDeg, rightEarDeg, eyeState, blinkClose, yawnAmount, faceX, faceY);
        canvas.restore();

        canvas.restore();
        if (planted) drawPaws(canvas);
        canvas.restore();
    }

    private void drawPaws(Canvas canvas) {
        tempRect.set(88f, 133f, 105f, 143f);
        canvas.drawOval(tempRect, accentPaint);
        tempRect.set(115f, 133f, 132f, 143f);
        canvas.drawOval(tempRect, accentPaint);
    }

    private void drawSittingTail(Canvas canvas, float wagDeg) {
        canvas.save();
        canvas.rotate(wagDeg, 130f, 130f);

        // Main orange plume
        tailPath.reset();
        tailPath.moveTo(126f, 134f);
        tailPath.cubicTo(154f, 142f, 182f, 124f, 178f, 94f);
        tailPath.cubicTo(176f, 85f, 165f, 84f, 157f, 92f);
        tailPath.cubicTo(146f, 103f, 140f, 116f, 124f, 120f);
        tailPath.close();
        canvas.drawPath(tailPath, furPaint);

        // Creamy fluffy tip with gentle scallop seam
        tailTipPath.reset();
        tailTipPath.moveTo(178f, 94f);
        tailTipPath.cubicTo(176f, 85f, 165f, 84f, 157f, 92f);
        tailTipPath.cubicTo(154f, 96f, 153f, 100f, 155f, 105f);
        tailTipPath.cubicTo(160f, 103f, 165f, 108f, 171f, 109f);
        tailTipPath.cubicTo(176f, 106f, 179f, 100f, 178f, 94f);
        tailTipPath.close();
        canvas.drawPath(tailTipPath, creamPaint);

        canvas.restore();
    }

    private void drawFoxHead(Canvas canvas, float leftEarDeg, float rightEarDeg,
                             EyeState eyeState, float blinkClose, float yawnAmount, float faceX, float faceY) {
        // Left & Right Big Expressive Ears
        drawSingleEar(canvas, true, leftEarDeg);
        drawSingleEar(canvas, false, rightEarDeg);

        // Plush Wide Fox Head Silhouette
        headPath.reset();
        headPath.moveTo(CX, 47f);
        headPath.cubicTo(132f, 47f, 146f, 62f, 147f, 79f);
        headPath.cubicTo(148f, 93f, 130f, 102f, CX, 102f);
        headPath.cubicTo(90f, 102f, 72f, 93f, 73f, 79f);
        headPath.cubicTo(74f, 62f, 88f, 47f, CX, 47f);
        headPath.close();
        canvas.drawPath(headPath, furPaint);

        // Iconic Cream Cheek Fur Mask
        cheekMaskPath.reset();
        cheekMaskPath.moveTo(73f, 79f);
        cheekMaskPath.cubicTo(84f, 77f, 97f, 85f, CX, 90f);
        cheekMaskPath.cubicTo(123f, 85f, 136f, 77f, 147f, 79f);
        cheekMaskPath.cubicTo(147f, 93f, 130f, 102f, CX, 102f);
        cheekMaskPath.cubicTo(90f, 102f, 73f, 93f, 73f, 79f);
        cheekMaskPath.close();
        canvas.drawPath(cheekMaskPath, creamPaint);
        if (meadow && FoxHourlySchedule.usesFirelightRim(meadowHour)) {
            strokePath.rewind(); strokePath.moveTo(74,80);strokePath.quadTo(76,94,93,99);
            strokePaint.setColor(0x85fca366); strokePaint.setStrokeWidth(2);
            canvas.drawPath(strokePath,strokePaint);strokePaint.setColor(accentPaint.getColor());
        }

        // Soft Peach Cheek Blushes
        tempRect.set(78f, 83f, 91f, 90f);
        canvas.drawOval(tempRect, blushPaint);
        tempRect.set(129f, 83f, 142f, 90f);
        canvas.drawOval(tempRect, blushPaint);

        // Feature translation stays inside the unwarped cheek contour.
        canvas.save();
        canvas.translate(faceX, faceY);
        // Eyes
        drawEye(canvas, 94f, 76f, eyeState, blinkClose);
        drawEye(canvas, 126f, 76f, eyeState, blinkClose);

        // Cute Button Nose
        tempRect.set(105.5f, 84.5f, 114.5f, 90.5f);
        canvas.drawOval(tempRect, accentPaint);

        // Mouth (gentle 'w' or soft cozy yawn)
        if (yawnAmount > 0.12f) {
            float ry = 1.5f + yawnAmount * 4.5f;
            float rx = 2.0f + yawnAmount * 3.0f;
            tempRect.set(CX - rx, 92.5f, CX + rx, 92.5f + ry * 2f);
            canvas.drawOval(tempRect, accentPaint);
        } else {
            strokePaint.setStrokeWidth(1.9f);
            strokePath.reset();
            strokePath.moveTo(105.5f, 93.2f);
            strokePath.quadTo(107.8f, 95.8f, CX, 93.2f);
            strokePath.quadTo(112.2f, 95.8f, 114.5f, 93.2f);
            canvas.drawPath(strokePath, strokePaint);
        }
        canvas.restore();
    }

    private void drawSingleEar(Canvas canvas, boolean isLeft, float angleDeg) {
        canvas.save();
        float pivotX = isLeft ? 88f : 132f;
        float pivotY = 56f;
        canvas.rotate(angleDeg, pivotX, pivotY);
        if (!isLeft) {
            canvas.scale(-1f, 1f, CX, 0f);
        }

        // Outer Orange Ear
        earPath.reset();
        earPath.moveTo(76f, 64f);
        earPath.cubicTo(68f, 47f, 66f, 30f, 71f, 23f);
        earPath.cubicTo(77f, 22f, 91f, 36f, 98f, 52f);
        earPath.close();
        canvas.drawPath(earPath, furPaint);
        if (currentPeriod == TimePeriod.NIGHT && !drawingEveningPalette) {
            strokePath.reset();
            strokePath.moveTo(71f, 44f);
            strokePath.quadTo(67f, 31f, 71f, 23f);
            canvas.drawPath(strokePath, rimPaint);
        }

        // Dark Espresso Ear Tip
        earTipPath.reset();
        earTipPath.moveTo(68.2f, 36f);
        earTipPath.cubicTo(68f, 29f, 69.5f, 24.5f, 71f, 23f);
        earTipPath.cubicTo(75f, 22.5f, 81f, 27.5f, 85.5f, 34f);
        earTipPath.quadTo(76f, 37.5f, 68.2f, 36f);
        earTipPath.close();
        canvas.drawPath(earTipPath, accentPaint);

        // Warm Cream Inner Ear
        innerEarPath.reset();
        innerEarPath.moveTo(79f, 57f);
        innerEarPath.cubicTo(74f, 46f, 74f, 38f, 76.5f, 36f);
        innerEarPath.cubicTo(81f, 37f, 88f, 45f, 92f, 53f);
        innerEarPath.close();
        canvas.drawPath(innerEarPath, innerEarPaint);

        canvas.restore();
    }

    private void drawEye(Canvas canvas, float cx, float cy, EyeState state, float blinkClose) {
        if (state == EyeState.HAPPY) {
            strokePaint.setStrokeWidth(2.6f);
            strokePath.reset();
            strokePath.moveTo(cx - 5.5f, cy + 1.5f);
            strokePath.quadTo(cx, cy - 5.0f, cx + 5.5f, cy + 1.5f);
            canvas.drawPath(strokePath, strokePaint);
            return;
        }
        if (state == EyeState.CLOSED || blinkClose > 0.85f) {
            strokePaint.setStrokeWidth(2.4f);
            strokePath.reset();
            strokePath.moveTo(cx - 5.2f, cy);
            strokePath.quadTo(cx, cy + 3.8f, cx + 5.2f, cy);
            canvas.drawPath(strokePath, strokePaint);
            return;
        }

        float openScale = (1f - blinkClose) * (state == EyeState.SLEEPY ? 0.62f : 1.0f);
        float ry = Math.max(1.2f, 5.2f * openScale);
        float rx = 4.6f;

        tempRect.set(cx - rx, cy - ry, cx + rx, cy + ry);
        canvas.drawOval(tempRect, accentPaint);

        if (openScale > 0.45f) {
            canvas.drawCircle(cx - 1.6f, cy - ry * 0.38f, 1.7f, eyeHighlightPaint);
        }
    }

    // =========================================================================
    // CURLED-UP SLEEPING FOX (Night: 20:00 - 04:59)
    // =========================================================================
    private void drawSleepingNightFox(Canvas canvas, float loopT, float okP, float bedtime) {
        // Slow deep sleeping breath (period = 4.5s -> exactly 4 cycles in 18s!)
        float breathPeriod = meadowHour == 0 || meadowHour == 3 ? 9f : 4.5f;
        float breath = (float) Math.sin((loopT / breathPeriod) * Math.PI * 2f);
        float scaleY = 1.0f + breath * 0.050f;
        float scaleX = 1.0f - breath * 0.014f;

        // Subtle mid-sleep ear twitch + tiny cozy shift (7.0s..11.5s)
        float earTwitch = 0f;
        if (loopT >= 7.2f && loopT <= 9.0f) {
            float tp = (loopT - 7.2f) / 1.8f;
            earTwitch = (float) Math.sin(tp * Math.PI * 3f) * 5.5f * bellCurve(tp);
        }
        if (meadow) {
            switch (meadowHour) {
                case 0: case 3:
                    earTwitch = 0f;
                    scaleY = 1f + breath * (meadowHour == 3 ? .024f : .035f);
                    break;
                case 2:
                    earTwitch = 0f;
                    break;
                case 4:
                    earTwitch = 10f * bellCurve((loopT - 10f) / 3f);
                    if (okP >= 0f) earTwitch += 12f * bellCurve(okP);
                    break;
                case 22:
                    earTwitch *= 1.65f;
                    break;
            }
        }
        // Night OK reaction ("Do Not Disturb" — subtle cozy snuggle + soft dim aura)
        boolean okSmile = false;
        if (okP >= 0f) {
            float env = bellCurve(okP);
            scaleY -= env * 0.060f;
            scaleX += env * 0.035f;
            okSmile = env > 0.25f;
        }

        float curl = bedtime < 0f ? 1f : easeInOutSine((bedtime - .94f) / .66f);
        float exhale = bedtime < 0f ? 0f : 1f - easeInOutSine((bedtime - 1.6f) / .8f);
        scaleY += .045f * exhale;
        // 1. Wide Soft Ground Shadow
        canvas.save();
        canvas.translate(CX, GROUND_Y + 2f);
        canvas.scale(1.22f, 0.22f);
        canvas.drawCircle(0f, 0f, 56f, groundShadowPaint);
        canvas.restore();

        canvas.save();
        canvas.translate(0f, GROUND_Y - 142f);
        canvas.scale(scaleX * 1.22f, scaleY * 1.22f, CX, 142f);

        // 2. Curled Cozy Torso
        tempRect.set(62f, 94f, 162f, 143f);
        canvas.drawOval(tempRect, furPaint);

        strokePath.reset();
        strokePath.moveTo(95f, 97f);
        strokePath.cubicTo(117f, 90f, 149f, 96f, 158f, 109f);
        canvas.drawPath(strokePath, rimPaint);

        // Subtle lower-back shading for depth
        tempRect.set(92f, 112f, 158f, 142f);
        canvas.drawOval(tempRect, furShadowPaint);

        // 3. Resting Head nestled peacefully on the left
        canvas.save();
        // Match the dipped head in screen space across the two different torso transforms.
        canvas.translate(lerp(-14f, -24f, curl), lerp(31f, 32f, curl) - 1.8f * breath);
        canvas.rotate(lerp(-10f, -14f, curl), CX, 82f);
        canvas.scale(lerp(1.09f, .96f, curl), lerp(.69f, .96f, curl), CX, 82f);
        float tuck = okP >= 0 ? 8f * bellCurve(okP) : 0f;
        drawFoxHead(canvas, -12f + earTwitch - tuck, 14f + tuck,
                okSmile ? EyeState.HAPPY : EyeState.CLOSED, 0f, 0f, 0f, 0f);
        canvas.restore();

        if (bedtime >= 0) {
            float tuckAngle = 3f * bellCurve((bedtime - 1.6f) / .8f);
            canvas.rotate(-18f * (1f - curl) + tuckAngle, 151f, 124f);
        }
        if (meadowHour == 2) canvas.rotate(6f * bellCurve((loopT - 8f) / 5f), 151f, 124f);

        // 4. Big Fluffy Tail wrapped snugly across the front like a natural blanket
        tailPath.reset();
        tailPath.moveTo(156f, 118f);
        tailPath.cubicTo(166f, 134f, 146f, 146f, 108f, 144f);
        tailPath.cubicTo(94f, 143f, 84f, 136f, 88f, 128f);
        tailPath.cubicTo(94f, 120f, 112f, 122f, 128f, 124f);
        tailPath.cubicTo(142f, 126f, 150f, 114f, 156f, 118f);
        tailPath.close();
        canvas.drawPath(tailPath, furPaint);

        // Creamy fluffy tail tip tucked under chin
        tailTipPath.reset();
        tailTipPath.moveTo(106f, 144f - breath * 1.8f);
        tailTipPath.cubicTo(92f, 144f, 82f, 136f, 86f, 128f);
        tailTipPath.cubicTo(92f, 121f, 103f, 122f, 110f, 126f);
        tailTipPath.quadTo(105f, 134f, 106f, 144f);
        tailTipPath.close();
        canvas.drawPath(tailTipPath, creamPaint);

        canvas.restore();
    }

    // =========================================================================
    // SUBTLE PROPS & REACTION FX
    // =========================================================================
    private final Paint rimPaint = makeRim();
    private static Paint makeRim() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(1.6f);
        paint.setColor(0x2cd6e4ff);
        return paint;
    }

    private static Shader feather(int rgb) {
        return new RadialGradient(0, 0, 1,
                new int[]{rgb | 0x6b000000, rgb | 0x24000000, rgb},
                new float[]{0, .45f, 1}, Shader.TileMode.CLAMP);
    }
    private final Shader morningGlow = feather(0xffd8a8);
    private final Shader sparkleGlow = feather(0xffedac);
    private final Shader fireflyGlow = feather(0xffb85c);
    private final Shader moonGlow = feather(0xebd9b5);

    private void glow(Canvas canvas, Shader shader, float x, float y, float radius, float strength) {
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(radius, radius);
        propGlowPaint.setShader(shader);
        propGlowPaint.setAlpha(Math.round(255 * clamp01(strength)));
        canvas.drawCircle(0, 0, 1, propGlowPaint);
        canvas.restore();
    }

    // Normalized time within the eight-second midday encounter.
    private static final float NUDGE_START = .45f;
    private static final float NUDGE_END = .58f;
    private static final float NUDGE_PEAK = (NUDGE_START + NUDGE_END) * .5f;
    private final Shader seedShadow = feather(0x594435);
    private boolean meadow;
    private int meadowHour = -1;
    private FoxHourlySchedule.FoxPose meadowPose;

    private static final long BEDTIME_MS = 2400L;
    private long meadowFrameMs, bedtimeStartMs = -1L, eveningLoopStartMs;
    private boolean drawingEveningPalette;

    // Captured at the live hour change. Both silhouettes use the ordinary drawing paints.
    private final Paint[] characterPaints = {furPaint, furShadowPaint, creamPaint, accentPaint,
            innerEarPaint, blushPaint, eyeHighlightPaint, strokePaint, groundShadowPaint, rimPaint};
    private static final class CharacterPalette {
        final int[] colors;
        Shader fur, ground;
        CharacterPalette(int count) { colors = new int[count]; }
    }
    private final CharacterPalette evening = new CharacterPalette(characterPaints.length);
    private final CharacterPalette moonlit = new CharacterPalette(characterPaints.length);

    private void capturePalette(CharacterPalette palette) {
        for (int i = 0; i < characterPaints.length; i++) palette.colors[i] = characterPaints[i].getColor();
        palette.fur = furPaint.getShader();
        palette.ground = groundShadowPaint.getShader();
    }

    private void applyPalette(CharacterPalette palette) {
        drawingEveningPalette = palette == evening;
        furPaint.setShader(palette.fur);
        groundShadowPaint.setShader(palette.ground);
        for (int i = 0; i < characterPaints.length; i++) {
            characterPaints[i].setColor(palette.colors[i]);
        }
    }

    private void drawBedtime(Canvas canvas, float seconds) {
        float handoff = seconds - .75f;
        float seated = 1f - easeInOutSine((handoff - .035f) / .155f);
        float sleeping = easeInOutSine(handoff / .16f);
        // Fade finished silhouettes, so underlying cheeks and paws cannot show through the fur.
        if (seated > 0f) {
            applyPalette(evening);
            if (seated < 1f) canvas.saveLayerAlpha(0, 0, VIRTUAL_W, VIRTUAL_H, Math.round(255 * seated));
            float seatedTime = (bedtimeStartMs - eveningLoopStartMs) / 1000f + seconds;
            drawAwakeSittingFox(canvas, seatedTime, seatedTime % LOOP_DURATION_SEC, -1f, seconds);
            if (seated < 1f) canvas.restore();
            applyPalette(moonlit);
        }
        if (sleeping > 0f) {
            if (sleeping < 1f) canvas.saveLayerAlpha(0, 0, VIRTUAL_W, VIRTUAL_H, Math.round(255 * sleeping));
            drawSleepingNightFox(canvas, 0f, -1f, seconds);
            if (sleeping < 1f) canvas.restore();
        }
    }

    void syncMeadowFrame(long frameTimeNanos) {
        meadowFrameMs = frameTimeNanos / 1_000_000L;
        invalidate();
    }

    void cancelBedtimeTransition() {
        if (bedtimeStartMs < 0L) return;
        bedtimeStartMs = -1L;
        startTimeMs = Math.min(startTimeMs, SystemClock.uptimeMillis());
    }

    void setMeadowHour(int hour, FoxHourlySchedule.FoxPose pose, long hourStartMs, boolean bedtime) {
        if (meadowHour == hour) return;
        if (bedtime) {
            eveningLoopStartMs = startTimeMs;
            capturePalette(evening);
        }
        meadowHour = hour;
        meadowPose = pose;
        meadowFrameMs = hourStartMs;
        okTriggerTimeMs = -1L;
        bedtimeStartMs = bedtime ? hourStartMs : -1L;
        startTimeMs = bedtime ? hourStartMs + BEDTIME_MS : hourStartMs;
        if (pose == FoxHourlySchedule.FoxPose.CURLED_SLEEP) setTimePeriod(TimePeriod.NIGHT);
        else if (hour <= 10) setTimePeriod(TimePeriod.MORNING);
        else if (hour <= 16) setTimePeriod(TimePeriod.MIDDAY);
        else setTimePeriod(TimePeriod.EVENING);
        if (bedtime) capturePalette(moonlit);
        invalidate();
    }

    void setMeadow() { meadow = true; }

    private void drawForeground(Canvas canvas, float t, float ok) {
        if (meadow && meadowHour != 7 && meadowHour != 11 && meadowHour != 18) {
            if (meadowPose == FoxHourlySchedule.FoxPose.CURLED_SLEEP) {
                float start = meadowHour == 1 ? 6f : 9f;
                float p = clamp01((t - start) / 5f);
                if (meadowHour != 4 && meadowHour != 2) {
                    drawNightStar(canvas, 144, 68 - (meadowHour == 1 ? 15f * p : 0f),
                            bellCurve(p) * (meadowHour == 3 ? .27f : .52f), 4.5f);
                }
                if (ok >= 0f && meadowHour != 4) {
                    drawNightStar(canvas, 114, 52 - 15 * ok, bellCurve(ok) * .6f, 6f);
                    if (meadowHour == 1 || meadowHour == 2) {
                        drawNightStar(canvas, 130, 59 - 20 * ok, bellCurve(ok) * .45f, 4f);
                    }
                }
            } else {
                if (meadowHour == 14 || meadowHour == 16) {
                    float p = clamp01((t - 3f) / 10f);
                    float x = 58f + 98f * p;
                    float y = 46f - 12f * bellCurve(p);
                    glow(canvas, sparkleGlow, x, y, 9f, bellCurve(p) * .75f);
                    drawFourPointStar(canvas, x, y, 3.5f, applyAlpha(0xffffdd91, bellCurve(p)));
                }
                if (ok >= 0f && (meadowHour <= 10 || meadowHour == 12 || meadowHour == 14)) {
                    drawOkMorningSparkles(canvas, ok);
                } else if (ok >= 0f && meadowHour >= 17 && meadowHour <= 19) {
                    drawOkEveningGlow(canvas, ok);
                }
            }
            return;
        }
        switch (currentPeriod) {
            case MORNING:
                if (t >= 4.5f && t < 11f) drawMorningMote(canvas, (t - 4.5f) / 6.5f);
                if (ok >= 0) drawOkMorningSparkles(canvas, ok);
                break;
            case MIDDAY:
                if (t >= 3.5f && t < 11.5f) drawMiddaySparkle(canvas, (t - 3.5f) / 8f);
                break;
            case EVENING:
                if (t < 6.5f) drawEveningFirefly(canvas, t / 6.5f, 1f);
                if (ok >= 0) drawOkEveningGlow(canvas, ok);
                break;
            case NIGHT:
                if (t >= 7.5f && t < 12.5f) {
                    drawNightStar(canvas, 146, 68, bellCurve((t - 7.5f) / 5f) * .72f, 5.5f);
                }
                if (ok >= 0) {
                    float progress = clamp01(ok * OK_DURATION_SEC / 2.2f);
                    drawNightStar(canvas, 114, 52 - 14 * progress, bellCurve(progress) * .66f, 7.5f);
                }
                break;
        }
    }

    private void drawMorningMote(Canvas canvas, float p) {
        float x = lerp(52f, 156f, easeInOutSine(p));
        float y = 44f - (float) Math.sin(p * Math.PI * 2.2f) * 14f - p * 12f;
        float alpha = bellCurve(p);
        if (meadow) {
            float wing = 2 + 3 * (float)Math.abs(Math.sin(p * 105));
            propPaint.setColor(applyAlpha(0xffffbb94, alpha));
            canvas.drawOval(x-wing,y-4,x,y+2,propPaint);
            canvas.drawOval(x,y-4,x+wing,y+2,propPaint);
            return;
        }
        glow(canvas, morningGlow, x, y, 10f, alpha);
        propPaint.setColor(applyAlpha(0xFFFFE6C2, alpha * 0.92f));
        canvas.drawCircle(x, y, 3.2f, propPaint);
    }

    private void drawMiddaySparkle(Canvas canvas, float p) {
        if (meadow) {
            drawMeadowDandelion(canvas, p);
            return;
        }
        float approach = easeInOutSine(p / .45f);
        float flight = clamp01((p - .45f) / .55f);
        float x = lerp(68, 108, approach) + 72 * flight;
        float y = lerp(20, 54, approach) - 28 * flight - 22 * bellCurve(flight);
        float alpha = bellCurve(p);
        float radius = 7.8f * (1 - .28f * flight);
        glow(canvas, sparkleGlow, x, y, radius * 2.2f, alpha * .7f);
        canvas.save();
        canvas.rotate(32 * flight, x, y);
        drawFourPointStar(canvas, x, y, radius, applyAlpha(0xfffff2c6, alpha));
        canvas.restore();
    }

    // The intact tuft pauses at the muzzle. Its four crowns then separate from that same point.
    private void drawMeadowDandelion(Canvas canvas, float p) {
        float approach = easeInOutSine(p / NUDGE_PEAK);
        float release = clamp01((p - NUDGE_PEAK) / (1f - NUDGE_PEAK));
        // Immediate outward impulse, then a slower drift. Zero initial velocity
        // here made the breakup appear late even when the fox was already airborne.
        float spread = 1f - (1f-release)*(1f-release)*(1f-release);
        float x = lerp(28, 57, approach);
        float y = lerp(45, 79, approach);
        float alpha = clamp01(p / .10f) * clamp01((1-p)/.16f);
        glow(canvas, seedShadow, x, y+1.5f, 15f, alpha*(1-spread));
        propPaint.setStrokeWidth(1.7f);
        for (int i=0; i<4; i++) {
            float sx = x + (i-1.5f)*4 + spread*(67+16*i);
            float sy = y - 4*bellCurve(i/3f) - spread*(15+14*i)
                    + release*4*(float)Math.sin(release*6+i);
            canvas.save();canvas.rotate(-14+9*i+spread*(16-i*12),sx,sy);
            propPaint.setColor(applyAlpha(0xffac865c, alpha));
            canvas.drawLine(sx,sy,sx-2,sy+6,propPaint);
            propPaint.setColor(applyAlpha(0xfffffff5, alpha));
            for (int j=0; j<5; j++) {
                double angle = Math.PI*(.08+.21*j);
                float tx=sx+5.5f*(float)Math.cos(angle), ty=sy-6*(float)Math.sin(angle);
                canvas.drawLine(sx,sy,tx,ty,propPaint);canvas.drawCircle(tx,ty,1.2f,propPaint);
            }
            canvas.restore();
        }
    }

    private void drawEveningFirefly(Canvas canvas, float p, float intensity) {
        float x = lerp(164f, 64f, easeInOutSine(p));
        float y = 50f + (float) Math.sin(p * Math.PI * 3f) * 10f;
        float pulse = 0.65f + 0.35f * (float) Math.sin(p * Math.PI * 6f);
        float alpha = bellCurve(p) * pulse * intensity;
        glow(canvas, fireflyGlow, x, y, 11f, alpha);
        propPaint.setColor(applyAlpha(0xFFFFE39E, alpha * 0.92f));
        canvas.drawCircle(x, y, 2.8f, propPaint);
    }

    private void drawOkMorningSparkles(Canvas canvas, float p) {
        float alpha = bellCurve(p);
        for (float deg : SPARKLE_ANGLES) {
            double rad = Math.toRadians(deg);
            float dist = 28f + easeInOutSine(p) * 22f;
            float sx = CX + (float) Math.cos(rad) * dist;
            float sy = 66f + (float) Math.sin(rad) * dist;
            drawFourPointStar(canvas, sx, sy, 5.8f * (0.6f + 0.4f * alpha),
                    applyAlpha(0xFFFFE4A8, alpha * 0.9f));
        }
    }

    private void drawOkEveningGlow(Canvas canvas, float p) {
        float alpha = bellCurve(p);
        float x = 172f;
        float y = 50f;
        glow(canvas, fireflyGlow, x, y, 15f * (0.7f + 0.3f * alpha), alpha);
        propPaint.setColor(applyAlpha(0xFFFFE29A, alpha * 0.85f));
        canvas.drawCircle(x, y, 3.0f, propPaint);
    }

    private void drawNightStar(Canvas canvas, float x, float y, float alpha, float size) {
        if (alpha <= 0.01f) return;
        // Very soft dim aura — preserves dark-room comfort
        glow(canvas, moonGlow, x, y, size * 2.2f, alpha);
        drawFourPointStar(canvas, x, y, size, applyAlpha(0xFFF3E6C8, alpha * 0.85f));
    }

    private void drawFourPointStar(Canvas canvas, float cx, float cy, float r, int color) {
        float inner = r * 0.28f;
        propPath.reset();
        propPath.moveTo(cx, cy - r);
        propPath.quadTo(cx + inner * 0.5f, cy - inner * 0.5f, cx + r, cy);
        propPath.quadTo(cx + inner * 0.5f, cy + inner * 0.5f, cx, cy + r);
        propPath.quadTo(cx - inner * 0.5f, cy + inner * 0.5f, cx - r, cy);
        propPath.quadTo(cx - inner * 0.5f, cy - inner * 0.5f, cx, cy - r);
        propPath.close();
        propPaint.setColor(color);
        canvas.drawPath(propPath, propPaint);
    }

    // =========================================================================
    // MATH & EASING HELPERS
    // =========================================================================
    private static float heldPose(float t, float rise, float hold, float fall) {
        return easeInOutSine(t / rise) * (1f - easeInOutSine((t - rise - hold) / fall));
    }

    private static float doubleHop(float p) {
        return p < .5f ? bellCurve(2f * p) : .76f * bellCurve(2f * p - 1f);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float easeInOutSine(float t) {
        return -(float) (Math.cos(Math.PI * clamp01(t)) - 1.0) * 0.5f;
    }

    private static float bellCurve(float t) {
        return (float) Math.sin(clamp01(t) * Math.PI);
    }

    private static int applyAlpha(int color, float alphaFraction) {
        int a = Math.round(clamp01(alphaFraction) * 255f);
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
