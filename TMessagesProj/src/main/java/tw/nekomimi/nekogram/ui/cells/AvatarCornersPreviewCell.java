package tw.nekomimi.nekogram.ui.cells;

import android.content.Context;
import android.os.Build;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AvatarCornerHelper;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import xyz.nextalone.nagram.NaConfig;

public class AvatarCornersPreviewCell extends FrameLayout {

    private final RectF rect = new RectF();
    private final Path onlineCutoutPath = new Path();
    private final Paint mockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint brightMockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final AltSeekbar seekBar;
    private final FrameLayout preview;
    private float committedAvatarCorners;
    private float previewAvatarCorners;

    public AvatarCornersPreviewCell(Context context, Runnable onValueChanged) {
        super(context);

        setWillNotDraw(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        committedAvatarCorners = NaConfig.INSTANCE.getAvatarCorners().Float();
        previewAvatarCorners = committedAvatarCorners;

        seekBar = new AltSeekbar(
                context,
                (value, stop) -> {
                    previewAvatarCorners = value;
                    invalidate();
                    boolean valueChanged = Float.compare(committedAvatarCorners, value) != 0;
                    if (stop && valueChanged) {
                        committedAvatarCorners = value;
                        NaConfig.INSTANCE.getAvatarCorners().setConfigFloat(value);
                        if (onValueChanged != null) {
                            onValueChanged.run();
                        }
                    }
                },
                0,
                28,
                LocaleController.getString(R.string.AvatarCorners),
                LocaleController.getString(R.string.AvatarCornersLeft),
                LocaleController.getString(R.string.AvatarCornersRight)
        );
        seekBar.setProgress(previewAvatarCorners / 28.0f);
        addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        preview = new FrameLayout(context) {
            @SuppressWarnings("deprecation")
            @Override
            protected void onDraw(Canvas canvas) {
                drawMock(canvas, getMeasuredWidth());
            }
        };
        preview.setWillNotDraw(false);
        // Inner card background -- AyuGram-style 12dp rounded fill + 0.5dp stroke
        // (mirrors com.exteragram.messenger.preferences.components.PreviewBackgroundDrawable).
        preview.setBackground(new PreviewCardDrawable());
        preview.setMinimumHeight(AndroidUtilities.dp(83));
        addView(preview, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP,
                21,
                114,
                21,
                21
        ));
    }

    /**
     * Returns a 0..1 squareness factor: 1 when the avatar is fully square
     * (corners = 0) and 0 when fully circular (corners = 28). Mirrors
     * {@code ExteraConfig.getAvatarSquareness} so the online-dot geometry
     * matches AyuGram's preview pixel-for-pixel.
     */
    private float getAvatarSquareness() {
        float v = 1f - (previewAvatarCorners / 28f);
        if (v < 0f) v = 0f;
        if (v > 1f) v = 1f;
        return v;
    }

    /**
     * Online-dot anchor offset that follows the avatar shape: stays on the
     * cardinal axis when round, slides toward the corner along the diagonal
     * (radius / sqrt(2)) when square. Same formula as AyuGram's
     * {@code ExteraConfig.getOnlineDotOffset}.
     */
    private float getOnlineDotOffset(float baseDp, float outerRadius) {
        float diag = (float) (outerRadius / Math.sqrt(2.0));
        return baseDp + (diag - baseDp) * getAvatarSquareness();
    }

    @SuppressWarnings("deprecation")
    private void drawMock(Canvas canvas, float width) {
        // Geometry constants ported verbatim from
        // com.exteragram.messenger.preferences.appearance.components.AvatarCornersPreviewCell#drawMock.
        float dp1 = AndroidUtilities.dp(1f);
        float left = AndroidUtilities.dp(15f);
        float textStart = AndroidUtilities.dp(83f);
        float avatarSize = AndroidUtilities.dp(56f);
        float avatarTop = AndroidUtilities.dp(12f) + dp1;
        float avatarRight = left + avatarSize;
        float avatarBottom = avatarTop + avatarSize;

        mockPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), 0.2f));
        brightMockPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), 0.4f));

        // Online dot scales with the squareness factor: bigger + further out
        // when square, smaller + tucked in when round.
        float squareness = getAvatarSquareness();
        float outerRadius = AndroidUtilities.dpf2(2f * squareness + 7f);
        float innerRadius = AndroidUtilities.dpf2(squareness + 5f);
        float onlineCx = avatarRight - getOnlineDotOffset(AndroidUtilities.dpf2(8f), outerRadius);
        float onlineCy = avatarBottom - getOnlineDotOffset(AndroidUtilities.dpf2(7.5f), outerRadius);

        // Avatar with a circular cut-out for the online dot's outer halo.
        canvas.save();
        onlineCutoutPath.reset();
        onlineCutoutPath.addCircle(onlineCx, onlineCy, outerRadius, Path.Direction.CCW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canvas.clipOutPath(onlineCutoutPath);
        } else {
            canvas.clipPath(onlineCutoutPath, Region.Op.DIFFERENCE);
        }
        rect.set(left, avatarTop, avatarRight, avatarBottom);
        int avatarCornerRadius = AvatarCornerHelper.getAvatarRoundRadius(
                56f,
                previewAvatarCorners,
                false,
                false,
                NaConfig.INSTANCE.getSingleCornerRadius().Bool()
        );
        canvas.drawRoundRect(rect, avatarCornerRadius, avatarCornerRadius, brightMockPaint);
        canvas.restore();

        // Inner online dot (green).
        Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_chats_onlineCircle));
        canvas.drawCircle(onlineCx, onlineCy, innerRadius, Theme.dialogs_onlineCirclePaint);

        // Mock text rows: title (top-left, brighter), timestamp (top-right),
        // and two snippet lines beneath -- widths are width-relative so the
        // layout breathes on tablets / RTL the same as AyuGram's preview.
        float rowRadius = AndroidUtilities.dp(4f);
        rect.set(
                textStart + AndroidUtilities.dp(6f),
                AndroidUtilities.dp(16f) + dp1,
                textStart + AndroidUtilities.dp(6f) + 0.4f * width,
                AndroidUtilities.dp(24.33f) + dp1
        );
        canvas.drawRoundRect(rect, rowRadius, rowRadius, brightMockPaint);

        rect.set(
                textStart + AndroidUtilities.dp(6f),
                AndroidUtilities.dp(38f) + dp1,
                textStart + AndroidUtilities.dp(6f) + 0.5f * width,
                AndroidUtilities.dp(46.33f) + dp1
        );
        canvas.drawRoundRect(rect, rowRadius, rowRadius, mockPaint);

        rect.set(
                textStart + AndroidUtilities.dp(6f),
                AndroidUtilities.dp(56f) + dp1,
                textStart + AndroidUtilities.dp(6f) + 0.36f * width,
                AndroidUtilities.dp(64.33f) + dp1
        );
        canvas.drawRoundRect(rect, rowRadius, rowRadius, mockPaint);

        rect.set(
                width - AndroidUtilities.dp(16f) - AndroidUtilities.dp(43f),
                AndroidUtilities.dp(16f) + dp1,
                width - AndroidUtilities.dp(16f),
                AndroidUtilities.dp(24.33f) + dp1
        );
        canvas.drawRoundRect(rect, rowRadius, rowRadius, mockPaint);
    }

    /**
     * Inner-card background drawable: 12dp-radius fill + 0.5dp stroke.
     * Colour values mirror exteraGram's PreviewColors so the preview matches
     * AyuGram's appearance section in both light and dark themes.
     */
    private static final class PreviewCardDrawable extends Drawable {
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rectF = new RectF();
        private final float radius = AndroidUtilities.dp(12f);

        PreviewCardDrawable() {
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(AndroidUtilities.dpf2(0.5f));
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            float baseAlpha = Theme.isCurrentThemeDark() ? 0.05f : 0.035f;
            int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
            backgroundPaint.setColor(Theme.multAlpha(textColor, baseAlpha));
            strokePaint.setColor(Theme.multAlpha(textColor, baseAlpha + 0.085f));
            float sw = strokePaint.getStrokeWidth() / 2f;
            rectF.set(
                    getBounds().left + sw,
                    getBounds().top + sw,
                    getBounds().right - sw,
                    getBounds().bottom - sw
            );
            canvas.drawRoundRect(rectF, radius, radius, backgroundPaint);
            canvas.drawRoundRect(rectF, radius, radius, strokePaint);
        }

        @Override
        public void setAlpha(int alpha) {
            backgroundPaint.setAlpha(alpha);
            strokePaint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            backgroundPaint.setColorFilter(colorFilter);
            strokePaint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (preview != null) {
            preview.invalidate();
        }
        if (seekBar != null) {
            seekBar.invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawLine(
                LocaleController.isRTL ? 0.0f : AndroidUtilities.dp(21.0f),
                getMeasuredHeight() - 1,
                getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(21.0f) : 0),
                getMeasuredHeight() - 1,
                Theme.dividerPaint
        );
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(widthMeasureSpec), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(222.0f), View.MeasureSpec.EXACTLY)
        );
    }
}
