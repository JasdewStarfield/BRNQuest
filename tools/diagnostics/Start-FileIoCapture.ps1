#Requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$TestRoot,
    [ValidateSet('Snapshot','Capture')][string]$Mode='Snapshot',
    [string]$LogPath,
    [string]$FilterConfig,
    # Explicit acknowledgement of the exported PMC's directory filter and Drop Filtered Events.
    [switch]$ConfirmDirectoryFilter,
    [ValidateRange(1,600)][int]$Seconds=120,
    [ValidateRange(16,2048)][int]$MaxMegabytes=512,
    [string]$OutputRoot=(Join-Path $PSScriptRoot '../../build/file-io-captures'),
    [string]$ProcmonPath=(Join-Path $PSScriptRoot '../../build/file-io-tools/procmon/Procmon64.exe'),
    [string]$HandlePath=(Join-Path $PSScriptRoot '../../build/file-io-tools/handle/handle64.exe')
)
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSScriptRoot 'FileIoCapture.psm1') -Force
$TestRoot=(Resolve-Path -LiteralPath $TestRoot).ProviderPath.TrimEnd('\')
if (-not (Test-Path -LiteralPath $TestRoot -PathType Container) -or $TestRoot.Length -le 3) { throw 'Select one test directory, not a drive root.' }
$OutputRoot=[IO.Path]::GetFullPath($OutputRoot)
if (Test-InRoot $OutputRoot $TestRoot) { throw 'Output must be outside the captured directory.' }
# Reject redirected root ancestors; filters must refer to the actual directory being tested.
for ($ancestor=Get-Item -LiteralPath $TestRoot; $null -ne $ancestor; $ancestor=$ancestor.Parent) {
    if ($ancestor.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'TestRoot ancestors must not be reparse points.' }
}
$elevated=([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if ($Mode -eq 'Capture') {
    if (-not $FilterConfig -or -not $ConfirmDirectoryFilter) { throw 'Export a directory-filtered PMC first; pass -FilterConfig and -ConfirmDirectoryFilter. See docs/AUTHOR_GUIDE.md (Backup, recovery and diagnosis).' }
    $FilterConfig=(Resolve-Path -LiteralPath $FilterConfig).ProviderPath
    if (-not $elevated) { throw 'Capture requires an administrator PowerShell terminal.' }
    Assert-MicrosoftTool $ProcmonPath
    if (Get-Process -Name Procmon,Procmon64,Procmon64a -ErrorAction SilentlyContinue) { throw 'Close existing ProcMon sessions before starting this collector.' }
}
$output=Join-Path $OutputRoot ([DateTime]::Now.ToString('yyyyMMdd-HHmmss')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8))
[IO.Directory]::CreateDirectory($output) | Out-Null
$manifest=@{ started=[DateTimeOffset]::Now.ToString('o'); root=$TestRoot; mode=$Mode; seconds=$Seconds; maxMegabytes=$MaxMegabytes; collectorPid=$PID }
if ($Mode -eq 'Capture') {
    $manifest.config=$FilterConfig
    $manifest.configSha256=(Get-FileHash -LiteralPath $FilterConfig).Hash
    $manifest.procmonSha256=(Get-FileHash -LiteralPath $ProcmonPath).Hash
}
$stream=$null; $reader=$null; $procmon=$null
try {
    if ($Mode -eq 'Capture') {
        if (-not $LogPath) { $LogPath=Join-Path $TestRoot 'logs/latest.log' }
        if (Test-Path -LiteralPath $LogPath) {
            $stream=[IO.File]::Open($LogPath,'Open','Read',([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
            $stream.Seek(0,'End') | Out-Null
            $reader=[IO.StreamReader]::new($stream,[Text.Encoding]::UTF8)
        }
        # ProcMon filters are a native binary format; this script records the config hash, not a claim to parse it.
        $procmon=Start-NativeTool $ProcmonPath @('/AcceptEula','/Quiet','/Minimized','/LoadConfig',$FilterConfig,'/BackingFile',"$output/events.pml")
        if ($procmon.WaitForExit(500)) { throw "ProcMon exited at startup (exit=$($procmon.ExitCode)); no live capture was established." }
    }
    Save-IoSnapshot $TestRoot "$output/baseline" '' $HandlePath $elevated
    if ($Mode -eq 'Capture') {
        $timer=[Diagnostics.Stopwatch]::StartNew(); $seen=0; $pending=''
        while ($timer.Elapsed.TotalSeconds -lt $Seconds) {
            if ($procmon.HasExited) { throw "ProcMon exited during capture (exit=$($procmon.ExitCode))." }
            if ((Test-Path -LiteralPath "$output/events.pml") -and (Get-Item -LiteralPath "$output/events.pml").Length -ge $MaxMegabytes*1MB) { $manifest.stopReason='size limit'; break }
            if (-not $stream -and (Test-Path -LiteralPath $LogPath)) {
                # Share deletion too: diagnostics must not prevent log rotation or renames.
                $stream=[IO.File]::Open($LogPath,'Open','Read',([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
                # A newly created log belongs to this capture; read it from the beginning.
                $reader=[IO.StreamReader]::new($stream,[Text.Encoding]::UTF8)
            }
            if ($reader) {
                $pending += $reader.ReadToEnd()
                while (($newline=$pending.IndexOf("`n")) -ge 0) {
                    $line=$pending.Substring(0,$newline).TrimEnd("`r"); $pending=$pending.Substring($newline+1)
                    if ($line.Contains('[BRNQuest/FILE_IO]')) {
                        [IO.File]::AppendAllText("$output/events.log",[DateTimeOffset]::Now.ToString('o')+' '+$line+"`n",[Text.Encoding]::UTF8)
                        if (++$seen -le 8) { Save-IoSnapshot $TestRoot "$output/failure-$seen" $line $HandlePath $elevated }
                    }
                }
                # Bound memory even if a malformed log contains no newline.
                if ($pending.Length -gt 1048576) { $pending=$pending.Substring($pending.Length-1048576) }
            }
            Start-Sleep -Milliseconds 200
        }
        $manifest.events=$seen
        $manifest.log=$LogPath
    }
} finally {
    if ($reader) { $reader.Dispose() } elseif ($stream) { $stream.Dispose() }
    # Termination is only issued while our launched process still exists; never stop a pre-existing session.
    if ($procmon -and -not $procmon.HasExited) {
        $stop=Start-NativeTool $ProcmonPath @('/Terminate','/Quiet')
        if (-not $stop.WaitForExit(15000)) { Write-Warning 'ProcMon termination timed out; stop capture manually.' }
        $stop.Dispose()
        $procmon.Dispose()
    }
    $manifest.finished=[DateTimeOffset]::Now.ToString('o')
    [IO.File]::WriteAllText("$output/manifest.json",($manifest | ConvertTo-Json -Depth 5),[Text.Encoding]::UTF8)
    Write-Output "Diagnostics: $output"
}
