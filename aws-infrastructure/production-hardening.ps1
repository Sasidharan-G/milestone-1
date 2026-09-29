# One-shot production hardening for the Mumbai stack (account 622952747916, ap-south-1).
# Run in PowerShell after `aws login`:
#   powershell -ExecutionPolicy Bypass -File aws-infrastructure\production-hardening.ps1
# Every step is safe to re-run. Steps 1-2 restart the app for ~30-60 s.

$Region  = 'ap-south-1'
$App     = 'kadaikutty-pos-backend'
$EbEnv     = 'kadaikutty-pos-production'
$Table   = 'kadaikutty-pos-production'
$ApiId   = 'dr88bwgl8h'
$Email   = 'sasidharangr9487@gmail.com'      # alerts go here (AWS sends one confirmation mail: click it)
$Version = 'kadaikutty-pos-server-v28'
$Bundle  = Join-Path $PSScriptRoot '..\artifacts\kadaikutty-pos-server-v28.zip'
$EbBucket = 'elasticbeanstalk-ap-south-1-622952747916'

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

Step '1. Deploy server v28 (master-PIN OTP fix + OTP SMS-abuse guard + S3 backup fix + real AWS error logging)'
aws s3 cp $Bundle "s3://$EbBucket/$App/$Version.zip" --region $Region
aws elasticbeanstalk create-application-version --application-name $App --version-label $Version `
    --source-bundle "S3Bucket=$EbBucket,S3Key=$App/$Version.zip" --region $Region --query 'ApplicationVersion.VersionLabel' --output text
aws elasticbeanstalk update-environment --environment-name $EbEnv --version-label $Version --region $Region --query 'Status' --output text
Wait-Ready
Write-Host '  /health ->' (curl.exe -s https://dr88bwgl8h.execute-api.ap-south-1.amazonaws.com/health)

Step '2. Stream server logs to CloudWatch (30 days) so errors are visible'
aws elasticbeanstalk update-environment --environment-name $EbEnv --region $Region --option-settings `
    'Namespace=aws:elasticbeanstalk:cloudwatch:logs,OptionName=StreamLogs,Value=true' `
    'Namespace=aws:elasticbeanstalk:cloudwatch:logs,OptionName=DeleteOnTerminate,Value=false' `
    'Namespace=aws:elasticbeanstalk:cloudwatch:logs,OptionName=RetentionInDays,Value=30' --query 'Status' --output text
Wait-Ready

Step '3. DynamoDB deletion protection (nobody can drop the table by accident)'
aws dynamodb update-table --table-name $Table --deletion-protection-enabled --region $Region --query 'TableDescription.DeletionProtectionEnabled' --output text

Step '4. Alert topic + e-mail subscription'
$topic = aws sns create-topic --name kadaikutty-pos-alerts --region $Region --query TopicArn --output text
aws sns subscribe --topic-arn $topic --protocol email --notification-endpoint $Email --region $Region --query SubscriptionArn --output text
Write-Host "  topic: $topic  (confirm the subscription e-mail AWS just sent, or alerts are dropped)"

Step '5. Alarms: API 5xx errors, server health, and no traffic-side outage'
aws cloudwatch put-metric-alarm --region $Region --alarm-name kadaikutty-pos-api-5xx `
    --alarm-description 'API Gateway returned 5xx to the app' `
    --namespace AWS/ApiGateway --metric-name 5xx --dimensions "Name=ApiId,Value=$ApiId" `
    --statistic Sum --period 300 --evaluation-periods 1 --threshold 5 --comparison-operator GreaterThanOrEqualToThreshold `
    --treat-missing-data notBreaching --alarm-actions $topic
aws cloudwatch put-metric-alarm --region $Region --alarm-name kadaikutty-pos-eb-unhealthy `
    --alarm-description 'Elastic Beanstalk environment is Degraded or Severe' `
    --namespace AWS/ElasticBeanstalk --metric-name EnvironmentHealth --dimensions "Name=EnvironmentName,Value=$EbEnv" `
    --statistic Maximum --period 300 --evaluation-periods 2 --threshold 20 --comparison-operator GreaterThanOrEqualToThreshold `
    --treat-missing-data notBreaching --alarm-actions $topic
aws cloudwatch put-metric-alarm --region $Region --alarm-name kadaikutty-pos-api-latency `
    --alarm-description 'API average latency above 5 s' `
    --namespace AWS/ApiGateway --metric-name Latency --dimensions "Name=ApiId,Value=$ApiId" `
    --statistic Average --period 300 --evaluation-periods 2 --threshold 5000 --comparison-operator GreaterThanThreshold `
    --treat-missing-data notBreaching --alarm-actions $topic

Step '6. Monthly cost budget (USD 25) with e-mail at 80% actual and 100% forecast'
$account = aws sts get-caller-identity --query Account --output text
$budget = @'
{"BudgetName":"kadaikutty-pos-monthly","BudgetLimit":{"Amount":"25","Unit":"USD"},"TimeUnit":"MONTHLY","BudgetType":"COST"}
'@
$notifications = @"
[{"Notification":{"NotificationType":"ACTUAL","ComparisonOperator":"GREATER_THAN","Threshold":80,"ThresholdType":"PERCENTAGE"},"Subscribers":[{"SubscriptionType":"EMAIL","Address":"$Email"}]},
 {"Notification":{"NotificationType":"FORECASTED","ComparisonOperator":"GREATER_THAN","Threshold":100,"ThresholdType":"PERCENTAGE"},"Subscribers":[{"SubscriptionType":"EMAIL","Address":"$Email"}]}]
"@
$budgetFile = Join-Path $env:TEMP 'kk-budget.json'; $notifFile = Join-Path $env:TEMP 'kk-notifications.json'
[System.IO.File]::WriteAllText($budgetFile, $budget); [System.IO.File]::WriteAllText($notifFile, $notifications)
aws budgets create-budget --account-id $account --budget "file://$budgetFile" --notifications-with-subscribers "file://$notifFile"

Step 'Done. Re-check'
aws elasticbeanstalk describe-environments --environment-names $EbEnv --region $Region --query 'Environments[0].[VersionLabel,Status,Health]' --output text
aws dynamodb describe-table --table-name $Table --region $Region --query 'Table.DeletionProtectionEnabled' --output text
aws cloudwatch describe-alarms --alarm-name-prefix kadaikutty-pos --region $Region --query 'MetricAlarms[].[AlarmName,StateValue]' --output text

