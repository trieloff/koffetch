# KotlinFFetch

[![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-orange?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Gradle](https://img.shields.io/badge/Gradle-8.0%2B-brightgreen?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org)

KotlinFFetch is a Kotlin port of SwiftFFetch, a library for fetching and processing content from AEM (.live) Content APIs and similar JSON-based endpoints. It is designed for composable applications, making it easy to retrieve, paginate, and process content in a Kotlin-native way.

## Features

- **Kotlin-native API**: Designed for idiomatic use in Kotlin projects.
- **Coroutines Support**: Uses Kotlin coroutines for efficient, modern async code.
- **Pagination**: Handles paginated endpoints seamlessly.
- **Composable**: Chainable methods for mapping, filtering, and transforming content.
- **HTTP Caching**: Intelligent caching with configurable cache policies.
- **Sheet Selection**: Access specific sheets in multi-sheet JSON resources.
- **Extensible**: Easily integrate with your own models and workflows.

## Installation

Add KotlinFFetch to your `build.gradle.kts` dependencies:

```kotlin
dependencies {
    implementation("com.terragon.kotlinffetch:kotlin-ffetch:1.0.0")
}
```

## Usage

### Fetch Entries from an Index

```kotlin
import com.terragon.kotlinffetch.*
import kotlinx.coroutines.flow.collect

val entries = ffetch("https://example.com/query-index.json")
entries.asFlow().collect { entry ->
    println(entry["title"] as? String ?: "")
}
```

### Get the First Entry

```kotlin
val firstEntry = ffetch("https://example.com/query-index.json").first()
println(firstEntry?.get("title") as? String ?: "")
```

### Get All Entries as a List

```kotlin
val allEntries = ffetch("https://example.com/query-index.json").all()
println("Total entries: ${allEntries.size}")
```

## HTTP Caching

KotlinFFetch includes comprehensive HTTP caching support with configurable cache policies.

### Default Caching Behavior

By default, KotlinFFetch uses the default cache policy and respects HTTP cache control headers:

```kotlin
// First request fetches from server
val entries1 = ffetch("https://example.com/api/data.json").all()

// Second request uses cache if server sent appropriate cache headers
val entries2 = ffetch("https://example.com/api/data.json").all()
```

### Cache Configuration

Use the `.cache()` method to configure caching behavior:

```kotlin
// Always fetch fresh data (bypass cache)
val freshData = ffetch("https://example.com/api/data.json")
    .cache(FFetchCacheConfig.NoCache)
    .all()

// Only use cached data (won't make network request)
val cachedData = ffetch("https://example.com/api/data.json")
    .cache(FFetchCacheConfig.CacheOnly)
    .all()

// Use cache if available, otherwise load from network
val data = ffetch("https://example.com/api/data.json")
    .cache(FFetchCacheConfig.CacheElseLoad)
    .all()
```

### Custom Cache Configuration

Create your own cache with specific settings:

```kotlin
val customConfig = FFetchCacheConfig(
    maxAge = 3600  // Cache for 1 hour regardless of server headers
)

val data = ffetch("https://example.com/api/data.json")
    .cache(customConfig)
    .all()
```

### Backward Compatibility

Legacy cache methods are still supported:

```kotlin
// Legacy method - maps to .cache(FFetchCacheConfig.NoCache)
val freshData = ffetch("https://example.com/api/data.json")
    .reloadCache()
    .all()

// Legacy method with parameter
val data = ffetch("https://example.com/api/data.json")
    .withCacheReload(false)  // Uses default cache behavior
    .all()
```

## Advanced Usage

```kotlin
val allEntries = ffetch("https://example.com/query-index.json").all()
allEntries.forEach { entry ->
    println(entry)
}
```

### Map and Filter Entries

```kotlin
ffetch("https://example.com/query-index.json")
    .map<String?> { it["title"] as? String }
    .filter { it?.contains("Kotlin") == true }
    .collect { title ->
        println(title ?: "")
    }
```

### Control Pagination with `chunks` and `limit`

```kotlin
ffetch("https://example.com/query-index.json")
    .chunks(100)
    .limit(5)
    .asFlow()
    .collect { entry ->
        println(entry)
    }
```

### Access a Specific Sheet

```kotlin
ffetch("https://example.com/query-index.json")
    .sheet("products")
    .asFlow()
    .collect { entry ->
        println(entry["sku"] as? String ?: "")
    }
```

### Document Following with Security

KotlinFFetch provides a `follow()` method to fetch HTML documents referenced in your data. For security, document following is restricted to the same hostname as your initial request by default.

```kotlin
// Basic document following (same hostname only)
val entriesWithDocs = ffetch("https://example.com/query-index.json")
    .follow("path", "document")  // follows URLs in 'path' field
    .all()

// The 'document' field will contain parsed HTML Document objects
for (entry in entriesWithDocs) {
    if (entry["document"] is Document) {
        val doc = entry["document"] as Document
        println(doc.title())
    }
}
```

#### Allowing Additional Hostnames

To allow document following to other hostnames, use the `allow()` method:

```kotlin
// Allow specific hostname
val entries = ffetch("https://example.com/query-index.json")
    .allow("trusted.com")
    .follow("path", "document")
    .all()

// Allow multiple hostnames
val entries = ffetch("https://example.com/query-index.json")
    .allow(listOf("trusted.com", "api.example.com"))
    .follow("path", "document")
    .all()

// Allow all hostnames (use with caution)
val entries = ffetch("https://example.com/query-index.json")
    .allow("*")
    .follow("path", "document")
    .all()
```

#### Security Considerations

The hostname restriction is an important security feature that prevents:
- **Cross-site request forgery (CSRF)**: Malicious sites cannot trick your app into fetching arbitrary content
- **Data exfiltration**: Prevents accidental requests to untrusted domains
- **Server-side request forgery (SSRF)**: Reduces risk of unintended internal network access

By default, only the hostname of your initial JSON request is allowed. This mirrors the security model used by web browsers for cross-origin requests.

## About Query Index Files

The `query-index.json` files used in the examples above are typically generated by AEM Live sites as part of their content indexing process. For more information about how these index files are created and structured, see the [AEM Live Indexing Documentation](https://www.aem.live/developer/indexing).

## Example

See `src/main/kotlin/com/terragon/kotlinffetch/examples/Examples.kt` in the repository for more detailed usage examples.

## License

This project is licensed under the terms of the Apache License 2.0. See [LICENSE](LICENSE) for details.

## Development Setup

### Quick Commands

Use Gradle for common development tasks:

```bash
# Run all tests
./gradlew test

# Build the project
./gradlew build

# Clean build artifacts
./gradlew clean

# Publish to local repository
./gradlew publishToMavenLocal
```

## Migration from SwiftFFetch

KotlinFFetch maintains API compatibility with SwiftFFetch where possible:

### Swift to Kotlin API Mapping

| Swift | Kotlin |
|-------|--------|
| `FFetch(url: "...")` | `FFetch("...")` |
| `for await entry in ffetch` | `ffetch.asFlow().collect { entry -> }` |
| `.all()` | `.all()` |
| `.first()` | `.first()` |
| `.map { }` | `.map<Type> { }` |
| `.filter { }` | `.filter { }` |
| `.chunks(100)` | `.chunks(100)` |
| `.sheet("name")` | `.sheet("name")` |
| `.follow("path", as: "doc")` | `.follow("path", "doc")` |
| `.allow("host.com")` | `.allow("host.com")` |
| `.cache(.noCache)` | `.cache(FFetchCacheConfig.NoCache)` |

### Key Differences

1. **Async Model**: Uses Kotlin Coroutines `Flow` instead of Swift's `AsyncSequence`
2. **Type System**: Explicit generic types required for `map()` operations
3. **HTTP Client**: Uses Ktor instead of URLSession
4. **HTML Parser**: Uses Jsoup instead of SwiftSoup
5. **Error Handling**: Uses sealed classes instead of Swift enums