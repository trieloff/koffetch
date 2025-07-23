//
// FFetch.kt
// KotlinFFetch
//
// Main FFetch implementation with simplified request handling
//

package com.terragon.kotlinffetch

import com.terragon.kotlinffetch.internal.FFetchRequestHandler
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.net.URL

/// Main FFetch class for asynchronous data fetching
class FFetch(
    internal val url: URL,
    internal val context: FFetchContext = FFetchContext(),
    internal val upstream: Flow<FFetchEntry>? = null
) {
    
    /// Initialize with URL and default context
    constructor(url: URL) : this(url, FFetchContext(), null)
    
    /// Initialize FFetch with a URL string
    /// Throws: FFetchError.InvalidURL if the URL is invalid
    constructor(url: String) : this(
        try { 
            URL(url) 
        } catch (e: Exception) { 
            throw FFetchError.InvalidURL(url) 
        }
    )
    
    init {
        // Add the URL's hostname or hostname:port to allowed hosts based on the port
        url.host?.let { hostname ->
            if (url.port != -1) {
                // For non-default ports, add hostname:port
                context.allowedHosts.add("${hostname}:${url.port}")
            } else {
                // For default ports, add hostname only
                context.allowedHosts.add(hostname)
            }
        }
    }
    
    /// Create the main data flow
    fun asFlow(): Flow<FFetchEntry> {
        return upstream ?: createFlow()
    }
    
    /// Create the main data stream
    private fun createFlow(): Flow<FFetchEntry> {
        return flow {
            try {
                FFetchRequestHandler.performRequest(url, context) { entry ->
                    emit(entry)
                }
            } catch (e: Exception) {
                throw when (e) {
                    is FFetchError -> e
                    else -> FFetchError.OperationFailed(e.message ?: "Unknown error")
                }
            }
        }
    }
}

// MARK: - Configuration Methods

/// Set custom chunk size for pagination
fun FFetch.chunks(size: Int): FFetch {
    val newContext = context.copy(chunkSize = size)
    return FFetch(url, newContext, upstream)
}

/// Select a specific sheet (for spreadsheet-like data sources)
fun FFetch.sheet(name: String): FFetch {
    val newContext = context.copy(sheetName = name)
    return FFetch(url, newContext, upstream)
}

/// Set maximum concurrency for parallel operations
fun FFetch.maxConcurrency(limit: Int): FFetch {
    val newContext = context.copy(maxConcurrency = limit)
    return FFetch(url, newContext, upstream)
}

/// Configure cache behavior for requests
fun FFetch.cache(config: FFetchCacheConfig): FFetch {
    val newContext = context.copy(
        cacheConfig = config,
        // Update cacheReload for backward compatibility
        cacheReload = config.noCache
    )
    return FFetch(url, newContext, upstream)
}

/// Force cache reload for requests
fun FFetch.reloadCache(): FFetch {
    return cache(FFetchCacheConfig.NoCache)
}

/// Enable cache reloading (backward compatibility)
fun FFetch.withCacheReload(reload: Boolean = true): FFetch {
    return cache(if (reload) FFetchCacheConfig.NoCache else FFetchCacheConfig.Default)
}

/// Set maximum concurrency for operations (backward compatibility)
fun FFetch.withMaxConcurrency(maxConcurrency: Int): FFetch {
    val newContext = context.copy(maxConcurrency = maxConcurrency)
    return FFetch(url, newContext, upstream)
}

/// Set custom HTTP client
fun FFetch.withHTTPClient(client: FFetchHTTPClient): FFetch {
    val newContext = context.copy(httpClient = client)
    return FFetch(url, newContext, upstream)
}

/// Set custom HTML parser
fun FFetch.withHTMLParser(parser: FFetchHTMLParser): FFetch {
    val newContext = context.copy(htmlParser = parser)
    return FFetch(url, newContext, upstream)
}

// MARK: - Convenience Functions

/// Create FFetch instance from URL string
/// Returns: FFetch instance
/// Throws: FFetchError.InvalidURL if the URL is invalid
fun ffetch(url: String): FFetch {
    return FFetch(url)
}

/// Create FFetch instance from URL
fun ffetch(url: URL): FFetch {
    return FFetch(url)
}