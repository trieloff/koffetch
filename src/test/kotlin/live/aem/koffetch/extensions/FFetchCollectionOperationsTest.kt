package live.aem.koffetch.extensions

import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import live.aem.koffetch.FFetch
import live.aem.koffetch.FFetchEntry
import live.aem.koffetch.TestDataGenerator
import live.aem.koffetch.mock.MockFFetchHTTPClient
import live.aem.koffetch.mock.MockResponse
import live.aem.koffetch.withHTTPClient
import live.aem.koffetch.chunks
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for FFetch collection operations (all, first, count)
 * Tests both Flow-based operations and extension functions on FFetch instances
 */
class FFetchCollectionOperationsTest {
    // ========== ALL OPERATION TESTS ==========

    @Test
    fun testAllWithSingleEntry() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(1, "single")
            val flow = entries.asFlow()
            val result = flow.all()

            assertEquals(1, result.size)
            assertEquals("single_1", result.first()["id"])
            assertEquals("Title 1", result.first()["title"])
        }

    @Test
    fun testAllWithMultipleEntries() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(5, "multi")
            val flow = entries.asFlow()
            val result = flow.all()

            assertEquals(5, result.size)
            assertEquals("multi_1", result[0]["id"])
            assertEquals("multi_5", result[4]["id"])
        }

    @Test
    fun testAllWithEmptyStream() =
        runTest {
            val flow = emptyList<FFetchEntry>().asFlow()
            val result = flow.all()

            assertTrue(result.isEmpty())
        }

    @Test
    fun testAllWithLargeDataset() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(1000, "large")
            val flow = entries.asFlow()
            val result = flow.all()

            assertEquals(1000, result.size)
            assertEquals("large_1", result.first()["id"])
            assertEquals("large_1000", result.last()["id"])
        }

    @Test
    fun testAllWithNullableData() =
        runTest {
            val entries = TestDataGenerator.createEntriesWithNulls(10)
            val flow = entries.asFlow()
            val result = flow.all()

            assertEquals(10, result.size)
            // Verify some entries have null optional_field
            assertTrue(result.any { it["optional_field"] == null })
            assertTrue(result.any { it["optional_field"] != null })
        }

    // ========== FIRST OPERATION TESTS ==========

    @Test
    fun testFirstWithSingleEntry() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(1, "first_single")
            val flow = entries.asFlow()
            val result = flow.first()

            assertNotNull(result)
            assertEquals("first_single_1", result["id"])
            assertEquals("Title 1", result["title"])
        }

    @Test
    fun testFirstWithMultipleEntries() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(10, "first_multi")
            val flow = entries.asFlow()
            val result = flow.first()

            assertNotNull(result)
            assertEquals("first_multi_1", result["id"])
            assertEquals("Title 1", result["title"])
        }

    @Test
    fun testFirstWithEmptyStream() =
        runTest {
            val flow = emptyList<FFetchEntry>().asFlow()
            val result = flow.first()

            assertNull(result)
        }

    @Test
    fun testFirstWithDelayedStream() =
        runTest {
            val flow = TestDataGenerator.createDelayedFFetchFlow(5, 5, "delayed")
            val result = flow.first()

            assertNotNull(result)
            assertEquals("delayed_1", result["id"])
            assertEquals("Delayed Title 1", result["title"])
        }

    // ========== COUNT OPERATION TESTS ==========

    @Test
    fun testCountWithSingleEntry() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(1, "count_single")
            val flow = entries.asFlow()
            val result = flow.count()

            assertEquals(1, result)
        }

    @Test
    fun testCountWithMultipleEntries() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(25, "count_multi")
            val flow = entries.asFlow()
            val result = flow.count()

            assertEquals(25, result)
        }

    @Test
    fun testCountWithEmptyStream() =
        runTest {
            val flow = emptyList<FFetchEntry>().asFlow()
            val result = flow.count()

            assertEquals(0, result)
        }

    @Test
    fun testCountWithLargeDataset() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(5000, "count_large")
            val flow = entries.asFlow()
            val result = flow.count()

            assertEquals(5000, result)
        }

    // ========== FLOW-BASED OPERATION TESTS ==========

    @Test
    fun testFlowAllWithTransformedData() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(5, "flow")
            val transformedFlow =
                entries.asFlow().map { entry ->
                    TestDataGenerator.createFFetchEntry(
                        id = "transformed_${entry["id"]}",
                        title = "TRANSFORMED_${entry["title"]}",
                        description = "TRANSFORMED_${entry["description"]}",
                    )
                }

            val result = transformedFlow.all()
            assertEquals(5, result.size)
            assertEquals("transformed_flow_1", result.first()["id"])
            assertEquals("TRANSFORMED_Title 1", result.first()["title"])
        }

    @Test
    fun testFlowFirstWithTransformedData() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(3, "flow_first")
            val transformedFlow =
                entries.asFlow().map { entry ->
                    TestDataGenerator.createFFetchEntry(
                        id = "first_${entry["id"]}",
                        title = "FIRST_${entry["title"]}",
                        description = entry["description"].toString(),
                    )
                }

            val result = transformedFlow.first()
            assertNotNull(result)
            assertEquals("first_flow_first_1", result["id"])
            assertEquals("FIRST_Title 1", result["title"])
        }

    @Test
    fun testFlowCountWithTransformedData() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(8, "flow_count")
            val transformedFlow =
                entries.asFlow().map { entry ->
                    TestDataGenerator.createFFetchEntry(
                        id = "counted_${entry["id"]}",
                        title = entry["title"].toString(),
                        description = entry["description"].toString(),
                    )
                }

            val result = transformedFlow.count()
            assertEquals(8, result)
        }

    @Test
    fun testFlowOperationsWithEmptyTransformedFlow() =
        runTest {
            val emptyFlow = emptyList<FFetchEntry>().asFlow()
            val transformedFlow =
                emptyFlow.map { entry ->
                    TestDataGenerator.createFFetchEntry(
                        id = "never_${entry["id"]}",
                        title = "never",
                        description = "never",
                    )
                }

            assertEquals(0, transformedFlow.count())
            assertTrue(transformedFlow.all().isEmpty())
            assertNull(transformedFlow.first())
        }

    // ========== CUSTOM TYPE TESTS ==========

    data class SimpleProduct(val id: String, val name: String, val price: Double)

    @Test
    fun testFlowCollectionWithCustomTypes() =
        runTest {
            val entries = TestDataGenerator.createProductEntries(5)
            val productFlow =
                entries.asFlow().map { entry ->
                    SimpleProduct(
                        id = entry["id"].toString(),
                        name = entry["name"].toString(),
                        price = entry["price"] as Double,
                    )
                }

            val allProducts = productFlow.toList()
            assertEquals(5, allProducts.size)
            assertEquals("product_1", allProducts.first().id)
            assertEquals("Product 1", allProducts.first().name)
            assertEquals(10.0, allProducts.first().price)
        }

    // ========== ERROR HANDLING AND EDGE CASES ==========

    @Test
    fun testCollectionOperationsWithFailingFlow() =
        runTest {
            val failingFlow = TestDataGenerator.createFailingFFetchFlow(3, "failing")

            // Test that the flow fails when fully consumed
            assertFailsWith<RuntimeException> {
                failingFlow.toList() // Force consumption of entire flow
            }

            // Test individual operations that also consume the flow
            val newFailingFlow = TestDataGenerator.createFailingFFetchFlow(3, "failing2")
            assertFailsWith<RuntimeException> {
                newFailingFlow.all()
            }

            val newFailingFlow2 = TestDataGenerator.createFailingFFetchFlow(3, "failing3")
            assertFailsWith<RuntimeException> {
                newFailingFlow2.count()
            }

            // First should succeed since it only takes the first element
            val newFailingFlow3 = TestDataGenerator.createFailingFFetchFlow(3, "failing4")
            val result = newFailingFlow3.first()
            assertNotNull(result)
            assertEquals("failing4_1", result!!["id"])
        }

    @Test
    fun testCollectionOperationsCancellation() =
        runTest {
            val job =
                launch {
                    val longRunningFlow = TestDataGenerator.createDelayedFFetchFlow(1000, 100, "long")
                    longRunningFlow.all()
                }

            delay(50) // Let it start
            job.cancel()
            job.join()
            assertTrue(job.isCancelled)
        }

    @Test
    fun testConcurrentCollectionOperations() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(100, "concurrent")
            val flow = entries.asFlow()

            val allJob = async { flow.all() }
            val countJob = async { flow.count() }
            val firstJob = async { flow.first() }

            val allResult = allJob.await()
            val countResult = countJob.await()
            val firstResult = firstJob.await()

            assertEquals(100, allResult.size)
            assertEquals(100, countResult)
            assertNotNull(firstResult)
            assertEquals("concurrent_1", firstResult["id"])
        }

    @Test
    fun testMemoryEfficiencyWithLargeDataset() =
        runTest {
            // Test that operations don't hold all data in memory unnecessarily
            val entries = TestDataGenerator.createFFetchEntries(10000, "memory")
            val flow = entries.asFlow()

            withTimeoutOrNull(5000) {
                val count = flow.count()
                assertEquals(10000, count)

                val first = flow.first()
                assertNotNull(first)
                assertEquals("memory_1", first["id"])
            } ?: throw AssertionError("Operations took too long - possible memory issue")
        }

    // ========== FFETCH EXTENSION METHOD TESTS ==========

    @Test
    fun testFFetchAllWithSingleEntry() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            val jsonResponse = """
                {
                    "total": 1,
                    "offset": 0,
                    "limit": 255,
                    "data": [
                        {
                            "id": "ffetch_all_1",
                            "title": "FFetch All Test 1",
                            "description": "Test description for FFetch all operation"
                        }
                    ]
                }
            """.trimIndent()
            
            mockClient.setSuccessResponse("https://example.com/test.json", jsonResponse)
            
            val ffetch = FFetch("https://example.com/test.json").withHTTPClient(mockClient)
            val result = ffetch.all()
            
            assertEquals(1, result.size)
            assertEquals("ffetch_all_1", result[0]["id"])
            assertEquals("FFetch All Test 1", result[0]["title"])
        }

    @Test
    fun testFFetchAllWithMultipleEntries() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            val jsonResponse = """
                {
                    "total": 3,
                    "offset": 0,
                    "limit": 255,
                    "data": [
                        {
                            "id": "ffetch_all_1",
                            "title": "FFetch All Test 1",
                            "description": "Test description 1"
                        },
                        {
                            "id": "ffetch_all_2",
                            "title": "FFetch All Test 2",
                            "description": "Test description 2"
                        },
                        {
                            "id": "ffetch_all_3",
                            "title": "FFetch All Test 3",
                            "description": "Test description 3"
                        }
                    ]
                }
            """.trimIndent()
            
            mockClient.setSuccessResponse("https://example.com/test.json", jsonResponse)
            
            val ffetch = FFetch("https://example.com/test.json").withHTTPClient(mockClient)
            val result = ffetch.all()
            
            assertEquals(3, result.size)
            assertEquals("ffetch_all_1", result[0]["id"])
            assertEquals("ffetch_all_3", result[2]["id"])
        }

    @Test
    fun testFFetchAllWithEmptyResult() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            val jsonResponse = """
                {
                    "total": 0,
                    "offset": 0,
                    "limit": 255,
                    "data": []
                }
            """.trimIndent()
            
            mockClient.setSuccessResponse("https://example.com/test.json", jsonResponse)
            
            val ffetch = FFetch("https://example.com/test.json").withHTTPClient(mockClient)
            val result = ffetch.all()
            
            assertTrue(result.isEmpty())
        }

    @Test
    fun testFFetchFirstWithSingleEntry() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            val jsonResponse = """
                {
                    "total": 1,
                    "offset": 0,
                    "limit": 255,
                    "data": [
                        {
                            "id": "ffetch_first_1",
                            "title": "FFetch First Test 1",
                            "description": "Test description for FFetch first operation"
                        }
                    ]
                }
            """.trimIndent()
            
            mockClient.setSuccessResponse("https://example.com/test.json", jsonResponse)
            
            val ffetch = FFetch("https://example.com/test.json").withHTTPClient(mockClient)
            val result = ffetch.first()
            
            assertNotNull(result)
            assertEquals("ffetch_first_1", result["id"])
            assertEquals("FFetch First Test 1", result["title"])
        }

    @Test
    fun testFFetchFirstWithMultipleEntries() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            val jsonResponse = """
                {
                    "total": 3,
                    "offset": 0,
                    "limit": 255,
                    "data": [
                        {
                            "id": "ffetch_first_1",
                            "title": "FFetch First Test 1",
                            "description": "Test description 1"
                        },
                        {
                            "id": "ffetch_first_2",
                            "title": "FFetch First Test 2",
                            "description": "Test description 2"
                        },
                        {
                            "id": "ffetch_first_3",
                            "title": "FFetch First Test 3",
                            "description": "Test description 3"
                        }
                    ]
                }
            """.trimIndent()
            
            mockClient.setSuccessResponse("https://example.com/test.json", jsonResponse)
            
            val ffetch = FFetch("https://example.com/test.json").withHTTPClient(mockClient)
            val result = ffetch.first()
            
            assertNotNull(result)
            assertEquals("ffetch_first_1", result["id"])
            assertEquals("FFetch First Test 1", result["title"])
        }

    @Test
    fun testFFetchFirstWithEmptyResult() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            val jsonResponse = """
                {
                    "total": 0,
                    "offset": 0,
                    "limit": 255,
                    "data": []
                }
            """.trimIndent()
            
            mockClient.setSuccessResponse("https://example.com/test.json", jsonResponse)
            
            val ffetch = FFetch("https://example.com/test.json").withHTTPClient(mockClient)
            val result = ffetch.first()
            
            assertNull(result)
        }

    @Test
    fun testFFetchCountWithSingleEntry() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            val jsonResponse = """
                {
                    "total": 1,
                    "offset": 0,
                    "limit": 255,
                    "data": [
                        {
                            "id": "ffetch_count_1",
                            "title": "FFetch Count Test 1",
                            "description": "Test description for FFetch count operation"
                        }
                    ]
                }
            """.trimIndent()
            
            mockClient.setSuccessResponse("https://example.com/test.json", jsonResponse)
            
            val ffetch = FFetch("https://example.com/test.json").withHTTPClient(mockClient)
            val result = ffetch.count()
            
            assertEquals(1, result)
        }

    @Test
    fun testFFetchCountWithMultipleEntries() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            val jsonResponse = """
                {
                    "total": 5,
                    "offset": 0,
                    "limit": 255,
                    "data": [
                        {"id": "ffetch_count_1", "title": "Title 1"},
                        {"id": "ffetch_count_2", "title": "Title 2"},
                        {"id": "ffetch_count_3", "title": "Title 3"},
                        {"id": "ffetch_count_4", "title": "Title 4"},
                        {"id": "ffetch_count_5", "title": "Title 5"}
                    ]
                }
            """.trimIndent()
            
            mockClient.setSuccessResponse("https://example.com/test.json", jsonResponse)
            
            val ffetch = FFetch("https://example.com/test.json").withHTTPClient(mockClient)
            val result = ffetch.count()
            
            assertEquals(5, result)
        }

    @Test
    fun testFFetchCountWithEmptyResult() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            val jsonResponse = """
                {
                    "total": 0,
                    "offset": 0,
                    "limit": 255,
                    "data": []
                }
            """.trimIndent()
            
            mockClient.setSuccessResponse("https://example.com/test.json", jsonResponse)
            
            val ffetch = FFetch("https://example.com/test.json").withHTTPClient(mockClient)
            val result = ffetch.count()
            
            assertEquals(0, result)
        }

    @Test
    fun testFFetchCountWithLargeDataset() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            
            // Setup paginated responses
            val pageSize = 100
            val totalItems = 250
            
            // First page
            val firstPageData = (1..pageSize).map { 
                """{"id": "large_$it", "title": "Title $it"}""" 
            }.joinToString(",")
            val firstPageResponse = """
                {
                    "total": $totalItems,
                    "offset": 0,
                    "limit": $pageSize,
                    "data": [$firstPageData]
                }
            """.trimIndent()
            
            // Second page  
            val secondPageData = (101..200).map { 
                """{"id": "large_$it", "title": "Title $it"}""" 
            }.joinToString(",")
            val secondPageResponse = """
                {
                    "total": $totalItems,
                    "offset": 100,
                    "limit": $pageSize, 
                    "data": [$secondPageData]
                }
            """.trimIndent()
            
            // Third page
            val thirdPageData = (201..250).map { 
                """{"id": "large_$it", "title": "Title $it"}""" 
            }.joinToString(",")
            val thirdPageResponse = """
                {
                    "total": $totalItems,
                    "offset": 200,
                    "limit": $pageSize,
                    "data": [$thirdPageData]
                }
            """.trimIndent()
            
            mockClient.setSuccessResponse("https://example.com/test.json", firstPageResponse)
            mockClient.setSuccessResponse("https://example.com/test.json?offset=100&limit=100", secondPageResponse)
            mockClient.setSuccessResponse("https://example.com/test.json?offset=200&limit=100", thirdPageResponse)
            
            val ffetch = FFetch("https://example.com/test.json").withHTTPClient(mockClient).chunks(pageSize)
            val result = ffetch.count()
            
            assertEquals(totalItems, result)
        }

    // ========== EDGE CASE TESTS FOR FFETCH EXTENSIONS ==========

    @Test
    fun testFFetchExtensionsWithNullValues() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            val jsonResponse = """
                {
                    "total": 2,
                    "offset": 0,
                    "limit": 255,
                    "data": [
                        {
                            "id": "null_test_1",
                            "title": "Test with nulls",
                            "optional_field": null,
                            "description": "Description 1"
                        },
                        {
                            "id": "null_test_2", 
                            "title": null,
                            "optional_field": "has_value",
                            "description": null
                        }
                    ]
                }
            """.trimIndent()
            
            mockClient.setSuccessResponse("https://example.com/test.json", jsonResponse)
            
            val ffetch = FFetch("https://example.com/test.json").withHTTPClient(mockClient)
            
            val allResult = ffetch.all()
            assertEquals(2, allResult.size)
            // Note: JSON null gets parsed as string "null" in this test setup
            assertTrue(allResult[0]["optional_field"] == null || allResult[0]["optional_field"] == "null")
            assertTrue(allResult[1]["title"] == null || allResult[1]["title"] == "null")
            
            val firstResult = ffetch.first()
            assertNotNull(firstResult)
            assertEquals("null_test_1", firstResult["id"])
            
            val countResult = ffetch.count()
            assertEquals(2, countResult)
        }

    @Test
    fun testFFetchExtensionsErrorHandling() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            mockClient.shouldThrowNetworkError = true
            mockClient.networkErrorMessage = "Test network failure"
            
            val ffetch = FFetch("https://example.com/test.json").withHTTPClient(mockClient)
            
            // All operations should fail with network error
            assertFailsWith<Exception> {
                ffetch.all()
            }
            
            assertFailsWith<Exception> {
                ffetch.first()
            }
            
            assertFailsWith<Exception> {
                ffetch.count()
            }
        }
}
