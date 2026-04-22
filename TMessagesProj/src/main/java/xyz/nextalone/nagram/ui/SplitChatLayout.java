package xyz.nextalone.nagram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;

/**
 * Crash-proof Split Chat layout.
 *
 * Each pane hosts a ChatActivity's view directly (like RightSlidingDialogContainer),
 * referencing the existing actionBarLayout — no new ActionBarLayout instances created.
 */
public class SplitChatLayout extends FrameLayout {

    // ── Pane model ────────────────────────────────────────────────────────────

    static class SplitPane {
        long dialogId;
        ChatActivity fragment;
        View fragmentView;
        FrameLayout container;
        float weight = 0.5f;
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final ArrayList<SplitPane> visiblePanes = new ArrayList<>();
    private final ArrayList<MiniPaneTab> miniTabs    = new ArrayList<>();

    private SplitDividerView dividerView;
    private FrameLayout leftContainer;
    private FrameLayout rightContainer;
    private HorizontalScrollView miniTabScrollView;
    private LinearLayout miniTabBar;
    private LaunchActivity launchActivity;
    private boolean isAttached = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public SplitChatLayout(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public void attachToActivity(LaunchActivity activity, long firstDialogId) {
        this.launchActivity = activity;
        if (isAttached) {
            openDialogInNextPane(firstDialogId);
            return;
        }
        isAttached = true;

        // Show picker first, BEFORE we overlay the screen
        showChatPicker(firstDialogId);
    }

    /** Called by SplitChatManager after user picks pane-2 dialog. */
    public void openDialogInNextPane(long dialogId) {
        if (!isAttached) return;

        if (visiblePanes.isEmpty()) {
            // First call: build layout + pane 1 (original chat) + pane 2 (picked)
            // pane 1 dialog is stored in tag
            long pane1DialogId = (getTag() instanceof Long) ? (Long) getTag() : 0;
            buildAndShow(pane1DialogId, dialogId);
        } else if (visiblePanes.size() < 2) {
            addPane(dialogId, false);
            animatePaneIn(rightContainer);
        } else {
            demoteOldestPane();
            addPane(dialogId, false);
            animatePaneIn(rightContainer);
        }
    }

    /** Store the originating dialog so we can use it in openDialogInNextPane. */
    public void setOriginDialogId(long dialogId) {
        setTag(dialogId);
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    private void buildAndShow(long pane1DialogId, long pane2DialogId) {
        buildLayout(launchActivity);

        // Add overlay on top of frameLayout
        launchActivity.frameLayout.addView(this,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        addPane(pane1DialogId, true);
        addPane(pane2DialogId, false);

        // Entry animation
        setAlpha(0f);
        setScaleX(0.97f);
        setScaleY(0.97f);
        animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(300)
                .setInterpolator(CubicBezierInterpolator.DEFAULT)
                .start();
    }

    private void buildLayout(Context ctx) {
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        leftContainer = new FrameLayout(ctx);
        addView(leftContainer);

        dividerView = new SplitDividerView(ctx);
        dividerView.setOnDividerDragListener(this::onDividerDragged);
        dividerView.setOnDividerDoubleTapListener(this::snapDividerToCenter);
        dividerView.setOnDividerLongPressListener(this::closeSplit);
        addView(dividerView);

        rightContainer = new FrameLayout(ctx);
        addView(rightContainer);

        // Mini-tab bar
        miniTabScrollView = new HorizontalScrollView(ctx);
        miniTabScrollView.setHorizontalScrollBarEnabled(false);
        miniTabScrollView.setOverScrollMode(OVER_SCROLL_NEVER);
        miniTabBar = new LinearLayout(ctx);
        miniTabBar.setOrientation(LinearLayout.HORIZONTAL);
        miniTabBar.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(6),
                AndroidUtilities.dp(8), AndroidUtilities.dp(6));
        miniTabScrollView.addView(miniTabBar,
                LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
        addView(miniTabScrollView,
                LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 56,
                        Gravity.BOTTOM | Gravity.START, 4, 0, 4, 8));
        miniTabScrollView.setVisibility(GONE);

        // Close button
        TextView closeFab = new TextView(ctx);
        closeFab.setText(LocaleController.getString("SplitChatClose", R.string.SplitChatClose));
        closeFab.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
        closeFab.setTextSize(12);
        closeFab.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6),
                AndroidUtilities.dp(10), AndroidUtilities.dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.getColor(Theme.key_actionBarDefault));
        bg.setCornerRadius(AndroidUtilities.dp(16));
        closeFab.setBackground(bg);
        closeFab.setOnClickListener(v -> closeSplit());
        addView(closeFab,
                LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                        Gravity.TOP | Gravity.END, 0, 8, 8, 0));
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        layoutPanes();
    }

    private void layoutPanes() {
        int totalW = getWidth(), totalH = getHeight();
        if (totalW == 0 || leftContainer == null) return;
        int divW = AndroidUtilities.dp(4);
        float leftWeight = visiblePanes.isEmpty() ? 0.5f : visiblePanes.get(0).weight;
        int leftW = (int) ((totalW - divW) * leftWeight);
        leftContainer.layout(0, 0, leftW, totalH);
        dividerView.layout(leftW, 0, leftW + divW, totalH);
        rightContainer.layout(leftW + divW, 0, totalW, totalH);
    }

    // ── Pane management ───────────────────────────────────────────────────────

    /**
     * Creates a ChatActivity, calls its lifecycle manually, and adds its view
     * directly to the pane container — no new ActionBarLayout needed.
     */
    private void addPane(long dialogId, boolean isLeft) {
        if (dialogId == 0) return;
        SplitPane pane = new SplitPane();
        pane.dialogId = dialogId;
        pane.container = isLeft ? leftContainer : rightContainer;

        Bundle args = new Bundle();
        if (dialogId > 0) {
            args.putLong("user_id", dialogId);
        } else {
            args.putLong("chat_id", -dialogId);
        }

        try {
            ChatActivity chat = new ChatActivity(args);
            chat.isInsideContainer = true;
            chat.setParentLayout(launchActivity.actionBarLayout);
            if (!chat.onFragmentCreate()) return;
            View view = chat.createView(launchActivity);
            if (view == null) return;
            chat.onResume();
            pane.fragment = chat;
            pane.fragmentView = view;
            pane.container.addView(view,
                    LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            visiblePanes.add(pane);
            if (dividerView != null) {
                dividerView.setVisibility(visiblePanes.size() >= 2 ? VISIBLE : GONE);
            }
            requestLayout();
        } catch (Exception e) {
            android.util.Log.e("SplitChatLayout", "addPane failed", e);
        }
    }

    private void demoteOldestPane() {
        if (visiblePanes.isEmpty()) return;
        SplitPane old = visiblePanes.remove(0);
        String title = getTitleForDialog(old.dialogId);

        old.container.animate()
                .scaleX(0.3f).scaleY(0.3f).alpha(0f)
                .setDuration(260)
                .setInterpolator(CubicBezierInterpolator.EASE_BOTH)
                .withEndAction(() -> {
                    try {
                        if (old.fragment != null) old.fragment.onPause();
                    } catch (Exception ignore) {}
                    old.container.removeAllViews();
                    old.container.setScaleX(1f);
                    old.container.setScaleY(1f);
                    old.container.setAlpha(1f);
                    addMiniTab(old.dialogId, title);

                    // Shift remaining pane to left
                    if (!visiblePanes.isEmpty()) {
                        SplitPane rem = visiblePanes.get(0);
                        if (rem.fragmentView != null && rem.fragmentView.getParent() != leftContainer) {
                            rightContainer.removeView(rem.fragmentView);
                            leftContainer.addView(rem.fragmentView,
                                    LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                        }
                        rem.container = leftContainer;
                    }
                    requestLayout();
                }).start();
    }

    private void addMiniTab(long dialogId, String title) {
        MiniPaneTab tab = new MiniPaneTab(getContext(), dialogId, title,
                () -> restoreMiniTab(dialogId));
        miniTabs.add(tab);
        miniTabBar.addView(tab);
        if (miniTabScrollView.getVisibility() != VISIBLE) {
            miniTabScrollView.setVisibility(VISIBLE);
            miniTabScrollView.setAlpha(0f);
            miniTabScrollView.animate().alpha(1f).setDuration(200).start();
        }
        tab.setScaleX(0.3f);
        tab.setScaleY(0.3f);
        tab.setAlpha(0f);
        new SpringAnimation(new FloatValueHolder(0f))
                .setSpring(new SpringForce(1000f)
                        .setStiffness(SpringForce.STIFFNESS_MEDIUM)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY))
                .addUpdateListener((a, val, vel) -> {
                    float p = val / 1000f;
                    tab.setScaleX(p); tab.setScaleY(p); tab.setAlpha(p);
                })
                .start();
    }

    private void restoreMiniTab(long dialogId) {
        MiniPaneTab found = null;
        for (MiniPaneTab t : miniTabs) if (t.dialogId == dialogId) { found = t; break; }
        if (found == null) return;
        miniTabs.remove(found);
        miniTabBar.removeView(found);
        if (miniTabs.isEmpty()) miniTabScrollView.setVisibility(GONE);
        if (visiblePanes.size() >= 2) demoteOldestPane();
        // Post to let demote animation start first
        AndroidUtilities.runOnUIThread(() -> addPane(dialogId, false), 300);
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    // ── Animations ────────────────────────────────────────────────────────────

    private void animatePaneIn(FrameLayout pane) {
        pane.setAlpha(0f);
        pane.setTranslationX(200f);
        SpringAnimation spring = new SpringAnimation(new FloatValueHolder(200f))
                .setSpring(new SpringForce(0f)
                        .setStiffness(SpringForce.STIFFNESS_MEDIUM)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY));
        spring.addUpdateListener((a, val, vel) -> pane.setTranslationX(val));
        spring.addEndListener((a, canceled, val, vel) -> pane.setTranslationX(0f));
        spring.start();
        pane.animate().alpha(1f).setDuration(200).start();
    }

    // ── Divider ───────────────────────────────────────────────────────────────

    private void onDividerDragged(float rawX) {
        int totalW = getWidth();
        if (totalW <= 0 || visiblePanes.isEmpty()) return;
        float w = Math.max(0.30f, Math.min(0.70f, rawX / totalW));
        visiblePanes.get(0).weight = w;
        requestLayout();
    }

    private void snapDividerToCenter() {
        if (visiblePanes.isEmpty()) return;
        float cur = visiblePanes.get(0).weight;
        ValueAnimator anim = ValueAnimator.ofFloat(cur, 0.5f);
        anim.setDuration(260);
        anim.setInterpolator(CubicBezierInterpolator.DEFAULT);
        anim.addUpdateListener(a -> {
            if (!visiblePanes.isEmpty()) visiblePanes.get(0).weight = (float) a.getAnimatedValue();
            requestLayout();
        });
        anim.start();
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    // ── Chat picker ───────────────────────────────────────────────────────────

    private void showChatPicker(long originDialogId) {
        setTag(originDialogId);
        if (launchActivity == null) return;
        Bundle args = new Bundle();
        args.putInt("dialogsType", 1);
        args.putBoolean("resetDelegate", false);
        DialogsActivity picker = new DialogsActivity(args);
        picker.setDelegate((fragment, dids, message, param, notify, scheduleDate, period, topicsFrag) -> {
            if (dids != null && !dids.isEmpty()) {
                long did = dids.get(0).dialogId;
                SplitChatManager.getInstance().openDialogInSplit(did);
            }
            fragment.finishFragment();
            return true;
        });
        launchActivity.actionBarLayout.presentFragment(
                new INavigationLayout.NavigationParams(picker));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getTitleForDialog(long dialogId) {
        try {
            int acc = UserConfig.selectedAccount;
            if (dialogId > 0) {
                TLRPC.User u = MessagesController.getInstance(acc).getUser(dialogId);
                if (u != null) return u.first_name != null ? u.first_name : "Chat";
            } else {
                TLRPC.Chat c = MessagesController.getInstance(acc).getChat(-dialogId);
                if (c != null) return c.title != null ? c.title : "Chat";
            }
        } catch (Exception ignore) {}
        return "Chat";
    }

    // ── Close / lifecycle ─────────────────────────────────────────────────────

    public void closeSplit() {
        isAttached = false;
        animate().alpha(0f).scaleX(0.96f).scaleY(0.96f)
                .setDuration(220)
                .withEndAction(() -> {
                    onDestroy();
                    if (getParent() instanceof ViewGroup)
                        ((ViewGroup) getParent()).removeView(this);
                    SplitChatManager.getInstance().onSplitClosed();
                }).start();
    }

    public void onPause() {
        for (SplitPane p : visiblePanes) {
            try { if (p.fragment != null) p.fragment.onPause(); } catch (Exception ignore) {}
        }
    }

    public void onResume() {
        for (SplitPane p : visiblePanes) {
            try { if (p.fragment != null) p.fragment.onResume(); } catch (Exception ignore) {}
        }
    }

    public void onDestroy() {
        for (SplitPane p : visiblePanes) {
            try {
                if (p.fragment != null) {
                    p.fragment.onPause();
                    p.fragment.onFragmentDestroy();
                }
            } catch (Exception ignore) {}
        }
        visiblePanes.clear();
        miniTabs.clear();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Draggable Divider
    // ══════════════════════════════════════════════════════════════════════════

    public static class SplitDividerView extends View {
        interface DividerDragListener      { void onDrag(float rawX); }
        interface DividerDoubleTapListener { void onDoubleTap(); }
        interface DividerLongPressListener { void onLongPress(); }

        private DividerDragListener      dragListener;
        private DividerDoubleTapListener doubleTapListener;
        private DividerLongPressListener longPressListener;

        private final Paint trackPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF handleRect  = new RectF();

        private float lastDownX;
        private long  lastTapTime;
        private boolean isDragging;
        private float pressProgress;
        private SpringAnimation pressSpring;

        private final Runnable longPressRunnable = () -> {
            if (!isDragging && longPressListener != null) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                longPressListener.onLongPress();
            }
        };

        public SplitDividerView(Context ctx) {
            super(ctx);
            trackPaint.setColor(0x33000000);
            handlePaint.setColor(Theme.getColor(Theme.key_actionBarDefault));
        }

        public void setOnDividerDragListener(DividerDragListener l)          { dragListener = l; }
        public void setOnDividerDoubleTapListener(DividerDoubleTapListener l) { doubleTapListener = l; }
        public void setOnDividerLongPressListener(DividerLongPressListener l) { longPressListener = l; }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            canvas.drawRect(0, 0, w, h, trackPaint);
            float hh = AndroidUtilities.dp(28) + pressProgress * AndroidUtilities.dp(10);
            float hw = AndroidUtilities.dp(3);
            float cx = w / 2f, cy = h / 2f;
            handleRect.set(cx - hw / 2f, cy - hh / 2f, cx + hw / 2f, cy + hh / 2f);
            canvas.drawRoundRect(handleRect, AndroidUtilities.dp(3), AndroidUtilities.dp(3), handlePaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getRawX();
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastDownX = x; isDragging = false;
                    postDelayed(longPressRunnable, 500);
                    springPress(1f);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(x - lastDownX) > AndroidUtilities.dp(4)) {
                        isDragging = true;
                        removeCallbacks(longPressRunnable);
                    }
                    if (isDragging && dragListener != null) dragListener.onDrag(x);
                    break;
                case MotionEvent.ACTION_UP:
                    removeCallbacks(longPressRunnable);
                    springPress(0f);
                    if (!isDragging) {
                        long now = System.currentTimeMillis();
                        if (now - lastTapTime < 350 && doubleTapListener != null)
                            doubleTapListener.onDoubleTap();
                        lastTapTime = now;
                    }
                    isDragging = false;
                    break;
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(longPressRunnable);
                    springPress(0f);
                    isDragging = false;
                    break;
            }
            return true;
        }

        private void springPress(float target) {
            if (pressSpring != null) pressSpring.cancel();
            pressSpring = new SpringAnimation(new FloatValueHolder(pressProgress * 1000f))
                    .setSpring(new SpringForce(target * 1000f)
                            .setStiffness(SpringForce.STIFFNESS_MEDIUM)
                            .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY));
            pressSpring.addUpdateListener((a, val, vel) -> {
                pressProgress = val / 1000f;
                invalidate();
            });
            pressSpring.start();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Mini Pane Tab Icon
    // ══════════════════════════════════════════════════════════════════════════

    public static class MiniPaneTab extends FrameLayout {
        final long dialogId;

        public MiniPaneTab(Context ctx, long dialogId, String title, Runnable onTap) {
            super(ctx);
            this.dialogId = dialogId;

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    AndroidUtilities.dp(44), AndroidUtilities.dp(44));
            lp.setMargins(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            setLayoutParams(lp);

            BackupImageView avatar = new BackupImageView(ctx);
            avatar.setRoundRadius(AndroidUtilities.dp(22));
            AvatarDrawable avatarDrawable = new AvatarDrawable();

            try {
                int acc = UserConfig.selectedAccount;
                if (dialogId > 0) {
                    TLRPC.User u = MessagesController.getInstance(acc).getUser(dialogId);
                    if (u != null) {
                        avatarDrawable.setInfo(u);
                        avatar.setImage(ImageLocation.getForUser(u, ImageLocation.TYPE_SMALL),
                                "50_50", avatarDrawable, u);
                    } else {
                        avatarDrawable.setInfo(0, title, null);
                        avatar.setImageDrawable(avatarDrawable);
                    }
                } else {
                    TLRPC.Chat c = MessagesController.getInstance(acc).getChat(-dialogId);
                    if (c != null) {
                        avatarDrawable.setInfo(c);
                        avatar.setImage(ImageLocation.getForChat(c, ImageLocation.TYPE_SMALL),
                                "50_50", avatarDrawable, c);
                    } else {
                        avatarDrawable.setInfo(0, title, null);
                        avatar.setImageDrawable(avatarDrawable);
                    }
                }
            } catch (Exception e) {
                avatarDrawable.setInfo(0, title, null);
                avatar.setImageDrawable(avatarDrawable);
            }

            addView(avatar, LayoutHelper.createFrame(40, 40, Gravity.CENTER));

            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setStroke(AndroidUtilities.dp(2), Theme.getColor(Theme.key_actionBarDefault));
            ring.setColor(Color.TRANSPARENT);
            setBackground(ring);

            setOnClickListener(v -> { if (onTap != null) onTap.run(); });
        }
    }
}
