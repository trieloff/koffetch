//
// FFetchCollectionOperations.kt
// KotlinFFetch
//
// Collection operations for FFetch flows
//

package live.aem.koffetch.extensions

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.toList
import live.aem.koffetch.*

// MARK: - Collection Operations

/**
 * Collects all entries from the FFetch flow into a list.
 *
 * This method consumes the entire flow and returns all entries as a List.
 * Use with caution on large datasets as it loads everything into memory.
 *
 * @return A list containing all FFetchEntry objects from the flow
 */
suspend fun FFetch.all(): List<FFetchEntry> {
    return asFlow().toList()
}

/**
 * Gets the first entry from the FFetch flow.
 *
 * This method returns the first entry emitted by the flow, or null if the flow is empty.
 * It's efficient as it only fetches the minimum data needed to get the first result.
 *
 * @return The first FFetchEntry from the flow, or null if empty
 */
suspend fun FFetch.first(): FFetchEntry? {
    return asFlow().firstOrNull()
}

/**
 * Counts the total number of entries in the FFetch flow.
 *
 * This method consumes the entire flow to count all entries. It's more efficient
 * than calling all().size because it doesn't store the entries in memory.
 *
 * @return The total number of entries in the flow
 */
suspend fun FFetch.count(): Int {
    return asFlow().fold(0) { count, _ -> count + 1 }
}

// MARK: - Collection Operations for Mapped Flows

/**
 * Collects all elements from a Flow into a list.
 *
 * This extension function provides a convenient way to collect any Flow into a List,
 * particularly useful for flows created by FFetch transformation operations.
 *
 * @param T The type of the flow elements
 * @return A list containing all elements from the flow
 */
suspend fun <T> Flow<T>.all(): List<T> {
    return toList()
}

/**
 * Gets the first element from a Flow.
 *
 * This extension function provides a convenient way to get the first element from any Flow,
 * particularly useful for flows created by FFetch transformation operations.
 *
 * @param T The type of the flow elements
 * @return The first element from the flow, or null if empty
 */
suspend fun <T> Flow<T>.first(): T? {
    return firstOrNull()
}

/**
 * Counts the total number of elements in a Flow.
 *
 * This extension function provides a convenient way to count elements in any Flow,
 * particularly useful for flows created by FFetch transformation operations.
 *
 * @param T The type of the flow elements
 * @return The total number of elements in the flow
 */
suspend fun <T> Flow<T>.count(): Int {
    return fold(0) { count, _ -> count + 1 }
}
