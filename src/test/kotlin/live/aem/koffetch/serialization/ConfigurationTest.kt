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

package live.aem.koffetch.serialization

import live.aem.koffetch.FFetchCacheConfig
import live.aem.koffetch.FFetchContext
import live.aem.koffetch.mock.MockFFetchHTTPClient
import live.aem.koffetch.mock.MockHTMLParser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigurationTest {
    @Test
    fun `test FFetchContext data class behavior and copying`() {
        val original =
            FFetchContext(
                chunkSize = 100,
                cacheReload = true,
                cacheConfig = FFetchCacheConfig.NoCache,
                sheetName = "test-sheet",
                maxConcurrency = 10,
            )

        // Test copy with modifications
        val modified =
            original.copy(
                chunkSize = 200,
                sheetName = "modified-sheet",
            )

        // Verify original is unchanged
        assertEquals(100, original.chunkSize)
        assertEquals("test-sheet", original.sheetName)
        assertTrue(original.cacheReload)

        // Verify copy has modifications
        assertEquals(200, modified.chunkSize)
        assertEquals("modified-sheet", modified.sheetName)
        assertEquals(10, modified.maxConcurrency) // unchanged fields preserved
        assertTrue(modified.cacheReload) // unchanged fields preserved
        assertEquals(FFetchCacheConfig.NoCache, modified.cacheConfig)

        // Verify they are different instances
        assertNotSame(original, modified)
    }

    @Test
    fun `test FFetchCacheConfig data class behavior`() {
        val original =
            FFetchCacheConfig(
                noCache = true,
                maxAge = 3600,
                ignoreServerCacheControl = true,
            )

        // Test copy with modifications
        val modified =
            original.copy(
                noCache = false,
                cacheOnly = true,
            )

        // Verify original is unchanged
        assertTrue(original.noCache)
        assertFalse(original.cacheOnly)
        assertEquals(3600, original.maxAge)
        assertTrue(original.ignoreServerCacheControl)

        // Verify copy has modifications
        assertFalse(modified.noCache)
        assertTrue(modified.cacheOnly)
        assertEquals(3600, modified.maxAge) // unchanged fields preserved
        assertTrue(modified.ignoreServerCacheControl) // unchanged fields preserved

        // Verify they are different instances
        assertNotSame(original, modified)
    }

    @Test
    fun `test configuration immutability and defaults`() {
        // Test default FFetchContext
        val defaultContext = FFetchContext()
        assertEquals(255, defaultContext.chunkSize)
        assertFalse(defaultContext.cacheReload)
        assertEquals(FFetchCacheConfig.Default, defaultContext.cacheConfig)
        assertNull(defaultContext.sheetName)
        assertEquals(5, defaultContext.maxConcurrency)
        assertTrue(defaultContext.allowedHosts.isEmpty())

        // Test default FFetchCacheConfig
        val defaultCacheConfig = FFetchCacheConfig()
        assertFalse(defaultCacheConfig.noCache)
        assertFalse(defaultCacheConfig.cacheOnly)
        assertFalse(defaultCacheConfig.cacheElseLoad)
        assertNull(defaultCacheConfig.maxAge)
        assertFalse(defaultCacheConfig.ignoreServerCacheControl)

        // Test immutability by attempting to modify
        val contextCopy = defaultContext.copy(chunkSize = 500)
        assertEquals(255, defaultContext.chunkSize) // original unchanged
        assertEquals(500, contextCopy.chunkSize)
    }

    @Test
    fun `test configuration validation and edge cases`() {
        // Test extreme values
        val extremeContext =
            FFetchContext(
                chunkSize = 1,
                maxConcurrency = 1,
            )
        assertEquals(1, extremeContext.chunkSize)
        assertEquals(1, extremeContext.maxConcurrency)

        val largeContext =
            FFetchContext(
                chunkSize = 10000,
                maxConcurrency = 1000,
            )
        assertEquals(10000, largeContext.chunkSize)
        assertEquals(1000, largeContext.maxConcurrency)

        // Test edge case cache configurations
        val edgeCacheConfig =
            FFetchCacheConfig(
                noCache = true,
                // contradictory but should be allowed
                cacheOnly = true,
                maxAge = 0,
            )
        assertTrue(edgeCacheConfig.noCache)
        assertTrue(edgeCacheConfig.cacheOnly)
        assertEquals(0, edgeCacheConfig.maxAge)

        val negativeCacheConfig =
            FFetchCacheConfig(
                maxAge = -1,
            )
        assertEquals(-1, negativeCacheConfig.maxAge)
    }

    @Test
    fun `test copy method behavior with various parameters`() {
        val original = FFetchContext()

        // Test partial copies
        val chunkSizeOnly = original.copy(chunkSize = 50)
        assertEquals(50, chunkSizeOnly.chunkSize)
        assertEquals(original.maxConcurrency, chunkSizeOnly.maxConcurrency)
        assertEquals(original.sheetName, chunkSizeOnly.sheetName)

        val multipleChanges =
            original.copy(
                chunkSize = 75,
                cacheReload = true,
                sheetName = "multi-test",
                maxConcurrency = 15,
            )
        assertEquals(75, multipleChanges.chunkSize)
        assertTrue(multipleChanges.cacheReload)
        assertEquals("multi-test", multipleChanges.sheetName)
        assertEquals(15, multipleChanges.maxConcurrency)

        // Test copying complex objects
        val customHttpClient = MockFFetchHTTPClient()
        val customHtmlParser = MockHTMLParser()
        val customCacheConfig = FFetchCacheConfig.CacheOnly

        val complexCopy =
            original.copy(
                httpClient = customHttpClient,
                htmlParser = customHtmlParser,
                cacheConfig = customCacheConfig,
            )

        assertEquals(customHttpClient, complexCopy.httpClient)
        assertEquals(customHtmlParser, complexCopy.htmlParser)
        assertEquals(customCacheConfig, complexCopy.cacheConfig)
    }

    @Test
    fun `test FFetchCacheConfig companion object constants`() {
        // Test Default configuration
        val defaultConfig = FFetchCacheConfig.Default
        assertFalse(defaultConfig.noCache)
        assertFalse(defaultConfig.cacheOnly)
        assertFalse(defaultConfig.cacheElseLoad)
        assertNull(defaultConfig.maxAge)
        assertFalse(defaultConfig.ignoreServerCacheControl)

        // Test NoCache configuration
        val noCacheConfig = FFetchCacheConfig.NoCache
        assertTrue(noCacheConfig.noCache)
        assertFalse(noCacheConfig.cacheOnly)
        assertFalse(noCacheConfig.cacheElseLoad)
        assertNull(noCacheConfig.maxAge)
        assertFalse(noCacheConfig.ignoreServerCacheControl)

        // Test CacheOnly configuration
        val cacheOnlyConfig = FFetchCacheConfig.CacheOnly
        assertFalse(cacheOnlyConfig.noCache)
        assertTrue(cacheOnlyConfig.cacheOnly)
        assertFalse(cacheOnlyConfig.cacheElseLoad)
        assertNull(cacheOnlyConfig.maxAge)
        assertFalse(cacheOnlyConfig.ignoreServerCacheControl)

        // Test CacheElseLoad configuration
        val cacheElseLoadConfig = FFetchCacheConfig.CacheElseLoad
        assertFalse(cacheElseLoadConfig.noCache)
        assertFalse(cacheElseLoadConfig.cacheOnly)
        assertTrue(cacheElseLoadConfig.cacheElseLoad)
        assertNull(cacheElseLoadConfig.maxAge)
        assertFalse(cacheElseLoadConfig.ignoreServerCacheControl)

        // Test that they are distinct instances
        assertNotSame(defaultConfig, noCacheConfig)
        assertNotSame(noCacheConfig, cacheOnlyConfig)
        assertNotSame(cacheOnlyConfig, cacheElseLoadConfig)
    }

    @Test
    fun `test configuration equality and hashCode`() {
        val config1 =
            FFetchCacheConfig(
                noCache = true,
                maxAge = 3600,
            )

        val config2 =
            FFetchCacheConfig(
                noCache = true,
                maxAge = 3600,
            )

        val config3 =
            FFetchCacheConfig(
                noCache = false,
                maxAge = 3600,
            )

        // Test equality
        assertEquals(config1, config2)
        assertEquals(config1.hashCode(), config2.hashCode())
        assertTrue(config1 != config3)

        // Test with FFetchContext
        val context1 = FFetchContext(chunkSize = 100, maxConcurrency = 5)
        val context2 = FFetchContext(chunkSize = 100, maxConcurrency = 5)
        val context3 = FFetchContext(chunkSize = 200, maxConcurrency = 5)

        assertEquals(context1.chunkSize, context2.chunkSize)
        assertEquals(context1.maxConcurrency, context2.maxConcurrency)
        assertTrue(context1.chunkSize != context3.chunkSize)
    }

    @Test
    fun `test mutable collections in FFetchContext`() {
        val context = FFetchContext()

        // Test that allowedHosts is mutable
        assertTrue(context.allowedHosts.isEmpty())

        context.allowedHosts.add("example.com")
        context.allowedHosts.add("api.example.com")

        assertEquals(2, context.allowedHosts.size)
        assertTrue(context.allowedHosts.contains("example.com"))
        assertTrue(context.allowedHosts.contains("api.example.com"))

        // Test copying preserves mutable collections
        val copied = context.copy(chunkSize = 100)
        assertEquals(2, copied.allowedHosts.size)
        assertTrue(copied.allowedHosts.contains("example.com"))

        // Test that modifications to copy don't affect original
        copied.allowedHosts.add("secure.example.com")
        assertEquals(2, context.allowedHosts.size) // original unchanged
        assertEquals(3, copied.allowedHosts.size)
    }

    @Test
    fun `test configuration with custom implementations`() {
        val customHttpClient = MockFFetchHTTPClient()
        val customHtmlParser = MockHTMLParser()

        val context =
            FFetchContext(
                httpClient = customHttpClient,
                htmlParser = customHtmlParser,
                chunkSize = 150,
            )

        assertEquals(customHttpClient, context.httpClient)
        assertEquals(customHtmlParser, context.htmlParser)
        assertEquals(150, context.chunkSize)

        // Test copying with custom implementations
        val copied = context.copy(chunkSize = 300)
        assertEquals(customHttpClient, copied.httpClient) // preserved
        assertEquals(customHtmlParser, copied.htmlParser) // preserved
        assertEquals(300, copied.chunkSize) // modified
    }

    @Test
    fun `test backward compatibility fields`() {
        val context = FFetchContext(cacheReload = true)
        assertTrue(context.cacheReload)

        // Test that cacheReload and cacheConfig can be used together
        val withBothCacheSettings =
            context.copy(
                cacheReload = false,
                cacheConfig = FFetchCacheConfig.NoCache,
            )

        assertFalse(withBothCacheSettings.cacheReload)
        assertEquals(FFetchCacheConfig.NoCache, withBothCacheSettings.cacheConfig)
    }
}
