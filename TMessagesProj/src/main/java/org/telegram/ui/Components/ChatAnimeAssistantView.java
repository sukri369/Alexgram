package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.Menu;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.chat.MiniChatAssistantView;
import org.telegram.ui.Helpers.AIAssistanceHelper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import tw.nekomimi.nekogram.config.ConfigItem;
import xyz.nextalone.nagram.NaConfig;

public class ChatAnimeAssistantView extends FrameLayout {

    public interface AssistantRequestCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    public interface AssistantRequestDelegate {
        void onRequest(String prompt, List<AIAssistanceHelper.HistoryItem> history, AssistantRequestCallback callback);

        default void onAutoReplyToggleChanged(long dialogId, boolean enabled) {
        }

        default void onAction(String action, String argument, AssistantRequestCallback callback) {
            if (callback != null) {
                callback.onError("This action is not available in this chat yet.");
            }
        }
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
    private final SharedPreferences preferences;
    private final long assistantDialogId;
    private final boolean showAutoReplyOption;
    private final boolean autoReplySupported;
    private final String autoReplyUnsupportedMessage;
    private final TextView title;
    private final TextView subtitle;
    private String activeTopicContext;
    private final List<AIAssistanceHelper.HistoryItem> conversationHistory = new ArrayList<>();

    private final Runnable frameRunnable = new Runnable() {
        @Override
        public void run() {
            final long now = SystemClock.uptimeMillis();
            final float dt = Math.min(33f, now - lastFrameTime) / 1000f;
            lastFrameTime = now;
            if (!paused) {
                boolean backgroundAnimationEnabled = preferences.getBoolean("background_animation", true);
                float intensity = backgroundAnimationEnabled ? preferences.getInt("animation_intensity", 70) / 100f : 0f;
                float runtimeScrollEnergy = backgroundAnimationEnabled ? scrollEnergy : 0f;
                characterView.tick(now, dt, typingActive, runtimeScrollEnergy, intensity);
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
    private float startPanelTx;
    private float startPanelTy;
    private float panelBaseTx;
    private float panelBaseTy;
    private float keyboardShiftY;
    private boolean dragging;
    private boolean panelDragging;
    private boolean panelDragAllowed;
    private boolean autoReplyEnabled;
    long lastFrameTime;
    private int typingDots;
    private Runnable typingRunnable;
    private final Rect visibleFrame = new Rect();
    private GestureDetector gestureDetector;
    private MiniChatAssistantView miniView;
    private SizeNotifierFrameLayout blurParent;

    public ChatAnimeAssistantView(@NonNull Context context, @Nullable SizeNotifierFrameLayout blurParent, long dialogId) {
        this(context, blurParent, dialogId, true, true, "This feature only works in chats.", 92, 86);
    }

    public ChatAnimeAssistantView(@NonNull Context context,
                                  @Nullable SizeNotifierFrameLayout blurParent,
                                  long dialogId,
                                  boolean showAutoReplyOption,
                                  boolean autoReplySupported,
                                  @Nullable String autoReplyUnsupportedMessage,
                                  int characterBottomMarginDp,
                                  int panelBottomMarginDp) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);

        preferences = context.getSharedPreferences("ai_assistant_prefs", Context.MODE_PRIVATE);
        assistantDialogId = dialogId;
        this.showAutoReplyOption = showAutoReplyOption;
        this.autoReplySupported = autoReplySupported;
        this.autoReplyUnsupportedMessage = TextUtils.isEmpty(autoReplyUnsupportedMessage) ? "This feature only works in chats." : autoReplyUnsupportedMessage;
        autoReplyEnabled = autoReplySupported && preferences.getBoolean(getAutoReplyPreferenceKey(), false);
        this.blurParent = blurParent;

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

        characterView = new AssistantCharacterView(context, preferences);
        characterContainer.addView(characterView, LayoutHelper.createFrame(86, 108, Gravity.CENTER));

        reactionBubble = new TextView(context);
        reactionBubble.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        reactionBubble.setGravity(Gravity.CENTER);
        reactionBubble.setTextColor(Color.WHITE);
        reactionBubble.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), 0x99111A2B));
        reactionBubble.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        reactionBubble.setVisibility(GONE);
        characterContainer.addView(reactionBubble, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, -8, 0, 0));

        addView(characterContainer, LayoutHelper.createFrame(100, 122, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 12, 0));

        panelContainer = blurParent != null ? new BlurredFrameLayout(context, blurParent) : new FrameLayout(context);
        if (panelContainer instanceof BlurredFrameLayout) {
            ((BlurredFrameLayout) panelContainer).backgroundColor = 0xAA152235;
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
        panelShape.setCornerRadius(AndroidUtilities.dp(40));
        panelShape.setColor(0xB8192B43);
        panelShape.setStroke(AndroidUtilities.dp(1), 0x66FFFFFF);
        panelContainer.setBackground(panelShape);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            panelContainer.setClipToOutline(true);
        }

        final LinearLayout panelContent = new LinearLayout(context);
        panelContent.setOrientation(LinearLayout.VERTICAL);
        panelContainer.addView(panelContent, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        title = new TextView(context);
        title.setText("Alexgram Assistance");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        title.setTypeface(AndroidUtilities.bold());
        panelContent.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        subtitle = new TextView(context);
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
        inputField.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), 0x66FFFFFF));
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
        sendButton.setBackground(Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(20), 0x33FFFFFF, 0x55FFFFFF));
        sendButton.setPadding(AndroidUtilities.dp(9), AndroidUtilities.dp(9), AndroidUtilities.dp(9), AndroidUtilities.dp(9));
        composer.addView(sendButton, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL));

        addView(panelContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 272, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 44, 0, 14, 0));

        setupPanelDragging();
        setupKeyboardListener();

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

        // Setup gesture detector for proper single tap vs long press detection
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (!dragging) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                    characterView.onTap();
                    showReactionBubble(randomReaction());
                    showPanel();
                }
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (!dragging) {
                    showLongPressMenu(characterContainer);
                }
            }
        });
        gestureDetector.setIsLongpressEnabled(true);

        characterContainer.setOnTouchListener((v, event) -> {
            final int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = false;
                    downX = event.getRawX();
                    downY = event.getRawY();
                    startTx = characterContainer.getTranslationX();
                    startTy = characterContainer.getTranslationY();
                    startPanelTx = panelContainer.getTranslationX();
                    startPanelTy = panelContainer.getTranslationY();
                    gestureDetector.onTouchEvent(event);
                    return false;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    if (!dragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                        dragging = true;
                    }
                    if (dragging) {
                        if (panelOpened && preferences.getBoolean("auto_follow", true)) {
                            panelBaseTx = startPanelTx + dx;
                            panelBaseTy = startPanelTy + dy - keyboardShiftY;
                            applyLinkedPositions(false);
                        } else {
                            characterContainer.setTranslationX(startTx + dx);
                            characterContainer.setTranslationY(startTy + dy);
                        }
                        return true;
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) {
                        if (panelOpened && preferences.getBoolean("auto_follow", true)) {
                            snapPanelToBounds();
                        } else {
                            snapToBounds();
                        }
                        return true;
                    }
                    // Pass to gesture detector for tap/long-press handling
                    gestureDetector.onTouchEvent(event);
                    return false;
                default:
                    return false;
            }
        });

        loadHistory();

        lastFrameTime = SystemClock.uptimeMillis();
        if (preferences.getBoolean("assistant_enabled", false)) {
            postOnAnimation(frameRunnable);
        } else {
            setVisibility(GONE);
        }
    }

    private void saveHistory() {
        try {
            JSONArray array = new JSONArray();
            for (AIAssistanceHelper.HistoryItem item : conversationHistory) {
                JSONObject obj = new JSONObject();
                obj.put("t", item.text);
                obj.put("u", item.isUser);
                array.put(obj);
            }
            preferences.edit().putString("history_" + assistantDialogId, array.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadHistory() {
        String saved = preferences.getString("history_" + assistantDialogId, null);
        if (saved != null) {
            try {
                JSONArray array = new JSONArray(saved);
                conversationHistory.clear();
                bubblesContainer.removeAllViews();
                messageViews.clear();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    String text = obj.getString("t");
                    boolean isUser = obj.getBoolean("u");
                    conversationHistory.add(new AIAssistanceHelper.HistoryItem(text, isUser));
                    addMessageBubble(text, isUser, false);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            addMessageBubble("Hi, I am Alexgram Assistant. Tap me and ask anything.", false, false);
            addMessageBubble("Try long-press to switch my style.", false, false);
        }
    }

    private void setupKeyboardListener() {
        // Now handled by Activity-level translation in ChatActivity/DialogsActivity
    }

    private void setupPanelDragging() {
        panelContainer.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    panelDragAllowed = event.getY() <= AndroidUtilities.dp(56);
                    panelDragging = false;
                    if (!panelDragAllowed) {
                        return false;
                    }
                    downX = event.getRawX();
                    downY = event.getRawY();
                    startTx = panelContainer.getTranslationX();
                    startTy = panelContainer.getTranslationY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!panelDragAllowed) {
                        return false;
                    }
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                    if (!panelDragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                        panelDragging = true;
                    }
                    if (!panelDragging) {
                        return true;
                    }
                    panelBaseTx = startTx + dx;
                    panelBaseTy = startTy + dy - keyboardShiftY;
                    applyLinkedPositions(false);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!panelDragAllowed) {
                        return false;
                    }
                    if (panelDragging) {
                        snapPanelToBounds();
                    }
                    panelDragging = false;
                    panelDragAllowed = false;
                    return true;
                default:
                    return false;
            }
        });
    }

    private float getCharacterLinkedOffsetX() {
        return -AndroidUtilities.dp(52);
    }

    private float getCharacterLinkedOffsetY() {
        return -AndroidUtilities.dp(72);
    }

    private void applyLinkedPositions(boolean animated) {
        float panelTx = panelBaseTx;
        float panelTy = panelBaseTy;
        float panelLeft = panelContainer.getLeft() + panelTx;
        float panelTop = panelContainer.getTop() + panelTy;
        float characterTx = panelLeft + getCharacterLinkedOffsetX() - characterContainer.getLeft();
        float characterTy = panelTop + getCharacterLinkedOffsetY() - characterContainer.getTop();

        if (animated) {
            panelContainer.animate().translationX(panelTx).translationY(panelTy).setDuration(260).setInterpolator(org.telegram.ui.Components.CubicBezierInterpolator.EASE_OUT_QUINT).start();
            characterContainer.animate().translationX(characterTx).translationY(characterTy).setDuration(260).setInterpolator(org.telegram.ui.Components.CubicBezierInterpolator.EASE_OUT_QUINT).start();
        } else {
            panelContainer.setTranslationX(panelTx);
            panelContainer.setTranslationY(panelTy);
            characterContainer.setTranslationX(characterTx);
            characterContainer.setTranslationY(characterTy);
        }
        if (miniView != null) {
            miniView.setTranslationX(characterContainer.getTranslationX());
            miniView.setTranslationY(characterContainer.getTranslationY());
        }
    }

    private void positionPanelFromCharacter(boolean animated) {
        float characterLeft = characterContainer.getLeft() + characterContainer.getTranslationX();
        float characterTop = characterContainer.getTop() + characterContainer.getTranslationY();
        panelBaseTx = (characterLeft - getCharacterLinkedOffsetX()) - panelContainer.getLeft();
        panelBaseTy = (characterTop - getCharacterLinkedOffsetY()) - panelContainer.getTop();
        clampPanelBaseToBounds();
        applyLinkedPositions(animated);
    }

    private void positionPanelFromCharacterAfterLayout(boolean animated) {
        if (panelContainer.getWidth() > 0 && panelContainer.getHeight() > 0) {
            positionPanelFromCharacter(animated);
            return;
        }
        panelContainer.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (panelContainer.getViewTreeObserver().isAlive()) {
                    panelContainer.getViewTreeObserver().removeOnPreDrawListener(this);
                }
                positionPanelFromCharacter(animated);
                return true;
            }
        });
    }

    private void clampPanelBaseToBounds() {
        float minX = -panelContainer.getLeft() + AndroidUtilities.dp(8);
        float maxX = getWidth() - panelContainer.getLeft() - panelContainer.getWidth() - AndroidUtilities.dp(8);
        float minY = -panelContainer.getTop() + AndroidUtilities.statusBarHeight + AndroidUtilities.dp(8);
        float maxY = getHeight() - panelContainer.getTop() - panelContainer.getHeight() - AndroidUtilities.dp(96);
        panelBaseTx = Math.max(minX, Math.min(maxX, panelBaseTx));
        panelBaseTy = Math.max(minY, Math.min(maxY, panelBaseTy));
    }

    private void animateLinkedToCurrent() {
        if (!panelOpened) {
            return;
        }
        applyLinkedPositions(true);
    }

    private void snapPanelToBounds() {
        clampPanelBaseToBounds();
        applyLinkedPositions(true);
    }

    private void focusInputAndShowKeyboard() {
        inputField.requestFocus();
        AndroidUtilities.showKeyboard(inputField);
    }

    private String getAutoReplyPreferenceKey() {
        return "assistant_auto_reply_" + assistantDialogId;
    }

    private boolean isQuotaOrRateLimitError(String error) {
        if (TextUtils.isEmpty(error)) {
            return false;
        }
        String lower = error.toLowerCase();
        return lower.contains("quota")
                || lower.contains("resource_exhausted")
                || lower.contains("rate limit")
                || lower.contains("too many requests")
                || lower.contains("insufficient_quota")
                || lower.contains("retrydelay")
                || lower.contains("http 429");
    }

    private void showLongPressMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(getContext(), anchor);
        int order = 0;
        if (showAutoReplyOption && assistantDialogId != 0) {
            popupMenu.getMenu().add(Menu.NONE, 1, order++, autoReplyEnabled ? "Auto Reply Mode: ON" : "Auto Reply Mode: OFF");
        }
        popupMenu.getMenu().add(Menu.NONE, 2, order++, "Switch Style");
        if (!TextUtils.isEmpty(activeTopicContext)) {
            popupMenu.getMenu().add(Menu.NONE, 4, order++, "Exit Discussion");
        }
        popupMenu.getMenu().add(Menu.NONE, 5, order++, "Clear Chat Memory");
        popupMenu.getMenu().add(Menu.NONE, 3, order, "Focus Chat Panel");
        popupMenu.setOnMenuItemClickListener(item -> {
            final int itemId = item.getItemId();
            if (itemId == 1) {
                if (!autoReplySupported) {
                    showReactionBubble("INFO");
                    addMessageBubble(autoReplyUnsupportedMessage, false, true);
                    return true;
                }
                autoReplyEnabled = !autoReplyEnabled;
                preferences.edit().putBoolean(getAutoReplyPreferenceKey(), autoReplyEnabled).apply();
                if (assistantRequestDelegate != null) {
                    assistantRequestDelegate.onAutoReplyToggleChanged(assistantDialogId, autoReplyEnabled);
                }
                showReactionBubble(autoReplyEnabled ? "ON" : "OFF");
                characterView.onTap();
                return true;
            } else if (itemId == 2) {
                characterView.cycleSkin();
                showReactionBubble("STYLE");
                return true;
            } else if (itemId == 3) {
                showPanel();
                focusInputAndShowKeyboard();
                return true;
            } else if (itemId == 4) {
                clearActiveTopic();
                showReactionBubble("RESET");
                addMessageBubble("Discussion cleared. I'm back to regular mode.", false, true);
                return true;
            } else if (itemId == 5) {
                clearHistory();
                showReactionBubble("🗑️");
                addMessageBubble("Chat history cleared.", false, true);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    public boolean isAutoReplyEnabled() {
        return autoReplyEnabled;
    }

    public void setAutoReplyEnabledState(boolean enabled) {
        if (!autoReplySupported) {
            autoReplyEnabled = false;
            return;
        }
        autoReplyEnabled = enabled;
        preferences.edit().putBoolean(getAutoReplyPreferenceKey(), enabled).apply();
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

    public void clearHistory() {
        conversationHistory.clear();
        messageViews.clear();
        bubblesContainer.removeAllViews();
        preferences.edit().remove("history_" + assistantDialogId).apply();
        addMessageBubble("Hi, I am Alexgram Assistant. Tap me and ask anything.", false, false);
        addMessageBubble("Try long-press to switch my style.", false, false);
        clearActiveTopic();
    }

    public void onDestroy() {
        paused = true;
        removeCallbacks(frameRunnable);
        if (typingRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(typingRunnable);
            typingRunnable = null;
        }
        clearHistory();
    }

    public void onTypingStateChanged(boolean active) {
        typingActive = active;
        if (active && preferences.getBoolean("reaction_bubbles", true)) {
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
        if (Math.abs(dy) > AndroidUtilities.dp(4) && preferences.getBoolean("reaction_bubbles", true)) {
            showReactionBubble(dy > 0 ? "👀" : "✨");
        }
    }

    private void snapToBounds() {
        final float maxX = Math.max(0, getWidth() - characterContainer.getWidth() - AndroidUtilities.dp(4));
        final float maxY = Math.max(0, getHeight() - characterContainer.getHeight() - AndroidUtilities.dp(16));
        final float targetX = characterContainer.getX() + characterContainer.getTranslationX() > getWidth() * 0.5f ? maxX - characterContainer.getLeft() : -characterContainer.getLeft();
        final float clampedY = Math.max(-characterContainer.getTop(), Math.min(maxY - characterContainer.getTop(), characterContainer.getTranslationY()));
        characterContainer.animate()
                .translationX(targetX)
                .translationY(clampedY)
                .setUpdateListener(animation -> {
                    if (miniView != null) {
                        miniView.setTranslationX(characterContainer.getTranslationX());
                        miniView.setTranslationY(characterContainer.getTranslationY());
                    }
                })
                .setDuration(280)
                .setInterpolator(org.telegram.ui.Components.CubicBezierInterpolator.EASE_OUT_QUINT)
                .start();
    }

    public void showPanel() {
        if (panelOpened) {
            clampPanelBaseToBounds();
            animateLinkedToCurrent();
            focusInputAndShowKeyboard();
            return;
        }
        panelOpened = true;
        panelBaseTx = panelContainer.getTranslationX();
        panelBaseTy = panelContainer.getTranslationY();
        panelScrim.setVisibility(VISIBLE);
        panelContainer.setVisibility(VISIBLE);
        positionPanelFromCharacterAfterLayout(false);
        panelContainer.bringToFront();
        characterContainer.bringToFront();
        panelScrim.animate().alpha(1f).setDuration(220).start();
        panelContainer.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(org.telegram.ui.Components.CubicBezierInterpolator.EASE_OUT_QUINT).start();
        characterView.onOpenPanel();
        showReactionBubble("💬");
    }

    public void setMiniView(MiniChatAssistantView miniView) {
        this.miniView = miniView;
    }

    private void hidePanel() {
        if (!panelOpened) {
            return;
        }
        panelOpened = false;
        panelScrim.animate().alpha(0f).setDuration(180).withEndAction(() -> panelScrim.setVisibility(GONE)).start();
        panelContainer.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(180).setInterpolator(org.telegram.ui.Components.CubicBezierInterpolator.DEFAULT).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                panelContainer.setVisibility(GONE);
                panelContainer.animate().setListener(null);
            }
        }).start();
    }

    private int customBottomOffset = 0;

    public void setAssistantBottomOffset(int offsetPx) {
        if (customBottomOffset != offsetPx) {
            customBottomOffset = offsetPx;
            requestLayout();
        }
    }

    public void startTopicDiscussion(String topicSummary) {
        this.activeTopicContext = topicSummary;
        showPanel();
        title.setText("Deep Discussion");
        subtitle.setText("Exploring the summary context");
        addMessageBubble("I've loaded the summary context. What would you like to know more about?", false, true);
        focusInputAndShowKeyboard();
    }

    public String getActiveTopicContext() {
        return activeTopicContext;
    }

    public void clearActiveTopic() {
        this.activeTopicContext = null;
        title.setText("Alexgram Assistance");
        subtitle.setText("Friendly mode online");
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        
        if (customBottomOffset > 0 && !panelOpened) {
            int parentHeight = bottom - top;
            int cHeight = characterContainer.getMeasuredHeight();
            int currentTop = characterContainer.getTop();
            int targetBottom = parentHeight - (customBottomOffset + AndroidUtilities.dp(16));
            if (currentTop + cHeight > targetBottom) {
                int cWidth = characterContainer.getMeasuredWidth();
                int cLeft = characterContainer.getLeft();
                int cTop = targetBottom - cHeight;
                characterContainer.layout(cLeft, cTop, cLeft + cWidth, targetBottom);
            }
        }
    }

    private void sendPrompt() {
        final String prompt = inputField.getText() == null ? "" : inputField.getText().toString().trim();
        if (TextUtils.isEmpty(prompt)) {
            return;
        }
        inputField.setText("");
        addMessageBubble(prompt, true, false);

        if (handleLocalChatActionCommand(prompt)) {
            return;
        }

        String localSettingsReply = handleLocalSettingsCommand(prompt);
        if (!TextUtils.isEmpty(localSettingsReply)) {
            addMessageBubble(localSettingsReply, false, true);
            showReactionBubble("⚙️");
            return;
        }

        showTypingBubble();
        characterView.onSendPrompt();

        if (assistantRequestDelegate == null) {
            hideTypingBubble("Assistant is not connected yet.");
            return;
        }

        final List<AIAssistanceHelper.HistoryItem> historyCopy = new ArrayList<>(conversationHistory);
        conversationHistory.add(new AIAssistanceHelper.HistoryItem(prompt, true));
        saveHistory();

        assistantRequestDelegate.onRequest(prompt, historyCopy, new AssistantRequestCallback() {
            @Override
            public void onSuccess(String response) {
                AndroidUtilities.runOnUIThread(() -> {
                    hideTypingBubble(TextUtils.isEmpty(response) ? "I could not generate a response right now." : response);
                    if (!TextUtils.isEmpty(response)) {
                        conversationHistory.add(new AIAssistanceHelper.HistoryItem(response, false));
                        saveHistory();
                    }
                });
            }

            @Override
            public void onError(String error) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (isQuotaOrRateLimitError(error)) {
                        hideTypingBubble("API quota reached. Please top up or wait, then try again.");
                    } else {
                        hideTypingBubble("Oops, network mood swing. Please try again.");
                    }
                });
            }
        });
    }

    private String handleLocalSettingsCommand(String prompt) {
        String normalized = normalizeSettingText(prompt);
        if (TextUtils.isEmpty(normalized)) {
            return null;
        }

        boolean asksList = normalized.contains("list") && (normalized.contains("a settings") || normalized.contains("settings"));
        if (asksList) {
            return buildSettingsExamples();
        }

        ParsedSettingsCommand command = parseSettingsCommand(normalized);
        if (command == null) {
            return null;
        }
        if (TextUtils.isEmpty(command.featureQuery)) {
            return "Tell me the setting name too, for example: turn off pill chat title.";
        }

        List<ConfigMatch> matches = findConfigMatches(command.featureQuery);
        if (matches.isEmpty()) {
            return "I could not find that A-Setting. Try the exact name from A-Settings, for example: turn off pill chat title.";
        }
        if (isAmbiguousMatch(matches)) {
            return buildAmbiguousReply(matches);
        }

        ConfigMatch match = matches.get(0);
        return applySettingsCommand(match, command);
    }

    private boolean handleLocalChatActionCommand(String prompt) {
        String normalized = normalizeSettingText(prompt);
        if (TextUtils.isEmpty(normalized)) {
            return false;
        }

        if (normalized.contains("mark all read") || normalized.contains("read all chats") || normalized.contains("clear unread")) {
            executeLocalAction("mark_all_read", "", "Marking chats as read...");
            return true;
        }

        if (normalized.contains("unmute")) {
            String chatName = extractNamedTargetQuery(prompt, "unmute");
            if (TextUtils.isEmpty(chatName)) {
                addMessageBubble("Tell me which chat to unmute, for example: unmute chat family.", false, true);
                return true;
            }
            executeLocalAction("unmute_chat_by_name", chatName, "Unmuting chat...");
            return true;
        }

        if (normalized.contains("mute")) {
            String chatName = extractNamedTargetQuery(prompt, "mute");
            if (TextUtils.isEmpty(chatName)) {
                addMessageBubble("Tell me which chat to mute, for example: mute chat work.", false, true);
                return true;
            }
            executeLocalAction("mute_chat_by_name", chatName, "Muting chat...");
            return true;
        }

        if (normalized.contains("unpin")) {
            String chatName = extractNamedTargetQuery(prompt, "unpin");
            if (TextUtils.isEmpty(chatName)) {
                addMessageBubble("Tell me which chat to unpin, for example: unpin chat updates.", false, true);
                return true;
            }
            executeLocalAction("unpin_chat_by_name", chatName, "Unpinning chat...");
            return true;
        }

        if (normalized.contains("pin")) {
            String chatName = extractNamedTargetQuery(prompt, "pin");
            if (TextUtils.isEmpty(chatName)) {
                addMessageBubble("Tell me which chat to pin, for example: pin chat family.", false, true);
                return true;
            }
            executeLocalAction("pin_chat_by_name", chatName, "Pinning chat...");
            return true;
        }

        if (normalized.contains("delete chat") || normalized.contains("remove chat") || normalized.startsWith("delete ") || normalized.startsWith("remove ")) {
            String chatName = extractNamedTargetQuery(prompt, normalized.startsWith("remove") ? "remove" : "delete");
            if (TextUtils.isEmpty(chatName)) {
                addMessageBubble("Tell me which chat to delete, for example: delete chat spam.", false, true);
                return true;
            }
            executeLocalAction("delete_chat_by_name", chatName, "Deleting chat...");
            return true;
        }

        if (normalized.contains("open channel") || normalized.contains("go to channel")) {
            String channelName = extractChatNameQuery(prompt, normalized);
            if (TextUtils.isEmpty(channelName)) {
                addMessageBubble("Tell me channel name, for example: open channel alexgram updates.", false, true);
                return true;
            }
            executeLocalAction("open_chat_by_name", "channel:" + channelName, "Opening channel...");
            return true;
        }

        if (normalized.contains("open group") || normalized.contains("go to group")) {
            String groupName = extractChatNameQuery(prompt, normalized);
            if (TextUtils.isEmpty(groupName)) {
                addMessageBubble("Tell me group name, for example: open group dev team.", false, true);
                return true;
            }
            executeLocalAction("open_chat_by_name", "group:" + groupName, "Opening group...");
            return true;
        }

        if (normalized.contains("open chat") || normalized.contains("go to chat") || normalized.startsWith("open ")) {
            String chatName = extractChatNameQuery(prompt, normalized);
            if (TextUtils.isEmpty(chatName)) {
                addMessageBubble("Tell me chat name, for example: open chat family.", false, true);
                return true;
            }
            executeLocalAction("open_chat_by_name", chatName, "Opening chat...");
            return true;
        }

        if (normalized.contains("unread") && (normalized.contains("scroll") || normalized.contains("jump") || normalized.contains("go") || normalized.contains("open"))) {
            executeLocalAction("scroll_unread", "", "Finding unread chats...");
            return true;
        }

        if (normalized.contains("list chats") || normalized.contains("show chats") || normalized.contains("chat list") || normalized.contains("list dialogs")) {
            executeLocalAction("list_chats", "", "Collecting chats...");
            return true;
        }

        if (normalized.contains("find message") || normalized.contains("search message") || normalized.contains("find msg") || normalized.contains("search msg") || normalized.startsWith("find ") || normalized.startsWith("search ")) {
            String query = extractSearchQuery(prompt, normalized);
            if (TextUtils.isEmpty(query)) {
                addMessageBubble("Tell me what to find, for example: find message project update.", false, true);
                return true;
            }
            executeLocalAction("find_message", query, "Searching messages...");
            return true;
        }

        if (normalized.contains("scroll") || normalized.contains("go up") || normalized.contains("go down") || normalized.contains("jump to top") || normalized.contains("jump to bottom")) {
            String direction = "down";
            if (containsAny(normalized, "top", "oldest", "beginning", "start")) {
                direction = "top";
            } else if (containsAny(normalized, "bottom", "latest", "newest", "end", "last")) {
                direction = "bottom";
            } else if (containsAny(normalized, "up", "above", "previous")) {
                direction = "up";
            } else if (containsAny(normalized, "down", "below", "next")) {
                direction = "down";
            }
            executeLocalAction("scroll_chat", direction, "Scrolling chat...");
            return true;
        }

        if (normalized.contains("play") && containsAny(normalized, "video", "audio", "voice", "music", "media", "song")) {
            String mediaType = "any";
            if (containsAny(normalized, "video", "clip")) {
                mediaType = "video";
            } else if (containsAny(normalized, "audio", "voice", "music", "song")) {
                mediaType = "audio";
            }
            executeLocalAction("play_media", mediaType, "Trying to play media...");
            return true;
        }

        return false;
    }

    private void executeLocalAction(String action, String argument, String typingText) {
        if (assistantRequestDelegate == null) {
            addMessageBubble("Assistant action bridge is not connected yet.", false, true);
            return;
        }

        showTypingBubble();
        if (!TextUtils.isEmpty(typingText) && !messageViews.isEmpty()) {
            View last = messageViews.get(messageViews.size() - 1);
            if (last instanceof TextView) {
                ((TextView) last).setText(typingText);
            }
        }

        assistantRequestDelegate.onAction(action, argument, new AssistantRequestCallback() {
            @Override
            public void onSuccess(String response) {
                AndroidUtilities.runOnUIThread(() -> hideTypingBubble(TextUtils.isEmpty(response) ? "Done." : response));
            }

            @Override
            public void onError(String error) {
                AndroidUtilities.runOnUIThread(() -> hideTypingBubble(TextUtils.isEmpty(error) ? "I could not run that action right now." : error));
            }
        });
    }

    private String extractSearchQuery(String originalPrompt, String normalizedPrompt) {
        if (TextUtils.isEmpty(originalPrompt)) {
            return "";
        }
        String query = originalPrompt.trim();
        String normalized = " " + normalizedPrompt + " ";
        if (normalized.contains(" find message ")) {
            query = query.replaceFirst("(?i)^.*find\\s+message\\s+", "");
        } else if (normalized.contains(" search message ")) {
            query = query.replaceFirst("(?i)^.*search\\s+message\\s+", "");
        } else if (normalized.contains(" find msg ")) {
            query = query.replaceFirst("(?i)^.*find\\s+msg\\s+", "");
        } else if (normalized.contains(" search msg ")) {
            query = query.replaceFirst("(?i)^.*search\\s+msg\\s+", "");
        } else if (normalizedPrompt.startsWith("find ")) {
            query = query.replaceFirst("(?i)^find\\s+", "");
        } else if (normalizedPrompt.startsWith("search ")) {
            query = query.replaceFirst("(?i)^search\\s+", "");
        }
        return query.trim();
    }

    private String extractChatNameQuery(String originalPrompt, String normalizedPrompt) {
        if (TextUtils.isEmpty(originalPrompt)) {
            return "";
        }
        String query = originalPrompt.trim();
        String normalized = " " + normalizedPrompt + " ";
        if (normalized.contains(" open chat ")) {
            query = query.replaceFirst("(?i)^.*open\\s+chat\\s+", "");
        } else if (normalized.contains(" open channel ")) {
            query = query.replaceFirst("(?i)^.*open\\s+channel\\s+", "");
        } else if (normalized.contains(" open group ")) {
            query = query.replaceFirst("(?i)^.*open\\s+group\\s+", "");
        } else if (normalized.contains(" go to chat ")) {
            query = query.replaceFirst("(?i)^.*go\\s+to\\s+chat\\s+", "");
        } else if (normalized.contains(" go to channel ")) {
            query = query.replaceFirst("(?i)^.*go\\s+to\\s+channel\\s+", "");
        } else if (normalized.contains(" go to group ")) {
            query = query.replaceFirst("(?i)^.*go\\s+to\\s+group\\s+", "");
        } else if (normalizedPrompt.startsWith("open ")) {
            query = query.replaceFirst("(?i)^open\\s+", "");
        }
        return query.trim();
    }

    private String extractNamedTargetQuery(String originalPrompt, String actionKeyword) {
        if (TextUtils.isEmpty(originalPrompt)) {
            return "";
        }
        String query = originalPrompt.trim();
        String keyword = TextUtils.isEmpty(actionKeyword) ? "" : actionKeyword.trim();
        if (!TextUtils.isEmpty(keyword)) {
            query = query.replaceFirst("(?i)^.*" + keyword + "\\s+", "");
        }
        query = query.replaceFirst("(?i)^chat\\s+", "")
                .replaceFirst("(?i)^group\\s+", "")
                .replaceFirst("(?i)^channel\\s+", "")
                .replaceFirst("(?i)^dialog\\s+", "")
                .trim();
        return query;
    }

    private boolean containsAny(String source, String... values) {
        if (TextUtils.isEmpty(source) || values == null) {
            return false;
        }
        for (String value : values) {
            if (!TextUtils.isEmpty(value) && source.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private ParsedSettingsCommand parseSettingsCommand(String normalizedPrompt) {
        String normalized = normalizeSettingText(normalizedPrompt);
        if (TextUtils.isEmpty(normalized)) {
            return null;
        }

        if (normalized.startsWith("set ") || normalized.startsWith("change ") || normalized.startsWith("update ")) {
            String valuePart = normalized;
            if (normalized.startsWith("set ")) {
                valuePart = normalized.substring(4);
            } else if (normalized.startsWith("change ")) {
                valuePart = normalized.substring(7);
            } else if (normalized.startsWith("update ")) {
                valuePart = normalized.substring(7);
            }
            int toIndex = valuePart.indexOf(" to ");
            int separatorLength = 4;
            if (toIndex < 0) {
                toIndex = valuePart.indexOf(" = ");
                separatorLength = 3;
            }
            if (toIndex < 0) {
                return new ParsedSettingsCommand(ACTION_SET, cleanFeatureQuery(valuePart), null);
            }
            String feature = valuePart.substring(0, toIndex).trim();
            String rawValue = valuePart.substring(toIndex + separatorLength).trim();
            return new ParsedSettingsCommand(ACTION_SET, cleanFeatureQuery(feature), rawValue);
        }

        int action = ACTION_UNKNOWN;
        if (normalized.contains("turn off") || normalized.contains("trun off") || normalized.contains("disable") || normalized.contains("deactivate")) {
            action = ACTION_OFF;
        } else if (normalized.contains("turn on") || normalized.contains("trun on") || normalized.contains("enable") || normalized.contains("activate")) {
            action = ACTION_ON;
        } else if (normalized.contains("toggle") || normalized.contains("switch")) {
            action = ACTION_TOGGLE;
        } else if (normalized.startsWith("is ") || normalized.contains(" status") || normalized.contains(" state")) {
            action = ACTION_STATUS;
        }

        if (action == ACTION_UNKNOWN) {
            return null;
        }

        return new ParsedSettingsCommand(action, cleanFeatureQuery(normalized), null);
    }

    private String cleanFeatureQuery(String value) {
        String featureQuery = " " + normalizeSettingText(value) + " ";
        featureQuery = featureQuery.replace(" turn off ", " ").replace(" trun off ", " ").replace(" turn on ", " ").replace(" trun on ", " ");
        featureQuery = featureQuery.replace(" disable ", " ").replace(" enable ", " ");
        featureQuery = featureQuery.replace(" deactivate ", " ").replace(" activate ", " ");
        featureQuery = featureQuery.replace(" toggle ", " ").replace(" switch ", " ");
        featureQuery = featureQuery.replace(" status ", " ").replace(" state ", " ").replace(" is ", " ");
        featureQuery = featureQuery.replace(" a settings ", " ").replace(" settings ", " ").replace(" feature ", " ");
        featureQuery = featureQuery.replace(" please ", " ").replace(" assistant ", " ").replace(" alexgram ", " ");
        return normalizeSettingText(featureQuery);
    }

    private String applySettingsCommand(ConfigMatch match, ParsedSettingsCommand command) {
        if (match == null || match.item == null) {
            return "I could not resolve that setting.";
        }

        String settingName = humanizeSettingName(match.readableName);
        ConfigItem item = match.item;

        if (command.action == ACTION_STATUS) {
            return settingName + " is currently " + formatSettingValue(item) + ".";
        }

        if (command.action == ACTION_ON || command.action == ACTION_OFF || command.action == ACTION_TOGGLE) {
            if (item.type != ConfigItem.configTypeBool) {
                return settingName + " is not an ON/OFF setting. Use: set " + normalizeSettingText(settingName) + " to <value>.";
            }
            boolean updated;
            if (command.action == ACTION_ON) {
                item.setConfigBool(true);
                updated = true;
            } else if (command.action == ACTION_OFF) {
                item.setConfigBool(false);
                updated = false;
            } else {
                updated = item.toggleConfigBool();
            }
            String response = settingName + " turned " + (updated ? "ON." : "OFF.");
            if (doesSettingLikelyNeedRestart(match)) {
                response += " Restart app once to fully apply this change.";
            }
            return response;
        }

        if (command.action == ACTION_SET) {
            if (TextUtils.isEmpty(command.rawValue)) {
                return "Tell me the value too. Example: set " + normalizeSettingText(settingName) + " to 1.";
            }
            Object parsed = parseSettingValue(item, command.rawValue);
            if (parsed == null) {
                return "I could not parse that value for " + settingName + ".";
            }

            if (item.type == ConfigItem.configTypeBool) {
                item.setConfigBool((Boolean) parsed);
            } else if (item.type == ConfigItem.configTypeInt) {
                item.setConfigInt((Integer) parsed);
            } else if (item.type == ConfigItem.configTypeLong) {
                item.setConfigLong((Long) parsed);
            } else if (item.type == ConfigItem.configTypeFloat) {
                item.setConfigFloat((Float) parsed);
            } else if (item.type == ConfigItem.configTypeString) {
                item.setConfigString((String) parsed);
            } else {
                return "This setting type is not supported yet.";
            }

            String response = settingName + " set to " + formatRawValue(parsed) + ".";
            if (doesSettingLikelyNeedRestart(match)) {
                response += " Restart app once to fully apply this change.";
            }
            return response;
        }

        return "I could not understand that command.";
    }

    private Object parseSettingValue(ConfigItem item, String rawValue) {
        String normalized = normalizeSettingText(rawValue);
        if (TextUtils.isEmpty(normalized)) {
            return null;
        }

        if (item.type == ConfigItem.configTypeBool) {
            if (normalized.equals("on") || normalized.equals("true") || normalized.equals("enable") || normalized.equals("enabled") || normalized.equals("1")) {
                return Boolean.TRUE;
            }
            if (normalized.equals("off") || normalized.equals("false") || normalized.equals("disable") || normalized.equals("disabled") || normalized.equals("0")) {
                return Boolean.FALSE;
            }
            Object parsed = item.checkConfigFromString(normalized);
            return parsed instanceof Boolean ? parsed : null;
        }

        if (item.type == ConfigItem.configTypeInt) {
            try {
                String compact = normalized.replaceAll("[^0-9-]", "");
                if (!TextUtils.isEmpty(compact)) {
                    return Integer.parseInt(compact);
                }
            } catch (Throwable ignore) {
            }
            Object parsed = item.checkConfigFromString(normalized);
            return parsed instanceof Integer ? parsed : null;
        }

        if (item.type == ConfigItem.configTypeLong) {
            try {
                String compact = normalized.replaceAll("[^0-9-]", "");
                if (!TextUtils.isEmpty(compact)) {
                    return Long.parseLong(compact);
                }
            } catch (Throwable ignore) {
            }
            Object parsed = item.checkConfigFromString(normalized);
            return parsed instanceof Long ? parsed : null;
        }

        if (item.type == ConfigItem.configTypeFloat) {
            try {
                String compact = normalized.replaceAll("[^0-9.-]", "");
                if (!TextUtils.isEmpty(compact)) {
                    return Float.parseFloat(compact);
                }
            } catch (Throwable ignore) {
            }
            Object parsed = item.checkConfigFromString(normalized);
            return parsed instanceof Float ? parsed : null;
        }

        if (item.type == ConfigItem.configTypeString) {
            String value = rawValue.trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
                value = value.substring(1, value.length() - 1);
            }
            return value;
        }

        return null;
    }

    private String formatSettingValue(ConfigItem item) {
        if (item.type == ConfigItem.configTypeBool) {
            return item.Bool() ? "ON" : "OFF";
        }
        if (item.value == null) {
            return "empty";
        }
        return formatRawValue(item.value);
    }

    private String formatRawValue(Object value) {
        if (value == null) {
            return "empty";
        }
        if (value instanceof String) {
            return "\"" + value + "\"";
        }
        return String.valueOf(value);
    }

    private boolean doesSettingLikelyNeedRestart(ConfigMatch match) {
        String name = normalizeSettingText(match.readableName + " " + match.methodName + " " + match.item.getKey());
        return name.contains("notification")
                || name.contains("icon")
                || name.contains("push service")
                || name.contains("proxy")
                || name.contains("service type")
                || name.contains("dc")
                || name.contains("id dc")
                || name.contains("language")
                || name.contains("font");
    }

    private String buildSettingsExamples() {
        List<String> examples = new ArrayList<>();
        examples.add("- turn off pill chat title");
        examples.add("- turn on hide stories from header");
        examples.add("- toggle reactions");
        examples.add("- is center action bar title on");
        examples.add("- set double tap action to 3");
        examples.add("- set custom title to Alexgram");
        examples.add("- set notification icon to 1");
        return "You can control A-Settings here. Try:\n" + TextUtils.join("\n", examples);
    }

    private List<ConfigMatch> findConfigMatches(String featureQuery) {
        String query = normalizeSettingText(featureQuery);
        if (TextUtils.isEmpty(query)) {
            return Collections.emptyList();
        }

        Method[] methods = NaConfig.class.getMethods();
        List<ConfigMatch> matches = new ArrayList<>();

        for (Method method : methods) {
            if (!method.getName().startsWith("get") || method.getParameterTypes().length != 0 || method.getReturnType() != ConfigItem.class) {
                continue;
            }
            try {
                Object result = method.invoke(NaConfig.INSTANCE);
                if (!(result instanceof ConfigItem)) {
                    continue;
                }
                ConfigItem item = (ConfigItem) result;

                int type = item.type;
                if (type != ConfigItem.configTypeBool
                        && type != ConfigItem.configTypeInt
                        && type != ConfigItem.configTypeLong
                        && type != ConfigItem.configTypeFloat
                        && type != ConfigItem.configTypeString) {
                    continue;
                }

                String methodName = splitCamel(method.getName().substring(3));
                String keyName = splitCamel(item.getKey());
                float score = matchScore(query, methodName, keyName);
                if (score >= 0.34f) {
                    matches.add(new ConfigMatch(item, keyName, methodName, score));
                }
            } catch (Throwable ignore) {
            }
        }

        if (matches.isEmpty()) {
            return Collections.emptyList();
        }

        Collections.sort(matches, (a, b) -> Float.compare(b.score, a.score));
        return matches;
    }

    private boolean isAmbiguousMatch(List<ConfigMatch> matches) {
        if (matches == null || matches.size() < 2) {
            return false;
        }
        float first = matches.get(0).score;
        float second = matches.get(1).score;
        return first >= 0.40f && second >= 0.40f && Math.abs(first - second) <= 0.12f;
    }

    private String buildAmbiguousReply(List<ConfigMatch> matches) {
        List<String> options = new ArrayList<>();
        Set<String> dedupe = new LinkedHashSet<>();
        int limit = Math.min(4, matches.size());
        for (int i = 0; i < limit; i++) {
            ConfigMatch match = matches.get(i);
            String name = humanizeSettingName(match.readableName);
            if (!dedupe.add(name)) {
                continue;
            }
            options.add("- " + name + " (" + settingTypeLabel(match.item.type) + ")");
        }
        return "I found multiple matching settings. Reply with exact setting name:\n" + TextUtils.join("\n", options);
    }

    private String settingTypeLabel(int type) {
        if (type == ConfigItem.configTypeBool) {
            return "on/off";
        }
        if (type == ConfigItem.configTypeInt) {
            return "integer";
        }
        if (type == ConfigItem.configTypeLong) {
            return "long";
        }
        if (type == ConfigItem.configTypeFloat) {
            return "float";
        }
        if (type == ConfigItem.configTypeString) {
            return "text";
        }
        return "value";
    }

    private float matchScore(String query, String... candidates) {
        Set<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return 0f;
        }

        float best = 0f;
        for (String candidate : candidates) {
            String normalizedCandidate = normalizeSettingText(candidate);
            if (TextUtils.isEmpty(normalizedCandidate)) {
                continue;
            }

            float score = 0f;
            if (normalizedCandidate.contains(query)) {
                score += 0.45f;
            }
            if (query.contains(normalizedCandidate) && normalizedCandidate.length() > 5) {
                score += 0.2f;
            }

            Set<String> candidateTokens = tokenize(normalizedCandidate);
            int hits = 0;
            for (String token : queryTokens) {
                if (candidateTokens.contains(token) || normalizedCandidate.contains(token)) {
                    hits++;
                }
            }
            score += (hits / (float) queryTokens.size()) * 0.8f;
            best = Math.max(best, score);
        }
        return best;
    }

    private Set<String> tokenize(String value) {
        Set<String> tokens = new LinkedHashSet<>();
        String[] parts = normalizeSettingText(value).split(" ");
        for (String part : parts) {
            if (part.length() >= 2) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private String normalizeSettingText(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String splitCamel(String value) {
        if (value == null) {
            return "";
        }
        String spaced = value.replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
        return normalizeSettingText(spaced);
    }

    private String humanizeSettingName(String value) {
        String normalized = normalizeSettingText(value);
        if (TextUtils.isEmpty(normalized)) {
            return "Setting";
        }
        String[] parts = normalized.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private static final int ACTION_UNKNOWN = -1;
    private static final int ACTION_ON = 1;
    private static final int ACTION_OFF = 0;
    private static final int ACTION_TOGGLE = 2;
    private static final int ACTION_STATUS = 3;
    private static final int ACTION_SET = 4;

    private static class ConfigMatch {
        final ConfigItem item;
        final String readableName;
        final String methodName;
        final float score;

        ConfigMatch(ConfigItem item, String readableName, String methodName, float score) {
            this.item = item;
            this.readableName = readableName;
            this.methodName = methodName;
            this.score = score;
        }
    }

    private static class ParsedSettingsCommand {
        final int action;
        final String featureQuery;
        final String rawValue;

        ParsedSettingsCommand(int action, String featureQuery, String rawValue) {
            this.action = action;
            this.featureQuery = featureQuery;
            this.rawValue = rawValue;
        }
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
                org.telegram.messenger.AndroidUtilities.runOnUIThread(this, 280);
            }
        };
        org.telegram.messenger.AndroidUtilities.runOnUIThread(typingRunnable, 280);
    }

    private void hideTypingBubble(String text) {
        if (typingRunnable != null) {
            org.telegram.messenger.AndroidUtilities.cancelRunOnUIThread(typingRunnable);
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
        
        if (text != null && text.contains("[GEN_IMAGE:")) {
            int start = text.indexOf("[GEN_IMAGE:") + 11;
            int end = text.lastIndexOf("]");
            if (end > start) {
                String prompt = text.substring(start, end).trim();
                addImageBubble(prompt);
                showReactionBubble("🎨");
                return;
            }
        }

        addMessageBubble(text, false, true);
        showReactionBubble("✨");
    }

    private void addImageBubble(String prompt) {
        final FrameLayout container = new FrameLayout(getContext());
        
        final BackupImageView imageView = new BackupImageView(getContext());
        imageView.setRoundRadius(AndroidUtilities.dp(14));
        //  pulsing placeholder background
        imageView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), 0x22FFFFFF));
        
        final RadialProgressView progressView = new RadialProgressView(getContext());
        progressView.setSize(AndroidUtilities.dp(30));
        progressView.setProgressColor(0xFFFFFFFF);
        
        container.addView(imageView, LayoutHelper.createFrame(220, 220));
        container.addView(progressView, LayoutHelper.createFrame(30, 30, Gravity.CENTER));
        
        LinearLayout.LayoutParams lp = LayoutHelper.createLinear(220, 220);
        lp.gravity = Gravity.LEFT;
        lp.topMargin = AndroidUtilities.dp(5);
        bubblesContainer.addView(container, lp);
        messageViews.add(container);
        
        while (messageViews.size() > MAX_BUBBLES) {
            View remove = messageViews.remove(0);
            bubblesContainer.removeView(remove);
        }

        String encodedPrompt = android.net.Uri.encode(prompt);
        String imageUrl = "https://image.pollinations.ai/prompt/" + encodedPrompt + "?width=1024&height=1024&nologo=true&seed=" + System.currentTimeMillis();
        
        imageView.getImageReceiver().setDelegate((imageReceiver, set, thumb, memCache) -> {
            if (set && !thumb) {
                progressView.animate().alpha(0f).setDuration(280).withEndAction(() -> progressView.setVisibility(GONE)).start();
            }
        });

        imageView.setImage(imageUrl, null, null);
        imageView.setOnClickListener(v -> {
             AndroidUtilities.addToClipboard(imageUrl);
             showReactionBubble("📋");
        });

        bubblesScrollView.post(() -> bubblesScrollView.fullScroll(View.FOCUS_DOWN));
    }

    private TextView addMessageBubble(String text, boolean isUser, boolean typewriter) {
        final TextView tv = new TextView(getContext());
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        tv.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8), AndroidUtilities.dp(10), AndroidUtilities.dp(8));
        tv.setMaxWidth(AndroidUtilities.dp(260));
        tv.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), isUser ? 0xD9368EFF : 0x99495D7A));
        tv.setLongClickable(true);
        tv.setOnLongClickListener(v -> {
            CharSequence value = tv.getText();
            if (!TextUtils.isEmpty(value)) {
                AndroidUtilities.addToClipboard(value.toString());
                showReactionBubble("📋");
            }
            return true;
        });

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
                        String current = new String(chars, 0, index[0]);
                        // Parse Markdown and Emojis dynamically
                        CharSequence formatted = tw.nekomimi.nekogram.helpers.EntitiesHelper.parseMarkdown(current);
                        formatted = org.telegram.messenger.Emoji.replaceEmoji(formatted, tv.getPaint().getFontMetricsInt(), false);
                        tv.setText(formatted);
                        
                        index[0] += Math.max(1, chars.length / 40);
                        AndroidUtilities.runOnUIThread(this, 18);
                    } else {
                        // Final full parse to ensure everything is matched
                        CharSequence formatted = tw.nekomimi.nekogram.helpers.EntitiesHelper.parseMarkdown(text);
                        formatted = org.telegram.messenger.Emoji.replaceEmoji(formatted, tv.getPaint().getFontMetricsInt(), false);
                        tv.setText(formatted);
                    }
                    bubblesScrollView.post(() -> bubblesScrollView.fullScroll(View.FOCUS_DOWN));
                }
            };
            AndroidUtilities.runOnUIThread(typer);
        } else {
            CharSequence formatted = tw.nekomimi.nekogram.helpers.EntitiesHelper.parseMarkdown(text);
            formatted = org.telegram.messenger.Emoji.replaceEmoji(formatted, tv.getPaint().getFontMetricsInt(), false);
            tv.setText(formatted);
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
        private final SharedPreferences preferences;

        private float tapKick;
        private float typingPulse;
        private float scrollTilt;
        private float panelFocus;
        private int skinIndex;

        private static final int[] SKIN_SKY = {0xFFF9D7CC, 0xFF2A2A48, 0xFF2A3450, 0xFF71D4FF};
        private static final int[] SKIN_MINT = {0xFFFDE2D0, 0xFF1F3644, 0xFF2D4F5D, 0xFF73F5CF};
        private static final int[] SKIN_SUNSET = {0xFFFFD4C3, 0xFF43315A, 0xFF5D3D79, 0xFFFF9F77};

        public AssistantCharacterView(Context context, SharedPreferences prefs) {
            super(context);
            this.preferences = prefs;
            outlinePaint.setStyle(Paint.Style.STROKE);
            outlinePaint.setStrokeWidth(AndroidUtilities.dp(1.3f));
            outlinePaint.setColor(0x66FFFFFF);
            skinIndex = preferences.getInt("character_skin", 0);
            setSkinColors();
        }

        void cycleSkin() {
            skinIndex = (skinIndex + 1) % 3;
            preferences.edit().putInt("character_skin", skinIndex).apply();
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

        void tick(long now, float dt, boolean typingActive, float scrollEnergy, float animationIntensity) {
            tapKick = Math.max(0f, tapKick - dt * 3.0f);
            typingPulse = Math.max(0f, Math.min(1.0f, typingPulse - dt * 2.0f + (typingActive ? dt * 4.0f : 0f)));
            scrollTilt *= 0.88f;
            panelFocus = Math.max(0f, panelFocus - dt * 0.9f);

            if (particles.size() < (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW ? 6 : 14) && (typingActive || scrollEnergy > 0.3f) && preferences.getBoolean("particle_effects", true)) {
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
