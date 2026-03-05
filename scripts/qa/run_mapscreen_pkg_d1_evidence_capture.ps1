param(
    [string]$RunId,
    [switch]$DryRun,
    [switch]$SkipRequiredGates,
    [switch]$RunConnectedAppTests,
    [switch]$RunConnectedAllModulesAtEnd,
    [switch]$RequireConnectedDevice
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptPath = Join-Path $PSScriptRoot "capture_mapscreen_phase2_package_evidence.ps1"
$invokeArgs = @{
    PackageId = "pkg-d1"
}

if ($PSBoundParameters.ContainsKey("RunId")) { $invokeArgs["RunId"] = $RunId }
if ($DryRun) { $invokeArgs["DryRun"] = $true }
if ($SkipRequiredGates) { $invokeArgs["SkipRequiredGates"] = $true }
if ($RunConnectedAppTests) { $invokeArgs["RunConnectedAppTests"] = $true }
if ($RunConnectedAllModulesAtEnd) { $invokeArgs["RunConnectedAllModulesAtEnd"] = $true }
if ($RequireConnectedDevice) { $invokeArgs["RequireConnectedDevice"] = $true }

& $scriptPath @invokeArgs
