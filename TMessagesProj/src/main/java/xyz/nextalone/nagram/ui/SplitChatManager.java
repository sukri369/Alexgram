package xyz.nextalone.nagram.ui;

import android.content.Context;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.LaunchActivity;

/**
 * Singleton that controls whether Split Chat mode is active and mediates
 * between ChatActivity button clicks and SplitChatLayout pane management.
 */
public class SplitChatManager {

    private static volatile SplitChatManager instance;
    private SplitChatLayout splitLayout;

    private SplitChatManager() {}

    public static SplitChatManager getInstance() {
        if (instance == null) {
            synchronized (SplitChatManager.class) {
                if (instance == null) {
                    instance = new SplitChatManager();
                }
            }
        }
        return instance;
    }

    /** Returns true when split mode is currently visible. */
    public boolean isActive() {
        return splitLayout != null && splitLayout.getParent() != null;
    }

    /**
     * Called when the user taps "Split Chat" from Advanced Tools.
     * Creates the SplitChatLayout if needed, attaches it to LaunchActivity's
     * frameLayout, then shows a chat picker so the user can pick pane #2.
     */
    public void activateSplitMode(LaunchActivity activity, long currentDialogId) {
        if (activity == null) return;
        try {
            if (splitLayout == null) {
                splitLayout = new SplitChatLayout(activity);
            }
            splitLayout.attachToActivity(activity, currentDialogId);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /**
     * Opens a new dialog in the next available split pane.
     * Called after the user picks a chat from the picker.
     */
    public void openDialogInSplit(long dialogId) {
        AndroidUtilities.runOnUIThread(() -> {
            if (splitLayout != null) {
                splitLayout.openDialogInNextPane(dialogId);
            }
        });
    }

    /** Closes split mode entirely, restoring the normal full-screen layout. */
    public void closeSplit() {
        AndroidUtilities.runOnUIThread(() -> {
            if (splitLayout != null) {
                splitLayout.closeSplit();
                splitLayout = null;
            }
        });
    }

    /** Called by LaunchActivity.onPause / onResume to propagate lifecycle. */
    public void onPause() {
        if (splitLayout != null) splitLayout.onPause();
    }

    public void onResume() {
        if (splitLayout != null) splitLayout.onResume();
    }

    /** Called by LaunchActivity.onDestroy. */
    public void onDestroy() {
        if (splitLayout != null) {
            splitLayout.onDestroy();
            splitLayout = null;
        }
    }

    /** Returns the active layout, or null. */
    public SplitChatLayout getLayout() {
        return splitLayout;
    }
}
