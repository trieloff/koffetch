//
// FFetchResponseTest.kt
// KotlinFFetch Tests
//
// Tests for FFetchResponse parsing and conversion
//

package live.aem.koffetch

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*

class FFetchResponseTest {

    @Test
    fun testBasicFFetchResponseDeserialization() {
        // Setup
        val jsonString = """
        {
            "total": 100,
            "offset": 0,
            "limit": 25,
            "data": [
                {
                    "path": "/content/page1",
                    "title": "Page 1",
                    "lastModified": 1640995200
                },
                {
                    "path": "/content/page2", 
                    "title": "Page 2",
                    "lastModified": 1640995300
                }
            ]
        }
        """.trimIndent()

        // Execute
        val response = Json.decodeFromString(FFetchResponse.serializer(), jsonString)

        // Verify
        assertEquals(100, response.total)
        assertEquals(0, response.offset)
        assertEquals(25, response.limit)
        assertEquals(2, response.data.size)
        
        val firstItem = response.data[0]
        assertEquals("/content/page1", firstItem["path"]?.toString()?.removeSurrounding("\""))
        assertEquals("Page 1", firstItem["title"]?.toString()?.removeSurrounding("\""))
    }

    @Test
    fun testFFetchResponseWithEmptyDataArray() {
        // Setup
        val jsonString = """
        {
            "total": 0,
            "offset": 0,
            "limit": 25,
            "data": []
        }
        """.trimIndent()

        // Execute
        val response = Json.decodeFromString(FFetchResponse.serializer(), jsonString)

        // Verify
        assertEquals(0, response.total)
        assertEquals(0, response.data.size)
    }

    @Test
    fun testFFetchResponseWithMixedDataTypes() {
        // Setup
        val jsonString = """
        {
            "total": 1,
            "offset": 0,
            "limit": 25,
            "data": [
                {
                    "path": "/content/product",
                    "title": "Product Name",
                    "price": 29.99,
                    "inStock": true,
                    "quantity": 50,
                    "tags": "electronics,gadgets",
                    "description": null
                }
            ]
        }
        """.trimIndent()

        // Execute
        val response = Json.decodeFromString(FFetchResponse.serializer(), jsonString)

        // Verify
        assertEquals(1, response.total)
        assertEquals(1, response.data.size)
        
        val item = response.data[0]
        assertTrue(item.containsKey("path"))
        assertTrue(item.containsKey("price"))
        assertTrue(item.containsKey("inStock"))
        assertTrue(item.containsKey("quantity"))
        assertTrue(item.containsKey("description"))
    }

    @Test
    fun testToFFetchEntriesConversion() {
        // Setup
        val jsonObject1 = JsonObject(mapOf(
            "path" to JsonPrimitive("/content/page1"),
            "title" to JsonPrimitive("Page 1"),
            "lastModified" to JsonPrimitive(1640995200)
        ))
        
        val jsonObject2 = JsonObject(mapOf(
            "path" to JsonPrimitive("/content/page2"),
            "title" to JsonPrimitive("Page 2"),
            "lastModified" to JsonPrimitive(1640995300)
        ))

        val response = FFetchResponse(
            total = 2,
            offset = 0,
            limit = 25,
            data = listOf(jsonObject1, jsonObject2)
        )

        // Execute
        val entries = response.toFFetchEntries()

        // Verify
        assertEquals(2, entries.size)
        
        val entry1 = entries[0]
        assertEquals("/content/page1", entry1["path"])
        assertEquals("Page 1", entry1["title"])
        assertEquals("1640995200", entry1["lastModified"]) // Numbers are converted to strings
        
        val entry2 = entries[1]
        assertEquals("/content/page2", entry2["path"])
        assertEquals("Page 2", entry2["title"])
        assertEquals("1640995300", entry2["lastModified"])
    }

    @Test
    fun testToFFetchEntriesWithComplexValues() {
        // Setup - testing how complex JSON values are handled
        val jsonObject = JsonObject(mapOf(
            "simpleString" to JsonPrimitive("test"),
            "quotedString" to JsonPrimitive("quoted value"),
            "number" to JsonPrimitive(42),
            "boolean" to JsonPrimitive(true),
            "nullValue" to JsonPrimitive(null as String?)
        ))

        val response = FFetchResponse(
            total = 1,
            offset = 0,
            limit = 25,
            data = listOf(jsonObject)
        )

        // Execute
        val entries = response.toFFetchEntries()

        // Verify
        assertEquals(1, entries.size)
        val entry = entries[0]
        
        assertEquals("test", entry["simpleString"])
        assertEquals("quoted value", entry["quotedString"]) // Quotes should be removed
        assertEquals("42", entry["number"]) // Number converted to string
        assertEquals("true", entry["boolean"]) // Boolean converted to string
        assertEquals("null", entry["nullValue"]) // null converted to string "null"
    }

    @Test
    fun testRealAEMResponseExample() {
        // Setup - Based on real AEM .live API response structure
        val aemResponse = """
        {
            "total": 247,
            "offset": 0,
            "limit": 255,
            "data": [
                {
                    "path": "/blog/2024/01/15/new-product-launch",
                    "title": "Exciting New Product Launch",
                    "author": "John Doe",
                    "publishedDate": "2024-01-15T10:30:00Z",
                    "tags": "product,launch,news",
                    "excerpt": "We are thrilled to announce our latest product...",
                    "imageUrl": "/content/dam/images/product-hero.jpg",
                    "readTime": 5,
                    "featured": true,
                    "lastModified": 1705312200,
                    "template": "blog-post"
                },
                {
                    "path": "/blog/2024/01/10/industry-insights",
                    "title": "Key Industry Insights for 2024",
                    "author": "Jane Smith", 
                    "publishedDate": "2024-01-10T14:45:00Z",
                    "tags": "industry,insights,trends",
                    "excerpt": "Looking ahead to 2024, several key trends...",
                    "imageUrl": "/content/dam/images/insights-hero.jpg",
                    "readTime": 8,
                    "featured": false,
                    "lastModified": 1704895500,
                    "template": "blog-post"
                }
            ]
        }
        """.trimIndent()

        // Execute
        val response = Json.decodeFromString(FFetchResponse.serializer(), aemResponse)
        val entries = response.toFFetchEntries()

        // Verify structure
        assertEquals(247, response.total)
        assertEquals(0, response.offset)
        assertEquals(255, response.limit)
        assertEquals(2, entries.size)

        // Verify first blog post
        val firstPost = entries[0]
        assertEquals("/blog/2024/01/15/new-product-launch", firstPost["path"])
        assertEquals("Exciting New Product Launch", firstPost["title"])
        assertEquals("John Doe", firstPost["author"])
        assertEquals("2024-01-15T10:30:00Z", firstPost["publishedDate"])
        assertEquals("product,launch,news", firstPost["tags"])
        assertEquals("5", firstPost["readTime"])
        assertEquals("true", firstPost["featured"])
        assertEquals("blog-post", firstPost["template"])

        // Verify second blog post
        val secondPost = entries[1]
        assertEquals("/blog/2024/01/10/industry-insights", secondPost["path"])
        assertEquals("Key Industry Insights for 2024", secondPost["title"])
        assertEquals("Jane Smith", secondPost["author"])
        assertEquals("false", secondPost["featured"])
        assertEquals("8", secondPost["readTime"])
    }

    @Test
    fun testMultiSheetResponseStructure() {
        // Setup - Testing response from multi-sheet AEM content
        val multiSheetResponse = """
        {
            "total": 15,
            "offset": 0,
            "limit": 255,
            "data": [
                {
                    "path": "/products/electronics/smartphone-x1",
                    "name": "Smartphone X1",
                    "category": "Electronics",
                    "price": 699.99,
                    "sku": "PHONE-X1-001",
                    "inStock": true,
                    "description": "Latest smartphone with advanced features"
                },
                {
                    "path": "/products/clothing/premium-jacket",
                    "name": "Premium Winter Jacket",
                    "category": "Clothing",
                    "price": 199.99,
                    "sku": "JACKET-PREM-002",
                    "inStock": false,
                    "description": "High-quality winter jacket for extreme weather"
                }
            ]
        }
        """.trimIndent()

        // Execute
        val response = Json.decodeFromString(FFetchResponse.serializer(), multiSheetResponse)
        val entries = response.toFFetchEntries()

        // Verify
        assertEquals(15, response.total)
        assertEquals(2, entries.size)

        val smartphone = entries[0]
        assertEquals("/products/electronics/smartphone-x1", smartphone["path"])
        assertEquals("699.99", smartphone["price"])
        assertEquals("true", smartphone["inStock"])

        val jacket = entries[1]
        assertEquals("/products/clothing/premium-jacket", jacket["path"])
        assertEquals("199.99", jacket["price"])
        assertEquals("false", jacket["inStock"])
    }

    @Test
    fun testMalformedJsonHandling() {
        // Test that malformed JSON throws appropriate error
        val malformedJson = """
        {
            "total": 1,
            "offset": 0,
            "limit": 25,
            "data": [
                {
                    "path": "/test"
                    "title": "Missing comma"
                }
            ]
        }
        """.trimIndent()

        assertFailsWith<Exception> {
            Json.decodeFromString(FFetchResponse.serializer(), malformedJson)
        }
    }

    @Test
    fun testMissingRequiredFields() {
        // Test response missing required fields
        val incompleteJson = """
        {
            "total": 1,
            "offset": 0
        }
        """.trimIndent()

        assertFailsWith<Exception> {
            Json.decodeFromString(FFetchResponse.serializer(), incompleteJson)
        }
    }

    @Test
    fun testInvalidDataTypes() {
        // Test response with invalid data types
        val invalidTypesJson = """
        {
            "total": "not_a_number",
            "offset": 0,
            "limit": 25,
            "data": []
        }
        """.trimIndent()

        assertFailsWith<Exception> {
            Json.decodeFromString(FFetchResponse.serializer(), invalidTypesJson)
        }
    }

    @Test
    fun testEmptyResponse() {
        // Test completely empty response
        val emptyJson = "{}"

        assertFailsWith<Exception> {
            Json.decodeFromString(FFetchResponse.serializer(), emptyJson)
        }
    }

    @Test
    fun testResponseWithSpecialCharacters() {
        // Setup - Test handling of special characters and unicode
        val specialCharsResponse = """
        {
            "total": 1,
            "offset": 0,
            "limit": 25,
            "data": [
                {
                    "path": "/content/special-chars",
                    "title": "Special Characters: àáâãäåæçèéêë",
                    "description": "Unicode test: 你好世界 🌍 ñáéíóú",
                    "tags": "test,unicode,special-chars",
                    "url": "https://example.com/path?param=value&other=test"
                }
            ]
        }
        """.trimIndent()

        // Execute
        val response = Json.decodeFromString(FFetchResponse.serializer(), specialCharsResponse)
        val entries = response.toFFetchEntries()

        // Verify
        assertEquals(1, entries.size)
        val entry = entries[0]
        
        assertEquals("Special Characters: àáâãäåæçèéêë", entry["title"])
        assertEquals("Unicode test: 你好世界 🌍 ñáéíóú", entry["description"])
        assertEquals("https://example.com/path?param=value&other=test", entry["url"])
    }

    @Test
    fun testLargeNumberHandling() {
        // Setup - Test handling of large numbers and timestamps
        val largeNumberResponse = """
        {
            "total": 1,
            "offset": 0,
            "limit": 25,
            "data": [
                {
                    "id": 9223372036854775807,
                    "timestamp": 1705312200000,
                    "bigDecimal": 99999999.99,
                    "scientificNotation": 1.23e10
                }
            ]
        }
        """.trimIndent()

        // Execute
        val response = Json.decodeFromString(FFetchResponse.serializer(), largeNumberResponse)
        val entries = response.toFFetchEntries()

        // Verify
        assertEquals(1, entries.size)
        val entry = entries[0]
        
        // All numbers should be converted to strings
        assertEquals("9223372036854775807", entry["id"])
        assertEquals("1705312200000", entry["timestamp"])
        assertEquals("99999999.99", entry["bigDecimal"])
        assertEquals("1.23e10", entry["scientificNotation"])
    }
}