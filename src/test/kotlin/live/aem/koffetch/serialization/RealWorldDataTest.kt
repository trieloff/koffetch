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

import live.aem.koffetch.FFetchResponse
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealWorldDataTest {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
    }

    @Test
    fun `test with actual AEM response structures`() {
        val aemResponseJson = """
            {
                "total": 1250,
                "offset": 0,
                "limit": 100,
                "data": [
                    {
                        "path": "/content/site/articles/article-1",
                        "title": "Understanding Modern Web Architecture",
                        "description": "A comprehensive guide to building scalable web applications",
                        "author": "Jane Developer",
                        "publishDate": "2024-01-15T10:30:00.000Z",
                        "lastModified": "2024-01-20T14:45:00.000Z",
                        "tags": ["web", "architecture", "scalability"],
                        "categories": {
                            "primary": "Technology",
                            "secondary": ["Web Development", "Software Architecture"]
                        },
                        "metadata": {
                            "template": "article-template",
                            "language": "en",
                            "region": "global",
                            "seo": {
                                "metaTitle": "Modern Web Architecture Guide",
                                "metaDescription": "Learn how to build scalable web applications",
                                "keywords": ["web", "architecture", "scalability", "performance"]
                            }
                        },
                        "status": "published",
                        "featured": true,
                        "readTime": 8,
                        "viewCount": 15420
                    },
                    {
                        "path": "/content/site/articles/article-2",
                        "title": "Kotlin Coroutines Deep Dive",
                        "description": "Mastering asynchronous programming in Kotlin",
                        "author": "John Kotliner",
                        "publishDate": "2024-01-18T09:15:00.000Z",
                        "lastModified": "2024-01-18T09:15:00.000Z",
                        "tags": ["kotlin", "coroutines", "async"],
                        "categories": {
                            "primary": "Programming",
                            "secondary": ["Kotlin", "Concurrency"]
                        },
                        "metadata": {
                            "template": "article-template",
                            "language": "en",
                            "region": "global",
                            "relatedArticles": [
                                "/content/site/articles/kotlin-basics",
                                "/content/site/articles/async-patterns"
                            ]
                        },
                        "status": "published",
                        "featured": false,
                        "readTime": 12,
                        "viewCount": 8760
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<FFetchResponse>(aemResponseJson)
        assertEquals(1250, response.total)
        assertEquals(100, response.limit)
        assertEquals(2, response.data.size)

        val entries = response.toFFetchEntries()
        assertEquals(2, entries.size)

        val firstEntry = entries[0]
        assertEquals("/content/site/articles/article-1", firstEntry["path"])
        assertEquals("Understanding Modern Web Architecture", firstEntry["title"])
        assertEquals("Jane Developer", firstEntry["author"])
        assertEquals("published", firstEntry["status"])
        assertEquals("true", firstEntry["featured"])
        assertEquals("8", firstEntry["readTime"])
    }

    @Test
    fun `test with malformed but recoverable JSON`() {
        // Test with trailing commas and extra whitespace
        val malformedJson = """
            {
                "total": 50,
                "offset": 0,
                "limit": 25,
                "data": [
                    {
                        "title": "Article with trailing comma",
                        "author": "Test Author",
                    },
                    {
                        "title": "Article with extra fields",
                        "content": "Some content here",
                        "extraField": "should be ignored",
                    }
                ],
            }
        """.trimIndent()

        // With lenient parsing, this should work
        val response = json.decodeFromString<FFetchResponse>(malformedJson)
        assertEquals(50, response.total)
        assertEquals(2, response.data.size)

        val entries = response.toFFetchEntries()
        assertEquals("Article with trailing comma", entries[0]["title"])
        assertEquals("Test Author", entries[0]["author"])
    }

    @Test
    fun `test with very deeply nested objects`() {
        val deeplyNestedResponse = FFetchResponse(
            total = 1,
            offset = 0,
            limit = 1,
            data = listOf(
                buildJsonObject {
                    put("id", "deep-test")
                    putJsonObject("level1") {
                        putJsonObject("level2") {
                            putJsonObject("level3") {
                                putJsonObject("level4") {
                                    putJsonObject("level5") {
                                        put("deepValue", "found at level 5")
                                        putJsonArray("deepArray") {
                                            add(JsonPrimitive("item1"))
                                            add(JsonPrimitive("item2"))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            )
        )

        val entries = deeplyNestedResponse.toFFetchEntries()
        assertEquals(1, entries.size)
        assertEquals("deep-test", entries[0]["id"])
        
        // The nested object should be serialized as a string
        val level1String = entries[0]["level1"] as String
        assertTrue(level1String.contains("level2"))
        assertTrue(level1String.contains("deepValue"))
    }

    @Test
    fun `test with extremely large string values`() {
        val largeContent = "Lorem ipsum ".repeat(10000) // ~110KB string
        val response = FFetchResponse(
            total = 1,
            offset = 0,
            limit = 1,
            data = listOf(
                buildJsonObject {
                    put("id", "large-content-test")
                    put("title", "Article with Large Content")
                    put("content", largeContent)
                    put("summary", "This article has very large content")
                }
            )
        )

        val entries = response.toFFetchEntries()
        assertEquals(1, entries.size)
        
        val contentValue = entries[0]["content"] as String
        assertTrue(contentValue.length > 100000)
        assertTrue(contentValue.startsWith("Lorem ipsum"))
        assertEquals("Article with Large Content", entries[0]["title"])
    }

    @Test
    fun `test with various character encodings`() {
        val unicodeResponse = FFetchResponse(
            total = 4,
            offset = 0,
            limit = 4,
            data = listOf(
                buildJsonObject {
                    put("language", "english")
                    put("text", "Hello World!")
                    put("emoji", "👋🌍")
                },
                buildJsonObject {
                    put("language", "japanese")
                    put("text", "こんにちは世界")
                    put("emoji", "🇯🇵")
                },
                buildJsonObject {
                    put("language", "arabic")
                    put("text", "مرحبا بالعالم")
                    put("emoji", "🇸🇦")
                },
                buildJsonObject {
                    put("language", "chinese")
                    put("text", "你好世界")
                    put("emoji", "🇨🇳")
                }
            )
        )

        val entries = unicodeResponse.toFFetchEntries()
        assertEquals(4, entries.size)

        assertEquals("Hello World!", entries[0]["text"])
        assertEquals("👋🌍", entries[0]["emoji"])
        assertEquals("こんにちは世界", entries[1]["text"])
        assertEquals("🇯🇵", entries[1]["emoji"])
        assertEquals("مرحبا بالعالم", entries[2]["text"])
        assertEquals("🇸🇦", entries[2]["emoji"])
        assertEquals("你好世界", entries[3]["text"])
        assertEquals("🇨🇳", entries[3]["emoji"])
    }

    @Test
    fun `test with mixed data types in arrays`() {
        val mixedArrayJson = """
            {
                "total": 1,
                "offset": 0,
                "limit": 1,
                "data": [
                    {
                        "mixedArray": [
                            "string value",
                            42,
                            true,
                            null,
                            {"nested": "object"},
                            ["nested", "array"]
                        ],
                        "tags": ["tag1", "tag2", "tag3"],
                        "metadata": {
                            "versions": [1, 2, 3],
                            "features": ["feature1", "feature2"],
                            "config": {
                                "enabled": true,
                                "options": ["opt1", "opt2"]
                            }
                        }
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<FFetchResponse>(mixedArrayJson)
        val entries = response.toFFetchEntries()
        
        assertEquals(1, entries.size)
        val entry = entries[0]
        
        // Arrays and objects should be converted to strings
        assertTrue(entry["mixedArray"] is String)
        assertTrue(entry["tags"] is String)
        assertTrue(entry["metadata"] is String)
        
        val mixedArrayString = entry["mixedArray"] as String
        assertTrue(mixedArrayString.contains("string value"))
        assertTrue(mixedArrayString.contains("42"))
        assertTrue(mixedArrayString.contains("true"))
    }

    @Test
    fun `test error handling with completely invalid JSON`() {
        val invalidJson = """
            {
                "total": "this should be a number"
                "offset": missing comma
                "invalid": json
            }
        """.trimIndent()

        assertThrows<kotlinx.serialization.SerializationException> {
            json.decodeFromString<FFetchResponse>(invalidJson)
        }
    }

    @Test
    fun `test performance with realistic AEM dataset`() {
        // Create a realistic dataset similar to what AEM might return
        val realisticDataEntries = (1..500).map { index ->
            buildJsonObject {
                put("path", "/content/site/articles/article-$index")
                put("title", "Article $index: ${generateTitle(index)}")
                put("description", "This is the description for article number $index")
                put("author", "Author ${index % 20}")
                put("publishDate", "2024-01-${(index % 28) + 1}T${(index % 24).toString().padStart(2, '0')}:00:00.000Z")
                put("lastModified", "2024-01-${(index % 28) + 1}T${(index % 24).toString().padStart(2, '0')}:30:00.000Z")
                putJsonArray("tags") {
                    add(JsonPrimitive("tag${index % 10}"))
                    add(JsonPrimitive("category${index % 5}"))
                    if (index % 3 == 0) add(JsonPrimitive("featured"))
                }
                putJsonObject("metadata") {
                    put("template", "article-template")
                    put("language", if (index % 4 == 0) "en" else "es")
                    put("region", if (index % 2 == 0) "global" else "regional")
                }
                put("status", if (index % 10 == 0) "draft" else "published")
                put("featured", index % 7 == 0)
                put("readTime", (index % 15) + 3)
                put("viewCount", index * 47)
            }
        }

        val response = FFetchResponse(
            total = 500,
            offset = 0,
            limit = 500,
            data = realisticDataEntries
        )

        val startTime = System.currentTimeMillis()
        val entries = response.toFFetchEntries()
        val endTime = System.currentTimeMillis()

        assertEquals(500, entries.size)
        assertTrue((endTime - startTime) < 3000, "Processing should complete within 3 seconds")

        // Verify data integrity
        val firstEntry = entries[0]
        assertEquals("/content/site/articles/article-1", firstEntry["path"])
        assertTrue((firstEntry["title"] as String).contains("Article 1"))
        assertEquals("Author 1", firstEntry["author"])

        val lastEntry = entries[499]
        assertEquals("/content/site/articles/article-500", lastEntry["path"])
        assertEquals("Author 0", lastEntry["author"]) // 500 % 20 = 0
    }

    @Test
    fun `test with real-world edge cases from AEM`() {
        val edgeCaseJson = """
            {
                "total": 3,
                "offset": 0,
                "limit": 3,
                "data": [
                    {
                        "path": "/content/site/articles/special-chars",
                        "title": "Article with \"quotes\" and 'apostrophes' & symbols",
                        "content": "Content with \n newlines \t tabs \\ backslashes",
                        "url": "https://example.com/path?param=value&other=test"
                    },
                    {
                        "path": "/content/site/articles/empty-fields",
                        "title": "",
                        "description": null,
                        "tags": [],
                        "metadata": {}
                    },
                    {
                        "path": "/content/site/articles/numeric-strings",
                        "id": "12345",
                        "version": "1.0.0",
                        "priority": "0001",
                        "percentage": "99.99%",
                        "date": "2024-01-15"
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<FFetchResponse>(edgeCaseJson)
        val entries = response.toFFetchEntries()

        assertEquals(3, entries.size)

        // Test special characters
        val specialCharsEntry = entries[0]
        assertTrue((specialCharsEntry["title"] as String).contains("\"quotes\""))
        assertTrue((specialCharsEntry["content"] as String).contains("\n"))
        assertTrue((specialCharsEntry["url"] as String).contains("?param=value"))

        // Test empty/null fields
        val emptyFieldsEntry = entries[1]
        assertEquals("", emptyFieldsEntry["title"])
        assertEquals("null", emptyFieldsEntry["description"])

        // Test numeric strings
        val numericEntry = entries[2]
        assertEquals("12345", numericEntry["id"])
        assertEquals("1.0.0", numericEntry["version"])
        assertEquals("0001", numericEntry["priority"])
        assertEquals("99.99%", numericEntry["percentage"])
    }

    private fun generateTitle(index: Int): String {
        val topics = listOf(
            "Web Development", "Mobile Apps", "Data Science", "Machine Learning",
            "Cloud Computing", "Cybersecurity", "DevOps", "AI Ethics", "Blockchain",
            "Software Architecture"
        )
        return topics[index % topics.size]
    }
}