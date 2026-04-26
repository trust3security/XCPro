param(
    [string]$Serial = "R5CT2084XHN",
    [string]$Package = "",
    [string]$Mode = "",
    [string]$OutputDirectory = "C:\Users\Asus\AndroidStudioProjects\XCPro",
    [string]$FileNamePrefix = "s22-ultra-map-diagnostics",
    [switch]$KeepLatest,
    [switch]$ClearRemote,
    [switch]$ClearOnly
)

$ErrorActionPreference = "Stop"

$serialArgs = @()
if (-not [string]::IsNullOrWhiteSpace($Serial)) {
    $serialArgs = @("-s", $Serial)
}

$remoteFile = "files/diagnostics/xcpro-map-diagnostics-latest.txt"
$packageCandidates = @()
if (-not [string]::IsNullOrWhiteSpace($Package)) {
    $packageCandidates += $Package
} else {
    $packageCandidates += @(
        "com.trust3.xcpro.debug",
        "com.example.openxcpro.debug",
        "com.trust3.xcpro"
    )
}

if (-not (Test-Path -LiteralPath $OutputDirectory)) {
    New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
}

$modeArg = $Mode.Trim().ToLowerInvariant()
$resolvedMode = if ([string]::IsNullOrWhiteSpace($modeArg)) { "manual" } else { $modeArg }
if ($resolvedMode -notin @("manual", "live", "condor", "thn", "sim", "replay")) {
    throw "Unsupported mode '$Mode'. Use one of: live, condor, thn, sim, replay, or leave blank."
}

$effectivePrefix = $FileNamePrefix
if ($resolvedMode -ne "manual") {
    $modeSuffixPattern = "-$resolvedMode-"
    if ($effectivePrefix -notmatch [regex]::Escape($modeSuffixPattern)) {
        if ($effectivePrefix -match "(?i)-map-diagnostics$") {
            $effectivePrefix = $effectivePrefix -replace "(?i)-map-diagnostics$", "-$resolvedMode-map-diagnostics"
        } else {
            $effectivePrefix = "$effectivePrefix-$resolvedMode"
        }
    }
}

function Get-RunAsFile {
    param([string]$CandidatePackage)

    $check = adb @serialArgs exec-out run-as $CandidatePackage sh -lc "if [ -f '$remoteFile' ]; then echo FOUND; else echo MISSING; fi"
    if ($LASTEXITCODE -ne 0) {
        return $null
    }
    if ($check.Trim() -ne "FOUND") {
        return $null
    }
    return $true
}

function Clear-RunAsFile {
    param([string]$CandidatePackage)

    adb @serialArgs exec-out run-as $CandidatePackage sh -lc "mkdir -p 'files/diagnostics' && : > '$remoteFile' && echo CLEARED" | Out-Null
    return $LASTEXITCODE -eq 0
}

function Read-RunAsFile {
    param([string]$CandidatePackage)

    return adb @serialArgs exec-out run-as $CandidatePackage cat $remoteFile
}

$selectedPackage = $null
$packagesTried = @()

foreach ($candidate in $packageCandidates) {
    if ([string]::IsNullOrWhiteSpace($candidate)) {
        continue
    }
    $packagesTried += $candidate
    if ($ClearRemote.IsPresent) {
        if (Clear-RunAsFile -CandidatePackage $candidate) {
            $selectedPackage = $candidate
            break
        }
    } elseif ($null -ne (Get-RunAsFile -CandidatePackage $candidate)) {
        $selectedPackage = $candidate
        break
    }
}

if (-not $selectedPackage) {
    if ($ClearRemote.IsPresent) {
        throw "Could not clear debug diagnostics file via run-as for packages: $($packagesTried -join ', ')."
    }
    throw "No debug diagnostics file found at '$remoteFile' for packages: $($packagesTried -join ', ')."
}

if ($ClearRemote.IsPresent) {
    Write-Host "Cleared remote diagnostics:"
    Write-Host "  package: $selectedPackage"
    Write-Host "  device : $Serial"
    Write-Host "  file   : $remoteFile"
    if ($ClearOnly.IsPresent) {
        return
    }
}

$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$outputPath = Join-Path $OutputDirectory "${effectivePrefix}-${timestamp}.txt"

$content = Read-RunAsFile -CandidatePackage $selectedPackage
$header = @"
# map-diagnostics export
# mode=$resolvedMode
# serial=$Serial
# package=$selectedPackage
# timestamp=$timestamp
# remote_file=$remoteFile
# output_file=$outputPath
"@
$payload = "$header`n$content"
$payload | Set-Content -Path $outputPath -Encoding utf8

Write-Host "Wrote diagnostics:"
  Write-Host "  package: $selectedPackage"
  Write-Host "  device : $Serial"
  Write-Host "  file   : $outputPath"

if ($KeepLatest.IsPresent) {
    $latestPath = Join-Path $OutputDirectory "${effectivePrefix}-latest.txt"
    $payload | Set-Content -Path $latestPath -Encoding utf8
    Write-Host "  latest : $latestPath"
}
