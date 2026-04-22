package xyz.nextalone.nagram.ui;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.LaunchActivity;

/**
 * Singleton that controls Split Chat state.
 */
public class SplitChatManager {

    private static volatile SplitChatManager instance;
    private SplitChatLayout splitLayout;

    private SplitChatManager() {}

    public static SplitChatManager getInstance() {
        if (instance == null) {
            synchronized (SplitChatManager.class) {
                if (instance == null) instance = new SplitChatManager();
            }
        }
        return instance;
    }

    public boolean isActive() {
        return splitLayout != null && splitLayout.getParent() != null;
    }

    /**
     * Called when user taps "Split Chat" from Advanced Tools.
     * Shows the chat picker first; once user picks, openDialogInSplit() is called.
     */
    public void activateSplitMode(LaunchActivity activity, long currentDialogId) {
        if (activity == null) return;
        try {
            if (splitLayout == null) {
                splitLayout = new SplitChatLayout(activity);
            }
            splitLayout.setOriginDialogId(currentDialogId);
            splitLayout.attachToActivity(activity, currentDialogId);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /** Called after the user picks a chat in the picker. */
    public void openDialogInSplit(long dialogId) {
        AndroidUtilities.runOnUIThread(() -> {
            if (splitLayout != null) {
                splitLayout.openDialogInNextPane(dialogId);
            }
        });
    }

    /**
     * Called by SplitChatLayout.closeSplit() when the animation finishes.
     * Just nulls the reference — does NOT call back into the layout.
     */
    public void onSplitClosed() {
        splitLayout = null;
    }

    public void onPause()   { if (splitLayout != null) splitLayout.onPause();   }
    public void onResume()  { if (splitLayout != null) splitLayout.onResume();  }
    public void onDestroy() {
        if (splitLayout != null) { splitLayout.onDestroy(); splitLayout = null; }
    }

    public SplitChatLayout getLayout() { return splitLayout; }
}
