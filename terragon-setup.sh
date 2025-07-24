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

# Download dependencies and attempt compilation
echo "📦 Downloading dependencies and attempting compilation..."
if ./gradlew compileKotlin --quiet > /dev/null 2>&1; then
    echo "✅ Dependencies downloaded and main code compiled successfully"
else
    echo "⚠️  Warning: Main code has compilation issues that need fixing"
    echo "    This is expected for development environments - continuing setup..."
fi

# Create any necessary directories
mkdir -p build/reports
mkdir -p build/test-results

# Setup linting and code quality tools
echo "🔍 Setting up code quality tools..."
if ./gradlew ktlintCheck --quiet > /dev/null 2>&1; then
    echo "✅ KtLint formatting rules installed"
else
    echo "⚠️  KtLint check failed - formatting rules may need adjustment"
fi

if ./gradlew detekt --quiet > /dev/null 2>&1; then
    echo "✅ Detekt static analysis rules installed"
else
    echo "⚠️  Detekt analysis failed - static analysis rules may need adjustment"
fi

echo "🎉 Environment setup complete!"
echo "Repository is ready for Terragon task execution."
echo ""
echo "Available commands:"
echo "  ./gradlew compileKotlin - Compile main Kotlin code"
echo "  ./gradlew build         - Build project (may have test issues)"
echo "  ./gradlew clean         - Clean build artifacts"
echo "  ./gradlew lint          - Run all linting checks"
echo "  ./gradlew lintFix       - Auto-fix formatting issues"
echo "  ./gradlew ktlintCheck   - Check Kotlin formatting"
echo "  ./gradlew ktlintFormat  - Format Kotlin code"
echo "  ./gradlew detekt        - Run static analysis"
echo ""
echo "🔧 Development setup:"
echo "  ./setup-pre-commit.sh   - Install pre-commit hooks (requires Python)"  
echo "  ./install-git-hooks.sh  - Install Git hooks (no Python required)"
echo ""
echo "⚠️  Note: Some tests have compilation issues and may need fixing before running './gradlew test'"