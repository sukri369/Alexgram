package xyz.nextalone.nagram.ui;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.LaunchActivity;

public class SplitChatManager {

    private static volatile SplitChatManager instance;
    private SplitChatLayout splitLayout;
    private long pendingOriginId;

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
     * Called when user taps "Split Chat".
     * Shows the picker first. The split overlay is built after the user picks.
     */
    public void activateSplitMode(LaunchActivity activity, long currentDialogId) {
        if (activity == null) return;
        try {
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    if (splitLayout != null && splitLayout.getContext() != activity) {
                        splitLayout = null;
                    }
                    if (splitLayout == null) {
                        splitLayout = new SplitChatLayout(activity);
                    }
                    pendingOriginId = currentDialogId;
                    splitLayout.setOriginDialogId(currentDialogId);
                    splitLayout.showPickerAndWait(activity, currentDialogId);
                } catch (Exception e) {
                    FileLog.e(e);
                    splitLayout = null;
                }
            });
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /**
     * Called from the picker delegate after user selects a chat.
     * Builds the actual split overlay.
     */
    public void openDialogInSplit(long dialogId) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                if (splitLayout != null) {
                    if (!splitLayout.built()) {
                        splitLayout.buildSplit(dialogId);
                    } else {
                        splitLayout.openDialogInNextPane(dialogId);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void onSplitClosed() {
        splitLayout = null;
    }

    public void onPause()  { if (splitLayout != null) { try { splitLayout.onPause();  } catch (Exception ignore) {} } }
    public void onResume() { if (splitLayout != null) { try { splitLayout.onResume(); } catch (Exception ignore) {} } }
    public void onDestroy() {
        if (splitLayout != null) { try { splitLayout.onDestroy(); } catch (Exception ignore) {} splitLayout = null; }
    }

    public SplitChatLayout getLayout() { return splitLayout; }
}
