package com.next2u.healthsync.shared.platform

import java.security.MessageDigest
import java.util.UUID

internal actual fun randomUuid(): String = UUID.randomUUID().toString()

internal actual fun sha256Hex(text: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(text.encodeToByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
