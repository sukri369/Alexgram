package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UndoView;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellHeader;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheckIcon;
import xyz.nextalone.nagram.NaConfig;

@SuppressLint("RtlHardcoded")
@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class NekoCustomizationSettingsActivity extends BaseNekoXSettingsActivity {

    private ListAdapter listAdapter;

    @Override
    protected RecyclerListView.SelectionAdapter getListAdapter() {
        return listAdapter;
    }

    @Override
    protected CellGroup getCellGroup() {
        return cellGroup;
    }

    @Override
    protected String getSettingsPrefix() {
        return "customization";
    }

    private final CellGroup cellGroup = new CellGroup(this);

    // Customization Settings
    private final AbstractConfigCell headerCustomization = cellGroup.appendCell(new ConfigCellHeader(LocaleController.getString("Customization", R.string.Customization)));
    private final AbstractConfigCell pillStackRow = cellGroup.appendCell(
            new ConfigCellTextCheckIcon(null, "PillStack", getString(R.string.PillStackPills), R.drawable.ic_ab_search, false, () ->
                    presentFragment(new com.exteragram.messenger.pillstack.ui.PillStackPreferencesActivity()))
    );
    private final AbstractConfigCell showQuickEditIconRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.showQuickEditIconInChatList, LocaleController.getString("ShowQuickEditIconDesc", R.string.ShowQuickEditIconDesc), LocaleController.getString("ShowQuickEditIcon", R.string.ShowQuickEditIcon)));
    private final AbstractConfigCell quickEditIconOnlyOwnRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.quickEditIconOnlyForOwnMessages, LocaleController.getString("QuickEditIconOnlyOwnDesc", R.string.QuickEditIconOnlyOwnDesc), LocaleController.getString("QuickEditIconOnlyOwn", R.string.QuickEditIconOnlyOwn)));
    private final AbstractConfigCell forceMusicSpeedControlRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.forceMusicSpeedControl, LocaleController.getString("ExperimentalMusicSpeedControlAbout", R.string.ExperimentalMusicSpeedControlAbout), LocaleController.getString("ExperimentalMusicSpeedControl", R.string.ExperimentalMusicSpeedControl)));
    private final AbstractConfigCell enableEditFileNameRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.enableEditFileName, LocaleController.getString("ExperimentalEditFileNameAbout", R.string.ExperimentalEditFileNameAbout), LocaleController.getString("ExperimentalEditFileName", R.string.ExperimentalEditFileName)));
    private final AbstractConfigCell enableChangeNameInGroupsRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.enableChangeNameInGroups, LocaleController.getString("ExperimentalChangeSenderNameAbout", R.string.ExperimentalChangeSenderNameAbout), LocaleController.getString("ExperimentalChangeSenderName", R.string.ExperimentalChangeSenderName)));
    private final AbstractConfigCell sendVideoAsRoundRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSendVideoAsRound()));

    public NekoCustomizationSettingsActivity() {
        if (!NekoConfig.showQuickEditIconInChatList.Bool()) {
            cellGroup.rows.remove(quickEditIconOnlyOwnRow);
        }
        addRowsToMap(cellGroup);
    }

    @Override
    public View createView(Context context) {
        View superView = super.createView(context);

        listAdapter = new ListAdapter(context);

        listView.setAdapter(listAdapter);
        listView.invalidateItemDecorations();

        setupDefaultListeners();

        cellGroup.callBackSettingsChanged = (key, newValue) -> {
            if (key.equals(NekoConfig.showQuickEditIconInChatList.getKey())) {
                if ((boolean) newValue) {
                    if (!cellGroup.rows.contains(quickEditIconOnlyOwnRow)) {
                        final int index = cellGroup.rows.indexOf(showQuickEditIconRow) + 1;
                        cellGroup.rows.add(index, quickEditIconOnlyOwnRow);
                        listAdapter.notifyItemInserted(index);
                    }
                } else {
                    if (cellGroup.rows.contains(quickEditIconOnlyOwnRow)) {
                        final int index = cellGroup.rows.indexOf(quickEditIconOnlyOwnRow);
                        cellGroup.rows.remove(quickEditIconOnlyOwnRow);
                        listAdapter.notifyItemRemoved(index);
                    }
                }
                addRowsToMap(cellGroup);
            } else if (key.equals(NekoConfig.forceMusicSpeedControl.getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.enableEditFileName.getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.enableChangeNameInGroups.getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            }
        };

        return superView;
    }

    @Override
    public int getBaseGuid() {
        return 14000;
    }

    @Override
    public int getDrawable() {
        return R.drawable.msg_theme;
    }

    @Override
    public String getTitle() {
        return LocaleController.getString("Customization", R.string.Customization);
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }
    }
}
