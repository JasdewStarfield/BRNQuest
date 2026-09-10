# Shared helpers deliberately use argument arrays and literal paths: log text is never shell code.
Set-StrictMode -Version Latest
function Test-InRoot([string]$Path, [string]$Root) {
    $p = [IO.Path]::GetFullPath($Path).TrimEnd('\','/')
    $r = [IO.Path]::GetFullPath($Root).TrimEnd('\','/')
    return $p.Equals($r, [StringComparison]::OrdinalIgnoreCase) -or $p.StartsWith($r + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)
}
function Get-EventPaths([string]$Line, [string]$Root) {
    # Java's structured source/target fields end at state; preserve spaces and Chinese characters.
    foreach ($m in [regex]::Matches($Line, '(?:source|target)=\{path=(.*?), state=')) {
        try { if (Test-InRoot $m.Groups[1].Value $Root) { $m.Groups[1].Value } } catch { }
    }
}
function Start-NativeTool([string]$Exe, [string[]]$Arguments, [switch]$Redirect) {
    $info = [Diagnostics.ProcessStartInfo]::new($Exe)
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.WindowStyle = 'Hidden'
    $info.RedirectStandardOutput = $Redirect.IsPresent
    $info.RedirectStandardError = $Redirect.IsPresent
    foreach ($argument in $Arguments) { $info.ArgumentList.Add($argument) }
    return [Diagnostics.Process]::Start($info)
}
function Assert-MicrosoftTool([string]$Path) {
    $signature = Get-AuthenticodeSignature -LiteralPath $Path -ErrorAction Stop
    if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'O=Microsoft Corporation(?:,|$)') {
        throw "Not a valid Microsoft-signed executable: $Path"
    }
}
function Save-IoSnapshot([string]$Root, [string]$Output, [string]$Line, [string]$HandlePath, [bool]$Elevated) {
    $started = [DateTimeOffset]::Now
    $paths = @($Root; Split-Path -Parent $Root; Get-EventPaths $Line $Root)
    $paths += @($paths | ForEach-Object { Split-Path -Parent $_ })
    $states = foreach ($path in ($paths | Where-Object { $_ } | Sort-Object -Unique)) {
        try {
            $item = Get-Item -LiteralPath $path -Force -ErrorAction Stop
            $acl = Get-Acl -LiteralPath $path -ErrorAction Stop
            @{ path=$path; attributes="$($item.Attributes)"; owner=$acl.Owner; sddl=$acl.Sddl }
        } catch { @{ path=$path; unavailable=$_.Exception.Message } }
    }
    $processes = try {
        @(Get-CimInstance Win32_Process -Filter "name='java.exe' OR name='javaw.exe'" -ErrorAction Stop | ForEach-Object {
            $owner = Invoke-CimMethod -InputObject $_ -MethodName GetOwner -ErrorAction Stop
            @{ pid=$_.ProcessId; name=$_.Name; created=$_.CreationDate; owner="$($owner.Domain)\$($owner.User)"; ownerResult=$owner.ReturnValue }
        })
    } catch { @{ unavailable=$_.Exception.Message } }
    $handleStatus = 'unavailable: administrator and Handle executable required'
    if ($Elevated -and (Test-Path -LiteralPath $HandlePath -PathType Leaf)) {
        try {
            Assert-MicrosoftTool $HandlePath
            $process = Start-NativeTool $HandlePath @('-accepteula','-nobanner','-u','-g',$Root) -Redirect
            # Preserve native bytes: Handle's console encoding can differ from PowerShell UTF-8.
            $outBytes = [IO.MemoryStream]::new(); $errBytes = [IO.MemoryStream]::new()
            $stdout = $process.StandardOutput.BaseStream.CopyToAsync($outBytes)
            $stderr = $process.StandardError.BaseStream.CopyToAsync($errBytes)
            if (-not $process.WaitForExit(10000)) { $process.Kill(); $process.WaitForExit(); $handleStatus='timeout' }
            else { $handleStatus="exit=$($process.ExitCode)" }
            $stdout.GetAwaiter().GetResult(); $stderr.GetAwaiter().GetResult()
            [IO.File]::WriteAllBytes("$Output.handles.raw.txt", $outBytes.ToArray())
            [IO.File]::WriteAllBytes("$Output.handles.stderr.raw.txt", $errBytes.ToArray())
            $outBytes.Dispose(); $errBytes.Dispose()
            $process.Dispose()
        } catch { $handleStatus="unavailable: $($_.Exception.Message)" }
    }
    # Capture identity is separate from the Java process identity. Never collect command lines/tokens.
    $data = @{ started=$started.ToString('o'); finished=[DateTimeOffset]::Now.ToString('o'); event=$Line;
        collector=[Security.Principal.WindowsIdentity]::GetCurrent().Name; elevated=$Elevated;
        paths=@($states); javaProcesses=$processes; handles=$handleStatus }
    [IO.File]::WriteAllText("$Output.json", ($data | ConvertTo-Json -Depth 8), [Text.Encoding]::UTF8)
}
Export-ModuleMember -Function Test-InRoot,Get-EventPaths,Start-NativeTool,Assert-MicrosoftTool,Save-IoSnapshot
