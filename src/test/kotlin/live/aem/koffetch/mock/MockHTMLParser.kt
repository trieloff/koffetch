//
// MockHTMLParser.kt
// KotlinFFetch Test Support
//
// Mock HTML parser implementation for testing document following
//

package live.aem.koffetch.mock

import live.aem.koffetch.FFetchHTMLParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Mock HTML parser for testing document following functionality
 * Provides error simulation and call tracking
 */
class MockHTMLParser : FFetchHTMLParser {
    // Call tracking
    private val _parseHistory = mutableListOf<String>()
    val parseHistory: List<String> get() = _parseHistory.toList()
    var parseCallCount: Int = 0
        private set

    var lastParsedHtml: String? = null
        private set

    var lastParsedDocument: Document? = null
        private set

    // Error simulation
    var shouldThrowError: Boolean = false
    var errorMessage: String = "Mock HTML parsing error"
    var errorAfterCalls: Int = -1 // Throw error after N successful calls (-1 = disabled)

    // Response customization
    private var customParseResult: Document? = null

    override fun parse(html: String): Document {
        parseCallCount++
        lastParsedHtml = html
        _parseHistory.add(html)

        // Check if we should throw error after N calls
        if (errorAfterCalls >= 0 && parseCallCount > errorAfterCalls) {
            throw RuntimeException(errorMessage)
        }

        // Check if we should throw error immediately
        if (shouldThrowError) {
            throw RuntimeException(errorMessage)
        }

        // Return custom result if provided
        customParseResult?.let {
            lastParsedDocument = it
            return it
        }

        // Default behavior: use real Jsoup parser
        return try {
            val document = Jsoup.parse(html)
            lastParsedDocument = document
            document
        } catch (e: Exception) {
            throw RuntimeException("HTML parsing failed: ${e.message}", e)
        }
    }

    // Configuration methods

    /**
     * Reset all state and configuration
     */
    fun reset() {
        _parseHistory.clear()
        parseCallCount = 0
        lastParsedHtml = null
        lastParsedDocument = null
        shouldThrowError = false
        errorMessage = "Mock HTML parsing error"
        errorAfterCalls = -1
        customParseResult = null
    }

    /**
     * Configure the parser to throw an error on the next parse call
     */
    fun throwErrorOnNextParse(message: String = "Mock HTML parsing error") {
        shouldThrowError = true
        errorMessage = message
    }

    /**
     * Configure the parser to throw an error after N successful parse calls
     */
    fun throwErrorAfter(
        callCount: Int,
        message: String = "Mock HTML parsing error",
    ) {
        errorAfterCalls = callCount
        errorMessage = message
    }

    /**
     * Configure the parser to return a specific document for all parse calls
     */
    fun setCustomParseResult(document: Document) {
        customParseResult = document
    }

    /**
     * Create a simple mock document with basic structure
     */
    fun createSimpleDocument(
        title: String,
        content: String,
    ): Document {
        val html =
            """
            <html>
                <head><title>$title</title></head>
                <body>
                    <h1>$title</h1>
                    <div class="content">$content</div>
                </body>
            </html>
            """.trimIndent()

        return Jsoup.parse(html)
    }

    /**
     * Create a complex mock document with various HTML elements
     */
    fun createComplexDocument(): Document {
        val html =
            """
            <html>
                <head>
                    <title>Complex Test Document</title>
                    <meta charset="UTF-8">
                </head>
                <body>
                    <header>
                        <h1>Test Document</h1>
                        <nav>
                            <ul>
                                <li><a href="/home">Home</a></li>
                                <li><a href="/about">About</a></li>
                            </ul>
                        </nav>
                    </header>
                    <main>
                        <article>
                            <h2>Article Title</h2>
                            <p>This is a paragraph with <strong>bold</strong> and <em>italic</em> text.</p>
                            <img src="/image.jpg" alt="Test image">
                            <table>
                                <tr><th>Column 1</th><th>Column 2</th></tr>
                                <tr><td>Data 1</td><td>Data 2</td></tr>
                            </table>
                        </article>
                        <aside>
                            <h3>Sidebar</h3>
                            <ul>
                                <li>Item 1</li>
                                <li>Item 2</li>
                            </ul>
                        </aside>
                    </main>
                    <footer>
                        <p>&copy; 2024 Test Company</p>
                    </footer>
                </body>
            </html>
            """.trimIndent()

        return Jsoup.parse(html)
    }

    /**
     * Create a malformed HTML document for testing error scenarios
     */
    fun createMalformedDocument(): String {
        return """
            <html>
                <head>
                    <title>Malformed Document
                <body>
                    <h1>Missing closing tags
                    <p>This paragraph is not closed
                    <div>
                        <span>Nested unclosed elements
                    </div>
                    <table>
                        <tr>
                            <td>Missing table headers and structure
                        <tr>
                            <td>Another incomplete row
                    </table>
            </html>
            """.trimIndent()
    }

    /**
     * Get statistics about parsing calls
     */
    fun getParsingStats(): MockParsingStats {
        return MockParsingStats(
            totalCalls = parseCallCount,
            uniqueHtmlDocuments = _parseHistory.toSet().size,
            averageHtmlLength =
                if (_parseHistory.isNotEmpty()) {
                    _parseHistory.sumOf { it.length }.toDouble() / _parseHistory.size
                } else {
                    0.0
                },
            hasEncounteredErrors = shouldThrowError || errorAfterCalls >= 0,
        )
    }

    /**
     * Verify that specific HTML content was parsed
     */
    fun wasHtmlParsed(htmlFragment: String): Boolean {
        return _parseHistory.any { it.contains(htmlFragment) }
    }

    /**
     * Verify that parsing was attempted with HTML containing specific elements
     */
    fun wasElementParsed(tagName: String): Boolean {
        return _parseHistory.any { html ->
            try {
                val doc = Jsoup.parse(html)
                doc.select(tagName).isNotEmpty()
            } catch (e: Exception) {
                false
            }
        }
    }
}

/**
 * Statistics about HTML parsing operations
 */
data class MockParsingStats(
    val totalCalls: Int,
    val uniqueHtmlDocuments: Int,
    val averageHtmlLength: Double,
    val hasEncounteredErrors: Boolean,
)
