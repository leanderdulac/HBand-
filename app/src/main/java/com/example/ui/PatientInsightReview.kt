package com.example.ui

import com.example.data.local.HBandSensorMetricEntity
import com.example.data.remote.clinicalInsightRecords
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal data class InsightReviewState(val text: String = "", val isLoading: Boolean = false)

/** When the shown text was generated, and whether the last request failed (for the highlight card). */
internal data class InsightMeta(val generatedAtMillis: Long? = null, val failed: Boolean = false)

/**
 * AI_INSIGHT_ENABLED=false keeps the pilot guard (PR #6): notice only, manual review in DEBUG,
 * never automatic. AI_INSIGHT_ENABLED=true shows the summary and generates it automatically
 * in debug and release builds.
 */
internal data class InsightPolicy(val manualRequests: Boolean, val automatic: Boolean, val showInsight: Boolean)

internal fun insightPolicy(debug: Boolean, aiInsightEnabled: Boolean) = InsightPolicy(
    manualRequests = debug || aiInsightEnabled,
    automatic = aiInsightEnabled,
    showInsight = aiInsightEnabled,
)

internal const val AUTOMATIC_INSIGHT_DEBOUNCE_MS = 10_000L
internal const val AUTOMATIC_INSIGHT_MIN_INTERVAL_MS = 15 * 60_000L
private const val SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000L

/**
 * Changes when the clinical readings the summary uses change (same 7-day window and fallback as
 * GeminiHealthAnalyzer); null when there is nothing clinical to summarise.
 */
internal fun automaticInsightKey(records: List<HBandSensorMetricEntity>, nowMillis: Long): Pair<Int, Long>? {
    val clinical = clinicalInsightRecords(records)
    if (clinical.isEmpty()) return null
    val recent = clinical.filter { it.timestampMillis >= nowMillis - SEVEN_DAYS_MS }.ifEmpty { clinical }
    return recent.size to recent.maxOf { it.timestampMillis }
}

/**
 * Explicit review by default; construction never reads or transmits metrics unless [automatic]
 * is set. Automatic generation waits for readings to settle ([debounceMillis]), never overlaps a
 * pending request and starts at most once per [minIntervalMillis], always with the latest data.
 */
internal class PatientInsightReview(
    private val scope: CoroutineScope,
    private val enabled: Boolean,
    private val metrics: Flow<List<HBandSensorMetricEntity>>,
    automatic: Boolean = false,
    private val debounceMillis: Long = AUTOMATIC_INSIGHT_DEBOUNCE_MS,
    private val minIntervalMillis: Long = AUTOMATIC_INSIGHT_MIN_INTERVAL_MS,
    private val now: () -> Long = System::currentTimeMillis,
    private val generate: suspend (List<HBandSensorMetricEntity>) -> String,
) {
    private val lock = Any()
    private var generation = 0L
    private var job: Job? = null
    private var lastStartedAt: Long? = null
    private val current = MutableStateFlow(InsightReviewState())
    val state = current.asStateFlow()
    private val currentMeta = MutableStateFlow(InsightMeta())
    val meta = currentMeta.asStateFlow()

    init {
        if (enabled && automatic) scope.launch { observeAutomatically() }
    }

    fun request() = synchronized(lock) {
        if (!enabled || current.value.isLoading) return
        val requestedGeneration = ++generation
        lastStartedAt = now()
        current.value = current.value.copy(isLoading = true)
        job = scope.launch {
            try {
                val text = generate(metrics.first())
                synchronized(lock) {
                    if (generation == requestedGeneration) {
                        current.value = InsightReviewState(text, true)
                        currentMeta.value = InsightMeta(generatedAtMillis = now())
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                synchronized(lock) {
                    if (generation == requestedGeneration) {
                        current.value = InsightReviewState(
                            "Não foi possível gerar o texto para revisão. Tente novamente.", true)
                        currentMeta.value = InsightMeta(failed = true)
                    }
                }
            }
        }.also { pending ->
            pending.invokeOnCompletion {
                synchronized(lock) {
                    if (generation == requestedGeneration) current.value = current.value.copy(isLoading = false)
                }
            }
        }
    }

    fun clear() = synchronized(lock) {
        generation++
        job?.cancel()
        job = null
        current.value = InsightReviewState()
        currentMeta.value = InsightMeta()
    }

    private suspend fun observeAutomatically() {
        metrics
            .map { automaticInsightKey(it, now()) }
            .distinctUntilChanged()
            // Storage failure: no automatic generation from unknown data; manual path stays.
            .catch { }
            .collectLatest { key ->
                if (key == null) return@collectLatest
                delay(debounceMillis)
                current.first { !it.isLoading }
                val last = synchronized(lock) { lastStartedAt }
                if (last != null) delay(last + minIntervalMillis - now())
                current.first { !it.isLoading }
                request()
            }
    }
}
