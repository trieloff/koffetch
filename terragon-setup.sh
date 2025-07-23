#!/bin/bash

# Terragon Labs Environment Setup Script
# This script runs before Terragon begins work on the repository
# Maximum execution time: 3 minutes

set -e

echo "🔧 Setting up KotlinFFetch development environment..."

# Check if Java is available, install if not found
if ! command -v java &> /dev/null; then
    echo "☕ Java not found, installing OpenJDK 21..."
    if command -v apt-get &> /dev/null; then
        apt-get update -qq && apt-get install -y -qq openjdk-21-jdk-headless
        export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
        export PATH=$JAVA_HOME/bin:$PATH
    else
        echo "❌ Cannot install Java automatically. Please ensure Java 21+ is available."
        exit 1
    fi
fi

# Verify Java installation
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
    echo "✅ Java $JAVA_VERSION detected"
else
    echo "❌ Java installation failed"
    exit 1
fi

# Make gradlew executable
if [ -f "./gradlew" ]; then
    chmod +x ./gradlew
    echo "✅ Made gradlew executable"
else
    echo "❌ gradlew not found"
    exit 1
fi

# Test gradle wrapper
echo "🔍 Testing Gradle wrapper..."
if ./gradlew --version > /dev/null 2>&1; then
    echo "✅ Gradle wrapper working"
else
    echo "❌ Gradle wrapper test failed"
    exit 1
fi

# Download dependencies and compile main code
echo "📦 Downloading dependencies and compiling..."
if ./gradlew compileKotlin --quiet > /dev/null 2>&1; then
    echo "✅ Dependencies downloaded and main code compiled"
else
    echo "⚠️  Warning: Could not compile main code"
    exit 1
fi

# Create any necessary directories
mkdir -p build/reports
mkdir -p build/test-results

echo "🎉 Environment setup complete!"
echo "Repository is ready for Terragon task execution."
echo ""
echo "Available commands:"
echo "  ./gradlew compileKotlin - Compile main Kotlin code"
echo "  ./gradlew build         - Build project (may have test issues)"
echo "  ./gradlew clean         - Clean build artifacts"
echo ""
echo "⚠️  Note: Some tests have compilation issues and may need fixing before running './gradlew test'"