param(
    [string]$RunId,
    [string]$PkgF1RunId,
    [string]$BuildVariant = "debug"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function New-ScenarioScaffold {
    param(
        [Parameter(Mandatory = $true)][string]$TierDir,
        [Parameter(Mandatory = $true)][string]$ScenarioId,
        [Parameter(Mandatory = $true)][string]$Title,
        [Parameter(Mandatory = $true)][string[]]$ExpectedChecks
    )

    $scenarioDir = Join-Path $TierDir $ScenarioId
    $videoDir = Join-Path $scenarioDir "screen_recordings"
    $screenshotsDir = Join-Path $scenarioDir "screenshots"
    $notesDir = Join-Path $scenarioDir "notes"

    New-Item -ItemType Directory -Path $videoDir -Force | Out-Null
    New-Item -ItemType Directory -Path $screenshotsDir -Force | Out-Null
    New-Item -ItemType Directory -Path $notesDir -Force | Out-Null

    @(
        @{ Path = (Join-Path $videoDir "PLACE_SCREEN_RECORDINGS_HERE.txt"); Text = "Drop screen recordings for $ScenarioId in this folder." },
        @{ Path = (Join-Path $screenshotsDir "PLACE_SCREENSHOTS_HERE.txt"); Text = "Drop screenshots for $ScenarioId in this folder." },
        @{ Path = (Join-Path $notesDir "PLACE_NOTES_HERE.txt"); Text = "Drop raw notes for $ScenarioId in this folder." }
    ) | ForEach-Object {
        Set-Content -Path $_.Path -Value $_.Text -Encoding UTF8
    }

    $checklist = @(
        "# Scenario Checklist",
        "",
        "Scenario: $ScenarioId",
        "Title: $Title",
        "",
        "Expected checks:",
        ""
    )
    foreach ($check in $ExpectedChecks) {
        $checklist += "- [ ] $check"
    }
    $checklist += @(
        "",
        "Evidence to attach:",
        "- screen recording in screen_recordings/",
        "- screenshots in screenshots/ if useful",
        "- operator notes in notes/"
    )
    Set-Content -Path (Join-Path $scenarioDir "CHECKLIST.md") -Value ($checklist -join "`r`n") -Encoding UTF8
}

$scenarioSpecs = @(
    [ordered]@{
        id = "scenario-01-replay-thermal"
        title = "Replay Thermal"
        expected = @(
            "Replay trail head stays attached to the blue icon throughout the thermal segment.",
            "Replay tail refresh remains smooth during playback and scrub.",
            "No stale tail state after replay stop or replay/live transition."
        )
    },
    [ordered]@{
        id = "scenario-02-live-circle"
        title = "Live Ground Circle"
        expected = @(
            "Repeated circles look materially less polygonal than the old 2-second live trail.",
            "Tail refresh remains coherent between stored live points.",
            "No obvious icon/trail desync during sustained circular movement."
        )
    },
    [ordered]@{
        id = "scenario-03-circle-exit"
        title = "Circle Exit"
        expected = @(
            "Straight-to-circle transition reacts promptly without waiting for a later point.",
            "Circle-to-straight exit does not leave stale circling drift behavior.",
            "No trail reset or tail snap artifact on exit."
        )
    },
    [ordered]@{
        id = "scenario-04-lifecycle"
        title = "Lifecycle Resume"
        expected = @(
            "Background/foreground transitions keep the blue icon and trail aligned.",
            "No frozen tail after resume.",
            "No duplicate cadence symptoms after resume."
        )
    },
    [ordered]@{
        id = "scenario-05-zoom-stress"
        title = "Zoom Stress"
        expected = @(
            "Zoom sweeps while moving do not cause tail flicker.",
            "Trail shape remains stable across medium/high zoom transitions.",
            "No thermalling style/trail flicker if thermalling contrast override is active."
        )
    },
    [ordered]@{
        id = "scenario-06-live-replay-switch"
        title = "Live Replay Switch"
        expected = @(
            "Live to replay transition does not contaminate trail timebase.",
            "Replay to live transition does not reuse stale display-pose snapshots.",
            "No stale tail or duplicate frame-owner symptoms after switching modes."
        )
    }
)

$repoRoot = Resolve-Path (Join-Path (Join-Path $PSScriptRoot "..") "..")
Set-Location $repoRoot

if ([string]::IsNullOrWhiteSpace($RunId)) {
    $RunId = Get-Date -Format "yyyyMMdd-HHmmss"
}

$generatedAt = (Get-Date).ToString("o")
$artifactRoot = Join-Path $repoRoot "artifacts/snail/ground/$RunId"
New-Item -ItemType Directory -Path $artifactRoot -Force | Out-Null

foreach ($tier in @("tier_a", "tier_b")) {
    $tierDir = Join-Path $artifactRoot $tier
    New-Item -ItemType Directory -Path $tierDir -Force | Out-Null
    foreach ($scenario in $scenarioSpecs) {
        New-ScenarioScaffold -TierDir $tierDir -ScenarioId $scenario.id -Title $scenario.title -ExpectedChecks $scenario.expected
    }
}

$sha = (git rev-parse HEAD).Trim()
$branch = (git branch --show-current).Trim()
$dirty = -not [string]::IsNullOrWhiteSpace((git status --porcelain))
$pkgF1ArtifactPath = if ([string]::IsNullOrWhiteSpace($PkgF1RunId)) {
    $null
} else {
    "artifacts/mapscreen/phase1/pkg-f1/$PkgF1RunId"
}

$manifest = [ordered]@{
    run_id = $RunId
    generated_at = $generatedAt
    purpose = "Ownship snail-trail ground validation"
    commit_sha = $sha
    branch = $branch
    dirty_worktree = $dirty
    build_variant = $BuildVariant
    pkg_f1_run_id = if ([string]::IsNullOrWhiteSpace($PkgF1RunId)) { $null } else { $PkgF1RunId }
    pkg_f1_artifact_path = $pkgF1ArtifactPath
    device_tiers = @("tier_a", "tier_b")
    scenario_ids = @($scenarioSpecs | ForEach-Object { $_.id })
}

$runbook = @(
    "# Snail Ground Validation Runbook",
    "",
    "Run ID: $RunId",
    "Build variant: $BuildVariant",
    "",
    "1. Run repo gates first:",
    "   - ./gradlew enforceRules",
    "   - ./gradlew testDebugUnitTest",
    "   - ./gradlew assembleDebug",
    "2. Capture the lifecycle/cadence proof lane before or alongside field testing:",
    "   - powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_pkg_f1_evidence_capture.ps1 -RunConnectedAppTests -RequireConnectedDevice",
    "3. For scenario-01, use the synthetic thermal replay baseline first (`THR` clean, `THN` wind-noisy) and record a screen capture.",
    "4. Use a known real thermal segment only as a secondary comparison if needed.",
    "5. For live scenarios, test outdoors with stable GNSS; bicycle/slow vehicle loops are preferred over walking if safe.",
    "6. Run the six scenarios on Tier A and Tier B devices.",
    "7. Attach evidence into each tier/scenario folder and complete ACCEPTANCE.md and observations.md.",
    "",
    "Important rules:",
    "- Ground testing is pre-flight confidence, not final in-air signoff.",
    "- Replay validation is mandatory because it is the best repeatable thermal-shape check on the ground.",
    "- `docs/IGC/example.igc` is still useful as a real-world replay smoke fixture, but it is not the primary thermalling-quality baseline.",
    "- pkg-f1 proof is required for MS-ENG-06 and MS-ENG-10; manual ground testing does not replace it."
) -join "`r`n"

$acceptance = @(
    "# Acceptance",
    "",
    "| Item | Status | Notes |",
    "|---|---|---|",
    "| Repo gates green | NOT RUN | |",
    "| pkg-f1 evidence attached | NOT RUN | |",
    "| scenario-01 replay thermal passed on Tier A | NOT RUN | |",
    "| scenario-01 replay thermal passed on Tier B | NOT RUN | |",
    "| scenario-02 live circle passed on Tier A | NOT RUN | |",
    "| scenario-02 live circle passed on Tier B | NOT RUN | |",
    "| scenario-03 circle exit passed on Tier A | NOT RUN | |",
    "| scenario-03 circle exit passed on Tier B | NOT RUN | |",
    "| scenario-04 lifecycle passed on Tier A | NOT RUN | |",
    "| scenario-04 lifecycle passed on Tier B | NOT RUN | |",
    "| scenario-05 zoom stress passed on Tier A | NOT RUN | |",
    "| scenario-05 zoom stress passed on Tier B | NOT RUN | |",
    "| scenario-06 live/replay switch passed on Tier A | NOT RUN | |",
    "| scenario-06 live/replay switch passed on Tier B | NOT RUN | |"
) -join "`r`n"

$observations = @(
    "# Observations",
    "",
    "| Tier | Scenario | Status | Notes |",
    "|---|---|---|---|",
    "| tier_a | scenario-01-replay-thermal | NOT RUN | |",
    "| tier_a | scenario-02-live-circle | NOT RUN | |",
    "| tier_a | scenario-03-circle-exit | NOT RUN | |",
    "| tier_a | scenario-04-lifecycle | NOT RUN | |",
    "| tier_a | scenario-05-zoom-stress | NOT RUN | |",
    "| tier_a | scenario-06-live-replay-switch | NOT RUN | |",
    "| tier_b | scenario-01-replay-thermal | NOT RUN | |",
    "| tier_b | scenario-02-live-circle | NOT RUN | |",
    "| tier_b | scenario-03-circle-exit | NOT RUN | |",
    "| tier_b | scenario-04-lifecycle | NOT RUN | |",
    "| tier_b | scenario-05-zoom-stress | NOT RUN | |",
    "| tier_b | scenario-06-live-replay-switch | NOT RUN | |"
) -join "`r`n"

$manifest | ConvertTo-Json -Depth 12 | Set-Content -Path (Join-Path $artifactRoot "manifest.json") -Encoding UTF8
$runbook | Set-Content -Path (Join-Path $artifactRoot "RUNBOOK.md") -Encoding UTF8
$acceptance | Set-Content -Path (Join-Path $artifactRoot "ACCEPTANCE.md") -Encoding UTF8
$observations | Set-Content -Path (Join-Path $artifactRoot "observations.md") -Encoding UTF8

Write-Host ""
Write-Host "[snail-ground] Created ground-validation scaffold."
Write-Host "[snail-ground] Artifact root: $artifactRoot"
