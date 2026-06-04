package tw.nekomimi.nekogram.tabs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CheckBoxSquare;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

/**
 * A single row in the Tabs by Type settings list.
 *
 * Layout (same as iMe's DialogsSortingTabCell):
 *
 * |  [Checkbox]  [TypeIcon]  [Label …]  |  [VertDivider]  [CircleButton] |
 *
 * Height: 48dp
 */
public class TabsByTypeCell extends LinearLayout {

    private final CheckBoxSquare checkBox;
    private final ImageView      iconView;
    private final TextView       textView;
    private final android.view.View    vertDivider;
    private final FrameLayout    buttonLayout;
    private final RLottieImageView      actionButton;

    private boolean drawDivider;

    public TabsByTypeCell(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setWillNotDraw(false);

        // ── Left section: checkbox + icon + label ──────────────────────────────
        LinearLayout leftSection = new LinearLayout(context);
        leftSection.setOrientation(HORIZONTAL);
        leftSection.setGravity(Gravity.CENTER_VERTICAL);

        checkBox = new CheckBoxSquare(context, false);
        leftSection.addView(checkBox,
                LayoutHelper.createLinear(18, 18, Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        leftSection.addView(iconView,
                LayoutHelper.createLinear(34, 34, Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

        textView = new TextView(context);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        leftSection.addView(textView,
                LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f, Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

        addView(leftSection, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));

        // ── Vertical 1dp divider ───────────────────────────────────────────────
        vertDivider = new android.view.View(context);
        addView(vertDivider, LayoutHelper.createLinear(1, 24, Gravity.CENTER_VERTICAL));

        // ── Right section: circle action button (pencil or eye) ───────────────
        buttonLayout = new FrameLayout(context);

        actionButton = new RLottieImageView(context);
        actionButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        buttonLayout.addView(actionButton,
                LayoutHelper.createFrame(36, 36, Gravity.CENTER, 12, 6, 12, 6));

        addView(buttonLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        updateColors();
    }

    /** Bind the cell to a given tab type + state. */
    public void setTab(TabsByTypeEntry tab, boolean isChecked, boolean drawDivider, boolean isArchive) {
        this.drawDivider = drawDivider;
        setWillNotDraw(!drawDivider);

        checkBox.setChecked(isChecked, false);
        iconView.setImageResource(tab.iconResId);

        textView.setText(tab.getTitle(getContext()));

        if (isArchive) {
            vertDivider.setVisibility(GONE);
            buttonLayout.setVisibility(GONE);
        } else {
            vertDivider.setVisibility(VISIBLE);
            buttonLayout.setVisibility(VISIBLE);

            // Build the circle button drawable
            if (tab.isEditableFab) {
                actionButton.clearAnimationDrawable();
                FloatingActionButtonType fabType = TabsByTypeSettings.getInstance().getTabFabType(tab);
                fabType.bindBig(actionButton);
            } else {
                actionButton.clearAnimationDrawable();
                actionButton.setImageResource(R.drawable.baseline_visibility_24);
            }
        }

        updateColors();
    }

    /** Animate the checkbox when toggled from outside. */
    public void setChecked(boolean checked, boolean animated) {
        checkBox.setChecked(checked, animated);
    }

    public boolean isChecked() {
        return checkBox.isChecked();
    }

    public void toggleCheck() {
        checkBox.setChecked(!checkBox.isChecked(), true);
    }

    /** Set listener for the action button (pencil / eye). */
    public void setOnActionButtonClick(OnClickListener listener) {
        actionButton.setOnClickListener(listener);
        actionButton.setClickable(listener != null);
    }

    public void updateColors() {
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        int accentColor = Theme.getColor(Theme.key_chats_actionBackground);

        checkBox.invalidate();

        iconView.setColorFilter(new PorterDuffColorFilter(accentColor, PorterDuff.Mode.SRC_IN));

        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

        vertDivider.setBackgroundColor(Theme.getColor(Theme.key_divider));

        // The circle background for the action button
        Drawable circleBg = Theme.createCircleDrawable(AndroidUtilities.dp(36), accentColor);
        actionButton.setBackground(circleBg);
        actionButton.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                android.view.View.MeasureSpec.makeMeasureSpec(
                        android.view.View.MeasureSpec.getSize(widthMeasureSpec),
                        android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(
                        AndroidUtilities.dp(48),
                        android.view.View.MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (drawDivider) {
            float y = getMeasuredHeight() - 1f;
            canvas.drawLine(AndroidUtilities.dp(16), y, getMeasuredWidth(), y, Theme.dividerPaint);
        }
    }
}
