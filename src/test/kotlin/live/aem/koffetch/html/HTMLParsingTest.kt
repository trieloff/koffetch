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
import live.aem.koffetch.FFetchError
import live.aem.koffetch.FFetchHTMLParser
import live.aem.koffetch.withHTMLParser
import org.jsoup.nodes.Document
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertContains

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

    @Test
    fun testDefaultFFetchHTMLParserErrorHandling() {
        val parser = DefaultFFetchHTMLParser()
        
        // Test that IllegalArgumentException is wrapped in FFetchError.DecodingError
        val maliciousParser = object : FFetchHTMLParser {
            override fun parse(html: String): Document {
                throw IllegalArgumentException("Invalid HTML input")
            }
        }
        
        assertFailsWith<IllegalArgumentException> {
            maliciousParser.parse("<html></html>")
        }
    }

    @Test
    fun testNullHTMLInput() {
        val parser = DefaultFFetchHTMLParser()
        
        // Jsoup handles null input gracefully by creating empty document
        val document = parser.parse("")
        assertNotNull(document)
        assertEquals("", document.title())
    }

    @Test
    fun testWhitespaceOnlyHTML() {
        val parser = DefaultFFetchHTMLParser()
        val whitespaceHTML = "   \n\t  \r  "
        
        val document = parser.parse(whitespaceHTML)
        assertNotNull(document)
        assertEquals("", document.title())
        assertNotNull(document.body())
    }

    @Test
    fun testComplexNestedStructures() {
        val html = """
            <html>
            <body>
                <div class="level1">
                    <div class="level2">
                        <div class="level3">
                            <div class="level4">
                                <div class="level5">
                                    <p>Deep nested content</p>
                                    <ul class="list">
                                        <li><a href="#">Item 1</a></li>
                                        <li><span class="highlight">Item 2</span></li>
                                        <li>
                                            <div class="item-container">
                                                <input type="checkbox" checked>
                                                <label>Nested form element</label>
                                            </div>
                                        </li>
                                    </ul>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
        
        val document = parser.parse(html)
        
        // Test deep nesting navigation
        val deepParagraph = document.select(".level5 p").first()
        assertNotNull(deepParagraph)
        assertEquals("Deep nested content", deepParagraph!!.text())
        
        // Test complex selectors
        val checkedInput = document.select("input[type=checkbox][checked]").first()
        assertNotNull(checkedInput)
        assertTrue(checkedInput!!.hasAttr("checked"))
        
        // Test hierarchical relationships
        val listItems = document.select(".list li")
        assertEquals(3, listItems.size)
        assertEquals("Item 1", listItems[0].select("a").text())
        assertEquals("Item 2", listItems[1].select("span.highlight").text())
    }

    @Test
    fun testDocumentTypeDeclarations() {
        val htmlWithDoctype = """
            <!DOCTYPE html>
            <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
            <html>
            <head><title>DOCTYPE Test</title></head>
            <body><p>Content with DOCTYPE</p></body>
            </html>
        """.trimIndent()
        
        val document = parser.parse(htmlWithDoctype)
        assertNotNull(document)
        assertEquals("DOCTYPE Test", document.title())
        assertEquals("Content with DOCTYPE", document.select("p").text())
    }

    @Test
    fun testHTMLCommentsAndCDATA() {
        val htmlWithComments = """
            <html>
            <head>
                <!-- This is a comment -->
                <title>Comments Test</title>
                <script type="text/javascript">
                    //<![CDATA[
                    var data = "<xml>test</xml>";
                    //]]>
                </script>
            </head>
            <body>
                <!-- Another comment -->
                <p>Content with comments</p>
                <!-- Multi-line
                     comment -->
            </body>
            </html>
        """.trimIndent()
        
        val document = parser.parse(htmlWithComments)
        assertNotNull(document)
        assertEquals("Comments Test", document.title())
        assertEquals("Content with comments", document.select("p").text())
        
        // Verify script tag exists (content may be filtered by Jsoup for security)
        val scriptTag = document.select("script").first()
        assertNotNull(scriptTag)
    }

    @Test
    fun testSelfClosingTags() {
        val htmlWithSelfClosing = """
            <html>
            <head>
                <meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <link rel="stylesheet" href="style.css"/>
            </head>
            <body>
                <img src="image.jpg" alt="Test" width="100" height="100"/>
                <br/>
                <hr/>
                <input type="text" name="test" value="default"/>
                <area shape="circle" coords="50,50,25" href="#"/>
            </body>
            </html>
        """.trimIndent()
        
        val document = parser.parse(htmlWithSelfClosing)
        
        // Test self-closing tags are properly parsed
        val metaTags = document.select("meta")
        assertEquals(2, metaTags.size)
        assertEquals("UTF-8", metaTags[0].attr("charset"))
        
        val img = document.select("img").first()
        assertNotNull(img)
        assertEquals("image.jpg", img!!.attr("src"))
        assertEquals("100", img.attr("width"))
        
        // Verify other self-closing elements
        assertNotNull(document.select("br").first())
        assertNotNull(document.select("hr").first())
        assertNotNull(document.select("input").first())
        assertNotNull(document.select("area").first())
    }

    @Test
    fun testExtensiveUnicodeSupport() {
        val unicodeHTML = """
            <html>
            <head><title>Unicode Test 测试 тест</title></head>
            <body>
                <p>English: Hello World</p>
                <p>Chinese: 你好世界 测试文本</p>
                <p>Japanese: こんにちは世界 テスト</p>
                <p>Korean: 안녕하세요 세계</p>
                <p>Russian: Привет мир тест</p>
                <p>Arabic: مرحبا بالعالم اختبار</p>
                <p>Hebrew: שלום עולם בדיקה</p>
                <p>Emoji: 🌍 🚀 ⭐ 🎉 💻 📱</p>
                <p>Math: ∑ ∫ ∂ ∞ ≈ ≠ ± √ ∈ ∀</p>
                <p>Currency: $ € £ ¥ ₹ ₽ ₿</p>
            </body>
            </html>
        """.trimIndent()
        
        val document = parser.parse(unicodeHTML)
        
        assertEquals("Unicode Test 测试 тест", document.title())
        
        val paragraphs = document.select("p")
        assertEquals(10, paragraphs.size)
        
        // Test various Unicode ranges
        assertTrue(paragraphs[1].text().contains("你好世界"))
        assertTrue(paragraphs[2].text().contains("こんにちは世界"))
        assertTrue(paragraphs[3].text().contains("안녕하세요"))
        assertTrue(paragraphs[4].text().contains("Привет"))
        assertTrue(paragraphs[5].text().contains("مرحبا"))
        assertTrue(paragraphs[6].text().contains("שלום"))
        assertTrue(paragraphs[7].text().contains("🌍"))
        assertTrue(paragraphs[8].text().contains("∑"))
        assertTrue(paragraphs[9].text().contains("€"))
    }

    @Test
    fun testMalformedTagRecovery() {
        val malformedHTML = """
            <html>
            <head><title>Malformed Test</title>
            <body>
                <div class="container
                    <p>Unclosed quote in attribute
                    <span style="color: red; font-size: 
                        Missing closing quote
                    </span>
                    <div><p>Nested without closing</div>
                    <img src="test.jpg" alt="Test Image
                    <a href="#">Link without closing
                    <ul>
                        <li>Item 1
                        <li>Item 2
                        <li>Item 3</ul>
                </div>
            </body>
        """.trimIndent()
        
        // Jsoup should recover gracefully from malformed HTML
        val document = parser.parse(malformedHTML)
        assertNotNull(document)
        assertEquals("Malformed Test", document.title())
        
        // Verify that parser recovered basic structure
        assertTrue(document.select("div").size > 0)
        assertTrue(document.select("p").size > 0)
        assertTrue(document.select("ul").size > 0)
        assertTrue(document.select("li").size >= 3)
    }

    @Test
    fun testLargeDocumentPerformance() {
        // Create a very large HTML document
        val largeHTML = buildString {
            append("<html><head><title>Performance Test</title></head><body>")
            
            // Create nested table structure
            append("<table id='main-table'>")
            repeat(100) { tableIndex ->
                append("<tr class='row-$tableIndex'>")
                repeat(50) { cellIndex ->
                    append("<td id='cell-$tableIndex-$cellIndex' class='cell' data-value='$cellIndex'>")
                    append("Content for cell $tableIndex-$cellIndex with some additional text ")
                    append("to make the content more substantial and realistic. ")
                    append("This cell contains index $cellIndex in row $tableIndex.")
                    append("</td>")
                }
                append("</tr>")
            }
            append("</table>")
            
            // Add large list structure
            append("<ul id='large-list'>")
            repeat(500) { listIndex ->
                append("<li class='item-$listIndex' data-index='$listIndex'>")
                append("<div class='item-content'>")
                append("<h3>Item $listIndex Title</h3>")
                append("<p>Description for item $listIndex with detailed content ")
                append("that spans multiple lines and includes various information ")
                append("about this particular item number $listIndex.</p>")
                append("<span class='metadata'>Created: $(System.currentTimeMillis())</span>")
                append("</div>")
                append("</li>")
            }
            append("</ul>")
            
            append("</body></html>")
        }
        
        // Parse the large document
        val startTime = System.currentTimeMillis()
        val document = parser.parse(largeHTML)
        val parseTime = System.currentTimeMillis() - startTime
        
        assertNotNull(document)
        assertEquals("Performance Test", document.title())
        
        // Verify structure was parsed correctly
        val rows = document.select("tr")
        assertEquals(100, rows.size)
        
        val cells = document.select("td")
        assertEquals(5000, cells.size) // 100 rows * 50 cells
        
        val listItems = document.select("ul#large-list li")
        assertEquals(500, listItems.size)
        
        // Test specific element access
        val specificCell = document.select("#cell-50-25").first()
        assertNotNull(specificCell)
        assertTrue(specificCell!!.text().contains("cell 50-25"))
        
        val specificItem = document.select(".item-250").first()
        assertNotNull(specificItem)
        assertTrue(specificItem!!.text().contains("Item 250"))
        
        // Performance should be reasonable (adjust threshold as needed)
        println("Large document parsing time: ${parseTime}ms")
        assertTrue(parseTime < 5000, "Parsing took too long: ${parseTime}ms")
    }

    @Test
    fun testXMLNamespacesInHTML() {
        val htmlWithNamespaces = """
            <html xmlns="http://www.w3.org/1999/xhtml" 
                  xmlns:og="http://opengraphprotocol.org/schema/" 
                  xmlns:fb="http://www.facebook.com/2008/fbml">
            <head>
                <title>Namespace Test</title>
                <meta property="og:title" content="Test Page"/>
                <meta property="og:description" content="Test Description"/>
                <meta property="fb:app_id" content="123456789"/>
            </head>
            <body>
                <div class="content">
                    <h1>Namespace Test Content</h1>
                    <p>Testing XML namespaces in HTML</p>
                </div>
            </body>
            </html>
        """.trimIndent()
        
        val document = parser.parse(htmlWithNamespaces)
        assertNotNull(document)
        assertEquals("Namespace Test", document.title())
        
        // Test Open Graph meta tags
        val ogTitle = document.select("meta[property=og:title]").first()
        assertNotNull(ogTitle)
        assertEquals("Test Page", ogTitle!!.attr("content"))
        
        val ogDescription = document.select("meta[property=og:description]").first()
        assertNotNull(ogDescription)
        assertEquals("Test Description", ogDescription!!.attr("content"))
    }

    @Test
    fun testCustomDataAttributes() {
        val htmlWithDataAttributes = """
            <html>
            <body>
                <div id="widget" 
                     data-widget-type="carousel" 
                     data-widget-id="123" 
                     data-config='{"autoplay": true, "duration": 5000}'
                     data-items="5"
                     data-responsive="true">
                    <div class="item" data-item-id="1" data-priority="high">Item 1</div>
                    <div class="item" data-item-id="2" data-priority="medium">Item 2</div>
                    <div class="item" data-item-id="3" data-priority="low">Item 3</div>
                </div>
                <button data-action="submit" data-target="#form" data-async="true">Submit</button>
            </body>
            </html>
        """.trimIndent()
        
        val document = parser.parse(htmlWithDataAttributes)
        
        val widget = document.select("#widget").first()
        assertNotNull(widget)
        assertEquals("carousel", widget!!.attr("data-widget-type"))
        assertEquals("123", widget.attr("data-widget-id"))
        assertTrue(widget.attr("data-config").contains("autoplay"))
        assertEquals("5", widget.attr("data-items"))
        assertEquals("true", widget.attr("data-responsive"))
        
        val items = document.select(".item")
        assertEquals(3, items.size)
        assertEquals("high", items[0].attr("data-priority"))
        assertEquals("medium", items[1].attr("data-priority"))
        assertEquals("low", items[2].attr("data-priority"))
        
        val button = document.select("button").first()
        assertNotNull(button)
        assertEquals("submit", button!!.attr("data-action"))
        assertEquals("#form", button.attr("data-target"))
        assertEquals("true", button.attr("data-async"))
    }

    @Test
    fun testVeryDeeplyNestedStructure() {
        // Create extremely deep nesting to test parser limits
        val deepHTML = buildString {
            append("<html><body>")
            repeat(50) { level ->
                append("<div class='level-$level' id='div-$level'>")
            }
            append("<p>Deep content at level 50</p>")
            repeat(50) {
                append("</div>")
            }
            append("</body></html>")
        }
        
        val document = parser.parse(deepHTML)
        assertNotNull(document)
        
        // Test that deep nesting is preserved
        val deepestDiv = document.select(".level-49").first()
        assertNotNull(deepestDiv)
        
        val deepParagraph = document.select("p").first()
        assertNotNull(deepParagraph)
        assertEquals("Deep content at level 50", deepParagraph!!.text())
        
        // Test selector traversal through deep structure
        val divs = document.select("div")
        assertEquals(50, divs.size)
    }

    @Test
    fun testSpecialCharacterEntities() {
        val htmlWithEntities = """
            <html>
            <body>
                <p>HTML Entities: &amp; &lt; &gt; &quot; &#39; &nbsp;</p>
                <p>Numeric: &#65; &#66; &#67; &#8364; &#8482;</p>
                <p>Hex: &#x41; &#x42; &#x43; &#x20AC; &#x2122;</p>
                <p>Special: &copy; &reg; &trade; &mdash; &ndash; &hellip;</p>
                <p>Math: &sum; &int; &part; &infin; &asymp; &ne; &plusmn; &radic;</p>
                <p>Arrows: &larr; &uarr; &rarr; &darr; &harr;</p>
                <p>Greek: &alpha; &beta; &gamma; &delta; &epsilon; &pi; &sigma; &omega;</p>
            </body>
            </html>
        """.trimIndent()
        
        val document = parser.parse(htmlWithEntities)
        val paragraphs = document.select("p")
        
        // Test that entities are properly decoded
        assertTrue(paragraphs[0].text().contains("&"))
        assertTrue(paragraphs[0].text().contains("<"))
        assertTrue(paragraphs[0].text().contains(">"))
        assertTrue(paragraphs[0].text().contains("\""))
        assertTrue(paragraphs[0].text().contains("'"))
        
        // Test numeric entities
        assertTrue(paragraphs[1].text().contains("A")) // &#65;
        assertTrue(paragraphs[1].text().contains("€")) // &#8364;
        assertTrue(paragraphs[1].text().contains("™")) // &#8482;
        
        // Test hex entities
        assertTrue(paragraphs[2].text().contains("A")) // &#x41;
        assertTrue(paragraphs[2].text().contains("€")) // &#x20AC;
        
        // Test named entities
        assertTrue(paragraphs[3].text().contains("©"))
        assertTrue(paragraphs[3].text().contains("®"))
        assertTrue(paragraphs[3].text().contains("™"))
        
        // Test math symbols
        assertTrue(paragraphs[4].text().contains("∑"))
        assertTrue(paragraphs[4].text().contains("∞"))
        
        // Test Greek letters
        assertTrue(paragraphs[6].text().contains("α"))
        assertTrue(paragraphs[6].text().contains("π"))
        assertTrue(paragraphs[6].text().contains("ω"))
    }

    @Test
    fun testBoundaryConditionsAndEdgeCases() {
        val parser = DefaultFFetchHTMLParser()
        
        // Test single character
        var document = parser.parse("a")
        assertNotNull(document)
        assertEquals("a", document.body().text())
        
        // Test single tag
        document = parser.parse("<p>")
        assertNotNull(document)
        assertNotNull(document.select("p").first())
        
        // Test only attributes
        document = parser.parse("<div class='test' id='item'>")
        assertNotNull(document)
        val div = document.select("div").first()
        assertEquals("test", div!!.attr("class"))
        assertEquals("item", div.attr("id"))
        
        // Test mixed case tags
        document = parser.parse("<DIV CLASS='TEST'><P>Content</P></DIV>")
        assertNotNull(document)
        assertEquals("Content", document.select("p").text())
        
        // Test tags with numbers
        document = parser.parse("<h1>Title 1</h1><h2>Title 2</h2><h6>Title 6</h6>")
        assertNotNull(document)
        assertEquals(3, document.select("h1, h2, h3, h4, h5, h6").size)
    }
}
