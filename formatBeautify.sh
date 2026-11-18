#!/bin/bash

# ===================================================
# formatBeautify.sh
# ===================================================
# Formats all Java source files under src/net/murat/ using the
# Google Java Format tool (format2_google_java.jar).
#
# Usage:
#     ./formatBeautify.sh
#
# The JAR path is resolved relative to the project root.
# ===================================================

set -e

# --- Resolve project root (directory containing this script) ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# --- Paths ---
FORMATTER="../../classes/format2_google_java.jar"
SRC_ROOT="src/net/murat"

# --- Sanity checks ---
if [ ! -f "$FORMATTER" ]; then
    echo "[ERROR] Formatter JAR not found: $FORMATTER"
    echo "        Expected at: $(cd .. && pwd)/classes/format2_google_java.jar"
    exit 1
fi

if [ ! -d "$SRC_ROOT" ]; then
    echo "[ERROR] Source root not found: $SRC_ROOT"
    exit 2
fi

# --- Collect all .java files under src/net/murat (recursive) ---
FILES=$(find "$SRC_ROOT" -type f -name "*.java")

if [ -z "$FILES" ]; then
    echo "[INFO] No .java files found under $SRC_ROOT"
    exit 0
fi

# --- Count and format ---
COUNT=0
while IFS= read -r file; do
    echo "[FORMAT] $file"
    java -jar "$FORMATTER" --replace "$file"
    COUNT=$((COUNT + 1))
done <<< "$FILES"

echo "[DONE] Formatted $COUNT file(s)."
