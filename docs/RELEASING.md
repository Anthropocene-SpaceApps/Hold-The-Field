# Releasing and packaging

The game ships as native packages, each with its own Java runtime, so players do not need to install Java.

| Platform | Files | Built by |
|---|---|---|
| Linux | `Agrocene-<version>-x86_64.AppImage`, `.deb`, portable `.tar.gz` | `packaging/build-appimage.sh`, `packaging/package.sh` |
| Windows | `.msi` installer and a portable `.zip` | `packaging/package.sh` (jpackage + WiX) |
| macOS | `.dmg` for Apple silicon and for Intel | `packaging/package.sh` (jpackage) |
| Any system with Java 21 | `Agrocene-<version>.jar` | `mvn package` |

All of them start the launcher; **PLAY** starts the game in a second Java process using the bundled runtime.

## Cut a release

```bash
git tag v1.0.0 && git push origin v1.0.0
```

The `release` workflow (`.github/workflows/release.yml`) runs the tests on Linux, builds every package on its own
operating system, uploads them as workflow artifacts, and creates a GitHub Release with checksums. To test the build
without publishing, run the workflow by hand from the Actions tab (**Run workflow**): it uploads the same files as
artifacts and does not create a release.

## Build one locally

You need a JDK 21 (with `jpackage`) and Maven.

```bash
mvn -f game/pom.xml package                  # the jar
packaging/package.sh app-image               # a self-contained folder: game/target/jpackage/app-image
packaging/package.sh installer               # .msi / .dmg / .deb for the platform you are on
packaging/build-appimage.sh                  # Linux only: dist/Agrocene-1.0.0-x86_64.AppImage
APPDIR_ONLY=1 packaging/build-appimage.sh    # just assemble the AppDir to test ./AppRun
```

Notes that cost time once:

- The bundled runtime must keep `bin/java`, because the launcher starts the game with it. `package.sh` passes
  `--jlink-options` without `--strip-native-commands` for exactly this reason.
- Windows installers need WiX Toolset 3 on the PATH. The workflow installs it if the runner lacks it.
- The Linux AppImage is built on Ubuntu 22.04 so it runs on older distributions. It needs an OpenGL 3.3 driver (Mesa is fine).
- `jpackage` only produces a package for the platform and CPU it runs on, hence the separate macOS Intel and Apple silicon jobs.
- Icons are generated art in `packaging/icons/` (a PNG for Linux, `.ico` for Windows, `.icns` for macOS).

## What players will see

The builds are **not code-signed**:

- Windows SmartScreen: "Windows protected your PC" → **More info** → **Run anyway**.
- macOS Gatekeeper: right-click the app → **Open** → **Open** (or `xattr -dr com.apple.quarantine "/Applications/Agrocene.app"`).
- Linux AppImage: `chmod +x Agrocene-*.AppImage`, then run it. On systems without FUSE 2, run it with `--appimage-extract-and-run`.

Signing needs a paid Apple Developer ID and a Windows code-signing certificate. If the team gets either, add the
signing flags to `packaging/package.sh` (`--mac-sign`, and `signtool` for the `.msi`).
