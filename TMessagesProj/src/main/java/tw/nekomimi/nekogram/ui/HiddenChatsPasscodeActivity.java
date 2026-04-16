package tw.nekomimi.nekogram.ui;

import android.content.Context;
import android.os.Bundle;
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
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.HapticFeedbackConstants;
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
    private FrameLayout containerView;
    private CodeFieldContainer codeFieldContainer;
    private CustomPhoneKeyboardView keyboardView;
    private AnimatedBackgroundView backgroundView;
    private ImageView fingerprintImage;

    public HiddenChatsPasscodeActivity(@Mode int mode) {
        this.mode = mode;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setCastShadows(false);
        actionBar.setTitle("Hidden Chats");
        actionBar.setBackgroundColor(0);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSelector), false);
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon), false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        
        backgroundView = new AnimatedBackgroundView(context);
        root.addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        containerView = new FrameLayout(context) {
            private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private RectF rect = new RectF();
            {
                paint.setColor(Color.argb(Theme.isCurrentThemeDark() ? 40 : 20, 255, 255, 255));
            }
            @Override
            protected void onDraw(Canvas canvas) {
                rect.set(0, 0, getWidth(), getHeight());
                canvas.drawRoundRect(rect, AndroidUtilities.dp(24), AndroidUtilities.dp(24), paint);
                super.onDraw(canvas);
            }
        };
        containerView.setWillNotDraw(false);
        containerView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(32), AndroidUtilities.dp(16), AndroidUtilities.dp(32));
        root.addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 80));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        containerView.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        titleView = new TextView(context);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 34);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        subtitleView = new TextView(context);
        subtitleView.setTextColor(Color.argb(180, 255, 255, 255));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitleView.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(12), AndroidUtilities.dp(24), 0);
        content.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        codeFieldContainer = new CodeFieldContainer(context) {
            @Override
            protected void processNextPressed() {
                handleCodeEntered();
            }
        };
        codeFieldContainer.setNumbersCount(4, CodeFieldContainer.TYPE_PASSCODE);
        for (CodeNumberField f : codeFieldContainer.codeField) {
            f.setShowSoftInputOnFocusCompat(false);
            f.setTransformationMethod(PasswordTransformationMethod.getInstance());
            f.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 28);
            f.setTextColor(Color.WHITE);
            f.setCursorColor(Color.WHITE);
            f.setCursorWidth(2);
            f.setOnFocusChangeListener((v, hasFocus) -> {
                keyboardView.setEditText(f);
                keyboardView.setDispatchBackWhenEmpty(true);
            });
        }
        content.addView(codeFieldContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 40, 0, 0));

        errorView = new TextView(context);
        errorView.setTextColor(0xFFff3b30);
        errorView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        errorView.setGravity(Gravity.CENTER_HORIZONTAL);
        errorView.setVisibility(View.INVISIBLE);
        errorView.setPadding(0, AndroidUtilities.dp(16), 0, 0);
        content.addView(errorView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        if (android.os.Build.VERSION.SDK_INT >= 23 && (mode == MODE_UNLOCK_CHATS || mode == MODE_UNLOCK_SETTINGS)) {
            fingerprintImage = new ImageView(context);
            fingerprintImage.setImageResource(R.drawable.fingerprint);
            fingerprintImage.setScaleType(ImageView.ScaleType.CENTER);
            fingerprintImage.setColorFilter(new android.graphics.PorterDuffColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN));
            fingerprintImage.setBackground(Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Color.TRANSPARENT, Color.argb(40, 255, 255, 255)));
            fingerprintImage.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                checkFingerprint();
            });
            fingerprintImage.setContentDescription(LocaleController.getString(R.string.AccDescrFingerprint));
            fingerprintImage.setVisibility(HiddenChatsController.getInstance().isBiometricEnabled() ? View.VISIBLE : View.GONE);
            content.addView(fingerprintImage, LayoutHelper.createLinear(56, 56, Gravity.CENTER_HORIZONTAL, 0, 30, 0, 0));
        }

        keyboardView = new CustomPhoneKeyboardView(context);
        // Custom styling for keyboard
        keyboardView.setBackgroundColor(0);
        root.addView(keyboardView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP, Gravity.BOTTOM, 0, 0, 0, 12));

        root.setOnClickListener(v -> focusFirstEmptyField());

        updateTexts();
        focusFirstEmptyField();
        
        runEntranceAnimation();

        fragmentView = root;
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAltFocusable(getParentActivity(), classGuid);
        AndroidUtilities.hideKeyboard(fragmentView);
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
                titleView.setText("Create Passcode");
                subtitleView.setText("Create a 4-digit passcode for Hidden Chats.");
            } else {
                titleView.setText("Confirm Passcode");
                subtitleView.setText("Enter the same 4 digits again.");
            }
        } else if (mode == MODE_CHANGE_PASSCODE) {
            if (changePasscodeStep == 0) {
                titleView.setText("Enter Current Passcode");
                subtitleView.setText("Verify your current passcode to change it.");
            } else if (changePasscodeStep == 1) {
                titleView.setText("Create New Passcode");
                subtitleView.setText("Create a new 4-digit passcode.");
            } else {
                titleView.setText("Confirm New Passcode");
                subtitleView.setText("Enter the same 4 digits again.");
            }
        } else {
            titleView.setText("Enter Passcode");
            subtitleView.setText("Use your Hidden Chats passcode to continue.");
        }
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

    private void runEntranceAnimation() {
        containerView.setAlpha(0);
        containerView.setScaleX(0.9f);
        containerView.setScaleY(0.9f);
        keyboardView.setAlpha(0);
        keyboardView.setTranslationY(AndroidUtilities.dp(100));

        titleView.setAlpha(0);
        titleView.setTranslationY(AndroidUtilities.dp(20));
        subtitleView.setAlpha(0);
        subtitleView.setTranslationY(AndroidUtilities.dp(20));
        codeFieldContainer.setAlpha(0);
        codeFieldContainer.setTranslationY(AndroidUtilities.dp(20));
        if (fingerprintImage != null) {
            fingerprintImage.setAlpha(0);
            fingerprintImage.setScaleX(0.5f);
            fingerprintImage.setScaleY(0.5f);
        }

        AndroidUtilities.runOnUIThread(() -> {
            containerView.animate().alpha(1).scaleX(1).scaleY(1).setDuration(500).setInterpolator(new OvershootInterpolator(1.0f)).start();
            keyboardView.animate().alpha(1).translationY(0).setDuration(600).setStartDelay(100).setInterpolator(AndroidUtilities.overshootInterpolator).start();

            titleView.animate().alpha(1).translationY(0).setDuration(400).setStartDelay(200).start();
            subtitleView.animate().alpha(1).translationY(0).setDuration(400).setStartDelay(300).start();
            codeFieldContainer.animate().alpha(1).translationY(0).setDuration(400).setStartDelay(400).start();
            if (fingerprintImage != null) {
                fingerprintImage.animate().alpha(1).scaleX(1).scaleY(1).setDuration(500).setStartDelay(500).setInterpolator(new OvershootInterpolator(1.5f)).start();
            }
        }, 100);
    }

    private class AnimatedBackgroundView extends View {
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ArrayList<Particle> particles = new ArrayList<>();
        private Random random = new Random();
        private long lastTime;
        private LinearGradient gradient;

        public AnimatedBackgroundView(Context context) {
            super(context);
            for (int i = 0; i < 30; i++) {
                particles.add(new Particle());
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            int color1 = Theme.isCurrentThemeDark() ? 0xFF061B3D : 0xFF1A237E;
            int color2 = Theme.isCurrentThemeDark() ? 0xFF041430 : 0xFF0D47A1;
            gradient = new LinearGradient(0, 0, 0, h, color1, color2, Shader.TileMode.CLAMP);
            paint.setShader(gradient);
            for (Particle p : particles) {
                p.reset(w, h);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (gradient == null) return;
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);

            long now = System.currentTimeMillis();
            float dt = lastTime == 0 ? 0.016f : (now - lastTime) / 1000f;
            lastTime = now;

            for (Particle p : particles) {
                p.update(dt, getWidth(), getHeight());
                p.draw(canvas);
            }
            invalidate();
        }

        private class Particle {
            float x, y, radius, vx, vy, alpha;
            Paint pPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            void reset(int w, int h) {
                x = random.nextFloat() * w;
                y = random.nextFloat() * h;
                radius = 2 + random.nextFloat() * 4;
                vx = (random.nextFloat() - 0.5f) * 20;
                vy = (random.nextFloat() - 0.5f) * 20;
                alpha = 0.1f + random.nextFloat() * 0.3f;
                pPaint.setColor(Color.WHITE);
            }

            void update(float dt, int w, int h) {
                x += vx * dt;
                y += vy * dt;
                if (x < 0) x = w;
                if (x > w) x = 0;
                if (y < 0) y = h;
                if (y > h) y = 0;
            }

            void draw(Canvas canvas) {
                pPaint.setAlpha((int) (alpha * 255));
                canvas.drawCircle(x, y, AndroidUtilities.dp(radius), pPaint);
            }
        }
    }
}
