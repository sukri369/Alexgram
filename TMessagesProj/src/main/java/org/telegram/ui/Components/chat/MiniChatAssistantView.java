package org.telegram.ui.Components.chat;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import java.util.ArrayList;

public class MiniChatAssistantView extends LinearLayout {
    private ArrayList<Object> history; // String or Bitmap

    public MiniChatAssistantView(Context context) {
        super(context);
        init();
    }

    public MiniChatAssistantView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        history = new ArrayList<>();
        setOrientation(VERTICAL);
    }

    public void addGeminiResponse(String response) {
        // Try to detect image (base64 or URL)
        android.graphics.Bitmap image = null;
        if (response != null && (response.startsWith("data:image/") || response.startsWith("http"))) {
            try {
                if (response.startsWith("data:image/")) {
                    String base64 = response.substring(response.indexOf(",") + 1);
                    byte[] decoded = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                    image = android.graphics.BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                } else if (response.startsWith("http")) {
                    java.net.URL url = new java.net.URL(response);
                    java.io.InputStream is = url.openStream();
                    image = android.graphics.BitmapFactory.decodeStream(is);
                    is.close();
                }
            } catch (Throwable e) {
                // fallback to text
            }
        }
        if (image != null) {
            addImage(image);
        } else {
            addMessage(response);
        }
    }

    public void addMessage(String message) {
        history.add(message);
        addTextBubble(message);
    }

    public void addImage(android.graphics.Bitmap image) {
        history.add(image);
        addImageBubble(image);
    }

    private void addTextBubble(String message) {
        android.widget.TextView tv = new android.widget.TextView(getContext());
        tv.setText(message);
        tv.setTextColor(android.graphics.Color.WHITE);
        tv.setBackgroundColor(0x99495D7A);
        tv.setPadding(24, 16, 24, 16);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 12;
        addView(tv, lp);
    }

    private void addImageBubble(android.graphics.Bitmap image) {
        android.widget.FrameLayout bubble = new android.widget.FrameLayout(getContext());
        android.widget.ImageView iv = new android.widget.ImageView(getContext());
        iv.setImageBitmap(image);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        bubble.addView(iv, new android.widget.FrameLayout.LayoutParams(320, 320));

        android.widget.ImageView downloadIcon = new android.widget.ImageView(getContext());
        downloadIcon.setImageResource(android.R.drawable.stat_sys_download);
        downloadIcon.setBackgroundColor(0x66000000);
        android.widget.FrameLayout.LayoutParams iconLp = new android.widget.FrameLayout.LayoutParams(64, 64);
        iconLp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.RIGHT;
        iconLp.bottomMargin = 16;
        iconLp.rightMargin = 16;
        bubble.addView(downloadIcon, iconLp);

        downloadIcon.setOnClickListener(v -> {
            saveAndCopyImage(image);
        });

        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 12;
        addView(bubble, lp);
    }

    private void saveAndCopyImage(android.graphics.Bitmap image) {
        // Save image to device storage
        java.io.File savedFile = org.telegram.messenger.AndroidUtilities.saveBitmapToGallery(image, "Alexgram_Assistant_Image");
        if (savedFile != null) {
            // Copy image to clipboard
            android.net.Uri uri = android.net.Uri.fromFile(savedFile);
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newUri(getContext().getContentResolver(), "Image", uri);
            clipboard.setPrimaryClip(clip);
            android.widget.Toast.makeText(getContext(), "Image saved and copied!", android.widget.Toast.LENGTH_SHORT).show();
        } else {
            android.widget.Toast.makeText(getContext(), "Failed to save image!", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    public ArrayList<Object> getHistory() {
        return history;
    }

    public void clearHistory() {
        history.clear();
        removeAllViews();
    }

    public String queryHistory(String query) {
        StringBuilder result = new StringBuilder();
        for (Object obj : history) {
            if (obj instanceof String && ((String)obj).contains(query)) {
                result.append((String)obj).append("\n");
            }
        }
        return result.toString();
    }
}
