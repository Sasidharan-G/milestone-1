# Diagnoses "Cognito password update failed" during registration. Run `aws login` first.
# It changes nothing except one throw-away Cognito user (diag-temp-user) that it creates and deletes.
#   powershell -ExecutionPolicy Bypass -File aws-infrastructure/diagnose-cognito.ps1

$Region  = 'ap-south-1'
$PoolId  = 'ap-south-1_voB9LWQ6I'
$EnvName = 'kadaikutty-pos-production'

function Step($title) { Write-Host ""; Write-Host "=== $title" -ForegroundColor Cyan }

Step '1. Who am I'
aws sts get-caller-identity --region $Region
if ($LASTEXITCODE -ne 0) { Write-Host 'Run "aws login" first, then re-run this script.' -ForegroundColor Red; exit 1 }
$account = (aws sts get-caller-identity --query Account --output text --region $Region)
$poolArn = "arn:aws:cognito-idp:${Region}:${account}:userpool/${PoolId}"

Step '2. Pool password policy (registration needs MinimumLength <= 6 and every Require* false)'
aws cognito-idp describe-user-pool --user-pool-id $PoolId --region $Region --query 'UserPool.Policies.PasswordPolicy'

Step '3. Custom attributes on the pool (needs user_id, company_id, role, permissions, status)'
aws cognito-idp describe-user-pool --user-pool-id $PoolId --region $Region --query 'UserPool.SchemaAttributes[?starts_with(Name, `custom:`)].Name' --output text

Step '4. Which IAM role does the Elastic Beanstalk instance run as'
$appName = (aws elasticbeanstalk describe-environments --environment-names $EnvName --region $Region --query 'Environments[0].ApplicationName' --output text)
$profile = (aws elasticbeanstalk describe-configuration-settings --application-name $appName --environment-name $EnvName --region $Region `
    --query "ConfigurationSettings[0].OptionSettings[?OptionName=='IamInstanceProfile'].Value | [0]" --output text)
Write-Host "application=$appName instanceProfile=$profile"
$roleArn = (aws iam get-instance-profile --instance-profile-name $profile --query 'InstanceProfile.Roles[0].Arn' --output text)
Write-Host "roleArn=$roleArn"

Step '5. Can that role call Cognito (expect "allowed" for all 9)'
aws iam simulate-principal-policy --policy-source-arn $roleArn --resource-arns $poolArn --region $Region `
    --action-names cognito-idp:AdminCreateUser cognito-idp:AdminGetUser cognito-idp:AdminUpdateUserAttributes cognito-idp:AdminSetUserPassword `
    cognito-idp:AdminInitiateAuth cognito-idp:AdminUserGlobalSignOut cognito-idp:AdminDeleteUser cognito-idp:AdminDisableUser cognito-idp:AdminEnableUser `
    --query 'EvaluationResults[].[EvalActionName,EvalDecision]' --output table

Step '6. Real test: create a throw-away user and give it a 6-digit password (this is the failing call)'
aws cognito-idp admin-create-user --user-pool-id $PoolId --username diag-temp-user --message-action SUPPRESS --region $Region --query 'User.Username'
aws cognito-idp admin-set-user-password --user-pool-id $PoolId --username diag-temp-user --password 123456 --permanent --region $Region
if ($LASTEXITCODE -eq 0) { Write-Host 'Pool ACCEPTS a 6-digit password -> the problem is the instance role (step 5) or a missing custom attribute (step 3).' -ForegroundColor Green }
else { Write-Host 'Pool REJECTED a 6-digit password -> fix the password policy (step 2).' -ForegroundColor Yellow }
aws cognito-idp admin-delete-user --user-pool-id $PoolId --username diag-temp-user --region $Region
