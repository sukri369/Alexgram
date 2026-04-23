package xyz.nextalone.nagram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;

/**
 * God-level Split Chat.
 *
 * Pattern: fragment.setParentLayout(existing) + createView() directly
 * (same as RightSlidingDialogContainer — no new ActionBarLayout = no crashes)
 *
 * Portrait  → TOP / BOTTOM panes (horizontal divider)
 * Landscape → LEFT / RIGHT panes (vertical divider)
 */
public class SplitChatLayout extends FrameLayout {

    // ─────────────────────────────────────────────────────────────────────────
    static class SplitPane {
        long dialogId;
        BaseFragment fragment;
        View fragmentView;
        FrameLayout container;
        float weight = 0.5f;
    }

    private final ArrayList<SplitPane> panes  = new ArrayList<>();
    private final ArrayList<MiniPaneTab> minis = new ArrayList<>();

    private View          divider;
    private FrameLayout   pane1Container;
    private FrameLayout   pane2Container;
    private HorizontalScrollView miniScroll;
    private LinearLayout  miniBar;
    private LaunchActivity host;
    private long          originId;
    private boolean       built = false;

    // ─────────────────────────────────────────────────────────────────────────
    public SplitChatLayout(Context ctx) {
        super(ctx);
        setClipChildren(false);
        setClipToPadding(false);
    }

    public void setOriginDialogId(long id) { this.originId = id; }
    public boolean built() { return built; }

    // ── Public entry ──────────────────────────────────────────────────────────

    public void showPickerAndWait(LaunchActivity activity, long currentDialogId) {
        this.host     = activity;
        this.originId = currentDialogId;
        if (built) return;
        try {
            activity.actionBarLayout.presentFragment(
                    new INavigationLayout.NavigationParams(buildPicker()));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void buildSplit(long pane2DialogId) {
        if (built || host == null) return;
        built = true;

        buildLayout(host);
        host.frameLayout.addView(this,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        embedFragment(originId,      pane1Container, true);
        embedFragment(pane2DialogId, pane2Container, false);

        setAlpha(0f); setScaleX(0.97f); setScaleY(0.97f);
        animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(260).setInterpolator(CubicBezierInterpolator.DEFAULT).start();

        if (divider != null) divider.setVisibility(panes.size() >= 2 ? VISIBLE : GONE);
    }

    // ── Picker ────────────────────────────────────────────────────────────────

    private org.telegram.ui.DialogsActivity buildPicker() {
        android.os.Bundle args = new android.os.Bundle();
        args.putBoolean("onlySelect", true);
        args.putBoolean("resetDelegate", false);
        org.telegram.ui.DialogsActivity picker = new org.telegram.ui.DialogsActivity(args);
        picker.setDelegate((frag, dids, msg, param, notify, date, period, topicsFrag) -> {
            if (dids != null && !dids.isEmpty()) {
                long did = dids.get(0).dialogId;
                frag.finishFragment();
                SplitChatManager.getInstance().openDialogInSplit(did);
            } else {
                frag.finishFragment();
            }
            return true;
        });
        return picker;
    }

    // ── Layout skeleton ───────────────────────────────────────────────────────

    private void buildLayout(Context ctx) {
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        pane1Container = new FrameLayout(ctx);
        addView(pane1Container, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        divider = new DividerView(ctx,
                this::onDrag, this::onDividerDoubleTap, this::closeSplit);
        addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 16));

        pane2Container = new FrameLayout(ctx);
        addView(pane2Container, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        miniScroll = new HorizontalScrollView(ctx);
        miniScroll.setHorizontalScrollBarEnabled(false);
        miniScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        miniBar = new LinearLayout(ctx);
        miniBar.setOrientation(LinearLayout.HORIZONTAL);
        miniBar.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(4),
                AndroidUtilities.dp(6), AndroidUtilities.dp(4));
        miniScroll.addView(miniBar, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
        addView(miniScroll, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, 52, Gravity.BOTTOM | Gravity.START, 4, 0, 4, 8));
        miniScroll.setVisibility(GONE);

        TextView close = new TextView(ctx);
        close.setText(LocaleController.getString("SplitChatClose", R.string.SplitChatClose));
        close.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
        close.setTextSize(12);
        close.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(5),
                AndroidUtilities.dp(10), AndroidUtilities.dp(5));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.getColor(Theme.key_actionBarDefault));
        bg.setCornerRadius(AndroidUtilities.dp(14));
        close.setBackground(bg);
        close.setOnClickListener(v -> closeSplit());
        addView(close, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.END, 0, 8, 8, 0));
    }

    // ── Fragment embedding ────────────────────────────────────────────────────

    private void embedFragment(long dialogId, FrameLayout container, boolean isFirst) {
        if (dialogId == 0 || container == null || host == null) return;
        try {
            android.os.Bundle args = new android.os.Bundle();
            if (dialogId > 0) args.putLong("user_id", dialogId);
            else              args.putLong("chat_id", -dialogId);

            org.telegram.ui.ChatActivity chat = new org.telegram.ui.ChatActivity(args);
            // DO NOT set isInsideContainer=true — it hides the input bar (ChatActivity line 8601-8602)
            // We'll manually fix the only things isInsideContainer was doing that we need.
            chat.setParentLayout(host.actionBarLayout);

            if (!chat.onFragmentCreate()) {
                FileLog.d("SplitChat: onFragmentCreate=false id=" + dialogId);
                return;
            }

            View view = chat.createView(host);
            if (view == null) {
                FileLog.d("SplitChat: createView=null id=" + dialogId);
                return;
            }

            // Fix: ChatActivity line 5157 sets contentView.setOccupyStatusBar(true) when
            // isInsideContainer=false. This would make the content view add status-bar-height
            // top padding internally, conflicting with our pane layout.
            // We override it here, since our panes manage their own layout.
            if (view instanceof org.telegram.ui.Components.SizeNotifierFrameLayout) {
                ((org.telegram.ui.Components.SizeNotifierFrameLayout) view).setOccupyStatusBar(false);
            }

            container.removeAllViews();
            container.addView(view, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            // Action bar: replicate what ActionBarLayout.presentFragment() does.
            // Only pane1 is at the top of the screen — it needs status bar height on its action bar.
            // Pane2 is BELOW the divider — no status bar height needed there.
            org.telegram.ui.ActionBar.ActionBar actionBar = chat.getActionBar();
            if (actionBar != null) {
                actionBar.setOccupyStatusBar(isFirst);
                if (actionBar.shouldAddToContainer()) {
                    if (actionBar.getParent() != null) {
                        ((ViewGroup) actionBar.getParent()).removeView(actionBar);
                    }
                    container.addView(actionBar);
                }
            }

            chat.onResume();
            chat.onTransitionAnimationEnd(true, false);
            chat.onBecomeFullyVisible();

            SplitPane pane = new SplitPane();
            pane.dialogId     = dialogId;
            pane.fragment     = chat;
            pane.fragmentView = view;
            pane.container    = container;
            panes.add(pane);

        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void swapPane(long dialogId, boolean isFirst) {
        FrameLayout container = isFirst ? pane1Container : pane2Container;
        if (!panes.isEmpty()) {
            int idx = isFirst ? 0 : panes.size() - 1;
            if (idx < panes.size()) {
                SplitPane old = panes.get(idx);
                try { if (old.fragment != null) { old.fragment.onPause(); old.fragment.onFragmentDestroy(); } } catch (Exception ignore) {}
                panes.remove(idx);
            }
        }
        container.removeAllViews();
        embedFragment(dialogId, container, isFirst);
        if (divider != null) divider.setVisibility(panes.size() >= 2 ? VISIBLE : GONE);
    }

    public void openDialogInNextPane(long dialogId) {
        if (!built) return;
        if (!panes.isEmpty()) demoteToMini(panes.get(0));
        AndroidUtilities.runOnUIThread(() -> swapPane(dialogId, false), 280);
    }

    // ── Demote to mini icon ───────────────────────────────────────────────────

    private void demoteToMini(SplitPane pane) {
        panes.remove(pane);
        String title = labelFor(pane.dialogId);
        pane.container.animate().scaleX(0.2f).scaleY(0.2f).alpha(0f).setDuration(240)
                .setInterpolator(CubicBezierInterpolator.EASE_BOTH)
                .withEndAction(() -> {
                    try { if (pane.fragment != null) { pane.fragment.onPause(); pane.fragment.onFragmentDestroy(); } } catch (Exception ignore) {}
                    pane.container.removeAllViews();
                    pane.container.setScaleX(1f); pane.container.setScaleY(1f); pane.container.setAlpha(1f);
                    addMiniTab(pane.dialogId, title);
                }).start();
    }

    private void addMiniTab(long dialogId, String title) {
        MiniPaneTab tab = new MiniPaneTab(getContext(), dialogId, title, () -> restoreMini(dialogId));
        minis.add(tab);
        miniBar.addView(tab);
        if (miniScroll.getVisibility() != VISIBLE) {
            miniScroll.setVisibility(VISIBLE);
            miniScroll.setAlpha(0f);
            miniScroll.animate().alpha(1f).setDuration(160).start();
        }
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

    private void restoreMini(long dialogId) {
        for (MiniPaneTab t : minis) {
            if (t.dialogId == dialogId) { miniBar.removeView(t); minis.remove(t); break; }
        }
        if (minis.isEmpty()) miniScroll.setVisibility(GONE);
        swapPane(dialogId, false);
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private boolean isPortrait() { return getWidth() > 0 && getHeight() > getWidth(); }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        int W = r - l, H = b - t;
        if (W == 0 || pane1Container == null) return;

        int divPx = divider != null && divider.getMeasuredHeight() > 0
                ? divider.getMeasuredHeight() : AndroidUtilities.dp(16);
        float w = panes.isEmpty() ? 0.5f : Math.max(0.25f, Math.min(0.75f, panes.get(0).weight));

        if (isPortrait()) {
            int p1H = (int) ((H - divPx) * w);
            pane1Container.layout(0, 0, W, p1H);
            divider.layout(0, p1H, W, p1H + divPx);
            pane2Container.layout(0, p1H + divPx, W, H);
        } else {
            int divV = divider != null && divider.getMeasuredWidth() > 0
                    ? divider.getMeasuredWidth() : AndroidUtilities.dp(16);
            int p1W = (int) ((W - divV) * w);
            pane1Container.layout(0, 0, p1W, H);
            divider.layout(p1W, 0, p1W + divV, H);
            pane2Container.layout(p1W + divV, 0, W, H);
        }
    }

    // ── Divider drag ──────────────────────────────────────────────────────────

    private void onDrag(float raw) {
        if (panes.isEmpty()) return;
        float frac = isPortrait() ? (raw / Math.max(1, getHeight())) : (raw / Math.max(1, getWidth()));
        panes.get(0).weight = Math.max(0.25f, Math.min(0.75f, frac));
        requestLayout();
    }

    private void onDividerDoubleTap() {
        if (panes.isEmpty()) return;
        float cur = panes.get(0).weight;
        ValueAnimator va = ValueAnimator.ofFloat(cur, 0.5f);
        va.setDuration(240); va.setInterpolator(CubicBezierInterpolator.DEFAULT);
        va.addUpdateListener(a -> { if (!panes.isEmpty()) panes.get(0).weight = (float) a.getAnimatedValue(); requestLayout(); });
        va.start();
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String labelFor(long id) {
        try {
            int acc = UserConfig.selectedAccount;
            if (id > 0) { TLRPC.User u = MessagesController.getInstance(acc).getUser(id); if (u != null && u.first_name != null) return u.first_name; }
            else         { TLRPC.Chat c = MessagesController.getInstance(acc).getChat(-id); if (c != null && c.title != null) return c.title; }
        } catch (Exception ignore) {}
        return "Chat";
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void closeSplit() {
        built = false;
        animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(200)
                .withEndAction(() -> {
                    for (SplitPane p : panes) {
                        try { if (p.fragment != null) { p.fragment.onPause(); p.fragment.onFragmentDestroy(); } } catch (Exception ignore) {}
                    }
                    panes.clear(); minis.clear();
                    if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
                    SplitChatManager.getInstance().onSplitClosed();
                }).start();
    }

    public void onPause()  { for (SplitPane p : panes) { try { if (p.fragment != null) p.fragment.onPause();  } catch (Exception ignore) {} } }
    public void onResume() { for (SplitPane p : panes) { try { if (p.fragment != null) p.fragment.onResume(); } catch (Exception ignore) {} } }
    public void onDestroy() {
        for (SplitPane p : panes) { try { if (p.fragment != null) { p.fragment.onPause(); p.fragment.onFragmentDestroy(); } } catch (Exception ignore) {} }
        panes.clear(); minis.clear();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Draggable Divider — horizontal pill in portrait, vertical in landscape
    // ══════════════════════════════════════════════════════════════════════════
    public static class DividerView extends View {
        interface OnDrag      { void onDrag(float raw); }
        interface OnDoubleTap { void onDoubleTap(); }
        interface OnLongPress { void onLongPress(); }

        private final OnDrag drag; private final OnDoubleTap dbl; private final OnLongPress lp;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pill  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect  = new RectF();
        private float downRaw, pressP; private long lastTap; private boolean dragging;
        private SpringAnimation pressAnim;
        private final Runnable lpRun;

        public DividerView(Context ctx, OnDrag drag, OnDoubleTap dbl, OnLongPress lp) {
            super(ctx);
            this.drag = drag; this.dbl = dbl; this.lp = lp;
            track.setColor(0x44000000);
            pill.setColor(Theme.getColor(Theme.key_actionBarDefault));
            lpRun = () -> {
                if (!dragging && lp != null) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    lp.onLongPress();
                }
            };
        }

        private boolean isHorizontal() { return getHeight() > getWidth(); }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), track);
            float big = AndroidUtilities.dp(28) + pressP * AndroidUtilities.dp(10);
            float sm  = AndroidUtilities.dp(3);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float pw, ph;
            if (isHorizontal()) { pw = big; ph = sm; }
            else                 { pw = sm;  ph = big; }
            rect.set(cx - pw / 2f, cy - ph / 2f, cx + pw / 2f, cy + ph / 2f);
            canvas.drawRoundRect(rect, AndroidUtilities.dp(3), AndroidUtilities.dp(3), pill);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float raw = isHorizontal() ? e.getRawY() : e.getRawX();
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downRaw = raw; dragging = false;
                    postDelayed(lpRun, 450); springPress(1f); break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(raw - downRaw) > AndroidUtilities.dp(4)) { dragging = true; removeCallbacks(lpRun); }
                    if (dragging && drag != null) drag.onDrag(raw); break;
                case MotionEvent.ACTION_UP:
                    removeCallbacks(lpRun); springPress(0f);
                    if (!dragging) {
                        long now = System.currentTimeMillis();
                        if (now - lastTap < 350 && dbl != null) dbl.onDoubleTap();
                        lastTap = now;
                    }
                    dragging = false; break;
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(lpRun); springPress(0f); dragging = false; break;
            }
            return true;
        }

        private void springPress(float target) {
            if (pressAnim != null) pressAnim.cancel();
            pressAnim = new SpringAnimation(new FloatValueHolder(pressP * 1000f))
                    .setSpring(new SpringForce(target * 1000f)
                            .setStiffness(SpringForce.STIFFNESS_MEDIUM)
                            .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY));
            pressAnim.addUpdateListener((a, v, vel) -> { pressP = v / 1000f; invalidate(); });
            pressAnim.start();
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
