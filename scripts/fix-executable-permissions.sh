#!/bin/bash
set -e

# Fix executable permissions for Gradle wrapper and scripts
if [ -f "gradlew" ]; then
    chmod +x gradlew
    if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
        git update-index --chmod=+x gradlew 2>/dev/null || true
    fi
fi

if [ -f "scripts/fix-executable-permissions.sh" ]; then
    chmod +x scripts/fix-executable-permissions.sh
fi

echo "Executable permissions updated successfully."
