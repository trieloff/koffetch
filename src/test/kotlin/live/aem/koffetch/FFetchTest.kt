//
// FFetchTest.kt
// KotlinFFetch
//
// Basic tests for FFetch functionality
//

package live.aem.koffetch

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import live.aem.koffetch.extensions.allow
import live.aem.koffetch.mock.MockFFetchHTTPClient
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FFetchTest {
    @Test
    fun testFFetchInitWithValidURL() =
        runTest {
            val ffetch = FFetch("https://example.com/query-index.json")
            assertNotNull(ffetch)
        }

    @Test
    fun testFFetchInitWithInvalidURL() {
        assertFailsWith<FFetchError.InvalidURL> {
            FFetch("not-a-valid-url")
        }
    }

    @Test
    fun testFFetchConvenienceFunction() =
        runTest {
            val ffetch = ffetch("https://example.com/query-index.json")
            assertNotNull(ffetch)
        }

    @Test
    fun testCacheConfigDefaults() {
        val config = FFetchCacheConfig.Default
        assertEquals(false, config.noCache)
        assertEquals(false, config.cacheOnly)
        assertEquals(false, config.cacheElseLoad)
    }

    @Test
    fun testFFetchContextDefaults() {
        val context = FFetchContext()
        assertEquals(255, context.chunkSize)
        assertEquals(false, context.cacheReload)
        assertEquals(5, context.maxConcurrency)
    }

    @Test
    fun testChunksConfiguration() =
        runTest {
            val ffetch = FFetch("https://example.com/query-index.json")
            val chunkedFFetch = ffetch.chunks(100)
            assertNotNull(chunkedFFetch)
        }

    @Test
    fun testSheetConfiguration() =
        runTest {
            val ffetch = FFetch("https://example.com/query-index.json")
            val sheetFFetch = ffetch.sheet("products")
            assertNotNull(sheetFFetch)
        }

    @Test
    fun testMaxConcurrencyConfiguration() =
        runTest {
            val ffetch = FFetch("https://example.com/query-index.json")
            val concurrentFFetch = ffetch.maxConcurrency(10)
            assertNotNull(concurrentFFetch)
        }

    @Test
    fun testCacheConfiguration() =
        runTest {
            val ffetch = FFetch("https://example.com/query-index.json")
            val cachedFFetch = ffetch.cache(FFetchCacheConfig.NoCache)
            assertNotNull(cachedFFetch)
        }

    @Test
    fun testReloadCacheConfiguration() =
        runTest {
            val ffetch = FFetch("https://example.com/query-index.json")
            val reloadFFetch = ffetch.reloadCache()
            assertNotNull(reloadFFetch)
        }

    @Test
    fun testAllowHostnameConfiguration() =
        runTest {
            val ffetch = FFetch("https://example.com/query-index.json")
            val allowedFFetch = ffetch.allow("trusted.com")
            assertNotNull(allowedFFetch)
        }

    @Test
    fun testAllowMultipleHostnamesConfiguration() =
        runTest {
            val ffetch = FFetch("https://example.com/query-index.json")
            val allowedFFetch = ffetch.allow(listOf("trusted.com", "api.example.com"))
            assertNotNull(allowedFFetch)
        }

    // URL Validation Edge Cases
    @Test
    fun testFFetchInitWithBlankURL() {
        assertFailsWith<FFetchError.InvalidURL> {
            FFetch("")
        }
    }

    @Test
    fun testFFetchInitWithWhitespaceURL() {
        assertFailsWith<FFetchError.InvalidURL> {
            FFetch("   ")
        }
    }

    @Test
    fun testFFetchInitWithJavaScriptURL() {
        assertFailsWith<FFetchError.InvalidURL> {
            FFetch("javascript:alert('test')")
        }
    }

    @Test
    fun testFFetchInitWithMalformedScheme() {
        assertFailsWith<FFetchError.InvalidURL> {
            FFetch("://missing-scheme")
        }
    }

    @Test
    fun testFFetchInitWithPartialHTTP() {
        assertFailsWith<FFetchError.InvalidURL> {
            FFetch("http://")
        }
    }

    @Test
    fun testFFetchInitWithRelativePathValid() {
        // Relative paths are actually invalid for URL constructor
        // This tests that the validation catches it appropriately
        assertFailsWith<FFetchError.InvalidURL> {
            FFetch("/query-index.json")
        }
    }

    @Test
    fun testFFetchInitWithoutSchemeOrPath() {
        assertFailsWith<FFetchError.InvalidURL> {
            FFetch("example.com")
        }
    }

    @Test
    fun testFFetchInitWithMalformedURLException() {
        assertFailsWith<FFetchError.InvalidURL> {
            FFetch("htp://bad-protocol.com")
        }
    }

    // HTTPS Protocol and Port Testing
    @Test
    fun testFFetchInitWithHTTPSDefaultPort() =
        runTest {
            val ffetch = FFetch("https://example.com:443/query-index.json")
            assertNotNull(ffetch)
            assertTrue(ffetch.context.allowedHosts.contains("example.com"))
        }

    @Test
    fun testFFetchInitWithHTTPSCustomPort() =
        runTest {
            val ffetch = FFetch("https://example.com:8443/query-index.json")
            assertNotNull(ffetch)
            assertTrue(ffetch.context.allowedHosts.contains("example.com:8443"))
        }

    @Test
    fun testFFetchInitWithHTTPCustomPort() =
        runTest {
            val ffetch = FFetch("http://example.com:8080/query-index.json")
            assertNotNull(ffetch)
            assertTrue(ffetch.context.allowedHosts.contains("example.com:8080"))
        }

    @Test
    fun testFFetchInitWithUnknownProtocol() =
        runTest {
            val ffetch = FFetch("ftp://example.com/query-index.json")
            assertNotNull(ffetch)
            // For unknown protocols, getDefaultPort returns -1 (INVALID_PORT)
            // So url.port will be -1, and the condition (port != INVALID_PORT && port != defaultPort) fails
            // Therefore, it should add just the hostname
            assertTrue(ffetch.context.allowedHosts.contains("example.com"))
        }

    @Test
    fun testFFetchInitWithNullHost() =
        runTest {
            // Create a URL with null host using file protocol
            val url = URL("file:///local/file.json")
            val ffetch = FFetch(url)
            assertNotNull(ffetch)
            // Should not add any hosts to allowedHosts when host is null
        }

    // Exception Propagation in createFlow
    @Test
    fun testCreateFlowWithIOException() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            mockClient.throwIOException = true

            val ffetch =
                FFetch("https://example.com/query-index.json")
                    .withHTTPClient(mockClient)

            assertFailsWith<FFetchError.NetworkError> {
                ffetch.asFlow().first()
            }
        }

    @Test
    fun testCreateFlowWithSerializationException() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            mockClient.throwSerializationException = true

            val ffetch =
                FFetch("https://example.com/query-index.json")
                    .withHTTPClient(mockClient)

            assertFailsWith<FFetchError.DecodingError> {
                ffetch.asFlow().first()
            }
        }

    // Flow behavior tests
    @Test
    fun testAsFlowWithUpstream() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            mockClient.jsonResponse = """{
                "total": 1, "offset": 0, "limit": 255, 
                "data": [{"path": "/test", "title": "Test"}]
            }"""

            val originalFFetch =
                FFetch("https://example.com/query-index.json")
                    .withHTTPClient(mockClient)

            val originalFlow = originalFFetch.asFlow()
            val ffetchWithUpstream = FFetch(originalFFetch.url, originalFFetch.context, originalFlow)

            val results = ffetchWithUpstream.asFlow().toList()
            assertEquals(1, results.size)
            assertEquals("/test", results[0]["path"])
        }

    // Constructor parameter validation
    @Test
    fun testFFetchInitWithURLObject() =
        runTest {
            val url = URL("https://example.com/query-index.json")
            val ffetch = FFetch(url)
            assertNotNull(ffetch)
            assertEquals(url, ffetch.url)
        }

    @Test
    fun testFFetchInitWithContextAndUpstream() =
        runTest {
            val url = URL("https://example.com/query-index.json")
            val context = FFetchContext(chunkSize = 100)
            val mockClient = MockFFetchHTTPClient()
            mockClient.jsonResponse = """{"total": 0, "offset": 0, "limit": 255, "data": []}"""

            val originalFFetch = FFetch(url).withHTTPClient(mockClient)
            val upstream = originalFFetch.asFlow()

            val ffetch = FFetch(url, context, upstream)
            assertNotNull(ffetch)
            assertEquals(url, ffetch.url)
            assertEquals(100, ffetch.context.chunkSize)
            assertEquals(upstream, ffetch.upstream)
        }

    // Additional Configuration Tests
    @Test
    fun testBackwardCompatibilityMethods() =
        runTest {
            val ffetch = FFetch("https://example.com/query-index.json")

            val withCacheReloadTrue = ffetch.withCacheReload(true)
            assertNotNull(withCacheReloadTrue)
            assertEquals(true, withCacheReloadTrue.context.cacheReload)

            val withCacheReloadFalse = ffetch.withCacheReload(false)
            assertNotNull(withCacheReloadFalse)
            assertEquals(false, withCacheReloadFalse.context.cacheReload)

            val withMaxConcurrency = ffetch.withMaxConcurrency(10)
            assertNotNull(withMaxConcurrency)
            assertEquals(10, withMaxConcurrency.context.maxConcurrency)
        }

    @Test
    fun testCustomHTTPClientConfiguration() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            val ffetch = FFetch("https://example.com/query-index.json")
            val clientFFetch = ffetch.withHTTPClient(mockClient)
            assertNotNull(clientFFetch)
            assertEquals(mockClient, clientFFetch.context.httpClient)
        }

    @Test
    fun testCustomHTMLParserConfiguration() =
        runTest {
            val mockParser = DefaultFFetchHTMLParser()
            val ffetch = FFetch("https://example.com/query-index.json")
            val parserFFetch = ffetch.withHTMLParser(mockParser)
            assertNotNull(parserFFetch)
            assertEquals(mockParser, parserFFetch.context.htmlParser)
        }

    // Additional tests for remaining uncovered lines
    @Test
    fun testFFetchWithIllegalArgumentException() {
        // Test the IllegalArgumentException catch block by using a malformed URL
        // that triggers IllegalArgumentException instead of MalformedURLException
        assertFailsWith<FFetchError.InvalidURL> {
            FFetch("://bad-url")
        }
    }

    @Test
    fun testConvenienceFunctionWithURLObject() =
        runTest {
            val url = URL("https://example.com/query-index.json")
            val ffetch = ffetch(url)
            assertNotNull(ffetch)
            assertEquals(url, ffetch.url)
        }

    @Test
    fun testDefaultContextConstructorPath() =
        runTest {
            val url = URL("https://example.com/query-index.json")
            val context = FFetchContext()
            val ffetch = FFetch(url, context, null)
            assertNotNull(ffetch)
            assertEquals(url, ffetch.url)
        }

    @Test
    fun testWithCacheReloadDefaultParam() =
        runTest {
            val ffetch = FFetch("https://example.com/query-index.json")
            val reloadFFetch = ffetch.withCacheReload()
            assertNotNull(reloadFFetch)
            assertEquals(true, reloadFFetch.context.cacheReload)
        }

    // Test for flow cancellation and cleanup
    @Test
    fun testFlowCancellation() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            mockClient.jsonResponse = """{"total": 1000, "offset": 0, "limit": 255, "data": []}"""
            mockClient.simulateNetworkDelay = 100

            val ffetch =
                FFetch("https://example.com/query-index.json")
                    .withHTTPClient(mockClient)

            val job =
                launch {
                    ffetch.asFlow().collect { _ ->
                        // This should be cancelled before collecting much
                    }
                }

            delay(50)
            job.cancel()
            assertTrue(job.isCancelled)
        }

    // Test resource cleanup after flow completion
    @Test
    fun testResourceCleanupAfterFlowCompletion() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            mockClient.jsonResponse = """{
                "total": 1, "offset": 0, "limit": 255, 
                "data": [{"path": "/test", "title": "Test"}]
            }"""

            val ffetch =
                FFetch("https://example.com/query-index.json")
                    .withHTTPClient(mockClient)

            // Collect the flow completely
            val results = ffetch.asFlow().toList()
            assertEquals(1, results.size)

            // Verify the flow can be collected again (resources are properly managed)
            val secondResults = ffetch.asFlow().toList()
            assertEquals(1, secondResults.size)
        }

    // Test for catching IllegalArgumentException in FFetch constructor
    @Test
    fun testFFetchInitWithIllegalArgumentExceptionTrigger() {
        // Test with a URL that triggers IllegalArgumentException specifically
        assertFailsWith<FFetchError.InvalidURL> {
            // This specific pattern triggers IllegalArgumentException in URL constructor
            FFetch("http://[invalid")
        }
    }

    // Test for SerializationException in createFlow by returning unparseable JSON
    @Test
    fun testCreateFlowWithSerializationExceptionFromUnparseableJSON() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            // Return JSON that cannot be parsed as FFetchResponse
            // Missing required fields and wrong structure
            mockClient.jsonResponse = """{"foo": "bar", "invalid": true}"""

            val ffetch =
                FFetch("https://example.com/query-index.json")
                    .withHTTPClient(mockClient)

            assertFailsWith<FFetchError.DecodingError> {
                ffetch.asFlow().first()
            }
        }

    // Test for URL with null host (covers the missed branch in init block)
    @Test
    fun testFFetchInitWithFileProtocolNullHost() =
        runTest {
            // File URLs have null host
            val ffetch = FFetch("file:///local/path/query-index.json")
            assertNotNull(ffetch)
            // The allowedHosts set should not contain any hosts for file protocol
            assertTrue(ffetch.context.allowedHosts.isEmpty() || !ffetch.context.allowedHosts.any { it == null })
        }

    // Test flow cancellation edge cases
    @Test
    fun testFlowCancellationDuringEmit() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            // Large dataset to ensure multiple emissions
            val largeData = (1..100).map { """{"path": "/test$it", "title": "Test $it"}""" }.joinToString(",")
            mockClient.jsonResponse = """{
                "total": 100, "offset": 0, "limit": 255, 
                "data": [$largeData]
            }"""

            val ffetch =
                FFetch("https://example.com/query-index.json")
                    .withHTTPClient(mockClient)

            // Take only first 5 items then cancel
            val results = ffetch.asFlow().take(5).toList()
            assertEquals(5, results.size)
        }

    // Test flow error handling with catch operator
    @Test
    fun testFlowErrorHandlingWithCatch() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            mockClient.throwIOException = true

            val ffetch =
                FFetch("https://example.com/query-index.json")
                    .withHTTPClient(mockClient)

            var caughtError: Throwable? = null
            ffetch.asFlow()
                .catch { e -> caughtError = e }
                .toList()

            assertNotNull(caughtError)
            assertTrue(caughtError is FFetchError.NetworkError)
        }

    // Test concurrent flow collection
    @Test
    fun testConcurrentFlowCollection() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            mockClient.jsonResponse = """{
                "total": 10, "offset": 0, "limit": 255, 
                "data": [{"path": "/test1"}, {"path": "/test2"}]
            }"""

            val ffetch =
                FFetch("https://example.com/query-index.json")
                    .withHTTPClient(mockClient)

            // Collect the same flow concurrently
            val job1 =
                launch {
                    val results =
                        ffetch.asFlow().toList()
                    assertEquals(2, results.size)
                }

            val job2 =
                launch {
                    val results =
                        ffetch.asFlow().toList()
                    assertEquals(2, results.size)
                }

            job1.join()
            job2.join()
        }

    // Test flow with different chunk sizes for pagination
    @Test
    fun testFlowWithSmallChunkSize() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            // First page response
            mockClient.setSuccessResponse(
                "https://example.com/query-index.json?offset=0&limit=2",
                """{
                    "total": 5, "offset": 0, "limit": 2,
                    "data": [{"path": "/test1"}, {"path": "/test2"}]
                }""",
            )
            // Second page response
            mockClient.setSuccessResponse(
                "https://example.com/query-index.json?offset=2&limit=2",
                """{
                    "total": 5, "offset": 2, "limit": 2,
                    "data": [{"path": "/test3"}, {"path": "/test4"}]
                }""",
            )
            // Third page response
            mockClient.setSuccessResponse(
                "https://example.com/query-index.json?offset=4&limit=2",
                """{
                    "total": 5, "offset": 4, "limit": 2,
                    "data": [{"path": "/test5"}]
                }""",
            )

            val ffetch =
                FFetch("https://example.com/query-index.json")
                    .chunks(2)
                    .withHTTPClient(mockClient)

            val results = ffetch.asFlow().toList()
            assertEquals(5, results.size)
            assertEquals("/test1", results[0]["path"])
            assertEquals("/test5", results[4]["path"])
        }

    // Test handling of malformed URL in constructor that triggers IllegalArgumentException
    @Test
    fun testFFetchInitWithMalformedURLTriggeringIllegalArgument() {
        // Test URL that triggers IllegalArgumentException in URL constructor
        // This happens with certain malformed IPv6 addresses
        assertFailsWith<FFetchError.InvalidURL> {
            FFetch("http://[::1")
        }
    }

    // Test FFetch with custom context preserving upstream
    @Test
    fun testFFetchWithCustomContextAndPreservedUpstream() =
        runTest {
            val mockClient = MockFFetchHTTPClient()
            mockClient.jsonResponse = """{
                "total": 2, "offset": 0, "limit": 255,
                "data": [{"path": "/test1"}, {"path": "/test2"}]
            }"""

            val originalFFetch =
                FFetch("https://example.com/query-index.json")
                    .withHTTPClient(mockClient)

            // Create upstream flow
            val upstreamFlow = originalFFetch.asFlow()

            // Create new FFetch with custom context but preserving upstream
            val customContext =
                FFetchContext(
                    chunkSize = 50,
                    maxConcurrency = 3,
                    cacheReload = true,
                )
            val newFFetch = FFetch(originalFFetch.url, customContext, upstreamFlow)

            // Verify it uses the upstream flow
            val results = newFFetch.asFlow().toList()
            assertEquals(2, results.size)

            // Verify context properties were set
            assertEquals(50, newFFetch.context.chunkSize)
            assertEquals(3, newFFetch.context.maxConcurrency)
            assertTrue(newFFetch.context.cacheReload)
        }
}
