package com.example.data.local

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.TimeZone

/** Production flow with actual Room queries delayed by an explicit test gate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class LocalTotalsLoadingTest {
    @Test fun pending_query_is_unknown_and_only_an_empty_completed_query_means_zero() = runBlocking {
        withDatabase { db ->
            val ready = CompletableDeferred<Unit>()
            val dao = object : HydrationDao by db.hydrationDao() {
                override fun getTodayTotalMlFlow(dateString: String): Flow<Int?> = flow {
                    ready.await()
                    emitAll(db.hydrationDao().getTodayTotalMlFlow(dateString))
                }
            }
            val records = LocalWellnessRecords(dao, db.breathingDao(), emptyFlow())
            val values = Channel<Int?>(Channel.UNLIMITED)
            val job = launch { records.todayHydrationMl.collect { values.send(it) } }
            try {
                assertNull("Query is pending, not a confirmed zero", withTimeout(3000) { values.receive() })
                ready.complete(Unit)
                assertEquals(0, withTimeout(3000) { values.receive() })
            } finally { job.cancelAndJoin() }
        }
    }

    @Test fun switching_day_drops_old_total_to_unknown_until_the_next_query_completes() = runBlocking {
        withDatabase { db ->
            val oldZone = TimeZone.getDefault()
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            try {
                var now = 1790467200000L // 2026-09-27 UTC, synthetic clock only.
                var ready = CompletableDeferred<Unit>()
                val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
                val dao = object : HydrationDao by db.hydrationDao() {
                    override fun getTodayTotalMlFlow(dateString: String): Flow<Int?> = flow {
                        ready.await()
                        emitAll(db.hydrationDao().getTodayTotalMlFlow(dateString))
                    }
                }
                val records = LocalWellnessRecords(dao, db.breathingDao(), changes) { now }
                records.addWaterIntake(500)
                val values = Channel<Int?>(Channel.UNLIMITED)
                val job = launch { records.todayHydrationMl.collect { values.send(it) } }
                suspend fun next() = withTimeout(3000) { values.receive() }
                try {
                    next() // Initial pending state is tested separately.
                    ready.complete(Unit)
                    assertEquals(500, next())
                    ready = CompletableDeferred()
                    now += 86_400_000L
                    changes.emit(Unit)
                    assertNull("New day must not look like zero or yesterday's 500", next())
                    ready.complete(Unit)
                    assertEquals(0, next())
                } finally { job.cancelAndJoin() }
            } finally { TimeZone.setDefault(oldZone) }
        }
    }

    private suspend fun withDatabase(block: suspend (AppDatabase) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try { block(db) } finally { db.close() }
    }
}
