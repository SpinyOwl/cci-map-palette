# CCI Map Palette

Gradle-driven project for building an FTB Chunks support resource pack. The current pack adds map colors for blocks from Chisel Chipped Integration.

## Structure

- `resourcepack/` contains the actual resource pack contents.
- `branding/` contains project branding assets.
- `scripts/` contains helper scripts and shell entry points used by Gradle.
- `docs/` contains project documentation.
- `gradle.properties` contains committed project configuration.
- `local.properties` is an optional ignored override file for local machine paths.

## Gradle Tasks

```powershell
.\gradlew.bat printProjectConfig
.\gradlew.bat generateColors
.\gradlew.bat generateBranding
.\gradlew.bat packResourcepack
```

To copy outputs into your local Minecraft instance, copy `local.properties.example` to `local.properties`. The relative paths for the `Star Technology` instance are already prepared.

## Shell Entry Point

On systems with `sh`, you can build the zip with:

```sh
./scripts/pack-resourcepack.sh
```
