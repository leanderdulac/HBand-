package com.next2u.healthsync.shared.ingest

/**
 * `X-App-Version` for Core requests.
 *
 * iOS shape is `ios-<marketingVersion>+<build>` (CFBundleShortVersionString +
 * CFBundleVersion). The build is required: a value without `+<build>` is not
 * enough for the Ktor client, which then uses the bundle header instead.
 * The header is not a JSON field, so it does not change the ingest schema.
 */
object AppVersionHeader {
    const val NAME = "X-App-Version"

    fun ios(marketingVersion: String, build: String): String? {
        val marketing = token(marketingVersion) ?: return null
        val number = token(build) ?: return null
        if (marketing.startsWith("$(") || number.startsWith("$(")) return null
        return "ios-$marketing+$number"
    }

    /**
     * Prefer an explicit header that already includes `+<build>`.
     * Otherwise use the platform header, which on iOS is read from the app bundle
     * so the Ktor path cannot drop the build.
     */
    fun choose(explicit: String?, platform: String?): String? {
        val cleanExplicit = token(explicit.orEmpty())
        if (cleanExplicit != null && hasBuild(cleanExplicit)) return cleanExplicit
        val cleanPlatform = token(platform.orEmpty())
        if (cleanPlatform != null && hasBuild(cleanPlatform)) return cleanPlatform
        return cleanExplicit
    }

    fun hasBuild(value: String): Boolean {
        val build = value.substringAfter('+', "")
        return '+' in value && build.isNotEmpty() && !build.startsWith("$(")
    }

    private fun token(raw: String): String? =
        raw.trim().filter { it.code in 0x21..0x7E }.take(64).takeIf { it.isNotEmpty() }
}

/** iOS: `ios-<CFBundleShortVersionString>+<CFBundleVersion>`. JVM: none. */
internal expect fun platformAppVersionHeader(): String?
