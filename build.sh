#!/usr/bin/env bash
set -e
command -v mvn >/dev/null || { echo "Install JDK 21 + Maven first (or use the GitHub method in BUILD.md)"; exit 1; }
mvn -q clean package
echo "DONE. Your plugin is: target/DiceGems-1.0.0.jar"
