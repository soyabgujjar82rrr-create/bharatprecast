package com.example.data.firebase

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.model.UserProfile
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseManager(private val context: Context) {
    private val tag = "FirebaseManager"

    val isFirebaseInitialized: Boolean
        get() {
            return try {
                val apps = FirebaseApp.getApps(context)
                if (apps.isNotEmpty()) {
                    true
                } else {
                    FirebaseApp.initializeApp(context) != null
                }
            } catch (e: Exception) {
                Log.w(tag, "Firebase not configured yet: ${e.message}")
                false
            }
        }

    val auth: FirebaseAuth?
        get() {
            return if (isFirebaseInitialized) {
                try {
                    FirebaseAuth.getInstance()
                } catch (e: Exception) {
                    Log.w(tag, "Failed to get FirebaseAuth: ${e.message}")
                    null
                }
            } else null
        }

    val storage: FirebaseStorage?
        get() {
            return if (isFirebaseInitialized) {
                try {
                    FirebaseStorage.getInstance()
                } catch (e: Exception) {
                    Log.w(tag, "Failed to get FirebaseStorage: ${e.message}")
                    null
                }
            } else null
        }

    fun getCurrentUser(): UserProfile? {
        val user = auth?.currentUser ?: return null
        return user.toUserProfile()
    }

    suspend fun signInWithEmail(email: String, pass: String): Result<UserProfile> {
        val firebaseAuth = auth ?: return Result.failure(Exception("Firebase is not configured. Please add google-services.json."))
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email.trim(), pass).await()
            val user = result.user ?: throw Exception("User is null after sign in")
            Result.success(user.toUserProfile())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUpWithEmail(email: String, pass: String, displayName: String): Result<UserProfile> {
        val firebaseAuth = auth ?: return Result.failure(Exception("Firebase is not configured. Please add google-services.json."))
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email.trim(), pass).await()
            val user = result.user ?: throw Exception("User is null after registration")
            if (displayName.isNotBlank()) {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName.trim())
                    .build()
                user.updateProfile(profileUpdates).await()
            }
            Result.success(user.toUserProfile())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String): Result<UserProfile> {
        val firebaseAuth = auth ?: return Result.failure(Exception("Firebase is not configured. Please add google-services.json."))
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val user = result.user ?: throw Exception("Google sign in returned empty user")
            Result.success(user.toUserProfile())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInWithFacebookProvider(activity: Activity): Result<UserProfile> {
        val firebaseAuth = auth ?: return Result.failure(Exception("Firebase is not configured. Please add google-services.json."))
        return try {
            val provider = OAuthProvider.newBuilder("facebook.com")
            // Scopes can be added if needed: provider.addCustomParameter("scope", "email,public_profile")
            val pending = firebaseAuth.pendingAuthResult
            val result = if (pending != null) {
                pending.await()
            } else {
                firebaseAuth.startActivityForSignInWithProvider(activity, provider.build()).await()
            }
            val user = result.user ?: throw Exception("Facebook sign in returned empty user")
            Result.success(user.toUserProfile())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.e(tag, "Error signing out", e)
        }
    }

    /**
     * Upload photo to Firebase Storage strictly under users/{userId}/photos/{photoId}.jpg
     * Each user only has access to their own subfolder.
     */
    suspend fun uploadPhoto(
        userId: String,
        photoId: String,
        fileUri: Uri,
        mimeType: String = "image/jpeg"
    ): Result<Pair<String, String>> {
        val fbStorage = storage ?: return Result.failure(Exception("Firebase Storage is not initialized."))
        return try {
            val storageRef = fbStorage.reference
            val photoPath = "users/$userId/photos/${photoId}.jpg"
            val imageRef = storageRef.child(photoPath)

            val metadata = StorageMetadata.Builder()
                .setContentType(mimeType)
                .setCustomMetadata("uploadedBy", userId)
                .build()

            imageRef.putFile(fileUri, metadata).await()
            val downloadUrl = imageRef.downloadUrl.await().toString()
            Result.success(Pair(downloadUrl, photoPath))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePhoto(storagePath: String): Result<Unit> {
        val fbStorage = storage ?: return Result.failure(Exception("Firebase Storage is not initialized."))
        return try {
            val imageRef = fbStorage.reference.child(storagePath)
            imageRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun FirebaseUser.toUserProfile(): UserProfile {
        return UserProfile(
            uid = this.uid,
            email = this.email,
            displayName = this.displayName?.ifBlank { null } ?: this.email?.substringBefore('@') ?: "Gallery User",
            photoUrl = this.photoUrl?.toString(),
            isAnonymous = this.isAnonymous
        )
    }
}
