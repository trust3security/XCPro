param(
    [string]$RepoRoot = ".",
    [switch]$SkipRateLimitProbe
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Read-LocalProperties {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        throw "local.properties not found at $Path"
    }

    $map = @{}
    foreach ($line in Get-Content -Path $Path) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        if ($line.TrimStart().StartsWith("#")) { continue }

        $parts = $line -split "=", 2
        if ($parts.Count -ne 2) { continue }

        $key = $parts[0].Trim()
        $value = $parts[1].Trim()
        if (-not [string]::IsNullOrWhiteSpace($key)) {
            $map[$key] = $value
        }
    }

    return $map
}

function Invoke-Curl {
    param(
        [Alias('Args')]
        [string[]]$CurlArgs,
        [switch]$CaptureHeaders,
        [string]$OutFile
    )

    $cmd = @("curl.exe", "-sS")
    if ($CaptureHeaders) { $cmd += "-i" }
    $cmd += $CurlArgs

    if (-not [string]::IsNullOrWhiteSpace($OutFile)) {
        $cmd += "-o"
        $cmd += $OutFile
    }

    $rest = @()
    if ($cmd.Count -gt 1) {
        $rest = $cmd[1..($cmd.Count - 1)]
    }

    & $cmd[0] @rest
}

function Redact-SecretsInFile {
    param([string]$Path)

    if (-not (Test-Path $Path)) { return }

    $content = Get-Content -Path $Path -Raw

    $content = [regex]::Replace(
        $content,
        '(?i)([?&](?:token|access_token|refresh_token|sig|signature|auth|apikey|api_key|session|key)=)([^&"\s]+)',
        '$1<REDACTED>'
    )

    $content = [regex]::Replace($content, '(?im)(Set-Cookie:\s*[^=;]+)=([^;\r\n]+)', '$1<REDACTED>')
    $content = [regex]::Replace($content, '(?im)(Authorization:\s*Bearer\s+)([^\r\n]+)', '$1<REDACTED>')
    $content = [regex]::Replace($content, '(?im)("key"\s*:\s*")([^"]+)(")', '$1<REDACTED>$3')
    $content = [regex]::Replace($content, '(?im)("password"\s*:\s*")([^"]+)(")', '$1<REDACTED>$3')
    $content = [regex]::Replace($content, '(?im)("token"\s*:\s*")([^"]+)(")', '$1<REDACTED>$3')

    Set-Content -Path $Path -Value $content -Encoding UTF8
}

function Parse-AuthBody {
    param([string]$AuthResponseFile)

    $raw = Get-Content -Path $AuthResponseFile -Raw
    $jsonStart = $raw.IndexOf('{')
    if ($jsonStart -lt 0) {
        throw "auth_success.txt missing JSON body"
    }

    $body = $raw.Substring($jsonStart)
    try {
        return $body | ConvertFrom-Json -ErrorAction Stop
    } catch {
        throw "Failed parsing auth_success body as JSON"
    }
}

function Get-RegionContext {
    param([string]$Region)

    switch ($Region.ToUpperInvariant()) {
        "WEST_US" { return @{ TimeZone = "Pacific Standard Time"; Lat = 37.0; Lon = -120.0 } }
        "EAST_US" { return @{ TimeZone = "Eastern Standard Time"; Lat = 39.0; Lon = -77.0 } }
        "EUROPE" { return @{ TimeZone = "W. Europe Standard Time"; Lat = 47.0; Lon = 8.0 } }
        "EAST_AUS" { return @{ TimeZone = "AUS Eastern Standard Time"; Lat = -33.8; Lon = 151.2 } }
        "WA" { return @{ TimeZone = "W. Australia Standard Time"; Lat = -31.95; Lon = 115.86 } }
        "NZ" { return @{ TimeZone = "New Zealand Standard Time"; Lat = -43.53; Lon = 172.64 } }
        "JAPAN" { return @{ TimeZone = "Tokyo Standard Time"; Lat = 35.68; Lon = 139.69 } }
        "ARGENTINA_CHILE" { return @{ TimeZone = "Argentina Standard Time"; Lat = -34.60; Lon = -58.38 } }
        "SANEW" { return @{ TimeZone = "South Africa Standard Time"; Lat = -26.20; Lon = 28.04 } }
        "BRAZIL" { return @{ TimeZone = "E. South America Standard Time"; Lat = -23.55; Lon = -46.63 } }
        "HRRR" { return @{ TimeZone = "Mountain Standard Time"; Lat = 39.74; Lon = -104.99 } }
        "ICONEU" { return @{ TimeZone = "W. Europe Standard Time"; Lat = 50.11; Lon = 8.68 } }
        default { return @{ TimeZone = "UTC"; Lat = 37.0; Lon = -120.0 } }
    }
}

function Get-TileXY {
    param(
        [double]$Latitude,
        [double]$Longitude,
        [int]$Zoom
    )

    $n = [math]::Pow(2, $Zoom)
    $x = [math]::Floor((($Longitude + 180.0) / 360.0) * $n)
    $latRad = $Latitude * [math]::PI / 180.0
    $y = [math]::Floor((1.0 - [math]::Log([math]::Tan($latRad) + 1.0 / [math]::Cos($latRad)) / [math]::PI) / 2.0 * $n)

    return @([int]$x, [int]$y)
}

function Find-WorkingTileSlot {
    param(
        [string]$Region,
        [double]$Lat,
        [double]$Lon,
        [string]$TimeZoneId,
        [string]$Param,
        [string]$Origin,
        [int]$Zoom
    )

    $zone = [System.TimeZoneInfo]::FindSystemTimeZoneById($TimeZoneId)
    $localNow = [System.TimeZoneInfo]::ConvertTimeFromUtc([datetime]::UtcNow, $zone)
    $xy = Get-TileXY -Latitude $Lat -Longitude $Lon -Zoom $Zoom
    $x = $xy[0]
    $y = $xy[1]

    foreach ($dayOffset in -1..2) {
        $date = $localNow.Date.AddDays($dayOffset)
        foreach ($hour in 6..20) {
            foreach ($minute in @(0, 30)) {
                $datePart = $date.ToString("yyyyMMdd")
                $timePart = ("{0:D2}{1:D2}" -f $hour, $minute)
                $url = "https://edge.skysight.io/$Region/$datePart/$timePart/$Param/$Zoom/$x/$y"
                $code = & curl.exe -sS -o NUL -w "%{http_code}" $url -H "Origin: $Origin"
                if ($code -eq "200") {
                    return @{
                        Region = $Region
                        DatePart = $datePart
                        TimePart = $timePart
                        Param = $Param
                        Zoom = $Zoom
                        X = $x
                        Y = $y
                        Url = $url
                        Status = $code
                    }
                }
            }
        }
    }

    return $null
}

function Is-WindParameter {
    param([string]$Param)

    $normalized = $Param.Trim().ToUpperInvariant()
    return $normalized -in @("SFCWIND0", "BLTOPWIND")
}

function Resolve-SourceLayer {
    param(
        [string]$Param,
        [string]$TimePart
    )

    $normalized = $Param.Trim().ToUpperInvariant()
    switch ($normalized) {
        "WSTAR_BSRATIO" { return "bsratio" }
        "SFCWIND0" { return "sfcwind0" }
        "BLTOPWIND" { return "bltopwind" }
        default { return $TimePart }
    }
}

function Resolve-SourceLayerCandidates {
    param(
        [string]$Param,
        [string]$TimePart
    )

    $normalized = $Param.Trim().ToUpperInvariant()
    switch ($normalized) {
        "WSTAR_BSRATIO" { return @("bsratio", $TimePart) }
        "SFCWIND0" { return @("sfcwind0") }
        "BLTOPWIND" { return @("bltopwind") }
        default { return @($TimePart) }
    }
}

function Resolve-MaxZoom {
    param([string]$Param)

    if (Is-WindParameter -Param $Param) {
        return 16
    }
    return 5
}

function Resolve-TileFormat {
    param([string]$Param)

    if (Is-WindParameter -Param $Param) {
        return "VECTOR_WIND_POINTS"
    }
    return "VECTOR_INDEXED_FILL"
}

function Extract-LegendArray {
    param([string]$RawLegend)

    $start = $RawLegend.IndexOf('[')
    $end = $RawLegend.LastIndexOf(']')
    if ($start -lt 0 -or $end -le $start) {
        throw "Legend payload does not contain JSON array"
    }

    return $RawLegend.Substring($start, ($end - $start + 1))
}

$root = Resolve-Path $RepoRoot
$localPropsPath = Join-Path $root "local.properties"
$props = Read-LocalProperties -Path $localPropsPath

$user = $props["SKYSIGHT_USER"]
$pass = $props["SKYSIGHT_PASS"]
$apiKey = $props["SKYSIGHT_API_KEY"]

if ([string]::IsNullOrWhiteSpace($user)) { throw "SKYSIGHT_USER is missing in local.properties" }
if ([string]::IsNullOrWhiteSpace($pass)) { throw "SKYSIGHT_PASS is missing in local.properties" }
if ([string]::IsNullOrWhiteSpace($apiKey)) { throw "SKYSIGHT_API_KEY is missing in local.properties" }

$evidenceDir = Join-Path $root "docs/integrations/skysight/evidence"
New-Item -ItemType Directory -Path $evidenceDir -Force | Out-Null

$cookies = Join-Path $evidenceDir "cookies.txt"
$authSuccess = Join-Path $evidenceDir "auth_success.txt"
$auth401 = Join-Path $evidenceDir "auth_401.txt"
$parametersSuccess = Join-Path $evidenceDir "parameters_success.json"
$parameters401 = Join-Path $evidenceDir "parameters_401.txt"
$tileTemplateError = Join-Path $evidenceDir "tile_template_error.txt"
$tileTemplateSuccess = Join-Path $evidenceDir "tile_template_success.json"
$legendError = Join-Path $evidenceDir "legend_error.txt"
$legendSuccess = Join-Path $evidenceDir "legend_success.json"
$valueError = Join-Path $evidenceDir "value_no_data.txt"
$valueSuccess = Join-Path $evidenceDir "value_success.json"
$tileHeadersFail = Join-Path $evidenceDir "tile_sample_headers_fail.txt"
$tileHeadersOk = Join-Path $evidenceDir "tile_sample_headers_ok.txt"
$rateLimitNotes = Join-Path $evidenceDir "RATE_LIMIT_NOTES.md"

$origin = "https://xalps.skysight.io"
$primaryParam = "wstar_bsratio"

$authPayloadPath = Join-Path $evidenceDir "auth_payload.json"
$authPayload = @{ username = $user; password = $pass } | ConvertTo-Json -Compress
[System.IO.File]::WriteAllText($authPayloadPath, $authPayload, [System.Text.UTF8Encoding]::new($false))
Invoke-Curl -CaptureHeaders -Args @(
    "-X", "POST", "https://skysight.io/api/auth",
    "-H", "Content-Type: application/json",
    "-H", "X-API-KEY: $apiKey",
    "--data-binary", "@$authPayloadPath",
    "-c", $cookies
) -OutFile $authSuccess
Redact-SecretsInFile -Path $authSuccess
Remove-Item -Path $authPayloadPath -Force -ErrorAction SilentlyContinue

$badPayloadPath = Join-Path $evidenceDir "auth_payload_bad.json"
$badPayload = @{ username = $user; password = "wrongpassword" } | ConvertTo-Json -Compress
[System.IO.File]::WriteAllText($badPayloadPath, $badPayload, [System.Text.UTF8Encoding]::new($false))
Invoke-Curl -CaptureHeaders -Args @(
    "-X", "POST", "https://skysight.io/api/auth",
    "-H", "Content-Type: application/json",
    "-H", "X-API-KEY: $apiKey",
    "--data-binary", "@$badPayloadPath"
) -OutFile $auth401
Redact-SecretsInFile -Path $auth401
Remove-Item -Path $badPayloadPath -Force -ErrorAction SilentlyContinue

$authBody = Parse-AuthBody -AuthResponseFile $authSuccess
$allowedRegions = @()
if ($authBody.PSObject.Properties.Name -contains "allowed_regions") {
    $allowedRegions = @($authBody.allowed_regions)
}
$selectedRegion = if ($allowedRegions.Count -gt 0) { [string]$allowedRegions[0] } else { "WEST_US" }

$regionCtx = Get-RegionContext -Region $selectedRegion
$workingTile = Find-WorkingTileSlot `
    -Region $selectedRegion `
    -Lat ([double]$regionCtx.Lat) `
    -Lon ([double]$regionCtx.Lon) `
    -TimeZoneId ([string]$regionCtx.TimeZone) `
    -Param $primaryParam `
    -Origin $origin `
    -Zoom 5

if ($null -eq $workingTile) {
    throw "Could not find a working SkySight tile for region $selectedRegion"
}

Invoke-Curl -CaptureHeaders -Args @(
    "https://skysight.io/api/forecast/parameters",
    "-H", "X-API-KEY: $apiKey"
) -OutFile $parameters401
Redact-SecretsInFile -Path $parameters401

$tileTemplateObject = [ordered]@{
    source = "edge.skysight.io"
    region = $workingTile.Region
    date = $workingTile.DatePart
    time = $workingTile.TimePart
    parameter = $workingTile.Param
    urlTemplate = "https://edge.skysight.io/$($workingTile.Region)/$($workingTile.DatePart)/$($workingTile.TimePart)/$($workingTile.Param)/{z}/{x}/{y}"
    minZoom = 3
    maxZoom = (Resolve-MaxZoom -Param $workingTile.Param)
    tileSizePx = 256
    format = (Resolve-TileFormat -Param $workingTile.Param)
    sourceLayer = (Resolve-SourceLayer -Param $workingTile.Param -TimePart $workingTile.TimePart)
    sourceLayerCandidates = (Resolve-SourceLayerCandidates -Param $workingTile.Param -TimePart $workingTile.TimePart)
    originHeaderRequired = $true
    zoomPolicy = [ordered]@{
        fillMaxZoom = 5
        windMaxZoom = 16
    }
    windUrlTemplateExample = "https://edge.skysight.io/$($workingTile.Region)/$($workingTile.DatePart)/$($workingTile.TimePart)/wind/{z}/{x}/{y}/sfcwind0"
}
if (Is-WindParameter -Param $workingTile.Param) {
    $tileTemplateObject["speedProperty"] = "spd"
    $tileTemplateObject["directionProperty"] = "dir"
} else {
    $tileTemplateObject["valueProperty"] = "idx"
}
$tileTemplateObject | ConvertTo-Json -Depth 8 | Set-Content -Path $tileTemplateSuccess -Encoding UTF8

Invoke-Curl -CaptureHeaders -Args @(
    $workingTile.Url
) -OutFile $tileTemplateError
Redact-SecretsInFile -Path $tileTemplateError

Invoke-Curl -CaptureHeaders -Args @(
    "-I", $workingTile.Url
) -OutFile $tileHeadersFail
Invoke-Curl -CaptureHeaders -Args @(
    "-I", $workingTile.Url,
    "-H", "Origin: $origin"
) -OutFile $tileHeadersOk
Redact-SecretsInFile -Path $tileHeadersFail
Redact-SecretsInFile -Path $tileHeadersOk

$legendUrl = "https://static2.skysight.io/$($workingTile.Region)/$($workingTile.DatePart)/legend/$($workingTile.Param)/1800.legend.json"
$legendRawPath = Join-Path $evidenceDir "legend_raw.jsonp"
Invoke-Curl -Args @(
    $legendUrl,
    "-H", "Origin: $origin"
) -OutFile $legendRawPath
$legendRaw = Get-Content -Path $legendRawPath -Raw
$legendJsonArray = Extract-LegendArray -RawLegend $legendRaw
$legendJsonArray | Set-Content -Path $legendSuccess -Encoding UTF8
Remove-Item -Path $legendRawPath -Force -ErrorAction SilentlyContinue

$legendErrorUrl = "https://static2.skysight.io/$($workingTile.Region)/$($workingTile.DatePart)/legend/invalid_param/1800.legend.json"
Invoke-Curl -CaptureHeaders -Args @(
    $legendErrorUrl,
    "-H", "Origin: $origin"
) -OutFile $legendError
Redact-SecretsInFile -Path $legendError

$valuePayloadPath = Join-Path $evidenceDir "value_payload.json"
$valuePayload = @{ times = @("$($workingTile.DatePart)$($workingTile.TimePart)") } | ConvertTo-Json -Compress
[System.IO.File]::WriteAllText($valuePayloadPath, $valuePayload, [System.Text.UTF8Encoding]::new($false))
$valueUrl = "https://cf.skysight.io/point/$($regionCtx.Lat)/$($regionCtx.Lon)?region=$($workingTile.Region)"
Invoke-Curl -Args @(
    "-X", "POST", $valueUrl,
    "-H", "Content-Type: application/json; charset=UTF-8",
    "-H", "Origin: $origin",
    "--data-binary", "@$valuePayloadPath"
) -OutFile $valueSuccess

Invoke-Curl -CaptureHeaders -Args @(
    "-X", "POST", $valueUrl,
    "-H", "Content-Type: application/json; charset=UTF-8",
    "-H", "Origin: $origin",
    "--data-binary", "{}"
) -OutFile $valueError
Redact-SecretsInFile -Path $valueError
Remove-Item -Path $valuePayloadPath -Force -ErrorAction SilentlyContinue

$valueJson = Get-Content -Path $valueSuccess -Raw | ConvertFrom-Json
$pointFields = @($valueJson.PSObject.Properties.Name | Where-Object {
    $_ -notin @("times", "twilights", "ap")
})

$parametersObject = [ordered]@{
    source = "Derived from cf.skysight.io point payload + known overlay mapping"
    region = $workingTile.Region
    date = $workingTile.DatePart
    time = $workingTile.TimePart
    allowedRegions = $allowedRegions
    pointResponseFields = ($pointFields | Sort-Object)
    overlayParameters = @(
        @{ id = "wstar_bsratio"; name = "Thermal"; category = "Thermal"; unit = "m/s"; supportsTiles = $true; supportsLegend = $true; supportsPointValue = $true },
        @{ id = "dwcrit"; name = "Thermal Height"; category = "Thermal"; unit = "m"; supportsTiles = $true; supportsLegend = $true; supportsPointValue = $true },
        @{ id = "sfcwind0"; name = "Surface Wind"; category = "Wind"; unit = "kt"; supportsTiles = $true; supportsLegend = $true; supportsPointValue = $true },
        @{ id = "bltopwind"; name = "BL Top Wind"; category = "Wind"; unit = "kt"; supportsTiles = $true; supportsLegend = $true; supportsPointValue = $true },
        @{ id = "zsfclcl"; name = "Cloudbase"; category = "Cloud"; unit = "m"; supportsTiles = $true; supportsLegend = $true; supportsPointValue = $true },
        @{ id = "zsfclcldif"; name = "Cloudbase Spread"; category = "Cloud"; unit = "m"; supportsTiles = $true; supportsLegend = $true; supportsPointValue = $true },
        @{ id = "accrain"; name = "Rain"; category = "Precip"; unit = "mm"; supportsTiles = $true; supportsLegend = $true; supportsPointValue = $true },
        @{ id = "CFRACL"; name = "Cloud Low"; category = "Cloud"; unit = "%"; supportsTiles = $true; supportsLegend = $true; supportsPointValue = $true },
        @{ id = "CFRACM"; name = "Cloud Mid"; category = "Cloud"; unit = "%"; supportsTiles = $true; supportsLegend = $true; supportsPointValue = $true },
        @{ id = "CFRACH"; name = "Cloud High"; category = "Cloud"; unit = "%"; supportsTiles = $true; supportsLegend = $true; supportsPointValue = $true },
        @{ id = "potfd"; name = "Potential Distance"; category = "XC"; unit = "km"; supportsTiles = $true; supportsLegend = $true; supportsPointValue = $true }
    )
}
$parametersObject | ConvertTo-Json -Depth 8 | Set-Content -Path $parametersSuccess -Encoding UTF8

if (-not $SkipRateLimitProbe) {
    $statuses = @()
    $probePayload = Join-Path $evidenceDir 'value_payload_probe.json'
    $valuePayload | Set-Content -Path $probePayload -Encoding UTF8

    for ($i = 0; $i -lt 12; $i++) {
        $status = & curl.exe -sS -o NUL -w "%{http_code}" `
            -X POST $valueUrl `
            -H "Content-Type: application/json; charset=UTF-8" `
            -H "Origin: $origin" `
            --data-binary "@$probePayload"
        $statuses += [string]$status
    }

    if (Test-Path $probePayload) {
        Remove-Item -Path $probePayload -Force -ErrorAction SilentlyContinue
    }

    $groups = $statuses | Group-Object | Sort-Object Name
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# RATE_LIMIT_NOTES")
    $lines.Add("")
    $lines.Add("Generated by scripts/integrations/capture_skysight_evidence.ps1")
    $lines.Add("")
    $lines.Add("## Probe Summary")
    foreach ($g in $groups) {
        $lines.Add("- HTTP $($g.Name): $($g.Count)")
    }
    if ($groups.Count -eq 0) {
        $lines.Add("- No probe results recorded.")
    }
    $lines.Add("")
    $lines.Add("## Notes")
    $lines.Add("- Probe used cf.skysight.io point endpoint with 12 sequential requests.")
    $lines.Add("- Capture Retry-After or X-RateLimit-* headers if HTTP 429 appears in future testing.")
    $lines.Add("- Tile endpoint requires Origin header (missing Origin returns HTTP 400).")

    Set-Content -Path $rateLimitNotes -Value ($lines -join "`r`n") -Encoding UTF8
} else {
    if (-not (Test-Path $rateLimitNotes)) {
        Set-Content -Path $rateLimitNotes -Value "# RATE_LIMIT_NOTES`r`n`r`nRate-limit probe skipped." -Encoding UTF8
    }
}

Set-Content -Path $cookies -Value "Cookie jar intentionally omitted from evidence pack." -Encoding UTF8

$required = @(
    "auth_success.txt",
    "parameters_success.json",
    "tile_template_success.json",
    "legend_success.json",
    "value_success.json",
    "tile_sample_headers_ok.txt",
    "tile_sample_headers_fail.txt",
    "RATE_LIMIT_NOTES.md"
)

Write-Output "SkySight evidence capture complete."
foreach ($name in $required) {
    $path = Join-Path $evidenceDir $name
    if (Test-Path $path) {
        Write-Output "FOUND $name"
    } else {
        Write-Output "MISSING $name"
    }
}
