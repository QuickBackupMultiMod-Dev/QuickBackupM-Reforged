<#
  Finds the lowest Minecraft version the CURRENT code supports, without editing
  any project file. It overrides the version properties from gradle.properties
  via Gradle -P flags and builds both loaders for each candidate, newest -> oldest.
  The last version that builds successfully is the support floor.

  Usage:
    pwsh ./scripts/find-min-mc.ps1                # full build per version (slow, most accurate for compile+AW)
    pwsh ./scripts/find-min-mc.ps1 -Fast          # compileJava + validateAccessWidener only (faster pre-screen)

  NOTE: each MC version needs matching loader / fabric-api / neoforge versions.
    fabric-api : https://modrinth.com/mod/fabric-api/versions
    neoforge   : https://projects.neoforged.net/neoforged/neoforge
    loader     : https://fabricmc.net/develop/
#>
param([switch]$Fast)

$ErrorActionPreference = "Continue"

# ---- Edit this table. Order NEWEST -> OLDEST. ------------------------------
$matrix = @(
    [pscustomobject]@{ mc = "1.21.5"; loader = "0.16.10"; api = "0.123.0+1.21.5"; neoforge = "21.5.65-beta" }
    # [pscustomobject]@{ mc = "...";  loader = "...";    api = "...";         neoforge = "..." }
)
# ---------------------------------------------------------------------------

if ($Fast) {
    $tasks = @(
        ":quickbakcupmulti_reforged-fabric:compileJava",
        ":quickbakcupmulti_reforged-fabric:validateAccessWidener",
        ":quickbakcupmulti_reforged-neoforge:compileJava",
        ":quickbakcupmulti_reforged-neoforge:validateAccessWidener"
    )
} else {
    $tasks = @(
        ":quickbakcupmulti_reforged-fabric:build",
        ":quickbakcupmulti_reforged-neoforge:build"
    )
}

$gradlew = Join-Path $PSScriptRoot "..\gradlew.bat"
$lastGood = $null

foreach ($row in $matrix) {
    Write-Host "=== Building against MC $($row.mc) ===" -ForegroundColor Cyan
    & $gradlew @tasks `
        "-Pminecraft_version=$($row.mc)" `
        "-Pfabric_loader_version=$($row.loader)" `
        "-Pfabric_api_version=$($row.api)" `
        "-Pneoforge_version=$($row.neoforge)" `
        --console=plain
    if ($LASTEXITCODE -eq 0) {
        Write-Host "MC $($row.mc): OK`n" -ForegroundColor Green
        $lastGood = $row.mc
    } else {
        Write-Host "MC $($row.mc): FAILED -- floor is the version above this one.`n" -ForegroundColor Red
        break
    }
}

Write-Host "Lowest MC version that builds: $($lastGood ?? '(none built)')" -ForegroundColor Yellow
