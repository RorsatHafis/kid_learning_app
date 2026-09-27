# First-run setup for "Start Bookeyond Kid.bat": writes ./.env with the values Docker Compose
# needs. Run once; the start script skips it when .env exists. Nothing here is committed
# (.env is in .gitignore) and nothing leaves this computer.
$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$envFile = Join-Path $root '.env'
if (Test-Path $envFile) { exit 0 }

function Plain([Security.SecureString]$secure) {
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}

Write-Host ''
Write-Host ' First-time setup' -ForegroundColor Cyan
Write-Host ' Choose the administrator account you will sign in with. It is created in the'
Write-Host ' local database on first start and only exists on this computer.'
Write-Host ''

do {
    $email = (Read-Host ' Administrator email').Trim()
    $ok = $email -match '^[^@\s]+@[^@\s]+\.[^@\s]+$'
    if (-not $ok) { Write-Host ' That does not look like an email address. Try again.' -ForegroundColor Yellow }
} until ($ok)

do {
    $first = Plain (Read-Host ' Administrator password (at least 12 characters)' -AsSecureString)
    $second = Plain (Read-Host ' Type the password again' -AsSecureString)
    $problem = $null
    if ($first.Length -lt 12) { $problem = 'The password must be at least 12 characters.' }
    elseif ($first.Length -gt 128) { $problem = 'The password must be at most 128 characters.' }
    elseif ($first.Contains("'")) { $problem = 'Please choose a password without an apostrophe ('').' }
    elseif ($first -cne $second) { $problem = 'The two passwords are different.' }
    if ($problem) { Write-Host " $problem Try again." -ForegroundColor Yellow }
} while ($problem)

# A fresh signing key for sign-in tokens, unique to this computer.
$bytes = New-Object byte[] 48
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$jwtSecret = [Convert]::ToBase64String($bytes)

# Single quotes keep Docker Compose from reading $ or other characters in the values.
$content = @(
    '# Created by scripts/windows/first-run.ps1 - local settings for docker-compose.yml.',
    '# Keep this file private. Delete it and run the start script again to choose a new administrator',
    '# (the new one is only created in an empty database - see the owner guide).',
    "JWT_SECRET='$jwtSecret'",
    "ADMIN_BOOTSTRAP='true'",
    "ADMIN_EMAIL='$email'",
    "ADMIN_PASSWORD='$first'"
) -join "`n"
# UTF-8 without a byte-order mark, which Docker Compose would read as part of the first name.
[IO.File]::WriteAllText($envFile, $content + "`n", (New-Object Text.UTF8Encoding($false)))
Write-Host ''
Write-Host ' Saved. Starting Bookeyond Kid...' -ForegroundColor Green
exit 0
