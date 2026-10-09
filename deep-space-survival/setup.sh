#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
case "$(uname -s):$(uname -m)" in
  Linux:x86_64) platform=linux;;
  Linux:aarch64) platform=linux-aarch64;;
  Darwin:arm64) platform=mac-aarch64;;
  Darwin:x86_64) platform=mac;;
  *) echo "Use Maven on this platform: mvn javafx:run"; exit 1;;
esac
mkdir -p .deps
for module in base graphics controls; do
  curl --fail --location --retry 2 "https://repo.maven.apache.org/maven2/org/openjfx/javafx-$module/21.0.5/javafx-$module-21.0.5-$platform.jar" -o ".deps/javafx-$module.jar"
done
echo "Ready. Run ./run.sh or ./build.sh --test"
