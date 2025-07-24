//
// AEMResponseBuilder.kt
// KotlinFFetch Integration Test Support
//
// Builder for creating realistic AEM API response scenarios
//

package live.aem.koffetch.mock

/**
 * Builder class for creating realistic AEM Live query-index.json responses
 * Supports multi-sheet responses, pagination scenarios, and various edge cases
 */
class AEMResponseBuilder {
    private var total: Int = 0
    private var offset: Int = 0
    private var limit: Int = 255
    private val data = mutableListOf<Map<String, Any?>>()

    companion object {
        /**
         * Create a basic blog posts response with realistic data
         */
        fun blogPostsResponse(
            pageSize: Int = 10,
            currentPage: Int = 0,
            totalPosts: Int = 150,
        ): AEMResponseBuilder {
            return AEMResponseBuilder().apply {
                total = totalPosts
                offset = currentPage * pageSize
                limit = pageSize

                val startId = offset + 1
                repeat(minOf(pageSize, totalPosts - offset)) { i ->
                    addBlogPost(
                        id = startId + i,
                        path = "/blog/2024/01/${String.format("%02d", startId + i)}/post-${startId + i}",
                        title = "Blog Post ${startId + i}",
                        author = listOf("John Doe", "Jane Smith", "Michael Johnson", "Sarah Wilson")[i % 4],
                        publishedDate = "2024-01-${String.format(
                            "%02d",
                            (startId + i) % 28 + 1,
                        )}T${String.format("%02d", (i * 3) % 24)}:${String.format("%02d", (i * 7) % 60)}:00Z",
                        category = listOf("Product News", "Industry Analysis", "Company News", "Technical")[i % 4],
                        featured = i % 3 == 0,
                    )
                }
            }
        }

        /**
         * Create a products response for e-commerce scenarios
         */
        fun productsResponse(
            category: String = "electronics",
            pageSize: Int = 20,
            totalProducts: Int = 45,
        ): AEMResponseBuilder {
            return AEMResponseBuilder().apply {
                total = totalProducts
                offset = 0
                limit = pageSize

                repeat(minOf(pageSize, totalProducts)) { i ->
                    addProduct(
                        id = i + 1,
                        path = "/products/$category/product-${i + 1}",
                        name = "Product ${i + 1}",
                        category = category.replaceFirstChar { it.uppercase() },
                        price = 99.99 + (i * 50.0),
                        inStock = i % 4 != 0,
                        rating = 3.0 + (i % 3) * 0.5,
                    )
                }
            }
        }

        /**
         * Create an empty response for testing edge cases
         */
        fun emptyResponse(): AEMResponseBuilder {
            return AEMResponseBuilder().apply {
                total = 0
                offset = 0
                limit = 255
            }
        }

        /**
         * Create a multi-sheet response scenario
         */
        fun multiSheetResponse(sheet: String): AEMResponseBuilder {
            return when (sheet) {
                "products" ->
                    AEMResponseBuilder().apply {
                        total = 25
                        offset = 0
                        limit = 25
                        repeat(5) { i ->
                            addSheetItem(
                                path = "/products/sheet-products/item-${i + 1}",
                                sheet = "products",
                                additionalData =
                                    mapOf(
                                        "name" to "Product from Products Sheet ${i + 1}",
                                        "category" to "Category ${('A' + i % 3)}",
                                        "price" to (100.0 + i * 50.0),
                                    ),
                            )
                        }
                    }
                "customers" ->
                    AEMResponseBuilder().apply {
                        total = 15
                        offset = 0
                        limit = 25
                        repeat(3) { i ->
                            addSheetItem(
                                path = "/customers/sheet-customers/customer-${i + 1}",
                                sheet = "customers",
                                additionalData =
                                    mapOf(
                                        "name" to "Customer ${i + 1}",
                                        "email" to "customer${i + 1}@example.com",
                                        "tier" to listOf("bronze", "silver", "gold")[i % 3],
                                    ),
                            )
                        }
                    }
                else -> emptyResponse()
            }
        }

        /**
         * Create response with special characters and unicode
         */
        fun unicodeResponse(): AEMResponseBuilder {
            return AEMResponseBuilder().apply {
                total = 3
                offset = 0
                limit = 25

                addUnicodeItem(
                    path = "/content/international/español",
                    title = "Título en Español: áéíóúñü",
                    content = "¡Hola mundo! Esta es una página en español.",
                    language = "es-ES",
                )

                addUnicodeItem(
                    path = "/content/international/français",
                    title = "Titre en Français: àâäéèêëïîôùûüÿç",
                    content = "Bonjour le monde! Ceci est une page en français.",
                    language = "fr-FR",
                )

                addUnicodeItem(
                    path = "/content/international/unicode",
                    title = "Unicode Test: 你好世界 🌍 العالم مرحبا",
                    content = "Hello World: 🇺🇸🇪🇸🇫🇷🇩🇪🇯🇵🇨🇳 ©️®️™️€£¥",
                    language = "multi",
                )
            }
        }

        /**
         * Create response with null values and edge cases
         */
        fun edgeCaseResponse(): AEMResponseBuilder {
            return AEMResponseBuilder().apply {
                total = 3
                offset = 0
                limit = 25

                // Item with null values
                data.add(
                    mapOf(
                        "path" to "/content/with-nulls",
                        "title" to "Item with null values",
                        "description" to null,
                        "author" to null,
                        "publishedDate" to "2024-01-01T00:00:00Z",
                        "tags" to null,
                    ),
                )

                // Item with boolean values
                data.add(
                    mapOf(
                        "path" to "/content/booleans",
                        "title" to "Boolean Test",
                        "published" to true,
                        "featured" to false,
                        "archived" to true,
                        "visible" to false,
                    ),
                )

                // Item with various numeric values
                data.add(
                    mapOf(
                        "path" to "/content/numbers",
                        "title" to "Numeric Values Test",
                        "integer" to 42,
                        "float" to 3.14159,
                        "negative" to -100,
                        "zero" to 0,
                        "large_number" to 9223372036854775807L,
                        "scientific" to 1.23e10,
                    ),
                )
            }
        }
    }

    /**
     * Add a blog post entry
     */
    fun addBlogPost(
        id: Int,
        path: String,
        title: String,
        author: String,
        publishedDate: String,
        category: String,
        featured: Boolean = false,
    ): AEMResponseBuilder {
        val entry =
            mapOf(
                "id" to id,
                "path" to path,
                "title" to title,
                "author" to author,
                "publishedDate" to publishedDate,
                "category" to category,
                "featured" to featured,
                "template" to "blog-post",
                "excerpt" to "This is an excerpt for $title",
                "tags" to "blog,${category.lowercase().replace(" ", "-")}",
                "readTime" to (5 + (id % 10)),
                "lastModified" to (System.currentTimeMillis() - (id * 60000)),
            )
        data.add(entry)
        return this
    }

    /**
     * Add a product entry
     */
    fun addProduct(
        id: Int,
        path: String,
        name: String,
        category: String,
        price: Double,
        inStock: Boolean = true,
        rating: Double = 4.0,
    ): AEMResponseBuilder {
        val entry =
            mapOf(
                "id" to id,
                "path" to path,
                "name" to name,
                "category" to category,
                "price" to price,
                "sku" to "SKU-${String.format("%05d", id)}",
                "inStock" to inStock,
                "stockQuantity" to if (inStock) 50 + (id % 100) else 0,
                "rating" to rating,
                "reviewCount" to (10 + (id % 200)),
                "description" to "Description for $name",
            )
        data.add(entry)
        return this
    }

    /**
     * Add a sheet-specific item for multi-sheet testing
     */
    fun addSheetItem(
        path: String,
        sheet: String,
        additionalData: Map<String, Any?>,
    ): AEMResponseBuilder {
        val entry =
            mutableMapOf<String, Any?>(
                "path" to path,
                "sheet" to sheet,
            )
        entry.putAll(additionalData)
        data.add(entry)
        return this
    }

    /**
     * Add a unicode/international content item
     */
    fun addUnicodeItem(
        path: String,
        title: String,
        content: String,
        language: String,
    ): AEMResponseBuilder {
        val entry =
            mapOf(
                "path" to path,
                "title" to title,
                "content" to content,
                "language" to language,
                "lastModified" to System.currentTimeMillis(),
            )
        data.add(entry)
        return this
    }

    /**
     * Set pagination parameters
     */
    fun withPagination(
        total: Int,
        offset: Int,
        limit: Int,
    ): AEMResponseBuilder {
        this.total = total
        this.offset = offset
        this.limit = limit
        return this
    }

    /**
     * Build the complete AEM response JSON string
     */
    fun build(): String {
        val dataJson =
            data.joinToString(",") { entry ->
                val fields =
                    entry.entries.joinToString(",") { (key, value) ->
                        when (value) {
                            is String -> "\"$key\":\"$value\""
                            is Number -> "\"$key\":$value"
                            is Boolean -> "\"$key\":$value"
                            null -> "\"$key\":null"
                            else -> "\"$key\":\"$value\""
                        }
                    }
                "{$fields}"
            }

        return """{"total":$total,"offset":$offset,"limit":$limit,"data":[$dataJson]}"""
    }
}
