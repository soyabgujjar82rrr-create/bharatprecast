package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.security.AppLockManager
import com.example.data.settings.AppSettings
import com.example.data.settings.LockTimingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLockManagerTest {

    private lateinit var context: Context
    private lateinit var settings: AppSettings
    private lateinit var appLockManager: AppLockManager

    @Before
    fun setUp() {
        AppLockManager.resetForTesting()
        context = ApplicationProvider.getApplicationContext()
        settings = AppSettings(context)
        // Clear prefs and sessions before each test
        settings.unlockAllPackages()
        settings.lockTiming = LockTimingMode.IMMEDIATELY
        appLockManager = AppLockManager.getInstance(context)
        appLockManager.invalidateAllSessions()
    }

    @Test
    fun testPinHashingAndVerification() {
        settings.setPin("1234")
        assertTrue("App lock should be enabled after setting PIN", settings.isAppLockEnabled)
        assertNotNull("PIN hash should exist", settings.pinHash)
        // Never store plain text
        assertFalse("PIN must not be stored in plaintext", settings.pinHash == "1234")
        assertTrue("Correct PIN verification should succeed", settings.verifyPin("1234"))
        assertFalse("Wrong PIN verification should fail", settings.verifyPin("9999"))
        assertFalse("Empty PIN verification should fail", settings.verifyPin(""))
    }

    @Test
    fun testSecurityRecoveryResetPin() {
        settings.setPin("1234")
        settings.setSecurityRecovery("What is your pet?", "Fluffy")

        assertTrue("Should report having recovery set", settings.hasSecurityRecovery())
        assertTrue("Correct answer verification should succeed", settings.verifySecurityAnswer("fluffy"))
        assertTrue("Case insensitive answer verification should succeed", settings.verifySecurityAnswer("FLUFFY"))
        assertFalse("Wrong answer verification should fail", settings.verifySecurityAnswer("Rex"))

        val resetSuccess = settings.resetPinWithRecovery("fluffy", "5678")
        assertTrue("Reset with recovery should succeed", resetSuccess)
        assertTrue("New PIN should work", settings.verifyPin("5678"))
        assertFalse("Old PIN should fail", settings.verifyPin("1234"))
    }

    @Test
    fun testPackageLockAndUnlock() {
        val whatsapp = "com.whatsapp"
        val instagram = "com.instagram"

        settings.setPin("1234")
        settings.setPackageLocked(whatsapp, true)
        settings.setPackageLocked(instagram, true)

        assertTrue(settings.isPackageLocked(whatsapp))
        assertTrue(settings.isPackageLocked(instagram))
        assertEquals(2, settings.lockedPackages.size)

        settings.setPackageLocked(whatsapp, false)
        assertFalse(settings.isPackageLocked(whatsapp))
        assertTrue(settings.isPackageLocked(instagram))

        settings.lockAllPackages(listOf("com.facebook.katana", "com.google.android.youtube"))
        assertTrue(settings.isPackageLocked("com.facebook.katana"))
        assertTrue(settings.isPackageLocked("com.google.android.youtube"))

        settings.unlockAllPackages()
        assertFalse(settings.isPackageLocked(instagram))
        assertEquals(0, settings.lockedPackages.size)
    }

    @Test
    fun testLockTimingModes() {
        settings.lockTiming = LockTimingMode.IMMEDIATELY
        assertEquals(0L, settings.lockTiming.timeoutMillis)

        settings.lockTiming = LockTimingMode.AFTER_30_SECONDS
        assertEquals(30000L, settings.lockTiming.timeoutMillis)

        settings.lockTiming = LockTimingMode.AFTER_1_MINUTE
        assertEquals(60000L, settings.lockTiming.timeoutMillis)

        settings.lockTiming = LockTimingMode.AFTER_5_MINUTES
        assertEquals(300000L, settings.lockTiming.timeoutMillis)
    }

    @Test
    fun testInstalledAppsCriticalPackagesProtection() {
        val apps = appLockManager.getInstalledLaunchableApps()
        assertNotNull(apps)
        // Ensure MyGallery is in the list
        val myApp = apps.find { it.packageName == context.packageName }
        assertNotNull("MyGallery itself must be present in the launchable apps list", myApp)

        // Ensure dialer/systemui are marked critical
        apps.filter { it.packageName in listOf("com.android.phone", "com.android.systemui", "com.google.android.dialer") }
            .forEach {
                assertTrue("Dialer/SystemUI must be marked as system critical exempt", it.isSystemCritical)
            }
    }

    // ================= TEST CASES A THROUGH H =================

    @Test
    fun testCaseA_WhatsAppLockON_FromLauncherRequiresLock() {
        val whatsapp = "com.whatsapp"
        settings.setPin("1234")
        settings.setPackageLocked(whatsapp, true)

        // User was in Launcher, then opens WhatsApp
        appLockManager.onForegroundPackageChanged("com.google.android.apps.nexuslauncher")
        val requiresLock = appLockManager.shouldLockPackage(whatsapp, "com.whatsapp.HomeActivity")

        assertTrue("Test Case A: WhatsApp lock ON from launcher must require App Lock PIN", requiresLock)
    }

    @Test
    fun testCaseB_WhatsAppLockON_FromShareIntentRequiresLock() {
        val whatsapp = "com.whatsapp"
        settings.setPin("1234")
        settings.setPackageLocked(whatsapp, true)

        // User is in Gallery or Share Sheet, then selects WhatsApp
        appLockManager.onForegroundPackageChanged("com.android.intentresolver")
        val requiresLock = appLockManager.shouldLockPackage(whatsapp, "com.whatsapp.ContactPicker")

        assertTrue("Test Case B: WhatsApp lock ON via Share intent must require App Lock PIN BEFORE WhatsApp is usable", requiresLock)
    }

    @Test
    fun testCaseC_WhatsAppLockOFF_FromLauncherOpensNormally() {
        val whatsapp = "com.whatsapp"
        settings.setPin("1234")
        settings.setPackageLocked(whatsapp, false) // Lock OFF

        appLockManager.onForegroundPackageChanged("com.google.android.apps.nexuslauncher")
        val requiresLock = appLockManager.shouldLockPackage(whatsapp, "com.whatsapp.HomeActivity")

        assertFalse("Test Case C: WhatsApp lock OFF from launcher must NOT require App Lock PIN", requiresLock)
    }

    @Test
    fun testCaseD_WhatsAppLockOFF_FromShareIntentOpensNormally() {
        val whatsapp = "com.whatsapp"
        settings.setPin("1234")
        settings.setPackageLocked(whatsapp, false) // Lock OFF

        appLockManager.onForegroundPackageChanged("com.android.intentresolver")
        val requiresLock = appLockManager.shouldLockPackage(whatsapp, "com.whatsapp.ContactPicker")

        assertFalse("Test Case D: WhatsApp lock OFF via Share intent must NOT require App Lock PIN", requiresLock)
    }

    @Test
    fun testCaseE_YouTubeON_InstagramOFF() {
        val youtube = "com.google.android.youtube"
        val instagram = "com.instagram.android"

        settings.setPin("1234")
        settings.setPackageLocked(youtube, true)
        settings.setPackageLocked(instagram, false)

        appLockManager.onForegroundPackageChanged("com.google.android.apps.nexuslauncher")

        val youtubeRequiresLock = appLockManager.shouldLockPackage(youtube, "com.google.android.youtube.MainActivity")
        val instagramRequiresLock = appLockManager.shouldLockPackage(instagram, "com.instagram.mainactivity.MainActivity")

        assertTrue("Test Case E: YouTube with lock ON must require App Lock", youtubeRequiresLock)
        assertFalse("Test Case E: Instagram with lock OFF must NOT require App Lock", instagramRequiresLock)
    }

    @Test
    fun testCaseF_PersistLockedAppsAcrossReboots() {
        val whatsapp = "com.whatsapp"
        val telegram = "org.telegram.messenger"

        settings.setPin("1234")
        settings.setPackageLocked(whatsapp, true)
        settings.setPackageLocked(telegram, true)

        // Simulate reboot / new instance reading from SharedPreferences
        val rebootedSettings = AppSettings(context)
        assertTrue("Test Case F: WhatsApp remains locked after restart", rebootedSettings.isPackageLocked(whatsapp))
        assertTrue("Test Case F: Telegram remains locked after restart", rebootedSettings.isPackageLocked(telegram))
        assertTrue("Test Case F: PIN hash remains intact after restart", rebootedSettings.verifyPin("1234"))
    }

    @Test
    fun testCaseG_WrongPinFailsVerification() {
        settings.setPin("1234")
        assertFalse("Test Case G: Wrong PIN verification must fail", settings.verifyPin("0000"))
        assertFalse("Test Case G: Partially correct PIN must fail", settings.verifyPin("123"))
        assertFalse("Test Case G: Wrong length PIN must fail", settings.verifyPin("12345"))
    }

    @Test
    fun testCaseH_UnlockAndTimingRelock() {
        val whatsapp = "com.whatsapp"
        settings.setPin("1234")
        settings.setPackageLocked(whatsapp, true)
        settings.lockTiming = LockTimingMode.IMMEDIATELY

        // Unlock WhatsApp
        appLockManager.markPackageUnlocked(whatsapp)
        assertTrue("Session should be valid immediately after unlocking", appLockManager.isPackageSessionValid(whatsapp))

        // Leaving WhatsApp to go Home / another app immediately invalidates session
        appLockManager.onForegroundPackageChanged("com.google.android.apps.nexuslauncher")
        assertFalse("Test Case H: Session must be invalid after leaving app when timing is IMMEDIATELY", appLockManager.isPackageSessionValid(whatsapp))
    }
}
