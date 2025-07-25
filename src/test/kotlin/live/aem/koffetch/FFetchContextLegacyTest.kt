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
import live.aem.koffetch.mock.MockFFetchHTTPClient
import live.aem.koffetch.mock.MockHTMLParser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FFetchContextLegacyTest {

    @Test
    fun `test FFetchContext LegacyParams default values`() {
        val legacyParams = FFetchContext.LegacyParams()
        
        assertEquals(FFetchContextBuilder.DEFAULT_CHUNK_SIZE, legacyParams.chunkSize)
        assertFalse(legacyParams.cacheReload)
        assertEquals(FFetchCacheConfig.Default, legacyParams.cacheConfig)
        assertNull(legacyParams.sheetName)
        assertNotNull(legacyParams.httpClient)
        assertNotNull(legacyParams.htmlParser)
        assertNull(legacyParams.total)
        assertEquals(FFetchContextBuilder.DEFAULT_MAX_CONCURRENCY, legacyParams.maxConcurrency)
        assertTrue(legacyParams.allowedHosts.isEmpty())
    }

    @Test
    fun `test FFetchContext LegacyParams with custom values`() {
        val customClient = MockFFetchHTTPClient()
        val customParser = MockHTMLParser()
        val customHosts = mutableSetOf("legacy1.com", "legacy2.com")
        
        val legacyParams = FFetchContext.LegacyParams(
            chunkSize = 200,
            cacheReload = true,
            cacheConfig = FFetchCacheConfig.NoCache,
            sheetName = "legacy-sheet",
            httpClient = customClient,
            htmlParser = customParser,
            total = 1500,
            maxConcurrency = 12,
            allowedHosts = customHosts
        )
        
        assertEquals(200, legacyParams.chunkSize)
        assertTrue(legacyParams.cacheReload)
        assertEquals(FFetchCacheConfig.NoCache, legacyParams.cacheConfig)
        assertEquals("legacy-sheet", legacyParams.sheetName)
        assertEquals(customClient, legacyParams.httpClient)
        assertEquals(customParser, legacyParams.htmlParser)
        assertEquals(1500, legacyParams.total)
        assertEquals(12, legacyParams.maxConcurrency)
        assertEquals(customHosts, legacyParams.allowedHosts)
    }

    @Test
    fun `test FFetchContext LegacyParams data class behavior`() {
        val original = FFetchContext.LegacyParams(chunkSize = 100, cacheReload = true)
        
        val copied = original.copy(chunkSize = 300, sheetName = "copied")
        
        // Verify original is unchanged
        assertEquals(100, original.chunkSize)
        assertTrue(original.cacheReload)
        assertNull(original.sheetName)
        
        // Verify copy has modifications
        assertEquals(300, copied.chunkSize)
        assertTrue(copied.cacheReload) // preserved
        assertEquals("copied", copied.sheetName)
        
        assertNotSame(original, copied)
    }

    @Test
    fun `test FFetchContext LegacyParams equality and hashCode`() {
        val customClient = MockFFetchHTTPClient()
        val customParser = MockHTMLParser()
        
        val params1 = FFetchContext.LegacyParams(
            chunkSize = 150, 
            maxConcurrency = 8,
            httpClient = customClient,
            htmlParser = customParser
        )
        val params2 = FFetchContext.LegacyParams(
            chunkSize = 150, 
            maxConcurrency = 8,
            httpClient = customClient,
            htmlParser = customParser
        )
        val params3 = FFetchContext.LegacyParams(
            chunkSize = 250, 
            maxConcurrency = 8,
            httpClient = customClient,
            htmlParser = customParser
        )
        
        assertEquals(params1, params2)
        assertEquals(params1.hashCode(), params2.hashCode())
        assertTrue(params1 != params3)
        assertTrue(params1.hashCode() != params3.hashCode())
    }

    @Test
    fun `test FFetchContext LegacyParams with all cache configurations`() {
        val defaultParams = FFetchContext.LegacyParams(cacheConfig = FFetchCacheConfig.Default)
        assertEquals(FFetchCacheConfig.Default, defaultParams.cacheConfig)
        
        val noCacheParams = FFetchContext.LegacyParams(cacheConfig = FFetchCacheConfig.NoCache)
        assertEquals(FFetchCacheConfig.NoCache, noCacheParams.cacheConfig)
        
        val cacheOnlyParams = FFetchContext.LegacyParams(cacheConfig = FFetchCacheConfig.CacheOnly)
        assertEquals(FFetchCacheConfig.CacheOnly, cacheOnlyParams.cacheConfig)
        
        val cacheElseLoadParams = FFetchContext.LegacyParams(cacheConfig = FFetchCacheConfig.CacheElseLoad)
        assertEquals(FFetchCacheConfig.CacheElseLoad, cacheElseLoadParams.cacheConfig)
        
        val customCacheParams = FFetchContext.LegacyParams(
            cacheConfig = FFetchCacheConfig(
                noCache = true,
                cacheOnly = false,
                maxAge = 7200,
                ignoreServerCacheControl = true
            )
        )
        assertTrue(customCacheParams.cacheConfig.noCache)
        assertFalse(customCacheParams.cacheConfig.cacheOnly)
        assertEquals(7200, customCacheParams.cacheConfig.maxAge)
        assertTrue(customCacheParams.cacheConfig.ignoreServerCacheControl)
    }

    @Test
    fun `test FFetchContext Companion create method`() {
        val customClient = MockFFetchHTTPClient()
        val customParser = MockHTMLParser()
        val customHosts = mutableSetOf("companion1.com", "companion2.com")
        
        val legacyParams = FFetchContext.LegacyParams(
            chunkSize = 333,
            cacheReload = true,
            cacheConfig = FFetchCacheConfig.CacheElseLoad,
            sheetName = "companion-test",
            httpClient = customClient,
            htmlParser = customParser,
            total = 2500,
            maxConcurrency = 18,
            allowedHosts = customHosts
        )
        
        val context = FFetchContext.create(legacyParams)
        
        assertEquals(333, context.chunkSize)
        assertTrue(context.cacheReload)
        assertEquals(FFetchCacheConfig.CacheElseLoad, context.cacheConfig)
        assertEquals("companion-test", context.sheetName)
        assertEquals(customClient, context.httpClient)
        assertEquals(customParser, context.htmlParser)
        assertEquals(2500, context.total)
        assertEquals(18, context.maxConcurrency)
        assertEquals(customHosts, context.allowedHosts)
    }

    @Test
    fun `test FFetchContext Companion create method with default LegacyParams`() {
        val defaultParams = FFetchContext.LegacyParams()
        val context = FFetchContext.create(defaultParams)
        
        assertEquals(FFetchContextBuilder.DEFAULT_CHUNK_SIZE, context.chunkSize)
        assertFalse(context.cacheReload)
        assertEquals(FFetchCacheConfig.Default, context.cacheConfig)
        assertNull(context.sheetName)
        assertTrue(context.httpClient is DefaultFFetchHTTPClient)
        assertTrue(context.htmlParser is DefaultFFetchHTMLParser)
        assertNull(context.total)
        assertEquals(FFetchContextBuilder.DEFAULT_MAX_CONCURRENCY, context.maxConcurrency)
        assertTrue(context.allowedHosts.isEmpty())
    }

    @Test
    fun `test FFetchContext Companion createLegacy method`() {
        val customClient = MockFFetchHTTPClient()
        
        val context = FFetchContext.createLegacy(
            chunkSize = 444,
            cacheReload = true,
            cacheConfig = FFetchCacheConfig.NoCache,
            sheetName = "create-legacy",
            httpClient = customClient
        )
        
        assertEquals(444, context.chunkSize)
        assertTrue(context.cacheReload)
        assertEquals(FFetchCacheConfig.NoCache, context.cacheConfig)
        assertEquals("create-legacy", context.sheetName)
        assertEquals(customClient, context.httpClient)
        
        // Verify defaults for parameters not specified
        assertTrue(context.htmlParser is DefaultFFetchHTMLParser)
        assertNull(context.total)
        assertEquals(FFetchContextBuilder.DEFAULT_MAX_CONCURRENCY, context.maxConcurrency)
        assertTrue(context.allowedHosts.isEmpty())
    }

    @Test
    fun `test FFetchContext Companion createLegacy method with all defaults`() {
        val context = FFetchContext.createLegacy()
        
        assertEquals(FFetchContextBuilder.DEFAULT_CHUNK_SIZE, context.chunkSize)
        assertFalse(context.cacheReload)
        assertEquals(FFetchCacheConfig.Default, context.cacheConfig)
        assertNull(context.sheetName)
        assertTrue(context.httpClient is DefaultFFetchHTTPClient)
        assertTrue(context.htmlParser is DefaultFFetchHTMLParser)
        assertNull(context.total)
        assertEquals(FFetchContextBuilder.DEFAULT_MAX_CONCURRENCY, context.maxConcurrency)
        assertTrue(context.allowedHosts.isEmpty())
    }

    @Test
    fun `test FFetchContext Companion createLegacy with partial parameters`() {
        val context1 = FFetchContext.createLegacy(chunkSize = 100)
        assertEquals(100, context1.chunkSize)
        assertFalse(context1.cacheReload) // default
        
        val context2 = FFetchContext.createLegacy(cacheReload = true)
        assertEquals(FFetchContextBuilder.DEFAULT_CHUNK_SIZE, context2.chunkSize) // default
        assertTrue(context2.cacheReload)
        
        val context3 = FFetchContext.createLegacy(sheetName = "partial")
        assertEquals("partial", context3.sheetName)
        assertEquals(FFetchContextBuilder.DEFAULT_CHUNK_SIZE, context3.chunkSize) // default
        assertFalse(context3.cacheReload) // default
    }

    @Test
    fun `test FFetchContext LegacyParams with edge cases`() {
        // Test zero and negative values
        val edgeParams = FFetchContext.LegacyParams(
            chunkSize = 0,
            maxConcurrency = -1,
            total = -100
        )
        assertEquals(0, edgeParams.chunkSize)
        assertEquals(-1, edgeParams.maxConcurrency)
        assertEquals(-100, edgeParams.total)
        
        // Test empty string sheet name
        val emptySheetParams = FFetchContext.LegacyParams(sheetName = "")
        assertEquals("", emptySheetParams.sheetName)
        
        // Test maximum values
        val maxParams = FFetchContext.LegacyParams(
            chunkSize = Int.MAX_VALUE,
            maxConcurrency = Int.MAX_VALUE,
            total = Int.MAX_VALUE
        )
        assertEquals(Int.MAX_VALUE, maxParams.chunkSize)
        assertEquals(Int.MAX_VALUE, maxParams.maxConcurrency)
        assertEquals(Int.MAX_VALUE, maxParams.total)
    }

    @Test
    fun `test LegacyParams with complex cache configurations`() {
        val complexCacheConfig = FFetchCacheConfig(
            noCache = true,
            cacheOnly = true, // contradictory but allowed
            cacheElseLoad = true,
            maxAge = 0,
            ignoreServerCacheControl = true
        )
        
        val params = FFetchContext.LegacyParams(cacheConfig = complexCacheConfig)
        
        assertEquals(complexCacheConfig, params.cacheConfig)
        assertTrue(params.cacheConfig.noCache)
        assertTrue(params.cacheConfig.cacheOnly)
        assertTrue(params.cacheConfig.cacheElseLoad)
        assertEquals(0, params.cacheConfig.maxAge)
        assertTrue(params.cacheConfig.ignoreServerCacheControl)
    }

    @Test
    fun `test Companion methods preserve mutable collections`() {
        val originalHosts = mutableSetOf("preserve1.com", "preserve2.com")
        val legacyParams = FFetchContext.LegacyParams(allowedHosts = originalHosts)
        
        val context = FFetchContext.create(legacyParams)
        
        // Test that the hosts are preserved but as a separate mutable set
        assertTrue(context.allowedHosts.contains("preserve1.com"))
        assertTrue(context.allowedHosts.contains("preserve2.com"))
        assertEquals(2, context.allowedHosts.size)
        
        // Test that modifying the original DOES affect the created context
        // Note: The create method shares the same mutable set reference
        originalHosts.add("new-host.com")
        assertTrue(context.allowedHosts.contains("new-host.com"))
        assertEquals(3, context.allowedHosts.size)
    }

    @Test
    fun `test round trip compatibility - LegacyParams to Context and back`() {
        val customClient = MockFFetchHTTPClient()
        val customParser = MockHTMLParser()
        val hosts = mutableSetOf("roundtrip.com")
        
        // Create LegacyParams with custom values
        val originalParams = FFetchContext.LegacyParams(
            chunkSize = 777,
            cacheReload = true,
            cacheConfig = FFetchCacheConfig.CacheOnly,
            sheetName = "roundtrip",
            httpClient = customClient,
            htmlParser = customParser,
            total = 888,
            maxConcurrency = 13,
            allowedHosts = hosts
        )
        
        // Create context from params
        val context = FFetchContext.create(originalParams)
        
        // Create new params from context values (simulating round trip)
        val roundTripParams = FFetchContext.LegacyParams(
            chunkSize = context.chunkSize,
            cacheReload = context.cacheReload,
            cacheConfig = context.cacheConfig,
            sheetName = context.sheetName,
            httpClient = context.httpClient,
            htmlParser = context.htmlParser,
            total = context.total,
            maxConcurrency = context.maxConcurrency,
            allowedHosts = context.allowedHosts.toMutableSet()
        )
        
        // Verify round trip preservation
        assertEquals(originalParams.chunkSize, roundTripParams.chunkSize)
        assertEquals(originalParams.cacheReload, roundTripParams.cacheReload)
        assertEquals(originalParams.cacheConfig, roundTripParams.cacheConfig)
        assertEquals(originalParams.sheetName, roundTripParams.sheetName)
        assertEquals(originalParams.httpClient, roundTripParams.httpClient)
        assertEquals(originalParams.htmlParser, roundTripParams.htmlParser)
        assertEquals(originalParams.total, roundTripParams.total)
        assertEquals(originalParams.maxConcurrency, roundTripParams.maxConcurrency)
        assertEquals(originalParams.allowedHosts, roundTripParams.allowedHosts)
    }
}