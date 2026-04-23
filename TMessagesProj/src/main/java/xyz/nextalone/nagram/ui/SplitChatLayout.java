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

public class SplitChatLayout extends FrameLayout {

    // ── Pane model ────────────────────────────────────────────────────────────

    static class SplitPane {
        long dialogId;
        ActionBarLayout paneLayout;
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

    private long originDialogId;
    private boolean isAttached = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public SplitChatLayout(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public void setOriginDialogId(long id) { this.originDialogId = id; }

    public void attachToActivity(LaunchActivity activity, long firstDialogId) {
        this.launchActivity  = activity;
        this.originDialogId  = firstDialogId;
        if (isAttached) return;
        isAttached = true;

        // 1. Build skeleton and add to window hierarchy FIRST
        buildLayout(activity);
        activity.frameLayout.addView(this,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // 2. Left pane = current chat
        addPane(firstDialogId, true);

        // 3. Right pane = full dialogs list — user taps any chat to open it here
        addDialogListPane();

        // 4. Entry animation
        setAlpha(0f);
        setScaleX(0.96f);
        setScaleY(0.96f);
        animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(300)
                .setInterpolator(CubicBezierInterpolator.DEFAULT)
                .start();
    }

    // ── Build skeleton ────────────────────────────────────────────────────────

    private void buildLayout(Context ctx) {
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        leftContainer = new FrameLayout(ctx);
        addView(leftContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        dividerView = new SplitDividerView(ctx);
        dividerView.setOnDividerDragListener(this::onDividerDragged);
        dividerView.setOnDividerDoubleTapListener(this::snapDividerToCenter);
        dividerView.setOnDividerLongPressListener(this::closeSplit);
        addView(dividerView);

        rightContainer = new FrameLayout(ctx);
        addView(rightContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Mini-tab strip (bottom-left, hidden until needed)
        miniTabScrollView = new HorizontalScrollView(ctx);
        miniTabScrollView.setHorizontalScrollBarEnabled(false);
        miniTabScrollView.setOverScrollMode(OVER_SCROLL_NEVER);
        miniTabBar = new LinearLayout(ctx);
        miniTabBar.setOrientation(LinearLayout.HORIZONTAL);
        miniTabBar.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6));
        miniTabScrollView.addView(miniTabBar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
        addView(miniTabScrollView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 56, Gravity.BOTTOM | Gravity.START, 4, 0, 4, 8));
        miniTabScrollView.setVisibility(GONE);

        // Close button (top-right)
        TextView closeFab = new TextView(ctx);
        closeFab.setText(LocaleController.getString("SplitChatClose", R.string.SplitChatClose));
        closeFab.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
        closeFab.setTextSize(12);
        closeFab.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(5), AndroidUtilities.dp(10), AndroidUtilities.dp(5));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.getColor(Theme.key_actionBarDefault));
        bg.setCornerRadius(AndroidUtilities.dp(14));
        closeFab.setBackground(bg);
        closeFab.setOnClickListener(v -> closeSplit());
        addView(closeFab, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.END, 0, 8, 8, 0));
    }

    // ── Layout — manual side-by-side panes ───────────────────────────────────

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // Let FrameLayout lay out mini-tab bar and close button using their LayoutParams gravity
        super.onLayout(changed, l, t, r, b);
        // Override pane positions manually
        int totalW = r - l, totalH = b - t;
        if (totalW == 0 || leftContainer == null) return;
        int divW = AndroidUtilities.dp(4);
        float leftWeight = visiblePanes.isEmpty() ? 0.5f : visiblePanes.get(0).weight;
        int leftW = Math.max(0, Math.min(totalW - divW, (int) ((totalW - divW) * leftWeight)));
        leftContainer.layout(0, 0, leftW, totalH);
        if (dividerView != null) dividerView.layout(leftW, 0, leftW + divW, totalH);
        rightContainer.layout(leftW + divW, 0, totalW, totalH);
    }

    private void measureAndLayoutExtras(int totalW, int totalH) {
        // no-op: handled by FrameLayout super.onLayout via LayoutParams gravity
    }

    // ── Pane management ───────────────────────────────────────────────────────

    /**
     * Creates a dedicated ActionBarLayout + ChatActivity for a pane.
     * The layout is added to the container FIRST (so it's in the window),
     * then the fragment is pushed to it.
     */
    private void addPane(long dialogId, boolean isLeft) {
        SplitPane pane = new SplitPane();
        pane.dialogId  = dialogId;
        pane.container = isLeft ? leftContainer : rightContainer;

        // 1. Create ActionBarLayout and add to container (in window hierarchy)
        ActionBarLayout paneLayout = new ActionBarLayout(launchActivity, false);
        pane.paneLayout = paneLayout;
        pane.container.removeAllViews();
        pane.container.addView(paneLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // 2. Build ChatActivity args
        Bundle args = new Bundle();
        if (dialogId > 0) {
            args.putLong("user_id", dialogId);
        } else {
            args.putLong("chat_id", -dialogId);
        }

        // 3. Create and push ChatActivity
        ChatActivity chat = new ChatActivity(args);
        chat.isInsideContainer = true;

        try {
            paneLayout.addFragmentToStack(chat, -1);
            paneLayout.onResume();
        } catch (Exception e) {
            android.util.Log.e("SplitChat", "addPane failed id=" + dialogId, e);
            pane.container.removeAllViews();
            return;
        }

        visiblePanes.add(pane);
        if (dividerView != null)
            dividerView.setVisibility(visiblePanes.size() >= 2 ? VISIBLE : GONE);
        requestLayout();
    }

    /**
     * Right pane shows the full Dialogs list. Tapping a chat opens it in this pane.
     */
    private void addDialogListPane() {
        SplitPane pane = new SplitPane();
        pane.dialogId  = 0;
        pane.container = rightContainer;

        ActionBarLayout paneLayout = new ActionBarLayout(launchActivity, false);
        pane.paneLayout = paneLayout;
        rightContainer.removeAllViews();
        rightContainer.addView(paneLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        Bundle args = new Bundle();
        args.putBoolean("onlySelect", false);
        DialogsActivity dialogs = new DialogsActivity(args);
        // When user taps a dialog, open it inside this pane
        dialogs.setDelegate((fragment, dids, message, param, notify, scheduleDate, period, topicsFrag) -> {
            if (dids != null && !dids.isEmpty()) {
                long did = dids.get(0).dialogId;
                openChatInRightPane(did);
            }
            return true;
        });

        try {
            paneLayout.addFragmentToStack(dialogs, -1);
            paneLayout.onResume();
        } catch (Exception e) {
            android.util.Log.e("SplitChat", "addDialogListPane failed", e);
            rightContainer.removeAllViews();
            return;
        }

        visiblePanes.add(pane);
        if (dividerView != null)
            dividerView.setVisibility(VISIBLE);
        requestLayout();
    }

    /** Opens a ChatActivity inside the right pane's ActionBarLayout. */
    private void openChatInRightPane(long dialogId) {
        if (visiblePanes.size() < 2) return;
        SplitPane rightPane = visiblePanes.get(1);
        if (rightPane.paneLayout == null) return;

        Bundle args = new Bundle();
        if (dialogId > 0) args.putLong("user_id", dialogId);
        else args.putLong("chat_id", -dialogId);

        ChatActivity chat = new ChatActivity(args);
        chat.isInsideContainer = true;
        rightPane.paneLayout.presentFragment(new INavigationLayout.NavigationParams(chat).setRemoveLast(false));
        rightPane.dialogId = dialogId;
    }

    /** Called from SplitChatManager when a 3rd chat is requested from outside. */
    public void openDialogInNextPane(long dialogId) {
        if (visiblePanes.size() < 2) {
            addPane(dialogId, false);
            animatePaneIn(rightContainer);
        } else {
            demoteOldestPane();
            AndroidUtilities.runOnUIThread(() -> {
                addPane(dialogId, false);
                animatePaneIn(rightContainer);
            }, 300);
        }
    }

    // ── Demote to mini icon ───────────────────────────────────────────────────

    private void demoteOldestPane() {
        if (visiblePanes.isEmpty()) return;
        SplitPane old = visiblePanes.remove(0);
        String title = getTitleForDialog(old.dialogId);

        old.container.animate()
                .scaleX(0.3f).scaleY(0.3f).alpha(0f)
                .setDuration(260)
                .setInterpolator(CubicBezierInterpolator.EASE_BOTH)
                .withEndAction(() -> {
                    try { if (old.paneLayout != null) old.paneLayout.onPause(); } catch (Exception ignore) {}
                    old.container.removeAllViews();
                    old.container.setScaleX(1f);
                    old.container.setScaleY(1f);
                    old.container.setAlpha(1f);
                    addMiniTab(old.dialogId, title);

                    // Shift remaining pane to left container
                    if (!visiblePanes.isEmpty()) {
                        SplitPane rem = visiblePanes.get(0);
                        if (rem.container == rightContainer) {
                            rightContainer.removeView(rem.paneLayout);
                            leftContainer.removeAllViews();
                            leftContainer.addView(rem.paneLayout,
                                    LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                            rem.container = leftContainer;
                        }
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
            miniTabScrollView.animate().alpha(1f).setDuration(180).start();
        }
        tab.setScaleX(0f); tab.setScaleY(0f); tab.setAlpha(0f);
        new SpringAnimation(new FloatValueHolder(0f))
                .setSpring(new SpringForce(1000f)
                        .setStiffness(SpringForce.STIFFNESS_MEDIUM)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY))
                .addUpdateListener((a, val, vel) -> {
                    float p = val / 1000f;
                    tab.setScaleX(p); tab.setScaleY(p); tab.setAlpha(p);
                }).start();
    }

    private void restoreMiniTab(long dialogId) {
        MiniPaneTab found = null;
        for (MiniPaneTab t : miniTabs) if (t.dialogId == dialogId) { found = t; break; }
        if (found == null) return;
        final MiniPaneTab tab = found;
        miniTabs.remove(tab);
        miniTabBar.removeView(tab);
        if (miniTabs.isEmpty()) miniTabScrollView.setVisibility(GONE);
        demoteOldestPane();
        AndroidUtilities.runOnUIThread(() -> {
            addPane(dialogId, false);
            animatePaneIn(rightContainer);
        }, 300);
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    // ── Animations ────────────────────────────────────────────────────────────

    private void animatePaneIn(FrameLayout pane) {
        pane.setAlpha(0f);
        pane.setTranslationX(AndroidUtilities.dp(120));
        new SpringAnimation(new FloatValueHolder(AndroidUtilities.dp(120)))
                .setSpring(new SpringForce(0f)
                        .setStiffness(SpringForce.STIFFNESS_MEDIUM)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY))
                .addUpdateListener((a, val, vel) -> pane.setTranslationX(val))
                .addEndListener((a, canceled, val, vel) -> pane.setTranslationX(0))
                .start();
        pane.animate().alpha(1f).setDuration(200).start();
    }

    // ── Divider callbacks ─────────────────────────────────────────────────────

    private void onDividerDragged(float rawX) {
        int totalW = getWidth();
        if (totalW <= 0 || visiblePanes.isEmpty()) return;
        float w = Math.max(0.25f, Math.min(0.75f, rawX / totalW));
        visiblePanes.get(0).weight = w;
        requestLayout();
    }

    private void snapDividerToCenter() {
        if (visiblePanes.isEmpty()) return;
        float cur = visiblePanes.get(0).weight;
        ValueAnimator a = ValueAnimator.ofFloat(cur, 0.5f);
        a.setDuration(260);
        a.setInterpolator(CubicBezierInterpolator.DEFAULT);
        a.addUpdateListener(va -> {
            if (!visiblePanes.isEmpty()) visiblePanes.get(0).weight = (float) va.getAnimatedValue();
            requestLayout();
        });
        a.start();
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
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

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void closeSplit() {
        isAttached = false;
        animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(220)
                .withEndAction(() -> {
                    onDestroy();
                    if (getParent() instanceof ViewGroup)
                        ((ViewGroup) getParent()).removeView(this);
                    SplitChatManager.getInstance().onSplitClosed();
                }).start();
    }

    public void onPause() {
        for (SplitPane p : visiblePanes) {
            try { if (p.paneLayout != null) p.paneLayout.onPause(); } catch (Exception ignore) {}
        }
    }

    public void onResume() {
        for (SplitPane p : visiblePanes) {
            try { if (p.paneLayout != null) p.paneLayout.onResume(); } catch (Exception ignore) {}
        }
    }

    public void onDestroy() {
        for (SplitPane p : visiblePanes) {
            try { if (p.paneLayout != null) p.paneLayout.onPause(); } catch (Exception ignore) {}
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

        private DividerDragListener      drag;
        private DividerDoubleTapListener dbl;
        private DividerLongPressListener lp;

        private final Paint track  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handle = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect   = new RectF();

        private float downX, pressP;
        private long lastTap;
        private boolean dragging;
        private SpringAnimation pressSpring;

        private final Runnable lpRunnable = () -> {
            if (!dragging && lp != null) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                lp.onLongPress();
            }
        };

        public SplitDividerView(Context ctx) {
            super(ctx);
            track.setColor(0x28000000);
            handle.setColor(Theme.getColor(Theme.key_actionBarDefault));
        }

        public void setOnDividerDragListener(DividerDragListener l)          { drag = l; }
        public void setOnDividerDoubleTapListener(DividerDoubleTapListener l) { dbl = l; }
        public void setOnDividerLongPressListener(DividerLongPressListener l) { lp = l; }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), track);
            float hh = AndroidUtilities.dp(28) + pressP * AndroidUtilities.dp(12);
            float hw = AndroidUtilities.dp(3);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            rect.set(cx - hw / 2f, cy - hh / 2f, cx + hw / 2f, cy + hh / 2f);
            canvas.drawRoundRect(rect, AndroidUtilities.dp(3), AndroidUtilities.dp(3), handle);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getRawX();
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = x; dragging = false;
                    postDelayed(lpRunnable, 480);
                    spring(1f); break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(x - downX) > AndroidUtilities.dp(4)) {
                        dragging = true; removeCallbacks(lpRunnable);
                    }
                    if (dragging && drag != null) drag.onDrag(x);
                    break;
                case MotionEvent.ACTION_UP:
                    removeCallbacks(lpRunnable); spring(0f);
                    if (!dragging) {
                        long now = System.currentTimeMillis();
                        if (now - lastTap < 340 && dbl != null) dbl.onDoubleTap();
                        lastTap = now;
                    }
                    dragging = false; break;
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(lpRunnable); spring(0f); dragging = false; break;
            }
            return true;
        }

        private void spring(float target) {
            if (pressSpring != null) pressSpring.cancel();
            pressSpring = new SpringAnimation(new FloatValueHolder(pressP * 1000f))
                    .setSpring(new SpringForce(target * 1000f)
                            .setStiffness(SpringForce.STIFFNESS_MEDIUM)
                            .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY));
            pressSpring.addUpdateListener((a, val, vel) -> { pressP = val / 1000f; invalidate(); });
            pressSpring.start();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Mini Pane Icon Tab
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

            BackupImageView av = new BackupImageView(ctx);
            av.setRoundRadius(AndroidUtilities.dp(22));
            AvatarDrawable ad = new AvatarDrawable();

            try {
                int acc = UserConfig.selectedAccount;
                if (dialogId > 0) {
                    TLRPC.User u = MessagesController.getInstance(acc).getUser(dialogId);
                    if (u != null) { ad.setInfo(u); av.setImage(ImageLocation.getForUser(u, ImageLocation.TYPE_SMALL), "50_50", ad, u); }
                    else { ad.setInfo(0, title, null); av.setImageDrawable(ad); }
                } else {
                    TLRPC.Chat c = MessagesController.getInstance(acc).getChat(-dialogId);
                    if (c != null) { ad.setInfo(c); av.setImage(ImageLocation.getForChat(c, ImageLocation.TYPE_SMALL), "50_50", ad, c); }
                    else { ad.setInfo(0, title, null); av.setImageDrawable(ad); }
                }
            } catch (Exception e) { ad.setInfo(0, title, null); av.setImageDrawable(ad); }

            addView(av, LayoutHelper.createFrame(40, 40, Gravity.CENTER));

            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setStroke(AndroidUtilities.dp(2), Theme.getColor(Theme.key_actionBarDefault));
            ring.setColor(Color.TRANSPARENT);
            setBackground(ring);

            setOnClickListener(v -> { if (onTap != null) onTap.run(); });
        }
    }
}
