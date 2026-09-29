package com.example.util

import android.app.Application
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Explicit package grants, not ACTION_SEND/chooser delivery. No operational recipient. */
@RunWith(AndroidJUnit4::class)
class ProviderGrantAndroidLabTest {
    @Test fun readGrantIsScopedReadOnlyAndRevocableAcrossUids() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val target = "com.aistudio.hbandhealthtech.pxq97m.storagelab"
        val recipient = "$target.test"
        check(context.packageName == target)
        check(context.applicationContext.javaClass == Application::class.java)
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        check(InstrumentationRegistry.getArguments().getString("providerLab") == "synthetic-only")
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(android.Manifest.permission.INTERNET))
        assertEquals(PackageManager.PERMISSION_DENIED, context.packageManager.checkPermission(android.Manifest.permission.INTERNET, recipient))
        assertTrue(context.databaseList().isEmpty())
        val provider = context.packageManager.getProviderInfo(ComponentName(context, FileProvider::class.java), 0)
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
        val recipientUid = context.packageManager.getApplicationInfo(recipient, 0).uid
        assertNotEquals(android.os.Process.myUid(), recipientUid)
        val evidence = File(context.filesDir, "provider-grants-evidence.json")
        val started = File(context.filesDir, "provider-grants-started.txt")
        check(!evidence.exists() && !started.exists()) { "Preserve prior runs; use a fresh AVD" }
        check(started.createNewFile())
        val first = ProgressImageGenerator.generateWeeklyProgressImage(context, emptyList(), 500, 90)
        val second = ProgressImageGenerator.generateWeeklyProgressImage(context, emptyList())
        val originalFirst = first.file.readBytes()
        val originalSecond = second.file.readBytes()
        fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val expectedHash = digest(originalFirst)
        val responses = LinkedBlockingQueue<Bundle>()
        val replyThread = HandlerThread("provider-probe-replies").apply { start() }
        val replies = Messenger(Handler(replyThread.looper) { responses.offer(it.data); true })
        val connected = CountDownLatch(1)
        var remote: Messenger? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                remote = Messenger(binder)
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) { remote = null }
        }
        val rows = JSONArray()
        var bound = false
        fun probe(label: String, uri: Uri, operation: Int, expected: String) {
            val request = Message.obtain(null, operation).apply {
                data = Bundle().apply { putString("uri", uri.toString()) }
                replyTo = replies
            }
            checkNotNull(remote).send(request)
            val result = checkNotNull(responses.poll(10, TimeUnit.SECONDS)) { "Probe reply timed out: $label" }
            assertEquals(label, recipientUid, result.getInt("uid"))
            assertNotEquals(android.os.Process.myPid(), result.getInt("pid"))
            assertEquals(label, expected, result.getString("outcome"))
            if (expected == "allowed") {
                assertEquals(expectedHash, result.getString("sha256"))
                assertEquals(originalFirst.size.toLong(), result.getLong("bytes"))
            } else { assertEquals("java.lang.SecurityException", result.getString("exception")) }
            rows.put(JSONObject().put("phase", label).put("uid", result.getInt("uid"))
                .put("pid", result.getInt("pid")).put("outcome", result.getString("outcome"))
                .put("sha256", result.getString("sha256")).put("exception", result.getString("exception")))
        }
        try {
            bound = context.bindService(Intent().setComponent(ComponentName(recipient, "com.example.util.ProviderProbeService")), connection, Context.BIND_AUTO_CREATE)
            check(bound && connected.await(10, TimeUnit.SECONDS)) { "Separate-UID probe did not connect" }
            probe("without-grant", first.uri, 1, "denied")
            context.grantUriPermission(recipient, first.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            probe("read-granted", first.uri, 1, "allowed")
            probe("other-uri-without-grant", second.uri, 1, "denied")
            probe("write-with-read-grant", first.uri, 2, "denied")
            context.revokeUriPermission(first.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            probe("after-revocation", first.uri, 1, "denied")
            assertArrayEquals(originalFirst, first.file.readBytes())
            assertArrayEquals(originalSecond, second.file.readBytes())
            assertTrue(context.databaseList().isEmpty())
            evidence.writeText(JSONObject().put("targetUid", android.os.Process.myUid())
                .put("targetPid", android.os.Process.myPid()).put("recipientUid", recipientUid)
                .put("phases", rows).put("sourceFile", first.file.absolutePath).put("sha256", expectedHash)
                .put("secondFile", second.file.absolutePath).put("secondSha256", digest(originalSecond))
                .put("artifactsUnchanged", true).toString(2))
        } finally {
            context.revokeUriPermission(first.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (bound) context.unbindService(connection)
            replyThread.quitSafely()
            first.bitmap.recycle()
            second.bitmap.recycle()
        }
    }
}
