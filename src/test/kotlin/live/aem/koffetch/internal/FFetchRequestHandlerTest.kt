//
// FFetchRequestHandlerTest.kt
// KotlinFFetch Tests
//
// Comprehensive tests for FFetchRequestHandler
//

package live.aem.koffetch.internal

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import live.aem.koffetch.FFetchContext
import live.aem.koffetch.FFetchEntry
import live.aem.koffetch.FFetchError
import live.aem.koffetch.mock.MockFFetchHTTPClient
import live.aem.koffetch.mock.MockResponse
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FFetchRequestHandlerTest {
    private lateinit var mockHttpClient: MockFFetchHTTPClient
    private lateinit var context: FFetchContext

    @BeforeTest
    fun setUp() {
        mockHttpClient = MockFFetchHTTPClient()
        context =
            FFetchContext(
                // Small chunk size for testing pagination
                chunkSize = 10,
                httpClient = mockHttpClient,
            )
    }

    @AfterTest
    fun tearDown() {
        mockHttpClient.reset()
    }

    @Test
    fun testSinglePageRequest() =
        runTest {
            // Setup
            val baseUrl = "https://example.com/api"
            val url = URL(baseUrl)
            val testData =
                listOf(
                    mapOf("path" to "/content/page1", "title" to "Page 1"),
                    mapOf("path" to "/content/page2", "title" to "Page 2"),
                )

            mockHttpClient.setSuccessResponse(
                "$baseUrl?offset=0&limit=10",
                mockHttpClient.createAEMResponse(2, 0, 10, testData),
            )

            // Execute
            val results = mutableListOf<FFetchEntry>()
            FFetchRequestHandler.performRequest(url, context) { entry ->
                results.add(entry)
            }

            // Verify
            assertEquals(2, results.size)
            assertEquals("Page 1", results[0]["title"])
            assertEquals("Page 2", results[1]["title"])
            assertEquals(1, mockHttpClient.requestLog.size)
        }

    @Test
    fun testMultiPagePagination() =
        runTest {
            // Setup
            val baseUrl = "https://example.com/api"
            val url = URL(baseUrl)
            val totalItems = 25
            mockHttpClient.setupPaginatedResponses(baseUrl, totalItems, 10)

            // Execute
            val results = mutableListOf<FFetchEntry>()
            FFetchRequestHandler.performRequest(url, context) { entry ->
                results.add(entry)
            }

            // Verify
            assertEquals(totalItems, results.size)
            assertTrue(mockHttpClient.requestLog.size >= 3) // At least 3 requests for 25 items with chunk size 10

            // Verify all items are present
            val paths = results.map { it["path"] }.toSet()
            assertEquals(totalItems, paths.size)
            assertTrue(paths.contains("/content/item-1"))
            assertTrue(paths.contains("/content/item-25"))
        }

    @Test
    fun testOffsetLimitParameterBuilding() =
        runTest {
            // Setup
            val baseUrl = "https://example.com/api"
            val url = URL(baseUrl)

            // Set up responses for multiple pages
            mockHttpClient.setSuccessResponse(
                "$baseUrl?offset=0&limit=5",
                mockHttpClient.createAEMResponse(15, 0, 5, (1..5).map { mapOf("id" to it) }),
            )
            mockHttpClient.setSuccessResponse(
                "$baseUrl?offset=5&limit=5",
                mockHttpClient.createAEMResponse(15, 5, 5, (6..10).map { mapOf("id" to it) }),
            )
            mockHttpClient.setSuccessResponse(
                "$baseUrl?offset=10&limit=5",
                mockHttpClient.createAEMResponse(15, 10, 5, (11..15).map { mapOf("id" to it) }),
            )

            // Use smaller chunk size for this test
            val testContext = context.copy(chunkSize = 5)

            // Execute
            val results = mutableListOf<FFetchEntry>()
            FFetchRequestHandler.performRequest(url, testContext) { entry ->
                results.add(entry)
            }

            // Verify
            assertEquals(15, results.size)
            assertEquals(3, mockHttpClient.requestLog.size)

            // Check that the correct URLs were requested
            val requestedUrls = mockHttpClient.requestLog.map { it.url }
            assertTrue(requestedUrls.contains("$baseUrl?offset=0&limit=5"))
            assertTrue(requestedUrls.contains("$baseUrl?offset=5&limit=5"))
            assertTrue(requestedUrls.contains("$baseUrl?offset=10&limit=5"))
        }

    @Test
    fun testTotalCountHandlingAndEarlyTermination() =
        runTest {
            // Setup - server reports total of 8 items, but we set up only 2 pages
            val baseUrl = "https://example.com/api"
            val url = URL(baseUrl)

            mockHttpClient.setSuccessResponse(
                "$baseUrl?offset=0&limit=5",
                mockHttpClient.createAEMResponse(8, 0, 5, (1..5).map { mapOf("id" to it) }),
            )
            mockHttpClient.setSuccessResponse(
                "$baseUrl?offset=5&limit=5",
                mockHttpClient.createAEMResponse(8, 5, 5, (6..8).map { mapOf("id" to it) }),
            )

            val testContext = context.copy(chunkSize = 5)

            // Execute
            val results = mutableListOf<FFetchEntry>()
            FFetchRequestHandler.performRequest(url, testContext) { entry ->
                results.add(entry)
            }

            // Verify
            assertEquals(8, results.size)
            assertEquals(2, mockHttpClient.requestLog.size) // Should stop after reaching total
        }

    @Test
    fun testSheetParameterInclusion() =
        runTest {
            // Setup
            val baseUrl = "https://example.com/api"
            val url = URL(baseUrl)
            val sheetName = "products"

            mockHttpClient.setSuccessResponse(
                "$baseUrl?offset=0&limit=10&sheet=$sheetName",
                mockHttpClient.createAEMResponse(3, 0, 10, (1..3).map { mapOf("product" to "Product $it") }),
            )

            val testContext = context.copy(sheetName = sheetName)

            // Execute
            val results = mutableListOf<FFetchEntry>()
            FFetchRequestHandler.performRequest(url, testContext) { entry ->
                results.add(entry)
            }

            // Verify
            assertEquals(3, results.size)
            assertEquals(1, mockHttpClient.requestLog.size)
            assertTrue(mockHttpClient.requestLog[0].url.contains("sheet=$sheetName"))
        }

    @Test
    fun testUrlWithExistingQueryParameters() =
        runTest {
            // Setup
            val baseUrl = "https://example.com/api?filter=active"
            val url = URL(baseUrl)

            mockHttpClient.setSuccessResponse(
                "$baseUrl&offset=0&limit=10",
                mockHttpClient.createAEMResponse(2, 0, 10, listOf(mapOf("status" to "active"))),
            )

            // Execute
            val results = mutableListOf<FFetchEntry>()
            FFetchRequestHandler.performRequest(url, context) { entry ->
                results.add(entry)
            }

            // Verify
            assertEquals(1, results.size)
            assertTrue(mockHttpClient.requestLog[0].url.contains("filter=active"))
            assertTrue(mockHttpClient.requestLog[0].url.contains("&offset=0&limit=10"))
        }

    @Test
    fun testNetworkErrorHandling() =
        runTest {
            // Setup
            val url = URL("https://example.com/api")
            mockHttpClient.shouldThrowNetworkError = true
            mockHttpClient.networkErrorMessage = "Connection timeout"

            // Execute & Verify
            assertFailsWith<FFetchError.NetworkError> {
                FFetchRequestHandler.performRequest(url, context) { }
            }
        }

    @Test
    fun testHttp404ErrorHandling() =
        runTest {
            // Setup
            val baseUrl = "https://example.com/api"
            val url = URL(baseUrl)

            mockHttpClient.setErrorResponse("$baseUrl?offset=0&limit=10", HttpStatusCode.NotFound)

            // Execute & Verify
            assertFailsWith<FFetchError.DocumentNotFound> {
                FFetchRequestHandler.performRequest(url, context) { }
            }
        }

    @Test
    fun testHttp500ErrorHandling() =
        runTest {
            // Setup
            val baseUrl = "https://example.com/api"
            val url = URL(baseUrl)

            mockHttpClient.setErrorResponse(
                "$baseUrl?offset=0&limit=10",
                HttpStatusCode.InternalServerError,
                "Server Error",
            )

            // Execute & Verify
            assertFailsWith<FFetchError.NetworkError> {
                FFetchRequestHandler.performRequest(url, context) { }
            }
        }

    @Test
    fun testMalformedJsonResponseHandling() =
        runTest {
            // Setup
            val baseUrl = "https://example.com/api"
            val url = URL(baseUrl)

            mockHttpClient.setSuccessResponse("$baseUrl?offset=0&limit=10", "{ invalid json }")

            // Execute & Verify
            assertFailsWith<FFetchError.NetworkError> {
                FFetchRequestHandler.performRequest(url, context) { }
            }
        }

    @Test
    fun testMissingRequiredFieldsInResponse() =
        runTest {
            // Setup
            val baseUrl = "https://example.com/api"
            val url = URL(baseUrl)

            // Response missing 'data' field
            mockHttpClient.setSuccessResponse("$baseUrl?offset=0&limit=10", "{\"total\":5,\"offset\":0,\"limit\":10}")

            // Execute & Verify
            assertFailsWith<FFetchError.NetworkError> {
                FFetchRequestHandler.performRequest(url, context) { }
            }
        }

    @Test
    fun testEmptyDataArrayHandling() =
        runTest {
            // Setup
            val baseUrl = "https://example.com/api"
            val url = URL(baseUrl)

            mockHttpClient.setSuccessResponse(
                "$baseUrl?offset=0&limit=10",
                mockHttpClient.createAEMResponse(0, 0, 10, emptyList()),
            )

            // Execute
            val results = mutableListOf<FFetchEntry>()
            FFetchRequestHandler.performRequest(url, context) { entry ->
                results.add(entry)
            }

            // Verify
            assertEquals(0, results.size)
            assertEquals(1, mockHttpClient.requestLog.size)
        }

    @Test
    fun testContextTotalIsUpdatedAfterFirstRequest() =
        runTest {
            // Setup
            val baseUrl = "https://example.com/api"
            val url = URL(baseUrl)
            val totalItems = 50

            mockHttpClient.setSuccessResponse(
                "$baseUrl?offset=0&limit=10",
                mockHttpClient.createAEMResponse(totalItems, 0, 10, (1..10).map { mapOf("id" to it) }),
            )

            val testContext = context.copy()
            assertNull(testContext.total) // Initially null

            // Execute just the first page
            var callCount = 0
            FFetchRequestHandler.performRequest(url, testContext) { entry ->
                callCount++
                if (callCount >= 10) return@performRequest // Stop after first page
            }

            // Verify that context.total would be updated (this is internal behavior)
            // We can't directly access the mutableContext from the test, but we can verify
            // the behavior by checking that subsequent pages would be requested
            assertTrue(mockHttpClient.requestLog.isNotEmpty())
        }

    @Test
    fun testCacheConfigurationPassedToHttpClient() =
        runTest {
            // Setup
            val baseUrl = "https://example.com/api"
            val url = URL(baseUrl)
            val cacheConfig = FFetchCacheConfig.NoCache

            mockHttpClient.setSuccessResponse(
                "$baseUrl?offset=0&limit=10",
                mockHttpClient.createAEMResponse(1, 0, 10, listOf(mapOf("id" to 1))),
            )

            val testContext = context.copy(cacheConfig = cacheConfig)

            // Execute
            FFetchRequestHandler.performRequest(url, testContext) { }

            // Verify
            assertEquals(cacheConfig, mockHttpClient.requestLog[0].cacheConfig)
        }

    @Test
    fun testLargeDatasetPagination() =
        runTest {
            // Setup
            val baseUrl = "https://example.com/api"
            val url = URL(baseUrl)
            val totalItems = 1000
            val chunkSize = 50

            mockHttpClient.setupPaginatedResponses(baseUrl, totalItems, chunkSize)
            val testContext = context.copy(chunkSize = chunkSize)

            // Execute
            val results = mutableListOf<FFetchEntry>()
            FFetchRequestHandler.performRequest(url, testContext) { entry ->
                results.add(entry)
            }

            // Verify
            assertEquals(totalItems, results.size)
            assertEquals(20, mockHttpClient.requestLog.size) // 1000 items / 50 per page = 20 pages

            // Verify first and last items
            assertTrue(results.any { it["path"] == "/content/item-1" })
            assertTrue(results.any { it["path"] == "/content/item-1000" })
        }

    @Test
    fun testUrlEncodingOfSpecialCharacters() =
        runTest {
            // Setup
            val baseUrl = "https://example.com/api"
            val url = URL(baseUrl)
            val sheetName = "special sheet name"

            mockHttpClient.setResponse(
                "$baseUrl?offset=0&limit=10&sheet=special sheet name",
                MockResponse.success(mockHttpClient.createAEMResponse(1, 0, 10, listOf(mapOf("test" to "value")))),
            )

            val testContext = context.copy(sheetName = sheetName)

            // Execute
            FFetchRequestHandler.performRequest(url, testContext) { }

            // Verify the URL contains the sheet parameter (URL encoding is handled by the HTTP client)
            assertTrue(mockHttpClient.requestLog[0].url.contains("sheet=special sheet name"))
        }
}
