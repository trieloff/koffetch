//
// MockFFetchHTTPClient.kt
// KotlinFFetch Test Support
//
// Mock HTTP client implementation for testing
//

package live.aem.koffetch.mock

import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import live.aem.koffetch.FFetchCacheConfig
import live.aem.koffetch.FFetchError
import live.aem.koffetch.FFetchHTTPClient

/**
 * Mock HTTP client for testing FFetch network operations
 * Supports configurable responses, delays, and error simulation
 */
class MockFFetchHTTPClient : FFetchHTTPClient {
    // Configuration for responses
    private val responses = mutableMapOf<String, MockResponse>()
    private val defaultResponse = MockResponse.success("{\"total\":0,\"offset\":0,\"limit\":255,\"data\":[]}")

    // Request tracking
    private val _requestLog = mutableListOf<MockRequest>()
    val requestLog: List<MockRequest> get() = _requestLog.toList()

    // Global configuration
    var simulateNetworkDelay: Long = 0
    var shouldThrowNetworkError: Boolean = false
    var networkErrorMessage: String = "Mock network error"

    // New properties for testing exception handling
    var throwIOException: Boolean = false
    var throwSerializationException: Boolean = false
    var jsonResponse: String = "{\"total\":0,\"offset\":0,\"limit\":255,\"data\":[]}"

    override suspend fun fetch(
        url: String,
        cacheConfig: FFetchCacheConfig,
    ): Pair<String, HttpResponse> {
        // Track the request
        _requestLog.add(MockRequest(url, cacheConfig))

        // Simulate network delay if configured
        if (simulateNetworkDelay > 0) {
            delay(simulateNetworkDelay)
        }

        // Simulate network error if configured
        if (shouldThrowNetworkError) {
            throw FFetchError.NetworkError(RuntimeException(networkErrorMessage))
        }

        // Simulate IOException if configured
        if (throwIOException) {
            throw java.io.IOException("Mock IO Exception")
        }

        // Simulate SerializationException if configured
        if (throwSerializationException) {
            throw kotlinx.serialization.SerializationException("Mock Serialization Exception")
        }

        // Find matching response
        val mockResponse = findResponseForURL(url)

        // Simulate HTTP errors
        if (mockResponse.httpStatus != HttpStatusCode.OK) {
            val mockHttpResponse = createMockHttpResponse(mockResponse.httpStatus)
            return Pair(mockResponse.body, mockHttpResponse)
        }

        // Return successful response
        val mockHttpResponse = createMockHttpResponse(HttpStatusCode.OK)
        val responseBody =
            if (jsonResponse != "{\"total\":0,\"offset\":0,\"limit\":255,\"data\":[]}") {
                jsonResponse
            } else {
                mockResponse.body
            }
        return Pair(responseBody, mockHttpResponse)
    }

    // Configuration methods

    /**
     * Set a response for a specific URL
     */
    fun setResponse(
        url: String,
        response: MockResponse,
    ) {
        responses[url] = response
    }

    /**
     * Set a response for URLs matching a pattern
     */
    fun setResponseForPattern(
        urlPattern: String,
        response: MockResponse,
    ) {
        responses[urlPattern] = response
    }

    /**
     * Set a successful JSON response
     */
    fun setSuccessResponse(
        url: String,
        jsonBody: String,
    ) {
        responses[url] = MockResponse.success(jsonBody)
    }

    /**
     * Set an error response with specific HTTP status
     */
    fun setErrorResponse(
        url: String,
        status: HttpStatusCode,
        body: String = "",
    ) {
        responses[url] = MockResponse.error(status, body)
    }

    /**
     * Clear all configured responses
     */
    fun clearResponses() {
        responses.clear()
        _requestLog.clear()
    }

    /**
     * Reset to default state
     */
    fun reset() {
        clearResponses()
        simulateNetworkDelay = 0
        shouldThrowNetworkError = false
        networkErrorMessage = "Mock network error"
        throwIOException = false
        throwSerializationException = false
        jsonResponse = "{\"total\":0,\"offset\":0,\"limit\":255,\"data\":[]}"
    }

    // Helper methods for building common AEM responses

    /**
     * Create a paginated AEM response
     */
    fun createAEMResponse(
        total: Int,
        offset: Int,
        limit: Int,
        data: List<Map<String, Any>>,
    ): String {
        val dataJson =
            data.joinToString(",") { entry ->
                val fields =
                    entry.entries.joinToString(",") { (key, value) ->
                        when (value) {
                            is String -> "\"$key\":\"$value\""
                            is Number -> "\"$key\":$value"
                            is Boolean -> "\"$key\":$value"
                            null -> "\"$key\":null"
                            else -> "\"$key\":\"$value\""
                        }
                    }
                "{$fields}"
            }

        return """{"total":$total,"offset":$offset,"limit":$limit,"data":[$dataJson]}"""
    }

    /**
     * Set up a multi-page response scenario
     */
    fun setupPaginatedResponses(
        baseUrl: String,
        totalItems: Int,
        itemsPerPage: Int = 255,
    ) {
        val allData =
            (1..totalItems).map { i ->
                mapOf(
                    "path" to "/content/item-$i",
                    "title" to "Item $i",
                    "lastModified" to System.currentTimeMillis() - (i * 1000),
                )
            }

        var offset = 0
        while (offset < totalItems) {
            val pageData = allData.drop(offset).take(itemsPerPage)
            val pageUrl =
                if (baseUrl.contains("?")) {
                    "$baseUrl&offset=$offset&limit=$itemsPerPage"
                } else {
                    "$baseUrl?offset=$offset&limit=$itemsPerPage"
                }

            val responseJson = createAEMResponse(totalItems, offset, itemsPerPage, pageData)
            setSuccessResponse(pageUrl, responseJson)

            offset += itemsPerPage
        }
    }

    // Private helper methods

    private fun findResponseForURL(url: String): MockResponse {
        // First try exact match
        responses[url]?.let { return it }

        // Then try pattern matching
        val patternMatch =
            responses.entries.find { (pattern, _) ->
                url.contains(pattern) || url.matches(pattern.toRegex())
            }

        return patternMatch?.value ?: defaultResponse
    }

    @OptIn(io.ktor.util.InternalAPI::class)
    private fun createMockHttpResponse(status: HttpStatusCode): HttpResponse {
        // This is a simplified mock - in a real scenario you might want to use a more sophisticated mock
        return object : HttpResponse() {
            override val call: io.ktor.client.call.HttpClientCall
                get() = throw UnsupportedOperationException("Mock response")
            override val content: io.ktor.utils.io.ByteReadChannel
                get() = throw UnsupportedOperationException("Mock response")
            override val headers: Headers
                get() = HeadersBuilder().build()
            override val requestTime: io.ktor.util.date.GMTDate
                get() = io.ktor.util.date.GMTDate()
            override val responseTime: io.ktor.util.date.GMTDate
                get() = io.ktor.util.date.GMTDate()
            override val status: HttpStatusCode
                get() = status
            override val version: HttpProtocolVersion
                get() = HttpProtocolVersion.HTTP_1_1
            override val coroutineContext: kotlin.coroutines.CoroutineContext
                get() = kotlinx.coroutines.Dispatchers.Unconfined
        }
    }
}

/**
 * Represents a mock HTTP response
 */
data class MockResponse(
    val httpStatus: HttpStatusCode,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
) {
    companion object {
        fun success(body: String) = MockResponse(HttpStatusCode.OK, body)

        fun error(
            status: HttpStatusCode,
            body: String = "",
        ) = MockResponse(status, body)

        // Common error responses
        fun notFound(body: String = "Not Found") = MockResponse(HttpStatusCode.NotFound, body)

        fun serverError(body: String = "Internal Server Error") = MockResponse(HttpStatusCode.InternalServerError, body)

        fun badRequest(body: String = "Bad Request") = MockResponse(HttpStatusCode.BadRequest, body)
    }
}

/**
 * Represents a recorded HTTP request
 */
data class MockRequest(
    val url: String,
    val cacheConfig: FFetchCacheConfig,
    val timestamp: Long = System.currentTimeMillis(),
)
