package com.example.data.security

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.example.ui.screens.AppLockOverlayActivity

/**
 * Android Accessibility Service for My Gallery App Lock.
 * Legitimate, officially supported Android mechanism for detecting foreground app launches,
 * recent apps transitions, and external Share intents without root, private APIs,
 * or reading private screen contents.
 */
class AppLockAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        // Only react to actual window state changes (activity foreground transitions)
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString() ?: return
        if (packageName.isBlank()) return
        val className = event.className?.toString() ?: ""

        // Skip our lock overlay activity itself to avoid recursion
        val overlayClass = AppLockOverlayActivity::class.java.name
        if (className == overlayClass || className.endsWith("AppLockOverlayActivity")) {
            return
        }

        // Skip system input methods (keyboards), system UI, and launchers
        if (packageName == "com.android.systemui" ||
            packageName.contains("inputmethod") ||
            packageName.contains("latin") ||
            packageName.contains("launcher")) {
            return
        }

        // If the package is MyGallery itself, internal lock screen handles it
        if (packageName == applicationContext.packageName) {
            return
        }

        val lockManager = AppLockManager.getInstance(applicationContext)
        lockManager.onForegroundPackageChanged(packageName)

        val isShare = lockManager.isShareIntentAction(className)
        val requiresLock = lockManager.shouldLockPackage(packageName, className)

        if (requiresLock) {
            // Launch the full-screen opaque lock overlay immediately without delay or animation
            val intent = Intent(this, AppLockOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                putExtra(AppLockOverlayActivity.EXTRA_LOCKED_PACKAGE, packageName)
                putExtra(AppLockOverlayActivity.EXTRA_IS_SHARE_FLOW, isShare)
            }
            startActivity(intent)
        }
    }

    override fun onInterrupt() {
        // Required by AccessibilityService interface
    }
}

