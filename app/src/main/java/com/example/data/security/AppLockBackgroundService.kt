package com.example.data.security

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.ui.screens.AppLockOverlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Background Service using Android UsageStatsManager.
 * Detects foreground app launches in real-time and intercepts locked apps
 * with the custom Lock Screen PIN activity.
 */
class AppLockBackgroundService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private var usageStatsManager: UsageStatsManager? = null
    private var powerManager: PowerManager? = null
    private var screenReceiver: BroadcastReceiver? = null

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "channel_app_lock_protection"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, AppLockBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    context.startForegroundService(intent)
                } catch (e: Exception) {
                    context.startService(intent)
                }
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AppLockBackgroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager

        startInForeground()
        registerScreenStateReceiver()
        startMonitoringLoop()
    }

    private fun startInForeground() {
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("App Lock Protection Active")
            .setContentText("Monitoring protected applications")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "App Lock Protection",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Monitors protected apps for secure PIN verification"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun registerScreenStateReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    // Lock all packages when screen turns off for maximum security
                    val lockManager = AppLockManager.getInstance(applicationContext)
                    lockManager.lockAll()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun startMonitoringLoop() {
        serviceScope.launch {
            val lockManager = AppLockManager.getInstance(applicationContext)
            val myPackage = applicationContext.packageName

            while (isActive) {
                try {
                    val isScreenInteractive = powerManager?.isInteractive ?: true
                    if (isScreenInteractive && lockManager.settings.isAppLockEnabled) {
                        val currentForegroundPkg = getForegroundPackage()

                        if (!currentForegroundPkg.isNullOrBlank() &&
                            currentForegroundPkg != myPackage &&
                            currentForegroundPkg != "com.android.systemui" &&
                            !currentForegroundPkg.contains("launcher") &&
                            !currentForegroundPkg.contains("inputmethod")
                        ) {
                            // Check if this exact package is configured to be locked
                            if (lockManager.isPackageLocked(currentForegroundPkg) &&
                                !lockManager.isPackageUnlocked(currentForegroundPkg)
                            ) {
                                // Intercept with custom Lock Screen PIN activity
                                launchLockOverlay(currentForegroundPkg)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                delay(250) // High-responsiveness polling interval
            }
        }
    }

    private fun getForegroundPackage(): String? {
        val usm = usageStatsManager ?: return null
        val now = System.currentTimeMillis()

        // 1. Query real-time usage events (most accurate)
        try {
            val events = usm.queryEvents(now - 3000, now)
            val event = UsageEvents.Event()
            var lastResumedPackage: String? = null

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                ) {
                    lastResumedPackage = event.packageName
                }
            }

            if (!lastResumedPackage.isNullOrBlank()) {
                return lastResumedPackage
            }
        } catch (e: Exception) {
            // Fallback below
        }

        // 2. Fallback to queryUsageStats
        try {
            val statsList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 10000, now)
            if (!statsList.isNullOrEmpty()) {
                val mostRecent = statsList.maxByOrNull { it.lastTimeUsed }
                return mostRecent?.packageName
            }
        } catch (e: Exception) {
            // Ignored
        }

        return null
    }

    private fun launchLockOverlay(targetPackage: String) {
        val intent = Intent(this, AppLockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            putExtra(AppLockOverlayActivity.EXTRA_LOCKED_PACKAGE, targetPackage)
            putExtra(AppLockOverlayActivity.EXTRA_IS_SHARE_FLOW, false)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // Already unregistered
            }
        }
    }
}
