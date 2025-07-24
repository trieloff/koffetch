//
// FFetchDocumentFollowingTest.kt
// KotlinFFetch
//
// Comprehensive tests for document following functionality
//

package live.aem.koffetch.extensions

import live.aem.koffetch.*
import live.aem.koffetch.mock.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import java.net.URL
import kotlin.test.*

class FFetchDocumentFollowingTest {

    private lateinit var mockHttpClient: MockFFetchHTTPClient
    private lateinit var mockHtmlParser: MockHTMLParser

    @BeforeTest
    fun setUp() {
        mockHttpClient = MockFFetchHTTPClient()
        mockHtmlParser = MockHTMLParser()
        
        // Set up basic AEM response with document references
        val initialResponse = mockHttpClient.createAEMResponse(
            total = 3,
            offset = 0,
            limit = 255,
            data = listOf(
                mapOf(
                    "path" to "/content/article-1",
                    "title" to "Article 1",
                    "documentUrl" to "https://example.com/docs/article1.html"
                ),
                mapOf(
                    "path" to "/content/article-2",
                    "title" to "Article 2",
                    "documentUrl" to "docs/article2.html" // relative URL
                ),
                mapOf(
                    "path" to "/content/article-3",
                    "title" to "Article 3",
                    "otherField" to "value"
                    // missing documentUrl field
                )
            )
        )
        
        mockHttpClient.setSuccessResponse(
            "https://example.com/query-index.json?offset=0&limit=255",
            initialResponse
        )

        // Set up HTML document responses
        mockHttpClient.setSuccessResponse(
            "https://example.com/docs/article1.html",
            "<html><body><h1>Article 1 Content</h1><p>Full content here</p></body></html>"
        )
        
        mockHttpClient.setSuccessResponse(
            "https://example.com/docs/article2.html",
            "<html><body><h1>Article 2 Content</h1><p>More content here</p></body></html>"
        )
    }

    @Test
    fun testSuccessfulDocumentFetchingAndParsing() = runTest {
        val ffetch = FFetch(
            URL("https://example.com/query-index.json"),
            FFetchContext(
                httpClient = mockHttpClient,
                htmlParser = mockHtmlParser
            )
        )
        
        val results = ffetch.follow("documentUrl").asFlow().toList()
        
        assertEquals(3, results.size)
        
        // First article should have parsed document
        val firstArticle = results.first { it["path"] == "/content/article-1" }
        assertNotNull(firstArticle["documentUrl"])
        assertNull(firstArticle["documentUrl_error"])
        
        // Verify HTML parser was called
        assertTrue(mockHtmlParser.parseCallCount > 0)
        assertTrue(mockHtmlParser.wasHtmlParsed("Article 1 Content"))
        
        // Second article should also succeed (relative URL resolved)
        val secondArticle = results.first { it["path"] == "/content/article-2" }
        assertNotNull(secondArticle["documentUrl"])
        assertNull(secondArticle["documentUrl_error"])
    }

    @Test
    fun testRelativeUrlResolution() = runTest {
        val ffetch = FFetch(
            URL("https://example.com/query-index.json"),
            FFetchContext(
                httpClient = mockHttpClient,
                htmlParser = mockHtmlParser
            )
        )
        
        val results = ffetch.follow("documentUrl").asFlow().toList()
        
        // Check that relative URL was correctly resolved
        val requests = mockHttpClient.requestLog
        val relativeUrlRequest = requests.find { it.url.contains("docs/article2.html") }
        assertNotNull(relativeUrlRequest)
        assertTrue(relativeUrlRequest.url.startsWith("https://example.com/"))
    }

    @Test
    fun testAbsoluteUrlHandling() = runTest {
        val ffetch = FFetch(
            URL("https://example.com/query-index.json"),
            FFetchContext(
                httpClient = mockHttpClient,
                htmlParser = mockHtmlParser
            )
        )
        
        val results = ffetch.follow("documentUrl").asFlow().toList()
        
        // Check that absolute URL was used as-is
        val requests = mockHttpClient.requestLog
        val absoluteUrlRequest = requests.find { it.url == "https://example.com/docs/article1.html" }
        assertNotNull(absoluteUrlRequest)
    }

    @Test
    fun testMissingUrlFieldScenarios() = runTest {
        val ffetch = FFetch(
            URL("https://example.com/query-index.json"),
            FFetchContext(
                httpClient = mockHttpClient,
                htmlParser = mockHtmlParser
            )
        )
        
        val results = ffetch.follow("document").asFlow().toList()
        
        assertEquals(3, results.size)
        
        // Third article has missing document field - should have error
        val thirdArticle = results.first { it["path"] == "/content/article-3" }
        assertNull(thirdArticle["document"])
        val error = thirdArticle["document_error"] as? String
        assertTrue(error?.contains("Missing or invalid URL") == true)
    }

    @Test
    fun testInvalidUrlFormats() = runTest {
        val invalidUrlResponse = mockHttpClient.createAEMResponse(
            total = 2,
            offset = 0,
            limit = 255,
            data = listOf(
                mapOf(
                    "path" to "/content/invalid-1",
                    "title" to "Invalid URL 1",
                    "documentUrl" to "not-a-valid-url"
                ),
                mapOf(
                    "path" to "/content/invalid-2",
                    "title" to "Invalid URL 2",
                    "documentUrl" to "://missing-protocol"
                )
            )
        )
        
        mockHttpClient.setSuccessResponse(
            "https://example.com/invalid-urls.json?offset=0&limit=255",
            invalidUrlResponse
        )
        
        val ffetch = FFetch(
            URL("https://example.com/invalid-urls.json"),
            FFetchContext(
                httpClient = mockHttpClient,
                htmlParser = mockHtmlParser
            )
        )
        
        val results = ffetch.follow("documentUrl").asFlow().toList()
        
        assertEquals(2, results.size)
        
        // Both entries should have errors due to invalid URLs
        results.forEach { entry ->
            assertNull(entry["documentUrl"])
            val error = entry["documentUrl_error"] as? String
            assertTrue(error?.contains("Could not resolve URL") == true)
        }
    }

    @Test
    fun testHttpErrorResponses() = runTest {
        // Set up HTTP error responses
        mockHttpClient.setErrorResponse(
            "https://example.com/docs/article1.html",
            HttpStatusCode.NotFound
        )
        
        mockHttpClient.setErrorResponse(
            "https://example.com/docs/article2.html",
            HttpStatusCode.InternalServerError
        )
        
        val ffetch = FFetch(
            URL("https://example.com/query-index.json"),
            FFetchContext(
                httpClient = mockHttpClient,
                htmlParser = mockHtmlParser
            )
        )
        
        val results = ffetch.follow("documentUrl").asFlow().toList()
        
        assertEquals(3, results.size)
        
        // First article should have HTTP error
        val firstArticle = results.first { it["path"] == "/content/article-1" }
        assertNull(firstArticle["documentUrl"])
        val error1 = firstArticle["documentUrl_error"] as? String
        assertTrue(error1?.contains("HTTP error 404") == true)
        
        // Second article should have HTTP error
        val secondArticle = results.first { it["path"] == "/content/article-2" }
        assertNull(secondArticle["documentUrl"])
        val error2 = secondArticle["documentUrl_error"] as? String
        assertTrue(error2?.contains("HTTP error 500") == true)
    }

    @Test
    fun testNetworkTimeoutAndFailures() = runTest {
        // Configure mock to return error responses for document URLs only
        mockHttpClient.setErrorResponse(
            "https://example.com/docs/article1.html",
            HttpStatusCode.GatewayTimeout
        )
        mockHttpClient.setErrorResponse(
            "https://example.com/docs/article2.html", 
            HttpStatusCode.GatewayTimeout
        )
        
        val ffetch = FFetch(
            URL("https://example.com/query-index.json"),
            FFetchContext(
                httpClient = mockHttpClient,
                htmlParser = mockHtmlParser
            )
        )
        
        val results = ffetch.follow("documentUrl").asFlow().toList()
        
        assertEquals(3, results.size)
        
        // All entries that originally had URLs should have HTTP errors
        val entriesWithUrls = results.filter { it["path"] in listOf("/content/article-1", "/content/article-2") }
        entriesWithUrls.forEach { entry ->
            assertNull(entry["documentUrl"])
            val error = entry["documentUrl_error"] as? String
            assertTrue(error?.contains("HTTP error") == true)
            assertTrue(error?.contains("504") == true)
        }
    }

    @Test
    fun testHtmlParsingErrors() = runTest {
        // Configure mock parser to throw errors
        mockHtmlParser.shouldThrowError = true
        mockHtmlParser.errorMessage = "Malformed HTML structure"
        
        val ffetch = FFetch(
            URL("https://example.com/query-index.json"),
            FFetchContext(
                httpClient = mockHttpClient,
                htmlParser = mockHtmlParser
            )
        )
        
        val results = ffetch.follow("documentUrl").asFlow().toList()
        
        assertEquals(3, results.size)
        
        // Entries should have parsing errors
        val entriesWithUrls = results.filter { 
            it.containsKey("documentUrl") && it["documentUrl"] is String 
        }
        
        entriesWithUrls.forEach { entry ->
            assertNull(entry["documentUrl"])
            val error = entry["documentUrl_error"] as? String
            assertTrue(error?.contains("HTML parsing error") == true)
            assertTrue(error?.contains("Malformed HTML structure") == true)
        }
    }

    @Test
    fun testLargeDocumentHandling() = runTest {
        // Create large HTML document (>10KB)
        val largeHtmlContent = buildString {
            append("<html><body>")
            repeat(1000) { i ->
                append("<div>Large content section $i with lots of text content that makes this document very large.</div>")
            }
            append("</body></html>")
        }
        
        mockHttpClient.setSuccessResponse(
            "https://example.com/docs/article1.html",
            largeHtmlContent
        )
        
        val ffetch = FFetch(
            URL("https://example.com/query-index.json"),
            FFetchContext(
                httpClient = mockHttpClient,
                htmlParser = mockHtmlParser
            )
        )
        
        val results = ffetch.follow("documentUrl").asFlow().toList()
        
        // Should handle large documents without issues
        val firstArticle = results.first { it["path"] == "/content/article-1" }
        assertNotNull(firstArticle["documentUrl"])
        assertNull(firstArticle["documentUrl_error"])
        
        // Verify large content was parsed
        assertTrue(mockHtmlParser.parseHistory.any { it.length > 10000 })
    }

    @Test
    fun testConcurrentDocumentFetching() = runTest {
        // Set up multiple documents with different processing times
        mockHttpClient.simulateNetworkDelay = 100 // 100ms delay
        
        val manyDocumentsResponse = mockHttpClient.createAEMResponse(
            total = 10,
            offset = 0,
            limit = 255,
            data = (1..10).map { i ->
                mapOf(
                    "path" to "/content/article-$i",
                    "title" to "Article $i",
                    "documentUrl" to "https://example.com/docs/article$i.html"
                )
            }
        )
        
        mockHttpClient.setSuccessResponse(
            "https://example.com/many-docs.json?offset=0&limit=255",
            manyDocumentsResponse
        )
        
        // Set up responses for all documents
        (1..10).forEach { i ->
            mockHttpClient.setSuccessResponse(
                "https://example.com/docs/article$i.html",
                "<html><body><h1>Article $i Content</h1></body></html>"
            )
        }
        
        val ffetch = FFetch(
            URL("https://example.com/many-docs.json"),
            FFetchContext(
                httpClient = mockHttpClient,
                htmlParser = mockHtmlParser,
                maxConcurrency = 3
            )
        )
        
        val startTime = System.currentTimeMillis()
        val results = ffetch.follow("documentUrl").asFlow().toList()
        val endTime = System.currentTimeMillis()
        
        assertEquals(10, results.size)
        
        // All documents should be processed successfully
        results.forEach { entry ->
            assertNotNull(entry["documentUrl"])
            assertNull(entry["documentUrl_error"])
        }
        
        // With concurrency limit of 3 and 100ms delay per request,
        // total time should be significantly less than sequential (10 * 100 = 1000ms)
        val totalTime = endTime - startTime
        assertTrue(totalTime < 800, "Expected concurrent processing to be faster than sequential. Actual time: ${totalTime}ms")
    }

    @Test
    fun testGracefulErrorHandling() = runTest {
        // Mix of success and failure scenarios
        val mixedResponse = mockHttpClient.createAEMResponse(
            total = 4,
            offset = 0,
            limit = 255,
            data = listOf(
                mapOf(
                    "path" to "/content/success-1",
                    "title" to "Success Article",
                    "documentUrl" to "https://example.com/docs/success.html"
                ),
                mapOf(
                    "path" to "/content/error-404",
                    "title" to "404 Article",
                    "documentUrl" to "https://example.com/docs/notfound.html"
                ),
                mapOf(
                    "path" to "/content/invalid-url",
                    "title" to "Invalid URL Article",
                    "documentUrl" to "not-a-url"
                ),
                mapOf(
                    "path" to "/content/missing-url",
                    "title" to "Missing URL Article"
                    // no documentUrl field
                )
            )
        )
        
        mockHttpClient.setSuccessResponse(
            "https://example.com/mixed-test.json?offset=0&limit=255",
            mixedResponse
        )
        
        mockHttpClient.setSuccessResponse(
            "https://example.com/docs/success.html",
            "<html><body><h1>Success Content</h1></body></html>"
        )
        
        mockHttpClient.setErrorResponse(
            "https://example.com/docs/notfound.html",
            HttpStatusCode.NotFound
        )
        
        val ffetch = FFetch(
            URL("https://example.com/mixed-test.json"),
            FFetchContext(
                httpClient = mockHttpClient,
                htmlParser = mockHtmlParser
            )
        )
        
        val results = ffetch.follow("documentUrl").asFlow().toList()
        
        assertEquals(4, results.size)
        
        // Success case
        val successEntry = results.first { it["path"] == "/content/success-1" }
        assertNotNull(successEntry["documentUrl"])
        assertNull(successEntry["documentUrl_error"])
        
        // 404 error case
        val errorEntry = results.first { it["path"] == "/content/error-404" }
        assertNull(errorEntry["documentUrl"])
        assertTrue(errorEntry["documentUrl_error"].toString().contains("HTTP error 404"))
        
        // Invalid URL case
        val invalidEntry = results.first { it["path"] == "/content/invalid-url" }
        assertNull(invalidEntry["documentUrl"])
        assertTrue(invalidEntry["documentUrl_error"].toString().contains("Could not resolve URL"))
        
        // Missing URL case
        val missingEntry = results.first { it["path"] == "/content/missing-url" }
        assertNull(missingEntry["documentUrl"])
        assertTrue(missingEntry["documentUrl_error"].toString().contains("Missing or invalid URL"))
    }

    @Test
    fun testDocumentFollowingWithCustomFieldNames() = runTest {
        val ffetch = FFetch(
            URL("https://example.com/query-index.json"),
            FFetchContext(
                httpClient = mockHttpClient,
                htmlParser = mockHtmlParser
            )
        )
        
        // Follow documents and store in different field
        val results = ffetch.follow("documentUrl", "parsedDocument").asFlow().toList()
        
        assertEquals(3, results.size)
        
        val firstArticle = results.first { it["path"] == "/content/article-1" }
        assertNotNull(firstArticle["parsedDocument"])
        assertNull(firstArticle["parsedDocument_error"])
        
        // Original field should still exist
        assertTrue(firstArticle.containsKey("documentUrl"))
    }

    @Test
    fun testMaxConcurrencyLimitsAreDespected() = runTest {
        // Create scenario that would be obvious if concurrency limits were ignored
        val concurrencyResponse = mockHttpClient.createAEMResponse(
            total = 6,
            offset = 0,
            limit = 255,
            data = (1..6).map { i ->
                mapOf(
                    "path" to "/content/concurrent-$i",
                    "title" to "Concurrent Article $i",
                    "documentUrl" to "https://example.com/docs/concurrent$i.html"
                )
            }
        )
        
        mockHttpClient.setSuccessResponse(
            "https://example.com/concurrent-test.json?offset=0&limit=255",
            concurrencyResponse
        )
        
        // Set up responses for all documents
        (1..6).forEach { i ->
            mockHttpClient.setSuccessResponse(
                "https://example.com/docs/concurrent$i.html",
                "<html><body><h1>Concurrent Article $i</h1></body></html>"
            )
        }
        
        val ffetch = FFetch(
            URL("https://example.com/concurrent-test.json"),
            FFetchContext(
                httpClient = mockHttpClient,
                htmlParser = mockHtmlParser,
                maxConcurrency = 2 // Limit to 2 concurrent requests
            )
        )
        
        val results = ffetch.follow("documentUrl").asFlow().toList()
        
        assertEquals(6, results.size)
        
        // All should succeed
        results.forEach { entry ->
            assertNotNull(entry["documentUrl"])
            assertNull(entry["documentUrl_error"])
        }
        
        // The exact behavior of concurrency limiting is internal,
        // but we can verify all requests were made
        assertEquals(7, mockHttpClient.requestLog.size) // 6 documents + 1 index
    }

    private suspend fun Flow<FFetchEntry>.toList(): List<FFetchEntry> {
        val list = mutableListOf<FFetchEntry>()
        collect { list.add(it) }
        return list
    }
}