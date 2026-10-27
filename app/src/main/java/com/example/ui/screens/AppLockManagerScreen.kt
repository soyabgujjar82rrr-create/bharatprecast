package com.example.ui.screens

import android.app.Activity
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.security.AppInfo
import com.example.data.security.AppLockBackgroundService
import com.example.data.security.AppLockManager
import com.example.data.settings.AppSettings
import com.example.data.settings.LockTimingMode
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanTertiary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.RoseSecondary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AppFilter {
    ALL, LOCKED, SYSTEM
}

@Composable
fun AppLockManagerScreen(
    appLockManager: AppLockManager,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings = appLockManager.settings

    var isMasterLockEnabled by remember { mutableStateOf(settings.isAppLockEnabled) }
    var lockTiming by remember { mutableStateOf(settings.lockTiming) }
    var isBiometricEnabled by remember { mutableStateOf(settings.isBiometricEnabled) }

    // Live permission states
    var isAccessibilityEnabled by remember { mutableStateOf(appLockManager.isAccessibilityServiceEnabled()) }
    var canDrawOverlays by remember { mutableStateOf(appLockManager.canDrawOverlays()) }
    var hasUsageStats by remember { mutableStateOf(appLockManager.hasUsageStatsPermission()) }

    // Apps list state
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(AppFilter.ALL) }
    val allInstalledApps = remember { mutableStateListOf<AppInfo>() }
    var isLoadingApps by remember { mutableStateOf(true) }

    // Dialog states
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showTimingDialog by remember { mutableStateOf(false) }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var showPermissionInfoDialog by remember { mutableStateOf(false) }

    // PIN Input states
    var pinInput by remember { mutableStateOf("") }
    var pinConfirmInput by remember { mutableStateOf("") }
    var currentPinInput by remember { mutableStateOf("") }
    var pinDialogError by remember { mutableStateOf<String?>(null) }

    // Recovery Dialog states
    var recoveryQuestionInput by remember { mutableStateOf(settings.securityQuestion ?: "What is your favorite color?") }
    var recoveryAnswerInput by remember { mutableStateOf("") }
    var recoveryDialogError by remember { mutableStateOf<String?>(null) }

    // Reload apps list
    suspend fun refreshApps() {
        isLoadingApps = true
        val apps = withContext(Dispatchers.IO) {
            appLockManager.getInstalledLaunchableApps()
        }
        allInstalledApps.clear()
        allInstalledApps.addAll(apps)
        isLoadingApps = false
    }

    LaunchedEffect(Unit) {
        refreshApps()
    }

    // Refresh permission states on resume when returning from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = appLockManager.isAccessibilityServiceEnabled()
                canDrawOverlays = appLockManager.canDrawOverlays()
                hasUsageStats = appLockManager.hasUsageStatsPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val lockedCount = allInstalledApps.count { settings.isPackageLocked(it.packageName) }

    val filteredApps = remember(allInstalledApps, searchQuery, selectedFilter, lockedCount) {
        allInstalledApps.filter { app ->
            val matchesSearch = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                AppFilter.ALL -> true
                AppFilter.LOCKED -> settings.isPackageLocked(app.packageName)
                AppFilter.SYSTEM -> app.isSystemApp
            }

            matchesSearch && matchesFilter
        }
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
                Column {
                    Text(
                        text = "App Lock Manager",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isMasterLockEnabled) "$lockedCount apps protected" else "Protection disabled",
                        fontSize = 12.sp,
                        color = if (isMasterLockEnabled) EmeraldSuccess else TextMuted
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. MASTER APP LOCK SWITCH CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isMasterLockEnabled) IndigoPrimary.copy(alpha = 0.5f) else DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (isMasterLockEnabled) IndigoPrimary else DarkSurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (isMasterLockEnabled) Color.White else TextMuted,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Enable App Lock",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (isMasterLockEnabled) "Active • PIN & Biometric enabled" else "Turn on to protect selected apps",
                                    fontSize = 12.sp,
                                    color = if (isMasterLockEnabled) EmeraldSuccess else TextMuted
                                )
                            }
                        }

                        Switch(
                            checked = isMasterLockEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (settings.pinHash == null) {
                                        showSetPinDialog = true
                                    } else {
                                        settings.isAppLockEnabled = true
                                        isMasterLockEnabled = true
                                        AppLockBackgroundService.start(context)
                                    }
                                } else {
                                    settings.isAppLockEnabled = false
                                    isMasterLockEnabled = false
                                    AppLockBackgroundService.stop(context)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = IndigoPrimary
                            )
                        )
                    }
                }
            }

            // 2. PERMISSION SETUP WIZARD (ACCESSIBILITY, OVERLAY & USAGE ACCESS)
            if (isMasterLockEnabled && (!isAccessibilityEnabled || !canDrawOverlays || !hasUsageStats)) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AmberWarning.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = AmberWarning,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "System Setup Required",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = AmberWarning
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { showPermissionInfoDialog = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Why permissions are needed",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Text(
                                text = "To protect other apps on modern Android, please grant the following official system permissions:",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )

                            // Step 1: Accessibility Service
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "1. Accessibility Service",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = if (isAccessibilityEnabled) "Enabled • Monitors app launches" else "Detects when protected apps open",
                                        fontSize = 11.sp,
                                        color = if (isAccessibilityEnabled) EmeraldSuccess else TextMuted
                                    )
                                }

                                if (isAccessibilityEnabled) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(EmeraldSuccess.copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Active", color = EmeraldSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            if (activity != null) appLockManager.openAccessibilitySettings(activity)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Enable", fontSize = 11.sp)
                                    }
                                }
                            }

                            HorizontalDivider(color = DarkBorder)

                            // Step 2: Display Over Other Apps (Overlay)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "2. Display Over Other Apps",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = if (canDrawOverlays) "Granted • Displays lock screen" else "Shows PIN screen over protected apps",
                                        fontSize = 11.sp,
                                        color = if (canDrawOverlays) EmeraldSuccess else TextMuted
                                    )
                                }

                                if (canDrawOverlays) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(EmeraldSuccess.copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Active", color = EmeraldSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            if (activity != null) appLockManager.openOverlaySettings(activity)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Grant", fontSize = 11.sp)
                                    }
                                }
                            }

                            HorizontalDivider(color = DarkBorder)

                            // Step 3: Usage Access (UsageStatsManager)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "3. Usage Access (UsageStatsManager)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = if (hasUsageStats) "Granted • Background launch detection" else "Detects foreground app launches",
                                        fontSize = 11.sp,
                                        color = if (hasUsageStats) EmeraldSuccess else TextMuted
                                    )
                                }

                                if (hasUsageStats) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(EmeraldSuccess.copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Active", color = EmeraldSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            if (activity != null) appLockManager.openUsageAccessSettings(activity)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Grant", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. SECURITY CONFIGURATION CARD (Lock Timing, Biometric, Change PIN)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "SECURITY SETTINGS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.sp
                        )

                        // Lock Timing Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTimingDialog = true }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = CyanTertiary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Lock Timing", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                                    Text("Re-locks after app is left", fontSize = 11.sp, color = TextMuted)
                                }
                            }
                            Text(
                                text = lockTiming.displayName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimary
                            )
                        }

                        HorizontalDivider(color = DarkBorder)

                        // Biometric Unlock
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Biometric Unlock", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                                    Text("Fingerprint or Face recognition", fontSize = 11.sp, color = TextMuted)
                                }
                            }
                            Switch(
                                checked = isBiometricEnabled,
                                enabled = isMasterLockEnabled && appLockManager.isBiometricSupported(),
                                onCheckedChange = {
                                    isBiometricEnabled = it
                                    settings.isBiometricEnabled = it
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = IndigoPrimary)
                            )
                        }

                        HorizontalDivider(color = DarkBorder)

                        // Change PIN & Forgot PIN Recovery buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = { showChangePinDialog = true }) {
                                Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Change PIN", fontSize = 12.sp)
                            }

                            TextButton(onClick = { showRecoveryDialog = true }) {
                                Icon(imageVector = Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Security Question", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 4. SEARCH & FILTER CONTROLS
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search installed apps...", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = TextMuted)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface,
                            focusedBorderColor = IndigoPrimary,
                            unfocusedBorderColor = DarkBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Filter Chips & Quick Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = selectedFilter == AppFilter.ALL,
                                onClick = { selectedFilter = AppFilter.ALL },
                                label = { Text("All (${allInstalledApps.size})", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = IndigoPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = selectedFilter == AppFilter.LOCKED,
                                onClick = { selectedFilter = AppFilter.LOCKED },
                                label = { Text("Locked ($lockedCount)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldSuccess,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }

                        // Quick Lock / Unlock Buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    if (settings.pinHash == null) {
                                        showSetPinDialog = true
                                    } else {
                                        val supported = allInstalledApps.filter { !it.isSystemCritical }.map { it.packageName }
                                        settings.lockAllPackages(supported)
                                        settings.isAppLockEnabled = true
                                        isMasterLockEnabled = true
                                        Toast.makeText(context, "All supported apps locked", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Text("Lock All", fontSize = 11.sp, color = IndigoPrimary)
                            }
                            TextButton(
                                onClick = {
                                    settings.unlockAllPackages()
                                    Toast.makeText(context, "All apps unlocked", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Unlock All", fontSize = 11.sp, color = RoseSecondary)
                            }
                        }
                    }
                }
            }

            // 5. APPS LIST
            items(filteredApps, key = { it.packageName }) { app ->
                val isLocked = settings.isPackageLocked(app.packageName)

                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isLocked) IndigoPrimary.copy(alpha = 0.4f) else DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            // App Icon
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                val bmp = remember(app.packageName) {
                                    try {
                                        app.icon?.toBitmap(80, 80)
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = app.appName,
                                        modifier = Modifier.size(32.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = app.appName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    if (app.isSystemCritical) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(DarkSurfaceVariant)
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text("System Exempt", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Text(
                                    text = if (app.isSystemCritical) "Exempt for emergency safety" else app.packageName,
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    maxLines = 1
                                )
                            }
                        }

                        // Switch or Disabled indicator
                        if (app.isSystemCritical) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Exempt",
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Switch(
                                checked = isLocked,
                                onCheckedChange = { checked ->
                                    if (checked && settings.pinHash == null) {
                                        showSetPinDialog = true
                                    } else {
                                        settings.setPackageLocked(app.packageName, checked)
                                        if (checked) {
                                            settings.isAppLockEnabled = true
                                            isMasterLockEnabled = true
                                        }
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = IndigoPrimary
                                )
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // ================= DIALOGS =================

    // Dialog: Lock Timing Selector
    if (showTimingDialog) {
        AlertDialog(
            onDismissRequest = { showTimingDialog = false },
            containerColor = DarkSurface,
            title = { Text("Lock Timing", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose how quickly apps re-lock after leaving them:",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    LockTimingMode.values().forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    lockTiming = mode
                                    settings.lockTiming = mode
                                    showTimingDialog = false
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = lockTiming == mode,
                                onClick = {
                                    lockTiming = mode
                                    settings.lockTiming = mode
                                    showTimingDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = IndigoPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(mode.displayName, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (mode == LockTimingMode.IMMEDIATELY) "Locks immediately when switching away" else "Re-lock after ${mode.displayName.lowercase()}",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimingDialog = false }) {
                    Text("Close", color = IndigoPrimary)
                }
            }
        )
    }

    // Dialog: Set PIN (initial setup)
    if (showSetPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showSetPinDialog = false
                pinInput = ""
                pinConfirmInput = ""
                pinDialogError = null
            },
            containerColor = DarkSurface,
            title = { Text("Create 4-Digit PIN", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Set a secure 4-digit PIN to protect your selected apps.", color = TextSecondary, fontSize = 13.sp)
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
                            isMasterLockEnabled = true
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
            title = { Text("Change 4-Digit PIN", color = TextPrimary, fontWeight = FontWeight.Bold) },
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
                            pinDialogError = "Incorrect current PIN"
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

    // Dialog: Security Recovery Question (Forgot PIN)
    if (showRecoveryDialog) {
        AlertDialog(
            onDismissRequest = {
                showRecoveryDialog = false
                recoveryAnswerInput = ""
                recoveryDialogError = null
            },
            containerColor = DarkSurface,
            title = { Text("Security Recovery Question", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Set a security question and answer so you can reset your PIN if you forget it.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = recoveryQuestionInput,
                        onValueChange = { recoveryQuestionInput = it },
                        label = { Text("Security Question") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = recoveryAnswerInput,
                        onValueChange = { recoveryAnswerInput = it },
                        label = { Text("Your Secret Answer") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    recoveryDialogError?.let {
                        Text(it, color = RoseSecondary, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (recoveryQuestionInput.isBlank()) {
                            recoveryDialogError = "Question cannot be blank"
                        } else if (recoveryAnswerInput.isBlank()) {
                            recoveryDialogError = "Answer cannot be blank"
                        } else {
                            settings.setSecurityRecovery(recoveryQuestionInput, recoveryAnswerInput)
                            showRecoveryDialog = false
                            recoveryAnswerInput = ""
                            recoveryDialogError = null
                            Toast.makeText(context, "Recovery question saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRecoveryDialog = false
                    recoveryAnswerInput = ""
                    recoveryDialogError = null
                }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Dialog: Why permissions are needed (Official Android Explanation)
    if (showPermissionInfoDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionInfoDialog = false },
            containerColor = DarkSurface,
            title = { Text("Android Security Notice", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Android strictly isolates apps from each other. To protect other installed apps, Android requires two official system mechanisms:",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "• Accessibility Service: Detects when you open a protected app so MyGallery can guard it. MyGallery does NOT read screen text, passwords, or personal messages.\n\n• Display Over Other Apps: Allows MyGallery to present the secure PIN and fingerprint screen before access is granted.",
                        color = TextPrimary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "You can disable or revoke these permissions in Android Settings at any time.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPermissionInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                ) {
                    Text("Got it", color = Color.White)
                }
            }
        )
    }
}
