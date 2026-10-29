package com.example.data.ingest

/** Local format/placeholder check only; a configured key is not proof of server authorization. */
object IngestApiKey {
    const val CONFIGURATION_ERROR = "O acesso ao serviço não está configurado para envio. Os registros continuam neste aparelho. Peça ajuda à equipe responsável."
    fun isUsable(key: String?): Boolean {
        val value = key?.trim().orEmpty()
        return value.isNotEmpty() && value.none { it <= ' ' || it >= '\u007f' } &&
            !value.equals("YOUR_HEALTHTECH_API_KEY_HERE", ignoreCase = true) &&
            !value.equals(IngestPayloadMapper.DEFAULT_PATIENT_ID, ignoreCase = true)
    }
}
