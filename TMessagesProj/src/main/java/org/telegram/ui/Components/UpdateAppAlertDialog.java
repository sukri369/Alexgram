package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import tw.nekomimi.nekogram.TextViewEffects;

public class UpdateAppAlertDialog extends BottomSheet {

    private TLRPC.TL_help_appUpdate appUpdate;
    private int accountNum;
    private ValueAnimator pulseAnimator;
    private float pulseProgress = 0f;
    private GradientHeaderView headerView;

    // Brand gradient: Alexgram blue → purple
    private static final int COLOR_A = 0xFF0F62FE;
    private static final int COLOR_B = 0xFF8434F7;
    private static final int COLOR_C = 0xFFAA20FF;

    public UpdateAppAlertDialog(Context context, TLRPC.TL_help_appUpdate update, int account) {
        super(context, false);
        appUpdate = update;
        accountNum = account;
        setCanceledOnTouchOutside(false);
        setApplyTopPadding(false);
        setApplyBottomPadding(false);

        // Root container
        FrameLayout root = new FrameLayout(context);
        containerView = root;

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        root.addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // ── Gradient Header ──────────────────────────────────────────
        headerView = new GradientHeaderView(context);
        card.addView(headerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 195));

        // ── Changelog ────────────────────────────────────────────────
        boolean hasLog = !TextUtils.isEmpty(appUpdate.text);
        if (hasLog) {
            TextView label = new TextView(context);
            label.setText("WHAT'S NEW");
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10.5f);
            label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            label.setTypeface(AndroidUtilities.bold());
            label.setLetterSpacing(0.13f);
            card.addView(label, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 18, 20, 6));

            // thin accent line
            View accentLine = new View(context) {
                final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
                    p.setShader(new LinearGradient(0, 0, w, 0,
                        new int[]{COLOR_A, COLOR_C, 0x00000000},
                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
                }
                @Override protected void onDraw(Canvas c) { c.drawRect(0, 0, getWidth(), getHeight(), p); }
            };
            accentLine.setWillNotDraw(false);
            card.addView(accentLine, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 2, 20, 0, 0, 0));

            ScrollView sv = new ScrollView(context) {
                @Override protected void onMeasure(int ws, int hs) {
                    super.onMeasure(ws, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(170), MeasureSpec.AT_MOST));
                }
            };
            sv.setVerticalScrollBarEnabled(false);
            sv.setClipToPadding(false);

            TextView log = new TextViewEffects(context);
            log.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            log.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            log.setLineSpacing(AndroidUtilities.dp(4), 1f);

            SpannableStringBuilder ssb = new SpannableStringBuilder(appUpdate.text);
            if (appUpdate.entities != null && !appUpdate.entities.isEmpty()) {
                MessageObject.addEntitiesToText(ssb, appUpdate.entities, false, false, false, false);
            }
            log.setText(ssb);

            sv.addView(log, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 20, 10, 20, 8));
            card.addView(sv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        // ── Spacer ───────────────────────────────────────────────────
        card.addView(new View(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, hasLog ? 16 : 26));

        // ── Gradient Download Button ──────────────────────────────────
        GradientButton dlBtn = new GradientButton(context,
            LocaleController.getString(R.string.AppUpdateDownloadNow));
        dlBtn.setOnClickListener(v -> {
            if ((appUpdate.flags & 2) != 0 && appUpdate.document != null) {
                FileLoader.getInstance(accountNum).loadFile(
                    appUpdate.document, "update", FileLoader.PRIORITY_NORMAL, 1);
            } else if (!TextUtils.isEmpty(appUpdate.url)) {
                Browser.openUrl(context, appUpdate.url);
            }
            dismiss();
        });
        card.addView(dlBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 54, 20, 0, 20, 0));

        // ── Remind Later Button ───────────────────────────────────────
        TextView laterBtn = new TextView(context);
        laterBtn.setText(LocaleController.getString(R.string.AppUpdateRemindMeLater));
        laterBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        laterBtn.setTextColor(0x99000000 | (Theme.getColor(Theme.key_featuredStickers_addButton) & 0xFFFFFF));
        laterBtn.setGravity(Gravity.CENTER);
        laterBtn.setPadding(0, AndroidUtilities.dp(4), 0, 0);
        laterBtn.setOnClickListener(v -> dismiss());
        card.addView(laterBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 20, 8, 20, 20));

        // Start animations
        startPulseAnimation();
        animateIn(card);
    }

    // ── Pulse animation for badge glow ────────────────────────────────
    private void startPulseAnimation() {
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
        pulseAnimator.setDuration(1800);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseAnimator.addUpdateListener(a -> {
            pulseProgress = (float) a.getAnimatedValue();
            if (headerView != null) headerView.invalidate();
        });
        pulseAnimator.start();
    }

    // ── Entrance animation ────────────────────────────────────────────
    private void animateIn(View v) {
        v.setAlpha(0f);
        v.setTranslationY(AndroidUtilities.dp(40));
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
            ObjectAnimator.ofFloat(v, View.ALPHA, 0f, 1f),
            ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, AndroidUtilities.dp(40), 0f)
        );
        set.setDuration(350);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
    }

    @Override
    protected boolean canDismissWithSwipe() { return false; }

    @Override
    public void dismiss() {
        if (pulseAnimator != null) { pulseAnimator.cancel(); pulseAnimator = null; }
        super.dismiss();
    }

    // ══════════════════════════════════════════════════════════════════
    // INNER: Premium Gradient Header
    // ══════════════════════════════════════════════════════════════════
    private class GradientHeaderView extends View {
        private final Paint bgPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint decorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF badgeRect  = new RectF();
        private final Path  clipPath   = new Path();
        private int lastW = 0, lastH = 0;

        GradientHeaderView(Context ctx) {
            super(ctx);
            setWillNotDraw(false);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (w == lastW && h == lastH) return;
            lastW = w; lastH = h;

            bgPaint.setShader(new LinearGradient(0, 0, w, h,
                new int[]{COLOR_A, COLOR_B, COLOR_C},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));

            clipPath.reset();
            float r = AndroidUtilities.dp(20);
            clipPath.addRoundRect(new RectF(0, 0, w, h),
                new float[]{r, r, r, r, 0, 0, 0, 0}, Path.Direction.CW);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();

            // Gradient bg with rounded top corners
            canvas.save();
            canvas.clipPath(clipPath);
            canvas.drawRect(0, 0, w, h, bgPaint);

            // Glassmorphism decorative circles
            decorPaint.setColor(0x14FFFFFF);
            canvas.drawCircle(w * 0.86f, h * 0.14f, AndroidUtilities.dp(68), decorPaint);
            decorPaint.setColor(0x0CFFFFFF);
            canvas.drawCircle(w * 0.08f, h * 0.82f, AndroidUtilities.dp(90), decorPaint);
            decorPaint.setColor(0x10FFFFFF);
            canvas.drawCircle(w * 0.5f, h * -0.1f, AndroidUtilities.dp(80), decorPaint);
            canvas.restore();

            // ── App name ─────────────────────────────────────────────
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.WHITE);
            textPaint.setTypeface(AndroidUtilities.bold());
            textPaint.setTextSize(AndroidUtilities.dp(26));
            textPaint.setShadowLayer(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(2), 0x40000000);
            canvas.drawText("Alexgram", w / 2f, h * 0.37f, textPaint);
            textPaint.clearShadowLayer();

            // ── "Update Available" subtitle ───────────────────────────
            textPaint.setTextSize(AndroidUtilities.dp(12.5f));
            textPaint.setTypeface(Typeface.DEFAULT);
            textPaint.setColor(0xCCFFFFFF);
            canvas.drawText("Update Available", w / 2f, h * 0.54f, textPaint);

            // ── Version badge with pulsing glow ───────────────────────
            String vLabel = "v " + appUpdate.version;
            textPaint.setTextSize(AndroidUtilities.dp(13));
            textPaint.setTypeface(AndroidUtilities.bold());
            textPaint.setColor(Color.WHITE);

            float tw   = textPaint.measureText(vLabel);
            float padH = AndroidUtilities.dp(14);
            float padV = AndroidUtilities.dp(6);
            float bw   = tw + padH * 2;
            float bh   = AndroidUtilities.dp(13) + padV * 2;
            float cx   = w / 2f;
            float cy   = h * 0.775f;
            float br   = bh / 2f;

            badgeRect.set(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2);

            // Glow pulse
            float glowRadius = bw * 0.55f * (1f + pulseProgress * 0.35f);
            int   glowA      = (int) (50 + pulseProgress * 110);
            glowPaint.setShader(new RadialGradient(cx, cy, glowRadius,
                new int[]{Color.argb(glowA, 140, 60, 255), 0x00FFFFFF},
                null, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, glowRadius, glowPaint);

            // Badge fill
            badgePaint.setStyle(Paint.Style.FILL);
            badgePaint.setColor(0x28FFFFFF);
            canvas.drawRoundRect(badgeRect, br, br, badgePaint);

            // Badge border (animated opacity)
            int borderA = (int) (140 + pulseProgress * 115);
            badgePaint.setStyle(Paint.Style.STROKE);
            badgePaint.setStrokeWidth(AndroidUtilities.dp(1.3f));
            badgePaint.setColor(Color.argb(borderA, 255, 255, 255));
            canvas.drawRoundRect(badgeRect, br, br, badgePaint);
            badgePaint.setStyle(Paint.Style.FILL);

            // Badge text
            float textY = cy + AndroidUtilities.dp(4.5f);
            canvas.drawText(vLabel, cx, textY, textPaint);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // INNER: Gradient Download Button
    // ══════════════════════════════════════════════════════════════════
    private static class GradientButton extends FrameLayout {
        private final Paint bgPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txtPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF btnRect   = new RectF();
        private boolean isPressed     = false;
        private float   pressScale    = 1f;
        private final String text;
        private ValueAnimator pressAnim;

        GradientButton(Context ctx, String txt) {
            super(ctx);
            this.text = txt;
            setWillNotDraw(false);
            setClickable(true);

            txtPaint.setTextAlign(Paint.Align.CENTER);
            txtPaint.setColor(Color.WHITE);
            txtPaint.setTypeface(AndroidUtilities.bold());
            txtPaint.setTextSize(AndroidUtilities.dp(15));
            txtPaint.setShadowLayer(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(1), 0x30000000);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            bgPaint.setShader(new LinearGradient(0, 0, w, 0,
                new int[]{COLOR_A, COLOR_B, COLOR_C},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
            btnRect.set(0, 0, w, h);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            canvas.save();
            canvas.scale(pressScale, pressScale, w / 2f, h / 2f);
            canvas.drawRoundRect(btnRect, h / 2f, h / 2f, bgPaint);
            canvas.drawText(text, w / 2f, h / 2f + AndroidUtilities.dp(5.5f), txtPaint);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    animatePress(true);  break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    animatePress(false); break;
            }
            return super.onTouchEvent(ev);
        }

        private void animatePress(boolean down) {
            if (pressAnim != null) pressAnim.cancel();
            float target = down ? 0.95f : 1f;
            pressAnim = ValueAnimator.ofFloat(pressScale, target);
            pressAnim.setDuration(120);
            pressAnim.addUpdateListener(a -> { pressScale = (float) a.getAnimatedValue(); invalidate(); });
            pressAnim.start();
        }
    }
}
