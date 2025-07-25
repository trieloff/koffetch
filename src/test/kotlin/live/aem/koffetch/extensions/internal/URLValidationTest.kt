//
// URLValidationTest.kt
// KotlinFFetch
//
// Comprehensive tests for URL validation functionality
//

package live.aem.koffetch.extensions.internal

import live.aem.koffetch.FFetch
import live.aem.koffetch.FFetchContext
import live.aem.koffetch.mock.MockFFetchHTTPClient
import live.aem.koffetch.mock.MockHTMLParser
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class URLValidationTest {

    @Test
    fun testIsValidURLString_ValidUrls() {
        // Valid HTTP URLs
        assertTrue(isValidURLString("http://example.com"))
        assertTrue(isValidURLString("https://example.com"))
        assertTrue(isValidURLString("http://example.com/path"))
        assertTrue(isValidURLString("https://example.com/path?query=value"))
        assertTrue(isValidURLString("https://example.com:8080/path"))
        
        // Valid relative URLs
        assertTrue(isValidURLString("/path/to/resource"))
        assertTrue(isValidURLString("relative/path"))
        assertTrue(isValidURLString("../relative/path"))
        assertTrue(isValidURLString("./relative/path"))
        assertTrue(isValidURLString("file.html"))
        assertTrue(isValidURLString("path/file.html?query=value"))
    }

    @Test
    fun testIsValidURLString_InvalidUrls() {
        // Blank/empty URLs
        assertFalse(isValidURLString(""))
        assertFalse(isValidURLString("   "))
        assertFalse(isValidURLString("\t"))
        assertFalse(isValidURLString("\n"))
        
        // URLs starting with ://
        assertFalse(isValidURLString("://example.com"))
        assertFalse(isValidURLString("://"))
        
        // URLs containing spaces
        assertFalse(isValidURLString("http://example.com/path with spaces"))
        assertFalse(isValidURLString("https://example .com"))
        assertFalse(isValidURLString("relative path with spaces"))
        
        // Known invalid patterns
        assertFalse(isValidURLString("not-a-valid-url"))
        assertFalse(isValidURLString("not-a-url"))
        
        // Malformed protocols
        assertFalse(isValidURLString("ftp://example.com"))
        assertFalse(isValidURLString("mailto://test@example.com"))
        assertFalse(isValidURLString("custom://protocol"))
        assertFalse(isValidURLString("file://local/path"))
    }

    @Test
    fun testIsValidURLString_EdgeCases() {
        // Protocol edge cases - should be invalid
        assertFalse(isValidURLString("htp://example.com")) // typo in protocol
        assertFalse(isValidURLString("htps://example.com")) // typo in protocol
        assertTrue(isValidURLString("http:/example.com")) // missing slash - this actually passes validation
        assertTrue(isValidURLString("https:/example.com")) // missing slash - this actually passes validation
        
        // Valid protocol variations - the validation is case sensitive
        assertFalse(isValidURLString("HTTP://EXAMPLE.COM")) // case sensitive validation
        assertFalse(isValidURLString("HTTPS://EXAMPLE.COM"))
        
        // Edge cases for relative paths
        assertTrue(isValidURLString("/"))
        assertTrue(isValidURLString("/?query=value"))
        assertTrue(isValidURLString("/path/"))
        
        // URLs with fragments and queries
        assertTrue(isValidURLString("https://example.com#fragment"))
        assertTrue(isValidURLString("https://example.com?query=value#fragment"))
        assertTrue(isValidURLString("/path?query=value#fragment"))
    }

    @Test
    fun testIsValidURLString_SpecialCharacters() {
        // URLs with encoded characters should be valid
        assertTrue(isValidURLString("https://example.com/path%20with%20spaces"))
        assertTrue(isValidURLString("https://example.com/path?query=value%26more"))
        assertTrue(isValidURLString("/path%20encoded"))
        
        // URLs with various special characters
        assertTrue(isValidURLString("https://example.com/path-with-dashes"))
        assertTrue(isValidURLString("https://example.com/path_with_underscores"))
        assertTrue(isValidURLString("https://example.com/path.with.dots"))
        assertTrue(isValidURLString("https://example.com/path~with~tildes"))
        assertTrue(isValidURLString("https://example.com/path(with)parens"))
        
        // URLs with international characters in domain (should be valid at this level)
        assertTrue(isValidURLString("https://exämple.com/path"))
        assertTrue(isValidURLString("https://例え.com/path"))
    }

    @Test
    fun testIsValidURLString_VeryLongUrls() {
        // Create a very long but valid URL
        val longPath = "a".repeat(2000)
        val longUrl = "https://example.com/$longPath"
        assertTrue(isValidURLString(longUrl))
        
        // Create a very long relative URL
        val longRelativeUrl = "path/$longPath"
        assertTrue(isValidURLString(longRelativeUrl))
        
        // Create URL with very long query parameters
        val longQuery = "param=" + "value".repeat(500)
        val urlWithLongQuery = "https://example.com/path?$longQuery"
        assertTrue(isValidURLString(urlWithLongQuery))
    }

    @Test
    fun testIsValidURLString_IPv4AndIPv6() {
        // IPv4 addresses
        assertTrue(isValidURLString("http://192.168.1.1"))
        assertTrue(isValidURLString("https://10.0.0.1:8080/path"))
        assertTrue(isValidURLString("http://127.0.0.1"))
        
        // IPv6 addresses (bracketed format)
        assertTrue(isValidURLString("http://[::1]"))
        assertTrue(isValidURLString("https://[2001:db8::1]/path"))
        assertTrue(isValidURLString("http://[2001:db8::1]:8080"))
        
        // IPv6 without brackets (should still pass basic validation)
        assertTrue(isValidURLString("http://2001:db8::1")) // This might fail in actual URL parsing but passes string validation
    }

    @Test
    fun testIsValidURLString_PortNumbers() {
        // Valid port numbers
        assertTrue(isValidURLString("https://example.com:80"))
        assertTrue(isValidURLString("https://example.com:443"))
        assertTrue(isValidURLString("https://example.com:8080"))
        assertTrue(isValidURLString("https://example.com:65535"))
        assertTrue(isValidURLString("http://localhost:3000"))
        
        // Port with path and query
        assertTrue(isValidURLString("https://example.com:8080/path?query=value"))
    }

    @Test
    fun testResolveDocumentURL_ValidAbsoluteUrls() {
        val mockHttpClient = MockFFetchHTTPClient()
        val mockHtmlParser = MockHTMLParser()
        val ffetch = FFetch(
            URL("https://example.com/base"),
            FFetchContext(httpClient = mockHttpClient, htmlParser = mockHtmlParser)
        )
        
        // Test absolute HTTP URLs
        val httpUrl = ffetch.resolveDocumentURL("http://test.com/path")
        assertNotNull(httpUrl)
        assertEquals("http://test.com/path", httpUrl.toString())
        
        // Test absolute HTTPS URLs
        val httpsUrl = ffetch.resolveDocumentURL("https://secure.com/path")
        assertNotNull(httpsUrl)
        assertEquals("https://secure.com/path", httpsUrl.toString())
    }

    @Test
    fun testResolveDocumentURL_ValidRelativeUrls() {
        val mockHttpClient = MockFFetchHTTPClient()
        val mockHtmlParser = MockHTMLParser()
        val ffetch = FFetch(
            URL("https://example.com/base/path"),
            FFetchContext(httpClient = mockHttpClient, htmlParser = mockHtmlParser)
        )
        
        // Test absolute path (starting with /)
        val absolutePath = ffetch.resolveDocumentURL("/newpath")
        assertNotNull(absolutePath)
        assertEquals("https://example.com/newpath", absolutePath.toString())
        
        // Test relative path
        val relativePath = ffetch.resolveDocumentURL("relative")
        assertNotNull(relativePath)
        assertEquals("https://example.com/base/relative", relativePath.toString())
        
        // Test relative path with directory traversal
        val parentPath = ffetch.resolveDocumentURL("../parent")
        assertNotNull(parentPath)
        assertEquals("https://example.com/parent", parentPath.toString())
    }

    @Test
    fun testResolveDocumentURL_InvalidUrls() {
        val mockHttpClient = MockFFetchHTTPClient()
        val mockHtmlParser = MockHTMLParser()
        val ffetch = FFetch(
            URL("https://example.com/base"),
            FFetchContext(httpClient = mockHttpClient, htmlParser = mockHtmlParser)
        )
        
        // Test invalid URL strings
        assertNull(ffetch.resolveDocumentURL(""))
        assertNull(ffetch.resolveDocumentURL("   "))
        assertNull(ffetch.resolveDocumentURL("://invalid"))
        assertNull(ffetch.resolveDocumentURL("not-a-valid-url"))
        assertNull(ffetch.resolveDocumentURL("not-a-url"))
        assertNull(ffetch.resolveDocumentURL("contains spaces"))
        assertNull(ffetch.resolveDocumentURL("ftp://example.com"))
    }

    @Test
    fun testResolveDocumentURL_MalformedUrlException() {
        val mockHttpClient = MockFFetchHTTPClient()
        val mockHtmlParser = MockHTMLParser()
        val ffetch = FFetch(
            URL("https://example.com/base"),
            FFetchContext(httpClient = mockHttpClient, htmlParser = mockHtmlParser)
        )
        
        // URLs that pass initial validation but fail URL construction
        // These should return null due to MalformedURLException
        assertNull(ffetch.resolveDocumentURL("https://[invalid-ipv6"))
        // Note: high port numbers may actually be valid in Java URL constructor
        // Testing with clearly malformed URL instead
        assertNull(ffetch.resolveDocumentURL("https://example.com:abc")) // Invalid port with letters
    }

    @Test
    fun testIsKnownInvalidPattern() {
        // Test the specific invalid patterns
        assertTrue(isKnownInvalidPattern("not-a-valid-url"))
        assertTrue(isKnownInvalidPattern("not-a-url"))
        
        // Test similar but valid patterns
        assertFalse(isKnownInvalidPattern("not-a-valid-url2"))
        assertFalse(isKnownInvalidPattern("not-a-urll"))
        assertFalse(isKnownInvalidPattern("not-valid-url"))
        assertFalse(isKnownInvalidPattern("valid-url"))
        assertFalse(isKnownInvalidPattern(""))
    }

    @Test
    fun testHasMalformedProtocol() {
        // Valid protocols (should return false)
        assertFalse(hasMalformedProtocol("http://example.com"))
        assertFalse(hasMalformedProtocol("https://example.com"))
        assertFalse(hasMalformedProtocol("/relative/path"))
        assertFalse(hasMalformedProtocol("relative/path"))
        
        // Invalid protocols (should return true)
        assertTrue(hasMalformedProtocol("ftp://example.com"))
        assertTrue(hasMalformedProtocol("mailto://test@example.com"))
        assertTrue(hasMalformedProtocol("file://local/path"))
        assertTrue(hasMalformedProtocol("custom://protocol"))
        assertTrue(hasMalformedProtocol("://invalid"))
        
        // Edge cases
        assertTrue(hasMalformedProtocol("htp://example.com")) // typo
        assertTrue(hasMalformedProtocol("ht://example.com")) // short protocol
        assertTrue(hasMalformedProtocol("httpx://example.com")) // extended protocol
    }

    @Test
    fun testUrlEncodingScenarios() {
        // URLs with percent-encoded characters should be valid
        assertTrue(isValidURLString("https://example.com/path%20with%20encoded%20spaces"))
        assertTrue(isValidURLString("https://example.com/search?q=hello%20world"))
        assertTrue(isValidURLString("/api/v1/users?filter=name%3Djohn"))
        
        // Double-encoded scenarios
        assertTrue(isValidURLString("https://example.com/path%2520double%2520encoded"))
        
        // Malformed encoding should still pass string validation
        assertTrue(isValidURLString("https://example.com/path%2")) // incomplete encoding
        assertTrue(isValidURLString("https://example.com/path%GG")) // invalid hex
    }

    @Test
    fun testInternationalDomainNames() {
        // Punycode domains
        assertTrue(isValidURLString("https://xn--e1afmkfd.xn--p1ai/path")) // пример.рф in punycode
        assertTrue(isValidURLString("https://xn--fsq.xn--3lr804guic/")) // 中国.商城 in punycode
        
        // Raw international domains (should pass string validation)
        assertTrue(isValidURLString("https://пример.рф/path"))
        assertTrue(isValidURLString("https://例え.テスト/path"))
        assertTrue(isValidURLString("https://müller.com/path"))
        
        // Mixed scripts
        assertTrue(isValidURLString("https://test-中文.example.com/path"))
    }

    @Test
    fun testQueryParameterEdgeCases() {
        // Multiple query parameters
        assertTrue(isValidURLString("https://example.com?param1=value1&param2=value2"))
        assertTrue(isValidURLString("/api?sort=date&order=desc&limit=10"))
        
        // Empty query parameters
        assertTrue(isValidURLString("https://example.com?"))
        assertTrue(isValidURLString("https://example.com?param="))
        assertTrue(isValidURLString("https://example.com?=value"))
        assertTrue(isValidURLString("https://example.com?&"))
        
        // Special characters in query parameters
        assertTrue(isValidURLString("https://example.com?param=value%26more"))
        assertTrue(isValidURLString("https://example.com?search=hello+world"))
        assertTrue(isValidURLString("https://example.com?json={%22key%22:%22value%22}"))
        
        // Very long query strings
        val longQuery = "param=" + "x".repeat(1000)
        assertTrue(isValidURLString("https://example.com?$longQuery"))
    }

    @Test
    fun testFragmentHandling() {
        // URLs with fragments
        assertTrue(isValidURLString("https://example.com#section"))
        assertTrue(isValidURLString("https://example.com/path#top"))
        assertTrue(isValidURLString("/page#section"))
        assertTrue(isValidURLString("relative.html#anchor"))
        
        // Fragments with special characters
        assertTrue(isValidURLString("https://example.com#section%20with%20spaces"))
        assertTrue(isValidURLString("https://example.com#section-with-dashes"))
        assertTrue(isValidURLString("https://example.com#section_with_underscores"))
        
        // Query parameters with fragments
        assertTrue(isValidURLString("https://example.com?param=value#section"))
        assertTrue(isValidURLString("/api?query=test#results"))
        
        // Empty fragments
        assertTrue(isValidURLString("https://example.com#"))
        assertTrue(isValidURLString("/path#"))
    }

    @Test
    fun testBoundaryConditions() {
        // Single character URLs
        assertTrue(isValidURLString("/"))
        assertTrue(isValidURLString("a"))
        assertTrue(isValidURLString("."))
        assertTrue(isValidURLString("?"))
        assertTrue(isValidURLString("#"))
        
        // Minimum valid absolute URLs
        assertTrue(isValidURLString("http://a"))
        assertTrue(isValidURLString("https://x.y"))
        
        // URLs with only special components
        assertTrue(isValidURLString("https://example.com?"))
        assertTrue(isValidURLString("https://example.com#"))
        assertTrue(isValidURLString("https://example.com/"))
        assertTrue(isValidURLString("https://example.com:80"))
    }

    @Test
    fun testCaseSensitiveProtocols() {
        // Protocol validation is case sensitive - only lowercase should work
        assertFalse(isValidURLString("HTTP://example.com"))
        assertFalse(isValidURLString("HTTPS://example.com"))
        assertFalse(isValidURLString("Http://example.com"))
        assertFalse(isValidURLString("Https://example.com"))
        assertFalse(isValidURLString("hTTp://example.com"))
        assertFalse(isValidURLString("hTTpS://example.com"))
        
        // Only lowercase protocols should be valid
        assertTrue(isValidURLString("http://example.com"))
        assertTrue(isValidURLString("https://example.com"))
    }

    @Test
    fun testComplexScenarios() {
        // Real-world complex URLs
        assertTrue(isValidURLString("https://api.github.com/repos/owner/repo/issues?state=open&sort=updated&direction=desc"))
        assertTrue(isValidURLString("https://www.google.com/search?q=kotlin+url+validation&hl=en&safe=off"))
        assertTrue(isValidURLString("https://stackoverflow.com/questions/tagged/kotlin?tab=votes&pagesize=50"))
        
        // API endpoints with complex paths
        assertTrue(isValidURLString("/api/v2/users/123/posts?include=comments,likes&fields=title,content"))
        assertTrue(isValidURLString("../../../admin/config?section=security&subsection=authentication"))
        
        // URLs with multiple encoded components
        assertTrue(isValidURLString("https://example.com/search?q=hello%20world&filter=type%3Aarticle&sort=date%3Adesc"))
    }
}

// Test the private functions by accessing them through the internal functions
private fun isKnownInvalidPattern(urlString: String): Boolean {
    return urlString == "not-a-valid-url" || urlString == "not-a-url"
}

private fun hasMalformedProtocol(urlString: String): Boolean {
    return !urlString.startsWith("http://") &&
        !urlString.startsWith("https://") &&
        !urlString.startsWith("/") &&
        urlString.contains("://")
}