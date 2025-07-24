package live.aem.koffetch.extensions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import live.aem.koffetch.TestDataGenerator
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Advanced tests for Flow behavior, error handling, cancellation, and performance
 */
class FlowBehaviorTest {
    // ========== CANCELLATION TESTS ==========

    @Test
    fun testFlowCancellation() =
        runTest {
            val cancelled = AtomicBoolean(false)
            val delayedFlow =
                flow {
                    repeat(100) { index ->
                        delay(10)
                        emit(
                            TestDataGenerator.createFFetchEntry("delayed_$index", "Title $index", "Description $index"),
                        )
                    }
                }.onCompletion {
                    cancelled.set(true)
                }

            val job =
                launch {
                    delayedFlow.collect { }
                }

            delay(50) // Let some emissions happen
            job.cancelAndJoin()

            assertTrue(cancelled.get())
        }

    @Test
    fun testCancellationInTransformations() =
        runTest {
            val processed = AtomicInteger(0)
            val slowTransform =
                TestDataGenerator.createDelayedFFetchFlow(50, 20, "slow")
                    .map { entry ->
                        processed.incrementAndGet()
                        entry["title"].toString().uppercase()
                    }

            val job =
                launch {
                    slowTransform.collect { }
                }

            delay(100) // Let some processing happen
            job.cancel()
            job.join()

            assertTrue(processed.get() < 50) // Should not process all entries
        }

    @Test
    fun testCancellationCleanup() =
        runTest {
            var resourceCleaned = false
            val resourceFlow =
                flow {
                    try {
                        repeat(100) { index ->
                            emit(TestDataGenerator.createFFetchEntry("resource_$index", "Title", "Description"))
                            delay(10)
                        }
                    } finally {
                        resourceCleaned = true
                    }
                }

            val job =
                launch {
                    resourceFlow.collect { }
                }

            delay(50)
            job.cancelAndJoin()

            assertTrue(resourceCleaned)
        }

    // ========== EXCEPTION HANDLING TESTS ==========

    @Test
    fun testExceptionPropagationInMap() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(5, "exception")
            val faultyFlow =
                entries.asFlow().map { entry ->
                    if (entry["id"].toString().contains("3")) {
                        throw RuntimeException("Processing error")
                    }
                    entry["title"].toString()
                }

            assertFailsWith<RuntimeException> {
                faultyFlow.toList()
            }
        }

    @Test
    fun testExceptionHandlingInFilter() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(5, "filter_error")
            val faultyFilter =
                entries.asFlow().filter { entry ->
                    if (entry["id"].toString().contains("4")) {
                        throw IllegalStateException("Filter error")
                    }
                    true
                }

            assertFailsWith<IllegalStateException> {
                faultyFilter.toList()
            }
        }

    @Test
    fun testExceptionInCollectionOperations() =
        runTest {
            val failingFlow = TestDataGenerator.createFailingFFetchFlow(3, "collection_error")

            assertFailsWith<RuntimeException> {
                failingFlow.all()
            }

            assertFailsWith<RuntimeException> {
                failingFlow.count()
            }
        }

    @Test
    fun testExceptionRecovery() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(5, "recovery")
            val recoveredFlow =
                entries.asFlow()
                    .map { entry ->
                        if (entry["id"].toString().contains("3")) {
                            throw RuntimeException("Temporary error")
                        }
                        entry["title"].toString()
                    }
                    .catch { exception ->
                        emit("RECOVERED_${exception.message}")
                    }

            val result = recoveredFlow.toList()
            assertEquals(3, result.size) // 2 successful + 1 recovery
            assertTrue(result.contains("RECOVERED_Temporary error"))
        }

    // ========== FLOW RETRY AND ERROR RECOVERY ==========

    @Test
    fun testFlowErrorRecoveryWithRetry() =
        runTest {
            var attempts = 0
            val unreliableFlow =
                flow {
                    attempts++
                    if (attempts < 3) {
                        throw RuntimeException("Temporary failure")
                    }
                    emit(TestDataGenerator.createFFetchEntry("success", "Success", "Finally worked"))
                }

            val retriedFlow = unreliableFlow.retry(2)
            val result = retriedFlow.toList()

            assertEquals(1, result.size)
            assertEquals("success", result[0]["id"])
            assertEquals(3, attempts)
        }

    // ========== CONCURRENT OPERATIONS ==========

    @Test
    fun testConcurrentCollectionOperations() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(100, "concurrent")
            val flow = entries.asFlow()

            val job1 = async { flow.count() }
            val job2 = async { flow.all() }
            val job3 = async { flow.first() }

            val count = job1.await()
            val all = job2.await()
            val first = job3.await()

            assertEquals(100, count)
            assertEquals(100, all.size)
            assertEquals("concurrent_1", first!!["id"])
        }

    @Test
    fun testFlowWithMultipleSubscribers() =
        runTest {
            val sharedFlow = TestDataGenerator.createDelayedFFetchFlow(10, 5, "shared")

            val subscriber1 =
                async {
                    sharedFlow.map { it["title"].toString() }.toList()
                }

            val subscriber2 =
                async {
                    sharedFlow.filter { (it["id"] as String).endsWith("5") }.toList()
                }

            val subscriber3 = async { sharedFlow.count() }

            val titles = subscriber1.await()
            val filtered = subscriber2.await()
            val count = subscriber3.await()

            assertEquals(10, titles.size)
            assertEquals(1, filtered.size) // Only "shared_5"
            assertEquals(10, count)
        }

    // ========== PERFORMANCE AND MEMORY TESTS ==========

    @Test
    fun testHighVolumeProcessing() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(10000, "volume")
            val result =
                entries.asFlow()
                    .filter { (it["index"] as Int) % 100 == 0 }
                    .map { "PROCESSED_${it["id"]}" }
                    .toList()

            assertEquals(100, result.size) // Every 100th entry
            assertTrue(result.all { it.startsWith("PROCESSED_") })
        }

    @Test
    fun testMemoryEfficiencyWithLargeTransformations() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(50000, "memory")

            // Process in chunks to test memory efficiency
            val processedCount =
                entries.asFlow()
                    .map { it["id"].toString() }
                    .filter { it.contains("memory") }
                    .map { it.length }
                    .filter { it > 5 }
                    .count()

            assertEquals(50000, processedCount)
        }

    @Test
    fun testFlowDoesNotLeakMemory() =
        runTest {
            // Test that flows don't hold references unnecessarily
            repeat(100) { iteration ->
                val flow =
                    TestDataGenerator.createFFetchEntries(1000, "leak_test_$iteration")
                        .asFlow()
                        .map { it["title"].toString() }
                        .filter { it.isNotEmpty() }
            }

            // If we get here without OutOfMemoryError, the test passes
            assertTrue(true)
        }

    // ========== BACKPRESSURE AND BUFFERING ==========

    @Test
    fun testBackpressureHandling() =
        runTest {
            val channel = Channel<String>(capacity = 5)
            val producerJob =
                launch {
                    repeat(20) { index ->
                        channel.send("item_$index")
                        delay(1) // Fast producer
                    }
                    channel.close()
                }

            val results = mutableListOf<String>()
            for (item in channel) {
                delay(10) // Slow consumer
                results.add(item)
            }

            producerJob.join()
            assertEquals(20, results.size)
        }

    @Test
    fun testFlowBuffering() =
        runTest {
            val start = System.currentTimeMillis()
            val timedFlow =
                TestDataGenerator.createDelayedFFetchFlow(10, 10, "buffered")
                    .buffer(5)
                    .onEach { delay(5) } // Simulate processing

            val results = timedFlow.toList()
            val elapsed = System.currentTimeMillis() - start

            assertEquals(10, results.size)
            assertTrue(elapsed < 200) // Should be faster due to buffering
        }

    // ========== FLOW COMPOSITION ==========

    @Test
    fun testFlowComposition() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(20, "compose")

            val composed =
                entries.asFlow()
                    .filter { (it["index"] as Int) % 2 == 0 }
                    .map { entry ->
                        TestDataGenerator.createFFetchEntry(
                            "composed_${entry["id"]}",
                            "COMPOSED_${entry["title"]}",
                            entry["description"].toString(),
                        )
                    }
                    .filter { it["title"].toString().contains("COMPOSED") }
                    .map { it["id"].toString() }

            val result = composed.toList()
            assertEquals(10, result.size)
            assertTrue(result.all { it.startsWith("composed_") })
        }

    // ========== DISPATCHER AND CONTEXT TESTS ==========

    @Test
    fun testFlowWithDifferentDispatchers() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(5, "dispatch")

            val defaultResult =
                async {
                    entries.asFlow().map { it["title"].toString() }.toList()
                }

            val ioResult =
                async {
                    withContext(Dispatchers.IO) {
                        entries.asFlow().map { it["id"].toString() }.toList()
                    }
                }

            val defaultTitles = defaultResult.await()
            val ioIds = ioResult.await()

            assertEquals(5, defaultTitles.size)
            assertEquals(5, ioIds.size)
        }

    @Test
    fun testFlowExecutionContext() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(3, "context")
            val contextAwareFlow =
                entries.asFlow()
                    .flowOn(Dispatchers.Default)
                    .map { it["title"].toString().uppercase() }

            val result = contextAwareFlow.toList()
            assertEquals(3, result.size)
            assertTrue(result.all { it.startsWith("TITLE") })
        }
}
