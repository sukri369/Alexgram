package tw.nekomimi.nekogram.config.cell;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberPicker;

import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.ConfigItem;

public class ConfigCellNumberPicker extends AbstractConfigCell implements WithBindConfig, WithKey, WithOnClick {
    private final ConfigItem bindConfig;
    private final int min;
    private final int max;
    private final String title;
    private final String key;

    public ConfigCellNumberPicker(String key, ConfigItem bind, int min, int max) {
        this.bindConfig = bind;
        this.key = key != null ? key : bindConfig.getKey();
        this.min = min;
        this.max = max;
        this.title = LocaleController.getString(this.key);
    }

    @Override
    public int getType() {
        return CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL;
    }

    @Override
    public ConfigItem getBindConfig() {
        return this.bindConfig;
    }

    @Override
    public String getKey() {
        return this.key;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder) {
        TextSettingsCell cell = (TextSettingsCell) holder.itemView;
        cell.setTextAndValue(title, String.valueOf(bindConfig.Int()), false, cellGroup.needSetDivider(this), true);
    }

    @Override
    public void onClick() {
        Context context = cellGroup.thisFragment.getParentActivity();
        if (context == null) {
            return;
        }

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setGravity(Gravity.CENTER);

        NumberPicker picker = new NumberPicker(context);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(bindConfig.Int());

        linearLayout.addView(picker, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 10, 0, 10));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialogInterface, i) -> {
            int newVal = picker.getValue();
            int activeCount = org.telegram.messenger.UserConfig.getActivatedAccountsCount();
            if ("MaxActiveAccounts".equals(getKey()) && newVal < activeCount) {
                AlertDialog.Builder warningBuilder = new AlertDialog.Builder(context);
                warningBuilder.setTitle(LocaleController.getString("Warning", R.string.Warning));
                warningBuilder.setMessage(LocaleController.formatString("MaxActiveAccountsWarning", R.string.MaxActiveAccountsWarning, activeCount, newVal));
                warningBuilder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface2, i2) -> {
                    saveAndNotify(newVal);
                });
                warningBuilder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                cellGroup.thisFragment.showDialog(warningBuilder.create());
            } else {
                saveAndNotify(newVal);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        cellGroup.thisFragment.showDialog(builder.create());
    }

    private void saveAndNotify(int newVal) {
        bindConfig.setConfigInt(newVal);
        if (cellGroup.listAdapter != null) {
            cellGroup.listAdapter.notifyItemChanged(cellGroup.rows.indexOf(this));
        }
        cellGroup.runCallback(bindConfig.getKey(), newVal);
    }
}
