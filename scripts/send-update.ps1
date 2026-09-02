[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateNotNullOrEmpty()]
    [string] $Message,
    [string] $Username = "PeakPvP Updates"
)
$ErrorActionPreference = 'Stop'
$webhookUrl = $env:PEAKPVP_UPDATE_WEBHOOK
if ([string]::IsNullOrWhiteSpace($webhookUrl)) { throw 'Set PEAKPVP_UPDATE_WEBHOOK before sending.' }
$uri = $null
if (-not [Uri]::TryCreate($webhookUrl, [UriKind]::Absolute, [ref] $uri) -or $uri.Scheme -ne 'https' -or
    $uri.Host -ne 'discord.com' -or -not $uri.AbsolutePath.StartsWith('/api/webhooks/', [StringComparison]::Ordinal)) {
    throw 'PEAKPVP_UPDATE_WEBHOOK must be an https://discord.com/api/webhooks/... URL.'
}
$items = @($Message -split '[\r\n]+' | ForEach-Object {
    ($_ -replace '^\s*[-*]\s*', '' -replace '\s{2,}', ' ').Trim()
} | Where-Object { $_ })
if ($items.Count -eq 0) { throw 'The update message cannot be empty.' }
$content = ($items | ForEach-Object { "- $_" }) -join "`n"
if ($content.Length -gt 2000) { throw 'The formatted update exceeds Discord''s 2,000-character limit.' }
$body = @{ username = $Username; content = $content } | ConvertTo-Json -Compress
Invoke-RestMethod -Method Post -Uri $uri -ContentType 'application/json' -Body $body | Out-Null
Write-Host "Sent $($items.Count) update item(s) to Discord."
