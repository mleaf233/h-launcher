param(
    [string]$ApkPath = "",
    [string[]]$RequiredLibs = @("lib/arm64-v8a/libhttps.so")
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $apkRoot = Join-Path $repoRoot "app/build/outputs/apk"
    if (-not (Test-Path $apkRoot)) {
        Write-Error "Could not find APK output directory: $apkRoot"
        exit 1
    }

    $latestApk = Get-ChildItem -Path $apkRoot -Recurse -Filter *.apk |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $latestApk) {
        Write-Error "No APK found under: $apkRoot"
        exit 1
    }

    $ApkPath = $latestApk.FullName
}

$resolvedApk = (Resolve-Path $ApkPath).Path
Add-Type -AssemblyName System.IO.Compression.FileSystem

$zip = [IO.Compression.ZipFile]::OpenRead($resolvedApk)
try {
    $allEntries = $zip.Entries | ForEach-Object { $_.FullName }
    $nativeLibs = $allEntries | Where-Object { $_ -like "lib/*/*.so" } | Sort-Object

    Write-Output "APK: $resolvedApk"
    Write-Output "Native libraries:"
    $nativeLibs | ForEach-Object { Write-Output "  $_" }

    $missing = @()
    foreach ($required in $RequiredLibs) {
        if (-not ($allEntries -contains $required)) {
            $missing += $required
        }
    }

    if ($missing.Count -gt 0) {
        Write-Error ("Missing required native libraries: " + ($missing -join ", "))
        exit 1
    }

    Write-Output ("Verification passed. Required libraries present: " + ($RequiredLibs -join ", "))
}
finally {
    $zip.Dispose()
}
