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

class FFetchContextTest {

    @Test
    fun `test FFetchContext default constructor`() {
        val context = FFetchContext()
        
        assertEquals(255, context.chunkSize)
        assertEquals(5, context.maxConcurrency)
        assertFalse(context.cacheReload)
        assertEquals(FFetchCacheConfig.Default, context.cacheConfig)
        assertNull(context.sheetName)
        assertNull(context.total)
        assertNotNull(context.httpClient)
        assertNotNull(context.htmlParser)
        assertTrue(context.allowedHosts.isEmpty())
    }

    @Test
    fun `test FFetchContext primary constructor with config objects`() {
        val performanceConfig = FFetchPerformanceConfig(chunkSize = 100, maxConcurrency = 10)
        val clientConfig = FFetchClientConfig(MockFFetchHTTPClient(), MockHTMLParser())
        val requestConfig = FFetchRequestConfig(sheetName = "test", total = 500)
        val securityConfig = FFetchSecurityConfig(mutableSetOf("example.com"))
        
        val context = FFetchContext(
            performanceConfig = performanceConfig,
            cacheReload = true,
            cacheConfig = FFetchCacheConfig.NoCache,
            clientConfig = clientConfig,
            requestConfig = requestConfig,
            securityConfig = securityConfig
        )
        
        assertEquals(100, context.chunkSize)
        assertEquals(10, context.maxConcurrency)
        assertTrue(context.cacheReload)
        assertEquals(FFetchCacheConfig.NoCache, context.cacheConfig)
        assertEquals("test", context.sheetName)
        assertEquals(500, context.total)
        assertEquals(clientConfig.httpClient, context.httpClient)
        assertEquals(clientConfig.htmlParser, context.htmlParser)
        assertTrue(context.allowedHosts.contains("example.com"))
    }

    @Test
    fun `test FFetchContext backward compatibility constructor`() {
        val customClient = MockFFetchHTTPClient()
        val customParser = MockHTMLParser()
        val allowedHosts = mutableSetOf("test.com", "api.test.com")
        
        val context = FFetchContext(
            chunkSize = 150,
            cacheReload = true,
            cacheConfig = FFetchCacheConfig.CacheOnly,
            sheetName = "products",
            httpClient = customClient,
            htmlParser = customParser,
            total = 1000,
            maxConcurrency = 15,
            allowedHosts = allowedHosts
        )
        
        assertEquals(150, context.chunkSize)
        assertTrue(context.cacheReload)
        assertEquals(FFetchCacheConfig.CacheOnly, context.cacheConfig)
        assertEquals("products", context.sheetName)
        assertEquals(customClient, context.httpClient)
        assertEquals(customParser, context.htmlParser)
        assertEquals(1000, context.total)
        assertEquals(15, context.maxConcurrency)
        assertEquals(allowedHosts, context.allowedHosts)
    }

    @Test
    fun `test FFetchContext property delegation to config objects`() {
        val context = FFetchContext()
        
        // Test that modifying delegated properties updates the underlying config objects
        context.chunkSize = 200
        assertEquals(200, context.performanceConfig.chunkSize)
        
        context.maxConcurrency = 20
        assertEquals(20, context.performanceConfig.maxConcurrency)
        
        val newClient = MockFFetchHTTPClient()
        context.httpClient = newClient
        assertEquals(newClient, context.clientConfig.httpClient)
        
        val newParser = MockHTMLParser()
        context.htmlParser = newParser
        assertEquals(newParser, context.clientConfig.htmlParser)
        
        context.sheetName = "updated"
        assertEquals("updated", context.requestConfig.sheetName)
        
        context.total = 999
        assertEquals(999, context.requestConfig.total)
        
        context.allowedHosts.add("new.example.com")
        assertTrue(context.securityConfig.allowedHosts.contains("new.example.com"))
    }

    @Test
    fun `test FFetchContext copy method with all parameters`() {
        val original = FFetchContext()
        val customClient = MockFFetchHTTPClient()
        val customParser = MockHTMLParser()
        val customHosts = mutableSetOf("secure.com")
        
        val copied = original.copy(
            chunkSize = 300,
            cacheReload = true,
            cacheConfig = FFetchCacheConfig.CacheElseLoad,
            sheetName = "copied",
            httpClient = customClient,
            htmlParser = customParser,
            total = 2000,
            maxConcurrency = 25,
            allowedHosts = customHosts
        )
        
        // Verify original is unchanged
        assertEquals(255, original.chunkSize)
        assertFalse(original.cacheReload)
        
        // Verify copy has all modifications
        assertEquals(300, copied.chunkSize)
        assertTrue(copied.cacheReload)
        assertEquals(FFetchCacheConfig.CacheElseLoad, copied.cacheConfig)
        assertEquals("copied", copied.sheetName)
        assertEquals(customClient, copied.httpClient)
        assertEquals(customParser, copied.htmlParser)
        assertEquals(2000, copied.total)
        assertEquals(25, copied.maxConcurrency)
        assertEquals(customHosts, copied.allowedHosts)
        
        assertNotSame(original, copied)
    }

    @Test
    fun `test FFetchContext copyWithConfigs method`() {
        val original = FFetchContext()
        val newPerformanceConfig = FFetchPerformanceConfig(chunkSize = 400, maxConcurrency = 30)
        val newClientConfig = FFetchClientConfig(MockFFetchHTTPClient(), MockHTMLParser())
        val newRequestConfig = FFetchRequestConfig(sheetName = "config-test", total = 3000)
        
        val copied = original.copyWithConfigs(
            performanceConfig = newPerformanceConfig,
            cacheReload = true,
            cacheConfig = FFetchCacheConfig.NoCache,
            clientConfig = newClientConfig,
            requestConfig = newRequestConfig
        )
        
        assertEquals(400, copied.chunkSize)
        assertEquals(30, copied.maxConcurrency)
        assertTrue(copied.cacheReload)
        assertEquals(FFetchCacheConfig.NoCache, copied.cacheConfig)
        assertEquals("config-test", copied.sheetName)
        assertEquals(3000, copied.total)
        assertEquals(newClientConfig.httpClient, copied.httpClient)
        assertEquals(newClientConfig.htmlParser, copied.htmlParser)
        
        // Verify original is unchanged
        assertEquals(255, original.chunkSize)
        assertFalse(original.cacheReload)
    }

    @Test
    fun `test FFetchContext copyWithSecurity method`() {
        val original = FFetchContext()
        val securityConfig = FFetchSecurityConfig(mutableSetOf("secure1.com", "secure2.com"))
        
        val copied = original.copyWithSecurity(securityConfig)
        
        assertEquals(securityConfig.allowedHosts, copied.allowedHosts)
        assertTrue(copied.allowedHosts.contains("secure1.com"))
        assertTrue(copied.allowedHosts.contains("secure2.com"))
        
        // Verify original is unchanged
        assertTrue(original.allowedHosts.isEmpty())
    }

    @Test
    fun `test FFetchContext equals and hashCode`() {
        val customClient = MockFFetchHTTPClient()
        val customParser = MockHTMLParser()
        
        val context1 = FFetchContext(
            chunkSize = 100, 
            maxConcurrency = 10, 
            cacheReload = true,
            httpClient = customClient,
            htmlParser = customParser
        )
        val context2 = FFetchContext(
            chunkSize = 100, 
            maxConcurrency = 10, 
            cacheReload = true,
            httpClient = customClient,
            htmlParser = customParser
        )
        val context3 = FFetchContext(
            chunkSize = 200, 
            maxConcurrency = 10, 
            cacheReload = true,
            httpClient = customClient,
            htmlParser = customParser
        )
        
        assertEquals(context1, context2)
        assertEquals(context1.hashCode(), context2.hashCode())
        assertTrue(context1 != context3)
        assertTrue(context1.hashCode() != context3.hashCode())
    }

    @Test
    fun `test FFetchContext toString method`() {
        val context = FFetchContext(chunkSize = 100, sheetName = "test-sheet")
        val toString = context.toString()
        
        assertTrue(toString.contains("FFetchContext"))
        assertTrue(toString.contains("chunkSize=100"))
        assertTrue(toString.contains("sheetName=test-sheet"))
        assertTrue(toString.contains("cacheReload=false"))
        assertTrue(toString.contains("maxConcurrency=5"))
    }

    @Test
    fun `test config parameter objects`() {
        // Test FFetchPerformanceConfig
        val perfConfig = FFetchPerformanceConfig(chunkSize = 500, maxConcurrency = 50)
        assertEquals(500, perfConfig.chunkSize)
        assertEquals(50, perfConfig.maxConcurrency)
        
        val defaultPerfConfig = FFetchPerformanceConfig()
        assertEquals(255, defaultPerfConfig.chunkSize)
        assertEquals(5, defaultPerfConfig.maxConcurrency)
        
        // Test FFetchClientConfig
        val customClient = MockFFetchHTTPClient()
        val customParser = MockHTMLParser()
        val clientConfig = FFetchClientConfig(customClient, customParser)
        assertEquals(customClient, clientConfig.httpClient)
        assertEquals(customParser, clientConfig.htmlParser)
        
        val defaultClientConfig = FFetchClientConfig()
        assertTrue(defaultClientConfig.httpClient is DefaultFFetchHTTPClient)
        assertTrue(defaultClientConfig.htmlParser is DefaultFFetchHTMLParser)
        
        // Test FFetchRequestConfig
        val requestConfig = FFetchRequestConfig(sheetName = "request-test", total = 750)
        assertEquals("request-test", requestConfig.sheetName)
        assertEquals(750, requestConfig.total)
        
        val defaultRequestConfig = FFetchRequestConfig()
        assertNull(defaultRequestConfig.sheetName)
        assertNull(defaultRequestConfig.total)
        
        // Test FFetchSecurityConfig
        val hosts = mutableSetOf("host1.com", "host2.com")
        val securityConfig = FFetchSecurityConfig(hosts)
        assertEquals(hosts, securityConfig.allowedHosts)
        
        val defaultSecurityConfig = FFetchSecurityConfig()
        assertTrue(defaultSecurityConfig.allowedHosts.isEmpty())
    }

    @Test
    fun `test complex configuration combinations`() {
        val context = FFetchContext()
        
        // Test chaining property modifications
        context.chunkSize = 100
        context.maxConcurrency = 10
        context.cacheReload = true
        context.sheetName = "chained"
        context.total = 500
        context.allowedHosts.add("chain.com")
        
        assertEquals(100, context.chunkSize)
        assertEquals(10, context.maxConcurrency)
        assertTrue(context.cacheReload)
        assertEquals("chained", context.sheetName)
        assertEquals(500, context.total)
        assertTrue(context.allowedHosts.contains("chain.com"))
        
        // Test that all config objects are updated
        assertEquals(100, context.performanceConfig.chunkSize)
        assertEquals(10, context.performanceConfig.maxConcurrency)
        assertEquals("chained", context.requestConfig.sheetName)
        assertEquals(500, context.requestConfig.total)
        assertTrue(context.securityConfig.allowedHosts.contains("chain.com"))
    }

    @Test
    fun `test allowedHosts mutable collection behavior`() {
        val context = FFetchContext()
        
        // Test adding hosts
        context.allowedHosts.add("test1.com")
        context.allowedHosts.add("test2.com")
        assertEquals(2, context.allowedHosts.size)
        
        // Test removing hosts
        context.allowedHosts.remove("test1.com")
        assertEquals(1, context.allowedHosts.size)
        assertFalse(context.allowedHosts.contains("test1.com"))
        assertTrue(context.allowedHosts.contains("test2.com"))
        
        // Test clearing hosts
        context.allowedHosts.clear()
        assertTrue(context.allowedHosts.isEmpty())
        
        // Test bulk operations
        val newHosts = setOf("bulk1.com", "bulk2.com", "bulk3.com")
        context.allowedHosts.addAll(newHosts)
        assertEquals(3, context.allowedHosts.size)
        assertTrue(context.allowedHosts.containsAll(newHosts))
    }

    @Test
    fun `test copy method preserves mutable collections correctly`() {
        val original = FFetchContext()
        original.allowedHosts.add("original.com")
        
        val copied = original.copy()
        
        // Test that copied instance has same content but is a separate collection
        assertTrue(copied.allowedHosts.contains("original.com"))
        assertEquals(original.allowedHosts.size, copied.allowedHosts.size)
        
        // Test that modifying copy doesn't affect original
        copied.allowedHosts.add("copied.com")
        assertFalse(original.allowedHosts.contains("copied.com"))
        assertTrue(copied.allowedHosts.contains("copied.com"))
        assertEquals(1, original.allowedHosts.size)
        assertEquals(2, copied.allowedHosts.size)
    }

    @Test
    fun `test edge cases and boundary values`() {
        // Test zero values
        val zeroContext = FFetchContext(chunkSize = 0, maxConcurrency = 0)
        assertEquals(0, zeroContext.chunkSize)
        assertEquals(0, zeroContext.maxConcurrency)
        
        // Test negative values
        val negativeContext = FFetchContext(chunkSize = -1, maxConcurrency = -1, total = -1)
        assertEquals(-1, negativeContext.chunkSize)
        assertEquals(-1, negativeContext.maxConcurrency)
        assertEquals(-1, negativeContext.total)
        
        // Test very large values
        val largeContext = FFetchContext(
            chunkSize = Int.MAX_VALUE,
            maxConcurrency = Int.MAX_VALUE,
            total = Int.MAX_VALUE
        )
        assertEquals(Int.MAX_VALUE, largeContext.chunkSize)
        assertEquals(Int.MAX_VALUE, largeContext.maxConcurrency)
        assertEquals(Int.MAX_VALUE, largeContext.total)
        
        // Test empty and null string values
        val emptyContext = FFetchContext(sheetName = "")
        assertEquals("", emptyContext.sheetName)
        
        val nullContext = FFetchContext(sheetName = null)
        assertNull(nullContext.sheetName)
    }
}