package com.vorthas.yusu.svc;

import static com.vorthas.yusu.svc.AppZygotePreload.TAG;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.vorthas.yusu.ui.util.KsuCliKt;

public class BootCompletedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        var action = intent.getAction();
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"com.vorthas.yusu.svc.LAUNCH".equals(action)) {
            return;
        }
        if (KsuCliKt.rootAvailable()) return;
        try {
            context.startService(new Intent(context, BridgeService.class));
            Log.i(TAG, "BridgeService started from boot action: " + action);
        } catch (Throwable e) {

            Log.e(TAG, "Failed to start BridgeService from boot action: " + action, e);
        }
    }
}
