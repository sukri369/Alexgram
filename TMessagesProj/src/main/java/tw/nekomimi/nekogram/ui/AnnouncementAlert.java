package tw.nekomimi.nekogram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
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
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

import tw.nekomimi.nekogram.TextViewEffects;

public class AnnouncementAlert extends BottomSheet {

    private TLRPC.Message message;
    private TLRPC.Chat chat;
    private LaunchActivity activity;
    private ValueAnimator pulseAnimator;
    private float pulseProgress = 0f;
    private GradientHeaderView headerView;
    private GradientButton openBtn;

    private static final int COLOR_A = 0xFF1A237E; // Dark Indigo
    private static final int COLOR_B = 0xFF4A148C; // Dark Purple
    private static final int COLOR_C = 0xFF880E4F; // Dark Pink/Magenta

    public AnnouncementAlert(LaunchActivity activity, TLRPC.Message message, TLRPC.Chat chat) {
        super(activity, false);
        this.activity = activity;
        this.message = message;
        this.chat = chat;
        setCanceledOnTouchOutside(true);
        setApplyTopPadding(false);
        setApplyBottomPadding(false);

        FrameLayout root = new FrameLayout(activity);
        containerView = root;

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        root.addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Header - slightly taller (160dp) to accommodate the megaphone icon
        headerView = new GradientHeaderView(activity);
        card.addView(headerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 160));

        // Content Scroll View
        ScrollView sv = new ScrollView(activity) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(350), MeasureSpec.AT_MOST));
            }
        };
        sv.setVerticalScrollBarEnabled(false);

        LinearLayout contentLayout = new LinearLayout(activity);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(0, 0, 0, AndroidUtilities.dp(8));

        // Photo media
        TLRPC.Photo photo = null;
        if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
            photo = message.media.photo;
        } else if (message.media instanceof TLRPC.TL_messageMediaWebPage && message.media.webpage != null) {
            photo = message.media.webpage.photo;
        }

        if (photo != null) {
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 640);
            if (size != null) {
                BackupImageView photoView = new BackupImageView(activity);
                photoView.setRoundRadius(AndroidUtilities.dp(12));
                photoView.setAspectFit(true);

                // Calculate layout dimensions in DP (NOT pixels) to avoid double-scaling inside LayoutHelper
                float displayH_dp = 160;
                if (size.w > 0) {
                    float screenW_dp = (AndroidUtilities.displaySize.x / AndroidUtilities.density) - 40;
                    displayH_dp = screenW_dp * ((float) size.h / size.w);
                    if (displayH_dp > 200) {
                        displayH_dp = 200;
                    }
                    if (displayH_dp < 40) {
                        displayH_dp = 120;
                    }
                }
                
                contentLayout.addView(photoView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, (int) displayH_dp, 20, 16, 20, 8));
                photoView.setImage(ImageLocation.getForPhoto(size, photo), "400_300", (Drawable) null, photo);
            }
        }

        // Text Content
        if (message.message != null && !message.message.isEmpty()) {
            TextView textLog = new TextViewEffects(activity);
            textLog.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textLog.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            textLog.setLineSpacing(AndroidUtilities.dp(4), 1f);
            textLog.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());

            SpannableStringBuilder ssb = new SpannableStringBuilder(message.message);
            if (message.entities != null && !message.entities.isEmpty()) {
                MessageObject.addEntitiesToText(ssb, message.entities, false, false, false, false);
            }
            textLog.setText(ssb);
            contentLayout.addView(textLog, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 12, 20, 16));
        }

        sv.addView(contentLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        card.addView(sv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Buttons Layout
        LinearLayout buttonsLayout = new LinearLayout(activity);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

        TextView closeBtn = new TextView(activity);
        closeBtn.setText(LocaleController.getString(R.string.Close));
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        closeBtn.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        closeBtn.setTypeface(AndroidUtilities.bold());
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        closeBtn.setOnClickListener(v -> dismiss());
        buttonsLayout.addView(closeBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 44, Gravity.CENTER_VERTICAL, 20, 0, 8, 0));

        openBtn = new GradientButton(activity, "Read on Channel");
        openBtn.setOnClickListener(v -> {
            dismiss();
            Bundle args = new Bundle();
            args.putLong("chat_id", chat.id);
            args.putInt("message_id", message.id);
            activity.getActionBarLayout().presentFragment(new ChatActivity(args), false, false, true, false);
        });
        buttonsLayout.addView(openBtn, LayoutHelper.createLinear(160, 44, Gravity.CENTER_VERTICAL, 8, 0, 20, 0));

        card.addView(buttonsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 8, 0, 16));

        startPulseAnimation();
    }

    private void startPulseAnimation() {
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
        pulseAnimator.setDuration(2600);
        pulseAnimator.setRepeatMode(ValueAnimator.RESTART);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
        pulseAnimator.addUpdateListener(a -> {
            pulseProgress = (float) a.getAnimatedValue();
            if (headerView != null) headerView.invalidate();
            if (openBtn != null) openBtn.invalidate();
        });
        pulseAnimator.start();
    }

    @Override
    public void dismiss() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        super.dismiss();
    }

    private class GradientHeaderView extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint decorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clipPath = new Path();
        private final Path starPath = new Path();
        private final Matrix shaderMatrix = new Matrix();
        private int lastW, lastH;

        // Sparkle properties: relative x, relative y, base size in dp, phase offset
        private final float[][] stars = {
            {0.12f, 0.20f, 6f, 0.0f},
            {0.88f, 0.28f, 7f, 0.2f},
            {0.20f, 0.70f, 5f, 0.4f},
            {0.82f, 0.75f, 6f, 0.6f},
            {0.50f, 0.12f, 5f, 0.8f}
        };

        GradientHeaderView(Context c) {
            super(c);
            setWillNotDraw(false);
            haloPaint.setStyle(Paint.Style.STROKE);
            haloPaint.setColor(Color.WHITE);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (w == lastW && h == lastH) return;
            lastW = w;
            lastH = h;
            bgPaint.setShader(new LinearGradient(-w * 0.4f, -h * 0.4f, w * 1.4f, h * 1.4f,
                    new int[]{COLOR_A, COLOR_B, COLOR_C}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            clipPath.reset();
            float r = AndroidUtilities.dp(16);
            clipPath.addRoundRect(new RectF(0, 0, w, h),
                    new float[]{r, r, r, r, 0, 0, 0, 0}, Path.Direction.CW);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            canvas.save();
            canvas.clipPath(clipPath);

            // Shifting gradient matrix rotation and movement
            shaderMatrix.reset();
            float centerX = w / 2f;
            float centerY = h / 2f;
            shaderMatrix.postRotate(pulseProgress * 360f, centerX, centerY);
            float dx = (float) Math.sin(pulseProgress * Math.PI * 2) * w * 0.12f;
            float dy = (float) Math.cos(pulseProgress * Math.PI * 2) * h * 0.12f;
            shaderMatrix.postTranslate(dx, dy);
            if (bgPaint.getShader() != null) {
                bgPaint.getShader().setLocalMatrix(shaderMatrix);
            }
            canvas.drawRect(0, 0, w, h, bgPaint);

            // Organic floating decorative background orbs
            float orb1X = w * 0.86f + (float) Math.cos(pulseProgress * Math.PI * 2) * AndroidUtilities.dp(8);
            float orb1Y = h * 0.14f + (float) Math.sin(pulseProgress * Math.PI * 2) * AndroidUtilities.dp(6);
            float orb1R = AndroidUtilities.dp(68) + (float) Math.sin(pulseProgress * Math.PI * 2) * AndroidUtilities.dp(4);
            decorPaint.setColor(0x14FFFFFF);
            canvas.drawCircle(orb1X, orb1Y, orb1R, decorPaint);

            float orb2X = w * 0.08f + (float) Math.sin(pulseProgress * Math.PI * 2) * AndroidUtilities.dp(10);
            float orb2Y = h * 0.82f + (float) Math.cos(pulseProgress * Math.PI * 2) * AndroidUtilities.dp(8);
            float orb2R = AndroidUtilities.dp(90) + (float) Math.cos(pulseProgress * Math.PI * 2) * AndroidUtilities.dp(5);
            decorPaint.setColor(0x0CFFFFFF);
            canvas.drawCircle(orb2X, orb2Y, orb2R, decorPaint);

            // Sparkles background
            for (float[] star : stars) {
                float starX = w * star[0];
                float starY = h * star[1];
                float maxR = AndroidUtilities.dp(star[2]);
                float phase = star[3];

                float starProgress = (pulseProgress + phase) % 1.0f;
                float scale = (float) Math.sin(starProgress * Math.PI);
                if (scale < 0) scale = 0;
                float r = maxR * scale;
                if (r > 0.1f) {
                    starPath.reset();
                    starPath.moveTo(starX, starY - r);
                    starPath.quadTo(starX, starY, starX + r, starY);
                    starPath.quadTo(starX, starY, starX, starY + r);
                    starPath.quadTo(starX, starY, starX - r, starY);
                    starPath.quadTo(starX, starY, starX, starY - r);
                    starPath.close();

                    starPaint.setColor(Color.WHITE);
                    starPaint.setAlpha((int) (scale * 160));
                    canvas.drawPath(starPath, starPaint);
                }
            }
            canvas.restore();

            // Expanding halo rings behind the megaphone icon
            float iconCenterX = w / 2f;
            float iconCenterY = h * 0.26f;
            float baseHaloRadius = AndroidUtilities.dp(20);
            float maxHaloRadius = AndroidUtilities.dp(44);
            for (int i = 0; i < 2; i++) {
                float ringProgress = (pulseProgress + i * 0.5f) % 1.0f;
                float haloR = baseHaloRadius + ringProgress * (maxHaloRadius - baseHaloRadius);
                int haloAlpha = (int) ((1.0f - ringProgress) * 0.22f * 255);
                if (haloAlpha > 0) {
                    haloPaint.setAlpha(haloAlpha);
                    haloPaint.setStrokeWidth(AndroidUtilities.dp(1.5f));
                    canvas.drawCircle(iconCenterX, iconCenterY, haloR, haloPaint);
                }
            }

            // Pulsing megaphone icon drawing
            Drawable icon = getContext().getResources().getDrawable(R.drawable.msg_channel);
            if (icon != null) {
                int iconSize = AndroidUtilities.dp(30);
                float iconScale = 1.0f + 0.08f * (float) Math.sin(pulseProgress * Math.PI * 2);
                canvas.save();
                canvas.scale(iconScale, iconScale, iconCenterX, iconCenterY);
                icon.setBounds((int)(iconCenterX - iconSize / 2f), (int)(iconCenterY - iconSize / 2f), 
                               (int)(iconCenterX + iconSize / 2f), (int)(iconCenterY + iconSize / 2f));
                icon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
                icon.draw(canvas);
                canvas.restore();
            }

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.WHITE);
            textPaint.setTypeface(AndroidUtilities.bold());
            textPaint.setTextSize(AndroidUtilities.dp(22));
            textPaint.setShadowLayer(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(2), 0x40000000);
            canvas.drawText("Alexgram", w / 2f, h * 0.58f, textPaint);
            textPaint.clearShadowLayer();

            textPaint.setTextSize(AndroidUtilities.dp(13f));
            textPaint.setTypeface(Typeface.DEFAULT);
            textPaint.setColor(0xCCFFFFFF);
            canvas.drawText("Official Announcement", w / 2f, h * 0.78f, textPaint);
        }
    }

    private class GradientButton extends FrameLayout {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txtPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shimmerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF btnRect = new RectF();
        private final Path buttonPath = new Path();
        private final String text;
        private float pressScale = 1f;
        private ValueAnimator pressAnim;

        GradientButton(Context ctx, String text) {
            super(ctx);
            this.text = text;
            setWillNotDraw(false);
            setClickable(true);
            txtPaint.setTextAlign(Paint.Align.CENTER);
            txtPaint.setColor(Color.WHITE);
            txtPaint.setTypeface(AndroidUtilities.bold());
            txtPaint.setTextSize(AndroidUtilities.dp(14));
            txtPaint.setShadowLayer(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(1), 0x30000000);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            bgPaint.setShader(new LinearGradient(0, 0, w, 0,
                    new int[]{COLOR_A, COLOR_B, COLOR_C}, null, Shader.TileMode.CLAMP));
            btnRect.set(0, 0, w, h);
            buttonPath.reset();
            buttonPath.addRoundRect(btnRect, h / 2f, h / 2f, Path.Direction.CW);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            canvas.save();
            canvas.scale(pressScale, pressScale, w / 2f, h / 2f);
            canvas.drawRoundRect(btnRect, h / 2f, h / 2f, bgPaint);

            // Shimmer glint sweeping across
            canvas.save();
            canvas.clipPath(buttonPath);
            float shimmerX = -w + (w * 3f) * pulseProgress;
            float shimmerWidth = AndroidUtilities.dp(35);
            shimmerPaint.reset();
            shimmerPaint.setAntiAlias(true);
            shimmerPaint.setShader(new LinearGradient(shimmerX - shimmerWidth, 0, shimmerX + shimmerWidth, 0,
                    new int[]{0x00FFFFFF, 0x3BFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, shimmerPaint);
            canvas.restore();

            canvas.drawText(text, w / 2f, h / 2f + AndroidUtilities.dp(5f), txtPaint);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    animatePress(0.95f);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    animatePress(1f);
                    break;
            }
            return super.onTouchEvent(ev);
        }

        private void animatePress(float to) {
            if (pressAnim != null) pressAnim.cancel();
            pressAnim = ValueAnimator.ofFloat(pressScale, to);
            pressAnim.setDuration(120);
            pressAnim.addUpdateListener(a -> {
                pressScale = (float) a.getAnimatedValue();
                invalidate();
            });
            pressAnim.start();
        }
    }
}
