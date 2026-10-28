#!/bin/bash
# Build lasercam.jar — requires JDK 17+
# No Maven, no Gradle, no dependencies

set -e
cd "$(dirname "$0")"

echo "Compiling..."
rm -rf build
mkdir -p build/classes
find src/main/java -name "*.java" > build/sources.txt
javac -d build/classes --source-path src/main/java @build/sources.txt

echo "Packaging..."
echo "Main-Class: com.lasercam.core.LaserCamApp" > build/MANIFEST.MF
jar cfm lasercam.jar build/MANIFEST.MF -C build/classes .
rm -rf build

echo "Done: lasercam.jar ($(du -h lasercam.jar | cut -f1))"
echo ""
echo "Usage:"
echo "  java -jar lasercam.jar                        # file chooser"
echo "  java -jar lasercam.jar file.svg               # scanline, pitch=3"
echo "  java -jar lasercam.jar file.svg spiral 2.0    # spiral, pitch=2"
