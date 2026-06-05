param(
    [string]$Repository = $env:GITHUB_REPOSITORY,
    [string]$Tag = "pdfium-desktop-v1",
    [string]$Title = "Desktop Pdfium binaries",
    [string]$PdfiumRoot = "third_party/pdfium"
)

if ([string]::IsNullOrWhiteSpace($Repository)) {
    throw "Repository is required. Pass -Repository owner/repo or set GITHUB_REPOSITORY."
}

$ErrorActionPreference = "Stop"

$folders = @(
    @{ Directory = "linux-x64-v8"; Asset = "pdfium-linux-x64-v8.zip" },
    @{ Directory = "win-x64-v8"; Asset = "pdfium-win-x64-v8.zip" }
)

$workDir = Join-Path ([System.IO.Path]::GetTempPath()) ("reader-pdfium-release-" + [System.Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $workDir | Out-Null

try {
    foreach ($folder in $folders) {
        $source = Join-Path $PdfiumRoot $folder.Directory
        if (-not (Test-Path $source)) {
            throw "Missing Pdfium folder: $source"
        }

        $assetPath = Join-Path $workDir $folder.Asset
        Compress-Archive -Force -Path $source -DestinationPath $assetPath
    }

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    gh release view $Tag --repo $Repository *> $null
    $releaseExists = $LASTEXITCODE -eq 0
    $ErrorActionPreference = $previousErrorActionPreference

    if (-not $releaseExists) {
        gh release create $Tag --repo $Repository --title $Title --notes "Pdfium binaries used by desktop CI packaging."
    }

    foreach ($folder in $folders) {
        $assetPath = Join-Path $workDir $folder.Asset
        gh release upload $Tag $assetPath --repo $Repository --clobber
    }
} finally {
    if (Test-Path $workDir) {
        Remove-Item -Recurse -Force $workDir
    }
}
