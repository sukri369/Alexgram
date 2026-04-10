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

import java.util.concurrent.Executor;

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
    private CodeFieldContainer codeFieldContainer;
    private CustomPhoneKeyboardView keyboardView;

    public HiddenChatsPasscodeActivity(@Mode int mode) {
        this.mode = mode;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle("Hidden Chats");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        root.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 42, 0, CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP));

        titleView = new TextView(context);
        titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 32);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        subtitleView = new TextView(context);
        subtitleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitleView.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(10), AndroidUtilities.dp(24), 0);
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
            f.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
            f.setOnFocusChangeListener((v, hasFocus) -> {
                keyboardView.setEditText(f);
                keyboardView.setDispatchBackWhenEmpty(true);
            });
        }
        content.addView(codeFieldContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 30, 0, 0));

        errorView = new TextView(context);
        errorView.setTextColor(getThemedColor(Theme.key_text_RedBold));
        errorView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        errorView.setGravity(Gravity.CENTER_HORIZONTAL);
        errorView.setVisibility(View.INVISIBLE);
        errorView.setPadding(0, AndroidUtilities.dp(14), 0, 0);
        content.addView(errorView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        if (android.os.Build.VERSION.SDK_INT >= 23 && (mode == MODE_UNLOCK_CHATS || mode == MODE_UNLOCK_SETTINGS)) {
            android.widget.ImageView fingerprintImage = new android.widget.ImageView(context);
            fingerprintImage.setImageResource(R.drawable.fingerprint);
            fingerprintImage.setScaleType(android.widget.ImageView.ScaleType.CENTER);
            fingerprintImage.setBackgroundResource(R.drawable.bar_selector_lock);
            fingerprintImage.setOnClickListener(v -> checkFingerprint());
            fingerprintImage.setContentDescription(LocaleController.getString(R.string.AccDescrFingerprint));
            fingerprintImage.setVisibility(HiddenChatsController.getInstance().isBiometricEnabled() ? View.VISIBLE : View.GONE);
            content.addView(fingerprintImage, LayoutHelper.createLinear(56, 56, Gravity.CENTER_HORIZONTAL, 0, 20, 0, 0));
        }

        keyboardView = new CustomPhoneKeyboardView(context);
        root.addView(keyboardView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP, Gravity.BOTTOM));

        root.setOnClickListener(v -> focusFirstEmptyField());

        updateTexts();
        focusFirstEmptyField();

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
        if (mode == MODE_UNLOCK_SETTINGS) {
            presentFragment(new HiddenChatsSettingsActivity(), true);
        } else {
            presentFragment(new HiddenChatsActivity(new Bundle()), true);
        }
    }
}
