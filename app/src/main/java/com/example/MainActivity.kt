package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.security.AppLockBackgroundService
import com.example.data.security.AppLockManager
import com.example.ui.GalleryFilter
import com.example.ui.MainViewModel
import com.example.ui.ScreenTab
import com.example.ui.components.FirebaseSetupDialog
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.PhotoEditorScreen
import com.example.ui.screens.PhotoViewerScreen
import com.example.ui.screens.PrivateVaultScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.IndigoPrimaryVariant
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appLockManager = AppLockManager.getInstance(applicationContext)
        if (appLockManager.settings.isAppLockEnabled) {
            AppLockBackgroundService.start(applicationContext)
        }
        setContent {
            MyApplicationTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val authLoading by viewModel.authLoading.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val selectedPhoto by viewModel.selectedPhoto.collectAsStateWithLifecycle()
    val editingPhoto by viewModel.editingPhoto.collectAsStateWithLifecycle()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsStateWithLifecycle()
    val isAppLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()
    val photos by viewModel.currentPhotos.collectAsStateWithLifecycle()
    val filter by viewModel.galleryFilter.collectAsStateWithLifecycle()
    val gridColumns by viewModel.gridColumns.collectAsStateWithLifecycle()
    val uploadingPhotoIds by viewModel.uploadingPhotoIds.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val isAutoSyncing by viewModel.isAutoSyncing.collectAsStateWithLifecycle()

    var showFirebaseSetupDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Lifecycle observer to handle Background Lock & Auto-Sync (System 3)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAppReturnedFromBackground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    if (showFirebaseSetupDialog) {
        FirebaseSetupDialog(
            isFirebaseConfigured = viewModel.isFirebaseConfigured,
            onDismiss = { showFirebaseSetupDialog = false }
        )
    }

    val user = currentUser
    if (user == null) {
        LoginScreen(
            viewModel = viewModel,
            isLoading = authLoading,
            errorMessage = authError
        )
    } else {
        // Back navigation handler
        BackHandler(
            enabled = currentTab != ScreenTab.HOME &&
                    selectedPhoto == null &&
                    editingPhoto == null &&
                    !isSettingsOpen &&
                    !isAppLocked
        ) {
            viewModel.selectTab(ScreenTab.HOME)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = DarkBackground,
                contentWindowInsets = WindowInsets.navigationBars,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (selectedPhoto == null && editingPhoto == null && !isSettingsOpen && !isAppLocked) {
                        GalleryBottomNavigationBar(
                            currentTab = currentTab,
                            onTabSelected = { viewModel.selectTab(it) }
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Crossfade(
                        targetState = currentTab,
                        label = "ScreenTransition"
                    ) { tab ->
                        when (tab) {
                            ScreenTab.HOME -> {
                                HomeScreen(
                                    user = user,
                                    photos = photos,
                                    isFirebaseConfigured = viewModel.isFirebaseConfigured,
                                    onNavigateTab = { viewModel.selectTab(it) },
                                    onPhotosSelected = { uris -> viewModel.onPhotosPicked(uris, context) },
                                    onPhotoClick = { photo -> viewModel.viewPhoto(photo) },
                                    onOpenFirebaseSetup = { showFirebaseSetupDialog = true }
                                )
                            }
                            ScreenTab.GALLERY -> {
                                GalleryScreen(
                                    photos = photos,
                                    filter = filter,
                                    gridColumns = gridColumns,
                                    uploadingPhotoIds = uploadingPhotoIds,
                                    isAutoSyncing = isAutoSyncing,
                                    onFilterChange = { viewModel.setFilter(it) },
                                    onToggleColumns = { viewModel.toggleGridColumns() },
                                    onPhotosSelected = { uris -> viewModel.onPhotosPicked(uris, context) },
                                    onPhotoClick = { photo -> viewModel.viewPhoto(photo) },
                                    onToggleFavorite = { photo -> viewModel.toggleFavorite(photo) },
                                    onSyncCameraPhotos = { viewModel.syncCameraPhotos() },
                                    onCameraCaptureRequested = { ctx, callback ->
                                        val (uri, _) = viewModel.createCameraCaptureUri(ctx)
                                        callback(uri)
                                    }
                                )
                            }
                            ScreenTab.VAULT -> {
                                PrivateVaultScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.selectTab(ScreenTab.HOME) }
                                )
                            }
                            ScreenTab.PROFILE -> {
                                ProfileScreen(
                                    user = user,
                                    photos = photos,
                                    isFirebaseConfigured = viewModel.isFirebaseConfigured,
                                    onSignOut = { viewModel.signOut() },
                                    onOpenFirebaseSetup = { showFirebaseSetupDialog = true },
                                    onOpenSettings = { viewModel.openSettings() }
                                )
                            }
                        }
                    }
                }
            }

            // Full-Screen Photo Viewer Overlay
            selectedPhoto?.let { photo ->
                PhotoViewerScreen(
                    photo = photo,
                    isUploading = uploadingPhotoIds.contains(photo.id),
                    onDismiss = { viewModel.dismissPhotoViewer() },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onDeletePhoto = { viewModel.deletePhoto(it) },
                    onUploadPhoto = { viewModel.uploadPhoto(it) },
                    onEditPhoto = { editTarget -> viewModel.openEditor(editTarget) }
                )
            }

            // Photo Editor Full-Screen Overlay (System 2)
            editingPhoto?.let { editTarget ->
                PhotoEditorScreen(
                    photo = editTarget,
                    onDismiss = { viewModel.closeEditor() },
                    onSaveSuccess = { savedFile, replaceOriginal ->
                        viewModel.saveEditedPhoto(editTarget, savedFile, replaceOriginal)
                    }
                )
            }

            // Settings Screen Overlay (System 4)
            if (isSettingsOpen) {
                SettingsScreen(
                    user = user,
                    settings = viewModel.settings,
                    appLockManager = viewModel.appLockManager,
                    onBack = { viewModel.closeSettings() },
                    onSignOut = { viewModel.signOut() }
                )
            }

            // App Lock Overlay (System 3)
            if (isAppLocked) {
                AppLockScreen(
                    appLockManager = viewModel.appLockManager,
                    onUnlocked = { viewModel.unlockApp() }
                )
            }
        }
    }
}

@Composable
fun GalleryBottomNavigationBar(
    currentTab: ScreenTab,
    onTabSelected: (ScreenTab) -> Unit
) {
    NavigationBar(
        containerColor = DarkSurface,
        contentColor = TextPrimary,
        tonalElevation = 8.dp,
        modifier = Modifier
            .border(
                width = 1.dp,
                color = DarkBorder,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .testTag("bottom_nav_bar")
    ) {
        NavigationBarItem(
            selected = currentTab == ScreenTab.HOME,
            onClick = { onTabSelected(ScreenTab.HOME) },
            icon = {
                Icon(
                    imageVector = if (currentTab == ScreenTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = "Home"
                )
            },
            label = {
                Text(
                    text = "Home",
                    fontWeight = if (currentTab == ScreenTab.HOME) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = navItemColors(),
            modifier = Modifier.testTag("nav_item_home")
        )

        NavigationBarItem(
            selected = currentTab == ScreenTab.GALLERY,
            onClick = { onTabSelected(ScreenTab.GALLERY) },
            icon = {
                Icon(
                    imageVector = if (currentTab == ScreenTab.GALLERY) Icons.Filled.Collections else Icons.Outlined.Collections,
                    contentDescription = "Gallery"
                )
            },
            label = {
                Text(
                    text = "Gallery",
                    fontWeight = if (currentTab == ScreenTab.GALLERY) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = navItemColors(),
            modifier = Modifier.testTag("nav_item_gallery")
        )

        NavigationBarItem(
            selected = currentTab == ScreenTab.VAULT,
            onClick = { onTabSelected(ScreenTab.VAULT) },
            icon = {
                Icon(
                    imageVector = if (currentTab == ScreenTab.VAULT) Icons.Filled.Lock else Icons.Outlined.Lock,
                    contentDescription = "Vault"
                )
            },
            label = {
                Text(
                    text = "Vault",
                    fontWeight = if (currentTab == ScreenTab.VAULT) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = navItemColors(),
            modifier = Modifier.testTag("nav_item_vault")
        )

        NavigationBarItem(
            selected = currentTab == ScreenTab.PROFILE,
            onClick = { onTabSelected(ScreenTab.PROFILE) },
            icon = {
                Icon(
                    imageVector = if (currentTab == ScreenTab.PROFILE) Icons.Filled.Person else Icons.Outlined.Person,
                    contentDescription = "Profile"
                )
            },
            label = {
                Text(
                    text = "Profile",
                    fontWeight = if (currentTab == ScreenTab.PROFILE) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = navItemColors(),
            modifier = Modifier.testTag("nav_item_profile")
        )
    }
}

@Composable
fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = IndigoPrimary,
    selectedTextColor = IndigoPrimary,
    unselectedIconColor = TextSecondary,
    unselectedTextColor = TextMuted,
    indicatorColor = IndigoPrimaryVariant.copy(alpha = 0.25f)
)
