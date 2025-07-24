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
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

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
        } catch (e: Exception) {
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
        } catch (e: Exception) {
            throw FFetchError.DecodingError(e)
        }
    }
}

/**
 * Configuration context for FFetch operations.
 *
 * This class holds all the configuration parameters and state needed for FFetch
 * operations, including pagination settings, caching configuration, HTTP client
 * customization, and security settings for document following.
 *
 * @param chunkSize Size of chunks to fetch during pagination (default: 255)
 * @param cacheReload Whether to reload cache (deprecated - use cacheConfig instead)
 * @param cacheConfig Cache configuration for HTTP requests
 * @param sheetName Name of the sheet to query (for multi-sheet responses)
 * @param httpClient HTTP client for making requests
 * @param htmlParser HTML parser for parsing documents
 * @param total Total number of entries (set after first request)
 * @param maxConcurrency Maximum number of concurrent operations
 * @param allowedHosts Set of allowed hostnames for document following (security feature)
 */
class FFetchContext(
    // / Size of chunks to fetch during pagination
    var chunkSize: Int = 255,
    // / Whether to reload cache (deprecated - use cacheConfig instead)
    var cacheReload: Boolean = false,
    // / Cache configuration for HTTP requests
    var cacheConfig: FFetchCacheConfig = FFetchCacheConfig.Default,
    // / Name of the sheet to query (for multi-sheet responses)
    var sheetName: String? = null,
    // / HTTP client for making requests
    var httpClient: FFetchHTTPClient = DefaultFFetchHTTPClient(HttpClient()),
    // / HTML parser for parsing documents
    var htmlParser: FFetchHTMLParser = DefaultFFetchHTMLParser(),
    // / Total number of entries (set after first request)
    var total: Int? = null,
    // / Maximum number of concurrent operations
    var maxConcurrency: Int = 5,
    // / Set of allowed hostnames for document following (security feature)
    // / By default, only the hostname of the initial request is allowed
    // / Use "*" to allow all hostnames
    var allowedHosts: MutableSet<String> = mutableSetOf(),
) {
    /**
     * Creates a copy of this FFetchContext with optionally modified parameters.
     *
     * This method ensures that mutable collections like allowedHosts are properly
     * deep copied to prevent unintended sharing between instances.
     *
     * @param chunkSize Size of chunks to fetch during pagination
     * @param cacheReload Whether to reload cache (deprecated)
     * @param cacheConfig Cache configuration for HTTP requests
     * @param sheetName Name of the sheet to query
     * @param httpClient HTTP client for making requests
     * @param htmlParser HTML parser for parsing documents
     * @param total Total number of entries
     * @param maxConcurrency Maximum number of concurrent operations
     * @param allowedHosts Set of allowed hostnames for document following
     * @return A new FFetchContext instance with the specified parameters
     */
    fun copy(
        chunkSize: Int = this.chunkSize,
        cacheReload: Boolean = this.cacheReload,
        cacheConfig: FFetchCacheConfig = this.cacheConfig,
        sheetName: String? = this.sheetName,
        httpClient: FFetchHTTPClient = this.httpClient,
        htmlParser: FFetchHTMLParser = this.htmlParser,
        total: Int? = this.total,
        maxConcurrency: Int = this.maxConcurrency,
        allowedHosts: MutableSet<String> = this.allowedHosts.toMutableSet(),
    ): FFetchContext {
        return FFetchContext(
            chunkSize = chunkSize,
            cacheReload = cacheReload,
            cacheConfig = cacheConfig,
            sheetName = sheetName,
            httpClient = httpClient,
            htmlParser = htmlParser,
            total = total,
            maxConcurrency = maxConcurrency,
            allowedHosts = allowedHosts,
        )
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
