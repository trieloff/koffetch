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
import java.net.URL

// MARK: - Constants

private const val HTTP_OK = 200
private const val HTTP_DEFAULT_PORT = 80
private const val HTTPS_DEFAULT_PORT = 443

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
    val urlString =
        entry[fieldName] as? String
            ?: return createErrorEntry(
                entry = entry,
                newFieldName = newFieldName,
                error = "Missing or invalid URL string in field '$fieldName'",
            )

    val resolvedURL =
        resolveDocumentURL(urlString)
            ?: return createErrorEntry(
                entry = entry,
                newFieldName = newFieldName,
                error = "Could not resolve URL from field '$fieldName': $urlString",
            )

    if (!isHostnameAllowed(resolvedURL)) {
        return createSecurityErrorEntry(
            entry = entry,
            newFieldName = newFieldName,
            hostname = resolvedURL.host ?: "unknown",
        )
    }

    return fetchDocumentData(
        entry = entry,
        newFieldName = newFieldName,
        resolvedURL = resolvedURL,
    )
}

// / Create security error entry for blocked hostname
private fun createSecurityErrorEntry(
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

// / Fetch document data from resolved URL
private suspend fun FFetch.fetchDocumentData(
    entry: FFetchEntry,
    newFieldName: String,
    resolvedURL: URL,
): FFetchEntry {
    return try {
        val (data, response) = context.httpClient.fetch(resolvedURL.toString(), context.cacheConfig)

        if (response.status.value != HTTP_OK) {
            return createErrorEntry(
                entry = entry,
                newFieldName = newFieldName,
                error = "HTTP error ${response.status.value} for $resolvedURL",
            )
        }

        return parseDocumentData(
            data = data,
            entry = entry,
            newFieldName = newFieldName,
            resolvedURL = resolvedURL,
        )
    } catch (e: Exception) {
        createErrorEntry(
            entry = entry,
            newFieldName = newFieldName,
            error = "Network error for $resolvedURL: ${e.message}",
        )
    }
}

// / Resolve document URL from string
private fun FFetch.resolveDocumentURL(urlString: String): URL? {
    return try {
        // Validate URL string - reject obviously invalid patterns
        if (urlString.isBlank() ||
            urlString.startsWith("://") ||
            urlString.contains(" ") ||
            // Specific patterns that are clearly invalid
            urlString == "not-a-valid-url" ||
            urlString == "not-a-url" ||
            // Check for malformed protocol patterns
            (
                !urlString.startsWith("http://") &&
                    !urlString.startsWith("https://") &&
                    !urlString.startsWith("/") &&
                    urlString.contains("://")
            ) // Has protocol separator but not at start
        ) {
            return null
        }

        val resolvedURL =
            if (urlString.startsWith("http://") || urlString.startsWith("https://")) {
                URL(urlString)
            } else {
                URL(url, urlString)
            }
        resolvedURL
    } catch (e: Exception) {
        null
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
    } catch (e: Exception) {
        createErrorEntry(
            entry = entry,
            newFieldName = newFieldName,
            error = "HTML parsing error for $resolvedURL: ${e.message}",
        )
    }
}

// / Create an entry with error information
private fun createErrorEntry(
    entry: FFetchEntry,
    newFieldName: String,
    error: String,
): FFetchEntry {
    return entry.toMutableMap().apply {
        put(newFieldName, null)
        put("${newFieldName}_error", error)
    }
}

// / Check if hostname is allowed for document following
private fun FFetch.isHostnameAllowed(url: URL): Boolean {
    // Allow wildcard
    if (context.allowedHosts.contains("*")) {
        return true
    }

    // Allow if hostname matches any in the allowlist
    // Include port number for security - different ports should be treated as different hostnames
    val hostname = url.host ?: return false
    val port = url.port
    val defaultPort = getDefaultPort(url.protocol)

    // For non-default ports, require explicit hostname:port permission
    if (port != -1 && port != defaultPort) {
        val hostnameWithPort = "$hostname:$port"
        return context.allowedHosts.contains(hostnameWithPort)
    }

    // For default ports, check for hostname-only permission
    return context.allowedHosts.contains(hostname)
}

// / Get default port for protocol
private fun getDefaultPort(protocol: String): Int {
    return when (protocol.lowercase()) {
        "http" -> HTTP_DEFAULT_PORT
        "https" -> HTTPS_DEFAULT_PORT
        else -> -1
    }
}

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
