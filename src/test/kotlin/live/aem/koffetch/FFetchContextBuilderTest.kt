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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FFetchContextBuilderTest {
    @Test
    fun `test FFetchContextBuilder default values`() {
        val builder = FFetchContextBuilder()

        assertEquals(FFetchContextBuilder.DEFAULT_CHUNK_SIZE, builder.chunkSize)
        assertEquals(FFetchContextBuilder.DEFAULT_MAX_CONCURRENCY, builder.maxConcurrency)
        assertFalse(builder.cacheReload)
        assertEquals(FFetchCacheConfig.Default, builder.cacheConfig)
        assertNull(builder.sheetName)
        assertNull(builder.total)
        assertNotNull(builder.httpClient)
        assertNotNull(builder.htmlParser)
        assertTrue(builder.allowedHosts.isEmpty())
    }

    @Test
    fun `test FFetchContextBuilder constants`() {
        assertEquals(255, FFetchContextBuilder.DEFAULT_CHUNK_SIZE)
        assertEquals(5, FFetchContextBuilder.DEFAULT_MAX_CONCURRENCY)
    }

    @Test
    fun `test FFetchContextBuilder property modification`() {
        val builder = FFetchContextBuilder()
        val customClient = MockFFetchHTTPClient()
        val customParser = MockHTMLParser()

        builder.chunkSize = 500
        builder.maxConcurrency = 20
        builder.cacheReload = true
        builder.cacheConfig = FFetchCacheConfig.NoCache
        builder.sheetName = "test-sheet"
        builder.total = 1000
        builder.httpClient = customClient
        builder.htmlParser = customParser
        builder.allowedHosts.add("example.com")

        assertEquals(500, builder.chunkSize)
        assertEquals(20, builder.maxConcurrency)
        assertTrue(builder.cacheReload)
        assertEquals(FFetchCacheConfig.NoCache, builder.cacheConfig)
        assertEquals("test-sheet", builder.sheetName)
        assertEquals(1000, builder.total)
        assertEquals(customClient, builder.httpClient)
        assertEquals(customParser, builder.htmlParser)
        assertTrue(builder.allowedHosts.contains("example.com"))
    }

    @Test
    fun `test FFetchContextBuilder buildPerformanceConfig`() {
        val builder = FFetchContextBuilder()
        builder.chunkSize = 300
        builder.maxConcurrency = 15

        val perfConfig = builder.buildPerformanceConfig()

        assertEquals(300, perfConfig.chunkSize)
        assertEquals(15, perfConfig.maxConcurrency)
    }

    @Test
    fun `test FFetchContextBuilder buildClientConfig`() {
        val builder = FFetchContextBuilder()
        val customClient = MockFFetchHTTPClient()
        val customParser = MockHTMLParser()

        builder.httpClient = customClient
        builder.htmlParser = customParser

        val clientConfig = builder.buildClientConfig()

        assertEquals(customClient, clientConfig.httpClient)
        assertEquals(customParser, clientConfig.htmlParser)
    }

    @Test
    fun `test FFetchContextBuilder buildRequestConfig`() {
        val builder = FFetchContextBuilder()
        builder.sheetName = "products"
        builder.total = 750

        val requestConfig = builder.buildRequestConfig()

        assertEquals("products", requestConfig.sheetName)
        assertEquals(750, requestConfig.total)
    }

    @Test
    fun `test FFetchContextBuilder buildSecurityConfig`() {
        val builder = FFetchContextBuilder()
        val hosts = mutableSetOf("secure1.com", "secure2.com")
        builder.allowedHosts = hosts

        val securityConfig = builder.buildSecurityConfig()

        assertEquals(hosts, securityConfig.allowedHosts)
        assertTrue(securityConfig.allowedHosts.contains("secure1.com"))
        assertTrue(securityConfig.allowedHosts.contains("secure2.com"))
    }

    @Test
    fun `test FFetchContextBuilder build method`() {
        val builder = FFetchContextBuilder()
        val customClient = MockFFetchHTTPClient()
        val customParser = MockHTMLParser()

        builder.chunkSize = 400
        builder.maxConcurrency = 25
        builder.cacheReload = true
        builder.cacheConfig = FFetchCacheConfig.CacheOnly
        builder.sheetName = "builder-test"
        builder.total = 2000
        builder.httpClient = customClient
        builder.htmlParser = customParser
        builder.allowedHosts.add("builder.example.com")

        val context = builder.build()

        assertEquals(400, context.chunkSize)
        assertEquals(25, context.maxConcurrency)
        assertTrue(context.cacheReload)
        assertEquals(FFetchCacheConfig.CacheOnly, context.cacheConfig)
        assertEquals("builder-test", context.sheetName)
        assertEquals(2000, context.total)
        assertEquals(customClient, context.httpClient)
        assertEquals(customParser, context.htmlParser)
        assertTrue(context.allowedHosts.contains("builder.example.com"))
    }

    @Test
    fun `test FFetchContextBuilder build with default values`() {
        val builder = FFetchContextBuilder()
        val context = builder.build()

        assertEquals(FFetchContextBuilder.DEFAULT_CHUNK_SIZE, context.chunkSize)
        assertEquals(FFetchContextBuilder.DEFAULT_MAX_CONCURRENCY, context.maxConcurrency)
        assertFalse(context.cacheReload)
        assertEquals(FFetchCacheConfig.Default, context.cacheConfig)
        assertNull(context.sheetName)
        assertNull(context.total)
        assertTrue(context.httpClient is DefaultFFetchHTTPClient)
        assertTrue(context.htmlParser is DefaultFFetchHTMLParser)
        assertTrue(context.allowedHosts.isEmpty())
    }

    @Test
    fun `test FFetchContextBuilder with all cache configurations`() {
        val builder = FFetchContextBuilder()

        // Test Default cache config
        builder.cacheConfig = FFetchCacheConfig.Default
        var context = builder.build()
        assertEquals(FFetchCacheConfig.Default, context.cacheConfig)

        // Test NoCache config
        builder.cacheConfig = FFetchCacheConfig.NoCache
        context = builder.build()
        assertEquals(FFetchCacheConfig.NoCache, context.cacheConfig)

        // Test CacheOnly config
        builder.cacheConfig = FFetchCacheConfig.CacheOnly
        context = builder.build()
        assertEquals(FFetchCacheConfig.CacheOnly, context.cacheConfig)

        // Test CacheElseLoad config
        builder.cacheConfig = FFetchCacheConfig.CacheElseLoad
        context = builder.build()
        assertEquals(FFetchCacheConfig.CacheElseLoad, context.cacheConfig)

        // Test custom cache config
        val customCacheConfig =
            FFetchCacheConfig(
                noCache = false,
                cacheOnly = false,
                cacheElseLoad = true,
                maxAge = 3600,
                ignoreServerCacheControl = true,
            )
        builder.cacheConfig = customCacheConfig
        context = builder.build()
        assertEquals(customCacheConfig, context.cacheConfig)
    }

    @Test
    fun `test FFetchContextBuilder edge cases`() {
        val builder = FFetchContextBuilder()

        // Test zero values
        builder.chunkSize = 0
        builder.maxConcurrency = 0
        builder.total = 0

        var context = builder.build()
        assertEquals(0, context.chunkSize)
        assertEquals(0, context.maxConcurrency)
        assertEquals(0, context.total)

        // Test negative values
        builder.chunkSize = -1
        builder.maxConcurrency = -1
        builder.total = -1

        context = builder.build()
        assertEquals(-1, context.chunkSize)
        assertEquals(-1, context.maxConcurrency)
        assertEquals(-1, context.total)

        // Test maximum values
        builder.chunkSize = Int.MAX_VALUE
        builder.maxConcurrency = Int.MAX_VALUE
        builder.total = Int.MAX_VALUE

        context = builder.build()
        assertEquals(Int.MAX_VALUE, context.chunkSize)
        assertEquals(Int.MAX_VALUE, context.maxConcurrency)
        assertEquals(Int.MAX_VALUE, context.total)

        // Test empty string sheet name
        builder.sheetName = ""
        context = builder.build()
        assertEquals("", context.sheetName)

        // Test null sheet name (explicit)
        builder.sheetName = null
        context = builder.build()
        assertNull(context.sheetName)
    }

    @Test
    fun `test FFetchContextBuilder mutable collections`() {
        val builder = FFetchContextBuilder()

        // Test that allowedHosts is mutable and initially empty
        assertTrue(builder.allowedHosts.isEmpty())

        // Test adding hosts
        builder.allowedHosts.add("host1.com")
        builder.allowedHosts.add("host2.com")
        assertEquals(2, builder.allowedHosts.size)

        // Test that built context has the same hosts
        val context = builder.build()
        assertEquals(2, context.allowedHosts.size)
        assertTrue(context.allowedHosts.contains("host1.com"))
        assertTrue(context.allowedHosts.contains("host2.com"))

        // Test that modifying builder after build DOES affect built context
        // Note: The builder shares the same mutable set reference
        val originalContextSize = context.allowedHosts.size
        builder.allowedHosts.add("host3.com")
        assertEquals(originalContextSize + 1, context.allowedHosts.size) // Context also has the new host
        assertEquals(3, builder.allowedHosts.size) // Builder has new host
        assertTrue(context.allowedHosts.contains("host3.com")) // Context shares the reference
    }

    @Test
    fun `test FFetchContextBuilder can be reused`() {
        val builder = FFetchContextBuilder()
        builder.chunkSize = 100
        builder.sheetName = "reusable"

        // Build first context
        val context1 = builder.build()
        assertEquals(100, context1.chunkSize)
        assertEquals("reusable", context1.sheetName)

        // Modify builder and build second context
        builder.chunkSize = 200
        builder.sheetName = "modified"
        val context2 = builder.build()

        // Verify first context is unchanged
        assertEquals(100, context1.chunkSize)
        assertEquals("reusable", context1.sheetName)

        // Verify second context has modifications
        assertEquals(200, context2.chunkSize)
        assertEquals("modified", context2.sheetName)
    }

    @Test
    fun `test FFetchContextBuilder with complex configurations`() {
        val builder = FFetchContextBuilder()

        // Configure builder with mix of values
        builder.apply {
            chunkSize = 123
            maxConcurrency = 7
            cacheReload = true
            cacheConfig =
                FFetchCacheConfig(
                    noCache = false,
                    cacheOnly = true,
                    maxAge = 1800,
                )
            sheetName = "complex-test"
            total = 456
            httpClient = MockFFetchHTTPClient()
            htmlParser = MockHTMLParser()
            allowedHosts.addAll(setOf("complex1.com", "complex2.com", "complex3.com"))
        }

        val context = builder.build()

        // Verify all configurations were applied
        assertEquals(123, context.chunkSize)
        assertEquals(7, context.maxConcurrency)
        assertTrue(context.cacheReload)
        assertFalse(context.cacheConfig.noCache)
        assertTrue(context.cacheConfig.cacheOnly)
        assertEquals(1800, context.cacheConfig.maxAge)
        assertEquals("complex-test", context.sheetName)
        assertEquals(456, context.total)
        assertTrue(context.httpClient is MockFFetchHTTPClient)
        assertTrue(context.htmlParser is MockHTMLParser)
        assertEquals(3, context.allowedHosts.size)
        assertTrue(context.allowedHosts.containsAll(setOf("complex1.com", "complex2.com", "complex3.com")))
    }

    @Test
    fun `test FFetchContextBuilder individual config builders`() {
        val builder = FFetchContextBuilder()
        builder.chunkSize = 666
        builder.maxConcurrency = 42
        builder.httpClient = MockFFetchHTTPClient()
        builder.htmlParser = MockHTMLParser()
        builder.sheetName = "individual"
        builder.total = 777
        builder.allowedHosts.add("individual.com")

        // Test each individual config builder
        val perfConfig = builder.buildPerformanceConfig()
        assertEquals(666, perfConfig.chunkSize)
        assertEquals(42, perfConfig.maxConcurrency)

        val clientConfig = builder.buildClientConfig()
        assertTrue(clientConfig.httpClient is MockFFetchHTTPClient)
        assertTrue(clientConfig.htmlParser is MockHTMLParser)

        val requestConfig = builder.buildRequestConfig()
        assertEquals("individual", requestConfig.sheetName)
        assertEquals(777, requestConfig.total)

        val securityConfig = builder.buildSecurityConfig()
        assertEquals(1, securityConfig.allowedHosts.size)
        assertTrue(securityConfig.allowedHosts.contains("individual.com"))

        // Verify full build produces same results
        val fullContext = builder.build()
        assertEquals(perfConfig.chunkSize, fullContext.chunkSize)
        assertEquals(perfConfig.maxConcurrency, fullContext.maxConcurrency)
        assertEquals(clientConfig.httpClient, fullContext.httpClient)
        assertEquals(clientConfig.htmlParser, fullContext.htmlParser)
        assertEquals(requestConfig.sheetName, fullContext.sheetName)
        assertEquals(requestConfig.total, fullContext.total)
        assertEquals(securityConfig.allowedHosts, fullContext.allowedHosts)
    }
}
