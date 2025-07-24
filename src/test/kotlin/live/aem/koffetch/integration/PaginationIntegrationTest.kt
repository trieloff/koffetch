//
// PaginationIntegrationTest.kt
// KotlinFFetch Integration Tests
//
// Integration tests for pagination scenarios
//

package live.aem.koffetch.integration

import live.aem.koffetch.*
import live.aem.koffetch.extensions.all
import live.aem.koffetch.extensions.count
import live.aem.koffetch.extensions.filter
import live.aem.koffetch.extensions.map
import live.aem.koffetch.mock.MockFFetchHTTPClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test

/**
 * Integration tests for pagination scenarios
 * Tests streaming across multiple pages and large dataset handling
 */
class PaginationIntegrationTest {
    
    private lateinit var mockClient: MockFFetchHTTPClient
    private val baseUrl = "https://example.com/large-dataset.json"
    
    @BeforeEach
    fun setUp() {
        mockClient = MockFFetchHTTPClient()
    }
    
    @AfterEach
    fun tearDown() {
        mockClient.reset()
    }
    
    @Test
    fun testBasicPaginationStreaming() = runBlocking {
        // Setup: Create paginated response with 3 pages of 100 items each
        val totalItems = 300
        val pageSize = 100
        
        setupPaginatedDataset(totalItems, pageSize)
        
        // Execute: Stream all data with matching chunk size
        val ffetch = FFetch(baseUrl)
            .withHTTPClient(mockClient)
            .chunks(pageSize)
        
        val allItems = ffetch.all()
        
        // Verify: All items retrieved
        assertEquals(totalItems, allItems.size)
        
        // Verify: Items are in correct order
        assertEquals("/content/item-1", allItems[0]["path"])
        assertEquals("Item 1", allItems[0]["title"])
        assertEquals("/content/item-300", allItems[299]["path"])
        assertEquals("Item 300", allItems[299]["title"])
        
        // Verify: Multiple requests were made for pagination
        assertTrue(mockClient.requestLog.size >= 3)
        
        // Verify: All requests were to expected URLs
        assertTrue(mockClient.requestLog.all { it.url.startsWith(baseUrl) })
        assertTrue(mockClient.requestLog.any { it.url.contains("offset=0") })
        assertTrue(mockClient.requestLog.any { it.url.contains("offset=100") })
        assertTrue(mockClient.requestLog.any { it.url.contains("offset=200") })
    }
    
    @Test
    fun testLargeDatasetPagination() = runBlocking {
        // Setup: Large dataset requiring many pages
        val totalItems = 2500
        val pageSize = 255 // Default chunk size
        
        setupPaginatedDataset(totalItems, pageSize)
        
        // Execute: Stream large dataset
        val ffetch = FFetch(baseUrl)
            .withHTTPClient(mockClient)
            .chunks(pageSize)
        
        val count = ffetch.count()
        
        // Verify: Correct count without loading everything into memory first
        assertEquals(totalItems, count)
        
        // Verify: Many paginated requests were made
        val expectedPages = (totalItems + pageSize - 1) / pageSize // Ceiling division
        assertEquals(expectedPages, mockClient.requestLog.size)
        
        // Verify: Last page request has correct offset
        val lastRequest = mockClient.requestLog.last()
        val expectedLastOffset = (expectedPages - 1) * pageSize
        assertTrue(lastRequest.url.contains("offset=$expectedLastOffset"))
    }
    
    @Test
    fun testSmallChunkSizePagination() = runBlocking {
        // Setup: Dataset that will require many small requests
        val totalItems = 500
        val chunkSize = 25 // Smaller than typical page size
        
        setupPaginatedDataset(totalItems, chunkSize)
        
        // Execute: Use the small chunk size
        val ffetch = FFetch(baseUrl)
            .withHTTPClient(mockClient)
            .chunks(chunkSize)
        
        val allItems = ffetch.all()
        
        // Verify: All data retrieved despite chunk size mismatch
        assertEquals(totalItems, allItems.size)
        
        // Verify: More requests were made due to smaller chunk size
        val expectedRequests = (totalItems + chunkSize - 1) / chunkSize
        assertEquals(expectedRequests, mockClient.requestLog.size)
        
        // Verify: Requests use the specified chunk size
        mockClient.requestLog.forEach { request ->
            assertTrue(request.url.contains("limit=$chunkSize"))
        }
    }
    
    @Test
    fun testEarlyTerminationScenario() = runBlocking {
        // Setup: Large dataset
        val totalItems = 1000
        val pageSize = 100
        
        setupPaginatedDataset(totalItems, pageSize)
        
        // Execute: Take only first 150 items (spanning 2 pages)
        val ffetch = FFetch(baseUrl)
            .withHTTPClient(mockClient)
            .chunks(pageSize)
        
        val first150Items = ffetch.asFlow().take(150).toList()
        
        // Verify: Only got the requested number of items
        assertEquals(150, first150Items.size)
        
        // Verify: Stopped early - should have made 2 requests (pages 1 and 2)
        assertTrue(mockClient.requestLog.size <= 2)
        
        // Verify: Items are in correct order
        assertEquals("Item 1", first150Items[0]["title"])
        assertEquals("Item 150", first150Items[149]["title"])
    }
    
    @Test
    fun testPaginationWithFiltering() = runBlocking {
        // Setup: Large dataset where we'll filter for specific items
        val totalItems = 1000
        val pageSize = 100
        
        setupPaginatedDataset(totalItems, pageSize)
        
        // Execute: Filter for items divisible by 100 (should be items 100, 200, ..., 1000)
        val ffetch = FFetch(baseUrl)
            .withHTTPClient(mockClient)
            .chunks(pageSize)
        
        val filteredItems = ffetch.asFlow()
            .filter { entry -> 
                val title = entry["title"] as String
                val itemNumber = title.removePrefix("Item ").toInt()
                itemNumber % 100 == 0
            }
            .all()
        
        // Verify: Got exactly the expected filtered items
        assertEquals(10, filteredItems.size) // Items 100, 200, ..., 1000
        
        // Verify: All pages were processed to apply the filter
        val expectedPages = (totalItems + pageSize - 1) / pageSize
        assertEquals(expectedPages, mockClient.requestLog.size)
        
        // Verify: Filtered items are correct
        assertEquals("Item 100", filteredItems[0]["title"])
        assertEquals("Item 1000", filteredItems[9]["title"])
    }
    
    @Test
    fun testPaginationWithTransformation() = runBlocking {
        // Setup: Dataset for transformation testing
        val totalItems = 300
        val pageSize = 50
        
        setupPaginatedDataset(totalItems, pageSize)
        
        // Execute: Transform each item to just extract the item number
        val ffetch = FFetch(baseUrl)
            .withHTTPClient(mockClient)
            .chunks(pageSize)
        
        val itemNumbers = ffetch.asFlow()
            .map { entry ->
                val title = entry["title"] as String
                title.removePrefix("Item ").toInt()
            }
            .all()
        
        // Verify: All items transformed correctly
        assertEquals(totalItems, itemNumbers.size)
        assertEquals(1, itemNumbers[0])
        assertEquals(300, itemNumbers[299])
        
        // Verify: Items are in sequential order
        for (i in 0 until totalItems) {
            assertEquals(i + 1, itemNumbers[i])
        }
        
        // Verify: All pages were processed
        val expectedPages = (totalItems + pageSize - 1) / pageSize
        assertEquals(expectedPages, mockClient.requestLog.size)
    }
    
    @Test
    fun testPaginationErrorRecovery() = runBlocking {
        // Setup: Dataset where middle page will fail
        val totalItems = 300
        val pageSize = 100
        
        // Set up first and third page successfully
        setupPaginatedPage(0, pageSize, totalItems)
        setupPaginatedPage(200, pageSize, totalItems)
        
        // Set up second page to return error
        val errorPageUrl = "$baseUrl?offset=100&limit=100"
        mockClient.setErrorResponse(errorPageUrl, io.ktor.http.HttpStatusCode.InternalServerError, "Server error")
        
        // Execute: Try to stream all data
        val ffetch = FFetch(baseUrl)
            .withHTTPClient(mockClient)
            .chunks(pageSize)
        
        // Verify: Error is propagated when hitting the failed page
        assertThrows<FFetchError.NetworkError> {
            runBlocking {
                ffetch.all()
            }
        }
        
        // Verify: Requests were made up to the failing page
        assertTrue(mockClient.requestLog.size >= 2)
        assertTrue(mockClient.requestLog.any { it.url.contains("offset=0") })
        assertTrue(mockClient.requestLog.any { it.url.contains("offset=100") })
    }
    
    @Test
    fun testInconsistentPageSizes() = runBlocking {
        // Setup: Pages with different sizes (last page is smaller)
        val totalItems = 250
        val pageSize = 100
        
        // First page: 100 items
        setupPaginatedPage(0, pageSize, totalItems, actualItemsInPage = 100)
        
        // Second page: 100 items  
        setupPaginatedPage(100, pageSize, totalItems, actualItemsInPage = 100)
        
        // Third page: only 50 items (partial page)
        setupPaginatedPage(200, pageSize, totalItems, actualItemsInPage = 50)
        
        // Execute: Stream all data
        val ffetch = FFetch(baseUrl)
            .withHTTPClient(mockClient)
            .chunks(pageSize)
        
        val allItems = ffetch.all()
        
        // Verify: All items retrieved despite inconsistent page sizes
        assertEquals(totalItems, allItems.size)
        
        // Verify: Items are in correct order
        assertEquals("Item 1", allItems[0]["title"])
        assertEquals("Item 250", allItems[249]["title"])
        
        // Verify: Three requests were made
        assertEquals(3, mockClient.requestLog.size)
    }
    
    @Test
    fun testEmptyPagesInSequence() = runBlocking {
        // Setup: Dataset with an empty page in the middle
        val pageSize = 100
        
        // First page: normal data
        setupPaginatedPage(0, pageSize, 300, actualItemsInPage = 100)
        
        // Second page: empty (no data)
        val emptyPageUrl = "$baseUrl?offset=100&limit=100"
        mockClient.setSuccessResponse(emptyPageUrl, """{"total":300,"offset":100,"limit":100,"data":[]}""")
        
        // Third page: normal data again
        setupPaginatedPage(200, pageSize, 300, actualItemsInPage = 100)
        
        // Execute: Stream data
        val ffetch = FFetch(baseUrl)
            .withHTTPClient(mockClient)
            .chunks(pageSize)
        
        val allItems = ffetch.all()
        
        // Verify: Got items from first and third page only
        assertEquals(200, allItems.size) // 100 from first page + 0 from second + 100 from third
        
        // Verify: All pages were requested
        assertEquals(3, mockClient.requestLog.size)
        assertTrue(mockClient.requestLog.any { it.url.contains("offset=0") })
        assertTrue(mockClient.requestLog.any { it.url.contains("offset=100") })
        assertTrue(mockClient.requestLog.any { it.url.contains("offset=200") })
    }
    
    @Test
    fun testPaginationWithCacheConfiguration() = runBlocking {
        // Setup: Paginated dataset
        val totalItems = 200
        val pageSize = 100
        
        setupPaginatedDataset(totalItems, pageSize)
        
        // Execute: Stream with specific cache configuration
        val ffetch = FFetch(baseUrl)
            .withHTTPClient(mockClient)
            .chunks(pageSize)
            .cache(FFetchCacheConfig.NoCache)
        
        val allItems = ffetch.all()
        
        // Verify: All data retrieved
        assertEquals(totalItems, allItems.size)
        
        // Verify: Cache configuration was applied to all requests
        assertEquals(2, mockClient.requestLog.size)
        mockClient.requestLog.forEach { request ->
            assertEquals(FFetchCacheConfig.NoCache, request.cacheConfig)
        }
    }
    
    // Helper methods
    
    private fun setupPaginatedDataset(totalItems: Int, pageSize: Int) {
        var offset = 0
        while (offset < totalItems) {
            setupPaginatedPage(offset, pageSize, totalItems)
            offset += pageSize
        }
    }
    
    private fun setupPaginatedPage(
        offset: Int, 
        pageSize: Int, 
        totalItems: Int, 
        actualItemsInPage: Int = minOf(pageSize, totalItems - offset)
    ) {
        val pageUrl = "$baseUrl?offset=$offset&limit=$pageSize"
        
        val dataItems = (1..actualItemsInPage).map { i ->
            val itemId = offset + i
            """{
                "id": $itemId,
                "path": "/content/item-$itemId",
                "title": "Item $itemId",
                "type": "article",
                "lastModified": ${System.currentTimeMillis() - (itemId * 1000)}
            }"""
        }.joinToString(",")
        
        val response = """
        {
            "total": $totalItems,
            "offset": $offset,
            "limit": $pageSize,
            "data": [$dataItems]
        }
        """.trimIndent()
        
        mockClient.setSuccessResponse(pageUrl, response)
    }
}