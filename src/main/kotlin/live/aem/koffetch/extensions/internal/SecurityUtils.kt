//
// SecurityUtils.kt
// KotlinFFetch
//
// Security utilities for document following hostname validation
//

package live.aem.koffetch.extensions.internal

import live.aem.koffetch.FFetch
import java.net.URL

private const val HTTP_DEFAULT_PORT = 80
private const val HTTPS_DEFAULT_PORT = 443

// / Check if hostname is allowed for document following
internal fun FFetch.isHostnameAllowed(url: URL): Boolean {
    val allowedHosts = context.allowedHosts
    
    return when {
        allowedHosts.contains("*") -> true
        url.host == null -> false
        else -> {
            val hostname = url.host
            val port = url.port
            val defaultPort = getDefaultPort(url.protocol)
            
            when {
                port != -1 && port != defaultPort -> allowedHosts.contains("$hostname:$port")
                else -> allowedHosts.contains(hostname)
            }
        }
    }
}

// / Get default port for protocol
private fun getDefaultPort(protocol: String): Int {
    return when (protocol.lowercase()) {
        "http" -> HTTP_DEFAULT_PORT
        "https" -> HTTPS_DEFAULT_PORT
        else -> -1
    }
}