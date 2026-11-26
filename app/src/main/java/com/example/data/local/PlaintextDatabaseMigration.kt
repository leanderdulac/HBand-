package com.example.data.local

import java.io.File

/** Reads and writes the encrypted side of a plaintext SQLite migration. */
internal interface EncryptedDatabaseCodec {
    fun export(source: File, destination: File, passphrase: ByteArray)
    fun readEncrypted(file: File, passphrase: ByteArray): DatabaseSnapshot
}

/**
 * Replaces a plaintext SQLite file with an encrypted copy only after the row
 * snapshot matches. A failed attempt leaves the original file in place.
 * Already-encrypted files (no SQLite header) are not rewritten.
 */
internal object PlaintextDatabaseMigration {
    const val PRESERVED_SUFFIX = ".plaintext-preserved"
    const val RETIRED_SUFFIX = ".plaintext-retired"
    private const val TEMP_SUFFIX = ".cipher-tmp"

    fun migrateIfPlaintext(databaseFile: File, passphrase: ByteArray, codec: EncryptedDatabaseCodec): Boolean {
        if (!databaseFile.exists() || !SqliteFileInspector.looksLikeUnencryptedSqlite(databaseFile)) return false
        val preserved = File(databaseFile.path + PRESERVED_SUFFIX)
        val temp = File(databaseFile.path + TEMP_SUFFIX)
        val retired = File(databaseFile.path + RETIRED_SUFFIX)
        try {
            AndroidPlaintextSnapshots.checkpoint(databaseFile)
            val before = AndroidPlaintextSnapshots.read(databaseFile)
            // FULL checkpoint has already folded the WAL into the main file.
            // Dropping sidecars now means a crash cannot leave a plaintext WAL
            // beside the encrypted file Room opens next.
            deleteSidecars(databaseFile)
            databaseFile.copyTo(preserved, overwrite = true)
            if (temp.exists() && !temp.delete()) error("Arquivo temporário da cifra não pôde ser recriado.")
            codec.export(databaseFile, temp, passphrase)
            if (!temp.isFile || SqliteFileInspector.looksLikeUnencryptedSqlite(temp)) {
                temp.delete()
                error("A cópia cifrada ainda parece um SQLite sem cifra.")
            }
            if (codec.readEncrypted(temp, passphrase) != before) {
                temp.delete()
                error("A cópia cifrada não confere com o banco em texto claro.")
            }
            if (retired.exists() && !retired.delete()) error("Cópia anterior da migração impede a troca.")
            if (!databaseFile.renameTo(retired)) {
                temp.delete()
                error("Não foi possível afastar o banco em texto claro.")
            }
            if (!temp.renameTo(databaseFile)) {
                retired.renameTo(databaseFile)
                temp.delete()
                error("Não foi possível colocar o banco cifrado no lugar.")
            }
            val confirmed = try {
                codec.readEncrypted(databaseFile, passphrase)
            } catch (error: Exception) {
                restore(databaseFile, retired)
                throw error
            }
            if (confirmed != before) {
                restore(databaseFile, retired)
                error("A abertura do banco cifrado não confere com os registros originais.")
            }
            retired.delete()
            preserved.delete()
            deleteSidecars(databaseFile)
            return true
        } catch (error: Exception) {
            temp.delete()
            // The live file is still the plaintext database: the extra copy is redundant.
            if (SqliteFileInspector.looksLikeUnencryptedSqlite(databaseFile)) preserved.delete()
            if (error is MigrationFailed) throw error
            throw MigrationFailed()
        }
    }

    /** Drops leftover plaintext copies only after the encrypted file has opened. */
    fun discardRetiredPlaintext(databaseFile: File) {
        if (!databaseFile.exists() || SqliteFileInspector.looksLikeUnencryptedSqlite(databaseFile)) return
        File(databaseFile.path + PRESERVED_SUFFIX).delete()
        File(databaseFile.path + RETIRED_SUFFIX).delete()
        File(databaseFile.path + TEMP_SUFFIX).delete()
    }

    private fun restore(databaseFile: File, retired: File) {
        if (!retired.exists()) return
        if (databaseFile.exists()) {
            val rejected = File(databaseFile.path + ".cipher-rejected")
            if (!databaseFile.renameTo(rejected) && !databaseFile.delete()) return
            rejected.delete()
        }
        retired.renameTo(databaseFile)
    }

    private fun deleteSidecars(databaseFile: File) {
        listOf("-wal", "-shm", "-journal").forEach { suffix -> File(databaseFile.path + suffix).delete() }
    }
}

internal class MigrationFailed : IllegalStateException(
    "Não foi possível cifrar o banco local. Os arquivos originais foram mantidos.",
)
