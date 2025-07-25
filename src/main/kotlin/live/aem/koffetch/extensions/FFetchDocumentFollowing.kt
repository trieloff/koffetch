//
// FFetchDocumentFollowing.kt
// KotlinFFetch
//
// Document following operations for FFetch flows
//

package live.aem.koffetch.extensions

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.supervisorScope
import live.aem.koffetch.FFetch
import live.aem.koffetch.FFetchEntry
import live.aem.koffetch.FFetchError
import live.aem.koffetch.extensions.internal.createErrorEntry
import live.aem.koffetch.extensions.internal.createSecurityErrorEntry
import live.aem.koffetch.extensions.internal.isHostnameAllowed
import live.aem.koffetch.extensions.internal.resolveDocumentURL
import java.net.URL

// MARK: - Constants

private const val HTTP_OK = 200

// MARK: - Document Following

/**
 * Follows references to fetch and parse HTML documents from URLs in entries.
 *
 * This method fetches HTML documents from URLs found in the specified field and parses them
 * into Jsoup Document objects. The parsed documents are added to each entry under the
 * specified field name (or a new field name if provided).
 *
 * For security, document following is restricted to the same hostname as the initial request
 * by default. Use the `allow()` method to permit additional hostnames:
 *
 * ```kotlin
 * ffetch("/query-index.json")
 *     .allow("trusted.com")               // Allow specific hostname
 *     .allow(listOf("api.com", "cdn.com")) // Allow multiple hostnames
 *     .allow("*")                         // Allow all hostnames (use with caution)
 *     .follow("path", "document")
 * ```
 *
 * **Error Handling:**
 * If a document cannot be fetched (due to network errors, security restrictions, or parsing failures),
 * the document field will be `null` and an error description will be stored in `{fieldName}_error`.
 *
 * @param fieldName The name of the field containing the URL to follow
 * @param newFieldName Optional new field name to store the parsed document (defaults to fieldName)
 * @return A new FFetch instance that will include parsed documents in the entries
 */
fun FFetch.follow(
    fieldName: String,
    newFieldName: String? = null,
): FFetch {
    val targetFieldName = newFieldName ?: fieldName

    val followFlow =
        flow {
            supervisorScope {
                val entries = asFlow().toList()
                val tasks =
                    entries.chunked(context.maxConcurrency).map { chunk ->
                        chunk.map { entry ->
                            async {
                                followDocument(entry, fieldName, targetFieldName)
                            }
                        }
                    }

                for (chunkTasks in tasks) {
                    val results = chunkTasks.awaitAll()
                    results.forEach { result -> emit(result) }
                }
            }
        }

    return FFetch(url, context, followFlow)
}

// / Internal method to follow a document reference
private suspend fun FFetch.followDocument(
    entry: FFetchEntry,
    fieldName: String,
    newFieldName: String,
): FFetchEntry {
    val urlString = entry[fieldName] as? String
        ?: return createErrorEntry(
            entry = entry,
            newFieldName = newFieldName,
            error = "Missing or invalid URL string in field '$fieldName'",
        )

    val resolvedURL = resolveDocumentURL(urlString)
        ?: return createErrorEntry(
            entry = entry,
            newFieldName = newFieldName,
            error = "Could not resolve URL from field '$fieldName': $urlString",
        )

    return if (!isHostnameAllowed(resolvedURL)) {
        createSecurityErrorEntry(
            entry = entry,
            newFieldName = newFieldName,
            hostname = resolvedURL.host ?: "unknown",
        )
    } else {
        fetchDocumentData(
            entry = entry,
            newFieldName = newFieldName,
            resolvedURL = resolvedURL,
        )
    }
}

// / Fetch document data from resolved URL
private suspend fun FFetch.fetchDocumentData(
    entry: FFetchEntry,
    newFieldName: String,
    resolvedURL: URL,
): FFetchEntry {
    return try {
        val (data, response) = context.httpClient.fetch(resolvedURL.toString(), context.cacheConfig)

        if (response.status.value != HTTP_OK) {
            createErrorEntry(
                entry = entry,
                newFieldName = newFieldName,
                error = "HTTP error ${response.status.value} for $resolvedURL",
            )
        } else {
            parseDocumentData(
                data = data,
                entry = entry,
                newFieldName = newFieldName,
                resolvedURL = resolvedURL,
            )
        }
    } catch (e: FFetchError.NetworkError) {
        createErrorEntry(
            entry = entry,
            newFieldName = newFieldName,
            error = "Network error for $resolvedURL: ${e.message}",
        )
    }
}

// / Parse document data and return updated entry
private fun FFetch.parseDocumentData(
    data: String,
    entry: FFetchEntry,
    newFieldName: String,
    resolvedURL: URL,
): FFetchEntry {
    return try {
        val document = context.htmlParser.parse(data)
        entry.toMutableMap().apply {
            put(newFieldName, document)
        }.toMap()
    } catch (e: FFetchError.DecodingError) {
        createErrorEntry(
            entry = entry,
            newFieldName = newFieldName,
            error = "HTML parsing error for $resolvedURL: ${e.message}",
        )
    }
}

// / Create an entry with error information

// MARK: - Hostname Security Configuration

/**
 * Allows document following from a specific hostname.
 *
 * This method adds the specified hostname to the list of allowed hosts for document following
 * operations. By default, only the hostname of the initial request is allowed for security.
 *
 * @param hostname The hostname to allow for document following (e.g., "example.com")
 * @return A new FFetch instance with the hostname added to the allowed hosts
 */
fun FFetch.allow(hostname: String): FFetch {
    val newContext =
        context.copy(
            allowedHosts =
                context.allowedHosts.toMutableSet().apply {
                    add(hostname)
                },
        )
    return FFetch(url, newContext, upstream)
}

/**
 * Allows document following from multiple hostnames.
 *
 * This method adds the specified hostnames to the list of allowed hosts for document following
 * operations. Use "*" to allow all hostnames (use with caution as this disables the security feature).
 *
 * @param hostnames The list of hostnames to allow for document following
 * @return A new FFetch instance with the hostnames added to the allowed hosts
 */
fun FFetch.allow(hostnames: List<String>): FFetch {
    val newContext =
        context.copy(
            allowedHosts =
                context.allowedHosts.toMutableSet().apply {
                    addAll(hostnames)
                },
        )
    return FFetch(url, newContext, upstream)
}
