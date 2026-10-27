package com.example.data.settings

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.util.UUID

enum class AppThemeMode {
    DARK, LIGHT, SYSTEM
}

enum class EditorSaveMode {
    SAVE_AS_NEW, REPLACE_ORIGINAL
}

enum class LockTimingMode(val displayName: String, val timeoutMillis: Long) {
    IMMEDIATELY("Immediately", 0L),
    AFTER_30_SECONDS("After 30 seconds", 30_000L),
    AFTER_1_MINUTE("After 1 minute", 60_000L),
    AFTER_5_MINUTES("After 5 minutes", 300_000L)
}

class AppSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("mygallery_app_settings", Context.MODE_PRIVATE)

    // PRIVACY & SECURITY
    var isAppLockEnabled: Boolean
        get() = prefs.getBoolean("key_app_lock_enabled", false)
        set(value) { prefs.edit().putBoolean("key_app_lock_enabled", value).commit() }

    var pinHash: String?
        get() = prefs.getString("key_pin_hash", null)
        private set(value) { prefs.edit().putString("key_pin_hash", value).commit() }

    private var pinSalt: String?
        get() = prefs.getString("key_pin_salt", null)
        private set(value) { prefs.edit().putString("key_pin_salt", value).commit() }

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean("key_biometric_enabled", false)
        set(value) { prefs.edit().putBoolean("key_biometric_enabled", value).commit() }

    var lockOnAppStart: Boolean
        get() = prefs.getBoolean("key_lock_on_start", true)
        set(value) { prefs.edit().putBoolean("key_lock_on_start", value).commit() }

    var lockOnBackgroundReturn: Boolean
        get() = prefs.getBoolean("key_lock_on_background", true)
        set(value) { prefs.edit().putBoolean("key_lock_on_background", value).commit() }

    // SYSTEM-LEVEL APP LOCK & PROTECTED APPS
    var lockTiming: LockTimingMode
        get() {
            val name = prefs.getString("key_lock_timing_mode", LockTimingMode.IMMEDIATELY.name)
            return try {
                LockTimingMode.valueOf(name ?: LockTimingMode.IMMEDIATELY.name)
            } catch (e: Exception) {
                LockTimingMode.IMMEDIATELY
            }
        }
        set(value) { prefs.edit().putString("key_lock_timing_mode", value.name).commit() }

    var lockedPackages: Set<String>
        get() = prefs.getStringSet("key_locked_packages", emptySet())?.toSet() ?: emptySet()
        set(value) { prefs.edit().putStringSet("key_locked_packages", HashSet(value)).commit() }

    fun isPackageLocked(packageName: String): Boolean {
        if (!isAppLockEnabled) return false
        return lockedPackages.contains(packageName)
    }

    fun setPackageLocked(packageName: String, locked: Boolean) {
        val current = lockedPackages.toMutableSet()
        if (locked) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        lockedPackages = current
    }

    fun lockAllPackages(packages: Collection<String>) {
        val current = lockedPackages.toMutableSet()
        current.addAll(packages)
        lockedPackages = current
    }

    fun unlockAllPackages() {
        lockedPackages = emptySet()
    }

    // SECURITY RECOVERY (FORGOT PIN)
    var securityQuestion: String?
        get() = prefs.getString("key_security_question", null)
        private set(value) = prefs.edit().putString("key_security_question", value).apply()

    private var securityAnswerHash: String?
        get() = prefs.getString("key_security_answer_hash", null)
        private set(value) = prefs.edit().putString("key_security_answer_hash", value).apply()

    private var securitySalt: String?
        get() = prefs.getString("key_security_salt", null)
        private set(value) = prefs.edit().putString("key_security_salt", value).apply()

    fun hasSecurityRecovery(): Boolean = !securityQuestion.isNullOrBlank() && !securityAnswerHash.isNullOrBlank()

    fun setSecurityRecovery(question: String, answer: String) {
        val salt = UUID.randomUUID().toString().take(12)
        val normalizedAnswer = answer.trim().lowercase()
        val hash = hashRecoveryAnswer(normalizedAnswer, salt)
        securityQuestion = question.trim()
        securitySalt = salt
        securityAnswerHash = hash
    }

    fun verifySecurityAnswer(answer: String): Boolean {
        val storedHash = securityAnswerHash ?: return false
        val storedSalt = securitySalt ?: return false
        val normalizedAnswer = answer.trim().lowercase()
        val computedHash = hashRecoveryAnswer(normalizedAnswer, storedSalt)
        return storedHash == computedHash
    }

    fun resetPinWithRecovery(answer: String, newPin: String): Boolean {
        if (verifySecurityAnswer(answer)) {
            setPin(newPin)
            return true
        }
        return false
    }

    private fun hashRecoveryAnswer(answer: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest((salt + answer + "MyGallery_Recovery_Salt").toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // PHOTOS & CAMERA
    var isAutoDetectCameraEnabled: Boolean
        get() = prefs.getBoolean("key_auto_detect_camera", true)
        set(value) = prefs.edit().putBoolean("key_auto_detect_camera", value).apply()

    var isAutoRefreshEnabled: Boolean
        get() = prefs.getBoolean("key_auto_refresh", true)
        set(value) = prefs.edit().putBoolean("key_auto_refresh", value).apply()

    var saveEditedAsNew: Boolean
        get() = prefs.getBoolean("key_save_edited_as_new", true)
        set(value) = prefs.edit().putBoolean("key_save_edited_as_new", value).apply()

    var confirmBeforeDelete: Boolean
        get() = prefs.getBoolean("key_confirm_before_delete", true)
        set(value) = prefs.edit().putBoolean("key_confirm_before_delete", value).apply()

    var showHiddenPhotos: Boolean
        get() = prefs.getBoolean("key_show_hidden_photos", false)
        set(value) = prefs.edit().putBoolean("key_show_hidden_photos", value).apply()

    // EDITOR
    var defaultEditorSaveMode: EditorSaveMode
        get() {
            val name = prefs.getString("key_editor_save_mode", EditorSaveMode.SAVE_AS_NEW.name)
            return try {
                EditorSaveMode.valueOf(name ?: EditorSaveMode.SAVE_AS_NEW.name)
            } catch (e: Exception) {
                EditorSaveMode.SAVE_AS_NEW
            }
        }
        set(value) = prefs.edit().putString("key_editor_save_mode", value.name).apply()

    // APP APPEARANCE & PREFERENCES
    var appTheme: AppThemeMode
        get() {
            val name = prefs.getString("key_app_theme", AppThemeMode.DARK.name)
            return try {
                AppThemeMode.valueOf(name ?: AppThemeMode.DARK.name)
            } catch (e: Exception) {
                AppThemeMode.DARK
            }
        }
        set(value) = prefs.edit().putString("key_app_theme", value.name).apply()

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean("key_notifications_enabled", true)
        set(value) = prefs.edit().putBoolean("key_notifications_enabled", value).apply()

    /**
     * Secure PIN Hashing with SHA-256 + Salt.
     * Never stores the PIN in plain text.
     */
    fun setPin(pin: String) {
        val salt = UUID.randomUUID().toString().take(12)
        val hash = hashPin(pin, salt)
        pinSalt = salt
        pinHash = hash
        isAppLockEnabled = true
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = pinHash ?: return false
        val storedSalt = pinSalt ?: return false
        val computedHash = hashPin(pin, storedSalt)
        return storedHash == computedHash
    }

    fun disablePin(currentPin: String): Boolean {
        if (verifyPin(currentPin)) {
            isAppLockEnabled = false
            pinHash = null
            pinSalt = null
            isBiometricEnabled = false
            return true
        }
        return false
    }

    private fun hashPin(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest((salt + pin + "MyGallery_Security_Pepper").toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
