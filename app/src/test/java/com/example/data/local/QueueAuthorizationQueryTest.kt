package com.example.data.local

import android.app.Application
import androidx.room.Room
import com.example.data.ingest.IngestPayloadMapper
import com.example.data.ingest.QueueAuthorization
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Actual generated Room DAO, isolated in-memory SQLite; never the tablet database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class QueueAuthorizationQueryTest {
    private lateinit var db: AppDatabase
    private lateinit var queue: IngestQueueDao

    @Before fun openDatabase() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        queue = db.ingestQueueDao()
    }

    @After fun closeDatabase() { db.close() }

    private suspend fun blocked() = queue.hasAuthorizationBlock(
        QueueAuthorization.UNAUTHORIZED_PREFIX, QueueAuthorization.FORBIDDEN_PREFIX,
    )

    @Test fun query_matches_existing_classifier_for_status_and_error_variants() = runBlocking {
        val errors = listOf(
            null, "", "timeout", "HTTP 401", "HTTP 403",
            QueueAuthorization.UNAUTHORIZED_PREFIX,
            QueueAuthorization.FORBIDDEN_PREFIX,
            IngestPayloadMapper.authErrorMessage(401, "test denied"),
            IngestPayloadMapper.authErrorMessage(403, "test denied"),
            QueueAuthorization.UNAUTHORIZED_PREFIX.lowercase(),
            " " + QueueAuthorization.UNAUTHORIZED_PREFIX,
            "Falha de autenticação na API HealthTech (HTTP 4010)",
            "Falha de autenticação na API HealthTech (HTTP 422)",
            QueueAuthorization.FORBIDDEN_PREFIX + "\nlegacy detail",
        )
        for (status in listOf("PENDING", "FAILED", "SYNCED", "pending", "UNKNOWN")) {
            for (error in errors) {
                queue.clearAll()
                val row = IngestQueueEntity(payloadJson = "synthetic", status = status, errorMessage = error)
                queue.insertItem(row)
                assertEquals("status=$status, error=$error", QueueAuthorization.isBlocked(row), blocked())
            }
        }
    }

    @Test fun empty_mixed_and_updated_queues_use_current_persisted_evidence() = runBlocking {
        assertFalse(blocked())
        repeat(40) { queue.insertItem(IngestQueueEntity(payloadJson = "synthetic-$it")) }
        val original = IngestQueueEntity(payloadJson = "synthetic-blocked", status = "FAILED",
            errorMessage = QueueAuthorization.UNAUTHORIZED_PREFIX)
        val id = queue.insertItem(original)
        assertTrue(blocked())
        // Manual retry must preserve the pause until its authorization evidence is cleared.
        queue.updateItem(original.copy(id = id, status = "PENDING"))
        assertTrue(blocked())
        queue.updateItem(original.copy(id = id, status = "SYNCED", errorMessage = null))
        assertFalse(blocked())
        // A second reading has its own identity; copying the original would violate uniqueness.
        val forbiddenId = queue.insertItem(IngestQueueEntity(
            payloadJson = "synthetic-forbidden", status = "FAILED",
            errorMessage = QueueAuthorization.FORBIDDEN_PREFIX,
        ))
        assertTrue(blocked())
        queue.deleteById(forbiddenId)
        assertFalse(blocked())
    }

    @Test fun large_or_invalid_payloads_do_not_affect_auth_evidence_or_get_rewritten() = runBlocking {
        val payload = "synthetic-invalid-json:" + "x".repeat(128_000)
        val row = IngestQueueEntity(payloadJson = payload, status = "FAILED",
            errorMessage = QueueAuthorization.FORBIDDEN_PREFIX)
        val id = queue.insertItem(row)
        repeat(5) { assertTrue(blocked()) }
        assertEquals(listOf(row.copy(id = id)), queue.getAllItemsSync())
    }
}
