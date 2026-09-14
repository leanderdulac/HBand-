package com.example.data.local

import java.io.File

object SqliteFileInspector {
    private val SQLITE_HEADER = "SQLite format 3".toByteArray(Charsets.US_ASCII)

    fun looksLikeUnencryptedSqlite(file: File): Boolean {
        if (!file.exists() || file.length() < SQLITE_HEADER.size.toLong()) return false
        val header = ByteArray(SQLITE_HEADER.size)
        file.inputStream().use { stream ->
            val read = stream.read(header)
            if (read < SQLITE_HEADER.size) return false
        }
        return header.contentEquals(SQLITE_HEADER)
    }

    fun deleteSidecars(databaseFile: File) {
        databaseFile.delete()
        File(databaseFile.path + "-wal").delete()
        File(databaseFile.path + "-shm").delete()
        File(databaseFile.path + "-journal").delete()
    }
}
