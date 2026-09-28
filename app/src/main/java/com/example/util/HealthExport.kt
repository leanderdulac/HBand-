package com.example.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import java.io.File
import java.io.IOException
import java.io.OutputStream

/** Suppresses supported system clipboard previews, not access by the chosen paste target. */
internal fun copyHealthText(context: Context, label: String, text: String) {
    val clip = ClipData.newPlainText(label, text).apply {
        description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(clip)
}

/** Publishes only a closed, nonempty export. Failure cleans up only this new file. */
internal fun <T> prepareHealthExport(
    directory: File,
    prefix: String,
    suffix: String,
    write: (OutputStream) -> Unit,
    publish: (File) -> T,
): T {
    if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
        throw IOException("Export directory unavailable")
    }
    val file = File.createTempFile(prefix, suffix, directory)
    try {
        file.outputStream().use(write)
        if (file.length() == 0L) throw IOException("Export is empty")
        return publish(file)
    } catch (failure: Throwable) {
        // Preserve cancellation/failure and earlier exports, including files already shared.
        try {
            if (file.exists() && !file.delete()) failure.addSuppressed(IOException("Export cleanup failed"))
        } catch (cleanup: Exception) {
            failure.addSuppressed(cleanup)
        }
        throw failure
    }
}
