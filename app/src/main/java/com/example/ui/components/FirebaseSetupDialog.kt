package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceContainerHigh
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private const val STORAGE_RULES_TEXT = """rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /users/{userId}/photos/{allPaths=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}"""

private const val PACKAGE_NAME = "com.aistudio.mygallery.pxmtqr"

@Composable
fun FirebaseSetupDialog(
    isFirebaseConfigured: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, DarkBorder, RoundedCornerShape(24.dp))
                .testTag("firebase_setup_dialog"),
            color = DarkSurface
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(IndigoPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = IndigoPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Firebase Integration",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isFirebaseConfigured) "Configured & Connected" else "Setup Instructions",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isFirebaseConfigured) EmeraldSuccess else TextMuted
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_setup_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Current Status Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isFirebaseConfigured) EmeraldSuccess.copy(alpha = 0.15f) else DarkSurfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (isFirebaseConfigured) EmeraldSuccess.copy(alpha = 0.4f) else DarkBorder
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isFirebaseConfigured) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isFirebaseConfigured) EmeraldSuccess else IndigoPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isFirebaseConfigured)
                                "Firebase is active! All authentications and user photos are securely processed in your Firebase project."
                            else
                                "Ready to test in AI Studio! Real Firebase Auth & Storage code is implemented. Follow the 4 steps below to connect your own Firebase project.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Step 1: Package Name
                SetupStepItem(
                    stepNumber = "1",
                    title = "Register App in Firebase",
                    description = "In the Firebase Console, add an Android app with this Application ID:"
                ) {
                    CodeSnippetBox(
                        code = PACKAGE_NAME,
                        onCopy = {
                            clipboardManager?.setPrimaryClip(ClipData.newPlainText("Package Name", PACKAGE_NAME))
                            Toast.makeText(context, "Copied Package Name!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Step 2: Download google-services.json
                SetupStepItem(
                    stepNumber = "2",
                    title = "Add google-services.json",
                    description = "Download google-services.json from Firebase project settings and drop it into the /app directory of the project."
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Step 3: Enable Auth Providers
                SetupStepItem(
                    stepNumber = "3",
                    title = "Enable Authentication Providers",
                    description = "Go to Build > Authentication > Sign-in method in Firebase Console:\n• Enable Email/Password\n• Enable Google (Add SHA-1 from debug key)\n• Enable Facebook (Enter App ID & Secret from developers.facebook.com)"
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Step 4: Storage Rules
                SetupStepItem(
                    stepNumber = "4",
                    title = "Set Storage Security Rules",
                    description = "Go to Firebase Storage > Rules. Paste the rules below to ensure each user can only read/write their own photos:"
                ) {
                    CodeSnippetBox(
                        code = STORAGE_RULES_TEXT,
                        onCopy = {
                            clipboardManager?.setPrimaryClip(ClipData.newPlainText("Storage Rules", STORAGE_RULES_TEXT))
                            Toast.makeText(context, "Copied Storage Rules!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dismiss_guide_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                ) {
                    Text("Got It", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SetupStepItem(
    stepNumber: String,
    title: String,
    description: String,
    content: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceVariant)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(IndigoPrimary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            lineHeight = 18.sp
        )
        if (content != null) {
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun CodeSnippetBox(
    code: String,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkBackground)
            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            ),
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onCopy,
            modifier = Modifier
                .size(32.dp)
                .testTag("copy_snippet_button")
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy",
                tint = IndigoPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
