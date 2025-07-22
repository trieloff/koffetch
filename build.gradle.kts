plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"
    jacoco
    `maven-publish`
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
    
    // HTML parsing (equivalent to SwiftSoup)
    implementation("org.jsoup:jsoup:1.17.2")
    
    // JSON handling
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.ktor:ktor-client-mock:2.3.7")
}

kotlin {
    jvmToolchain(17)
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.terragon.kotlinffetch"
            artifactId = "kotlin-ffetch"
            version = "1.0.0"
            
            from(components["java"])
            
            pom {
                name.set("KotlinFFetch")
                description.set("A Kotlin port of SwiftFFetch for fetching and processing content from AEM (.live) Content APIs")
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