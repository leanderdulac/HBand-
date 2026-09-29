package com.example.util

import android.content.ClipboardManager
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 36], application = android.app.Application::class)
class HealthClipboardTest {
    @Test fun copying_marks_sensitive_without_changing_the_selected_text() {
        val context = RuntimeEnvironment.getApplication()
        val original = "Registros fictícios\n72 bpm, \"valor\""
        copyHealthText(context, "Test records", original)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip!!
        assertEquals(original, clip.getItemAt(0).text.toString())
        assertEquals("Test records", clip.description.label)
        assertTrue(clip.description.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"))
    }
}
