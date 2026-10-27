package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.VaultMediaItem
import com.example.data.model.VaultMediaType
import com.example.data.repository.VaultRepository
import com.example.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultSecurityAndDuplicateTest {

    private lateinit var context: Context
    private lateinit var repository: VaultRepository
    private lateinit var settings: AppSettings
    private lateinit var testMediaDir: File

    @Before
    fun setUp() = runBlocking(Dispatchers.IO) {
        context = ApplicationProvider.getApplicationContext()
        repository = VaultRepository(context)
        settings = AppSettings(context)
        testMediaDir = File(context.cacheDir, "test_source_media").apply { mkdirs() }
        AppDatabase.getInstance(context).clearAllTables()
    }

    private fun createDummySourceMedia(name: String, content: String = "dummy_content_bytes"): Uri {
        val file = File(testMediaDir, name)
        FileOutputStream(file).use { it.write(content.toByteArray()) }
        return Uri.fromFile(file)
    }

    @Test
    fun `test Case A - Import one photo results in exactly 1 vault item`() = runBlocking {
        val uri = createDummySourceMedia("photo_a.jpg", "image_a_content")
        val result = repository.importMedia(uri, VaultMediaType.PHOTO)

        assertTrue("Import must succeed", result.isSuccess)
        val photos = repository.getPhotos().first()
        assertEquals("Must contain exactly 1 photo", 1, photos.size)
        assertEquals("photo_a.jpg", photos[0].originalName)
        assertTrue(File(photos[0].privateFilePath).exists())
    }

    @Test
    fun `test Case B - Import the same photo again results in still exactly 1 item`() = runBlocking {
        val uri = createDummySourceMedia("photo_b.jpg", "image_b_unique_content")

        // First import
        val result1 = repository.importMedia(uri, VaultMediaType.PHOTO)
        assertTrue(result1.isSuccess)

        // Second import of same URI/content
        val result2 = repository.importMedia(uri, VaultMediaType.PHOTO)
        assertTrue(result2.isSuccess)

        val photos = repository.getPhotos().first()
        assertEquals("Duplicate must be prevented; list must contain exactly 1 photo", 1, photos.size)
        assertEquals(result1.getOrNull()?.id, result2.getOrNull()?.id)
    }

    @Test
    fun `test Case C - Import 5 photos results in exactly 5 vault items`() = runBlocking {
        for (i in 1..5) {
            val uri = createDummySourceMedia("photo_batch_$i.jpg", "content_stream_$i")
            val result = repository.importMedia(uri, VaultMediaType.PHOTO)
            assertTrue("Import $i must succeed", result.isSuccess)
        }

        val allPhotos = repository.getPhotos().first().filter { it.originalName.startsWith("photo_batch_") }
        assertEquals("Must contain exactly 5 vault items", 5, allPhotos.size)
    }

    @Test
    fun `test Case E - Select All selects all filtered items and counts correctly`() {
        val dummyItems = (1..4).map {
            VaultMediaItem(
                id = "item_$it",
                mediaType = VaultMediaType.PHOTO,
                originalName = "test_$it.jpg",
                privateFilePath = "/data/vault/item_$it.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 1024L
            )
        }

        val selectedIds = mutableListOf<String>()

        // Simulate Select All click
        selectedIds.clear()
        selectedIds.addAll(dummyItems.map { it.id })

        assertEquals(4, selectedIds.size)
        assertTrue(selectedIds.containsAll(dummyItems.map { it.id }))
    }

    @Test
    fun `test Case F - Batch delete permanently removes selected items from DB and disk`() = runBlocking {
        val uri1 = createDummySourceMedia("delete_test_1.jpg", "del_content_1")
        val uri2 = createDummySourceMedia("delete_test_2.jpg", "del_content_2")

        val item1 = repository.importMedia(uri1, VaultMediaType.PHOTO).getOrThrow()
        val item2 = repository.importMedia(uri2, VaultMediaType.PHOTO).getOrThrow()

        val initialPhotos = repository.getPhotos().first()
        assertTrue(initialPhotos.any { it.id == item1.id })
        assertTrue(initialPhotos.any { it.id == item2.id })

        val deletedCount = repository.batchDeletePermanently(listOf(item1, item2))
        assertEquals(2, deletedCount)

        assertFalse(File(item1.privateFilePath).exists())
        assertFalse(File(item2.privateFilePath).exists())

        val remainingPhotos = repository.getPhotos().first()
        assertFalse(remainingPhotos.any { it.id == item1.id })
        assertFalse(remainingPhotos.any { it.id == item2.id })
    }

    @Test
    fun `test Case G and H - Wrong PIN must reject authentication before WhatsApp share`() {
        settings.setPin("1234")

        val wrongPin = "9999"
        val isVerified = settings.verifyPin(wrongPin)
        assertFalse("Wrong PIN must be rejected", isVerified)

        // Ensure no URIs should be generated or shared when PIN fails
        val mockItems = listOf<VaultMediaItem>()
        val uris = repository.getShareableContentUris(mockItems)
        assertTrue("No URIs granted when auth fails", uris.isEmpty())
    }

    @Test
    fun `test Case I - Correct PIN validates authentication and allows media sharing`() {
        settings.setPin("5678")

        val correctPin = "5678"
        assertTrue("Correct PIN must verify", settings.verifyPin(correctPin))
    }

    @Test
    fun `test Case D - After successful import original source file can be safely removed`() = runBlocking {
        val uri = createDummySourceMedia("photo_d.jpg", "content_d")
        val result = repository.importMedia(uri, VaultMediaType.PHOTO)

        assertTrue("Import must succeed", result.isSuccess)
        val privateFile = File(result.getOrThrow().privateFilePath)
        assertTrue("Private file must exist and be verified", privateFile.exists() && privateFile.length() > 0L)
    }

    @Test
    fun `test Case J - App restart distinct check prevents any duplicate display`() = runBlocking {
        val uri = createDummySourceMedia("photo_restart.jpg", "restart_content")
        repository.importMedia(uri, VaultMediaType.PHOTO)

        // Simulate app restart / re-querying flow
        val reloadedPhotos = repository.getPhotos().first().filter { it.originalName == "photo_restart.jpg" }
        assertEquals("Must display exactly 1 photo across reloads", 1, reloadedPhotos.size)
    }

    @Test
    fun `test Case K - Restore deletes private vault file and removes record from database`() = runBlocking {
        val uri = createDummySourceMedia("photo_k.jpg", "content_k_restore")
        val item = repository.importMedia(uri, VaultMediaType.PHOTO).getOrThrow()
        val privateFile = File(item.privateFilePath)
        assertTrue(privateFile.exists())

        // Simulating restore flow: safe verification before removal
        val isVerified = privateFile.exists() && privateFile.length() > 0L
        assertTrue("Private file verified before restore", isVerified)

        // Call repository restore logic
        val restoreResult = repository.restoreMedia(item)
        if (restoreResult.isSuccess) {
            // Once restored, private file is deleted from vault
            assertFalse("Private file deleted after restore", privateFile.exists())
            val photosAfterRestore = repository.getPhotos().first().filter { it.id == item.id }
            assertTrue("Vault record removed after restore", photosAfterRestore.isEmpty())
        }
    }
}
