# CCI Map Palette

Gradle-driven project for building an FTB Chunks support resource pack. The current pack adds map colors for blocks from Chisel Chipped Integration, XTones Reworked, XyCraft: World, GregTech CEu Modern, Simply Light, Applied Energistics 2, ExtendedAE, and Rechiseled: Create.

## Structure

- `resourcepack/` contains the actual resource pack contents.
- `branding/` contains project branding assets.
- `scripts/` contains shell entry points.
- `docs/` contains project documentation.
- `generator-core/` contains reusable Kotlin generation logic.
- `generator-cli/` contains the parameterized Kotlin CLI used by Gradle.
- `gradle.properties` contains committed project configuration.
- `local.yml` is an optional ignored override file for local machine paths and batch generation specs.

## Gradle Tasks

```sh
.\gradlew.bat printProjectConfig
```
```sh
.\gradlew.bat generateColors
```
```sh
.\gradlew.bat generateAllColors
```
```sh
.\gradlew.bat packResourcepack
```

You can override generation inputs per run:

```sh
.\gradlew.bat generateColors `
  -PgenerateColorsSourceJar="G:\path\to\some-mod.jar" `
  -PgenerateColorsOutputFile="resourcepack\assets\some_mod\ftbchunks_block_colors.json" `
  -PgenerateColorsSourceNamespace="some_mod"
```

To generate a configured set of mods in one run, define `colorGenerationSpecs` in `local.yml`:

```yaml
colorGenerationSpecs:
  - sourceNamespace: mod_a
    sourceJar: G:/path/to/mod-a.jar
  - sourceNamespace: mod_b
    sourceJar: G:/path/to/mod-b.jar
    outputFile: resourcepack/assets/mod_b/ftbchunks_block_colors.json
```

If `outputFile` is omitted, it defaults to `resourcepack/assets/<sourceNamespace>/ftbchunks_block_colors.json`.

If a mod stores textures in a companion jar, set `assetJars` as a comma-separated list in the YAML entry.

If some block models reference textures that are not available in the provided jars, those blocks are skipped and the rest of the namespace is still generated.

To copy outputs into your local Minecraft instance, copy `local.yml.example` to `local.yml`. The relative paths for the `Star Technology` instance are already prepared.

## Shell Entry Point

On systems with `sh`, you can build the zip with:

```sh
./scripts/pack-resourcepack.sh
```
