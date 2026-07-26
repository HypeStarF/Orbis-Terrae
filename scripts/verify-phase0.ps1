$ErrorActionPreference = 'Stop'
$Root = Resolve-Path (Join-Path $PSScriptRoot '..')
Set-Location $Root

java -version
& .\gradlew.bat clean phase0Check --no-configuration-cache --warning-mode=fail --stacktrace
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

git diff --check
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$status = git status --short
if ($status) {
    Write-Error "Repository changed during verification:`n$status"
}

Write-Host 'Automated Phase 0 checks passed. Complete the client/server smoke-test checklist next.'
