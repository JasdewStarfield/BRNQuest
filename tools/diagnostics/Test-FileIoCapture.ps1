#Requires -Version 7.0
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSScriptRoot 'FileIoCapture.psm1') -Force
function Assert($Value,[string]$Message) { if (-not $Value) { throw $Message } }
Assert (Test-InRoot 'C:\测试 空格\child' 'C:\测试 空格') 'Unicode child rejected'
Assert (Test-InRoot 'c:\TEST\child' 'C:\test') 'Case-insensitive root rejected'
Assert (-not (Test-InRoot 'C:\test-other\child' 'C:\test')) 'Sibling prefix accepted'
Assert (-not (Test-InRoot 'C:\test\..\outside' 'C:\test')) 'Traversal accepted'
$line='source={path=C:\测试 空格\a, state=[]} target={path=C:\outside, state=[]}'
$paths=@(Get-EventPaths $line 'C:\测试 空格')
Assert ($paths.Count -eq 1 -and $paths[0] -eq 'C:\测试 空格\a') 'Event parser escaped root or changed spaces'
$fixture=Join-Path $PSScriptRoot '../../build/file-io-script-test'
[IO.Directory]::CreateDirectory($fixture) | Out-Null
$fixture=[IO.Path]::GetFullPath($fixture)
Save-IoSnapshot $fixture "$fixture/snapshot" "source={path=$fixture\missing, state=[]}" '' $false
$snapshot=Get-Content -LiteralPath "$fixture/snapshot.json" -Encoding UTF8 -Raw | ConvertFrom-Json
Assert ($snapshot.paths | Where-Object { $_.path -eq "$fixture\missing" -and $_.unavailable }) 'Missing path evidence absent'
Assert ($snapshot.handles -like 'unavailable:*') 'Non-admin limitation missing'
# The argument contains shell metacharacters: ProcessStartInfo must pass them literally.
$exe=(Get-Process -Id $PID).Path
# Use -File and explicit output encoding for an unambiguous argv test.
$script=Join-Path $fixture 'argv.ps1'
[IO.File]::WriteAllText($script,'param([string]$Value) [Console]::OutputEncoding=[Text.Encoding]::UTF8; [Console]::Write($Value)',[Text.Encoding]::UTF8)
$process=Start-NativeTool $exe @('-NoProfile','-File',$script,'中文 ; $value space') -Redirect
$text=$process.StandardOutput.ReadToEnd(); $process.WaitForExit(); $process.Dispose()
Assert ($text -eq '中文 ; $value space') 'Native arguments were reinterpreted'
$rejected=$false
try { & (Join-Path $PSScriptRoot 'Start-FileIoCapture.ps1') -TestRoot $fixture -Mode Capture } catch { $rejected=$_.Exception.Message -like 'Export a directory-filtered*' }
Assert $rejected 'Capture accepted absent PMC'
Write-Output 'PASS: containment, Unicode paths, event parser, snapshot, literal argv, missing filter guard.'
