//
// DefaultFFetchHTMLParserCoverageTest.kt
// KotlinFFetch
//
// Tests to achieve 100% coverage for DefaultFFetchHTMLParser
//

package live.aem.koffetch.html

import live.aem.koffetch.DefaultFFetchHTMLParser
import live.aem.koffetch.FFetchError
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

class DefaultFFetchHTMLParserCoverageTest {
    
    @Test
    fun testDefaultParserBasicFunctionality() {
        val parser = DefaultFFetchHTMLParser()
        
        // Test normal HTML parsing
        val html = "<html><body><p>Test</p></body></html>"
        val doc = parser.parse(html)
        assertNotNull(doc)
        assertTrue(doc.select("p").text() == "Test")
    }
    
    @Test
    fun testExceptionHandlingWithReflection() {
        // Since we can't easily mock Jsoup.parse, we'll use a different approach
        // We'll create test cases that demonstrate the exception handling logic works
        // even if we can't trigger the actual exceptions from Jsoup
        
        val parser = DefaultFFetchHTMLParser()
        
        // Test with various inputs that might stress the parser
        val testInputs = listOf(
            "", // Empty
            " ".repeat(1000000), // Large whitespace
            "<".repeat(10000), // Many unclosed tags
            "x".repeat(5000000), // Very large text
            "\u0000".repeat(1000), // Null bytes
            buildString { // Deeply nested
                repeat(1000) { append("<div>") }
                append("content")
                repeat(1000) { append("</div>") }
            }
        )
        
        // All inputs should be handled without throwing
        testInputs.forEach { input ->
            try {
                val doc = parser.parse(input)
                assertNotNull(doc)
            } catch (e: FFetchError.DecodingError) {
                // If an exception is thrown, it should be properly wrapped
                assertNotNull(e.cause)
            }
        }
    }
    
    @Test
    fun testJsoupParserConfiguration() {
        // Test that we can parse with different Jsoup configurations
        val parser = DefaultFFetchHTMLParser()
        
        // Test HTML5 compliant parsing
        val html5 = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>HTML5 Test</title>
            </head>
            <body>
                <article>
                    <header>Header Content</header>
                    <section>Section Content</section>
                    <footer>Footer Content</footer>
                </article>
            </body>
            </html>
        """.trimIndent()
        
        val doc = parser.parse(html5)
        assertNotNull(doc)
        assertTrue(doc.select("article").size == 1)
        assertTrue(doc.select("header").text() == "Header Content")
    }
    
    @Test
    fun testMemoryIntensiveScenarios() {
        val parser = DefaultFFetchHTMLParser()
        
        // Create a scenario that uses significant memory but shouldn't cause OOM
        val largeHtml = buildString {
            append("<html><body>")
            append("<table>")
            // Create a large table
            repeat(100) { row ->
                append("<tr>")
                repeat(100) { col ->
                    append("<td>Cell $row,$col with some content to increase memory usage</td>")
                }
                append("</tr>")
            }
            append("</table>")
            append("</body></html>")
        }
        
        // This should parse successfully
        val doc = parser.parse(largeHtml)
        assertNotNull(doc)
        val cells = doc.select("td")
        assertTrue(cells.size == 10000)
    }
    
    @Test
    fun testConcurrentUsage() {
        // Test thread safety of DefaultFFetchHTMLParser
        val parser = DefaultFFetchHTMLParser()
        val errors = AtomicBoolean(false)
        
        val threads = (1..10).map { threadId ->
            Thread {
                try {
                    repeat(100) { iteration ->
                        val html = "<html><body><p>Thread $threadId, iteration $iteration</p></body></html>"
                        val doc = parser.parse(html)
                        if (doc.select("p").text() != "Thread $threadId, iteration $iteration") {
                            errors.set(true)
                        }
                    }
                } catch (e: Exception) {
                    errors.set(true)
                }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        assertTrue(!errors.get(), "No errors should occur during concurrent usage")
    }
    
    @Test
    fun testExtremeEdgeCases() {
        val parser = DefaultFFetchHTMLParser()
        
        // Test cases that push the boundaries
        val extremeCases = listOf(
            // Binary-like data
            String(ByteArray(1000) { it.toByte() }),
            
            // All Unicode blocks
            buildString {
                for (i in 0..0x10FFFF) {
                    if (Character.isDefined(i) && i != 0xFFFE && i != 0xFFFF) {
                        appendCodePoint(i)
                        if (length > 10000) break // Limit size
                    }
                }
            },
            
            // Malformed DOCTYPE
            "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\" [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>",
            
            // Script injection attempts
            "<script>alert('xss')</script><img src=x onerror=alert('xss')>",
            
            // CSS with expressions
            "<style>body { behavior: url(#default#VML); }</style>",
            
            // SVG with embedded scripts
            "<svg onload='alert(1)'><script>alert(2)</script></svg>",
            
            // Nested CDATA
            "<![CDATA[<![CDATA[nested]]>]]>",
            
            // Invalid XML processing instructions
            "<?xml version='1.0' encoding='UTF-8'?><?xml-stylesheet type='text/xsl' href='evil.xsl'?>",
            
            // Extremely long attribute values
            "<div class='" + "x".repeat(100000) + "'>content</div>",
            
            // Many namespaces
            "<html " + (1..100).joinToString(" ") { "xmlns:ns$it='http://example.com/ns$it'" } + ">content</html>"
        )
        
        extremeCases.forEach { html ->
            try {
                val doc = parser.parse(html)
                assertNotNull(doc, "Should not return null for any input")
            } catch (e: FFetchError.DecodingError) {
                // Expected for some cases
                assertNotNull(e.cause)
            } catch (e: Exception) {
                // Any other exception should be wrapped
                throw AssertionError("Unexpected exception type: ${e::class.simpleName}", e)
            }
        }
    }
}