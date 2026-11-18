package com.next2u.healthsync.shared.ingest

import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal actual object PlatformQueueStore {
    private val path: String
        get() {
            val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            val dir = dirs.first() as String
            return "$dir/next2u-healthsync-queue.json"
        }

    actual fun loadJson(): String =
        NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) ?: "[]"

    actual fun saveJson(json: String) {
        (json as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }
}
