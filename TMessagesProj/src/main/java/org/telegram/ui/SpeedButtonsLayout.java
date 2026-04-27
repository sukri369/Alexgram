package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.PathParser;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSlider;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PopupSwipeBackLayout;
import org.telegram.ui.Components.SpeedIconDrawable;

public class SpeedButtonsLayout extends PopupSwipeBackLayout {

    private final LinearLayout mainLayout;
    private final LinearLayout customLayout;
    private final CustomSpeedSlider speedSlider;
    private final ActionBarMenuSubItem customItem;
    private final ActionBarMenuSubItem[] speedItems = new ActionBarMenuSubItem[5];
    private final Callback callback;

    public SpeedButtonsLayout(Context context, Callback callback) {
        super(context, null);
        this.callback = callback;

        mainLayout = new LinearLayout(context);
        mainLayout.setOrientation(LinearLayout.VERTICAL);

        addOnSwipeBackProgressListener((layout, toProgress, progress) -> {
            if (getParent() instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) getParent();
                for (int i = 0; i < parent.getChildCount(); i++) {
                    View child = parent.getChildAt(i);
                    if (child != this) {
                        child.setAlpha(1f - progress);
                        child.setTranslationY(AndroidUtilities.dp(16) * progress);
                        if (progress >= 1f) {
                            child.setVisibility(GONE);
                        } else if (child.getVisibility() != VISIBLE) {
                            child.setVisibility(VISIBLE);
                        }
                    }
                }
            }
        });

        for (int i = 0; i < 5; i++) {
            int icon;
            String text;
            float speed;
            if (i == 0) {
                icon = R.drawable.msg_speed_0_2;
                text = LocaleController.getString(R.string.SpeedVerySlow);
                speed = 0.2f;
            } else if (i == 1) {
                icon = R.drawable.msg_speed_slow;
                text = LocaleController.getString(R.string.SpeedSlow);
                speed = 0.5f;
            } else if (i == 2) {
                icon = R.drawable.msg_speed_normal;
                text = LocaleController.getString(R.string.SpeedNormal);
                speed = 1.0f;
            } else if (i == 3) {
                icon = R.drawable.msg_speed_fast;
                text = LocaleController.getString(R.string.SpeedFast);
                speed = 1.5f;
            } else {
                icon = R.drawable.msg_speed_superfast;
                text = LocaleController.getString(R.string.SpeedVeryFast);
                speed = 2.0f;
            }

            ActionBarMenuSubItem item = ActionBarMenuItem.addItem(mainLayout, icon, text, false, null);
            item.setColors(0xffffffff, 0xffffffff);
            item.setOnClickListener((view) -> {
                callback.onSpeedSelected(speed, true, true);
            });
            item.setSelectorColor(0x1affffff);
            speedItems[i] = item;
        }

        customItem = new ActionBarMenuSubItem(context, false, false);
        customItem.setTextAndIcon(LocaleController.getString("PollV2PollDurationOptionCustom", R.string.PollV2PollDurationOptionCustom), 0, new GenericPathDrawable("M20 13C20 15.2091 19.1046 17.2091 17.6569 18.6569L19.0711 20.0711C20.8807 18.2614 22 15.7614 22 13 22 7.47715 17.5228 3 12 3 6.47715 3 2 7.47715 2 13 2 15.7614 3.11929 18.2614 4.92893 20.0711L6.34315 18.6569C4.89543 17.2091 4 15.2091 4 13 4 8.58172 7.58172 5 12 5 16.4183 5 20 8.58172 20 13ZM15.293 8.29297 10.793 12.793 12.2072 14.2072 16.7072 9.70718 15.293 8.29297Z", 24, 24));
        customItem.setColors(0xffffffff, 0xffffffff);
        customItem.setOnClickListener((view) -> {
            openForeground(1);
            try {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            } catch (Exception ignore) {}
        });
        customItem.setSelectorColor(0x1affffff);
        mainLayout.addView(customItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        addView(mainLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        customLayout = new LinearLayout(context);
        customLayout.setOrientation(LinearLayout.VERTICAL);
        customLayout.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));

        ActionBarMenuSubItem backItem = new ActionBarMenuSubItem(context, false, false);
        backItem.setTextAndIcon(LocaleController.getString("Back", R.string.Back), R.drawable.ic_ab_back);
        backItem.setColors(0xffffffff, 0xffffffff);
        backItem.setOnClickListener((view) -> closeForeground());
        backItem.setSelectorColor(0x1affffff);
        customLayout.addView(backItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        speedSlider = new CustomSpeedSlider(context, null);
        speedSlider.setOnValueChange((value, isFinal) -> {
            callback.onSpeedSelected(speedSlider.getSpeed(value), isFinal, false);
        });
        customLayout.addView(speedSlider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, 16, 8, 16, 8));

        addView(customLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    public void update(float currentVideoSpeed, boolean isFinal) {
        boolean custom = true;
        for (int a = 0; a < speedItems.length; a++) {
            boolean selected = false;
            if (a == 0 && Math.abs(currentVideoSpeed - 0.2f) < 0.01f) selected = true;
            else if (a == 1 && Math.abs(currentVideoSpeed - 0.5f) < 0.1f) selected = true;
            else if (a == 2 && Math.abs(currentVideoSpeed - 1.0f) < 0.1f) selected = true;
            else if (a == 3 && Math.abs(currentVideoSpeed - 1.5f) < 0.1f) selected = true;
            else if (a == 4 && Math.abs(currentVideoSpeed - 2.0f) < 0.1f) selected = true;

            if (selected) {
                speedItems[a].setColors(0xff6BB6F9, 0xff6BB6F9);
                custom = false;
            } else {
                speedItems[a].setColors(0xffffffff, 0xffffffff);
            }
        }
        if (custom) {
            customItem.setColors(0xff6BB6F9, 0xff6BB6F9);
        } else {
            customItem.setColors(0xffffffff, 0xffffffff);
        }
        speedSlider.setSpeed(currentVideoSpeed, false);
    }

    public interface Callback {
        void onSpeedSelected(float speed, boolean isFinal, boolean closeMenu);
    }

    private static class CustomSpeedSlider extends ActionBarMenuSlider.SpeedSlider {

        public static final float MIN_SPEED = 0.2f;
        public static final float MAX_SPEED = 16.0f;

        public CustomSpeedSlider(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);
        }

        @Override
        public float getSpeed(float value) {
            return MIN_SPEED + (MAX_SPEED - MIN_SPEED) * value;
        }

        @Override
        public float getSpeed() {
            return getSpeed(getValue());
        }

        @Override
        public void setSpeed(float speed, boolean animated) {
            setValue((speed - MIN_SPEED) / (MAX_SPEED - MIN_SPEED), animated);
        }

        @Override
        protected String getLeftStringValue(float value) {
            return SpeedIconDrawable.formatNumber(getSpeed(value)) + "x";
        }

        @Override
        protected String getRightStringValue(float value) {
            return null;
        }

        @Override
        protected int getColorValue(float value) {
            final float speed = getSpeed(value);
            return ColorUtils.blendARGB(
                0xff6BB6F9,
                0xff3196f0,
                MathUtils.clamp((speed - 1f) / (16f - 1f), 0, 1)
            );
        }
    }

    private static class GenericPathDrawable extends Drawable {
        private final Path path;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int width;
        private final int height;

        public GenericPathDrawable(String pathData, int width, int height) {
            this.path = PathParser.createPathFromPathData(pathData);
            this.width = width;
            this.height = height;
            paint.setColor(0xffffffff);
            paint.setStyle(Paint.Style.FILL);
            paint.setAntiAlias(true);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            canvas.save();
            float scale = Math.min((float) bounds.width() / width, (float) bounds.height() / height) * 0.8f;
            canvas.translate(bounds.centerX() - (width * scale) / 2f, bounds.centerY() - (height * scale) / 2f);
            canvas.scale(scale, scale);
            canvas.drawPath(path, paint);
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return AndroidUtilities.dp(width);
        }

        @Override
        public int getIntrinsicHeight() {
            return AndroidUtilities.dp(height);
        }
    }
}
