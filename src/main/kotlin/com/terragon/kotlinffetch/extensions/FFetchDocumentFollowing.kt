//
// FFetchDocumentFollowing.kt
// KotlinFFetch
//
// Document following operations for FFetch flows
//

package com.terragon.kotlinffetch.extensions

import com.terragon.kotlinffetch.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.supervisorScope
import java.net.URL

// MARK: - Document Following

/// Follow references to fetch HTML documents
///
/// This method fetches HTML documents from URLs found in the specified field and parses them
/// into Jsoup Document objects. For security, document following is restricted to the same
/// hostname as the initial request by default.
///
/// For security, by default only URLs with the same hostname as the initial request are allowed.
/// Use `.allow()` to permit additional hostnames:
/// ```kotlin
/// .allow("trusted.com")               // Allow specific hostname
/// .allow(listOf("api.com", "cdn.com")) // Allow multiple hostnames
/// .allow("*")                         // Allow all hostnames (use with caution)
/// ```
///
/// Error Handling:
/// If a document cannot be fetched (due to network errors, security restrictions, or parsing failures),
/// the document field will be `null` and an error description will be stored in `{fieldName}_error`.
fun FFetch.follow(fieldName: String, newFieldName: String? = null): FFetch {
    val targetFieldName = newFieldName ?: fieldName
    
    val followFlow = flow {
        supervisorScope {
            val entries = asFlow().toList()
            val tasks = entries.chunked(context.maxConcurrency).map { chunk ->
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

/// Internal method to follow a document reference
private suspend fun FFetch.followDocument(
    entry: FFetchEntry,
    fieldName: String,
    newFieldName: String
): FFetchEntry {
    val urlString = entry[fieldName] as? String
        ?: return createErrorEntry(
            entry = entry,
            newFieldName = newFieldName,
            error = "Missing or invalid URL string in field '$fieldName'"
        )
    
    val resolvedURL = resolveDocumentURL(urlString)
        ?: return createErrorEntry(
            entry = entry,
            newFieldName = newFieldName,
            error = "Could not resolve URL from field '$fieldName': $urlString"
        )
    
    if (!isHostnameAllowed(resolvedURL)) {
        return createSecurityErrorEntry(
            entry = entry,
            newFieldName = newFieldName,
            hostname = resolvedURL.host ?: "unknown"
        )
    }
    
    return fetchDocumentData(
        entry = entry,
        newFieldName = newFieldName,
        resolvedURL = resolvedURL
    )
}

/// Create security error entry for blocked hostname
private fun createSecurityErrorEntry(
    entry: FFetchEntry,
    newFieldName: String,
    hostname: String
): FFetchEntry {
    return createErrorEntry(
        entry = entry,
        newFieldName = newFieldName,
        error = "Hostname '$hostname' is not allowed for document following. " +
               "Use .allow() to permit additional hostnames."
    )
}

/// Fetch document data from resolved URL
private suspend fun FFetch.fetchDocumentData(
    entry: FFetchEntry,
    newFieldName: String,
    resolvedURL: URL
): FFetchEntry {
    return try {
        val (data, response) = context.httpClient.fetch(resolvedURL.toString(), context.cacheConfig)
        
        if (response.status.value != 200) {
            return createErrorEntry(
                entry = entry,
                newFieldName = newFieldName,
                error = "HTTP error ${response.status.value} for $resolvedURL"
            )
        }
        
        parseDocumentData(
            data = data,
            entry = entry,
            newFieldName = newFieldName,
            resolvedURL = resolvedURL
        )
        
    } catch (e: Exception) {
        createErrorEntry(
            entry = entry,
            newFieldName = newFieldName,
            error = "Network error for $resolvedURL: ${e.message}"
        )
    }
}

/// Resolve document URL from string
private fun FFetch.resolveDocumentURL(urlString: String): URL? {
    return try {
        if (urlString.startsWith("http://") || urlString.startsWith("https://")) {
            URL(urlString)
        } else {
            URL(url, urlString)
        }
    } catch (e: Exception) {
        null
    }
}

/// Parse document data and return updated entry
private fun FFetch.parseDocumentData(
    data: String,
    entry: FFetchEntry,
    newFieldName: String,
    resolvedURL: URL
): FFetchEntry {
    return try {
        val document = context.htmlParser.parse(data)
        entry.toMutableMap().apply {
            put(newFieldName, document)
        }
    } catch (e: Exception) {
        createErrorEntry(
            entry = entry,
            newFieldName = newFieldName,
            error = "HTML parsing error for $resolvedURL: ${e.message}"
        )
    }
}

/// Create an entry with error information
private fun createErrorEntry(
    entry: FFetchEntry,
    newFieldName: String,
    error: String
): FFetchEntry {
    return entry.toMutableMap().apply {
        put(newFieldName, null)
        put("${newFieldName}_error", error)
    }
}

/// Check if hostname is allowed for document following
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

/// Get default port for protocol
private fun getDefaultPort(protocol: String): Int {
    return when (protocol.lowercase()) {
        "http" -> 80
        "https" -> 443
        else -> -1
    }
}

// MARK: - Hostname Security Configuration

/// Allow document following from specific hostname
fun FFetch.allow(hostname: String): FFetch {
    val newContext = context.copy(allowedHosts = context.allowedHosts.toMutableSet().apply {
        add(hostname)
    })
    return FFetch(url, newContext, upstream)
}

/// Allow document following from multiple hostnames
fun FFetch.allow(hostnames: List<String>): FFetch {
    val newContext = context.copy(allowedHosts = context.allowedHosts.toMutableSet().apply {
        addAll(hostnames)
    })
    return FFetch(url, newContext, upstream)
}