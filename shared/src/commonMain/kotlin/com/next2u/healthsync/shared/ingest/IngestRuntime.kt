package com.next2u.healthsync.shared.ingest

import com.next2u.healthsync.shared.model.BloodPressure
import com.next2u.healthsync.shared.model.HBandTelemetry
import com.next2u.healthsync.shared.model.QueuedReading
import com.next2u.healthsync.shared.model.SleepSummary
import com.next2u.healthsync.shared.platform.isoUtcNow
import com.next2u.healthsync.shared.store.PatientStore
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface QueueStore {
    fun loadJson(): String
    fun saveJson(json: String)
}

object FileQueueStore : QueueStore {
    override fun loadJson(): String = PlatformQueueStore.loadJson()
    override fun saveJson(json: String) = PlatformQueueStore.saveJson(json)
}

class MemoryQueueStore(initial: String = "[]") : QueueStore {
    private var contents = initial
    override fun loadJson(): String = contents
    override fun saveJson(json: String) {
        contents = json
    }
}

@Serializable
data class StoredQueueItem(
    val clientReadingId: String,
    val payloadJson: String,
    val status: String = "PENDING",
)

data class RecordOutcome(
    val queued: Boolean,
    val clientReadingId: String? = null,
    val message: String,
)

data class FlushOutcome(
    val syncedCount: Int = 0,
    val failedCount: Int = 0,
    val pendingCount: Int = 0,
    val lastHttpCode: Int = 0,
    val message: String,
)

/**
 * Host-facing ingest API for Android (later) and iOS Swift.
 * Mirrors Android: persist first, durable `client_reading_id`, batch ≤ 50,
 * selective receipt. Does not invent vitals.
 */
object IngestRuntime {
    const val DEFAULT_PASSWORD = "0000"

    internal var store: QueueStore = FileQueueStore
    internal var engineOverride: HttpClientEngine? = null
    private val mutex = Mutex()
    private var deduper = IngestDeduper()
    private val json = Json { ignoreUnknownKeys = true }
    private var configuration: IngestConfiguration =
        IngestConfiguration.resolve(IngestConfiguration.DEFAULT_BASE_URL, null, "")

    val lastHttpCode: Int get() = _lastHttpCode
    private var _lastHttpCode: Int = 0

    fun configure(baseUrl: String, overrideKey: String?, builtInKey: String) {
        configuration = IngestConfiguration.resolve(baseUrl, overrideKey, builtInKey)
    }

    fun configurationError(): String? = configuration.status.configurationError

    fun pendingCount(): Int = load().count { it.status == "PENDING" }

    fun useStore(next: QueueStore) {
        store = next
        deduper = IngestDeduper()
        _lastHttpCode = 0
    }

    fun useEngine(engine: HttpClientEngine?) {
        engineOverride = engine
    }

    suspend fun recordLive(
        deviceId: String,
        deviceModel: String,
        heartRate: Int,
        spo2: Int,
        systolic: Int,
        diastolic: Int,
        temperatureCelsius: Float,
        steps: Int,
        calories: Float,
        distanceMeters: Float,
        hrvScore: Int,
        patientId: String,
    ): RecordOutcome {
        val telemetry = HBandTelemetry(
            deviceId = deviceId,
            deviceModel = deviceModel,
            timestamp = isoUtcNow(),
            heartRate = heartRate,
            bloodPressure = BloodPressure(systolic, diastolic),
            spO2 = spo2,
            temperatureCelsius = temperatureCelsius,
            steps = steps,
            calories = calories,
            distanceMeters = distanceMeters,
            hrvScore = hrvScore,
            sleepSummary = SleepSummary(),
            isRealSensorData = true,
        )
        return record(telemetry, patientId)
    }

    suspend fun record(telemetry: HBandTelemetry, patientId: String): RecordOutcome = mutex.withLock {
        if (!telemetry.isRealSensorData) {
            return RecordOutcome(false, message = "Somente leituras reais do relógio entram na fila.")
        }
        PatientStore.tryAppendMetric(telemetry)
        if (!IngestPayload.isIngestible(telemetry)) {
            return RecordOutcome(false, message = IngestPayload.MISSING_HR_ERROR)
        }
        if (!deduper.shouldEnqueue(telemetry)) {
            return RecordOutcome(false, message = "Leitura repetida no intervalo de 30s; gravada localmente, sem novo envio.")
        }
        val payload = IngestPayload.telemetryToJson(telemetry, patientId)
        val id = IngestReadingIdentity.forPayload(payload)
        val items = load().toMutableList()
        items += StoredQueueItem(clientReadingId = id, payloadJson = payload, status = "PENDING")
        persist(items)
        RecordOutcome(true, id, "Registro na fila com client_reading_id $id.")
    }

    suspend fun flush(): FlushOutcome = mutex.withLock {
        val transport = configuration.transport()
        if (transport.configurationError != null) {
            return FlushOutcome(message = transport.configurationError, pendingCount = pendingUnlocked())
        }
        val pending = load().filter { it.status == "PENDING" }
        if (pending.isEmpty()) {
            return FlushOutcome(message = "Fila vazia.", lastHttpCode = _lastHttpCode)
        }
        val prepared = mutableListOf<PreparedReading>()
        val kept = load().toMutableList()
        for (item in pending) {
            val reading = QueuedReading(item.clientReadingId, item.payloadJson)
            try {
                prepared += IngestReconciler.prepare(reading)
            } catch (_: Exception) {
                mark(kept, item.clientReadingId, "FAILED")
            }
        }
        persist(kept)
        if (prepared.isEmpty()) {
            return FlushOutcome(
                failedCount = pending.size,
                pendingCount = pendingUnlocked(),
                message = "Nenhum registro preparado para envio.",
            )
        }
        val client = IngestClient(
            IngestClient.ingestHttpClient(engineOverride ?: defaultIngestEngine()),
            transport,
        )
        var synced = 0
        var failed = 0
        var lastCode = 0
        for (chunk in IngestReconciler.chunks(prepared)) {
            val body = IngestReconciler.batchBody(chunk)
            val key = IngestReconciler.flushIdempotencyKey(chunk)
            val call = client.batchIngestWearableData(body, key)
            lastCode = call.statusCode
            _lastHttpCode = call.statusCode
            val decisions = IngestReconciler.batch(chunk, call.statusCode, call.body)
            val latest = load().toMutableList()
            chunk.forEachIndexed { index, reading ->
                when (decisions[index].outcome) {
                    IngestItemOutcome.ACCEPTED, IngestItemOutcome.DUPLICATE -> {
                        latest.removeAll { it.clientReadingId == reading.item.clientReadingId }
                        synced++
                    }
                    IngestItemOutcome.REJECTED, IngestItemOutcome.CLIENT_ERROR, IngestItemOutcome.AUTH -> {
                        mark(latest, reading.item.clientReadingId, "FAILED")
                        failed++
                    }
                    IngestItemOutcome.TRANSIENT -> { /* stay PENDING */ }
                }
            }
            persist(latest)
        }
        FlushOutcome(
            syncedCount = synced,
            failedCount = failed,
            pendingCount = pendingUnlocked(),
            lastHttpCode = lastCode,
            message = "Nesta tentativa: concluídos $synced, falha $failed, aguardando ${pendingUnlocked()}. HTTP $lastCode.",
        )
    }

    private fun load(): List<StoredQueueItem> = try {
        json.decodeFromString<List<StoredQueueItem>>(store.loadJson().ifBlank { "[]" })
    } catch (_: Exception) {
        emptyList()
    }

    private fun persist(items: List<StoredQueueItem>) {
        store.saveJson(json.encodeToString(items))
    }

    private fun mark(items: MutableList<StoredQueueItem>, id: String, status: String) {
        val index = items.indexOfFirst { it.clientReadingId == id }
        if (index >= 0) items[index] = items[index].copy(status = status)
    }

    private fun pendingUnlocked(): Int = load().count { it.status == "PENDING" }
}
