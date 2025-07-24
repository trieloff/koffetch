//
// Examples.kt
// KotlinFFetch
//
// Usage examples for KotlinFFetch
//

package live.aem.koffetch.examples

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import live.aem.koffetch.FFetchCacheConfig
import live.aem.koffetch.FFetchEntry
import live.aem.koffetch.extensions.all
import live.aem.koffetch.extensions.allow
import live.aem.koffetch.extensions.cache
import live.aem.koffetch.extensions.chunks
import live.aem.koffetch.extensions.filter
import live.aem.koffetch.extensions.first
import live.aem.koffetch.extensions.follow
import live.aem.koffetch.extensions.limit
import live.aem.koffetch.extensions.map
import live.aem.koffetch.extensions.reloadCache
import live.aem.koffetch.extensions.sheet
import live.aem.koffetch.extensions.withCacheReload
import live.aem.koffetch.ffetch
import org.jsoup.nodes.Document

/**
 * Examples demonstrating various KotlinFFetch usage patterns.
 *
 * This object contains comprehensive examples showing how to use KotlinFFetch
 * for common AEM Edge Delivery Services operations including pagination,
 * filtering, transformation, document following, caching, and security.
 */
object Examples {
    private const val EXAMPLE_CHUNK_SIZE = 100
    private const val EXAMPLE_LIMIT = 5
    private const val EXAMPLE_CACHE_MAX_AGE = 3600

    /**
     * Example: Stream all entries from an AEM query index.
     *
     * Demonstrates the basic usage of ffetch to stream all entries
     * from a query index using Kotlin Flow.
     */
    fun streamAllEntries() =
        runBlocking {
            val entries = ffetch("https://example.com/query-index.json")

            entries.asFlow().collect { entry ->
                println(entry["title"] as? String ?: "No title")
            }
        }

    /**
     * Example: Get the first entry from an AEM query index.
     *
     * @return The first entry from the index, or null if no entries exist
     */
    suspend fun getFirstEntry(): FFetchEntry? {
        return ffetch("https://example.com/query-index.json").first()
    }

    /**
     * Example: Get all entries from an AEM query index as a list.
     *
     * @return A list containing all entries from the index
     */
    suspend fun getAllEntries(): List<FFetchEntry> {
        return ffetch("https://example.com/query-index.json").all()
    }

    /**
     * Example: Map and filter entries using Flow operations.
     *
     * Demonstrates how to transform entries and filter them based
     * on specific criteria (entries containing "Kotlin" in the title).
     */
    fun mapAndFilterEntries() =
        runBlocking {
            ffetch("https://example.com/query-index.json")
                .map<String?> { it["title"] as? String }
                .filter { it?.contains("Kotlin") == true }
                .collect { title ->
                    println(title ?: "")
                }
        }

    /**
     * Example: Control pagination using chunks and limits.
     *
     * Shows how to configure chunk size for pagination and limit
     * the total number of entries processed.
     */
    fun controlPagination() =
        runBlocking {
            ffetch("https://example.com/query-index.json")
                .chunks(EXAMPLE_CHUNK_SIZE)
                .limit(EXAMPLE_LIMIT)
                .asFlow()
                .collect { entry ->
                    println(entry)
                }
        }

    /**
     * Example: Access a specific sheet from a multi-sheet index.
     *
     * Demonstrates targeting a specific sheet (e.g., "products")
     * when working with indices that contain multiple sheets.
     */
    fun accessSpecificSheet() =
        runBlocking {
            ffetch("https://example.com/query-index.json")
                .sheet("products")
                .asFlow()
                .collect { entry ->
                    println(entry["sku"] as? String ?: "")
                }
        }

    /**
     * Example: Document following with security constraints.
     *
     * Shows how to use the follow() extension to fetch and parse HTML documents
     * referenced in index entries, with default security (same hostname only).
     */
    fun documentFollowingWithSecurity() =
        runBlocking {
            // Basic document following (same hostname only)
            val entriesWithDocs =
                ffetch("https://example.com/query-index.json")
                    .follow("path", "document") // follows URLs in 'path' field
                    .all()

            // The 'document' field will contain parsed HTML Document objects
            for (entry in entriesWithDocs) {
                if (entry["document"] is Document) {
                    val doc = entry["document"] as Document
                    println(doc.title())
                }
            }
        }

    /**
     * Example: Allow additional hostnames for document following.
     *
     * Demonstrates different ways to configure hostname allowlists
     * for document following operations, including single hostnames,
     * multiple hostnames, and wildcard permissions.
     */
    fun allowAdditionalHostnames() =
        runBlocking {
            // Allow specific hostname
            ffetch("https://example.com/query-index.json")
                .allow("trusted.com")
                .follow("path", "document")
                .all()

            // Allow multiple hostnames
            ffetch("https://example.com/query-index.json")
                .allow(listOf("trusted.com", "api.example.com"))
                .follow("path", "document")
                .all()

            // Allow all hostnames (use with caution)
            ffetch("https://example.com/query-index.json")
                .allow("*")
                .follow("path", "document")
                .all()
        }

    /**
     * Example: Configure caching behavior.
     *
     * Shows different caching strategies including bypassing cache,
     * cache-only mode, and cache-else-load behavior.
     */
    fun cacheConfiguration() =
        runBlocking {
            // Always fetch fresh data (bypass cache)
            ffetch("https://example.com/api/data.json")
                .cache(FFetchCacheConfig.NoCache)
                .all()

            // Only use cached data (won't make network request)
            ffetch("https://example.com/api/data.json")
                .cache(FFetchCacheConfig.CacheOnly)
                .all()

            // Use cache if available, otherwise load from network
            ffetch("https://example.com/api/data.json")
                .cache(FFetchCacheConfig.CacheElseLoad)
                .all()
        }

    /**
     * Example: Custom cache configuration with specific parameters.
     *
     * Demonstrates creating a custom cache configuration with
     * specific max-age settings.
     */
    fun customConfiguration() =
        runBlocking {
            // Cache for 1 hour regardless of server headers
            val customConfig =
                FFetchCacheConfig(
                    maxAge = EXAMPLE_CACHE_MAX_AGE,
                )

            ffetch("https://example.com/api/data.json")
                .cache(customConfig)
                .all()
        }

    /**
     * Example: Using backward compatibility methods.
     *
     * Shows how to use legacy cache methods that map to the newer
     * cache configuration system for maintaining compatibility.
     */
    fun backwardCompatibility() =
        runBlocking {
            // Legacy method - maps to .cache(FFetchCacheConfig.NoCache)
            ffetch("https://example.com/api/data.json")
                .reloadCache()
                .all()

            // Legacy method with parameter
            ffetch("https://example.com/api/data.json")
                .withCacheReload(false) // Uses default cache behavior
                .all()
        }
}
