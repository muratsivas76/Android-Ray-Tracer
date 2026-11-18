#!/bin/bash

# Exit immediately if any command fails
set -e

echo "============================================="
echo "   MURAT RAY TRACER - COMPILING SOURCE       "
echo "============================================="

clear

# 1. Clean old classes
echo "[1/4] Cleaning old classes..."
rm -rf obj/net

# 2. Compile Java sources safely (Targeting Java 1.8 Compliance)
echo "[2/4] Compiling Java source files..."
javac -source 1.8 -target 1.8 -parameters -encoding UTF-8 \
      -sourcepath src -cp obj -g -proc:none -nowarn -O -d obj \
      src/net/murat/gui/MuratRayTracerGUI.java

# 3. Create GUI Executable JAR with Manifest
echo "[3/4] Packaging executable guijtracer.jar..."
jar cmf MANIFEST.MF bin/guijtracer.jar -C obj . -C . src *.java *.sh

# 5. Clean up compiled class tree to maintain code safety
echo "[4/4] Cleaning temporary objects..."
rm -rf obj/net

echo "============================================="
echo "🚀 Build Complete! Launching application..."
echo "============================================="

# Execute the GUI jar in the background
java -jar bin/guijtracer.jar &
