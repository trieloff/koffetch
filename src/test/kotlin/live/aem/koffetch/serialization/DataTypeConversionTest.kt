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

import live.aem.koffetch.FFetchEntry
import live.aem.koffetch.FFetchResponse
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataTypeConversionTest {

    @Test
    fun `test FFetchEntry Map operations`() {
        val entry: FFetchEntry = mapOf(
            "title" to "Sample Article",
            "author" to "John Doe",
            "publishDate" to "2024-01-15",
            "views" to "1500",
            "rating" to "4.5",
            "published" to "true",
            "tags" to null
        )

        assertEquals("Sample Article", entry["title"])
        assertEquals("John Doe", entry["author"])
        assertEquals("1500", entry["views"])
        assertEquals("4.5", entry["rating"])
        assertEquals("true", entry["published"])
        assertNull(entry["tags"])
        assertNull(entry["nonexistent"])
        
        assertTrue(entry.containsKey("title"))
        assertTrue(entry.containsKey("tags"))
        assertEquals(7, entry.size)
    }

    @Test
    fun `test type casting in user code scenarios`() {
        val entry: FFetchEntry = mapOf(
            "numericString" to "123",
            "booleanString" to "true",
            "floatString" to "3.14",
            "emptyString" to "",
            "whitespaceString" to "   "
        )

        // Safe type casting scenarios
        val numericValue = entry["numericString"]?.toString()?.toIntOrNull()
        assertEquals(123, numericValue)

        val booleanValue = entry["booleanString"]?.toString()?.toBooleanStrictOrNull()
        assertEquals(true, booleanValue)

        val floatValue = entry["floatString"]?.toString()?.toDoubleOrNull()
        assertEquals(3.14, floatValue)

        val emptyValue = entry["emptyString"]?.toString()
        assertEquals("", emptyValue)

        val trimmedValue = entry["whitespaceString"]?.toString()?.trim()
        assertEquals("", trimmedValue)
    }

    @Test
    fun `test null safety and optional value handling`() {
        val entry: FFetchEntry = mapOf(
            "validValue" to "content",
            "nullValue" to null,
            "emptyValue" to ""
        )

        // Safe access patterns
        val validContent = entry["validValue"]?.takeIf { it.toString().isNotBlank() }
        assertEquals("content", validContent)

        val nullContent = entry["nullValue"]?.takeIf { it.toString().isNotBlank() }
        assertNull(nullContent)

        val emptyContent = entry["emptyValue"]?.takeIf { it.toString().isNotBlank() }
        assertNull(emptyContent)

        val missingContent = entry["missing"]?.takeIf { it.toString().isNotBlank() }
        assertNull(missingContent)

        // Elvis operator usage
        val fallbackValue = entry["missing"]?.toString() ?: "default"
        assertEquals("default", fallbackValue)
    }

    @Test
    fun `test collection operations on mixed-type data`() {
        val entries: List<FFetchEntry> = listOf(
            mapOf("category" to "tech", "priority" to "1", "active" to "true"),
            mapOf("category" to "science", "priority" to "2", "active" to "false"),
            mapOf("category" to "tech", "priority" to "3", "active" to "true"),
            mapOf("category" to "business", "priority" to null, "active" to "true")
        )

        // Filter operations
        val techEntries = entries.filter { it["category"] == "tech" }
        assertEquals(2, techEntries.size)

        val activeEntries = entries.filter { it["active"] == "true" }
        assertEquals(3, activeEntries.size)

        val validPriorityEntries = entries.filter { 
            it["priority"]?.toString()?.toIntOrNull() != null 
        }
        assertEquals(3, validPriorityEntries.size)

        // Map operations
        val categories = entries.mapNotNull { it["category"]?.toString() }.distinct()
        assertEquals(setOf("tech", "science", "business"), categories.toSet())

        val priorities = entries.mapNotNull { 
            it["priority"]?.toString()?.toIntOrNull() 
        }.sorted()
        assertEquals(listOf(1, 2, 3), priorities)
    }

    @Test
    fun `test data integrity across transformations`() {
        val originalResponse = FFetchResponse(
            total = 3,
            offset = 0,
            limit = 3,
            data = listOf(
                buildJsonObject {
                    put("id", "1")
                    put("title", "Article 1")
                    put("metadata", "{'version': '1.0'}")
                },
                buildJsonObject {
                    put("id", "2")
                    put("title", "Article 2")
                    put("metadata", "{'version': '1.1'}")
                },
                buildJsonObject {
                    put("id", "3")
                    put("title", "Article 3")
                    put("metadata", null)
                }
            )
        )

        val entries = originalResponse.toFFetchEntries()
        
        // Verify data integrity
        assertEquals(3, entries.size)
        assertEquals("1", entries[0]["id"])
        assertEquals("Article 1", entries[0]["title"])
        assertEquals("{'version': '1.0'}", entries[0]["metadata"])
        assertEquals("null", entries[2]["metadata"])
        
        // Transform and verify integrity maintained
        val transformedEntries = entries.map { entry ->
            entry.toMutableMap().apply {
                put("processed", "true")
                put("timestamp", System.currentTimeMillis().toString())
            }
        }
        
        assertEquals(3, transformedEntries.size)
        transformedEntries.forEach { entry ->
            assertEquals("true", entry["processed"])
            assertNotNull(entry["timestamp"])
            assertTrue(entry.size >= 4) // original fields + 2 new ones
        }
    }

    @Test
    fun `test JsonObject to Map conversion edge cases`() {
        val response = FFetchResponse(
            total = 1,
            offset = 0,
            limit = 1,
            data = listOf(
                buildJsonObject {
                    put("simpleString", "value")
                    put("quotedString", "\"quoted value\"")
                    put("escapedString", "value with \"quotes\" and \\backslashes\\")
                    put("numberAsString", "123")
                    put("booleanAsString", "true")
                    put("nullValue", null)
                    put("emptyString", "")
                    put("unicodeString", "Hello 🌍 World")
                }
            )
        )

        val entry = response.toFFetchEntries().first()
        
        assertEquals("value", entry["simpleString"])
        assertEquals("quoted value", entry["quotedString"])
        assertEquals("value with \"quotes\" and \\backslashes\\", entry["escapedString"])
        assertEquals("123", entry["numberAsString"])
        assertEquals("true", entry["booleanAsString"])
        assertEquals("null", entry["nullValue"])
        assertEquals("", entry["emptyString"])
        assertEquals("Hello 🌍 World", entry["unicodeString"])
    }

    @Test
    fun `test complex nested data handling`() {
        val response = FFetchResponse(
            total = 1,
            offset = 0,
            limit = 1,
            data = listOf(
                buildJsonObject {
                    put("metadata", "{\"author\": \"John\", \"tags\": [\"kotlin\", \"test\"]}")
                    put("config", "[{\"key\": \"value\"}, {\"number\": 42}]")
                }
            )
        )

        val entry = response.toFFetchEntries().first()
        
        val metadataString = entry["metadata"] as String
        assertTrue(metadataString.contains("John"))
        assertTrue(metadataString.contains("kotlin"))
        
        val configString = entry["config"] as String
        assertTrue(configString.contains("value"))
        assertTrue(configString.contains("42"))
    }

    @Test
    fun `test performance with large datasets`() {
        val largeResponse = FFetchResponse(
            total = 10000,
            offset = 0,
            limit = 10000,
            data = (1..10000).map { index ->
                buildJsonObject {
                    put("id", index.toString())
                    put("title", "Article $index")
                    put("content", "Content for article number $index with some additional text to make it realistic")
                    put("author", "Author${index % 100}")
                    put("category", "Category${index % 10}")
                }
            }
        )

        val startTime = System.currentTimeMillis()
        val entries = largeResponse.toFFetchEntries()
        val endTime = System.currentTimeMillis()
        
        assertEquals(10000, entries.size)
        assertTrue((endTime - startTime) < 5000, "Conversion should complete within 5 seconds")
        
        // Verify random entries for correctness
        assertEquals("5000", entries[4999]["id"])
        assertEquals("Article 5000", entries[4999]["title"])
        assertEquals("Author0", entries[4999]["author"])
    }

    @Test
    fun `test type coercion edge cases`() {
        val entries: List<FFetchEntry> = listOf(
            mapOf(
                "stringNumber" to "123",
                "stringFloat" to "45.67",
                "stringBoolean" to "true",
                "stringNull" to "null",
                "actualNull" to null,
                "leadingZeros" to "00123",
                "scientificNotation" to "1.23e-4"
            )
        )

        val entry = entries.first()
        
        // Test various coercion scenarios
        assertEquals(123, entry["stringNumber"]?.toString()?.toIntOrNull())
        assertEquals(45.67, entry["stringFloat"]?.toString()?.toDoubleOrNull())
        assertEquals(true, entry["stringBoolean"]?.toString()?.toBooleanStrictOrNull())
        assertEquals("null", entry["stringNull"]?.toString())
        assertNull(entry["actualNull"])
        assertEquals(123, entry["leadingZeros"]?.toString()?.toIntOrNull())
        assertEquals(0.000123, entry["scientificNotation"]?.toString()?.toDoubleOrNull() ?: 0.0, 0.0000001)
    }

    @Test
    fun `test immutability and defensive copying`() {
        val originalEntry: FFetchEntry = mapOf(
            "title" to "Original Title",
            "count" to "100"
        )

        // Create a modified copy
        val modifiedEntry = originalEntry.toMutableMap().apply {
            put("title", "Modified Title")
            put("count", "200")
            put("newField", "new value")
        }

        // Verify original is unchanged
        assertEquals("Original Title", originalEntry["title"])
        assertEquals("100", originalEntry["count"])
        assertNull(originalEntry["newField"])

        // Verify modified copy has changes
        assertEquals("Modified Title", modifiedEntry["title"])
        assertEquals("200", modifiedEntry["count"])
        assertEquals("new value", modifiedEntry["newField"])
    }
}