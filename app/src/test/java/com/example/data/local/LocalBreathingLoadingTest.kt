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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class LocalBreathingLoadingTest {
    @Test fun breathing_is_unknown_before_query_then_preserves_zero_and_saved_duration() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val ready = CompletableDeferred<Unit>()
        val dao = object : BreathingDao by db.breathingDao() {
            override fun getTotalBreathingSecondsFlow(): Flow<Int?> = flow {
                ready.await()
                emitAll(db.breathingDao().getTotalBreathingSecondsFlow())
            }
        }
        val records = LocalWellnessRecords(db.hydrationDao(), dao, emptyFlow())
        val values = Channel<Int?>(Channel.UNLIMITED)
        val job = launch { records.totalBreathingSeconds.collect { values.send(it) } }
        try {
            assertNull(withTimeout(3000) { values.receive() })
            ready.complete(Unit)
            assertEquals(0, withTimeout(3000) { values.receive() })
            records.saveBreathingSession(90)
            assertEquals(90, withTimeout(3000) { values.receive() })
        } finally { job.cancelAndJoin(); db.close() }
    }
}
