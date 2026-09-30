package com.example.data.repository

import com.example.data.local.*
import com.example.data.model.HealthCheckResponse
import com.example.data.model.IngestResponse
import com.example.data.model.HBandTelemetry
import com.example.data.model.BloodPressure
import com.example.data.model.SleepSummary
import com.example.data.remote.HealthTechApiService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.io.IOException

/** In-memory fixtures and fake API only; never opens the app database or a network connection. */
class WearableQueueConcurrencyTest {
    // These fixtures have no database. Transaction rollback is tested with real Room separately.
    private fun repository(queue: IngestQueueDao, metrics: HBandSensorMetricDao, api: HealthTechApiService) =
        WearableRepository(queue, metrics, api, LocalWriteTransaction { block -> block() }, maxBatchItems = 1)

    @Test fun contradictory_duplicate_receipt_stays_pending_and_replays_same_payload_and_key() = runBlocking {
        for (status in listOf("accepted", "duplicate")) {
            val original = item().copy(clientReadingId = "synthetic-contradictory-$status")
            val queue = Queue(original)
            val bodies = mutableListOf<String>()
            val keys = mutableListOf<String?>()
            var consistent = false
            val api = object : HealthTechApiService by Api() {
                override suspend fun batchIngestWearableData(body: RequestBody, idempotencyKey: String?): Response<okhttp3.ResponseBody> {
                    val raw = okio.Buffer().also { body.writeTo(it) }.readUtf8()
                    bodies += raw; keys += idempotencyKey
                    val request = org.json.JSONObject(raw)
                    val row = request.getJSONArray("readings").getJSONObject(0)
                    val result = org.json.JSONObject().put("patient_id", request.getString("patient_id"))
                        .put("reading_id", "synthetic-stored-reading").put("ingest_status", status)
                        .put("duplicate", if (consistent) status == "duplicate" else status != "duplicate")
                    val receipt = org.json.JSONObject().put("index", 0)
                        .put("client_reading_id", row.getString("client_reading_id"))
                        .put("status", status).put("result", result)
                    return Response.success(org.json.JSONObject().put("patient_id", request.getString("patient_id"))
                        .put("results", org.json.JSONArray().put(receipt)).toString().toResponseBody())
                }
            }
            val repo = repository(queue, metrics, api)
            val first = repo.processQueueDetailed()
            assertEquals("$status contradiction cannot confirm synchronization", 0, first.syncedCount)
            assertTrue(first.hadTransientFailure)
            val pending = queue.rows[original.id]!!
            assertEquals("PENDING", pending.status)
            assertEquals(1, pending.retries)
            assertEquals(original.payloadJson, pending.payloadJson)
            assertEquals(original.clientReadingId, pending.clientReadingId)
            assertEquals(original.id, pending.id)
            consistent = true
            // Recreate only the repository; the fake durable rows survive for this local fixture.
            assertEquals(1, repository(queue, metrics, api).processQueueDetailed().syncedCount)
            assertEquals(2, bodies.size)
            assertEquals(bodies[0], bodies[1])
            assertNotNull(keys[0])
            assertEquals(keys[0], keys[1])
            assertEquals("SYNCED", queue.rows[original.id]!!.status)
            assertNull(queue.rows[original.id]!!.errorMessage)
        }
    }
    @Test fun manual_retry_transport_failure_preserves_auth_pause_for_new_readings() = runBlocking {
        assertTransientRetryKeepsAuthPause { throw IOException("Test connection interrupted") }
    }

    @Test fun manual_retry_server_failure_preserves_auth_pause_for_new_readings() = runBlocking {
        assertTransientRetryKeepsAuthPause { Response.error(503, "Test unavailable".toResponseBody()) }
    }

    @Test fun manual_retry_timeout_or_rate_limit_response_preserves_auth_pause() = runBlocking {
        for (code in listOf(408, 429)) {
            assertTransientRetryKeepsAuthPause(expectedStatus = "FAILED", transientFlag = false) {
                Response.error(code, "Test retry later".toResponseBody())
            }
        }
    }

    private suspend fun assertTransientRetryKeepsAuthPause(
        expectedStatus: String = "PENDING",
        transientFlag: Boolean = true,
        retry: suspend () -> Response<IngestResponse>,
    ) {
        for (code in listOf(401, 403)) {
            val queue = Queue(item())
            val api = Api().apply { ingest = { Response.error(code, "Test denied".toResponseBody()) } }
            val repo = repository(queue, RecordingMetrics(metrics), api)
            assertNotNull(repo.processQueueDetailed().authError)
            val authEvidence = queue.rows[1L]?.errorMessage
            api.ingest = retry
            val result = repo.retryAllFailed()
            assertEquals(transientFlag, result.hadTransientFailure)
            assertEquals(authEvidence, queue.rows[1L]?.errorMessage)
            assertEquals(1, queue.rows[1L]?.retries)
            assertEquals(expectedStatus, queue.rows[1L]?.status)
            assertFalse(repo.isSyncing.value)
            val restarted = repository(queue, RecordingMetrics(metrics), api)
            restarted.enqueueTelemetry(telemetry(), "TEST-PATIENT")
            assertNotNull(restarted.processQueueDetailed().authError)
            assertEquals(2, api.calls.get())
            // A later explicit retry may still recover and send the preserved queue.
            api.ingest = { Response.success(IngestResponse(ingest_status = "accepted")) }
            assertEquals(2, restarted.retryAllFailed().syncedCount)
            assertTrue(queue.rows.values.all { it.status == "SYNCED" && it.errorMessage == null })
        }
    }

    @Test fun automatic_auth_pause_does_not_materialize_the_queue_payloads() = runBlocking {
        val initial = item().copy(
            status = QueueStatus.FAILED.name,
            errorMessage = com.example.data.ingest.QueueAuthorization.UNAUTHORIZED_PREFIX,
        )
        val queue = object : IngestQueueDao by Queue(initial) {
            override suspend fun getAllItemsSync(): List<IngestQueueEntity> =
                error("Automatic pause must not load the full queue")
            override suspend fun getPendingItems(): List<IngestQueueEntity> =
                error("Blocked sends must not load pending payloads")
        }
        val api = Api()
        val recordings = RecordingMetrics(metrics)
        val repo = repository(queue, recordings, api)
        repo.enqueueTelemetry(telemetry(), "TEST-PATIENT")
        assertEquals(1, recordings.recorded.size)
        assertNotNull(repo.processQueueDetailed().authError)
        assertEquals(0, api.calls.get())
        assertFalse(repo.isSyncing.value)
    }

    @Test fun auth_failure_pauses_new_readings_and_separate_repository_instances() = runBlocking {
        for (code in listOf(401, 403)) {
            val queue = Queue(item())
            val api = Api().apply { ingest = { Response.error(code, "denied".toResponseBody()) } }
            val recordings = RecordingMetrics(metrics)
            val first = repository(queue, recordings, api).processQueueDetailed()
            assertNotNull(first.authError)
            val before = queue.rows[1L]
            val restarted = repository(queue, recordings, api)
            restarted.enqueueTelemetry(telemetry(), "TEST-PATIENT")
            assertEquals(1, recordings.recorded.size)
            assertEquals(2, queue.rows.size)
            assertEquals(before, queue.rows[1L])
            assertEquals(QueueStatus.PENDING.name, queue.rows[2L]?.status)
            assertNotNull(restarted.processQueueDetailed().authError)
            assertEquals(1, api.calls.get())
            assertFalse(restarted.isSyncing.value)
        }
    }

    @Test fun explicit_retry_after_auth_correction_sends_preserved_records() = runBlocking {
        val queue = Queue(item())
        val api = Api().apply { ingest = { Response.error(401, "denied".toResponseBody()) } }
        val repo = repository(queue, RecordingMetrics(metrics), api)
        repo.processQueueDetailed()
        repo.enqueueTelemetry(telemetry(), "TEST-PATIENT")
        api.ingest = { Response.success(IngestResponse(ingest_status = "accepted")) }
        assertEquals(2, repo.retryAllFailed().syncedCount)
        assertTrue(queue.rows.values.all { it.status == QueueStatus.SYNCED.name && it.errorMessage == null })
        repo.enqueueTelemetry(telemetry(), "TEST-PATIENT")
        assertEquals(4, api.calls.get())
    }

    @Test fun repeated_auth_failure_stops_manual_batch_then_keeps_automatic_sends_paused() = runBlocking {
        val queue = Queue(item())
        val api = Api().apply { ingest = { Response.error(401, "denied".toResponseBody()) } }
        val repo = repository(queue, RecordingMetrics(metrics), api)
        repo.processQueueDetailed()
        repeat(4) { repo.enqueueTelemetry(telemetry(), "TEST-PATIENT") }
        assertNotNull(repo.retryAllFailed().authError)
        repo.processQueueDetailed()
        assertEquals(2, api.calls.get())
        assertEquals(5, queue.rows.size)
    }

    @Test fun cancellation_during_auth_retry_preserves_pause_and_allows_another_explicit_retry() = runBlocking {
        val queue = Queue(item())
        val api = Api().apply { ingest = { Response.error(401, "denied".toResponseBody()) } }
        val repo = repository(queue, metrics, api)
        repo.processQueueDetailed()
        val started = CompletableDeferred<Unit>()
        api.ingest = { started.complete(Unit); awaitCancellation() }
        val retry = launch { repo.retryAllFailed() }
        started.await()
        retry.cancelAndJoin()
        assertNotNull(repository(queue, metrics, api).processQueueDetailed().authError)
        assertEquals(2, api.calls.get())
        api.ingest = { Response.success(IngestResponse(ingest_status = "accepted")) }
        assertEquals(1, repo.retryAllFailed().syncedCount)
    }

    @Test fun client_validation_error_does_not_pause_unrelated_pending_records() = runBlocking {
        val queue = Queue(item())
        val api = Api().apply { ingest = { Response.error(422, "invalid".toResponseBody()) } }
        val repo = repository(queue, RecordingMetrics(metrics), api)
        repo.processQueueDetailed()
        api.ingest = { Response.success(IngestResponse(ingest_status = "accepted")) }
        repo.enqueueTelemetry(telemetry(), "TEST-PATIENT")
        assertEquals(2, api.calls.get())
        assertEquals(QueueStatus.SYNCED.name, queue.rows[2L]?.status)
    }

    private fun item() = IngestQueueEntity(id = 1, payloadJson = """{
        "patient_id":"TEST-PATIENT", "device_id":"TEST-DEVICE",
        "timestamp":"2026-09-23T12:00:00Z", "heart_rate":72
    }""".trimIndent())

    private class Queue(initial: IngestQueueEntity) : IngestQueueDao {
        val rows = ConcurrentHashMap<Long, IngestQueueEntity>().apply { put(initial.id, initial) }
        val writes = AtomicInteger()
        var readFailure = false
        var beforeUpdate: (IngestQueueEntity) -> Unit = {}
        var afterUpdate: (IngestQueueEntity) -> Unit = {}
        var beforeInsert: () -> Unit = {}
        var beforeRemoval: suspend () -> Unit = {}
        override fun getAllItems() = flowOf(rows.values.toList())
        override suspend fun getAllItemsSync() = rows.values.toList()
        override suspend fun hasAuthorizationBlock(unauthorizedPrefix: String, forbiddenPrefix: String) =
            rows.values.any { row ->
                row.status in listOf("PENDING", "FAILED") &&
                    (row.errorMessage?.startsWith(unauthorizedPrefix) == true ||
                        row.errorMessage?.startsWith(forbiddenPrefix) == true)
            }
        override suspend fun getPendingItems(): List<IngestQueueEntity> {
            if (readFailure) throw IllegalStateException("Test database read failure")
            return rows.values.filter { it.status == QueueStatus.PENDING.name }.sortedBy { it.createdAt }
        }
        override suspend fun getFailedItems() = rows.values.filter { it.status == QueueStatus.FAILED.name }
        override suspend fun insertItem(item: IngestQueueEntity): Long {
            beforeInsert()
            val id = if (item.id == 0L) (rows.keys.maxOrNull() ?: 0L) + 1 else item.id
            rows[id] = item.copy(id = id)
            return id
        }
        override suspend fun updateItem(item: IngestQueueEntity) {
            writes.incrementAndGet()
            beforeUpdate(item)
            rows.computeIfPresent(item.id) { _, _ -> item } // Room @Update never reinserts a deleted row.
            afterUpdate(item)
        }
        override suspend fun deleteById(id: Long) { beforeRemoval(); rows.remove(id) }
        override suspend fun clearSyncedItems() { beforeRemoval(); rows.entries.removeIf { it.value.status == QueueStatus.SYNCED.name } }
        override suspend fun clearAll() { beforeRemoval(); rows.clear() }
        override fun getPendingCountFlow() = flowOf(rows.values.count { it.status == QueueStatus.PENDING.name })
    }

    private val metrics = object : HBandSensorMetricDao {
        override fun getAllMetrics() = flowOf(emptyList<HBandSensorMetricEntity>())
        override suspend fun getAllMetricsList() = emptyList<HBandSensorMetricEntity>()
        override suspend fun getHistoryWindow(deviceId: String, fromMillis: Long, toMillis: Long) =
            error("Unexpected history read")
        override fun getLatestMetric() = flowOf<HBandSensorMetricEntity?>(null)
        override fun getRecentMetrics(limit: Int) = getAllMetrics()
        override suspend fun insertMetric(metric: HBandSensorMetricEntity) = error("Unexpected metric write")
        override suspend fun insertMetrics(metrics: List<HBandSensorMetricEntity>) = error("Unexpected metric write")
        override suspend fun deleteMetricById(id: Long) = error("Unexpected metric delete")
        override suspend fun clearAllMetrics() = error("Unexpected metric delete")
    }

    private class Api : com.example.data.ingest.SingleOnlyTestApi() {
        val calls = AtomicInteger()
        var ingest: suspend () -> Response<IngestResponse> = { Response.success(IngestResponse(ingest_status = "accepted")) }
        var health: suspend () -> Response<HealthCheckResponse> = { Response.success(null) }
        override suspend fun ingestWearableData(body: RequestBody, idempotencyKey: String?): Response<IngestResponse> {
            calls.incrementAndGet()
            return com.example.data.ingest.withSyntheticReceipt(ingest(), body)
        }
        override suspend fun checkHealth() = health()
    }

    private val removals: List<suspend (WearableRepository) -> Any> = listOf(
        { it.deleteQueueItem(2L) }, { it.clearAllQueue() }, { it.clearSyncedItems() },
    )

    @Test fun removal_refuses_a_snapshot_already_owned_by_another_sender() = runBlocking {
        withTimeout(10000) {
            for (remove in removals) {
                val first = item()
                val second = first.copy(id = 2, clientReadingId = "synthetic-removal-second", createdAt = first.createdAt + 1)
                val queue = Queue(first).apply { rows[2L] = second }
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val api = Api().apply { ingest = { entered.complete(Unit); release.await(); Response.success(IngestResponse(ingest_status = "accepted")) } }
                val sender = repository(queue, metrics, api)
                val processing = async { sender.processQueueDetailed() }
                try {
                    entered.await()
                    val refused = remove(repository(queue, metrics, api))
                    assertEquals(second, queue.rows[2L])
                    assertEquals(false as Any, refused)
                } finally { release.complete(Unit) }
                assertEquals(2, processing.await().syncedCount)
                assertEquals(2, api.calls.get())
                assertEquals("SYNCED", queue.rows[2L]?.status)
                assertFalse(sender.isSyncing.value)
            }
        }
    }

    @Test fun removal_admitted_first_blocks_senders_and_other_removals_without_claiming_sync() = runBlocking {
        withTimeout(10000) {
            for ((index, remove) in removals.withIndex()) {
                val queue = Queue(item()).apply { rows[2L] = item().copy(id = 2, clientReadingId = "synthetic-removal-second") }
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                queue.beforeRemoval = { entered.complete(Unit); release.await() }
                val api = Api()
                val remover = repository(queue, metrics, api)
                val removal = async { remove(remover) }
                try {
                    entered.await()
                    val other = repository(queue, metrics, api)
                    assertFalse(remover.isSyncing.value)
                    assertEquals(0, other.processQueueDetailed().syncedCount)
                    assertEquals(0, api.calls.get())
                    assertEquals(false as Any, other.deleteQueueItem(1L))
                } finally { release.complete(Unit) }
                assertEquals(true as Any, removal.await())
                val expectedRemaining = listOf(1, 0, 2)[index]
                assertEquals(expectedRemaining, queue.rows.size)
                assertEquals(expectedRemaining, remover.processQueueDetailed().syncedCount)
                assertEquals(expectedRemaining, api.calls.get())
            }
        }
    }

    @Test fun removal_failure_or_cancellation_preserves_rows_and_releases_admission() = runBlocking {
        for (remove in removals) {
            for (failure in listOf(IllegalStateException("Synthetic storage unavailable"), CancellationException("Synthetic cancellation"))) {
                val first = item()
                val queue = Queue(first).apply { beforeRemoval = { throw failure } }
                val api = Api()
                val repo = repository(queue, metrics, api)
                val thrown = runCatching { remove(repo) }.exceptionOrNull()
                assertEquals(failure.javaClass, thrown?.javaClass)
                assertEquals(failure.message, thrown?.message)
                assertEquals(first, queue.rows[1L])
                assertFalse(repo.isSyncing.value)
                queue.beforeRemoval = {}
                assertEquals(1, repository(queue, metrics, api).processQueueDetailed().syncedCount)
            }
        }
    }

    @Test fun cancelling_a_suspended_removal_releases_admission_without_deleting() = runBlocking {
        withTimeout(10000) {
            for (remove in removals) {
                val first = item()
                val entered = CompletableDeferred<Unit>()
                val queue = Queue(first).apply { beforeRemoval = { entered.complete(Unit); awaitCancellation() } }
                val api = Api()
                val repo = repository(queue, metrics, api)
                val deletion = launch { remove(repo) }
                try {
                    entered.await()
                    assertEquals(0, repository(queue, metrics, api).processQueueDetailed().syncedCount)
                    assertEquals(0, api.calls.get())
                } finally { deletion.cancelAndJoin() }
                assertEquals(first, queue.rows[1L])
                assertFalse(repo.isSyncing.value)
                assertEquals(1, repository(queue, metrics, api).processQueueDetailed().syncedCount)
            }
        }
    }

    private class RecordingMetrics(delegate: HBandSensorMetricDao) : HBandSensorMetricDao by delegate {
        val recorded = mutableListOf<HBandSensorMetricEntity>()
        override suspend fun insertMetric(metric: HBandSensorMetricEntity): Long {
            recorded.add(metric)
            return recorded.size.toLong()
        }
    }

    private fun telemetry() = HBandTelemetry(
        deviceId = "TEST-DEVICE", deviceModel = "TEST-MODEL", timestamp = "2026-09-23T12:00:00Z",
        heartRate = 72, bloodPressure = BloodPressure(), spO2 = 0, temperatureCelsius = 0f,
        steps = 0, calories = 0f, distanceMeters = 0f, hrvScore = 0, sleepSummary = SleepSummary(),
    )

    @Test fun manual_capture_returns_its_success_without_processing_the_queue_again() = runBlocking {
        val queue = Queue(item()).apply { rows.clear() }
        val recordings = RecordingMetrics(metrics)
        val api = Api()
        val result = repository(queue, recordings, api)
            .enqueueAndProcessTelemetry(telemetry(), "TEST-PATIENT")
        assertEquals(1, recordings.recorded.size)
        assertEquals(1, queue.rows.size)
        assertEquals(1, api.calls.get())
        assertEquals(1, queue.writes.get())
        assertEquals(1, result.syncedCount)
        assertFalse(result.hasIncompleteItems)
        assertTrue(result.message.contains("Concluídos no aplicativo: 1"))
        val payload = org.json.JSONObject(queue.rows[1L]!!.payloadJson)
        assertEquals("TEST-PATIENT", payload.getString("patient_id"))
        assertEquals("TEST-DEVICE", payload.getString("device_id"))
    }

    @Test fun manual_capture_does_not_immediately_repeat_a_failed_network_attempt() = runBlocking {
        val queue = Queue(item()).apply { rows.clear() }
        val api = Api().apply { ingest = { throw IOException("Test offline") } }
        val result = repository(queue, RecordingMetrics(metrics), api)
            .enqueueAndProcessTelemetry(telemetry(), "TEST-PATIENT")
        assertEquals(1, api.calls.get())
        assertEquals(1, queue.writes.get())
        assertEquals(1, queue.rows[1L]!!.retries)
        assertEquals(QueueStatus.PENDING.name, queue.rows[1L]!!.status)
        assertEquals(1, result.pendingCount)
        assertTrue(result.hadTransientFailure)
    }

    @Test fun manual_capture_returns_auth_failure_instead_of_an_empty_second_attempt() = runBlocking {
        val queue = Queue(item()).apply { rows.clear() }
        val api = Api().apply { ingest = { Response.error(401, "Test unauthorized".toResponseBody()) } }
        val result = repository(queue, RecordingMetrics(metrics), api)
            .enqueueAndProcessTelemetry(telemetry(), "TEST-PATIENT")
        assertEquals(1, api.calls.get())
        assertEquals(1, result.failedCount)
        assertNotNull(result.authError)
        assertTrue(result.hasIncompleteItems)
        assertTrue(result.patientMessage().contains("Peça ajuda"))
    }

    @Test fun manual_capture_propagates_storage_failure_without_hidden_retry() = runBlocking {
        val storageError = IllegalStateException("Test result write unavailable")
        val queue = Queue(item()).apply { rows.clear(); beforeUpdate = { throw storageError } }
        val api = Api()
        val repo = repository(queue, RecordingMetrics(metrics), api)
        val failure = try {
            repo.enqueueAndProcessTelemetry(telemetry(), "TEST-PATIENT"); null
        } catch (error: Exception) { error }
        assertTrue(failure is QueuePersistenceException)
        assertSame(storageError, failure!!.cause)
        assertEquals(1, api.calls.get())
        assertEquals(1, queue.writes.get())
        assertEquals(QueueStatus.PENDING.name, queue.rows[1L]!!.status)
        assertEquals(0, queue.rows[1L]!!.retries)
        assertFalse(repo.isSyncing.value)
    }

    @Test fun manual_capture_cancellation_keeps_saved_record_available_for_later_processing() = runBlocking {
        val queue = Queue(item()).apply { rows.clear() }
        val entered = CompletableDeferred<Unit>()
        val api = Api().apply { ingest = { entered.complete(Unit); awaitCancellation() } }
        val repo = repository(queue, RecordingMetrics(metrics), api)
        val task = launch { repo.enqueueAndProcessTelemetry(telemetry(), "TEST-PATIENT") }
        withTimeout(5_000) { entered.await() }
        task.cancelAndJoin()
        assertEquals(1, api.calls.get())
        assertEquals(0, queue.writes.get())
        assertEquals(QueueStatus.PENDING.name, queue.rows[1L]!!.status)
        assertEquals(0, queue.rows[1L]!!.retries)
        assertFalse(repo.isSyncing.value)
    }

    @Test fun manual_capture_saves_before_health_check_and_preserves_record_when_that_check_is_cancelled() = runBlocking {
        val queue = Queue(item()).apply { rows.clear() }
        val entered = CompletableDeferred<Unit>()
        val api = Api().apply { health = { entered.complete(Unit); awaitCancellation() } }
        val repo = repository(queue, RecordingMetrics(metrics), api)
        val task = launch { repo.enqueueAndProcessTelemetry(telemetry(), "TEST-PATIENT") }
        withTimeout(5_000) { entered.await() }
        assertEquals(1, queue.rows.size)
        task.cancelAndJoin()
        assertEquals(0, api.calls.get())
        assertEquals(0, queue.writes.get())
        assertEquals(QueueStatus.PENDING.name, queue.rows[1L]!!.status)
        assertFalse(repo.isSyncing.value)
    }

    @Test fun manual_capture_rejects_invalid_reading_without_storing_or_sending_it() = runBlocking {
        val queue = Queue(item()).apply { rows.clear() }
        val recordings = RecordingMetrics(metrics)
        val api = Api()
        val failure = try {
            repository(queue, recordings, api)
                .enqueueAndProcessTelemetry(telemetry().copy(heartRate = 0), "TEST-PATIENT"); null
        } catch (error: Exception) { error }
        assertTrue(failure is IllegalArgumentException)
        assertTrue(recordings.recorded.isEmpty())
        assertTrue(queue.rows.isEmpty())
        assertEquals(0, api.calls.get())
    }

    @Test fun manual_capture_does_not_send_when_queue_insertion_fails() = runBlocking {
        val storageError = IllegalStateException("Test queue insertion unavailable")
        val queue = Queue(item()).apply { rows.clear(); beforeInsert = { throw storageError } }
        val api = Api()
        val failure = try {
            repository(queue, RecordingMetrics(metrics), api)
                .enqueueAndProcessTelemetry(telemetry(), "TEST-PATIENT"); null
        } catch (error: Exception) { error }
        assertTrue(failure is IllegalStateException)
        assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it === storageError })
        assertTrue(queue.rows.isEmpty())
        assertEquals(0, api.calls.get())
    }

    @Test fun automatic_enqueue_preserves_its_id_result_when_background_processing_fails() = runBlocking {
        val queue = Queue(item()).apply {
            rows.clear()
            beforeUpdate = { throw IllegalStateException("Test result write unavailable") }
        }
        val recordings = RecordingMetrics(metrics)
        val api = Api()
        val repo = repository(queue, recordings, api)
        assertEquals(1L, repo.enqueueTelemetry(telemetry(), "TEST-PATIENT"))
        assertEquals(1, recordings.recorded.size)
        assertEquals(1, api.calls.get())
        assertEquals(1, queue.rows.size)
        assertEquals(QueueStatus.PENDING.name, queue.rows[1L]!!.status)
        assertFalse(repo.isSyncing.value)
    }

    @Test fun separate_repository_instances_do_not_send_the_same_pending_item_concurrently() = runBlocking {
        val queue = Queue(item())
        val api = Api()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        api.ingest = { entered.complete(Unit); release.await(); Response.success(IngestResponse(ingest_status = "accepted")) }
        val foreground = repository(queue, metrics, api)
        val background = repository(queue, metrics, api)
        val first = async { foreground.processQueueDetailed() }
        try {
            withTimeout(5_000) { entered.await() }
            val second = withTimeout(1_000) { background.processQueueDetailed() }
            assertEquals(0, second.syncedCount)
            assertEquals(1, api.calls.get())
            assertTrue(background.isSyncing.value)
        } finally { release.complete(Unit) }
        assertEquals(1, first.await().syncedCount)
        assertEquals(1, queue.writes.get())
        assertFalse(foreground.isSyncing.value)
        assertFalse(background.isSyncing.value)
        assertEquals(0, background.processQueueDetailed().syncedCount)
        assertEquals(1, api.calls.get())
    }

    @Test fun cancellation_preserves_pending_record_and_releases_processing_for_retry() = runBlocking {
        val original = item().copy(retries = 4)
        val queue = Queue(original)
        val api = Api()
        val entered = CompletableDeferred<Unit>()
        api.ingest = { entered.complete(Unit); awaitCancellation() }
        val repo = repository(queue, metrics, api)
        val task = launch { repo.processQueueDetailed() }
        withTimeout(5_000) { entered.await() }
        task.cancelAndJoin()
        assertEquals(original, queue.rows[1L])
        assertEquals(0, queue.writes.get())
        assertNull(repo.lastSyncResult.value)
        assertFalse(repo.isSyncing.value)
        api.ingest = { Response.success(IngestResponse(ingest_status = "accepted")) }
        assertEquals(1, repository(queue, metrics, api).processQueueDetailed().syncedCount)
        assertEquals(QueueStatus.SYNCED.name, queue.rows[1L]!!.status)
    }

    @Test fun cancelled_health_check_does_not_replace_last_observed_server_state() = runBlocking {
        val api = Api()
        val repo = repository(Queue(item()), metrics, api)
        repo.checkApiHealth()
        val previous = repo.apiHealth.value
        val entered = CompletableDeferred<Unit>()
        api.health = { entered.complete(Unit); awaitCancellation() }
        val task = launch { repo.checkApiHealth() }
        withTimeout(5_000) { entered.await() }
        task.cancelAndJoin()
        assertEquals(previous, repo.apiHealth.value)
    }

    @Test fun database_read_failure_releases_the_shared_gate() = runBlocking {
        val queue = Queue(item())
        val api = Api()
        val repo = repository(queue, metrics, api)
        queue.readFailure = true
        try {
            repo.processQueueDetailed()
            fail("Expected database error")
        } catch (expected: IllegalStateException) {
            assertEquals("Test database read failure", expected.message)
        }
        assertFalse(repo.isSyncing.value)
        assertEquals(0, api.calls.get())
        queue.readFailure = false
        assertEquals(1, repository(queue, metrics, api).processQueueDetailed().syncedCount)
    }

    @Test fun real_network_failure_still_counts_a_retry_and_allows_later_success() = runBlocking {
        val queue = Queue(item())
        val api = Api()
        api.ingest = { throw IOException("Offline test transport") }
        val repo = repository(queue, metrics, api)
        assertTrue(repo.processQueueDetailed().hadTransientFailure)
        assertEquals(1, queue.rows[1L]!!.retries)
        assertEquals(QueueStatus.PENDING.name, queue.rows[1L]!!.status)
        assertFalse(repo.isSyncing.value)
        api.ingest = { Response.success(IngestResponse(ingest_status = "accepted")) }
        assertEquals(1, repo.processQueueDetailed().syncedCount)
        assertEquals(QueueStatus.SYNCED.name, queue.rows[1L]!!.status)
    }

    @Test fun auth_and_server_failures_keep_the_existing_retry_classification() = runBlocking {
        val queue = Queue(item().copy(retries = 4))
        val api = Api()
        val repo = repository(queue, metrics, api)
        api.ingest = { Response.error(401, "Unauthorized test response".toResponseBody()) }
        val auth = repo.processQueueDetailed()
        assertNotNull(auth.authError)
        assertFalse(auth.hadTransientFailure)
        assertEquals(4, queue.rows[1L]!!.retries)
        assertEquals(QueueStatus.FAILED.name, queue.rows[1L]!!.status)
        queue.rows[1L] = item().copy(retries = 4)
        api.ingest = { Response.error(503, "Unavailable test response".toResponseBody()) }
        val server = repo.processQueueDetailed()
        assertNull(server.authError)
        assertTrue(server.hadTransientFailure)
        assertEquals(1, server.failedCount)
        assertEquals(5, queue.rows[1L]!!.retries)
        assertEquals(QueueStatus.FAILED.name, queue.rows[1L]!!.status)
    }

    @Test fun cancellation_after_one_response_preserves_it_and_leaves_the_next_item_pending() = runBlocking {
        val first = item()
        val second = first.copy(id = 2, createdAt = first.createdAt + 1, retries = 4)
        val queue = Queue(first).apply { rows[2L] = second }
        val api = Api()
        val enteredSecond = CompletableDeferred<Unit>()
        api.ingest = {
            if (api.calls.get() == 1) Response.success(IngestResponse(ingest_status = "accepted"))
            else { enteredSecond.complete(Unit); awaitCancellation() }
        }
        val repo = repository(queue, metrics, api)
        val task = launch { repo.processQueueDetailed() }
        withTimeout(5_000) { enteredSecond.await() }
        task.cancelAndJoin()
        assertEquals(QueueStatus.SYNCED.name, queue.rows[1L]!!.status)
        assertEquals(second, queue.rows[2L])
        assertEquals(1, queue.writes.get())
        assertFalse(repo.isSyncing.value)
    }

    @Test fun retry_selected_failed_item_starts_sending_without_waiting_for_periodic_work() = runBlocking {
        val selected = item().copy(status = QueueStatus.FAILED.name, retries = 5, errorMessage = "Offline")
        val untouched = selected.copy(id = 2)
        val queue = Queue(selected).apply { rows[2L] = untouched }
        val api = Api()
        val result = repository(queue, metrics, api).retryFailedItem(selected.id)
        assertEquals(1, result.syncedCount)
        assertEquals(1, api.calls.get())
        assertEquals(QueueStatus.SYNCED.name, queue.rows[1L]!!.status)
        assertEquals(0, queue.rows[1L]!!.retries)
        assertEquals(untouched, queue.rows[2L])
    }

    @Test fun retry_all_failed_items_starts_sending_and_preserves_completed_items() = runBlocking {
        val failed = item().copy(status = QueueStatus.FAILED.name, retries = 5)
        val completed = failed.copy(id = 3, status = QueueStatus.SYNCED.name)
        val queue = Queue(failed).apply { rows[2L] = failed.copy(id = 2); rows[3L] = completed }
        val api = Api()
        val result = repository(queue, metrics, api).retryAllFailed()
        assertEquals(2, result.syncedCount)
        assertEquals(2, api.calls.get())
        assertEquals(QueueStatus.SYNCED.name, queue.rows[1L]!!.status)
        assertEquals(QueueStatus.SYNCED.name, queue.rows[2L]!!.status)
        assertEquals(completed, queue.rows[3L])
    }

    @Test fun retry_during_another_processor_does_not_reset_failed_records_or_send_in_parallel() = runBlocking {
        val failed = item().copy(id = 2, status = QueueStatus.FAILED.name, retries = 5, errorMessage = "Offline")
        val queue = Queue(item()).apply { rows[2L] = failed }
        val api = Api()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        api.ingest = { entered.complete(Unit); release.await(); Response.success(IngestResponse(ingest_status = "accepted")) }
        val first = async { repository(queue, metrics, api).processQueueDetailed() }
        val repo = repository(queue, metrics, api)
        try {
            withTimeout(5_000) { entered.await() }
            val busy = withTimeout(5_000) { repo.retryAllFailed() }
            assertEquals(0, busy.syncedCount)
            assertTrue(busy.message.contains("andamento"))
            assertEquals(failed, queue.rows[2L])
            assertEquals(1, api.calls.get())
        } finally { release.complete(Unit) }
        first.await()
        assertEquals(1, repo.retryAllFailed().syncedCount)
        assertEquals(2, api.calls.get())
    }

    @Test fun obsolete_retry_does_not_send_completed_missing_or_unrelated_pending_records() = runBlocking {
        val completed = item().copy(status = QueueStatus.SYNCED.name)
        val pending = item().copy(id = 2)
        val queue = Queue(completed).apply { rows[2L] = pending }
        val api = Api()
        val repo = repository(queue, metrics, api)
        for (id in listOf(1L, 99L)) {
            val result = repo.retryFailedItem(id)
            assertEquals(0, result.syncedCount)
            assertTrue(result.message.contains("Nenhum registro com falha"))
        }
        assertEquals(0, repo.retryAllFailed().syncedCount)
        assertEquals(0, api.calls.get())
        assertEquals(completed, queue.rows[1L])
        assertEquals(pending, queue.rows[2L])
        assertEquals(0, queue.writes.get())
        assertFalse(repo.isSyncing.value)
    }

    @Test fun retry_transport_failure_reports_pending_instead_of_success() = runBlocking {
        val queue = Queue(item().copy(status = QueueStatus.FAILED.name, retries = 5))
        val api = Api().apply { ingest = { throw IOException("Offline test transport") } }
        val result = repository(queue, metrics, api).retryAllFailed()
        assertEquals(1, api.calls.get())
        assertEquals(0, result.syncedCount)
        assertTrue(result.hadTransientFailure)
        assertEquals(QueueStatus.PENDING.name, queue.rows[1L]!!.status)
        assertEquals(1, queue.rows[1L]!!.retries)
    }

    @Test fun cancelling_retry_leaves_the_requeued_record_available_for_later_processing() = runBlocking {
        val original = item().copy(status = QueueStatus.FAILED.name, retries = 5, errorMessage = "Old error")
        val queue = Queue(original)
        val api = Api()
        val entered = CompletableDeferred<Unit>()
        api.ingest = { entered.complete(Unit); awaitCancellation() }
        val repo = repository(queue, metrics, api)
        val task = launch { repo.retryFailedItem(original.id) }
        withTimeout(5_000) { entered.await() }
        task.cancelAndJoin()
        assertEquals(original.copy(status = QueueStatus.PENDING.name, retries = 0, errorMessage = null), queue.rows[1L])
        assertFalse(repo.isSyncing.value)
        api.ingest = { Response.success(IngestResponse(ingest_status = "accepted")) }
        assertEquals(1, repo.processQueueDetailed().syncedCount)
    }

    @Test fun partial_success_message_keeps_the_pending_item_visible() = runBlocking {
        val first = item()
        val queue = Queue(first).apply { rows[2L] = first.copy(id = 2, createdAt = first.createdAt + 1) }
        val api = Api()
        api.ingest = {
            if (api.calls.get() == 1) Response.success(IngestResponse(ingest_status = "accepted"))
            else throw IOException("Offline after first response")
        }
        val repo = repository(queue, metrics, api)
        val result = repo.processQueueDetailed()
        assertEquals(1, result.syncedCount)
        assertEquals(0, result.failedCount)
        assertEquals(1, result.pendingCount)
        assertTrue(result.hasIncompleteItems)
        assertTrue(result.message.contains("Aguardando envio: 1"))
        assertEquals(result.message, result.patientMessage())
        assertEquals(result.message, repo.lastSyncResult.value)
    }

    @Test fun permanent_failure_message_does_not_describe_the_record_as_waiting_for_connectivity() = runBlocking {
        val queue = Queue(item())
        val api = Api().apply { ingest = { Response.error(422, "Invalid test payload".toResponseBody()) } }
        val result = repository(queue, metrics, api).processQueueDetailed()
        assertEquals(1, result.failedCount)
        assertEquals(0, result.pendingCount)
        assertTrue(result.hasIncompleteItems)
        assertTrue(result.message.contains("Com falha: 1"))
        assertTrue(result.message.contains("Aguardando envio: 0"))
    }

    @Test fun stopped_batch_counts_unattempted_records_and_retry_exhaustion_separately() = runBlocking {
        val first = item().copy(retries = 4)
        val queue = Queue(first).apply {
            for (id in 2L..4L) rows[id] = first.copy(id = id, createdAt = first.createdAt + id, retries = 0)
        }
        val api = Api().apply { ingest = { throw IOException("Offline test transport") } }
        val result = repository(queue, metrics, api).processQueueDetailed()
        assertEquals(2, api.calls.get())
        assertEquals(0, result.syncedCount)
        assertEquals(1, result.failedCount)
        assertEquals(3, result.pendingCount)
        assertEquals(0, queue.rows[3L]!!.retries)
        assertEquals(0, queue.rows[4L]!!.retries)
        assertTrue(result.message.contains("Aguardando envio: 3"))
    }

    @Test fun auth_failure_keeps_previous_success_and_unattempted_counts_without_exposing_server_details() = runBlocking {
        val first = item()
        val queue = Queue(first).apply {
            for (id in 2L..3L) rows[id] = first.copy(id = id, createdAt = first.createdAt + id)
        }
        val api = Api()
        api.ingest = {
            if (api.calls.get() == 1) Response.success(IngestResponse(ingest_status = "accepted"))
            else Response.error(401, "INTERNAL-TEST-DETAIL".toResponseBody())
        }
        val result = repository(queue, metrics, api).processQueueDetailed()
        assertEquals(2, api.calls.get())
        assertEquals(1, result.syncedCount)
        assertEquals(1, result.failedCount)
        assertEquals(1, result.pendingCount)
        assertTrue(result.hasIncompleteItems)
        assertTrue(result.message.contains("Concluídos no aplicativo: 1"))
        assertTrue(result.message.contains("Peça ajuda"))
        assertFalse(result.message.contains("INTERNAL-TEST-DETAIL"))
    }

    @Test fun skipped_invalid_sample_is_counted_once_as_failed_not_as_pending() = runBlocking {
        val queue = Queue(item().copy(payloadJson = """{"heart_rate":0}"""))
        val api = Api()
        val result = repository(queue, metrics, api).processQueueDetailed()
        assertEquals(0, api.calls.get())
        assertEquals(1, result.skippedCount)
        assertEquals(1, result.failedCount)
        assertEquals(0, result.pendingCount)
        assertTrue(result.hasIncompleteItems)
    }

    @Test fun completed_batch_does_not_claim_to_cover_records_inserted_after_its_snapshot() = runBlocking {
        val first = item()
        val later = first.copy(id = 2)
        val queue = Queue(first)
        val api = Api().apply { ingest = { queue.rows[2L] = later; Response.success(IngestResponse(ingest_status = "accepted")) } }
        val result = repository(queue, metrics, api).processQueueDetailed()
        assertEquals(1, api.calls.get())
        assertEquals(1, result.syncedCount)
        assertEquals(0, result.failedCount)
        assertEquals(0, result.pendingCount)
        assertFalse(result.hasIncompleteItems)
        assertEquals(later, queue.rows[2L])
        assertTrue(result.message.startsWith("Nesta tentativa:"))
        assertFalse(result.message.contains("Fila limpa"))
    }

    @Test fun no_pending_records_does_not_claim_a_clean_queue_when_failed_records_exist() = runBlocking {
        val failed = item().copy(status = QueueStatus.FAILED.name)
        val queue = Queue(failed)
        val api = Api()
        val result = repository(queue, metrics, api).processQueueDetailed()
        assertEquals(0, api.calls.get())
        assertEquals(failed, queue.rows[1L])
        assertTrue(result.message.contains("nesta tentativa"))
        assertFalse(result.message.contains("Fila limpa"))
    }

    @Test fun storage_failure_after_http_success_stops_batch_without_a_second_failure_write() = runBlocking {
        val first = item()
        val second = first.copy(id = 2, createdAt = first.createdAt + 1)
        val storageError = IllegalStateException("Test storage unavailable")
        val queue = Queue(first).apply {
            rows[2L] = second
            beforeUpdate = { if (writes.get() == 1) throw storageError }
        }
        val api = Api()
        val repo = repository(queue, metrics, api)
        val failure = try { repo.processQueueDetailed(); null } catch (error: Exception) { error }
        assertTrue(failure is QueuePersistenceException)
        assertSame(storageError, failure!!.cause)
        assertEquals(1, api.calls.get())
        assertEquals(1, queue.writes.get())
        assertEquals(first, queue.rows[1L])
        assertEquals(second, queue.rows[2L])
        assertFalse(repo.isSyncing.value)
        assertNull(repo.lastSyncResult.value)

        // An explicitly recovered store can process another attempt; the admission gate is released.
        queue.beforeUpdate = {}
        assertEquals(2, repo.processQueueDetailed().syncedCount)
        assertEquals(3, api.calls.get())
        assertFalse(repo.isSyncing.value)
    }

    @Test fun ambiguous_local_write_does_not_overwrite_an_already_saved_success_as_pending() = runBlocking {
        val queue = Queue(item()).apply {
            afterUpdate = { if (writes.get() == 1) throw IllegalStateException("Test completion acknowledgement lost") }
        }
        val api = Api()
        val repo = repository(queue, metrics, api)
        val failure = try { repo.processQueueDetailed(); null } catch (error: Exception) { error }
        assertTrue(failure is QueuePersistenceException)
        assertEquals(1, api.calls.get())
        assertEquals(1, queue.writes.get())
        assertEquals(QueueStatus.SYNCED.name, queue.rows[1L]!!.status)
        assertEquals(0, queue.rows[1L]!!.retries)
        assertFalse(repo.isSyncing.value)
        queue.afterUpdate = {}
        assertEquals(0, repo.processQueueDetailed().syncedCount)
        assertEquals(1, api.calls.get())
    }

    @Test fun storage_failure_after_http_error_preserves_the_record_and_aborts_without_reclassifying_it() = runBlocking {
        for (code in listOf(401, 422, 503)) {
            val original = item()
            val storageError = IllegalStateException("Test write unavailable")
            val queue = Queue(original).apply {
                rows[2L] = original.copy(id = 2, createdAt = original.createdAt + 1)
                beforeUpdate = { throw storageError }
            }
            val api = Api().apply { ingest = { Response.error(code, "Test HTTP error".toResponseBody()) } }
            val repo = repository(queue, metrics, api)
            val failure = try { repo.processQueueDetailed(); null } catch (error: Exception) { error }
            assertTrue("HTTP $code", failure is QueuePersistenceException)
            assertSame(storageError, failure!!.cause)
            assertEquals(1, api.calls.get())
            assertEquals(1, queue.writes.get())
            assertEquals(original, queue.rows[1L])
            assertEquals(0, queue.rows[2L]!!.retries)
            assertNull(repo.lastSyncResult.value)
            assertFalse(repo.isSyncing.value)
        }
    }

    @Test fun storage_failure_while_recording_a_network_error_remains_a_local_failure() = runBlocking {
        val original = item().copy(retries = 4)
        val storageError = IllegalStateException("Test storage unavailable")
        val queue = Queue(original).apply { beforeUpdate = { throw storageError } }
        val api = Api().apply { ingest = { throw IOException("Test offline") } }
        val repo = repository(queue, metrics, api)
        val failure = try { repo.processQueueDetailed(); null } catch (error: Exception) { error }
        assertTrue(failure is QueuePersistenceException)
        assertSame(storageError, failure!!.cause)
        assertEquals(1, api.calls.get())
        assertEquals(1, queue.writes.get())
        assertEquals(original, queue.rows[1L])
        assertFalse(repo.isSyncing.value)
    }

    @Test fun cancellation_during_result_persistence_is_not_wrapped_or_written_as_failure() = runBlocking {
        val original = item()
        val queue = Queue(original).apply { beforeUpdate = { throw CancellationException("Test cancellation") } }
        val api = Api()
        val repo = repository(queue, metrics, api)
        val failure = try { repo.processQueueDetailed(); null } catch (error: Exception) { error }
        assertTrue(failure is CancellationException)
        assertEquals(1, api.calls.get())
        assertEquals(1, queue.writes.get())
        assertEquals(original, queue.rows[1L])
        assertFalse(repo.isSyncing.value)
        assertNull(repo.lastSyncResult.value)
    }

    @Test fun storage_failure_for_invalid_sample_aborts_before_sending_the_next_record() = runBlocking {
        val original = item().copy(payloadJson = """{"heart_rate":0}""")
        val queue = Queue(original).apply {
            rows[2L] = item().copy(id = 2, createdAt = original.createdAt + 1)
            beforeUpdate = { throw IllegalStateException("Test write unavailable") }
        }
        val api = Api()
        val repo = repository(queue, metrics, api)
        val failure = try { repo.processQueueDetailed(); null } catch (error: Exception) { error }
        assertTrue(failure is QueuePersistenceException)
        assertEquals(0, api.calls.get())
        assertEquals(1, queue.writes.get())
        assertEquals(original, queue.rows[1L])
        assertFalse(repo.isSyncing.value)
    }
}
