//
// FFetchRequestHandler.kt
// KotlinFFetch
//
// Internal request handling for FFetch
//

package live.aem.koffetch.internal

import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import live.aem.koffetch.*
import java.net.URL

// / Internal class to handle HTTP requests and pagination
internal object FFetchRequestHandler {
    private const val HTTP_OK = 200
    private const val HTTP_NOT_FOUND = 404

    @OptIn(ExperimentalSerializationApi::class)
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            allowTrailingComma = true
        }

    // / Perform paginated requests and emit entries
    suspend fun performRequest(
        url: URL,
        context: FFetchContext,
        emit: suspend (FFetchEntry) -> Unit,
    ) {
        var mutableContext = context.copy()

        var offset = 0

        while (true) {
            // Check if we've reached the total (if known)
            mutableContext.total?.let { total ->
                if (offset >= total) return@performRequest
            }

            val requestURL =
                buildRequestURL(
                    url = url,
                    offset = offset,
                    context = mutableContext,
                )

            val fetchResponse =
                executeRequest(
                    url = requestURL,
                    context = mutableContext,
                )

            // Update total if this is the first request
            if (mutableContext.total == null) {
                mutableContext.total = fetchResponse.total
            }

            // Emit entries (check for cancellation between each emit)
            val entries = fetchResponse.toFFetchEntries()
            for (entry in entries) {
                emit(entry)
                // The emit function will throw CancellationException
                // if the flow collection has been cancelled
            }

            // Check if we've reached the end
            if (offset + mutableContext.chunkSize >= fetchResponse.total) {
                return@performRequest
            }

            offset += mutableContext.chunkSize
        }
    }

    // / Build request URL with pagination parameters
    private fun buildRequestURL(
        url: URL,
        offset: Int,
        context: FFetchContext,
    ): String {
        val baseUrl = url.toString()
        val separator = if (baseUrl.contains("?")) "&" else "?"

        var params = mutableListOf<String>()
        params.add("offset=$offset")
        params.add("limit=${context.chunkSize}")

        context.sheetName?.let { sheet ->
            params.add("sheet=$sheet")
        }

        return "$baseUrl$separator${params.joinToString("&")}"
    }

    // / Execute HTTP request and parse response
    private suspend fun executeRequest(
        url: String,
        context: FFetchContext,
    ): FFetchResponse {
        try {
            val (data, response) = context.httpClient.fetch(url, context.cacheConfig)

            validateHTTPResponse(response)

            // Parse JSON response
            return json.decodeFromString(FFetchResponse.serializer(), data)
        } catch (e: FFetchError) {
            throw e
        } catch (e: Exception) {
            throw FFetchError.NetworkError(e)
        }
    }

    // / Validate HTTP response status
    private fun validateHTTPResponse(response: HttpResponse) {
        when (response.status.value) {
            HTTP_OK -> return // OK
            HTTP_NOT_FOUND -> throw FFetchError.DocumentNotFound
            else -> throw FFetchError.NetworkError(
                RuntimeException("HTTP ${response.status.value}: ${response.status.description}"),
            )
        }
    }
}
