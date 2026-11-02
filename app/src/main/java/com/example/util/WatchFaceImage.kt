package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.data.hband.WatchFace
import java.io.File

/** Recorta (quadrado central) e reduz a foto escolhida; devolve o caminho do PNG ou null. */
internal object WatchFaceImage {
    fun prepare(context: Context, uri: Uri): String? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= WatchFace.MAX_SIDE_PX) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val (left, top, side) = WatchFace.centerSquare(decoded.width, decoded.height).let { Triple(it[0], it[1], it[2]) }
        val square = Bitmap.createBitmap(decoded, left, top, side, side)
        val target = minOf(side, WatchFace.MAX_SIDE_PX)
        val scaled = if (side == target) square else Bitmap.createScaledBitmap(square, target, target, true)
        val dir = File(context.filesDir, "watchface").apply { mkdirs() }
        val out = File(dir, "dial.png")
        out.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        out.absolutePath
    }.getOrNull()
}
