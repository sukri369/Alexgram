package tw.nekomimi.nekogram.helpers;

import org.telegram.ui.MainTabsActivity;

import xyz.nextalone.nagram.NaConfig;

public final class MainTabsHelper {
    public static final int MAIN_TABS_HEIGHT = 56;
    public static final int MAIN_TABS_MARGIN = 8;
    public static final int MAIN_TABS_MARGIN_COMPACT = 4;
    public static final int FILTER_TABS_HEIGHT = 36;
    public static final int TAB_WIDTH = 80;
    public static final int TAB_PADDING = 4;

    private MainTabsHelper() {
    }

    public static boolean isMainTabsHideTitleStyle() {
        return NaConfig.INSTANCE.getMainTabsHideTitles().Bool();
    }

    public static int getMainTabsHeight() {
        return isMainTabsHideTitleStyle() ? FILTER_TABS_HEIGHT : MAIN_TABS_HEIGHT;
    }

    public static int getMainTabsMargin() {
        return isMainTabsHideTitleStyle() ? MAIN_TABS_MARGIN_COMPACT : MAIN_TABS_MARGIN;
    }

    public static int getMainTabsHeightWithMargins() {
        return getMainTabsHeight() + getMainTabsMargin() * 2;
    }

    public static boolean isContactsTabHidden() {
        return NaConfig.INSTANCE.getHideContacts().Bool();
    }

    public static int getChatsPosition() {
        return 0;
    }

    public static int getContactsPosition() {
        return isContactsTabHidden() ? -1 : 1;
    }

    public static int getCallsOrSettingsPosition() {
        return isContactsTabHidden() ? 1 : 2;
    }

    public static int getProfilePosition() {
        return isContactsTabHidden() ? 2 : 3;
    }

    public static int getFragmentsCount() {
        return isContactsTabHidden() ? 3 : 4;
    }

    public static int getTabsViewWidth() {
        return TAB_WIDTH * 4 + (getMainTabsMargin() + TAB_PADDING) * 2;
    }
}
