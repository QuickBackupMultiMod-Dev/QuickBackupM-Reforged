<#
  Prepare and run .github/workflows/functional-tests.yml via nektos/act.

  Usage (from repo root or anywhere):
    pwsh ./scripts/act-ci.ps1 setup
    pwsh ./scripts/act-ci.ps1 dry-run
    pwsh ./scripts/act-ci.ps1 matrix
    pwsh ./scripts/act-ci.ps1 smoke
    pwsh ./scripts/act-ci.ps1 client
    pwsh ./scripts/act-ci.ps1 full
#>
param(
    [Parameter(Position = 0, Mandatory = $true)]
    [ValidateSet("setup", "dry-run", "matrix", "smoke", "client", "full")]
    [string]$Command
)

$ErrorActionPreference = "Stop"
Set-Location (Resolve-Path (Join-Path $PSScriptRoot ".."))

$Workflow = ".github/workflows/functional-tests.yml"
$Image = "qbm-act:22.04"
$Actions = @("checkout", "setup-java", "cache", "upload-artifact", "download-artifact")

function Assert-Tool($Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "$Name is not on PATH. Install Docker Desktop and: winget install nektos.act"
    }
}

function Ensure-ActNetwork {
    Assert-Tool docker
    docker network inspect qbm-act *>$null
    if ($LASTEXITCODE -ne 0) { docker network create qbm-act | Out-Null }
}

function Clear-ActHostReports {
    foreach ($dir in @(".act-reports", "reports")) {
        if (Test-Path $dir) {
            Remove-Item -LiteralPath $dir -Recurse -Force
        }
    }
}

function Invoke-Act {
    param([string[]]$ActArgs)
    Assert-Tool act
    Ensure-ActNetwork
    & act workflow_dispatch -W $Workflow --env ACT=true @ActArgs
    if ($LASTEXITCODE -ne 0) { throw "act exited $LASTEXITCODE" }
}

function Get-MinecraftVersion {
    $line = Get-Content -LiteralPath "gradle.properties" |
        Where-Object { $_ -match '^\s*minecraft_version\s*=' } |
        Select-Object -Last 1
    if (-not $line) { throw "minecraft_version not found in gradle.properties" }
    return ($line -split '=', 2)[1].Trim()
}

switch ($Command) {
    "setup" {
        Assert-Tool docker
        docker info | Out-Null
        docker pull catthehacker/ubuntu:act-22.04
        docker build -t $Image .github/act
        docker volume create qbm-act-gradle | Out-Null
        docker volume create qbm-act-harness-cache | Out-Null
        # User-defined network: host mode breaks Gradle daemon TCP on Docker Desktop; builtin bridge rejects aliases.
        Ensure-ActNetwork

        New-Item -ItemType Directory -Force -Path ".github/act-actions" | Out-Null
        foreach ($name in $Actions) {
            $dest = ".github/act-actions/$name"
            if (-not (Test-Path (Join-Path $dest "action.yml"))) {
                git clone --depth 1 --branch v4 "https://github.com/actions/$name.git" $dest
            }
        }

        # Seed this branch's Gradle wrapper into the Linux volume (hash dir is wrapper-defined).
        docker run --rm `
            -v qbm-act-gradle:/root/.gradle `
            -v "${PWD}:/src" `
            -w /src `
            $Image bash -lc @'
set -euo pipefail
sed -i "s/\r$//" gradlew
chmod +x gradlew
./gradlew --version
java -version
xvfb-run -a --server-args="-screen 0 1280x1024x24" echo xvfb-ok
python3 --version
'@
        Write-Host "setup ok: image $Image, volumes, local actions, gradle wrapper, xvfb, python3"
    }
    "dry-run" {
        Invoke-Act @("-e", ".github/workflows/act-event.json", "-n")
    }
    "matrix" {
        Clear-ActHostReports
        Invoke-Act @("-e", ".github/workflows/act-event.json", "--job", "resolve-matrix")
    }
    "smoke" {
        Clear-ActHostReports
        $mc = Get-MinecraftVersion
        Invoke-Act @(
            "-e", ".github/workflows/act-event.json",
            "--input", "versions=$mc",
            "--input", "loaders=fabric",
            "--input", "sides=server",
            "--input", "scenarios=boot"
        )
    }
    "client" {
        Clear-ActHostReports
        $mc = Get-MinecraftVersion
        Invoke-Act @(
            "-e", ".github/workflows/act-event-client.json",
            "--input", "versions=$mc",
            "--input", "loaders=fabric",
            "--input", "sides=client",
            "--input", "scenarios=menu"
        )
    }
    "full" {
        Clear-ActHostReports
        $eventPath = Join-Path $PWD ".act-event-full.json"
        @{ inputs = @{ versions = "1.21"; loaders = "fabric,neoforge"; sides = ""; scenarios = "" } } |
            ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $eventPath -Encoding utf8
        try {
            Invoke-Act @("-e", $eventPath)
        } finally {
            Remove-Item -LiteralPath $eventPath -ErrorAction SilentlyContinue
        }
    }
}
