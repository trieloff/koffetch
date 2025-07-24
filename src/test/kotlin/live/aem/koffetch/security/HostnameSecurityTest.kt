//
// HostnameSecurityTest.kt
// KotlinFFetch
//
// Security tests for hostname restrictions in document following
//

package live.aem.koffetch.security

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import live.aem.koffetch.*
import live.aem.koffetch.extensions.*
import live.aem.koffetch.mock.*
import java.net.URL
import kotlin.test.*

class HostnameSecurityTest {
    private lateinit var mockHttpClient: MockFFetchHTTPClient

    @BeforeTest
    fun setUp() {
        mockHttpClient = MockFFetchHTTPClient()

        // Set up basic AEM response with document reference
        val initialResponse =
            mockHttpClient.createAEMResponse(
                total = 2,
                offset = 0,
                limit = 255,
                data =
                    listOf(
                        mapOf(
                            "path" to "/content/article-1",
                            "title" to "Test Article",
                            "documentUrl" to "https://example.com/doc1.html",
                        ),
                        mapOf(
                            "path" to "/content/article-2",
                            "title" to "External Article",
                            "documentUrl" to "https://external.com/doc2.html",
                        ),
                    ),
            )

        mockHttpClient.setSuccessResponse(
            "https://example.com/query-index.json?offset=0&limit=255",
            initialResponse,
        )

        // Set up HTML responses for document URLs
        mockHttpClient.setSuccessResponse(
            "https://example.com/doc1.html",
            "<html><body><h1>Same Domain Document</h1></body></html>",
        )

        mockHttpClient.setSuccessResponse(
            "https://external.com/doc2.html",
            "<html><body><h1>External Domain Document</h1></body></html>",
        )
    }

    @Test
    fun testDefaultHostnameRestriction() =
        runTest {
            val ffetch =
                FFetch(
                    URL("https://example.com/query-index.json"),
                    FFetchContext(httpClient = mockHttpClient),
                )

            val results = ffetch.follow("documentUrl").asFlow().toList()

            assertEquals(2, results.size)

            // First entry should succeed (same hostname)
            val firstEntry = results.first { it["path"] == "/content/article-1" }
            assertNotNull(firstEntry["documentUrl"])
            assertNull(firstEntry["documentUrl_error"])

            // Second entry should fail (external hostname)
            val secondEntry = results.first { it["path"] == "/content/article-2" }
            assertNull(secondEntry["documentUrl"])
            val error = firstEntry["documentUrl_error"] as? String
            assertTrue(error?.contains("not allowed") == true || error == null)
        }

    @Test
    fun testAllowSpecificHostname() =
        runTest {
            val ffetch =
                FFetch(
                    URL("https://example.com/query-index.json"),
                    FFetchContext(httpClient = mockHttpClient),
                ).allow("external.com")

            val results = ffetch.follow("documentUrl").asFlow().toList()

            assertEquals(2, results.size)

            // Both entries should now succeed
            results.forEach { entry ->
                assertNotNull(entry["documentUrl"])
                assertNull(entry["documentUrl_error"])
            }
        }

    @Test
    fun testAllowMultipleHostnames() =
        runTest {
            val ffetch =
                FFetch(
                    URL("https://example.com/query-index.json"),
                    FFetchContext(httpClient = mockHttpClient),
                ).allow(listOf("external.com", "another.com"))

            val results = ffetch.follow("documentUrl").asFlow().toList()

            assertEquals(2, results.size)

            // Both entries should succeed
            results.forEach { entry ->
                assertNotNull(entry["documentUrl"])
                assertNull(entry["documentUrl_error"])
            }
        }

    @Test
    fun testWildcardAllowsBehavior() =
        runTest {
            val ffetch =
                FFetch(
                    URL("https://example.com/query-index.json"),
                    FFetchContext(httpClient = mockHttpClient),
                ).allow("*")

            val results = ffetch.follow("documentUrl").asFlow().toList()

            assertEquals(2, results.size)

            // All entries should succeed with wildcard
            results.forEach { entry ->
                assertNotNull(entry["documentUrl"])
                assertNull(entry["documentUrl_error"])
            }
        }

    @Test
    fun testBlockedHostnameScenarios() =
        runTest {
            // Set up response with blocked hostname
            val responseWithBlocked =
                mockHttpClient.createAEMResponse(
                    total = 1,
                    offset = 0,
                    limit = 255,
                    data =
                        listOf(
                            mapOf(
                                "path" to "/content/blocked-article",
                                "title" to "Blocked Article",
                                "documentUrl" to "https://malicious.com/doc.html",
                            ),
                        ),
                )

            mockHttpClient.setSuccessResponse(
                "https://example.com/blocked-query.json?offset=0&limit=255",
                responseWithBlocked,
            )

            val ffetch =
                FFetch(
                    URL("https://example.com/blocked-query.json"),
                    FFetchContext(httpClient = mockHttpClient),
                )

            val results = ffetch.follow("documentUrl").asFlow().toList()

            assertEquals(1, results.size)

            val entry = results.first()
            assertNull(entry["documentUrl"])
            val error = entry["documentUrl_error"] as? String
            assertTrue(error?.contains("malicious.com") == true)
            assertTrue(error?.contains("not allowed") == true)
        }

    @Test
    fun testMaliciousUrlPatterns() =
        runTest {
            val maliciousUrls =
                listOf(
                    "file:///etc/passwd",
                    "data:text/html,<script>alert('xss')</script>",
                    "javascript:alert('xss')",
                    "ftp://malicious.com/file",
                    "ldap://malicious.com/query",
                )

            maliciousUrls.forEachIndexed { index, maliciousUrl ->
                val response =
                    mockHttpClient.createAEMResponse(
                        total = 1,
                        offset = 0,
                        limit = 255,
                        data =
                            listOf(
                                mapOf(
                                    "path" to "/content/malicious-$index",
                                    "title" to "Malicious URL Test $index",
                                    "documentUrl" to maliciousUrl,
                                ),
                            ),
                    )

                mockHttpClient.setSuccessResponse(
                    "https://example.com/malicious-$index.json?offset=0&limit=255",
                    response,
                )

                val ffetch =
                    FFetch(
                        URL("https://example.com/malicious-$index.json"),
                        FFetchContext(httpClient = mockHttpClient),
                    )

                val results = ffetch.follow("documentUrl").asFlow().toList()

                assertEquals(1, results.size)

                val entry = results.first()
                assertNull(entry["documentUrl"])
                val error = entry["documentUrl_error"] as? String
                assertNotNull(error, "Expected error for malicious URL: $maliciousUrl")
            }
        }

    @Test
    fun testSubdomainHandling() =
        runTest {
            val subdomainResponse =
                mockHttpClient.createAEMResponse(
                    total = 2,
                    offset = 0,
                    limit = 255,
                    data =
                        listOf(
                            mapOf(
                                "path" to "/content/subdomain-1",
                                "title" to "Subdomain Test 1",
                                "documentUrl" to "https://api.example.com/doc1.html",
                            ),
                            mapOf(
                                "path" to "/content/subdomain-2",
                                "title" to "Subdomain Test 2",
                                "documentUrl" to "https://cdn.example.com/doc2.html",
                            ),
                        ),
                )

            mockHttpClient.setSuccessResponse(
                "https://example.com/subdomain-test.json?offset=0&limit=255",
                subdomainResponse,
            )

            mockHttpClient.setSuccessResponse(
                "https://api.example.com/doc1.html",
                "<html><body><h1>API Document</h1></body></html>",
            )

            mockHttpClient.setSuccessResponse(
                "https://cdn.example.com/doc2.html",
                "<html><body><h1>CDN Document</h1></body></html>",
            )

            val ffetch =
                FFetch(
                    URL("https://example.com/subdomain-test.json"),
                    FFetchContext(httpClient = mockHttpClient),
                )

            val results = ffetch.follow("documentUrl").asFlow().toList()

            assertEquals(2, results.size)

            // Subdomains should be blocked by default (exact hostname match required)
            results.forEach { entry ->
                assertNull(entry["documentUrl"])
                val error = entry["documentUrl_error"] as? String
                assertTrue(error?.contains("not allowed") == true)
            }

            // Now allow the subdomains explicitly
            val allowedFFetch = ffetch.allow(listOf("api.example.com", "cdn.example.com"))
            val allowedResults = allowedFFetch.follow("documentUrl").asFlow().toList()

            assertEquals(2, allowedResults.size)

            // Now they should succeed
            allowedResults.forEach { entry ->
                assertNotNull(entry["documentUrl"])
                assertNull(entry["documentUrl_error"])
            }
        }

    @Test
    fun testPortNumberHandling() =
        runTest {
            val portResponse =
                mockHttpClient.createAEMResponse(
                    total = 2,
                    offset = 0,
                    limit = 255,
                    data =
                        listOf(
                            mapOf(
                                "path" to "/content/port-1",
                                "title" to "Port Test 1",
                                "documentUrl" to "https://example.com:8080/doc1.html",
                            ),
                            mapOf(
                                "path" to "/content/port-2",
                                "title" to "Port Test 2",
                                "documentUrl" to "https://example.com:9000/doc2.html",
                            ),
                        ),
                )

            mockHttpClient.setSuccessResponse(
                "https://example.com/port-test.json?offset=0&limit=255",
                portResponse,
            )

            mockHttpClient.setSuccessResponse(
                "https://example.com:8080/doc1.html",
                "<html><body><h1>Port 8080 Document</h1></body></html>",
            )

            mockHttpClient.setSuccessResponse(
                "https://example.com:9000/doc2.html",
                "<html><body><h1>Port 9000 Document</h1></body></html>",
            )

            val ffetch =
                FFetch(
                    URL("https://example.com/port-test.json"),
                    FFetchContext(httpClient = mockHttpClient),
                )

            val results = ffetch.follow("documentUrl").asFlow().toList()

            assertEquals(2, results.size)

            // Port numbers create different hostnames, so should be blocked
            results.forEach { entry ->
                assertNull(entry["documentUrl"])
                val error = entry["documentUrl_error"] as? String
                assertTrue(error?.contains("not allowed") == true)
            }

            // Allow the specific hostname:port combinations
            val allowedFFetch = ffetch.allow(listOf("example.com:8080", "example.com:9000"))
            val allowedResults = allowedFFetch.follow("documentUrl").asFlow().toList()

            assertEquals(2, allowedResults.size)

            // Now they should succeed
            allowedResults.forEach { entry ->
                assertNotNull(entry["documentUrl"])
                assertNull(entry["documentUrl_error"])
            }
        }

    @Test
    fun testSecurityErrorMessagesAreInformative() =
        runTest {
            val response =
                mockHttpClient.createAEMResponse(
                    total = 1,
                    offset = 0,
                    limit = 255,
                    data =
                        listOf(
                            mapOf(
                                "path" to "/content/test-article",
                                "title" to "Test Article",
                                "documentUrl" to "https://blocked.com/doc.html",
                            ),
                        ),
                )

            mockHttpClient.setSuccessResponse(
                "https://example.com/security-test.json?offset=0&limit=255",
                response,
            )

            val ffetch =
                FFetch(
                    URL("https://example.com/security-test.json"),
                    FFetchContext(httpClient = mockHttpClient),
                )

            val results = ffetch.follow("documentUrl").asFlow().toList()

            assertEquals(1, results.size)

            val entry = results.first()
            assertNull(entry["documentUrl"])
            val error = entry["documentUrl_error"] as? String

            assertNotNull(error)
            assertTrue(error.contains("blocked.com"))
            assertTrue(error.contains("not allowed"))
            assertTrue(error.contains("allow()"))
        }

    @Test
    fun testNoHostnameUrlsAreBlocked() =
        runTest {
            val response =
                mockHttpClient.createAEMResponse(
                    total = 1,
                    offset = 0,
                    limit = 255,
                    data =
                        listOf(
                            mapOf(
                                "path" to "/content/no-host-article",
                                "title" to "No Host Article",
                                "documentUrl" to "/relative/path/doc.html",
                            ),
                        ),
                )

            mockHttpClient.setSuccessResponse(
                "https://example.com/no-host-test.json?offset=0&limit=255",
                response,
            )

            mockHttpClient.setSuccessResponse(
                "https://example.com/relative/path/doc.html",
                "<html><body><h1>Relative Path Document</h1></body></html>",
            )

            val ffetch =
                FFetch(
                    URL("https://example.com/no-host-test.json"),
                    FFetchContext(httpClient = mockHttpClient),
                )

            val results = ffetch.follow("documentUrl").asFlow().toList()

            assertEquals(1, results.size)

            val entry = results.first()
            // Relative URLs should be resolved to the same hostname and succeed
            assertNotNull(entry["documentUrl"])
            assertNull(entry["documentUrl_error"])
        }

    private suspend fun Flow<FFetchEntry>.toList(): List<FFetchEntry> {
        val list = mutableListOf<FFetchEntry>()
        collect { list.add(it) }
        return list
    }
}
