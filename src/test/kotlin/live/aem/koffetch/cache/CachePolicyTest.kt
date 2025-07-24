//
// CachePolicyTest.kt
// KotlinFFetch
//
// Tests for cache policy enforcement and configuration
//

package live.aem.koffetch.cache

import live.aem.koffetch.*
import kotlin.test.*

class CachePolicyTest {
    @Test
    fun testDefaultCacheConfigBehavior() {
        val config = FFetchCacheConfig.Default

        assertFalse(config.noCache)
        assertFalse(config.cacheOnly)
        assertFalse(config.cacheElseLoad)
        assertNull(config.maxAge)
        assertFalse(config.ignoreServerCacheControl)
    }

    @Test
    fun testNoCacheConfigBehavior() {
        val config = FFetchCacheConfig.NoCache

        assertTrue(config.noCache)
        assertFalse(config.cacheOnly)
        assertFalse(config.cacheElseLoad)
        assertNull(config.maxAge)
        assertFalse(config.ignoreServerCacheControl)
    }

    @Test
    fun testCacheOnlyConfigBehavior() {
        val config = FFetchCacheConfig.CacheOnly

        assertFalse(config.noCache)
        assertTrue(config.cacheOnly)
        assertFalse(config.cacheElseLoad)
        assertNull(config.maxAge)
        assertFalse(config.ignoreServerCacheControl)
    }

    @Test
    fun testCacheElseLoadConfigBehavior() {
        val config = FFetchCacheConfig.CacheElseLoad

        assertFalse(config.noCache)
        assertFalse(config.cacheOnly)
        assertTrue(config.cacheElseLoad)
        assertNull(config.maxAge)
        assertFalse(config.ignoreServerCacheControl)
    }

    @Test
    fun testCustomCacheConfigWithMaxAge() {
        val config = FFetchCacheConfig(maxAge = 3600)

        assertFalse(config.noCache)
        assertFalse(config.cacheOnly)
        assertFalse(config.cacheElseLoad)
        assertEquals(3600L, config.maxAge)
        assertFalse(config.ignoreServerCacheControl)
    }

    @Test
    fun testCacheConfigWithIgnoreServerCacheControl() {
        val config = FFetchCacheConfig(ignoreServerCacheControl = true)

        assertFalse(config.noCache)
        assertFalse(config.cacheOnly)
        assertFalse(config.cacheElseLoad)
        assertNull(config.maxAge)
        assertTrue(config.ignoreServerCacheControl)
    }

    @Test
    fun testCombinedCacheConfigOptions() {
        val config =
            FFetchCacheConfig(
                cacheElseLoad = true,
                maxAge = 1800,
                ignoreServerCacheControl = true,
            )

        assertFalse(config.noCache)
        assertFalse(config.cacheOnly)
        assertTrue(config.cacheElseLoad)
        assertEquals(1800L, config.maxAge)
        assertTrue(config.ignoreServerCacheControl)
    }

    @Test
    fun testCacheConfigDataClassBehavior() {
        val config1 = FFetchCacheConfig(noCache = true, maxAge = 300)
        val config2 = FFetchCacheConfig(noCache = true, maxAge = 300)
        val config3 = FFetchCacheConfig(noCache = false, maxAge = 300)

        // Test equality
        assertEquals(config1, config2)
        assertNotEquals(config1, config3)

        // Test hashCode
        assertEquals(config1.hashCode(), config2.hashCode())
        assertNotEquals(config1.hashCode(), config3.hashCode())

        // Test toString
        assertTrue(config1.toString().contains("noCache=true"))
        assertTrue(config1.toString().contains("maxAge=300"))
    }

    @Test
    fun testCacheConfigCopy() {
        val original =
            FFetchCacheConfig(
                noCache = true,
                maxAge = 600,
                ignoreServerCacheControl = true,
            )

        val copied = original.copy(noCache = false)

        assertFalse(copied.noCache)
        assertEquals(600L, copied.maxAge)
        assertTrue(copied.ignoreServerCacheControl)

        // Original should be unchanged
        assertTrue(original.noCache)
    }

    @Test
    fun testInvalidCacheConfigCombinations() {
        // These combinations are logically contradictory but should be allowed
        // Implementation should handle the precedence

        val noCacheAndCacheOnly =
            FFetchCacheConfig(
                noCache = true,
                cacheOnly = true,
            )

        assertTrue(noCacheAndCacheOnly.noCache)
        assertTrue(noCacheAndCacheOnly.cacheOnly)

        val cacheOnlyAndCacheElseLoad =
            FFetchCacheConfig(
                cacheOnly = true,
                cacheElseLoad = true,
            )

        assertTrue(cacheOnlyAndCacheElseLoad.cacheOnly)
        assertTrue(cacheOnlyAndCacheElseLoad.cacheElseLoad)
    }

    @Test
    fun testMaxAgeValidation() {
        // Test various maxAge values
        val configs =
            listOf(
                FFetchCacheConfig(maxAge = 0L),
                FFetchCacheConfig(maxAge = 1L),
                FFetchCacheConfig(maxAge = 86400L), // 1 day
                FFetchCacheConfig(maxAge = 604800L), // 1 week
                FFetchCacheConfig(maxAge = Long.MAX_VALUE),
            )

        configs.forEach { config ->
            assertNotNull(config.maxAge)
            assertTrue(config.maxAge!! >= 0)
        }
    }

    @Test
    fun testCacheConfigCompanionObjects() {
        // Test that companion objects are properly configured
        assertNotNull(FFetchCacheConfig.Default)
        assertNotNull(FFetchCacheConfig.NoCache)
        assertNotNull(FFetchCacheConfig.CacheOnly)
        assertNotNull(FFetchCacheConfig.CacheElseLoad)

        // Test that they are different instances
        assertNotSame(FFetchCacheConfig.Default, FFetchCacheConfig.NoCache)
        assertNotSame(FFetchCacheConfig.NoCache, FFetchCacheConfig.CacheOnly)
        assertNotSame(FFetchCacheConfig.CacheOnly, FFetchCacheConfig.CacheElseLoad)
    }

    @Test
    fun testCacheConfigImmutability() {
        val config = FFetchCacheConfig.Default

        // Config should be immutable - these should create new instances
        val modified1 = config.copy(noCache = true)
        val modified2 = config.copy(maxAge = 300)

        assertNotSame(config, modified1)
        assertNotSame(config, modified2)
        assertNotSame(modified1, modified2)

        // Original should be unchanged
        assertFalse(config.noCache)
        assertNull(config.maxAge)
    }

    @Test
    fun testCacheConfigSerialization() {
        // Test that the data class properties are accessible
        val config =
            FFetchCacheConfig(
                noCache = true,
                cacheOnly = false,
                cacheElseLoad = true,
                maxAge = 1200,
                ignoreServerCacheControl = true,
            )

        // Test property access
        assertTrue(config.noCache)
        assertFalse(config.cacheOnly)
        assertTrue(config.cacheElseLoad)
        assertEquals(1200L, config.maxAge)
        assertTrue(config.ignoreServerCacheControl)
    }
}
