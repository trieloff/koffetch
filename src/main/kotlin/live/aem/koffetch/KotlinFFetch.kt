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

package live.aem.koffetch

/// KotlinFFetch - A Kotlin port of the SwiftFFetch library for Adobe Experience Manager Edge Delivery Services
///
/// KotlinFFetch provides a fluent API for working with AEM indices, offering lazy pagination,
/// filtering, transformation, and document following capabilities.
///
/// ## Features
///
/// - **Lazy Pagination**: Efficiently stream large datasets without loading everything into memory
/// - **Fluent API**: Chain operations like `filter`, `map`, `limit`, and `slice`
/// - **Coroutines**: Built on Kotlin's coroutines for modern async programming
/// - **Document Following**: Follow references to fetch and parse HTML documents
/// - **Multi-sheet Support**: Work with multi-sheet JSON responses
/// - **Configurable**: Customize HTTP client, HTML parser, and concurrency settings
/// - **Minimal Dependencies**: Only depends on Ktor, Jsoup, and kotlinx-serialization
///
/// ## Basic Usage
///
/// ```kotlin
/// import live.aem.koffetch.*
/// import kotlinx.coroutines.flow.collect
///
/// // Stream all entries
/// val entries = ffetch("/query-index.json")
/// entries.asFlow().collect { entry ->
///     println(entry["title"] as? String ?: "No title")
/// }
///
/// // Get first 10 published entries
/// val published = ffetch("/query-index.json")
///     .filter { (it["published"] as? Boolean) == true }
///     .limit(10)
///     .all()
/// ```
///
/// ## Advanced Usage
///
/// ```kotlin
/// // Follow document references and extract titles
/// val titles = ffetch("/query-index.json")
///     .follow("path", "document")
///     .map { entry ->
///         val doc = entry["document"] as? Document
///         doc?.title() ?: "No document"
///     }
///     .limit(5)
///     .all()
/// ```
///
/// ## Configuration
///
/// ```kotlin
/// // Custom chunk size and concurrency
/// val entries = ffetch("/query-index.json")
///     .chunks(100)
///     .maxConcurrency(10)
///     .withCacheReload(true)
/// ```
object KotlinFFetch {
    /// Current version of KotlinFFetch
    const val VERSION = "1.0.0"
    
    /// Build information
    const val BUILD = "KotlinFFetch $VERSION - Kotlin port of SwiftFFetch for AEM Edge Delivery Services"
}