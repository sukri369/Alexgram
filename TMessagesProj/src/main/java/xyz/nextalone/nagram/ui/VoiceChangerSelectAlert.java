package xyz.nextalone.nagram.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import xyz.nextalone.nagram.NaConfig;

public class VoiceChangerSelectAlert extends BottomSheet {

    private final RecyclerListView listView;
    private final int[] effects = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14};
    private final String[] effectEmojis = {
            "🎙️", "🤖", "👽", "🗣️", "♒",
            "🧒", "🐭", "👨", "👩", "👹",
            "🔊", "🌫️", "🎈", "🧪", "🕳️"
    };
    private final int[] effectKeys = {
            R.string.VoiceChangerNormal,
            R.string.VoiceChangerRobotic,
            R.string.VoiceChangerAlien,
            R.string.VoiceChangerHoarseness,
            R.string.VoiceChangerModulation,
            R.string.VoiceChangerChild,
            R.string.VoiceChangerMouse,
            R.string.VoiceChangerMan,
            R.string.VoiceChangerWoman,
            R.string.VoiceChangerMonster,
            R.string.VoiceChangerEcho,
            R.string.VoiceChangerNoise,
            R.string.VoiceChangerHelium,
            R.string.VoiceChangerHexafluoride,
            R.string.VoiceChangerCave
    };

    private final String[] effectNames = {
            "Normal", "Robotic", "Alien", "Hoarseness", "Modulation",
            "Child", "Mouse", "Man", "Woman", "Monster",
            "Echo", "Noise", "Helium", "Hexafluoride", "Cave"
    };

    public VoiceChangerSelectAlert(Context context) {
        super(context, true);

        FrameLayout container = new FrameLayout(context);

        TextView titleView = new TextView(context);
        titleView.setText(LocaleController.getString("VoiceChanger", R.string.VoiceChanger));
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        titleView.setTextSize(20);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setGravity(Gravity.CENTER);
        container.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP, 0, 12, 0, 0));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new GridLayoutManager(context, 3));
        listView.setAdapter(new ListAdapter());
        listView.setClipToPadding(false);
        listView.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(64), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        listView.setVerticalScrollBarEnabled(false);
        listView.setOnItemClickListener((view, position) -> {
            NaConfig.INSTANCE.setVoiceChangerEffectValue(effects[position]);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.voiceChangerUpdated);
            listView.getAdapter().notifyDataSetChanged();
            AndroidUtilities.runOnUIThread(this::dismiss, 150);
        });

        container.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        setCustomView(container);
    }

    private class VoiceEffectCell extends FrameLayout {

        private final TextView emojiView;
        private final TextView textView;
        private final FrameLayout circleView;
        private final Paint selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean isSelected;

        public VoiceEffectCell(@NonNull Context context) {
            super(context);

            circleView = new FrameLayout(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);
                    if (isSelected) {
                        selectorPaint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
                        selectorPaint.setStyle(Paint.Style.STROKE);
                        selectorPaint.setStrokeWidth(AndroidUtilities.dp(2));
                        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, getWidth() / 2f - AndroidUtilities.dp(2), selectorPaint);
                    }
                }
            };
            circleView.setWillNotDraw(false);
            
            emojiView = new TextView(context);
            emojiView.setTextSize(32);
            emojiView.setGravity(Gravity.CENTER);
            circleView.addView(emojiView, LayoutHelper.createFrame(64, 64, Gravity.CENTER));

            addView(circleView, LayoutHelper.createFrame(72, 72, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));

            textView = new TextView(context);
            textView.setTextSize(13);
            textView.setLines(1);
            textView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            textView.setGravity(Gravity.CENTER);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 4, 84, 4, 8));
        }

        public void setEffect(int effectIndex, boolean selected) {
            isSelected = selected;
            textView.setText(LocaleController.getString(effectNames[effectIndex], effectKeys[effectIndex]));
            emojiView.setText(effectEmojis[effectIndex]);

            int circleColor;
            if (selected) {
                circleColor = Theme.getColor(Theme.key_featuredStickers_addButton);
                circleView.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(36), circleColor));
                textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                textView.setTextColor(circleColor);
            } else {
                circleColor = Theme.getColor(Theme.key_dialogBackgroundGray);
                circleView.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(36), circleColor));
                textView.setTypeface(Typeface.DEFAULT);
                textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            }
            circleView.invalidate();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                animateScale(1.1f);
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                animateScale(1.0f);
            }
            return super.onTouchEvent(event);
        }

        private void animateScale(float scale) {
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(this, View.SCALE_X, scale),
                    ObjectAnimator.ofFloat(this, View.SCALE_Y, scale)
            );
            animatorSet.setDuration(150);
            animatorSet.start();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(110), MeasureSpec.EXACTLY));
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            VoiceEffectCell cell = new VoiceEffectCell(parent.getContext());
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            VoiceEffectCell cell = (VoiceEffectCell) holder.itemView;
            cell.setEffect(position, NaConfig.INSTANCE.getVoiceChangerEffectValue() == effects[position]);
        }

        @Override
        public int getItemCount() {
            return effects.length;
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }
    }
}
