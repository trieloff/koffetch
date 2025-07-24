//
// Examples.kt
// KotlinFFetch
//
// Usage examples for KotlinFFetch
//

package live.aem.koffetch.examples

import live.aem.koffetch.*
import live.aem.koffetch.extensions.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.jsoup.nodes.Document

/// Basic usage examples
object Examples {
    
    /// Example: Stream all entries
    fun streamAllEntries() = runBlocking {
        val entries = ffetch("https://example.com/query-index.json")
        
        entries.asFlow().collect { entry ->
            println(entry["title"] as? String ?: "No title")
        }
    }
    
    /// Example: Get first entry
    suspend fun getFirstEntry(): FFetchEntry? {
        return ffetch("https://example.com/query-index.json").first()
    }
    
    /// Example: Get all entries as list
    suspend fun getAllEntries(): List<FFetchEntry> {
        return ffetch("https://example.com/query-index.json").all()
    }
    
    /// Example: Map and filter entries
    fun mapAndFilterEntries() = runBlocking {
        ffetch("https://example.com/query-index.json")
            .map<String?> { it["title"] as? String }
            .filter { it?.contains("Kotlin") == true }
            .collect { title ->
                println(title ?: "")
            }
    }
    
    /// Example: Control pagination with chunks and limit
    fun controlPagination() = runBlocking {
        ffetch("https://example.com/query-index.json")
            .chunks(100)
            .limit(5)
            .asFlow()
            .collect { entry ->
                println(entry)
            }
    }
    
    /// Example: Access a specific sheet
    fun accessSpecificSheet() = runBlocking {
        ffetch("https://example.com/query-index.json")
            .sheet("products")
            .asFlow()
            .collect { entry ->
                println(entry["sku"] as? String ?: "")
            }
    }
    
    /// Example: Document following with security
    fun documentFollowingWithSecurity() = runBlocking {
        // Basic document following (same hostname only)
        val entriesWithDocs = ffetch("https://example.com/query-index.json")
            .follow("path", "document")  // follows URLs in 'path' field
            .all()
        
        // The 'document' field will contain parsed HTML Document objects
        for (entry in entriesWithDocs) {
            if (entry["document"] is Document) {
                val doc = entry["document"] as Document
                println(doc.title())
            }
        }
    }
    
    /// Example: Allow additional hostnames
    fun allowAdditionalHostnames() = runBlocking {
        // Allow specific hostname
        val entries1 = ffetch("https://example.com/query-index.json")
            .allow("trusted.com")
            .follow("path", "document")
            .all()
        
        // Allow multiple hostnames
        val entries2 = ffetch("https://example.com/query-index.json")
            .allow(listOf("trusted.com", "api.example.com"))
            .follow("path", "document")
            .all()
        
        // Allow all hostnames (use with caution)
        val entries3 = ffetch("https://example.com/query-index.json")
            .allow("*")
            .follow("path", "document")
            .all()
    }
    
    /// Example: Cache configuration
    fun cacheConfiguration() = runBlocking {
        // Always fetch fresh data (bypass cache)
        val freshData = ffetch("https://example.com/api/data.json")
            .cache(FFetchCacheConfig.NoCache)
            .all()
        
        // Only use cached data (won't make network request)
        val cachedData = ffetch("https://example.com/api/data.json")
            .cache(FFetchCacheConfig.CacheOnly)
            .all()
        
        // Use cache if available, otherwise load from network
        val data = ffetch("https://example.com/api/data.json")
            .cache(FFetchCacheConfig.CacheElseLoad)
            .all()
    }
    
    /// Example: Custom configuration
    fun customConfiguration() = runBlocking {
        val customConfig = FFetchCacheConfig(
            maxAge = 3600  // Cache for 1 hour regardless of server headers
        )
        
        val data = ffetch("https://example.com/api/data.json")
            .cache(customConfig)
            .all()
    }
    
    /// Example: Backward compatibility methods
    fun backwardCompatibility() = runBlocking {
        // Legacy method - maps to .cache(FFetchCacheConfig.NoCache)
        val freshData = ffetch("https://example.com/api/data.json")
            .reloadCache()
            .all()
        
        // Legacy method with parameter
        val data = ffetch("https://example.com/api/data.json")
            .withCacheReload(false)  // Uses default cache behavior
            .all()
    }
}