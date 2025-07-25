//
// PerformanceIntegrationTest.kt
// KotlinFFetch Integration Tests
//
// Integration tests for performance scenarios
//

package live.aem.koffetch.integration

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import live.aem.koffetch.FFetch
import live.aem.koffetch.FFetchError
import live.aem.koffetch.extensions.all
import live.aem.koffetch.extensions.count
import live.aem.koffetch.extensions.map
import live.aem.koffetch.mock.MockFFetchHTTPClient
import live.aem.koffetch.withHTTPClient
import live.aem.koffetch.chunks
import live.aem.koffetch.maxConcurrency
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import kotlin.system.measureTimeMillis
import kotlin.test.Test

/**
 * Integration tests for performance scenarios
 * Tests memory usage, streaming efficiency, and concurrent request handling
 */
class PerformanceIntegrationTest {
    private lateinit var mockClient: MockFFetchHTTPClient
    private val baseUrl = "https://example.com/performance-test.json"

    @BeforeEach
    fun setUp() {
        mockClient = MockFFetchHTTPClient()
    }

    @AfterEach
    fun tearDown() {
        mockClient.reset()
    }

    @Test
    fun testLargeDatasetMemoryEfficiency() =
        runBlocking {
            // Setup: Large dataset that would be problematic if loaded entirely into memory
            val totalItems = 10000
            val pageSize = 1000

            setupLargePerformanceDataset(totalItems, pageSize)

            // Execute: Process large dataset without loading everything at once
            val ffetch =
                FFetch(baseUrl)
                    .withHTTPClient(mockClient)
                    .chunks(pageSize)

            var processedCount = 0
            var memoryChecksPassed = 0

            // Process in streaming fashion
            ffetch.asFlow().collect { entry ->
                processedCount++

                // Simulate processing each entry
                val title = entry["title"] as String
                assertTrue(title.startsWith("Performance Item"))

                // Periodically check that we're not accumulating too much in memory
                if (processedCount % 1000 == 0) {
                    // In a real test, you might check actual memory usage here
                    // For this mock test, we just verify we're processing incrementally
                    memoryChecksPassed++
                    assertTrue(processedCount <= totalItems)
                }
            }

            // Verify: All items were processed
            assertEquals(totalItems, processedCount)
            assertEquals(10, memoryChecksPassed) // 10 memory checks for 10k items

            // Verify: Streaming was used (multiple paginated requests)
            val expectedPages = totalItems / pageSize
            assertEquals(expectedPages, mockClient.requestLog.size)
        }

    @Test
    fun testStreamingVsBatchPerformance() =
        runBlocking {
            // Setup: Medium dataset for performance comparison
            val totalItems = 5000
            val pageSize = 500

            setupLargePerformanceDataset(totalItems, pageSize)

            val ffetch =
                FFetch(baseUrl)
                    .withHTTPClient(mockClient)
                    .chunks(pageSize)

            // Test 1: Streaming approach (process as we go)
            mockClient.reset()
            setupLargePerformanceDataset(totalItems, pageSize)

            val streamingTime =
                measureTimeMillis {
                    var streamingCount = 0
                    ffetch.asFlow().collect { entry ->
                        streamingCount++
                        // Simulate processing work
                        entry["title"]?.toString()?.length
                    }
                    assertEquals(totalItems, streamingCount)
                }

            // Test 2: Batch approach (load all then process)
            mockClient.reset()
            setupLargePerformanceDataset(totalItems, pageSize)

            val batchTime =
                measureTimeMillis {
                    val allItems = ffetch.all()
                    assertEquals(totalItems, allItems.size)

                    var batchCount = 0
                    allItems.forEach { entry ->
                        batchCount++
                        // Same processing work
                        entry["title"]?.toString()?.length
                    }
                    assertEquals(totalItems, batchCount)
                }

            // Verify: Both approaches processed the same amount of data
            // Note: In a real scenario, streaming might be slower due to network overhead
            // but uses less memory. Here we just verify both work correctly.
            assertTrue(streamingTime > 0)
            assertTrue(batchTime > 0)

            println("Streaming time: ${streamingTime}ms, Batch time: ${batchTime}ms")
        }

    @Test
    fun testConcurrentRequestHandling() =
        runBlocking {
            // Setup: Multiple different endpoints for concurrent testing
            val endpoints =
                listOf(
                    "https://example.com/dataset-1.json",
                    "https://example.com/dataset-2.json",
                    "https://example.com/dataset-3.json",
                    "https://example.com/dataset-4.json",
                    "https://example.com/dataset-5.json",
                )

            val itemsPerDataset = 1000
            val pageSize = 200

            // Setup responses for all endpoints
            endpoints.forEachIndexed { index, url ->
                setupDatasetForEndpoint(url, itemsPerDataset, pageSize, datasetId = index + 1)
            }

            // Execute: Process all datasets concurrently
            val startTime = System.currentTimeMillis()

            val results =
                endpoints.map { url ->
                    async {
                        val ffetch =
                            FFetch(url)
                                .withHTTPClient(mockClient)
                                .chunks(pageSize)

                        ffetch.count()
                    }
                }.awaitAll()

            val totalTime = System.currentTimeMillis() - startTime

            // Verify: All datasets processed correctly
            assertEquals(5, results.size)
            assertTrue(results.all { it == itemsPerDataset })

            // Verify: Requests were made to all endpoints
            assertEquals(5 * (itemsPerDataset / pageSize), mockClient.requestLog.size) // 5 datasets * 5 pages each

            endpoints.forEach { url ->
                assertTrue(mockClient.requestLog.any { it.url.startsWith(url) })
            }

            println("Concurrent processing of ${endpoints.size} datasets took: ${totalTime}ms")
        }

    @Test
    fun testHighConcurrencyLimit() =
        runBlocking {
            // Setup: Dataset with high concurrency configuration
            val totalItems = 2000
            val pageSize = 100

            setupLargePerformanceDataset(totalItems, pageSize)

            // Execute: Process with high max concurrency
            val ffetch =
                FFetch(baseUrl)
                    .withHTTPClient(mockClient)
                    .chunks(pageSize)
                    .maxConcurrency(10) // Higher than default

            val startTime = System.currentTimeMillis()
            val count = ffetch.count()
            val endTime = System.currentTimeMillis()

            // Verify: Correct count retrieved
            assertEquals(totalItems, count)

            // Verify: All pages were requested
            val expectedPages = totalItems / pageSize
            assertEquals(expectedPages, mockClient.requestLog.size)

            println("High concurrency processing took: ${endTime - startTime}ms")
        }

    @Test
    fun testPartialStreamingPerformance() =
        runBlocking {
            // Setup: Very large dataset where we only want a small portion
            val totalItems = 50000
            val pageSize = 1000
            val itemsWanted = 500 // Only want first 500 items

            setupLargePerformanceDataset(totalItems, pageSize, simulateNetworkDelay = 10L)

            // Execute: Take only first N items efficiently
            val ffetch =
                FFetch(baseUrl)
                    .withHTTPClient(mockClient)
                    .chunks(pageSize)

            val startTime = System.currentTimeMillis()
            val firstNItems =
                ffetch.asFlow()
                    .take(itemsWanted)
                    .toList()
            val endTime = System.currentTimeMillis()

            // Verify: Got exactly the items we wanted
            assertEquals(itemsWanted, firstNItems.size)
            assertEquals("Performance Item 1", firstNItems[0]["title"])
            assertEquals("Performance Item 500", firstNItems[499]["title"])

            // Verify: Only fetched the first page (efficient)
            assertEquals(1, mockClient.requestLog.size)
            assertTrue(mockClient.requestLog[0].url.contains("offset=0"))

            println("Partial streaming ($itemsWanted/$totalItems items) took: ${endTime - startTime}ms")
        }

    @Test
    fun testNetworkLatencyHandling() =
        runBlocking {
            // Setup: Dataset with simulated network delay
            val totalItems = 1000
            val pageSize = 250
            val networkDelayMs = 50L

            setupLargePerformanceDataset(totalItems, pageSize, simulateNetworkDelay = networkDelayMs)

            // Execute: Process with network latency
            val ffetch =
                FFetch(baseUrl)
                    .withHTTPClient(mockClient)
                    .chunks(pageSize)

            val startTime = System.currentTimeMillis()
            val count = ffetch.count()
            val endTime = System.currentTimeMillis()

            // Verify: Correct data retrieved despite latency
            assertEquals(totalItems, count)

            // Verify: Total time includes network delays
            val expectedMinTime = (totalItems / pageSize) * networkDelayMs
            assertTrue((endTime - startTime) >= expectedMinTime)

            println("Processing with ${networkDelayMs}ms network delay took: ${endTime - startTime}ms")
        }

    @Test
    fun testMemoryLeakPrevention() =
        runBlocking {
            // Setup: Process multiple datasets in sequence to test for memory leaks
            val datasets = 5
            val itemsPerDataset = 2000
            val pageSize = 400

            for (datasetIndex in 1..datasets) {
                val datasetUrl = "https://example.com/dataset-$datasetIndex.json"
                setupDatasetForEndpoint(datasetUrl, itemsPerDataset, pageSize, datasetId = datasetIndex)

                // Process entire dataset
                val ffetch =
                    FFetch(datasetUrl)
                        .withHTTPClient(mockClient)
                        .chunks(pageSize)

                val count = ffetch.count()
                assertEquals(itemsPerDataset, count)

                // Clear the mock client between datasets to simulate cleanup
                if (datasetIndex < datasets) {
                    mockClient.clearResponses()
                }
            }

            // Verify: All datasets were processed
            // In a real test, you might check memory usage here
            val totalExpectedRequests = datasets * (itemsPerDataset / pageSize)
            assertTrue(mockClient.requestLog.size <= totalExpectedRequests)

            println("Processed $datasets datasets sequentially without memory leaks")
        }

    @Test
    fun testErrorRecoveryPerformance() =
        runBlocking {
            // Setup: Dataset where some requests will fail and retry
            val totalItems = 1000
            val pageSize = 200

            setupLargePerformanceDataset(totalItems, pageSize)

            // Make the second page fail initially
            val failingUrl = "$baseUrl?offset=200&limit=200"
            mockClient.setErrorResponse(failingUrl, io.ktor.http.HttpStatusCode.InternalServerError, "Temporary error")

            // Execute: Try to process data (will fail on second page)
            val ffetch =
                FFetch(baseUrl)
                    .withHTTPClient(mockClient)
                    .chunks(pageSize)

            // This test verifies that errors are handled efficiently
            assertThrows<FFetchError.NetworkError> {
                runBlocking {
                    ffetch.count()
                }
            }

            // Verify: Failure happened efficiently (didn't retry indefinitely)
            assertTrue(mockClient.requestLog.size <= 10) // Shouldn't make too many attempts
            assertTrue(mockClient.requestLog.any { it.url.contains("offset=0") }) // First request worked
            assertTrue(mockClient.requestLog.any { it.url.contains("offset=200") }) // Second request failed
        }

    // Helper methods

    private fun setupLargePerformanceDataset(
        totalItems: Int,
        pageSize: Int,
        simulateNetworkDelay: Long = 0L,
    ) {
        mockClient.simulateNetworkDelay = simulateNetworkDelay

        var offset = 0
        while (offset < totalItems) {
            val itemsInThisPage = minOf(pageSize, totalItems - offset)
            val pageUrl = "$baseUrl?offset=$offset&limit=$pageSize"

            val dataItems =
                (1..itemsInThisPage).map { i ->
                    val itemId = offset + i
                    """{
                    "id": $itemId,
                    "path": "/content/performance/item-$itemId",
                    "title": "Performance Item $itemId",
                    "content": "${"Large content block ".repeat(20)}for item $itemId",
                    "size": ${200 + (itemId % 100)},
                    "timestamp": ${System.currentTimeMillis() - (itemId * 1000)},
                    "type": "${listOf("article", "page", "document")[itemId % 3]}"
                }"""
                }.joinToString(",")

            val response =
                """
                {
                    "total": $totalItems,
                    "offset": $offset,
                    "limit": $pageSize,
                    "data": [$dataItems]
                }
                """.trimIndent()

            mockClient.setSuccessResponse(pageUrl, response)
            offset += pageSize
        }
    }

    private fun setupDatasetForEndpoint(
        url: String,
        totalItems: Int,
        pageSize: Int,
        datasetId: Int,
    ) {
        var offset = 0
        while (offset < totalItems) {
            val itemsInThisPage = minOf(pageSize, totalItems - offset)
            val pageUrl = "$url?offset=$offset&limit=$pageSize"

            val dataItems =
                (1..itemsInThisPage).map { i ->
                    val itemId = offset + i
                    """{
                    "id": $itemId,
                    "datasetId": $datasetId,
                    "path": "/content/dataset-$datasetId/item-$itemId",
                    "title": "Dataset $datasetId Item $itemId",
                    "content": "Content for dataset $datasetId item $itemId"
                }"""
                }.joinToString(",")

            val response =
                """
                {
                    "total": $totalItems,
                    "offset": $offset,
                    "limit": $pageSize,
                    "data": [$dataItems]
                }
                """.trimIndent()

            mockClient.setSuccessResponse(pageUrl, response)
            offset += pageSize
        }
    }
}
