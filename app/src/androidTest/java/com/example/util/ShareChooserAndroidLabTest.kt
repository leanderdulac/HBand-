package com.example.util

import android.app.Application
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.*
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ui.components.ShareProgressDialog
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ShareChooserAndroidLabTest {
    @get:Rule val compose = createComposeRule()

    @Test fun productionDialogDeliversReadOnlyCardThroughAndroidChooser() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val target = "com.aistudio.hbandhealthtech.pxq97m.storagelab"
        val recipient = "$target.test"
        check(context.packageName == target)
        check(context.applicationContext.javaClass == Application::class.java)
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        check(InstrumentationRegistry.getArguments().getString("providerLab") == "synthetic-only")
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(android.Manifest.permission.INTERNET))
        assertEquals(PackageManager.PERMISSION_DENIED, context.packageManager.checkPermission(android.Manifest.permission.INTERNET, recipient))
        assertTrue(context.databaseList().isEmpty())
        val started = File(context.filesDir, "share-chooser-started.txt")
        check(!started.exists() && started.createNewFile()) { "Preserve previous run" }
        val card = ProgressImageGenerator.generateWeeklyProgressImage(context, emptyList(), 500, 90)
        val before = card.file.readBytes()
        val hash = MessageDigest.getInstance("SHA-256").digest(before).joinToString("") { "%02x".format(it) }
        val uid = context.packageManager.getApplicationInfo(recipient, 0).uid
        assertNotEquals(android.os.Process.myUid(), uid)
        val responses = LinkedBlockingQueue<Bundle>()
        val thread = HandlerThread("chooser-probe-replies").apply { start() }
        val replies = Messenger(Handler(thread.looper) { responses.offer(it.data); true })
        val latch = CountDownLatch(1)
        var remote: Messenger? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) { remote = Messenger(binder); latch.countDown() }
            override fun onServiceDisconnected(name: ComponentName) { remote = null }
        }
        var bound = false
        val automation = instrumentation.uiAutomation
        fun screenshot(name: String) {
            // Accessibility/Compose state can advance before the animation is drawn.
            // Require consecutive identical frames after a minimum animation interval.
            SystemClock.sleep(600)
            var previous = checkNotNull(automation.takeScreenshot())
            repeat(12) {
                SystemClock.sleep(250)
                val current = checkNotNull(automation.takeScreenshot())
                val stable = current.sameAs(previous)
                previous.recycle()
                if (stable) {
                    File(context.filesDir, "$name.png").outputStream().use {
                        check(current.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                    current.recycle()
                    return
                }
                previous = current
            }
            previous.recycle()
            error("No stable screenshot: $name")
        }
        fun probe(operation: Int): Bundle {
            checkNotNull(remote).send(Message.obtain(null, operation).apply {
                data = Bundle().apply { putString("uri", card.uri.toString()) }
                replyTo = replies
            })
            val result = checkNotNull(responses.poll(10, TimeUnit.SECONDS)) { "Probe timeout" }
            assertEquals(uid, result.getInt("uid"))
            assertNotEquals(android.os.Process.myPid(), result.getInt("pid"))
            return result
        }
        try {
            bound = context.bindService(Intent().setComponent(ComponentName(recipient, "com.example.util.ProviderProbeService")), connection, Context.BIND_AUTO_CREATE)
            check(bound && latch.await(10, TimeUnit.SECONDS))
            assertEquals("", probe(3).getString("receipt"))
            val denied = probe(1)
            assertEquals("denied", denied.getString("outcome"))
            assertEquals("java.lang.SecurityException", denied.getString("exception"))
            compose.setContent { MaterialTheme { ShareProgressDialog(card, {}, {}) } }
            compose.onNodeWithTag("share_progress_intent_button").performScrollTo().assertIsDisplayed()
            screenshot("share-dialog")
            compose.onNodeWithTag("share_progress_intent_button").performClick()
            // Find only the own synthetic receiver in Android's chooser. Never click another target.
            var selected = false
            var chooserPackage = ""
            val deadline = SystemClock.elapsedRealtime() + 15000
            while (!selected && SystemClock.elapsedRealtime() < deadline) {
                val root = automation.rootInActiveWindow
                if (root?.packageName?.toString() in setOf("android", "com.android.intentresolver")) {
                    val label = root!!.findAccessibilityNodeInfosByText("Next2U Lab Receiver")
                        .singleOrNull { it.text?.toString() == "Next2U Lab Receiver" }
                    if (label != null) {
                        chooserPackage = root.packageName.toString()
                        screenshot("android-chooser")
                        var node: AccessibilityNodeInfo? = label
                        while (node != null && !node.isClickable) node = node.parent
                        selected = node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                    }
                }
                if (!selected) SystemClock.sleep(200)
            }
            check(selected) { "Own receiver was not selected from Android chooser" }
            var receipt = ""
            val receivedDeadline = SystemClock.elapsedRealtime() + 10000
            while (receipt.isEmpty() && SystemClock.elapsedRealtime() < receivedDeadline) {
                receipt = probe(3).getString("receipt").orEmpty()
                if (receipt.isEmpty()) SystemClock.sleep(100)
            }
            val result = JSONObject(receipt)
            assertEquals("received", result.getString("outcome"))
            assertEquals(Intent.ACTION_SEND, result.getString("action"))
            assertEquals("image/png", result.getString("mime"))
            assertEquals(card.uri.toString(), result.getString("uri"))
            assertEquals(card.summaryText, result.getString("summary"))
            assertEquals(hash, result.getString("sha256"))
            assertEquals(before.size.toLong(), result.getLong("bytes"))
            assertEquals(uid, result.getInt("uid"))
            assertNotEquals(android.os.Process.myPid(), result.getInt("pid"))
            assertTrue(result.getBoolean("writeDenied"))
            assertNotEquals(0, result.getInt("flags") and Intent.FLAG_GRANT_READ_URI_PERMISSION)
            assertEquals(0, result.getInt("flags") and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            assertArrayEquals(before, card.file.readBytes())
            assertTrue(context.databaseList().isEmpty())
            val visibleDeadline = SystemClock.elapsedRealtime() + 10000
            var receiverVisible = false
            while (!receiverVisible && SystemClock.elapsedRealtime() < visibleDeadline) {
                val root = automation.rootInActiveWindow
                receiverVisible = root?.packageName?.toString() == recipient &&
                    root.findAccessibilityNodeInfosByText("Synthetic card received").isNotEmpty()
                if (!receiverVisible) SystemClock.sleep(100)
            }
            check(receiverVisible) { "Synthetic receiver UI not visible" }
            screenshot("synthetic-receiver")
            File(context.filesDir, "share-chooser-evidence.json").writeText(JSONObject()
                .put("targetUid", android.os.Process.myUid()).put("targetPid", android.os.Process.myPid())
                .put("deniedBeforeChooser", true).put("chooserPackage", chooserPackage)
                .put("productionDialogButtonUsed", true).put("explicitGrantCalled", false)
                .put("file", card.file.absolutePath).put("artifactUnchanged", true).put("receipt", result).toString(2))
        } catch (failure: Throwable) {
            runCatching { screenshot("share-chooser-failure") }
            throw failure
        } finally {
            if (bound) context.unbindService(connection)
            thread.quitSafely()
            // Compose can still draw the retained bitmap until its activity is disposed by the rule.
        }
    }
}
