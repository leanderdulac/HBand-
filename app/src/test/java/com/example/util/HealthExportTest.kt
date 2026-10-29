package com.example.util

import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HealthExportTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun failed_write_removes_only_new_partial_export_and_allows_retry() {
        val directory = temporary.newFolder()
        val previous = File(directory, "previous.csv").apply { writeText("earlier snapshot") }
        val error = IOException("Synthetic disk failure")
        var published = false
        val thrown = assertThrows(IOException::class.java) {
            prepareHealthExport(directory, "health_", ".csv", {
                it.write("partial".toByteArray())
                throw error
            }, { published = true })
        }
        assertSame(error, thrown)
        assertFalse(published)
        assertEquals(listOf(previous), directory.listFiles()!!.toList())
        val next = prepareHealthExport(directory, "health_", ".csv", { it.write("complete".toByteArray()) }, { it })
        assertEquals("complete", next.readText())
        assertEquals("earlier snapshot", previous.readText())
    }

    @Test fun empty_export_is_not_published_and_leaves_no_new_file() {
        val directory = temporary.newFolder()
        var published = false
        assertThrows(IOException::class.java) {
            prepareHealthExport(directory, "health_", ".png", {}, { published = true })
        }
        assertFalse(published)
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun provider_failure_cleans_new_file_and_keeps_original_exception() {
        val directory = temporary.newFolder()
        val error = IllegalArgumentException("Synthetic provider failure")
        val thrown = assertThrows(IllegalArgumentException::class.java) {
            prepareHealthExport(directory, "health_", ".png", { it.write(byteArrayOf(1, 2)) }, { throw error })
        }
        assertSame(error, thrown)
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun cancellation_is_rethrown_and_partial_file_is_removed() {
        val directory = temporary.newFolder()
        val cancelled = CancellationException("Synthetic cancellation")
        val thrown = assertThrows(CancellationException::class.java) {
            prepareHealthExport(directory, "health_", ".csv", {
                it.write(byteArrayOf(1))
                throw cancelled
            }, { fail("Must not publish") })
        }
        assertSame(cancelled, thrown)
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun successful_exports_keep_separate_complete_snapshots() {
        val directory = File(temporary.root, "new-directory")
        val first = prepareHealthExport(directory, "health_", ".csv", { it.write("first".toByteArray()) }, { it })
        val second = prepareHealthExport(directory, "health_", ".csv", { it.write("second".toByteArray()) }, { it })
        assertNotEquals(first, second)
        assertEquals("first", first.readText())
        assertEquals("second", second.readText())
    }

    @Test fun invalid_directory_does_not_overwrite_existing_file() {
        val file = temporary.newFile().apply { writeText("preserved") }
        assertThrows(IOException::class.java) {
            prepareHealthExport(file, "health_", ".csv", { fail("Must not write") }, { fail("Must not publish") })
        }
        assertEquals("preserved", file.readText())
    }

    @Test fun concurrent_exports_do_not_replace_each_other() {
        val directory = File(temporary.root, "concurrent")
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val jobs = (1..8).map { index -> pool.submit<File> {
                check(start.await(5, TimeUnit.SECONDS))
                prepareHealthExport(directory, "health_", ".csv", { it.write("snapshot-$index".toByteArray()) }, { it })
            } }
            start.countDown()
            val files = jobs.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(8, files.toSet().size)
            files.forEachIndexed { index, file -> assertEquals("snapshot-${index + 1}", file.readText()) }
        } finally {
            pool.shutdownNow()
            check(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}
