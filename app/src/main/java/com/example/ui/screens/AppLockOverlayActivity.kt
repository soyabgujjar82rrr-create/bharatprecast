package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.data.security.AppLockManager
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RoseSecondary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class AppLockOverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_LOCKED_PACKAGE = "extra_locked_package"
        const val EXTRA_IS_SHARE_FLOW = "extra_is_share_flow"
    }

    private var currentPackageState by mutableStateOf("")
    private var isShareFlow by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)

        // Block screenshots, background peeking, and recent apps preview
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        window.setBackgroundDrawableResource(android.R.color.black)
        enableEdgeToEdge()

        val lockedPackage = intent.getStringExtra(EXTRA_LOCKED_PACKAGE) ?: ""
        isShareFlow = intent.getBooleanExtra(EXTRA_IS_SHARE_FLOW, false)
        val lockManager = AppLockManager.getInstance(applicationContext)
        if (lockedPackage.isBlank() || !lockManager.isPackageLocked(lockedPackage)) {
            finish()
            overridePendingTransition(0, 0)
            return
        }
        currentPackageState = lockedPackage

        setContent {
            MyApplicationTheme {
                key(currentPackageState) {
                    AppLockOverlayEntry(
                        targetPackage = currentPackageState,
                        onUnlockSuccess = {
                            val target = currentPackageState
                            val lockMgr = AppLockManager.getInstance(applicationContext)
                            lockMgr.markPackageUnlocked(target)

                            // If not a share sheet flow, ensure the target application is resumed cleanly
                            if (!isShareFlow) {
                                try {
                                    val launchIntent = packageManager.getLaunchIntentForPackage(target)
                                    if (launchIntent != null) {
                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        startActivity(launchIntent)
                                    }
                                } catch (e: Exception) {
                                    // Ignored, finish() returns to it
                                }
                            }
                            finish()
                            overridePendingTransition(0, 0)
                        },
                        onCancelOrHome = {
                            // Return to Home screen securely without exposing protected app
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(homeIntent)
                            finish()
                            overridePendingTransition(0, 0)
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        overridePendingTransition(0, 0)
        val newPkg = intent.getStringExtra(EXTRA_LOCKED_PACKAGE) ?: ""
        isShareFlow = intent.getBooleanExtra(EXTRA_IS_SHARE_FLOW, false)
        val lockManager = AppLockManager.getInstance(applicationContext)
        if (newPkg.isNotBlank() && lockManager.isPackageLocked(newPkg)) {
            currentPackageState = newPkg
        } else {
            finish()
            overridePendingTransition(0, 0)
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}

@Composable
fun AppLockOverlayEntry(
    targetPackage: String,
    onUnlockSuccess: () -> Unit,
    onCancelOrHome: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager

    val appName = remember(targetPackage) {
        try {
            val appInfo = pm.getApplicationInfo(targetPackage, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            "Protected App"
        }
    }

    val appIcon = remember(targetPackage) {
        try {
            pm.getApplicationIcon(targetPackage)
        } catch (e: Exception) {
            null
        }
    }

    AppLockOverlayContent(
        packageName = targetPackage,
        appName = appName,
        appIcon = appIcon,
        onUnlockSuccess = onUnlockSuccess,
        onCancelOrHome = onCancelOrHome
    )
}

@Composable
fun AppLockOverlayContent(
    packageName: String,
    appName: String,
    appIcon: Drawable?,
    onUnlockSuccess: () -> Unit,
    onCancelOrHome: () -> Unit
) {
    // Intercept Back button: return to home screen
    BackHandler { onCancelOrHome() }

    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val lockManager = remember { AppLockManager.getInstance(context) }
    val settings = lockManager.settings

    var enteredPin by remember(packageName) { mutableStateOf("") }
    var errorMessage by remember(packageName) { mutableStateOf<String?>(null) }
    var showForgotDialog by remember(packageName) { mutableStateOf(false) }

    // Forgot PIN dialog states
    var recoveryAnswerInput by remember(packageName) { mutableStateOf("") }
    var newPinInput by remember(packageName) { mutableStateOf("") }
    var recoveryError by remember(packageName) { mutableStateOf<String?>(null) }

    val shakeOffset = remember { Animatable(0f) }

    fun triggerShake() {
        scope.launch {
            shakeOffset.snapTo(0f)
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    -24f at 50
                    24f at 100
                    -16f at 150
                    16f at 200
                    -8f at 250
                    8f at 300
                    0f at 400
                }
            )
        }
    }

    // Auto-trigger biometric prompt if enabled
    LaunchedEffect(packageName) {
        if (settings.isBiometricEnabled && lockManager.isBiometricSupported() && activity != null) {
            lockManager.authenticateWithBiometrics(
                activity = activity,
                targetAppName = appName,
                onSuccess = { onUnlockSuccess() },
                onError = { /* fallback to PIN */ }
            )
        }
    }

    fun onDigitPress(digit: String) {
        if (enteredPin.length < 4) {
            val nextPin = enteredPin + digit
            enteredPin = nextPin
            errorMessage = null

            if (nextPin.length == 4) {
                if (settings.verifyPin(nextPin)) {
                    onUnlockSuccess()
                } else {
                    errorMessage = "Incorrect PIN"
                    triggerShake()
                    enteredPin = ""
                }
            }
        }
    }

    fun onBackspace() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            errorMessage = null
        }
    }

    Surface(
        color = Color.Black,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top cancel action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCancelOrHome) {
                    Text("Exit", color = TextSecondary, fontSize = 13.sp)
                }
            }

            // Header: App Icon & Title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkSurface)
                        .border(1.5.dp, DarkBorder, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (appIcon != null) {
                        val bmp = remember(appIcon) {
                            try {
                                appIcon.toBitmap(120, 120)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = appName,
                                modifier = Modifier.size(56.dp)
                            )
                        } else {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(36.dp))
                        }
                    } else {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(36.dp))
                    }
                }

                Text(
                    text = appName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(EmeraldSuccess)
                    )
                    Text(
                        text = "Protected by My Gallery App Lock",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Enter 4-Digit PIN",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                // PIN Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                ) {
                    repeat(4) { index ->
                        val isFilled = index < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (isFilled) IndigoPrimary else DarkSurfaceVariant)
                                .border(
                                    width = 1.dp,
                                    color = if (isFilled) IndigoPrimary else DarkBorder,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                // Error message
                AnimatedVisibility(visible = errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = RoseSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Keypad
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )

                for (row in rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (digit in row) {
                            OverlayKeypadButton(
                                text = digit,
                                onClick = { onDigitPress(digit) }
                            )
                        }
                    }
                }

                // Bottom row: Biometric, 0, Backspace
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Biometric button
                    if (settings.isBiometricEnabled && lockManager.isBiometricSupported() && activity != null) {
                        IconButton(
                            onClick = {
                                lockManager.authenticateWithBiometrics(
                                    activity = activity,
                                    targetAppName = appName,
                                    onSuccess = { onUnlockSuccess() },
                                    onError = { errorMessage = it }
                                )
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biometrics",
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(64.dp))
                    }

                    // Digit 0
                    OverlayKeypadButton(
                        text = "0",
                        onClick = { onDigitPress("0") }
                    )

                    // Backspace
                    IconButton(
                        onClick = { onBackspace() },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Backspace",
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Forgot PIN option
                TextButton(
                    onClick = { showForgotDialog = true },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text("Forgot PIN?", color = TextMuted, fontSize = 12.sp)
                }
            }
        }
    }

    // Forgot PIN Dialog
    if (showForgotDialog) {
        val hasRecovery = settings.hasSecurityRecovery()
        val question = settings.securityQuestion

        AlertDialog(
            onDismissRequest = {
                showForgotDialog = false
                recoveryAnswerInput = ""
                newPinInput = ""
                recoveryError = null
            },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = if (hasRecovery) "Reset PIN" else "Forgot PIN",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (hasRecovery && question != null) {
                        Text("Answer your security question to reset your PIN:", color = TextSecondary, fontSize = 13.sp)
                        Text(
                            text = question,
                            color = IndigoPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        OutlinedTextField(
                            value = recoveryAnswerInput,
                            onValueChange = { recoveryAnswerInput = it },
                            label = { Text("Your Answer") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newPinInput,
                            onValueChange = { if (it.length <= 4) newPinInput = it },
                            label = { Text("New 4-digit PIN") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        recoveryError?.let {
                            Text(it, color = RoseSecondary, fontSize = 12.sp)
                        }
                    } else {
                        Text(
                            text = "No security recovery question was set. Please open MyGallery directly and verify your identity in App Settings.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                if (hasRecovery) {
                    Button(
                        onClick = {
                            if (newPinInput.length != 4) {
                                recoveryError = "New PIN must be 4 digits"
                            } else if (settings.resetPinWithRecovery(recoveryAnswerInput, newPinInput)) {
                                Toast.makeText(context, "PIN successfully reset!", Toast.LENGTH_SHORT).show()
                                showForgotDialog = false
                                recoveryAnswerInput = ""
                                newPinInput = ""
                                recoveryError = null
                            } else {
                                recoveryError = "Incorrect answer"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                    ) {
                        Text("Reset PIN", color = Color.White)
                    }
                } else {
                    Button(
                        onClick = { showForgotDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                    ) {
                        Text("OK", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun OverlayKeypadButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(DarkSurfaceVariant)
            .border(1.dp, DarkBorder, CircleShape)
            .clickable(onClick = onClick)
            .testTag("overlay_keypad_$text"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}
