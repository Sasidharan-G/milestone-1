# AWS Setup Guide

_Last verified: 2026-09-17, against the actual live account (638120274634, ap-southeast-2)._

This describes what is **actually deployed today**, not the original App-Runner-based plan in
`aws-infrastructure/cloudformation-template.yaml`. Two AWS services that plan depends on turned out
to be unavailable on this account, and the deployment moved to a working alternative instead of
waiting on AWS support tickets. Read §0 before touching infrastructure — it explains why App Runner
and CloudFront are not used here.

## 0. Two hard blockers found on this account (not IAM permission problems)

- **AWS App Runner is denied by an AWS Organizations Service Control Policy** on this account
  (`arn:aws:organizations::715621341399:policy/o-byjoxcc48x/service_control_policy/p-lxkw5x7p`),
  regardless of the IAM user's own permissions (this account's `kadakutty-server` IAM user has
  `AdministratorAccess`, and App Runner is still denied — the deny is enforced above IAM, at the AWS
  Organization level). This makes `aws-infrastructure/cloudformation-template.yaml`'s App Runner
  service and `.github/workflows/deploy-server.yml`'s ECR→App Runner pipeline **non-functional on
  this account**. Only your organization's management-account admin can lift an SCP; do not spend
  time retrying App Runner calls expecting a different result.
- **CloudFront requires AWS account verification** before it will create any resource
  (`AccessDenied: Your account must be verified before you can add new CloudFront resources.`) — a
  fraud-prevention gate AWS puts on some accounts, lifted by opening an AWS Support case. Until that
  is resolved, CloudFront is not an option for HTTPS either.

Given both of the "obvious" AWS paths to a public HTTPS endpoint were closed, the backend runs on
**Elastic Beanstalk** (already working, not blocked) fronted by an **API Gateway HTTP API** purely to
get free automatic HTTPS without owning a custom domain (see §3). Neither of these needs the blocked
services. If your organization later lifts the App Runner SCP or verifies the account for CloudFront,
you can migrate — the app itself has no dependency on how it's hosted.

## 1. What's actually running

| Resource | Value |
|---|---|
| Region | `ap-southeast-2` (Sydney) — the CloudFormation template's default of `ap-south-1` was **not** used; everything below is Sydney |
| AWS account | `638120274634` |
| Elastic Beanstalk application | `kadaikutty-pos-backend` |
| Elastic Beanstalk environment | `kadaikutty-pos-production` (single instance, `MaxSize=1`/`MinSize=1`, `AllAtOnce` deploys — a deploy causes ~30-60s of downtime, there is no second instance to roll onto) |
| EB origin URL (HTTP only, do not use directly from the app) | `http://kadaikutty-pos-api.ap-southeast-2.elasticbeanstalk.com` |
| **Public HTTPS URL (use this in the app)** | `https://u3bmxkaw25.execute-api.ap-southeast-2.amazonaws.com/` |
| Cognito User Pool | `ap-southeast-2_v18RtXdVm` (client `5bqj6ljq7djovji0r98abb1la4`) |
| DynamoDB table | `kadaikutty-pos-production` |
| S3 backup bucket | `kadakutty-pos-backups-638120274634-ap-southeast-2-production` |
| SMS Provider | `SMS_PROVIDER=msg91` (default). Configured via EB environment properties: `MSG91_WIDGET_ID`, `MSG91_TOKEN_AUTH`, `MSG91_AUTH_KEY`. Fallback credentials were removed (R18 fix); missing keys fail fast at startup. |
| Secrets Manager | `/kadakutty-pos/production/{master-pin,jwt-secret,reset-secret}` — `JWT_SECRET`/`RESET_SECRET` are wired into the EB environment via EB's **environment-secrets** feature (`aws:elasticbeanstalk:application:environmentsecrets`), not plain env vars; `AWS_MASTER_PIN_SECRET_ARN` env var points the app at the master-pin secret directly |
| CloudFormation stack that created the above (Cognito/DynamoDB/S3/Secrets/IAM role) | `kadakutty-pos-production` — note the stack name has a typo ("kadakutty", missing the second "i") baked into every resource name it generates; the DynamoDB table name output was overridden to the correct spelling, everything else kept the typo. Cosmetic, but don't "fix" the typo by renaming resources — that would recreate them empty. |
| Orphaned/unused | A second DynamoDB table `kadakutty-pos-production` (typo spelling, 1 stray item) exists from an earlier manual attempt and is **not** referenced by anything live — safe to delete once you've confirmed you don't need it, or safe to ignore (PAY_PER_REQUEST tables cost ~nothing idle) |

## 2. A real bug this setup already had: trailing spaces in env var names

As found 2026-09-17: three of the EB environment variables were set with a **trailing space baked
into the variable name itself** (`'AWS_REGION   '`, `'PROVIDER_MODE '`, `'AWS_COGNITO_CLIENT_ID '` —
almost certainly from copy-pasting into the AWS Console's environment-properties table). Since
`process.env.AWS_REGION` in Node.js is an exact-string lookup, `process.env.AWS_REGION` was
**undefined**, and `process.env.PROVIDER_MODE` was too — which made `providerRegistry.ts` silently
fall back to `PROVIDER_MODE=local`. **The live server had been running in local (ephemeral, per-instance
JSON file) mode the whole time, not AWS mode** — nothing was actually reaching DynamoDB/S3/Cognito
despite the environment being fully provisioned for it. This was fixed by removing the bad keys and
re-adding them without the trailing space (`aws elasticbeanstalk update-environment` with matching
`--options-to-remove`/`--option-settings`, done as two separate calls — doing add+remove of
similarly-named keys in one call silently dropped the adds). `GET /health` now reports
`{"mode":"aws", ...}`, confirming the fix. **Always check this field after any future deploy or
environment-variable change.**

## 3. HTTPS without a custom domain (API Gateway HTTP API)

The EB load balancer serves plain HTTP only — getting a browser/OS-trusted TLS certificate onto it
needs either a custom domain (ACM certificate + Route 53, or your own DNS) or CloudFront (blocked,
see §0). Android's release build variant refuses non-HTTPS URLs by design
(`BackendApiClient.request()`), so something has to terminate TLS in front of the EB origin.

The fix used here: an API Gateway **HTTP API** (not REST API — cheaper, simpler) with a single
`HTTP_PROXY` integration forwarding every path to the EB origin. API Gateway's own
`*.execute-api.<region>.amazonaws.com` domain always has a valid, publicly-trusted certificate with
no setup required — this is the same reason CloudFront's `*.cloudfront.net` domain works without a
custom domain, but API Gateway isn't behind the account-verification gate.

How it was created (for reference / rebuilding if needed):
```bash
API_ID=$(aws apigatewayv2 create-api --name kadaikutty-pos-https --protocol-type HTTP \
  --query 'ApiId' --output text)
INTEGRATION_ID=$(aws apigatewayv2 create-integration --api-id "$API_ID" \
  --integration-type HTTP_PROXY --integration-method ANY \
  --integration-uri "http://kadaikutty-pos-api.ap-southeast-2.elasticbeanstalk.com/{proxy}" \
  --payload-format-version 1.0 --timeout-in-millis 29000 --query 'IntegrationId' --output text)
aws apigatewayv2 create-route --api-id "$API_ID" --route-key 'ANY /{proxy+}' \
  --target "integrations/$INTEGRATION_ID"
aws apigatewayv2 create-stage --api-id "$API_ID" --stage-name '$default' --auto-deploy
```
(A one-shot `create-api --target http://...:{proxy}` shortcut looks tempting but fails — the quick-create
route doesn't capture `{proxy+}`, so the integration's path variable is never populated. Create the
integration and the `ANY /{proxy+}` route explicitly as above.)

**Known limitation**: this is a plain HTTP reverse proxy, not a WebSocket-aware one. socket.io (used
for the `data_changed` realtime push) auto-negotiates transport and falls back to HTTP long-polling
when a true WebSocket upgrade isn't available through the proxy — so realtime still works, just as
polling instead of a persistent socket, with a little more latency and request volume. The 15-minute
periodic sync (`SyncScheduler.schedulePeriodicSync`) is the safety net either way. Also note the
integration timeout is capped at 29 seconds (API Gateway HTTP API's hard maximum) — long-held
long-polling requests get cut off and socket.io just reconnects; this is normal, not an error.

**Once you have a real domain**: point it at the EB environment's **Application Load Balancer**
directly (Route 53 alias or a CNAME) with an ACM certificate on an HTTPS listener — that gets you
real WebSocket support back and removes the API Gateway hop entirely. Retire the API Gateway API at
that point (`aws apigatewayv2 delete-api --api-id u3bmxkaw25`).

## 4. Deploying a new server build

> **The live stack is now Mumbai (ap-south-1), not Sydney.** For it, run `aws login` and then
> `powershell -ExecutionPolicy Bypass -File aws-infrastructure\deploy-server.ps1 -VersionLabel <new label>`:
> it builds the bundle (with the tests), uploads it, switches the environment and checks `/health`.
> The Sydney-era commands below show the same steps by hand; the bucket, region and URL in them are stale.

```bash
cd server && npm run build && npm test          # must be green before packaging
cd ../aws-infrastructure
./package-elastic-beanstalk.ps1 -OutputPath "artifacts/kadaikutty-pos-server-vNEXT.zip"

aws s3 cp artifacts/kadaikutty-pos-server-vNEXT.zip \
  s3://elasticbeanstalk-ap-southeast-2-638120274634/kadaikutty-pos-backend/kadaikutty-pos-server-vNEXT.zip

aws elasticbeanstalk create-application-version \
  --application-name kadaikutty-pos-backend --version-label kadaikutty-pos-server-vNEXT \
  --source-bundle S3Bucket=elasticbeanstalk-ap-southeast-2-638120274634,S3Key=kadaikutty-pos-backend/kadaikutty-pos-server-vNEXT.zip

aws elasticbeanstalk update-environment \
  --environment-name kadaikutty-pos-production --version-label kadaikutty-pos-server-vNEXT

# poll until Status=Ready, Health=Green:
aws elasticbeanstalk describe-environments --environment-names kadaikutty-pos-production \
  --query 'Environments[0].{Status:Status,Health:Health,Version:VersionLabel}'

# then always confirm provider mode didn't regress:
curl -s https://u3bmxkaw25.execute-api.ap-southeast-2.amazonaws.com/health
```

Single instance + `AllAtOnce` means every deploy is a brief outage. That's an acceptable tradeoff
pre-launch; revisit (`MinSize=2`, `DeploymentPolicy=RollingWithAdditionalBatch`) before real traffic.

## 5. Still pending — manual, compliance-gated steps nobody can automate

- **SNS SMS is in the sandbox** (`MonthlySpendLimit: 1`, the AWS default) — OTP SMS can only reach
  phone numbers you've explicitly verified in the SNS console. Moving to production SMS access is an
  AWS Support case (Service Quotas console → "Request production access" for SMS), and **for sending
  to Indian numbers you additionally need TRAI DLT registration** (a Principal Entity ID and
  registered SMS templates with India's telecom regulator) — this needs your business's own PAN/GST
  and is not something achievable from this session. Until it's done, only pre-verified numbers can
  receive OTPs.
- **App Runner SCP / CloudFront account verification** — both need your AWS Organization
  management-account admin or an AWS Support case respectively; not fixable via the member account's
  IAM permissions no matter how broad.
- **Custom domain** — not purchased/configured. See §3 for what changes once you have one.
