package com.example.data.security

import android.app.Activity
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import com.example.data.settings.AppSettings
import com.example.data.settings.LockTimingMode
import java.util.concurrent.ConcurrentHashMap

class AppLockManager(private val context: Context) {
    val settings: AppSettings = AppSettings(context)
    private var cancellationSignal: CancellationSignal? = null

    // Tracks unlock timestamps per package name.
    // KEY: Exact Android package name (e.g. "com.whatsapp", "com.instagram.android")
    // VALUE: System.currentTimeMillis() when successfully unlocked
    // Thread-safe, per-package session tracking.
    // NO GLOBAL/STATIC VARIABLE FOR CURRENTLY PROTECTED APP!
    private val unlockedSessions = ConcurrentHashMap<String, Long>()

    companion object {
        @Volatile
        private var INSTANCE: AppLockManager? = null

        fun getInstance(context: Context): AppLockManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppLockManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun resetForTesting() {
            synchronized(this) {
                INSTANCE = null
            }
        }
    }

    // ================= 1. PERMISSIONS & SETUP DETECTION =================

    /**
     * Checks if the MyGallery Accessibility Service is enabled in Android Settings.
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(context, AppLockAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if the app has permission to draw overlay windows on top of other apps.
     */
    fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Checks if Usage Access permission is granted.
     */
    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openAccessibilitySettings(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openOverlaySettings(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                activity.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    activity.startActivity(intent)
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }
        }
    }

    fun openUsageAccessSettings(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ================= 2. APP DISCOVERY & FILTERING =================

    /**
     * Discovers all launchable applications installed on the phone.
     * Prevents locking system-critical apps (e.g. Phone dialer, Emergency, Home launcher).
     */
    fun getInstalledLaunchableApps(): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)

        // Critical packages that should never be blocked to prevent locking the user out of the phone
        val criticalPackages = setOf(
            "com.android.phone",
            "com.android.emergency",
            "com.android.server.telecom",
            "com.google.android.dialer",
            "com.android.systemui",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.vending.setup",
            "com.google.android.setupwizard"
        )

        // Detect default Home launcher package
        val defaultHomePackage = try {
            pm.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }

        val apps = mutableListOf<AppInfo>()
        val seenPackages = mutableSetOf<String>()

        for (resolveInfo in resolveInfos) {
            val pkg = resolveInfo.activityInfo.packageName
            if (seenPackages.contains(pkg)) continue
            seenPackages.add(pkg)

            val appName = try {
                resolveInfo.loadLabel(pm).toString()
            } catch (e: Exception) {
                pkg
            }

            val icon = try {
                resolveInfo.loadIcon(pm)
            } catch (e: Exception) {
                null
            }

            val appInfo = resolveInfo.activityInfo.applicationInfo
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isCritical = criticalPackages.contains(pkg) || pkg == defaultHomePackage

            apps.add(
                AppInfo(
                    packageName = pkg,
                    appName = appName,
                    icon = icon,
                    isSystemApp = isSystem,
                    isSystemCritical = isCritical,
                    isLocked = settings.isPackageLocked(pkg)
                )
            )
        }

        // Always include MyGallery itself in the list
        val myPkg = context.packageName
        if (!seenPackages.contains(myPkg)) {
            val myIcon = try {
                pm.getApplicationIcon(myPkg)
            } catch (e: Exception) {
                null
            }
            apps.add(
                AppInfo(
                    packageName = myPkg,
                    appName = "MyGallery",
                    icon = myIcon,
                    isSystemApp = false,
                    isSystemCritical = false,
                    isLocked = settings.isAppLockEnabled
                )
            )
        }

        // Sort: Locked apps first, then alphabetical by app name
        return apps.sortedWith(
            compareByDescending<AppInfo> { it.isLocked }
                .thenBy { it.appName.lowercase() }
        )
    }

    // ================= 3. PER-PACKAGE LOCK & SESSION MANAGEMENT =================

    /**
     * Checks if a package is configured to be locked by the user.
     * Uses: lockedPackages: Set<String>
     */
    fun isPackageLocked(packageName: String): Boolean {
        if (!settings.isAppLockEnabled) return false
        return settings.isPackageLocked(packageName)
    }

    /**
     * Checks if an exact package currently has an active, valid unlocked session.
     */
    fun isPackageUnlocked(packageName: String): Boolean {
        if (!isPackageLocked(packageName)) return true
        val lastUnlockTime = unlockedSessions[packageName] ?: return false
        val timeout = settings.lockTiming.timeoutMillis
        if (timeout == 0L) {
            // IMMEDIATELY: Session valid only for 1.5 seconds right after unlocking to let the app resume
            return (System.currentTimeMillis() - lastUnlockTime) <= 1500L
        }
        val elapsed = System.currentTimeMillis() - lastUnlockTime
        return elapsed <= timeout
    }

    /**
     * Marks an exact package as unlocked at this moment.
     */
    fun markPackageUnlocked(packageName: String) {
        if (packageName.isNotBlank()) {
            unlockedSessions[packageName] = System.currentTimeMillis()
        }
    }

    /**
     * Immediately locks an exact package (removes its unlock session).
     */
    fun lockPackage(packageName: String) {
        if (packageName.isNotBlank()) {
            unlockedSessions.remove(packageName)
        }
    }

    /**
     * Locks all packages (e.g. on device sleep, reboot, or user manual lock).
     */
    fun lockAll() {
        unlockedSessions.clear()
    }

    fun invalidateAllSessions() {
        lockAll()
    }

    /**
     * Alias for isPackageUnlocked for backward compatibility and test verification.
     */
    fun isPackageSessionValid(packageName: String): Boolean = isPackageUnlocked(packageName)

    /**
     * Handles package transition event.
     * If lock timing is IMMEDIATELY, leaving a protected package invalidates its session.
     */
    fun onForegroundPackageChanged(newPackage: String) {
        if (newPackage.isBlank()) return
        if (settings.lockTiming.timeoutMillis == 0L) {
            // IMMEDIATELY mode: invalidate any other package sessions
            unlockedSessions.keys.filter { it != newPackage }.forEach { pkg ->
                unlockedSessions.remove(pkg)
            }
        }
    }

    /**
     * Evaluates whether an exact incoming package and activity class should be intercepted by App Lock.
     * Guaranteed:
     * - Uses exact packageName
     * - Checks exact lockedPackages Set
     * - Share intents (ACTION_SEND / ACTION_SEND_MULTIPLE / Chooser) ALWAYS require lock
     * - Returns true ONLY if packageName is locked AND session is not unlocked
     */
    fun shouldLockPackage(packageName: String, className: String = ""): Boolean {
        if (packageName.isBlank()) return false
        if (!settings.isAppLockEnabled) return false

        // Check exact package against locked-app list
        if (!settings.isPackageLocked(packageName)) {
            return false
        }

        // Special protection: Share Intent & external Send/View activities MUST ALWAYS require PIN/biometric
        if (isShareIntentAction(className)) {
            unlockedSessions.remove(packageName)
            return true
        }

        // Check if package has an active unlocked session
        val lastUnlock = unlockedSessions[packageName]
        if (lastUnlock != null) {
            val timeout = settings.lockTiming.timeoutMillis
            if (timeout == 0L) {
                // Immediately mode: allow only the initial transition right after unlocking
                if ((System.currentTimeMillis() - lastUnlock) <= 1500L) {
                    return false
                }
                return true
            } else {
                val elapsed = System.currentTimeMillis() - lastUnlock
                if (elapsed <= timeout) {
                    return false
                }
            }
        }

        return true
    }

    /**
     * Detects if the opened activity is an Android Share/Send/Chooser activity.
     */
    fun isShareIntentAction(className: String): Boolean {
        if (className.isBlank()) return false
        val lower = className.lowercase()
        return lower.contains("share") ||
                lower.contains("send") ||
                lower.contains("picker") ||
                lower.contains("chooser") ||
                lower.contains("contactpicker") ||
                lower.contains("intentresolver") ||
                lower.contains("sendmedia") ||
                lower.contains("directshare")
    }

    // ================= 4. BIOMETRIC AUTHENTICATION =================

    fun isBiometricSupported(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val biometricManager = context.getSystemService(BiometricManager::class.java)
            if (biometricManager != null) {
                val canAuth = biometricManager.canAuthenticate()
                return canAuth == BiometricManager.BIOMETRIC_SUCCESS
            }
        }
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguard?.isDeviceSecure == true
    }

    fun authenticateWithBiometrics(
        activity: Activity,
        targetAppName: String = "MyGallery",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            cancellationSignal?.cancel()
            cancellationSignal = CancellationSignal()

            try {
                val prompt = BiometricPrompt.Builder(activity)
                    .setTitle("Unlock $targetAppName")
                    .setSubtitle("Use your biometric credential to access $targetAppName")
                    .setNegativeButton("Use PIN", activity.mainExecutor, DialogInterface.OnClickListener { _, _ ->
                        onError("PIN required")
                    })
                    .build()

                prompt.authenticate(
                    cancellationSignal!!,
                    activity.mainExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                            super.onAuthenticationSucceeded(result)
                            onSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            super.onAuthenticationError(errorCode, errString)
                            if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED) {
                                onError(errString?.toString() ?: "Biometric authentication failed")
                            }
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            onError("Biometric not recognized. Please try again.")
                        }
                    }
                )
            } catch (e: Exception) {
                onError("Biometric error: ${e.localizedMessage}")
            }
        } else {
            onError("Biometric unlock is not supported on this Android version")
        }
    }

    fun cancelAuthentication() {
        cancellationSignal?.cancel()
        cancellationSignal = null
    }
}
