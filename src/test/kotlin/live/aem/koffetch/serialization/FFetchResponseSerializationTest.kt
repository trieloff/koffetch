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

package live.aem.koffetch.serialization

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import live.aem.koffetch.FFetchResponse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FFetchResponseSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `test complete response deserialization with various structures`() {
        val jsonString =
            """
            {
                "total": 150,
                "offset": 0,
                "limit": 50,
                "data": [
                    {
                        "title": "Article 1",
                        "author": "John Doe",
                        "publishDate": "2024-01-15",
                        "tags": ["tech", "kotlin"]
                    },
                    {
                        "title": "Article 2",
                        "author": "Jane Smith",
                        "publishDate": "2024-01-20",
                        "categories": {
                            "primary": "technology",
                            "secondary": "programming"
                        }
                    }
                ]
            }
            """.trimIndent()

        val response = json.decodeFromString<FFetchResponse>(jsonString)

        assertEquals(150, response.total)
        assertEquals(0, response.offset)
        assertEquals(50, response.limit)
        assertEquals(2, response.data.size)

        val firstEntry = response.data[0]
        assertEquals("Article 1", (firstEntry["title"] as JsonPrimitive).content)
        assertEquals("John Doe", (firstEntry["author"] as JsonPrimitive).content)
    }

    @Test
    fun `test missing optional fields handling`() {
        val jsonString =
            """
            {
                "total": 100,
                "offset": 10,
                "limit": 25,
                "data": [
                    {
                        "title": "Minimal Entry"
                    }
                ]
            }
            """.trimIndent()

        val response = json.decodeFromString<FFetchResponse>(jsonString)

        assertEquals(100, response.total)
        assertEquals(10, response.offset)
        assertEquals(25, response.limit)
        assertEquals(1, response.data.size)

        val entry = response.data[0]
        assertEquals("Minimal Entry", (entry["title"] as JsonPrimitive).content)
        assertEquals(1, entry.size)
    }

    @Test
    fun `test extra unexpected fields (forward compatibility)`() {
        val jsonString =
            """
            {
                "total": 50,
                "offset": 0,
                "limit": 10,
                "data": [
                    {
                        "title": "Test Article",
                        "futureField": "some value",
                        "anotherNewField": 42
                    }
                ],
                "extraResponseField": "ignored",
                "metadata": {
                    "version": "2.0"
                }
            }
            """.trimIndent()

        val response = json.decodeFromString<FFetchResponse>(jsonString)

        assertEquals(50, response.total)
        assertEquals(0, response.offset)
        assertEquals(10, response.limit)
        assertEquals(1, response.data.size)

        val entry = response.data[0]
        assertEquals("Test Article", (entry["title"] as JsonPrimitive).content)
        assertEquals("some value", (entry["futureField"] as JsonPrimitive).content)
        assertEquals(42, (entry["anotherNewField"] as JsonPrimitive).content.toInt())
    }

    @Test
    fun `test array vs object confusion scenarios`() {
        val jsonString =
            """
            {
                "total": 1,
                "offset": 0,
                "limit": 1,
                "data": [
                    {
                        "stringField": "text",
                        "numberField": 123,
                        "booleanField": true,
                        "nullField": null,
                        "arrayField": ["item1", "item2"],
                        "objectField": {
                            "nested": "value"
                        }
                    }
                ]
            }
            """.trimIndent()

        val response = json.decodeFromString<FFetchResponse>(jsonString)
        val entry = response.data[0]

        assertTrue(entry["stringField"] is JsonPrimitive)
        assertTrue(entry["numberField"] is JsonPrimitive)
        assertTrue(entry["booleanField"] is JsonPrimitive)
        assertTrue(entry["arrayField"] is kotlinx.serialization.json.JsonArray)
        assertTrue(entry["objectField"] is JsonObject)
    }

    @Test
    fun `test toFFetchEntries conversion with edge cases`() {
        val response =
            FFetchResponse(
                total = 3,
                offset = 0,
                limit = 3,
                data =
                    listOf(
                        buildJsonObject {
                            put("title", "\"Quoted Title\"")
                            put("description", "Normal text")
                            put("number", 42)
                            put("boolean", true)
                        },
                        buildJsonObject {
                            put("emptyString", "")
                            put("whitespace", "   ")
                        },
                        buildJsonObject {
                            put("specialChars", "Hello\nWorld\t!")
                            put("unicode", "🚀 Kotlin")
                        },
                    ),
            )

        val entries = response.toFFetchEntries()

        assertEquals(3, entries.size)

        // First entry
        assertEquals("Quoted Title", entries[0]["title"])
        assertEquals("Normal text", entries[0]["description"])
        assertEquals("42", entries[0]["number"])
        assertEquals("true", entries[0]["boolean"])

        // Second entry
        assertEquals("", entries[1]["emptyString"])
        assertEquals("   ", entries[1]["whitespace"])

        // Third entry
        assertEquals("Hello\nWorld\t!", entries[2]["specialChars"])
        assertEquals("🚀 Kotlin", entries[2]["unicode"])
    }

    @Test
    fun `test serializer behavior with empty data array`() {
        val jsonString =
            """
            {
                "total": 0,
                "offset": 0,
                "limit": 10,
                "data": []
            }
            """.trimIndent()

        val response = json.decodeFromString<FFetchResponse>(jsonString)

        assertEquals(0, response.total)
        assertEquals(0, response.offset)
        assertEquals(10, response.limit)
        assertEquals(0, response.data.size)

        val entries = response.toFFetchEntries()
        assertEquals(0, entries.size)
    }

    @Test
    fun `test large response deserialization`() {
        val dataEntries =
            (1..1000).map { index ->
                buildJsonObject {
                    put("id", index)
                    put("title", "Article $index")
                    put("content", "This is the content of article number $index")
                }
            }

        val response =
            FFetchResponse(
                total = 1000,
                offset = 0,
                limit = 1000,
                data = dataEntries,
            )

        val entries = response.toFFetchEntries()
        assertEquals(1000, entries.size)
        assertEquals("500", entries[499]["id"])
        assertEquals("Article 500", entries[499]["title"])
    }

    @Test
    fun `test nested object serialization behavior`() {
        val jsonString =
            """
            {
                "total": 1,
                "offset": 0,
                "limit": 1,
                "data": [
                    {
                        "metadata": {
                            "author": {
                                "name": "John Doe",
                                "email": "john@example.com"
                            },
                            "tags": ["kotlin", "serialization"]
                        }
                    }
                ]
            }
            """.trimIndent()

        val response = json.decodeFromString<FFetchResponse>(jsonString)
        val entry = response.data[0]

        assertTrue(entry["metadata"] is JsonObject)
        val metadata = entry["metadata"] as JsonObject
        assertTrue(metadata["author"] is JsonObject)
        assertTrue(metadata["tags"] is kotlinx.serialization.json.JsonArray)
    }

    @Test
    fun `test malformed JSON handling`() {
        val invalidJsonString =
            """
            {
                "total": "not a number",
                "offset": 0,
                "limit": 10,
                "data": []
            }
            """.trimIndent()

        assertThrows<kotlinx.serialization.SerializationException> {
            json.decodeFromString<FFetchResponse>(invalidJsonString)
        }
    }

    @Test
    fun `test response with null values in data`() {
        val jsonString =
            """
            {
                "total": 2,
                "offset": 0,
                "limit": 2,
                "data": [
                    {
                        "title": "Article with nulls",
                        "author": null,
                        "publishDate": "2024-01-15"
                    },
                    {
                        "title": null,
                        "content": "Article without title"
                    }
                ]
            }
            """.trimIndent()

        val response = json.decodeFromString<FFetchResponse>(jsonString)
        val entries = response.toFFetchEntries()

        assertEquals(2, entries.size)
        assertEquals("Article with nulls", entries[0]["title"])
        assertEquals("null", entries[0]["author"])
        assertEquals("null", entries[1]["title"])
    }

    @Test
    fun `test serializer descriptor and serialization`() {
        val serializer = FFetchResponse.serializer()

        // Test serializer descriptor
        assertNotNull(serializer.descriptor)
        assertEquals("live.aem.koffetch.FFetchResponse", serializer.descriptor.serialName)
        assertEquals(4, serializer.descriptor.elementsCount)

        // Test round-trip serialization/deserialization
        val original =
            FFetchResponse(
                total = 100,
                offset = 10,
                limit = 25,
                data =
                    listOf(
                        buildJsonObject {
                            put("title", "Test Article")
                            put("content", "Test content")
                        },
                    ),
            )

        val serialized = json.encodeToString(serializer, original)
        val deserialized = json.decodeFromString(serializer, serialized)

        assertEquals(original.total, deserialized.total)
        assertEquals(original.offset, deserialized.offset)
        assertEquals(original.limit, deserialized.limit)
        assertEquals(original.data.size, deserialized.data.size)
    }

    @Test
    fun `test serializer with complex nested structures`() {
        val complexResponse =
            FFetchResponse(
                total = 1,
                offset = 0,
                limit = 1,
                data =
                    listOf(
                        buildJsonObject {
                            put(
                                "nested",
                                buildJsonObject {
                                    put(
                                        "level2",
                                        buildJsonObject {
                                            put("value", "deep value")
                                        },
                                    )
                                },
                            )
                            put(
                                "array",
                                kotlinx.serialization.json.JsonArray(
                                    listOf(
                                        JsonPrimitive("item1"),
                                        JsonPrimitive("item2"),
                                    ),
                                ),
                            )
                        },
                    ),
            )

        val serialized = json.encodeToString(FFetchResponse.serializer(), complexResponse)
        val deserialized = json.decodeFromString<FFetchResponse>(serialized)

        assertEquals(1, deserialized.total)
        assertEquals(1, deserialized.data.size)

        val entry = deserialized.data[0]
        assertTrue(entry["nested"] is JsonObject)
        assertTrue(entry["array"] is kotlinx.serialization.json.JsonArray)
    }
}
