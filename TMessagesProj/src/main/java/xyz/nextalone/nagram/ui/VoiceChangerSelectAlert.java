package xyz.nextalone.nagram.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import xyz.nextalone.nagram.NaConfig;

public class VoiceChangerSelectAlert extends BottomSheet {

    private final RecyclerListView listView;
    private final int[] effects = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14};
    private final String[] effectNames = {
            "Normal",
            "Robotic",
            "Alien",
            "Hoarseness",
            "Modulation",
            "Child",
            "Mouse",
            "Man",
            "Woman",
            "Monster",
            "Echo",
            "Noise",
            "Helium",
            "Hexafluoride",
            "Cave"
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

    public VoiceChangerSelectAlert(Context context) {
        super(context, true);

        setTitle(LocaleController.getString("VoiceChanger", R.string.VoiceChanger));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(new ListAdapter());
        listView.setVerticalScrollBarEnabled(false);
        listView.setOnItemClickListener((view, position) -> {
            NaConfig.setVoiceChangerEffectValue(effects[position]);
            dismiss();
        });

        setCustomView(listView);
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = new CheckBoxCell(parent.getContext(), 1, 21, null);
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            CheckBoxCell cell = (CheckBoxCell) holder.itemView;
            String name = LocaleController.getString(effectNames[position], effectKeys[position]);
            cell.setText(name, "", NaConfig.getVoiceChangerEffectValue() == effects[position], false);
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
