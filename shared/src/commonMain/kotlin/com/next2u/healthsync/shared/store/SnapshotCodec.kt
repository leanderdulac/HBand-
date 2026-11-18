package com.next2u.healthsync.shared.store

/**
 * Result of reading the encrypted snapshot. A failed read must never be
 * followed by a write — that would wipe the patient's file.
 */
data class PlaintextRead(
    val missing: Boolean = false,
    val failed: Boolean = false,
    val plaintext: String = "",
    val detail: String = "",
)

interface SnapshotCodec {
    fun read(): PlaintextRead
    fun write(plaintext: String): Boolean
}

/** In-memory codec for tests. Does not encrypt. */
class MemorySnapshotCodec(initial: String? = null) : SnapshotCodec {
    var plaintext: String? = initial
        private set
    var writeCount: Int = 0
        private set

    override fun read(): PlaintextRead {
        val text = plaintext
        return if (text == null) PlaintextRead(missing = true)
        else PlaintextRead(plaintext = text)
    }

    override fun write(plaintext: String): Boolean {
        writeCount++
        this.plaintext = plaintext
        return true
    }
}

internal expect fun defaultSnapshotCodec(): SnapshotCodec
