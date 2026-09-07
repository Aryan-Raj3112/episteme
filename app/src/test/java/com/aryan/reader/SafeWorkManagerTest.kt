package com.aryan.reader

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Guards the WorkManager hardening (broken-JobScheduler devices must degrade
 * to "no background work" instead of crashing). The latch is process-global,
 * so every test resets it to avoid leaking state into other test classes
 * running in the same JVM.
 */
class SafeWorkManagerTest {

    private class TestWorker(context: Context, params: WorkerParameters) :
        androidx.work.Worker(context, params) {
        override fun doWork(): Result = Result.success()
    }

    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        SafeWorkManager.resetForTests()
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        workManager = mockk(relaxed = true)
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any()) } returns workManager
    }

    @After
    fun tearDown() {
        unmockkObject(WorkManager.Companion)
        SafeWorkManager.resetForTests()
    }

    @Test
    fun `healthy device delegates enqueue and reports success`() {
        val request = OneTimeWorkRequestBuilder<TestWorker>().build()

        assertTrue(
            SafeWorkManager.enqueueUniqueWork(
                context, "work", ExistingWorkPolicy.REPLACE, request
            )
        )

        verify { workManager.enqueueUniqueWork("work", ExistingWorkPolicy.REPLACE, request) }
    }

    @Test
    fun `healthy device delegates plain enqueue`() {
        val request = OneTimeWorkRequestBuilder<TestWorker>().build()

        assertTrue(SafeWorkManager.enqueue(context, request))

        verify { workManager.enqueue(request) }
    }

    @Test
    fun `missing JobScheduler method latches and short-circuits later calls`() {
        every { WorkManager.getInstance(any()) } throws
            NoSuchMethodError("No virtual method forNamespace")

        val request = OneTimeWorkRequestBuilder<TestWorker>().build()
        assertFalse(
            SafeWorkManager.enqueueUniqueWork(
                context, "work", ExistingWorkPolicy.KEEP, request
            )
        )
        assertTrue(SafeWorkManager.isJobSchedulerBroken())
        assertNull(SafeWorkManager.getOrNull(context))

        // Second call must not touch WorkManager again.
        assertFalse(SafeWorkManager.cancelUniqueWork(context, "work"))
        assertFalse(SafeWorkManager.cancelAllWorkByTag(context, "tag"))
        assertFalse(SafeWorkManager.pruneWork(context))
        assertNull(SafeWorkManager.getWorkInfoByIdFlow(context, UUID.randomUUID()))
        verify(exactly = 1) { WorkManager.getInstance(any()) }
    }

    @Test
    fun `transient failure does not latch and stays retryable`() {
        every { WorkManager.getInstance(any()) } throws
            IllegalStateException("WorkManager is not initialized")

        val request = OneTimeWorkRequestBuilder<TestWorker>().build()
        assertFalse(
            SafeWorkManager.enqueueUniqueWork(
                context, "work", ExistingWorkPolicy.KEEP, request
            )
        )
        assertFalse(SafeWorkManager.isJobSchedulerBroken())

        assertFalse(
            SafeWorkManager.enqueueUniqueWork(
                context, "work", ExistingWorkPolicy.KEEP, request
            )
        )
        verify(exactly = 2) { WorkManager.getInstance(any()) }
    }

    @Test
    fun `healthy device exposes work info flow`() = runBlocking {
        val id = UUID.randomUUID()
        val info = mockk<WorkInfo>()
        every { workManager.getWorkInfoByIdFlow(id) } returns flowOf(info)

        assertSame(info, SafeWorkManager.getWorkInfoByIdFlow(context, id)?.first())
    }

    @Test
    fun `getOrNull returns instance when healthy`() {
        assertSame(workManager, SafeWorkManager.getOrNull(context))
    }

    @Test
    fun `enqueue failure on the instance itself is swallowed`() {
        val request = OneTimeWorkRequestBuilder<TestWorker>().build()
        every {
            workManager.enqueueUniqueWork(
                any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>()
            )
        } throws RuntimeException("quota")

        assertFalse(
            SafeWorkManager.enqueueUniqueWork(
                context, "work", ExistingWorkPolicy.KEEP, request
            )
        )
        assertFalse(SafeWorkManager.isJobSchedulerBroken())
    }
}
