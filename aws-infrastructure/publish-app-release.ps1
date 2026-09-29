# Publishes a build to the public app-releases S3 bucket (created/updated via CloudFormation)
# and prints a direct download link. Safe to re-run for every new APK.
#
#   powershell -ExecutionPolicy Bypass -File aws-infrastructure\publish-app-release.ps1 `
#       -ApkPath "apk-releases\kadaikutty-pos-v32-release.apk" -VersionLabel "v32"
#
# First run also creates the AppReleasesBucket (via a CloudFormation stack update on
# kadaikutty-pos-production) — every existing resource's parameters are read back from the live
# stack and passed through unchanged, so nothing else in the stack is touched.

param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [Parameter(Mandatory = $true)][string]$VersionLabel,
    [string]$Region = 'ap-south-1',
    [string]$StackName = 'kadaikutty-pos-production'
)

$ErrorActionPreference = 'Stop'
function Step($t) { Write-Host ""; Write-Host "=== $t" -ForegroundColor Cyan }

if (-not (Test-Path -LiteralPath $ApkPath)) { throw "APK not found: $ApkPath" }

Step '1. Who am I'
aws sts get-caller-identity --region $Region
if ($LASTEXITCODE -ne 0) { Write-Host 'Run "aws login" first, then re-run this script.' -ForegroundColor Red; exit 1 }

Step '2. Read the live stack''s current parameters (so the update changes only the new bucket)'
$paramsJson = aws cloudformation describe-stacks --stack-name $StackName --region $Region --query 'Stacks[0].Parameters' --output json
$params = $paramsJson | ConvertFrom-Json
$overrides = ($params | ForEach-Object { "$($_.ParameterKey)=$($_.ParameterValue)" }) -join ' '
Write-Host "  parameters carried over: $($params.Count)"

Step '3. Update the stack (adds AppReleasesBucket + policy; everything else stays as it is)'
aws cloudformation deploy `
    --stack-name $StackName `
    --template-file (Join-Path $PSScriptRoot 'cloudformation-template.yaml') `
    --region $Region `
    --capabilities CAPABILITY_NAMED_IAM `
    --parameter-overrides $overrides.Split(' ') `
    --no-fail-on-empty-changeset
if ($LASTEXITCODE -ne 0) { throw 'Stack update failed - see the error above.' }

$bucket = aws cloudformation describe-stacks --stack-name $StackName --region $Region --query "Stacks[0].Outputs[?OutputKey=='AppReleasesBucketName'].OutputValue | [0]" --output text
$baseUrl = aws cloudformation describe-stacks --stack-name $StackName --region $Region --query "Stacks[0].Outputs[?OutputKey=='AppReleasesBaseUrl'].OutputValue | [0]" --output text
Write-Host "  bucket: $bucket"

Step '4. Upload this build (versioned) and refresh the always-latest link'
$fileName = "kadaikutty-pos-$VersionLabel.apk"
aws s3 cp $ApkPath "s3://$bucket/releases/$fileName" --region $Region `
    --content-type 'application/vnd.android.package-archive' --content-disposition "attachment; filename=$fileName"
aws s3 cp $ApkPath "s3://$bucket/kadaikutty-pos-latest.apk" --region $Region `
    --content-type 'application/vnd.android.package-archive' --content-disposition 'attachment; filename=kadaikutty-pos-latest.apk'

Step 'Done — links for the client'
Write-Host "This build ($VersionLabel): $baseUrl/releases/$fileName" -ForegroundColor Green
Write-Host "Always-latest (re-upload overwrites it, same link every time): $baseUrl/kadaikutty-pos-latest.apk" -ForegroundColor Green
Write-Host ""
Write-Host 'The client''s phone will block the install until "Install unknown apps" is allowed for their browser once.' -ForegroundColor Yellow
