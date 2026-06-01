// [Alexgram: Customizable Message Menu] - Start
package tw.nekomimi.nekogram.ui.cells;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import tw.nekomimi.nekogram.settings.BaseNekoXSettingsActivity;

public class MessageMenuConfigCell extends LinearLayout {
    private final TextView titleView;
    private final LinearLayout buttonsContainer;
    private final TextView[] buttons = new TextView[3];
    private final String configKey;
    private final boolean defaultValue;
    private int currentMode;
    private final Runnable onChanged;

    public MessageMenuConfigCell(Context context, String key, String title, int iconResId, boolean defaultVal, Runnable onChanged) {
        super(context);
        this.configKey = key;
        this.defaultValue = defaultVal;
        this.onChanged = onChanged;
        this.currentMode = BaseNekoXSettingsActivity.getMessageMenuMode(key, defaultVal);

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

        if (iconResId > 0) {
            android.widget.ImageView iconView = new android.widget.ImageView(context);
            iconView.setImageResource(iconResId);
            iconView.setColorFilter(new android.graphics.PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), android.graphics.PorterDuff.Mode.MULTIPLY));
            addView(iconView, LayoutHelper.createLinear(24, 24, Gravity.CENTER, 0, 0, 12, 0));
        }

        titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        buttonsContainer = new LinearLayout(context);
        buttonsContainer.setOrientation(HORIZONTAL);
        addView(buttonsContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        String[] labels = {"Hide", "Text", "Icon"};
        for (int i = 0; i < 3; i++) {
            final int mode = i;
            buttons[i] = new TextView(context);
            buttons[i].setText(labels[i]);
            buttons[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            buttons[i].setGravity(Gravity.CENTER);
            buttons[i].setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(6));
            buttonsContainer.addView(buttons[i], LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, i < 2 ? 4 : 0, 0));

            buttons[i].setOnClickListener(v -> {
                updateSelected(mode);
                BaseNekoXSettingsActivity.setMessageMenuMode(configKey, mode);
                if (this.onChanged != null) this.onChanged.run();
            });
        }
        updateSelected(currentMode);
    }

    private void updateSelected(int selectedMode) {
        currentMode = selectedMode;
        int activeColor = Theme.getColor(Theme.key_featuredStickers_addButton);
        int inactiveColor = 0x10000000; // light grey for inactive
        if (Theme.getActiveTheme().isDark()) {
            inactiveColor = 0x20FFFFFF; // slightly lighter in dark mode
        }
        for (int i = 0; i < 3; i++) {
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(AndroidUtilities.dp(6));
            if (i == selectedMode) {
                gd.setColor(activeColor);
                buttons[i].setTextColor(Color.WHITE);
            } else {
                gd.setColor(inactiveColor);
                buttons[i].setTextColor(Theme.getColor(Theme.key_dialogTextGray));
            }
            buttons[i].setBackground(gd);
        }
    }
}
// [Alexgram: Customizable Message Menu] - End
