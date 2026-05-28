package tw.nekomimi.nekogram.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.*;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.animation.ValueAnimator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Random;

public class BirthdayAlert extends Dialog {

    private final boolean isTest;
    private ConfettiView confettiView;
    private GlassmorphicCardView cardView;
    private TextView cakeEmojiView;
    private GlassmorphicButton makeWishButton;
    private ValueAnimator pulseAnimator;
    private boolean wishMade = false;

    public BirthdayAlert(Context context, boolean isTest) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        this.isTest = isTest;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.setStatusBarColor(Color.TRANSPARENT);
                window.setNavigationBarColor(Color.TRANSPARENT);
            }
        }

        // Root FrameLayout
        FrameLayout rootLayout = new FrameLayout(getContext());
        rootLayout.setBackgroundColor(0xE60A0E17); // Premium deep obsidian space color
        setContentView(rootLayout);

        // Confetti View covering whole screen
        confettiView = new ConfettiView(getContext());
        rootLayout.addView(confettiView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Glassmorphic Card View
        cardView = new GlassmorphicCardView(getContext());
        int cardWidth = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(40), AndroidUtilities.dp(340));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                cardWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        rootLayout.addView(cardView, cardParams);

        // Card Content Container
        LinearLayout contentLayout = new LinearLayout(getContext());
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        contentLayout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(32), AndroidUtilities.dp(24), AndroidUtilities.dp(24));
        cardView.addView(contentLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Animated Cake Emoji
        cakeEmojiView = new TextView(getContext());
        cakeEmojiView.setText("🎂");
        cakeEmojiView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 64);
        cakeEmojiView.setGravity(Gravity.CENTER);
        contentLayout.addView(cakeEmojiView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        // Start pulse animation for cake
        pulseAnimator = ValueAnimator.ofFloat(1.0f, 1.15f);
        pulseAnimator.setDuration(1200);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setInterpolator(new OvershootInterpolator());
        pulseAnimator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            cakeEmojiView.setScaleX(value);
            cakeEmojiView.setScaleY(value);
        });
        pulseAnimator.start();

        // Happy Birthday Title
        TextView titleView = new TextView(getContext());
        int currentAccount = UserConfig.selectedAccount;
        TLRPC.User currentUser = UserConfig.getInstance(currentAccount).getCurrentUser();
        String name = currentUser != null ? UserObject.getUserName(currentUser) : "";
        titleView.setText("Happy Birthday,\n" + name + "! 🎉");
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        titleView.setTextColor(Color.WHITE);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        titleView.setLineSpacing(AndroidUtilities.dp(4), 1.0f);
        contentLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 0));

        // Subtext / Wish message
        TextView messageView = new TextView(getContext());
        messageView.setText("Today is all about you! We are wishing you a legendary, god-level year ahead. May your path be filled with light, joy, and infinite success! 🚀✨");
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        messageView.setTextColor(0xFFB0C4DE); // Light Steel Blue
        messageView.setGravity(Gravity.CENTER);
        messageView.setLineSpacing(AndroidUtilities.dp(4), 1.0f);
        contentLayout.addView(messageView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        // Make a Wish Button
        makeWishButton = new GlassmorphicButton(getContext(), "Make a Wish 🌟");
        makeWishButton.setOnClickListener(v -> {
            if (wishMade) return;
            wishMade = true;

            // Blow out the candles (change cake emoji)
            cakeEmojiView.setText("🍰");

            // Explosive confetti burst from the button's center coordinates
            int[] loc = new int[2];
            makeWishButton.getLocationOnScreen(loc);
            float cx = loc[0] + makeWishButton.getWidth() / 2f;
            float cy = loc[1] + makeWishButton.getHeight() / 2f;
            confettiView.triggerBurst(cx, cy, 150);

            // Play premium haptic vibration feedback
            triggerExplosionVibration();

            // Animate button update
            makeWishButton.updateText("Wish Sent to the Stars! 🌌✨");
            makeWishButton.setEnabled(false);
        });
        contentLayout.addView(makeWishButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 0));

        // Close Button
        TextView closeBtn = new TextView(getContext());
        closeBtn.setText(LocaleController.getString(R.string.Close));
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        closeBtn.setTextColor(0xFF8B9BB4);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        closeBtn.setOnClickListener(v -> dismiss());
        contentLayout.addView(closeBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 0));

        // Card Entry Animation
        cardView.setAlpha(0f);
        cardView.setScaleX(0.85f);
        cardView.setScaleY(0.85f);
        cardView.animate()
                .alpha(1.0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(450)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();
    }

    private void triggerExplosionVibration() {
        try {
            Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = new long[]{0, 60, 40, 90, 30, 140};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    vibrator.vibrate(pattern, -1);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void dismiss() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
        }
        if (cardView != null) {
            cardView.stop();
        }
        super.dismiss();
    }

    public static boolean isToday(TLRPC.UserFull userFull) {
        if (userFull == null || userFull.birthday == null) return false;
        Calendar cal = Calendar.getInstance();
        int todayMonth = cal.get(Calendar.MONTH) + 1;
        int todayDay = cal.get(Calendar.DAY_OF_MONTH);
        return userFull.birthday.month == todayMonth && userFull.birthday.day == todayDay;
    }

    public static boolean shouldShowBirthdayWish(TLRPC.UserFull userFull) {
        if (!isToday(userFull)) {
            return false;
        }
        int currentAccount = UserConfig.selectedAccount;
        if (!UserConfig.getInstance(currentAccount).isClientActivated()) {
            return false;
        }

        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        int lastShownYear = org.telegram.messenger.MessagesController.getGlobalMainSettings().getInt("last_shown_birthday_year", 0);
        return lastShownYear != currentYear;
    }

    public static void show(Activity activity, boolean isTest) {
        if (activity == null || activity.isFinishing()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) return;
        try {
            BirthdayAlert alert = new BirthdayAlert(activity, isTest);
            alert.show();

            if (!isTest) {
                int currentYear = Calendar.getInstance().get(Calendar.YEAR);
                org.telegram.messenger.MessagesController.getGlobalMainSettings().edit().putInt("last_shown_birthday_year", currentYear).apply();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Glassmorphic Card containing contents with rotating gradient border
    private static class GlassmorphicCardView extends FrameLayout {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float borderProgress = 0f;
        private ValueAnimator borderAnimator;

        public GlassmorphicCardView(Context context) {
            super(context);
            setWillNotDraw(false);
            bgPaint.setColor(0xF2161D26); // Glass dark navy base

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(AndroidUtilities.dp(2));

            borderAnimator = ValueAnimator.ofFloat(0f, 1f);
            borderAnimator.setDuration(5000);
            borderAnimator.setRepeatCount(ValueAnimator.INFINITE);
            borderAnimator.setRepeatMode(ValueAnimator.RESTART);
            borderAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
            borderAnimator.addUpdateListener(animation -> {
                borderProgress = (float) animation.getAnimatedValue();
                invalidate();
            });
            borderAnimator.start();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            rect.set(AndroidUtilities.dp(1), AndroidUtilities.dp(1), w - AndroidUtilities.dp(1), h - AndroidUtilities.dp(1));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float r = AndroidUtilities.dp(24);
            canvas.drawRoundRect(rect, r, r, bgPaint);

            int w = getWidth();
            int h = getHeight();
            Matrix matrix = new Matrix();
            matrix.postRotate(borderProgress * 360f, w / 2f, h / 2f);
            LinearGradient gradient = new LinearGradient(0, 0, w, h,
                    new int[]{0xFFFF007F, 0xFF7F00FF, 0xFF00F5FF, 0xFFFF007F},
                    null, Shader.TileMode.CLAMP);
            gradient.setLocalMatrix(matrix);
            borderPaint.setShader(gradient);
            canvas.drawRoundRect(rect, r, r, borderPaint);
        }

        public void stop() {
            if (borderAnimator != null) {
                borderAnimator.cancel();
            }
        }
    }

    // Glassmorphic interactive wish button
    private static class GlassmorphicButton extends FrameLayout {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txtPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private String text;
        private float pressScale = 1.0f;
        private ValueAnimator pressAnim;

        public GlassmorphicButton(Context context, String text) {
            super(context);
            this.text = text;
            setWillNotDraw(false);
            setClickable(true);

            txtPaint.setTextAlign(Paint.Align.CENTER);
            txtPaint.setColor(Color.WHITE);
            txtPaint.setTypeface(AndroidUtilities.bold());
            txtPaint.setTextSize(AndroidUtilities.dp(15));

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(AndroidUtilities.dp(1));
            borderPaint.setColor(0x40FFFFFF);
        }

        public void updateText(String newText) {
            this.text = newText;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            rect.set(AndroidUtilities.dp(1), AndroidUtilities.dp(1), w - AndroidUtilities.dp(1), h - AndroidUtilities.dp(1));
            bgPaint.setShader(new LinearGradient(0, 0, w, 0,
                    new int[]{0xFFFF007F, 0xFF7F00FF}, null, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            canvas.save();
            canvas.scale(pressScale, pressScale, w / 2f, h / 2f);

            float r = h / 2f;
            canvas.drawRoundRect(rect, r, r, bgPaint);
            canvas.drawRoundRect(rect, r, r, borderPaint);
            canvas.drawText(text, w / 2f, h / 2f + AndroidUtilities.dp(5f), txtPaint);

            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!isEnabled()) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    animatePress(0.92f);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    animatePress(1.0f);
                    break;
            }
            return super.onTouchEvent(event);
        }

        private void animatePress(float to) {
            if (pressAnim != null) pressAnim.cancel();
            pressAnim = ValueAnimator.ofFloat(pressScale, to);
            pressAnim.setDuration(100);
            pressAnim.addUpdateListener(a -> {
                pressScale = (float) a.getAnimatedValue();
                invalidate();
            });
            pressAnim.start();
        }
    }

    // Custom Canvas Confetti views (Ambient and Explosion particles)
    private static class ConfettiView extends View {
        private final ArrayList<Particle> particles = new ArrayList<>();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random = new Random();
        private final int maxAmbientParticles = 60;

        public ConfettiView(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        public void triggerBurst(float cx, float cy, int count) {
            for (int i = 0; i < count; i++) {
                particles.add(new Particle(cx, cy, true));
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0) return;

            // Maintain ambient particles
            int ambientCount = 0;
            for (Particle p : particles) {
                if (!p.isBurst) ambientCount++;
            }
            if (ambientCount < maxAmbientParticles && random.nextFloat() < 0.25f) {
                particles.add(new Particle(random.nextFloat() * w, -50, false));
            }

            // Update & draw particles
            for (int i = particles.size() - 1; i >= 0; i--) {
                Particle p = particles.get(i);
                p.update();
                if (p.y > h + 100 || p.alpha <= 0 || p.x < -100 || p.x > w + 100) {
                    particles.remove(i);
                } else {
                    p.draw(canvas, paint);
                }
            }

            invalidate();
        }
    }

    private static class Particle {
        float x, y;
        float vx, vy;
        float rotation;
        float rotationSpeed;
        float width, height;
        int color;
        int shape; // 0 = rect, 1 = circle, 2 = oval
        float alpha = 255;
        boolean isBurst;

        Particle(float x, float y, boolean isBurst) {
            this.x = x;
            this.y = y;
            this.isBurst = isBurst;
            Random r = new Random();

            int[] colors = {
                    0xFFFF2E93, // Neon Pink
                    0xFFFF8A00, // Neon Orange
                    0xFFFFE600, // Neon Yellow
                    0xFF00E5FF, // Neon Cyan
                    0xFFBD00FF, // Neon Purple
                    0xFF00FF75  // Neon Green
            };
            this.color = colors[r.nextInt(colors.length)];
            this.shape = r.nextInt(3);

            float dpScale = AndroidUtilities.density;
            this.width = (8 + r.nextInt(8)) * dpScale;
            this.height = (6 + r.nextInt(6)) * dpScale;
            this.rotation = r.nextInt(360);
            this.rotationSpeed = (r.nextFloat() * 4 - 2) * 2;

            if (isBurst) {
                double angle = r.nextDouble() * 2 * Math.PI;
                float speed = (2 + r.nextFloat() * 10) * dpScale;
                this.vx = (float) (Math.cos(angle) * speed);
                this.vy = (float) (Math.sin(angle) * speed) - 2 * dpScale;
            } else {
                this.vx = (r.nextFloat() * 2 - 1) * dpScale;
                this.vy = (1.5f + r.nextFloat() * 3.5f) * dpScale;
            }
        }

        void update() {
            float dpScale = AndroidUtilities.density;
            x += vx;
            y += vy;
            rotation += rotationSpeed;

            if (isBurst) {
                vy += 0.18f * dpScale; // Gravity
                vx *= 0.97f; // drag
                vy *= 0.97f;
                alpha -= 2.5f; // fade
            } else {
                vx += (float) Math.sin(y * 0.04f) * 0.04f * dpScale;
            }
        }

        void draw(Canvas canvas, Paint paint) {
            if (alpha <= 0) return;
            paint.setColor(color);
            paint.setAlpha((int) alpha);

            canvas.save();
            canvas.translate(x, y);
            canvas.rotate(rotation);

            if (shape == 0) {
                canvas.drawRect(-width / 2, -height / 2, width / 2, height / 2, paint);
            } else if (shape == 1) {
                canvas.drawCircle(0, 0, width / 2, paint);
            } else {
                canvas.drawOval(new RectF(-width / 2, -height / 2, width / 2, height / 2), paint);
            }

            canvas.restore();
        }
    }
}
