package tw.nekomimi.nekogram.filters.popup;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

public class RegexUserFilterPopup {
    public static void show(BaseFragment fragment, View anchorView, float touchedX, float touchedY, Theme.ResourcesProvider resourcesProvider, Runnable onDelete) {
        show(fragment, anchorView, touchedX, touchedY, resourcesProvider, onDelete, null, null, null);
    }

    public static void show(BaseFragment fragment, View anchorView, float touchedX, float touchedY,
                            Theme.ResourcesProvider resourcesProvider,
                            Runnable onDelete, Runnable onHide, Runnable onMask, Runnable onMaskColor) {
        if (fragment.getFragmentView() == null) {
            return;
        }

        var layout = RegexPopupUtils.createLayout(fragment);
        var popupWindow = RegexPopupUtils.createPopupWindow(layout);
        var windowLayout = createPopupLayout(layout, popupWindow, resourcesProvider, onDelete, onHide, onMask, onMaskColor);
        RegexPopupUtils.showPopupAtTouch(fragment, anchorView, touchedX, touchedY, popupWindow, windowLayout);
    }

    private static ActionBarPopupWindow.ActionBarPopupWindowLayout createPopupLayout(
            ActionBarPopupWindow.ActionBarPopupWindowLayout layout,
            ActionBarPopupWindow popupWindow,
            Theme.ResourcesProvider resourcesProvider,
            Runnable onDelete, Runnable onHide, Runnable onMask, Runnable onMaskColor) {
        layout.setFitItems(true);

        if (onHide != null) {
            var hideBtn = ActionBarMenuItem.addItem(layout, R.drawable.msg_block2, getString(R.string.IgnoreBlocked), false, resourcesProvider);
            hideBtn.setOnClickListener(view -> {
                onHide.run();
                popupWindow.dismiss();
            });
        }

        if (onMask != null) {
            var maskBtn = ActionBarMenuItem.addItem(layout, R.drawable.msg_spoiler, getString(R.string.MaskBlockedUserMessages), false, resourcesProvider);
            maskBtn.setOnClickListener(view -> {
                onMask.run();
                popupWindow.dismiss();
            });
        }

        if (onMaskColor != null) {
            var colorBtn = ActionBarMenuItem.addItem(layout, R.drawable.msg_photo_settings_solar, getString(R.string.RegexFiltersSpoilerColor), false, resourcesProvider);
            colorBtn.setOnClickListener(view -> {
                onMaskColor.run();
                popupWindow.dismiss();
            });
        }

        var deleteBtn = ActionBarMenuItem.addItem(layout, R.drawable.msg_delete, getString(R.string.Delete), false, resourcesProvider);
        deleteBtn.setOnClickListener(view -> {
            if (onDelete != null) {
                onDelete.run();
            }
            popupWindow.dismiss();
        });
        RegexPopupUtils.applyDeleteItemColor(deleteBtn);

        return layout;
    }
}
