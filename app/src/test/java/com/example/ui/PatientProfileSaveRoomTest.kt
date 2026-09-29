package com.example.ui

import android.app.Application
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.local.UserProfileEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real Room DAO with synthetic rows and controllable failure boundaries; no operational VM. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class PatientProfileSaveRoomTest {
    private lateinit var db: AppDatabase
    private val original = UserProfileEntity(patientId = "synthetic-patient", fullName = "Original", heightCm = 162.5f)
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun close() { db.close() }

    private fun exercise(failBefore: Boolean = false, failAfter: Boolean = false) = runBlocking {
        val dao = db.userProfileDao()
        dao.saveUserProfile(original)
        val other = original.copy(id = "unrelated-profile", patientId = "other-patient")
        dao.saveUserProfile(other)
        val edited = original.copy(fullName = "Corrected", weightKg = 63.7f)
        var writes = 0
        val callbacks = mutableListOf<UserProfileEntity>()
        val job = SupervisorJob()
        val controller = PatientProfileSave(CoroutineScope(coroutineContext + job), { row ->
            writes++
            if (failBefore) error("synthetic-private-detail")
            dao.saveUserProfile(row)
            if (failAfter) error("synthetic-private-detail-after-commit")
        }, { callbacks += it })
        controller.save("request", edited)
        val result = withTimeout(5_000) { controller.state.first { it?.status != ProfileSaveStatus.SAVING }!! }
        assertEquals(1, writes)
        assertEquals(if (failBefore) original else edited, dao.getUserProfile())
        assertEquals(if (failBefore || failAfter) ProfileSaveStatus.UNCONFIRMED else ProfileSaveStatus.SAVED, result.status)
        assertEquals(if (failBefore || failAfter) emptyList<UserProfileEntity>() else listOf(edited), callbacks)
        db.openHelper.readableDatabase.query("SELECT patientId FROM user_profile WHERE id = 'unrelated-profile'").use {
            assertTrue(it.moveToFirst()); assertEquals(other.patientId, it.getString(0))
        }
        assertEquals(original.id, result.profileId); assertEquals(original.patientId, result.patientId)
        job.cancel()
    }
    @Test fun confirmed_write_preserves_ids_precision_and_unrelated_row() { exercise() }
    @Test fun failed_write_preserves_original_profile_and_does_not_notify_success() { exercise(failBefore = true) }
    @Test fun exception_after_commit_is_uncertain_without_rollback_or_second_write() { exercise(failAfter = true) }
}
