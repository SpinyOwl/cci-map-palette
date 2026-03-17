# Star Technology Chunk Colors: CCI Edition

Resource pack that adds `ftbchunks_block_colors.json` for blocks from `chisel_chipped_integration`, so they render with sensible colors in FTB Chunks.

## Regenerate

From the pack root:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\generate-ftbchunks-colors.ps1
```

If the mod jar version changes, pass the jar path explicitly:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\generate-ftbchunks-colors.ps1 -SourceJar "..\..\..\mods\chisel_chipped_integration-vX.Y.Z-1.20.1.jar"
```
