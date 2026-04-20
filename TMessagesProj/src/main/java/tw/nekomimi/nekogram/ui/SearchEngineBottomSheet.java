package tw.nekomimi.nekogram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import xyz.nextalone.nagram.NaConfig;

public class SearchEngineBottomSheet extends BottomSheet {

    public static final String[] ENGINE_NAMES = {
        "Google",
        "Bing",
        "Yahoo",
        "DuckDuckGo",
        "Yandex",
        "Brave",
        "Baidu",
        "Ahmia",
        "Startpage",
        "SearXNG",
        "Swisscows",
        "WolframAlpha",
        "Ecosia"
    };
    public static final String[] ENGINE_URLS = {
        "https://www.google.com/search?q=",
        "https://www.bing.com/search?q=",
        "https://search.yahoo.com/search?p=",
        "https://duckduckgo.com/?q=",
        "https://yandex.com/search/?text=",
        "https://search.brave.com/search?q=",
        "https://www.baidu.com/s?wd=",
        "https://ahmia.fi/search/?q=",
        "https://www.startpage.com/do/dsearch?query=",
        "https://searx.be/search?q=",
        "https://swisscows.com/en/web?query=",
        "https://www.wolframalpha.com/input?i=",
        "https://www.ecosia.org/search?q="
    };
    // Brand colors for each engine badge
    private static final int[] ENGINE_COLORS = {
        0xFF4285F4, // Google blue
        0xFF00809D, // Bing teal
        0xFF720E9E, // Yahoo purple
        0xFFDE5833, // DuckDuckGo red-orange
        0xFFFF0000, // Yandex red
        0xFFFF6000, // Brave orange
        0xFF2319DC, // Baidu blue
        0xFF1A73E8, // Ahmia blue
        0xFF00192A, // Startpage navy
        0xFF3C3C3C, // SearXNG grey
        0xFFD32F2F, // Swisscows red
        0xFFFF9800, // WolframAlpha orange
        0xFF006655  // Ecosia deep green
    };

    public interface Callback {
        void onEngineSelected(int index);
    }

    private final Callback callback;

    public SearchEngineBottomSheet(Context context, Callback callback) {
        super(context, false);
        this.callback = callback;
        setApplyTopPadding(false);
        setApplyBottomPadding(false);
        buildLayout(context);
    }

    private void buildLayout(Context context) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));

        // Title
        TextView title = new TextView(context);
        title.setText("Search Engine");
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        title.setTextSize(18);
        title.setTypeface(AndroidUtilities.bold());
        title.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(12));
        container.addView(title);

        // List
        RecyclerListView listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        EngineAdapter adapter = new EngineAdapter(context);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> {
            NaConfig.INSTANCE.getSelectedSearchEngine().setConfigInt(position);
            if (callback != null) callback.onEngineSelected(position);
            dismiss();
        });
        container.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Bottom padding for navigation bar
        View bottomPad = new View(context);
        container.addView(bottomPad, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));

        setCustomView(container);
    }

    private static class EngineAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        EngineAdapter(Context context) {
            this.context = context;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            EngineCell cell = new EngineCell(context);
            cell.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(60)));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int selected = NaConfig.INSTANCE.getSelectedSearchEngine().Int();
            ((EngineCell) holder.itemView).bind(position, selected == position);
        }

        @Override
        public int getItemCount() {
            return ENGINE_NAMES.length;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }
    }

    private static class EngineCell extends FrameLayout {
        private final EngineBadgeView badge;
        private final TextView nameView;
        private final View checkView;

        EngineCell(Context context) {
            super(context);
            setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));

            badge = new EngineBadgeView(context);
            addView(badge, LayoutHelper.createFrame(40, 40, Gravity.LEFT | Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

            nameView = new TextView(context);
            nameView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            nameView.setTextSize(16);
            nameView.setSingleLine(true);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            addView(nameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.CENTER_VERTICAL, 72, 0, 56, 0));

            checkView = new View(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    if (!isSelected()) return;
                    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                    p.setColor(Theme.getColor(Theme.key_checkboxSquareBackground));
                    p.setStrokeWidth(AndroidUtilities.dp(2.5f));
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    p.setStrokeJoin(Paint.Join.ROUND);
                    float w = getWidth(), h = getHeight();
                    canvas.drawLine(w * 0.1f, h * 0.5f, w * 0.4f, h * 0.8f, p);
                    canvas.drawLine(w * 0.4f, h * 0.8f, w * 0.9f, h * 0.2f, p);
                }
            };
            addView(checkView, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 16, 0));
        }

        void bind(int index, boolean selected) {
            badge.setEngine(index);
            nameView.setText(ENGINE_NAMES[index]);
            checkView.setSelected(selected);
            checkView.invalidate();
        }
    }

    public static class EngineBadgeView extends View {
        private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private final Rect textBounds = new Rect();
        private int engineIndex = 0;

        public EngineBadgeView(Context context) {
            super(context);
            textPaint.setColor(Color.WHITE);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        }

        public void setEngine(int index) {
            this.engineIndex = index;
            if (index >= 0 && index < ENGINE_COLORS.length) {
                circlePaint.setColor(ENGINE_COLORS[index]);
            }
            invalidate();
        }

        public int getEngineIndex() { return engineIndex; }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            float r = Math.min(w, h) / 2f;
            oval.set(w / 2f - r, h / 2f - r, w / 2f + r, h / 2f + r);
            canvas.drawOval(oval, circlePaint);

            String letter = engineIndex >= 0 && engineIndex < ENGINE_NAMES.length
                ? String.valueOf(ENGINE_NAMES[engineIndex].charAt(0)) : "?";
            textPaint.setTextSize(r * 1.0f);
            textPaint.getTextBounds(letter, 0, letter.length(), textBounds);
            canvas.drawText(letter,
                w / 2f - textBounds.exactCenterX(),
                h / 2f - textBounds.exactCenterY(),
                textPaint);
        }
    }

    public static String getEngineUrl(int index) {
        if (index >= 0 && index < ENGINE_URLS.length) return ENGINE_URLS[index];
        return ENGINE_URLS[0];
    }

    public static String getEngineName(int index) {
        if (index >= 0 && index < ENGINE_NAMES.length) return ENGINE_NAMES[index];
        return ENGINE_NAMES[0];
    }

    public static int getEngineColor(int index) {
        if (index >= 0 && index < ENGINE_COLORS.length) return ENGINE_COLORS[index];
        return ENGINE_COLORS[0];
    }
}
