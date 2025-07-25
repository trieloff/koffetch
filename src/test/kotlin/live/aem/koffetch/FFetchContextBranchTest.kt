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

import live.aem.koffetch.mock.MockFFetchHTTPClient
import live.aem.koffetch.mock.MockHTMLParser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FFetchContextBranchTest {
    @Test
    fun `test FFetchContext equals method all branches`() {
        val customClient = MockFFetchHTTPClient()
        val customParser = MockHTMLParser()

        val context1 = FFetchContext(httpClient = customClient, htmlParser = customParser)
        val context2 = FFetchContext(httpClient = customClient, htmlParser = customParser)

        // Test same instance
        assertTrue(context1.equals(context1))

        // Test null comparison
        assertFalse(context1.equals(null))

        // Test different class
        assertFalse(context1.equals("not-a-context"))

        // Test equal contexts
        assertTrue(context1.equals(context2))

        // Test different chunkSize
        val diffChunkSize = context1.copy(chunkSize = 999)
        assertFalse(context1.equals(diffChunkSize))

        // Test different cacheReload
        val diffCacheReload = context1.copy(cacheReload = true)
        assertFalse(context1.equals(diffCacheReload))

        // Test different cacheConfig
        val diffCacheConfig = context1.copy(cacheConfig = FFetchCacheConfig.NoCache)
        assertFalse(context1.equals(diffCacheConfig))

        // Test different sheetName - null vs non-null
        val diffSheetName = context1.copy(sheetName = "test")
        assertFalse(context1.equals(diffSheetName))

        // Test different sheetName - both non-null
        val context1WithSheet = context1.copy(sheetName = "sheet1")
        val context2WithSheet = context1.copy(sheetName = "sheet2")
        assertFalse(context1WithSheet.equals(context2WithSheet))

        // Test different httpClient
        val diffHttpClient = context1.copy(httpClient = MockFFetchHTTPClient())
        assertFalse(context1.equals(diffHttpClient))

        // Test different htmlParser
        val diffHtmlParser = context1.copy(htmlParser = MockHTMLParser())
        assertFalse(context1.equals(diffHtmlParser))

        // Test different total - null vs non-null
        val diffTotal = context1.copy(total = 100)
        assertFalse(context1.equals(diffTotal))

        // Test different total - both non-null
        val context1WithTotal = context1.copy(total = 100)
        val context2WithTotal = context1.copy(total = 200)
        assertFalse(context1WithTotal.equals(context2WithTotal))

        // Test different maxConcurrency
        val diffMaxConcurrency = context1.copy(maxConcurrency = 999)
        assertFalse(context1.equals(diffMaxConcurrency))

        // Test different allowedHosts
        val diffAllowedHosts = context1.copy(allowedHosts = mutableSetOf("test.com"))
        assertFalse(context1.equals(diffAllowedHosts))
    }

    @Test
    fun `test FFetchContext hashCode method consistency`() {
        val customClient = MockFFetchHTTPClient()
        val customParser = MockHTMLParser()

        val context1 =
            FFetchContext(
                chunkSize = 100,
                cacheReload = true,
                cacheConfig = FFetchCacheConfig.NoCache,
                sheetName = "test",
                total = 500,
                maxConcurrency = 10,
                httpClient = customClient,
                htmlParser = customParser,
                allowedHosts = mutableSetOf("example.com"),
            )

        val context2 =
            FFetchContext(
                chunkSize = 100,
                cacheReload = true,
                cacheConfig = FFetchCacheConfig.NoCache,
                sheetName = "test",
                total = 500,
                maxConcurrency = 10,
                httpClient = customClient,
                htmlParser = customParser,
                allowedHosts = mutableSetOf("example.com"),
            )

        // Test hashCode consistency
        assertEquals(context1.hashCode(), context1.hashCode())

        // Test equal objects have equal hashCodes
        assertEquals(context1, context2)
        assertEquals(context1.hashCode(), context2.hashCode())

        // Test hashCode includes all relevant fields
        val diffContext = context1.copy(chunkSize = 200)
        assertNotEquals(context1.hashCode(), diffContext.hashCode())
    }

    @Test
    fun `test FFetchContext hashCode with null values`() {
        val contextWithNulls = FFetchContext()
        val hashCodeWithNulls = contextWithNulls.hashCode()

        // Should not throw exception with null fields
        assertNotNull(hashCodeWithNulls)

        // Test consistency with null values
        assertEquals(hashCodeWithNulls, contextWithNulls.hashCode())

        // Test changing from null to non-null affects hashCode
        val contextWithValues = contextWithNulls.copy(sheetName = "test", total = 100)
        assertNotEquals(hashCodeWithNulls, contextWithValues.hashCode())
    }

    @Test
    fun `test config object property setters update underlying objects`() {
        val context = FFetchContext()
        val originalPerfConfig = context.performanceConfig
        val originalClientConfig = context.clientConfig
        val originalRequestConfig = context.requestConfig
        val originalSecurityConfig = context.securityConfig

        // Test chunkSize setter creates new performance config
        context.chunkSize = 999
        assertNotSame(originalPerfConfig, context.performanceConfig)
        assertEquals(999, context.performanceConfig.chunkSize)
        assertEquals(originalPerfConfig.maxConcurrency, context.performanceConfig.maxConcurrency)

        // Test maxConcurrency setter creates new performance config
        context.maxConcurrency = 50
        assertEquals(999, context.performanceConfig.chunkSize) // preserved
        assertEquals(50, context.performanceConfig.maxConcurrency)

        // Test httpClient setter creates new client config
        val newClient = MockFFetchHTTPClient()
        context.httpClient = newClient
        assertNotSame(originalClientConfig, context.clientConfig)
        assertEquals(newClient, context.clientConfig.httpClient)
        assertEquals(originalClientConfig.htmlParser, context.clientConfig.htmlParser)

        // Test htmlParser setter creates new client config
        val newParser = MockHTMLParser()
        context.htmlParser = newParser
        assertEquals(newClient, context.clientConfig.httpClient) // preserved
        assertEquals(newParser, context.clientConfig.htmlParser)

        // Test sheetName setter creates new request config
        context.sheetName = "new-sheet"
        assertNotSame(originalRequestConfig, context.requestConfig)
        assertEquals("new-sheet", context.requestConfig.sheetName)
        assertEquals(originalRequestConfig.total, context.requestConfig.total)

        // Test total setter creates new request config
        context.total = 777
        assertEquals("new-sheet", context.requestConfig.sheetName) // preserved
        assertEquals(777, context.requestConfig.total)

        // Test allowedHosts setter creates new security config
        val newHosts = mutableSetOf("new.com")
        context.allowedHosts = newHosts
        assertNotSame(originalSecurityConfig, context.securityConfig)
        assertEquals(newHosts, context.securityConfig.allowedHosts)
    }

    @Test
    fun `test copyWithConfigs preserves allowedHosts correctly`() {
        val original = FFetchContext()
        original.allowedHosts.add("original.com")

        val newPerfConfig = FFetchPerformanceConfig(chunkSize = 300)
        val copied = original.copyWithConfigs(performanceConfig = newPerfConfig)

        // Test that allowedHosts is preserved but as a separate mutable set
        assertTrue(copied.allowedHosts.contains("original.com"))
        assertEquals(1, copied.allowedHosts.size)

        // Test that modifying copied allowedHosts doesn't affect original
        copied.allowedHosts.add("copied.com")
        assertFalse(original.allowedHosts.contains("copied.com"))
        assertEquals(1, original.allowedHosts.size)
    }

    @Test
    fun `test copy method preserves allowedHosts as separate collection`() {
        val original = FFetchContext()
        original.allowedHosts.addAll(setOf("host1.com", "host2.com"))

        val copied = original.copy()

        // Test contents are the same
        assertEquals(original.allowedHosts, copied.allowedHosts)

        // Test that they are separate mutable collections
        copied.allowedHosts.add("copied-only.com")
        assertFalse(original.allowedHosts.contains("copied-only.com"))

        original.allowedHosts.add("original-only.com")
        assertFalse(copied.allowedHosts.contains("original-only.com"))
    }

    @Test
    fun `test property delegation getter branches`() {
        val context = FFetchContext()

        // Test that getters return values from underlying config objects
        assertEquals(context.performanceConfig.chunkSize, context.chunkSize)
        assertEquals(context.performanceConfig.maxConcurrency, context.maxConcurrency)
        assertEquals(context.clientConfig.httpClient, context.httpClient)
        assertEquals(context.clientConfig.htmlParser, context.htmlParser)
        assertEquals(context.requestConfig.sheetName, context.sheetName)
        assertEquals(context.requestConfig.total, context.total)
        assertEquals(context.securityConfig.allowedHosts, context.allowedHosts)

        // Test that modifying config objects directly affects getters
        context.performanceConfig = FFetchPerformanceConfig(chunkSize = 888, maxConcurrency = 88)
        assertEquals(888, context.chunkSize)
        assertEquals(88, context.maxConcurrency)

        context.requestConfig = FFetchRequestConfig(sheetName = "direct", total = 999)
        assertEquals("direct", context.sheetName)
        assertEquals(999, context.total)
    }

    @Test
    fun `test backward compatibility constructor delegates to new constructor`() {
        val customClient = MockFFetchHTTPClient()
        val customParser = MockHTMLParser()
        val customHosts = mutableSetOf("compat.com")

        val context =
            FFetchContext(
                chunkSize = 123,
                cacheReload = true,
                cacheConfig = FFetchCacheConfig.CacheOnly,
                sheetName = "compat",
                httpClient = customClient,
                htmlParser = customParser,
                total = 456,
                maxConcurrency = 7,
                allowedHosts = customHosts,
            )

        // Verify that backward compatibility constructor properly delegates
        assertEquals(123, context.performanceConfig.chunkSize)
        assertEquals(7, context.performanceConfig.maxConcurrency)
        assertTrue(context.cacheReload)
        assertEquals(FFetchCacheConfig.CacheOnly, context.cacheConfig)
        assertEquals(customClient, context.clientConfig.httpClient)
        assertEquals(customParser, context.clientConfig.htmlParser)
        assertEquals("compat", context.requestConfig.sheetName)
        assertEquals(456, context.requestConfig.total)
        assertEquals(customHosts, context.securityConfig.allowedHosts)
    }

    @Test
    fun `test FFetchContextBuilder build creates independent config objects`() {
        val builder = FFetchContextBuilder()
        builder.chunkSize = 200
        builder.maxConcurrency = 20

        val context1 = builder.build()

        // Modify builder
        builder.chunkSize = 300
        builder.maxConcurrency = 30

        val context2 = builder.build()

        // Verify first context is unchanged
        assertEquals(200, context1.chunkSize)
        assertEquals(20, context1.maxConcurrency)

        // Verify second context has new values
        assertEquals(300, context2.chunkSize)
        assertEquals(30, context2.maxConcurrency)

        // Verify they have independent config objects
        assertNotSame(context1.performanceConfig, context2.performanceConfig)
    }

    @Test
    fun `test all cache configuration constants work with context`() {
        val context = FFetchContext()

        // Test Default
        context.cacheConfig = FFetchCacheConfig.Default
        assertFalse(context.cacheConfig.noCache)
        assertFalse(context.cacheConfig.cacheOnly)
        assertFalse(context.cacheConfig.cacheElseLoad)

        // Test NoCache
        context.cacheConfig = FFetchCacheConfig.NoCache
        assertTrue(context.cacheConfig.noCache)
        assertFalse(context.cacheConfig.cacheOnly)
        assertFalse(context.cacheConfig.cacheElseLoad)

        // Test CacheOnly
        context.cacheConfig = FFetchCacheConfig.CacheOnly
        assertFalse(context.cacheConfig.noCache)
        assertTrue(context.cacheConfig.cacheOnly)
        assertFalse(context.cacheConfig.cacheElseLoad)

        // Test CacheElseLoad
        context.cacheConfig = FFetchCacheConfig.CacheElseLoad
        assertFalse(context.cacheConfig.noCache)
        assertFalse(context.cacheConfig.cacheOnly)
        assertTrue(context.cacheConfig.cacheElseLoad)
    }

    @Test
    fun `test error handling and edge cases in context operations`() {
        val context = FFetchContext()

        // Test that setting properties to same value doesn't create new objects
        val originalPerfConfig = context.performanceConfig
        context.chunkSize = context.chunkSize
        // Note: This will still create a new object due to the copy() implementation
        // but the values should be the same
        assertEquals(originalPerfConfig.chunkSize, context.performanceConfig.chunkSize)
        assertEquals(originalPerfConfig.maxConcurrency, context.performanceConfig.maxConcurrency)

        // Test extreme values don't cause errors
        context.chunkSize = Int.MIN_VALUE
        assertEquals(Int.MIN_VALUE, context.chunkSize)

        context.maxConcurrency = Int.MAX_VALUE
        assertEquals(Int.MAX_VALUE, context.maxConcurrency)

        context.total = 0
        assertEquals(0, context.total)

        // Test null values are handled correctly
        context.sheetName = null
        assertNull(context.sheetName)

        context.total = null
        assertNull(context.total)

        // Test empty collections work
        context.allowedHosts.clear()
        assertTrue(context.allowedHosts.isEmpty())

        // Test adding to empty collection
        context.allowedHosts.add("test.com")
        assertEquals(1, context.allowedHosts.size)
    }

    @Test
    fun `test parameter validation edge cases`() {
        // Test that FFetchContext accepts any values without validation
        // This tests current behavior - no validation is performed

        val context =
            FFetchContext(
                chunkSize = -999,
                maxConcurrency = -1,
                total = -100,
                sheetName = "",
                allowedHosts = mutableSetOf(),
            )

        assertEquals(-999, context.chunkSize)
        assertEquals(-1, context.maxConcurrency)
        assertEquals(-100, context.total)
        assertEquals("", context.sheetName)
        assertTrue(context.allowedHosts.isEmpty())

        // Test that copy preserves these values
        val copied = context.copy()
        assertEquals(-999, copied.chunkSize)
        assertEquals(-1, copied.maxConcurrency)
        assertEquals(-100, copied.total)
        assertEquals("", copied.sheetName)
    }
}
