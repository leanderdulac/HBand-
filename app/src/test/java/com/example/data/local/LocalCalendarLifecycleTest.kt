package com.example.data.local

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.room.Room
import com.example.util.localCalendarChanges
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class LocalCalendarLifecycleTest {
    @Test fun receiver_signals_and_unregisters_on_android36() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val before = shadowOf(app).registeredReceivers.size
        val events = Channel<Unit>(Channel.UNLIMITED)
        val job = launch(start = CoroutineStart.UNDISPATCHED) { localCalendarChanges(app).collect { events.send(it) } }
        try {
            withTimeout(3000) { events.receive() }
            assertEquals(before + 1, shadowOf(app).registeredReceivers.size)
            for (action in listOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_TICK)) {
                app.sendBroadcast(Intent(action))
                shadowOf(Looper.getMainLooper()).idle()
                withTimeout(3000) { events.receive() }
            }
        } finally { job.cancelAndJoin() }
        assertEquals(before, shadowOf(app).registeredReceivers.size)
    }

    @Test fun subscribed_total_handles_zone_and_backward_clock_changes_then_resumes_without_old_replay() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val oldZone = TimeZone.getDefault()
        val oldLocale = Locale.getDefault()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))
            Locale.setDefault(Locale.US)
            var now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse("2026-09-26 23:59:59")!!.time
            val records = LocalWellnessRecords(db.hydrationDao(), db.breathingDao(), localCalendarChanges(app)) { now }
            for ((day, amount) in listOf("2026-09-25" to 100, "2026-09-26" to 250, "2026-09-27" to 500)) {
                db.hydrationDao().insertLog(HydrationLogEntity(amountMl = amount, dateString = day))
            }
            // Same lifecycle sharing policy used by MainViewModel / collectAsStateWithLifecycle.
            val state = records.todayHydrationMl.stateIn(scope, SharingStarted.WhileSubscribed(0, 0), 0)
            val totals = Channel<Int>(Channel.UNLIMITED)
            val collector = launch { state.collect { totals.send(it) } }
            suspend fun expect(amount: Int) = withTimeout(3000) { while (totals.receive() != amount) Unit }
            try {
                expect(250)
                TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
                app.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))
                shadowOf(Looper.getMainLooper()).idle()
                expect(500)
                now -= 2 * 24 * 60 * 60 * 1000L
                app.sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED))
                shadowOf(Looper.getMainLooper()).idle()
                expect(100)
            } finally { collector.cancelAndJoin() }
            withTimeout(3000) { while (state.value != 0) yield() }
            now += 2 * 24 * 60 * 60 * 1000L
            // No broadcast while unobserved: a fresh subscription still reaches today's stored total.
            assertEquals(500, withTimeout(3000) { state.first { it != 0 } })
            assertEquals(3, listOf("2026-09-25", "2026-09-26", "2026-09-27").sumOf {
                db.hydrationDao().getHydrationLogsForDate(it).first().size
            })
        } finally {
            scope.cancel()
            db.close()
            TimeZone.setDefault(oldZone)
            Locale.setDefault(oldLocale)
        }
    }
}
