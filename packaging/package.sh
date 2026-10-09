#!/usr/bin/env bash
# Build a native app image or installer with jpackage (JDK 21+). The jar must already exist: mvn -f game/pom.xml package
#
#   packaging/package.sh app-image    self-contained folder with its own Java runtime (all platforms)
#   packaging/package.sh installer    .msi (Windows), .dmg (macOS) or .deb (Linux), built on that platform
#
# Output goes to game/target/jpackage/<app-image|installer>. Run from anywhere. Works in Git Bash on Windows.
set -euo pipefail

KIND="${1:-app-image}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGET="$ROOT/game/target"
JAR="$TARGET/hold-the-field.jar"
VERSION="${VERSION:-1.0.0}"
OUT="$TARGET/jpackage/$KIND"
INPUT="$TARGET/jpackage-input"

[ -f "$JAR" ] || { echo "Missing $JAR. Build it first: mvn -f game/pom.xml package" >&2; exit 1; }
command -v jpackage >/dev/null || { echo "jpackage not found: use a full JDK 21 or newer" >&2; exit 1; }

rm -rf "$INPUT" "$OUT"
mkdir -p "$INPUT" "$OUT"
cp "$JAR" "$INPUT/"

# Only the Java modules the game and the launcher use (keeps the bundled runtime small)
MODULES="java.base,java.desktop,java.logging,java.net.http,java.sql,java.naming,java.prefs,jdk.unsupported,jdk.crypto.ec"

case "$(uname -s)" in
  Linux*)  PLATFORM=linux;   NAME="HoldTheField";    ICON="$ROOT/packaging/icons/icon-256.png" ;;
  Darwin*) PLATFORM=mac;     NAME="Hold the Field";  ICON="$ROOT/packaging/icons/hold-the-field.icns" ;;
  MINGW*|MSYS*|CYGWIN*) PLATFORM=windows; NAME="Hold the Field"; ICON="$ROOT/packaging/icons/hold-the-field.ico" ;;
  *) echo "Unsupported platform $(uname -s)" >&2; exit 1 ;;
esac

COMMON=(
  --name "$NAME"
  --app-version "$VERSION"
  --vendor "Team Anthropocene"
  --description "Hold the Field: a farming game where the climate raids your fields and NASA satellite data is your scout"
  --copyright "MIT License"
  --input "$INPUT"
  --main-jar hold-the-field.jar
  --main-class org.anthropocene.htf.launcher.Launcher
  --add-modules "$MODULES"
  # keep bin/java in the bundled runtime: the launcher starts the game as a second Java process
  --jlink-options "--strip-debug --no-man-pages --no-header-files"
  --icon "$ICON"
  --dest "$OUT"
)

case "$KIND" in
  app-image)
    jpackage "${COMMON[@]}" --type app-image
    ;;
  installer)
    case "$PLATFORM" in
      windows) jpackage "${COMMON[@]}" --type msi --win-menu --win-shortcut --win-dir-chooser --win-menu-group "Hold the Field" ;;
      mac)     jpackage "${COMMON[@]}" --type dmg --mac-package-name "Hold the Field" ;;
      linux)   jpackage "${COMMON[@]}" --type deb --linux-shortcut --linux-menu-group Game --linux-package-name hold-the-field --linux-deb-maintainer "team-anthropocene@users.noreply.github.com" ;;
    esac
    ;;
  *) echo "Usage: $0 app-image|installer" >&2; exit 1 ;;
esac

echo "Built:"
ls -la "$OUT"
