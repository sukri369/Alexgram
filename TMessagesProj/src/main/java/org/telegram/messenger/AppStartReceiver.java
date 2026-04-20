/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import xyz.nextalone.nagram.NaConfig;

public class AppStartReceiver extends BroadcastReceiver {

    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        boolean isBoot = Intent.ACTION_BOOT_COMPLETED.equals(action);
        boolean isRestart = "org.telegram.start".equals(action);

        if (isBoot || isRestart) {
            AndroidUtilities.runOnUIThread(() -> {
                if (isBoot) {
                    SharedConfig.loadConfig();
                    if (SharedConfig.passcodeHash.length() > 0) {
                        SharedConfig.appLocked = true;
                        SharedConfig.saveConfig();
                    }
                }
                // For the restart case, only start the service if RunInBackground is enabled
                if (isBoot || NaConfig.INSTANCE.getRunInBackground().Bool()) {
                    ApplicationLoader.startPushService();
                }
            });
        }
    }
}

