package com.example.data.local

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Synthetic Room only: no MainViewModel startup, BLE, worker, AI or network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class LocalWellnessRecordsTest {
    private lateinit var db: AppDatabase
    private lateinit var previousZone: TimeZone
    private lateinit var previousLocale: Locale
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var now = 0L
    private lateinit var records: LocalWellnessRecords

    @Before fun setUp() {
        previousZone = TimeZone.getDefault()
        previousLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))
        Locale.setDefault(Locale.US)
        now = instant("2026-09-26 23:59:59")
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        records = LocalWellnessRecords(db.hydrationDao(), db.breathingDao(), changes) { now }
    }

    @After fun tearDown() {
        db.close()
        TimeZone.setDefault(previousZone)
        Locale.setDefault(previousLocale)
    }

    private fun instant(text: String): Long = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(text)!!.time
    private suspend fun water(day: String) = db.hydrationDao().getHydrationLogsForDate(day).first()

    @Test fun water_after_midnight_has_matching_timestamp_and_new_date_preserving_yesterday() = runBlocking {
        records.addWaterIntake(250)
        val yesterday = water("2026-09-26").single()
        now += 2000
        records.addWaterIntake(300)
        assertEquals(listOf(yesterday), water("2026-09-26"))
        val today = water("2026-09-27").single()
        assertEquals(300, today.amountMl)
        assertEquals(now, today.timestampMillis)
        assertNotEquals(yesterday.id, today.id)
    }

    @Test fun breathing_after_midnight_uses_save_day_and_keeps_all_time_total() = runBlocking {
        records.saveBreathingSession(60)
        now += 2000
        records.saveBreathingSession(90)
        val rows = db.breathingDao().getAllSessionsFlow().first()
        assertEquals(listOf("2026-09-27", "2026-09-26"), rows.map { it.dateString })
        assertEquals(now, rows.first().timestampMillis)
        assertEquals(150, db.breathingDao().getTotalBreathingSecondsFlow().first())
    }

    @Test fun reset_after_midnight_deletes_only_current_day() = runBlocking {
        records.addWaterIntake(250)
        val yesterday = water("2026-09-26").single()
        now += 2000
        db.hydrationDao().insertLog(HydrationLogEntity(amountMl = 300, timestampMillis = now, dateString = "2026-09-27"))
        records.resetTodayHydration()
        assertEquals(listOf(yesterday), water("2026-09-26"))
        assertTrue(water("2026-09-27").isEmpty())
    }

    @Test fun visible_total_switches_day_without_insert_or_viewmodel_recreation() = runBlocking {
        records.addWaterIntake(250)
        val totals = Channel<Int>(Channel.UNLIMITED)
        val collection = launch { records.todayHydrationMl.collect { totals.send(it) } }
        suspend fun awaitTotal(expected: Int) = withTimeout(3000) { while (totals.receive() != expected) Unit }
        try {
            awaitTotal(250)
            now += 2000
            changes.emit(Unit)
            awaitTotal(0)
            records.addWaterIntake(300)
            awaitTotal(300)
        } finally { collection.cancelAndJoin() }
    }

    @Test fun resubscription_reads_current_day_even_without_a_broadcast() = runBlocking {
        records.addWaterIntake(250)
        assertEquals(250, records.todayHydrationMl.first { it == 250 })
        now += 2000
        assertEquals(0, records.todayHydrationMl.first())
    }

    @Test fun timezone_change_reselects_local_day_and_never_relabels_history() = runBlocking {
        records.addWaterIntake(250)
        val original = water("2026-09-26").single()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        records.addWaterIntake(300)
        assertEquals(listOf(original), water("2026-09-26"))
        assertEquals(now, water("2026-09-27").single().timestampMillis)
        records.resetTodayHydration()
        assertEquals(listOf(original), water("2026-09-26"))
    }

    @Test fun clock_moved_back_uses_current_local_day_for_new_entries_only() = runBlocking {
        records.addWaterIntake(250)
        val original = water("2026-09-26").single()
        now = instant("2026-09-25 22:00:00")
        records.addWaterIntake(100)
        assertEquals(100, water("2026-09-25").single().amountMl)
        assertEquals(listOf(original), water("2026-09-26"))
    }

    @Test fun nonpositive_breathing_stays_ignored() = runBlocking {
        records.saveBreathingSession(0)
        records.saveBreathingSession(-5)
        assertTrue(db.breathingDao().getAllSessionsFlow().first().isEmpty())
    }
}
