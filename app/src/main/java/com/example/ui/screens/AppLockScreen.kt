package com.example.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.security.AppLockManager
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AppLockScreen(
    appLockManager: AppLockManager,
    onUnlocked: () -> Unit
) {
    // Prevent back navigation while locked
    BackHandler { /* Do nothing while locked */ }

    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showForgotDialog by remember { mutableStateOf(false) }

    var recoveryAnswerInput by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var recoveryError by remember { mutableStateOf<String?>(null) }

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

    // Auto-prompt biometrics if enabled
    LaunchedEffect(Unit) {
        if (appLockManager.settings.isBiometricEnabled && appLockManager.isBiometricSupported() && activity != null) {
            appLockManager.authenticateWithBiometrics(
                activity = activity,
                onSuccess = { onUnlocked() },
                onError = { /* Let user use PIN */ }
            )
        }
    }

    fun handleDigit(digit: String) {
        if (enteredPin.length < 4) {
            val newPin = enteredPin + digit
            enteredPin = newPin
            errorMessage = null

            if (newPin.length == 4) {
                if (appLockManager.settings.verifyPin(newPin)) {
                    onUnlocked()
                } else {
                    errorMessage = "Incorrect PIN. Please try again."
                    triggerShake()
                    enteredPin = ""
                }
            }
        }
    }

    fun handleBackspace() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            errorMessage = null
        }
    }

    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            containerColor = DarkSurface,
            title = {
                Text("Forgot PIN?", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "For your privacy and security, your PIN is securely hashed. If you have forgotten your PIN, you can sign out or clear app storage to reset your local PIN.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showForgotDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("OK", color = Color.White)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp)
            .testTag("app_lock_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Lock Icon Container
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant)
                    .border(2.dp, IndigoPrimary.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = IndigoPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "MyGallery Locked",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Enter your 4-digit PIN to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(30.dp))

            // PIN Dots Indicator with Shake Effect
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                    .testTag("pin_dots_row")
            ) {
                for (i in 0 until 4) {
                    val isFilled = i < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFilled) {
                                    if (errorMessage != null) RoseSecondary else IndigoPrimary
                                } else {
                                    DarkSurfaceVariant
                                }
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (isFilled) IndigoPrimary else DarkBorder,
                                shape = CircleShape
                            )
                    )
                }
            }

            // Error Message
            Box(modifier = Modifier.height(36.dp), contentAlignment = Alignment.Center) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = RoseSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Numeric Keypad (1 to 9, 0, Backspace, Biometrics)
            val keypad = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("BIO", "0", "BACK")
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (row in keypad) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (key in row) {
                            when (key) {
                                "BIO" -> {
                                    if (appLockManager.settings.isBiometricEnabled && appLockManager.isBiometricSupported() && activity != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clip(CircleShape)
                                                .background(DarkSurface)
                                                .clickable {
                                                    appLockManager.authenticateWithBiometrics(
                                                        activity = activity,
                                                        onSuccess = { onUnlocked() },
                                                        onError = { msg -> errorMessage = msg }
                                                    )
                                                }
                                                .testTag("keypad_bio"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Fingerprint,
                                                contentDescription = "Biometric Unlock",
                                                tint = EmeraldSuccess,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(68.dp))
                                    }
                                }
                                "BACK" -> {
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(CircleShape)
                                            .background(DarkSurface)
                                            .clickable { handleBackspace() }
                                            .testTag("keypad_backspace"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = "Backspace",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                else -> {
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(CircleShape)
                                            .background(DarkSurface)
                                            .border(1.dp, DarkBorder, CircleShape)
                                            .clickable { handleDigit(key) }
                                            .testTag("keypad_button_$key"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = key,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = { showForgotDialog = true },
                modifier = Modifier.testTag("forgot_pin_button")
            ) {
                Text(
                    text = "Forgot PIN?",
                    color = TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    // Forgot PIN Dialog
    if (showForgotDialog) {
        val hasRecovery = appLockManager.settings.hasSecurityRecovery()
        val question = appLockManager.settings.securityQuestion

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
                            text = "No security recovery question was set. If you are signed into MyGallery with your account, you can reinstall or re-authenticate.",
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
                            } else if (appLockManager.settings.resetPinWithRecovery(recoveryAnswerInput, newPinInput)) {
                                Toast.makeText(context, "PIN successfully reset!", Toast.LENGTH_SHORT).show()
                                showForgotDialog = false
                                recoveryAnswerInput = ""
                                newPinInput = ""
                                recoveryError = null
                                onUnlocked()
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
