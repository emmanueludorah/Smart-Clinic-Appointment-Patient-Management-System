#!/bin/sh
# SCAPMS - build script (Linux / macOS / Git Bash). Requires JDK 17 or newer.
cd "$(dirname "$0")" || exit 1
rm -rf out && mkdir out
javac --release 17 -encoding UTF-8 -d out $(find src -name "*.java") || { echo "BUILD FAILED"; exit 1; }
jar --create --file SCAPMS.jar --main-class scapms.Main -C out .
echo "Build successful. Run with: java -jar SCAPMS.jar"
