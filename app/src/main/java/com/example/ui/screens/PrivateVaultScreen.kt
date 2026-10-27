package com.example.ui.screens

import android.app.Activity
import android.media.MediaPlayer
import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.VaultMediaItem
import com.example.data.model.VaultMediaType
import com.example.ui.MainViewModel
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class VaultSortOrder {
    NEWEST, OLDEST, NAME, SIZE
}

@Composable
fun PrivateVaultScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsStateWithLifecycle()

    if (!isVaultUnlocked) {
        VaultLockScreen(
            viewModel = viewModel,
            onUnlocked = { viewModel.unlockVault() },
            onCancel = onBack
        )
    } else {
        VaultContentScreen(
            viewModel = viewModel,
            onLockVault = { viewModel.lockVault() },
            onBack = onBack
        )
    }
}

/**
 * High-Security Lock Screen protecting Private Media Vault.
 */
@Composable
fun VaultLockScreen(
    viewModel: MainViewModel,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit
) {
    BackHandler { onCancel() }

    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val settings = viewModel.settings
    val lockManager = viewModel.appLockManager

    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
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

    LaunchedEffect(Unit) {
        if (settings.isBiometricEnabled && lockManager.isBiometricSupported() && activity != null) {
            lockManager.authenticateWithBiometrics(
                activity = activity,
                targetAppName = "Private Media Vault",
                onSuccess = { onUnlocked() },
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
                if (settings.pinHash == null || settings.verifyPin(nextPin)) {
                    onUnlocked()
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
        color = DarkBackground,
        modifier = Modifier.fillMaxSize().testTag("vault_lock_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                        .border(2.dp, IndigoPrimary.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Vault Locked",
                        tint = IndigoPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Text(
                    text = "Private Media Vault",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Text(
                    text = "Encrypted Sandbox • Invisible to Other Apps",
                    fontSize = 12.sp,
                    color = EmeraldSuccess,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (settings.pinHash == null) "Create or Enter PIN to Unlock" else "Enter PIN to Access Private Files",
                    fontSize = 14.sp,
                    color = TextSecondary
                )

                // PIN Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                ) {
                    repeat(4) { index ->
                        val isFilled = index < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (isFilled) IndigoPrimary else DarkSurfaceVariant)
                                .border(1.dp, if (isFilled) IndigoPrimary else DarkBorder, CircleShape)
                        )
                    }
                }

                AnimatedVisibility(visible = errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = RoseSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Keypad
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("bio", "0", "back")
                )

                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { key ->
                            when (key) {
                                "bio" -> {
                                    if (settings.isBiometricEnabled && lockManager.isBiometricSupported() && activity != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    lockManager.authenticateWithBiometrics(
                                                        activity = activity,
                                                        targetAppName = "Private Media Vault",
                                                        onSuccess = { onUnlocked() },
                                                        onError = { /* ignored */ }
                                                    )
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Fingerprint,
                                                contentDescription = "Biometric",
                                                tint = IndigoPrimary,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(68.dp))
                                    }
                                }
                                "back" -> {
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(CircleShape)
                                            .clickable { onBackspace() },
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
                                            .clickable { onDigitPress(key) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = key,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Unlocked Private Vault Content Screen with Photos, Videos, and Audio tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultContentScreen(
    viewModel: MainViewModel,
    onLockVault: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    val categories = listOf("Photos", "Videos", "Audio")

    val photos by viewModel.vaultPhotos.collectAsStateWithLifecycle()
    val videos by viewModel.vaultVideos.collectAsStateWithLifecycle()
    val audioList by viewModel.vaultAudio.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(VaultSortOrder.NEWEST) }
    var isSelectMode by remember { mutableStateOf(false) }
    val selectedItemIds = remember { mutableStateListOf<String>() }
    var pendingShareItems by remember { mutableStateOf<List<VaultMediaItem>?>(null) }
    var showShareAuthDialog by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    // Active media preview/player states
    var viewingPhoto by remember { mutableStateOf<VaultMediaItem?>(null) }
    var playingVideo by remember { mutableStateOf<VaultMediaItem?>(null) }
    var activeAudioItem by remember { mutableStateOf<VaultMediaItem?>(null) }

    // Import Pickers
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        uris.distinct().forEach { uri ->
            viewModel.importVaultMedia(uri, VaultMediaType.PHOTO)
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        uris.distinct().forEach { uri ->
            viewModel.importVaultMedia(uri, VaultMediaType.VIDEO)
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.distinct().forEach { uri ->
            viewModel.importVaultMedia(uri, VaultMediaType.AUDIO)
        }
    }

    fun triggerAddMedia() {
        when (selectedCategoryIndex) {
            0 -> photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            1 -> videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            2 -> audioPickerLauncher.launch("audio/*")
        }
    }

    val currentItems = when (selectedCategoryIndex) {
        0 -> photos
        1 -> videos
        else -> audioList
    }

    val filteredItems = remember(currentItems, searchQuery, sortOrder) {
        val filtered = if (searchQuery.isBlank()) {
            currentItems
        } else {
            currentItems.filter { it.originalName.contains(searchQuery, ignoreCase = true) }
        }

        when (sortOrder) {
            VaultSortOrder.NEWEST -> filtered.sortedByDescending { it.importedAt }
            VaultSortOrder.OLDEST -> filtered.sortedBy { it.importedAt }
            VaultSortOrder.NAME -> filtered.sortedBy { it.originalName.lowercase() }
            VaultSortOrder.SIZE -> filtered.sortedByDescending { it.sizeBytes }
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .border(width = 0.5.dp, color = DarkBorder)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Private Media Vault",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            Text(
                                text = "App-Private Sandbox • Not Visible in Normal Apps",
                                fontSize = 11.sp,
                                color = EmeraldSuccess
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = onLockVault,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lock", fontSize = 12.sp, color = IndigoPrimary)
                        }
                    }
                }

                // Three Category Tabs: Photos, Videos, Audio
                TabRow(
                    selectedTabIndex = selectedCategoryIndex,
                    containerColor = DarkSurface,
                    contentColor = TextPrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedCategoryIndex]),
                            color = IndigoPrimary
                        )
                    }
                ) {
                    categories.forEachIndexed { index, title ->
                        val count = when (index) {
                            0 -> photos.size
                            1 -> videos.size
                            else -> audioList.size
                        }
                        Tab(
                            selected = selectedCategoryIndex == index,
                            onClick = {
                                selectedCategoryIndex = index
                                isSelectMode = false
                                selectedItemIds.clear()
                            },
                            text = {
                                Text(
                                    text = "$title ($count)",
                                    fontWeight = if (selectedCategoryIndex == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isSelectMode) {
                FloatingActionButton(
                    onClick = { triggerAddMedia() },
                    containerColor = IndigoPrimary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("vault_add_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Import Media")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search & Filter Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search ${categories[selectedCategoryIndex].lowercase()}...", fontSize = 12.sp, color = TextMuted) },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = IndigoPrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true
                )

                if (isSelectMode) {
                    val allSelected = filteredItems.isNotEmpty() && selectedItemIds.size == filteredItems.size
                    TextButton(
                        onClick = {
                            if (allSelected) {
                                selectedItemIds.clear()
                            } else {
                                selectedItemIds.clear()
                                selectedItemIds.addAll(filteredItems.map { it.id })
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(if (allSelected) "Deselect All" else "Select All", fontSize = 11.sp, color = IndigoPrimary)
                    }
                }

                // Select Mode Toggle
                OutlinedButton(
                    onClick = {
                        isSelectMode = !isSelectMode
                        selectedItemIds.clear()
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(if (isSelectMode) "Done" else "Select", fontSize = 12.sp)
                }
            }

            // Selection Action Bar (when selecting)
            AnimatedVisibility(visible = isSelectMode && selectedItemIds.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedItemIds.size} of ${filteredItems.size} selected",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Restore Selected
                            Button(
                                onClick = {
                                    val toRestore = currentItems.filter { selectedItemIds.contains(it.id) }
                                    viewModel.batchRestoreVaultMedia(toRestore) {
                                        selectedItemIds.clear()
                                        isSelectMode = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Restore", fontSize = 11.sp)
                            }

                            // Share Selected (Requires PIN/Biometric Authentication First)
                            Button(
                                onClick = {
                                    val toShare = currentItems.filter { selectedItemIds.contains(it.id) }
                                    if (toShare.isNotEmpty()) {
                                        pendingShareItems = toShare
                                        showShareAuthDialog = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Share", fontSize = 11.sp)
                            }

                            // Delete Selected (Prompts confirmation dialog)
                            Button(
                                onClick = {
                                    showBatchDeleteConfirm = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RoseSecondary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Delete", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Media Content per Tab
            if (filteredItems.isEmpty()) {
                VaultEmptyState(
                    categoryName = categories[selectedCategoryIndex],
                    onImport = { triggerAddMedia() }
                )
            } else {
                when (selectedCategoryIndex) {
                    0 -> {
                        // PHOTOS GRID
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 110.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredItems, key = { it.id }) { photo ->
                                VaultPhotoGridItem(
                                    item = photo,
                                    isSelectMode = isSelectMode,
                                    isSelected = selectedItemIds.contains(photo.id),
                                    onItemClick = {
                                        if (isSelectMode) {
                                            if (selectedItemIds.contains(photo.id)) selectedItemIds.remove(photo.id)
                                            else selectedItemIds.add(photo.id)
                                        } else {
                                            viewingPhoto = photo
                                        }
                                    },
                                    onItemLongClick = {
                                        isSelectMode = true
                                        if (!selectedItemIds.contains(photo.id)) selectedItemIds.add(photo.id)
                                    }
                                )
                            }
                        }
                    }
                    1 -> {
                        // VIDEOS GRID
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 130.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredItems, key = { it.id }) { video ->
                                VaultVideoGridItem(
                                    item = video,
                                    isSelectMode = isSelectMode,
                                    isSelected = selectedItemIds.contains(video.id),
                                    onItemClick = {
                                        if (isSelectMode) {
                                            if (selectedItemIds.contains(video.id)) selectedItemIds.remove(video.id)
                                            else selectedItemIds.add(video.id)
                                        } else {
                                            playingVideo = video
                                        }
                                    },
                                    onItemLongClick = {
                                        isSelectMode = true
                                        if (!selectedItemIds.contains(video.id)) selectedItemIds.add(video.id)
                                    }
                                )
                            }
                        }
                    }
                    2 -> {
                        // AUDIO LIST
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredItems, key = { it.id }) { audio ->
                                VaultAudioListItem(
                                    item = audio,
                                    isSelectMode = isSelectMode,
                                    isSelected = selectedItemIds.contains(audio.id),
                                    isActive = activeAudioItem?.id == audio.id,
                                    onPlayClick = {
                                        activeAudioItem = if (activeAudioItem?.id == audio.id) null else audio
                                    },
                                    onItemClick = {
                                        if (isSelectMode) {
                                            if (selectedItemIds.contains(audio.id)) selectedItemIds.remove(audio.id)
                                            else selectedItemIds.add(audio.id)
                                        } else {
                                            activeAudioItem = audio
                                        }
                                    },
                                    onRestore = { viewModel.restoreVaultMedia(audio) },
                                    onDelete = { viewModel.deleteVaultMediaPermanently(audio) },
                                    onShare = {
                                        pendingShareItems = listOf(audio)
                                        showShareAuthDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Full-Screen Photo Viewer Dialog
    viewingPhoto?.let { photo ->
        VaultPhotoViewerDialog(
            item = photo,
            onDismiss = { viewingPhoto = null },
            onRestore = {
                viewModel.restoreVaultMedia(photo) { viewingPhoto = null }
            },
            onDelete = {
                viewModel.deleteVaultMediaPermanently(photo) { viewingPhoto = null }
            },
            onShare = {
                pendingShareItems = listOf(photo)
                showShareAuthDialog = true
            }
        )
    }

    // In-App Video Player Dialog
    playingVideo?.let { video ->
        VaultVideoPlayerDialog(
            item = video,
            onDismiss = { playingVideo = null },
            onRestore = {
                viewModel.restoreVaultMedia(video) { playingVideo = null }
            },
            onDelete = {
                viewModel.deleteVaultMediaPermanently(video) { playingVideo = null }
            },
            onShare = {
                pendingShareItems = listOf(video)
                showShareAuthDialog = true
            }
        )
    }

    // Share Authentication Gate
    if (showShareAuthDialog && pendingShareItems != null) {
        VaultShareAuthDialog(
            items = pendingShareItems!!,
            viewModel = viewModel,
            onSuccess = { targetPkg ->
                val itemsToShare = pendingShareItems!!
                showShareAuthDialog = false
                pendingShareItems = null
                viewModel.shareVaultMediaAuthenticated(itemsToShare, targetPkg)
            },
            onDismiss = {
                showShareAuthDialog = false
                pendingShareItems = null
            }
        )
    }

    // Batch Delete Confirmation Dialog
    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            containerColor = DarkSurface,
            title = { Text("Permanently Delete ${selectedItemIds.size} Items?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("These files will be erased from your device storage immediately. This action cannot be undone.", color = TextSecondary, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = currentItems.filter { selectedItemIds.contains(it.id) }
                        showBatchDeleteConfirm = false
                        viewModel.batchDeleteVaultMedia(toDelete) {
                            selectedItemIds.clear()
                            isSelectMode = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseSecondary)
                ) {
                    Text("Delete Permanently", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

/**
 * Grid Item for Vault Photos
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultPhotoGridItem(
    item: VaultMediaItem,
    isSelectMode: Boolean,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) IndigoPrimary else DarkBorder
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = File(item.privateFilePath),
                contentDescription = item.originalName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Selection Checkbox
            if (isSelectMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) IndigoPrimary else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Size badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatFileSize(item.sizeBytes),
                    fontSize = 9.sp,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Grid Item for Vault Videos
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultVideoGridItem(
    item: VaultMediaItem,
    isSelectMode: Boolean,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) IndigoPrimary else DarkBorder
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = File(item.privateFilePath),
                contentDescription = item.originalName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Play Icon overlay
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Duration badge
            if (item.durationMs > 0L) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatDuration(item.durationMs),
                        fontSize = 10.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Size badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatFileSize(item.sizeBytes),
                    fontSize = 10.sp,
                    color = Color.White
                )
            }

            if (isSelectMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) IndigoPrimary else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * List Item for Vault Audio Files with Inline Player Controls
 */
@Composable
fun VaultAudioListItem(
    item: VaultMediaItem,
    isSelectMode: Boolean,
    isSelected: Boolean,
    isActive: Boolean,
    onPlayClick: () -> Unit,
    onItemClick: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(isActive) {
        if (isActive) {
            try {
                val player = MediaPlayer().apply {
                    setDataSource(item.privateFilePath)
                    prepare()
                    setOnCompletionListener {
                        isPlaying = false
                        currentPositionMs = 0L
                    }
                }
                mediaPlayer = player
                player.start()
                isPlaying = true
            } catch (e: Exception) {
                Toast.makeText(context, "Could not play audio", Toast.LENGTH_SHORT).show()
            }
        }
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
        }
    }

    // Position updater coroutine
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    currentPositionMs = player.currentPosition.toLong()
                }
            }
            delay(250)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) IndigoPrimary else DarkBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectMode) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) IndigoPrimary else TextMuted,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = 8.dp)
                    )
                }

                // Audio Icon / Play button
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (isActive) IndigoPrimary else DarkSurfaceVariant)
                        .clickable { onPlayClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = if (isActive) Color.White else CyanTertiary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.originalName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (item.durationMs > 0L) formatDuration(item.durationMs) else "Audio",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                        Text(
                            text = "•",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                        Text(
                            text = formatFileSize(item.sizeBytes),
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                // Actions: Share, Restore, Delete
                Row {
                    IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onRestore, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Restore, contentDescription = "Restore", tint = EmeraldSuccess, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = RoseSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Audio Seeking Slider when active
            if (isActive) {
                Spacer(modifier = Modifier.height(8.dp))
                val totalDuration = if (item.durationMs > 0L) item.durationMs else (mediaPlayer?.duration?.toLong() ?: 1L)
                Slider(
                    value = currentPositionMs.toFloat().coerceIn(0f, totalDuration.toFloat()),
                    onValueChange = { newPos ->
                        currentPositionMs = newPos.toLong()
                        mediaPlayer?.seekTo(newPos.toInt())
                    },
                    valueRange = 0f..totalDuration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = IndigoPrimary,
                        activeTrackColor = IndigoPrimary,
                        inactiveTrackColor = DarkBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatDuration(currentPositionMs), fontSize = 10.sp, color = TextMuted)
                    Text(text = formatDuration(totalDuration), fontSize = 10.sp, color = TextMuted)
                }
            }
        }
    }
}

/**
 * Full Screen Private Photo Viewer
 */
@Composable
fun VaultPhotoViewerDialog(
    item: VaultMediaItem,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color.Black,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Photo Image
                AsyncImage(
                    model = File(item.privateFilePath),
                    contentDescription = item.originalName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = item.originalName,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatFileSize(item.sizeBytes)} • Private Vault",
                            color = EmeraldSuccess,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onShare) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }
                }

                // Bottom Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onRestore,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore to Gallery")
                    }

                    Button(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = RoseSecondary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = DarkSurface,
            title = { Text("Permanently Delete Photo?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("This file will be completely wiped from the device. This action cannot be undone.", color = TextSecondary, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseSecondary)
                ) {
                    Text("Delete Permanently", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

/**
 * In-App Video Player Dialog with MediaController
 */
@Composable
fun VaultVideoPlayerDialog(
    item: VaultMediaItem,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color.Black,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // VideoView embedded via AndroidView
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoPath(item.privateFilePath)
                            val mediaController = MediaController(ctx)
                            mediaController.setAnchorView(this)
                            setMediaController(mediaController)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                start()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Top Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = item.originalName,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatDuration(item.durationMs)} • Private Video Player",
                            color = EmeraldSuccess,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onShare) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }
                }

                // Bottom Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onRestore,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore to Gallery")
                    }

                    Button(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = RoseSecondary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = DarkSurface,
            title = { Text("Permanently Delete Video?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("This video will be permanently wiped from private storage. This action cannot be undone.", color = TextSecondary, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseSecondary)
                ) {
                    Text("Delete Permanently", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

/**
 * Empty State for Vault Sections
 */
@Composable
fun VaultEmptyState(
    categoryName: String,
    onImport: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant)
                    .border(1.5.dp, DarkBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (categoryName) {
                        "Photos" -> Icons.Default.Lock
                        "Videos" -> Icons.Default.Videocam
                        else -> Icons.Default.MusicNote
                    },
                    contentDescription = null,
                    tint = IndigoPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Text(
                text = "No Private $categoryName Yet",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = "Imported $categoryName are moved to app-private storage and deleted from public media apps. Only accessible here with PIN.",
                fontSize = 12.sp,
                color = TextMuted,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onImport,
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import $categoryName")
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

/**
 * Security Gate: Requires PIN or Biometric authentication BEFORE private media URIs are generated or shared to WhatsApp / Sharesheet.
 */
@Composable
fun VaultShareAuthDialog(
    items: List<VaultMediaItem>,
    viewModel: MainViewModel,
    onSuccess: (targetPackage: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val settings = viewModel.settings
    val lockManager = viewModel.appLockManager

    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTarget by remember { mutableStateOf<String?>("com.whatsapp") }
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

    fun onDigitPress(digit: String) {
        if (enteredPin.length < 4) {
            val nextPin = enteredPin + digit
            enteredPin = nextPin
            errorMessage = null

            if (nextPin.length == 4) {
                if (settings.pinHash == null || settings.verifyPin(nextPin)) {
                    onSuccess(selectedTarget)
                } else {
                    errorMessage = "Incorrect PIN. Share cancelled."
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Authentication Required",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Text(
                    text = "Verify your PIN or Biometrics to securely share ${if (items.size == 1) items.first().originalName else "${items.size} private items"}.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                // Share Destination Target Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTarget == "com.whatsapp",
                        onClick = { selectedTarget = "com.whatsapp" },
                        label = { Text("WhatsApp", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldSuccess.copy(alpha = 0.2f),
                            selectedLabelColor = EmeraldSuccess
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = selectedTarget == null,
                        onClick = { selectedTarget = null },
                        label = { Text("All Apps", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = IndigoPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = IndigoPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                // PIN Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                ) {
                    repeat(4) { index ->
                        val isFilled = index < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (isFilled) IndigoPrimary else DarkSurfaceVariant)
                                .border(1.dp, if (isFilled) IndigoPrimary else DarkBorder, CircleShape)
                        )
                    }
                }

                AnimatedVisibility(visible = errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = RoseSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Compact Keypad
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("bio", "0", "back")
                    )

                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { key ->
                                when (key) {
                                    "bio" -> {
                                        if (settings.isBiometricEnabled && lockManager.isBiometricSupported() && activity != null) {
                                            IconButton(
                                                onClick = {
                                                    lockManager.authenticateWithBiometrics(
                                                        activity = activity,
                                                        targetAppName = "Private Media Share",
                                                        onSuccess = { onSuccess(selectedTarget) },
                                                        onError = { /* fallback to PIN */ }
                                                    )
                                                },
                                                modifier = Modifier.size(54.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Fingerprint,
                                                    contentDescription = "Biometric",
                                                    tint = CyanTertiary,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.size(54.dp))
                                        }
                                    }
                                    "back" -> {
                                        IconButton(
                                            onClick = { onBackspace() },
                                            modifier = Modifier.size(54.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                                contentDescription = "Backspace",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    else -> {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(CircleShape)
                                                .background(DarkBackground)
                                                .border(1.dp, DarkBorder, CircleShape)
                                                .clickable { onDigitPress(key) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = key,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
