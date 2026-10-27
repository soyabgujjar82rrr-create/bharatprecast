package com.example.data.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Ensures App Lock configuration and state are preserved and ready immediately after device boot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON") {
            context?.let { ctx ->
                // Initialize singleton and warm up secure settings
                val lockManager = AppLockManager.getInstance(ctx)
                lockManager.invalidateAllSessions()
                if (lockManager.settings.isAppLockEnabled) {
                    AppLockBackgroundService.start(ctx)
                }
            }
        }
    }
}
