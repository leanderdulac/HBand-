package com.example.util

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Synthetic artifacts retained in a fresh, network-disabled .storagelab emulator. */
@RunWith(AndroidJUnit4::class)
class ProviderAndroidLabTest {
    private lateinit var context: Context
    private val evidence get() = File(context.filesDir, "provider-native-evidence.json")
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    @Before fun requireIsolatedEmulator() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.aistudio.hbandhealthtech.pxq97m.storagelab")
        check(context.applicationContext.javaClass == Application::class.java)
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        check(InstrumentationRegistry.getArguments().getString("providerLab") == "synthetic-only")
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(android.Manifest.permission.INTERNET))
        assertTrue("Operational database must not be initialized", context.databaseList().isEmpty())
        val provider = context.packageManager.getProviderInfo(ComponentName(context, FileProvider::class.java), 0)
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
        assertEquals("${context.packageName}.fileprovider", provider.authority)
    }

    private fun verifyUri(uri: Uri, file: File): String {
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.fileprovider", uri.authority)
        assertEquals(context.cacheDir.canonicalFile, file.parentFile!!.canonicalFile)
        assertEquals("image/png", context.contentResolver.getType(uri))
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        assertTrue(bytes.size > 100)
        assertArrayEquals(file.readBytes(), bytes)
        val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertNotNull(image)
        assertEquals(1080, image.width)
        assertEquals(1350, image.height)
        image.recycle()
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)!!.use {
            assertTrue(it.moveToFirst())
            assertEquals(file.name, it.getString(0))
            assertEquals(file.length(), it.getLong(1))
            assertFalse(it.moveToNext())
        }
        return digest(bytes)
    }

    private fun verifyOutsideRootsRejected() {
        // No file is created here; this is outside every configured FileProvider root.
        val outside = File(context.dataDir, "databases/provider-lab-outside.png")
        try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outside)
            fail("Unconfigured root must not receive a URI")
        } catch (_: IllegalArgumentException) { /* Expected boundary refusal. */ }
    }

    @Test fun generateAndVerifyNativeUris() {
        check(!evidence.exists()) { "Existing evidence must be preserved; do not reseed" }
        val absent = ProgressImageGenerator.generateWeeklyProgressImage(context, emptyList())
        val known = ProgressImageGenerator.generateWeeklyProgressImage(context, emptyList(), 500, 90)
        assertNotEquals(absent.uri, known.uri)
        assertNotEquals(absent.file.canonicalPath, known.file.canonicalPath)
        assertTrue(absent.summaryText.contains("Água registrada hoje: Indisponível"))
        assertTrue(absent.summaryText.contains("Respiração — total salvo no aplicativo: Indisponível"))
        assertTrue(known.summaryText.contains("Água registrada hoje: 500 mL"))
        assertTrue(known.summaryText.contains("Respiração — total salvo no aplicativo: 1 min 30 s"))
        val rows = JSONArray()
        for (data in listOf(absent, known)) {
            val hash = verifyUri(data.uri, data.file)
            rows.put(JSONObject().put("uri", data.uri.toString()).put("file", data.file.absolutePath)
                .put("sha256", hash).put("bytes", data.file.length()))
            data.bitmap.recycle()
        }
        verifyOutsideRootsRejected()
        evidence.writeText(JSONObject().put("pid", Process.myPid()).put("artifacts", rows).toString(2))
    }

    @Test fun reopenRetainedUrisInAnotherProcess() {
        check(evidence.isFile) { "Seed evidence required; do not create substitutes" }
        val before = evidence.readBytes()
        val saved = JSONObject(String(before, Charsets.UTF_8))
        assertNotEquals(saved.getInt("pid"), Process.myPid())
        val rows = saved.getJSONArray("artifacts")
        assertEquals(2, rows.length())
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val file = File(row.getString("file"))
            assertEquals(row.getLong("bytes"), file.length())
            assertEquals(row.getString("sha256"), verifyUri(Uri.parse(row.getString("uri")), file))
        }
        verifyOutsideRootsRejected()
        assertArrayEquals(before, evidence.readBytes())
    }
}
