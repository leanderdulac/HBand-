package com.next2u.healthsync.shared.ingest

internal expect object PlatformQueueStore {
    fun loadJson(): String
    fun saveJson(json: String)
}
