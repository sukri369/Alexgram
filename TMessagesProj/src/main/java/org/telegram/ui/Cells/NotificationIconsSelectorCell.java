package org.telegram.ui.Cells;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter;
import org.telegram.ui.LauncherIconController;
import org.telegram.ui.PremiumPreviewFragment;

import java.util.ArrayList;
import java.util.List;

import xyz.nextalone.nagram.NaConfig;

public class NotificationIconsSelectorCell extends RecyclerListView implements NotificationCenter.NotificationCenterDelegate {

    private List<IconItem> availableIcons = new ArrayList<>();
    private LinearLayoutManager linearLayoutManager;
    private int currentAccount;
    private BaseFragment fragment;

    public NotificationIconsSelectorCell(Context context, BaseFragment fragment, int currentAccount) {
        super(context);
        this.fragment = fragment;
        this.currentAccount = currentAccount;
        setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));

        setFocusable(false);
        setItemAnimator(null);
        setLayoutAnimation(null);

        setLayoutManager(linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        setAdapter(new SelectionAdapter() {
            @Override
            public boolean isEnabled(ViewHolder holder) {
                return true;
            }

            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RecyclerListView.Holder(new IconHolderView(parent.getContext()));
            }

            @Override
            public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                IconHolderView holderView = (IconHolderView) holder.itemView;
                IconItem item = availableIcons.get(position);
                holderView.bind(item, position);
                holderView.setOnClickListener(v -> selectIcon(position));
            }

            @Override
            public int getItemCount() {
                return availableIcons.size();
            }
        });
        addItemDecoration(new ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull State state) {
                int pos = parent.getChildViewHolder(view).getAdapterPosition();
                if (pos == 0) {
                    outRect.left = AndroidUtilities.dp(18);
                }
                if (pos == getAdapter().getItemCount() - 1) {
                    outRect.right = AndroidUtilities.dp(18);
                } else {
                    outRect.right = AndroidUtilities.dp(24);
                }
            }
        });
        setOnItemClickListener((view, position) -> selectIcon(position));
        updateIconsVisibility();
    }

    private void selectIcon(int position) {
        IconItem item = availableIcons.get(position);
        if (item.premium && !UserConfig.hasPremiumOnAccounts()) {
            fragment.showDialog(new PremiumFeatureBottomSheet(fragment, PremiumPreviewFragment.PREMIUM_FEATURE_APPLICATION_ICONS, true));
            return;
        }

        if (NaConfig.INSTANCE.getNotificationIcon().Int() == position) {
            return;
        }

        LinearSmoothScroller smoothScroller = new LinearSmoothScroller(getContext()) {
            @Override
            public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int snapPreference) {
                return boxStart - viewStart + AndroidUtilities.dp(16);
            }

            @Override
            protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                return super.calculateSpeedPerPixel(displayMetrics) * 3f;
            }
        };
        smoothScroller.setTargetPosition(position);
        linearLayoutManager.startSmoothScroll(smoothScroller);

        int oldPosition = NaConfig.INSTANCE.getNotificationIcon().Int();
        NaConfig.INSTANCE.getNotificationIcon().setConfigInt(position);
        getAdapter().notifyItemChanged(oldPosition);
        getAdapter().notifyItemChanged(position);
        BulletinFactory.of(fragment).createSimpleBulletin(R.drawable.msg_info, LocaleController.getString("RestartRequired", R.string.RestartRequired)).show();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateIconsVisibility() {
        availableIcons.clear();

        for (LauncherIconController.LauncherIcon icon : LauncherIconController.LauncherIcon.values()) {
            if (icon == LauncherIconController.LauncherIcon.TELEGRAM ||
                icon == LauncherIconController.LauncherIcon.VINTAGE ||
                icon == LauncherIconController.LauncherIcon.AQUA ||
                icon == LauncherIconController.LauncherIcon.PREMIUM ||
                icon == LauncherIconController.LauncherIcon.TURBO ||
                icon == LauncherIconController.LauncherIcon.NOX) {
                continue;
            }
            availableIcons.add(new IconItem(icon));
        }

        if (MessagesController.getInstance(currentAccount).premiumFeaturesBlocked()) {
            for (int i = 0; i < availableIcons.size(); i++) {
                if (availableIcons.get(i).premium) {
                    availableIcons.remove(i);
                    i--;
                }
            }
        }
        getAdapter().notifyDataSetChanged();
        invalidateItemDecorations();

        int current = NaConfig.INSTANCE.getNotificationIcon().Int();
        if (current >= 0 && current < availableIcons.size()) {
            linearLayoutManager.scrollToPositionWithOffset(current, AndroidUtilities.dp(16));
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.premiumStatusChangedGlobal);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.premiumStatusChangedGlobal);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.premiumStatusChangedGlobal) {
            updateIconsVisibility();
        }
    }
    private static class IconItem {
        int resId;
        int foregroundResId;
        int titleResId;
        boolean premium;

        IconItem(int resId, int foregroundResId, int titleResId) {
            this.resId = resId;
            this.foregroundResId = foregroundResId;
            this.titleResId = titleResId;
        }

        IconItem(LauncherIconController.LauncherIcon icon) {
            this.resId = icon.background;
            this.foregroundResId = icon.foreground;
            this.titleResId = icon.title;
            this.premium = icon.premium;
        }
    }

    private final static class IconHolderView extends LinearLayout {
        private Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private AppIconsSelectorCell.AdaptiveIconImageView iconView;
        private TextView titleView;

        private float progress;

        private IconHolderView(@NonNull Context context) {
            super(context);
            setOrientation(VERTICAL);
            setWillNotDraw(false);

            iconView = new AppIconsSelectorCell.AdaptiveIconImageView(context);
            iconView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
            iconView.setClickable(false);
            addView(iconView, LayoutHelper.createLinear(58, 58, Gravity.CENTER_HORIZONTAL));

            titleView = new TextView(context);
            titleView.setSingleLine();
            titleView.setClickable(false);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));

            outlinePaint.setStyle(Paint.Style.STROKE);
            outlinePaint.setStrokeWidth(Math.max(2, AndroidUtilities.dp(0.5f)));

            fillPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }

        @Override
        public void draw(Canvas canvas) {
            float stroke = outlinePaint.getStrokeWidth();
            AndroidUtilities.rectTmp.set(iconView.getLeft() + stroke, iconView.getTop() + stroke, iconView.getRight() - stroke, iconView.getBottom() - stroke);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(AppIconsSelectorCell.ICONS_ROUND_RADIUS), AndroidUtilities.dp(AppIconsSelectorCell.ICONS_ROUND_RADIUS), fillPaint);
            super.draw(canvas);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(AppIconsSelectorCell.ICONS_ROUND_RADIUS), AndroidUtilities.dp(AppIconsSelectorCell.ICONS_ROUND_RADIUS), outlinePaint);
        }

        private void setProgress(float progress) {
            this.progress = progress;
            titleView.setTextColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), Theme.getColor(Theme.key_windowBackgroundWhiteValueText), progress));
            outlinePaint.setColor(ColorUtils.blendARGB(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_switchTrack), 0x3F), Theme.getColor(Theme.key_windowBackgroundWhiteValueText), progress));
            outlinePaint.setStrokeWidth(Math.max(2, AndroidUtilities.dp(AndroidUtilities.lerp(0.5f, 2f, progress))));
            invalidate();
        }

        public void setSelected(boolean selected, boolean animate) {
            float to = selected ? 1 : 0;
            if (to == progress && animate) return;

            if (animate) {
                ValueAnimator animator = ValueAnimator.ofFloat(progress, to).setDuration(250);
                animator.setInterpolator(Easings.easeInOutQuad);
                animator.addUpdateListener(animation -> setProgress((Float) animation.getAnimatedValue()));
                animator.start();
            } else {
                setProgress(to);
            }
        }

        public void bind(IconItem item, int position) {
            iconView.setImageResource(item.resId);
            iconView.setForeground(item.foregroundResId);
            titleView.setText(LocaleController.getString(item.titleResId));
            setSelected(NaConfig.INSTANCE.getNotificationIcon().Int() == position, false);
        }
    }
}
