//
// FFetchCollectionOperations.kt
// KotlinFFetch
//
// Collection operations for FFetch flows
//

package live.aem.koffetch.extensions

import live.aem.koffetch.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.toList

// MARK: - Collection Operations

/// Collect all entries into a list
suspend fun FFetch.all(): List<FFetchEntry> {
    return asFlow().toList()
}

/// Get the first entry
suspend fun FFetch.first(): FFetchEntry? {
    return asFlow().firstOrNull()
}

/// Count the total number of entries
suspend fun FFetch.count(): Int {
    return asFlow().fold(0) { count, _ -> count + 1 }
}

// MARK: - Collection Operations for Mapped Flows

/// Collect all transformed entries into a list
suspend fun <T> Flow<T>.all(): List<T> {
    return toList()
}

/// Get the first transformed entry
suspend fun <T> Flow<T>.first(): T? {
    return firstOrNull()
}

/// Count the total number of transformed entries
suspend fun <T> Flow<T>.count(): Int {
    return fold(0) { count, _ -> count + 1 }
}