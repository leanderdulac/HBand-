package com.next2u.healthsync.shared.ingest

import java.io.File

internal actual object PlatformQueueStore {
    private val file: File
        get() = File(System.getProperty("java.io.tmpdir"), "next2u-healthsync-queue.json")

    actual fun loadJson(): String = if (file.exists()) file.readText() else "[]"

    actual fun saveJson(json: String) {
        file.writeText(json)
    }

    actual fun deletePlaintext(): Boolean = runCatching {
        !file.exists() || file.delete()
    }.getOrDefault(false)

    fun overrideForTests(contents: String) {
        file.writeText(contents)
    }
}
