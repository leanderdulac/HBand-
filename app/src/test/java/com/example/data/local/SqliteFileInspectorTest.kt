package com.example.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SqliteFileInspectorTest {

    @Test
    fun `standard sqlite header is detected as unencrypted`() {
        val file = File.createTempFile("plain", ".db")
        file.writeBytes("SQLite format 3\u0000 extra".toByteArray(Charsets.ISO_8859_1))
        assertTrue(SqliteFileInspector.looksLikeUnencryptedSqlite(file))
        file.delete()
    }

    @Test
    fun `sqlcipher ciphertext is not treated as a plaintext sqlite file`() {
        val file = File.createTempFile("enc", ".db")
        file.writeBytes(ByteArray(32) { 0x7A })
        assertFalse(SqliteFileInspector.looksLikeUnencryptedSqlite(file))
        file.delete()
    }
}
