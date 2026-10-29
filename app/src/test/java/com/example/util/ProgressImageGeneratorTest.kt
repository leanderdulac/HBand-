package com.example.util

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.HBandSensorMetricEntity
import java.io.File
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = android.app.Application::class)
class ProgressImageGeneratorTest {
    @get:Rule val temporary = TemporaryFolder()
    private val zone = TimeZone.getTimeZone("America/Sao_Paulo")
    private val now = Calendar.getInstance(zone).apply { clear(); set(2026, 8, 15, 12, 0) }.timeInMillis

    @Test fun empty_share_card_has_no_fabricated_vitals_or_sleep_quality() {
        val summary = weeklyShareSummary(emptyList(), 0, 0, now, zone)
        assertEquals(3, summary.lines.count { it.second == "Sem medição disponível" })
        assertFalse(summary.text.contains("72 bpm"))
        assertFalse(summary.text.contains("45 Mins"))
        assertFalse(summary.text.contains("Verificada"))
        assertTrue(summary.text.contains("09/09/2026 a 15/09/2026"))
    }

    @Test fun share_window_does_not_fall_back_to_old_data_or_include_future_readings() {
        val summary = weeklyShareSummary(listOf(metric(now - 10 * 86_400_000L), metric(now + 60_000)), 300, 120, now, zone)
        assertEquals("0", summary.lines.toMap()["Registros no período"])
        assertEquals("300 mL", summary.lines.toMap()["Água registrada hoje"])
        assertEquals("2 min 0 s", summary.lines.toMap()["Respiração — total salvo no aplicativo"])
    }

    @Test fun share_uses_positive_readings_and_does_not_add_snapshot_counters() {
        val summary = weeklyShareSummary(listOf(metric(now), metric(now - 1000).copy(heartRate = 0, steps = 250)), 0, 0, now, zone)
        assertEquals("60 bpm", summary.lines.toMap()["Média dos batimentos registrados"])
        assertEquals("250", summary.lines.toMap()["Passos — maior valor salvo no período"])
    }

    @Test fun file_provider_sharing_contract_on_android_style_paths() {
        // AndroidX FileProvider 1.18.0 matches rootPath + '/', which does not match
        // Windows canonical paths in Robolectric. Do not replace the real provider.
        assumeTrue("FileProvider requires Android-style filesystem paths", File.separatorChar == '/')
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = ProgressImageGenerator.generateWeeklyProgressImage(context, emptyList(), 0, 0)
        assertEquals("${context.packageName}.fileprovider", result.uri.authority)
        assertTrue(result.file.exists())
    }

    @Test fun empty_card_renderer_is_verified_without_claiming_android_sharing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = ProgressImageGenerator.renderWeeklyProgressBitmap(context, weeklyShareSummary(emptyList(), 0, 0, now, zone))
        assertEquals(1080, bitmap.width)
        assertEquals(1350, bitmap.height)
        val output = File("build/patient-ui/share_empty.png")
        requireNotNull(output.parentFile).mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun image_provider_failure_leaves_no_new_export_and_preserves_previous_files() {
        val directory = temporary.newFolder()
        val previous = File(directory, "previous.png").apply { writeText("earlier snapshot") }
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = object : ContextWrapper(base) {
            override fun getCacheDir(): File = directory
            override fun getPackageName(): String = "synthetic.missing.provider"
        }
        repeat(2) {
            assertThrows(IllegalArgumentException::class.java) {
                ProgressImageGenerator.generateWeeklyProgressImage(context, emptyList(), 0, 0)
            }
            assertEquals(listOf(previous), directory.listFiles()!!.toList())
            assertEquals("earlier snapshot", previous.readText())
        }
    }

    private fun metric(at: Long) = HBandSensorMetricEntity(
        deviceId = "test-device", timestamp = "test", timestampMillis = at,
        heartRate = 60, systolicBp = 0, diastolicBp = 0, spO2 = 0,
        temperatureCelsius = 0f, steps = 100, calories = 0f, distanceMeters = 0f,
        hrvScore = 0, deepSleepMinutes = 0, lightSleepMinutes = 0, awakeMinutes = 0,
    )
}
