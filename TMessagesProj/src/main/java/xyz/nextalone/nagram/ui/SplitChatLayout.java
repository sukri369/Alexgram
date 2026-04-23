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

    static class SplitPane {
        long dialogId;
        ActionBarLayout paneLayout;
        FrameLayout container;
        float weight = 0.5f;
    }

    private final ArrayList<SplitPane> visiblePanes = new ArrayList<>();
    private final ArrayList<MiniPaneTab> miniTabs    = new ArrayList<>();

    private View dividerView;
    private FrameLayout topContainer;    // portrait-top / landscape-left
    private FrameLayout bottomContainer; // portrait-bottom / landscape-right
    private HorizontalScrollView miniTabScrollView;
    private LinearLayout miniTabBar;
    private LaunchActivity launchActivity;
    private long originDialogId;
    private boolean isAttached = false;

    public SplitChatLayout(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
    }

    public void setOriginDialogId(long id) { this.originDialogId = id; }

    // ── Entry ─────────────────────────────────────────────────────────────────

    public void attachToActivity(LaunchActivity activity, long firstDialogId) {
        this.launchActivity = activity;
        this.originDialogId = firstDialogId;
        if (isAttached) return;
        isAttached = true;

        buildLayout(activity);
        activity.frameLayout.addView(this,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Defer fragment attachment until after first layout pass (so panes have real size)
        post(() -> {
            addChatPane(originDialogId, true);
            addDialogListPane();
        });

        // Entry animation
        setAlpha(0f); setScaleX(0.97f); setScaleY(0.97f);
        animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(280).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private void buildLayout(Context ctx) {
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        topContainer = new FrameLayout(ctx);
        addView(topContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        dividerView = new SplitDividerView(ctx,
                this::onDividerDragged, this::snapDividerToCenter, this::closeSplit);
        addView(dividerView);

        bottomContainer = new FrameLayout(ctx);
        addView(bottomContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Mini-tab strip
        miniTabScrollView = new HorizontalScrollView(ctx);
        miniTabScrollView.setHorizontalScrollBarEnabled(false);
        miniTabScrollView.setOverScrollMode(OVER_SCROLL_NEVER);
        miniTabBar = new LinearLayout(ctx);
        miniTabBar.setOrientation(LinearLayout.HORIZONTAL);
        miniTabBar.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(4), AndroidUtilities.dp(6), AndroidUtilities.dp(4));
        miniTabScrollView.addView(miniTabBar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
        addView(miniTabScrollView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 52, Gravity.BOTTOM | Gravity.START, 4, 0, 4, 8));
        miniTabScrollView.setVisibility(GONE);

        // Close button
        TextView closeBtn = new TextView(ctx);
        closeBtn.setText(LocaleController.getString("SplitChatClose", R.string.SplitChatClose));
        closeBtn.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
        closeBtn.setTextSize(12);
        closeBtn.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(5), AndroidUtilities.dp(10), AndroidUtilities.dp(5));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.getColor(Theme.key_actionBarDefault));
        bg.setCornerRadius(AndroidUtilities.dp(14));
        closeBtn.setBackground(bg);
        closeBtn.setOnClickListener(v -> closeSplit());
        addView(closeBtn, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.END, 0, 8, 8, 0));
    }

    /** True = portrait (top/bottom split). False = landscape (left/right split). */
    private boolean isPortrait() {
        return getWidth() <= getHeight();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        layoutPanes();
    }

    private void layoutPanes() {
        int W = getWidth(), H = getHeight();
        if (W == 0 || topContainer == null) return;
        int divPx = AndroidUtilities.dp(4);
        float w = visiblePanes.isEmpty() ? 0.5f : visiblePanes.get(0).weight;
        w = Math.max(0.25f, Math.min(0.75f, w));

        if (isPortrait()) {
            // TOP / BOTTOM split — horizontal divider
            int topH = (int) ((H - divPx) * w);
            topContainer.layout(0, 0, W, topH);
            dividerView.layout(0, topH, W, topH + divPx);
            bottomContainer.layout(0, topH + divPx, W, H);
        } else {
            // LEFT / RIGHT split — vertical divider
            int leftW = (int) ((W - divPx) * w);
            topContainer.layout(0, 0, leftW, H);
            dividerView.layout(leftW, 0, leftW + divPx, H);
            bottomContainer.layout(leftW + divPx, 0, W, H);
        }
    }

    // ── Pane creation ─────────────────────────────────────────────────────────

    private void addChatPane(long dialogId, boolean isTop) {
        SplitPane pane = new SplitPane();
        pane.dialogId  = dialogId;
        pane.container = isTop ? topContainer : bottomContainer;

        ActionBarLayout layout = new ActionBarLayout(launchActivity, false);
        pane.paneLayout = layout;
        pane.container.removeAllViews();
        pane.container.addView(layout,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        Bundle args = new Bundle();
        if (dialogId > 0) args.putLong("user_id", dialogId);
        else              args.putLong("chat_id", -dialogId);

        ChatActivity chat = new ChatActivity(args);
        chat.isInsideContainer = true;

        // post again to ensure ActionBarLayout is measured before presenting
        layout.post(() -> {
            try {
                layout.presentFragment(
                        new INavigationLayout.NavigationParams(chat).setNoAnimation(true));
                layout.onResume();
            } catch (Exception e) {
                android.util.Log.e("SplitChat", "addChatPane failed id=" + dialogId, e);
            }
        });

        visiblePanes.add(pane);
        updateDividerVisibility();
        requestLayout();
    }

    private void addDialogListPane() {
        SplitPane pane = new SplitPane();
        pane.dialogId  = 0;
        pane.container = bottomContainer;

        ActionBarLayout layout = new ActionBarLayout(launchActivity, false);
        pane.paneLayout = layout;
        pane.container.removeAllViews();
        pane.container.addView(layout,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        DialogsActivity dialogs = new DialogsActivity(new Bundle());
        dialogs.setDelegate((frag, dids, msg, param, notify, date, period, topicsFrag) -> {
            if (dids != null && !dids.isEmpty()) {
                openChatInBottom(dids.get(0).dialogId);
            }
            return true;
        });

        layout.post(() -> {
            try {
                layout.presentFragment(
                        new INavigationLayout.NavigationParams(dialogs).setNoAnimation(true));
                layout.onResume();
            } catch (Exception e) {
                android.util.Log.e("SplitChat", "addDialogListPane failed", e);
            }
        });

        visiblePanes.add(pane);
        updateDividerVisibility();
        requestLayout();
    }

    private void openChatInBottom(long dialogId) {
        if (visiblePanes.size() < 2) return;
        SplitPane pane = visiblePanes.get(1);
        if (pane.paneLayout == null) return;

        Bundle args = new Bundle();
        if (dialogId > 0) args.putLong("user_id", dialogId);
        else              args.putLong("chat_id", -dialogId);

        ChatActivity chat = new ChatActivity(args);
        chat.isInsideContainer = true;
        pane.paneLayout.presentFragment(
                new INavigationLayout.NavigationParams(chat).setRemoveLast(false));
        pane.dialogId = dialogId;
    }

    public void openDialogInNextPane(long dialogId) {
        if (!isAttached) return;
        if (visiblePanes.size() < 2) {
            post(() -> addChatPane(dialogId, false));
        } else {
            demoteOldestPane();
            postDelayed(() -> addChatPane(dialogId, false), 300);
        }
    }

    private void updateDividerVisibility() {
        if (dividerView != null)
            dividerView.setVisibility(visiblePanes.size() >= 2 ? VISIBLE : GONE);
    }

    // ── Demote to mini icon ───────────────────────────────────────────────────

    private void demoteOldestPane() {
        if (visiblePanes.isEmpty()) return;
        SplitPane old = visiblePanes.remove(0);
        String title = getTitleForDialog(old.dialogId);

        old.container.animate().scaleX(0.3f).scaleY(0.3f).alpha(0f).setDuration(250)
                .setInterpolator(CubicBezierInterpolator.EASE_BOTH)
                .withEndAction(() -> {
                    try { if (old.paneLayout != null) old.paneLayout.onPause(); } catch (Exception ignore) {}
                    old.container.removeAllViews();
                    old.container.setScaleX(1f); old.container.setScaleY(1f); old.container.setAlpha(1f);
                    addMiniTab(old.dialogId, title);

                    // Move remaining pane to top container
                    if (!visiblePanes.isEmpty()) {
                        SplitPane rem = visiblePanes.get(0);
                        if (rem.container == bottomContainer) {
                            bottomContainer.removeView(rem.paneLayout);
                            topContainer.removeAllViews();
                            topContainer.addView(rem.paneLayout, LayoutHelper.createFrame(
                                    LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                            rem.container = topContainer;
                        }
                    }
                    requestLayout();
                }).start();
    }

    private void addMiniTab(long dialogId, String title) {
        MiniPaneTab tab = new MiniPaneTab(getContext(), dialogId, title, () -> restoreMiniTab(dialogId));
        miniTabs.add(tab);
        miniTabBar.addView(tab);
        miniTabScrollView.setVisibility(VISIBLE);
        miniTabScrollView.setAlpha(0f);
        miniTabScrollView.animate().alpha(1f).setDuration(180).start();
        tab.setScaleX(0f); tab.setScaleY(0f); tab.setAlpha(0f);
        new SpringAnimation(new FloatValueHolder(0f))
                .setSpring(new SpringForce(1000f)
                        .setStiffness(SpringForce.STIFFNESS_MEDIUM)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY))
                .addUpdateListener((a, v, vel) -> {
                    float p = v / 1000f;
                    tab.setScaleX(p); tab.setScaleY(p); tab.setAlpha(p);
                }).start();
    }

    private void restoreMiniTab(long dialogId) {
        MiniPaneTab found = null;
        for (MiniPaneTab t : miniTabs) if (t.dialogId == dialogId) { found = t; break; }
        if (found == null) return;
        miniTabs.remove(found);
        miniTabBar.removeView(found);
        if (miniTabs.isEmpty()) miniTabScrollView.setVisibility(GONE);
        demoteOldestPane();
        postDelayed(() -> addChatPane(dialogId, false), 300);
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    // ── Divider ───────────────────────────────────────────────────────────────

    private void onDividerDragged(float raw) {
        if (visiblePanes.isEmpty()) return;
        float frac;
        if (isPortrait()) frac = raw / getHeight();
        else              frac = raw / getWidth();
        visiblePanes.get(0).weight = Math.max(0.25f, Math.min(0.75f, frac));
        requestLayout();
    }

    private void snapDividerToCenter() {
        if (visiblePanes.isEmpty()) return;
        float cur = visiblePanes.get(0).weight;
        ValueAnimator a = ValueAnimator.ofFloat(cur, 0.5f);
        a.setDuration(250); a.setInterpolator(CubicBezierInterpolator.DEFAULT);
        a.addUpdateListener(va -> {
            if (!visiblePanes.isEmpty()) visiblePanes.get(0).weight = (float) va.getAnimatedValue();
            requestLayout();
        });
        a.start();
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getTitleForDialog(long id) {
        try {
            int acc = UserConfig.selectedAccount;
            if (id > 0) { TLRPC.User u = MessagesController.getInstance(acc).getUser(id); if (u != null) return u.first_name != null ? u.first_name : "Chat"; }
            else         { TLRPC.Chat c = MessagesController.getInstance(acc).getChat(-id); if (c != null) return c.title != null ? c.title : "Chat"; }
        } catch (Exception ignore) {}
        return "Chat";
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void closeSplit() {
        isAttached = false;
        animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(220)
                .withEndAction(() -> {
                    onDestroy();
                    if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
                    SplitChatManager.getInstance().onSplitClosed();
                }).start();
    }

    public void onPause()  { for (SplitPane p : visiblePanes) { try { if (p.paneLayout != null) p.paneLayout.onPause();  } catch (Exception ignore) {} } }
    public void onResume() { for (SplitPane p : visiblePanes) { try { if (p.paneLayout != null) p.paneLayout.onResume(); } catch (Exception ignore) {} } }
    public void onDestroy() {
        for (SplitPane p : visiblePanes) { try { if (p.paneLayout != null) p.paneLayout.onPause(); } catch (Exception ignore) {} }
        visiblePanes.clear(); miniTabs.clear();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Divider View — horizontal in portrait, vertical in landscape
    // ══════════════════════════════════════════════════════════════════════════

    public static class SplitDividerView extends View {
        interface Drag { void onDrag(float raw); }
        interface DoubleTap { void onDoubleTap(); }
        interface LongPress { void onLongPress(); }

        private final Drag drag; private final DoubleTap dbl; private final LongPress lp;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pill  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect  = new RectF();
        private float downRaw, pressP; private long lastTap; private boolean dragging;
        private SpringAnimation pressSpring;
        private final Runnable lpRun;

        public SplitDividerView(Context ctx, Drag drag, DoubleTap dbl, LongPress lp) {
            super(ctx);
            this.drag = drag; this.dbl = dbl; this.lp = lp;
            track.setColor(0x28000000);
            pill.setColor(Theme.getColor(Theme.key_actionBarDefault));
            lpRun = () -> { if (!dragging && lp != null) { performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); lp.onLongPress(); } };
        }

        private boolean isPortrait() { return getWidth() <= ((View) getParent()).getHeight() / 2 + 1 || getHeight() > getWidth(); }

        @Override protected void onDraw(Canvas canvas) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), track);
            boolean port = getHeight() > getWidth(); // portrait divider is wide+short
            float pillW, pillH;
            if (port) { pillW = AndroidUtilities.dp(32) + pressP * AndroidUtilities.dp(12); pillH = AndroidUtilities.dp(3); }
            else       { pillW = AndroidUtilities.dp(3); pillH = AndroidUtilities.dp(32) + pressP * AndroidUtilities.dp(12); }
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            rect.set(cx - pillW / 2f, cy - pillH / 2f, cx + pillW / 2f, cy + pillH / 2f);
            canvas.drawRoundRect(rect, AndroidUtilities.dp(3), AndroidUtilities.dp(3), pill);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            boolean port = getHeight() > getWidth();
            float raw = port ? e.getRawY() : e.getRawX();
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN: downRaw = raw; dragging = false; postDelayed(lpRun, 480); spring(1f); break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(raw - downRaw) > AndroidUtilities.dp(4)) { dragging = true; removeCallbacks(lpRun); }
                    if (dragging && drag != null) drag.onDrag(raw); break;
                case MotionEvent.ACTION_UP:
                    removeCallbacks(lpRun); spring(0f);
                    if (!dragging) { long now = System.currentTimeMillis(); if (now - lastTap < 340 && dbl != null) dbl.onDoubleTap(); lastTap = now; }
                    dragging = false; break;
                case MotionEvent.ACTION_CANCEL: removeCallbacks(lpRun); spring(0f); dragging = false; break;
            }
            return true;
        }

        private void spring(float target) {
            if (pressSpring != null) pressSpring.cancel();
            pressSpring = new SpringAnimation(new FloatValueHolder(pressP * 1000f))
                    .setSpring(new SpringForce(target * 1000f).setStiffness(SpringForce.STIFFNESS_MEDIUM).setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY));
            pressSpring.addUpdateListener((a, v, vel) -> { pressP = v / 1000f; invalidate(); });
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
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(AndroidUtilities.dp(44), AndroidUtilities.dp(44));
            lp.setMargins(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            setLayoutParams(lp);

            BackupImageView av = new BackupImageView(ctx);
            av.setRoundRadius(AndroidUtilities.dp(22));
            AvatarDrawable ad = new AvatarDrawable();
            try {
                int acc = UserConfig.selectedAccount;
                if (dialogId > 0) { TLRPC.User u = MessagesController.getInstance(acc).getUser(dialogId); if (u != null) { ad.setInfo(u); av.setImage(ImageLocation.getForUser(u, ImageLocation.TYPE_SMALL), "50_50", ad, u); } else { ad.setInfo(0, title, null); av.setImageDrawable(ad); } }
                else { TLRPC.Chat c = MessagesController.getInstance(acc).getChat(-dialogId); if (c != null) { ad.setInfo(c); av.setImage(ImageLocation.getForChat(c, ImageLocation.TYPE_SMALL), "50_50", ad, c); } else { ad.setInfo(0, title, null); av.setImageDrawable(ad); } }
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
