package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;

public class ChatAnimeAssistantView extends FrameLayout {

    public interface AssistantRequestCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    public interface AssistantRequestDelegate {
        void onRequest(String prompt, AssistantRequestCallback callback);
    }

    private static final int MAX_BUBBLES = 10;

    private final FrameLayout characterContainer;
    private final AssistantCharacterView characterView;
    private final TextView reactionBubble;
    private final View panelScrim;
    private final FrameLayout panelContainer;
    private final LinearLayout bubblesContainer;
    private final ScrollView bubblesScrollView;
    private final EditText inputField;
    private final ImageView sendButton;

    private final Runnable frameRunnable = new Runnable() {
        @Override
        public void run() {
            final long now = SystemClock.uptimeMillis();
            final float dt = Math.min(33f, now - lastFrameTime) / 1000f;
            lastFrameTime = now;
            if (!paused) {
                characterView.tick(now, dt, typingActive, scrollEnergy);
                scrollEnergy *= 0.84f;
                postOnAnimation(frameRunnable);
            }
        }
    };

    private final List<View> messageViews = new ArrayList<>();

    private AssistantRequestDelegate assistantRequestDelegate;
    private boolean paused;
    private boolean panelOpened;
    private boolean typingActive;
    private float scrollEnergy;
    private float downX;
    private float downY;
    private float startTx;
    private float startTy;
    private boolean dragging;
    private long lastFrameTime;
    private int typingDots;
    private Runnable typingRunnable;

    public ChatAnimeAssistantView(@NonNull Context context, @Nullable SizeNotifierFrameLayout blurParent) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);

        panelScrim = new View(context);
        panelScrim.setBackgroundColor(0x33000000);
        panelScrim.setVisibility(GONE);
        panelScrim.setAlpha(0f);
        panelScrim.setOnClickListener(v -> hidePanel());
        addView(panelScrim, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        characterContainer = new FrameLayout(context);
        characterContainer.setClipChildren(false);
        characterContainer.setClipToPadding(false);
        characterContainer.setClickable(true);
        characterContainer.setFocusable(true);

        characterView = new AssistantCharacterView(context);
        characterContainer.addView(characterView, LayoutHelper.createFrame(86, 108, Gravity.CENTER));

        reactionBubble = new TextView(context);
        reactionBubble.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        reactionBubble.setGravity(Gravity.CENTER);
        reactionBubble.setTextColor(Color.WHITE);
        reactionBubble.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), 0x99111A2B));
        reactionBubble.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        reactionBubble.setVisibility(GONE);
        characterContainer.addView(reactionBubble, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, -8, 0, 0));

        addView(characterContainer, LayoutHelper.createFrame(100, 122, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 12, 92));

        panelContainer = blurParent != null ? new BlurredFrameLayout(context, blurParent) : new FrameLayout(context);
        if (panelContainer instanceof BlurredFrameLayout) {
            ((BlurredFrameLayout) panelContainer).setBackgroundColor(0xAA152235);
            ((BlurredFrameLayout) panelContainer).drawBlur = SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_LOW;
        } else {
            panelContainer.setBackgroundColor(0xEE17263D);
        }
        panelContainer.setVisibility(GONE);
        panelContainer.setAlpha(0f);
        panelContainer.setScaleX(0.92f);
        panelContainer.setScaleY(0.92f);
        panelContainer.setClipToPadding(false);
        panelContainer.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(14), AndroidUtilities.dp(14), AndroidUtilities.dp(12));

        final GradientDrawable panelShape = new GradientDrawable();
        panelShape.setCornerRadius(AndroidUtilities.dp(20));
        panelShape.setColor(0xB8192B43);
        panelShape.setStroke(AndroidUtilities.dp(1), 0x66FFFFFF);
        panelContainer.setBackground(panelShape);

        final LinearLayout panelContent = new LinearLayout(context);
        panelContent.setOrientation(LinearLayout.VERTICAL);
        panelContainer.addView(panelContent, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        final TextView title = new TextView(context);
        title.setText(LocaleController.getString(R.string.AppName) + " Assistant");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        title.setTypeface(AndroidUtilities.bold());
        panelContent.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final TextView subtitle = new TextView(context);
        subtitle.setText("Friendly mode online");
        subtitle.setTextColor(0xBBE0ECFF);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        subtitle.setPadding(0, AndroidUtilities.dp(2), 0, AndroidUtilities.dp(8));
        panelContent.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        bubblesScrollView = new ScrollView(context);
        bubblesScrollView.setVerticalScrollBarEnabled(false);
        bubblesScrollView.setOverScrollMode(OVER_SCROLL_NEVER);
        panelContent.addView(bubblesScrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        bubblesContainer = new LinearLayout(context);
        bubblesContainer.setOrientation(LinearLayout.VERTICAL);
        bubblesContainer.setPadding(0, 0, 0, AndroidUtilities.dp(6));
        bubblesScrollView.addView(bubblesContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final LinearLayout composer = new LinearLayout(context);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        panelContent.addView(composer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        inputField = new EditText(context);
        inputField.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), 0x66FFFFFF));
        inputField.setHint("Ask me anything...");
        inputField.setHintTextColor(0x99FFFFFF);
        inputField.setTextColor(Color.WHITE);
        inputField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        inputField.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        inputField.setMaxLines(3);
        composer.addView(inputField, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

        sendButton = new ImageView(context);
        sendButton.setImageResource(R.drawable.msg_send);
        sendButton.setColorFilter(new android.graphics.PorterDuffColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN));
        sendButton.setBackground(Theme.createSelectorCircleDrawable(AndroidUtilities.dp(20), 0x33FFFFFF, 0x55FFFFFF));
        sendButton.setPadding(AndroidUtilities.dp(9), AndroidUtilities.dp(9), AndroidUtilities.dp(9), AndroidUtilities.dp(9));
        composer.addView(sendButton, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL));

        addView(panelContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 272, Gravity.BOTTOM | Gravity.RIGHT, 44, 0, 14, 86));

        sendButton.setOnClickListener(v -> sendPrompt());
        inputField.setOnEditorActionListener((v, actionId, event) -> {
            sendPrompt();
            return true;
        });
        inputField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                onTypingStateChanged(!TextUtils.isEmpty(s));
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        characterContainer.setOnLongClickListener(v -> {
            characterView.cycleSkin();
            showReactionBubble("🎨");
            return true;
        });

        characterContainer.setOnTouchListener((v, event) -> {
            final int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = false;
                    downX = event.getRawX();
                    downY = event.getRawY();
                    startTx = characterContainer.getTranslationX();
                    startTy = characterContainer.getTranslationY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    if (!dragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                        dragging = true;
                    }
                    if (dragging) {
                        characterContainer.setTranslationX(startTx + dx);
                        characterContainer.setTranslationY(startTy + dy);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!dragging) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        characterView.onTap();
                        showReactionBubble(randomReaction());
                        if (panelOpened) {
                            hidePanel();
                        } else {
                            showPanel();
                        }
                    } else {
                        snapToBounds();
                    }
                    return true;
                default:
                    return false;
            }
        });

        addMessageBubble("Hi, I am your chat companion. Tap me and ask anything.", false, false);
        addMessageBubble("Try long-press to switch my style.", false, false);

        lastFrameTime = SystemClock.uptimeMillis();
        postOnAnimation(frameRunnable);
    }

    public void setAssistantRequestDelegate(@Nullable AssistantRequestDelegate assistantRequestDelegate) {
        this.assistantRequestDelegate = assistantRequestDelegate;
    }

    public void onPause() {
        paused = true;
        removeCallbacks(frameRunnable);
    }

    public void onResume() {
        if (paused) {
            paused = false;
            lastFrameTime = SystemClock.uptimeMillis();
            postOnAnimation(frameRunnable);
        }
    }

    public void onDestroy() {
        removeCallbacks(frameRunnable);
        if (typingRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(typingRunnable);
            typingRunnable = null;
        }
    }

    public void onTypingStateChanged(boolean active) {
        typingActive = active;
        if (active) {
            characterView.onTypingPulse();
            showReactionBubble("✍️");
        }
    }

    public void onChatScrolled(int dy) {
        if (Math.abs(dy) < 2) {
            return;
        }
        scrollEnergy = Math.min(1f, scrollEnergy + Math.abs(dy) / 140f);
        characterView.onScrollImpulse(dy);
        if (Math.abs(dy) > AndroidUtilities.dp(4)) {
            showReactionBubble(dy > 0 ? "👀" : "✨");
        }
    }

    private void snapToBounds() {
        final float maxX = Math.max(0, getWidth() - characterContainer.getWidth() - AndroidUtilities.dp(4));
        final float maxY = Math.max(0, getHeight() - characterContainer.getHeight() - AndroidUtilities.dp(84));
        final float targetX = characterContainer.getX() + characterContainer.getTranslationX() > getWidth() * 0.5f ? maxX - characterContainer.getLeft() : -characterContainer.getLeft();
        final float clampedY = Math.max(-characterContainer.getTop(), Math.min(maxY - characterContainer.getTop(), characterContainer.getTranslationY()));
        characterContainer.animate()
                .translationX(targetX)
                .translationY(clampedY)
                .setDuration(280)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .start();
    }

    private void showPanel() {
        if (panelOpened) {
            return;
        }
        panelOpened = true;
        panelScrim.setVisibility(VISIBLE);
        panelContainer.setVisibility(VISIBLE);
        panelScrim.animate().alpha(1f).setDuration(220).start();
        panelContainer.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        characterView.onOpenPanel();
        showReactionBubble("💬");
    }

    private void hidePanel() {
        if (!panelOpened) {
            return;
        }
        panelOpened = false;
        panelScrim.animate().alpha(0f).setDuration(180).withEndAction(() -> panelScrim.setVisibility(GONE)).start();
        panelContainer.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(180).setInterpolator(CubicBezierInterpolator.DEFAULT).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                panelContainer.setVisibility(GONE);
                panelContainer.animate().setListener(null);
            }
        }).start();
    }

    private void sendPrompt() {
        final String prompt = inputField.getText() == null ? "" : inputField.getText().toString().trim();
        if (TextUtils.isEmpty(prompt)) {
            return;
        }
        inputField.setText("");
        addMessageBubble(prompt, true, false);
        showTypingBubble();
        characterView.onSendPrompt();

        if (assistantRequestDelegate == null) {
            hideTypingBubble("Assistant is not connected yet.");
            return;
        }

        assistantRequestDelegate.onRequest(prompt, new AssistantRequestCallback() {
            @Override
            public void onSuccess(String response) {
                AndroidUtilities.runOnUIThread(() -> hideTypingBubble(TextUtils.isEmpty(response) ? "I could not generate a response right now." : response));
            }

            @Override
            public void onError(String error) {
                AndroidUtilities.runOnUIThread(() -> hideTypingBubble("Oops, network mood swing. " + (TextUtils.isEmpty(error) ? "Please try again." : error)));
            }
        });
    }

    private void showTypingBubble() {
        final TextView typingView = addMessageBubble("Thinking.", false, false);
        typingRunnable = new Runnable() {
            @Override
            public void run() {
                if (typingView.getParent() == null) {
                    return;
                }
                typingDots = (typingDots + 1) % 4;
                typingView.setText("Thinking" + (typingDots == 0 ? "." : new String(new char[typingDots]).replace('\0', '.')));
                AndroidUtilities.runOnUIThread(this, 280);
            }
        };
        AndroidUtilities.runOnUIThread(typingRunnable, 280);
    }

    private void hideTypingBubble(String text) {
        if (typingRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(typingRunnable);
            typingRunnable = null;
        }
        if (!messageViews.isEmpty()) {
            View last = messageViews.get(messageViews.size() - 1);
            if (last instanceof TextView) {
                CharSequence current = ((TextView) last).getText();
                if (current != null && current.toString().startsWith("Thinking")) {
                    bubblesContainer.removeView(last);
                    messageViews.remove(last);
                }
            }
        }
        addMessageBubble(text, false, true);
        showReactionBubble("✨");
    }

    private TextView addMessageBubble(String text, boolean isUser, boolean typewriter) {
        final TextView tv = new TextView(getContext());
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        tv.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8), AndroidUtilities.dp(10), AndroidUtilities.dp(8));
        tv.setMaxWidth(AndroidUtilities.dp(260));
        tv.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), isUser ? 0xD9368EFF : 0x99495D7A));

        LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        lp.gravity = isUser ? Gravity.RIGHT : Gravity.LEFT;
        lp.topMargin = AndroidUtilities.dp(5);
        bubblesContainer.addView(tv, lp);
        messageViews.add(tv);
        while (messageViews.size() > MAX_BUBBLES) {
            View remove = messageViews.remove(0);
            bubblesContainer.removeView(remove);
        }

        if (typewriter && !TextUtils.isEmpty(text)) {
            final char[] chars = text.toCharArray();
            final int[] index = {0};
            Runnable typer = new Runnable() {
                @Override
                public void run() {
                    if (index[0] <= chars.length) {
                        tv.setText(new String(chars, 0, index[0]));
                        index[0] += Math.max(1, chars.length / 40);
                        AndroidUtilities.runOnUIThread(this, 18);
                    }
                    bubblesScrollView.post(() -> bubblesScrollView.fullScroll(View.FOCUS_DOWN));
                }
            };
            AndroidUtilities.runOnUIThread(typer);
        } else {
            tv.setText(text);
            bubblesScrollView.post(() -> bubblesScrollView.fullScroll(View.FOCUS_DOWN));
        }
        return tv;
    }

    private void showReactionBubble(String text) {
        reactionBubble.setText(text);
        reactionBubble.setVisibility(VISIBLE);
        reactionBubble.setAlpha(0f);
        reactionBubble.setTranslationY(AndroidUtilities.dp(6));
        reactionBubble.animate().alpha(1f).translationY(0).setDuration(130).withEndAction(() -> reactionBubble.animate().alpha(0f).translationY(-AndroidUtilities.dp(8)).setStartDelay(900).setDuration(260).withEndAction(() -> {
            reactionBubble.setVisibility(GONE);
            reactionBubble.setAlpha(1f);
            reactionBubble.setTranslationY(0);
        }).start()).start();
    }

    private String randomReaction() {
        String[] reactions = new String[]{"😊", "✨", "🌸", "💫", "🫶", "😺"};
        int index = (int) (SystemClock.uptimeMillis() % reactions.length);
        return reactions[index];
    }

    private static final class AssistantCharacterView extends View {

        private final Paint skinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint eyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF tempRect = new RectF();
        private final Path hairPath = new Path();
        private final List<Particle> particles = new ArrayList<>();

        private float tapKick;
        private float typingPulse;
        private float scrollTilt;
        private float panelFocus;
        private int skinIndex;

        private static final int[] SKIN_SKY = {0xFFF9D7CC, 0xFF2A2A48, 0xFF2A3450, 0xFF71D4FF};
        private static final int[] SKIN_MINT = {0xFFFDE2D0, 0xFF1F3644, 0xFF2D4F5D, 0xFF73F5CF};
        private static final int[] SKIN_SUNSET = {0xFFFFD4C3, 0xFF43315A, 0xFF5D3D79, 0xFFFF9F77};

        public AssistantCharacterView(Context context) {
            super(context);
            outlinePaint.setStyle(Paint.Style.STROKE);
            outlinePaint.setStrokeWidth(AndroidUtilities.dp(1.3f));
            outlinePaint.setColor(0x66FFFFFF);
            setSkinColors();
        }

        void cycleSkin() {
            skinIndex = (skinIndex + 1) % 3;
            setSkinColors();
            spawnParticles(6);
            invalidate();
        }

        void onTap() {
            tapKick = 1f;
            spawnParticles(7);
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }

        void onTypingPulse() {
            typingPulse = 1f;
            spawnParticles(3);
        }

        void onSendPrompt() {
            tapKick = 0.9f;
            typingPulse = 0.8f;
            spawnParticles(8);
        }

        void onOpenPanel() {
            panelFocus = 1f;
        }

        void onScrollImpulse(int dy) {
            scrollTilt = Math.max(-1f, Math.min(1f, scrollTilt + (dy > 0 ? 0.18f : -0.18f)));
        }

        void tick(long now, float dt, boolean typingActive, float scrollEnergy) {
            tapKick = Math.max(0f, tapKick - dt * 2.8f);
            typingPulse = Math.max(0f, typingPulse - dt * 1.6f + (typingActive ? dt * 0.8f : 0f));
            scrollTilt *= 0.88f;
            panelFocus = Math.max(0f, panelFocus - dt * 0.9f);

            if (particles.size() < (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW ? 6 : 14) && (typingActive || scrollEnergy > 0.3f)) {
                spawnParticles(1);
            }

            for (int i = particles.size() - 1; i >= 0; i--) {
                Particle p = particles.get(i);
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                p.life -= dt;
                p.vy -= dt * 6f;
                if (p.life <= 0f) {
                    particles.remove(i);
                }
            }
            invalidate();
        }

        private void setSkinColors() {
            int[] skin;
            if (skinIndex == 1) {
                skin = SKIN_MINT;
            } else if (skinIndex == 2) {
                skin = SKIN_SUNSET;
            } else {
                skin = SKIN_SKY;
            }
            skinPaint.setColor(skin[0]);
            hairPaint.setColor(skin[1]);
            dressPaint.setColor(skin[2]);
            accentPaint.setColor(skin[3]);
            eyePaint.setColor(0xFF132035);
            glowPaint.setColor(skin[3]);
        }

        private void spawnParticles(int count) {
            for (int i = 0; i < count; i++) {
                Particle p = new Particle();
                p.x = 0f;
                p.y = -AndroidUtilities.dp(22);
                p.vx = (float) ((Math.random() - 0.5f) * 30f);
                p.vy = (float) (Math.random() * 30f + 20f);
                p.life = 0.8f + (float) Math.random() * 0.5f;
                particles.add(p);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final float w = getWidth();
            final float h = getHeight();
            final float cx = w / 2f;
            final long now = SystemClock.uptimeMillis();
            final float t = now / 900f;
            final float breath = (float) Math.sin(t) * AndroidUtilities.dp(1.6f);
            final float blink = ((now / 2500) % 9 == 0) ? 0.2f : 1f;
            final float bounce = tapKick * AndroidUtilities.dp(5);
            final float tilt = scrollTilt * 8f;

            canvas.save();
            canvas.translate(cx, h * 0.58f + breath - bounce);
            canvas.rotate(tilt);

            glowPaint.setAlpha((int) (50 + 60 * (typingPulse + panelFocus)));
            canvas.drawCircle(0, AndroidUtilities.dp(6), AndroidUtilities.dp(34 + 4 * typingPulse), glowPaint);

            tempRect.set(-AndroidUtilities.dp(16), AndroidUtilities.dp(2), AndroidUtilities.dp(16), AndroidUtilities.dp(34));
            canvas.drawRoundRect(tempRect, AndroidUtilities.dp(14), AndroidUtilities.dp(14), dressPaint);

            tempRect.set(-AndroidUtilities.dp(19), -AndroidUtilities.dp(30), AndroidUtilities.dp(19), AndroidUtilities.dp(8));
            canvas.drawOval(tempRect, skinPaint);

            hairPath.reset();
            hairPath.moveTo(-AndroidUtilities.dp(17), -AndroidUtilities.dp(26));
            hairPath.quadTo(0, -AndroidUtilities.dp(42), AndroidUtilities.dp(17), -AndroidUtilities.dp(26));
            hairPath.lineTo(AndroidUtilities.dp(15), -AndroidUtilities.dp(2));
            hairPath.quadTo(0, -AndroidUtilities.dp(12), -AndroidUtilities.dp(15), -AndroidUtilities.dp(2));
            hairPath.close();
            canvas.drawPath(hairPath, hairPaint);

            final float eyeHalf = AndroidUtilities.dp(3.6f);
            final float eyeHeight = AndroidUtilities.dp(2.2f) * blink;
            tempRect.set(-AndroidUtilities.dp(9), -AndroidUtilities.dp(16), -AndroidUtilities.dp(2), -AndroidUtilities.dp(16) + eyeHeight * 2);
            canvas.drawRoundRect(tempRect, eyeHalf, eyeHalf, eyePaint);
            tempRect.set(AndroidUtilities.dp(2), -AndroidUtilities.dp(16), AndroidUtilities.dp(9), -AndroidUtilities.dp(16) + eyeHeight * 2);
            canvas.drawRoundRect(tempRect, eyeHalf, eyeHalf, eyePaint);

            tempRect.set(-AndroidUtilities.dp(4), -AndroidUtilities.dp(6), AndroidUtilities.dp(4), -AndroidUtilities.dp(2));
            canvas.drawArc(tempRect, 8 + typingPulse * 24, 164 - typingPulse * 48, false, eyePaint);

            accentPaint.setAlpha((int) (180 + typingPulse * 60));
            tempRect.set(-AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(24));
            canvas.drawRoundRect(tempRect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), accentPaint);

            canvas.drawCircle(-AndroidUtilities.dp(17), -AndroidUtilities.dp(18), AndroidUtilities.dp(2), accentPaint);
            canvas.drawCircle(AndroidUtilities.dp(17), -AndroidUtilities.dp(18), AndroidUtilities.dp(2), accentPaint);

            canvas.drawRoundRect(-AndroidUtilities.dp(19), -AndroidUtilities.dp(30), AndroidUtilities.dp(19), AndroidUtilities.dp(34), AndroidUtilities.dp(16), AndroidUtilities.dp(16), outlinePaint);

            for (int i = 0; i < particles.size(); i++) {
                Particle p = particles.get(i);
                glowPaint.setAlpha((int) (255 * Math.max(0f, p.life)));
                canvas.drawCircle(p.x, p.y, AndroidUtilities.dp(1.6f), glowPaint);
            }

            canvas.restore();
        }

        private static final class Particle {
            float x;
            float y;
            float vx;
            float vy;
            float life;
        }
    }
}