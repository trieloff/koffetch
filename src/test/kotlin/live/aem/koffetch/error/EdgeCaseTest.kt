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

package live.aem.koffetch.error

import live.aem.koffetch.*
import live.aem.koffetch.mock.MockFFetchHTTPClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EdgeCaseTest {
    private val mockClient = MockFFetchHTTPClient()
    private val context =
        FFetchContext().apply {
            httpClient = mockClient
        }

    @Test
    fun testInvalidURLsShouldThrowInvalidURLError() {
        val invalidUrls =
            listOf(
                "",
                "not-a-url",
                "://missing-scheme",
                "http://",
                "javascript:alert('xss')",
            )

        invalidUrls.forEach { invalidUrl ->
            val exception =
                assertFailsWith<FFetchError.InvalidURL> {
                    FFetch(invalidUrl)
                }

            assertTrue(exception.message!!.contains(invalidUrl))
        }
    }

    @Test
    fun testZeroChunkSizeShouldWork() {
        val context =
            FFetchContext().apply {
                chunkSize = 0
                httpClient = mockClient
            }

        val ffetch = FFetch(java.net.URL("https://example.com"), context)
        assertEquals(0, ffetch.context.chunkSize)
    }

    @Test
    fun testNegativeChunkSizeShouldWork() {
        val context =
            FFetchContext().apply {
                chunkSize = -1
                httpClient = mockClient
            }

        val ffetch = FFetch(java.net.URL("https://example.com"), context)
        assertEquals(-1, ffetch.context.chunkSize)
    }

    @Test
    fun testExtremelyLargeChunkSizeShouldBeHandled() {
        val context =
            FFetchContext().apply {
                chunkSize = Int.MAX_VALUE
                httpClient = mockClient
            }

        val ffetch = FFetch(java.net.URL("https://example.com"), context)
        assertEquals(Int.MAX_VALUE, ffetch.context.chunkSize)
    }

    @Test
    fun testZeroConcurrencyLimitShouldWork() {
        val context =
            FFetchContext().apply {
                maxConcurrency = 0
                httpClient = mockClient
            }

        val ffetch = FFetch(java.net.URL("https://example.com"), context)
        assertEquals(0, ffetch.context.maxConcurrency)
    }

    @Test
    fun testNegativeConcurrencyLimitShouldBeHandled() {
        val context =
            FFetchContext().apply {
                maxConcurrency = -1
                httpClient = mockClient
            }

        val ffetch = FFetch(java.net.URL("https://example.com"), context)
        assertEquals(-1, ffetch.context.maxConcurrency)
    }

    @Test
    fun testExtremelyHighConcurrencyLimitShouldBeAccepted() {
        val context =
            FFetchContext().apply {
                maxConcurrency = 10000
                httpClient = mockClient
            }

        val ffetch = FFetch(java.net.URL("https://example.com"), context)
        assertEquals(10000, ffetch.context.maxConcurrency)
    }

    @Test
    fun testSheetNamesWithSpecialCharacters() {
        val specialSheetNames =
            listOf(
                "sheet-with-dashes",
                "sheet_with_underscores",
                "sheet with spaces",
                "sheet@with#special\$chars%",
                "sheet.with.dots",
                "sheet/with/slashes",
                "sheet\\with\\backslashes",
                "sheet\"with\"quotes",
                "sheet'with'apostrophes",
                "sheet<with>brackets",
                "sheet[with]square[brackets]",
                "sheet{with}curly{braces}",
                "sheet|with|pipes",
                "sheet+with+plus+signs",
                "sheet=with=equals",
                "sheet?with?questions",
                "sheet&with&ampersands",
                "sheetñwithñunicode字符",
                "🎉emoji🎉sheet🎉",
                "",
            )

        specialSheetNames.forEach { sheetName ->
            val context =
                FFetchContext().apply {
                    this.sheetName = sheetName
                    httpClient = mockClient
                }

            val ffetch = FFetch(java.net.URL("https://example.com"), context)
            assertEquals(sheetName, ffetch.context.sheetName)
        }
    }

    @Test
    fun testVeryLongSheetNameShouldBeHandled() {
        val longSheetName = "a".repeat(10000)
        val context =
            FFetchContext().apply {
                sheetName = longSheetName
                httpClient = mockClient
            }

        val ffetch = FFetch(java.net.URL("https://example.com"), context)
        assertEquals(longSheetName, ffetch.context.sheetName)
    }

    @Test
    fun testCacheConfigurationWithInvalidMaxAge() {
        val invalidMaxAges =
            listOf(
                -1L,
                0L,
                Long.MAX_VALUE,
                Long.MIN_VALUE,
            )

        invalidMaxAges.forEach { maxAge ->
            val cacheConfig = FFetchCacheConfig(maxAge = maxAge)
            val context =
                FFetchContext().apply {
                    this.cacheConfig = cacheConfig
                    httpClient = mockClient
                }

            val ffetch = FFetch(java.net.URL("https://example.com"), context)
            assertEquals(maxAge, ffetch.context.cacheConfig.maxAge)
        }
    }

    @Test
    fun testCacheConfigurationWithConflictingFlags() {
        val conflictingConfigs =
            listOf(
                FFetchCacheConfig(noCache = true, cacheOnly = true),
                FFetchCacheConfig(noCache = true, cacheElseLoad = true),
                FFetchCacheConfig(cacheOnly = true, cacheElseLoad = true),
                FFetchCacheConfig(noCache = true, cacheOnly = true, cacheElseLoad = true),
            )

        conflictingConfigs.forEach { cacheConfig ->
            val context =
                FFetchContext().apply {
                    this.cacheConfig = cacheConfig
                    httpClient = mockClient
                }

            // Should not throw an exception, but behavior might be undefined
            val ffetch = FFetch(java.net.URL("https://example.com"), context)
            assertEquals(cacheConfig, ffetch.context.cacheConfig)
        }
    }

    @Test
    fun testValidURLsShouldBeHandled() {
        val validUrls =
            listOf(
                "https://example.com",
                "http://example.com",
                "https://example.com:8080",
                "https://example.com/path",
                "https://example.com/path?query=value",
                "https://example.com/path#fragment",
                "https://example.com/path?query=value#fragment",
                "https://subdomain.example.com",
                "https://example.com/path/to/resource.json",
                "https://127.0.0.1:8080",
                "https://localhost",
            )

        validUrls.forEach { url ->
            // Should not throw an exception for valid URLs
            val ffetch = FFetch(url)
            assertEquals(url, ffetch.url.toString())
        }
    }

    @Test
    fun testEmptyAllowedHostsShouldBePopulatedWithInitialHostname() {
        val context =
            FFetchContext().apply {
                allowedHosts.clear()
                httpClient = mockClient
            }

        val ffetch = FFetch(java.net.URL("https://example.com"), context)

        assertTrue(ffetch.context.allowedHosts.contains("example.com"))
        assertEquals(1, ffetch.context.allowedHosts.size)
    }

    @Test
    fun testWildcardAllowedHostsShouldBePreserved() {
        val context =
            FFetchContext().apply {
                allowedHosts.clear()
                allowedHosts.add("*")
                httpClient = mockClient
            }

        val ffetch = FFetch(java.net.URL("https://example.com"), context)

        assertTrue(ffetch.context.allowedHosts.contains("*"))
        assertTrue(ffetch.context.allowedHosts.contains("example.com"))
    }

    @Test
    fun testMultipleAllowedHostsShouldBePreserved() {
        val context =
            FFetchContext().apply {
                allowedHosts.clear()
                allowedHosts.addAll(listOf("example.com", "api.example.com", "cdn.example.com"))
                httpClient = mockClient
            }

        val ffetch = FFetch(java.net.URL("https://test.com"), context)

        assertTrue(ffetch.context.allowedHosts.contains("example.com"))
        assertTrue(ffetch.context.allowedHosts.contains("api.example.com"))
        assertTrue(ffetch.context.allowedHosts.contains("cdn.example.com"))
        assertTrue(ffetch.context.allowedHosts.contains("test.com"))
        assertEquals(4, ffetch.context.allowedHosts.size)
    }

    @Test
    fun testDefaultContextValuesShouldBeReasonable() {
        val context = FFetchContext()

        assertTrue(context.chunkSize > 0)
        assertTrue(context.maxConcurrency > 0)
        assertNotNull(context.httpClient)
        assertNotNull(context.htmlParser)
        assertNotNull(context.cacheConfig)
        assertNotNull(context.allowedHosts)
        assertEquals(false, context.cacheReload) // deprecated but should have a default
        assertNull(context.sheetName) // should be null by default
        assertNull(context.total) // should be null initially
    }
}
