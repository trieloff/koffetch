//
// DefaultFFetchHTMLParserTest.kt
// KotlinFFetch
//
// Tests specifically targeting DefaultFFetchHTMLParser edge cases and error handling
//

package live.aem.koffetch.html

import live.aem.koffetch.DefaultFFetchHTMLParser
import live.aem.koffetch.FFetchError
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

class DefaultFFetchHTMLParserTest {
    private val parser = DefaultFFetchHTMLParser()


    @Test
    fun testNormalParsingDoesNotThrowException() {
        // Test that normal HTML parsing works correctly
        val html = "<html><body><p>Normal content</p></body></html>"
        val document = parser.parse(html)
        
        assertNotNull(document)
        assertEquals("Normal content", document.select("p").text())
    }

    @Test
    fun testEmptyStringParsing() {
        // Test parsing of empty string
        val document = parser.parse("")
        
        assertNotNull(document)
        assertNotNull(document.body())
        assertEquals("", document.title())
    }

    @Test
    fun testWhitespaceOnlyParsing() {
        // Test parsing of whitespace-only string
        val document = parser.parse("   \n\t  ")
        
        assertNotNull(document)
        assertNotNull(document.body())
    }

    @Test
    fun testSpecialCharacterParsing() {
        // Test parsing with special characters that might cause issues
        val specialChars = "\u0000\u0001\u0002\u0003\u0004\u0005"
        val document = parser.parse(specialChars)
        
        assertNotNull(document)
        assertNotNull(document.body())
    }

    @Test
    fun testVeryLargeHTMLParsing() {
        // Test parsing a large HTML string
        val largeHtml = buildString {
            append("<html><body>")
            repeat(1000) { i ->
                append("<div id='div$i'>Content $i</div>")
            }
            append("</body></html>")
        }
        
        val document = parser.parse(largeHtml)
        
        assertNotNull(document)
        assertEquals(1000, document.select("div").size)
    }

    @Test
    fun testDeeplyNestedHTMLParsing() {
        // Test parsing deeply nested HTML
        val deepHtml = buildString {
            append("<html><body>")
            repeat(100) { append("<div>") }
            append("Deep content")
            repeat(100) { append("</div>") }
            append("</body></html>")
        }
        
        val document = parser.parse(deepHtml)
        
        assertNotNull(document)
        assertTrue(document.text().contains("Deep content"))
    }

    @Test
    fun testMalformedHTMLParsing() {
        // Test parsing malformed HTML - Jsoup should handle it gracefully
        val malformedHtml = "<html><body><div><p>Unclosed tags"
        val document = parser.parse(malformedHtml)
        
        assertNotNull(document)
        assertTrue(document.text().contains("Unclosed tags"))
    }

    @Test
    fun testHTMLWithInvalidCharacters() {
        // Test HTML with various invalid characters
        val invalidCharsHtml = "<html><body><p>\uFFFE\uFFFF\u0000</p></body></html>"
        val document = parser.parse(invalidCharsHtml)
        
        assertNotNull(document)
        assertNotNull(document.body())
    }

    @Test
    fun testConcurrentParsing() {
        // Test that the parser can be used concurrently
        val threads = List(10) { index ->
            Thread {
                val html = "<html><body><p>Thread $index content</p></body></html>"
                val document = parser.parse(html)
                assertEquals("Thread $index content", document.select("p").text())
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test
    fun testParsingPerformance() {
        // Test parsing performance with various HTML sizes
        val sizes = listOf(10, 100, 1000)
        
        sizes.forEach { size ->
            val html = buildString {
                append("<html><body>")
                repeat(size) { i ->
                    append("<div class='item-$i'>Item $i content</div>")
                }
                append("</body></html>")
            }
            
            val startTime = System.currentTimeMillis()
            val document = parser.parse(html)
            val parseTime = System.currentTimeMillis() - startTime
            
            assertNotNull(document)
            assertEquals(size, document.select("div").size)
            
            // Ensure parsing is reasonably fast
            assertTrue(parseTime < 1000, "Parsing $size elements took too long: ${parseTime}ms")
        }
    }

    @Test
    fun testHTMLEntityHandling() {
        // Test various HTML entities
        val entityHtml = """
            <html>
            <body>
                <p>&amp; &lt; &gt; &quot; &apos;</p>
                <p>&#65; &#66; &#67;</p>
                <p>&#x41; &#x42; &#x43;</p>
            </body>
            </html>
        """.trimIndent()
        
        val document = parser.parse(entityHtml)
        
        val paragraphs = document.select("p")
        assertEquals(3, paragraphs.size)
        
        // Check entity decoding
        assertTrue(paragraphs[0].text().contains("&"))
        assertTrue(paragraphs[0].text().contains("<"))
        assertTrue(paragraphs[0].text().contains(">"))
        assertTrue(paragraphs[0].text().contains("\""))
        
        // Check numeric entities
        assertEquals("A B C", paragraphs[1].text())
        
        // Check hex entities
        assertEquals("A B C", paragraphs[2].text())
    }

    @Test
    fun testUnicodeHandling() {
        // Test various Unicode characters
        val unicodeHtml = """
            <html>
            <head><title>Unicode 测试</title></head>
            <body>
                <p>English: Hello</p>
                <p>中文: 你好</p>
                <p>日本語: こんにちは</p>
                <p>한국어: 안녕하세요</p>
                <p>العربية: مرحبا</p>
                <p>Emoji: 😀 🎉 🚀</p>
            </body>
            </html>
        """.trimIndent()
        
        val document = parser.parse(unicodeHtml)
        
        assertEquals("Unicode 测试", document.title())
        
        val paragraphs = document.select("p")
        assertEquals(6, paragraphs.size)
        
        assertTrue(paragraphs[1].text().contains("你好"))
        assertTrue(paragraphs[2].text().contains("こんにちは"))
        assertTrue(paragraphs[3].text().contains("안녕하세요"))
        assertTrue(paragraphs[4].text().contains("مرحبا"))
        assertTrue(paragraphs[5].text().contains("😀"))
    }


    @Test
    fun testEdgeCaseHTMLStructures() {
        // Test various edge case HTML structures
        val edgeCases = listOf(
            "<!DOCTYPE html>",
            "<html/>",
            "<html></html>",
            "<!-- comment only -->",
            "<![CDATA[cdata content]]>",
            "<?xml version='1.0'?>",
            "<html><head/><body/></html>",
            "plain text without tags",
            "<>",
            "</>",
            "<html xmlns='http://www.w3.org/1999/xhtml'></html>"
        )
        
        edgeCases.forEach { html ->
            val document = parser.parse(html)
            assertNotNull(document, "Failed to parse: $html")
            assertNotNull(document.body(), "No body for: $html")
        }
    }

    @Test
    fun testParserRobustness() {
        // Test parser with various problematic inputs
        val problematicInputs = listOf(
            "<html><body><script>alert('test')</script></body></html>",
            "<html><body><style>body { color: red; }</style></body></html>",
            "<html><body><iframe src='dangerous.html'></iframe></body></html>",
            "<html><body onclick='alert()'>Click me</body></html>",
            "<html><body><a href='javascript:void(0)'>Link</a></body></html>"
        )
        
        problematicInputs.forEach { html ->
            val document = parser.parse(html)
            assertNotNull(document, "Failed to parse: $html")
            assertNotNull(document.body(), "No body for: $html")
        }
    }
}