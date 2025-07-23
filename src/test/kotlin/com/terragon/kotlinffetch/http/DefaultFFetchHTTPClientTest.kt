//
// DefaultFFetchHTTPClientTest.kt
// KotlinFFetch
//
// Tests for DefaultFFetchHTTPClient implementation
//

package com.terragon.kotlinffetch.http

import com.terragon.kotlinffetch.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class DefaultFFetchHTTPClientTest {
    
    @Test
    fun testHttpGetRequestExecution() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""{"data": "test"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))
        val (content, response) = client.fetch("https://example.com/test")
        
        assertEquals("""{"data": "test"}""", content)
        assertEquals(HttpStatusCode.OK, response.status)
    }
    
    @Test
    fun testHttpGetWithDifferentUrls() = runTest {
        val responses = mapOf(
            "https://api.example.com/data" to """{"type": "api"}""",
            "https://cdn.example.com/content" to """{"type": "cdn"}""",
            "https://example.com/index.json" to """{"type": "index"}"""
        )
        
        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            respond(
                content = ByteReadChannel(responses[url] ?: """{"error": "not found"}"""),
                status = if (responses.containsKey(url)) HttpStatusCode.OK else HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
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
    fun testRequestConfigurationAndHeaders() = runTest {
        var capturedRequest: HttpRequestData? = null
        
        val mockEngine = MockEngine { request ->
            capturedRequest = request
            respond(
                content = ByteReadChannel("OK"),
                status = HttpStatusCode.OK
            )
        }
        
        val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))
        client.fetch("https://example.com/test")
        
        assertNotNull(capturedRequest)
        assertEquals(HttpMethod.Get, capturedRequest!!.method)
        assertEquals("https://example.com/test", capturedRequest!!.url.toString())
    }
    
    @Test
    fun testResponseParsingAndStatusCodes() = runTest {
        val testCases = listOf(
            HttpStatusCode.OK to "Success response",
            HttpStatusCode.Created to "Created response",
            HttpStatusCode.Accepted to "Accepted response",
            HttpStatusCode.NoContent to ""
        )
        
        for ((statusCode, content) in testCases) {
            val mockEngine = MockEngine { request ->
                respond(
                    content = ByteReadChannel(content),
                    status = statusCode
                )
            }
            
            val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))
            val (responseContent, response) = client.fetch("https://example.com/test")
            
            assertEquals(content, responseContent)
            assertEquals(statusCode, response.status)
        }
    }
    
    @Test
    fun testTimeoutConfiguration() = runTest {
        val mockEngine = MockEngine { request ->
            // Simulate slow response
            kotlinx.coroutines.delay(2000)
            respond(content = ByteReadChannel("Delayed response"))
        }
        
        val clientWithTimeout = HttpClient(mockEngine) {
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
    fun testNetworkErrorHandling() = runTest {
        val mockEngine = MockEngine { request ->
            throw Exception("Network connection failed")
        }
        
        val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))
        
        val error = assertFailsWith<FFetchError.NetworkError> {
            client.fetch("https://example.com/error")
        }
        
        assertTrue(error.message?.contains("Network error") == true)
        assertTrue(error.cause?.message?.contains("Network connection failed") == true)
    }
    
    @Test
    fun testHttpErrorStatusCodes() = runTest {
        val errorCases = listOf(
            HttpStatusCode.BadRequest,
            HttpStatusCode.Unauthorized,
            HttpStatusCode.Forbidden,
            HttpStatusCode.NotFound,
            HttpStatusCode.InternalServerError,
            HttpStatusCode.BadGateway,
            HttpStatusCode.ServiceUnavailable
        )
        
        for (statusCode in errorCases) {
            val mockEngine = MockEngine { request ->
                respond(
                    content = ByteReadChannel("Error response"),
                    status = statusCode
                )
            }
            
            val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))
            val (content, response) = client.fetch("https://example.com/test")
            
            assertEquals("Error response", content)
            assertEquals(statusCode, response.status)
        }
    }
    
    @Test
    fun testCacheConfigurationPassing() = runTest {
        val cacheConfigs = listOf(
            FFetchCacheConfig.Default,
            FFetchCacheConfig.NoCache,
            FFetchCacheConfig.CacheOnly,
            FFetchCacheConfig.CacheElseLoad,
            FFetchCacheConfig(maxAge = 3600)
        )
        
        val mockEngine = MockEngine { request ->
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
    fun testLargeResponseHandling() = runTest {
        val largeContent = "x".repeat(10000) // 10KB response
        
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel(largeContent),
                status = HttpStatusCode.OK
            )
        }
        
        val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))
        val (content, response) = client.fetch("https://example.com/large")
        
        assertEquals(largeContent, content)
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(10000, content.length)
    }
    
    @Test
    fun testConcurrentRequests() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("Response for ${request.url.encodedPath}"),
                status = HttpStatusCode.OK
            )
        }
        
        val client = DefaultFFetchHTTPClient(HttpClient(mockEngine))
        val urls = (1..5).map { "https://example.com/path$it" }
        
        val results = urls.map { url ->
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
}