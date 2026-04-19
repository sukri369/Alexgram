package tw.nekomimi.nekogram.ui;

import org.telegram.ui.DialogsActivity;
import org.telegram.ui.Components.FilterTabsView;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import tw.nekomimi.nekogram.helpers.HiddenChatsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

public class HiddenChatsActivity extends DialogsActivity {

    public HiddenChatsActivity(Bundle args) {
        super(args);
    }
    
    @Override
    public View createView(android.content.Context context) {
        hasStories = false;
        hasOnlySlefStories = false;
        dialogStoriesCellVisible = false;
        progressToDialogStoriesCell = 0f;
        progressToShowStories = 0f;
        canShowFilterTabsView = false;
        View view = super.createView(context);

        hasStories = false;
        hasOnlySlefStories = false;
        dialogStoriesCellVisible = false;
        progressToDialogStoriesCell = 0f;
        progressToShowStories = 0f;
        
        if (filterTabsView != null) {
            filterTabsView.setVisibility(View.GONE);
            animatorFilterTabsVisible.setValue(false, false);
        }
        
        if (dialogStoriesCell != null) {
            dialogStoriesCell.setVisibility(View.GONE);
        }

        if (floatingButtonStories != null) {
            floatingButtonStories.setVisibility(View.GONE);
        }
        
        if (floatingButton3 != null) {
            floatingButton3.setVisibility(View.VISIBLE);
            floatingButton3.setImageResource(R.drawable.filled_fab_compose_32);
            floatingButton3.setOnClickListener(v -> {
                Bundle args = new Bundle();
                args.putBoolean("onlySelect", true);
                args.putInt("dialogsType", 3); // Forward/Select type
                args.putString("customTitle", LocaleController.getString("HideChat", R.string.HideChat));
                DialogsActivity picker = new DialogsActivity(args);
                picker.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
                    for (MessagesStorage.TopicKey topicKey : dids) {
                        HiddenChatsController.getInstance().hide(currentAccount, topicKey.dialogId);
                    }
                    fragment.finishFragment();
                    updateVisibleRows(0);
                    return true;
                });
                presentFragment(picker);
            });
        }

        for (ViewPage viewPage : viewPages) {
            if (viewPage != null && viewPage.listView != null) {
                viewPage.listView.requestLayout();
            }
        }

        if (actionBar != null) {
            actionBar.setOnLongClickListener(v -> true);
        }
        
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (actionBar != null) {
            actionBar.setTitle("Hidden Chats");
        }
    }

    @Override
    public java.util.ArrayList<org.telegram.tgnet.TLRPC.Dialog> getDialogsArray(int currentAccount, int dialogsType, int folderId, boolean frozen) {
        if (frozen) {
            return super.getDialogsArray(currentAccount, dialogsType, folderId, frozen);
        }
        java.util.ArrayList<org.telegram.tgnet.TLRPC.Dialog> all = getMessagesController().getDialogs(0);
        java.util.ArrayList<org.telegram.tgnet.TLRPC.Dialog> hidden = new java.util.ArrayList<>();
        tw.nekomimi.nekogram.helpers.HiddenChatsController controller = tw.nekomimi.nekogram.helpers.HiddenChatsController.getInstance();
        for (int i = 0; i < all.size(); i++) {
             org.telegram.tgnet.TLRPC.Dialog d = all.get(i);
             if (controller.isHidden(currentAccount, d.id)) {
                  hidden.add(d);
             }
        }
        return hidden;
    }

    @Override
    public boolean hasHiddenArchive() {
        // Hidden chats list is a custom subset and must not reserve archive pull space.
        return false;
    }

    @Override
    public void updateStoriesVisibility(boolean animated) {
        hasStories = false;
        hasOnlySlefStories = false;
        dialogStoriesCellVisible = false;
        progressToDialogStoriesCell = 0f;
        progressToShowStories = 0f;
        if (dialogStoriesCell != null) {
            dialogStoriesCell.setVisibility(View.GONE);
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        tw.nekomimi.nekogram.helpers.HiddenChatsController.getInstance().lock();
    }
}
