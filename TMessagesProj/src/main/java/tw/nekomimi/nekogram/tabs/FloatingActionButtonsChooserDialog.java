package tw.nekomimi.nekogram.tabs;

import android.content.Context;
import android.graphics.Rect;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class FloatingActionButtonsChooserDialog extends BottomSheet {

    public interface Callback {
        void onSave(FloatingActionButtonType selectedType);
    }

    private final Callback callback;
    private final ArrayList<FloatingActionButtonType> dialogButtons = new ArrayList<>();
    private FloatingActionButtonType selectedButton;
    private int itemWidth;

    private RecyclerListView listView;
    private ListAdapter adapter;
    private View bottomShadow;
    private TextView saveButton;

    public FloatingActionButtonsChooserDialog(Context context, FloatingActionButtonType currentSelection, Callback callback) {
        super(context, false);
        this.callback = callback;
        this.selectedButton = currentSelection != null ? currentSelection : FloatingActionButtonType.getDefault();

        for (FloatingActionButtonType type : FloatingActionButtonType.values()) {
            if (type != FloatingActionButtonType.CREATE_ALBUM) {
                dialogButtons.add(type);
            }
        }

        setTitle(LocaleController.getString("create_folder_change_fab_title", R.string.create_folder_change_fab_title), true);
        setApplyBottomPadding(false);

        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                itemWidth = (MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(28)) / 4;
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };

        // Recycler list
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new GridLayoutManager(context, 4));
        adapter = new ListAdapter();
        listView.setAdapter(adapter);
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setEnabled(true);
        listView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        listView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        listView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                RecyclerView.ViewHolder holder = parent.getChildViewHolder(view);
                if (holder != null) {
                    int adapterPosition = holder.getAdapterPosition() % 4;
                    outRect.left = adapterPosition == 0 ? 0 : AndroidUtilities.dp(4);
                    outRect.right = adapterPosition == 3 ? 0 : AndroidUtilities.dp(4);
                }
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < dialogButtons.size()) {
                selectedButton = dialogButtons.get(position);
                adapter.notifyDataSetChanged();
            }
        });

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 48));

        // Bottom shadow line
        bottomShadow = new View(context);
        bottomShadow.setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
        frameLayout.addView(bottomShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 48));

        // Save button
        saveButton = new TextView(context);
        saveButton.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        saveButton.setAllCaps(true);
        saveButton.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        saveButton.setGravity(Gravity.CENTER);
        saveButton.setBackground(Theme.createSelectorWithBackgroundDrawable(Theme.getColor(Theme.key_dialogBackground), Theme.getColor(Theme.key_listSelector)));
        saveButton.setText(LocaleController.getString("Save", R.string.Save));
        saveButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.0f);
        saveButton.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        saveButton.setOnClickListener(v -> {
            if (FloatingActionButtonsChooserDialog.this.callback != null) {
                FloatingActionButtonsChooserDialog.this.callback.onSave(selectedButton);
            }
            dismiss();
        });

        frameLayout.addView(saveButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));

        setCustomView(frameLayout);
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            return dialogButtons.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            CircleIconCheckCell cell = new CircleIconCheckCell(parent.getContext(), 36, ImageView.ScaleType.CENTER) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), MeasureSpec.EXACTLY));
                }
            };
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            CircleIconCheckCell cell = (CircleIconCheckCell) holder.itemView;
            FloatingActionButtonType buttonType = dialogButtons.get(position);
            cell.setColor(Theme.getColor(Theme.key_chats_actionBackground));
            cell.setFabIcon(buttonType);
            cell.setName(buttonType.getTitle(cell.getContext()));
            cell.setChecked(selectedButton == buttonType, false);
        }
    }
}
