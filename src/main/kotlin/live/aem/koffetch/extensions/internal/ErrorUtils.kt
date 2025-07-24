//
// ErrorUtils.kt
// KotlinFFetch
//
// Error handling utilities for document following
//

package live.aem.koffetch.extensions.internal

import live.aem.koffetch.FFetchEntry

// / Create security error entry for blocked hostname
internal fun createSecurityErrorEntry(
    entry: FFetchEntry,
    newFieldName: String,
    hostname: String,
): FFetchEntry {
    return createErrorEntry(
        entry = entry,
        newFieldName = newFieldName,
        error =
            "Hostname '$hostname' is not allowed for document following. " +
                "Use .allow() to permit additional hostnames.",
    )
}

// / Create generic error entry
internal fun createErrorEntry(
    entry: FFetchEntry,
    newFieldName: String,
    error: String,
): FFetchEntry {
    return entry.toMutableMap().apply {
        put(newFieldName, null)
        put("${newFieldName}_error", error)
    }
}