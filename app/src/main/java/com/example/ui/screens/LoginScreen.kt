package com.example.ui.screens

import android.app.Activity
import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.example.ui.MainViewModel
import com.example.ui.components.FirebaseSetupDialog
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceContainerHigh
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.GoogleRed
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.IndigoPrimaryVariant
import com.example.ui.theme.RoseSecondary
import com.example.ui.theme.RoseSecondaryVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    isLoading: Boolean,
    errorMessage: String?
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showFirebaseSetup by remember { mutableStateOf(false) }

    if (showFirebaseSetup) {
        FirebaseSetupDialog(
            isFirebaseConfigured = viewModel.isFirebaseConfigured,
            onDismiss = { showFirebaseSetup = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Brand Logo & Title
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(IndigoPrimary, RoseSecondary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Collections,
                    contentDescription = "MyGallery Logo",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "MyGallery",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )

            Text(
                text = "Personal, secure cloud & local photo gallery",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Firebase Integration Status Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
                    .clickable { showFirebaseSetup = true }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .testTag("firebase_setup_badge"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (viewModel.isFirebaseConfigured) Color(0xFF10B981) else Color(0xFFF59E0B))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (viewModel.isFirebaseConfigured) "Firebase Connected" else "Firebase Instructions",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = "Setup Guide",
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Auth Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isSignUp) "Create Account" else "Welcome Back",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isSignUp) "Sign up to store and protect your photos" else "Sign in to access your private gallery",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Error Message
                    AnimatedVisibility(visible = errorMessage != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("auth_error_card"),
                            colors = CardDefaults.cardColors(
                                containerColor = RoseSecondary.copy(alpha = 0.15f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = RoseSecondaryVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = errorMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    // Display Name (Only in Sign Up mode)
                    if (isSignUp) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Your Name") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary)
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("display_name_input"),
                            shape = RoundedCornerShape(14.dp),
                            colors = getTextFieldColors()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null, tint = TextSecondary)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_input"),
                        shape = RoundedCornerShape(14.dp),
                        colors = getTextFieldColors()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = TextSecondary)
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = TextSecondary
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (isSignUp) {
                                    viewModel.signUpWithEmail(email, password, displayName)
                                } else {
                                    viewModel.signInWithEmail(email, password)
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input"),
                        shape = RoundedCornerShape(14.dp),
                        colors = getTextFieldColors()
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Submit Button
                    Button(
                        onClick = {
                            if (isSignUp) {
                                viewModel.signUpWithEmail(email, password, displayName)
                            } else {
                                viewModel.signInWithEmail(email, password)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("email_auth_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (isSignUp) "Create My Account" else "Sign In with Email",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Switch Mode Toggle
                    TextButton(
                        onClick = {
                            isSignUp = !isSignUp
                            viewModel.clearAuthError()
                        },
                        modifier = Modifier.testTag("switch_auth_mode_button")
                    ) {
                        Text(
                            text = if (isSignUp) "Already have an account? Sign In" else "Don't have an account? Sign Up",
                            color = IndigoPrimaryVariant,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Divider "or continue with"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = DarkBorder
                        )
                        Text(
                            text = "  OR  ",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontWeight = FontWeight.SemiBold
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = DarkBorder
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Continue with Google Button
                    OutlinedButton(
                        onClick = {
                            val credentialManager = CredentialManager.create(context)
                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId("dummy-client-id-for-preview") // Provided via Firebase console
                                .setAutoSelectEnabled(false)
                                .build()

                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()

                            scope.launch {
                                try {
                                    val result = credentialManager.getCredential(
                                        request = request,
                                        context = activity ?: context
                                    )
                                    val cred = result.credential
                                    if (cred is GoogleIdTokenCredential) {
                                        viewModel.signInWithGoogleToken(cred.idToken)
                                    }
                                } catch (e: GetCredentialException) {
                                    Toast.makeText(
                                        context,
                                        "Google Sign-In: Configure Web Client ID in Firebase (${e.message})",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Google Sign-In ready! Hook up Firebase Google auth.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("google_login_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = DarkSurfaceVariant
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(GoogleRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("G", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Continue with Google",
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Continue with Facebook Button
                    OutlinedButton(
                        onClick = {
                            if (activity != null) {
                                viewModel.signInWithFacebook(activity)
                            } else {
                                Toast.makeText(context, "Facebook Auth requires active Activity", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("facebook_login_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = DarkSurfaceVariant
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(FacebookBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("f", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Continue with Facebook",
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI Studio Instant Test Drive / Demo Login Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.signInDemoUser() }
                    .testTag("demo_login_button"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(IndigoPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = IndigoPrimaryVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Test in Google AI Studio",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "One-tap preview mode with mock user & full gallery testing",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun getTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = IndigoPrimary,
    unfocusedBorderColor = DarkBorder,
    focusedContainerColor = DarkSurfaceVariant,
    unfocusedContainerColor = DarkSurfaceVariant,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = IndigoPrimaryVariant,
    unfocusedLabelColor = TextSecondary,
    cursorColor = IndigoPrimary
)
