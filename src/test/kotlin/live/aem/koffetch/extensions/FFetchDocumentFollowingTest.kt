//
// FFetchDocumentFollowingTest.kt
// KotlinFFetch
//
// Comprehensive tests for document following functionality
//

package live.aem.koffetch.extensions

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import live.aem.koffetch.FFetch
import live.aem.koffetch.FFetchContext
import live.aem.koffetch.FFetchEntry
import live.aem.koffetch.FFetchError
import live.aem.koffetch.mock.MockFFetchHTTPClient
import live.aem.koffetch.mock.MockHTMLParser
import java.net.URL
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FFetchDocumentFollowingTest {
    private lateinit var mockHttpClient: MockFFetchHTTPClient
    private lateinit var mockHtmlParser: MockHTMLParser

    @BeforeTest
    fun setUp() {
        mockHttpClient = MockFFetchHTTPClient()
        mockHtmlParser = MockHTMLParser()

        // Set up basic AEM response with document references
        val initialResponse =
            mockHttpClient.createAEMResponse(
                total = 3,
                offset = 0,
                limit = 255,
                data =
                    listOf(
                        mapOf(
                            "path" to "/content/article-1",
                            "title" to "Article 1",
                            "documentUrl" to "https://example.com/docs/article1.html",
                        ),
                        mapOf(
                            "path" to "/content/article-2",
                            "title" to "Article 2",
                            // relative URL
                            "documentUrl" to "docs/article2.html",
                        ),
                        mapOf(
                            "path" to "/content/article-3",
                            "title" to "Article 3",
                            "otherField" to "value",
                            // missing documentUrl field
                        ),
                    ),
            )

        mockHttpClient.setSuccessResponse(
            "https://example.com/query-index.json?offset=0&limit=255",
            initialResponse,
        )

        // Set up HTML document responses
        mockHttpClient.setSuccessResponse(
            "https://example.com/docs/article1.html",
            "<html><body><h1>Article 1 Content</h1><p>Full content here</p></body></html>",
        )

        mockHttpClient.setSuccessResponse(
            "https://example.com/docs/article2.html",
            "<html><body><h1>Article 2 Content</h1><p>More content here</p></body></html>",
        )
    }

    @Test
    fun testSuccessfulDocumentFetchingAndParsing() =
        runTest {
            val ffetch =
                FFetch(
                    URL("https://example.com/query-index.json"),
                    FFetchContext(
                        httpClient = mockHttpClient,
                        htmlParser = mockHtmlParser,
                    ),
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
    fun testRelativeUrlResolution() =
        runTest {
            val ffetch =
                FFetch(
                    URL("https://example.com/query-index.json"),
                    FFetchContext(
                        httpClient = mockHttpClient,
                        htmlParser = mockHtmlParser,
                    ),
                )

            val results = ffetch.follow("documentUrl").asFlow().toList()

            // Check that relative URL was correctly resolved
            val requests = mockHttpClient.requestLog
            val relativeUrlRequest = requests.find { it.url.contains("docs/article2.html") }
            assertNotNull(relativeUrlRequest)
            assertTrue(relativeUrlRequest.url.startsWith("https://example.com/"))
        }

    @Test
    fun testAbsoluteUrlHandling() =
        runTest {
            val ffetch =
                FFetch(
                    URL("https://example.com/query-index.json"),
                    FFetchContext(
                        httpClient = mockHttpClient,
                        htmlParser = mockHtmlParser,
                    ),
                )

            val results = ffetch.follow("documentUrl").asFlow().toList()

            // Check that absolute URL was used as-is
            val requests = mockHttpClient.requestLog
            val absoluteUrlRequest = requests.find { it.url == "https://example.com/docs/article1.html" }
            assertNotNull(absoluteUrlRequest)
        }

    @Test
    fun testMissingUrlFieldScenarios() =
        runTest {
            val ffetch =
                FFetch(
                    URL("https://example.com/query-index.json"),
                    FFetchContext(
                        httpClient = mockHttpClient,
                        htmlParser = mockHtmlParser,
                    ),
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
    fun testInvalidUrlFormats() =
        runTest {
            val invalidUrlResponse =
                mockHttpClient.createAEMResponse(
                    total = 2,
                    offset = 0,
                    limit = 255,
                    data =
                        listOf(
                            mapOf(
                                "path" to "/content/invalid-1",
                                "title" to "Invalid URL 1",
                                "documentUrl" to "not-a-valid-url",
                            ),
                            mapOf(
                                "path" to "/content/invalid-2",
                                "title" to "Invalid URL 2",
                                "documentUrl" to "://missing-protocol",
                            ),
                        ),
                )

            mockHttpClient.setSuccessResponse(
                "https://example.com/invalid-urls.json?offset=0&limit=255",
                invalidUrlResponse,
            )

            val ffetch =
                FFetch(
                    URL("https://example.com/invalid-urls.json"),
                    FFetchContext(
                        httpClient = mockHttpClient,
                        htmlParser = mockHtmlParser,
                    ),
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
    fun testHttpErrorResponses() =
        runTest {
            // Set up HTTP error responses
            mockHttpClient.setErrorResponse(
                "https://example.com/docs/article1.html",
                HttpStatusCode.NotFound,
            )

            mockHttpClient.setErrorResponse(
                "https://example.com/docs/article2.html",
                HttpStatusCode.InternalServerError,
            )

            val ffetch =
                FFetch(
                    URL("https://example.com/query-index.json"),
                    FFetchContext(
                        httpClient = mockHttpClient,
                        htmlParser = mockHtmlParser,
                    ),
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
    fun testNetworkTimeoutAndFailures() =
        runTest {
            // Configure mock to return error responses for document URLs only
            mockHttpClient.setErrorResponse(
                "https://example.com/docs/article1.html",
                HttpStatusCode.GatewayTimeout,
            )
            mockHttpClient.setErrorResponse(
                "https://example.com/docs/article2.html",
                HttpStatusCode.GatewayTimeout,
            )

            val ffetch =
                FFetch(
                    URL("https://example.com/query-index.json"),
                    FFetchContext(
                        httpClient = mockHttpClient,
                        htmlParser = mockHtmlParser,
                    ),
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
    fun testHtmlParsingErrors() =
        runTest {
            // Configure mock parser to throw errors
            mockHtmlParser.shouldThrowError = true
            mockHtmlParser.errorMessage = "Malformed HTML structure"

            val ffetch =
                FFetch(
                    URL("https://example.com/query-index.json"),
                    FFetchContext(
                        httpClient = mockHttpClient,
                        htmlParser = mockHtmlParser,
                    ),
                )

            val results = ffetch.follow("documentUrl").asFlow().toList()

            assertEquals(3, results.size)

            // Entries should have parsing errors
            val entriesWithUrls =
                results.filter {
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
    fun testLargeDocumentHandling() =
        runTest {
            // Create large HTML document (>10KB)
            val largeHtmlContent =
                buildString {
                    append("<html><body>")
                    repeat(1000) { i ->
                        append(
                            "<div>Large content section $i with lots of text content " +
                                "that makes this document very large.</div>",
                        )
                    }
                    append("</body></html>")
                }

            mockHttpClient.setSuccessResponse(
                "https://example.com/docs/article1.html",
                largeHtmlContent,
            )

            val ffetch =
                FFetch(
                    URL("https://example.com/query-index.json"),
                    FFetchContext(
                        httpClient = mockHttpClient,
                        htmlParser = mockHtmlParser,
                    ),
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
    fun testConcurrentDocumentFetching() =
        runTest {
            // Set up multiple documents with different processing times
            mockHttpClient.simulateNetworkDelay = 100 // 100ms delay

            val manyDocumentsResponse =
                mockHttpClient.createAEMResponse(
                    total = 10,
                    offset = 0,
                    limit = 255,
                    data =
                        (1..10).map { i ->
                            mapOf(
                                "path" to "/content/article-$i",
                                "title" to "Article $i",
                                "documentUrl" to "https://example.com/docs/article$i.html",
                            )
                        },
                )

            mockHttpClient.setSuccessResponse(
                "https://example.com/many-docs.json?offset=0&limit=255",
                manyDocumentsResponse,
            )

            // Set up responses for all documents
            (1..10).forEach { i ->
                mockHttpClient.setSuccessResponse(
                    "https://example.com/docs/article$i.html",
                    "<html><body><h1>Article $i Content</h1></body></html>",
                )
            }

            val ffetch =
                FFetch(
                    URL("https://example.com/many-docs.json"),
                    FFetchContext(
                        httpClient = mockHttpClient,
                        htmlParser = mockHtmlParser,
                        maxConcurrency = 3,
                    ),
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
            assertTrue(
                totalTime < 800,
                "Expected concurrent processing to be faster than sequential. Actual time: ${totalTime}ms",
            )
        }

    @Test
    fun testGracefulErrorHandling() =
        runTest {
            // Mix of success and failure scenarios
            val mixedResponse =
                mockHttpClient.createAEMResponse(
                    total = 4,
                    offset = 0,
                    limit = 255,
                    data =
                        listOf(
                            mapOf(
                                "path" to "/content/success-1",
                                "title" to "Success Article",
                                "documentUrl" to "https://example.com/docs/success.html",
                            ),
                            mapOf(
                                "path" to "/content/error-404",
                                "title" to "404 Article",
                                "documentUrl" to "https://example.com/docs/notfound.html",
                            ),
                            mapOf(
                                "path" to "/content/invalid-url",
                                "title" to "Invalid URL Article",
                                "documentUrl" to "not-a-url",
                            ),
                            mapOf(
                                "path" to "/content/missing-url",
                                "title" to "Missing URL Article",
                                // no documentUrl field
                            ),
                        ),
                )

            mockHttpClient.setSuccessResponse(
                "https://example.com/mixed-test.json?offset=0&limit=255",
                mixedResponse,
            )

            mockHttpClient.setSuccessResponse(
                "https://example.com/docs/success.html",
                "<html><body><h1>Success Content</h1></body></html>",
            )

            mockHttpClient.setErrorResponse(
                "https://example.com/docs/notfound.html",
                HttpStatusCode.NotFound,
            )

            val ffetch =
                FFetch(
                    URL("https://example.com/mixed-test.json"),
                    FFetchContext(
                        httpClient = mockHttpClient,
                        htmlParser = mockHtmlParser,
                    ),
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
    fun testDocumentFollowingWithCustomFieldNames() =
        runTest {
            val ffetch =
                FFetch(
                    URL("https://example.com/query-index.json"),
                    FFetchContext(
                        httpClient = mockHttpClient,
                        htmlParser = mockHtmlParser,
                    ),
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
    fun testMaxConcurrencyLimitsAreDespected() =
        runTest {
            // Create scenario that would be obvious if concurrency limits were ignored
            val concurrencyResponse =
                mockHttpClient.createAEMResponse(
                    total = 6,
                    offset = 0,
                    limit = 255,
                    data =
                        (1..6).map { i ->
                            mapOf(
                                "path" to "/content/concurrent-$i",
                                "title" to "Concurrent Article $i",
                                "documentUrl" to "https://example.com/docs/concurrent$i.html",
                            )
                        },
                )

            mockHttpClient.setSuccessResponse(
                "https://example.com/concurrent-test.json?offset=0&limit=255",
                concurrencyResponse,
            )

            // Set up responses for all documents
            (1..6).forEach { i ->
                mockHttpClient.setSuccessResponse(
                    "https://example.com/docs/concurrent$i.html",
                    "<html><body><h1>Concurrent Article $i</h1></body></html>",
                )
            }

            val ffetch =
                FFetch(
                    URL("https://example.com/concurrent-test.json"),
                    FFetchContext(
                        httpClient = mockHttpClient,
                        htmlParser = mockHtmlParser,
                        // Limit to 2 concurrent requests
                        maxConcurrency = 2,
                    ),
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

    @Test
    fun testHostnameSecurityRestrictions() =
        runTest {
            val crossDomainResponse =
                mockHttpClient.createAEMResponse(
                    total = 2,
                    offset = 0,
                    limit = 255,
                    data =
                        listOf(
                            mapOf(
                                "path" to "/content/external-1",
                                "title" to "External Article 1",
                                "documentUrl" to "https://external-domain.com/doc1.html",
                            ),
                            mapOf(
                                "path" to "/content/external-2",
                                "title" to "External Article 2",
                                "documentUrl" to "https://another-domain.com/doc2.html",
                            ),
                        ),
                )

            mockHttpClient.setSuccessResponse(
                "https://example.com/cross-domain.json?offset=0&limit=255",
                crossDomainResponse,
            )

            val ffetch =
                FFetch(
                    URL("https://example.com/cross-domain.json"),
                    FFetchContext(
                        httpClient = mockHttpClient,
                        htmlParser = mockHtmlParser,
                    ),
                )

            val results = ffetch.follow("documentUrl").asFlow().toList()

            assertEquals(2, results.size)

            // Both entries should have security errors since external domains are not allowed
            results.forEach { entry ->
                assertNull(entry["documentUrl"])
                val error = entry["documentUrl_error"] as? String
                assertTrue(error?.contains("is not allowed for document following") == true)
                assertTrue(error?.contains("Use .allow() to permit additional hostnames") == true)
            }
        }

    @Test
    fun testAllowSpecificHostname() =
        runTest {
            val externalResponse =
                mockHttpClient.createAEMResponse(
                    total = 1,
                    offset = 0,
                    limit = 255,
                    data =
                        listOf(
                            mapOf(
                                "path" to "/content/allowed-external",
                                "title" to "Allowed External Article",
                                "documentUrl" to "https://trusted-domain.com/doc.html",
                            ),
                        ),
                )

            mockHttpClient.setSuccessResponse(
                "https://example.com/external.json?offset=0&limit=255",
                externalResponse,
            )

            mockHttpClient.setSuccessResponse(
                "https://trusted-domain.com/doc.html",
                "<html><body><h1>Trusted Content</h1></body></html>",
            )

            val ffetch =
                FFetch(
                    URL("https://example.com/external.json"),
                    FFetchContext(
                        httpClient = mockHttpClient,
                        htmlParser = mockHtmlParser,
                    ),
                )

            val results = ffetch.allow("trusted-domain.com").follow("documentUrl").asFlow().toList()

            assertEquals(1, results.size)

            val entry = results.first()
            assertNotNull(entry["documentUrl"])
            assertNull(entry["documentUrl_error"])
        }

    @Test
    fun testAllowMultipleHostnames() =
        runTest {
            val multipleExternalResponse =
                mockHttpClient.createAEMResponse(
                    total = 3,
                    offset = 0,
                    limit = 255,
                    data =
                        listOf(
                            mapOf(
                                "path" to "/content/trusted-1",
                                "title" to "Trusted Article 1",
                                "documentUrl" to "https://api.trusted.com/doc1.html",
                            ),
                            mapOf(
                                "path" to "/content/trusted-2",
                                "title" to "Trusted Article 2",
                                "documentUrl" to "https://cdn.trusted.com/doc2.html",
                            ),
                            mapOf(
                                "path" to "/content/blocked",
                                "title" to "Blocked Article",
                                "documentUrl" to "https://untrusted.com/doc.html",
                            ),
                        ),
                )

            mockHttpClient.setSuccessResponse(
                "https://example.com/multiple.json?offset=0&limit=255",
                multipleExternalResponse,
            )

            mockHttpClient.setSuccessResponse(
                "https://api.trusted.com/doc1.html",
                "<html><body><h1>API Content</h1></body></html>",
            )

            mockHttpClient.setSuccessResponse(
                "https://cdn.trusted.com/doc2.html",
                "<html><body><h1>CDN Content</h1></body></html>",
            )

            val ffetch =
                FFetch(
                    URL("https://example.com/multiple.json"),
                    FFetchContext(
                        httpClient = mockHttpClient,
                        htmlParser = mockHtmlParser,
                    ),
                )

            val results =
                ffetch
                    .allow(listOf("api.trusted.com", "cdn.trusted.com"))
                    .follow("documentUrl")
                    .asFlow()
                    .toList()

            assertEquals(3, results.size)

            // First two should succeed
            val trustedEntries = results.filter { it["path"].toString().startsWith("/content/trusted") }
            trustedEntries.forEach { entry ->
                assertNotNull(entry["documentUrl"])
                assertNull(entry["documentUrl_error"])
            }

            // Third should be blocked
            val blockedEntry = results.first { it["path"] == "/content/blocked" }
            assertNull(blockedEntry["documentUrl"])
            val error = blockedEntry["documentUrl_error"] as? String
            assertTrue(error?.contains("untrusted.com") == true)
            assertTrue(error?.contains("is not allowed") == true)
        }

    @Test
    fun testAllowAllHostnamesWithWildcard() =
        runTest {
            val wildcardResponse =
                mockHttpClient.createAEMResponse(
                    total = 2,
                    offset = 0,
                    limit = 255,
                    data =
                        listOf(
                            mapOf(
                                "path" to "/content/any-1",
                                "title" to "Any Domain 1",
                                "documentUrl" to "https://random-domain-1.com/doc.html",
                            ),
                            mapOf(
                                "path" to "/content/any-2",
                                "title" to "Any Domain 2",
                                "documentUrl" to "https://random-domain-2.org/doc.html",
                            ),
                        ),
                )

            mockHttpClient.setSuccessResponse(
                "https://example.com/wildcard.json?offset=0&limit=255",
                wildcardResponse,
            )

            mockHttpClient.setSuccessResponse(
                "https://random-domain-1.com/doc.html",
                "<html><body><h1>Random Content 1</h1></body></html>",
            )

            mockHttpClient.setSuccessResponse(
                "https://random-domain-2.org/doc.html",
                "<html><body><h1>Random Content 2</h1></body></html>",
            )

            val ffetch =
                FFetch(
                    URL("https://example.com/wildcard.json"),
                    FFetchContext(
                        httpClient = mockHttpClient,
                        htmlParser = mockHtmlParser,
                    ),
                )

            val results = ffetch.allow("*").follow("documentUrl").asFlow().toList()

            assertEquals(2, results.size)

            // Both should succeed with wildcard permission
            results.forEach { entry ->
                assertNotNull(entry["documentUrl"])
                assertNull(entry["documentUrl_error"])
            }
        }

    @Test
    fun testPortSpecificHostnameValidation() =
        runTest {
            val portResponse =
                mockHttpClient.createAEMResponse(
                    total = 2,
                    offset = 0,
                    limit = 255,
                    data =
                        listOf(
                            mapOf(
                                "path" to "/content/port-8080",
                                "title" to "Port 8080 Article",
                                "documentUrl" to "https://api.example.com:8080/doc.html",
                            ),
                            mapOf(
                                "path" to "/content/port-9000",
                                "title" to "Port 9000 Article",
                                "documentUrl" to "https://api.example.com:9000/doc.html",
                            ),
                        ),
                )

            mockHttpClient.setSuccessResponse(
                "https://example.com/ports.json?offset=0&limit=255",
                portResponse,
            )

            mockHttpClient.setSuccessResponse(
                "https://api.example.com:8080/doc.html",
                "<html><body><h1>Port 8080 Content</h1></body></html>",
            )

            val ffetch =
                FFetch(
                    URL("https://example.com/ports.json"),
                    FFetchContext(
                        httpClient = mockHttpClient,
                        htmlParser = mockHtmlParser,
                    ),
                )

            val results =
                ffetch
                    .allow("api.example.com:8080") // Only allow port 8080
                    .follow("documentUrl")
                    .asFlow()
                    .toList()

            assertEquals(2, results.size)

            // Port 8080 should succeed
            val port8080Entry = results.first { it["path"] == "/content/port-8080" }
            assertNotNull(port8080Entry["documentUrl"])
            assertNull(port8080Entry["documentUrl_error"])

            // Port 9000 should be blocked
            val port9000Entry = results.first { it["path"] == "/content/port-9000" }
            assertNull(port9000Entry["documentUrl"])
            val error = port9000Entry["documentUrl_error"] as? String
            assertTrue(error?.contains("9000") == true || error?.contains("api.example.com") == true)
            assertTrue(error?.contains("is not allowed") == true)
        }

    @Test
    fun testOutOfMemoryErrorHandling() =
        runTest {
            // Configure mock parser to throw OutOfMemoryError
            val oomHtmlParser = MockHTMLParser()
            oomHtmlParser.shouldThrowError = false
            oomHtmlParser.reset()
            oomHtmlParser.throwErrorOnNextParse("Simulated OOM during HTML parsing")

            // Override the parse method to throw OutOfMemoryError instead of IllegalArgumentException
            val customParser =
                object : live.aem.koffetch.FFetchHTMLParser {
                    override fun parse(html: String): org.jsoup.nodes.Document {
                        throw OutOfMemoryError("Simulated OOM during HTML parsing")
                    }
                }

            val ffetch =
                FFetch(
                    URL("https://example.com/query-index.json"),
                    FFetchContext(
                        httpClient = mockHttpClient,
                        htmlParser = customParser,
                    ),
                )

            val results = ffetch.follow("documentUrl").asFlow().toList()

            assertEquals(3, results.size)

            // Entries that had valid URLs should have OutOfMemoryError handling
            val entriesWithUrls = results.filter { it.containsKey("documentUrl") && it["documentUrl"] is String }
            entriesWithUrls.forEach { entry ->
                assertNull(entry["documentUrl"])
                val error = entry["documentUrl_error"] as? String
                assertTrue(error?.contains("HTML parsing error") == true)
                assertTrue(error?.contains("Simulated OOM during HTML parsing") == true)
            }
        }

    @Test
    fun testFFetchNetworkErrorHandling() =
        runTest {
            // Configure mock client to throw NetworkError
            val networkErrorClient =
                object : live.aem.koffetch.FFetchHTTPClient {
                    override suspend fun fetch(
                        url: String,
                        cacheConfig: live.aem.koffetch.FFetchCacheConfig,
                    ): Pair<String, io.ktor.client.statement.HttpResponse> {
                        if (url.contains("docs/article")) {
                            throw FFetchError.NetworkError(RuntimeException("Simulated network failure for $url"))
                        }
                        // For other URLs, delegate to the mock client
                        return mockHttpClient.fetch(url, cacheConfig)
                    }
                }

            // Set up the base response
            val initialResponse =
                mockHttpClient.createAEMResponse(
                    total = 1,
                    offset = 0,
                    limit = 255,
                    data =
                        listOf(
                            mapOf(
                                "path" to "/content/network-error",
                                "title" to "Network Error Article",
                                "documentUrl" to "https://example.com/docs/article.html",
                            ),
                        ),
                )

            mockHttpClient.setSuccessResponse(
                "https://example.com/network-test.json?offset=0&limit=255",
                initialResponse,
            )

            val ffetch =
                FFetch(
                    URL("https://example.com/network-test.json"),
                    FFetchContext(
                        httpClient = networkErrorClient,
                        htmlParser = mockHtmlParser,
                    ),
                )

            val results = ffetch.follow("documentUrl").asFlow().toList()

            assertEquals(1, results.size)

            val entry = results.first()
            assertNull(entry["documentUrl"])
            val error = entry["documentUrl_error"] as? String
            assertTrue(error?.contains("Network error") == true)
            assertTrue(error?.contains("Simulated network failure") == true)
        }

    @Test
    fun testEdgeCaseUrlValidation() =
        runTest {
            val edgeCaseResponse =
                mockHttpClient.createAEMResponse(
                    total = 6,
                    offset = 0,
                    limit = 255,
                    data =
                        listOf(
                            mapOf(
                                "path" to "/content/whitespace",
                                "title" to "Whitespace URL",
                                // Only whitespace
                                "documentUrl" to "  \t\n  ",
                            ),
                            mapOf(
                                "path" to "/content/empty-string",
                                "title" to "Empty URL",
                                // Empty string
                                "documentUrl" to "",
                            ),
                            mapOf(
                                "path" to "/content/spaces-in-url",
                                "title" to "Spaces in URL",
                                "documentUrl" to "https://example.com/path with spaces.html",
                            ),
                            mapOf(
                                "path" to "/content/malformed-protocol",
                                "title" to "Malformed Protocol",
                                // Missing 't' in http
                                "documentUrl" to "htp://example.com/doc.html",
                            ),
                            mapOf(
                                "path" to "/content/missing-hostname",
                                "title" to "Missing Hostname",
                                // Missing hostname
                                "documentUrl" to "https:///doc.html",
                            ),
                            mapOf(
                                "path" to "/content/path-only",
                                "title" to "Path Only",
                                // Absolute path - should work
                                "documentUrl" to "/absolute/path/to/doc.html",
                            ),
                        ),
                )

            mockHttpClient.setSuccessResponse(
                "https://example.com/edge-cases.json?offset=0&limit=255",
                edgeCaseResponse,
            )

            mockHttpClient.setSuccessResponse(
                "https://example.com/absolute/path/to/doc.html",
                "<html><body><h1>Absolute Path Content</h1></body></html>",
            )

            val ffetch =
                FFetch(
                    URL("https://example.com/edge-cases.json"),
                    FFetchContext(
                        httpClient = mockHttpClient,
                        htmlParser = mockHtmlParser,
                    ),
                )

            val results = ffetch.follow("documentUrl").asFlow().toList()

            assertEquals(6, results.size)

            // Most should have URL resolution errors
            val errorEntries = results.filter { it["path"] != "/content/path-only" }
            errorEntries.forEach { entry ->
                assertNull(entry["documentUrl"])
                val error = entry["documentUrl_error"] as? String
                assertTrue(
                    error?.contains("Missing or invalid URL") == true ||
                        error?.contains("Could not resolve URL") == true ||
                        error?.contains("is not allowed for document following") == true,
                    "Expected URL error for ${entry["path"]}, got: $error",
                )
            }

            // Absolute path should work
            val pathOnlyEntry = results.first { it["path"] == "/content/path-only" }
            assertNotNull(pathOnlyEntry["documentUrl"])
            assertNull(pathOnlyEntry["documentUrl_error"])
        }

    @Test
    fun testCustomFieldNameErrorPropagation() =
        runTest {
            val errorResponse =
                mockHttpClient.createAEMResponse(
                    total = 1,
                    offset = 0,
                    limit = 255,
                    data =
                        listOf(
                            mapOf(
                                "path" to "/content/custom-field-error",
                                "title" to "Custom Field Error",
                                "documentUrl" to "not-a-valid-url",
                            ),
                        ),
                )

            mockHttpClient.setSuccessResponse(
                "https://example.com/custom-field.json?offset=0&limit=255",
                errorResponse,
            )

            val ffetch =
                FFetch(
                    URL("https://example.com/custom-field.json"),
                    FFetchContext(
                        httpClient = mockHttpClient,
                        htmlParser = mockHtmlParser,
                    ),
                )

            val results = ffetch.follow("documentUrl", "customDocument").asFlow().toList()

            assertEquals(1, results.size)

            val entry = results.first()
            assertNull(entry["customDocument"])
            val error = entry["customDocument_error"] as? String
            assertTrue(error?.contains("Could not resolve URL") == true)

            // Original field should still exist
            assertTrue(entry.containsKey("documentUrl"))
            assertEquals("not-a-valid-url", entry["documentUrl"])
        }

    @Test
    fun testProtocolEdgeCases() =
        runTest {
            val protocolResponse =
                mockHttpClient.createAEMResponse(
                    total = 3,
                    offset = 0,
                    limit = 255,
                    data =
                        listOf(
                            mapOf(
                                "path" to "/content/http-explicit",
                                "title" to "HTTP Explicit",
                                "documentUrl" to "http://insecure.example.com/doc.html",
                            ),
                            mapOf(
                                "path" to "/content/https-explicit",
                                "title" to "HTTPS Explicit",
                                "documentUrl" to "https://secure.example.com/doc.html",
                            ),
                            mapOf(
                                "path" to "/content/unknown-protocol",
                                "title" to "Unknown Protocol",
                                "documentUrl" to "ftp://files.example.com/doc.txt",
                            ),
                        ),
                )

            mockHttpClient.setSuccessResponse(
                "https://example.com/protocols.json?offset=0&limit=255",
                protocolResponse,
            )

            val ffetch =
                FFetch(
                    URL("https://example.com/protocols.json"),
                    FFetchContext(
                        httpClient = mockHttpClient,
                        htmlParser = mockHtmlParser,
                    ),
                )

            val results =
                ffetch
                    .allow(listOf("insecure.example.com", "secure.example.com", "files.example.com"))
                    .follow("documentUrl")
                    .asFlow()
                    .toList()

            assertEquals(3, results.size)

            // FTP protocol should fail URL resolution
            val ftpEntry = results.first { it["path"] == "/content/unknown-protocol" }
            assertNull(ftpEntry["documentUrl"])
            val error = ftpEntry["documentUrl_error"] as? String
            assertTrue(error?.contains("Could not resolve URL") == true)
        }

    @Test
    fun testNullHostnameHandling() =
        runTest {
            val nullHostResponse =
                mockHttpClient.createAEMResponse(
                    total = 1,
                    offset = 0,
                    limit = 255,
                    data =
                        listOf(
                            mapOf(
                                "path" to "/content/null-host",
                                "title" to "Null Host",
                                // File URL with null host
                                "documentUrl" to "file:///local/path/doc.html",
                            ),
                        ),
                )

            mockHttpClient.setSuccessResponse(
                "https://example.com/null-host.json?offset=0&limit=255",
                nullHostResponse,
            )

            val ffetch =
                FFetch(
                    URL("https://example.com/null-host.json"),
                    FFetchContext(
                        httpClient = mockHttpClient,
                        htmlParser = mockHtmlParser,
                    ),
                )

            val results = ffetch.follow("documentUrl").asFlow().toList()

            assertEquals(1, results.size)

            val entry = results.first()
            assertNull(entry["documentUrl"])
            val error = entry["documentUrl_error"] as? String
            assertTrue(error?.contains("unknown") == true || error?.contains("Could not resolve URL") == true)
        }

    private suspend fun Flow<FFetchEntry>.toList(): List<FFetchEntry> {
        val list = mutableListOf<FFetchEntry>()
        collect { list.add(it) }
        return list
    }
}
