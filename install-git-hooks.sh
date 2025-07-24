#!/bin/bash

# Install custom Git hooks for Kotlin FFetch project

set -e

echo "🔧 Installing Git hooks..."

# Create .git/hooks directory if it doesn't exist
mkdir -p .git/hooks

# Copy our custom hooks
if [ -f ".githooks/pre-commit" ]; then
    cp .githooks/pre-commit .git/hooks/pre-commit
    chmod +x .git/hooks/pre-commit
    echo "✅ Installed pre-commit hook"
else
    echo "❌ .githooks/pre-commit not found"
    exit 1
fi

# Configure Git to use our hooks directory (alternative approach)
git config core.hooksPath .githooks

echo ""
echo "✅ Git hooks installed successfully!"
echo ""
echo "📋 Installed hooks:"
echo "   • pre-commit - Runs linting, formatting, and tests"
echo ""
echo "🎯 The hook will run automatically on 'git commit'"
echo "💡 To skip hooks (not recommended): git commit --no-verify"