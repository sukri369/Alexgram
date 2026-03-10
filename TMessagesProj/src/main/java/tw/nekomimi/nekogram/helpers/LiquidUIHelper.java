package tw.nekomimi.nekogram.helpers;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import tw.nekomimi.nekogram.NekoConfig;

public class LiquidUIHelper {

    public static void applyLiquidEffect(View view) {
        if (!NekoConfig.liquidGlassUI.Bool()) {
            return;
        }

        view.setBackground(createLiquidDrawable());
        view.setTranslationZ(AndroidUtilities.dp(4));
    }

    public static GradientDrawable createLiquidDrawable() {
        GradientDrawable gd = new GradientDrawable();
        int color = Theme.getColor(Theme.key_windowBackgroundWhite);
        int alpha = 40; // 0-255
        int glassColor = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
        
        gd.setColor(glassColor);
        gd.setCornerRadius(AndroidUtilities.dp(16));
        gd.setStroke(AndroidUtilities.dp(1), Color.parseColor("#20FFFFFF"));
        return gd;
    }
}
