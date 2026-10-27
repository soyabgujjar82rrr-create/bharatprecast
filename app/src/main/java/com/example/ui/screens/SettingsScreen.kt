package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserProfile
import com.example.data.security.AppLockManager
import com.example.data.settings.AppSettings
import com.example.data.settings.AppThemeMode
import com.example.data.settings.EditorSaveMode
import com.example.ui.theme.CyanTertiary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceContainerHigh
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.GoogleRed
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.RoseSecondary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    user: UserProfile,
    settings: AppSettings,
    appLockManager: AppLockManager,
    onBack: () -> Unit,
    onSignOut: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    // Local mutable state reflecting settings
    var appLockEnabled by remember { mutableStateOf(settings.isAppLockEnabled) }
    var biometricEnabled by remember { mutableStateOf(settings.isBiometricEnabled) }
    var lockOnStart by remember { mutableStateOf(settings.lockOnAppStart) }
    var lockOnBg by remember { mutableStateOf(settings.lockOnBackgroundReturn) }

    var autoDetectCamera by remember { mutableStateOf(settings.isAutoDetectCameraEnabled) }
    var autoRefresh by remember { mutableStateOf(settings.isAutoRefreshEnabled) }
    var confirmDelete by remember { mutableStateOf(settings.confirmBeforeDelete) }
    var showHidden by remember { mutableStateOf(settings.showHiddenPhotos) }

    var editorSaveMode by remember { mutableStateOf(settings.defaultEditorSaveMode) }
    var appTheme by remember { mutableStateOf(settings.appTheme) }
    var notifications by remember { mutableStateOf(settings.notificationsEnabled) }
    var showAppLockManagerScreen by remember { mutableStateOf(false) }

    if (showAppLockManagerScreen) {
        AppLockManagerScreen(
            appLockManager = appLockManager,
            onBack = {
                showAppLockManagerScreen = false
                appLockEnabled = settings.isAppLockEnabled
                biometricEnabled = settings.isBiometricEnabled
                lockOnStart = settings.lockOnAppStart
                lockOnBg = settings.lockOnBackgroundReturn
            }
        )
        return
    }

    // Dialog states
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showDisablePinDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showInstallApkDialog by remember { mutableStateOf(false) }

    // PIN Input temporary states
    var pinInput by remember { mutableStateOf("") }
    var pinConfirmInput by remember { mutableStateOf("") }
    var currentPinInput by remember { mutableStateOf("") }
    var pinDialogError by remember { mutableStateOf<String?>(null) }

    // Dialog: Create PIN
    if (showSetPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showSetPinDialog = false
                pinInput = ""
                pinConfirmInput = ""
                pinDialogError = null
            },
            containerColor = DarkSurface,
            title = { Text("Set 4-Digit PIN", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter a secure 4-digit PIN to lock MyGallery.", color = TextSecondary, fontSize = 13.sp)
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4) pinInput = it },
                        label = { Text("Enter 4-digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pinConfirmInput,
                        onValueChange = { if (it.length <= 4) pinConfirmInput = it },
                        label = { Text("Confirm PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    pinDialogError?.let {
                        Text(it, color = RoseSecondary, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length != 4) {
                            pinDialogError = "PIN must be exactly 4 digits"
                        } else if (pinInput != pinConfirmInput) {
                            pinDialogError = "PINs do not match"
                        } else {
                            settings.setPin(pinInput)
                            appLockEnabled = true
                            showSetPinDialog = false
                            pinInput = ""
                            pinConfirmInput = ""
                            pinDialogError = null
                            Toast.makeText(context, "App Lock enabled with your new PIN", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save PIN", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSetPinDialog = false
                    pinInput = ""
                    pinConfirmInput = ""
                    pinDialogError = null
                }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Dialog: Disable PIN
    if (showDisablePinDialog) {
        AlertDialog(
            onDismissRequest = {
                showDisablePinDialog = false
                currentPinInput = ""
                pinDialogError = null
            },
            containerColor = DarkSurface,
            title = { Text("Disable App Lock", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter your current PIN to turn off App Lock.", color = TextSecondary, fontSize = 13.sp)
                    OutlinedTextField(
                        value = currentPinInput,
                        onValueChange = { if (it.length <= 4) currentPinInput = it },
                        label = { Text("Current 4-digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    pinDialogError?.let {
                        Text(it, color = RoseSecondary, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (settings.disablePin(currentPinInput)) {
                            appLockEnabled = false
                            biometricEnabled = false
                            showDisablePinDialog = false
                            currentPinInput = ""
                            pinDialogError = null
                            Toast.makeText(context, "App Lock disabled", Toast.LENGTH_SHORT).show()
                        } else {
                            pinDialogError = "Incorrect PIN"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseSecondary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Disable", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDisablePinDialog = false
                    currentPinInput = ""
                    pinDialogError = null
                }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Dialog: Change PIN
    if (showChangePinDialog) {
        AlertDialog(
            onDismissRequest = {
                showChangePinDialog = false
                currentPinInput = ""
                pinInput = ""
                pinConfirmInput = ""
                pinDialogError = null
            },
            containerColor = DarkSurface,
            title = { Text("Change PIN", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = currentPinInput,
                        onValueChange = { if (it.length <= 4) currentPinInput = it },
                        label = { Text("Current PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4) pinInput = it },
                        label = { Text("New 4-digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pinConfirmInput,
                        onValueChange = { if (it.length <= 4) pinConfirmInput = it },
                        label = { Text("Confirm New PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    pinDialogError?.let {
                        Text(it, color = RoseSecondary, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!settings.verifyPin(currentPinInput)) {
                            pinDialogError = "Current PIN is incorrect"
                        } else if (pinInput.length != 4) {
                            pinDialogError = "New PIN must be 4 digits"
                        } else if (pinInput != pinConfirmInput) {
                            pinDialogError = "New PINs do not match"
                        } else {
                            settings.setPin(pinInput)
                            showChangePinDialog = false
                            currentPinInput = ""
                            pinInput = ""
                            pinConfirmInput = ""
                            pinDialogError = null
                            Toast.makeText(context, "PIN successfully updated", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Update PIN", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showChangePinDialog = false
                    currentPinInput = ""
                    pinInput = ""
                    pinConfirmInput = ""
                    pinDialogError = null
                }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Dialog: About MyGallery
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = DarkSurface,
            title = { Text("About MyGallery", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("MyGallery v1.0", fontWeight = FontWeight.Bold, color = CyanTertiary)
                    Text("A modern, private photo gallery app for Android featuring:", color = TextSecondary, fontSize = 13.sp)
                    Text("• Camera roll automatic detection & sync\n• Built-in advanced photo editor with Crop, Filters, Draw, Text & Undo/Redo\n• Secure App Lock with PIN and Biometric unlock\n• Original phone gallery delete protection\n• Google & Facebook cloud sync support", color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
                }
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)) {
                    Text("OK", color = Color.White)
                }
            }
        )
    }

    // Dialog: Privacy Policy
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            containerColor = DarkSurface,
            title = { Text("Privacy Policy", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Your privacy is our utmost priority. All photos and metadata in MyGallery stay strictly on your device. We do not track, collect, or transmit your private photos to third parties. Deleting a photo from MyGallery never touches or deletes your device's original camera photos.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(onClick = { showPrivacyDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)) {
                    Text("Understood", color = Color.White)
                }
            }
        )
    }

    // Dialog: Terms
    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            containerColor = DarkSurface,
            title = { Text("Terms of Service", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "MyGallery is provided to give you full control over your media with device protection. By using MyGallery, you agree to keep your App Lock PIN secure.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(onClick = { showTermsDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)) {
                    Text("Accept", color = Color.White)
                }
            }
        )
    }

    // Dialog: Install APK on Android Phone
    if (showInstallApkDialog) {
        val currentApkFile = remember {
            try {
                val src = File(context.applicationInfo.sourceDir)
                if (src.exists() && src.length() > 0) {
                    val cacheDir = File(context.cacheDir, "apks").apply { mkdirs() }
                    val target = File(cacheDir, "MyGallery.apk")
                    if (!target.exists() || target.length() != src.length()) {
                        src.copyTo(target, overwrite = true)
                    }
                    target
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
        val apkAvailable = currentApkFile != null && currentApkFile.exists() && currentApkFile.length() > 0
        val apkSizeMb = if (apkAvailable) String.format("%.1f", currentApkFile!!.length() / (1024f * 1024f)) else "26.1"

        AlertDialog(
            onDismissRequest = { showInstallApkDialog = false },
            containerColor = DarkSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Android, contentDescription = null, tint = EmeraldSuccess)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Install MyGallery APK", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Build Status: Verified Android APK ($apkSizeMb MB)",
                        color = if (apkAvailable) EmeraldSuccess else RoseSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "1. Direct Install: Tap 'Install APK' to launch Android's package installer.\n2. Export to Phone: Tap 'Share APK' to send or save MyGallery.apk via Quick Share, WhatsApp, Drive, or Files.\n3. Note: If prompted, allow 'Install unknown apps' in Android settings.",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    if (!apkAvailable) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(RoseSecondary.copy(alpha = 0.15f))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Notice: Standalone APK binary is not found on this device.",
                                color = RoseSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Share / Export APK
                    Button(
                        onClick = {
                            if (apkAvailable && currentApkFile != null) {
                                try {
                                    val apkUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        currentApkFile
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/vnd.android.package-archive"
                                        putExtra(Intent.EXTRA_STREAM, apkUri)
                                        putExtra(Intent.EXTRA_SUBJECT, "MyGallery.apk")
                                        putExtra(Intent.EXTRA_TEXT, "MyGallery Android APK ($apkSizeMb MB)")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share or Save MyGallery.apk"))
                                    showInstallApkDialog = false
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Share error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Error: Real APK binary is not available to export.", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share APK", color = Color.White, fontSize = 12.sp)
                    }

                    // Install APK
                    Button(
                        onClick = {
                            if (apkAvailable && currentApkFile != null) {
                                try {
                                    val apkUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        currentApkFile
                                    )
                                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(installIntent)
                                    showInstallApkDialog = false
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Install error: ${e.localizedMessage}. Try 'Share APK' to save.", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "Error: Real APK binary is not available to install.", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Install APK", color = Color.White, fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showInstallApkDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            }
        )
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("settings_screen")
        ) {
            // ================= 1. ACCOUNT SECTION =================
            SettingsSectionHeader(title = "ACCOUNT", icon = Icons.Default.Person)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(user.displayName ?: "User", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(user.email ?: "local@mygallery.app", color = TextMuted, fontSize = 13.sp)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Active", color = EmeraldSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(color = DarkBorder)

                    // Google Account Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(GoogleRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("G", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Google Account", color = TextPrimary, fontSize = 14.sp)
                        }
                        val isGoogleLinked = user.email?.contains("@gmail.com") == true
                        Text(
                            text = if (isGoogleLinked) "Connected" else "Not Linked",
                            color = if (isGoogleLinked) EmeraldSuccess else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Facebook Account Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(FacebookBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("f", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Facebook Account", color = TextPrimary, fontSize = 14.sp)
                        }
                        Text("Ready to Link", color = CyanTertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    HorizontalDivider(color = DarkBorder)

                    // Sign Out Action
                    Button(
                        onClick = onSignOut,
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = RoseSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out / Switch User", color = RoseSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ================= 2. PRIVACY & SECURITY =================
            SettingsSectionHeader(title = "PRIVACY & SECURITY", icon = Icons.Default.Security)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Manage Protected Apps (Full System App Lock Manager)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(IndigoPrimary.copy(alpha = 0.14f))
                            .clickable { showAppLockManagerScreen = true }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(IndigoPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Apps,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("App Lock Manager", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    text = if (settings.isAppLockEnabled) "${settings.lockedPackages.size} apps protected • ${settings.lockTiming.displayName}" else "Protect installed apps with PIN/Biometrics",
                                    color = if (settings.isAppLockEnabled) EmeraldSuccess else TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open",
                            tint = IndigoPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    HorizontalDivider(color = DarkBorder)

                    // App Lock Switch
                    SettingsToggleRow(
                        title = "App Lock",
                        subtitle = "Require PIN or Biometrics to open MyGallery",
                        icon = Icons.Default.Lock,
                        checked = appLockEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                showSetPinDialog = true
                            } else {
                                showDisablePinDialog = true
                            }
                        }
                    )

                    // Change PIN button (if enabled)
                    AnimatedVisibility(visible = appLockEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showChangePinDialog = true }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Change 4-Digit PIN", color = IndigoPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Edit", color = TextMuted, fontSize = 12.sp)
                        }
                    }

                    // Biometric Unlock Switch
                    SettingsToggleRow(
                        title = "Biometric Unlock",
                        subtitle = "Unlock with Fingerprint or Face recognition",
                        icon = Icons.Default.Fingerprint,
                        checked = biometricEnabled,
                        enabled = appLockEnabled,
                        onCheckedChange = {
                            biometricEnabled = it
                            settings.isBiometricEnabled = it
                        }
                    )

                    // Lock when App Starts
                    SettingsToggleRow(
                        title = "Lock on App Start",
                        subtitle = "Prompt authentication every time app launches",
                        icon = Icons.Default.Lock,
                        checked = lockOnStart,
                        enabled = appLockEnabled,
                        onCheckedChange = {
                            lockOnStart = it
                            settings.lockOnAppStart = it
                        }
                    )

                    // Lock when returning from background
                    SettingsToggleRow(
                        title = "Lock on Background Return",
                        subtitle = "Lock immediately when switching back from other apps",
                        icon = Icons.Default.Lock,
                        checked = lockOnBg,
                        enabled = appLockEnabled,
                        onCheckedChange = {
                            lockOnBg = it
                            settings.lockOnBackgroundReturn = it
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ================= 3. PHOTOS =================
            SettingsSectionHeader(title = "PHOTOS & CAMERA", icon = Icons.Default.PhotoLibrary)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingsToggleRow(
                        title = "Auto-Detect Camera Photos",
                        subtitle = "Automatically display new photos taken with your phone's camera",
                        icon = Icons.Default.CameraAlt,
                        checked = autoDetectCamera,
                        onCheckedChange = {
                            autoDetectCamera = it
                            settings.isAutoDetectCameraEnabled = it
                        }
                    )

                    SettingsToggleRow(
                        title = "Auto-Refresh Gallery",
                        subtitle = "Keep gallery in sync with device storage in real-time",
                        icon = Icons.Default.Refresh,
                        checked = autoRefresh,
                        onCheckedChange = {
                            autoRefresh = it
                            settings.isAutoRefreshEnabled = it
                        }
                    )

                    SettingsToggleRow(
                        title = "Confirm Before Deleting",
                        subtitle = "Display reminder that original phone photo will remain safe",
                        icon = Icons.Default.Delete,
                        checked = confirmDelete,
                        onCheckedChange = {
                            confirmDelete = it
                            settings.confirmBeforeDelete = it
                        }
                    )

                    SettingsToggleRow(
                        title = "Show Hidden Photos",
                        subtitle = "Display private / archived photos in gallery",
                        icon = Icons.Default.Visibility,
                        checked = showHidden,
                        onCheckedChange = {
                            showHidden = it
                            settings.showHiddenPhotos = it
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ================= 4. EDITOR =================
            SettingsSectionHeader(title = "PHOTO EDITOR", icon = Icons.Default.Edit)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Default Save Behavior", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editorSaveMode = EditorSaveMode.SAVE_AS_NEW
                                settings.defaultEditorSaveMode = EditorSaveMode.SAVE_AS_NEW
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = editorSaveMode == EditorSaveMode.SAVE_AS_NEW,
                            onClick = {
                                editorSaveMode = EditorSaveMode.SAVE_AS_NEW
                                settings.defaultEditorSaveMode = EditorSaveMode.SAVE_AS_NEW
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = IndigoPrimary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Save As New Photo (Recommended)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Preserves original photo and saves edited copy", color = TextMuted, fontSize = 11.sp)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editorSaveMode = EditorSaveMode.REPLACE_ORIGINAL
                                settings.defaultEditorSaveMode = EditorSaveMode.REPLACE_ORIGINAL
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = editorSaveMode == EditorSaveMode.REPLACE_ORIGINAL,
                            onClick = {
                                editorSaveMode = EditorSaveMode.REPLACE_ORIGINAL
                                settings.defaultEditorSaveMode = EditorSaveMode.REPLACE_ORIGINAL
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = IndigoPrimary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Replace in MyGallery", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Updates MyGallery record without deleting original device photo", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ================= 5. APP =================
            SettingsSectionHeader(title = "APP & ABOUT", icon = Icons.Default.Info)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Theme Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.ColorLens, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Appearance", color = TextPrimary, fontSize = 14.sp)
                        }
                        Text("Dark Mode", color = CyanTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    SettingsToggleRow(
                        title = "Notifications",
                        subtitle = "Notify when new camera photos are synced",
                        icon = Icons.Default.Notifications,
                        checked = notifications,
                        onCheckedChange = {
                            notifications = it
                            settings.notificationsEnabled = it
                        }
                    )

                    HorizontalDivider(color = DarkBorder)

                    // Install APK on Android Phone
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(EmeraldSuccess.copy(alpha = 0.15f))
                            .clickable { showInstallApkDialog = true }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldSuccess),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Install APK",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Install APK on Phone", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Tap to install or share MyGallery.apk (26.1 MB)", color = EmeraldSuccess, fontSize = 11.sp)
                            }
                        }
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(16.dp))
                    }

                    HorizontalDivider(color = DarkBorder)

                    // About MyGallery
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAboutDialog = true }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("About MyGallery", color = TextPrimary, fontSize = 14.sp)
                        Text("v1.0", color = TextMuted, fontSize = 12.sp)
                    }

                    // Privacy Policy
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPrivacyDialog = true }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Privacy Policy", color = TextPrimary, fontSize = 14.sp)
                        Text("Read", color = IndigoPrimary, fontSize = 12.sp)
                    }

                    // Terms of Service
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTermsDialog = true }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Terms of Service", color = TextPrimary, fontSize = 14.sp)
                        Text("Read", color = IndigoPrimary, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun SettingsSectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) (if (checked) IndigoPrimary else TextMuted) else DarkBorder,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = if (enabled) TextPrimary else TextMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = IndigoPrimary,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = DarkSurfaceVariant
            )
        )
    }
}
