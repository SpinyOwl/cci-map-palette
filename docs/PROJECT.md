# Project Notes

## Purpose

This repository builds a Minecraft resource pack that adds `ftbchunks_block_colors.json` data so blocks from supported mods render with sensible colors in FTB Chunks.

Current supported mods:

- Chisel Chipped Integration
- XTones Reworked
- XyCraft: World
- GregTech CEu Modern
- Simply Light
- Applied Energistics 2
- ExtendedAE

## Layout

- `resourcepack/` contains the pack contents that should end up in the zip file.
- `branding/` contains branding and listing assets that are not part of the pack payload.
- `scripts/` contains shell entry points.
- `docs/` contains project documentation.
- `generator-core/` contains reusable Kotlin generation logic.
- `generator-cli/` contains the parameterized Kotlin CLI for color map generation.
- `gradle.properties` contains committed project metadata and default paths.
- `local.yml` is optional local override config for machine-specific paths and multi-entry color generation specs.

## Build Entry Points

- `.\gradlew.bat generateColors` regenerates the block color map.
- `.\gradlew.bat generateAllColors` regenerates all configured color maps from `colorGenerationSpecs`.
- `.\gradlew.bat packResourcepack` builds a distributable zip.
- `.\gradlew.bat syncResourcepack` copies the unpacked pack to a configured instance.
- `.\gradlew.bat syncPackedResourcepack` copies the built zip to a configured instance.
