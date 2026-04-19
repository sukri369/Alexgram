package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class SettingsSearchResultCell extends FrameLayout {

    private final ImageView iconView;
    private final TextView titleView;
    private final TextView subtitleView;
    private final TextView categoryView;
    private final ImageView deleteButton;
    private final View divider;
    private final FrameLayout iconBackground;

    private boolean isDark;

    public SettingsSearchResultCell(Context context) {
        super(context);
        
        isDark = Theme.getActiveTheme().isDark();
        int textColor = isDark ? Color.WHITE : 0xFF1A1A2E;
        int subTextColor = isDark ? 0xAAFFFFFF : 0xAA5C6B7F;

        setPadding(dp(16), dp(8), dp(16), dp(8));
        
        iconBackground = new FrameLayout(context);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setCornerRadius(dp(12));
        iconBg.setColor(isDark ? 0x1A64B5F6 : 0x1A2196F3);
        iconBackground.setBackground(iconBg);
        addView(iconBackground, LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL | Gravity.LEFT));

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        iconView.setColorFilter(new PorterDuffColorFilter(0xFF2196F3, PorterDuff.Mode.SRC_IN));
        iconBackground.addView(iconView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 56, 0, 80, 0));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setTextColor(textColor);
        titleView.setSingleLine(true);
        texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        subtitleView.setTextColor(subTextColor);
        subtitleView.setSingleLine(true);
        texts.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        categoryView = new TextView(context);
        categoryView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
        categoryView.setTypeface(AndroidUtilities.bold());
        categoryView.setTextColor(0xFF2196F3);
        categoryView.setPadding(dp(6), dp(2), dp(6), dp(2));
        GradientDrawable catBg = new GradientDrawable();
        catBg.setCornerRadius(dp(4));
        catBg.setColor(isDark ? 0x222196F3 : 0x112196F3);
        categoryView.setBackground(catBg);
        addView(categoryView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT));

        deleteButton = new ImageView(context);
        deleteButton.setScaleType(ImageView.ScaleType.CENTER);
        deleteButton.setImageResource(R.drawable.ic_close_white);
        deleteButton.setColorFilter(new PorterDuffColorFilter(isDark ? 0x44FFFFFF : 0x44000000, PorterDuff.Mode.SRC_IN));
        deleteButton.setBackground(Theme.createSelectorSimpleDrawable(context, isDark ? 0x22FFFFFF : 0x11000000, 0x00000000));
        deleteButton.setVisibility(GONE);
        addView(deleteButton, LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 4, 0));

        divider = new View(context);
        divider.setBackgroundColor(isDark ? 0x10FFFFFF : 0x10000000);
        addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM, 56, 0, 0, 0));
        
        setBackground(Theme.getSelectorDrawable(false));
    }

    public void setData(SettingsSearchManager.SearchItem item, boolean last) {
        titleView.setText(item.title);
        subtitleView.setText(item.subtitle);
        categoryView.setText(item.category.toUpperCase());
        iconView.setImageResource(item.iconRes);
        divider.setVisibility(last ? GONE : VISIBLE);
        deleteButton.setVisibility(GONE);
        categoryView.setVisibility(VISIBLE);
    }

    public void setCanDelete(Runnable onDelete) {
        if (onDelete == null) {
            deleteButton.setVisibility(GONE);
            categoryView.setVisibility(VISIBLE);
            deleteButton.setOnClickListener(null);
        } else {
            deleteButton.setVisibility(VISIBLE);
            categoryView.setVisibility(GONE);
            deleteButton.setOnClickListener(v -> onDelete.run());
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(64), MeasureSpec.EXACTLY));
    }
}
