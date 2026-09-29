param(
    [string]$OutputPath = (Join-Path $PSScriptRoot 'artifacts/kadaikutty-pos-server.zip')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$serverDirectory = Resolve-Path (Join-Path $PSScriptRoot '../server')
$resolvedOutputPath = [System.IO.Path]::GetFullPath($OutputPath)
$outputDirectory = Split-Path -Parent $resolvedOutputPath
$stagingDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("kadaikutty-pos-eb-" + [guid]::NewGuid().ToString('N'))

try {
    Push-Location $serverDirectory
    npm.cmd ci
    if ($LASTEXITCODE -ne 0) { throw "npm ci failed with exit code $LASTEXITCODE" }
    npm.cmd test
    if ($LASTEXITCODE -ne 0) { throw "npm test failed with exit code $LASTEXITCODE" }
    Pop-Location

    New-Item -ItemType Directory -Path $stagingDirectory -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $serverDirectory 'package.json') -Destination $stagingDirectory
    Copy-Item -LiteralPath (Join-Path $serverDirectory 'package-lock.json') -Destination $stagingDirectory
    Copy-Item -LiteralPath (Join-Path $serverDirectory 'Procfile') -Destination $stagingDirectory
    Copy-Item -LiteralPath (Join-Path $serverDirectory 'dist') -Destination $stagingDirectory -Recurse

    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    if (Test-Path -LiteralPath $resolvedOutputPath) {
        Remove-Item -LiteralPath $resolvedOutputPath -Force
    }
    $archive = [System.IO.Compression.ZipFile]::Open(
        $resolvedOutputPath,
        [System.IO.Compression.ZipArchiveMode]::Create
    )
    try {
        Get-ChildItem -LiteralPath $stagingDirectory -File -Recurse | ForEach-Object {
            $entryName = $_.FullName.Substring($stagingDirectory.Length + 1).Replace('\', '/')
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $archive,
                $_.FullName,
                $entryName,
                [System.IO.Compression.CompressionLevel]::Optimal
            ) | Out-Null
        }
    }
    finally {
        $archive.Dispose()
    }
    Write-Host "Elastic Beanstalk bundle created: $resolvedOutputPath"
}
finally {
    if ((Get-Location).Path -eq $serverDirectory.Path) {
        Pop-Location
    }
    if (Test-Path -LiteralPath $stagingDirectory) {
        Remove-Item -LiteralPath $stagingDirectory -Recurse -Force
    }
}
