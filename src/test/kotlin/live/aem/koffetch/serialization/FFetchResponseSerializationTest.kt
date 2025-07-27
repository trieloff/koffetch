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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import live.aem.koffetch.FFetchResponse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun `test missing required field total`() {
        val jsonString =
            """
            {
                "offset": 0,
                "limit": 10,
                "data": []
            }
            """.trimIndent()

        assertThrows<kotlinx.serialization.MissingFieldException> {
            json.decodeFromString<FFetchResponse>(jsonString)
        }
    }

    @Test
    fun `test missing required field offset`() {
        val jsonString =
            """
            {
                "total": 100,
                "limit": 10,
                "data": []
            }
            """.trimIndent()

        assertThrows<kotlinx.serialization.MissingFieldException> {
            json.decodeFromString<FFetchResponse>(jsonString)
        }
    }

    @Test
    fun `test missing required field limit`() {
        val jsonString =
            """
            {
                "total": 100,
                "offset": 0,
                "data": []
            }
            """.trimIndent()

        assertThrows<kotlinx.serialization.MissingFieldException> {
            json.decodeFromString<FFetchResponse>(jsonString)
        }
    }

    @Test
    fun `test missing required field data`() {
        val jsonString =
            """
            {
                "total": 100,
                "offset": 0,
                "limit": 10
            }
            """.trimIndent()

        assertThrows<kotlinx.serialization.MissingFieldException> {
            json.decodeFromString<FFetchResponse>(jsonString)
        }
    }

    @Test
    fun `test null values for required fields`() {
        val jsonString =
            """
            {
                "total": null,
                "offset": 0,
                "limit": 10,
                "data": []
            }
            """.trimIndent()

        assertThrows<kotlinx.serialization.SerializationException> {
            json.decodeFromString<FFetchResponse>(jsonString)
        }
    }

    @Test
    fun `test data field with null instead of array`() {
        val jsonString =
            """
            {
                "total": 100,
                "offset": 0,
                "limit": 10,
                "data": null
            }
            """.trimIndent()

        assertThrows<kotlinx.serialization.SerializationException> {
            json.decodeFromString<FFetchResponse>(jsonString)
        }
    }

    @Test
    fun `test wrong type for integer fields`() {
        val jsonString =
            """
            {
                "total": 100,
                "offset": "zero",
                "limit": 10,
                "data": []
            }
            """.trimIndent()

        assertThrows<kotlinx.serialization.SerializationException> {
            json.decodeFromString<FFetchResponse>(jsonString)
        }
    }

    @Test
    fun `test data field with non-object elements`() {
        val jsonString =
            """
            {
                "total": 100,
                "offset": 0,
                "limit": 10,
                "data": ["string", 123, true]
            }
            """.trimIndent()

        assertThrows<kotlinx.serialization.SerializationException> {
            json.decodeFromString<FFetchResponse>(jsonString)
        }
    }

    @Test
    fun `test serialization with extreme values`() {
        val extremeResponse =
            FFetchResponse(
                total = Int.MAX_VALUE,
                offset = Int.MAX_VALUE - 1000,
                limit = Int.MAX_VALUE,
                data = emptyList(),
            )

        val serialized = json.encodeToString(FFetchResponse.serializer(), extremeResponse)
        val deserialized = json.decodeFromString<FFetchResponse>(serialized)

        assertEquals(Int.MAX_VALUE, deserialized.total)
        assertEquals(Int.MAX_VALUE - 1000, deserialized.offset)
        assertEquals(Int.MAX_VALUE, deserialized.limit)
    }

    @Test
    fun `test serialization with negative values`() {
        val negativeResponse =
            FFetchResponse(
                total = -1,
                offset = -10,
                limit = -5,
                data = emptyList(),
            )

        val serialized = json.encodeToString(FFetchResponse.serializer(), negativeResponse)
        val deserialized = json.decodeFromString<FFetchResponse>(serialized)

        assertEquals(-1, deserialized.total)
        assertEquals(-10, deserialized.offset)
        assertEquals(-5, deserialized.limit)
    }

    @Test
    fun `test very deeply nested JSON structures`() {
        val deeplyNested =
            buildJsonObject {
                put(
                    "level1",
                    buildJsonObject {
                        put(
                            "level2",
                            buildJsonObject {
                                put(
                                    "level3",
                                    buildJsonObject {
                                        put(
                                            "level4",
                                            buildJsonObject {
                                                put(
                                                    "level5",
                                                    buildJsonObject {
                                                        put("value", "deep")
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            }

        val response =
            FFetchResponse(
                total = 1,
                offset = 0,
                limit = 1,
                data = listOf(deeplyNested),
            )

        val serialized = json.encodeToString(FFetchResponse.serializer(), response)
        val deserialized = json.decodeFromString<FFetchResponse>(serialized)

        assertEquals(1, deserialized.data.size)
        val entry = deserialized.data[0]
        assertTrue(entry["level1"] is JsonObject)
    }

    @Test
    fun `test serialization with mixed array types`() {
        val mixedArrayData =
            buildJsonObject {
                put(
                    "mixedArray",
                    kotlinx.serialization.json.JsonArray(
                        listOf(
                            JsonPrimitive("string"),
                            JsonPrimitive(123),
                            JsonPrimitive(true),
                            kotlinx.serialization.json.JsonNull,
                            buildJsonObject { put("key", "value") },
                            kotlinx.serialization.json.JsonArray(
                                listOf(JsonPrimitive("nested")),
                            ),
                        ),
                    ),
                )
            }

        val response =
            FFetchResponse(
                total = 1,
                offset = 0,
                limit = 1,
                data = listOf(mixedArrayData),
            )

        val serialized = json.encodeToString(FFetchResponse.serializer(), response)
        val deserialized = json.decodeFromString<FFetchResponse>(serialized)

        assertEquals(1, deserialized.data.size)
        assertTrue(deserialized.data[0]["mixedArray"] is kotlinx.serialization.json.JsonArray)
    }

    @Test
    fun `test serializer with empty strings as values`() {
        val emptyStringData =
            buildJsonObject {
                put("empty", "")
                put("spaces", "   ")
                put("tabs", "\t\t")
                put("newlines", "\n\n")
                put("mixed", " \t\n ")
            }

        val response =
            FFetchResponse(
                total = 1,
                offset = 0,
                limit = 1,
                data = listOf(emptyStringData),
            )

        val serialized = json.encodeToString(FFetchResponse.serializer(), response)
        assertTrue(serialized.contains("\"empty\":\"\""))
        assertTrue(serialized.contains("\"spaces\":\"   \""))

        val deserialized = json.decodeFromString<FFetchResponse>(serialized)
        val entry = deserialized.data[0]
        assertEquals("", (entry["empty"] as JsonPrimitive).content)
        assertEquals("   ", (entry["spaces"] as JsonPrimitive).content)
    }

    @Test
    fun `test serializer with boolean edge cases`() {
        val booleanData =
            buildJsonObject {
                put("true", true)
                put("false", false)
                put("trueString", "true")
                put("falseString", "false")
                put("True", "True")
                put("FALSE", "FALSE")
            }

        val response =
            FFetchResponse(
                total = 1,
                offset = 0,
                limit = 1,
                data = listOf(booleanData),
            )

        val serialized = json.encodeToString(FFetchResponse.serializer(), response)
        val deserialized = json.decodeFromString<FFetchResponse>(serialized)

        val entry = deserialized.data[0]
        val trueValue = entry["true"] as JsonPrimitive
        val falseValue = entry["false"] as JsonPrimitive
        assertTrue(trueValue.isString.not() && trueValue.content == "true")
        assertFalse(falseValue.isString.not() && falseValue.content == "true")
        assertEquals("true", (entry["trueString"] as JsonPrimitive).content)
    }

    @Test
    fun `test serializer with special JSON characters`() {
        val specialCharsData =
            buildJsonObject {
                put("quotes", "He said \"Hello\"")
                put("backslash", "C:\\Windows\\System32")
                put("unicode", "\u0048\u0065\u006C\u006C\u006F")
                put("control", "Line1\nLine2\rLine3\tTabbed")
                put("escaped", "\\\"\\n\\r\\t\\\\")
            }

        val response =
            FFetchResponse(
                total = 1,
                offset = 0,
                limit = 1,
                data = listOf(specialCharsData),
            )

        val serialized = json.encodeToString(FFetchResponse.serializer(), response)
        val deserialized = json.decodeFromString<FFetchResponse>(serialized)

        val entry = deserialized.data[0]
        assertEquals("He said \"Hello\"", (entry["quotes"] as JsonPrimitive).content)
        assertEquals("C:\\Windows\\System32", (entry["backslash"] as JsonPrimitive).content)
    }

    @Test
    fun `test serializer element indices and descriptors`() {
        val serializer = FFetchResponse.serializer()
        val descriptor = serializer.descriptor

        // Test element names
        assertEquals("total", descriptor.getElementName(0))
        assertEquals("offset", descriptor.getElementName(1))
        assertEquals("limit", descriptor.getElementName(2))
        assertEquals("data", descriptor.getElementName(3))

        // Test element descriptors
        assertNotNull(descriptor.getElementDescriptor(0))
        assertNotNull(descriptor.getElementDescriptor(1))
        assertNotNull(descriptor.getElementDescriptor(2))
        assertNotNull(descriptor.getElementDescriptor(3))
    }

    @Test
    fun `test serialization with field order variations`() {
        // JSON with fields in different order
        val reorderedJson =
            """
            {
                "data": [{"id": 1}],
                "limit": 20,
                "total": 100,
                "offset": 5
            }
            """.trimIndent()

        val response = json.decodeFromString<FFetchResponse>(reorderedJson)

        assertEquals(100, response.total)
        assertEquals(5, response.offset)
        assertEquals(20, response.limit)
        assertEquals(1, response.data.size)
    }

    @Test
    fun `test custom Json configuration with lenient parsing`() {
        val lenientJson =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                allowStructuredMapKeys = true
            }

        val malformedButLenientJson =
            """
            {
                total: 100,
                offset: 0,
                limit: 10,
                data: [],
                extra: "ignored"
            }
            """.trimIndent()

        val response = lenientJson.decodeFromString<FFetchResponse>(malformedButLenientJson)
        assertEquals(100, response.total)
    }

    @Test
    fun `test serializer with duplicate keys in data`() {
        // This tests how the serializer handles malformed JSON with duplicate keys
        val jsonWithDuplicates =
            """
            {
                "total": 1,
                "offset": 0,
                "limit": 1,
                "data": [
                    {
                        "id": 1,
                        "name": "First",
                        "id": 2,
                        "name": "Second"
                    }
                ]
            }
            """.trimIndent()

        // Most JSON parsers will use the last value for duplicate keys
        val response = json.decodeFromString<FFetchResponse>(jsonWithDuplicates)
        val entry = response.data[0]
        assertEquals("2", (entry["id"] as JsonPrimitive).content)
        assertEquals("Second", (entry["name"] as JsonPrimitive).content)
    }

    @Test
    fun `test explicit serializer usage`() {
        val response =
            FFetchResponse(
                total = 50,
                offset = 10,
                limit = 20,
                data =
                    listOf(
                        buildJsonObject {
                            put("explicit", "test")
                        },
                    ),
            )

        // Use explicit serializer instance
        val explicitSerializer = serializer<FFetchResponse>()
        val serialized = json.encodeToString(explicitSerializer, response)
        val deserialized = json.decodeFromString(explicitSerializer, serialized)

        assertEquals(response.total, deserialized.total)
        assertEquals(response.offset, deserialized.offset)
        assertEquals(response.limit, deserialized.limit)
    }

    @Test
    fun `test polymorphic serialization context`() {
        val polymorphicData =
            buildJsonObject {
                put("type", "article")
                put(
                    "content",
                    buildJsonObject {
                        put("title", "Test Article")
                        put("body", "Article body")
                    },
                )
                put(
                    "metadata",
                    kotlinx.serialization.json.JsonArray(
                        listOf(
                            buildJsonObject {
                                put("key", "author")
                                put("value", "John Doe")
                            },
                            buildJsonObject {
                                put("key", "date")
                                put("value", "2024-01-15")
                            },
                        ),
                    ),
                )
            }

        val response =
            FFetchResponse(
                total = 1,
                offset = 0,
                limit = 1,
                data = listOf(polymorphicData),
            )

        val serialized = json.encodeToString(FFetchResponse.serializer(), response)
        val deserialized = json.decodeFromString<FFetchResponse>(serialized)

        assertEquals(1, deserialized.data.size)
        assertEquals("article", (deserialized.data[0]["type"] as JsonPrimitive).content)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `test deserialization with explicit nulls`() {
        val jsonConfigExplicitNulls =
            Json {
                explicitNulls = false
                ignoreUnknownKeys = true
            }

        val jsonString =
            """
            {
                "total": 100,
                "offset": 0,
                "limit": 10,
                "data": []
            }
            """.trimIndent()

        val response = jsonConfigExplicitNulls.decodeFromString<FFetchResponse>(jsonString)
        assertEquals(100, response.total)
        assertEquals(0, response.offset)
        assertEquals(10, response.limit)
        assertEquals(0, response.data.size)
    }

    @Test
    fun `test deserialization with very large integers`() {
        val jsonString =
            """
            {
                "total": 2147483647,
                "offset": 2147483646,
                "limit": 2147483645,
                "data": []
            }
            """.trimIndent()

        val response = json.decodeFromString<FFetchResponse>(jsonString)
        assertEquals(Int.MAX_VALUE, response.total)
        assertEquals(Int.MAX_VALUE - 1, response.offset)
        assertEquals(Int.MAX_VALUE - 2, response.limit)
    }

    @Test
    fun `test partial JSON object deserialization`() {
        // This tests the decoder's ability to handle incomplete reads
        val jsonString =
            """
            {
                "total": 5,
                "offset": 0,
                "limit": 5,
                "data": [
                    {"id": 1, "name": "Item 1"},
                    {"id": 2, "name": "Item 2"},
                    {"id": 3, "name": "Item 3"},
                    {"id": 4, "name": "Item 4"},
                    {"id": 5, "name": "Item 5"}
                ]
            }
            """.trimIndent()

        val response = json.decodeFromString<FFetchResponse>(jsonString)
        assertEquals(5, response.total)
        assertEquals(5, response.data.size)
        assertEquals("Item 3", (response.data[2]["name"] as JsonPrimitive).content)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `test serializer with coercing inputs`() {
        val coercingJson =
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }

        // Test with enum-like strings that might be coerced
        val jsonString =
            """
            {
                "total": 10,
                "offset": 0,
                "limit": 10,
                "data": [
                    {"status": "ACTIVE", "priority": "HIGH"},
                    {"status": "inactive", "priority": "low"}
                ]
            }
            """.trimIndent()

        val response = coercingJson.decodeFromString<FFetchResponse>(jsonString)
        assertEquals(10, response.total)
        assertEquals(2, response.data.size)
    }

    @Test
    fun `test deserialization error recovery`() {
        // Test partial object that becomes valid
        val partialJson =
            """
            {"total": 1, "offset": 0, "limit": 1, "data": [{"test": "value"}]}
            """.trimIndent()

        val response = json.decodeFromString<FFetchResponse>(partialJson)
        assertEquals(1, response.total)
        assertEquals(1, response.data.size)
    }

    @Test
    fun `test serializer with whitespace variations`() {
        val jsonWithWhitespace =
            """
            {
                "total"     :     100     ,
                "offset"    :     0       ,
                "limit"     :     10      ,
                "data"      :     [       ]
            }
            """.trimIndent()

        val response = json.decodeFromString<FFetchResponse>(jsonWithWhitespace)
        assertEquals(100, response.total)
        assertEquals(0, response.offset)
        assertEquals(10, response.limit)
    }

    @Test
    fun `test zero values for all numeric fields`() {
        val zeroResponse =
            FFetchResponse(
                total = 0,
                offset = 0,
                limit = 0,
                data = emptyList(),
            )

        val serialized = json.encodeToString(FFetchResponse.serializer(), zeroResponse)
        val deserialized = json.decodeFromString<FFetchResponse>(serialized)

        assertEquals(0, deserialized.total)
        assertEquals(0, deserialized.offset)
        assertEquals(0, deserialized.limit)
        assertEquals(0, deserialized.data.size)
    }

    @Test
    fun `test serialization with JsonObject containing all primitive types`() {
        val allTypesData =
            buildJsonObject {
                put("string", "test")
                put("int", 42)
                put("long", 9223372036854775807L)
                put("float", 3.14f)
                put("double", 2.71828)
                put("boolean", true)
                put("null", kotlinx.serialization.json.JsonNull)
            }

        val response =
            FFetchResponse(
                total = 1,
                offset = 0,
                limit = 1,
                data = listOf(allTypesData),
            )

        val serialized = json.encodeToString(FFetchResponse.serializer(), response)
        val deserialized = json.decodeFromString<FFetchResponse>(serialized)

        assertEquals(1, deserialized.data.size)
        val entry = deserialized.data[0]
        assertEquals("test", (entry["string"] as JsonPrimitive).content)
        assertEquals("42", (entry["int"] as JsonPrimitive).content)
        assertEquals("9223372036854775807", (entry["long"] as JsonPrimitive).content)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `test empty data array variations`() {
        val variations =
            listOf(
                """{"total": 0, "offset": 0, "limit": 10, "data": []}""",
                """{"total": 0, "offset": 0, "limit": 10, "data": [ ]}""",
                """{"total": 0, "offset": 0, "limit": 10, "data": [
                ]}""",
            )

        variations.forEach { jsonString ->
            val response = json.decodeFromString<FFetchResponse>(jsonString)
            assertEquals(0, response.total)
            assertEquals(0, response.data.size)
        }
    }
}
