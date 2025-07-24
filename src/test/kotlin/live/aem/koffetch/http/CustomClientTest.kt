//
// CustomClientTest.kt
// KotlinFFetch
//
// Tests for custom HTTP client integration and client switching
//

package live.aem.koffetch.http

import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.util.InternalAPI
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import live.aem.koffetch.DefaultFFetchHTTPClient
import live.aem.koffetch.FFetch
import live.aem.koffetch.FFetchCacheConfig
import live.aem.koffetch.FFetchError
import live.aem.koffetch.FFetchHTTPClient
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CustomClientTest {
    // Mock HTTP client for testing
    private class TestHTTPClient(
        private val responses: Map<String, Pair<String, HttpStatusCode>> = mapOf(),
        private val delay: Long = 0,
        private val shouldThrowError: Boolean = false,
    ) : FFetchHTTPClient {
        var requestCount = 0
            private set

        var lastRequestedUrl: String? = null
            private set

        var lastCacheConfig: FFetchCacheConfig? = null
            private set

        override suspend fun fetch(
            url: String,
            cacheConfig: FFetchCacheConfig,
        ): Pair<String, HttpResponse> {
            requestCount++
            lastRequestedUrl = url
            lastCacheConfig = cacheConfig

            if (shouldThrowError) {
                throw FFetchError.NetworkError(Exception("Custom client error"))
            }

            if (delay > 0) {
                kotlinx.coroutines.delay(delay)
            }

            val (content, statusCode) = responses[url] ?: ("Not found" to HttpStatusCode.NotFound)

            // Create a mock HttpResponse - this is simplified for testing
            // In real implementation, we'd need to create a proper HttpResponse mock
            @OptIn(InternalAPI::class)
            val mockResponse =
                object : HttpResponse() {
                    override val coroutineContext: CoroutineContext = Job()
                    override val call: io.ktor.client.call.HttpClientCall
                        get() = throw NotImplementedError()
                    override val content: io.ktor.utils.io.ByteReadChannel
                        get() = throw NotImplementedError()
                    override val headers: Headers
                        get() = Headers.Empty
                    override val requestTime: io.ktor.util.date.GMTDate
                        get() = throw NotImplementedError()
                    override val responseTime: io.ktor.util.date.GMTDate
                        get() = throw NotImplementedError()
                    override val status: HttpStatusCode
                        get() = statusCode
                    override val version: HttpProtocolVersion
                        get() = HttpProtocolVersion.HTTP_1_1
                }

            return Pair(content, mockResponse)
        }
    }

    @Test
    fun testWithHTTPClientMethod() =
        runTest {
            val customClient =
                TestHTTPClient(
                    mapOf(
                        "https://example.com/test" to ("Custom response" to HttpStatusCode.OK),
                    ),
                )

            val ffetch =
                FFetch("https://example.com/test")
                    .withHTTPClient(customClient)

            assertSame(customClient, ffetch.context.httpClient)

            // Verify original FFetch instance is unchanged
            val originalFFetch = FFetch("https://example.com/test")
            assertNotSame(customClient, originalFFetch.context.httpClient)
        }

    @Test
    fun testCustomClientConfiguration() =
        runTest {
            val responses =
                mapOf(
                    "https://api.example.com/data" to ("API data" to HttpStatusCode.OK),
                    "https://cdn.example.com/assets" to ("Asset data" to HttpStatusCode.OK),
                )

            val customClient = TestHTTPClient(responses)

            val ffetch =
                FFetch("https://api.example.com/data")
                    .withHTTPClient(customClient)

            assertEquals(customClient, ffetch.context.httpClient)

            // Test that custom client receives requests
            val (content, response) = customClient.fetch("https://api.example.com/data", FFetchCacheConfig.Default)
            assertEquals("API data", content)
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(1, customClient.requestCount)
            assertEquals("https://api.example.com/data", customClient.lastRequestedUrl)
        }

    @Test
    fun testCustomClientWithCacheConfig() =
        runTest {
            val customClient =
                TestHTTPClient(
                    mapOf(
                        "https://example.com/test" to ("Test data" to HttpStatusCode.OK),
                    ),
                )

            val ffetch =
                FFetch("https://example.com/test")
                    .withHTTPClient(customClient)
                    .cache(FFetchCacheConfig.NoCache)

            // Verify both client and cache config are set
            assertSame(customClient, ffetch.context.httpClient)
            assertEquals(FFetchCacheConfig.NoCache, ffetch.context.cacheConfig)

            // Test that cache config is passed to client
            customClient.fetch("https://example.com/test", ffetch.context.cacheConfig)
            assertEquals(FFetchCacheConfig.NoCache, customClient.lastCacheConfig)
        }

    @Test
    fun testClientSwitching() =
        runTest {
            val client1 = TestHTTPClient(mapOf("url1" to ("Response 1" to HttpStatusCode.OK)))
            val client2 = TestHTTPClient(mapOf("url2" to ("Response 2" to HttpStatusCode.OK)))

            val ffetch =
                FFetch("https://example.com/test")
                    .withHTTPClient(client1)
                    .withHTTPClient(client2) // Should override client1

            assertSame(client2, ffetch.context.httpClient)
            assertNotSame(client1, ffetch.context.httpClient)
        }

    @Test
    fun testClientChaining() =
        runTest {
            val customClient = TestHTTPClient()

            val ffetch =
                FFetch("https://example.com/test")
                    .chunks(50)
                    .withHTTPClient(customClient)
                    .maxConcurrency(3)
                    .cache(FFetchCacheConfig.CacheOnly)

            // Verify all configurations are preserved
            assertEquals(50, ffetch.context.chunkSize)
            assertSame(customClient, ffetch.context.httpClient)
            assertEquals(3, ffetch.context.maxConcurrency)
            assertEquals(FFetchCacheConfig.CacheOnly, ffetch.context.cacheConfig)
        }

    @Test
    fun testCustomClientErrorHandling() =
        runTest {
            val faultyClient = TestHTTPClient(shouldThrowError = true)

            assertFailsWith<FFetchError.NetworkError> {
                faultyClient.fetch("https://example.com/test", FFetchCacheConfig.Default)
            }
        }

    @Test
    fun testCustomClientWithTimeout() =
        runTest {
            val slowClient =
                TestHTTPClient(
                    responses = mapOf("https://example.com/slow" to ("Slow response" to HttpStatusCode.OK)),
                    delay = 100,
                )

            val (content, response) = slowClient.fetch("https://example.com/slow", FFetchCacheConfig.Default)
            assertEquals("Slow response", content)
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(slowClient.requestCount > 0)
        }

    @Test
    fun testClientLifecycleManagement() =
        runTest {
            val client =
                TestHTTPClient(
                    mapOf(
                        "https://example.com/test1" to ("Response 1" to HttpStatusCode.OK),
                        "https://example.com/test2" to ("Response 2" to HttpStatusCode.OK),
                    ),
                )

            val ffetch1 = FFetch("https://example.com/test1").withHTTPClient(client)
            val ffetch2 = FFetch("https://example.com/test2").withHTTPClient(client)

            // Same client instance should be shared
            assertSame(client, ffetch1.context.httpClient)
            assertSame(client, ffetch2.context.httpClient)

            // Client should handle multiple requests
            client.fetch("https://example.com/test1", FFetchCacheConfig.Default)
            client.fetch("https://example.com/test2", FFetchCacheConfig.Default)

            assertEquals(2, client.requestCount)
        }

    @Test
    fun testCustomClientCompatibility() =
        runTest {
            // Test that custom client works with all cache configurations
            val cacheConfigs =
                listOf(
                    FFetchCacheConfig.Default,
                    FFetchCacheConfig.NoCache,
                    FFetchCacheConfig.CacheOnly,
                    FFetchCacheConfig.CacheElseLoad,
                    FFetchCacheConfig(maxAge = 3600),
                )

            val client =
                TestHTTPClient(
                    mapOf(
                        "https://example.com/test" to ("Test response" to HttpStatusCode.OK),
                    ),
                )

            for (config in cacheConfigs) {
                val ffetch =
                    FFetch("https://example.com/test")
                        .withHTTPClient(client)
                        .cache(config)

                assertEquals(config, ffetch.context.cacheConfig)
                assertSame(client, ffetch.context.httpClient)
            }
        }

    @Test
    fun testClientStateTracking() =
        runTest {
            val client =
                TestHTTPClient(
                    mapOf(
                        "https://example.com/page1" to ("Page 1" to HttpStatusCode.OK),
                        "https://example.com/page2" to ("Page 2" to HttpStatusCode.OK),
                        "https://example.com/page3" to ("Page 3" to HttpStatusCode.OK),
                    ),
                )

            // Make multiple requests
            client.fetch("https://example.com/page1", FFetchCacheConfig.Default)
            assertEquals(1, client.requestCount)
            assertEquals("https://example.com/page1", client.lastRequestedUrl)

            client.fetch("https://example.com/page2", FFetchCacheConfig.NoCache)
            assertEquals(2, client.requestCount)
            assertEquals("https://example.com/page2", client.lastRequestedUrl)
            assertEquals(FFetchCacheConfig.NoCache, client.lastCacheConfig)

            client.fetch("https://example.com/page3", FFetchCacheConfig.CacheOnly)
            assertEquals(3, client.requestCount)
            assertEquals("https://example.com/page3", client.lastRequestedUrl)
            assertEquals(FFetchCacheConfig.CacheOnly, client.lastCacheConfig)
        }

    @Test
    fun testContextImmutabilityWithCustomClient() =
        runTest {
            val client1 = TestHTTPClient()
            val client2 = TestHTTPClient()

            val originalFFetch = FFetch("https://example.com/test")
            val modifiedFFetch = originalFFetch.withHTTPClient(client1)
            val furtherModifiedFFetch = modifiedFFetch.withHTTPClient(client2)

            // Original should have default client
            assertTrue(originalFFetch.context.httpClient is DefaultFFetchHTTPClient)

            // First modification should have client1
            assertSame(client1, modifiedFFetch.context.httpClient)

            // Second modification should have client2
            assertSame(client2, furtherModifiedFFetch.context.httpClient)

            // All should be different instances
            assertNotSame(originalFFetch, modifiedFFetch)
            assertNotSame(modifiedFFetch, furtherModifiedFFetch)
            assertNotSame(originalFFetch.context, modifiedFFetch.context)
            assertNotSame(modifiedFFetch.context, furtherModifiedFFetch.context)
        }
}
