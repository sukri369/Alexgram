package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tw.nekomimi.nekogram.TextViewEffects;

public class UpdateAppAlertDialog extends BottomSheet {

    private TLRPC.TL_help_appUpdate appUpdate;
    private int accountNum;
    private Context ctx;
    private ValueAnimator pulseAnimator;
    private float pulseProgress = 0f;
    private GradientHeaderView headerView;
    private GradientButton dlBtn;

    private static final int COLOR_A = 0xFF0F62FE;
    private static final int COLOR_B = 0xFF8434F7;
    private static final int COLOR_C = 0xFFAA20FF;

    public UpdateAppAlertDialog(Context context, TLRPC.TL_help_appUpdate update, int account) {
        super(context, false);
        this.ctx = context;
        appUpdate = update;
        accountNum = account;
        setCanceledOnTouchOutside(false);
        setApplyTopPadding(false);
        setApplyBottomPadding(false);

        FrameLayout root = new FrameLayout(context);
        containerView = root;

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        root.addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // ── Header ──────────────────────────────────────────────────
        headerView = new GradientHeaderView(context);
        card.addView(headerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 195));

        // ── Changelog ───────────────────────────────────────────────
        boolean hasLog = !TextUtils.isEmpty(appUpdate.text);
        if (hasLog) {
            TextView label = new TextView(context);
            label.setText("WHAT'S NEW");
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10.5f);
            label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            label.setTypeface(AndroidUtilities.bold());
            label.setLetterSpacing(0.13f);
            card.addView(label, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 18, 20, 6));

            View accentLine = new View(context) {
                final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
                    p.setShader(new LinearGradient(0, 0, w, 0,
                        new int[]{COLOR_A, COLOR_C, 0x00000000},
                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
                }
                @Override protected void onDraw(Canvas c) { c.drawRect(0, 0, getWidth(), getHeight(), p); }
            };
            accentLine.setWillNotDraw(false);
            card.addView(accentLine, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 2, 20, 0, 0, 0));

            ScrollView sv = new ScrollView(context) {
                @Override protected void onMeasure(int ws, int hs) {
                    super.onMeasure(ws, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(170), MeasureSpec.AT_MOST));
                }
            };
            sv.setVerticalScrollBarEnabled(false);

            TextView log = new TextViewEffects(context);
            log.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            log.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            log.setLineSpacing(AndroidUtilities.dp(4), 1f);

            SpannableStringBuilder ssb = new SpannableStringBuilder(appUpdate.text);
            if (appUpdate.entities != null && !appUpdate.entities.isEmpty()) {
                MessageObject.addEntitiesToText(ssb, appUpdate.entities, false, false, false, false);
            }
            log.setText(ssb);
            sv.addView(log, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 20, 10, 20, 8));
            card.addView(sv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        card.addView(new View(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, hasLog ? 16 : 26));

        // ── Download Button ─────────────────────────────────────────
        dlBtn = new GradientButton(context, LocaleController.getString(R.string.AppUpdateDownloadNow));
        dlBtn.setOnClickListener(v -> onDownloadClicked());
        card.addView(dlBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 54, 20, 0, 20, 0));

        // ── Remind Later ────────────────────────────────────────────
        TextView laterBtn = new TextView(context);
        laterBtn.setText(LocaleController.getString(R.string.AppUpdateRemindMeLater));
        laterBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        laterBtn.setTextColor(0x99000000 | (Theme.getColor(Theme.key_featuredStickers_addButton) & 0xFFFFFF));
        laterBtn.setGravity(Gravity.CENTER);
        laterBtn.setOnClickListener(v -> dismiss());
        card.addView(laterBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 20, 8, 20, 20));

        startPulseAnimation();
    }

    // ── Download Logic ───────────────────────────────────────────────
    private void onDownloadClicked() {
        if ((appUpdate.flags & 2) != 0 && appUpdate.document != null) {
            // Already have document → download directly
            startDocumentDownload(appUpdate.document);
        } else if (!TextUtils.isEmpty(appUpdate.url)) {
            String url = appUpdate.url;
            Matcher m = Pattern.compile("t\\.me/([^/?#\\s]+)/(\\d+)").matcher(url);
            if (m.find()) {
                // It's a t.me channel link → fetch the APK document from that message
                fetchDocumentFromTelegramUrl(m.group(1), Integer.parseInt(m.group(2)));
            } else {
                // Direct URL → open in browser as fallback
                Browser.openUrl(ctx, url);
                dismiss();
            }
        }
    }

    /**
     * Resolves a Telegram channel message URL, extracts the APK document from it,
     * then downloads it through Telegram's own file system (no browser needed).
     */
    private void fetchDocumentFromTelegramUrl(String username, int msgId) {
        dlBtn.setLoading(true);

        TLRPC.TL_contacts_resolveUsername resolveReq = new TLRPC.TL_contacts_resolveUsername();
        resolveReq.username = username;

        ConnectionsManager.getInstance(accountNum).sendRequest(resolveReq, (res1, err1) ->
            AndroidUtilities.runOnUIThread(() -> {
                if (err1 != null || !(res1 instanceof TLRPC.TL_contacts_resolvedPeer)) {
                    fallbackBrowser();
                    return;
                }
                TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) res1;
                MessagesController mc = MessagesController.getInstance(accountNum);
                mc.putUsers(resolved.users, false);
                mc.putChats(resolved.chats, false);
                MessagesStorage.getInstance(accountNum).putUsersAndChats(resolved.users, resolved.chats, false, true);

                if (resolved.chats.isEmpty()) { fallbackBrowser(); return; }

                long channelId = resolved.chats.get(0).id;
                TLRPC.InputChannel inputChannel = mc.getInputChannel(channelId);
                if (inputChannel == null) { fallbackBrowser(); return; }

                TLRPC.TL_channels_getMessages getMsgReq = new TLRPC.TL_channels_getMessages();
                getMsgReq.channel = inputChannel;
                getMsgReq.id = new ArrayList<>(Collections.singletonList(msgId));

                ConnectionsManager.getInstance(accountNum).sendRequest(getMsgReq, (res2, err2) ->
                    AndroidUtilities.runOnUIThread(() -> {
                        if (err2 != null || !(res2 instanceof TLRPC.messages_Messages)) {
                            fallbackBrowser(); return;
                        }
                        TLRPC.messages_Messages msgs = (TLRPC.messages_Messages) res2;
                        if (msgs.messages.isEmpty()) { fallbackBrowser(); return; }

                        TLRPC.Message msg = msgs.messages.get(0);
                        if (msg.media instanceof TLRPC.TL_messageMediaDocument
                                && msg.media.document != null) {
                            startDocumentDownload(msg.media.document);
                        } else {
                            fallbackBrowser();
                        }
                    })
                );
            })
        );
    }

    private void startDocumentDownload(TLRPC.Document doc) {
        // Attach document to pendingAppUpdate so UpdateLayout can track progress
        appUpdate.document = doc;
        appUpdate.flags |= 2;
        if (SharedConfig.pendingAppUpdate != null) {
            SharedConfig.pendingAppUpdate.document = doc;
            SharedConfig.pendingAppUpdate.flags |= 2;
            SharedConfig.saveConfig();
        }
        FileLoader.getInstance(accountNum).loadFile(doc, "update", FileLoader.PRIORITY_NORMAL, 1);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
        dismiss();
    }

    private void fallbackBrowser() {
        dlBtn.setLoading(false);
        if (!TextUtils.isEmpty(appUpdate.url)) {
            Browser.openUrl(ctx, appUpdate.url);
        }
        dismiss();
    }

    // ── Pulse animation ──────────────────────────────────────────────
    private void startPulseAnimation() {
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
        pulseAnimator.setDuration(1800);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseAnimator.addUpdateListener(a -> {
            pulseProgress = (float) a.getAnimatedValue();
            if (headerView != null) headerView.invalidate();
        });
        pulseAnimator.start();
    }

    @Override
    protected boolean canDismissWithSwipe() { return false; }

    @Override
    public void dismiss() {
        if (pulseAnimator != null) { pulseAnimator.cancel(); pulseAnimator = null; }
        super.dismiss();
    }

    // ════════════════════════════════════════════════════════════════
    // INNER: Gradient Header
    // ════════════════════════════════════════════════════════════════
    private class GradientHeaderView extends View {
        private final Paint bgPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint decorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF badgeRect  = new RectF();
        private final Path  clipPath   = new Path();
        private int lastW, lastH;

        GradientHeaderView(Context c) { super(c); setWillNotDraw(false); }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (w == lastW && h == lastH) return;
            lastW = w; lastH = h;
            bgPaint.setShader(new LinearGradient(0, 0, w, h,
                new int[]{COLOR_A, COLOR_B, COLOR_C}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            clipPath.reset();
            float r = AndroidUtilities.dp(20);
            clipPath.addRoundRect(new RectF(0, 0, w, h),
                new float[]{r, r, r, r, 0, 0, 0, 0}, Path.Direction.CW);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            canvas.save();
            canvas.clipPath(clipPath);
            canvas.drawRect(0, 0, w, h, bgPaint);
            decorPaint.setColor(0x14FFFFFF);
            canvas.drawCircle(w * 0.86f, h * 0.14f, AndroidUtilities.dp(68), decorPaint);
            decorPaint.setColor(0x0CFFFFFF);
            canvas.drawCircle(w * 0.08f, h * 0.82f, AndroidUtilities.dp(90), decorPaint);
            decorPaint.setColor(0x10FFFFFF);
            canvas.drawCircle(w * 0.5f,  h * -0.1f, AndroidUtilities.dp(80), decorPaint);
            canvas.restore();

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.WHITE);
            textPaint.setTypeface(AndroidUtilities.bold());
            textPaint.setTextSize(AndroidUtilities.dp(26));
            textPaint.setShadowLayer(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(2), 0x40000000);
            canvas.drawText("Alexgram", w / 2f, h * 0.32f, textPaint);
            textPaint.clearShadowLayer();

            textPaint.setTextSize(AndroidUtilities.dp(12.5f));
            textPaint.setTypeface(Typeface.DEFAULT);
            textPaint.setColor(0xCCFFFFFF);
            canvas.drawText("Update Available", w / 2f, h * 0.47f, textPaint);

            String vLabel = "v " + appUpdate.version;
            textPaint.setTextSize(AndroidUtilities.dp(13));
            textPaint.setTypeface(AndroidUtilities.bold());
            textPaint.setColor(Color.WHITE);
            float tw = textPaint.measureText(vLabel);
            float padH = AndroidUtilities.dp(14), padV = AndroidUtilities.dp(6);
            float bw = tw + padH * 2, bh = AndroidUtilities.dp(13) + padV * 2;
            float cx = w / 2f, cy = h * 0.70f, br = bh / 2f;
            badgeRect.set(cx - bw/2, cy - bh/2, cx + bw/2, cy + bh/2);

            float glowR = bw * 0.55f * (1f + pulseProgress * 0.35f);
            int glowA = (int)(50 + pulseProgress * 110);
            glowPaint.setShader(new RadialGradient(cx, cy, glowR,
                new int[]{Color.argb(glowA, 140, 60, 255), 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, glowR, glowPaint);

            badgePaint.setStyle(Paint.Style.FILL);
            badgePaint.setColor(0x28FFFFFF);
            canvas.drawRoundRect(badgeRect, br, br, badgePaint);

            badgePaint.setStyle(Paint.Style.STROKE);
            badgePaint.setStrokeWidth(AndroidUtilities.dp(1.3f));
            badgePaint.setColor(Color.argb((int)(140 + pulseProgress * 115), 255, 255, 255));
            canvas.drawRoundRect(badgeRect, br, br, badgePaint);
            badgePaint.setStyle(Paint.Style.FILL);

            canvas.drawText(vLabel, cx, cy + AndroidUtilities.dp(4.5f), textPaint);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // INNER: Gradient Button with loading state
    // ════════════════════════════════════════════════════════════════
    private static class GradientButton extends FrameLayout {
        private final Paint bgPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txtPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF btnRect  = new RectF();
        private final String defaultText;
        private String currentText;
        private boolean loading = false;
        private float pressScale = 1f;
        private ValueAnimator pressAnim;
        private ValueAnimator loadingAnim;
        private float loadingAlpha = 1f;

        GradientButton(Context ctx, String text) {
            super(ctx);
            defaultText = text;
            currentText = text;
            setWillNotDraw(false);
            setClickable(true);
            txtPaint.setTextAlign(Paint.Align.CENTER);
            txtPaint.setColor(Color.WHITE);
            txtPaint.setTypeface(AndroidUtilities.bold());
            txtPaint.setTextSize(AndroidUtilities.dp(15));
            txtPaint.setShadowLayer(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(1), 0x30000000);
        }

        void setLoading(boolean isLoading) {
            loading = isLoading;
            setClickable(!isLoading);
            if (isLoading) {
                currentText = "Fetching APK…";
                if (loadingAnim == null) {
                    loadingAnim = ValueAnimator.ofFloat(1f, 0.4f, 1f);
                    loadingAnim.setDuration(900);
                    loadingAnim.setRepeatCount(ValueAnimator.INFINITE);
                    loadingAnim.addUpdateListener(a -> { loadingAlpha = (float) a.getAnimatedValue(); invalidate(); });
                    loadingAnim.start();
                }
            } else {
                currentText = defaultText;
                loadingAlpha = 1f;
                if (loadingAnim != null) { loadingAnim.cancel(); loadingAnim = null; }
            }
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            bgPaint.setShader(new LinearGradient(0, 0, w, 0,
                new int[]{COLOR_A, COLOR_B, COLOR_C}, null, Shader.TileMode.CLAMP));
            btnRect.set(0, 0, w, h);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            canvas.save();
            canvas.scale(pressScale, pressScale, w / 2f, h / 2f);
            canvas.drawRoundRect(btnRect, h / 2f, h / 2f, bgPaint);
            txtPaint.setAlpha((int)(255 * loadingAlpha));
            canvas.drawText(currentText, w / 2f, h / 2f + AndroidUtilities.dp(5.5f), txtPaint);
            txtPaint.setAlpha(255);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (loading) return false;
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN: animatePress(0.95f); break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: animatePress(1f); break;
            }
            return super.onTouchEvent(ev);
        }

        private void animatePress(float to) {
            if (pressAnim != null) pressAnim.cancel();
            pressAnim = ValueAnimator.ofFloat(pressScale, to);
            pressAnim.setDuration(120);
            pressAnim.addUpdateListener(a -> { pressScale = (float) a.getAnimatedValue(); invalidate(); });
            pressAnim.start();
        }
    }
}
