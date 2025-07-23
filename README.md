# KotlinFFetch

[![79% Vibe_Coded](https://img.shields.io/badge/79%25-Vibe_Coded-ff69b4?style=for-the-badge&logo=zedindustries&logoColor=white)](https://github.com/trieloff/vibe-coded-badge-action)

[![codecov](https://img.shields.io/codecov/c/github/terragon/kotlin-ffetch?style=for-the-badge&logo=codecov&logoColor=white)](https://codecov.io/gh/terragon/kotlin-ffetch)
[![Build Status](https://img.shields.io/github/actions/workflow/status/terragon/kotlin-ffetch/test.yaml?style=for-the-badge&logo=github)](https://github.com/terragon/kotlin-ffetch/actions)
[![Kotlin 1.9+](https://img.shields.io/badge/Kotlin-1.9%2B-orange?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Platform-JVM-lightgrey?style=for-the-badge)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge)](LICENSE)
[![Gradle](https://img.shields.io/badge/Gradle-Compatible-brightgreen?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org)

KotlinFFetch is a Kotlin library for fetching and processing content from AEM (.live) Content APIs and similar JSON-based endpoints. It is designed for composable applications, making it easy to retrieve, paginate, and process content in a Kotlin-native way.

## Features

- **Kotlin-native API**: Designed for idiomatic use in Kotlin projects.
- **Coroutines Support**: Uses Kotlin coroutines for efficient, modern code.
- **Pagination**: Handles paginated endpoints seamlessly.
- **Composable**: Chainable methods for mapping, filtering, and transforming content.
- **HTTP Caching**: Intelligent caching with respect for HTTP cache control headers.
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
import com.terragon.kotlinffetch.FFetch

val entries = FFetch("https://example.com/query-index.json")
for (entry in entries) {
    println(entry["title"] as? String ?: "")
}
```

### Get the First Entry

```kotlin
val firstEntry = FFetch("https://example.com/query-index.json").first()
println(firstEntry?["title"] as? String ?: "")
```

### Get All Entries as an Array

```kotlin
val allEntries = FFetch("https://example.com/query-index.json").all()
println("Total entries: ${allEntries.size}")
```

## HTTP Caching

KotlinFFetch includes comprehensive HTTP caching support that respects server cache control headers by default and allows for custom cache configurations.

### Default Caching Behavior

By default, KotlinFFetch uses HTTP client caching and respects HTTP cache control headers:

```swift
// First request fetches from server
let entries1 = try await FFetch(url: "https://example.com/api/data.json").all()

// Second request uses cache if server sent appropriate cache headers
let entries2 = try await FFetch(url: "https://example.com/api/data.json").all()
```

### Cache Configuration

Use the `.cache()` method to configure caching behavior:

```swift
// Always fetch fresh data (bypass cache)
let freshData = try await FFetch(url: "https://example.com/api/data.json")
    .cache(.noCache)
    .all()

// Only use cached data (won't make network request)
let cachedData = try await FFetch(url: "https://example.com/api/data.json")
    .cache(.cacheOnly)
    .all()

// Use cache if available, otherwise load from network
let data = try await FFetch(url: "https://example.com/api/data.json")
    .cache(.cacheElseLoad)
    .all()
```

### Custom Cache Configuration

Create your own cache with specific configurations:

```kotlin
val customConfig = FFetchCacheConfig(
    policy = CachePolicy.USE_PROTOCOL_CACHE_POLICY,
    maxAge = 3600  // Cache for 1 hour regardless of server headers
)

val data = FFetch("https://example.com/api/data.json")
    .cache(customConfig)
    .all()
```

### Cache Sharing

The cache is reusable between multiple FFetch calls:

```kotlin
// Create a shared cache configuration
val config = FFetchCacheConfig()

// Use with FFetch
val ffetchData = FFetch("https://api.example.com/data.json")
    .cache(config)
    .all()
```

### Backward Compatibility

Legacy cache methods are still supported:

```kotlin
// Legacy method - maps to .cache(CachePolicy.NO_CACHE)
val freshData = FFetch("https://example.com/api/data.json")
    .reloadCache()
    .all()

// Legacy method with parameter
val data = FFetch("https://example.com/api/data.json")
    .withCacheReload(false)  // Uses default cache behavior
    .all()
```

## Advanced Usage

```kotlin
val allEntries = FFetch("https://example.com/query-index.json").all()
allEntries.forEach { entry ->
    println(entry)
}
```

### Map and Filter Entries

```kotlin
val filteredTitles = FFetch("https://example.com/query-index.json")
    .map { it["title"] as? String }
    .filter { it?.contains("Kotlin") == true }

for (title in filteredTitles) {
    println(title ?: "")
}
```

### Control Pagination with `chunks` and `limit`

```kotlin
val limitedEntries = FFetch("https://example.com/query-index.json")
    .chunks(100)
    .limit(5)

for (entry in limitedEntries) {
    println(entry)
}
```

### Access a Specific Sheet

```kotlin
val productEntries = FFetch("https://example.com/query-index.json")
    .sheet("products")

for (entry in productEntries) {
    println(entry["sku"] as? String ?: "")
}
```

### Document Following with Security

SwiftFFetch provides a `follow()` method to fetch HTML documents referenced in your data. For security, document following is restricted to the same hostname as your initial request by default.

```kotlin
// Basic document following (same hostname only)
val entriesWithDocs = FFetch("https://example.com/query-index.json")
    .follow("path", "document")  // follows URLs in 'path' field
    .all()

// The 'document' field will contain parsed HTML Document objects
for (entry in entriesWithDocs) {
    val doc = entry["document"] as? Document
    doc?.let { println(it.title()) }
}
```

#### Allowing Additional Hostnames

To allow document following to other hostnames, use the `allow()` method:

```kotlin
// Allow specific hostname
val entries = FFetch("https://example.com/query-index.json")
    .allow("trusted.com")
    .follow("path", "document")
    .all()

// Allow multiple hostnames
val entries = FFetch("https://example.com/query-index.json")
    .allow(listOf("trusted.com", "api.example.com"))
    .follow("path", "document")
    .all()

// Allow all hostnames (use with caution)
val entries = FFetch("https://example.com/query-index.json")
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

See the `examples` package in the repository for more detailed usage.

## License

This project is licensed under the terms of the Apache License 2.0. See [LICENSE](LICENSE) for details.

## Development Setup

### Quick Commands
Use Gradle for common development tasks:

```bash
# Run all tests
./gradlew test

# Run tests with coverage
./gradlew test jacocoTestReport

# Build the project
./gradlew build

# Clean build artifacts
./gradlew clean

# Publish to local repository
./gradlew publishToMavenLocal
```
