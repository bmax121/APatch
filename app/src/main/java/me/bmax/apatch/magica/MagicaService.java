package me.bmax.apatch.magica;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

/** Isolated, app-zygote-backed service whose launch triggers the jailbreak preload. */
public class MagicaService extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new Binder();
    }
}
