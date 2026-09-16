#!/usr/bin/env bash
# Provisions / updates the KadaiKutty POS AWS stack and prints the server environment block.
#
#   ./setup-aws-resources.sh owner/milestone-1                       # first run (no App Runner yet)
#   CREATE_APP_RUNNER=true ./setup-aws-resources.sh owner/milestone-1  # after first image is in ECR
#
# Env overrides: ENVIRONMENT (production), PROJECT_NAME (kadaikutty-pos), AWS_REGION (ap-south-1),
#                ALLOWED_ORIGINS, MASTER_SUPPORT_PHONE, IMAGE_TAG (latest), OIDC_PROVIDER_EXISTS (false)
set -euo pipefail

GITHUB_REPOSITORY="${1:?usage: $0 <github-owner/repo>}"
ENVIRONMENT="${ENVIRONMENT:-production}"
PROJECT_NAME="${PROJECT_NAME:-kadaikutty-pos}"
REGION="${AWS_REGION:-ap-south-1}"
CREATE_APP_RUNNER="${CREATE_APP_RUNNER:-false}"
CREATE_OIDC=$([ "${OIDC_PROVIDER_EXISTS:-false}" = "true" ] && echo false || echo true)
STACK_NAME="$PROJECT_NAME-$ENVIRONMENT"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE="$DIR/cloudformation-template.yaml"

echo "==> Validating template"
aws cloudformation validate-template --region "$REGION" --template-body "file://$TEMPLATE" >/dev/null

echo "==> Deploying stack $STACK_NAME in $REGION (CreateAppRunnerService=$CREATE_APP_RUNNER)"
aws cloudformation deploy \
  --region "$REGION" \
  --stack-name "$STACK_NAME" \
  --template-file "$TEMPLATE" \
  --capabilities CAPABILITY_NAMED_IAM \
  --no-fail-on-empty-changeset \
  --parameter-overrides \
    "ProjectName=$PROJECT_NAME" \
    "Environment=$ENVIRONMENT" \
    "GitHubRepository=$GITHUB_REPOSITORY" \
    "CreateGitHubOidcProvider=$CREATE_OIDC" \
    "CreateAppRunnerService=$CREATE_APP_RUNNER" \
    "ImageTag=${IMAGE_TAG:-latest}" \
    "AllowedOrigins=${ALLOWED_ORIGINS:-}" \
    "MasterSupportPhone=${MASTER_SUPPORT_PHONE:-}"

echo "==> SNS SMS defaults (transactional, spend cap 10 USD/month)"
aws sns set-sms-attributes --region "$REGION" --attributes DefaultSMSType=Transactional,MonthlySpendLimit=10 >/dev/null

out() { aws cloudformation describe-stacks --region "$REGION" --stack-name "$STACK_NAME" \
          --query "Stacks[0].Outputs[?OutputKey=='$1'].OutputValue" --output text; }

OUT_FILE="$DIR/stack-outputs.$ENVIRONMENT.env"
{
cat <<EOF
# ---- paste into server/.env to run the server against AWS from this machine ----
NODE_ENV=production
PROVIDER_MODE=aws
AWS_REGION=$(out AwsRegion)
AWS_COGNITO_USER_POOL_ID=$(out AwsCognitoUserPoolId)
AWS_COGNITO_CLIENT_ID=$(out AwsCognitoClientId)
AWS_COGNITO_PHONE_COUNTRY_CODE=+91
AWS_DYNAMODB_TABLE=$(out AwsDynamoDbTable)
AWS_S3_BACKUP_BUCKET=$(out AwsS3BackupBucket)
AWS_PRESIGNED_URL_SECONDS=300
AWS_MASTER_PIN_SECRET_ARN=$(out AwsMasterPinSecretArn)
# JWT_SECRET / RESET_SECRET: fetch with
#   aws secretsmanager get-secret-value --secret-id $(out AppSecretsArn) --query SecretString --output text
#   aws secretsmanager get-secret-value --secret-id $(out ResetSecretArn) --query SecretString --output text

# ---- GitHub repository secrets for .github/workflows/deploy-server.yml ----
AWS_REGION=$(out AwsRegion)
AWS_DEPLOY_ROLE_ARN=$(out GitHubDeployRoleArn)
ECR_REPOSITORY_URI=$(out EcrRepositoryUri)
EOF
if [ "$CREATE_APP_RUNNER" = "true" ]; then
  echo
  echo "# ---- Android: android-app/gradle.properties ----"
  echo "BACKEND_BASE_URL=$(out AppRunnerServiceUrl)"
fi
} | tee "$OUT_FILE"
echo "==> Saved to $OUT_FILE (gitignored)"
