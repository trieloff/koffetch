package live.aem.koffetch

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import java.net.URL

/**
 * Utility class for generating consistent test data across all extension tests
 */
object TestDataGenerator {
    /**
     * Creates a simple FFetchEntry with the given parameters
     */
    fun createFFetchEntry(
        id: String,
        title: String,
        description: String,
        additionalFields: Map<String, Any?> = emptyMap(),
    ): FFetchEntry {
        val fields =
            mutableMapOf<String, Any?>(
                "id" to id,
                "title" to title,
                "description" to description,
            )
        fields.putAll(additionalFields)
        return fields
    }

    /**
     * Creates a list of FFetchEntry objects with sequential IDs
     */
    fun createFFetchEntries(
        count: Int,
        prefix: String = "entry",
    ): List<FFetchEntry> {
        return (1..count).map { index ->
            createFFetchEntry(
                id = "${prefix}_$index",
                title = "Title $index",
                description = "Description for $prefix $index",
                additionalFields =
                    mapOf(
                        "index" to index,
                        "category" to if (index % 2 == 0) "even" else "odd",
                    ),
            )
        }
    }

    /**
     * Creates a mock FFetch instance that returns the provided entries
     */
    fun createMockFFetch(entries: List<FFetchEntry>): FFetch {
        val entriesFlow = entries.asFlow()
        return FFetch(
            URL("https://example.com/test"),
            FFetchContext().apply {
                allowedHosts.add("example.com")
            },
            entriesFlow
        )
    }

    /**
     * Creates a Flow of FFetchEntry objects with optional delay
     */
    fun createDelayedFFetchFlow(
        count: Int,
        delayMs: Long = 10,
        prefix: String = "entry",
    ): Flow<FFetchEntry> =
        flow {
            repeat(count) { index ->
                delay(delayMs)
                emit(
                    createFFetchEntry(
                        id = "${prefix}_${index + 1}",
                        title = "Delayed Title ${index + 1}",
                        description = "Delayed description for $prefix ${index + 1}",
                    ),
                )
            }
        }

    /**
     * Creates a Flow that emits some entries then fails with an exception
     */
    fun createFailingFFetchFlow(
        successCount: Int,
        prefix: String = "entry",
    ): Flow<FFetchEntry> =
        flow {
            repeat(successCount) { index ->
                emit(
                    createFFetchEntry(
                        id = "${prefix}_${index + 1}",
                        title = "Success Title ${index + 1}",
                        description = "Success description for $prefix ${index + 1}",
                    ),
                )
            }
            throw RuntimeException("Test failure after $successCount entries")
        }

    /**
     * Creates sample product data for testing custom types
     */
    data class Product(
        val id: String,
        val name: String,
        val price: Double,
        val category: String,
    )

    fun createProductEntries(count: Int): List<FFetchEntry> {
        return (1..count).map { index ->
            createFFetchEntry(
                id = "product_$index",
                title = "Product $index",
                description = "Product description $index",
                additionalFields =
                    mapOf(
                        "name" to "Product $index",
                        "price" to index * 10.0,
                        "category" to
                            if (index % 3 == 0) {
                                "electronics"
                            } else if (index % 2 == 0) {
                                "books"
                            } else {
                                "clothing"
                            },
                    ),
            )
        }
    }

    /**
     * Creates entries with null values for testing null handling
     */
    fun createEntriesWithNulls(count: Int): List<FFetchEntry> {
        return (1..count).map { index ->
            createFFetchEntry(
                id = "nullable_$index",
                title = if (index % 2 == 0) "Title $index" else "",
                description = if (index % 3 == 0) "Description $index" else "",
                additionalFields =
                    mapOf(
                        "optional_field" to if (index % 4 == 0) null else "Value $index",
                        "number" to if (index % 5 == 0) null else index,
                    ),
            )
        }
    }
}
