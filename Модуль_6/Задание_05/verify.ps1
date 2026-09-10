param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Tasks)
$ErrorActionPreference = 'Stop'
if (-not $Tasks -or $Tasks.Count -eq 0) { $Tasks = @('build') }
Push-Location $PSScriptRoot
try {
    & "$PSScriptRoot\gradlew.bat" -I "$PSScriptRoot\build-support\windows-output.gradle" @Tasks
    if ($LASTEXITCODE -ne 0) { throw "Gradle finished with exit code $LASTEXITCODE" }
} finally { Pop-Location }
