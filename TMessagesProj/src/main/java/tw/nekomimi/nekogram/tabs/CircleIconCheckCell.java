package tw.nekomimi.nekogram.tabs;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

public class CircleIconCheckCell extends FrameLayout {

    private final View circleView;
    private final RLottieImageView iconView;
    private final TextView nameTextView;
    private final CheckBox2 checkBox;

    private final int iconSize;
    private final ImageView.ScaleType iconScaleType;

    public CircleIconCheckCell(Context context, int iconSize, ImageView.ScaleType iconScaleType) {
        super(context);
        this.iconSize = iconSize;
        this.iconScaleType = iconScaleType;

        circleView = new View(context);
        addView(circleView, LayoutHelper.createFrame(62, 62, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 7, 0, 0));

        iconView = new RLottieImageView(context);
        iconView.setScaleType(iconScaleType);
        int topMargin = ((62 - iconSize) / 2) + 7;
        addView(iconView, LayoutHelper.createFrame(iconSize, iconSize, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, topMargin, 0, 0));

        nameTextView = new TextView(context);
        nameTextView.setAllCaps(true);
        nameTextView.setMaxLines(2);
        nameTextView.setGravity(Gravity.CENTER);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        nameTextView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 11.0f);
        nameTextView.setLines(2);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 6, 72, 6, 0));

        checkBox = new CheckBox2(context, 21);
        checkBox.setColor(-1, Theme.key_dialogBackground, Theme.key_dialogRoundCheckBoxCheck);
        checkBox.setDrawUnchecked(false);
        checkBox.setDrawBackgroundAsArc(4);
        checkBox.setProgressDelegate(progress -> {
            float scale = 1.0f - (checkBox.getProgress() * 0.143f);
            circleView.setScaleX(scale);
            circleView.setScaleY(scale);
        });
        addView(checkBox, LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 19, 48, 0, 0));
    }

    public boolean isChecked() {
        return checkBox.isChecked();
    }

    public void setChecked(boolean checked, boolean animated) {
        checkBox.setChecked(checked, animated);
    }

    public void setColor(int color) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setShape(GradientDrawable.OVAL);
        gradientDrawable.setColor(color);
        circleView.setBackground(gradientDrawable);
    }

    public void setFabIcon(FloatingActionButtonType fab) {
        fab.bindBig(iconView);
    }

    public void setName(String name) {
        nameTextView.setText(name);
    }
}
