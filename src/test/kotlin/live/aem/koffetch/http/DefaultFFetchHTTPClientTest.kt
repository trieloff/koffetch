//
// DefaultFFetchHTTPClientTest.kt
// KotlinFFetch
//
// Tests for DefaultFFetchHTTPClient implementation
//

package live.aem.koffetch.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import live.aem.koffetch.DefaultFFetchHTTPClient
import live.aem.koffetch.FFetchCacheConfig
import live.aem.koffetch.FFetchError
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultFFetchHTTPClientTest {
    @Test
    fun testHttpGetRequestExecution() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    respond(
                        content = ByteReadChannel("""{"data": "test"}"""),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))
            val (content, response) = client.fetch("https://example.com/test")

            assertEquals("""{"data": "test"}""", content)
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun testHttpGetWithDifferentUrls() =
        runTest {
            val responses =
                mapOf(
                    "https://api.example.com/data" to """{"type": "api"}""",
                    "https://cdn.example.com/content" to """{"type": "cdn"}""",
                    "https://example.com/index.json" to """{"type": "index"}""",
                )

            val mockEngine =
                MockEngine { request ->
                    val url = request.url.toString()
                    respond(
                        content = ByteReadChannel(responses[url] ?: """{"error": "not found"}"""),
                        status = if (responses.containsKey(url)) HttpStatusCode.OK else HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))

            for ((url, expectedContent) in responses) {
                val (content, response) = client.fetch(url)
                assertEquals(expectedContent, content)
                assertEquals(HttpStatusCode.OK, response.status)
            }
        }

    @Test
    fun testRequestConfigurationAndHeaders() =
        runTest {
            var capturedRequest: HttpRequestData? = null

            val mockEngine =
                MockEngine { request ->
                    capturedRequest = request
                    respond(
                        content = ByteReadChannel("OK"),
                        status = HttpStatusCode.OK,
                    )
                }

            val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))
            client.fetch("https://example.com/test")

            assertNotNull(capturedRequest)
            assertEquals(HttpMethod.Get, capturedRequest!!.method)
            assertEquals("https://example.com/test", capturedRequest!!.url.toString())
        }

    @Test
    fun testResponseParsingAndStatusCodes() =
        runTest {
            val testCases =
                listOf(
                    HttpStatusCode.OK to "Success response",
                    HttpStatusCode.Created to "Created response",
                    HttpStatusCode.Accepted to "Accepted response",
                    HttpStatusCode.NoContent to "",
                )

            for ((statusCode, content) in testCases) {
                val mockEngine =
                    MockEngine { request ->
                        respond(
                            content = ByteReadChannel(content),
                            status = statusCode,
                        )
                    }

                val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))
                val (responseContent, response) = client.fetch("https://example.com/test")

                assertEquals(content, responseContent)
                assertEquals(statusCode, response.status)
            }
        }

    @Test
    fun testTimeoutConfiguration() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    // Simulate slow response
                    kotlinx.coroutines.delay(2000)
                    respond(content = ByteReadChannel("Delayed response"))
                }

            val clientWithTimeout =
                HttpClient(mockEngine) {
                    install(HttpTimeout) {
                        requestTimeoutMillis = 1000
                    }
                }

            val client = DefaultFFetchHTTPClient(clientWithTimeout)

            assertFailsWith<FFetchError.NetworkError> {
                client.fetch("https://example.com/slow")
            }
        }

    @Test
    fun testNetworkErrorHandling() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    throw IOException("Network connection failed")
                }

            val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))

            val error =
                assertFailsWith<FFetchError.NetworkError> {
                    client.fetch("https://example.com/error")
                }

            assertTrue(error.message?.contains("Network error") == true)
            assertTrue(error.cause?.message?.contains("Network connection failed") == true)
        }

    @Test
    fun testHttpErrorStatusCodes() =
        runTest {
            val errorCases =
                listOf(
                    HttpStatusCode.BadRequest,
                    HttpStatusCode.Unauthorized,
                    HttpStatusCode.Forbidden,
                    HttpStatusCode.NotFound,
                    HttpStatusCode.InternalServerError,
                    HttpStatusCode.BadGateway,
                    HttpStatusCode.ServiceUnavailable,
                )

            for (statusCode in errorCases) {
                val mockEngine =
                    MockEngine { request ->
                        respond(
                            content = ByteReadChannel("Error response"),
                            status = statusCode,
                        )
                    }

                val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))
                val (content, response) = client.fetch("https://example.com/test")

                assertEquals("Error response", content)
                assertEquals(statusCode, response.status)
            }
        }

    @Test
    fun testCacheConfigurationPassing() =
        runTest {
            val cacheConfigs =
                listOf(
                    FFetchCacheConfig.Default,
                    FFetchCacheConfig.NoCache,
                    FFetchCacheConfig.CacheOnly,
                    FFetchCacheConfig.CacheElseLoad,
                    FFetchCacheConfig(maxAge = 3600),
                )

            val mockEngine =
                MockEngine { request ->
                    respond(content = ByteReadChannel("Test response"))
                }

            val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))

            for (cacheConfig in cacheConfigs) {
                val (content, response) = client.fetch("https://example.com/test", cacheConfig)
                assertEquals("Test response", content)
                assertEquals(HttpStatusCode.OK, response.status)
            }
        }

    @Test
    fun testLargeResponseHandling() =
        runTest {
            val largeContent = "x".repeat(10000) // 10KB response

            val mockEngine =
                MockEngine { request ->
                    respond(
                        content = ByteReadChannel(largeContent),
                        status = HttpStatusCode.OK,
                    )
                }

            val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))
            val (content, response) = client.fetch("https://example.com/large")

            assertEquals(largeContent, content)
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(10000, content.length)
        }

    @Test
    fun testConcurrentRequests() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    respond(
                        content = ByteReadChannel("Response for ${request.url.encodedPath}"),
                        status = HttpStatusCode.OK,
                    )
                }

            val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))
            val urls = (1..5).map { "https://example.com/path$it" }

            val results =
                urls.map { url ->
                    async {
                        client.fetch(url)
                    }
                }.map { it.await() }

            assertEquals(5, results.size)
            results.forEachIndexed { index, (content, response) ->
                assertTrue(content.contains("path${index + 1}"))
                assertEquals(HttpStatusCode.OK, response.status)
            }
        }

    @Test
    fun testSpecificNetworkExceptions() =
        runTest {
            // Test ClientRequestException (4xx status codes)
            val clientErrorEngine =
                MockEngine { request ->
                    respond(
                        content = ByteReadChannel("Bad Request"),
                        status = HttpStatusCode.BadRequest,
                    )
                }
            val clientErrorClient = DefaultFFetchHTTPClient(HttpClient(clientErrorEngine))
            // Note: With MockEngine, 4xx responses don't automatically throw exceptions
            // They return the response content, so this should succeed
            val (content, response) = clientErrorClient.fetch("https://example.com/client-error")
            assertEquals("Bad Request", content)
            assertEquals(HttpStatusCode.BadRequest, response.status)

            // Test ServerResponseException (5xx status codes)
            val serverErrorEngine =
                MockEngine { request ->
                    respond(
                        content = ByteReadChannel("Internal Server Error"),
                        status = HttpStatusCode.InternalServerError,
                    )
                }
            val serverErrorClient = DefaultFFetchHTTPClient(HttpClient(serverErrorEngine))
            // Note: With MockEngine, 5xx responses don't automatically throw exceptions
            // They return the response content, so this should succeed
            val (serverContent, serverResponse) = serverErrorClient.fetch("https://example.com/server-error")
            assertEquals("Internal Server Error", serverContent)
            assertEquals(HttpStatusCode.InternalServerError, serverResponse.status)
        }

    @Test
    fun testAdditionalTimeoutScenarios() =
        runTest {
            // Test with very short timeout - using actual timeout configuration
            val shortTimeoutEngine =
                MockEngine { request ->
                    delay(500) // Half second delay
                    respond(content = ByteReadChannel("Should timeout"))
                }

            val shortTimeoutClient =
                HttpClient(shortTimeoutEngine) {
                    install(HttpTimeout) {
                        requestTimeoutMillis = 100 // Very short timeout
                    }
                }

            val client = DefaultFFetchHTTPClient(shortTimeoutClient)

            assertFailsWith<FFetchError.NetworkError> {
                client.fetch("https://example.com/short-timeout")
            }

            // Test infinite timeout configuration
            val infiniteTimeoutEngine =
                MockEngine { request ->
                    respond(content = ByteReadChannel("Immediate response"))
                }

            val infiniteTimeoutClient =
                HttpClient(infiniteTimeoutEngine) {
                    install(HttpTimeout) {
                        requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS // Infinite timeout
                    }
                }

            val infiniteTimeoutHttpClient = DefaultFFetchHTTPClient(infiniteTimeoutClient)
            val (content, response) = infiniteTimeoutHttpClient.fetch("https://example.com/infinite-timeout")

            assertEquals("Immediate response", content)
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun testContentTypeHandling() =
        runTest {
            val contentTypes =
                mapOf(
                    "application/json" to """{ "key": "value" }""",
                    "text/plain" to "Plain text content",
                    "text/html" to "<html><body>HTML content</body></html>",
                    "application/xml" to "<?xml version='1.0'?><root>XML content</root>",
                    "text/csv" to "col1,col2\nval1,val2",
                    "application/octet-stream" to "Binary data content",
                )

            for ((contentType, content) in contentTypes) {
                val mockEngine =
                    MockEngine { request ->
                        respond(
                            content = ByteReadChannel(content),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, contentType),
                        )
                    }

                val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))
                val (responseContent, response) = client.fetch("https://example.com/test")

                assertEquals(content, responseContent)
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(contentType, response.headers[HttpHeaders.ContentType])
            }
        }

    @Test
    fun testCharsetHandling() =
        runTest {
            val charsets =
                listOf(
                    "UTF-8" to "Unicode content: test",
                    "ISO-8859-1" to "Latin content: test",
                    "US-ASCII" to "ASCII content",
                )

            for ((charset, content) in charsets) {
                val mockEngine =
                    MockEngine { request ->
                        respond(
                            content = ByteReadChannel(content),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "text/plain; charset=$charset"),
                        )
                    }

                val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))
                val (responseContent, response) = client.fetch("https://example.com/test")

                assertEquals(content, responseContent)
                assertEquals(HttpStatusCode.OK, response.status)
                assertContains(response.headers[HttpHeaders.ContentType] ?: "", charset)
            }
        }

    @Test
    fun testCustomHeaders() =
        runTest {
            var capturedHeaders: io.ktor.http.Headers? = null

            val mockEngine =
                MockEngine { request ->
                    capturedHeaders = request.headers
                    respond(
                        content = ByteReadChannel("OK"),
                        status = HttpStatusCode.OK,
                    )
                }

            val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))
            client.fetch("https://example.com/test")

            assertNotNull(capturedHeaders)
            // Verify standard headers are present
            assertTrue(
                capturedHeaders!!.contains(HttpHeaders.UserAgent) ||
                    capturedHeaders!!.contains(HttpHeaders.Accept),
            )
        }

    @Test
    fun testResponseHeadersAccess() =
        runTest {
            val customHeaders =
                headersOf(
                    "X-Custom-Header" to listOf("custom-value"),
                    "X-API-Version" to listOf("1.0"),
                    HttpHeaders.CacheControl to listOf("no-cache"),
                    HttpHeaders.ETag to listOf("\"abc123\""),
                )

            val mockEngine =
                MockEngine { request ->
                    respond(
                        content = ByteReadChannel("Response with headers"),
                        status = HttpStatusCode.OK,
                        headers = customHeaders,
                    )
                }

            val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))
            val (content, response) = client.fetch("https://example.com/test")

            assertEquals("Response with headers", content)
            assertEquals("custom-value", response.headers["X-Custom-Header"])
            assertEquals("1.0", response.headers["X-API-Version"])
            assertEquals("no-cache", response.headers[HttpHeaders.CacheControl])
            assertEquals("\"abc123\"", response.headers[HttpHeaders.ETag])
        }

    @Test
    fun testEmptyResponseHandling() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    respond(
                        content = ByteReadChannel(""),
                        status = HttpStatusCode.NoContent,
                    )
                }

            val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))
            val (content, response) = client.fetch("https://example.com/empty")

            assertEquals("", content)
            assertEquals(HttpStatusCode.NoContent, response.status)
        }

    @Test
    fun testCacheConfigurationVariations() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    respond(
                        content = ByteReadChannel("Cached response"),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.CacheControl, "max-age=3600"),
                    )
                }

            val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))

            // Test different cache configurations
            val cacheConfigs =
                listOf(
                    FFetchCacheConfig.Default,
                    FFetchCacheConfig.NoCache,
                    FFetchCacheConfig.CacheOnly,
                    FFetchCacheConfig.CacheElseLoad,
                    FFetchCacheConfig(maxAge = 1800),
                    FFetchCacheConfig(noCache = true, ignoreServerCacheControl = true),
                    FFetchCacheConfig(cacheElseLoad = true, maxAge = 7200),
                )

            for (cacheConfig in cacheConfigs) {
                val (content, response) = client.fetch("https://example.com/cached", cacheConfig)
                assertEquals("Cached response", content)
                assertEquals(HttpStatusCode.OK, response.status)
            }
        }

    @Test
    fun testVeryLargeResponseHandling() =
        runTest {
            val veryLargeContent = "x".repeat(1_000_000) // 1MB response

            val mockEngine =
                MockEngine { request ->
                    respond(
                        content = ByteReadChannel(veryLargeContent),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentLength, veryLargeContent.length.toString()),
                    )
                }

            val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))
            val (content, response) = client.fetch("https://example.com/large")

            assertEquals(veryLargeContent, content)
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(1_000_000, content.length)
        }

    @Test
    fun testRedirectStatusCodes() =
        runTest {
            // Test NotModified which doesn't cause redirect loops
            val notModifiedEngine =
                MockEngine { request ->
                    respond(
                        content = ByteReadChannel("Not modified"),
                        status = HttpStatusCode.NotModified,
                    )
                }

            val client = DefaultFFetchHTTPClient(HttpClient(notModifiedEngine))
            val (content, response) = client.fetch("https://example.com/not-modified")

            assertEquals("Not modified", content)
            assertEquals(HttpStatusCode.NotModified, response.status)

            // Test successful response with various status codes
            val successCodes = listOf(HttpStatusCode.OK, HttpStatusCode.Created, HttpStatusCode.Accepted)

            for (statusCode in successCodes) {
                val successEngine =
                    MockEngine { request ->
                        respond(
                            content = ByteReadChannel("Success response for $statusCode"),
                            status = statusCode,
                        )
                    }

                val successClient = DefaultFFetchHTTPClient(HttpClient(successEngine))
                val (successContent, successResponse) = successClient.fetch("https://example.com/success")

                assertEquals("Success response for $statusCode", successContent)
                assertEquals(statusCode, successResponse.status)
            }
        }

    @Test
    fun testHighConcurrencyStressTest() =
        runTest {
            val requestCount = 50
            val mockEngine =
                MockEngine { request ->
                    // Add small delay to simulate real network latency
                    delay(10)
                    respond(
                        content = ByteReadChannel("Response for ${request.url.encodedPath}"),
                        status = HttpStatusCode.OK,
                    )
                }

            val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))
            val urls = (1..requestCount).map { "https://example.com/path$it" }

            val results =
                urls.map { url ->
                    async {
                        client.fetch(url)
                    }
                }.map { it.await() }

            assertEquals(requestCount, results.size)
            results.forEachIndexed { index, (content, response) ->
                assertTrue(content.contains("path${index + 1}"))
                assertEquals(HttpStatusCode.OK, response.status)
            }
        }

    @Test
    fun testClientInitializationWithDefaults() =
        runTest {
            // Test that DefaultFFetchHTTPClient can be created with a basic HttpClient
            val basicClient = DefaultFFetchHTTPClient(HttpClient())
            assertNotNull(basicClient)

            // Test with mock engine
            val mockEngine =
                MockEngine { request ->
                    respond(
                        content = ByteReadChannel("Default client test"),
                        status = HttpStatusCode.OK,
                    )
                }

            val mockClient = DefaultFFetchHTTPClient(HttpClient(mockEngine))
            val (content, response) = mockClient.fetch("https://example.com/default")

            assertEquals("Default client test", content)
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun testIOExceptionHandling() =
        runTest {
            val ioErrorEngine =
                MockEngine { request ->
                    throw IOException("I/O operation failed")
                }

            val client = DefaultFFetchHTTPClient(HttpClient(ioErrorEngine))
            val error =
                assertFailsWith<FFetchError.NetworkError> {
                    client.fetch("https://example.com/io-error")
                }

            assertTrue(error.cause is IOException)
            assertContains(error.message ?: "", "Network error")
            assertContains(error.cause?.message ?: "", "I/O operation failed")
        }

    @Test
    fun testGenericExceptionHandling() =
        runTest {
            val genericErrorEngine =
                MockEngine { request ->
                    throw RuntimeException("Generic network error")
                }

            val client = DefaultFFetchHTTPClient(HttpClient(genericErrorEngine))

            // For non-IOException runtime exceptions, they should be thrown as-is
            assertFailsWith<RuntimeException> {
                client.fetch("https://example.com/generic-error")
            }
        }

    @Test
    fun testMultipleErrorScenarios() =
        runTest {
            val errorScenarios =
                listOf(
                    IOException("Connection reset") to "I/O",
                    RuntimeException("Unexpected error") to "Runtime",
                    IllegalStateException("Invalid state") to "IllegalState",
                )

            for ((exception, description) in errorScenarios) {
                val errorEngine =
                    MockEngine { request ->
                        throw exception
                    }

                val client = DefaultFFetchHTTPClient(HttpClient(errorEngine))

                when (exception) {
                    is IOException -> {
                        val error =
                            assertFailsWith<FFetchError.NetworkError> {
                                client.fetch("https://example.com/$description")
                            }
                        assertTrue(error.cause is IOException)
                    }
                    else -> {
                        // For non-IOException errors, they should be thrown as-is or handled differently
                        assertFailsWith<RuntimeException> {
                            client.fetch("https://example.com/$description")
                        }
                    }
                }
            }
        }

    @Test
    fun testResponseContentParsing() =
        runTest {
            val jsonContent = """{
                "name": "Test",
                "value": 123,
                "nested": {
                    "field": "data"
                }
            }"""

            val mockEngine =
                MockEngine { request ->
                    respond(
                        content = ByteReadChannel(jsonContent),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))
            val (content, response) = client.fetch("https://example.com/json")

            assertEquals(jsonContent, content)
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(content.contains("Test"))
            assertTrue(content.contains("123"))
            assertTrue(content.contains("nested"))
        }

    @Test
    fun testAllCacheConfigProperties() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    respond(
                        content = ByteReadChannel("Cache test response"),
                        status = HttpStatusCode.OK,
                    )
                }

            val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))

            // Test all cache config property combinations
            val cacheConfigs =
                listOf(
                    FFetchCacheConfig(noCache = true),
                    FFetchCacheConfig(cacheOnly = true),
                    FFetchCacheConfig(cacheElseLoad = true),
                    FFetchCacheConfig(maxAge = 3600L),
                    FFetchCacheConfig(ignoreServerCacheControl = true),
                    FFetchCacheConfig(
                        noCache = false,
                        cacheOnly = false,
                        cacheElseLoad = true,
                        maxAge = 1800L,
                        ignoreServerCacheControl = false,
                    ),
                )

            for (config in cacheConfigs) {
                val (content, response) = client.fetch("https://example.com/cache-test", config)
                assertEquals("Cache test response", content)
                assertEquals(HttpStatusCode.OK, response.status)
            }
        }

    @Test
    fun testUncoveredExceptionPaths() =
        runTest {
            // Test ConnectTimeoutException
            val connectTimeoutEngine =
                MockEngine { request ->
                    throw ConnectTimeoutException("Connection timeout", null)
                }

            val connectTimeoutClient = DefaultFFetchHTTPClient(HttpClient(connectTimeoutEngine))
            val connectTimeoutError =
                assertFailsWith<FFetchError.NetworkError> {
                    connectTimeoutClient.fetch("https://example.com/connect-timeout")
                }
            assertTrue(connectTimeoutError.cause is ConnectTimeoutException)

            // Test SocketTimeoutException
            val socketTimeoutEngine =
                MockEngine { request ->
                    throw SocketTimeoutException("Socket timeout", null)
                }

            val socketTimeoutClient = DefaultFFetchHTTPClient(HttpClient(socketTimeoutEngine))
            val socketTimeoutError =
                assertFailsWith<FFetchError.NetworkError> {
                    socketTimeoutClient.fetch("https://example.com/socket-timeout")
                }
            assertTrue(socketTimeoutError.cause is SocketTimeoutException)
        }

    @Test
    fun testCacheConfigDefault() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    respond(
                        content = ByteReadChannel("Default cache test"),
                        status = HttpStatusCode.OK,
                    )
                }

            val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))
            // Test with default cache config (should use the default parameter)
            val (content, response) = client.fetch("https://example.com/default-cache")

            assertEquals("Default cache test", content)
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun testHttpStatusExceptionHandling() =
        runTest {
            // Test 4xx Client Error
            val clientErrorEngine =
                MockEngine { request ->
                    respond(
                        content = ByteReadChannel("Bad Request"),
                        status = HttpStatusCode.BadRequest,
                    )
                }
            val clientErrorClient = DefaultFFetchHTTPClient(HttpClient(clientErrorEngine))
            // Note: The default client doesn't automatically throw on 4xx,
            // so let's just test it returns the error content
            val (content, response) = clientErrorClient.fetch("https://example.com/client-error")
            assertEquals("Bad Request", content)
            assertEquals(HttpStatusCode.BadRequest, response.status)

            // Test 5xx Server Error
            val serverErrorEngine =
                MockEngine { request ->
                    respond(
                        content = ByteReadChannel("Internal Server Error"),
                        status = HttpStatusCode.InternalServerError,
                    )
                }
            val serverErrorClient = DefaultFFetchHTTPClient(HttpClient(serverErrorEngine))
            val (serverContent, serverResponse) = serverErrorClient.fetch("https://example.com/server-error")
            assertEquals("Internal Server Error", serverContent)
            assertEquals(HttpStatusCode.InternalServerError, serverResponse.status)

            // Test HttpRequestTimeoutException
            val requestTimeoutEngine =
                MockEngine { request ->
                    throw HttpRequestTimeoutException("http://example.com", 5000)
                }
            val requestTimeoutClient = DefaultFFetchHTTPClient(HttpClient(requestTimeoutEngine))
            val requestTimeoutError =
                assertFailsWith<FFetchError.NetworkError> {
                    requestTimeoutClient.fetch("https://example.com/request-timeout")
                }
            assertTrue(requestTimeoutError.cause is HttpRequestTimeoutException)
        }

    @Test
    fun testClientRequestException() =
        runTest {
            // Create a client with expectSuccess = true to trigger ClientRequestException
            val client =
                DefaultFFetchHTTPClient(
                    HttpClient(
                        MockEngine { request ->
                            respond(
                                content = ByteReadChannel("Client error"),
                                status = HttpStatusCode.BadRequest,
                                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                            )
                        },
                    ) {
                        expectSuccess = true
                    },
                )

            val error =
                assertFailsWith<FFetchError.NetworkError> {
                    client.fetch("https://example.com/client-error")
                }

            assertTrue(error.cause is ClientRequestException)
            assertContains(error.message ?: "", "Network error")
        }

    @Test
    fun testServerResponseException() =
        runTest {
            // Create a client with expectSuccess = true to trigger ServerResponseException
            val client =
                DefaultFFetchHTTPClient(
                    HttpClient(
                        MockEngine { request ->
                            respond(
                                content = ByteReadChannel("Server error"),
                                status = HttpStatusCode.InternalServerError,
                                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                            )
                        },
                    ) {
                        expectSuccess = true
                    },
                )

            val error =
                assertFailsWith<FFetchError.NetworkError> {
                    client.fetch("https://example.com/server-error")
                }

            assertTrue(error.cause is ServerResponseException)
            assertContains(error.message ?: "", "Network error")
        }

    @Test
    fun testRedirectResponseException() =
        runTest {
            // Create a client with followRedirects = false to trigger RedirectResponseException
            val client =
                DefaultFFetchHTTPClient(
                    HttpClient(
                        MockEngine { request ->
                            respond(
                                content = ByteReadChannel("Redirect"),
                                status = HttpStatusCode.MovedPermanently,
                                headers =
                                    headersOf(
                                        HttpHeaders.Location to listOf("https://example.com/new-location"),
                                        HttpHeaders.ContentType to listOf("text/plain"),
                                    ),
                            )
                        },
                    ) {
                        followRedirects = false
                        expectSuccess = true
                    },
                )

            val error =
                assertFailsWith<FFetchError.NetworkError> {
                    client.fetch("https://example.com/redirect")
                }

            assertTrue(error.cause is RedirectResponseException)
            assertContains(error.message ?: "", "Network error")
        }
}
