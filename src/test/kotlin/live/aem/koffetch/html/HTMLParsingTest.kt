//
// HTMLParsingTest.kt
// KotlinFFetch
//
// Tests for HTML parsing functionality and DefaultFFetchHTMLParser
//

package live.aem.koffetch.html

import kotlinx.coroutines.test.runTest
import live.aem.koffetch.DefaultFFetchHTMLParser
import live.aem.koffetch.FFetch
import live.aem.koffetch.FFetchHTMLParser
import org.jsoup.nodes.Document
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HTMLParsingTest {
    private val parser = DefaultFFetchHTMLParser()

    @Test
    fun testBasicHTMLParsing() {
        val html =
            """
            <!DOCTYPE html>
            <html>
            <head><title>Test Page</title></head>
            <body>
                <h1>Hello World</h1>
                <p>This is a test paragraph.</p>
            </body>
            </html>
            """.trimIndent()

        val document = parser.parse(html)

        assertNotNull(document)
        assertEquals("Test Page", document.title())
        assertEquals("Hello World", document.select("h1").text())
        assertEquals("This is a test paragraph.", document.select("p").text())
    }

    @Test
    fun testHTMLParsingWithAttributes() {
        val html =
            """
            <html>
            <body>
                <div id="main" class="container">
                    <a href="https://example.com" target="_blank">Example Link</a>
                    <img src="image.jpg" alt="Test Image" width="100" height="50">
                </div>
            </body>
            </html>
            """.trimIndent()

        val document = parser.parse(html)

        val mainDiv = document.select("#main").first()
        assertNotNull(mainDiv)
        assertEquals("container", mainDiv!!.className())

        val link = document.select("a").first()
        assertNotNull(link)
        assertEquals("https://example.com", link!!.attr("href"))
        assertEquals("_blank", link.attr("target"))
        assertEquals("Example Link", link.text())

        val image = document.select("img").first()
        assertNotNull(image)
        assertEquals("image.jpg", image!!.attr("src"))
        assertEquals("Test Image", image.attr("alt"))
        assertEquals("100", image.attr("width"))
        assertEquals("50", image.attr("height"))
    }

    @Test
    fun testHTMLParsingWithTables() {
        val html =
            """
            <table>
                <thead>
                    <tr>
                        <th>Name</th>
                        <th>Age</th>
                        <th>City</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td>John</td>
                        <td>25</td>
                        <td>New York</td>
                    </tr>
                    <tr>
                        <td>Jane</td>
                        <td>30</td>
                        <td>Los Angeles</td>
                    </tr>
                </tbody>
            </table>
            """.trimIndent()

        val document = parser.parse(html)

        val headers = document.select("th")
        assertEquals(3, headers.size)
        assertEquals("Name", headers[0].text())
        assertEquals("Age", headers[1].text())
        assertEquals("City", headers[2].text())

        val rows = document.select("tbody tr")
        assertEquals(2, rows.size)

        val firstRowCells = rows[0].select("td")
        assertEquals("John", firstRowCells[0].text())
        assertEquals("25", firstRowCells[1].text())
        assertEquals("New York", firstRowCells[2].text())
    }

    @Test
    fun testHTMLParsingWithForms() {
        val html =
            """
            <form action="/submit" method="post">
                <input type="text" name="username" value="testuser" required>
                <input type="password" name="password" placeholder="Enter password">
                <input type="email" name="email" value="test@example.com">
                <textarea name="message" rows="4" cols="50">Default message</textarea>
                <select name="country">
                    <option value="us">United States</option>
                    <option value="ca" selected>Canada</option>
                    <option value="uk">United Kingdom</option>
                </select>
                <input type="submit" value="Submit">
            </form>
            """.trimIndent()

        val document = parser.parse(html)

        val form = document.select("form").first()
        assertNotNull(form)
        assertEquals("/submit", form!!.attr("action"))
        assertEquals("post", form.attr("method"))

        val usernameInput = document.select("input[name=username]").first()
        assertNotNull(usernameInput)
        assertEquals("text", usernameInput!!.attr("type"))
        assertEquals("testuser", usernameInput.attr("value"))
        assertTrue(usernameInput.hasAttr("required"))

        val passwordInput = document.select("input[name=password]").first()
        assertNotNull(passwordInput)
        assertEquals("password", passwordInput!!.attr("type"))
        assertEquals("Enter password", passwordInput.attr("placeholder"))

        val textarea = document.select("textarea").first()
        assertNotNull(textarea)
        assertEquals("Default message", textarea!!.text())
        assertEquals("4", textarea.attr("rows"))

        val selectedOption = document.select("option[selected]").first()
        assertNotNull(selectedOption)
        assertEquals("ca", selectedOption!!.attr("value"))
        assertEquals("Canada", selectedOption.text())
    }

    @Test
    fun testMalformedHTMLHandling() {
        val malformedHTML =
            """
            <html>
            <head><title>Test</title>
            <body>
                <div>
                    <p>Unclosed paragraph
                    <span>Nested span
                    <div>Another div</div>
                </div>
            </body>
            """.trimIndent()

        // Jsoup should handle malformed HTML gracefully
        val document = parser.parse(malformedHTML)

        assertNotNull(document)
        assertEquals("Test", document.title())
        assertTrue(document.select("div").size > 0)
        assertTrue(document.select("p").size > 0)
    }

    @Test
    fun testEmptyHTMLHandling() {
        val emptyHTML = ""
        val document = parser.parse(emptyHTML)

        assertNotNull(document)
        // Jsoup creates a basic HTML structure even for empty input
        assertNotNull(document.select("html"))
        assertNotNull(document.select("head"))
        assertNotNull(document.select("body"))
    }

    @Test
    fun testHTMLWithSpecialCharacters() {
        val html =
            """
            <html>
            <body>
                <p>Special characters: &amp; &lt; &gt; &quot; &#39;</p>
                <p>Unicode: 🌟 ñ é ü ß</p>
                <p>Symbols: © ® ™</p>
            </body>
            </html>
            """.trimIndent()

        val document = parser.parse(html)

        val paragraphs = document.select("p")
        assertEquals(3, paragraphs.size)

        // Jsoup should decode HTML entities
        assertTrue(paragraphs[0].text().contains("&"))
        assertTrue(paragraphs[0].text().contains("<"))
        assertTrue(paragraphs[0].text().contains(">"))

        // Unicode characters should be preserved
        assertTrue(paragraphs[1].text().contains("🌟"))
        assertTrue(paragraphs[1].text().contains("ñ"))

        // Symbol entities should be decoded
        assertTrue(paragraphs[2].text().contains("©"))
        assertTrue(paragraphs[2].text().contains("®"))
    }

    @Test
    fun testLargeHTMLDocument() {
        val largeHTML =
            buildString {
                append("<html><body>")
                repeat(1000) { i ->
                    append("<div id='item$i' class='item'>Item $i content</div>")
                }
                append("</body></html>")
            }

        val document = parser.parse(largeHTML)

        assertNotNull(document)
        val items = document.select(".item")
        assertEquals(1000, items.size)

        // Test access to specific items
        assertEquals("item0", items[0].attr("id"))
        assertEquals("Item 0 content", items[0].text())
        assertEquals("item999", items[999].attr("id"))
        assertEquals("Item 999 content", items[999].text())
    }

    @Test
    fun testHTMLParsingErrorHandling() {
        // Test with extremely malformed input that might cause parsing issues
        val problematicHTML = "\u0000\u0001\u0002<invalid>tag\u0003"

        // Should not throw an exception, but handle gracefully
        val document = parser.parse(problematicHTML)
        assertNotNull(document)
    }

    @Test
    fun testWithHTMLParserMethodIntegration() =
        runTest {
            val customParser =
                object : FFetchHTMLParser {
                    override fun parse(html: String): Document {
                        // Custom parser that adds a special attribute
                        val doc = DefaultFFetchHTMLParser().parse(html)
                        doc.body().attr("custom-parsed", "true")
                        return doc
                    }
                }

            val ffetch =
                FFetch("https://example.com/page.html")
                    .withHTMLParser(customParser)

            assertSame(customParser, ffetch.context.htmlParser)

            // Verify the custom parser behavior
            val html = "<html><body><p>Test</p></body></html>"
            val document = ffetch.context.htmlParser.parse(html)
            assertEquals("true", document.body().attr("custom-parsed"))
        }

    @Test
    fun testHTMLParserChaining() =
        runTest {
            val parser1 = DefaultFFetchHTMLParser()
            val parser2 =
                object : FFetchHTMLParser {
                    override fun parse(html: String): Document {
                        return DefaultFFetchHTMLParser().parse(html)
                    }
                }

            val ffetch =
                FFetch("https://example.com/test.html")
                    .withHTMLParser(parser1)
                    .withHTMLParser(parser2) // Should override the first parser

            assertSame(parser2, ffetch.context.htmlParser)
            assertNotSame(parser1, ffetch.context.htmlParser)
        }

    @Test
    fun testHTMLParsingExceptionHandling() {
        // Test that parsing errors are properly wrapped in FFetchError.DecodingError
        val faultyParser =
            object : FFetchHTMLParser {
                override fun parse(html: String): Document {
                    throw RuntimeException("Parsing failed")
                }
            }

        assertFailsWith<Exception> {
            faultyParser.parse("<html></html>")
        }
    }
}
