#!/bin/bash

# Pre-commit setup script for Kotlin FFetch project
# This script installs and configures pre-commit hooks for code quality

set -e

echo "🚀 Setting up pre-commit hooks for Kotlin FFetch..."

# Check if Python is installed
if ! command -v python3 &> /dev/null; then
    echo "❌ Python 3 is required but not installed. Please install Python 3 first."
    exit 1
fi

# Check if pip is installed
if ! command -v pip3 &> /dev/null && ! command -v pip &> /dev/null; then
    echo "❌ pip is required but not installed. Please install pip first."
    exit 1
fi

# Install pre-commit if not already installed
if ! command -v pre-commit &> /dev/null; then
    echo "📦 Installing pre-commit..."
    if command -v pip3 &> /dev/null; then
        pip3 install pre-commit
    else
        pip install pre-commit
    fi
else
    echo "✅ pre-commit is already installed"
fi

# Install pre-commit hooks
echo "🔧 Installing pre-commit hooks..."
pre-commit install

# Install commit-msg hook for conventional commits (optional)
echo "📝 Installing commit-msg hook..."
pre-commit install --hook-type commit-msg

# Run pre-commit on all files to test the setup
echo "🧪 Testing pre-commit hooks on all files..."
pre-commit run --all-files || {
    echo "⚠️  Some hooks failed. This is normal for the first run."
    echo "   The hooks will auto-fix formatting issues where possible."
    echo "   Please review the changes and commit them."
}

echo ""
echo "✅ Pre-commit hooks setup complete!"
echo ""
echo "📋 Available hooks:"
echo "   • trailing-whitespace    - Remove trailing whitespace"
echo "   • end-of-file-fixer     - Ensure files end with newline"
echo "   • check-yaml            - Validate YAML syntax"
echo "   • check-json            - Validate JSON syntax"
echo "   • check-added-large-files - Prevent large files"
echo "   • ktlint                - Kotlin code formatting"
echo "   • detekt                - Kotlin static analysis"
echo "   • detect-secrets        - Prevent secrets in commits"
echo ""
echo "🎯 Usage:"
echo "   • Hooks run automatically on 'git commit'"
echo "   • Run manually: pre-commit run --all-files"
echo "   • Update hooks: pre-commit autoupdate"
echo "   • Skip hooks: git commit --no-verify (not recommended)"
echo ""
echo "🔧 Gradle linting commands:"
echo "   • ./gradlew lint        - Run all linting checks"
echo "   • ./gradlew lintFix     - Auto-fix formatting issues"
echo "   • ./gradlew ktlintCheck - Check Kotlin formatting"
echo "   • ./gradlew ktlintFormat - Format Kotlin code"
echo "   • ./gradlew detekt      - Run static analysis"
echo ""