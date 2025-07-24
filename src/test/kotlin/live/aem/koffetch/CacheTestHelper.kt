//
// CacheTestHelper.kt
// KotlinFFetch
//
// Helper utilities for testing cache behavior and mock implementations
//

package live.aem.koffetch.mock

import live.aem.koffetch.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.coroutines.CoroutineContext

/**
 * Mock cache implementation for testing cache behavior
 */
class MockCache {
    private val cache = mutableMapOf<String, CacheEntry>()
    private val accessLog = mutableListOf<CacheAccess>()
    
    data class CacheEntry(
        val content: String,
        val timestamp: Long = System.currentTimeMillis(),
        val maxAge: Long? = null,
        val headers: Map<String, String> = emptyMap()
    ) {
        fun isExpired(currentTime: Long = System.currentTimeMillis()): Boolean {
            return maxAge?.let { age ->
                (currentTime - timestamp) > (age * 1000)
            } ?: false
        }
    }
    
    data class CacheAccess(
        val url: String,
        val operation: CacheOperation,
        val timestamp: Long = System.currentTimeMillis(),
        val hit: Boolean = false
    )
    
    enum class CacheOperation {
        GET, PUT, CLEAR, INVALIDATE
    }
    
    fun get(url: String): CacheEntry? {
        val entry = cache[url]
        val hit = entry != null && !entry.isExpired()
        accessLog.add(CacheAccess(url, CacheOperation.GET, hit = hit))
        return if (hit) entry else null
    }
    
    fun put(url: String, content: String, maxAge: Long? = null, headers: Map<String, String> = emptyMap()) {
        val entry = CacheEntry(content, maxAge = maxAge, headers = headers)
        cache[url] = entry
        accessLog.add(CacheAccess(url, CacheOperation.PUT))
    }
    
    fun clear() {
        cache.clear()
        accessLog.add(CacheAccess("*", CacheOperation.CLEAR))
    }
    
    fun invalidate(url: String) {
        cache.remove(url)
        accessLog.add(CacheAccess(url, CacheOperation.INVALIDATE))
    }
    
    fun size(): Int = cache.size
    
    fun getAccessLog(): List<CacheAccess> = accessLog.toList()
    
    fun getCacheHitRatio(): Double {
        val gets = accessLog.filter { it.operation == CacheOperation.GET }
        if (gets.isEmpty()) return 0.0
        val hits = gets.count { it.hit }
        return hits.toDouble() / gets.size
    }
    
    fun containsUrl(url: String): Boolean = cache.containsKey(url)
    
    fun getEntry(url: String): CacheEntry? = cache[url]
}

/**
 * HTTP client that integrates with MockCache for testing
 */
class CacheAwareTestHTTPClient(
    private val mockCache: MockCache = MockCache(),
    private val networkResponses: Map<String, String> = mapOf(),
    private val networkDelay: Long = 0,
    private val simulateNetworkErrors: Set<String> = setOf()
) : FFetchHTTPClient {
    
    var networkRequestCount = 0
        private set
    
    override suspend fun fetch(url: String, cacheConfig: FFetchCacheConfig): Pair<String, HttpResponse> {
        // Handle cache-only mode
        if (cacheConfig.cacheOnly) {
            val cachedEntry = mockCache.get(url)
            if (cachedEntry != null) {
                return createResponse(cachedEntry.content, HttpStatusCode.OK)
            } else {
                throw FFetchError.NetworkError(Exception("Cache-only mode: no cached response found"))
            }
        }
        
        // Handle no-cache mode
        if (cacheConfig.noCache) {
            return fetchFromNetwork(url)
        }
        
        // Handle cache-else-load mode
        if (cacheConfig.cacheElseLoad) {
            val cachedEntry = mockCache.get(url)
            if (cachedEntry != null) {
                return createResponse(cachedEntry.content, HttpStatusCode.OK)
            }
            // Fall through to network request
        }
        
        // Default behavior: check cache first, then network
        val cachedEntry = mockCache.get(url)
        if (cachedEntry != null && !shouldIgnoreCache(cacheConfig)) {
            return createResponse(cachedEntry.content, HttpStatusCode.OK)
        }
        
        // Fetch from network and cache the result
        val (content, response) = fetchFromNetwork(url)
        
        // Cache the response unless no-cache is specified
        if (!cacheConfig.noCache) {
            mockCache.put(url, content, cacheConfig.maxAge)
        }
        
        return Pair(content, response)
    }
    
    private fun shouldIgnoreCache(cacheConfig: FFetchCacheConfig): Boolean {
        return cacheConfig.ignoreServerCacheControl || cacheConfig.noCache
    }
    
    private suspend fun fetchFromNetwork(url: String): Pair<String, HttpResponse> {
        networkRequestCount++
        
        if (simulateNetworkErrors.contains(url)) {
            throw FFetchError.NetworkError(Exception("Simulated network error for $url"))
        }
        
        if (networkDelay > 0) {
            kotlinx.coroutines.delay(networkDelay)
        }
        
        val content = networkResponses[url] ?: "Default response for $url"
        return createResponse(content, HttpStatusCode.OK)
    }
    
    private fun createResponse(content: String, statusCode: HttpStatusCode): Pair<String, HttpResponse> {
        @OptIn(InternalAPI::class)
        val mockResponse = object : HttpResponse() {
            override val coroutineContext: CoroutineContext = Job()
            override val call: io.ktor.client.call.HttpClientCall
                get() = throw NotImplementedError()
            override val content: io.ktor.utils.io.ByteReadChannel
                get() = throw NotImplementedError()
            override val headers: Headers
                get() = Headers.Empty
            override val requestTime: io.ktor.util.date.GMTDate
                get() = throw NotImplementedError()
            override val responseTime: io.ktor.util.date.GMTDate
                get() = throw NotImplementedError()
            override val status: HttpStatusCode
                get() = statusCode
            override val version: HttpProtocolVersion
                get() = HttpProtocolVersion.HTTP_1_1
        }
        
        return Pair(content, mockResponse)
    }
    
    fun getCache(): MockCache = mockCache
    
    fun resetNetworkRequestCount() {
        networkRequestCount = 0
    }
}

/**
 * Test utilities for cache behavior verification
 */
object CacheTestUtils {
    
    /**
     * Create a test scenario with multiple URLs and cache configurations
     */
    fun createTestScenario(
        urls: List<String>,
        responses: Map<String, String> = mapOf(),
        cacheConfig: FFetchCacheConfig = FFetchCacheConfig.Default
    ): TestScenario {
        val networkResponses = urls.associateWith { url ->
            responses[url] ?: "Response for $url"
        }
        
        val client = CacheAwareTestHTTPClient(
            networkResponses = networkResponses
        )
        
        return TestScenario(urls, client, cacheConfig)
    }
    
    /**
     * Verify cache hit/miss behavior
     */
    suspend fun verifyCacheBehavior(
        client: CacheAwareTestHTTPClient,
        url: String,
        cacheConfig: FFetchCacheConfig,
        expectedNetworkRequests: Int,
        expectedCacheHits: Int = 0
    ) {
        val initialNetworkCount = client.networkRequestCount
        val initialCacheSize = client.getCache().size()
        
        // Make the request
        client.fetch(url, cacheConfig)
        
        val finalNetworkCount = client.networkRequestCount
        val finalCacheSize = client.getCache().size()
        
        val actualNetworkRequests = finalNetworkCount - initialNetworkCount
        val actualCacheGrowth = finalCacheSize - initialCacheSize
        
        assert(actualNetworkRequests == expectedNetworkRequests) {
            "Expected $expectedNetworkRequests network requests, but got $actualNetworkRequests"
        }
        
        if (expectedCacheHits > 0) {
            val cacheHitRatio = client.getCache().getCacheHitRatio()
            assert(cacheHitRatio > 0) {
                "Expected cache hits, but cache hit ratio is $cacheHitRatio"
            }
        }
    }
    
    /**
     * Test repeated requests to verify caching behavior
     */
    suspend fun testRepeatedRequests(
        client: CacheAwareTestHTTPClient,
        url: String,
        cacheConfig: FFetchCacheConfig,
        repetitions: Int = 3
    ): CacheTestResult {
        val initialNetworkCount = client.networkRequestCount
        
        repeat(repetitions) {
            client.fetch(url, cacheConfig)
        }
        
        val finalNetworkCount = client.networkRequestCount
        val networkRequests = finalNetworkCount - initialNetworkCount
        val cacheHitRatio = client.getCache().getCacheHitRatio()
        
        return CacheTestResult(
            repetitions = repetitions,
            networkRequests = networkRequests,
            cacheHitRatio = cacheHitRatio,
            cacheSize = client.getCache().size()
        )
    }
}

data class TestScenario(
    val urls: List<String>,
    val client: CacheAwareTestHTTPClient,
    val cacheConfig: FFetchCacheConfig
)

data class CacheTestResult(
    val repetitions: Int,
    val networkRequests: Int,
    val cacheHitRatio: Double,
    val cacheSize: Int
) {
    val cacheHits: Int get() = (repetitions * cacheHitRatio).toInt()
    val cacheMisses: Int get() = repetitions - cacheHits
    val effectiveCacheUtilization: Double get() = if (repetitions > 1) cacheHits.toDouble() / (repetitions - 1) else 0.0
}

/**
 * Builder for creating complex cache test scenarios
 */
class CacheTestBuilder {
    private val urls = mutableListOf<String>()
    private val responses = mutableMapOf<String, String>()
    private val errorUrls = mutableSetOf<String>()
    private var networkDelay = 0L
    private var cacheConfig = FFetchCacheConfig.Default
    
    fun withUrl(url: String, response: String? = null): CacheTestBuilder {
        urls.add(url)
        response?.let { responses[url] = it }
        return this
    }
    
    fun withUrls(vararg urlPairs: Pair<String, String>): CacheTestBuilder {
        urlPairs.forEach { (url, response) ->
            urls.add(url)
            responses[url] = response
        }
        return this
    }
    
    fun withNetworkError(url: String): CacheTestBuilder {
        errorUrls.add(url)
        return this
    }
    
    fun withNetworkDelay(delay: Long): CacheTestBuilder {
        networkDelay = delay
        return this
    }
    
    fun withCacheConfig(config: FFetchCacheConfig): CacheTestBuilder {
        cacheConfig = config
        return this
    }
    
    fun build(): TestScenario {
        val client = CacheAwareTestHTTPClient(
            networkResponses = responses.toMap(),
            networkDelay = networkDelay,
            simulateNetworkErrors = errorUrls.toSet()
        )
        
        return TestScenario(urls.toList(), client, cacheConfig)
    }
}