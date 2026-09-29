# Rotates secrets that were exposed in plaintext during chat-based support sessions.
#
# RESET_SECRET is always rotated (a fresh random value is generated inside this script and never
# printed). MSG91 keys are rotated only if you pass the new values you got from the MSG91
# dashboard (this script cannot fetch them - MSG91 is not an AWS service).
#
# Usage - rotate RESET_SECRET only:
#   powershell -ExecutionPolicy Bypass -File aws-infrastructure\rotate-secrets.ps1
#
# Usage - also rotate MSG91 (after regenerating the Auth Key on control.msg91.com):
#   powershell -ExecutionPolicy Bypass -File aws-infrastructure\rotate-secrets.ps1 `
#       -Msg91AuthKey "NEW_AUTH_KEY" -Msg91TokenAuth "NEW_TOKEN_AUTH"
#
# Note: the Master Control PIN is NOT rotated here. Rotate it from inside the app itself
# ("Forgot Master PIN" -> OTP to +919789418144 -> set new PIN) - that is the same code path
# and updates the same Secrets Manager secret this script would otherwise have to touch blind.

param(
    [switch]$SkipResetSecret,
    [string]$Msg91AuthKey,
    [string]$Msg91TokenAuth,
    [string]$Msg91WidgetId,
    [string]$Region = 'ap-south-1',
    [string]$AppName = 'kadaikutty-pos-backend',
    [string]$EnvName = 'kadaikutty-pos-production'
)

$ErrorActionPreference = 'Stop'
function Step($t) { Write-Host ""; Write-Host "=== $t" -ForegroundColor Cyan }

Step '1. Who am I'
aws sts get-caller-identity --region $Region
if ($LASTEXITCODE -ne 0) { Write-Host 'Run "aws login" first, then re-run this script.' -ForegroundColor Red; exit 1 }

$settings = @()

if (-not $SkipResetSecret) {
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $newSecret = -join ($bytes | ForEach-Object { $_.ToString('x2') })
    Write-Host "  New RESET_SECRET generated (64 hex chars, not printed)."
    $settings += "Namespace=aws:elasticbeanstalk:application:environment,OptionName=RESET_SECRET,Value=$newSecret"
}

if ($Msg91AuthKey)   { $settings += "Namespace=aws:elasticbeanstalk:application:environment,OptionName=MSG91_AUTH_KEY,Value=$Msg91AuthKey" }
if ($Msg91TokenAuth) { $settings += "Namespace=aws:elasticbeanstalk:application:environment,OptionName=MSG91_TOKEN_AUTH,Value=$Msg91TokenAuth" }
if ($Msg91WidgetId)  { $settings += "Namespace=aws:elasticbeanstalk:application:environment,OptionName=MSG91_WIDGET_ID,Value=$Msg91WidgetId" }

if ($settings.Count -eq 0) {
    Write-Host "Nothing to rotate (RESET_SECRET was skipped and no MSG91 values were passed)." -ForegroundColor Yellow
    exit 0
}

Step '2. Applying to the live EB environment (only the listed vars change; everything else is left as-is)'
aws elasticbeanstalk update-environment --region $Region --application-name $AppName --environment-name $EnvName --option-settings $settings
if ($LASTEXITCODE -ne 0) { throw 'Update failed - see the error above.' }

Step '3. Waiting for the environment to finish applying (Ready / Green)'
do {
    Start-Sleep -Seconds 15
    $statusLine = aws elasticbeanstalk describe-environments --region $Region --application-name $AppName --environment-names $EnvName --query 'Environments[0].[Status,Health]' --output text
    Write-Host "  status: $statusLine"
} while ($statusLine -notmatch '^Ready')

Step 'Done'
Write-Host 'Rotated successfully. Existing logins are unaffected (RESET_SECRET only signs password-reset tokens; login uses JWT_SECRET, which was not touched).' -ForegroundColor Green
if ($Msg91AuthKey -or $Msg91TokenAuth) {
    Write-Host 'Send a test OTP from the app now to confirm the new MSG91 key works before you delete/deactivate the old one on control.msg91.com.' -ForegroundColor Yellow
}
