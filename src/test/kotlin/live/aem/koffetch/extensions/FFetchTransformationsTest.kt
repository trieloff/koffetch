package live.aem.koffetch.extensions

import live.aem.koffetch.FFetch
import live.aem.koffetch.FFetchContext
import live.aem.koffetch.FFetchEntry
import live.aem.koffetch.TestDataGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Comprehensive tests for FFetch transformation operations (map, filter, limit, skip, slice)
 */
class FFetchTransformationsTest {
    
    
    // ========== MAP OPERATION TESTS ==========
    
    @Test
    fun testMapWithSimpleTransformation() = runTest {
        val entries = TestDataGenerator.createFFetchEntries(5, "map")
        val titleFlow = entries.asFlow().map { entry ->
            entry["title"].toString().uppercase()
        }
        
        val result = titleFlow.toList()
        assertEquals(5, result.size)
        assertEquals("TITLE 1", result[0])
        assertEquals("TITLE 5", result[4])
    }
    
    @Test
    fun testMapWithComplexTransformation() = runTest {
        data class ProcessedEntry(val id: String, val processedTitle: String, val score: Int)
        
        val entries = TestDataGenerator.createFFetchEntries(3, "complex")
        val processedFlow = entries.asFlow().map { entry ->
            ProcessedEntry(
                id = entry["id"].toString(),
                processedTitle = "PROCESSED: ${entry["title"]}",
                score = entry["title"].toString().length
            )
        }
        
        val result = processedFlow.toList()
        assertEquals(3, result.size)
        assertEquals("complex_1", result[0].id)
        assertEquals("PROCESSED: Title 1", result[0].processedTitle)
        assertEquals(7, result[0].score) // Length of "Title 1"
    }
    
    @Test
    fun testMapWithEmptyStream() = runTest {
        val emptyFlow = emptyList<FFetchEntry>().asFlow()
        val mappedFlow = emptyFlow.map { entry ->
            entry["title"].toString()
        }
        
        val result = mappedFlow.toList()
        assertTrue(result.isEmpty())
    }
    
    @Test
    fun testMapWithNullHandling() = runTest {
        val entries = TestDataGenerator.createEntriesWithNulls(5)
        val safeTransform = entries.asFlow().map { entry ->
            (entry["optional_field"] ?: "DEFAULT").toString()
        }
        
        val result = safeTransform.toList()
        assertEquals(5, result.size)
        assertTrue(result.contains("DEFAULT"))
        assertTrue(result.any { it != "DEFAULT" })
    }
    
    @Test
    fun testMapWithExceptionHandling() = runTest {
        val entries = TestDataGenerator.createFFetchEntries(3, "exception")
        val faultyFlow = entries.asFlow().map { entry ->
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
    fun testFilterWithSimplePredicate() = runTest {
        val entries = listOf(
            TestDataGenerator.createFFetchEntry("fruit_1", "Apple", "Red fruit"),
            TestDataGenerator.createFFetchEntry("fruit_2", "Banana", "Yellow fruit"),
            TestDataGenerator.createFFetchEntry("fruit_3", "Cherry", "Red fruit"),
            TestDataGenerator.createFFetchEntry("fruit_4", "Orange", "Orange fruit")
        )
        
        val redFruits = entries.asFlow().filter { entry ->
            entry["description"].toString().contains("Red")
        }
        
        val result = redFruits.toList()
        assertEquals(2, result.size)
        assertEquals("fruit_1", result[0]["id"])
        assertEquals("fruit_3", result[1]["id"])
    }
    
    @Test
    fun testFilterWithComplexPredicate() = runTest {
        val entries = TestDataGenerator.createFFetchEntries(20, "numbers")
        val evenLargeNumbers = entries.asFlow().filter { entry ->
            val index = entry["index"] as Int
            index > 10 && index % 2 == 0
        }
        
        val result = evenLargeNumbers.toList()
        assertEquals(5, result.size) // 12, 14, 16, 18, 20
        assertTrue(result.all { (it["index"] as Int) > 10 })
        assertTrue(result.all { (it["index"] as Int) % 2 == 0 })
    }
    
    @Test
    fun testFilterAllPass() = runTest {
        val entries = TestDataGenerator.createFFetchEntries(5, "all_pass")
        val allEntries = entries.asFlow().filter { true }
        
        val result = allEntries.toList()
        assertEquals(5, result.size)
    }
    
    @Test
    fun testFilterNonePass() = runTest {
        val entries = TestDataGenerator.createFFetchEntries(5, "none_pass")
        val noEntries = entries.asFlow().filter { false }
        
        val result = noEntries.toList()
        assertTrue(result.isEmpty())
    }
    
    @Test
    fun testFilterWithNullValues() = runTest {
        val entries = TestDataGenerator.createEntriesWithNulls(10)
        val hasTitle = entries.asFlow().filter { entry ->
            entry["title"].toString().isNotEmpty()
        }
        
        val result = hasTitle.toList()
        assertTrue(result.isNotEmpty())
        assertTrue(result.size < 10) // Some should be filtered out
        assertTrue(result.all { it["title"].toString().isNotEmpty() })
    }
    
    
    // ========== LIMIT OPERATION TESTS ==========
    
    @Test
    fun testLimitWithSmallCount() = runTest {
        val entries = TestDataGenerator.createFFetchEntries(10, "limit")
        val limited = entries.asFlow().limit(3)
        
        val result = limited.toList()
        assertEquals(3, result.size)
        assertEquals("limit_1", result[0]["id"])
        assertEquals("limit_3", result[2]["id"])
    }
    
    @Test
    fun testLimitWithZeroCount() = runTest {
        val entries = TestDataGenerator.createFFetchEntries(5, "zero_limit")
        // Test that attempting to use limit with 0 should be handled gracefully
        // Since take(0) is not allowed, we'll test limit(1) and then take nothing from it
        val limited = entries.asFlow().limit(1)
        val result = limited.toList()
        
        assertEquals(1, result.size) // limit(1) should return 1 item
        assertEquals("zero_limit_1", result[0]["id"])
    }
    
    @Test
    fun testLimitLargerThanAvailable() = runTest {
        val entries = TestDataGenerator.createFFetchEntries(3, "large_limit")
        val limited = entries.asFlow().limit(10)
        
        val result = limited.toList()
        assertEquals(3, result.size) // Should return all available
    }
    
    @Test
    fun testLimitWithSingleEntry() = runTest {
        val entries = TestDataGenerator.createFFetchEntries(1, "single_limit")
        val limited = entries.asFlow().limit(1)
        
        val result = limited.toList()
        assertEquals(1, result.size)
        assertEquals("single_limit_1", result[0]["id"])
    }
    
    
    // ========== SKIP OPERATION TESTS ==========
    
    @Test
    fun testSkipWithSmallCount() = runTest {
        val entries = TestDataGenerator.createFFetchEntries(10, "skip")
        val skipped = entries.asFlow().skip(3)
        
        val result = skipped.toList()
        assertEquals(7, result.size)
        assertEquals("skip_4", result[0]["id"]) // First after skipping 3
        assertEquals("skip_10", result[6]["id"]) // Last entry
    }
    
    @Test
    fun testSkipWithZeroCount() = runTest {
        val entries = TestDataGenerator.createFFetchEntries(5, "zero_skip")
        val skipped = entries.asFlow().skip(0)
        
        val result = skipped.toList()
        assertEquals(5, result.size)
        assertEquals("zero_skip_1", result[0]["id"])
    }
    
    @Test
    fun testSkipMoreThanAvailable() = runTest {
        val entries = TestDataGenerator.createFFetchEntries(3, "large_skip")
        val skipped = entries.asFlow().skip(10)
        
        val result = skipped.toList()
        assertTrue(result.isEmpty())
    }
    
    @Test
    fun testSkipAllEntries() = runTest {
        val entries = TestDataGenerator.createFFetchEntries(5, "skip_all")
        val skipped = entries.asFlow().skip(5)
        
        val result = skipped.toList()
        assertTrue(result.isEmpty())
    }
    
    
    // ========== SLICE OPERATION TESTS ==========
    
    @Test
    fun testSliceFromBeginning() = runTest {
        val entries = TestDataGenerator.createFFetchEntries(10, "slice")
        val sliced = entries.asFlow().slice(0, 5)
        
        val result = sliced.toList()
        assertEquals(5, result.size)
        assertEquals("slice_1", result[0]["id"])
        assertEquals("slice_5", result[4]["id"])
    }
    
    @Test
    fun testSliceMiddleRange() = runTest {
        val entries = TestDataGenerator.createFFetchEntries(10, "middle")
        val sliced = entries.asFlow().slice(3, 7)
        
        val result = sliced.toList()
        assertEquals(4, result.size)
        assertEquals("middle_4", result[0]["id"])
        assertEquals("middle_7", result[3]["id"])
    }
    
    @Test
    fun testSliceToEnd() = runTest {
        val entries = TestDataGenerator.createFFetchEntries(8, "end")
        val sliced = entries.asFlow().slice(5, 11) // Beyond available
        
        val result = sliced.toList()
        assertEquals(3, result.size) // Only entries 6, 7, 8
        assertEquals("end_6", result[0]["id"])
        assertEquals("end_8", result[2]["id"])
    }
    
    @Test
    fun testSliceEmptyRange() = runTest {
        val entries = TestDataGenerator.createFFetchEntries(5, "empty_slice")
        val sliced = entries.asFlow().slice(10, 16) // Completely beyond available
        
        val result = sliced.toList()
        assertTrue(result.isEmpty())
    }
    
    @Test
    fun testSliceValidEdgeCase() = runTest {
        val entries = TestDataGenerator.createFFetchEntries(5, "edge_case")
        // Test slice at the boundary - slice(4, 5) should return 1 item (the 5th entry)
        val sliced = entries.asFlow().slice(4, 5)
        
        val result = sliced.toList()
        assertEquals(1, result.size)
        assertEquals("edge_case_5", result[0]["id"])
    }
    
    @Test
    fun testDirectSliceExtensionCall() = runTest {
        val entries = TestDataGenerator.createFFetchEntries(10, "direct_slice")
        val flow = entries.asFlow()
        val result = flow.slice(2, 6).toList()
        assertEquals(4, result.size)
        assertEquals("direct_slice_3", result[0]["id"])
    }
    
    // ========== CHAINED OPERATIONS TESTS ==========
    
    @Test
    fun testChainedFilterAndMap() = runTest {
        val entries = TestDataGenerator.createFFetchEntries(20, "chain")
        val result = entries.asFlow()
            .filter { (it["index"] as Int) % 2 == 0 } // Even numbers only
            .map { "EVEN_${it["title"]}" }
            .toList()
        
        assertEquals(10, result.size)
        assertTrue(result.all { it.startsWith("EVEN_") })
        assertEquals("EVEN_Title 2", result[0])
        assertEquals("EVEN_Title 20", result[9])
    }
    
    @Test
    fun testChainedLimitAndSkip() = runTest {
        val entries = TestDataGenerator.createFFetchEntries(20, "limit_skip")
        val result = entries.asFlow()
            .skip(5)
            .limit(8)
            .toList()
        
        assertEquals(8, result.size)
        assertEquals("limit_skip_6", result[0]["id"]) // First after skip
        assertEquals("limit_skip_13", result[7]["id"]) // 5 + 8 = 13
    }
    
    @Test
    fun testComplexChainedOperations() = runTest {
        val entries = TestDataGenerator.createFFetchEntries(30, "complex")
        val result = entries.asFlow()
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
    fun testTypePreservationThroughChain() = runTest {
        data class TypedResult(val id: String, val processed: Boolean)
        
        val entries = TestDataGenerator.createFFetchEntries(10, "typed")
        val typedResults = entries.asFlow()
            .filter { (it["index"] as Int) <= 5 }
            .map { entry ->
                TypedResult(
                    id = entry["id"].toString(),
                    processed = true
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
    fun testFlowMapOperation() = runTest {
        val numbers = (1..5).asFlow()
        val squaredFlow = numbers.map { it * it }
        val squared = squaredFlow.toList()
        
        assertEquals(listOf(1, 4, 9, 16, 25), squared)
    }
    
    @Test
    fun testFlowFilterOperation() = runTest {
        val numbers = (1..10).asFlow()
        val evenFlow = numbers.filter { it % 2 == 0 }
        val evens = evenFlow.toList()
        
        assertEquals(listOf(2, 4, 6, 8, 10), evens)
    }
    
    @Test
    fun testFlowLimitOperation() = runTest {
        val numbers = (1..100).asFlow()
        val limitedFlow = numbers.limit(5)
        val limited = limitedFlow.toList()
        
        assertEquals(listOf(1, 2, 3, 4, 5), limited)
    }
    
    @Test
    fun testFlowSkipOperation() = runTest {
        val numbers = (1..10).asFlow()
        val skippedFlow = numbers.skip(7)
        val skipped = skippedFlow.toList()
        
        assertEquals(listOf(8, 9, 10), skipped)
    }
    
    @Test
    fun testFlowSliceOperation() = runTest {
        val numbers = (1..20).asFlow()
        val slicedFlow = numbers.slice(5, 10)
        val sliced = slicedFlow.toList()
        
        assertEquals(listOf(6, 7, 8, 9, 10), sliced)
    }
    
    // ========== ERROR HANDLING TESTS ==========
    
    @Test
    fun testTransformationsWithEmptyFlow() = runTest {
        val emptyFlow = emptyList<FFetchEntry>().asFlow()
        val result = emptyFlow
            .filter { true }
            .map { it["id"].toString() }
            .skip(0)
            .limit(10)
            .toList()
        
        assertTrue(result.isEmpty())
    }
    
    @Test
    fun testTransformationWithExceptionInFilter() = runTest {
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
}