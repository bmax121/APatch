package me.bmax.apatch.magica;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.File;

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
        // Only auto-trigger jailbreak if the user has previously opted into jailbreak
        // mode (the marker file exists). Without this guard, flashing a real KernelPatch
        // boot.img from fastboot would cause the boot receiver to re-trigger the magica
        // chain, which would then re-write the jailbreak marker and confuse the UI.
        if (!new File("/data/adb/ap/jailbreak").exists()) {
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
