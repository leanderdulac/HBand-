package com.next2u.healthsync.shared.store

import com.next2u.healthsync.shared.ingest.FileQueueStore
import com.next2u.healthsync.shared.ingest.IngestRuntime
import com.next2u.healthsync.shared.ingest.QueueStore
import com.next2u.healthsync.shared.ingest.StoredQueueItem
import com.next2u.healthsync.shared.model.HBandTelemetry
import com.next2u.healthsync.shared.platform.nowMillis
import kotlinx.serialization.json.Json

/**
 * Encrypted local snapshot for metrics, ingest queue, profile and wellness.
 * Unknown schema or a failed decrypt leaves the file untouched.
 */
object PatientStore {
    const val SCHEMA_VERSION = 1
    const val HOOKS_MISSING = "Ganchos de cifra iOS ausentes. A abertura recusou o armazenamento."
    const val UNKNOWN_SCHEMA = "Versão de esquema desconhecida. Os registros não foram alterados."
    const val CORRUPT = "Não foi possível ler o arquivo cifrado. Os registros não foram alterados."
    const val WRITE_FAILED = "Não foi possível gravar o arquivo cifrado."

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private var codec: SnapshotCodec = defaultSnapshotCodec()
    private var snapshot: PatientSnapshot = PatientSnapshot()
    private var _status: StoreStatus = StoreStatus.OPENING
    private var _detail: String = ""

    val status: StoreStatus get() = _status
    val detail: String get() = _detail
    val isReady: Boolean get() = _status == StoreStatus.READY

    fun useCodec(next: SnapshotCodec) {
        codec = next
        snapshot = PatientSnapshot()
        _status = StoreStatus.OPENING
        _detail = ""
    }

    fun resetForTests(next: SnapshotCodec = MemorySnapshotCodec()) {
        useCodec(next)
        IngestRuntime.useStore(com.next2u.healthsync.shared.ingest.MemoryQueueStore())
    }

    fun open(): StoreStatus {
        if (_status == StoreStatus.READY) return StoreStatus.READY
        if (_status == StoreStatus.FAILED) return StoreStatus.FAILED
        val read = try {
            codec.read()
        } catch (error: Exception) {
            return fail(CORRUPT, error.message)
        }
        if (read.failed) return fail(read.detail.ifBlank { CORRUPT })
        if (read.missing) return createFresh()
        val decoded = try {
            json.decodeFromString<PatientSnapshot>(read.plaintext)
        } catch (error: Exception) {
            return fail(CORRUPT, error.message)
        }
        if (decoded.schemaVersion != SCHEMA_VERSION) {
            return fail(UNKNOWN_SCHEMA, "schema=${decoded.schemaVersion}")
        }
        snapshot = decoded
        _status = StoreStatus.READY
        _detail = ""
        return StoreStatus.READY
    }

    fun attachQueue() {
        check(isReady) { "PatientStore precisa estar READY antes de ligar a fila." }
        IngestRuntime.useStore(PatientQueueBridge)
    }

    fun metrics(): List<SensorMetric> = snapshot.metrics.toList()
    fun queue(): List<StoredQueueItem> = snapshot.queue.toList()
    fun profile(): UserProfile = snapshot.profile
    fun hydration(): List<HydrationLog> = snapshot.hydration.toList()
    fun breathing(): List<BreathingSession> = snapshot.breathing.toList()
    fun metricCount(): Int = snapshot.metrics.size

    fun latestMetric(): SensorMetric? = snapshot.metrics.maxByOrNull { it.timestampMillis }

    fun localSummary(): String {
        val last = latestMetric() ?: return "Nenhuma medição gravada neste iPhone ainda."
        val bpm = if (last.heartRate > 0) "${last.heartRate} bpm" else "sem FC"
        return "${snapshot.metrics.size} medições gravadas neste iPhone. Última: $bpm (${last.timestamp})."
    }

    fun saveProfile(profile: UserProfile): Boolean {
        if (!isReady) return false
        val previous = snapshot
        snapshot = snapshot.copy(profile = profile)
        return commit(previous)
    }

    fun appendHydration(log: HydrationLog): Boolean {
        if (!isReady) return false
        val previous = snapshot
        val id = snapshot.nextHydrationId
        snapshot = snapshot.copy(
            hydration = snapshot.hydration + log.copy(id = id),
            nextHydrationId = id + 1,
        )
        return commit(previous)
    }

    fun appendBreathing(session: BreathingSession): Boolean {
        if (!isReady) return false
        val previous = snapshot
        val id = snapshot.nextBreathingId
        snapshot = snapshot.copy(
            breathing = snapshot.breathing + session.copy(id = id),
            nextBreathingId = id + 1,
        )
        return commit(previous)
    }

    /**
     * Persist every real watch reading. No-op if the store is not READY so
     * ingest tests keep working without opening storage.
     */
    fun tryAppendMetric(telemetry: HBandTelemetry): Long {
        if (!isReady || !telemetry.isRealSensorData) return -1L
        val previous = snapshot
        val id = snapshot.nextMetricId
        val metric = telemetry.toMetric(id, nowMillis())
        snapshot = snapshot.copy(
            metrics = snapshot.metrics + metric,
            nextMetricId = id + 1,
        )
        return if (commit(previous)) id else -1L
    }

    internal fun loadQueueJson(): String = json.encodeToString(snapshot.queue)

    internal fun replaceQueueJson(raw: String) {
        if (!isReady) return
        val items = try {
            json.decodeFromString<List<StoredQueueItem>>(raw.ifBlank { "[]" })
        } catch (_: Exception) {
            return
        }
        val previous = snapshot
        snapshot = snapshot.copy(queue = items)
        commit(previous)
    }

    private fun createFresh(): StoreStatus {
        val imported = importLegacyQueue()
        snapshot = PatientSnapshot(
            schemaVersion = SCHEMA_VERSION,
            queue = imported,
            profile = UserProfile(),
        )
        return if (writeSnapshot()) {
            _status = StoreStatus.READY
            _detail = ""
            StoreStatus.READY
        } else {
            fail(WRITE_FAILED)
        }
    }

    private fun importLegacyQueue(): List<StoredQueueItem> = try {
        val raw = FileQueueStore.loadJson()
        if (raw.isBlank() || raw == "[]") emptyList()
        else json.decodeFromString<List<StoredQueueItem>>(raw)
    } catch (_: Exception) {
        emptyList()
    }

    private fun commit(previous: PatientSnapshot): Boolean {
        if (writeSnapshot()) return true
        snapshot = previous
        return false
    }

    private fun writeSnapshot(): Boolean = try {
        codec.write(json.encodeToString(PatientSnapshot.serializer(), snapshot))
    } catch (_: Exception) {
        false
    }

    private fun fail(message: String, extra: String? = null): StoreStatus {
        _status = StoreStatus.FAILED
        _detail = if (extra.isNullOrBlank()) message else "$message ($extra)"
        return StoreStatus.FAILED
    }
}

object PatientQueueBridge : QueueStore {
    override fun loadJson(): String = PatientStore.loadQueueJson()
    override fun saveJson(json: String) = PatientStore.replaceQueueJson(json)
}
