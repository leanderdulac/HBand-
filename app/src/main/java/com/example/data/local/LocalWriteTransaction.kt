package com.example.data.local

import androidx.room.RoomDatabase
import androidx.room.withTransaction

/** Required boundary for related local writes. Never include network operations in the block. */
fun interface LocalWriteTransaction {
    suspend fun run(block: suspend () -> Unit)
}

fun RoomDatabase.localWriteTransaction(): LocalWriteTransaction = LocalWriteTransaction { block ->
    withTransaction { block() }
}
