package com.example.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.GalleryPhoto
import com.example.data.model.UserProfile
import com.example.ui.theme.CyanTertiary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceContainerHigh
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.IndigoPrimaryVariant
import com.example.ui.theme.RoseSecondary
import com.example.ui.theme.RoseSecondaryVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ProfileScreen(
    user: UserProfile,
    photos: List<GalleryPhoto>,
    isFirebaseConfigured: Boolean,
    onSignOut: () -> Unit,
    onOpenFirebaseSetup: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val favoritesCount = photos.count { it.isFavorite }
    val cloudCount = photos.count { it.isUploaded }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            containerColor = DarkSurface,
            title = {
                Text("Confirm Sign Out", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to sign out of MyGallery?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseSecondary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Sign Out", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("profile_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Profile Picture with Avatar Border
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .border(3.dp, Brush.linearGradient(listOf(IndigoPrimary, RoseSecondary)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!user.photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(user.photoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "User Avatar",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(listOf(IndigoPrimary, RoseSecondary))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.displayName?.firstOrNull()?.uppercase() ?: "U",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // User Name
        Text(
            text = user.displayName ?: "MyGallery User",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Email
        Text(
            text = user.email ?: "Signed in via Firebase",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(6.dp))

        // User ID Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurfaceVariant)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "UID: ${user.uid.take(12)}...",
                fontSize = 11.sp,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Statistics Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_stats_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ProfileStatColumn(
                    value = photos.size.toString(),
                    label = "Photos",
                    color = IndigoPrimaryVariant
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(36.dp)
                        .background(DarkBorder)
                )
                ProfileStatColumn(
                    value = favoritesCount.toString(),
                    label = "Favorites",
                    color = RoseSecondaryVariant
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(36.dp)
                        .background(DarkBorder)
                )
                ProfileStatColumn(
                    value = cloudCount.toString(),
                    label = "Cloud Synced",
                    color = CyanTertiary
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // App Settings Card (New System)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenSettings)
                .testTag("profile_settings_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(IndigoPrimary.copy(alpha = 0.5f))
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(IndigoPrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = IndigoPrimary
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "App Settings & Privacy",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "App Lock, PIN, Camera Auto-Sync & Editor",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Firebase Cloud Integration Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenFirebaseSetup)
                .testTag("profile_firebase_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isFirebaseConfigured) EmeraldSuccess.copy(alpha = 0.2f) else IndigoPrimary.copy(alpha = 0.2f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = if (isFirebaseConfigured) EmeraldSuccess else IndigoPrimary
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Firebase Cloud & Storage",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isFirebaseConfigured) "Connected & Secure (User isolation active)" else "Tap to view configuration instructions",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isFirebaseConfigured) EmeraldSuccess else TextMuted
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Security Policy Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(EmeraldSuccess.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = EmeraldSuccess
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "Data Isolation",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Your photos are strictly accessible by your user credentials only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Logout Button
        Button(
            onClick = { showLogoutConfirm = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("logout_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = RoseSecondary.copy(alpha = 0.2f),
                contentColor = RoseSecondaryVariant
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, RoseSecondary.copy(alpha = 0.4f))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Sign Out",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sign Out",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ProfileStatColumn(
    value: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}
