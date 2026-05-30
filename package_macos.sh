#!/usr/bin/env bash
set -euo pipefail

TYPE="${1:-app-image}"
VERSION="${VERSION:-1.0.0}"
VENDOR="${VENDOR:-CatCatch Team}"
SKIP_SERVER="${SKIP_SERVER:-0}"

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
DIST_DIR="$PROJECT_ROOT/dist-macos"
WORK_DIR="$PROJECT_ROOT/packaging/work-macos"
INPUT_DIR="$WORK_DIR/input"
TEMP_DIR="$WORK_DIR/temp"
ICON_PATH="$PROJECT_ROOT/packaging/icons/catcatch.icns"
JAVAFX_LIB_DIR="${JAVAFX_LIB_DIR:-$PROJECT_ROOT/lib}"

if [[ ! -x "/usr/libexec/java_home" ]]; then
  echo "This script must run on macOS." >&2
  exit 1
fi

JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}"
JPACKAGE_EXE="$JAVA_HOME/bin/jpackage"
JAR_EXE="$JAVA_HOME/bin/jar"
JAVAC_EXE="$JAVA_HOME/bin/javac"
MODULE_PATH="$JAVAFX_LIB_DIR:$JAVA_HOME/jmods"

if [[ ! -x "$JPACKAGE_EXE" ]]; then
  echo "jpackage was not found. Install JDK 17 or newer." >&2
  exit 1
fi

if [[ ! -f "$ICON_PATH" ]]; then
  echo "Missing icon: $ICON_PATH" >&2
  exit 1
fi

echo "This packaging flow expects macOS JavaFX jars in: $JAVAFX_LIB_DIR"

OUT_DIR="$PROJECT_ROOT/out"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

mapfile -t JAVA_FILES < <(find "$PROJECT_ROOT/src" -name '*.java' -print)
"$JAVAC_EXE" -encoding UTF-8 --module-path "$JAVAFX_LIB_DIR" --add-modules javafx.controls -d "$OUT_DIR" "${JAVA_FILES[@]}"

rm -rf "$WORK_DIR"
mkdir -p "$INPUT_DIR" "$TEMP_DIR" "$DIST_DIR"

"$JAR_EXE" --create --file "$INPUT_DIR/CatCatch.jar" -C "$OUT_DIR" .

COMMON_ARGS=(
  --type "$TYPE"
  --dest "$DIST_DIR"
  --temp "$TEMP_DIR"
  --input "$INPUT_DIR"
  --main-jar CatCatch.jar
  --app-version "$VERSION"
  --vendor "$VENDOR"
  --copyright "Copyright (c) 2026 CatCatch Team"
  --description "JavaFX multiplayer cat catching game"
  --icon "$ICON_PATH"
)

CLIENT_ARGS=(
  --name CatCatch
  --main-class catcatch.CatCatchJavaFxApp
  --module-path "$MODULE_PATH"
  --add-modules javafx.controls
  --java-options=-Dfile.encoding=UTF-8
)

echo "Packaging CatCatch client as $TYPE ..."
"$JPACKAGE_EXE" "${COMMON_ARGS[@]}" "${CLIENT_ARGS[@]}"

if [[ "$SKIP_SERVER" != "1" ]]; then
  SERVER_ARGS=(
    --name CatCatchServer
    --main-class catcatch.CatCatchServer
    --java-options=-Dfile.encoding=UTF-8
  )

  echo "Packaging CatCatch server as $TYPE ..."
  "$JPACKAGE_EXE" "${COMMON_ARGS[@]}" "${SERVER_ARGS[@]}"
fi

echo
echo "Package output:"
echo "  $DIST_DIR"
