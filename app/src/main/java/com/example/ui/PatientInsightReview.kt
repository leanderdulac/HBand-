package com.example.ui

import com.example.data.local.HBandSensorMetricEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal data class InsightReviewState(val text: String = "", val isLoading: Boolean = false)

/** Explicit development review only; construction never reads or transmits metrics. */
internal class PatientInsightReview(
    private val scope: CoroutineScope,
    private val enabled: Boolean,
    private val metrics: Flow<List<HBandSensorMetricEntity>>,
    private val generate: suspend (List<HBandSensorMetricEntity>) -> String,
) {
    private val lock = Any()
    private var generation = 0L
    private var job: Job? = null
    private val current = MutableStateFlow(InsightReviewState())
    val state = current.asStateFlow()

    fun request() = synchronized(lock) {
        if (!enabled || current.value.isLoading) return
        val requestedGeneration = ++generation
        current.value = current.value.copy(isLoading = true)
        job = scope.launch {
            try {
                val text = generate(metrics.first())
                synchronized(lock) {
                    if (generation == requestedGeneration) current.value = InsightReviewState(text, true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                synchronized(lock) {
                    if (generation == requestedGeneration) current.value = InsightReviewState(
                        "Não foi possível gerar o texto para revisão. Tente novamente.", true)
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
    }
}
