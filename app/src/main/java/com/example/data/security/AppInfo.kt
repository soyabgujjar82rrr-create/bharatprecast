package com.example.data.security

import android.graphics.drawable.Drawable

/**
 * Model representing an installed launchable application on the Android device.
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean = false,
    val isSystemCritical: Boolean = false, // Critical apps like Phone dialer, emergency dialer, home launcher
    val isLocked: Boolean = false
)
