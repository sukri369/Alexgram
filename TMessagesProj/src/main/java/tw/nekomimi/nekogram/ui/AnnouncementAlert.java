package tw.nekomimi.nekogram.ui;

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

        // Header
        headerView = new GradientHeaderView(activity);
        card.addView(headerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 150));

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

                int screenW = AndroidUtilities.displaySize.x - AndroidUtilities.dp(40);
                int displayH = (int) (screenW * ((float) size.h / size.w));
                if (displayH > AndroidUtilities.dp(240)) {
                    displayH = AndroidUtilities.dp(240);
                }
                
                contentLayout.addView(photoView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, displayH, 20, 16, 20, 8));
                photoView.setImage(ImageLocation.getForPhoto(size, photo), "400_300", null, photo);
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

        sv.addView(contentLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        card.addView(sv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Buttons
        LinearLayout buttonsLayout = new LinearLayout(activity);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setGravity(Gravity.RIGHT);

        TextView closeBtn = new TextView(activity);
        closeBtn.setText(LocaleController.getString(R.string.Close));
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        closeBtn.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        closeBtn.setTypeface(AndroidUtilities.bold());
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        closeBtn.setOnClickListener(v -> dismiss());
        buttonsLayout.addView(closeBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 44, 0, 0, 8, 8));

        GradientButton openBtn = new GradientButton(activity, "Read on Channel");
        openBtn.setOnClickListener(v -> {
            dismiss();
            Bundle args = new Bundle();
            args.putLong("chat_id", chat.id);
            args.putInt("message_id", message.id);
            activity.getActionBarLayout().presentFragment(new ChatActivity(args), false, false, true, false);
        });
        buttonsLayout.addView(openBtn, LayoutHelper.createLinear(AndroidUtilities.dp(150), 44, 8, 0, 20, 8));

        card.addView(buttonsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 16));

        startPulseAnimation();
    }

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
        private final Path clipPath = new Path();
        private int lastW, lastH;

        GradientHeaderView(Context c) {
            super(c);
            setWillNotDraw(false);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (w == lastW && h == lastH) return;
            lastW = w;
            lastH = h;
            bgPaint.setShader(new LinearGradient(0, 0, w, h,
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
            canvas.drawRect(0, 0, w, h, bgPaint);
            decorPaint.setColor(0x14FFFFFF);
            canvas.drawCircle(w * 0.86f, h * 0.14f, AndroidUtilities.dp(68), decorPaint);
            decorPaint.setColor(0x0CFFFFFF);
            canvas.drawCircle(w * 0.08f, h * 0.82f, AndroidUtilities.dp(90), decorPaint);
            canvas.restore();

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.WHITE);
            textPaint.setTypeface(AndroidUtilities.bold());
            textPaint.setTextSize(AndroidUtilities.dp(22));
            textPaint.setShadowLayer(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(2), 0x40000000);
            canvas.drawText("Alexgram", w / 2f, h * 0.45f, textPaint);
            textPaint.clearShadowLayer();

            textPaint.setTextSize(AndroidUtilities.dp(13f));
            textPaint.setTypeface(Typeface.DEFAULT);
            textPaint.setColor(0xCCFFFFFF);
            canvas.drawText("Official Announcement", w / 2f, h * 0.65f, textPaint);
        }
    }

    private static class GradientButton extends FrameLayout {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txtPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF btnRect = new RectF();
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
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            canvas.save();
            canvas.scale(pressScale, pressScale, w / 2f, h / 2f);
            canvas.drawRoundRect(btnRect, h / 2f, h / 2f, bgPaint);
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
