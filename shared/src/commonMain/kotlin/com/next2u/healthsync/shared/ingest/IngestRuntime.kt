package com.next2u.healthsync.shared.ingest

import com.next2u.healthsync.shared.model.BloodPressure
import com.next2u.healthsync.shared.model.HBandTelemetry
import com.next2u.healthsync.shared.model.QueuedReading
import com.next2u.healthsync.shared.model.SleepSummary
import com.next2u.healthsync.shared.platform.isoUtcNow
import com.next2u.healthsync.shared.platform.nowMillis
import com.next2u.healthsync.shared.store.PatientStore
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext
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
    val queuedAtMillis: Long = 0,
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
    /** > 0 when automatic sends are paused by backoff; the host should flush again after this. */
    val retryInMs: Long = 0L,
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
    private val backoff = IngestBackoffWindow()
    /** Small flushes: a 503 mid-outage costs at most this many items of work. */
    const val FLUSH_CHUNK_ITEMS = 10
    private var deduper = IngestDeduper()
    private val json = Json { ignoreUnknownKeys = true }
    private var configuration: IngestConfiguration =
        IngestConfiguration.resolve(IngestConfiguration.DEFAULT_BASE_URL, null, "")

    val lastHttpCode: Int get() = _lastHttpCode
    private var _lastHttpCode: Int = 0

    /**
     * Nome deste aparelho (ex.: "iPhone de LFM"), enviado como `device_label` em cada leitura.
     * O Core aceita campos extras (extra="allow") e guarda em `extra`/`frame`; não entra na
     * identidade de deduplicação nem na assinatura da métrica.
     */
    var deviceLabel: String? = null
        private set

    fun setDeviceLabel(raw: String?) {
        deviceLabel = sanitizeDeviceLabel(raw)
    }

    /**
     * Identificação do paciente gerada no app (ex.: "PAT-LEANDRO-7F3A"). Leituras na fila com o
     * padrão antigo (PAT-HBAND-001) ou sem paciente saem com esta identificação no envio.
     */
    var patientId: String? = null
        private set

    fun setPatientId(raw: String?) {
        patientId = raw?.trim()?.takeIf { it.isNotEmpty() && it != IngestPayload.DEFAULT_PATIENT_ID }
    }

    fun sanitizeDeviceLabel(raw: String?): String? =
        raw?.trim()?.replace(Regex("\\s+"), " ")?.take(DEVICE_LABEL_MAX)?.takeIf { it.isNotEmpty() }

    const val DEVICE_LABEL_MAX = 64

    /** Credencial válida já aplicada ao store atual; null = ainda não houve configuração válida. */
    private var appliedKey: String? = null

    fun configure(baseUrl: String, overrideKey: String?, builtInKey: String) {
        configuration = IngestConfiguration.resolve(baseUrl, overrideKey, builtInKey)
        val transport = configuration.transport()
        if (transport.configurationError == null && transport.apiKey != appliedKey) {
            // Nova chave (ou primeira válida após abrir o app/atualizar): registros que falharam
            // por acesso (401/403) voltam para a fila em vez de ficarem presos como FAILED.
            appliedKey = transport.apiKey
            val items = load()
            if (items.any { it.status == "FAILED" }) {
                backoff.recordSuccess()
                persist(items.map { if (it.status == "FAILED") it.copy(status = "PENDING") else it })
            }
        }
    }

    /** Fila pendente + falhas: a mesma contagem em todas as telas. */
    fun waitingCount(): Int = load().count { it.status == "PENDING" || it.status == "FAILED" }

    fun configurationError(): String? = configuration.status.configurationError

    fun pendingCount(): Int = load().count { it.status == "PENDING" }

    fun failedCount(): Int = load().count { it.status == "FAILED" }

    fun items(): List<StoredQueueItem> = load()

    fun visibleItems(): List<StoredQueueItem> = load()
        .sortedWith(compareBy<StoredQueueItem> {
            when (it.status) { "FAILED" -> 0; "PENDING" -> 1; else -> 2 }
        }.thenByDescending { it.queuedAtMillis })
        .take(30)

    fun statusLabel(status: String): String = when (status) {
        "PENDING" -> "Aguardando envio"
        "FAILED" -> "Falha no envio"
        "SYNCED" -> "Concluído no aplicativo"
        else -> "Situação do envio indisponível"
    }

    fun retryFailed(clientReadingId: String) {
        backoff.recordSuccess() // explicit patient retry goes out now
        val items = load().toMutableList()
        mark(items, clientReadingId, "PENDING")
        persist(items)
    }

    fun retryAllFailed() {
        backoff.recordSuccess() // explicit patient retry goes out now
        val items = load().map { if (it.status == "FAILED") it.copy(status = "PENDING") else it }
        persist(items)
    }

    fun deleteItem(clientReadingId: String) {
        persist(load().filterNot { it.clientReadingId == clientReadingId })
    }

    fun clearAll() {
        persist(emptyList())
    }

    fun useStore(next: QueueStore) {
        backoff.recordSuccess()
        appliedKey = null
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

    /**
     * Watch history sample. Uses a deterministic `client_reading_id` so the Core
     * dedups reconnect/replay. Does not use the 30s live-reading window.
     */
    suspend fun recordHistorySample(
        deviceId: String,
        deviceModel: String,
        timestampIso: String,
        timestampMillis: Long,
        metricType: String,
        heartRate: Int,
        spo2: Int,
        systolic: Int,
        diastolic: Int,
        temperatureCelsius: Float,
        steps: Int,
        calories: Float,
        distanceMeters: Float,
        hrvScore: Int,
        deepSleepMinutes: Int,
        lightSleepMinutes: Int,
        awakeMinutes: Int,
        patientId: String,
    ): RecordOutcome {
        val telemetry = HBandTelemetry(
            deviceId = deviceId,
            deviceModel = deviceModel,
            timestamp = timestampIso,
            heartRate = heartRate,
            bloodPressure = BloodPressure(systolic, diastolic),
            spO2 = spo2,
            temperatureCelsius = temperatureCelsius,
            steps = steps,
            calories = calories,
            distanceMeters = distanceMeters,
            hrvScore = hrvScore,
            sleepSummary = SleepSummary(deepSleepMinutes, lightSleepMinutes, awakeMinutes),
            isRealSensorData = true,
        )
        val id = IngestReadingIdentity.historyId(deviceId, metricType, timestampMillis)
        return record(
            telemetry = telemetry,
            patientId = patientId,
            clientReadingId = id,
            skipLiveDeduper = true,
            observedAtMillis = timestampMillis,
        )
    }

    suspend fun record(telemetry: HBandTelemetry, patientId: String): RecordOutcome =
        record(telemetry, patientId, clientReadingId = null, skipLiveDeduper = false, observedAtMillis = 0L)

    private suspend fun record(
        telemetry: HBandTelemetry,
        patientId: String,
        clientReadingId: String?,
        skipLiveDeduper: Boolean,
        observedAtMillis: Long,
    ): RecordOutcome = mutex.withLock {
        if (!telemetry.isRealSensorData) {
            return RecordOutcome(false, message = "Somente leituras reais do relógio entram na fila.")
        }
        if (observedAtMillis > 0L) {
            PatientStore.tryAppendObservedMetric(telemetry, observedAtMillis)
        } else {
            PatientStore.tryAppendMetric(telemetry)
        }
        if (!IngestPayload.isIngestible(telemetry)) {
            return RecordOutcome(false, message = IngestPayload.MISSING_HR_ERROR)
        }
        if (!skipLiveDeduper && !deduper.shouldEnqueue(telemetry)) {
            return RecordOutcome(false, message = "Leitura repetida no intervalo de 30s; gravada localmente, sem novo envio.")
        }
        val base = IngestPayload.withDeviceLabel(IngestPayload.telemetryToJson(telemetry, patientId), deviceLabel)
        val requested = clientReadingId?.takeIf { IngestReadingIdentity.isValid(it) }
        val payload = if (requested == null) base else IngestPayload.withClientReadingId(base, requested)
        val id = IngestReadingIdentity.forPayload(payload)
        val items = load().toMutableList()
        if (items.any { it.clientReadingId == id }) {
            return RecordOutcome(false, id, "Já na fila com client_reading_id $id.")
        }
        items += StoredQueueItem(
            clientReadingId = id,
            payloadJson = payload,
            status = "PENDING",
            queuedAtMillis = nowMillis(),
        )
        persist(items)
        RecordOutcome(true, id, "Registro na fila com client_reading_id $id.")
    }

    suspend fun flush(): FlushOutcome = mutex.withLock {
        coroutineContext.ensureActive()
        val transport = configuration.transport()
        if (transport.configurationError != null) {
            return FlushOutcome(message = transport.configurationError, pendingCount = pendingUnlocked())
        }
        val pending = load().filter { it.status == "PENDING" }
        if (pending.isEmpty()) {
            return FlushOutcome(message = "Fila vazia.", lastHttpCode = _lastHttpCode)
        }
        val waitMs = backoff.remainingMs(nowMillis())
        if (waitMs > 0) {
            return FlushOutcome(
                pendingCount = pending.size,
                lastHttpCode = _lastHttpCode,
                message = "O serviço pediu para aguardar. Nova tentativa em ${(waitMs + 999) / 1000} s.",
                retryInMs = waitMs,
            )
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
        var transient = false
        var retryAfterMs: Long? = null
        for (chunk in IngestReconciler.chunks(prepared, FLUSH_CHUNK_ITEMS)) {
            coroutineContext.ensureActive()
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
                    IngestItemOutcome.TRANSIENT -> { transient = true /* stay PENDING */ }
                }
            }
            persist(latest)
            call.retryAfterMs?.let { retryAfterMs = maxOf(retryAfterMs ?: 0L, it) }
            // 503/429/network: the Core needs room; stop this flush and back off.
            if (decisions.any { it.outcome == IngestItemOutcome.TRANSIENT }) break
        }
        val retryInMs = if (transient) {
            backoff.recordTransientFailure(nowMillis(), retryAfterMs)
        } else {
            if (synced > 0) backoff.recordSuccess()
            0L
        }
        FlushOutcome(
            syncedCount = synced,
            failedCount = failed,
            pendingCount = pendingUnlocked(),
            lastHttpCode = lastCode,
            message = "Nesta tentativa: concluídos $synced, falha $failed, aguardando ${pendingUnlocked()}. HTTP $lastCode.",
            retryInMs = retryInMs,
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
