#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [ ! -f .deps/javafx-controls.jar ]; then
  echo "JavaFX dependencies missing. Run ./setup.sh first, or use mvn javafx:run."
  exit 1
fi
mkdir -p target/classes target/test-classes
compiler_root="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java.home = //p')"
compatibility=(-source 21 -target 21 -Xlint:-options)
if [ -f "$compiler_root/lib/ct.sym" ]; then compatibility=(--release 21); fi
sources=()
while IFS= read -r source_file; do sources+=("$source_file"); done < <(find src/main/java -name '*.java')
java -m jdk.compiler/com.sun.tools.javac.Main "${compatibility[@]}" --module-path .deps --add-modules javafx.controls -d target/classes "${sources[@]}"
if [ "${1:-}" = "--test" ]; then
  tests=()
  while IFS= read -r test_file; do tests+=("$test_file"); done < <(find src/test/java -name '*.java')
  java -m jdk.compiler/com.sun.tools.javac.Main "${compatibility[@]}" -cp target/classes -d target/test-classes "${tests[@]}"
  java -ea -cp target/classes:target/test-classes space.orpheus.SimulationTest
fi
