//
// Copyright © 2025 Terragon Labs. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package live.aem.koffetch

import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException

// / Represents a single entry from an AEM index response
typealias FFetchEntry = Map<String, Any?>

/**
 * Represents the JSON response structure from AEM indices.
 *
 * This data class models the standard response format returned by AEM Edge Delivery
 * Services indices, including pagination information and the actual data entries.
 *
 * @param total Total number of entries available in the index
 * @param offset Current offset in the result set (0-based)
 * @param limit Maximum number of entries requested in this response
 * @param data Array of JSON objects representing the actual data entries
 */
@Serializable
data class FFetchResponse(
    // / Total number of entries available
    val total: Int,
    // / Current offset in the result set
    val offset: Int,
    // / Maximum number of entries requested
    val limit: Int,
    // / Array of data entries
    val data: List<JsonObject>,
) {
    /**
     * Converts the JSON response data to a list of FFetchEntry objects.
     *
     * This method transforms the raw JsonObject entries into a more convenient
     * Map-based representation, handling proper string unquoting and type conversion.
     *
     * @return A list of FFetchEntry objects (Map<String, Any?>) representing the data
     */
    fun toFFetchEntries(): List<FFetchEntry> {
        return data.map { jsonObject ->
            jsonObject.entries.associate { (key, value) ->
                key to
                    when (value) {
                        is JsonPrimitive -> {
                            if (value.isString) {
                                // For string primitives, use the content and remove surrounding quotes if they exist
                                // This handles cases like "\"Quoted Title\"" where the content still has quotes
                                value.content.removeSurrounding("\"")
                            } else {
                                // For non-string primitives, convert to string
                                value.toString()
                            }
                        }
                        else -> {
                            // For non-primitives (arrays, objects), use toString and remove quotes
                            value.toString().removeSurrounding("\"")
                        }
                    }
            }
        }
    }
}

/**
 * Sealed class hierarchy representing errors that can occur during FFetch operations.
 *
 * This provides a type-safe way to handle different categories of errors that may
 * occur when fetching data from AEM indices or processing the results.
 */
sealed class FFetchError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /**
     * Error thrown when a URL string is invalid or malformed.
     *
     * @param url The invalid URL string that caused the error
     */
    class InvalidURL(url: String) : FFetchError("Invalid URL: $url")

    /**
     * Error thrown when a network operation fails.
     *
     * This wraps underlying network exceptions from HTTP requests.
     *
     * @param cause The underlying network exception
     */
    class NetworkError(cause: Throwable) : FFetchError("Network error: ${cause.message}", cause)

    /**
     * Error thrown when JSON decoding or HTML parsing fails.
     *
     * This wraps underlying parsing exceptions when processing server responses.
     *
     * @param cause The underlying decoding/parsing exception
     */
    class DecodingError(cause: Throwable) : FFetchError("Decoding error: ${cause.message}", cause)

    /**
     * Error thrown when the server response format is invalid or unexpected.
     *
     * This occurs when the response doesn't match the expected AEM index format.
     */
    object InvalidResponse : FFetchError("Invalid response format")

    /**
     * Error thrown when a document reference cannot be found or accessed.
     *
     * This occurs during document following operations when the referenced document is not available.
     */
    object DocumentNotFound : FFetchError("Document not found")

    /**
     * Generic error for operations that fail for reasons not covered by other error types.
     *
     * @param message Descriptive message explaining the failure
     */
    class OperationFailed(message: String) : FFetchError("Operation failed: $message")
}

/**
 * Configuration for HTTP caching behavior in FFetch requests.
 *
 * This data class provides fine-grained control over how the HTTP client handles
 * caching of responses, including options to bypass cache, use cache only, or
 * customize cache behavior based on response age and server headers.
 *
 * @param noCache Whether to ignore cache and always fetch from server
 * @param cacheOnly Whether to only use cache and never fetch from server
 * @param cacheElseLoad Whether to use cache if available, otherwise fetch from server
 * @param maxAge Maximum age in seconds for cached responses to be considered valid
 * @param ignoreServerCacheControl Whether to ignore HTTP cache control headers from server
 */
data class FFetchCacheConfig(
    // / Whether to ignore cache and always fetch from server
    val noCache: Boolean = false,
    // / Whether to only use cache and never fetch from server
    val cacheOnly: Boolean = false,
    // / Whether to use cache if available, otherwise fetch from server
    val cacheElseLoad: Boolean = false,
    // / Maximum age in seconds for cached responses
    val maxAge: Long? = null,
    // / Whether to ignore server cache control headers
    val ignoreServerCacheControl: Boolean = false,
) {
    companion object {
        /**
         * Default cache configuration that respects HTTP cache control headers.
         *
         * This configuration follows standard HTTP caching behavior, respecting
         * server-provided cache control headers and using cached responses when appropriate.
         */
        val Default = FFetchCacheConfig()

        /**
         * Cache configuration that ignores cache and always fetches from server.
         *
         * This configuration bypasses all caching and forces fresh requests to the server,
         * useful when you need the most up-to-date data.
         */
        val NoCache = FFetchCacheConfig(noCache = true)

        /**
         * Cache configuration that only uses cache and never fetches from server.
         *
         * This configuration will only return cached responses and will fail if no
         * cached response is available, useful for offline scenarios.
         */
        val CacheOnly = FFetchCacheConfig(cacheOnly = true)

        /**
         * Cache configuration that uses cache if available, otherwise fetches from server.
         *
         * This configuration prefers cached responses but falls back to server requests
         * when no cached response is available, providing a good balance of performance and freshness.
         */
        val CacheElseLoad = FFetchCacheConfig(cacheElseLoad = true)
    }
}

/**
 * Interface for HTTP client abstraction.
 *
 * This interface allows for customization of HTTP request handling, enabling
 * different implementations for authentication, custom headers, or specialized
 * HTTP behavior while maintaining compatibility with the FFetch API.
 */
interface FFetchHTTPClient {
    /**
     * Performs an HTTP GET request to fetch content from the specified URL.
     *
     * @param url The URL to fetch content from
     * @param cacheConfig Cache configuration to control caching behavior
     * @return A Pair containing the response body as a String and the HttpResponse object
     * @throws FFetchError.NetworkError if the network request fails
     */
    suspend fun fetch(
        url: String,
        cacheConfig: FFetchCacheConfig = FFetchCacheConfig.Default,
    ): Pair<String, HttpResponse>
}

/**
 * Interface for HTML parsing abstraction.
 *
 * This interface allows for customization of HTML parsing behavior, enabling
 * different parsing implementations while maintaining compatibility with the
 * document following features of FFetch.
 */
interface FFetchHTMLParser {
    /**
     * Parses an HTML string into a Document object.
     *
     * @param html The HTML content to parse
     * @return A Document object representing the parsed HTML
     * @throws FFetchError.DecodingError if the HTML parsing fails
     */
    fun parse(html: String): Document
}

/**
 * Default HTTP client implementation using Ktor.
 *
 * This implementation provides standard HTTP request functionality using the Ktor
 * HTTP client library. It handles basic GET requests and error handling for network operations.
 *
 * @param client The Ktor HttpClient instance to use for making requests
 */
class DefaultFFetchHTTPClient(private val client: HttpClient) : FFetchHTTPClient {
    override suspend fun fetch(
        url: String,
        cacheConfig: FFetchCacheConfig,
    ): Pair<String, HttpResponse> {
        try {
            val response = client.get(url)
            val content = response.bodyAsText()
            return Pair(content, response)
        } catch (e: ClientRequestException) {
            throw FFetchError.NetworkError(e)
        } catch (e: ServerResponseException) {
            throw FFetchError.NetworkError(e)
        } catch (e: RedirectResponseException) {
            throw FFetchError.NetworkError(e)
        } catch (e: HttpRequestTimeoutException) {
            throw FFetchError.NetworkError(e)
        } catch (e: ConnectTimeoutException) {
            throw FFetchError.NetworkError(e)
        } catch (e: SocketTimeoutException) {
            throw FFetchError.NetworkError(e)
        } catch (e: IOException) {
            throw FFetchError.NetworkError(e)
        }
    }
}

/**
 * Default HTML parser implementation using Jsoup.
 *
 * This implementation provides standard HTML parsing functionality using the Jsoup
 * library for parsing HTML documents during document following operations.
 */
class DefaultFFetchHTMLParser : FFetchHTMLParser {
    override fun parse(html: String): Document {
        return try {
            Jsoup.parse(html)
        } catch (e: IllegalArgumentException) {
            throw FFetchError.DecodingError(e)
        } catch (e: OutOfMemoryError) {
            throw FFetchError.DecodingError(e)
        }
    }
}

/**
 * Performance and concurrency configuration for FFetch operations.
 */
data class FFetchPerformanceConfig(
    /**
     * Size of data chunks to process in batch operations.
     * Default value is 255 entries per chunk.
     */
    val chunkSize: Int = FFetchContextBuilder.DEFAULT_CHUNK_SIZE,
    /**
     * Maximum number of concurrent operations allowed.
     * Default value is 5 concurrent operations.
     */
    val maxConcurrency: Int = FFetchContextBuilder.DEFAULT_MAX_CONCURRENCY,
)

/**
 * Client configuration for HTTP and HTML processing.
 */
data class FFetchClientConfig(
    /**
     * HTTP client implementation for making network requests.
     * Defaults to DefaultFFetchHTTPClient with standard Ktor HttpClient.
     */
    val httpClient: FFetchHTTPClient = DefaultFFetchHTTPClient(HttpClient()),
    /**
     * HTML parser implementation for document processing.
     * Defaults to DefaultFFetchHTMLParser using Jsoup.
     */
    val htmlParser: FFetchHTMLParser = DefaultFFetchHTMLParser(),
)

/**
 * Request-specific configuration parameters.
 */
data class FFetchRequestConfig(
    /**
     * Optional name of the sheet to fetch data from.
     * When null, uses the default sheet or index.
     */
    val sheetName: String? = null,
    /**
     * Optional total number of entries expected in the response.
     * When null, no specific total is enforced.
     */
    val total: Int? = null,
)

/**
 * Security configuration for hostname restrictions.
 */
data class FFetchSecurityConfig(
    /**
     * Set of hostnames that are allowed for HTTP requests.
     * When empty, all hosts are allowed. Use this for security restrictions.
     */
    val allowedHosts: MutableSet<String> = mutableSetOf(),
)

/**
 * Builder class for FFetchContext to avoid long parameter lists.
 */
class FFetchContextBuilder {
    /** Size of data chunks to process in batch operations. */
    var chunkSize: Int = DEFAULT_CHUNK_SIZE
    /** Maximum number of concurrent operations allowed. */
    var maxConcurrency: Int = DEFAULT_MAX_CONCURRENCY
    /** Whether to reload cache. */
    var cacheReload: Boolean = false
    /** Cache configuration for HTTP requests. */
    var cacheConfig: FFetchCacheConfig = FFetchCacheConfig.Default
    /** Optional name of the sheet to fetch data from. */
    var sheetName: String? = null
    /** Optional total number of entries expected in the response. */
    var total: Int? = null
    /** HTTP client implementation for making network requests. */
    var httpClient: FFetchHTTPClient = DefaultFFetchHTTPClient(HttpClient())
    /** HTML parser implementation for document processing. */
    var htmlParser: FFetchHTMLParser = DefaultFFetchHTMLParser()
    /** Set of hostnames that are allowed for HTTP requests. */
    var allowedHosts: MutableSet<String> = mutableSetOf()

    /** Builds the performance configuration from current settings. */
    fun buildPerformanceConfig() = FFetchPerformanceConfig(chunkSize, maxConcurrency)
    /** Builds the client configuration from current settings. */
    fun buildClientConfig() = FFetchClientConfig(httpClient, htmlParser)
    /** Builds the request configuration from current settings. */
    fun buildRequestConfig() = FFetchRequestConfig(sheetName, total)
    /** Builds the security configuration from current settings. */
    fun buildSecurityConfig() = FFetchSecurityConfig(allowedHosts)

    /** Builds the final FFetchContext with all configured settings. */
    fun build() = FFetchContext(this)

    companion object {
        /** Default chunk size for batch operations. */
        const val DEFAULT_CHUNK_SIZE = 255
        /** Default maximum concurrency for operations. */
        const val DEFAULT_MAX_CONCURRENCY = 5
    }
}

/**
 * Configuration context for FFetch operations.
 *
 * This class holds all the configuration parameters and state needed for FFetch
 * operations, now organized into logical parameter groups to reduce complexity.
 *
 * @param performanceConfig Performance and concurrency settings
 * @param cacheReload Whether to reload cache (deprecated - use cacheConfig instead)
 * @param cacheConfig Cache configuration for HTTP requests
 * @param clientConfig Client configuration for HTTP and HTML processing
 * @param requestConfig Request-specific configuration parameters
 * @param securityConfig Security configuration for hostname restrictions
 */
class FFetchContext(
    var performanceConfig: FFetchPerformanceConfig = FFetchPerformanceConfig(),
    var cacheReload: Boolean = false,
    var cacheConfig: FFetchCacheConfig = FFetchCacheConfig.Default,
    var clientConfig: FFetchClientConfig = FFetchClientConfig(),
    var requestConfig: FFetchRequestConfig = FFetchRequestConfig(),
    var securityConfig: FFetchSecurityConfig = FFetchSecurityConfig(),
) {
    // Backward compatibility properties
    /**
     * Size of data chunks to process in batch operations.
     * This is a backward compatibility property that delegates to performanceConfig.
     */
    var chunkSize: Int
        get() = performanceConfig.chunkSize
        set(value) {
            performanceConfig = performanceConfig.copy(chunkSize = value)
        }

    /**
     * Maximum number of concurrent operations allowed.
     * This is a backward compatibility property that delegates to performanceConfig.
     */
    var maxConcurrency: Int
        get() = performanceConfig.maxConcurrency
        set(value) {
            performanceConfig = performanceConfig.copy(maxConcurrency = value)
        }

    /**
     * HTTP client implementation for making network requests.
     * This is a backward compatibility property that delegates to clientConfig.
     */
    var httpClient: FFetchHTTPClient
        get() = clientConfig.httpClient
        set(value) {
            clientConfig = clientConfig.copy(httpClient = value)
        }

    /**
     * HTML parser implementation for document processing.
     * This is a backward compatibility property that delegates to clientConfig.
     */
    var htmlParser: FFetchHTMLParser
        get() = clientConfig.htmlParser
        set(value) {
            clientConfig = clientConfig.copy(htmlParser = value)
        }

    /**
     * Optional name of the sheet to fetch data from.
     * This is a backward compatibility property that delegates to requestConfig.
     */
    var sheetName: String?
        get() = requestConfig.sheetName
        set(value) {
            requestConfig = requestConfig.copy(sheetName = value)
        }

    /**
     * Optional total number of entries expected in the response.
     * This is a backward compatibility property that delegates to requestConfig.
     */
    var total: Int?
        get() = requestConfig.total
        set(value) {
            requestConfig = requestConfig.copy(total = value)
        }

    /**
     * Set of hostnames that are allowed for HTTP requests.
     * This is a backward compatibility property that delegates to securityConfig.
     */
    var allowedHosts: MutableSet<String>
        get() = securityConfig.allowedHosts
        set(value) {
            securityConfig = securityConfig.copy(allowedHosts = value)
        }

    // Builder pattern constructor for backward compatibility
    constructor(
        configBuilder: FFetchContextBuilder,
    ) : this(
        performanceConfig = configBuilder.buildPerformanceConfig(),
        cacheReload = configBuilder.cacheReload,
        cacheConfig = configBuilder.cacheConfig,
        clientConfig = configBuilder.buildClientConfig(),
        requestConfig = configBuilder.buildRequestConfig(),
        securityConfig = configBuilder.buildSecurityConfig(),
    )

    /**
     * Legacy parameter holder for backward compatibility.
     */
    data class LegacyParams(
        /** Size of data chunks to process in batch operations. */
        val chunkSize: Int = FFetchContextBuilder.DEFAULT_CHUNK_SIZE,
        /** Whether to reload cache. */
        val cacheReload: Boolean = false,
        /** Cache configuration for HTTP requests. */
        val cacheConfig: FFetchCacheConfig = FFetchCacheConfig.Default,
        /** Optional name of the sheet to fetch data from. */
        val sheetName: String? = null,
        /** HTTP client implementation for making network requests. */
        val httpClient: FFetchHTTPClient = DefaultFFetchHTTPClient(HttpClient()),
        /** HTML parser implementation for document processing. */
        val htmlParser: FFetchHTMLParser = DefaultFFetchHTMLParser(),
        /** Optional total number of entries expected in the response. */
        val total: Int? = null,
        /** Maximum number of concurrent operations allowed. */
        val maxConcurrency: Int = FFetchContextBuilder.DEFAULT_MAX_CONCURRENCY,
        /** Set of hostnames that are allowed for HTTP requests. */
        val allowedHosts: MutableSet<String> = mutableSetOf(),
    )

    companion object {
        /**
         * Creates FFetchContext with legacy parameter style for backward compatibility.
         */
        fun create(legacyParams: LegacyParams): FFetchContext {
            return FFetchContextBuilder().apply {
                chunkSize = legacyParams.chunkSize
                cacheReload = legacyParams.cacheReload
                cacheConfig = legacyParams.cacheConfig
                sheetName = legacyParams.sheetName
                httpClient = legacyParams.httpClient
                htmlParser = legacyParams.htmlParser
                total = legacyParams.total
                maxConcurrency = legacyParams.maxConcurrency
                allowedHosts = legacyParams.allowedHosts
            }.build()
        }

        /**
         * Creates FFetchContext with individual parameters for backward compatibility.
         */
        fun createLegacy(
            chunkSize: Int = FFetchContextBuilder.DEFAULT_CHUNK_SIZE,
            cacheReload: Boolean = false,
            cacheConfig: FFetchCacheConfig = FFetchCacheConfig.Default,
            sheetName: String? = null,
            httpClient: FFetchHTTPClient = DefaultFFetchHTTPClient(HttpClient()),
        ): FFetchContext {
            return FFetchContextBuilder().apply {
                this.chunkSize = chunkSize
                this.cacheReload = cacheReload
                this.cacheConfig = cacheConfig
                this.sheetName = sheetName
                this.httpClient = httpClient
            }.build()
        }
    }

    /**
     * Creates a copy of this FFetchContext with optionally modified legacy parameters.
     * For new code, prefer the parameter object-based copy method.
     */
    fun copyLegacy(
        chunkSize: Int = this.chunkSize,
        cacheReload: Boolean = this.cacheReload,
        cacheConfig: FFetchCacheConfig = this.cacheConfig,
        sheetName: String? = this.sheetName,
    ): FFetchContext {
        return createLegacy(
            chunkSize = chunkSize,
            cacheReload = cacheReload,
            cacheConfig = cacheConfig,
            sheetName = sheetName,
            httpClient = this.httpClient,
        ).also { context ->
            context.total = this.total
            context.maxConcurrency = this.maxConcurrency
            context.allowedHosts = this.allowedHosts.toMutableSet()
        }
    }

    /**
     * Creates a copy with parameter object-based configuration (preferred method).
     */
    fun copy(
        performanceConfig: FFetchPerformanceConfig = this.performanceConfig,
        cacheReload: Boolean = this.cacheReload,
        cacheConfig: FFetchCacheConfig = this.cacheConfig,
        clientConfig: FFetchClientConfig = this.clientConfig,
        requestConfig: FFetchRequestConfig = this.requestConfig,
    ): FFetchContext {
        return FFetchContext(
            performanceConfig = performanceConfig,
            cacheReload = cacheReload,
            cacheConfig = cacheConfig,
            clientConfig = clientConfig,
            requestConfig = requestConfig,
            securityConfig = this.securityConfig.copy(
                allowedHosts = this.securityConfig.allowedHosts.toMutableSet(),
            ),
        )
    }

    /**
     * Creates a copy with a modified security configuration.
     */
    fun copyWithSecurity(
        securityConfig: FFetchSecurityConfig,
    ): FFetchContext {
        return copy().copy(
            performanceConfig = this.performanceConfig,
            cacheReload = this.cacheReload,
            cacheConfig = this.cacheConfig,
            clientConfig = this.clientConfig,
            requestConfig = this.requestConfig,
        ).also { context ->
            context.securityConfig = securityConfig
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FFetchContext

        if (chunkSize != other.chunkSize) return false
        if (cacheReload != other.cacheReload) return false
        if (cacheConfig != other.cacheConfig) return false
        if (sheetName != other.sheetName) return false
        if (httpClient != other.httpClient) return false
        if (htmlParser != other.htmlParser) return false
        if (total != other.total) return false
        if (maxConcurrency != other.maxConcurrency) return false
        if (allowedHosts != other.allowedHosts) return false

        return true
    }

    override fun hashCode(): Int {
        var result = chunkSize
        result = 31 * result + cacheReload.hashCode()
        result = 31 * result + cacheConfig.hashCode()
        result = 31 * result + (sheetName?.hashCode() ?: 0)
        result = 31 * result + httpClient.hashCode()
        result = 31 * result + htmlParser.hashCode()
        result = 31 * result + (total ?: 0)
        result = 31 * result + maxConcurrency
        result = 31 * result + allowedHosts.hashCode()
        return result
    }

    override fun toString(): String {
        return "FFetchContext(chunkSize=$chunkSize, cacheReload=$cacheReload, " +
            "cacheConfig=$cacheConfig, sheetName=$sheetName, httpClient=$httpClient, " +
            "htmlParser=$htmlParser, total=$total, maxConcurrency=$maxConcurrency, " +
            "allowedHosts=$allowedHosts)"
    }
}

// / Transform function type for map operations
typealias FFetchTransform<Input, Output> = suspend (Input) -> Output

// / Predicate function type for filter operations
typealias FFetchPredicate<Element> = suspend (Element) -> Boolean
