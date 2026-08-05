package me.bmax.apatch.magica;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import me.bmax.apatch.util.APatchCliKt;

/** Auto-jailbreak on boot: if root is not available, trigger the magica chain. */
public class BootCompletedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"me.bmax.apatch.magica.LAUNCH".equals(action)) {
            return;
        }
        if (APatchCliKt.rootAvailable()) {
            return;
        }
        try {
            context.startService(new Intent(context, MagicaService.class));
            Log.i(AppZygotePreload.TAG, "MagicaService started from boot action: " + action);
        } catch (Throwable e) {
            Log.e(AppZygotePreload.TAG, "Failed to start MagicaService from boot action: " + action, e);
        }
    }
}
