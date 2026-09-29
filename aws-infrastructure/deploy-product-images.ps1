# Deploys everything the new product-photo feature needs on the live Mumbai backend:
#   1. CloudFormation stack update -> creates ProductImagesBucket (public-read photos bucket)
#      and grants the app's IAM role s3:PutObject/GetObject/etc. on it. Every other existing
#      stack parameter is read back and passed through unchanged (same pattern as
#      publish-app-release.ps1), so this cannot accidentally flip CreateAppRunnerService on.
#   2. Sets AWS_S3_PRODUCT_IMAGES_BUCKET on the live Elastic Beanstalk environment to that
#      bucket's name.
#   3. Builds a fresh server bundle (server/dist + package.json + Procfile, via
#      package-elastic-beanstalk.ps1, which also runs `npm ci` and the test suite first) and
#      deploys it as a new EB application version, the same way production-hardening.ps1 does.
#
# Run in PowerShell after `aws login`:
#   powershell -ExecutionPolicy Bypass -File aws-infrastructure\deploy-product-images.ps1 -VersionLabel kadaikutty-pos-server-v29
#
# Steps 2-3 restart the app for ~30-60s each. Safe to re-run end to end if anything fails partway.

param(
    [Parameter(Mandatory = $true)][string]$VersionLabel,
    [string]$Region = 'ap-south-1',
    [string]$StackName = 'kadaikutty-pos-production',
    [string]$EbApp = 'kadaikutty-pos-backend',
    [string]$EbEnv = 'kadaikutty-pos-production',
    [string]$EbBucket = 'elasticbeanstalk-ap-south-1-622952747916',
    [string]$HealthUrl = 'https://dr88bwgl8h.execute-api.ap-south-1.amazonaws.com/health'
)

$ErrorActionPreference = 'Stop'
function Step($t) { Write-Host ""; Write-Host "=== $t" -ForegroundColor Cyan }
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

Step '1. Deploy CloudFormation stack update (adds ProductImagesBucket + IAM policy)'
$paramsJson = aws cloudformation describe-stacks --stack-name $StackName --region $Region --query 'Stacks[0].Parameters' --output json
$params = $paramsJson | ConvertFrom-Json
$overrides = ($params | ForEach-Object { "$($_.ParameterKey)=$($_.ParameterValue)" }) -join ' '
Write-Host "  parameters carried over: $($params.Count)"
aws cloudformation deploy `
    --stack-name $StackName `
    --template-file (Join-Path $PSScriptRoot 'cloudformation-template.yaml') `
    --region $Region `
    --capabilities CAPABILITY_NAMED_IAM `
    --parameter-overrides $overrides.Split(' ') `
    --no-fail-on-empty-changeset
if ($LASTEXITCODE -ne 0) { throw 'Stack update failed - see the error above.' }

$bucket = aws cloudformation describe-stacks --stack-name $StackName --region $Region `
    --query "Stacks[0].Outputs[?OutputKey=='AwsS3ProductImagesBucket'].OutputValue | [0]" --output text
if ([string]::IsNullOrWhiteSpace($bucket) -or $bucket -eq 'None') { throw 'ProductImagesBucket output not found - did the stack update actually apply?' }
Write-Host "  bucket: $bucket"

Step '2. Set AWS_S3_PRODUCT_IMAGES_BUCKET on the live Elastic Beanstalk environment'
aws elasticbeanstalk update-environment --environment-name $EbEnv --region $Region --option-settings `
    "Namespace=aws:elasticbeanstalk:application:environment,OptionName=AWS_S3_PRODUCT_IMAGES_BUCKET,Value=$bucket" `
    --query 'Status' --output text
Wait-Ready

Step '3. Build and deploy the new server bundle (productRoutes.ts + S3 product-image support)'
$bundlePath = Join-Path $PSScriptRoot "artifacts/$VersionLabel.zip"
& (Join-Path $PSScriptRoot 'package-elastic-beanstalk.ps1') -OutputPath $bundlePath
aws s3 cp $bundlePath "s3://$EbBucket/$EbApp/$VersionLabel.zip" --region $Region
aws elasticbeanstalk create-application-version --application-name $EbApp --version-label $VersionLabel `
    --source-bundle "S3Bucket=$EbBucket,S3Key=$EbApp/$VersionLabel.zip" --region $Region --query 'ApplicationVersion.VersionLabel' --output text
aws elasticbeanstalk update-environment --environment-name $EbEnv --version-label $VersionLabel --region $Region --query 'Status' --output text
Wait-Ready

Step 'Done - verify'
Write-Host '  /health ->' (curl.exe -s $HealthUrl)
aws elasticbeanstalk describe-environments --environment-names $EbEnv --region $Region --query 'Environments[0].[VersionLabel,Status,Health]' --output text
Write-Host ''
Write-Host 'Now rebuild the Android release APK (BuildConfig points at the same backend URL, no app-side URL change needed) so it can pick up product photos from other devices.' -ForegroundColor Yellow
