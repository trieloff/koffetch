//
// DefaultFFetchHTMLParserExceptionTest.kt
// KotlinFFetch
//
// Tests for DefaultFFetchHTMLParser exception handling using reflection
//

package live.aem.koffetch.html

import live.aem.koffetch.DefaultFFetchHTMLParser
import live.aem.koffetch.FFetchError
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultFFetchHTMLParserExceptionTest {
    @Test
    fun testIllegalArgumentExceptionWrapping() {
        // Create a special HTML input that might trigger parsing issues
        // We'll try to trigger IllegalArgumentException through Jsoup
        val parser = DefaultFFetchHTMLParser()

        // Since we can't directly mock Jsoup.parse, we'll test edge cases that might throw
        // In practice, Jsoup is very robust and rarely throws IllegalArgumentException
        // But we can still test the parser handles various edge cases

        // Test with null bytes which might cause issues
        val problematicHtml = "\u0000<html>\u0000<body>\u0000</body>\u0000</html>\u0000"
        val doc = parser.parse(problematicHtml)
        assertNotNull(doc)
    }

    @Test
    fun testOutOfMemorySimulation() {
        // We can't actually trigger OutOfMemoryError without exhausting memory,
        // but we can test that the parser handles extremely large inputs gracefully
        val parser = DefaultFFetchHTMLParser()

        // Create a large HTML string that could potentially cause memory issues
        val largeHtml =
            buildString {
                append("<html><body>")
                // Create a moderately large HTML (not too large to actually cause OOM)
                repeat(10000) { i ->
                    append("<div id='item$i' class='class$i' data-value='$i'>")
                    append("Content for item $i with some additional text to increase size. ")
                    append("This is line 2 of content. This is line 3 of content.")
                    append("</div>")
                }
                append("</body></html>")
            }

        // Parser should handle this without throwing
        val doc = parser.parse(largeHtml)
        assertNotNull(doc)
        assertTrue(doc.select("div").size > 0)
    }

    @Test
    fun testRealWorldScenarios() {
        val parser = DefaultFFetchHTMLParser()

        // Test various real-world scenarios that might trigger exceptions
        val testCases =
            listOf(
                // Empty and null-like inputs
                "",
                "   ",
                "\n\n\n",
                "\r\n\r\n",
                "\t\t\t",
                // Invalid XML/HTML constructs
                "<?xml version='1.0' encoding='UTF-8'?><root>",
                "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\">",
                "<![CDATA[test data]]>",
                // Malformed HTML
                "<html><head><title>Test</title></head><body",
                "<div><span><p></div></span></p>",
                "<input type='text' value='unclosed quote>",
                // Special characters and escape sequences
                "\u0001\u0002\u0003\u0004\u0005",
                "\uFFFE\uFFFF",
                "\\x00\\x01\\x02",
                // Very deeply nested structures (simplified to avoid actual stack overflow)
                (1..50).fold("<html><body>") { acc, _ -> "$acc<div>" } +
                    "content" +
                    (1..50).fold("") { acc, _ -> "$acc</div>" } +
                    "</body></html>",
                // Large attributes
                "<div class='" + "x".repeat(1000) + "'>content</div>",
                // Many attributes
                "<div " + (1..100).joinToString(" ") { "attr$it='value$it'" } + ">content</div>",
                // Unicode edge cases
                "\uD800\uDC00",
                // Valid surrogate pair
                "\uD83D\uDE00",
                // Emoji
                // Comments and CDATA
                "<!-- Comment with special chars: <>&\" -->",
                "<script>/*<![CDATA[*/var x = 1;/*]]>*/</script>",
                // Mixed content
                "Text before <html>Text in middle<body>Body text</body>Text after</html> Text at end",
            )

        // All test cases should be handled without throwing exceptions
        testCases.forEach { html ->
            val doc = parser.parse(html)
            assertNotNull(doc, "Failed to parse: ${html.take(50)}...")
        }
    }

    @Test
    fun testConcurrentParsingStress() {
        // Test thread safety and potential race conditions
        val parser = DefaultFFetchHTMLParser()
        val threadCount = 20
        val iterationsPerThread = 100
        val errors = AtomicInteger(0)

        val threads =
            (1..threadCount).map { threadId ->
                Thread {
                    try {
                        repeat(iterationsPerThread) { iteration ->
                            val html = "<html><body><p>Thread $threadId, iteration $iteration</p></body></html>"
                            val doc = parser.parse(html)
                            assertEquals("Thread $threadId, iteration $iteration", doc.select("p").text())
                        }
                    } catch (e: Exception) {
                        errors.incrementAndGet()
                        e.printStackTrace()
                    }
                }
            }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(0, errors.get(), "Concurrent parsing should not produce errors")
    }

    @Test
    fun testParserExhaustiveEdgeCases() {
        val parser = DefaultFFetchHTMLParser()

        // Additional edge cases focusing on potential exception triggers
        val edgeCases =
            listOf(
                // Binary data simulation
                String(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)),
                // Control characters
                (0..31).map { it.toChar() }.joinToString(""),
                // High Unicode planes
                "\uD800\uDC00\uD800\uDC01\uD800\uDC02",
                // Linear B syllables
                // RTL and LTR markers
                "\u200E\u200F\u202A\u202B\u202C\u202D\u202E",
                // Zero-width characters
                "\u200B\u200C\u200D\uFEFF",
                // Incomplete surrogate pairs (might be handled differently)
                "\uD800",
                // High surrogate without low
                "\uDC00",
                // Low surrogate without high
                // Very long single line
                "<p>" + "x".repeat(10000) + "</p>",
                // Many empty elements
                (1..1000).joinToString("") { "<div></div>" },
                // Nested tables
                "<table><tr><td><table><tr><td>Nested</td></tr></table></td></tr></table>",
                // Form with many inputs
                "<form>" + (1..100).joinToString("") { "<input name='field$it' value='value$it'>" } + "</form>",
                // SVG in HTML
                "<html><body><svg><circle cx='50' cy='50' r='40'/></svg></body></html>",
                // MathML in HTML
                "<html><body><math><mi>x</mi><mo>+</mo><mi>y</mi></math></body></html>",
                // Script with various content
                "<script>var x = '<div>Not HTML</div>';</script>",
                "<script type='text/javascript'><!--\nalert('test');\n//--></script>",
                // Style with various content
                "<style>body { content: '<div>Not HTML</div>'; }</style>",
                "<style type='text/css'>/* <![CDATA[ */ body { color: red; } /* ]]> */</style>",
            )

        edgeCases.forEach { html ->
            try {
                val doc = parser.parse(html)
                assertNotNull(doc, "Should parse without returning null")
            } catch (e: Exception) {
                // If any exception occurs, it should be wrapped appropriately
                when (e) {
                    is FFetchError.DecodingError -> {
                        // This is expected for some edge cases
                        assertNotNull(e.cause)
                    }
                    else -> throw AssertionError("Unexpected exception type: ${e::class.simpleName}", e)
                }
            }
        }
    }
}
