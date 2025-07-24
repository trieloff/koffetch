//
// FFetchTest.kt
// KotlinFFetch
//
// Basic tests for FFetch functionality
//

package live.aem.koffetch

import live.aem.koffetch.extensions.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class FFetchTest {
    
    @Test
    fun testFFetchInitWithValidURL() = runTest {
        val ffetch = FFetch("https://example.com/query-index.json")
        assertNotNull(ffetch)
    }
    
    @Test
    fun testFFetchInitWithInvalidURL() {
        assertFailsWith<FFetchError.InvalidURL> {
            FFetch("not-a-valid-url")
        }
    }
    
    @Test
    fun testFFetchConvenienceFunction() = runTest {
        val ffetch = ffetch("https://example.com/query-index.json")
        assertNotNull(ffetch)
    }
    
    @Test
    fun testCacheConfigDefaults() {
        val config = FFetchCacheConfig.Default
        assertEquals(false, config.noCache)
        assertEquals(false, config.cacheOnly)
        assertEquals(false, config.cacheElseLoad)
    }
    
    @Test
    fun testFFetchContextDefaults() {
        val context = FFetchContext()
        assertEquals(255, context.chunkSize)
        assertEquals(false, context.cacheReload)
        assertEquals(5, context.maxConcurrency)
    }
    
    @Test
    fun testChunksConfiguration() = runTest {
        val ffetch = FFetch("https://example.com/query-index.json")
        val chunkedFFetch = ffetch.chunks(100)
        assertNotNull(chunkedFFetch)
    }
    
    @Test
    fun testSheetConfiguration() = runTest {
        val ffetch = FFetch("https://example.com/query-index.json")
        val sheetFFetch = ffetch.sheet("products")
        assertNotNull(sheetFFetch)
    }
    
    @Test
    fun testMaxConcurrencyConfiguration() = runTest {
        val ffetch = FFetch("https://example.com/query-index.json")
        val concurrentFFetch = ffetch.maxConcurrency(10)
        assertNotNull(concurrentFFetch)
    }
    
    @Test
    fun testCacheConfiguration() = runTest {
        val ffetch = FFetch("https://example.com/query-index.json")
        val cachedFFetch = ffetch.cache(FFetchCacheConfig.NoCache)
        assertNotNull(cachedFFetch)
    }
    
    @Test
    fun testReloadCacheConfiguration() = runTest {
        val ffetch = FFetch("https://example.com/query-index.json")
        val reloadFFetch = ffetch.reloadCache()
        assertNotNull(reloadFFetch)
    }
    
    @Test
    fun testAllowHostnameConfiguration() = runTest {
        val ffetch = FFetch("https://example.com/query-index.json")
        val allowedFFetch = ffetch.allow("trusted.com")
        assertNotNull(allowedFFetch)
    }
    
    @Test
    fun testAllowMultipleHostnamesConfiguration() = runTest {
        val ffetch = FFetch("https://example.com/query-index.json")
        val allowedFFetch = ffetch.allow(listOf("trusted.com", "api.example.com"))
        assertNotNull(allowedFFetch)
    }
}