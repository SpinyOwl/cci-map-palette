param(
    [string]$SourceJar = "..\..\..\mods\chisel_chipped_integration-v1.1.6-1.20.1.jar",
    [string]$OutputFile = "resourcepack\assets\chisel_chipped_integration\ftbchunks_block_colors.json"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Drawing

function Get-ZipText {
    param(
        [Parameter(Mandatory = $true)]$Zip,
        [Parameter(Mandatory = $true)][string]$EntryPath
    )

    $entry = $Zip.GetEntry($EntryPath)
    if ($null -eq $entry) {
        throw "Missing zip entry: $EntryPath"
    }

    $reader = [System.IO.StreamReader]::new($entry.Open())
    try {
        return $reader.ReadToEnd()
    }
    finally {
        $reader.Dispose()
    }
}

function Get-ZipJson {
    param(
        [Parameter(Mandatory = $true)]$Zip,
        [Parameter(Mandatory = $true)][string]$EntryPath
    )

    return (Get-ZipText -Zip $Zip -EntryPath $EntryPath) | ConvertFrom-Json
}

function Get-BlockstateModelId {
    param(
        [Parameter(Mandatory = $true)][string]$JsonText,
        [Parameter(Mandatory = $true)][string]$EntryPath
    )

    $match = [regex]::Match($JsonText, '"model"\s*:\s*"([^"]+)"')
    if (-not $match.Success) {
        throw "No model found in blockstate: $EntryPath"
    }

    return $match.Groups[1].Value
}

function Resolve-TextureReference {
    param(
        [Parameter(Mandatory = $true)]$Textures,
        [Parameter(Mandatory = $true)][string[]]$PreferredKeys
    )

    foreach ($key in $PreferredKeys) {
        $properties = $Textures.PSObject.Properties.Name
        if ($properties -contains $key) {
            $value = [string]$Textures.$key
            while ($value.StartsWith("#")) {
                $lookup = $value.Substring(1)
                if (-not ($properties -contains $lookup)) {
                    throw "Unresolved texture reference: $value"
                }

                $value = [string]$Textures.$lookup
            }

            return $value
        }
    }

    return $null
}

function Get-AverageHexColor {
    param(
        [Parameter(Mandatory = $true)]$Zip,
        [Parameter(Mandatory = $true)][string]$TextureId
    )

    if ($TextureId -notmatch ":") {
        throw "Texture id is missing namespace: $TextureId"
    }

    $namespace, $path = $TextureId -split ":", 2
    $entryPath = "assets/$namespace/textures/$path.png"
    $entry = $Zip.GetEntry($EntryPath)
    if ($null -eq $entry) {
        throw "Missing texture: $entryPath"
    }

    $stream = $entry.Open()
    try {
        $bitmap = [System.Drawing.Bitmap]::new($stream)
        try {
            [long]$r = 0
            [long]$g = 0
            [long]$b = 0
            [long]$count = 0

            for ($x = 0; $x -lt $bitmap.Width; $x++) {
                for ($y = 0; $y -lt $bitmap.Height; $y++) {
                    $pixel = $bitmap.GetPixel($x, $y)
                    if ($pixel.A -eq 0) {
                        continue
                    }

                    $r += $pixel.R
                    $g += $pixel.G
                    $b += $pixel.B
                    $count++
                }
            }

            if ($count -eq 0) {
                return "#000000"
            }

            return "#{0:X2}{1:X2}{2:X2}" -f ([int]($r / $count)), ([int]($g / $count)), ([int]($b / $count))
        }
        finally {
            $bitmap.Dispose()
        }
    }
    finally {
        $stream.Dispose()
    }
}

$resolvedJar = if ([System.IO.Path]::IsPathRooted($SourceJar)) {
    $SourceJar
}
else {
    [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot $SourceJar))
}

$resolvedOutput = if ([System.IO.Path]::IsPathRooted($OutputFile)) {
    $OutputFile
}
else {
    [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\$OutputFile"))
}

$zip = [System.IO.Compression.ZipFile]::OpenRead($resolvedJar)
try {
    $blockstates = $zip.Entries |
        Where-Object { $_.FullName -like "assets/chisel_chipped_integration/blockstates/*.json" } |
        Sort-Object FullName

    $colors = [ordered]@{}

    foreach ($blockstate in $blockstates) {
        $blockId = [System.IO.Path]::GetFileNameWithoutExtension($blockstate.Name)
        $blockstateText = Get-ZipText -Zip $zip -EntryPath $blockstate.FullName
        $modelId = Get-BlockstateModelId -JsonText $blockstateText -EntryPath $blockstate.FullName

        $modelNamespace, $modelPath = $modelId -split ":", 2
        $modelEntryPath = "assets/$modelNamespace/models/$modelPath.json"
        $modelJson = Get-ZipJson -Zip $zip -EntryPath $modelEntryPath

        $textureId = Resolve-TextureReference -Textures $modelJson.textures -PreferredKeys @(
            "up",
            "top",
            "all",
            "end",
            "wool",
            "side",
            "carpet_side",
            "particle"
        )

        if (-not $textureId) {
            throw "No usable texture found in model: $modelEntryPath"
        }

        $colors[$blockId] = Get-AverageHexColor -Zip $zip -TextureId $textureId
    }

    $outputDir = Split-Path -Parent $resolvedOutput
    if (-not (Test-Path $outputDir)) {
        New-Item -ItemType Directory -Path $outputDir | Out-Null
    }

    $json = $colors | ConvertTo-Json -Depth 5
    [System.IO.File]::WriteAllText($resolvedOutput, $json + [Environment]::NewLine)
}
finally {
    $zip.Dispose()
}
