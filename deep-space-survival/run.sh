#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./build.sh
exec java --module-path .deps --add-modules javafx.controls --enable-native-access=javafx.graphics -cp target/classes space.orpheus.GameApp "$@"
