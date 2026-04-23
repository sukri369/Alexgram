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
import android.widget.ImageView;
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
 * Split Chat.
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
        PaneContainer container;
        float weight = 0.5f;
    }

    private final ArrayList<SplitPane> panes  = new ArrayList<>();
    private final ArrayList<MiniPaneTab> minis = new ArrayList<>();

    private View          divider;
    private PaneContainer pane1Container;
    private PaneContainer pane2Container;
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
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        pane1Container = new PaneContainer(ctx);
        addView(pane1Container, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        divider = new DividerView(ctx,
                this::onDrag, this::onDragEnd, this::onDividerDoubleTap, this::closeSplit);
        addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 16));

        pane2Container = new PaneContainer(ctx);
        addView(pane2Container, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

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

    private void embedFragment(long dialogId, PaneContainer container, boolean isFirst) {
        if (dialogId == 0 || container == null || host == null) return;
        try {
            android.os.Bundle args = new android.os.Bundle();
            if (dialogId > 0) args.putLong("user_id", dialogId);
            else              args.putLong("chat_id", -dialogId);

            org.telegram.ui.ChatActivity chat = new org.telegram.ui.ChatActivity(args) {
                @Override
                public void finishFragment() {
                    if (panes.size() > 1) {
                        boolean first = !panes.isEmpty() && panes.get(0).dialogId == dialogId;
                        closePane(dialogId, first);
                    } else {
                        closeSplit();
                    }
                }
            };
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

            // Fix: ChatAvatarContainer (the header avatar+title+subtitle) defaults to
            // occupyStatusBar=true which adds statusBarHeight top-padding internally.
            // Inside our 56dp action bar (occupyStatusBar=false) the avatar overflows
            // below the bar and bleeds into the message area (clipChildren=false in ChatActivity).
            // Call setOccupyStatusBar(false) on it via reflection so it fits within 56dp.
            try {
                java.lang.reflect.Field avField = org.telegram.ui.ChatActivity.class.getDeclaredField("avatarContainer");
                avField.setAccessible(true);
                Object avatarContainer = avField.get(chat);
                if (avatarContainer != null) {
                    java.lang.reflect.Method setOsb = avatarContainer.getClass()
                            .getMethod("setOccupyStatusBar", boolean.class);
                    setOsb.invoke(avatarContainer, false);
                }
            } catch (Exception ignore) {
                FileLog.d("SplitChat: could not set avatarContainer.setOccupyStatusBar");
            }

            container.removeAllViews();
            container.addView(view, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            // Action bar: replicate what ActionBarLayout.presentFragment() does.
            // Only pane1 is at the top of the screen — it needs status bar height on its action bar.
            // Pane2 is BELOW the divider — no status bar height needed there.
            // Use occupyStatusBar=false for BOTH panes so avatarContainer positions
            // title identically in both. The status-bar gap for pane1 is handled
            // by padding on the container instead.
            if (isFirst) {
                container.setPadding(0, AndroidUtilities.statusBarHeight, 0, 0);
            } else {
                container.setPadding(0, 0, 0, 0);
            }

            org.telegram.ui.ActionBar.ActionBar actionBar = chat.getActionBar();
            if (actionBar != null) {
                actionBar.setOccupyStatusBar(false); // same for both panes
                if (actionBar.shouldAddToContainer()) {
                    if (actionBar.getParent() != null) {
                        ((ViewGroup) actionBar.getParent()).removeView(actionBar);
                    }
                    container.addView(actionBar);
                }

                // Add God-level Split Options Menu Button (3 dots)
                ImageView moreBtn = new ImageView(getContext());
                moreBtn.setColorFilter(new android.graphics.PorterDuffColorFilter(
                        Theme.getColor(Theme.key_actionBarDefaultIcon), 
                        android.graphics.PorterDuff.Mode.MULTIPLY));
                moreBtn.setImageResource(R.drawable.ic_split_more);
                moreBtn.setScaleType(ImageView.ScaleType.CENTER);
                moreBtn.setBackground(Theme.createSelectorDrawable(
                        Theme.getColor(Theme.key_actionBarDefaultSelector), 1));
                moreBtn.setOnClickListener(v -> showPaneMenu(v, dialogId, isFirst));
                
                // Position it at the right, just before the standard 3-dots (which are 48dp wide)
                FrameLayout.LayoutParams moreLp = LayoutHelper.createFrame(
                        48, 56, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                moreLp.rightMargin = AndroidUtilities.dp(48);
                actionBar.addView(moreBtn, moreLp);
            }

            chat.onResume();
            chat.onTransitionAnimationEnd(true, false);
            chat.onBecomeFullyVisible();

            // After a frame, scroll the chat list to the bottom so the latest
            // message is visible and the input bar is in the correct position.
            final org.telegram.ui.ChatActivity finalChat = chat;
            view.post(() -> {
                try {
                    java.lang.reflect.Field f = org.telegram.ui.ChatActivity.class.getDeclaredField("chatListView");
                    f.setAccessible(true);
                    Object lv = f.get(finalChat);
                    if (lv instanceof androidx.recyclerview.widget.RecyclerView) {
                        ((androidx.recyclerview.widget.RecyclerView) lv).scrollToPosition(0);
                    }
                } catch (Exception ignore) {}
                // Force the container to re-measure so fragment sizes are fully correct
                container.requestLayout();
            });

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
        PaneContainer container = isFirst ? pane1Container : pane2Container;
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

    private void swapPanes() {
        if (panes.size() < 2) return;
        long id1 = panes.get(0).dialogId;
        long id2 = panes.get(1).dialogId;
        
        SplitPane p1 = panes.get(0);
        SplitPane p2 = panes.get(1);
        try { if (p1.fragment != null) { p1.fragment.onPause(); p1.fragment.onFragmentDestroy(); } } catch(Exception ignore){}
        try { if (p2.fragment != null) { p2.fragment.onPause(); p2.fragment.onFragmentDestroy(); } } catch(Exception ignore){}
        panes.clear();
        
        pane1Container.removeAllViews();
        pane2Container.removeAllViews();
        
        embedFragment(id2, pane1Container, true);
        embedFragment(id1, pane2Container, false);
        requestLayout();
    }

    private void closePane(long dialogId, boolean isFirst) {
        if (panes.size() <= 1) {
            closeSplit();
            return;
        }

        long survivingId = panes.get(isFirst ? 1 : 0).dialogId;
        float startWeight = panes.get(0).weight;
        float endWeight = isFirst ? 0f : 1f;

        ValueAnimator a = ValueAnimator.ofFloat(startWeight, endWeight);
        a.setDuration(240);
        a.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
        a.addUpdateListener(anim -> {
            if (!panes.isEmpty()) {
                panes.get(0).weight = (float) anim.getAnimatedValue();
                requestLayout();
            }
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                seamlessExitToNormal(survivingId);
            }
        });
        a.start();
    }

    private void showPaneMenu(View anchor, long dialogId, boolean isFirst) {
        org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = 
            new org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout(getContext());
        
        final org.telegram.ui.ActionBar.ActionBarPopupWindow[] popupWindow = new org.telegram.ui.ActionBar.ActionBarPopupWindow[1];

        // 1. Expand
        org.telegram.ui.ActionBar.ActionBarMenuSubItem expandItem = new org.telegram.ui.ActionBar.ActionBarMenuSubItem(getContext(), true, false);
        expandItem.setTextAndIcon("Expand", R.drawable.ic_split_expand_na);
        expandItem.setOnClickListener(v -> {
            if (popupWindow[0] != null) popupWindow[0].dismiss();
            if (!panes.isEmpty()) {
                panes.get(0).weight = isFirst ? 0.75f : 0.25f;
                requestLayout();
            }
        });
        popupLayout.addView(expandItem);

        // 2. Full screen
        org.telegram.ui.ActionBar.ActionBarMenuSubItem fullItem = new org.telegram.ui.ActionBar.ActionBarMenuSubItem(getContext(), false, false);
        fullItem.setTextAndIcon("Full Screen", R.drawable.ic_split_fullscreen_na);
        fullItem.setOnClickListener(v -> {
            if (popupWindow[0] != null) popupWindow[0].dismiss();
            SplitPane otherPane = null;
            for (SplitPane p : panes) {
                if (p.dialogId != dialogId) {
                    otherPane = p; break;
                }
            }
            if (otherPane != null) demoteToMini(otherPane);
        });
        popupLayout.addView(fullItem);

        // 3. Switch Chat
        org.telegram.ui.ActionBar.ActionBarMenuSubItem switchItem = new org.telegram.ui.ActionBar.ActionBarMenuSubItem(getContext(), false, false);
        switchItem.setTextAndIcon("Switch Chat", R.drawable.ic_split_switch_na);
        switchItem.setOnClickListener(v -> {
            if (popupWindow[0] != null) popupWindow[0].dismiss();
            swapPanes();
        });
        popupLayout.addView(switchItem);

        // 4. Close Chat
        org.telegram.ui.ActionBar.ActionBarMenuSubItem closeItem = new org.telegram.ui.ActionBar.ActionBarMenuSubItem(getContext(), false, true);
        closeItem.setTextAndIcon("Close Chat", R.drawable.ic_split_close_chat_na);
        closeItem.setColors(Theme.getColor(Theme.key_text_RedBold), Theme.getColor(Theme.key_text_RedBold));
        closeItem.setOnClickListener(v -> {
            if (popupWindow[0] != null) popupWindow[0].dismiss();
            closePane(dialogId, isFirst);
        });
        popupLayout.addView(closeItem);

        popupWindow[0] = new org.telegram.ui.ActionBar.ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        popupWindow[0].setOutsideTouchable(true);
        popupWindow[0].setClippingEnabled(true);
        popupWindow[0].setAnimationStyle(R.style.PopupAnimation);
        popupWindow[0].setFocusable(true);
        popupWindow[0].setInputMethodMode(org.telegram.ui.ActionBar.ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow[0].setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        
        popupWindow[0].showAsDropDown(anchor, 0, -anchor.getMeasuredHeight());
    }

    public void openDialogInNextPane(long dialogId) {
        if (!built) return;
        if (!panes.isEmpty()) demoteToMini(panes.get(0));
        AndroidUtilities.runOnUIThread(() -> swapPane(dialogId, false), 280);
    }

    // ── Demote to mini icon ───────────────────────────────────────────────────

    private void demoteToMini(SplitPane pane) {
        String title = labelFor(pane.dialogId);
        pane.container.animate().scaleX(0.2f).scaleY(0.2f).alpha(0f).setDuration(240)
                .setInterpolator(CubicBezierInterpolator.EASE_BOTH)
                .withEndAction(() -> {
                    panes.remove(pane);
                    try { if (pane.fragment != null) { pane.fragment.onPause(); pane.fragment.onFragmentDestroy(); } } catch (Exception ignore) {}
                    pane.container.removeAllViews();
                    pane.container.setScaleX(1f); pane.container.setScaleY(1f); pane.container.setAlpha(1f);
                    
                    if (panes.size() == 1 && panes.get(0).container == pane2Container) {
                        long remId = panes.get(0).dialogId;
                        panes.clear();
                        pane2Container.removeAllViews();
                        embedFragment(remId, pane1Container, true);
                    }
                    
                    addMiniTab(pane.dialogId, title);
                    requestLayout();
                    requestFocus();
                }).start();
        
        if (panes.size() == 2) {
            boolean isFirst = (pane.container == pane1Container);
            ValueAnimator a = ValueAnimator.ofFloat(panes.get(0).weight, isFirst ? 0.0f : 1.0f);
            a.setDuration(240);
            a.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
            a.addUpdateListener(anim -> {
                panes.get(0).weight = (float) anim.getAnimatedValue();
                requestLayout();
            });
            a.start();
        }
    }

    private void addMiniTab(long dialogId, String title) {
        MiniPaneTab tab = new MiniPaneTab(getContext(), dialogId, title, () -> restoreMini(dialogId));
        minis.add(tab);
        addView(tab, LayoutHelper.createFrame(56, 56, Gravity.BOTTOM | Gravity.END, 0, 0, 16, 72));
        
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
        MiniPaneTab toRemove = null;
        for (MiniPaneTab t : minis) {
            if (t.dialogId == dialogId) { toRemove = t; break; }
        }
        if (toRemove != null) {
            removeView(toRemove);
            minis.remove(toRemove);
        }
        
        if (panes.size() == 1) {
            panes.get(0).weight = 0.5f;
            embedFragment(dialogId, pane2Container, false);
            if (divider != null) divider.setVisibility(VISIBLE);
            requestLayout();
        } else {
            swapPane(dialogId, false);
        }
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private boolean isPortrait() { return getWidth() > 0 && getHeight() > getWidth(); }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int W = MeasureSpec.getSize(widthMeasureSpec);
        int H = MeasureSpec.getSize(heightMeasureSpec);

        if (pane1Container == null || W == 0 || H == 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        boolean hasP1 = false, hasP2 = false;
        for (SplitPane p : panes) {
            if (p.container == pane1Container) hasP1 = true;
            if (p.container == pane2Container) hasP2 = true;
        }

        int divPx   = (hasP1 && hasP2) ? AndroidUtilities.dp(16) : 0;
        float w     = (hasP1 && hasP2) ? Math.max(0.25f, Math.min(0.75f, panes.get(0).weight)) : 1.0f;
        boolean por = H > W;

        if (por) {
            if (hasP1 && hasP2) {
                int p1H = (int) ((H - divPx) * w);
                int p2H = H - divPx - p1H;
                pane1Container.measure(MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(p1H, MeasureSpec.EXACTLY));
                if (divider != null) divider.measure(MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(divPx, MeasureSpec.EXACTLY));
                pane2Container.measure(MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(p2H, MeasureSpec.EXACTLY));
            } else if (hasP1) {
                pane1Container.measure(MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY));
                pane2Container.measure(MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY));
                if (divider != null) divider.measure(MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY));
            } else if (hasP2) {
                pane1Container.measure(MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY));
                pane2Container.measure(MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY));
                if (divider != null) divider.measure(MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY));
            } else {
                pane1Container.measure(MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY));
                pane2Container.measure(MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY));
            }
        } else {
            if (hasP1 && hasP2) {
                int p1W = (int) ((W - divPx) * w);
                int p2W = W - divPx - p1W;
                pane1Container.measure(MeasureSpec.makeMeasureSpec(p1W, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY));
                if (divider != null) divider.measure(MeasureSpec.makeMeasureSpec(divPx, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY));
                pane2Container.measure(MeasureSpec.makeMeasureSpec(p2W, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY));
            } else if (hasP1) {
                pane1Container.measure(MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY));
                pane2Container.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY));
                if (divider != null) divider.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY));
            } else if (hasP2) {
                pane1Container.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY));
                pane2Container.measure(MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY));
                if (divider != null) divider.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY));
            } else {
                pane1Container.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY));
                pane2Container.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY));
            }
        }

        // Measure overlay views (mini bar, close button) at normal MATCH_PARENT
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child != pane1Container && child != pane2Container && child != divider) {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
            }
        }
        setMeasuredDimension(W, H);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        int W = r - l, H = b - t;
        if (W == 0 || pane1Container == null) return;

        boolean hasP1 = false, hasP2 = false;
        for (SplitPane p : panes) {
            if (p.container == pane1Container) hasP1 = true;
            if (p.container == pane2Container) hasP2 = true;
        }

        if (hasP1 && hasP2) {
            pane1Container.setRoundMode(H > W ? 1 : 3);
            pane2Container.setRoundMode(H > W ? 2 : 4);
        } else {
            pane1Container.setRoundMode(0);
            pane2Container.setRoundMode(0);
        }
        
        if (hasP1) pane1Container.setPadding(0, AndroidUtilities.statusBarHeight, 0, 0);
        if (hasP2) pane2Container.setPadding(0, hasP1 ? 0 : AndroidUtilities.statusBarHeight, 0, 0);

        int divPx = divider != null && hasP1 && hasP2 ? (H > W ? divider.getMeasuredHeight() : divider.getMeasuredWidth()) : 0;
        float w   = hasP1 && hasP2 ? Math.max(0.25f, Math.min(0.75f, panes.get(0).weight)) : 1.0f;

        if (H > W) { // portrait
            if (hasP1 && hasP2) {
                int p1H = (int) ((H - divPx) * w);
                pane1Container.layout(0, 0, W, p1H);
                if (divider != null) divider.layout(0, p1H, W, p1H + divPx);
                pane2Container.layout(0, p1H + divPx, W, H);
            } else if (hasP1) {
                pane1Container.layout(0, 0, W, H);
                pane2Container.layout(0, 0, 0, 0);
            } else if (hasP2) {
                pane1Container.layout(0, 0, 0, 0);
                pane2Container.layout(0, 0, W, H);
            } else {
                pane1Container.layout(0, 0, 0, 0);
                pane2Container.layout(0, 0, 0, 0);
            }
        } else { // landscape
            if (hasP1 && hasP2) {
                int p1W = (int) ((W - divPx) * w);
                pane1Container.layout(0, 0, p1W, H);
                if (divider != null) divider.layout(p1W, 0, p1W + divPx, H);
                pane2Container.layout(p1W + divPx, 0, W, H);
            } else if (hasP1) {
                pane1Container.layout(0, 0, W, H);
                pane2Container.layout(0, 0, 0, 0);
            } else if (hasP2) {
                pane1Container.layout(0, 0, 0, 0);
                pane2Container.layout(0, 0, W, H);
            } else {
                pane1Container.layout(0, 0, 0, 0);
                pane2Container.layout(0, 0, 0, 0);
            }
        }
    }

    // ── Divider drag ──────────────────────────────────────────────────────────

    private void onDrag(float raw) {
        if (panes.isEmpty()) return;
        // raw is a screen-absolute coordinate (getRawX/Y).
        // We must subtract this view's screen offset to get a view-local position.
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        if (getHeight() > getWidth()) { // portrait → vertical drag
            float relY = raw - loc[1];
            panes.get(0).weight = Math.max(0.0f, Math.min(1.0f, relY / Math.max(1, getHeight())));
        } else { // landscape → horizontal drag
            float relX = raw - loc[0];
            panes.get(0).weight = Math.max(0.0f, Math.min(1.0f, relX / Math.max(1, getWidth())));
        }
        requestLayout();
    }

    private void onDragEnd() {
        if (panes.isEmpty()) return;
        float w = panes.get(0).weight;
        if (w < 0.15f) {
            closePane(panes.get(0).dialogId, true);
        } else if (w > 0.85f) {
            if (panes.size() > 1) closePane(panes.get(1).dialogId, false);
        }
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

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == android.view.KeyEvent.ACTION_UP) {
                closeSplit();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(android.view.MotionEvent ev) {
        if (!hasFocus()) requestFocus();
        return super.onInterceptTouchEvent(ev);
    }

    private void seamlessExitToNormal(long survivingDialogId) {
        if (!built) return;
        built = false;

        for (SplitPane p : panes) {
            try { if (p.fragment != null) { p.fragment.onPause(); p.fragment.onFragmentDestroy(); } } catch (Exception ignore) {}
        }
        panes.clear(); minis.clear();

        if (host != null && host.frameLayout != null) {
            try { host.frameLayout.removeView(this); } catch (Exception ignore) {}
            
            if (survivingDialogId != originId) {
                android.os.Bundle args = new android.os.Bundle();
                if (survivingDialogId > 0) args.putLong("user_id", survivingDialogId);
                else                       args.putLong("chat_id", -survivingDialogId);
                org.telegram.ui.ChatActivity chat = new org.telegram.ui.ChatActivity(args);
                try {
                    org.telegram.ui.ActionBar.INavigationLayout.NavigationParams params = 
                        new org.telegram.ui.ActionBar.INavigationLayout.NavigationParams(chat);
                    params.setNoAnimation(true);
                    host.actionBarLayout.presentFragment(params);
                } catch (Exception e) {
                    host.actionBarLayout.presentFragment(chat, false);
                }
            }
        }
        SplitChatManager.getInstance().onSplitClosed();
    }

    public void closeSplit() {
        if (!built) return;
        built = false;
        long aId = originId;
        if (!panes.isEmpty()) aId = panes.get(0).dialogId;
        final long activeId = aId;

        animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(200)
                .withEndAction(() -> {
                    for (SplitPane p : panes) {
                        try { if (p.fragment != null) { p.fragment.onPause(); p.fragment.onFragmentDestroy(); } } catch (Exception ignore) {}
                    }
                    panes.clear(); minis.clear();
                    if (host != null && host.frameLayout != null) {
                        try { host.frameLayout.removeView(this); } catch (Exception ignore) {}
                        
                        if (activeId != originId) {
                            android.os.Bundle args = new android.os.Bundle();
                            if (activeId > 0) args.putLong("user_id", activeId);
                            else              args.putLong("chat_id", -activeId);
                            org.telegram.ui.ChatActivity chat = new org.telegram.ui.ChatActivity(args);
                            try {
                                org.telegram.ui.ActionBar.INavigationLayout.NavigationParams params = 
                                    new org.telegram.ui.ActionBar.INavigationLayout.NavigationParams(chat);
                                params.setNoAnimation(true);
                                host.actionBarLayout.presentFragment(params);
                            } catch (Exception e) {
                                host.actionBarLayout.presentFragment(chat, false);
                            }
                        }
                    }
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
    // PaneContainer — like ActionBarLayout.LayoutContainer but standalone.
    // Places ActionBar at y=0 and positions all other children BELOW it.
    // This is what prevents messages from overlapping the header.
    // ══════════════════════════════════════════════════════════════════════════
    public static class PaneContainer extends FrameLayout {
        private int roundMode = 0;

        public PaneContainer(Context ctx) {
            super(ctx);
            setWillNotDraw(false);
            setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    if (roundMode == 0) {
                        outline.setRect(0, 0, view.getWidth(), view.getHeight());
                        return;
                    }
                    int r = AndroidUtilities.dp(16);
                    int l = 0, t = 0, rght = view.getWidth(), b = view.getHeight();
                    if (roundMode == 1) t = -r; // top pane, round bottom
                    if (roundMode == 2) b = view.getHeight() + r; // bottom pane, round top
                    if (roundMode == 3) l = -r; // left pane, round right
                    if (roundMode == 4) rght = view.getWidth() + r; // right pane, round left
                    outline.setRoundRect(l, t, rght, b, r);
                }
            });
            setClipToOutline(true);
        }

        public void setRoundMode(int mode) {
            if (this.roundMode != mode) {
                this.roundMode = mode;
                invalidateOutline();
            }
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec);
            int h = MeasureSpec.getSize(hSpec);
            int actionBarH = 0;
            // First pass: measure ActionBar (unspecified height so it wraps)
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child instanceof org.telegram.ui.ActionBar.ActionBar && child.getVisibility() != GONE) {
                    child.measure(
                        MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(h, MeasureSpec.UNSPECIFIED));
                    actionBarH = child.getMeasuredHeight();
                    break;
                }
            }
            // Second pass: measure other children with reduced height (below action bar)
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (!(child instanceof org.telegram.ui.ActionBar.ActionBar)) {
                    measureChildWithMargins(child, wSpec, 0, hSpec, actionBarH);
                }
            }
            setMeasuredDimension(w, h);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            // topPad = statusBarHeight for pane1, 0 for pane2
            int topPad   = getPaddingTop();
            int actionBarH = 0;
            // Layout ActionBar below the top padding
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child instanceof org.telegram.ui.ActionBar.ActionBar && child.getVisibility() != GONE) {
                    child.layout(0, topPad, child.getMeasuredWidth(), topPad + child.getMeasuredHeight());
                    actionBarH = child.getMeasuredHeight();
                    break;
                }
            }
            // Layout all other children below padding + ActionBar
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (!(child instanceof org.telegram.ui.ActionBar.ActionBar)) {
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();
                    child.layout(
                        lp.leftMargin,
                        lp.topMargin + topPad + actionBarH,
                        lp.leftMargin + child.getMeasuredWidth(),
                        lp.topMargin + topPad + actionBarH + child.getMeasuredHeight());
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Draggable Divider — horizontal pill in portrait, vertical in landscape
    // ══════════════════════════════════════════════════════════════════════════
    public static class DividerView extends View {
        interface OnDrag      { void onDrag(float raw); }
        interface OnDragEnd   { void onDragEnd(); }
        interface OnDoubleTap { void onDoubleTap(); }
        interface OnLongPress { void onLongPress(); }

        private final OnDrag drag; private final OnDragEnd dragEnd; private final OnDoubleTap dbl; private final OnLongPress lp;
        private final Paint pill  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect  = new RectF();
        private float downRaw, pressP; private long lastTap; private boolean dragging;
        private SpringAnimation pressAnim;
        private final Runnable lpRun;

        public DividerView(Context ctx, OnDrag drag, OnDragEnd dragEnd, OnDoubleTap dbl, OnLongPress lp) {
            super(ctx);
            this.drag = drag; this.dragEnd = dragEnd; this.dbl = dbl; this.lp = lp;
            pill.setColor(Theme.getColor(Theme.key_actionBarDefault));
            lpRun = () -> {
                if (!dragging && lp != null) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    lp.onLongPress();
                }
            };
        }

        private boolean isHorizontal() { return getWidth() >= getHeight(); }

        @Override
        protected void onDraw(Canvas canvas) {
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
                    } else {
                        if (dragEnd != null) dragEnd.onDragEnd();
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
        private float downX, downY;
        private float startTransX, startTransY;
        private boolean dragging = false;
        private final Runnable onTap;

        public MiniPaneTab(Context ctx, long dialogId, String title, Runnable onTap) {
            super(ctx);
            this.dialogId = dialogId;
            this.onTap = onTap;

            BackupImageView av = new BackupImageView(ctx);
            av.setRoundRadius(AndroidUtilities.dp(28));
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
            addView(av, LayoutHelper.createFrame(56, 56, Gravity.CENTER));

            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setStroke(AndroidUtilities.dp(2), Theme.getColor(Theme.key_actionBarDefault));
            ring.setColor(Color.TRANSPARENT);
            setBackground(ring);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startTransX = getTranslationX();
                    startTransY = getTranslationY();
                    dragging = false;
                    animate().scaleX(0.9f).scaleY(0.9f).setDuration(150).start();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - downX;
                    float dy = e.getRawY() - downY;
                    if (!dragging && (Math.abs(dx) > AndroidUtilities.dp(4) || Math.abs(dy) > AndroidUtilities.dp(4))) {
                        dragging = true;
                    }
                    if (dragging) {
                        setTranslationX(startTransX + dx);
                        setTranslationY(startTransY + dy);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                    if (!dragging && e.getAction() == MotionEvent.ACTION_UP && onTap != null) {
                        onTap.run();
                    }
                    dragging = false;
                    return true;
            }
            return super.onTouchEvent(e);
        }
    }
}
