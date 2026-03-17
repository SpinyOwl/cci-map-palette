# CCI Map Palette

Resource pack that adds map colors for blocks from Chisel Chipped Integration so they render correctly in FTB Chunks.

## Regenerate

From the pack root:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\generate-ftbchunks-colors.ps1
```

If the mod jar version changes, pass the jar path explicitly:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\generate-ftbchunks-colors.ps1 -SourceJar "..\..\..\mods\chisel_chipped_integration-vX.Y.Z-1.20.1.jar"
```
