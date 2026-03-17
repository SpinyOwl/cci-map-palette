# CCI Map Palette

Gradle-driven project for building an FTB Chunks support resource pack. The current pack adds map colors for blocks from Chisel Chipped Integration.

## Structure

- `resourcepack/` contains the actual resource pack contents.
- `branding/` contains project branding assets.
- `scripts/` contains shell entry points.
- `docs/` contains project documentation.
- `generator-core/` contains reusable Kotlin generation logic.
- `generator-cli/` contains the parameterized Kotlin CLI used by Gradle.
- `gradle.properties` contains committed project configuration.
- `local.properties` is an optional ignored override file for local machine paths.

## Gradle Tasks

```powershell
.\gradlew.bat printProjectConfig
.\gradlew.bat generateColors
.\gradlew.bat packResourcepack
```

You can override generation inputs per run:

```powershell
.\gradlew.bat generateColors `
  -PgenerateColorsSourceJar="G:\path\to\some-mod.jar" `
  -PgenerateColorsOutputFile="resourcepack\assets\some_mod\ftbchunks_block_colors.json" `
  -PgenerateColorsSourceNamespace="some_mod"
```

To copy outputs into your local Minecraft instance, copy `local.properties.example` to `local.properties`. The relative paths for the `Star Technology` instance are already prepared.

## Shell Entry Point

On systems with `sh`, you can build the zip with:

```sh
./scripts/pack-resourcepack.sh
```
