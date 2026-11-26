package com.example.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Source-level guards: cleartext stays off and the Gemini key wiring stays in place. */
class CleartextPolicyTest {
    @Test fun android_blocks_cleartext_and_excludes_credential_prefs_from_backup() {
        val manifest = text("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
        assertTrue(manifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
        val network = text(
            "src/main/res/xml/network_security_config.xml",
            "app/src/main/res/xml/network_security_config.xml",
        )
        assertTrue(network.contains("cleartextTrafficPermitted=\"false\""))
        assertFalse(network.contains("cleartextTrafficPermitted=\"true\""))
        val backup = text("src/main/res/xml/backup_rules.xml", "app/src/main/res/xml/backup_rules.xml")
        assertTrue(backup.contains("hband_credentials.xml"))
        assertTrue(backup.contains("hband_sqlcipher.xml"))
    }

    @Test fun ios_ats_is_the_default_and_gemini_key_stays_wired() {
        val plist = text(
            "../iosApp/Next2UPaciente/Info.plist",
            "iosApp/Next2UPaciente/Info.plist",
        )
        val ats = plist.indexOf("NSAllowsArbitraryLoads")
        assertTrue(ats >= 0)
        assertTrue(plist.substring(ats, ats + 80).contains("<false/>"))
        assertFalse(plist.contains("NSExceptionDomains"))
        assertFalse(plist.contains("NSAllowsArbitraryLoadsForMedia"))
        assertTrue(plist.contains("<key>Next2UGeminiApiKey</key>"))
        assertTrue(plist.contains("<string>\$(NEXT2U_GEMINI_API_KEY)</string>"))
        assertTrue(plist.contains("<key>Next2UIngestApiKey</key>"))
        val analyzer = text(
            "src/main/java/com/example/data/remote/GeminiHealthAnalyzer.kt",
            "app/src/main/java/com/example/data/remote/GeminiHealthAnalyzer.kt",
        )
        assertTrue(analyzer.contains("GEMINI_API_KEY"))
        assertTrue(analyzer.contains("generativelanguage.googleapis.com"))
        val gradle = text("build.gradle.kts", "app/build.gradle.kts")
        assertTrue(gradle.contains("AI_INSIGHT_ENABLED"))
        assertTrue(gradle.contains("GEMINI_API_KEY"))
        val crypto = text(
            "../iosApp/Next2UPaciente/PatientCrypto.swift",
            "iosApp/Next2UPaciente/PatientCrypto.swift",
        )
        assertTrue(crypto.contains("completeUntilFirstUserAuthentication"))
        assertTrue(crypto.contains("kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly"))
    }

    private fun text(vararg paths: String): String {
        val file = paths.map { File(it) }.firstOrNull { it.isFile }
            ?: error("Arquivo não encontrado: ${paths.toList()} (cwd=${File(".").absolutePath})")
        return file.readText()
    }
}
