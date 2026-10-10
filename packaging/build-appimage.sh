#!/usr/bin/env bash
# Build a Linux AppImage: a single file that runs on most distributions (needs an OpenGL 3.3 driver).
#
#   mvn -f game/pom.xml package
#   packaging/build-appimage.sh            -> dist/Agrocene-<version>-<arch>.AppImage
#
# appimagetool is downloaded from GitHub unless APPIMAGETOOL points at one. Set APPDIR_ONLY=1 to stop after
# assembling the AppDir (useful to test it with ./AppRun or when appimagetool is unavailable).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="${VERSION:-1.0.0}"
ARCH="${ARCH:-$(uname -m)}"
WORK="$ROOT/game/target/appimage"
APPDIR="$WORK/Agrocene.AppDir"
DIST="$ROOT/dist"

"$ROOT/packaging/package.sh" app-image
rm -rf "$WORK"
mkdir -p "$APPDIR/usr" "$DIST"
cp -a "$ROOT/game/target/jpackage/app-image/Agrocene/." "$APPDIR/usr/"
cp "$ROOT/packaging/linux/agrocene.desktop" "$APPDIR/agrocene.desktop"
cp "$ROOT/packaging/icons/icon-256.png" "$APPDIR/agrocene.png"
cp "$ROOT/packaging/icons/icon-256.png" "$APPDIR/.DirIcon"
cat > "$APPDIR/AppRun" <<'RUN'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/usr/bin/Agrocene" "$@"
RUN
chmod +x "$APPDIR/AppRun"
echo "AppDir ready: $APPDIR"
[ "${APPDIR_ONLY:-0}" = "1" ] && exit 0

TOOL="${APPIMAGETOOL:-}"
if [ -z "$TOOL" ]; then
  TOOL="$WORK/appimagetool"
  URL="https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-$ARCH.AppImage"
  echo "Downloading $URL"
  curl -fsSL -o "$TOOL" "$URL"
  chmod +x "$TOOL"
fi
OUTFILE="$DIST/Agrocene-$VERSION-$ARCH.AppImage"
ARCH="$ARCH" "$TOOL" --appimage-extract-and-run "$APPDIR" "$OUTFILE"
chmod +x "$OUTFILE"
echo "Built $OUTFILE"
