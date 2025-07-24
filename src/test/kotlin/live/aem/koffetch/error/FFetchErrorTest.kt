//
// Copyright © 2025 Terragon Labs. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package live.aem.koffetch.error

import kotlinx.serialization.SerializationException
import live.aem.koffetch.FFetchError
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FFetchErrorTest {
    @Test
    fun testInvalidURLError() {
        val invalidUrl = "not-a-valid-url"
        val error = FFetchError.InvalidURL(invalidUrl)

        assertTrue(error.message!!.contains(invalidUrl))
        assertEquals("Invalid URL: $invalidUrl", error.message)
        assertNull(error.cause)
    }

    @Test
    fun testNetworkError() {
        val cause = IOException("Connection failed")
        val error = FFetchError.NetworkError(cause)

        assertEquals(cause, error.cause)
        assertTrue(error.message!!.contains("Network error"))
        assertTrue(error.message!!.contains("Connection failed"))
    }

    @Test
    fun testNetworkErrorWithNullCauseMessage() {
        val cause = IOException()
        val error = FFetchError.NetworkError(cause)

        assertEquals(cause, error.cause)
        assertTrue(error.message!!.contains("Network error"))
    }

    @Test
    fun testDecodingError() {
        val cause = SerializationException("Invalid JSON format")
        val error = FFetchError.DecodingError(cause)

        assertEquals(cause, error.cause)
        assertTrue(error.message!!.contains("Decoding error"))
        assertTrue(error.message!!.contains("Invalid JSON format"))
    }

    @Test
    fun testInvalidResponse() {
        val error = FFetchError.InvalidResponse

        assertEquals("Invalid response format", error.message)
        assertNull(error.cause)
    }

    @Test
    fun testDocumentNotFound() {
        val error = FFetchError.DocumentNotFound

        assertEquals("Document not found", error.message)
        assertNull(error.cause)
    }

    @Test
    fun testOperationFailed() {
        val customMessage = "Failed to process request"
        val error = FFetchError.OperationFailed(customMessage)

        assertTrue(error.message!!.contains("Operation failed"))
        assertTrue(error.message!!.contains(customMessage))
        assertNull(error.cause)
    }

    @Test
    fun testOperationFailedWithEmptyMessage() {
        val error = FFetchError.OperationFailed("")

        assertEquals("Operation failed: ", error.message)
        assertNull(error.cause)
    }

    @Test
    fun testFFetchErrorIsException() {
        val error = FFetchError.InvalidURL("test")

        assertTrue(error is Exception)
        assertTrue(error is Throwable)
    }

    @Test
    fun testAllErrorTypesAreDistinct() {
        val invalidUrl = FFetchError.InvalidURL("test")
        val networkError = FFetchError.NetworkError(IOException())
        val decodingError = FFetchError.DecodingError(SerializationException())
        val invalidResponse = FFetchError.InvalidResponse
        val documentNotFound = FFetchError.DocumentNotFound
        val operationFailed = FFetchError.OperationFailed("test")

        val errors = listOf(invalidUrl, networkError, decodingError, invalidResponse, documentNotFound, operationFailed)

        // Each error should have a different class
        val classes = errors.map { it::class }.toSet()
        assertEquals(6, classes.size)
    }

    @Test
    fun testErrorMessagesAreDescriptive() {
        val errors =
            listOf(
                FFetchError.InvalidURL("test") to "Invalid URL",
                FFetchError.NetworkError(IOException("test")) to "Network error",
                FFetchError.DecodingError(SerializationException("test")) to "Decoding error",
                FFetchError.InvalidResponse to "Invalid response format",
                FFetchError.DocumentNotFound to "Document not found",
                FFetchError.OperationFailed("test") to "Operation failed",
            )

        errors.forEach { (error, expectedPrefix) ->
            assertNotNull(error.message)
            assertTrue(error.message!!.isNotEmpty())
            assertTrue(
                error.message!!.startsWith(expectedPrefix),
                "Error message '${error.message}' should start with '$expectedPrefix'",
            )
        }
    }
}
