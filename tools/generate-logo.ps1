param(
    [int]$PackSize = 256,
    [int]$ListingSize = 512
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

function Save-ResizedPng {
    param(
        [Parameter(Mandatory = $true)][string]$SourcePath,
        [Parameter(Mandatory = $true)][int]$Size,
        [Parameter(Mandatory = $true)][string]$DestinationPath
    )

    $sourceBitmap = [System.Drawing.Bitmap]::new($SourcePath)
    $targetBitmap = [System.Drawing.Bitmap]::new($Size, $Size)
    $graphics = [System.Drawing.Graphics]::FromImage($targetBitmap)

    try {
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $graphics.DrawImage($sourceBitmap, 0, 0, $Size, $Size)

        $directory = Split-Path -Parent $DestinationPath
        if (-not (Test-Path $directory)) {
            New-Item -ItemType Directory -Path $directory | Out-Null
        }

        $targetBitmap.Save($DestinationPath, [System.Drawing.Imaging.ImageFormat]::Png)
    }
    finally {
        $graphics.Dispose()
        $targetBitmap.Dispose()
        $sourceBitmap.Dispose()
    }
}

function New-LogoImage {
    param(
        [Parameter(Mandatory = $true)][int]$Size,
        [Parameter(Mandatory = $true)][string]$Path
    )

    $bitmap = [System.Drawing.Bitmap]::new($Size, $Size)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)

    try {
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

        $rect = [System.Drawing.Rectangle]::new(0, 0, $Size, $Size)
        $bgBrush = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
            [System.Drawing.Point]::new(0, 0),
            [System.Drawing.Point]::new($Size, $Size),
            [System.Drawing.Color]::FromArgb(255, 20, 48, 76),
            [System.Drawing.Color]::FromArgb(255, 13, 111, 104)
        )
        $graphics.FillRectangle($bgBrush, $rect)
        $bgBrush.Dispose()

        $overlayBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(55, 247, 166, 0))
        $graphics.FillEllipse($overlayBrush, $Size * 0.58, $Size * 0.08, $Size * 0.46, $Size * 0.46)
        $overlayBrush.Dispose()

        $gridPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(38, 255, 255, 255), [Math]::Max(1, $Size / 128))
        $margin = [int]($Size * 0.08)
        $step = [int](($Size - ($margin * 2)) / 6)
        for ($i = 0; $i -le 6; $i++) {
            $offset = $margin + ($i * $step)
            $graphics.DrawLine($gridPen, $margin, $offset, $Size - $margin, $offset)
            $graphics.DrawLine($gridPen, $offset, $margin, $offset, $Size - $margin)
        }
        $gridPen.Dispose()

        $frameSize = [int]($Size * 0.34)
        $frameX = [int]($Size * 0.09)
        $frameY = [int]($Size * 0.11)
        $frameRect = [System.Drawing.Rectangle]::new($frameX, $frameY, $frameSize, $frameSize)

        $frameShadow = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(55, 0, 0, 0))
        $graphics.FillRectangle($frameShadow, $frameX + ($Size * 0.02), $frameY + ($Size * 0.03), $frameSize, $frameSize)
        $frameShadow.Dispose()

        $frameBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(235, 243, 239, 228))
        $graphics.FillRectangle($frameBrush, $frameRect)
        $frameBrush.Dispose()

        $borderPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(255, 42, 64, 87), [Math]::Max(4, $Size / 32))
        $graphics.DrawRectangle($borderPen, $frameRect)
        $borderPen.Dispose()

        $cellGap = [int]($Size * 0.03)
        $cellSize = [int](($frameSize - ($cellGap * 3)) / 2)
        $cellX1 = $frameX + $cellGap
        $cellY1 = $frameY + $cellGap
        $cellX2 = $cellX1 + $cellSize + $cellGap
        $cellY2 = $cellY1 + $cellSize + $cellGap

        $palette = @(
            [System.Drawing.Color]::FromArgb(255, 216, 112, 52),
            [System.Drawing.Color]::FromArgb(255, 248, 201, 72),
            [System.Drawing.Color]::FromArgb(255, 54, 164, 146),
            [System.Drawing.Color]::FromArgb(255, 106, 88, 188)
        )
        $positions = @(
            @{ X = $cellX1; Y = $cellY1; Color = $palette[0] },
            @{ X = $cellX2; Y = $cellY1; Color = $palette[1] },
            @{ X = $cellX1; Y = $cellY2; Color = $palette[2] },
            @{ X = $cellX2; Y = $cellY2; Color = $palette[3] }
        )

        foreach ($tile in $positions) {
            $tileRect = [System.Drawing.Rectangle]::new($tile.X, $tile.Y, $cellSize, $cellSize)
            $tileBrush = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
                [System.Drawing.Point]::new($tile.X, $tile.Y),
                [System.Drawing.Point]::new($tile.X + $cellSize, $tile.Y + $cellSize),
                [System.Drawing.Color]::FromArgb(255, [Math]::Min(255, $tile.Color.R + 24), [Math]::Min(255, $tile.Color.G + 24), [Math]::Min(255, $tile.Color.B + 24)),
                [System.Drawing.Color]$tile.Color
            )
            $graphics.FillRectangle($tileBrush, $tileRect)
            $tileBrush.Dispose()

            $tilePen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(180, 18, 28, 42), [Math]::Max(2, $Size / 64))
            $graphics.DrawRectangle($tilePen, $tileRect)
            $tilePen.Dispose()

            $linePen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(60, 255, 255, 255), [Math]::Max(1, $Size / 128))
            $graphics.DrawLine($linePen, $tile.X + ($cellSize * 0.2), $tile.Y, $tile.X + ($cellSize * 0.2), $tile.Y + $cellSize)
            $graphics.DrawLine($linePen, $tile.X + ($cellSize * 0.55), $tile.Y, $tile.X + ($cellSize * 0.55), $tile.Y + $cellSize)
            $graphics.DrawLine($linePen, $tile.X, $tile.Y + ($cellSize * 0.38), $tile.X + $cellSize, $tile.Y + ($cellSize * 0.38))
            $graphics.DrawLine($linePen, $tile.X, $tile.Y + ($cellSize * 0.73), $tile.X + $cellSize, $tile.Y + ($cellSize * 0.73))
            $linePen.Dispose()
        }

        $diamondCenterX = [int]($Size * 0.22)
        $diamondCenterY = [int]($Size * 0.78)
        $diamondRadius = [int]($Size * 0.06)
        $diamondPoints = @(
            [System.Drawing.Point]::new($diamondCenterX, $diamondCenterY - $diamondRadius),
            [System.Drawing.Point]::new($diamondCenterX + $diamondRadius, $diamondCenterY),
            [System.Drawing.Point]::new($diamondCenterX, $diamondCenterY + $diamondRadius),
            [System.Drawing.Point]::new($diamondCenterX - $diamondRadius, $diamondCenterY)
        )
        $diamondBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 244, 132, 38))
        $diamondPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(255, 255, 240, 219), [Math]::Max(3, $Size / 56))
        $graphics.FillPolygon($diamondBrush, $diamondPoints)
        $graphics.DrawPolygon($diamondPen, $diamondPoints)
        $diamondBrush.Dispose()
        $diamondPen.Dispose()

        $fontSize = [Math]::Max(12, [int]($Size * 0.09))
        $font = [System.Drawing.Font]::new("Segoe UI", $fontSize, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
        $stringBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(235, 0, 0, 0))
        $format = [System.Drawing.StringFormat]::new()
        $format.Alignment = [System.Drawing.StringAlignment]::Center
        $format.LineAlignment = [System.Drawing.StringAlignment]::Center
        $format.Trimming = [System.Drawing.StringTrimming]::EllipsisCharacter
        $textX = [single]($Size * 0.08)
        $textWidth = [single]($Size * 0.84)
        $lineHeight = [single]($Size * 0.18)
        $lineStart = [single]($Size * 0.06)
        $graphics.DrawString("FTB", $font, $stringBrush, [System.Drawing.RectangleF]::new($textX, $lineStart, $textWidth, $lineHeight), $format)
        $graphics.DrawString("CHUNKS", $font, $stringBrush, [System.Drawing.RectangleF]::new($textX, $lineStart + ($lineHeight * 1.1), $textWidth, $lineHeight), $format)
        $graphics.DrawString("CHISEL", $font, $stringBrush, [System.Drawing.RectangleF]::new($textX, $lineStart + ($lineHeight * 2.2), $textWidth, $lineHeight), $format)
        $graphics.DrawString("CHIPPED", $font, $stringBrush, [System.Drawing.RectangleF]::new($textX, $lineStart + ($lineHeight * 3.3), $textWidth, $lineHeight), $format)
        $format.Dispose()
        $stringBrush.Dispose()
        $font.Dispose()

        $directory = Split-Path -Parent $Path
        if (-not (Test-Path $directory)) {
            New-Item -ItemType Directory -Path $directory | Out-Null
        }

        $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    }
    finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

$root = Split-Path -Parent $PSScriptRoot
$referenceImage = Join-Path $root "branding\curseforge-logo.png"

if (Test-Path $referenceImage) {
    Save-ResizedPng -SourcePath $referenceImage -Size $PackSize -DestinationPath (Join-Path $root "pack.png")
}
else {
    New-LogoImage -Size $PackSize -Path (Join-Path $root "pack.png")
    New-LogoImage -Size $ListingSize -Path (Join-Path $root "branding\curseforge-logo.png")
}
