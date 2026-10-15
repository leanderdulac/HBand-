package com.next2u.healthsync.shared.ingest

internal actual fun defaultElapsedMs(): Long = System.nanoTime() / 1_000_000L
