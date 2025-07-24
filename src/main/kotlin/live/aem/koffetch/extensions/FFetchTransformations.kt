//
// FFetchTransformations.kt
// KotlinFFetch
//
// Transformation operations for FFetch flows
//

package live.aem.koffetch.extensions

import kotlinx.coroutines.flow.*
import live.aem.koffetch.*
import kotlinx.coroutines.flow.filter as flowFilter
import kotlinx.coroutines.flow.map as flowMap

// MARK: - Transformation Operations

/**
 * Transforms FFetchEntry objects to a different type using the provided function.
 *
 * This method applies a transformation function to each entry in the flow, converting
 * them to a different type. The transformation is applied lazily as entries are consumed.
 *
 * @param T The target type to transform entries to
 * @param transform The transformation function to apply to each entry
 * @return A Flow of transformed objects of type T
 */
fun <T> FFetch.map(transform: FFetchTransform<FFetchEntry, T>): Flow<T> {
    return asFlow().map { entry -> transform(entry) }
}

/**
 * Filters entries using the provided predicate function.
 *
 * This method creates a new FFetch instance that only emits entries that satisfy
 * the given predicate. The filtering is applied lazily as entries are fetched.
 *
 * @param predicate The predicate function that determines which entries to include
 * @return A new FFetch instance that emits only the filtered entries
 */
fun FFetch.filter(predicate: FFetchPredicate<FFetchEntry>): FFetch {
    val filteredFlow = asFlow().filter { entry -> predicate(entry) }
    return FFetch(url, context, filteredFlow)
}

/**
 * Limits the number of entries returned from the flow.
 *
 * This method creates a new FFetch instance that will emit at most the specified
 * number of entries. Once the limit is reached, the flow will complete.
 *
 * @param count The maximum number of entries to emit (must be non-negative)
 * @return A new FFetch instance that emits at most count entries
 */
fun FFetch.limit(count: Int): FFetch {
    val limitedFlow = asFlow().take(count)
    return FFetch(url, context, limitedFlow)
}

/**
 * Skips the specified number of entries from the beginning of the flow.
 *
 * This method creates a new FFetch instance that ignores the first count entries
 * and starts emitting from the (count + 1)th entry onwards.
 *
 * @param count The number of entries to skip from the beginning (must be non-negative)
 * @return A new FFetch instance that skips the first count entries
 */
fun FFetch.skip(count: Int): FFetch {
    val skippedFlow = asFlow().drop(count)
    return FFetch(url, context, skippedFlow)
}

/**
 * Extracts a slice of entries from the flow.
 *
 * This method creates a new FFetch instance that emits entries from the start index
 * (inclusive) to the end index (exclusive). It's equivalent to skip(start).limit(end - start).
 *
 * @param start The starting index (inclusive, must be non-negative)
 * @param end The ending index (exclusive, must be greater than start)
 * @return A new FFetch instance that emits entries from start to end
 */
fun FFetch.slice(
    start: Int,
    end: Int,
): FFetch {
    return skip(start).limit(end - start)
}

// MARK: - Transformation Operations for Mapped Flows

/**
 * Transforms elements in a Flow using the provided function.
 *
 * This extension function provides a convenient way to transform elements in any Flow,
 * particularly useful for flows created by FFetch.map() operations.
 *
 * @param T The input type of the flow elements
 * @param U The output type after transformation
 * @param transform The suspend transformation function to apply to each element
 * @return A new Flow with transformed elements of type U
 */
fun <T, U> Flow<T>.map(transform: suspend (T) -> U): Flow<U> {
    return flowMap(transform)
}

/**
 * Filters elements in a Flow using the provided predicate.
 *
 * This extension function provides a convenient way to filter elements in any Flow,
 * particularly useful for flows created by FFetch transformation operations.
 *
 * @param T The type of the flow elements
 * @param predicate The suspend predicate function that determines which elements to keep
 * @return A new Flow containing only elements that satisfy the predicate
 */
fun <T> Flow<T>.filter(predicate: suspend (T) -> Boolean): Flow<T> {
    return flowFilter(predicate)
}

/**
 * Limits the number of elements emitted by a Flow.
 *
 * This extension function provides a convenient way to limit any Flow,
 * particularly useful for flows created by FFetch transformation operations.
 *
 * @param T The type of the flow elements
 * @param count The maximum number of elements to emit (must be non-negative)
 * @return A new Flow that emits at most count elements
 */
fun <T> Flow<T>.limit(count: Int): Flow<T> {
    return take(count)
}

/**
 * Skips the specified number of elements from the beginning of a Flow.
 *
 * This extension function provides a convenient way to skip elements in any Flow,
 * particularly useful for flows created by FFetch transformation operations.
 *
 * @param T The type of the flow elements
 * @param count The number of elements to skip from the beginning (must be non-negative)
 * @return A new Flow that skips the first count elements
 */
fun <T> Flow<T>.skip(count: Int): Flow<T> {
    return drop(count)
}

/**
 * Extracts a slice of elements from a Flow.
 *
 * This extension function provides a convenient way to slice any Flow,
 * particularly useful for flows created by FFetch transformation operations.
 *
 * @param T The type of the flow elements
 * @param start The starting index (inclusive, must be non-negative)
 * @param end The ending index (exclusive, must be greater than start)
 * @return A new Flow that emits elements from start to end
 */
fun <T> Flow<T>.slice(
    start: Int,
    end: Int,
): Flow<T> {
    return skip(start).limit(end - start)
}
