package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

public final class HiddenChatsPasscodeDialog {

    private HiddenChatsPasscodeDialog() {
    }

    public interface OnCodeConfirmed {
        boolean onCodeConfirmed(String code);
    }

    public static void showFourDigitDialog(
            @NonNull BaseFragment fragment,
            @NonNull Context context,
            @NonNull String title,
            @Nullable String subtitle,
            @NonNull String positiveText,
            @NonNull String invalidLengthMessage,
            @Nullable String invalidCodeMessage,
            @NonNull OnCodeConfirmed callback
    ) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(10), AndroidUtilities.dp(24), 0);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subtitleView = new TextView(context);
            subtitleView.setText(subtitle);
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            subtitleView.setTextSize(16);
            subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
            subtitleView.setPadding(0, 0, 0, AndroidUtilities.dp(10));
            container.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        LinearLayout boxes = new LinearLayout(context);
        boxes.setOrientation(LinearLayout.HORIZONTAL);
        boxes.setGravity(Gravity.CENTER_HORIZONTAL);
        container.addView(boxes, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final TextView[] pinBoxes = new TextView[4];
        for (int i = 0; i < 4; i++) {
            TextView box = new TextView(context);
            box.setTextSize(24);
            box.setGravity(Gravity.CENTER);
            box.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            int horizontal = i == 0 ? 0 : AndroidUtilities.dp(10);
            boxes.addView(box, LayoutHelper.createLinear(46, 54, Gravity.CENTER, horizontal, 0, 0, 0));
            pinBoxes[i] = box;
        }

        EditTextBoldCursor hiddenInput = new EditTextBoldCursor(context);
        hiddenInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        hiddenInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        hiddenInput.setTextColor(0);
        hiddenInput.setCursorColor(0);
        hiddenInput.setCursorWidth(0f);
        hiddenInput.setCursorSize(0);
        hiddenInput.setBackground(null);
        hiddenInput.setPadding(0, 0, 0, 0);
        hiddenInput.setGravity(Gravity.CENTER);
        hiddenInput.setLongClickable(false);
        hiddenInput.setTextIsSelectable(false);
        hiddenInput.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);

        container.addView(hiddenInput, LayoutHelper.createLinear(1, 1));
        builder.setView(container);

        Runnable updateBoxes = () -> {
            String code = hiddenInput.getText() == null ? "" : hiddenInput.getText().toString();
            int len = code.length();
            for (int i = 0; i < pinBoxes.length; i++) {
                boolean filled = i < len;
                pinBoxes[i].setText(filled ? "•" : "");
                pinBoxes[i].setBackground(createBoxBackground(filled));
            }
        };

        hiddenInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateBoxes.run();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        View.OnClickListener focusInput = v -> {
            hiddenInput.requestFocus();
            AndroidUtilities.showKeyboard(hiddenInput);
        };
        container.setOnClickListener(focusInput);
        boxes.setOnClickListener(focusInput);
        for (TextView pinBox : pinBoxes) {
            pinBox.setOnClickListener(focusInput);
        }

        builder.setPositiveButton(positiveText, null);
        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.show();
        updateBoxes.run();
        AndroidUtilities.runOnUIThread(() -> {
            hiddenInput.requestFocus();
            AndroidUtilities.showKeyboard(hiddenInput);
        }, 80);

        TextView positiveButton = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (positiveButton != null) {
            positiveButton.setOnClickListener(v -> {
                String code = hiddenInput.getText() == null ? "" : hiddenInput.getText().toString();
                if (code.length() != 4) {
                    BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, invalidLengthMessage).show();
                    shakeBoxes(pinBoxes);
                    return;
                }
                boolean ok = callback.onCodeConfirmed(code);
                if (ok) {
                    dialog.dismiss();
                } else {
                    if (invalidCodeMessage != null && !invalidCodeMessage.isEmpty()) {
                        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, invalidCodeMessage).show();
                    }
                    hiddenInput.setText("");
                    updateBoxes.run();
                    shakeBoxes(pinBoxes);
                    hiddenInput.requestFocus();
                    AndroidUtilities.showKeyboard(hiddenInput);
                }
            });
        }
    }

    private static GradientDrawable createBoxBackground(boolean filled) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(AndroidUtilities.dp(12));
        int bg = Theme.getColor(Theme.key_windowBackgroundWhite);
        drawable.setColor(bg);
        int strokeColor = filled
                ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)
                : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3);
        drawable.setStroke(AndroidUtilities.dp(1.5f), strokeColor);
        return drawable;
    }

    private static void shakeBoxes(TextView[] pinBoxes) {
        for (TextView pinBox : pinBoxes) {
            if (pinBox != null) {
                AndroidUtilities.shakeViewSpring(pinBox, 3f);
            }
        }
    }
}
