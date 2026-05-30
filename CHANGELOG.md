# Changelog

## 0.3.2

- Updated the build target for Star Technology THETA compatibility, including Forge 47.4.20, Architectury API 9.2.14, FTB Chunks 2001.3.7, and FTB Library 2001.2.12.
- Replaced snapshot build plugins with stable resolvable versions and moved the Gradle wrapper to 8.8 for the newer Loom toolchain.
- Added `/cci_map_palette auto_override unmapped` and `/cci_map_palette auto_override list_unmapped` to list loaded mods that are not currently enabled for automatic runtime color resolution.
- Added explicit CurseForge publication metadata for Minecraft 1.20.1, Fabric, Forge, and Java 17 so uploaded files are tagged correctly.

## 0.3.1

- Added runtime block name resolvers so the large map overlay can show resolved names for blocks with runtime-derived map state.
- Added AE2 and GTCEu cable runtime name resolution so overlay labels reflect the actual cable variant instead of the base block id.
- Added GTCEu cable runtime color resolution to keep minimap colors aligned with resolved cable materials and states.

## 0.3.0

- Added runtime fluid color overrides and automatic fluid color resolution for configured namespaces.
- Unified `auto_override` so namespace support applies to both blocks and fluids.
- Expanded the debug overlay to show fluid ids, whether map coloring comes from block or fluid state, and separate runtime block/fluid override values.
- Broadened AE2 support from `cable_bus` only to AE2 colorable block entities that expose runtime color state.
- Added a targeted `/cci_map_palette invalidate_map <radius>` command for rescanning loaded chunks around the player.
- Hardened the render hook so runtime override failures fall back to the original FTB Chunks path instead of aborting map rendering.

## 0.1.1

- Added default auto-override coverage for more block-heavy building mods including Chipped, Rechiseled, FramedBlocks, Architect's Palette, Dustrial Decor, and Create Diesel Generators.
- Added publishing metadata and Gradle-based CurseForge publishing for Fabric and Forge artifacts.
- Added the project icon to packaged mod metadata for both loaders.

## 0.1.0

- Added runtime FTB Chunks color overrides for Fabric and Forge on Minecraft 1.20.1.
- Added a public client API for registering per-block runtime color resolvers.
- Added automatic namespace-based texture-aware color resolution for selected mods.
- Added shared hot-reloadable client config in `config/cci-map-palette-client.toml`.
- Added in-game commands for debug overlay and automatic override namespace management.
- Added a targeted AE2 `cable_bus` color resolver based on runtime block entity color.
