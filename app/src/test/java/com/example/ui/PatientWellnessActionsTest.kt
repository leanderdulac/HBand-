package com.example.ui

import android.app.Application
import androidx.room.Room
import com.example.data.local.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.TimeZone

/** Actual local record writer and in-memory Room; no app startup, BLE or transport. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class PatientWellnessActionsTest {
    private lateinit var db: AppDatabase
    private lateinit var oldZone: TimeZone
    private lateinit var actions: PatientWellnessActions
    private lateinit var records: LocalWellnessRecords
    private val notices = mutableListOf<Pair<String, Boolean>>()
    private var failure: Throwable? = null
    private var afterWrite = false
    private var calls = 0

    @Before fun setUp() {
        oldZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val water = db.hydrationDao()
        val breathing = db.breathingDao()
        records = LocalWellnessRecords(
            object : HydrationDao by water {
                override suspend fun insertLog(log: HydrationLogEntity) = write { water.insertLog(log) }
                override suspend fun resetTodayLogs(dateString: String) = write { water.resetTodayLogs(dateString) }
            },
            object : BreathingDao by breathing {
                override suspend fun insertSession(session: BreathingSessionEntity) = write { breathing.insertSession(session) }
            },
            emptyFlow(),
        ) { 0L }
        actions = PatientWellnessActions(records) { message, error -> notices.add(message to error) }
    }

    private suspend fun write(operation: suspend () -> Unit) {
        calls++
        if (!afterWrite) failure?.let { throw it }
        operation()
        if (afterWrite) failure?.let { throw it }
    }

    @After fun tearDown() { db.close(); TimeZone.setDefault(oldZone) }

    private fun assertFailureNotice(operation: String) {
        assertEquals(1, calls)
        assertEquals(1, notices.size)
        assertTrue(notices.single().second)
        assertTrue(notices.single().first.contains(operation))
        assertFalse(notices.single().first.contains("synthetic-private-detail"))
        assertTrue(notices.single().first.startsWith("Não foi possível confirmar"))
    }

    @Test fun failed_water_insert_is_reported_once_without_success_or_retry() = runBlocking {
        failure = IllegalStateException("synthetic-private-detail")
        actions.addWaterIntake(250)
        assertFailureNotice("água")
        assertTrue(db.hydrationDao().getHydrationLogsForDate("1970-01-01").first().isEmpty())
    }

    @Test fun failed_reset_preserves_existing_rows_and_reports_failure() = runBlocking {
        val row = HydrationLogEntity(id = 19, amountMl = 300, timestampMillis = 0, dateString = "1970-01-01")
        db.hydrationDao().insertLog(row)
        failure = IllegalStateException("synthetic-private-detail")
        actions.resetTodayHydration()
        assertFailureNotice("exclusão")
        assertEquals(listOf(row), db.hydrationDao().getHydrationLogsForDate("1970-01-01").first())
    }

    @Test fun failed_breathing_insert_is_reported_without_success_or_retry() = runBlocking {
        failure = IllegalStateException("synthetic-private-detail")
        actions.saveBreathingSession(75)
        assertFailureNotice("respiração")
        assertTrue(db.breathingDao().getAllSessionsFlow().first().isEmpty())
    }

    @Test fun successful_water_insert_preserves_previous_row_and_confirms_saved_amount() = runBlocking {
        val old = HydrationLogEntity(id = 19, amountMl = 300, timestampMillis = -1, dateString = "1970-01-01")
        db.hydrationDao().insertLog(old)
        actions.addWaterIntake(250)
        val rows = db.hydrationDao().getHydrationLogsForDate("1970-01-01").first()
        assertEquals(old, rows.single { it.id == 19L })
        val added = rows.single { it.id != 19L }
        assertEquals(250, added.amountMl)
        assertEquals(0L, added.timestampMillis)
        assertEquals(1, calls)
        assertEquals(listOf("Mais 250 mL de água registrados neste celular." to false), notices)
    }

    @Test fun successful_reset_removes_only_today() = runBlocking {
        val old = HydrationLogEntity(id = 19, amountMl = 300, timestampMillis = -1, dateString = "1969-12-31")
        db.hydrationDao().insertLog(old)
        db.hydrationDao().insertLog(old.copy(id = 20, timestampMillis = 0, dateString = "1970-01-01"))
        actions.resetTodayHydration()
        assertEquals(listOf(old), db.hydrationDao().getHydrationLogsForDate("1969-12-31").first())
        assertTrue(db.hydrationDao().getHydrationLogsForDate("1970-01-01").first().isEmpty())
        assertEquals(1, calls)
        assertEquals(listOf("Registros de água de hoje apagados neste celular." to false), notices)
    }

    @Test fun successful_breathing_save_preserves_previous_session_and_confirms_duration() = runBlocking {
        val old = BreathingSessionEntity(id = 21, durationSeconds = 60, timestampMillis = -1, dateString = "1969-12-31")
        db.breathingDao().insertSession(old)
        actions.saveBreathingSession(75)
        val rows = db.breathingDao().getAllSessionsFlow().first()
        assertEquals(old, rows.single { it.id == 21L })
        assertEquals(75, rows.single { it.id != 21L }.durationSeconds)
        assertEquals(135, db.breathingDao().getTotalBreathingSecondsFlow().first())
        assertEquals(1, calls)
        assertEquals(listOf("Tempo de respiração salvo neste celular: 1 min 15 s." to false), notices)
    }

    @Test fun cancellation_is_rethrown_unchanged_for_each_action_without_notice_or_retry() = runBlocking {
        val cancelled = CancellationException("synthetic cancellation")
        failure = cancelled
        val operations: List<suspend () -> Unit> = listOf(
            { actions.addWaterIntake(250) }, { actions.resetTodayHydration() }, { actions.saveBreathingSession(75) },
        )
        for (operation in operations) {
            calls = 0
            try { operation(); fail("Expected cancellation") } catch (actual: CancellationException) {
                assertSame(cancelled, actual)
            }
            assertEquals(1, calls)
            assertTrue(notices.isEmpty())
        }
    }

    @Test fun nonpositive_breathing_does_not_write_or_notify() = runBlocking {
        actions.saveBreathingSession(0)
        actions.saveBreathingSession(-1)
        assertEquals(0, calls)
        assertTrue(notices.isEmpty())
        assertTrue(db.breathingDao().getAllSessionsFlow().first().isEmpty())
    }

    @Test fun failure_after_commit_does_not_retry_or_claim_rollback() = runBlocking {
        afterWrite = true
        failure = IllegalStateException("synthetic-private-detail")
        actions.addWaterIntake(250)
        assertFailureNotice("água")
        assertEquals(250, db.hydrationDao().getHydrationLogsForDate("1970-01-01").first().single().amountMl)
    }

    @Test fun closed_room_database_cancellation_is_preserved_without_failure_notice() = runBlocking {
        db.close()
        try { actions.addWaterIntake(250); fail("Expected Room cancellation") } catch (_: CancellationException) {
            // Room cancels its coroutine scope on close; this is not a recoverable write exception.
        }
        assertEquals(1, calls)
        assertTrue(notices.isEmpty())
    }

    @Test fun fatal_errors_are_not_swallowed_as_recoverable_storage_errors() = runBlocking {
        val fatal = AssertionError("synthetic fatal")
        failure = fatal
        try { actions.addWaterIntake(250); fail("Expected error") } catch (actual: AssertionError) {
            assertSame(fatal, actual)
        }
        assertEquals(1, calls)
        assertTrue(notices.isEmpty())
    }

    @Test fun breathing_receipt_precedes_notice_and_confirms_real_room_write() = runBlocking {
        val events = mutableListOf<String>()
        val action = PatientWellnessActions(records) { _, _ -> events += "notice"; throw IllegalStateException("notice") }
        try {
            action.saveBreathingSession(75) { saved -> events += "result:$saved" }
            fail("Expected notification exception")
        } catch (_: IllegalStateException) { }
        assertEquals(listOf("result:true", "notice"), events)
        assertEquals(75, db.breathingDao().getAllSessionsFlow().first().single().durationSeconds)
    }

    @Test fun breathing_post_commit_exception_reports_uncertain_before_notice_without_retry() = runBlocking {
        afterWrite = true
        failure = IllegalStateException("synthetic-private-detail")
        val events = mutableListOf<String>()
        val action = PatientWellnessActions(records) { _, error -> events += "notice:$error" }
        action.saveBreathingSession(75) { saved -> events += "result:$saved" }
        assertEquals(listOf("result:false", "notice:true"), events)
        assertEquals(1, calls)
        assertEquals(75, db.breathingDao().getAllSessionsFlow().first().single().durationSeconds)
    }

    @Test fun result_callback_failure_is_not_reclassified_as_storage_failure() = runBlocking {
        val failure = IllegalStateException("receipt")
        val results = mutableListOf<Boolean>()
        try {
            actions.saveBreathingSession(75) { results += it; throw failure }
            fail("Expected callback failure")
        } catch (actual: IllegalStateException) { assertSame(failure, actual) }
        assertEquals(listOf(true), results)
        assertTrue(notices.isEmpty())
        assertEquals(75, db.breathingDao().getAllSessionsFlow().first().single().durationSeconds)
    }

    @Test fun notification_failure_is_not_reclassified_or_repeated_after_successful_write() = runBlocking {
        val notificationFailure = IllegalStateException("synthetic notification failure")
        var notifications = 0
        val action = PatientWellnessActions(records) { _, _ -> notifications++; throw notificationFailure }
        try { action.addWaterIntake(250); fail("Expected notification failure") } catch (actual: IllegalStateException) {
            assertSame(notificationFailure, actual)
        }
        assertEquals(1, notifications)
        assertEquals(1, calls)
        assertEquals(250, db.hydrationDao().getHydrationLogsForDate("1970-01-01").first().single().amountMl)
    }
}
