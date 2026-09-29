package com.example.data.ingest

import com.example.data.model.HBandTelemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** One collector owned by the BLE/application scope, independent of screen observers. */
fun CoroutineScope.recordLiveReadings(
    readings: Flow<HBandTelemetry?>,
    savingEnabled: () -> Boolean,
    save: suspend (HBandTelemetry) -> Unit,
    onFailure: (Exception) -> Unit,
): Job = launch {
    collectLiveReadings(readings, savingEnabled, save, onFailure)
}

/** A durable local save never waits for HTTP; signals carry no readings and may coalesce. */
fun CoroutineScope.recordLiveReadingsWithSync(
    readings: Flow<HBandTelemetry?>,
    savingEnabled: () -> Boolean,
    save: suspend (HBandTelemetry) -> Unit,
    sync: suspend () -> Unit,
    onFailure: (Exception) -> Unit,
    onSyncFailure: (Exception) -> Unit,
): Job = launch {
    val owner = this
    val stopping = AtomicBoolean(false)
    val requests = Channel<Unit>(Channel.CONFLATED)
    val processor = launch {
        try {
            for (request in requests) {
                try {
                    sync()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    onSyncFailure(error)
                }
            }
        } catch (cancelled: CancellationException) {
            // Do not leave an orphan collector after an independent processor cancellation.
            if (!stopping.get()) owner.cancel(cancelled)
            throw cancelled
        }
    }
    try {
        collectLiveReadings(readings, savingEnabled, {
            save(it)
            requests.trySend(Unit) // Only after commit; never suspends the local collector.
        }, onFailure)
    } finally {
        stopping.set(true)
        requests.cancel()
        processor.cancel()
    }
}

private suspend fun collectLiveReadings(
    readings: Flow<HBandTelemetry?>,
    savingEnabled: () -> Boolean,
    save: suspend (HBandTelemetry) -> Unit,
    onFailure: (Exception) -> Unit,
) {
    val deduper = IngestDeduper()
    readings.collect { reading ->
        if (reading != null && savingEnabled()) {
            try {
                deduper.saveIfNeeded(reading, save)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // A failed write must not silently stop all subsequent live recording.
                onFailure(error)
            }
        }
    }
}
