# Project Notes

## Purpose

This repository builds a Minecraft resource pack that adds `ftbchunks_block_colors.json` data so blocks from supported mods render with sensible colors in FTB Chunks.

## Layout

- `resourcepack/` contains the pack contents that should end up in the zip file.
- `branding/` contains branding and listing assets that are not part of the pack payload.
- `scripts/` contains generator scripts and shell entry points invoked directly or through Gradle tasks.
- `docs/` contains project documentation.
- `gradle.properties` contains committed project metadata and default paths.
- `local.properties` is optional local override config for machine-specific paths such as a Minecraft instance.

## Build Entry Points

- `.\gradlew.bat generateColors` regenerates the block color map.
- `.\gradlew.bat packResourcepack` builds a distributable zip.
- `.\gradlew.bat syncResourcepack` copies the unpacked pack to a configured instance.
- `.\gradlew.bat syncPackedResourcepack` copies the built zip to a configured instance.
