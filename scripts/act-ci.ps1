<#
  Prepare and run .github/workflows/functional-tests.yml via nektos/act.

  Usage (from repo root or anywhere):
    pwsh ./scripts/act-ci.ps1 setup
    pwsh ./scripts/act-ci.ps1 dry-run
    pwsh ./scripts/act-ci.ps1 matrix
    pwsh ./scripts/act-ci.ps1 smoke
    pwsh ./scripts/act-ci.ps1 client
#>
param(
    [Parameter(Position = 0, Mandatory = $true)]
    [ValidateSet("setup", "dry-run", "matrix", "smoke", "client")]
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

function Invoke-Act {
    param([string[]]$ActArgs)
    Assert-Tool act
    & act workflow_dispatch -W $Workflow @ActArgs
    if ($LASTEXITCODE -ne 0) { throw "act exited $LASTEXITCODE" }
}

switch ($Command) {
    "setup" {
        Assert-Tool docker
        docker info | Out-Null
        docker pull catthehacker/ubuntu:act-22.04
        docker build -t $Image .github/act
        docker volume create qbm-act-gradle | Out-Null
        docker volume create qbm-act-harness-cache | Out-Null

        New-Item -ItemType Directory -Force -Path ".github/act-actions" | Out-Null
        foreach ($name in $Actions) {
            $dest = ".github/act-actions/$name"
            if (-not (Test-Path (Join-Path $dest "action.yml"))) {
                git clone --depth 1 --branch v4 "https://github.com/actions/$name.git" $dest
            }
        }

        # Seed the OS-independent Gradle wrapper zip into the Linux volume.
        # Hash directory name must match Gradle 8.14.5's wrapper (gradle-wrapper.properties).
        docker run --rm -v qbm-act-gradle:/root/.gradle $Image bash -lc @'
set -euo pipefail
dest=/root/.gradle/wrapper/dists/gradle-8.14.5-bin/690y85m0j9nfaub7xoiayko8a
mkdir -p "$dest"
if [ ! -f "$dest/gradle-8.14.5-bin.zip.ok" ]; then
  curl -fsSL https://services.gradle.org/distributions/gradle-8.14.5-bin.zip -o "$dest/gradle-8.14.5-bin.zip"
  unzip -q -o "$dest/gradle-8.14.5-bin.zip" -d "$dest"
  touch "$dest/gradle-8.14.5-bin.zip.ok"
fi
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
        Invoke-Act @("-e", ".github/workflows/act-event.json", "--job", "resolve-matrix")
    }
    "smoke" {
        Invoke-Act @(
            "-e", ".github/workflows/act-event.json",
            "--input", "versions=1.21",
            "--input", "loaders=fabric",
            "--input", "sides=server",
            "--input", "scenarios=boot"
        )
    }
    "client" {
        Invoke-Act @(
            "-e", ".github/workflows/act-event-client.json",
            "--input", "versions=1.21",
            "--input", "loaders=fabric",
            "--input", "sides=client",
            "--input", "scenarios=menu"
        )
    }
}
