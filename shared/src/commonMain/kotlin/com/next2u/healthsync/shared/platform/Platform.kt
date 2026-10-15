package com.next2u.healthsync.shared.platform

internal expect fun randomUuid(): String

internal expect fun sha256Hex(text: String): String
