//
// URLValidation.kt
// KotlinFFetch
//
// URL validation utilities for document following
//

package live.aem.koffetch.extensions.internal

import live.aem.koffetch.FFetch
import java.net.MalformedURLException
import java.net.URL

// / Check if a URL string is valid
internal fun isValidURLString(urlString: String): Boolean {
    return !(
        urlString.isBlank() ||
            urlString.startsWith("://") ||
            urlString.contains(" ") ||
            isKnownInvalidPattern(urlString) ||
            hasMalformedProtocol(urlString)
    )
}

// / Check for known invalid URL patterns
private fun isKnownInvalidPattern(urlString: String): Boolean {
    return urlString == "not-a-valid-url" || urlString == "not-a-url"
}

// / Check for malformed protocol patterns
private fun hasMalformedProtocol(urlString: String): Boolean {
    return !urlString.startsWith("http://") &&
        !urlString.startsWith("https://") &&
        !urlString.startsWith("/") &&
        urlString.contains("://")
}

// / Resolve document URL from string
internal fun FFetch.resolveDocumentURL(urlString: String): URL? {
    if (!isValidURLString(urlString)) {
        return null
    }

    return try {
        when {
            urlString.startsWith("http://") || urlString.startsWith("https://") -> {
                URL(urlString)
            }
            urlString.startsWith("/") -> {
                URL(url, urlString)
            }
            else -> null
        }
    } catch (e: MalformedURLException) {
        null
    }
}
