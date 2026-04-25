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

import androidx.core.view.OnApplyWindowInsetsListener;
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
        int account;
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
    private org.telegram.ui.DialogsActivity activePicker;
    private org.telegram.ui.ActionBar.BaseFragment originFragment;
    private long          originId;
    private long          lastBackTime;
    private int           originAccount;
    private boolean       built = false;
    private boolean       isReplacing = false;

    // ─────────────────────────────────────────────────────────────────────────
    public SplitChatLayout(Context ctx) {
        super(ctx);
        setClipChildren(false);
        setClipToPadding(false);

        // Precision Keyboard Handling: 
        // We must intercept and adjust insets before they reach the panes.
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            if (!built) return insets;

            int[] loc = new int[2];
            getLocationOnScreen(loc);
            int screenH = AndroidUtilities.displayMetrics.heightPixels;
            
            // 1. Top Pane: Should NEVER see the keyboard bottom inset
            if (pane1Container != null) {
                WindowInsetsCompat topInsets = new WindowInsetsCompat.Builder(insets)
                        .setInsets(WindowInsetsCompat.Type.systemBars(), 
                                androidx.core.graphics.Insets.of(
                                        insets.getInsets(WindowInsetsCompat.Type.systemBars()).left,
                                        insets.getInsets(WindowInsetsCompat.Type.systemBars()).top,
                                        insets.getInsets(WindowInsetsCompat.Type.systemBars()).right,
                                        0)) // Zero keyboard for top
                        .build();
                ViewCompat.dispatchApplyWindowInsets(pane1Container, topInsets);
            }

            // 2. Bottom Pane: Simply dispatch the insets.
            // The PaneContainer will handle the localization and the chat will handle the padding internally.
            if (pane2Container != null) {
                ViewCompat.dispatchApplyWindowInsets(pane2Container, insets);
            }

            return insets.consumeSystemWindowInsets();
        });
    }

    public void setOriginDialog(int account, long id) { this.originAccount = account; this.originId = id; }
    public boolean built() { return built; }

    // ── Public entry ──────────────────────────────────────────────────────────

    public void showPickerAndWait(org.telegram.ui.LaunchActivity activity, long currentDialogId) {
        this.host     = activity;
        this.originId = currentDialogId;
        
        java.util.List<org.telegram.ui.ActionBar.BaseFragment> stack = activity.actionBarLayout.getFragmentStack();
        if (!stack.isEmpty()) {
            this.originFragment = stack.get(stack.size() - 1);
        }

        if (built) return;
        try {
            activePicker = buildPicker();
            activity.actionBarLayout.presentFragment(
                    new org.telegram.ui.ActionBar.INavigationLayout.NavigationParams(activePicker));
        } catch (Exception e) {
            org.telegram.messenger.FileLog.e(e);
        }
    }

    public void buildSplit(int account, long pane2DialogId) {
        if (built || host == null) return;
        built = true;

        if (activePicker != null) {
            activePicker.finishFragment();
            activePicker = null;
        }
        
        if (originFragment != null) {
            originFragment.removeSelfFromStack();
            originFragment = null;
        }

        buildLayout(host);
        host.frameLayout.addView(this,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        
        requestFocus();

        embedFragment(originAccount, originId,      pane1Container, true);
        embedFragment(account,       pane2DialogId, pane2Container, false);

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
        args.putBoolean("allowSwitchAccount", true);
        args.putBoolean("checkCanWrite", false);
        org.telegram.ui.DialogsActivity picker = new org.telegram.ui.DialogsActivity(args);

        java.util.HashSet<Long> excluded = new java.util.HashSet<>();
        excluded.add(originId);
        for (SplitPane p : panes) excluded.add(p.dialogId);
        for (MiniPaneTab m : minis) excluded.add(m.dialogId);
        picker.setExcludedDialogIds(excluded);

        picker.setDelegate((frag, dids, msg, param, notify, date, period, topicsFrag) -> {
            if (dids != null && !dids.isEmpty()) {
                long did = dids.get(0).dialogId;
                
                boolean alreadyOpen = (did == originId);
                if (!alreadyOpen) {
                    for (SplitPane p : panes) {
                        if (p.dialogId == did) {
                            alreadyOpen = true;
                            break;
                        }
                    }
                }
                if (!alreadyOpen) {
                    for (MiniPaneTab m : minis) {
                        if (m.dialogId == did) {
                            alreadyOpen = true;
                            break;
                        }
                    }
                }

                if (alreadyOpen) {
                    try {
                        org.telegram.messenger.AndroidUtilities.vibrateCursor(frag.getFragmentView());
                        org.telegram.ui.Components.BulletinFactory.of(frag).createErrorBulletin("Chat is already open in split mode").show();
                    } catch (Exception ignore) {
                        android.widget.Toast.makeText(frag.getParentActivity(), "Chat is already open.", android.widget.Toast.LENGTH_SHORT).show();
                    }
                    return false;
                }

                frag.finishFragment();
                SplitChatManager.getInstance().openDialogInSplit(frag.getCurrentAccount(), did);
            } else {
                frag.finishFragment();
            }
            return true;
        });
        return picker;
    }

    // ── Layout skeleton ───────────────────────────────────────────────────────

    private void buildLayout(Context ctx) {
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        pane1Container = new PaneContainer(ctx);
        addView(pane1Container, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        divider = new DividerView(ctx,
                this::onDrag, this::onDragEnd, this::onDividerDoubleTap, this::showDividerMenu);
        addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 16));

        pane2Container = new PaneContainer(ctx);
        addView(pane2Container, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    // ── Fragment embedding ────────────────────────────────────────────────────

    private void embedFragment(int account, long dialogId, PaneContainer container, boolean isFirst) {
        if (dialogId == 0 || container == null || host == null) return;
        try {
            android.os.Bundle args = new android.os.Bundle();
            args.putInt("account", account);
            if (dialogId > 0) args.putLong("user_id", dialogId);
            else              args.putLong("chat_id", -dialogId);

            org.telegram.ui.ChatActivity chat = new org.telegram.ui.ChatActivity(args) {
                @Override
                public void didReceivedNotification(int id, int account, Object... args) {
                    if (id == org.telegram.messenger.NotificationCenter.closeChats) {
                        return; // Ignore closeChats to prevent split from closing on account switch
                    }
                    super.didReceivedNotification(id, account, args);
                }

                @Override
                public void finishFragment() {
                    if (isReplacing) return;
                    
                    // If account was switched, old fragments might finish themselves.
                    // We only want to close the split if it's an explicit user action (like Back button).
                    // If the account doesn't match, it's likely a system-triggered finish.
                    if (this.getCurrentAccount() != org.telegram.messenger.UserConfig.selectedAccount) {
                        // Just remove this pane but don't close the whole split if others exist.
                        if (panes.size() > 1) {
                            boolean first = !panes.isEmpty() && panes.get(0).dialogId == dialogId;
                            closePane(dialogId, first);
                        }
                        return;
                    }

                    // Verify this fragment is still the active one for this dialog in our panes
                    boolean found = false;
                    for (SplitPane p : panes) {
                        if (p.dialogId == dialogId && p.fragment == this) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) return;

                    if (panes.size() > 1) {
                        boolean first = !panes.isEmpty() && panes.get(0).dialogId == dialogId;
                        closePane(dialogId, first);
                    } else {
                        closeSplit();
                    }
                }
            };
            // Core Fix: Enable Telegram's native 'embedded mode' to fix padding/shadows/gaps.
            try {
                java.lang.reflect.Field isic = org.telegram.ui.ChatActivity.class.getDeclaredField("isInsideContainer");
                isic.setAccessible(true);
                isic.set(chat, true);
            } catch (Exception ignore) {}

            chat.setParentLayout(host.actionBarLayout);
            chat.setCurrentAccount(account);

            if (!chat.onFragmentCreate()) {
                FileLog.d("SplitChat: onFragmentCreate=false id=" + dialogId);
                return;
            }

            View view = chat.createView(host);
            if (view == null) {
                FileLog.d("SplitChat: createView=null id=" + dialogId);
                return;
            }

            // Precision Fix for White Strip and UI Visibility:
            if (view instanceof org.telegram.ui.Components.SizeNotifierFrameLayout) {
                org.telegram.ui.Components.SizeNotifierFrameLayout snfl = (org.telegram.ui.Components.SizeNotifierFrameLayout) view;
                snfl.setOccupyStatusBar(false);
                view.setBackground(null);
                snfl.setClipChildren(false);
                snfl.setClipToPadding(false);
                
                // Final "God-Level" Guardian: Keep headers and input bars visible even if 
                // Telegram's internal logic tries to hide them because of 'isInsideContainer'.
                // Precision Fix for Keyboard Spacing (God-Level Interceptor):
                // Telegram's SNFL detects the GLOBAL keyboard height and assumes the chat is full-screen.
                // We must intercept the size change notification and provide a LOCAL overlap height instead.
                try {
                    java.lang.reflect.Field delegateField = org.telegram.ui.Components.SizeNotifierFrameLayout.class.getDeclaredField("delegate");
                    delegateField.setAccessible(true);
                    final org.telegram.ui.Components.SizeNotifierFrameLayout.SizeNotifierFrameLayoutDelegate originalDelegate = 
                        (org.telegram.ui.Components.SizeNotifierFrameLayout.SizeNotifierFrameLayoutDelegate) delegateField.get(snfl);
                    
                    snfl.setDelegate((keyboardHeight, isWidthGreater) -> {
                        if (originalDelegate != null) {
                            originalDelegate.onSizeChanged(keyboardHeight, isWidthGreater);
                        }
                    });
                } catch (Exception e) {
                    FileLog.e("SplitChat: failed to intercept SNFL delegate", e);
                }

                // Double-Shift: Disable Telegram's automatic AdjustPan panning.
                // ChatActivity uses AdjustPanLayoutHelper to "pan" (shift) the entire view up,
                // which conflicts with our SNFL bottom-padding logic and causes a double-gap.
                try {
                    java.lang.reflect.Field apf = org.telegram.ui.ChatActivity.class.getDeclaredField("adjustPanLayoutHelper");
                    apf.setAccessible(true);
                    Object helper = apf.get(chat);
                    if (helper != null) {
                        java.lang.reflect.Method sem = helper.getClass().getDeclaredMethod("setEnabled", boolean.class);
                        sem.setAccessible(true);
                        sem.invoke(helper, false);
                    }
                } catch (Exception e) {
                    FileLog.e("SplitChat: failed to disable adjustPanLayoutHelper", e);
                }

                snfl.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                    snfl.setOccupyStatusBar(false);
                    try {
                        org.telegram.ui.ActionBar.ActionBar ab = chat.getActionBar();
                        if (ab != null) ab.setVisibility(View.VISIBLE);
                        
                        java.lang.reflect.Field evf = org.telegram.ui.ChatActivity.class.getDeclaredField("chatActivityEnterView");
                        evf.setAccessible(true);
                        View enterView = (View) evf.get(chat);
                        if (enterView != null) enterView.setVisibility(View.VISIBLE);
                    } catch (Exception ignore) {}
                });

                // Surgical cleanup of internal top panels
                try {
                    Class<?> cls = org.telegram.ui.ChatActivity.class;
                    String[] fields = {
                        "topPanelLayout", "topPanelLayoutFade", "topChatPanelView",
                        "fragmentContextView", "fragmentLocationContextView",
                        "fragmentContextViewWrapper", "fragmentLocationContextViewWrapper"
                    };
                    for (String fName : fields) {
                        try {
                            java.lang.reflect.Field f = cls.getDeclaredField(fName);
                            f.setAccessible(true);
                            Object o = f.get(chat);
                            if (o instanceof View) {
                                ((View) o).setBackground(null);
                            }
                        } catch (Exception ignore) {}
                    }
                    
                    // Recursive cleanup of ANY remaining white backgrounds in children
                    ViewGroup vg = (ViewGroup) view;
                    for (int i = 0; i < vg.getChildCount(); i++) {
                        View childView = vg.getChildAt(i);
                        if (!(childView instanceof org.telegram.ui.Components.RecyclerListView)) {
                            childView.setBackground(null);
                        }
                    }
                } catch (Exception ignore) {}
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
                actionBar.setCastShadows(false); // Remove shadow to prevent white lines
                if (actionBar.shouldAddToContainer()) {
                    if (actionBar.getParent() != null) {
                        ((ViewGroup) actionBar.getParent()).removeView(actionBar);
                    }
                    container.addView(actionBar);
                }

                // Add Split Options Menu Button (3 dots)
                ImageView moreBtn = new ImageView(getContext());
                moreBtn.setColorFilter(new android.graphics.PorterDuffColorFilter(
                        Theme.getColor(Theme.key_actionBarDefaultIcon), 
                        android.graphics.PorterDuff.Mode.MULTIPLY));
                moreBtn.setImageResource(R.drawable.ic_split_more);
                moreBtn.setScaleType(ImageView.ScaleType.CENTER);
                
                // Pink pill background
                android.graphics.drawable.GradientDrawable pillBg = new android.graphics.drawable.GradientDrawable();
                pillBg.setColor(0x25FF69B4); // Premium pink translucency
                pillBg.setCornerRadius(AndroidUtilities.dp(16));
                moreBtn.setBackground(pillBg);
                
                // Hot pink dots
                moreBtn.setColorFilter(new android.graphics.PorterDuffColorFilter(
                        0xFFFF69B4, android.graphics.PorterDuff.Mode.SRC_IN));
                
                moreBtn.setOnClickListener(v -> showPaneMenu(v, dialogId, isFirst));
                
                // Position it as a beautiful pill in the header
                FrameLayout.LayoutParams moreLp = LayoutHelper.createFrame(
                        40, 32, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
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
            pane.account      = account;
            pane.dialogId     = dialogId;
            pane.fragment     = chat;
            pane.fragmentView = view;
            pane.container    = container;
            panes.add(pane);

        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void swapPane(int account, long dialogId, boolean isFirst) {
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
        embedFragment(account, dialogId, container, isFirst);
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
        
        embedFragment(p2.account, id2, pane1Container, true);
        embedFragment(p1.account, id1, pane2Container, false);
        requestLayout();
    }

    private void switchChat(long oldDialogId) {
        org.telegram.ui.LaunchActivity activity = (org.telegram.ui.LaunchActivity) org.telegram.messenger.AndroidUtilities.findActivity(getContext());
        if (activity == null) return;

        android.os.Bundle args = new android.os.Bundle();
        args.putBoolean("onlySelect", true);
        args.putBoolean("checkCanOpenChat", false);
        args.putBoolean("allowSwitchAccount", true);
        args.putBoolean("forSplitSwitch", true);
        args.putString("customTitle", "Switch Chat");
        org.telegram.ui.DialogsActivity picker = new org.telegram.ui.DialogsActivity(args) {
            @Override
            public void onFragmentDestroy() {
                super.onFragmentDestroy();
                org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> restoreVisibilityIfNeeded(), 150);
            }
        };
        picker.setDelegate((frag, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids != null && !dids.isEmpty()) {
                long did = dids.get(0).dialogId;
                replaceChatInPane(frag.getCurrentAccount(), did, oldDialogId);
                frag.finishFragment();
            } else {
                frag.finishFragment();
            }
            return true;
        });
        setVisibility(GONE);
        activity.actionBarLayout.presentFragment(picker);
    }

    private void addChat() {
        org.telegram.ui.LaunchActivity activity = (org.telegram.ui.LaunchActivity) org.telegram.messenger.AndroidUtilities.findActivity(getContext());
        if (activity == null) return;

        android.os.Bundle args = new android.os.Bundle();
        args.putBoolean("onlySelect", true);
        args.putBoolean("checkCanOpenChat", false);
        args.putBoolean("allowSwitchAccount", true);
        args.putBoolean("forSplitSwitch", true);
        args.putString("customTitle", "Add Chat");
        org.telegram.ui.DialogsActivity picker = new org.telegram.ui.DialogsActivity(args) {
            @Override
            public void onFragmentDestroy() {
                super.onFragmentDestroy();
                org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> restoreVisibilityIfNeeded(), 150);
            }
        };
        picker.setDelegate((frag, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids != null && !dids.isEmpty()) {
                long did = dids.get(0).dialogId;
                SplitChatManager.getInstance().openDialogInSplit(frag.getCurrentAccount(), did);
                frag.finishFragment();
            } else {
                frag.finishFragment();
            }
            return true;
        });
        setVisibility(GONE);
        activity.actionBarLayout.presentFragment(picker);
    }

    private boolean isPickerActive() {
        if (host == null || host.actionBarLayout == null) return false;
        java.util.List<org.telegram.ui.ActionBar.BaseFragment> stack = host.actionBarLayout.getFragmentStack();
        for (org.telegram.ui.ActionBar.BaseFragment f : stack) {
            android.os.Bundle args = f.getArguments();
            if (args != null && args.getBoolean("forSplitSwitch", false)) {
                return true;
            }
        }
        return false;
    }

    private void restoreVisibilityIfNeeded() {
        if (isPickerActive()) return;
        setVisibility(VISIBLE);
    }

    private void replaceChatInPane(int account, long newDialogId, long oldDialogId) {
        int targetIndex = -1;
        for (int i = 0; i < panes.size(); i++) {
            if (panes.get(i).dialogId == oldDialogId) {
                targetIndex = i;
                break;
            }
        }

        if (targetIndex == -1) return;

        isReplacing = true;
        try {
            SplitPane target = panes.get(targetIndex);
            boolean isFirst = (target.container == pane1Container);
            float weight = target.weight;
            PaneContainer container = target.container;

            // 1. Embed new fragment first (it will be added to the end of 'panes' list)
            container.removeAllViews();
            int beforeCount = panes.size();
            embedFragment(account, newDialogId, container, isFirst);

            if (panes.size() > beforeCount) {
                // 2. Get the newly added pane
                SplitPane newPane = panes.remove(panes.size() - 1);
                newPane.weight = weight;

                // 3. Destroy old fragment safely
                try {
                    if (target.fragment != null) {
                        target.fragment.onPause();
                        target.fragment.onFragmentDestroy();
                    }
                } catch (Exception ignore) {}

                // 4. Atomic swap in the list
                panes.set(targetIndex, newPane);
            }
        } finally {
            isReplacing = false;
        }
        requestLayout();
        setVisibility(VISIBLE); // Force visibility after successful replacement
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

        // 1b. Add Chat (New Pane)
        org.telegram.ui.ActionBar.ActionBarMenuSubItem addItem = new org.telegram.ui.ActionBar.ActionBarMenuSubItem(getContext(), false, false);
        addItem.setTextAndIcon("Add Chat", R.drawable.ic_split_add_na);
        addItem.setOnClickListener(v -> {
            if (popupWindow[0] != null) popupWindow[0].dismiss();
            addChat();
        });
        popupLayout.addView(addItem);

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

        // 3. Swap Positions (only if 2 panes)
        if (panes.size() > 1) {
            org.telegram.ui.ActionBar.ActionBarMenuSubItem swapItem = new org.telegram.ui.ActionBar.ActionBarMenuSubItem(getContext(), false, false);
            swapItem.setTextAndIcon("Swap Positions", R.drawable.ic_split_switch_na);
            swapItem.setOnClickListener(v -> {
                if (popupWindow[0] != null) popupWindow[0].dismiss();
                swapPanes();
            });
            popupLayout.addView(swapItem);
        }

        // 3b. Switch Chat (Replace this pane)
        org.telegram.ui.ActionBar.ActionBarMenuSubItem switchItem = new org.telegram.ui.ActionBar.ActionBarMenuSubItem(getContext(), false, false);
        switchItem.setTextAndIcon("Switch Chat", R.drawable.ic_split_switch_na);
        switchItem.setOnClickListener(v -> {
            if (popupWindow[0] != null) popupWindow[0].dismiss();
            switchChat(dialogId);
        });
        popupLayout.addView(switchItem);

        // 4. Close Chat
        org.telegram.ui.ActionBar.ActionBarMenuSubItem closeItem = new org.telegram.ui.ActionBar.ActionBarMenuSubItem(getContext(), false, true);
        closeItem.setTextAndIcon("Close Chat", R.drawable.ic_split_close_chat_na);
        int red = 0xFFF44336;
        closeItem.setTextColor(red);
        closeItem.setIconColor(red);
        closeItem.setOnClickListener(v -> {
            if (popupWindow[0] != null) popupWindow[0].dismiss();
            closePane(dialogId, isFirst);
        });
        popupLayout.addView(closeItem);

        showPopup(anchor, popupLayout, popupWindow);
    }

    private void showDividerMenu(android.view.View anchor) {
        org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = 
            new org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout(getContext());
        
        final org.telegram.ui.ActionBar.ActionBarPopupWindow[] popupWindow = new org.telegram.ui.ActionBar.ActionBarPopupWindow[1];

        // 1. Reset Position
        org.telegram.ui.ActionBar.ActionBarMenuSubItem resetItem = new org.telegram.ui.ActionBar.ActionBarMenuSubItem(getContext(), true, false);
        resetItem.setTextAndIcon("Reset Position", R.drawable.msg_reset);
        resetItem.setOnClickListener(v -> {
            if (popupWindow[0] != null) popupWindow[0].dismiss();
            onDividerDoubleTap();
        });
        popupLayout.addView(resetItem);

        // 2. Close Split Chat (Premium Red)
        org.telegram.ui.ActionBar.ActionBarMenuSubItem closeItem = new org.telegram.ui.ActionBar.ActionBarMenuSubItem(getContext(), false, false);
        closeItem.setTextAndIcon("Close Split Chat", R.drawable.ic_split_close_chat_na);
        
        // Apply Red Color to both text and icon
        int red = 0xFFF44336;
        closeItem.setTextColor(red);
        closeItem.setIconColor(red);
        
        closeItem.setOnClickListener(v -> {
            if (popupWindow[0] != null) popupWindow[0].dismiss();
            closeSplit(true);
        });
        popupLayout.addView(closeItem);

        showPopup(anchor, popupLayout, popupWindow);
    }

    private void showPopup(android.view.View anchor, org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout layout, org.telegram.ui.ActionBar.ActionBarPopupWindow[] popupWindow) {
        // Set a standard minimum width to fix the "slider" selection highlight width
        layout.setMinimumWidth(AndroidUtilities.dp(196));
        
        popupWindow[0] = new org.telegram.ui.ActionBar.ActionBarPopupWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        popupWindow[0].setOutsideTouchable(true);
        popupWindow[0].setClippingEnabled(true);
        popupWindow[0].setFocusable(true);
        popupWindow[0].setInputMethodMode(org.telegram.ui.ActionBar.ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow[0].setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        
        // Use standard dropdown logic with a small offset to ensure it doesn't overlap the pill
        popupWindow[0].showAsDropDown(anchor, 0, AndroidUtilities.dp(4));
    }

    public void openDialogInNextPane(int account, long dialogId) {
        if (!built) return;
        if (!panes.isEmpty()) demoteToMini(panes.get(0));
        AndroidUtilities.runOnUIThread(() -> swapPane(account, dialogId, false), 280);
    }

    // ── Demote to mini icon ───────────────────────────────────────────────────

    private void demoteToMini(SplitPane pane) {
        String title = labelFor(pane.account, pane.dialogId);
        pane.container.animate().scaleX(0.2f).scaleY(0.2f).alpha(0f).setDuration(240)
                .setInterpolator(CubicBezierInterpolator.EASE_BOTH)
                .withEndAction(() -> {
                    panes.remove(pane);
                    try { if (pane.fragment != null) { pane.fragment.onPause(); pane.fragment.onFragmentDestroy(); } } catch (Exception ignore) {}
                    pane.container.removeAllViews();
                    pane.container.setScaleX(1f); pane.container.setScaleY(1f); pane.container.setAlpha(1f);
                    
                    if (panes.size() == 1 && panes.get(0).container == pane2Container) {
                        long remId = panes.get(0).dialogId;
                        int remAcc = panes.get(0).account;
                        panes.clear();
                        pane2Container.removeAllViews();
                        embedFragment(remAcc, remId, pane1Container, true);
                    }
                    
                    addMiniTab(pane.account, pane.dialogId, title);
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

    private void addMiniTab(int account, long dialogId, String title) {
        MiniPaneTab tab = new MiniPaneTab(getContext(), account, dialogId, title, () -> restoreMini(account, dialogId));
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

    private void restoreMini(int account, long dialogId) {
        MiniPaneTab toRemove = null;
        for (MiniPaneTab t : minis) {
            if (t.dialogId == dialogId && t.account == account) { toRemove = t; break; }
        }
        if (toRemove != null) {
            removeView(toRemove);
            minis.remove(toRemove);
        }
        
        if (panes.size() == 1) {
            panes.get(0).weight = 0.5f;
            embedFragment(account, dialogId, pane2Container, false);
            if (divider != null) divider.setVisibility(VISIBLE);
            requestLayout();
        } else {
            swapPane(account, dialogId, false);
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

    private String labelFor(int acc, long id) {
        try {
            if (id > 0) { TLRPC.User u = MessagesController.getInstance(acc).getUser(id); if (u != null && u.first_name != null) return u.first_name; }
            else         { TLRPC.Chat c = MessagesController.getInstance(acc).getChat(-id); if (c != null && c.title != null) return c.title; }
        } catch (Exception ignore) {}
        return "Chat";
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public boolean onBackPressed() {
        if (!built || getVisibility() != VISIBLE || getAlpha() < 0.1f) return false;
        if (isPickerActive()) return false;
        
        long now = System.currentTimeMillis();
        if (now - lastBackTime < 400) return true;
        lastBackTime = now;
        
        boolean consumed = false;
        for (int i = panes.size() - 1; i >= 0; i--) {
            SplitPane p = panes.get(i);
            if (p.fragment != null) {
                try {
                    if (!p.fragment.onBackPressed(true)) {
                        consumed = true;
                        break;
                    }
                } catch (Exception ignore) {
                    consumed = true;
                    break;
                }
            }
        }
        
        if (!consumed) {
            if (!minis.isEmpty()) {
                MiniPaneTab last = minis.get(minis.size() - 1);
                restoreMini(last.account, last.dialogId);
                return true;
            }
            
            closeSplit(true);
        }
        
        return true;
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
            
            // Unified fix for seamless exit: 
            // Always present the surviving chat with NO animation, regardless of whether it was the origin chat.
            android.os.Bundle args = new android.os.Bundle();
            if (survivingDialogId > 0) args.putLong("user_id", survivingDialogId);
            else                       args.putLong("chat_id", -survivingDialogId);
            org.telegram.ui.ChatActivity chat = new org.telegram.ui.ChatActivity(args);
            try {
                org.telegram.ui.ActionBar.INavigationLayout.NavigationParams params = 
                    new org.telegram.ui.ActionBar.INavigationLayout.NavigationParams(chat);
                params.setNoAnimation(true); // Force 0ms animation for "God-level" seamlessness
                params.setRemoveLast(false);
                host.actionBarLayout.presentFragment(params);
            } catch (Exception e) {
                host.actionBarLayout.presentFragment(chat, false); // Revert to no-anim boolean
            }
        }
        SplitChatManager.getInstance().onSplitClosed();
    }

    public void closeSplit() { closeSplit(false); }

    public void closeSplit(boolean fromBack) {
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
                        
                        if (!fromBack && activeId != originId) {
                            android.os.Bundle args = new android.os.Bundle();
                            if (activeId > 0) args.putLong("user_id", activeId);
                            else              args.putLong("chat_id", -activeId);
                            org.telegram.ui.ChatActivity chat = new org.telegram.ui.ChatActivity(args);
                            try {
                                org.telegram.ui.ActionBar.INavigationLayout.NavigationParams params = 
                                    new org.telegram.ui.ActionBar.INavigationLayout.NavigationParams(chat);
                                params.setNoAnimation(true);
                                params.setRemoveLast(false);
                                host.actionBarLayout.presentFragment(params);
                            } catch (Exception e) {
                                host.actionBarLayout.presentFragment(chat, true);
                            }
                        } else {
                            if (host.actionBarLayout.getFragmentStack().isEmpty()) {
                                host.actionBarLayout.addFragmentToStack(new org.telegram.ui.MainTabsActivity());
                            }
                        }
                    }
                    if (host != null) host.setIgnoreBackUntil(System.currentTimeMillis() + 600);
                    SplitChatManager.getInstance().onSplitClosed();
                }).start();
        
        AndroidUtilities.runOnUIThread(() -> {
            if (!built && SplitChatManager.getInstance().isActive()) {
                SplitChatManager.getInstance().onSplitClosed();
            }
        }, 500);
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

        @Override
        public android.view.WindowInsets dispatchApplyWindowInsets(android.view.WindowInsets insets) {
            androidx.core.view.WindowInsetsCompat compat = androidx.core.view.WindowInsetsCompat.toWindowInsetsCompat(insets);
            androidx.core.graphics.Insets ime = compat.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime());
            
            if (ime.bottom > 0) {
                int[] loc = new int[2];
                getLocationOnScreen(loc);
                
                android.graphics.Rect r = new android.graphics.Rect();
                getWindowVisibleDisplayFrame(r);
                
                int viewBottomOnScreen = loc[1] + getHeight();
                //Inset Fix: Translate global keyboard insets to local pane coordinates.
                int fixedImeBottom = Math.max(0, viewBottomOnScreen - r.bottom);
                
                androidx.core.view.WindowInsetsCompat fixedCompat = new androidx.core.view.WindowInsetsCompat.Builder(compat)
                    .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(), androidx.core.graphics.Insets.of(ime.left, ime.top, ime.right, fixedImeBottom))
                    .build();
                return super.dispatchApplyWindowInsets(fixedCompat.toWindowInsets());
            }
            return super.dispatchApplyWindowInsets(insets);
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

            // Pass 1: Measure ActionBar
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child instanceof org.telegram.ui.ActionBar.ActionBar && child.getVisibility() != GONE) {
                    child.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), 
                                 MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                    actionBarH = child.getMeasuredHeight();
                    break;
                }
            }

            // Pass 2: Full-Stretch Measure all other children
            // We force them to fill the ENTIRE pane height.
            // Keyboard spacing is handled INTERNALLY by the chat fragment using our intercepted insets.
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (!(child instanceof org.telegram.ui.ActionBar.ActionBar)) {
                    child.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                                 MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
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
            // Layout all other children starting from the same top as ActionBar
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (!(child instanceof org.telegram.ui.ActionBar.ActionBar)) {
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();
                    child.layout(
                        lp.leftMargin,
                        lp.topMargin + topPad - AndroidUtilities.dp(1), // 1dp Overlap to kill any tiny gaps
                        lp.leftMargin + child.getMeasuredWidth(),
                        lp.topMargin + topPad + child.getMeasuredHeight());
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
        interface OnLongPress { void onLongPress(View anchor); }

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
                    lp.onLongPress(this);
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
        final int account;
        final long dialogId;
        private float downX, downY;
        private float startTransX, startTransY;
        private boolean dragging = false;
        private final Runnable onTap;

        public MiniPaneTab(Context ctx, int account, long dialogId, String title, Runnable onTap) {
            super(ctx);
            this.account = account;
            this.dialogId = dialogId;
            this.onTap = onTap;

            BackupImageView av = new BackupImageView(ctx);
            av.setRoundRadius(AndroidUtilities.dp(28));
            AvatarDrawable ad = new AvatarDrawable();
            try {
                int acc = account;
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
