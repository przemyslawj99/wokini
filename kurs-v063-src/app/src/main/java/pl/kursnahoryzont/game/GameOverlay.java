package pl.kursnahoryzont.game;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;

import java.util.Locale;

final class GameOverlay extends View {
    private final GameState state;
    private final Context context;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF actionRect = new RectF();
    private final RectF sailRect = new RectF();
    private final RectF oarsRect = new RectF();
    private final RectF viewRect = new RectF();
    private final RectF trimMinusRect = new RectF();
    private final RectF trimPlusRect = new RectF();
    private final RectF mapRect = new RectF();
    private final RectF journalRect = new RectF();
    private final RectF pauseRect = new RectF();
    private final RectF sensitivityMinusRect = new RectF();
    private final RectF sensitivityPlusRect = new RectF();
    private final RectF volumeMinusRect = new RectF();
    private final RectF volumePlusRect = new RectF();
    private final RectF qualityRect = new RectF();
    private final RectF testCodeRect = new RectF();
    private final RectF resumeRect = new RectF();
    private int joystickPointer = -1;
    private int lookPointer = -1;
    private float joystickCx;
    private float joystickCy;
    private float joystickRadius;
    private float knobX;
    private float knobY;
    private float lookLastX;
    private float lookLastY;
    private ToneGenerator tone;
    private int toneVolume = -1;

    GameOverlay(Context context, GameState state) {
        super(context);
        this.state = state;
        this.context = context;
        setFocusable(true);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(4f);
        stroke.setColor(Color.argb(190, 235, 224, 190));
        refreshTone();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        layoutControls(w, h);

        drawAtmosphereOverlay(c, w, h);
        drawTopHud(c, w, h);
        drawNavigationInstruments(c, w, h);
        if (!state.paused && !state.journalOpen) {
            drawJoystick(c);
            drawControls(c, w, h);
            drawNearbyStation(c, w, h);
        }
        if (state.storm > 0.32f) drawRain(c, w, h);
        if (state.mapOpen) drawMap(c, w, h);
        if (state.tradeOpen) drawTrade(c, w, h);
        if (state.journalOpen) drawJournal(c, w, h);
        if (state.paused) drawPause(c, w, h);
        if (state.messageTimer > 0f && !state.tradeOpen) drawMessage(c, w, h);
        if (state.saveNoticeTimer > 0f) drawSaveNotice(c, w, h);
        postInvalidateOnAnimation();
    }

    private void layoutControls(int w, int h) {
        joystickRadius = Math.min(w, h) * 0.135f;
        joystickCx = w * 0.135f;
        joystickCy = h * 0.79f;
        if (joystickPointer < 0) { knobX = joystickCx; knobY = joystickCy; }

        float bh = h * 0.115f;
        float bw = w * 0.135f;
        float gap = w * 0.012f;
        actionRect.set(w - bw - gap, h - bh - gap, w - gap, h - gap);
        sailRect.set(w - bw * 2f - gap * 2f, h - bh - gap,
                w - bw - gap * 2f, h - gap);
        viewRect.set(w - bw - gap, h - bh * 2f - gap * 2f,
                w - gap, h - bh - gap * 2f);
        oarsRect.set(w * 0.31f, h - bh - gap, w * 0.43f, h - gap);
        trimPlusRect.set(w * 0.58f, h - bh - gap, w * 0.70f, h - gap);
        trimMinusRect.set(w * 0.445f, h - bh - gap, w * 0.565f, h - gap);
        mapRect.set(w - w * 0.115f, h * 0.035f, w - w * 0.018f, h * 0.115f);
        journalRect.set(w - w * 0.225f, h * 0.035f, w - w * 0.128f, h * 0.115f);
        pauseRect.set(w - w * 0.335f, h * 0.035f, w - w * 0.238f, h * 0.115f);

        sensitivityMinusRect.set(w * 0.40f, h * 0.315f, w * 0.47f, h * 0.405f);
        sensitivityPlusRect.set(w * 0.63f, h * 0.315f, w * 0.70f, h * 0.405f);
        volumeMinusRect.set(w * 0.40f, h * 0.45f, w * 0.47f, h * 0.54f);
        volumePlusRect.set(w * 0.63f, h * 0.45f, w * 0.70f, h * 0.54f);
        qualityRect.set(w * 0.43f, h * 0.565f, w * 0.67f, h * 0.645f);
        testCodeRect.set(w * 0.43f, h * 0.665f, w * 0.67f, h * 0.745f);
        resumeRect.set(w * 0.40f, h * 0.775f, w * 0.70f, h * 0.855f);
    }

    private void drawTopHud(Canvas c, int w, int h) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(174, 12, 25, 31));
        c.drawRoundRect(new RectF(w * 0.018f, h * 0.025f, w * 0.865f, h * 0.142f),
                18f, 18f, paint);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(h * 0.035f);
        paint.setColor(Color.rgb(246, 232, 196));
        String mode = state.onLand ? GameState.PORT_NAMES[state.currentPort]
                : state.belowDeck ? state.interiorDeckName()
                : state.atHelm ? "Za sterem" : "Pokład górny";
        c.drawText(mode, w * 0.034f, h * 0.072f, paint);

        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextSize(h * 0.026f);
        float headingDeg = state.shipHeading * 180f / GameState.PI;
        while (headingDeg < 0f) headingDeg += 360f;
        String line = String.format(Locale.US,
                "Kurs %s %03.0f°     Prędkość %.1f kn     Wiatr %.1f m/s     %s · %s",
                state.headingName(), headingDeg, state.shipSpeed * 1.94384f,
                state.windStrength, state.timeText(), state.weatherText());
        paint.setColor(Color.rgb(226, 236, 232));
        c.drawText(line, w * 0.034f, h * 0.116f, paint);

        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(h * 0.029f);
        paint.setColor(Color.rgb(255, 220, 112));
        String economy = "Monety: " + state.gold + "    Ładownia: " + state.cargoTotal()
                + "/" + state.cargoCapacity() + "    Keja: " + state.quayCargoTotal();
        c.drawText(economy, w * 0.59f, h * 0.073f, paint);
        drawButton(c, mapRect, "MAPA", false);
        drawButton(c, journalRect, "DZIENNIK", false);
        drawButton(c, pauseRect, "PAUZA", false);
    }

    private void drawNavigationInstruments(Canvas c, int w, int h) {
        float radius = h * 0.057f;
        float cy = h * 0.215f;
        drawDial(c, w * 0.88f, cy, radius, state.shipHeading, "KOMPAS", "N");
        drawDial(c, w * 0.955f, cy, radius, state.relativeWind(), "WIATR", "W");

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(165, 13, 25, 29));
        RectF status = new RectF(w * 0.735f, h * 0.287f, w * 0.995f, h * 0.345f);
        c.drawRoundRect(status, 13f, 13f, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(h * 0.022f);
        paint.setColor(Color.rgb(245, 226, 178));
        String berth = state.moored ? "CUMY" : state.anchorDropped ? "KOTWICA" : "SWOBODNY";
        String sail = state.sailRaised ? String.format(Locale.US, "ŻAGIEL %.0f%%", state.sailTrim * 100f)
                : "ŻAGIEL ZRZUCONY";
        c.drawText(berth + "   ·   " + sail, status.centerX(),
                status.centerY() - (paint.ascent() + paint.descent()) * 0.5f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawDial(Canvas c, float cx, float cy, float radius, float angle,
                          String label, String north) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(188, 16, 28, 31));
        c.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(Color.rgb(226, 204, 154));
        c.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(radius * 0.34f);
        paint.setColor(Color.WHITE);
        c.drawText(north, cx, cy - radius * 0.48f, paint);
        Path arrow = new Path();
        float dx = (float)Math.sin(angle) * radius * 0.67f;
        float dy = -(float)Math.cos(angle) * radius * 0.67f;
        float px = (float)Math.cos(angle) * radius * 0.18f;
        float py = (float)Math.sin(angle) * radius * 0.18f;
        arrow.moveTo(cx + dx, cy + dy);
        arrow.lineTo(cx - dx * 0.35f + px, cy - dy * 0.35f + py);
        arrow.lineTo(cx - dx * 0.35f - px, cy - dy * 0.35f - py);
        arrow.close();
        paint.setColor(Color.rgb(200, 63, 45));
        c.drawPath(arrow, paint);
        paint.setTextSize(radius * 0.25f);
        paint.setColor(Color.rgb(232, 219, 184));
        c.drawText(label, cx, cy + radius * 1.32f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawJoystick(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(75, 10, 22, 28));
        c.drawCircle(joystickCx, joystickCy, joystickRadius, paint);
        paint.setColor(Color.argb(110, 232, 218, 177));
        c.drawCircle(knobX, knobY, joystickRadius * 0.43f, paint);
        c.drawCircle(joystickCx, joystickCy, joystickRadius, stroke);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(joystickRadius * 0.22f);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setColor(Color.WHITE);
        String joystickMode = state.atHelm ? "STER" : state.handlingSheet ? "SZOT" : "RUCH";
        c.drawText(joystickMode, joystickCx,
                joystickCy + joystickRadius * 0.08f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawControls(Canvas c, int w, int h) {
        drawButton(c, actionRect, state.actionLabel(), true);
        if (state.handlingSheet) {
            drawButton(c, trimMinusRect, "LUZUJ", true);
            drawButton(c, trimPlusRect, "WYBIERAJ", true);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(h * 0.026f);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setColor(Color.WHITE);
            String trim = String.format(Locale.US, "Szot %.0f%% — reguluj drążkiem lub przyciskami",
                    state.sailTrim * 100f);
            c.drawText(trim, w * 0.575f, h * 0.83f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        } else if (state.atHelm) {
            drawButton(c, sailRect, state.sailRaised ? "ZRZUĆ ŻAGIEL" : "POSTAW ŻAGIEL", true);
            drawButton(c, viewRect, state.helmThirdPerson ? "WIDOK 1 OS." : "WIDOK 3 OS.", true);
            drawButton(c, oarsRect, state.oarsActive ? "STOP WIOSŁA" : "WIOSŁA", true);
            drawButton(c, trimMinusRect, "LUZUJ", true);
            drawButton(c, trimPlusRect, "WYBIERAJ", true);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(h * 0.026f);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setColor(Color.WHITE);
            c.drawText(String.format(Locale.US, "Szot %.0f%%    Ster %+.0f%%    Wiosła: %s",
                            state.sailTrim * 100f, state.rudder * 100f,
                            state.oarsActive ? "PRACA" : "STOP"),
                    w * 0.575f, h * 0.83f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawAtmosphereOverlay(Canvas c, int w, int h) {
        float morning = GameState.clamp(1f - Math.abs(state.worldHours - 6f) / 2.5f, 0f, 1f);
        float fog = GameState.clamp(0.05f + morning * 0.24f + state.storm * 0.24f, 0f, 0.42f);
        int alpha = (int)(fog * 255f);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0f, h * 0.28f, 0f, h * 0.76f,
                Color.argb(0, 205, 220, 220), Color.argb(alpha, 194, 211, 211),
                Shader.TileMode.CLAMP));
        c.drawRect(0f, h * 0.25f, w, h * 0.78f, paint);
        paint.setShader(null);
    }

    private void drawNearbyStation(Canvas c, int w, int h) {
        String label = state.nearbyStationLabel();
        if (label.length() == 0) return;
        float pulse = 0.75f + 0.25f * (float)Math.sin(System.currentTimeMillis() * 0.006);
        RectF panel = new RectF(w * 0.36f, h * 0.62f, w * 0.64f, h * 0.705f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb((int)(190f * pulse), 72, 49, 22));
        c.drawRoundRect(panel, 18f, 18f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(Color.argb((int)(255f * pulse), 255, 220, 128));
        c.drawRoundRect(panel, 18f, 18f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(h * 0.029f);
        paint.setColor(Color.WHITE);
        c.drawText(label, panel.centerX(), panel.centerY() - (paint.ascent() + paint.descent()) * 0.5f,
                paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawJournal(Canvas c, int w, int h) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(235, 27, 31, 29));
        RectF panel = new RectF(w * 0.18f, h * 0.10f, w * 0.82f, h * 0.90f);
        c.drawRoundRect(panel, 24f, 24f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        paint.setColor(Color.rgb(220, 189, 123));
        c.drawRoundRect(panel, 24f, 24f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(h * 0.046f);
        paint.setColor(Color.rgb(246, 226, 178));
        c.drawText("DZIENNIK KUPCA", panel.centerX(), panel.top + h * 0.075f, paint);
        paint.setTextSize(h * 0.03f);
        paint.setColor(Color.WHITE);
        c.drawText("Cel: " + state.journalObjective(), panel.centerX(), panel.top + h * 0.135f, paint);

        String[] steps = {"Kup towar", "Załaduj z kei", "Przepłyń do innego portu",
                "Wyładuj na kei", "Sprzedaj na targu"};
        for (int i = 0; i < steps.length; i++) {
            float y = panel.top + h * (0.23f + i * 0.105f);
            boolean done = state.journalStage > i;
            boolean active = state.journalStage == i;
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(h * 0.032f);
            paint.setTypeface(active ? android.graphics.Typeface.DEFAULT_BOLD
                    : android.graphics.Typeface.DEFAULT);
            paint.setColor(done ? Color.rgb(126, 210, 136)
                    : active ? Color.rgb(255, 220, 112) : Color.rgb(172, 178, 176));
            c.drawText(done ? "✓" : active ? "●" : "○", panel.left + w * 0.055f, y, paint);
            c.drawText(steps[i], panel.left + w * 0.095f, y, paint);
        }
        RectF close = new RectF(panel.centerX() - w * 0.11f, panel.bottom - h * 0.12f,
                panel.centerX() + w * 0.11f, panel.bottom - h * 0.035f);
        drawButton(c, close, "ZAMKNIJ", true);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawPause(Canvas c, int w, int h) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(155, 0, 0, 0));
        c.drawRect(0f, 0f, w, h, paint);
        RectF panel = new RectF(w * 0.28f, h * 0.11f, w * 0.72f, h * 0.89f);
        paint.setColor(Color.argb(247, 24, 30, 31));
        c.drawRoundRect(panel, 26f, 26f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        paint.setColor(Color.rgb(218, 188, 126));
        c.drawRoundRect(panel, 26f, 26f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(h * 0.05f);
        paint.setColor(Color.rgb(246, 226, 178));
        c.drawText("PAUZA I USTAWIENIA", panel.centerX(), panel.top + h * 0.075f, paint);

        paint.setTextSize(h * 0.031f);
        paint.setColor(Color.WHITE);
        c.drawText(String.format(Locale.US, "Czułość kamery: %.0f%%", state.cameraSensitivity * 100f),
                panel.centerX(), h * 0.285f, paint);
        drawButton(c, sensitivityMinusRect, "−", true);
        drawButton(c, sensitivityPlusRect, "+", true);

        c.drawText(String.format(Locale.US, "Głośność: %.0f%%", state.masterVolume * 100f),
                panel.centerX(), h * 0.42f, paint);
        drawButton(c, volumeMinusRect, "−", true);
        drawButton(c, volumePlusRect, "+", true);

        c.drawText("Jakość grafiki", panel.centerX(), h * 0.545f, paint);
        drawButton(c, qualityRect, state.qualityText(), true);
        drawButton(c, testCodeRect, "KOD TESTOWY", true);
        drawButton(c, resumeRect, "WRÓĆ DO GRY", true);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawSaveNotice(Canvas c, int w, int h) {
        float fade = Math.min(1f, state.saveNoticeTimer);
        RectF panel = new RectF(w * 0.40f, h * 0.285f, w * 0.60f, h * 0.355f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb((int)(220f * fade), 29, 83, 55));
        c.drawRoundRect(panel, 16f, 16f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(Color.argb((int)(255f * fade), 148, 233, 167));
        c.drawRoundRect(panel, 16f, 16f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(h * 0.03f);
        paint.setColor(Color.argb((int)(255f * fade), 255, 255, 255));
        c.drawText("GRA ZAPISANA", panel.centerX(), panel.centerY()
                - (paint.ascent() + paint.descent()) * 0.5f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void refreshTone() {
        int volume = Math.round(state.masterVolume * 100f);
        if (volume == toneVolume && tone != null) return;
        if (tone != null) tone.release();
        toneVolume = volume;
        tone = new ToneGenerator(AudioManager.STREAM_MUSIC, volume);
    }

    private void playClick() {
        refreshTone();
        if (state.masterVolume > 0f) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 45);
    }

    private void playSave() {
        refreshTone();
        if (state.masterVolume > 0f) tone.startTone(ToneGenerator.TONE_PROP_ACK, 120);
    }

    private void drawButton(Canvas c, RectF r, String label, boolean active) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(active ? Color.argb(190, 91, 53, 24) : Color.argb(125, 38, 45, 47));
        c.drawRoundRect(r, 18f, 18f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(active ? Color.rgb(236, 205, 143) : Color.rgb(138, 145, 145));
        c.drawRoundRect(r, 18f, 18f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(Math.min(r.height() * 0.25f, r.width() / Math.max(6f, label.length()) * 1.45f));
        paint.setColor(active ? Color.WHITE : Color.LTGRAY);
        c.drawText(label, r.centerX(), r.centerY() - (paint.ascent() + paint.descent()) * 0.5f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawMessage(Canvas c, int w, int h) {
        paint.setTextSize(h * 0.031f);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        float tw = paint.measureText(state.message);
        RectF r = new RectF(w * 0.5f - tw * 0.58f, h * 0.18f,
                w * 0.5f + tw * 0.58f, h * 0.25f);
        paint.setColor(Color.argb(195, 15, 22, 25));
        c.drawRoundRect(r, 16f, 16f, paint);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(state.message, w * 0.5f, r.centerY() - (paint.ascent() + paint.descent()) * 0.5f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawRain(Canvas c, int w, int h) {
        int alpha = 18 + (int)(state.storm * 34f);
        paint.setStrokeWidth(1f);
        paint.setColor(Color.argb(alpha, 170, 198, 210));
        int count = 30 + (int)(state.storm * 32f);
        long tick = System.currentTimeMillis() / 18L;
        for (int i = 0; i < count; i++) {
            float x = ((i * 211f + tick * 13f) % (w + 80f)) - 40f;
            float y = ((i * 97f + tick * 19f) % (h + 60f)) - 30f;
            float length = 10f + (i % 5) * 2f;
            c.drawLine(x, y, x - length * 0.28f, y + length, paint);
        }
    }

    private void drawMap(Canvas c, int w, int h) {
        RectF panel = new RectF(w * 0.19f, h * 0.12f, w * 0.81f, h * 0.88f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(242, 222, 205, 156));
        c.drawRoundRect(panel, 24f, 24f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        paint.setColor(Color.rgb(72, 45, 24));
        c.drawRoundRect(panel, 24f, 24f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(h * 0.045f);
        paint.setColor(Color.rgb(63, 39, 20));
        c.drawText("ARCHIPELAG SZAFIROWY", panel.centerX(), panel.top + h * 0.075f, paint);

        for (int i = 0; i < GameState.PORT_X.length; i++) {
            float mx = mapX(GameState.PORT_X[i], panel);
            float my = mapY(GameState.PORT_Z[i], panel);
            paint.setColor(Color.rgb(87, 121, 55));
            c.drawCircle(mx, my, 24f + GameState.PORT_R[i] * 0.35f, paint);
            paint.setColor(Color.rgb(62, 41, 22));
            paint.setTextSize(h * 0.026f);
            c.drawText(GameState.PORT_NAMES[i], mx, my - 34f, paint);
        }
        float sx = mapX(state.shipX, panel);
        float sy = mapY(state.shipZ, panel);
        Path ship = new Path();
        ship.moveTo(sx, sy - 22f);
        ship.lineTo(sx - 13f, sy + 16f);
        ship.lineTo(sx + 13f, sy + 16f);
        ship.close();
        paint.setColor(Color.rgb(151, 30, 20));
        c.drawPath(ship, paint);
        paint.setTextSize(h * 0.023f);
        c.drawText("Twój statek", sx, sy + 42f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private float mapX(float worldX, RectF p) {
        return p.left + p.width() * (worldX + 90f) / 530f;
    }

    private float mapY(float worldZ, RectF p) {
        return p.bottom - p.height() * (worldZ + 80f) / 570f;
    }

    private void drawTrade(Canvas c, int w, int h) {
        RectF panel = tradePanel(w, h);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(246, 25, 30, 29));
        c.drawRoundRect(panel, 24f, 24f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        paint.setColor(Color.rgb(217, 188, 125));
        c.drawRoundRect(panel, 24f, 24f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(h * 0.043f);
        paint.setColor(Color.rgb(245, 224, 178));
        c.drawText("TARG I STOCZNIA — " + GameState.PORT_NAMES[state.currentPort],
                panel.centerX(), panel.top + h * 0.062f, paint);
        paint.setTextSize(h * 0.025f);
        c.drawText("Monety: " + state.gold + "     Statek: " + state.shipName()
                        + "     Ładownia: " + state.cargoTotal() + "/" + state.cargoCapacity()
                        + "     Na kei: " + state.quayCargoTotal(),
                panel.centerX(), panel.top + h * 0.105f, paint);

        for (int i = 0; i < GameState.GOODS.length; i++) {
            RectF row = tradeRow(w, h, i);
            paint.setColor(i % 2 == 0 ? Color.argb(120, 82, 68, 45) : Color.argb(85, 110, 93, 60));
            c.drawRoundRect(row, 12f, 12f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(Color.WHITE);
            paint.setTextSize(h * 0.029f);
            c.drawText(GameState.GOODS[i], row.left + w * 0.018f, row.centerY() - 4f, paint);
            paint.setTextSize(h * 0.021f);
            c.drawText("Cena: " + GameState.PRICES[state.currentPort][i]
                            + "    Statek: " + state.cargo[i] + "    Keja: " + state.quayCargo[i],
                    row.left + w * 0.018f, row.centerY() + h * 0.030f, paint);
            RectF buy = tradeBuyRect(w, h, i);
            RectF sell = tradeSellRect(w, h, i);
            drawButton(c, buy, "KUP", true);
            drawButton(c, sell, "SPRZEDAJ",
                    state.quayPort == state.currentPort && state.quayCargo[i] > 0);
        }

        RectF brigRow = shipyardRow(w, h, 0);
        paint.setColor(Color.argb(120, 55, 66, 74));
        c.drawRoundRect(brigRow, 12f, 12f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(Color.rgb(245, 224, 178));
        paint.setTextSize(h * 0.027f);
        c.drawText("STOCZNIA: BRYG", brigRow.left + w * 0.018f, brigRow.centerY() - h * 0.012f, paint);
        paint.setTextSize(h * 0.020f);
        String brigInfo = state.brigOwned
                ? "Kupiony • większy kadłub • ładownia " + GameState.BRIG_CARGO_CAPACITY
                : "Cena: " + GameState.BRIG_PRICE + " monet • ładownia " + GameState.BRIG_CARGO_CAPACITY;
        c.drawText(brigInfo, brigRow.left + w * 0.018f, brigRow.centerY() + h * 0.025f, paint);
        drawButton(c, shipyardButtonRect(w, h, 0), state.brigShipyardButtonText(), true);

        RectF frigateRow = shipyardRow(w, h, 1);
        paint.setColor(Color.argb(120, 60, 70, 80));
        c.drawRoundRect(frigateRow, 12f, 12f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(Color.rgb(245, 224, 178));
        paint.setTextSize(h * 0.027f);
        c.drawText("STOCZNIA: FREGATA", frigateRow.left + w * 0.018f, frigateRow.centerY() - h * 0.012f, paint);
        paint.setTextSize(h * 0.020f);
        String frigateInfo = state.frigateOwned
                ? "Kupiona • nowy kadłub • ładownia " + GameState.FRIGATE_CARGO_CAPACITY
                : "Cena: " + GameState.FRIGATE_PRICE + " monet • ładownia " + GameState.FRIGATE_CARGO_CAPACITY;
        c.drawText(frigateInfo, frigateRow.left + w * 0.018f, frigateRow.centerY() + h * 0.025f, paint);
        drawButton(c, shipyardButtonRect(w, h, 1), state.frigateShipyardButtonText(), true);

        RectF close = tradeCloseRect(w, h);
        drawButton(c, close, "ZAMKNIJ", true);
    }

    private RectF tradePanel(int w, int h) {
        return new RectF(w * 0.16f, h * 0.055f, w * 0.84f, h * 0.945f);
    }

    private RectF tradeRow(int w, int h, int i) {
        float top = h * (0.19f + i * 0.155f);
        return new RectF(w * 0.20f, top, w * 0.80f, top + h * 0.115f);
    }

    private RectF tradeBuyRect(int w, int h, int i) {
        RectF row = tradeRow(w, h, i);
        return new RectF(w * 0.60f, row.top + h * 0.016f, w * 0.68f, row.bottom - h * 0.016f);
    }

    private RectF tradeSellRect(int w, int h, int i) {
        RectF row = tradeRow(w, h, i);
        return new RectF(w * 0.69f, row.top + h * 0.016f, w * 0.785f, row.bottom - h * 0.016f);
    }

    private RectF shipyardRow(int w, int h, int index) {
        float top = index == 0 ? h * 0.64f : h * 0.765f;
        return new RectF(w * 0.20f, top, w * 0.80f, top + h * 0.10f);
    }

    private RectF shipyardButtonRect(int w, int h, int index) {
        RectF row = shipyardRow(w, h, index);
        return new RectF(w * 0.59f, row.top + h * 0.014f, w * 0.785f, row.bottom - h * 0.014f);
    }

    private RectF tradeCloseRect(int w, int h) {
        RectF panel = tradePanel(w, h);
        return new RectF(panel.centerX() - w * 0.08f, h * 0.885f,
                panel.centerX() + w * 0.08f, h * 0.935f);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int index = e.getActionIndex();
        int id = e.getPointerId(index);

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            float x = e.getX(index);
            float y = e.getY(index);
            if (state.paused) {
                handlePauseTap(x, y);
                return true;
            }
            if (pauseRect.contains(x, y)) {
                state.togglePause();
                cancelPointers();
                playClick();
                return true;
            }
            if (state.journalOpen) {
                handleJournalTap(x, y);
                return true;
            }
            if (journalRect.contains(x, y)) {
                state.journalOpen = true;
                state.mapOpen = false;
                state.tradeOpen = false;
                cancelPointers();
                playClick();
                return true;
            }
            if (state.tradeOpen) {
                handleTradeTap(x, y);
                return true;
            }
            if (mapRect.contains(x, y)) {
                state.mapOpen = !state.mapOpen;
                state.tradeOpen = false;
                playClick();
                return true;
            }
            if (state.mapOpen) {
                state.mapOpen = false;
                return true;
            }
            if (actionRect.contains(x, y)) { runAction(); return true; }
            if (state.atHelm && viewRect.contains(x, y)) {
                state.toggleHelmView();
                playClick();
                return true;
            }
            if (state.atHelm && sailRect.contains(x, y)) {
                state.toggleSail();
                playClick();
                return true;
            }
            if (state.atHelm && oarsRect.contains(x, y)) {
                state.toggleOars();
                playClick();
                return true;
            }
            if (state.atHelm && trimMinusRect.contains(x, y)) {
                state.trimSail(-0.08f);
                playClick();
                return true;
            }
            if (state.atHelm && trimPlusRect.contains(x, y)) {
                state.trimSail(0.08f);
                playClick();
                return true;
            }
            if (state.handlingSheet && trimMinusRect.contains(x, y)) {
                state.sailTrim = GameState.clamp(state.sailTrim - 0.08f, 0.05f, 1f);
                state.showMessage("Luzujesz szot");
                playClick();
                return true;
            }
            if (state.handlingSheet && trimPlusRect.contains(x, y)) {
                state.sailTrim = GameState.clamp(state.sailTrim + 0.08f, 0.05f, 1f);
                state.showMessage("Wybierasz szot");
                playClick();
                return true;
            }

            float dx = x - joystickCx;
            float dy = y - joystickCy;
            if (x < getWidth() * 0.38f && y > getHeight() * 0.42f
                    && joystickPointer < 0) {
                joystickPointer = id;
                updateJoystick(x, y);
            } else if (lookPointer < 0) {
                lookPointer = id;
                lookLastX = x;
                lookLastY = y;
            }
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < e.getPointerCount(); i++) {
                int pointerId = e.getPointerId(i);
                float x = e.getX(i);
                float y = e.getY(i);
                if (pointerId == joystickPointer) updateJoystick(x, y);
                if (pointerId == lookPointer) {
                    state.cameraDeltaX += x - lookLastX;
                    state.cameraDeltaY += y - lookLastY;
                    lookLastX = x;
                    lookLastY = y;
                }
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP
                || action == MotionEvent.ACTION_CANCEL) {
            if (id == joystickPointer || action == MotionEvent.ACTION_CANCEL) {
                joystickPointer = -1;
                state.moveX = 0f;
                state.moveY = 0f;
                knobX = joystickCx;
                knobY = joystickCy;
            }
            if (id == lookPointer || action == MotionEvent.ACTION_CANCEL) lookPointer = -1;
            return true;
        }
        return true;
    }

    private void updateJoystick(float x, float y) {
        float dx = x - joystickCx;
        float dy = y - joystickCy;
        float len = (float)Math.sqrt(dx * dx + dy * dy);
        if (len > joystickRadius) {
            dx = dx / len * joystickRadius;
            dy = dy / len * joystickRadius;
        }
        knobX = joystickCx + dx;
        knobY = joystickCy + dy;
        // OpenGL camera faces the ship's +Z axis, so screen-left is local +X.
        // Invert only the horizontal input; vertical movement remains unchanged.
        state.moveX = -dx / joystickRadius;
        state.moveY = -dy / joystickRadius;
    }

    private void handleTradeTap(float x, float y) {
        int w = getWidth();
        int h = getHeight();
        for (int i = 0; i < GameState.GOODS.length; i++) {
            if (tradeBuyRect(w, h, i).contains(x, y)) { state.buy(i); playClick(); return; }
            if (tradeSellRect(w, h, i).contains(x, y)) { state.sell(i); playClick(); return; }
        }
        if (shipyardButtonRect(w, h, 0).contains(x, y)) {
            state.brigShipyardAction();
            playClick();
            return;
        }
        if (shipyardButtonRect(w, h, 1).contains(x, y)) {
            state.frigateShipyardAction();
            playClick();
            return;
        }
        RectF panel = tradePanel(w, h);
        if (tradeCloseRect(w, h).contains(x, y) || !panel.contains(x, y)) {
            state.tradeOpen = false;
            playClick();
        }
    }

    private void runAction() {
        float before = state.saveNoticeTimer;
        state.interact();
        if (state.saveNoticeTimer > before) playSave();
        else playClick();
    }

    private void handleJournalTap(float x, float y) {
        int w = getWidth();
        int h = getHeight();
        RectF panel = new RectF(w * 0.18f, h * 0.10f, w * 0.82f, h * 0.90f);
        RectF close = new RectF(panel.centerX() - w * 0.11f, panel.bottom - h * 0.12f,
                panel.centerX() + w * 0.11f, panel.bottom - h * 0.035f);
        if (close.contains(x, y) || journalRect.contains(x, y) || !panel.contains(x, y)) {
            state.journalOpen = false;
            playClick();
        }
    }

    private void handlePauseTap(float x, float y) {
        if (sensitivityMinusRect.contains(x, y)) state.adjustSensitivity(-0.1f);
        else if (sensitivityPlusRect.contains(x, y)) state.adjustSensitivity(0.1f);
        else if (volumeMinusRect.contains(x, y)) state.adjustVolume(-0.1f);
        else if (volumePlusRect.contains(x, y)) state.adjustVolume(0.1f);
        else if (qualityRect.contains(x, y)) state.cycleQuality();
        else if (testCodeRect.contains(x, y)) {
            showTestCodeDialog();
            return;
        }
        else if (resumeRect.contains(x, y) || pauseRect.contains(x, y)) state.togglePause();
        else return;
        refreshTone();
        playClick();
    }

    private void showTestCodeDialog() {
        final EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setHint("Wpisz kod testowy");
        new AlertDialog.Builder(context)
                .setTitle("Kod testowy")
                .setMessage("Kod pozwala szybko zasilić konto do testowania sklepu i statków.")
                .setView(input)
                .setPositiveButton("ZATWIERDŹ", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        boolean ok = state.redeemTestGoldCode(input.getText().toString());
                        if (ok) playSave(); else playClick();
                    }
                })
                .setNegativeButton("ANULUJ", null)
                .show();
    }

    private void cancelPointers() {
        joystickPointer = -1;
        lookPointer = -1;
        state.moveX = 0f;
        state.moveY = 0f;
        knobX = joystickCx;
        knobY = joystickCy;
    }

    @Override protected void onDetachedFromWindow() {
        if (tone != null) {
            tone.release();
            tone = null;
        }
        super.onDetachedFromWindow();
    }
}
