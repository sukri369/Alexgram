package tw.nekomimi.nekogram.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.text.method.PasswordTransformationMethod;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.IntDef;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.CodeFieldContainer;
import org.telegram.ui.CodeNumberField;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CustomPhoneKeyboardView;
import org.telegram.ui.Components.LayoutHelper;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import tw.nekomimi.nekogram.helpers.HiddenChatsController;
import tw.nekomimi.nekogram.settings.HiddenChatsSettingsActivity;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.FingerprintController;
import org.telegram.messenger.LocaleController;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.Executor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.HapticFeedbackConstants;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;

public class HiddenChatsPasscodeActivity extends BaseFragment {

    public static final int MODE_UNLOCK_CHATS = 0;
    public static final int MODE_UNLOCK_SETTINGS = 1;
    public static final int MODE_SETUP_PASSCODE = 2;
    public static final int MODE_CHANGE_PASSCODE = 3;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({MODE_UNLOCK_CHATS, MODE_UNLOCK_SETTINGS, MODE_SETUP_PASSCODE, MODE_CHANGE_PASSCODE})
    public @interface Mode {
    }

    @Mode
    private final int mode;

    private int setupStep;
    private int changePasscodeStep;
    private String firstPasscode;
    private String currentPasscodeVerified;

    private TextView titleView;
    private TextView subtitleView;
    private TextView errorView;
    private HudContainerView containerView;
    private CodeFieldContainer codeFieldContainer;
    private CustomPhoneKeyboardView keyboardView;
    private CyberBackgroundView backgroundView;
    private ImageView fingerprintImage;

    private String targetTitle;
    private String targetSubtitle;

    private boolean stableIsDark;

    public HiddenChatsPasscodeActivity(@Mode int mode) {
        this.mode = mode;
    }

    @Override
    public boolean onFragmentCreate() {
        stableIsDark = Theme.isCurrentThemeDark();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
    }

    private void updateThemeColors() {
        if (actionBar == null) return;
        boolean isDark = stableIsDark;
        int color = isDark ? Color.WHITE : 0xFF1A1A2E;
        int primaryColor = isDark ? 0xFF00E5FF : 0xFF00ACC1;
        int textColor = isDark ? Color.WHITE : 0xFF212121;
        int subTextColor = isDark ? Color.argb(200, 255, 255, 255) : Color.argb(200, 66, 66, 66);

        actionBar.setBackgroundColor(0);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSelector), false);
        actionBar.setItemsColor(color, false);
        actionBar.setTitleColor(color);

        if (rootView != null) {
            rootView.setBackgroundColor(isDark ? 0xFF000205 : 0xFFFAFAFA);
        }

        if (titleView != null) titleView.setTextColor(primaryColor);
        if (subtitleView != null) subtitleView.setTextColor(subTextColor);

        if (codeFieldContainer != null) {
            for (int i = 0; i < codeFieldContainer.codeField.length; i++) {
                CodeNumberField f = codeFieldContainer.codeField[i];
                if (f != null) {
                    f.setTextColor(textColor);
                    f.setCursorColor(primaryColor);
                    f.invalidate();
                }
            }
        }

        if (fingerprintImage != null) {
            fingerprintImage.setColorFilter(new android.graphics.PorterDuffColorFilter(primaryColor, android.graphics.PorterDuff.Mode.SRC_IN));
            fingerprintImage.setBackground(Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(64), Color.TRANSPARENT, Color.argb(isDark ? 40 : 20, 0, 229, 255)));
        }

        if (backgroundView != null) backgroundView.invalidate();
        if (containerView != null) containerView.invalidate();

        if (getParentActivity() != null) {
            AndroidUtilities.setLightStatusBar(getParentActivity().getWindow(), !isDark);
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setCastShadows(false);
        actionBar.setTitle("HIDDEN CHATS");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        boolean isDark = stableIsDark;
        FrameLayout root = new FrameLayout(context);
        rootView = root;
        root.setBackgroundColor(isDark ? 0xFF000205 : 0xFFFAFAFA);

        int primaryColor = isDark ? 0xFF00E5FF : 0xFF00ACC1;
        int textColor = isDark ? Color.WHITE : 0xFF212121;
        int subTextColor = isDark ? Color.argb(200, 255, 255, 255) : Color.argb(200, 66, 66, 66);

        backgroundView = new CyberBackgroundView(context);
        root.addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        containerView = new HudContainerView(context);
        root.addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 28, 64, 28, 0));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        containerView.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 20, 0, 20));

        titleView = new TextView(context);
        titleView.setTextColor(primaryColor);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 28);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleView.setLetterSpacing(0.06f);
        content.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        subtitleView = new TextView(context);
        subtitleView.setTextColor(subTextColor);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitleView.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(8), AndroidUtilities.dp(32), 0);
        subtitleView.setAllCaps(true);
        content.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        codeFieldContainer = new CodeFieldContainer(context) {
            @Override
            protected void processNextPressed() {
                handleCodeEntered();
            }
        };
        codeFieldContainer.setNumbersCount(4, CodeFieldContainer.TYPE_PASSCODE);
        for (int i = 0; i < codeFieldContainer.codeField.length; i++) {
            final CodeNumberField f = codeFieldContainer.codeField[i];
            f.setShowSoftInputOnFocusCompat(false);
            f.setTransformationMethod(PasswordTransformationMethod.getInstance());
            f.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 32);
            f.setTextColor(textColor);
            f.setCursorColor(primaryColor);
            f.setCursorWidth(AndroidUtilities.dp(2));
            
            // Custom high-tech underline background
            f.setBackground(new android.graphics.drawable.Drawable() {
                private Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override
                public void draw(@NonNull Canvas canvas) {
                    boolean focused = f.isFocused();
                    p.setColor(primaryColor);
                    p.setStrokeWidth(AndroidUtilities.dp(focused ? 3 : 1.5f));
                    p.setAlpha(focused ? 255 : 100);
                    float y = canvas.getHeight() - AndroidUtilities.dp(2);
                    canvas.drawLine(0, y, canvas.getWidth(), y, p);
                    
                    if (focused) {
                        p.setAlpha(40);
                        canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), p);
                    }
                }
                @Override public void setAlpha(int alpha) {}
                @Override public void setColorFilter(android.graphics.ColorFilter cf) {}
                @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
            });

            f.setPadding(0, 0, 0, AndroidUtilities.dp(8));
            f.setOnFocusChangeListener((v, hasFocus) -> {
                keyboardView.setEditText(f);
                keyboardView.setDispatchBackWhenEmpty(true);
                if (hasFocus) {
                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
                v.invalidate();
            });
        }
        content.addView(codeFieldContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 40, 0, 0));

        errorView = new TextView(context);
        errorView.setTextColor(0xFFFF3D00);
        errorView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        errorView.setGravity(Gravity.CENTER_HORIZONTAL);
        errorView.setVisibility(View.INVISIBLE);
        errorView.setPadding(0, AndroidUtilities.dp(20), 0, 0);
        errorView.setAllCaps(true);
        content.addView(errorView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        if (android.os.Build.VERSION.SDK_INT >= 23 && (mode == MODE_UNLOCK_CHATS || mode == MODE_UNLOCK_SETTINGS)) {
            fingerprintImage = new ImageView(context);
            fingerprintImage.setImageResource(R.drawable.fingerprint);
            fingerprintImage.setScaleType(ImageView.ScaleType.CENTER);
            fingerprintImage.setColorFilter(new android.graphics.PorterDuffColorFilter(primaryColor, android.graphics.PorterDuff.Mode.SRC_IN));
            fingerprintImage.setBackground(Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(64), Color.TRANSPARENT, Color.argb(isDark ? 40 : 20, 0, 229, 255)));
            fingerprintImage.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                checkFingerprint();
            });
            fingerprintImage.setContentDescription(LocaleController.getString(R.string.AccDescrFingerprint));
            fingerprintImage.setVisibility(HiddenChatsController.getInstance().isBiometricEnabled() ? View.VISIBLE : View.GONE);
            content.addView(fingerprintImage, LayoutHelper.createLinear(64, 64, Gravity.CENTER_HORIZONTAL, 0, 30, 0, 0));
        }

        keyboardView = new CustomPhoneKeyboardView(context);
        keyboardView.setBackgroundColor(0);
        root.addView(keyboardView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP, Gravity.BOTTOM, 8, 0, 8, 16));

        root.setOnClickListener(v -> focusFirstEmptyField());

        updateTexts();
        focusFirstEmptyField();

        updateThemeColors();
        
        runGodEntranceAnimation();

        fragmentView = root;
        return fragmentView;
    }

    private FrameLayout rootView;

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        
        // Use a delegate to force re-application of our stable colors whenever the global theme changes
        // This prevents the ActionBarLayout from overriding our transparent header with white
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, this::updateThemeColors, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_windowBackgroundWhite));
        
        return themeDescriptions;
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAltFocusable(getParentActivity(), classGuid);
        AndroidUtilities.hideKeyboard(fragmentView);
        updateThemeColors();
        if (mode == MODE_UNLOCK_CHATS || mode == MODE_UNLOCK_SETTINGS) {
            checkFingerprint();
        }
    }

    private void checkFingerprint() {
        if (android.os.Build.VERSION.SDK_INT < 23) {
            return;
        }
        if (!HiddenChatsController.getInstance().isBiometricEnabled()) {
            return;
        }
        android.app.Activity parentActivity = getParentActivity();
        if (parentActivity instanceof FragmentActivity) {
            try {
                if (BiometricManager.from(getContext()).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS && FingerprintController.isKeyReady() && !FingerprintController.checkDeviceFingerprintsChanged()) {
                    final Executor executor = ContextCompat.getMainExecutor(getContext());
                    BiometricPrompt prompt = new BiometricPrompt((FragmentActivity) parentActivity, executor, new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationError(int errMsgId, @NonNull CharSequence errString) {
                            FileLog.d("HiddenChatsPasscodeActivity onAuthenticationError " + errMsgId + " \"" + errString + "\"");
                            focusFirstEmptyField();
                        }

                        @Override
                        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                            FileLog.d("HiddenChatsPasscodeActivity onAuthenticationSucceeded");
                            AndroidUtilities.runOnUIThread(() -> {
                                HiddenChatsController.getInstance().unlock();
                                if (mode == MODE_UNLOCK_SETTINGS) {
                                    presentFragment(new HiddenChatsSettingsActivity(), true);
                                } else {
                                    presentFragment(new tw.nekomimi.nekogram.ui.HiddenChatsActivity(new android.os.Bundle()), true);
                                }
                            });
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            FileLog.d("HiddenChatsPasscodeActivity onAuthenticationFailed");
                            AndroidUtilities.runOnUIThread(() -> showInlineError("Fingerprint not recognized"));
                        }
                    });
                    final BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Hidden Chats")
                            .setNegativeButtonText(LocaleController.getString(R.string.UsePIN))
                            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                            .build();
                    prompt.authenticate(promptInfo);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        AndroidUtilities.removeAltFocusable(getParentActivity(), classGuid);
    }

    private void updateTexts() {
        if (mode == MODE_SETUP_PASSCODE) {
            if (setupStep == 0) {
                targetTitle = "INIT NEW KEY";
                targetSubtitle = "Establish 4-digit security protocol.";
            } else {
                targetTitle = "CONFIRM KEY";
                targetSubtitle = "Verify integrity of the new passcode.";
            }
        } else if (mode == MODE_CHANGE_PASSCODE) {
            if (changePasscodeStep == 0) {
                targetTitle = "VERIFY CURRENT";
                targetSubtitle = "Verification required for decryption change.";
            } else if (changePasscodeStep == 1) {
                targetTitle = "GEN NEW PASS";
                targetSubtitle = "Generate a new 4-digit access code.";
            } else {
                targetTitle = "VERIFY NEW";
                targetSubtitle = "Finalize the encryption update.";
            }
        } else {
            targetTitle = "HIDDEN ACCESS";
            targetSubtitle = "Authentication needed to access secure chats.";
        }
        
        runDecryptionAnim(titleView, targetTitle);
        runDecryptionAnim(subtitleView, targetSubtitle);
    }

    private void focusFirstEmptyField() {
        if (codeFieldContainer == null || codeFieldContainer.codeField == null) {
            return;
        }
        for (int i = 0; i < codeFieldContainer.codeField.length; i++) {
            if (codeFieldContainer.codeField[i].length() == 0) {
                codeFieldContainer.codeField[i].requestFocus();
                AndroidUtilities.showKeyboard(codeFieldContainer.codeField[i]);
                return;
            }
        }
        codeFieldContainer.codeField[codeFieldContainer.codeField.length - 1].requestFocus();
        AndroidUtilities.showKeyboard(codeFieldContainer.codeField[codeFieldContainer.codeField.length - 1]);
    }

    private String getCode() {
        return codeFieldContainer != null ? codeFieldContainer.getCode() : "";
    }

    private void clearCode() {
        if (codeFieldContainer == null || codeFieldContainer.codeField == null) {
            return;
        }
        for (int i = 0; i < codeFieldContainer.codeField.length; i++) {
            codeFieldContainer.codeField[i].setText("");
        }
        focusFirstEmptyField();
    }

    private void showInlineError(String message) {
        errorView.setText(message);
        errorView.setVisibility(View.VISIBLE);
        if (codeFieldContainer != null) {
            AndroidUtilities.shakeViewSpring(codeFieldContainer, 4f);
            ValueAnimator colorAnim = ValueAnimator.ofArgb(Color.WHITE, 0xFFff3b30, Color.WHITE);
            colorAnim.setDuration(400);
            colorAnim.addUpdateListener(anim -> {
                int color = (int) anim.getAnimatedValue();
                for (CodeNumberField f : codeFieldContainer.codeField) {
                    f.setTextColor(color);
                }
            });
            colorAnim.start();
        }
        if (fragmentView != null) {
            fragmentView.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        }
    }

    private void clearInlineError() {
        errorView.setText("");
        errorView.setVisibility(View.INVISIBLE);
    }

    private void handleCodeEntered() {
        String code = getCode();
        if (code.length() != 4) {
            showInlineError("Passcode must be 4 digits");
            return;
        }

        clearInlineError();
        HiddenChatsController controller = HiddenChatsController.getInstance();

        if (mode == MODE_SETUP_PASSCODE) {
            if (setupStep == 0) {
                firstPasscode = code;
                setupStep = 1;
                clearCode();
                updateTexts();
                return;
            }
            if (!code.equals(firstPasscode)) {
                showInlineError("Passcodes do not match");
                setupStep = 0;
                firstPasscode = null;
                clearCode();
                updateTexts();
                return;
            }
            controller.setPasscode(code);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.done, "Hidden Chats Setup Complete").show();
            finishFragment();
            return;
        }

        if (mode == MODE_CHANGE_PASSCODE) {
            if (changePasscodeStep == 0) {
                if (!controller.checkPasscode(code)) {
                    showInlineError("Incorrect Passcode");
                    clearCode();
                    return;
                }
                currentPasscodeVerified = code;
                changePasscodeStep = 1;
                clearCode();
                updateTexts();
                return;
            }
            if (changePasscodeStep == 1) {
                firstPasscode = code;
                changePasscodeStep = 2;
                clearCode();
                updateTexts();
                return;
            }
            if (!code.equals(firstPasscode)) {
                showInlineError("Passcodes do not match");
                changePasscodeStep = 1;
                firstPasscode = null;
                clearCode();
                updateTexts();
                return;
            }
            controller.setPasscode(code);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.done, "Passcode Changed Successfully").show();
            finishFragment();
            return;
        }

        if (!controller.checkPasscode(code)) {
            showInlineError("Incorrect Passcode");
            clearCode();
            return;
        }

        controller.unlock();
        if (fragmentView != null) {
            fragmentView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
        if (mode == MODE_UNLOCK_SETTINGS) {
            presentFragment(new HiddenChatsSettingsActivity(), true);
        } else {
            presentFragment(new HiddenChatsActivity(new Bundle()), true);
        }
    }

    private void runDecryptionAnim(TextView view, String targetText) {
        if (targetText == null) return;
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(800);
        anim.addUpdateListener(animation -> {
            float p = (float) animation.getAnimatedValue();
            StringBuilder sb = new StringBuilder();
            int charsToReveal = (int) (targetText.length() * p);
            for (int i = 0; i < targetText.length(); i++) {
                if (i < charsToReveal) {
                    sb.append(targetText.charAt(i));
                } else {
                    char c = (char) ('!' + new Random().nextInt(90));
                    sb.append(c);
                }
            }
            view.setText(sb.toString());
        });
        anim.start();
    }

    private void runGodEntranceAnimation() {
        containerView.setAlpha(0);
        containerView.setScaleX(1.2f);
        containerView.setScaleY(1.2f);
        keyboardView.setAlpha(0);
        keyboardView.setTranslationY(AndroidUtilities.dp(150));

        if (fingerprintImage != null) {
            fingerprintImage.setAlpha(0f);
            fingerprintImage.setScaleX(0f);
            fingerprintImage.setScaleY(0f);
        }

        AndroidUtilities.runOnUIThread(() -> {
            containerView.animate().alpha(1).scaleX(1).scaleY(1).setDuration(800).setInterpolator(new OvershootInterpolator(0.8f)).start();
            keyboardView.animate().alpha(1).translationY(0).setDuration(1000).setStartDelay(300).setInterpolator(AndroidUtilities.overshootInterpolator).start();
            
            if (fingerprintImage != null) {
                fingerprintImage.animate().alpha(1).scaleX(1).scaleY(1).setDuration(600).setStartDelay(800).setInterpolator(new OvershootInterpolator(1.4f)).start();
            }
        }, 100);
    }

    private class CyberBackgroundView extends View {
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ArrayList<Particle> particles = new ArrayList<>();
        private Random random = new Random();
        private long lastTime;
        private LinearGradient bgGradient;
        private float glitchOffset;
        private int frameCount;

        public CyberBackgroundView(Context context) {
            super(context);
            for (int i = 0; i < 40; i++) particles.add(new Particle());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            long now = SystemClock.elapsedRealtime();
            float dt = lastTime == 0 ? 0.016f : (now - lastTime) / 1000f;
            lastTime = now;
            frameCount++;
            
            boolean isDark = stableIsDark;
            int color1 = isDark ? 0xFF00050A : 0xFFFAFAFA;
            int color2 = isDark ? 0xFF061B3D : 0xFFECEFF1;
            int accentColor = isDark ? 0xFF00E5FF : 0xFF00ACC1;

            // Mesh Base
            bgGradient = new LinearGradient(0, 0, 0, getHeight(), color1, color2, Shader.TileMode.CLAMP);
            paint.setShader(bgGradient);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(null);

            // Glitch Math
            glitchOffset = 0;
            if (frameCount % (isDark ? 120 : 240) == 0) glitchOffset = (random.nextFloat() - 0.5f) * AndroidUtilities.dp(8);
            
            canvas.save();
            canvas.translate(glitchOffset, 0);

            // Plexus Lines
            paint.setStrokeWidth(AndroidUtilities.dp(0.5f));
            for (int i = 0; i < particles.size(); i++) {
                Particle p1 = particles.get(i);
                p1.update(dt, getWidth(), getHeight());
                for (int j = i + 1; j < particles.size(); j++) {
                    Particle p2 = particles.get(j);
                    float dist = (float) Math.hypot(p1.x - p2.x, p1.y - p2.y);
                    if (dist < AndroidUtilities.dp(120)) {
                        paint.setColor(accentColor);
                        paint.setAlpha((int) ((1f - dist / AndroidUtilities.dp(120)) * (isDark ? 60 : 40)));
                        canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint);
                    }
                }
            }

            // Particles
            for (Particle p : particles) p.draw(canvas, accentColor, isDark);

            // Scanlines
            paint.setColor(isDark ? Color.WHITE : Color.BLACK);
            paint.setAlpha(isDark ? 12 : 6);
            paint.setStrokeWidth(AndroidUtilities.dp(1));
            float scanPos = (now % 4000) / 4000f * getHeight();
            canvas.drawLine(0, scanPos, getWidth(), scanPos, paint);

            canvas.restore();
            invalidate();
        }

        private class Particle {
            float x, y, vx, vy, radius, alpha;
            void reset(int w, int h) {
                x = random.nextFloat() * w; y = random.nextFloat() * h;
                vx = (random.nextFloat() - 0.5f) * 40; vy = (random.nextFloat() - 0.5f) * 40;
                radius = 1 + random.nextFloat() * 2; alpha = 0.2f + random.nextFloat() * 0.5f;
            }
            void update(float dt, int w, int h) {
                if (x == 0) reset(w, h);
                x += vx * dt; y += vy * dt;
                if (x < 0) x = w; if (x > w) x = 0;
                if (y < 0) y = h; if (y > h) y = 0;
            }
            void draw(Canvas canvas, int color, boolean isDark) {
                paint.setColor(color);
                paint.setAlpha((int) (alpha * (isDark ? 255 : 180)));
                canvas.drawCircle(x, y, AndroidUtilities.dp(radius), paint);
            }
        }
    }

    private class HudContainerView extends FrameLayout {
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float scanLinePos = 0;
        private boolean scanDown = true;

        public HudContainerView(Context context) {
            super(context);
            setWillNotDraw(false);
            setBackgroundColor(Color.argb(30, 0, 0, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            boolean isDark = stableIsDark;
            int primaryColor = isDark ? 0xFF00E5FF : 0xFF00ACC1;
            
            // Glass Base
            paint.setColor(Color.argb(isDark ? 40 : 25, 0, 229, 255));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(1));
            canvas.drawRect(AndroidUtilities.dp(2), AndroidUtilities.dp(2), w - AndroidUtilities.dp(2), h - AndroidUtilities.dp(2), paint);
            
            // Corners
            paint.setColor(primaryColor);
            paint.setStrokeWidth(AndroidUtilities.dp(3));
            float len = AndroidUtilities.dp(20);
            // TL
            canvas.drawLine(0, 0, len, 0, paint); canvas.drawLine(0, 0, 0, len, paint);
            // TR
            canvas.drawLine(w, 0, w - len, 0, paint); canvas.drawLine(w, 0, w, len, paint);
            // BL
            canvas.drawLine(0, h, len, h, paint); canvas.drawLine(0, h, 0, h - len, paint);
            // BR
            canvas.drawLine(w, h, w - len, h, paint); canvas.drawLine(w, h, w, h - len, paint);

            // Laser Scanner
            paint.setStrokeWidth(AndroidUtilities.dp(2));
            paint.setAlpha(isDark ? 100 : 80);
            canvas.drawLine(AndroidUtilities.dp(4), scanLinePos, w - AndroidUtilities.dp(4), scanLinePos, paint);
            
            if (scanDown) {
                scanLinePos += AndroidUtilities.dp(2);
                if (scanLinePos > h) scanDown = false;
            } else {
                scanLinePos -= AndroidUtilities.dp(2);
                if (scanLinePos < 0) scanDown = true;
            }

            // Tech Text Overlay (Decorative)
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(AndroidUtilities.dp(8));
            paint.setColor(isDark ? 0xFF00E5FF : 0xFF00ACC1);
            paint.setAlpha(isDark ? 120 : 180);
            canvas.drawText("SEC_LEVEL: ALPHA", AndroidUtilities.dp(10), AndroidUtilities.dp(15), paint);
            canvas.drawText("ENCR_MODE: AES_256", w - AndroidUtilities.dp(85), h - AndroidUtilities.dp(10), paint);

            invalidate();
        }
    }
}
