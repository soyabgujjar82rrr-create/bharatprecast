package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.GalleryPhoto
import com.example.ui.GalleryFilter
import com.example.ui.theme.CyanTertiary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceContainerHigh
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.RoseSecondary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

@Composable
fun GalleryScreen(
    photos: List<GalleryPhoto>,
    filter: GalleryFilter,
    gridColumns: Int,
    uploadingPhotoIds: Set<String>,
    isAutoSyncing: Boolean,
    onFilterChange: (GalleryFilter) -> Unit,
    onToggleColumns: () -> Unit,
    onPhotosSelected: (List<Uri>) -> Unit,
    onPhotoClick: (GalleryPhoto) -> Unit,
    onToggleFavorite: (GalleryPhoto) -> Unit,
    onSyncCameraPhotos: () -> Unit,
    onCameraCaptureRequested: (Context, (Uri) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var currentCameraUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onPhotosSelected(uris)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            currentCameraUri?.let { uri ->
                onPhotosSelected(listOf(uri))
            }
        }
    }

    // Permission launcher for Camera & Storage Auto-Sync
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            grants[Manifest.permission.READ_MEDIA_IMAGES] == true
        } else {
            grants[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        if (hasStorage) {
            onSyncCameraPhotos()
        }
    }

    val displayedPhotos = when (filter) {
        GalleryFilter.ALL -> photos
        GalleryFilter.FAVORITES -> photos.filter { it.isFavorite }
        GalleryFilter.CLOUD -> photos.filter { it.isUploaded }
    }

    Scaffold(
        containerColor = DarkBackground,
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Camera snap button
                SmallFloatingActionButton(
                    onClick = {
                        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES)
                        } else {
                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                        permissionLauncher.launch(permissions)

                        onCameraCaptureRequested(context) { uri ->
                            currentCameraUri = uri
                            cameraLauncher.launch(uri)
                        }
                    },
                    containerColor = RoseSecondary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("gallery_fab_camera")
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Take Photo"
                    )
                }

                // Add from picker button
                FloatingActionButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    containerColor = IndigoPrimary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.testTag("gallery_fab_add")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = "Add Photos")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Photos", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("gallery_screen")
        ) {
            // Top Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "My Gallery",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${displayedPhotos.size} photos • Permanent local storage",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Sync Camera Photos Button
                    IconButton(
                        onClick = {
                            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                            } else {
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            permissionLauncher.launch(permissions)
                            onSyncCameraPhotos()
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceVariant)
                            .testTag("sync_camera_button")
                    ) {
                        if (isAutoSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = CyanTertiary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync Camera Photos",
                                tint = CyanTertiary
                            )
                        }
                    }

                    // Column Switcher
                    IconButton(
                        onClick = onToggleColumns,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceVariant)
                            .testTag("toggle_columns_button")
                    ) {
                        Icon(
                            imageVector = if (gridColumns == 2) Icons.Default.GridView else Icons.Default.ViewModule,
                            contentDescription = "Toggle Grid Columns",
                            tint = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Policy / Safety Notice Banner (Requirement 1, 2, 3)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Safe Policy",
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Auto-syncs camera photos. Deleting only removes from MyGallery — original phone gallery is safe.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter == GalleryFilter.ALL,
                    onClick = { onFilterChange(GalleryFilter.ALL) },
                    label = { Text("All (${photos.size})") },
                    colors = chipColors(),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("filter_all_chip")
                )

                FilterChip(
                    selected = filter == GalleryFilter.FAVORITES,
                    onClick = { onFilterChange(GalleryFilter.FAVORITES) },
                    label = { Text("Favorites (${photos.count { it.isFavorite }})") },
                    colors = chipColors(),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("filter_favorites_chip")
                )

                FilterChip(
                    selected = filter == GalleryFilter.CLOUD,
                    onClick = { onFilterChange(GalleryFilter.CLOUD) },
                    label = { Text("Cloud (${photos.count { it.isUploaded }})") },
                    colors = chipColors(),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("filter_cloud_chip")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Grid or Empty State
            if (displayedPhotos.isEmpty()) {
                EmptyGalleryState(
                    filter = filter,
                    onAddPhotosClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onSyncCameraClick = {
                        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                        } else {
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                        permissionLauncher.launch(permissions)
                        onSyncCameraPhotos()
                    }
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("gallery_photo_grid"),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayedPhotos, key = { it.id }) { photo ->
                        GalleryPhotoCard(
                            photo = photo,
                            isUploading = uploadingPhotoIds.contains(photo.id),
                            onClick = { onPhotoClick(photo) },
                            onToggleFavorite = { onToggleFavorite(photo) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GalleryPhotoCard(
    photo: GalleryPhoto,
    isUploading: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("photo_card_${photo.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceContainerHigh),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = photo.localUri,
                contentDescription = photo.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Top Status Bar: Cloud status & Source tag
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cloud Sync Pill or Camera indicator
                if (photo.isUploaded) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Synced to Cloud",
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else if (photo.source == "CAMERA") {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Camera",
                            color = CyanTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(1.dp))
                }

                // Favorite Button
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .testTag("favorite_button_${photo.id}")
                ) {
                    Icon(
                        imageVector = if (photo.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (photo.isFavorite) "Unfavorite" else "Favorite",
                        tint = if (photo.isFavorite) RoseSecondary else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Uploading Indicator Overlay
            if (isUploading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = CyanTertiary,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyGalleryState(
    filter: GalleryFilter,
    onAddPhotosClick: () -> Unit,
    onSyncCameraClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(DarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (filter) {
                    GalleryFilter.ALL -> Icons.Default.Collections
                    GalleryFilter.FAVORITES -> Icons.Default.Favorite
                    GalleryFilter.CLOUD -> Icons.Default.CloudUpload
                },
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when (filter) {
                GalleryFilter.ALL -> "No Photos in MyGallery Yet"
                GalleryFilter.FAVORITES -> "No Favorite Photos"
                GalleryFilter.CLOUD -> "No Cloud Backed-up Photos"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when (filter) {
                GalleryFilter.ALL -> "Photos taken with your phone's camera will automatically appear here. You can also pick photos directly."
                GalleryFilter.FAVORITES -> "Tap the heart icon on any photo to save it to your favorites."
                GalleryFilter.CLOUD -> "Connect Firebase in Settings to back up photos to your private cloud storage."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (filter == GalleryFilter.ALL) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.Button(
                    onClick = onSyncCameraClick,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CyanTertiary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Auto-Sync Camera", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                androidx.compose.material3.OutlinedButton(
                    onClick = onAddPhotosClick,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Pick Photos", color = TextPrimary)
                }
            }
        }
    }
}

@Composable
fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = DarkSurface,
    labelColor = TextSecondary,
    selectedContainerColor = IndigoPrimary,
    selectedLabelColor = Color.White
)
