# 维护者：打标签并推送，触发 GitHub Actions 自动构建 axs-api JAR 并发布 Release。
param(
    [string]$Version = ""
)

$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

if (-not $Version) {
    $Version = (Select-String -Path "gradle.properties" -Pattern '^version=').Line.Split("=", 2)[1]
}

$Tag = "v$Version"

Write-Host "==> Sync gradle.properties version to $Version"
(Get-Content "gradle.properties") -replace '^version=.*', "version=$Version" | Set-Content "gradle.properties" -Encoding UTF8

Write-Host "==> Local build check"
.\gradlew.bat :axs-api:jar --no-daemon
Get-ChildItem "axs-api\build\libs\axs-api-$Version.jar"

Write-Host "==> Commit version bump (if changed)"
git add gradle.properties
$staged = git diff --cached --name-only
if ($staged) {
    git commit -m "chore: bump axs-api version to $Version"
}

Write-Host "==> Tag and push $Tag"
git tag -a $Tag -m "axs-api $Version" 2>$null
if ($LASTEXITCODE -ne 0) { git tag -f $Tag -m "axs-api $Version" }
git push origin main
git push origin $Tag

Write-Host "Done. Watch: https://github.com/xuanmomo233/ArcartXSuite-Core/actions"
