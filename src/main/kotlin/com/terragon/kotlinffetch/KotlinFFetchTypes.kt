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

package com.terragon.kotlinffetch

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/// Represents a single entry from an AEM index response
typealias FFetchEntry = Map<String, Any?>

/// Represents the JSON response structure from AEM indices
@Serializable
data class FFetchResponse(
    /// Total number of entries available
    val total: Int,
    
    /// Current offset in the result set
    val offset: Int,
    
    /// Maximum number of entries requested
    val limit: Int,
    
    /// Array of data entries
    val data: List<JsonObject>
) {
    fun toFFetchEntries(): List<FFetchEntry> {
        return data.map { jsonObject ->
            jsonObject.entries.associate { (key, value) ->
                key to value.toString().removeSurrounding("\"")
            }
        }
    }
}

/// Errors that can occur during FFetch operations
sealed class FFetchError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidURL(url: String) : FFetchError("Invalid URL: $url")
    class NetworkError(cause: Throwable) : FFetchError("Network error: ${cause.message}", cause)
    class DecodingError(cause: Throwable) : FFetchError("Decoding error: ${cause.message}", cause)
    object InvalidResponse : FFetchError("Invalid response format")
    object DocumentNotFound : FFetchError("Document not found")
    class OperationFailed(message: String) : FFetchError("Operation failed: $message")
}

/// Cache configuration for FFetch requests
data class FFetchCacheConfig(
    /// Whether to ignore cache and always fetch from server
    val noCache: Boolean = false,
    
    /// Whether to only use cache and never fetch from server
    val cacheOnly: Boolean = false,
    
    /// Whether to use cache if available, otherwise fetch from server
    val cacheElseLoad: Boolean = false,
    
    /// Maximum age in seconds for cached responses
    val maxAge: Long? = null,
    
    /// Whether to ignore server cache control headers
    val ignoreServerCacheControl: Boolean = false
) {
    companion object {
        /// Default cache configuration that respects HTTP cache control headers
        val Default = FFetchCacheConfig()
        
        /// Cache configuration that ignores cache and always fetches from server
        val NoCache = FFetchCacheConfig(noCache = true)
        
        /// Cache configuration that only uses cache and never fetches from server
        val CacheOnly = FFetchCacheConfig(cacheOnly = true)
        
        /// Cache configuration that uses cache if available, otherwise fetches from server
        val CacheElseLoad = FFetchCacheConfig(cacheElseLoad = true)
    }
}

/// Interface for HTTP client abstraction
interface FFetchHTTPClient {
    suspend fun fetch(url: String, cacheConfig: FFetchCacheConfig = FFetchCacheConfig.Default): Pair<String, HttpResponse>
}

/// Interface for HTML parsing abstraction
interface FFetchHTMLParser {
    fun parse(html: String): Document
}

/// Default HTTP client implementation using Ktor
class DefaultFFetchHTTPClient(private val client: HttpClient) : FFetchHTTPClient {
    override suspend fun fetch(url: String, cacheConfig: FFetchCacheConfig): Pair<String, HttpResponse> {
        try {
            val response = client.get(url)
            val content = response.bodyAsText()
            return Pair(content, response)
        } catch (e: Exception) {
            throw FFetchError.NetworkError(e)
        }
    }
}

/// Default HTML parser implementation using Jsoup
class DefaultFFetchHTMLParser : FFetchHTMLParser {
    override fun parse(html: String): Document {
        return try {
            Jsoup.parse(html)
        } catch (e: Exception) {
            throw FFetchError.DecodingError(e)
        }
    }
}

/// Configuration context for FFetch operations
class FFetchContext(
    /// Size of chunks to fetch during pagination
    var chunkSize: Int = 255,
    
    /// Whether to reload cache (deprecated - use cacheConfig instead)
    var cacheReload: Boolean = false,
    
    /// Cache configuration for HTTP requests
    var cacheConfig: FFetchCacheConfig = FFetchCacheConfig.Default,
    
    /// Name of the sheet to query (for multi-sheet responses)
    var sheetName: String? = null,
    
    /// HTTP client for making requests
    var httpClient: FFetchHTTPClient = DefaultFFetchHTTPClient(HttpClient()),
    
    /// HTML parser for parsing documents
    var htmlParser: FFetchHTMLParser = DefaultFFetchHTMLParser(),
    
    /// Total number of entries (set after first request)
    var total: Int? = null,
    
    /// Maximum number of concurrent operations
    var maxConcurrency: Int = 5,
    
    /// Set of allowed hostnames for document following (security feature)
    /// By default, only the hostname of the initial request is allowed
    /// Use "*" to allow all hostnames
    var allowedHosts: MutableSet<String> = mutableSetOf()
) {
    /// Copy method to ensure mutable collections are deep copied
    fun copy(
        chunkSize: Int = this.chunkSize,
        cacheReload: Boolean = this.cacheReload,
        cacheConfig: FFetchCacheConfig = this.cacheConfig,
        sheetName: String? = this.sheetName,
        httpClient: FFetchHTTPClient = this.httpClient,
        htmlParser: FFetchHTMLParser = this.htmlParser,
        total: Int? = this.total,
        maxConcurrency: Int = this.maxConcurrency,
        allowedHosts: MutableSet<String> = this.allowedHosts.toMutableSet()
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
            allowedHosts = allowedHosts
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
        return "FFetchContext(chunkSize=$chunkSize, cacheReload=$cacheReload, cacheConfig=$cacheConfig, sheetName=$sheetName, httpClient=$httpClient, htmlParser=$htmlParser, total=$total, maxConcurrency=$maxConcurrency, allowedHosts=$allowedHosts)"
    }
}

/// Transform function type for map operations
typealias FFetchTransform<Input, Output> = suspend (Input) -> Output

/// Predicate function type for filter operations
typealias FFetchPredicate<Element> = suspend (Element) -> Boolean