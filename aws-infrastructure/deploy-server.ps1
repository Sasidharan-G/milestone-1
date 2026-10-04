# Deploys the current server code to the live Mumbai Elastic Beanstalk environment as a new
# application version. Use it for server-only changes: no CloudFormation or environment-variable
# change is needed (for example the shop-logo cloud sync, which keeps the logo in the existing
# private backup bucket under the shop's own prefix).
#
# It builds the bundle first (npm ci + the full server test suite, via package-elastic-beanstalk.ps1),
# uploads it, switches the environment to it, waits until it is Ready and then checks that the
# server is really the new one. The app restarts for about 30-60 seconds.
#
# Run in PowerShell after `aws login`:
#   powershell -ExecutionPolicy Bypass -File aws-infrastructure\deploy-server.ps1 -VersionLabel kadaikutty-pos-server-v20261003-1630
#
# The label must be new (Elastic Beanstalk refuses a label it already has). Safe to re-run with a
# fresh label if anything fails partway: nothing is switched until the bundle is uploaded.

param(
    [Parameter(Mandatory = $true)][string]$VersionLabel,
    [string]$Region = 'ap-south-1',
    [string]$EbApp = 'kadaikutty-pos-backend',
    [string]$EbEnv = 'kadaikutty-pos-production',
    [string]$EbBucket = 'elasticbeanstalk-ap-south-1-622952747916',
    [string]$HealthUrl = 'https://dr88bwgl8h.execute-api.ap-south-1.amazonaws.com/health'
)

$ErrorActionPreference = 'Stop'
function Step($t) { Write-Host ""; Write-Host "=== $t" -ForegroundColor Cyan }
# aws.exe failures do not stop a PowerShell script by themselves, so the calls that must succeed go
# through here. (Not named "Aws": command names are case-insensitive and a function would call itself.)
function Invoke-Aws {
    & aws.exe @args
    if ($LASTEXITCODE -ne 0) { throw "aws $($args -join ' ') failed with exit code $LASTEXITCODE" }
}
function Wait-Ready {
    for ($i = 0; $i -lt 40; $i++) {
        $s = aws elasticbeanstalk describe-environments --environment-names $EbEnv --region $Region --query 'Environments[0].[Status,Health]' --output text
        Write-Host "  $s"
        if ($s -match '^Ready') { return }
        Start-Sleep -Seconds 15
    }
    throw 'Environment did not become Ready in 10 minutes'
}

Step '0. Who am I'
aws sts get-caller-identity --region $Region
if ($LASTEXITCODE -ne 0) { Write-Host 'Run "aws login" first, then re-run this script.' -ForegroundColor Red; exit 1 }

Step '1. What is live now'
Invoke-Aws elasticbeanstalk describe-environments --environment-names $EbEnv --region $Region --query 'Environments[0].[VersionLabel,Status,Health]' --output text
Wait-Ready

Step "2. Build the bundle ($VersionLabel): npm ci + all server tests"
$bundlePath = Join-Path $PSScriptRoot "artifacts/$VersionLabel.zip"
& (Join-Path $PSScriptRoot 'package-elastic-beanstalk.ps1') -OutputPath $bundlePath
if (-not (Test-Path -LiteralPath $bundlePath)) { throw "The bundle was not created: $bundlePath" }

Step '3. Upload and deploy'
Invoke-Aws s3 cp $bundlePath "s3://$EbBucket/$EbApp/$VersionLabel.zip" --region $Region
Invoke-Aws elasticbeanstalk create-application-version --application-name $EbApp --version-label $VersionLabel `
    --source-bundle "S3Bucket=$EbBucket,S3Key=$EbApp/$VersionLabel.zip" --region $Region --query 'ApplicationVersion.VersionLabel' --output text
Invoke-Aws elasticbeanstalk update-environment --environment-name $EbEnv --version-label $VersionLabel --region $Region --query 'Status' --output text
Wait-Ready

Step '4. Verify'
$base = $HealthUrl -replace '/health$', ''
$health = curl.exe -s $HealthUrl
Write-Host "  /health -> $health"
if ($health -notmatch '"mode"\s*:\s*"aws"') { Write-Host '  WARNING: /health does not report mode "aws".' -ForegroundColor Yellow }
# The shop-logo route answers 401 (needs a sign-in) on the new server and 404 on one that does not have it.
$logoRoute = curl.exe -s -o NUL -w '%{http_code}' "$base/api/v1/account/shop-logo"
Write-Host "  GET /api/v1/account/shop-logo without a sign-in -> $logoRoute (401 = new server is live)"
if ($logoRoute -ne '401') { Write-Host '  WARNING: expected 401 from the shop-logo route.' -ForegroundColor Yellow }
Invoke-Aws elasticbeanstalk describe-environments --environment-names $EbEnv --region $Region --query 'Environments[0].[VersionLabel,Status,Health]' --output text
Write-Host ''
Write-Host 'Done. Phones running the new app now keep the shop logo in the cloud: it comes back after a reinstall and shows on every device of the shop.' -ForegroundColor Green
