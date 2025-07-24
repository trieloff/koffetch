plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"
    jacoco
    `maven-publish`
    id("io.gitlab.arturbosch.detekt") version "1.23.4"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
}

group = "com.terragon.kotlinffetch"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Core Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    
    // Coroutines for async/await functionality
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // HTTP client (equivalent to URLSession)
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    
    // HTML parsing
    implementation("org.jsoup:jsoup:1.17.2")
    
    // JSON handling
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.ktor:ktor-client-mock:2.3.7")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

// Detekt configuration
detekt {
    toolVersion = "1.23.4"
    config.setFrom(file("detekt.yml"))
    buildUponDefaultConfig = true
}

// KtLint configuration
ktlint {
    version.set("1.0.1")
    debug.set(false)
    verbose.set(true)
    android.set(false)
    outputToConsole.set(true)
    outputColorName.set("RED")
    ignoreFailures.set(false)
    enableExperimentalRules.set(true)
    filter {
        exclude("**/generated/**")
        include("**/kotlin/**")
    }
}

// Add linting tasks
tasks.register("lint") {
    dependsOn("ktlintCheck", "detekt")
    description = "Run all linting checks"
    group = "verification"
}

tasks.register("lintFix") {
    dependsOn("ktlintFormat")
    description = "Fix linting issues where possible"
    group = "formatting"
}

// Make check depend on lint
tasks.check {
    dependsOn("lint")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.terragon.kotlinffetch"
            artifactId = "kotlin-ffetch"
            version = "1.0.0"
            
            from(components["java"])
            
            pom {
                name.set("KotlinFFetch")
                description.set("A Kotlin library for fetching and processing content from AEM (.live) Content APIs")
                url.set("https://github.com/terragon/kotlin-ffetch")
                
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }
}