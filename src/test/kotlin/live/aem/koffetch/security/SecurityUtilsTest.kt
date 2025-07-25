//
// SecurityUtilsTest.kt
// KotlinFFetch
//
// Direct unit tests for SecurityUtils functions to achieve precise branch coverage
//

package live.aem.koffetch.security

import live.aem.koffetch.FFetch
import live.aem.koffetch.FFetchContext
import live.aem.koffetch.extensions.allow
import live.aem.koffetch.extensions.internal.isHostnameAllowed
import live.aem.koffetch.mock.MockFFetchHTTPClient
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecurityUtilsTest {
    private val mockHttpClient = MockFFetchHTTPClient()

    @Test
    fun testIsHostnameAllowedWithWildcard() {
        val ffetch =
            FFetch(
                URL("https://example.com/test.json"),
                FFetchContext(httpClient = mockHttpClient),
            ).allow("*")

        val testUrl = URL("https://evil.com/test.html")
        assertTrue(ffetch.isHostnameAllowed(testUrl))
    }

    @Test
    fun testIsHostnameAllowedWithNullHostname() {
        val ffetch =
            FFetch(
                URL("https://example.com/test.json"),
                FFetchContext(httpClient = mockHttpClient),
            ).allow("example.com")

        // File URLs have null hostname
        val fileUrl = URL("file:///etc/passwd")
        assertFalse(ffetch.isHostnameAllowed(fileUrl))
    }

    @Test
    fun testIsHostnameAllowedWithExplicitDefaultPorts() {
        val ffetch =
            FFetch(
                URL("https://example.com/test.json"),
                FFetchContext(httpClient = mockHttpClient),
            ).allow("example.com")

        // HTTP with explicit default port 80
        val httpUrlPort80 = URL("http://example.com:80/test.html")
        assertTrue(ffetch.isHostnameAllowed(httpUrlPort80))

        // HTTPS with explicit default port 443
        val httpsUrlPort443 = URL("https://example.com:443/test.html")
        assertTrue(ffetch.isHostnameAllowed(httpsUrlPort443))
    }

    @Test
    fun testIsHostnameAllowedWithNonDefaultPorts() {
        val ffetch =
            FFetch(
                URL("https://example.com/test.json"),
                FFetchContext(httpClient = mockHttpClient),
            ).allow("example.com:8080")

        // Non-default port should check hostname:port combination
        val urlWithPort = URL("https://example.com:8080/test.html")
        assertTrue(ffetch.isHostnameAllowed(urlWithPort))

        // Same hostname but different port should fail
        val urlWithDifferentPort = URL("https://example.com:9000/test.html")
        assertFalse(ffetch.isHostnameAllowed(urlWithDifferentPort))
    }

    @Test
    fun testIsHostnameAllowedWithHttpProtocol() {
        val ffetch =
            FFetch(
                URL("http://example.com/test.json"),
                FFetchContext(httpClient = mockHttpClient),
            ).allow("example.com")

        // HTTP protocol should work (tests the "http" branch in getDefaultPort)
        val httpUrl = URL("http://example.com/test.html")
        assertTrue(ffetch.isHostnameAllowed(httpUrl))
    }

    @Test
    fun testIsHostnameAllowedWithUnsupportedProtocol() {
        val ffetch =
            FFetch(
                URL("https://example.com/test.json"),
                FFetchContext(httpClient = mockHttpClient),
            ).allow("example.com")

        // Unsupported protocol tests the "else" branch in getDefaultPort (-1)
        // But the hostname is still allowed, so these should pass (hostname matching wins)
        val ftpUrl = URL("ftp://example.com/test.txt")
        assertTrue(ffetch.isHostnameAllowed(ftpUrl))

        // Use another supported but non-HTTP/HTTPS protocol
        val jarUrl = URL("jar:file:/path/to/file.jar!/resource")
        // This will have null hostname, so should fail like file:// URLs
        assertFalse(ffetch.isHostnameAllowed(jarUrl))

        // But different hostname with unsupported protocol should fail
        val ftpDifferentHostUrl = URL("ftp://other.com/test.txt")
        assertFalse(ffetch.isHostnameAllowed(ftpDifferentHostUrl))
    }

    @Test
    fun testIsHostnameAllowedWithUnsupportedProtocolAndPort() {
        val ffetch =
            FFetch(
                URL("https://example.com/test.json"),
                FFetchContext(httpClient = mockHttpClient),
            ).allow("example.com:2121")

        // Unsupported protocol with explicit port - getDefaultPort returns -1
        // Since port != -1 (it's 2121) AND port != defaultPort (-1),
        // it should check for "hostname:port" combination
        val ftpWithPortUrl = URL("ftp://example.com:2121/test.txt")
        assertTrue(ffetch.isHostnameAllowed(ftpWithPortUrl))

        // Same host but different port should fail
        val ftpDifferentPortUrl = URL("ftp://example.com:2122/test.txt")
        assertFalse(ffetch.isHostnameAllowed(ftpDifferentPortUrl))
    }

    @Test
    fun testIsHostnameAllowedExactHostnameMatch() {
        val ffetch =
            FFetch(
                URL("https://example.com/test.json"),
                FFetchContext(httpClient = mockHttpClient),
            ).allow("example.com")

        // Exact hostname match should work
        val sameHostUrl = URL("https://example.com/test.html")
        assertTrue(ffetch.isHostnameAllowed(sameHostUrl))

        // Different hostname should fail
        val differentHostUrl = URL("https://evil.com/test.html")
        assertFalse(ffetch.isHostnameAllowed(differentHostUrl))
    }

    @Test
    fun testIsHostnameAllowedCaseInsensitiveProtocol() {
        val ffetch =
            FFetch(
                URL("https://example.com/test.json"),
                FFetchContext(httpClient = mockHttpClient),
            ).allow("example.com")

        // Protocol should be case-insensitive (tests protocol.lowercase())
        val uppercaseHttpUrl = URL("HTTP://example.com/test.html")
        assertTrue(ffetch.isHostnameAllowed(uppercaseHttpUrl))

        val mixedCaseHttpsUrl = URL("HtTpS://example.com/test.html")
        assertTrue(ffetch.isHostnameAllowed(mixedCaseHttpsUrl))
    }
}
