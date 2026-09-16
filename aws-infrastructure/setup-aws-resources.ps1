<#
.SYNOPSIS
  Provisions / updates the KadaiKutty POS AWS stack and prints the server environment block.

.EXAMPLE
  # 1. First run: create Cognito/DynamoDB/S3/Secrets/ECR/IAM (no App Runner yet)
  .\setup-aws-resources.ps1 -GitHubRepository "owner/milestone-1"

  # 2. After the deploy workflow pushed the first image to ECR:
  .\setup-aws-resources.ps1 -GitHubRepository "owner/milestone-1" -CreateAppRunnerService

.NOTES
  Requires AWS CLI v2 with credentials (`aws login` or `aws configure`) and CloudFormation/IAM rights.
#>
param(
  [Parameter(Mandatory = $true)] [string] $GitHubRepository,
  [string] $Environment = "production",
  [string] $ProjectName = "kadaikutty-pos",
  [string] $Region = "ap-south-1",
  [string] $AllowedOrigins = "",
  [string] $MasterSupportPhone = "",
  [string] $ImageTag = "latest",
  [switch] $CreateAppRunnerService,
  [switch] $OidcProviderExists
)

$ErrorActionPreference = "Stop"
$stackName = "$ProjectName-$Environment"
$template = Join-Path $PSScriptRoot "cloudformation-template.yaml"

Write-Host "==> Validating template"
aws cloudformation validate-template --region $Region --template-body "file://$template" | Out-Null

$createService = if ($CreateAppRunnerService) { "true" } else { "false" }
$createOidc = if ($OidcProviderExists) { "false" } else { "true" }

Write-Host "==> Deploying stack $stackName in $Region (CreateAppRunnerService=$createService)"
aws cloudformation deploy `
  --region $Region `
  --stack-name $stackName `
  --template-file $template `
  --capabilities CAPABILITY_NAMED_IAM `
  --no-fail-on-empty-changeset `
  --parameter-overrides `
    "ProjectName=$ProjectName" `
    "Environment=$Environment" `
    "GitHubRepository=$GitHubRepository" `
    "CreateGitHubOidcProvider=$createOidc" `
    "CreateAppRunnerService=$createService" `
    "ImageTag=$ImageTag" `
    "AllowedOrigins=$AllowedOrigins" `
    "MasterSupportPhone=$MasterSupportPhone"
if ($LASTEXITCODE -ne 0) { throw "CloudFormation deploy failed" }

Write-Host "==> SNS SMS defaults (transactional, spend cap 10 USD/month)"
aws sns set-sms-attributes --region $Region --attributes DefaultSMSType=Transactional,MonthlySpendLimit=10 | Out-Null

Write-Host "==> Stack outputs"
$outputsJson = aws cloudformation describe-stacks --region $Region --stack-name $stackName --query "Stacks[0].Outputs" --output json
$outputs = @{}
foreach ($o in ($outputsJson | ConvertFrom-Json)) { $outputs[$o.OutputKey] = $o.OutputValue }

$envBlock = @"
# ---- paste into server/.env to run the server against AWS from this machine ----
NODE_ENV=production
PROVIDER_MODE=aws
AWS_REGION=$($outputs.AwsRegion)
AWS_COGNITO_USER_POOL_ID=$($outputs.AwsCognitoUserPoolId)
AWS_COGNITO_CLIENT_ID=$($outputs.AwsCognitoClientId)
AWS_COGNITO_PHONE_COUNTRY_CODE=+91
AWS_DYNAMODB_TABLE=$($outputs.AwsDynamoDbTable)
AWS_S3_BACKUP_BUCKET=$($outputs.AwsS3BackupBucket)
AWS_PRESIGNED_URL_SECONDS=300
AWS_MASTER_PIN_SECRET_ARN=$($outputs.AwsMasterPinSecretArn)
# JWT_SECRET / RESET_SECRET: fetch with
#   aws secretsmanager get-secret-value --secret-id $($outputs.AppSecretsArn) --query SecretString --output text
#   aws secretsmanager get-secret-value --secret-id $($outputs.ResetSecretArn) --query SecretString --output text

# ---- GitHub repository secrets for .github/workflows/deploy-server.yml ----
AWS_REGION=$($outputs.AwsRegion)
AWS_DEPLOY_ROLE_ARN=$($outputs.GitHubDeployRoleArn)
ECR_REPOSITORY_URI=$($outputs.EcrRepositoryUri)
"@
if ($outputs.AppRunnerServiceUrl) {
  $envBlock += "`n# ---- Android: android-app/gradle.properties ----`nBACKEND_BASE_URL=$($outputs.AppRunnerServiceUrl)`n"
}
Write-Host $envBlock
$outFile = Join-Path $PSScriptRoot "stack-outputs.$Environment.env"
$envBlock | Out-File -FilePath $outFile -Encoding utf8
Write-Host "==> Saved to $outFile (gitignored)"
