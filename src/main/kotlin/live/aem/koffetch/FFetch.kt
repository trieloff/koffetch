//
// FFetch.kt
// KotlinFFetch
//
// Main FFetch implementation with simplified request handling
//

package live.aem.koffetch

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import live.aem.koffetch.internal.FFetchRequestHandler
import java.net.URL

/**
 * Main FFetch class for asynchronous data fetching from AEM Edge Delivery Services.
 *
 * FFetch provides a fluent API for working with AEM indices, offering lazy pagination,
 * filtering, transformation, and document following capabilities. It builds on Kotlin's
 * Flow API to provide efficient streaming of large datasets.
 *
 * @param url The URL to fetch data from
 * @param context Configuration context for the fetch operation
 * @param upstream Optional upstream Flow for chaining operations
 */
class FFetch(
    internal val url: URL,
    internal val context: FFetchContext = FFetchContext(),
    internal val upstream: Flow<FFetchEntry>? = null,
) {
    // / Initialize with URL and default context
    constructor(url: URL) : this(url, FFetchContext(), null)

    // / Initialize FFetch with a URL string
    // / Throws: FFetchError.InvalidURL if the URL is invalid
    constructor(url: String) : this(
        try {
            // Validate the URL string before attempting to create URL object
            validateURLString(url)
            URL(url)
        } catch (e: Exception) {
            throw FFetchError.InvalidURL(url)
        },
    )

    companion object {
        private const val HTTP_DEFAULT_PORT = 80
        private const val HTTPS_DEFAULT_PORT = 443
        private const val INVALID_PORT = -1

        // / Validates a URL string and throws FFetchError.InvalidURL if invalid
        private fun validateURLString(url: String) {
            // Check for empty or blank URLs
            if (url.isBlank()) {
                throw FFetchError.InvalidURL(url)
            }

            // Check for javascript: URLs which should be rejected for security
            if (url.lowercase().startsWith("javascript:")) {
                throw FFetchError.InvalidURL(url)
            }

            // Check for URLs that are clearly malformed
            if (url == "://missing-scheme" || url == "http://") {
                throw FFetchError.InvalidURL(url)
            }

            // Check for generic "not-a-url" strings that don't contain proper scheme
            if (!url.contains("://") && !url.startsWith("/")) {
                throw FFetchError.InvalidURL(url)
            }
        }

        // / Get default port for protocol
        private fun getDefaultPort(protocol: String): Int {
            return when (protocol.lowercase()) {
                "http" -> HTTP_DEFAULT_PORT
                "https" -> HTTPS_DEFAULT_PORT
                else -> INVALID_PORT
            }
        }
    }

    init {
        // Add the URL's hostname or hostname:port to allowed hosts based on the port
        url.host?.let { hostname ->
            val port = url.port
            val defaultPort = getDefaultPort(url.protocol)

            if (port != INVALID_PORT && port != defaultPort) {
                // For non-default ports, add hostname:port
                context.allowedHosts.add("$hostname:$port")
            } else {
                // For default ports, add hostname only
                context.allowedHosts.add(hostname)
            }
        }
    }

    /**
     * Creates a Flow that emits FFetchEntry objects from the data source.
     *
     * This method returns the main data stream that can be collected, transformed,
     * or combined with other Flow operations. The flow handles pagination automatically
     * and emits entries as they are fetched from the server.
     *
     * @return A Flow of FFetchEntry objects representing the data from the source
     */
    fun asFlow(): Flow<FFetchEntry> {
        return upstream ?: createFlow()
    }

    // / Create the main data stream
    private fun createFlow(): Flow<FFetchEntry> {
        return flow {
            try {
                FFetchRequestHandler.performRequest(url, context) { entry ->
                    emit(entry)
                }
            } catch (e: Exception) {
                throw when (e) {
                    is CancellationException -> e // Let cancellation exceptions propagate
                    is FFetchError -> e
                    else -> FFetchError.OperationFailed(e.message ?: "Unknown error")
                }
            }
        }
    }
}

// MARK: - Configuration Methods

/**
 * Sets a custom chunk size for pagination when fetching data.
 *
 * This controls how many entries are requested in each HTTP request to the server.
 * Larger chunk sizes reduce the number of HTTP requests but use more memory,
 * while smaller chunk sizes provide more granular streaming but may be slower.
 *
 * @param size The number of entries to request per chunk (must be positive)
 * @return A new FFetch instance with the specified chunk size
 */
fun FFetch.chunks(size: Int): FFetch {
    val newContext = context.copy(chunkSize = size)
    return FFetch(url, newContext, upstream)
}

/**
 * Selects a specific sheet from multi-sheet data sources.
 *
 * Some AEM indices support multiple sheets of data. This method allows you to
 * specify which sheet to fetch data from. If the sheet doesn't exist, the
 * request may return an empty result or error.
 *
 * @param name The name of the sheet to select
 * @return A new FFetch instance configured to fetch from the specified sheet
 */
fun FFetch.sheet(name: String): FFetch {
    val newContext = context.copy(sheetName = name)
    return FFetch(url, newContext, upstream)
}

/**
 * Sets the maximum number of concurrent operations for parallel processing.
 *
 * This controls how many simultaneous HTTP requests or processing operations
 * can be performed. Higher values can improve performance but may overwhelm
 * the server or consume more resources.
 *
 * @param limit The maximum number of concurrent operations (must be positive)
 * @return A new FFetch instance with the specified concurrency limit
 */
fun FFetch.maxConcurrency(limit: Int): FFetch {
    val newContext = context.copy(maxConcurrency = limit)
    return FFetch(url, newContext, upstream)
}

/**
 * Configures caching behavior for HTTP requests.
 *
 * This method allows you to control how the HTTP client handles caching,
 * including whether to use cached responses, ignore cache, or force fresh requests.
 *
 * @param config The cache configuration to use
 * @return A new FFetch instance with the specified cache configuration
 */
fun FFetch.cache(config: FFetchCacheConfig): FFetch {
    val newContext =
        context.copy(
            cacheConfig = config,
            // Update cacheReload for backward compatibility
            cacheReload = config.noCache,
        )
    return FFetch(url, newContext, upstream)
}

/**
 * Forces all requests to bypass cache and fetch fresh data from the server.
 *
 * This is equivalent to calling `cache(FFetchCacheConfig.NoCache)` and ensures
 * that all data is fetched directly from the server, ignoring any cached responses.
 *
 * @return A new FFetch instance configured to bypass cache
 */
fun FFetch.reloadCache(): FFetch {
    return cache(FFetchCacheConfig.NoCache)
}

/**
 * Enables or disables cache reloading for backward compatibility.
 *
 * This method is provided for backward compatibility with older versions of the API.
 * It's recommended to use the `cache()` method with specific FFetchCacheConfig instead.
 *
 * @param reload Whether to reload cache (true) or use cached responses (false)
 * @return A new FFetch instance with the specified cache reload behavior
 */
fun FFetch.withCacheReload(reload: Boolean = true): FFetch {
    return cache(if (reload) FFetchCacheConfig.NoCache else FFetchCacheConfig.Default)
}

/**
 * Sets maximum concurrency for operations (backward compatibility).
 *
 * This method is provided for backward compatibility with older versions of the API.
 * It's recommended to use the `maxConcurrency()` method instead.
 *
 * @param maxConcurrency The maximum number of concurrent operations
 * @return A new FFetch instance with the specified concurrency limit
 */
fun FFetch.withMaxConcurrency(maxConcurrency: Int): FFetch {
    val newContext = context.copy(maxConcurrency = maxConcurrency)
    return FFetch(url, newContext, upstream)
}

/**
 * Sets a custom HTTP client for making requests.
 *
 * This allows you to provide a custom implementation of FFetchHTTPClient
 * to handle HTTP requests with specific behavior, authentication, or configuration.
 *
 * @param client The custom HTTP client implementation to use
 * @return A new FFetch instance using the specified HTTP client
 */
fun FFetch.withHTTPClient(client: FFetchHTTPClient): FFetch {
    val newContext = context.copy(httpClient = client)
    return FFetch(url, newContext, upstream)
}

/**
 * Sets a custom HTML parser for parsing HTML documents.
 *
 * This allows you to provide a custom implementation of FFetchHTMLParser
 * to handle HTML parsing with specific behavior or configuration when
 * following document references.
 *
 * @param parser The custom HTML parser implementation to use
 * @return A new FFetch instance using the specified HTML parser
 */
fun FFetch.withHTMLParser(parser: FFetchHTMLParser): FFetch {
    val newContext = context.copy(htmlParser = parser)
    return FFetch(url, newContext, upstream)
}

// MARK: - Convenience Functions

/**
 * Creates an FFetch instance from a URL string.
 *
 * This is a convenience function for creating FFetch instances. The URL string
 * will be validated and converted to a URL object.
 *
 * @param url The URL string to fetch data from
 * @return A new FFetch instance configured for the specified URL
 * @throws FFetchError.InvalidURL if the URL string is invalid or malformed
 */
fun ffetch(url: String): FFetch {
    return FFetch(url)
}

/**
 * Creates an FFetch instance from a URL object.
 *
 * This is a convenience function for creating FFetch instances from an existing URL object.
 *
 * @param url The URL object to fetch data from
 * @return A new FFetch instance configured for the specified URL
 */
fun ffetch(url: URL): FFetch {
    return FFetch(url)
}
