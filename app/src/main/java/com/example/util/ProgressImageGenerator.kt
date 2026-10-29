package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.local.HBandSensorMetricEntity
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ShareProgressData(val bitmap: Bitmap, val file: File, val uri: Uri, val summaryText: String)

internal data class WeeklyShareSummary(val period: String, val lines: List<Pair<String, String>>) {
    val text: String get() = buildString {
        appendLine("next2u SAÚDE — registros neste celular")
        appendLine(period)
        lines.forEach { (label, value) -> appendLine("$label: $value") }
        append("Registros podem estar incompletos. Origem e recebimento pela equipe não verificados neste cartão.")
    }
}

internal fun weeklyShareSummary(
    metrics: List<HBandSensorMetricEntity>,
    hydrationMl: Int?,
    breathingSeconds: Int?,
    nowMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): WeeklyShareSummary {
    val start = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_YEAR, -6)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val recent = metrics.filter { it.timestampMillis > 0 && it.timestampMillis in start..nowMillis }
    val rates = recent.map { it.heartRate }.filter { it > 0 }
    val steps = recent.map { it.steps }.filter { it > 0 }.maxOrNull()
    val calories = recent.map { it.calories }.filter { it.isFinite() && it > 0f }.maxOrNull()
    val locale = Locale.forLanguageTag("pt-BR")
    val format = SimpleDateFormat("dd/MM/yyyy", locale).apply { this.timeZone = timeZone }
    return WeeklyShareSummary(
        period = "${format.format(Date(start))} a ${format.format(Date(nowMillis))}",
        lines = listOf(
            "Registros no período" to recent.size.toString(),
            "Média dos batimentos registrados" to (rates.takeIf { it.isNotEmpty() }?.average()?.toInt()?.let { "$it bpm" } ?: "Sem medição disponível"),
            "Passos — maior valor salvo no período" to (steps?.toString() ?: "Sem medição disponível"),
            "Calorias — maior valor salvo no período" to (calories?.let { String.format(locale, "%.0f kcal", it) } ?: "Sem medição disponível"),
            "Água registrada hoje" to (hydrationMl?.takeIf { it >= 0 }?.let { "$it mL" } ?: "Indisponível"),
            "Respiração — total salvo no aplicativo" to (breathingSeconds?.takeIf { it >= 0 }?.let { "${it / 60} min ${it % 60} s" } ?: "Indisponível"),
        ),
    )
}

object ProgressImageGenerator {
    fun generateWeeklyProgressImage(
        context: Context,
        metrics: List<HBandSensorMetricEntity>,
        hydrationMl: Int? = null,
        breathingSeconds: Int? = null,
    ): ShareProgressData {
        val summary = weeklyShareSummary(metrics, hydrationMl, breathingSeconds, System.currentTimeMillis())
        val bitmap = renderWeeklyProgressBitmap(context, summary)
        try {
            return prepareHealthExport(
                directory = context.cacheDir,
                prefix = "weekly_health_progress_",
                suffix = ".png",
                write = {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) {
                        throw IOException("Image compression failed")
                    }
                },
                publish = { file ->
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    ShareProgressData(bitmap, file, uri, summary.text)
                },
            )
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
    }

    internal fun renderWeeklyProgressBitmap(context: Context, summary: WeeklyShareSummary): Bitmap {
        val bitmap = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(248, 249, 255))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val logo = BitmapFactory.decodeResource(context.resources, R.drawable.next2u_saude_logo)
        if (logo != null) {
            val logoWidth = 240f
            val logoHeight = logoWidth * logo.height / logo.width
            canvas.drawBitmap(logo, null, RectF(64f, 48f, 64f + logoWidth, 48f + logoHeight), paint)
        }
        fun text(value: String, y: Float, size: Float, bold: Boolean = false, x: Float = 64f) {
            paint.color = Color.rgb(25, 28, 30)
            paint.typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            paint.textSize = size
            // Fit long labels within the fixed export width; the in-app text remains scalable.
            while (paint.measureText(value) > 952f && paint.textSize > 24f) paint.textSize -= 1f
            canvas.drawText(value, x, y, paint)
        }
        text("Registros neste celular", 190f, 48f, true)
        text(summary.period, 238f, 32f)
        text("Datas conforme o horário do celular", 278f, 28f)
        summary.lines.forEachIndexed { index, (label, value) ->
            val y = 315f + index * 135f
            paint.color = Color.WHITE
            canvas.drawRoundRect(RectF(48f, y, 1032f, y + 119f), 20f, 20f, paint)
            text(label, y + 40f, 30f)
            text(value, y + 89f, 40f, true)
        }
        text("Os registros podem estar incompletos.", 1175f, 30f)
        text("Origem e recebimento pela equipe não verificados", 1222f, 28f)
        text("neste cartão.", 1259f, 28f)
        return bitmap
    }
}
