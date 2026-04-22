package xyz.nextalone.nagram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
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
 * Split-chat layout.
 *
 * Manages up to 2 visible panes + N minimized icon-tabs.
 * Panes are separated by a draggable SplitDividerView.
 * When a 3rd chat opens, the oldest pane demotes to a mini icon-tab at the bottom.
 * Tapping a mini icon swaps it back with a spring animation.
 */
public class SplitChatLayout extends FrameLayout {

    // ── Pane models ──────────────────────────────────────────────────────────

    static class SplitPane {
        long dialogId;
        ActionBarLayout actionBarLayout;
        FrameLayout container;
        float weight = 0.5f; // horizontal weight [0.3, 0.7]
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final ArrayList<SplitPane> visiblePanes = new ArrayList<>();   // max 2
    private final ArrayList<MiniPaneTab> miniTabs    = new ArrayList<>();  // minimized

    private SplitDividerView dividerView;
    private FrameLayout leftContainer;
    private FrameLayout rightContainer;
    private HorizontalScrollView miniTabScrollView;
    private LinearLayout miniTabBar;
    private LaunchActivity launchActivity;

    private ValueAnimator openAnimator;
    private boolean isAttached = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public SplitChatLayout(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
    }

    // ── Attach / Detach ───────────────────────────────────────────────────────

    /** Attaches this layout on top of the normal LaunchActivity content and initialises pane 1. */
    public void attachToActivity(LaunchActivity activity, long firstDialogId) {
        this.launchActivity = activity;
        if (isAttached) {
            // Already showing — open a second pane directly
            openDialogInNextPane(firstDialogId);
            return;
        }
        isAttached = true;
        buildLayout(activity.getApplicationContext());
        activity.frameLayout.addView(this, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Pane 1 = current chat (create a new ActionBarLayout wrapping currentDialogId)
        addPane(firstDialogId, true);

        // Animate entry
        setAlpha(0f);
        setScaleX(0.97f);
        setScaleY(0.97f);
        animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(300)
                .setInterpolator(AndroidUtilities.overshootInterpolator)
                .start();

        // Show picker so user can choose pane 2
        showChatPicker();
    }

    private void buildLayout(Context ctx) {
        // Background scrim
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        // Left pane container
        leftContainer = new FrameLayout(ctx);
        addView(leftContainer);

        // Divider
        dividerView = new SplitDividerView(ctx);
        dividerView.setOnDividerDragListener(this::onDividerDragged);
        dividerView.setOnDividerDoubleTapListener(this::snapDividerToCenter);
        dividerView.setOnDividerLongPressListener(this::closeSplit);
        addView(dividerView);

        // Right pane container
        rightContainer = new FrameLayout(ctx);
        addView(rightContainer);

        // Mini-tab bar (bottom)
        miniTabScrollView = new HorizontalScrollView(ctx);
        miniTabScrollView.setHorizontalScrollBarEnabled(false);
        miniTabScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        miniTabBar = new LinearLayout(ctx);
        miniTabBar.setOrientation(LinearLayout.HORIZONTAL);
        miniTabBar.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6));
        miniTabScrollView.addView(miniTabBar, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        addView(miniTabScrollView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, 56, Gravity.BOTTOM | Gravity.START,
                4, 0, 4, 8));
        miniTabScrollView.setVisibility(GONE);

        // Close-split FAB (top-right corner)
        TextView closeFab = new TextView(ctx);
        closeFab.setText(LocaleController.getString("SplitChatClose", R.string.SplitChatClose));
        closeFab.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
        closeFab.setTextSize(12);
        closeFab.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6),
                AndroidUtilities.dp(10), AndroidUtilities.dp(6));
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(Theme.getColor(Theme.key_actionBarDefault));
        closeBg.setCornerRadius(AndroidUtilities.dp(16));
        closeFab.setBackground(closeBg);
        closeFab.setOnClickListener(v -> closeSplit());
        addView(closeFab, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.END, 0, 8, 8, 0));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        layoutPanes();
    }

    private void layoutPanes() {
        int totalW = getMeasuredWidth();
        int totalH = getMeasuredHeight();
        if (totalW == 0 || totalH == 0) return;

        int divW = AndroidUtilities.dp(4);
        float leftWeight = visiblePanes.size() > 0 ? visiblePanes.get(0).weight : 0.5f;

        int leftW  = (int) ((totalW - divW) * leftWeight);
        int rightW = totalW - divW - leftW;

        // Left pane
        if (leftContainer != null) {
            leftContainer.layout(0, 0, leftW, totalH);
        }
        // Divider
        if (dividerView != null) {
            dividerView.layout(leftW, 0, leftW + divW, totalH);
        }
        // Right pane
        if (rightContainer != null) {
            rightContainer.layout(leftW + divW, 0, totalW, totalH);
        }
    }

    // ── Pane management ───────────────────────────────────────────────────────

    private void addPane(long dialogId, boolean isFirst) {
        SplitPane pane = new SplitPane();
        pane.dialogId = dialogId;
        pane.weight = 0.5f;

        Context ctx = getContext();

        pane.actionBarLayout = new ActionBarLayout(launchActivity, false);
        pane.container = isFirst ? leftContainer : rightContainer;

        // Build ChatActivity for this dialog
        Bundle args = new Bundle();
        if (dialogId > 0) {
            args.putLong("user_id", dialogId);
        } else {
            args.putLong("chat_id", -dialogId);
        }
        ChatActivity chatActivity = new ChatActivity(args);
        chatActivity.isInsideContainer = true;

        pane.container.addView(pane.actionBarLayout,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        pane.actionBarLayout.addFragmentToStack(chatActivity, -1);
        pane.actionBarLayout.onResume();

        visiblePanes.add(pane);
        dividerView.setVisibility(visiblePanes.size() >= 2 ? VISIBLE : GONE);
        requestLayout();
        invalidate();
    }

    /** Called after the user picks a chat in the picker. */
    public void openDialogInNextPane(long dialogId) {
        if (visiblePanes.size() < 2) {
            // Slide pane 2 in
            addPane(dialogId, false);
            animatePaneIn(rightContainer);
        } else {
            // 3rd chat: demote pane 0 to mini, shift pane 1 to left, open pane 2 on right
            demoteOldestPane();
            addPane(dialogId, false);
            animatePaneIn(rightContainer);
        }
    }

    private void demoteOldestPane() {
        if (visiblePanes.isEmpty()) return;
        SplitPane old = visiblePanes.remove(0);

        // Grab avatar/title for the mini tab
        String title = getTitleForDialog(old.dialogId);

        // Animate old pane shrinking to a dot
        old.container.animate()
                .scaleX(0.3f).scaleY(0.3f).alpha(0f)
                .setDuration(280)
                .setInterpolator(CubicBezierInterpolator.EASE_BOTH)
                .withEndAction(() -> {
                    if (old.actionBarLayout != null) {
                        old.actionBarLayout.onPause();
                    }
                    old.container.removeAllViews();
                    addMiniTab(old.dialogId, title);
                    // Move pane[0] (was pane[1]) to left
                    if (!visiblePanes.isEmpty()) {
                        SplitPane remaining = visiblePanes.get(0);
                        leftContainer.removeAllViews();
                        rightContainer.removeAllViews();
                        leftContainer.addView(remaining.actionBarLayout,
                                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                        remaining.container = leftContainer;
                    }
                    requestLayout();
                }).start();
    }

    private void addMiniTab(long dialogId, String title) {
        MiniPaneTab tab = new MiniPaneTab(getContext(), dialogId, title, () -> restoreMiniTab(dialogId));
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
        SpringAnimation spring = new SpringAnimation(new FloatValueHolder(0f));
        spring.setSpring(new SpringForce(1000f).setStiffness(SpringForce.STIFFNESS_MEDIUM)
                .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY));
        spring.addUpdateListener((anim, val, vel) -> {
            float p = val / 1000f;
            tab.setScaleX(p);
            tab.setScaleY(p);
            tab.setAlpha(p);
        });
        spring.start();
    }

    private void restoreMiniTab(long dialogId) {
        MiniPaneTab tab = null;
        for (MiniPaneTab t : miniTabs) {
            if (t.dialogId == dialogId) { tab = t; break; }
        }
        if (tab == null) return;
        miniTabs.remove(tab);
        miniTabBar.removeView(tab);
        if (miniTabs.isEmpty()) {
            miniTabScrollView.setVisibility(GONE);
        }
        // Demote the current pane[1] to mini, then restore this one in pane[1]
        if (visiblePanes.size() >= 2) {
            demoteOldestPane();
        }
        openDialogInNextPane(dialogId);
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    // ── Animations ────────────────────────────────────────────────────────────

    private void animatePaneIn(FrameLayout pane) {
        pane.setTranslationX(pane.getMeasuredWidth() > 0 ? pane.getMeasuredWidth() : 400f);
        pane.setAlpha(0f);
        SpringAnimation springX = new SpringAnimation(new FloatValueHolder(pane.getMeasuredWidth() > 0 ? pane.getMeasuredWidth() : 400f));
        springX.setSpring(new SpringForce(0f)
                .setStiffness(SpringForce.STIFFNESS_MEDIUM)
                .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY));
        springX.addUpdateListener((a, val, vel) -> pane.setTranslationX(val));
        springX.addEndListener((a, canceled, val, vel) -> pane.setTranslationX(0f));
        springX.start();
        pane.animate().alpha(1f).setDuration(200).start();
    }

    // ── Divider callbacks ─────────────────────────────────────────────────────

    private void onDividerDragged(float rawX) {
        int totalW = getMeasuredWidth();
        if (totalW <= 0 || visiblePanes.isEmpty()) return;
        float weight = rawX / (float) totalW;
        weight = Math.max(0.30f, Math.min(0.70f, weight));
        if (!visiblePanes.isEmpty()) visiblePanes.get(0).weight = weight;
        requestLayout();
    }

    private void snapDividerToCenter() {
        if (visiblePanes.isEmpty()) return;
        float current = visiblePanes.get(0).weight;
        ValueAnimator anim = ValueAnimator.ofFloat(current, 0.5f);
        anim.setDuration(280);
        anim.setInterpolator(AndroidUtilities.overshootInterpolator);
        anim.addUpdateListener(a -> {
            if (!visiblePanes.isEmpty()) visiblePanes.get(0).weight = (float) a.getAnimatedValue();
            requestLayout();
        });
        anim.start();
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    // ── Chat picker ───────────────────────────────────────────────────────────

    private void showChatPicker() {
        if (launchActivity == null) return;
        Bundle args = new Bundle();
        args.putInt("dialogsType", 1); // forward/picker mode
        args.putBoolean("resetDelegate", false);
        DialogsActivity picker = new DialogsActivity(args);
        picker.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids != null && !dids.isEmpty()) {
                long did = dids.get(0).dialogId;
                SplitChatManager.getInstance().openDialogInSplit(did);
            }
            fragment.finishFragment();
            return true;
        });
        launchActivity.actionBarLayout.presentFragment(new INavigationLayout.NavigationParams(picker));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getTitleForDialog(long dialogId) {
        try {
            int account = UserConfig.selectedAccount;
            if (dialogId > 0) {
                TLRPC.User u = MessagesController.getInstance(account).getUser(dialogId);
                if (u != null) return u.first_name;
            } else {
                TLRPC.Chat c = MessagesController.getInstance(account).getChat(-dialogId);
                if (c != null) return c.title;
            }
        } catch (Exception ignore) {}
        return "Chat";
    }

    // ── Close / lifecycle ─────────────────────────────────────────────────────

    public void closeSplit() {
        animate().alpha(0f).scaleX(0.96f).scaleY(0.96f)
                .setDuration(250)
                .withEndAction(() -> {
                    onDestroy();
                    if (getParent() instanceof ViewGroup) {
                        ((ViewGroup) getParent()).removeView(this);
                    }
                    SplitChatManager.getInstance().closeSplit();
                }).start();
    }

    public void onPause() {
        for (SplitPane p : visiblePanes) {
            if (p.actionBarLayout != null) p.actionBarLayout.onPause();
        }
    }

    public void onResume() {
        for (SplitPane p : visiblePanes) {
            if (p.actionBarLayout != null) p.actionBarLayout.onResume();
        }
    }

    public void onDestroy() {
        for (SplitPane p : visiblePanes) {
            if (p.actionBarLayout != null) {
                p.actionBarLayout.onPause();
            }
        }
        visiblePanes.clear();
        miniTabs.clear();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Inner classes
    // ══════════════════════════════════════════════════════════════════════════

    // ── Draggable divider ─────────────────────────────────────────────────────

    public static class SplitDividerView extends View {
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF handleRect = new RectF();

        interface DividerDragListener   { void onDrag(float rawX); }
        interface DividerDoubleTapListener { void onDoubleTap(); }
        interface DividerLongPressListener { void onLongPress(); }

        private DividerDragListener dragListener;
        private DividerDoubleTapListener doubleTapListener;
        private DividerLongPressListener longPressListener;

        private float lastDownX;
        private long lastTapTime;
        private boolean isDragging;
        private final Runnable longPressRunnable = () -> {
            if (!isDragging && longPressListener != null) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                longPressListener.onLongPress();
            }
        };

        // pressed scale spring
        private float pressProgress = 0f;
        private SpringAnimation pressSpring;

        public SplitDividerView(Context ctx) {
            super(ctx);
            trackPaint.setColor(0x22FFFFFF);
            handlePaint.setColor(Theme.getColor(Theme.key_actionBarDefault));
            handlePaint.setAlpha(200);
        }

        public void setOnDividerDragListener(DividerDragListener l)         { dragListener = l; }
        public void setOnDividerDoubleTapListener(DividerDoubleTapListener l){ doubleTapListener = l; }
        public void setOnDividerLongPressListener(DividerLongPressListener l){ longPressListener = l; }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            canvas.drawRect(0, 0, w, h, trackPaint);
            // Animated handle pill
            float hh = AndroidUtilities.dp(32) + pressProgress * AndroidUtilities.dp(8);
            float hw = AndroidUtilities.dp(4);
            float cx = w / 2f, cy = h / 2f;
            handleRect.set(cx - hw / 2f, cy - hh / 2f, cx + hw / 2f, cy + hh / 2f);
            canvas.drawRoundRect(handleRect, AndroidUtilities.dp(3), AndroidUtilities.dp(3), handlePaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getRawX();
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastDownX = x;
                    isDragging = false;
                    postDelayed(longPressRunnable, 500);
                    setPressSpring(1f);
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
                    setPressSpring(0f);
                    if (!isDragging) {
                        long now = System.currentTimeMillis();
                        if (now - lastTapTime < 350 && doubleTapListener != null) {
                            doubleTapListener.onDoubleTap();
                        }
                        lastTapTime = now;
                    }
                    isDragging = false;
                    break;
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(longPressRunnable);
                    setPressSpring(0f);
                    isDragging = false;
                    break;
            }
            return true;
        }

        private void setPressSpring(float target) {
            if (pressSpring != null) pressSpring.cancel();
            pressSpring = new SpringAnimation(new FloatValueHolder(pressProgress * 1000f));
            pressSpring.setSpring(new SpringForce(target * 1000f)
                    .setStiffness(SpringForce.STIFFNESS_MEDIUM)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY));
            pressSpring.addUpdateListener((a, val, vel) -> {
                pressProgress = val / 1000f;
                invalidate();
            });
            pressSpring.start();
        }
    }

    // ── Mini pane tab icon ────────────────────────────────────────────────────

    public static class MiniPaneTab extends FrameLayout {
        final long dialogId;
        private final Runnable onTap;

        public MiniPaneTab(Context ctx, long dialogId, String title, Runnable onTap) {
            super(ctx);
            this.dialogId = dialogId;
            this.onTap = onTap;

            setLayoutParams(new LinearLayout.LayoutParams(
                    AndroidUtilities.dp(44), AndroidUtilities.dp(44)));
            ((LinearLayout.LayoutParams) getLayoutParams()).setMargins(
                    AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);

            // Avatar circle
            BackupImageView avatar = new BackupImageView(ctx);
            avatar.setRoundRadius(AndroidUtilities.dp(22));
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(0, title, null);
            avatar.setImageDrawable(avatarDrawable);

            // Try to load real avatar
            try {
                int account = UserConfig.selectedAccount;
                if (dialogId > 0) {
                    TLRPC.User u = MessagesController.getInstance(account).getUser(dialogId);
                    if (u != null) {
                        avatarDrawable.setInfo(u);
                        ImageLocation loc = ImageLocation.getForUser(u, ImageLocation.TYPE_SMALL);
                        avatar.setImage(loc, "50_50", avatarDrawable, u);
                    }
                } else {
                    TLRPC.Chat c = MessagesController.getInstance(account).getChat(-dialogId);
                    if (c != null) {
                        avatarDrawable.setInfo(c);
                        ImageLocation loc = ImageLocation.getForChat(c, ImageLocation.TYPE_SMALL);
                        avatar.setImage(loc, "50_50", avatarDrawable, c);
                    }
                }
            } catch (Exception ignore) {}

            addView(avatar, LayoutHelper.createFrame(40, 40, Gravity.CENTER));

            // Ring border
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setStroke(AndroidUtilities.dp(2), Theme.getColor(Theme.key_actionBarDefault));
            ring.setColor(Color.TRANSPARENT);
            setBackground(ring);

            setOnClickListener(v -> {
                if (onTap != null) onTap.run();
            });
            setOnLongClickListener(v -> {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                return true;
            });
        }
    }
}
