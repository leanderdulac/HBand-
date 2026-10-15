package com.next2u.healthsync.shared.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSUUID

internal actual fun randomUuid(): String = NSUUID().UUIDString().lowercase()

@OptIn(ExperimentalForeignApi::class)
internal actual fun sha256Hex(text: String): String {
    val data = text.encodeToByteArray()
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    data.usePinned { input ->
        digest.usePinned { output ->
            CC_SHA256(input.addressOf(0), data.size.toUInt(), output.addressOf(0))
        }
    }
    return digest.joinToString("") { it.toString(16).padStart(2, '0') }
}
