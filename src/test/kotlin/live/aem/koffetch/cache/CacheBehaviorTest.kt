//
// CacheBehaviorTest.kt
// KotlinFFetch
//
// Tests for cache behavior integration and FFetch cache methods
//

package live.aem.koffetch.cache

import kotlinx.coroutines.test.runTest
import live.aem.koffetch.*
import live.aem.koffetch.mock.MockFFetchHTTPClient
import live.aem.koffetch.mock.MockResponse
import java.net.URL
import kotlin.test.*

class CacheBehaviorTest {
    private fun createMockClient(responses: Map<String, String> = mapOf()): MockFFetchHTTPClient {
        val client = MockFFetchHTTPClient()
        responses.forEach { (url, content) ->
            client.setResponse(url, MockResponse.success(content))
        }
        return client
    }

    @Test
    fun testCacheMethodConfiguration() =
        runTest {
            val ffetch = FFetch("https://example.com/test.json")

            // Test default cache config
            assertEquals(FFetchCacheConfig.Default, ffetch.context.cacheConfig)

            // Test cache() method with different configs
            val noCacheFFetch = ffetch.cache(FFetchCacheConfig.NoCache)
            assertEquals(FFetchCacheConfig.NoCache, noCacheFFetch.context.cacheConfig)
            assertTrue(noCacheFFetch.context.cacheReload)

            val cacheOnlyFFetch = ffetch.cache(FFetchCacheConfig.CacheOnly)
            assertEquals(FFetchCacheConfig.CacheOnly, cacheOnlyFFetch.context.cacheConfig)

            val cacheElseLoadFFetch = ffetch.cache(FFetchCacheConfig.CacheElseLoad)
            assertEquals(FFetchCacheConfig.CacheElseLoad, cacheElseLoadFFetch.context.cacheConfig)
        }

    @Test
    fun testReloadCacheMethod() =
        runTest {
            val ffetch = FFetch("https://example.com/test.json")
            val reloadFFetch = ffetch.reloadCache()

            assertEquals(FFetchCacheConfig.NoCache, reloadFFetch.context.cacheConfig)
            assertTrue(reloadFFetch.context.cacheReload)

            // Original should be unchanged
            assertEquals(FFetchCacheConfig.Default, ffetch.context.cacheConfig)
            assertFalse(ffetch.context.cacheReload)
        }

    @Test
    fun testWithCacheReloadMethod() =
        runTest {
            val ffetch = FFetch("https://example.com/test.json")

            // Test enabling cache reload
            val reloadEnabledFFetch = ffetch.withCacheReload(true)
            assertEquals(FFetchCacheConfig.NoCache, reloadEnabledFFetch.context.cacheConfig)

            // Test disabling cache reload
            val reloadDisabledFFetch = ffetch.withCacheReload(false)
            assertEquals(FFetchCacheConfig.Default, reloadDisabledFFetch.context.cacheConfig)

            // Test default parameter (should enable reload)
            val defaultReloadFFetch = ffetch.withCacheReload()
            assertEquals(FFetchCacheConfig.NoCache, defaultReloadFFetch.context.cacheConfig)
        }

    @Test
    fun testCacheConfigurationChaining() =
        runTest {
            val ffetch =
                FFetch("https://example.com/test.json")
                    .cache(FFetchCacheConfig(maxAge = 3600))
                    .chunks(100)
                    .maxConcurrency(3)

            assertEquals(3600L, ffetch.context.cacheConfig.maxAge)
            assertEquals(100, ffetch.context.chunkSize)
            assertEquals(3, ffetch.context.maxConcurrency)
        }

    @Test
    fun testCacheConfigWithCustomMaxAge() =
        runTest {
            val ffetch = FFetch("https://example.com/test.json")
            val customCacheConfig = FFetchCacheConfig(maxAge = 1800, cacheElseLoad = true)
            val configuredFFetch = ffetch.cache(customCacheConfig)

            assertEquals(1800L, configuredFFetch.context.cacheConfig.maxAge)
            assertTrue(configuredFFetch.context.cacheConfig.cacheElseLoad)
        }

    @Test
    fun testCacheConfigImmutability() =
        runTest {
            val originalFFetch = FFetch("https://example.com/test.json")
            val modifiedFFetch = originalFFetch.cache(FFetchCacheConfig.NoCache)

            // Original should be unchanged
            assertEquals(FFetchCacheConfig.Default, originalFFetch.context.cacheConfig)
            assertFalse(originalFFetch.context.cacheReload)

            // Modified should have new config
            assertEquals(FFetchCacheConfig.NoCache, modifiedFFetch.context.cacheConfig)
            assertTrue(modifiedFFetch.context.cacheReload)

            // They should be different instances
            assertNotSame(originalFFetch, modifiedFFetch)
            assertNotSame(originalFFetch.context, modifiedFFetch.context)
        }

    @Test
    fun testCacheConfigurationPrecedence() =
        runTest {
            val ffetch = FFetch("https://example.com/test.json")

            // Apply multiple cache configurations
            val finalFFetch =
                ffetch
                    .cache(FFetchCacheConfig.CacheOnly)
                    .reloadCache() // This should override to NoCache
                    .cache(FFetchCacheConfig(maxAge = 600)) // This should override again

            // The last cache configuration should win
            assertEquals(600L, finalFFetch.context.cacheConfig.maxAge)
            assertFalse(finalFFetch.context.cacheConfig.noCache)
            assertFalse(finalFFetch.context.cacheConfig.cacheOnly)
        }

    @Test
    fun testBackwardCompatibilityCacheReload() =
        runTest {
            val ffetch = FFetch("https://example.com/test.json")

            // Test that cacheReload is properly set based on cache config
            val noCacheFFetch = ffetch.cache(FFetchCacheConfig.NoCache)
            assertTrue(noCacheFFetch.context.cacheReload)

            val defaultCacheFFetch = ffetch.cache(FFetchCacheConfig.Default)
            assertFalse(defaultCacheFFetch.context.cacheReload)

            val customCacheFFetch = ffetch.cache(FFetchCacheConfig(cacheElseLoad = true))
            assertFalse(customCacheFFetch.context.cacheReload)
        }

    @Test
    fun testCacheMethodWithHTTPClient() =
        runTest {
            val mockClient =
                createMockClient(
                    mapOf(
                        "https://example.com/test.json" to """{"data": "test"}""",
                    ),
                )

            val ffetch =
                FFetch("https://example.com/test.json")
                    .withHTTPClient(mockClient)
                    .cache(FFetchCacheConfig.NoCache)

            assertEquals(FFetchCacheConfig.NoCache, ffetch.context.cacheConfig)
            assertSame(mockClient, ffetch.context.httpClient)
        }

    @Test
    fun testCacheConfigurationContextCopy() =
        runTest {
            val originalContext =
                FFetchContext(
                    chunkSize = 100,
                    maxConcurrency = 2,
                    cacheConfig = FFetchCacheConfig.Default,
                )

            val ffetch = FFetch(URL("https://example.com/test.json"), originalContext)
            val modifiedFFetch = ffetch.cache(FFetchCacheConfig.CacheOnly)

            // Verify that only cache config changed, other properties preserved
            assertEquals(100, modifiedFFetch.context.chunkSize)
            assertEquals(2, modifiedFFetch.context.maxConcurrency)
            assertEquals(FFetchCacheConfig.CacheOnly, modifiedFFetch.context.cacheConfig)

            // Original context should be unchanged
            assertEquals(FFetchCacheConfig.Default, originalContext.cacheConfig)
        }

    @Test
    fun testComplexCacheConfigurationScenario() =
        runTest {
            val mockClient = createMockClient()

            val ffetch =
                FFetch("https://api.example.com/data.json")
                    .chunks(50)
                    .withHTTPClient(mockClient)
                    .cache(
                        FFetchCacheConfig(
                            cacheElseLoad = true,
                            maxAge = 7200,
                            ignoreServerCacheControl = true,
                        ),
                    )
                    .maxConcurrency(4)

            with(ffetch.context) {
                assertEquals(50, chunkSize)
                assertSame(mockClient, httpClient)
                assertTrue(cacheConfig.cacheElseLoad)
                assertEquals(7200L, cacheConfig.maxAge)
                assertTrue(cacheConfig.ignoreServerCacheControl)
                assertEquals(4, maxConcurrency)
            }
        }

    @Test
    fun testCacheConfigValidation() =
        runTest {
            val ffetch = FFetch("https://example.com/test.json")

            // Test with various valid cache configurations
            val validConfigs =
                listOf(
                    FFetchCacheConfig(),
                    FFetchCacheConfig(noCache = true),
                    FFetchCacheConfig(cacheOnly = true),
                    FFetchCacheConfig(cacheElseLoad = true),
                    FFetchCacheConfig(maxAge = 0),
                    FFetchCacheConfig(maxAge = Long.MAX_VALUE),
                    FFetchCacheConfig(
                        cacheElseLoad = true,
                        maxAge = 3600,
                        ignoreServerCacheControl = true,
                    ),
                )

            validConfigs.forEach { config ->
                val configuredFFetch = ffetch.cache(config)
                assertEquals(config, configuredFFetch.context.cacheConfig)
            }
        }
}
