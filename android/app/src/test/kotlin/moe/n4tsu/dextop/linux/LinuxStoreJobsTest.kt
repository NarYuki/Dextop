package moe.n4tsu.dextop.linux

import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinuxStoreJobsTest {
    @Test fun appListPeekDoesNotConsumeTheStoresCompletionEvent() {
        val field = LinuxStoreJobs::class.java.getDeclaredField("jobs").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val jobs = field.get(LinuxStoreJobs) as ConcurrentHashMap<String, LinuxStoreJobs.Job>
        val container = "completion-peek-test"
        val key = "$container\u0000example"
        jobs[key] = LinuxStoreJobs.Job(container, "install", listOf("example")).apply { state = "done" }
        try {
            repeat(2) { assertEquals("done", LinuxStoreJobs.poll(container, consume = false).single()["state"]) }
            assertEquals("done", LinuxStoreJobs.poll(container).single()["state"])
            assertTrue(LinuxStoreJobs.poll(container, consume = false).isEmpty())
        } finally { jobs.remove(key) }
    }
}
