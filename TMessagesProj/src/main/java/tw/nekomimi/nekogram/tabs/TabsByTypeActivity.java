package tw.nekomimi.nekogram.tabs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * "Tabs by type" settings screen — ported from iMe's DialogsSortingSettingsFragment.
 *
 * Shows two tabs (Main List | Archive) at the top, a toggle for Auto-sorting,
 * and a drag-to-reorder list of chat-type rows.
 */
public class TabsByTypeActivity extends BaseFragment {

    // ── View types ─────────────────────────────────────────────────────────────
    private static final int VT_TAB_HEADER  = 0;   // Main List / Archive selector row
    private static final int VT_AUTO_SORT   = 1;   // TextCheckCell
    private static final int VT_HINT        = 2;   // TextInfoPrivacyCell
    private static final int VT_ENTRY       = 3;   // TabsByTypeCell rows
    private static final int VT_DRAG_HINT   = 4;   // drag hint footer

    // ── State ──────────────────────────────────────────────────────────────────
    private boolean archiveSelected = false;
    private TabsByTypeSettings settings;

    // ── Views ──────────────────────────────────────────────────────────────────
    private RecyclerListView listView;
    private ListAdapter      adapter;
    private ItemTouchHelper  touchHelper;

    // ── Items ──────────────────────────────────────────────────────────────────
    private final ArrayList<Item> items = new ArrayList<>();

    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public boolean onFragmentCreate() {
        settings = TabsByTypeSettings.getInstance();
        buildItems();
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        // Action bar
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.TabsByType));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setSupportsChangeAnimations(false);
        listView.setItemAnimator(animator);

        adapter = new ListAdapter(context);
        listView.setAdapter(adapter);

        touchHelper = new ItemTouchHelper(new TouchCallback());
        touchHelper.attachToRecyclerView(listView);

        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= items.size()) return;
            Item item = items.get(position);
            if (item.viewType == VT_AUTO_SORT) {
                boolean cur = settings.isEnabled(archiveSelected);
                settings.setEnabled(archiveSelected, !cur);
                ((TextCheckCell) view).setChecked(!cur);
                buildItems();
                adapter.notifyDataSetChanged();
            } else if (item.viewType == VT_ENTRY && item.tab != null) {
                TabsByTypeCell cell = (TabsByTypeCell) view;
                cell.toggleCheck();
                settings.setTabEnabled(item.tab, archiveSelected, cell.isChecked());
            }
        });

        frameLayout.addView(listView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    // ── Build item list ────────────────────────────────────────────────────────

    private void buildItems() {
        items.clear();

        // 0) Tab header (Main List / Archive segmented control)
        items.add(new Item(VT_TAB_HEADER, null));

        // 1) Auto-sorting toggle
        items.add(new Item(VT_AUTO_SORT, null));

        boolean enabled = settings.isEnabled(archiveSelected);

        // 2) Hint below toggle (only when disabled)
        if (!enabled) {
            items.add(new Item(VT_HINT, null));
        }

        // 3) Tab type rows (only when enabled)
        if (enabled) {
            List<TabsByTypeEntry> sorted = settings.getSortedTabs(archiveSelected);
            for (TabsByTypeEntry tab : sorted) {
                items.add(new Item(VT_ENTRY, tab));
            }
            // 4) Drag hint footer
            items.add(new Item(VT_DRAG_HINT, null));
        }
    }

    // ── Adapter ────────────────────────────────────────────────────────────────

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context ctx;

        ListAdapter(Context ctx) {
            this.ctx = ctx;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).viewType;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int vt = holder.getItemViewType();
            return vt == VT_AUTO_SORT || vt == VT_ENTRY;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VT_TAB_HEADER:
                    view = new TabSelectorView(ctx);
                    break;
                case VT_AUTO_SORT: {
                    TextCheckCell cell = new TextCheckCell(ctx);
                    cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = cell;
                    break;
                }
                case VT_HINT:
                case VT_DRAG_HINT: {
                    TextInfoPrivacyCell cell = new TextInfoPrivacyCell(ctx);
                    view = cell;
                    break;
                }
                case VT_ENTRY:
                default:
                    view = new TabsByTypeCell(ctx);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerView.ViewHolder(view) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Item item = items.get(position);
            switch (item.viewType) {

                case VT_TAB_HEADER:
                    ((TabSelectorView) holder.itemView).bind(archiveSelected);
                    break;

                case VT_AUTO_SORT: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    cell.setTextAndCheck(
                            LocaleController.getString(R.string.TabsByTypeAutoSorting),
                            settings.isEnabled(archiveSelected),
                            true);
                    break;
                }

                case VT_HINT: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setText(LocaleController.getString(R.string.TabsByTypeAutoSortingHint));
                    break;
                }

                case VT_ENTRY: {
                    TabsByTypeEntry tab = item.tab;
                    boolean isLast = (position == items.size() - 2); // before drag hint
                    TabsByTypeCell cell = (TabsByTypeCell) holder.itemView;
                    cell.setTab(
                            tab,
                            settings.isTabEnabled(tab, archiveSelected),
                            !isLast);
                    // Action button click — pencil = no-op for now; eye = no-op
                    cell.setOnActionButtonClick(v -> {
                        // Future: open FAB chooser dialog
                    });
                    break;
                }

                case VT_DRAG_HINT: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setText(LocaleController.getString(R.string.TabsByTypeDragHint));
                    break;
                }
            }
        }
    }

    // ── Drag / reorder ─────────────────────────────────────────────────────────

    private class TouchCallback extends ItemTouchHelper.Callback {

        @Override
        public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
            if (items.get(vh.getAdapterPosition()).viewType != VT_ENTRY) {
                return makeMovementFlags(0, 0);
            }
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView rv,
                              @NonNull RecyclerView.ViewHolder from,
                              @NonNull RecyclerView.ViewHolder to) {
            int fromPos = from.getAdapterPosition();
            int toPos   = to.getAdapterPosition();
            if (items.get(fromPos).viewType != VT_ENTRY
                    || items.get(toPos).viewType != VT_ENTRY) {
                return false;
            }
            TabsByTypeEntry fromTab = items.get(fromPos).tab;
            TabsByTypeEntry toTab   = items.get(toPos).tab;
            settings.swapTabOrders(fromTab, toTab, archiveSelected);
            Collections.swap(items, fromPos, toPos);
            adapter.notifyItemMoved(fromPos, toPos);
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {}

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder vh, int actionState) {
            super.onSelectedChanged(vh, actionState);
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
                vh.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                vh.itemView.setAlpha(0.85f);
            }
        }

        @Override
        public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
            super.clearView(rv, vh);
            vh.itemView.setAlpha(1f);
        }
    }

    // ── Tab selector (Main List / Archive pill header) ─────────────────────────

    private class TabSelectorView extends FrameLayout {

        private final TextView mainBtn;
        private final TextView archiveBtn;

        TabSelectorView(Context context) {
            super(context);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);

            mainBtn    = buildPill(context, LocaleController.getString(R.string.TabsByTypeMainList));
            archiveBtn = buildPill(context, LocaleController.getString(R.string.TabsByTypeArchive));

            row.addView(mainBtn,    LayoutHelper.createLinear(0, 36, 1f));
            row.addView(archiveBtn, LayoutHelper.createLinear(0, 36, 1f, 4, 0, 0, 0));

            addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.CENTER, 16, 8, 16, 8));

            mainBtn.setOnClickListener(v -> {
                if (archiveSelected) {
                    archiveSelected = false;
                    buildItems();
                    adapter.notifyDataSetChanged();
                }
            });
            archiveBtn.setOnClickListener(v -> {
                if (!archiveSelected) {
                    archiveSelected = true;
                    buildItems();
                    adapter.notifyDataSetChanged();
                }
            });
        }

        void bind(boolean archiveSel) {
            stylePill(mainBtn,    !archiveSel);
            stylePill(archiveBtn,  archiveSel);
        }

        private TextView buildPill(Context context, String text) {
            TextView tv = new TextView(context);
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            tv.setSingleLine(true);
            tv.setText(text);
            return tv;
        }

        private void stylePill(TextView tv, boolean selected) {
            int accent = Theme.getColor(Theme.key_chats_actionBackground);
            if (selected) {
                tv.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), accent));
                tv.setTextColor(Color.WHITE);
            } else {
                tv.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18),
                        Theme.getColor(Theme.key_windowBackgroundWhite)));
                tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(52), MeasureSpec.EXACTLY));
        }
    }

    // ── Item model ─────────────────────────────────────────────────────────────

    private static class Item {
        final int            viewType;
        final TabsByTypeEntry tab;     // non-null only for VT_ENTRY

        Item(int viewType, TabsByTypeEntry tab) {
            this.viewType = viewType;
            this.tab      = tab;
        }
    }
}
