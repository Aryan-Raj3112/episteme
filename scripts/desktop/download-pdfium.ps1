param(
    [string]$Repository = $env:GITHUB_REPOSITORY,
    [string]$Tag = "pdfium-desktop-v1",
    [string]$Destination = "third_party/pdfium"
)

if ([string]::IsNullOrWhiteSpace($Repository)) {
    throw "Repository is required. Pass -Repository owner/repo or set GITHUB_REPOSITORY."
}

$ErrorActionPreference = "Stop"

$assets = @(
    @{ Name = "pdfium-linux-x64-v8.zip"; Directory = "linux-x64-v8" },
    @{ Name = "pdfium-win-x64-v8.zip"; Directory = "win-x64-v8" }
)

New-Item -ItemType Directory -Force -Path $Destination | Out-Null
$workDir = Join-Path ([System.IO.Path]::GetTempPath()) ("reader-pdfium-" + [System.Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $workDir | Out-Null

try {
    foreach ($asset in $assets) {
        $zipPath = Join-Path $workDir $asset.Name
        gh release download $Tag --repo $Repository --pattern $asset.Name --dir $workDir --clobber

        if (-not (Test-Path $zipPath)) {
            throw "Pdfium release asset was not downloaded: $($asset.Name)"
        }

        $targetDir = Join-Path $Destination $asset.Directory
        if (Test-Path $targetDir) {
            Remove-Item -Recurse -Force $targetDir
        }

        $extractDir = Join-Path $workDir $asset.Directory
        New-Item -ItemType Directory -Force -Path $extractDir | Out-Null
        Expand-Archive -Force -Path $zipPath -DestinationPath $extractDir

        $rootedDir = Join-Path $extractDir $asset.Directory
        if (Test-Path $rootedDir) {
            Move-Item -Path $rootedDir -Destination $targetDir
        } else {
            New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
            Move-Item -Path (Join-Path $extractDir "*") -Destination $targetDir
        }
    }
} finally {
    if (Test-Path $workDir) {
        Remove-Item -Recurse -Force $workDir
    }
}
