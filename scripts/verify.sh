#!/usr/bin/env bash
# Проверка модуля после переноса на Java: scripts/verify.sh :modules:common
set -euo pipefail
module="${1:?укажи модуль, например :modules:common}"
cd "$(dirname "$0")/.."
./gradlew --offline -q compileJava compileTestJava
./gradlew --offline -q "${module}:test"
./gradlew --offline -q apiCheck
echo "OK $module"
