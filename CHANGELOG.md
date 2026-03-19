# Changelog

## 0.3.0

- Added runtime fluid color overrides and automatic fluid color resolution for configured namespaces.
- Added targeted map invalidation commands and expanded the debug overlay with per-pixel FTB Chunks render state reporting.
- Added render-task failure diagnostics to the debug overlay to expose stuck region render errors directly in game.
- Fixed Forge and Fabric fluid color helper platform wiring so fluid override rendering no longer crashes map image tasks.

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
