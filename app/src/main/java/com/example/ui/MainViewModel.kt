package com.example.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.GalleryPhoto
import com.example.data.model.UserProfile
import com.example.data.model.VaultMediaItem
import com.example.data.model.VaultMediaType
import com.example.data.repository.GalleryRepository
import com.example.data.repository.VaultRepository
import com.example.data.security.AppLockManager
import com.example.data.settings.AppSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class ScreenTab {
    HOME, GALLERY, VAULT, PROFILE
}

enum class GalleryFilter {
    ALL, FAVORITES, CLOUD
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GalleryRepository(application.applicationContext)
    val vaultRepository: VaultRepository = VaultRepository(application.applicationContext)
    private val prefs = application.getSharedPreferences("mygallery_prefs", Context.MODE_PRIVATE)

    val settings: AppSettings = AppSettings(application.applicationContext)
    val appLockManager: AppLockManager = AppLockManager(application.applicationContext)

    val isFirebaseConfigured: Boolean = repository.firebaseManager.isFirebaseInitialized

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _currentTab = MutableStateFlow(ScreenTab.HOME)
    val currentTab: StateFlow<ScreenTab> = _currentTab.asStateFlow()

    private val _selectedPhoto = MutableStateFlow<GalleryPhoto?>(null)
    val selectedPhoto: StateFlow<GalleryPhoto?> = _selectedPhoto.asStateFlow()

    private val _editingPhoto = MutableStateFlow<GalleryPhoto?>(null)
    val editingPhoto: StateFlow<GalleryPhoto?> = _editingPhoto.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _isAppLocked = MutableStateFlow(settings.isAppLockEnabled && settings.lockOnAppStart)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _galleryFilter = MutableStateFlow(GalleryFilter.ALL)
    val galleryFilter: StateFlow<GalleryFilter> = _galleryFilter.asStateFlow()

    private val _gridColumns = MutableStateFlow(2)
    val gridColumns: StateFlow<Int> = _gridColumns.asStateFlow()

    private val _uploadingPhotoIds = MutableStateFlow<Set<String>>(emptySet())
    val uploadingPhotoIds: StateFlow<Set<String>> = _uploadingPhotoIds.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isAutoSyncing = MutableStateFlow(false)
    val isAutoSyncing: StateFlow<Boolean> = _isAutoSyncing.asStateFlow()

    private var cameraObserver: ContentObserver? = null

    init {
        // 1. Check if there is an active Firebase user
        val existingUser = repository.firebaseManager.getCurrentUser()
        if (existingUser != null) {
            _currentUser.value = existingUser
        } else {
            // Restore persistent local profile if previously used
            val savedUid = prefs.getString("saved_user_uid", null)
            val savedName = prefs.getString("saved_user_name", null)
            val savedEmail = prefs.getString("saved_user_email", null)
            if (savedUid != null) {
                _currentUser.value = UserProfile(
                    uid = savedUid,
                    email = savedEmail ?: "user@mygallery.app",
                    displayName = savedName ?: "My Gallery User",
                    photoUrl = null,
                    isAnonymous = false
                )
            } else {
                // Default to a permanent local device user so photos are preserved immediately
                val defaultUser = UserProfile(
                    uid = "local_phone_user",
                    email = "device@mygallery.app",
                    displayName = "My Phone Gallery",
                    photoUrl = null,
                    isAnonymous = false
                )
                saveUserProfile(defaultUser)
                _currentUser.value = defaultUser
            }
        }

        // Setup real-time camera photo observer (Requirement 2 & System 3)
        setupCameraObserver()

        // Trigger initial camera scan if permission is already granted and setting is enabled
        if (repository.hasStoragePermission() && settings.isAutoDetectCameraEnabled) {
            syncCameraPhotos()
        }
    }

    private fun setupCameraObserver() {
        try {
            cameraObserver = repository.createCameraContentObserver(
                scope = viewModelScope,
                getUserId = { _currentUser.value?.uid }
            ) { newCount ->
                if (newCount > 0 && settings.isAutoDetectCameraEnabled) {
                    _statusMessage.value = "Camera: $newCount new ${if (newCount == 1) "photo" else "photos"} automatically added to MyGallery!"
                }
            }
            getApplication<Application>().contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                cameraObserver!!
            )
        } catch (e: Exception) {
            // Ignore if observer registration fails
        }
    }

    override fun onCleared() {
        super.onCleared()
        cameraObserver?.let {
            try {
                getApplication<Application>().contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentPhotos: StateFlow<List<GalleryPhoto>> = _currentUser.flatMapLatest { user ->
        if (user == null) flowOf(emptyList())
        else repository.getPhotos(user.uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val favoritePhotos: StateFlow<List<GalleryPhoto>> = _currentUser.flatMapLatest { user ->
        if (user == null) flowOf(emptyList())
        else repository.getFavoritePhotos(user.uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uploadedPhotos: StateFlow<List<GalleryPhoto>> = _currentUser.flatMapLatest { user ->
        if (user == null) flowOf(emptyList())
        else repository.getUploadedPhotos(user.uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(tab: ScreenTab) {
        _currentTab.value = tab
    }

    fun setFilter(filter: GalleryFilter) {
        _galleryFilter.value = filter
    }

    fun toggleGridColumns() {
        _gridColumns.value = if (_gridColumns.value == 2) 3 else 2
    }

    fun viewPhoto(photo: GalleryPhoto) {
        _selectedPhoto.value = photo
    }

    fun dismissPhotoViewer() {
        _selectedPhoto.value = null
    }

    fun openEditor(photo: GalleryPhoto) {
        _editingPhoto.value = photo
    }

    fun closeEditor() {
        _editingPhoto.value = null
    }

    fun openSettings() {
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
    }

    fun unlockApp() {
        _isAppLocked.value = false
    }

    fun lockApp() {
        if (settings.isAppLockEnabled) {
            _isAppLocked.value = true
        }
    }

    fun onAppReturnedFromBackground() {
        if (settings.isAppLockEnabled && settings.lockOnBackgroundReturn) {
            _isAppLocked.value = true
        }
        if (settings.isAutoDetectCameraEnabled && repository.hasStoragePermission()) {
            syncCameraPhotos()
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private fun saveUserProfile(profile: UserProfile) {
        prefs.edit()
            .putString("saved_user_uid", profile.uid)
            .putString("saved_user_name", profile.displayName)
            .putString("saved_user_email", profile.email)
            .apply()
    }

    /**
     * Requirement 2: Scans camera photos and automatically brings them into MyGallery.
     */
    fun syncCameraPhotos() {
        if (!settings.isAutoDetectCameraEnabled) return
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            _isAutoSyncing.value = true
            try {
                val newCount = repository.syncCameraPhotos(user.uid)
                if (newCount > 0) {
                    _statusMessage.value = "Synced $newCount new ${if (newCount == 1) "photo" else "photos"} from camera!"
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                _isAutoSyncing.value = false
            }
        }
    }

    fun signInWithEmail(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _authError.value = "Please enter both email and password"
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            val result = repository.firebaseManager.signInWithEmail(email, pass)
            result.onSuccess { user ->
                saveUserProfile(user)
                _currentUser.value = user
                syncCameraPhotos()
            }.onFailure { error ->
                _authError.value = error.localizedMessage ?: "Failed to sign in"
            }
            _authLoading.value = false
        }
    }

    fun signUpWithEmail(email: String, pass: String, displayName: String) {
        if (email.isBlank() || pass.isBlank()) {
            _authError.value = "Please enter both email and password"
            return
        }
        if (pass.length < 6) {
            _authError.value = "Password must be at least 6 characters"
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            val result = repository.firebaseManager.signUpWithEmail(email, pass, displayName)
            result.onSuccess { user ->
                saveUserProfile(user)
                _currentUser.value = user
                syncCameraPhotos()
            }.onFailure { error ->
                _authError.value = error.localizedMessage ?: "Failed to create account"
            }
            _authLoading.value = false
        }
    }

    fun signInWithGoogleToken(idToken: String) {
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            val result = repository.firebaseManager.signInWithGoogleIdToken(idToken)
            result.onSuccess { user ->
                saveUserProfile(user)
                _currentUser.value = user
                syncCameraPhotos()
            }.onFailure { error ->
                _authError.value = error.localizedMessage ?: "Google sign in failed"
            }
            _authLoading.value = false
        }
    }

    /**
     * System 1: Official Facebook / Meta OAuth login flow
     */
    fun signInWithFacebook(activity: Activity) {
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            val result = repository.firebaseManager.signInWithFacebookProvider(activity)
            result.onSuccess { user ->
                saveUserProfile(user)
                _currentUser.value = user
                syncCameraPhotos()
                _statusMessage.value = "Signed in with Facebook (${user.displayName ?: "User"})"
            }.onFailure { error ->
                val msg = error.localizedMessage ?: "Facebook sign in failed"
                if (msg.contains("disabled", ignoreCase = true) || msg.contains("configuration", ignoreCase = true)) {
                    _authError.value = "Facebook Sign-In: Facebook OAuth provider must be enabled with your Meta App ID in Firebase Console (Authentication > Sign-in method > Facebook)."
                } else {
                    _authError.value = "Facebook Sign-In: $msg"
                }
            }
            _authLoading.value = false
        }
    }

    fun signInDemoUser() {
        val demoUser = UserProfile(
            uid = "demo_user_permanent",
            email = "demo.gallery@aistudio.dev",
            displayName = "Alex Morgan",
            photoUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=400&q=80",
            isAnonymous = false
        )
        saveUserProfile(demoUser)
        _currentUser.value = demoUser
        _statusMessage.value = "Signed in with permanent demo profile"
        syncCameraPhotos()
    }

    fun signOut() {
        repository.firebaseManager.signOut()
        val defaultUser = UserProfile(
            uid = "local_phone_user",
            email = "device@mygallery.app",
            displayName = "My Phone Gallery",
            photoUrl = null,
            isAnonymous = false
        )
        saveUserProfile(defaultUser)
        _currentUser.value = defaultUser
        _selectedPhoto.value = null
        _editingPhoto.value = null
        _isSettingsOpen.value = false
        _currentTab.value = ScreenTab.HOME
        _statusMessage.value = "Switched to local device gallery"
    }

    /**
     * Requirement 1: Photos added through picker are permanently copied into app storage.
     */
    fun onPhotosPicked(uris: List<Uri>, context: Context) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            var addedCount = 0
            for (uri in uris) {
                try {
                    var fileName = "photo_${System.currentTimeMillis()}.jpg"
                    var fileSize = 0L

                    val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                            if (nameIndex != -1) fileName = it.getString(nameIndex) ?: fileName
                            if (sizeIndex != -1) fileSize = it.getLong(sizeIndex)
                        }
                    }

                    val savedPhoto = repository.addLocalPhoto(
                        userId = user.uid,
                        uri = uri,
                        fileName = fileName,
                        fileSizeBytes = fileSize,
                        source = "PICKER"
                    )
                    addedCount++

                    if (repository.firebaseManager.storage != null) {
                        uploadPhoto(savedPhoto)
                    }
                } catch (e: Exception) {
                    // Continue with next photo
                }
            }
            if (addedCount > 0) {
                _statusMessage.value = "Added $addedCount ${if (addedCount == 1) "photo" else "photos"} permanently to MyGallery"
            }
        }
    }

    fun createCameraCaptureUri(context: Context): Pair<Uri, File> {
        val cacheDir = File(context.cacheDir, "camera").apply { if (!exists()) mkdirs() }
        val imageFile = File(cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
        return Pair(uri, imageFile)
    }

    fun onCameraPhotoCaptured(capturedUri: Uri, context: Context) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            try {
                val fileName = "camera_${System.currentTimeMillis()}.jpg"
                val savedPhoto = repository.addLocalPhoto(
                    userId = user.uid,
                    uri = capturedUri,
                    fileName = fileName,
                    fileSizeBytes = 0L,
                    source = "CAMERA"
                )
                _statusMessage.value = "New camera photo saved permanently in MyGallery!"
                if (repository.firebaseManager.storage != null) {
                    uploadPhoto(savedPhoto)
                }
            } catch (e: Exception) {
                _statusMessage.value = "Failed to save camera photo: ${e.localizedMessage}"
            }
        }
    }

    /**
     * System 2: Save Edited Photo Handler
     */
    fun saveEditedPhoto(originalPhoto: GalleryPhoto, savedFile: File, replaceOriginal: Boolean) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            try {
                if (replaceOriginal) {
                    val updated = repository.updatePhotoFile(originalPhoto, savedFile)
                    if (_selectedPhoto.value?.id == originalPhoto.id) {
                        _selectedPhoto.value = updated
                    }
                    _statusMessage.value = "Photo updated in MyGallery (original phone photo safe)"
                } else {
                    val newPhoto = repository.addLocalPhoto(
                        userId = user.uid,
                        uri = Uri.fromFile(savedFile),
                        fileName = "edited_${originalPhoto.fileName}",
                        fileSizeBytes = savedFile.length(),
                        source = "EDITED"
                    )
                    _selectedPhoto.value = newPhoto
                    _statusMessage.value = "Saved as new photo in MyGallery!"
                }
                _editingPhoto.value = null
            } catch (e: Exception) {
                _statusMessage.value = "Error saving edited photo: ${e.localizedMessage}"
            }
        }
    }

    fun uploadPhoto(photo: GalleryPhoto) {
        if (!repository.firebaseManager.isFirebaseInitialized) {
            _statusMessage.value = "Firebase Storage not connected. Photo is permanently saved locally."
            return
        }

        viewModelScope.launch {
            _uploadingPhotoIds.value = _uploadingPhotoIds.value + photo.id
            val result = repository.uploadPhotoToCloud(photo)
            result.onSuccess {
                _statusMessage.value = "Uploaded ${photo.fileName} to Cloud"
                if (_selectedPhoto.value?.id == photo.id) {
                    _selectedPhoto.value = it
                }
            }.onFailure { err ->
                _statusMessage.value = "Upload error: ${err.localizedMessage ?: "Could not upload"}"
            }
            _uploadingPhotoIds.value = _uploadingPhotoIds.value - photo.id
        }
    }

    fun toggleFavorite(photo: GalleryPhoto) {
        viewModelScope.launch {
            repository.toggleFavorite(photo)
            if (_selectedPhoto.value?.id == photo.id) {
                _selectedPhoto.value = photo.copy(isFavorite = !photo.isFavorite)
            }
        }
    }

    /**
     * Requirement 3: "MyGallery से हटाने पर original Gallery photo delete न हो"
     */
    fun deletePhoto(photo: GalleryPhoto) {
        viewModelScope.launch {
            repository.deletePhoto(photo)
            if (_selectedPhoto.value?.id == photo.id) {
                _selectedPhoto.value = null
            }
            _statusMessage.value = "Removed from MyGallery. Your original phone photo is safe and not deleted."
        }
    }

    // ================= PRIVATE MEDIA VAULT =================

    val vaultPhotos: StateFlow<List<VaultMediaItem>> = vaultRepository.getPhotos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaultVideos: StateFlow<List<VaultMediaItem>> = vaultRepository.getVideos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaultAudio: StateFlow<List<VaultMediaItem>> = vaultRepository.getAudio()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    fun unlockVault() {
        _isVaultUnlocked.value = true
    }

    fun lockVault() {
        _isVaultUnlocked.value = false
    }

    fun importVaultMedia(uri: Uri, type: VaultMediaType, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val result = vaultRepository.importMedia(uri, type)
            result.onSuccess { item ->
                _statusMessage.value = "Securely imported ${item.originalName} to Private Vault"
                onComplete?.invoke(true)
            }.onFailure { err ->
                _statusMessage.value = "Import failed: ${err.localizedMessage ?: "Unknown error"}"
                onComplete?.invoke(false)
            }
        }
    }

    fun restoreVaultMedia(item: VaultMediaItem, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val result = vaultRepository.restoreMedia(item)
            result.onSuccess {
                _statusMessage.value = "Restored ${item.originalName} to normal storage"
                onComplete?.invoke(true)
            }.onFailure { err ->
                _statusMessage.value = "Restore failed: ${err.localizedMessage ?: "Unknown error"}"
                onComplete?.invoke(false)
            }
        }
    }

    fun deleteVaultMediaPermanently(item: VaultMediaItem, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val result = vaultRepository.deletePermanently(item)
            result.onSuccess {
                _statusMessage.value = "Permanently deleted ${item.originalName}"
                onComplete?.invoke(true)
            }.onFailure { err ->
                _statusMessage.value = "Delete failed: ${err.localizedMessage ?: "Unknown error"}"
                onComplete?.invoke(false)
            }
        }
    }

    fun batchRestoreVaultMedia(items: List<VaultMediaItem>, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val restoredCount = vaultRepository.batchRestoreMedia(items)
            _statusMessage.value = "Restored $restoredCount of ${items.size} files to normal storage"
            onComplete?.invoke()
        }
    }

    fun batchDeleteVaultMedia(items: List<VaultMediaItem>, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val deletedCount = vaultRepository.batchDeletePermanently(items)
            _statusMessage.value = "Permanently deleted $deletedCount files"
            onComplete?.invoke()
        }
    }

    fun shareVaultMediaAuthenticated(items: List<VaultMediaItem>, targetPackage: String? = null): Boolean {
        return vaultRepository.shareAuthenticatedMedia(items, targetPackage)
    }
}
