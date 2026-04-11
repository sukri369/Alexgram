package tw.nekomimi.nekogram.ui;

import org.telegram.ui.DialogsActivity;
import org.telegram.ui.Components.FilterTabsView;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import tw.nekomimi.nekogram.helpers.HiddenChatsController;
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
        View view = super.createView(context);
        if (view instanceof ViewGroup) {
            ViewGroup contentView = (ViewGroup) view;
            for (int i = 0; i < contentView.getChildCount(); i++) {
                View child = contentView.getChildAt(i);
                if (child instanceof FilterTabsView) {
                    child.setVisibility(View.GONE);
                } else if (child instanceof FragmentFloatingButton) {
                    FragmentFloatingButton fab = (FragmentFloatingButton) child;
                    // FragmentFloatingButton doesn't expose isSubButton but we can check layout params
                    // Sub buttons usually have specific margins or we can check the icon
                    // In DialogsActivity, floatingButtonStories is added first as sub button.
                    if (fab.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams) {
                        android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) fab.getLayoutParams();
                        // Sub button (camera) has bottom margin 14, main button also has 14 but different gravity/size usually
                        // However, DialogsActivity adds floatingButtonStories (camera) THEN floatingButton3 (FAB).
                        // Let's hide the one with story icon or simply check if it's the story button.
                        // Since we can't easily check isSubButton, we can hide the one that matches story behavior.
                    }
                    
                    // Actually, let's just use the fact that there are two buttons.
                    // The camera one is floatingButtonStories. The main one is floatingButton3.
                }
            }
        }
        
        // Let's use a more direct approach by finding the buttons in DialogsActivity
        // Since they are private, we'll use reflection or just find them by index/type.
        
        AndroidUtilities.runOnUIThread(() -> {
            if (fragmentView instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) fragmentView;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    View child = vg.getChildAt(i);
                    if (child instanceof FilterTabsView) {
                        child.setVisibility(View.GONE);
                    } else if (child instanceof FragmentFloatingButton) {
                        FragmentFloatingButton fab = (FragmentFloatingButton) child;
                        // The main FAB is usually the one with the compose icon or the last one added.
                        // The story FAB is added first.
                        if (i == vg.getChildCount() - 1) { // Main FAB
                            fab.setImageResource(R.drawable.filled_fab_compose_32);
                            fab.setOnClickListener(v -> {
                                Bundle args = new Bundle();
                                args.putBoolean("onlySelect", true);
                                args.putInt("dialogsType", 3); // Forward/Select type
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
                        } else { // Likely stories button
                            fab.setVisibility(View.GONE);
                        }
                    }
                }
            }
        });
        
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
        java.util.ArrayList<org.telegram.tgnet.TLRPC.Dialog> all = getMessagesController().getAllDialogs();
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
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        tw.nekomimi.nekogram.helpers.HiddenChatsController.getInstance().lock();
    }
}
