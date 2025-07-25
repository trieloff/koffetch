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

package live.aem.koffetch.api

import kotlinx.coroutines.runBlocking
import live.aem.koffetch.FFetch
import live.aem.koffetch.FFetchCacheConfig
import live.aem.koffetch.FFetchContext
import live.aem.koffetch.FFetchEntry
import live.aem.koffetch.FFetchError
import live.aem.koffetch.FFetchHTMLParser
import live.aem.koffetch.FFetchHTTPClient
import live.aem.koffetch.FFetchPredicate
import live.aem.koffetch.FFetchResponse
import live.aem.koffetch.FFetchTransform
import live.aem.koffetch.cache
import live.aem.koffetch.chunks
import live.aem.koffetch.ffetch
import live.aem.koffetch.maxConcurrency
import live.aem.koffetch.mock.MockFFetchHTTPClient
import live.aem.koffetch.mock.MockHTMLParser
import live.aem.koffetch.reloadCache
import live.aem.koffetch.sheet
import live.aem.koffetch.withCacheReload
import live.aem.koffetch.withHTMLParser
import live.aem.koffetch.withHTTPClient
import live.aem.koffetch.withMaxConcurrency
import org.junit.jupiter.api.Test
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class APICompatibilityTest {
    @Test
    fun `test all public API methods maintain expected signatures`() {
        val url = "https://example.com/api/data.json"

        // Test constructor signatures
        val ffetch1 = FFetch(url)
        assertNotNull(ffetch1)

        val ffetch2 = FFetch(URL(url))
        assertNotNull(ffetch2)

        val ffetch3 = ffetch(url)
        assertNotNull(ffetch3)

        val ffetch4 = ffetch(URL(url))
        assertNotNull(ffetch4)

        // Test all methods return FFetch for chaining
        val chained =
            ffetch1
                .chunks(100)
                .sheet("testsheet")
                .maxConcurrency(3)
                .cache(FFetchCacheConfig.NoCache)
                .reloadCache()
                .withCacheReload(true)
                .withMaxConcurrency(2)

        assertNotNull(chained)
        assertTrue(chained is FFetch)
    }

    @Test
    fun `test fluent API chaining with various combinations`() {
        val url = "https://example.com/api/data.json"

        // Test basic chaining
        val chain1 =
            ffetch(url)
                .chunks(50)
                .cache(FFetchCacheConfig.CacheOnly)

        assertNotNull(chain1)
        assertEquals(50, chain1.context.chunkSize)
        assertEquals(FFetchCacheConfig.CacheOnly, chain1.context.cacheConfig)

        // Test extended chaining
        val chain2 =
            ffetch(url)
                .sheet("data")
                .maxConcurrency(10)
                .withCacheReload(false)
                .chunks(25)

        assertNotNull(chain2)
        assertEquals("data", chain2.context.sheetName)
        assertEquals(10, chain2.context.maxConcurrency)
        assertEquals(25, chain2.context.chunkSize)

        // Test chaining creates new instances
        val original = ffetch(url)
        val modified = original.chunks(100)

        assertNotSame(original, modified)
        assertEquals(255, original.context.chunkSize) // default
        assertEquals(100, modified.context.chunkSize)
    }

    @Test
    fun `test extension function visibility and accessibility`() {
        val url = "https://example.com/api/data.json"
        val instance = ffetch(url)

        // Test that all extension functions are accessible
        assertNotNull(instance.chunks(100))
        assertNotNull(instance.sheet("test"))
        assertNotNull(instance.maxConcurrency(5))
        assertNotNull(instance.cache(FFetchCacheConfig.Default))
        assertNotNull(instance.reloadCache())
        assertNotNull(instance.withCacheReload(true))
        assertNotNull(instance.withMaxConcurrency(3))
        assertNotNull(instance.withHTTPClient(MockFFetchHTTPClient()))
        assertNotNull(instance.withHTMLParser(MockHTMLParser()))

        // Test asFlow is accessible
        assertNotNull(instance.asFlow())
    }

    @Test
    fun `test sealed class behavior and pattern matching`() {
        // Test FFetchError sealed class
        val invalidURLError = FFetchError.InvalidURL("invalid-url")
        val networkError = FFetchError.NetworkError(RuntimeException("Network issue"))
        val decodingError = FFetchError.DecodingError(RuntimeException("JSON parsing issue"))
        val invalidResponse = FFetchError.InvalidResponse
        val documentNotFound = FFetchError.DocumentNotFound
        val operationFailed = FFetchError.OperationFailed("Custom failure")

        // Test pattern matching works
        val errorMessages =
            listOf(
                invalidURLError,
                networkError,
                decodingError,
                invalidResponse,
                documentNotFound,
                operationFailed,
            ).map { error ->
                when (error) {
                    is FFetchError.InvalidURL -> "Invalid URL: ${error.message}"
                    is FFetchError.NetworkError -> "Network: ${error.message}"
                    is FFetchError.DecodingError -> "Decoding: ${error.message}"
                    is FFetchError.InvalidResponse -> "Invalid response"
                    is FFetchError.DocumentNotFound -> "Document not found"
                    is FFetchError.OperationFailed -> "Operation failed: ${error.message}"
                }
            }

        assertEquals(6, errorMessages.size)
        assertTrue(errorMessages[0].contains("Invalid URL"))
        assertTrue(errorMessages[1].contains("Network"))
        assertTrue(errorMessages[2].contains("Decoding"))
        assertEquals("Invalid response", errorMessages[3])
        assertEquals("Document not found", errorMessages[4])
        assertTrue(errorMessages[5].contains("Operation failed"))
    }

    @Test
    fun `test default parameter behavior`() {
        val url = "https://example.com/api/data.json"

        // Test FFetchContext defaults
        val context = FFetchContext()
        assertEquals(255, context.chunkSize)
        assertEquals(false, context.cacheReload)
        assertEquals(FFetchCacheConfig.Default, context.cacheConfig)
        assertEquals(null, context.sheetName)
        assertEquals(5, context.maxConcurrency)
        assertTrue(context.allowedHosts.isEmpty())

        // Test FFetchCacheConfig defaults
        val cacheConfig = FFetchCacheConfig()
        assertEquals(false, cacheConfig.noCache)
        assertEquals(false, cacheConfig.cacheOnly)
        assertEquals(false, cacheConfig.cacheElseLoad)
        assertEquals(null, cacheConfig.maxAge)
        assertEquals(false, cacheConfig.ignoreServerCacheControl)

        // Test that default parameters work in methods
        val instance = ffetch(url)
        val withCache = instance.cache(FFetchCacheConfig())
        assertEquals(FFetchCacheConfig.Default, withCache.context.cacheConfig)
    }

    @Test
    fun `test companion object methods and constants`() {
        // Test FFetchCacheConfig companion object
        assertNotNull(FFetchCacheConfig.Default)
        assertNotNull(FFetchCacheConfig.NoCache)
        assertNotNull(FFetchCacheConfig.CacheOnly)
        assertNotNull(FFetchCacheConfig.CacheElseLoad)

        // Verify companion object constants
        assertEquals(true, FFetchCacheConfig.NoCache.noCache)
        assertEquals(true, FFetchCacheConfig.CacheOnly.cacheOnly)
        assertEquals(true, FFetchCacheConfig.CacheElseLoad.cacheElseLoad)

        // Test that they're distinct instances
        assertNotSame(FFetchCacheConfig.Default, FFetchCacheConfig.NoCache)
        assertNotSame(FFetchCacheConfig.NoCache, FFetchCacheConfig.CacheOnly)
    }

    @Test
    fun `test typealias compatibility`() {
        // Test FFetchEntry typealias
        val entry: FFetchEntry =
            mapOf(
                "title" to "Test Article",
                "author" to "John Doe",
            )

        assertTrue(entry is Map<String, Any?>)
        assertEquals("Test Article", entry["title"])
        assertEquals("John Doe", entry["author"])

        // Test FFetchTransform typealias
        val transform: FFetchTransform<String, Int> = { input ->
            input.length
        }

        runBlocking {
            val result = transform("hello")
            assertEquals(5, result)
        }

        // Test FFetchPredicate typealias
        val predicate: FFetchPredicate<String> = { input ->
            input.length > 3
        }

        runBlocking {
            assertTrue(predicate("hello"))
            assertTrue(!predicate("hi"))
        }
    }

    @Test
    fun `test interface contract compliance`() {
        // Test FFetchHTTPClient interface
        val httpClient = MockFFetchHTTPClient()
        assertTrue(httpClient is FFetchHTTPClient)

        // Test FFetchHTMLParser interface
        val htmlParser = MockHTMLParser()
        assertTrue(htmlParser is FFetchHTMLParser)

        // Test that interfaces can be used in API
        val instance =
            ffetch("https://example.com/test")
                .withHTTPClient(httpClient)
                .withHTMLParser(htmlParser)

        assertEquals(httpClient, instance.context.httpClient)
        assertEquals(htmlParser, instance.context.htmlParser)
    }

    @Test
    fun `test data class behavior and immutability`() {
        // Test FFetchResponse data class
        val response =
            FFetchResponse(
                total = 100,
                offset = 0,
                limit = 25,
                data = emptyList(),
            )

        // Test copy functionality
        val copied = response.copy(offset = 25)
        assertEquals(100, copied.total)
        assertEquals(25, copied.offset)
        assertEquals(25, copied.limit)
        assertEquals(0, response.offset) // original unchanged

        // Test FFetchContext data class
        val context = FFetchContext()
        val modifiedContext = context.copy(chunkSize = 100)
        assertEquals(255, context.chunkSize) // original unchanged
        assertEquals(100, modifiedContext.chunkSize)

        // Test FFetchCacheConfig data class
        val cacheConfig = FFetchCacheConfig()
        val modifiedCacheConfig = cacheConfig.copy(noCache = true)
        assertEquals(false, cacheConfig.noCache) // original unchanged
        assertEquals(true, modifiedCacheConfig.noCache)
    }

    @Test
    fun `test backward compatibility methods`() {
        val url = "https://example.com/api/data.json"
        val instance = ffetch(url)

        // Test deprecated/compatibility methods still work
        val withCacheReload = instance.withCacheReload(true)
        assertTrue(withCacheReload.context.cacheReload)
        assertEquals(FFetchCacheConfig.NoCache, withCacheReload.context.cacheConfig)

        val withoutCacheReload = instance.withCacheReload(false)
        assertEquals(false, withoutCacheReload.context.cacheReload)
        assertEquals(FFetchCacheConfig.Default, withoutCacheReload.context.cacheConfig)

        val withMaxConcurrency = instance.withMaxConcurrency(10)
        assertEquals(10, withMaxConcurrency.context.maxConcurrency)
    }

    @Test
    fun `test API method parameter validation`() {
        val url = "https://example.com/api/data.json"
        val instance = ffetch(url)

        // Test that methods accept valid parameters
        assertNotNull(instance.chunks(1))
        assertNotNull(instance.chunks(1000))
        assertNotNull(instance.maxConcurrency(1))
        assertNotNull(instance.maxConcurrency(100))
        assertNotNull(instance.sheet(""))
        assertNotNull(instance.sheet("valid-sheet-name"))

        // Test cache configurations
        assertNotNull(instance.cache(FFetchCacheConfig.Default))
        assertNotNull(instance.cache(FFetchCacheConfig.NoCache))
        assertNotNull(instance.cache(FFetchCacheConfig.CacheOnly))
        assertNotNull(instance.cache(FFetchCacheConfig.CacheElseLoad))

        // Test custom cache config
        val customCache =
            FFetchCacheConfig(
                noCache = true,
                maxAge = 3600,
                ignoreServerCacheControl = true,
            )
        assertNotNull(instance.cache(customCache))
    }
}
