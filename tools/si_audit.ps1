$ErrorActionPreference = "Stop"

$forbidden = @(
  "kmh","kph","km/h",
  "knots","\bkt\b",
  "fpm","ft/min",
  "\bft\b","feet",
  "mph"
)

$files = Get-ChildItem -Recurse -Include *.kt -Path . | Where-Object { $_.FullName -notmatch "\\build\\|\\.gradle\\|\\.idea\\|\\generated\\" }

$hits = @()

foreach ($f in $files) {
  $text = Get-Content $f.FullName -Raw
  foreach ($pat in $forbidden) {
    if ($text -match $pat) {
      $hits += "$($f.FullName): $pat"
    }
  }
}

if ($hits.Count -gt 0) {
  Write-Host "❌ SI audit FAILED. Found forbidden tokens:" -ForegroundColor Red
  $hits | Sort-Object -Unique | ForEach-Object { Write-Host $_ }
  exit 1
}

Write-Host "✅ SI audit PASSED." -ForegroundColor Green
exit 0