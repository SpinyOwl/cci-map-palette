# CCI Map Palette

Client-side runtime color overrides for FTB Chunks minimaps.

The mod patches FTB Chunks at map render time and can replace block colors using:
- explicit runtime rules
- automatic texture-aware color resolution for configured mod namespaces
- special-case runtime handling for blocks whose visible color comes from block entities or dynamic model state

The current focus is improving map colors for content-heavy tech and building packs where FTB Chunks falls back to poor `defaultMapColor()` values.

## Layout

- `common/` contains shared code.
- `fabric/` contains the Fabric entrypoint.
- `forge/` contains the Forge entrypoint.

## Features

- Runtime interception of FTB Chunks block color resolution.
- Shared Fabric + Forge client config in `config/cci-map-palette-client.toml`.
- Hot reload of namespace overrides while the game is running.
- In-game commands for debug overlay and namespace management.
- Texture-aware automatic color calculation for configured mods.

## Commands

```text
/cci_map_palette debug [on|off]
/cci_map_palette auto_override
/cci_map_palette auto_override list
/cci_map_palette auto_override unmapped
/cci_map_palette auto_override add <namespace-or-mod-name>
/cci_map_palette auto_override remove <namespace-or-mod-name>
```

## Dependencies

- Minecraft `1.20.1`
- Architectury API
- FTB Chunks
- Fabric API on Fabric

## Build

```sh
.\gradlew.bat build
```

## Publish

CurseForge publishing is wired through Gradle. Fill the publishing properties in [`gradle.properties`](/G:/Programming/cci-map-palette/gradle.properties) or user-local Gradle properties, set a `CURSEFORGE_API_TOKEN` environment variable or `curseforgeApiToken` Gradle property, then run the publish task documented in [PUBLISHING.md](/G:/Programming/cci-map-palette/PUBLISHING.md).
