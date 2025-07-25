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
import live.aem.koffetch.FFetchEntry
import live.aem.koffetch.TestDataGenerator
import live.aem.koffetch.extensions.all
import live.aem.koffetch.extensions.first
import live.aem.koffetch.extensions.count
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
}
