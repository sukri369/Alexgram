package org.telegram.ui.Templates;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class TemplateCell extends android.widget.LinearLayout {
    private final BackupImageView avatarView;
    private final TextView titleView;
    private final TextView subtitleView;
    private final ImageView previewButton;
    private final ImageView sendButton;
    private boolean needDivider;
    private TemplateSettings template;

    public TemplateCell(@NonNull Context context) {
        super(context);
        setWillNotDraw(false);
        setPadding(dp(16), dp(7), dp(8), dp(7));
        setOrientation(android.widget.LinearLayout.HORIZONTAL);
        setGravity(android.view.Gravity.CENTER_VERTICAL);

        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(dp(20));
        addView(avatarView, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL));

        android.widget.LinearLayout textContainer = new android.widget.LinearLayout(context);
        textContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
        addView(textContainer, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 14, 0, 12, 0));

        titleView = new TextView(context);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(16);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        textContainer.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 22));

        subtitleView = new TextView(context);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setTextSize(14);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        textContainer.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 20));

        previewButton = new ImageView(context);
        previewButton.setScaleType(ImageView.ScaleType.CENTER);
        previewButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));
        previewButton.setImageResource(R.drawable.msg_views_solar);
        previewButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.SRC_IN));
        previewButton.setContentDescription(LocaleController.getString(R.string.Open));
        addView(previewButton, LayoutHelper.createLinear(42, 42, Gravity.CENTER_VERTICAL));

        sendButton = new ImageView(context);
        sendButton.setScaleType(ImageView.ScaleType.CENTER);
        sendButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));
        sendButton.setImageResource(R.drawable.ic_send);
        sendButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelSend), PorterDuff.Mode.SRC_IN));
        addView(sendButton, LayoutHelper.createLinear(42, 42, Gravity.CENTER_VERTICAL));
    }

    public void bind(TemplateSettings template, boolean divider, Runnable previewCallback, Runnable sendCallback) {
        this.template = template;
        needDivider = divider;
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo((int) template.id, template.name, null);
        avatarView.setImageDrawable(avatarDrawable);
        titleView.setText(template.name);
        String preview = template.text.replace('\n', ' ').trim();
        if (TextUtils.isEmpty(preview) && template.hasMessages()) {
            preview = LocaleController.formatPluralString("Messages", template.getMessageCount());
        }
        if (template.usageRating > 0) {
            subtitleView.setText(LocaleController.getString(R.string.chat_template_subtitle_sent) + " " + LocaleController.formatPluralString("Times", template.usageRating) + " - " + preview);
        } else {
            subtitleView.setText(preview);
        }
        previewButton.setOnClickListener(v -> {
            if (previewCallback != null) {
                previewCallback.run();
            }
        });
        sendButton.setOnClickListener(v -> {
            if (sendCallback != null) {
                sendCallback.run();
            }
        });
        invalidate();
    }

    public TemplateSettings getTemplate() {
        return template;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : dp(70), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? dp(70) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }
}
