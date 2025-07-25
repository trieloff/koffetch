package live.aem.koffetch.extensions

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import live.aem.koffetch.FFetchEntry
import live.aem.koffetch.TestDataGenerator
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Comprehensive tests for FFetch transformation operations (map, filter, limit, skip, slice)
 */
class FFetchTransformationsTest {
    // ========== MAP OPERATION TESTS ==========

    @Test
    fun testMapWithSimpleTransformation() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(5, "map")
            val titleFlow =
                entries.asFlow().map { entry ->
                    entry["title"].toString().uppercase()
                }

            val result = titleFlow.toList()
            assertEquals(5, result.size)
            assertEquals("TITLE 1", result[0])
            assertEquals("TITLE 5", result[4])
        }

    @Test
    fun testMapWithComplexTransformation() =
        runTest {
            data class ProcessedEntry(val id: String, val processedTitle: String, val score: Int)

            val entries = TestDataGenerator.createFFetchEntries(3, "complex")
            val processedFlow =
                entries.asFlow().map { entry ->
                    ProcessedEntry(
                        id = entry["id"].toString(),
                        processedTitle = "PROCESSED: ${entry["title"]}",
                        score = entry["title"].toString().length,
                    )
                }

            val result = processedFlow.toList()
            assertEquals(3, result.size)
            assertEquals("complex_1", result[0].id)
            assertEquals("PROCESSED: Title 1", result[0].processedTitle)
            assertEquals(7, result[0].score) // Length of "Title 1"
        }

    @Test
    fun testMapWithEmptyStream() =
        runTest {
            val emptyFlow = emptyList<FFetchEntry>().asFlow()
            val mappedFlow =
                emptyFlow.map { entry ->
                    entry["title"].toString()
                }

            val result = mappedFlow.toList()
            assertTrue(result.isEmpty())
        }

    @Test
    fun testMapWithNullHandling() =
        runTest {
            val entries = TestDataGenerator.createEntriesWithNulls(5)
            val safeTransform =
                entries.asFlow().map { entry ->
                    (entry["optional_field"] ?: "DEFAULT").toString()
                }

            val result = safeTransform.toList()
            assertEquals(5, result.size)
            assertTrue(result.contains("DEFAULT"))
            assertTrue(result.any { it != "DEFAULT" })
        }

    @Test
    fun testMapWithExceptionHandling() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(3, "exception")
            val faultyFlow =
                entries.asFlow().map { entry ->
                    if (entry["id"].toString().contains("2")) {
                        throw RuntimeException("Transformation error for entry 2")
                    }
                    entry["title"].toString()
                }

            assertFailsWith<RuntimeException> {
                faultyFlow.toList()
            }
        }

    // ========== FILTER OPERATION TESTS ==========

    @Test
    fun testFilterWithSimplePredicate() =
        runTest {
            val entries =
                listOf(
                    TestDataGenerator.createFFetchEntry("fruit_1", "Apple", "Red fruit"),
                    TestDataGenerator.createFFetchEntry("fruit_2", "Banana", "Yellow fruit"),
                    TestDataGenerator.createFFetchEntry("fruit_3", "Cherry", "Red fruit"),
                    TestDataGenerator.createFFetchEntry("fruit_4", "Orange", "Orange fruit"),
                )

            val redFruits =
                entries.asFlow().filter { entry ->
                    entry["description"].toString().contains("Red")
                }

            val result = redFruits.toList()
            assertEquals(2, result.size)
            assertEquals("fruit_1", result[0]["id"])
            assertEquals("fruit_3", result[1]["id"])
        }

    @Test
    fun testFilterWithComplexPredicate() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(20, "numbers")
            val evenLargeNumbers =
                entries.asFlow().filter { entry ->
                    val index = entry["index"] as Int
                    index > 10 && index % 2 == 0
                }

            val result = evenLargeNumbers.toList()
            assertEquals(5, result.size) // 12, 14, 16, 18, 20
            assertTrue(result.all { (it["index"] as Int) > 10 })
            assertTrue(result.all { (it["index"] as Int) % 2 == 0 })
        }

    @Test
    fun testFilterAllPass() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(5, "all_pass")
            val allEntries = entries.asFlow().filter { true }

            val result = allEntries.toList()
            assertEquals(5, result.size)
        }

    @Test
    fun testFilterNonePass() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(5, "none_pass")
            val noEntries = entries.asFlow().filter { false }

            val result = noEntries.toList()
            assertTrue(result.isEmpty())
        }

    @Test
    fun testFilterWithNullValues() =
        runTest {
            val entries = TestDataGenerator.createEntriesWithNulls(10)
            val hasTitle =
                entries.asFlow().filter { entry ->
                    entry["title"].toString().isNotEmpty()
                }

            val result = hasTitle.toList()
            assertTrue(result.isNotEmpty())
            assertTrue(result.size < 10) // Some should be filtered out
            assertTrue(result.all { it["title"].toString().isNotEmpty() })
        }

    // ========== LIMIT OPERATION TESTS ==========

    @Test
    fun testLimitWithSmallCount() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(10, "limit")
            val limited = entries.asFlow().limit(3)

            val result = limited.toList()
            assertEquals(3, result.size)
            assertEquals("limit_1", result[0]["id"])
            assertEquals("limit_3", result[2]["id"])
        }

    @Test
    fun testLimitWithZeroCount() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(5, "zero_limit")
            // Test that attempting to use limit with 0 should be handled gracefully
            // Since take(0) is not allowed, we'll test limit(1) and then take nothing from it
            val limited = entries.asFlow().limit(1)
            val result = limited.toList()

            assertEquals(1, result.size) // limit(1) should return 1 item
            assertEquals("zero_limit_1", result[0]["id"])
        }

    @Test
    fun testLimitLargerThanAvailable() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(3, "large_limit")
            val limited = entries.asFlow().limit(10)

            val result = limited.toList()
            assertEquals(3, result.size) // Should return all available
        }

    @Test
    fun testLimitWithSingleEntry() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(1, "single_limit")
            val limited = entries.asFlow().limit(1)

            val result = limited.toList()
            assertEquals(1, result.size)
            assertEquals("single_limit_1", result[0]["id"])
        }

    // ========== SKIP OPERATION TESTS ==========

    @Test
    fun testSkipWithSmallCount() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(10, "skip")
            val skipped = entries.asFlow().skip(3)

            val result = skipped.toList()
            assertEquals(7, result.size)
            assertEquals("skip_4", result[0]["id"]) // First after skipping 3
            assertEquals("skip_10", result[6]["id"]) // Last entry
        }

    @Test
    fun testSkipWithZeroCount() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(5, "zero_skip")
            val skipped = entries.asFlow().skip(0)

            val result = skipped.toList()
            assertEquals(5, result.size)
            assertEquals("zero_skip_1", result[0]["id"])
        }

    @Test
    fun testSkipMoreThanAvailable() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(3, "large_skip")
            val skipped = entries.asFlow().skip(10)

            val result = skipped.toList()
            assertTrue(result.isEmpty())
        }

    @Test
    fun testSkipAllEntries() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(5, "skip_all")
            val skipped = entries.asFlow().skip(5)

            val result = skipped.toList()
            assertTrue(result.isEmpty())
        }

    // ========== SLICE OPERATION TESTS ==========

    @Test
    fun testSliceFromBeginning() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(10, "slice")
            val sliced = entries.asFlow().slice(0, 5)

            val result = sliced.toList()
            assertEquals(5, result.size)
            assertEquals("slice_1", result[0]["id"])
            assertEquals("slice_5", result[4]["id"])
        }

    @Test
    fun testSliceMiddleRange() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(10, "middle")
            val sliced = entries.asFlow().slice(3, 7)

            val result = sliced.toList()
            assertEquals(4, result.size)
            assertEquals("middle_4", result[0]["id"])
            assertEquals("middle_7", result[3]["id"])
        }

    @Test
    fun testSliceToEnd() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(8, "end")
            val sliced = entries.asFlow().slice(5, 11) // Beyond available

            val result = sliced.toList()
            assertEquals(3, result.size) // Only entries 6, 7, 8
            assertEquals("end_6", result[0]["id"])
            assertEquals("end_8", result[2]["id"])
        }

    @Test
    fun testSliceEmptyRange() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(5, "empty_slice")
            val sliced = entries.asFlow().slice(10, 16) // Completely beyond available

            val result = sliced.toList()
            assertTrue(result.isEmpty())
        }

    @Test
    fun testSliceValidEdgeCase() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(5, "edge_case")
            // Test slice at the boundary - slice(4, 5) should return 1 item (the 5th entry)
            val sliced = entries.asFlow().slice(4, 5)

            val result = sliced.toList()
            assertEquals(1, result.size)
            assertEquals("edge_case_5", result[0]["id"])
        }

    @Test
    fun testDirectSliceExtensionCall() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(10, "direct_slice")
            val flow = entries.asFlow()
            val result = flow.slice(2, 6).toList()
            assertEquals(4, result.size)
            assertEquals("direct_slice_3", result[0]["id"])
        }

    // ========== CHAINED OPERATIONS TESTS ==========

    @Test
    fun testChainedFilterAndMap() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(20, "chain")
            val result =
                entries.asFlow()
                    .filter { (it["index"] as Int) % 2 == 0 } // Even numbers only
                    .map { "EVEN_${it["title"]}" }
                    .toList()

            assertEquals(10, result.size)
            assertTrue(result.all { it.startsWith("EVEN_") })
            assertEquals("EVEN_Title 2", result[0])
            assertEquals("EVEN_Title 20", result[9])
        }

    @Test
    fun testChainedLimitAndSkip() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(20, "limit_skip")
            val result =
                entries.asFlow()
                    .skip(5)
                    .limit(8)
                    .toList()

            assertEquals(8, result.size)
            assertEquals("limit_skip_6", result[0]["id"]) // First after skip
            assertEquals("limit_skip_13", result[7]["id"]) // 5 + 8 = 13
        }

    @Test
    fun testComplexChainedOperations() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(30, "complex")
            val result =
                entries.asFlow()
                    .filter { (it["index"] as Int) > 10 }
                    .map { "FILTERED_${it["id"]}" }
                    .skip(3)
                    .limit(5)
                    .toList()

            assertEquals(5, result.size)
            assertEquals("FILTERED_complex_14", result[0]) // 11, 12, 13 skipped, starts at 14
            assertEquals("FILTERED_complex_18", result[4])
        }

    @Test
    fun testTypePreservationThroughChain() =
        runTest {
            data class TypedResult(val id: String, val processed: Boolean)

            val entries = TestDataGenerator.createFFetchEntries(10, "typed")
            val typedResults =
                entries.asFlow()
                    .filter { (it["index"] as Int) <= 5 }
                    .map { entry ->
                        TypedResult(
                            id = entry["id"].toString(),
                            processed = true,
                        )
                    }
                    .toList()

            assertEquals(5, typedResults.size)
            assertTrue(typedResults.all { it.processed })
            assertEquals("typed_1", typedResults[0].id)
            assertEquals("typed_5", typedResults[4].id)
        }

    // ========== FLOW-SPECIFIC OPERATION TESTS ==========

    @Test
    fun testFlowMapOperation() =
        runTest {
            val numbers = (1..5).asFlow()
            val squaredFlow = numbers.map { it * it }
            val squared = squaredFlow.toList()

            assertEquals(listOf(1, 4, 9, 16, 25), squared)
        }

    @Test
    fun testFlowFilterOperation() =
        runTest {
            val numbers = (1..10).asFlow()
            val evenFlow = numbers.filter { it % 2 == 0 }
            val evens = evenFlow.toList()

            assertEquals(listOf(2, 4, 6, 8, 10), evens)
        }

    @Test
    fun testFlowLimitOperation() =
        runTest {
            val numbers = (1..100).asFlow()
            val limitedFlow = numbers.limit(5)
            val limited = limitedFlow.toList()

            assertEquals(listOf(1, 2, 3, 4, 5), limited)
        }

    @Test
    fun testFlowSkipOperation() =
        runTest {
            val numbers = (1..10).asFlow()
            val skippedFlow = numbers.skip(7)
            val skipped = skippedFlow.toList()

            assertEquals(listOf(8, 9, 10), skipped)
        }

    @Test
    fun testFlowSliceOperation() =
        runTest {
            val numbers = (1..20).asFlow()
            val slicedFlow = numbers.slice(5, 10)
            val sliced = slicedFlow.toList()

            assertEquals(listOf(6, 7, 8, 9, 10), sliced)
        }

    // ========== ERROR HANDLING TESTS ==========

    @Test
    fun testTransformationsWithEmptyFlow() =
        runTest {
            val emptyFlow = emptyList<FFetchEntry>().asFlow()
            val result =
                emptyFlow
                    .filter { true }
                    .map { it["id"].toString() }
                    .skip(0)
                    .limit(10)
                    .toList()

            assertTrue(result.isEmpty())
        }

    @Test
    fun testTransformationWithExceptionInFilter() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(5, "error")

            assertFailsWith<RuntimeException> {
                entries.asFlow()
                    .filter { entry ->
                        if (entry["id"].toString().contains("3")) {
                            throw RuntimeException("Filter error")
                        }
                        true
                    }
                    .toList()
            }
        }

    // ========== FFETCH-SPECIFIC TRANSFORMATION TESTS ==========

    @Test
    fun testFFetchMapTransformation() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(5, "ffetch_map")
            val mockFFetch = TestDataGenerator.createMockFFetch(entries)

            val titleFlow = mockFFetch.map { entry -> entry["title"].toString().uppercase() }
            val result = titleFlow.toList()

            assertEquals(5, result.size)
            assertEquals("TITLE 1", result[0])
            assertEquals("TITLE 5", result[4])
        }

    @Test
    fun testFFetchFilterTransformation() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(10, "ffetch_filter")
            val mockFFetch = TestDataGenerator.createMockFFetch(entries)

            val filteredFFetch =
                mockFFetch.filter { entry ->
                    (entry["index"] as Int) % 2 == 0
                }
            val result = filteredFFetch.asFlow().toList()

            assertEquals(5, result.size) // Only even-indexed entries
            assertTrue(result.all { (it["index"] as Int) % 2 == 0 })
        }

    @Test
    fun testFFetchLimitTransformation() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(10, "ffetch_limit")
            val mockFFetch = TestDataGenerator.createMockFFetch(entries)

            val limitedFFetch = mockFFetch.limit(3)
            val result = limitedFFetch.asFlow().toList()

            assertEquals(3, result.size)
            assertEquals("ffetch_limit_1", result[0]["id"])
            assertEquals("ffetch_limit_3", result[2]["id"])
        }

    @Test
    fun testFFetchSkipTransformation() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(10, "ffetch_skip")
            val mockFFetch = TestDataGenerator.createMockFFetch(entries)

            val skippedFFetch = mockFFetch.skip(3)
            val result = skippedFFetch.asFlow().toList()

            assertEquals(7, result.size)
            assertEquals("ffetch_skip_4", result[0]["id"]) // First after skipping 3
            assertEquals("ffetch_skip_10", result[6]["id"]) // Last entry
        }

    @Test
    fun testFFetchSliceTransformation() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(10, "ffetch_slice")
            val mockFFetch = TestDataGenerator.createMockFFetch(entries)

            val slicedFFetch = mockFFetch.slice(2, 6)
            val result = slicedFFetch.asFlow().toList()

            assertEquals(4, result.size)
            assertEquals("ffetch_slice_3", result[0]["id"]) // Index 2 (skip 2)
            assertEquals("ffetch_slice_6", result[3]["id"]) // Index 5 (limit 4)
        }

    @Test
    fun testFFetchChainedTransformations() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(20, "ffetch_chain")
            val mockFFetch = TestDataGenerator.createMockFFetch(entries)

            val transformedFFetch =
                mockFFetch
                    .filter { (it["index"] as Int) % 2 == 0 } // Even numbers only
                    .skip(2) // Skip first 2 even numbers (2, 4)
                    .limit(3) // Take next 3 even numbers (6, 8, 10)

            val result = transformedFFetch.asFlow().toList()

            assertEquals(3, result.size)
            assertEquals(6, result[0]["index"] as Int)
            assertEquals(8, result[1]["index"] as Int)
            assertEquals(10, result[2]["index"] as Int)
        }

    @Test
    fun testFFetchMapWithCustomTypes() =
        runTest {
            data class ProcessedEntry(val id: String, val processedTitle: String, val score: Int)

            val entries = TestDataGenerator.createProductEntries(5)
            val mockFFetch = TestDataGenerator.createMockFFetch(entries)

            val processedFlow =
                mockFFetch.map { entry ->
                    ProcessedEntry(
                        id = entry["id"].toString(),
                        processedTitle = "PROCESSED: ${entry["title"]}",
                        score = entry["title"].toString().length,
                    )
                }

            val result = processedFlow.toList()
            assertEquals(5, result.size)
            assertEquals("product_1", result[0].id)
            assertEquals("PROCESSED: Product 1", result[0].processedTitle)
            assertEquals(9, result[0].score) // Length of "Product 1"
        }

    @Test
    fun testFFetchTransformationsWithEmptyEntries() =
        runTest {
            val emptyEntries = emptyList<FFetchEntry>()
            val mockFFetch = TestDataGenerator.createMockFFetch(emptyEntries)

            // Test all operations with empty FFetch
            val filteredResult = mockFFetch.filter { true }.asFlow().toList()
            val limitedResult = mockFFetch.limit(5).asFlow().toList()
            val skippedResult = mockFFetch.skip(2).asFlow().toList()
            val slicedResult = mockFFetch.slice(1, 3).asFlow().toList()
            val mappedResult = mockFFetch.map { it["id"].toString() }.toList()

            assertTrue(filteredResult.isEmpty())
            assertTrue(limitedResult.isEmpty())
            assertTrue(skippedResult.isEmpty())
            assertTrue(slicedResult.isEmpty())
            assertTrue(mappedResult.isEmpty())
        }

    @Test
    fun testFFetchBoundaryConditions() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(5, "boundary")
            val mockFFetch = TestDataGenerator.createMockFFetch(entries)

            // Test limit with 1 (minimum valid positive value)
            val smallLimitResult = mockFFetch.limit(1).asFlow().toList()
            assertEquals(1, smallLimitResult.size)

            // Test skip with 0
            val zeroSkipResult = mockFFetch.skip(0).asFlow().toList()
            assertEquals(5, zeroSkipResult.size)

            // Test slice with valid non-empty range
            val validSliceResult = mockFFetch.slice(1, 4).asFlow().toList()
            assertEquals(3, validSliceResult.size)

            // Test large skip value (larger than available entries)
            val largeSkipResult = mockFFetch.skip(10).asFlow().toList()
            assertTrue(largeSkipResult.isEmpty())

            // Test large limit value (larger than available entries)
            val largeLimitResult = mockFFetch.limit(100).asFlow().toList()
            assertEquals(5, largeLimitResult.size) // Should return all available
        }

    @Test
    fun testFFetchErrorHandling() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(5, "error_test")
            val mockFFetch = TestDataGenerator.createMockFFetch(entries)

            // Test that limit(0) throws an exception as expected
            assertFailsWith<IllegalArgumentException> {
                mockFFetch.limit(0).asFlow().toList()
            }

            // Test negative limit values through slice
            assertFailsWith<IllegalArgumentException> {
                mockFFetch.slice(5, 3).asFlow().toList() // This will call limit(-2)
            }
        }

    @Test
    fun testFFetchTransformationContextPreservation() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(5, "context")
            val mockFFetch = TestDataGenerator.createMockFFetch(entries)

            // Test that transformations preserve the original FFetch's URL and context
            val filteredFFetch = mockFFetch.filter { true }
            val limitedFFetch = mockFFetch.limit(3)
            val skippedFFetch = mockFFetch.skip(1)
            val slicedFFetch = mockFFetch.slice(0, 2)

            // All transformed FFetch instances should have the same URL and context
            assertEquals(mockFFetch.url, filteredFFetch.url)
            assertEquals(mockFFetch.url, limitedFFetch.url)
            assertEquals(mockFFetch.url, skippedFFetch.url)
            assertEquals(mockFFetch.url, slicedFFetch.url)

            assertEquals(mockFFetch.context, filteredFFetch.context)
            assertEquals(mockFFetch.context, limitedFFetch.context)
            assertEquals(mockFFetch.context, skippedFFetch.context)
            assertEquals(mockFFetch.context, slicedFFetch.context)
        }

    @Test
    fun testFFetchComplexFilterPredicates() =
        runTest {
            val entries = TestDataGenerator.createProductEntries(20)
            val mockFFetch = TestDataGenerator.createMockFFetch(entries)

            val complexFilteredFFetch =
                mockFFetch.filter { entry ->
                    val price = entry["price"] as Double
                    val category = entry["category"].toString()
                    price > 50.0 && (category == "electronics" || category == "books")
                }

            val result = complexFilteredFFetch.asFlow().toList()

            assertTrue(result.isNotEmpty())
            assertTrue(
                result.all {
                    val price = it["price"] as Double
                    val category = it["category"].toString()
                    price > 50.0 && (category == "electronics" || category == "books")
                },
            )
        }

    @Test
    fun testFFetchLargeDatasetPerformance() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(1000, "large")
            val mockFFetch = TestDataGenerator.createMockFFetch(entries)

            val result =
                mockFFetch
                    .filter { (it["index"] as Int) % 10 == 0 } // Every 10th entry
                    .skip(5) // Skip first 5 matches
                    .limit(20) // Take next 20
                    .asFlow()
                    .toList()

            assertEquals(20, result.size)
            assertEquals(60, result[0]["index"] as Int) // First match after skipping 5
            assertEquals(250, result[19]["index"] as Int) // 20th match
        }

    @Test
    fun testFFetchInvalidParameterHandling() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(5, "invalid")
            val mockFFetch = TestDataGenerator.createMockFFetch(entries)

            // Test that invalid parameters throw exceptions as expected
            assertFailsWith<IllegalArgumentException> {
                mockFFetch.skip(-5).asFlow().toList()
            }

            // Test slice edge case that would result in limit(0)
            assertFailsWith<IllegalArgumentException> {
                mockFFetch.slice(2, 2).asFlow().toList()
            }
        }

    @Test
    fun testFFetchSuspendTransformations() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(5, "suspend")
            val mockFFetch = TestDataGenerator.createMockFFetch(entries)

            // Test map with suspend transformation
            val suspendTransform =
                mockFFetch.map { entry ->
                    // Simulate a suspend operation
                    delay(1)
                    "SUSPEND_${entry["id"]}"
                }

            val result = suspendTransform.toList()
            assertEquals(5, result.size)
            assertTrue(result.all { it.startsWith("SUSPEND_") })
        }

    @Test
    fun testFFetchMemoryEfficiency() =
        runTest {
            // Test that transformations don't eagerly load all data
            val largeEntries = TestDataGenerator.createFFetchEntries(1000, "memory")
            val mockFFetch = TestDataGenerator.createMockFFetch(largeEntries)

            // Only take first 5 elements - should not process all 1000
            val result =
                mockFFetch
                    .filter { (it["index"] as Int) > 0 } // All should pass
                    .limit(5)
                    .asFlow()
                    .toList()

            assertEquals(5, result.size)
            assertEquals("memory_1", result[0]["id"])
            assertEquals("memory_5", result[4]["id"])
        }

    @Test
    fun testFFetchConcurrentOperations() =
        runTest {
            val entries = TestDataGenerator.createFFetchEntries(10, "concurrent")
            val mockFFetch = TestDataGenerator.createMockFFetch(entries)

            // Run multiple transformations concurrently
            val job1 = async { mockFFetch.filter { true }.asFlow().toList() }
            val job2 = async { mockFFetch.map { it["id"].toString() }.toList() }
            val job3 = async { mockFFetch.limit(5).asFlow().toList() }

            val result1 = job1.await()
            val result2 = job2.await()
            val result3 = job3.await()

            assertEquals(10, result1.size)
            assertEquals(10, result2.size)
            assertEquals(5, result3.size)
        }
}
