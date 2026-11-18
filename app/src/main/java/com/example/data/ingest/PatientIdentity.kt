package com.example.data.ingest

import com.example.data.local.UserProfileDao
import com.example.data.local.UserProfileEntity
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Locale

/**
 * Patient ID shown in Meu perfil and sent as `patient_id`. A new install starts with the
 * shared placeholder [IngestPayloadMapper.DEFAULT_PATIENT_ID]; as soon as a profile is saved
 * with a name it gets its own ID: "PAT-" + first name (no accents, A-Z0-9, max 12) + "-" +
 * 4 random uppercase hex digits, e.g. PAT-LEANDRO-7F3A. Generated once; later name edits
 * keep it, so the Core keeps one patient per phone.
 */
object PatientIdentity {
    const val SLUG_MAX = 12
    const val FALLBACK_SLUG = "PACIENTE"
    private val generated = Regex("^PAT-[A-Z0-9]{1,$SLUG_MAX}-[0-9A-F]{4}$")
    private val marks = Regex("\\p{Mn}+")
    private val random = SecureRandom()

    fun isDefault(id: String?): Boolean {
        val value = id?.trim().orEmpty()
        return value.isEmpty() || value.equals(IngestPayloadMapper.DEFAULT_PATIENT_ID, ignoreCase = true)
    }

    fun isGenerated(id: String?): Boolean = id != null && generated.matches(id)

    /** First name, accents removed, uppercase, A-Z0-9 only, at most [SLUG_MAX]; null if none. */
    fun slug(fullName: String?): String? = fullName.orEmpty().trim().split(Regex("\\s+"))
        .asSequence()
        .map { word ->
            Normalizer.normalize(word, Normalizer.Form.NFD).replace(marks, "")
                .uppercase(Locale.ROOT).filter { it in 'A'..'Z' || it in '0'..'9' }.take(SLUG_MAX)
        }
        .firstOrNull { it.isNotEmpty() }

    fun randomSuffix(): String = "%04X".format(Locale.ROOT, random.nextInt(0x10000))

    /** Null when there is no name yet: the ID only changes once the patient enters a name. */
    fun build(fullName: String?, suffix: String): String? {
        if (fullName.isNullOrBlank()) return null
        require(Regex("^[0-9A-F]{4}$").matches(suffix)) { "suffix" }
        return "PAT-${slug(fullName) ?: FALLBACK_SLUG}-$suffix"
    }

    /** Assign an ID only when it is still empty/default and a name exists; otherwise unchanged. */
    fun assignIfNeeded(profile: UserProfileEntity, suffix: () -> String = ::randomSuffix): UserProfileEntity {
        if (!isDefault(profile.patientId)) return profile
        val id = build(profile.fullName, suffix()) ?: return profile
        return profile.copy(patientId = id)
    }

    /** Existing installs: a saved profile with a name but the default ID gets its own on launch. */
    suspend fun migrate(dao: UserProfileDao, suffix: () -> String = ::randomSuffix): UserProfileEntity? {
        val saved = dao.getUserProfile() ?: return null
        val updated = assignIfNeeded(saved, suffix)
        if (updated != saved) dao.saveUserProfile(updated)
        return updated
    }

    /** Send-time patient: a queued reading still on the default ID takes the current one. */
    fun forSend(recorded: String, current: String?): String =
        if (isDefault(recorded) && current != null && !isDefault(current)) current.trim() else recorded
}
