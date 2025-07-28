//
// DefaultFFetchHTMLParserDirectTest.kt  
// KotlinFFetch
//
// Direct tests for DefaultFFetchHTMLParser exception handling
//

package live.aem.koffetch.html

import live.aem.koffetch.DefaultFFetchHTMLParser
import live.aem.koffetch.FFetchError
import live.aem.koffetch.FFetchHTMLParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultFFetchHTMLParserDirectTest {
    
    // Since DefaultFFetchHTMLParser catches IllegalArgumentException and OutOfMemoryError
    // from Jsoup.parse(), we need to find inputs that might trigger these exceptions.
    // However, Jsoup is very robust and rarely throws these exceptions.
    // The best we can do is test with extreme inputs that might trigger edge cases.
    
    @Test
    fun testParserWithMockWrapper() {
        // Create a wrapper that can inject exceptions
        class TestableHTMLParser : FFetchHTMLParser {
            var shouldThrowIllegalArgument = false
            var shouldThrowOutOfMemory = false
            
            override fun parse(html: String): Document {
                return try {
                    when {
                        shouldThrowIllegalArgument -> throw IllegalArgumentException("Test exception")
                        shouldThrowOutOfMemory -> throw OutOfMemoryError("Test OOM")
                        else -> Jsoup.parse(html)
                    }
                } catch (e: IllegalArgumentException) {
                    throw FFetchError.DecodingError(e)
                } catch (e: OutOfMemoryError) {
                    throw FFetchError.DecodingError(e)
                }
            }
        }
        
        val testParser = TestableHTMLParser()
        
        // Test IllegalArgumentException handling
        testParser.shouldThrowIllegalArgument = true
        val illegalArgException = assertFailsWith<FFetchError.DecodingError> {
            testParser.parse("<html></html>")
        }
        assertTrue(illegalArgException.cause is IllegalArgumentException)
        
        // Test OutOfMemoryError handling
        testParser.shouldThrowIllegalArgument = false // Reset flag
        testParser.shouldThrowOutOfMemory = true
        val oomException = assertFailsWith<FFetchError.DecodingError> {
            testParser.parse("<html></html>")
        }
        assertTrue(oomException.cause is OutOfMemoryError)
        
        // Test normal operation
        testParser.shouldThrowIllegalArgument = false
        testParser.shouldThrowOutOfMemory = false
        val doc = testParser.parse("<html><body>Test</body></html>")
        assertNotNull(doc)
    }
    
    @Test
    fun testDefaultParserRobustness() {
        // Test the actual DefaultFFetchHTMLParser with various edge cases
        val parser = DefaultFFetchHTMLParser()
        
        // While we can't directly trigger the exceptions, we can ensure
        // the parser handles all edge cases gracefully
        val edgeCaseInputs = listOf(
            "", // Empty
            "   ", // Whitespace
            "\u0000", // Null byte
            "\uFFFF", // High unicode
            "<" + "x".repeat(1_000_000) + ">", // Large tag
            (1..1000).joinToString("") { "<div>" } + (1..1000).joinToString("") { "</div>" }, // Deep nesting
            "<!DOCTYPE html>" + "x".repeat(100_000), // Large content
            "<html" + (1..1000).joinToString("") { " attr$it='value$it'" } + ">", // Many attributes
            "<html><body>" + (1..10000).joinToString("") { "<p>Paragraph $it</p>" } + "</body></html>" // Many elements
        )
        
        edgeCaseInputs.forEach { input ->
            try {
                val doc = parser.parse(input)
                assertNotNull(doc, "Parser should not return null")
            } catch (e: FFetchError.DecodingError) {
                // If an exception is thrown, it should be properly wrapped
                assertNotNull(e.cause, "DecodingError should have a cause")
                assertTrue(
                    e.cause is IllegalArgumentException || e.cause is OutOfMemoryError,
                    "Cause should be IllegalArgumentException or OutOfMemoryError"
                )
            }
        }
    }
    
    @Test 
    fun testActualDefaultFFetchHTMLParser() {
        // Direct test of DefaultFFetchHTMLParser
        val parser = DefaultFFetchHTMLParser()
        
        // Test normal parsing
        val normalHtml = "<html><head><title>Test</title></head><body>Content</body></html>"
        val doc = parser.parse(normalHtml)
        assertNotNull(doc)
        assertTrue(doc.title() == "Test")
        assertTrue(doc.body().text() == "Content")
        
        // Test empty input
        val emptyDoc = parser.parse("")
        assertNotNull(emptyDoc)
        
        // Test malformed input
        val malformedDoc = parser.parse("<div><p>unclosed")
        assertNotNull(malformedDoc)
        
        // Test special characters
        val specialDoc = parser.parse("<p>&amp;&lt;&gt;</p>")
        assertNotNull(specialDoc)
        assertTrue(specialDoc.text().contains("&"))
        assertTrue(specialDoc.text().contains("<"))
        assertTrue(specialDoc.text().contains(">"))
    }
}
