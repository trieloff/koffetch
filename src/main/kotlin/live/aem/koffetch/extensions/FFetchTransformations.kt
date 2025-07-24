//
// FFetchTransformations.kt
// KotlinFFetch
//
// Transformation operations for FFetch flows
//

package live.aem.koffetch.extensions

import live.aem.koffetch.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.map as flowMap
import kotlinx.coroutines.flow.filter as flowFilter
import kotlinx.coroutines.sync.Semaphore
import java.net.URL

// MARK: - Transformation Operations

/// Transform entries using the provided function
fun <T> FFetch.map(transform: FFetchTransform<FFetchEntry, T>): Flow<T> {
    return asFlow().map { entry -> transform(entry) }
}

/// Filter entries using the provided predicate
fun FFetch.filter(predicate: FFetchPredicate<FFetchEntry>): FFetch {
    val filteredFlow = asFlow().filter { entry -> predicate(entry) }
    return FFetch(url, context, filteredFlow)
}

/// Limit the number of entries returned
fun FFetch.limit(count: Int): FFetch {
    val limitedFlow = asFlow().take(count)
    return FFetch(url, context, limitedFlow)
}

/// Skip a number of entries from the beginning
fun FFetch.skip(count: Int): FFetch {
    val skippedFlow = asFlow().drop(count)
    return FFetch(url, context, skippedFlow)
}

/// Extract a slice of entries
fun FFetch.slice(start: Int, end: Int): FFetch {
    return skip(start).limit(end - start)
}

// MARK: - Transformation Operations for Mapped Flows

/// Transform mapped entries using the provided function
fun <T, U> Flow<T>.map(transform: suspend (T) -> U): Flow<U> {
    return flowMap(transform)
}

/// Filter transformed entries using the provided predicate
fun <T> Flow<T>.filter(predicate: suspend (T) -> Boolean): Flow<T> {
    return flowFilter(predicate)
}

/// Limit the number of transformed entries returned
fun <T> Flow<T>.limit(count: Int): Flow<T> {
    return take(count)
}

/// Skip a number of transformed entries from the beginning
fun <T> Flow<T>.skip(count: Int): Flow<T> {
    return drop(count)
}

/// Extract a slice of transformed entries
fun <T> Flow<T>.slice(start: Int, end: Int): Flow<T> {
    return skip(start).limit(end - start)
}