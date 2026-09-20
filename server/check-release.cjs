require('dotenv').config();
const problems = [];
for (const key of ['RESET_SECRET']) {
  const value = process.env[key];
  if (!value || /^(your_|replace-)/.test(value)) problems.push(`${key} missing or placeholder`);
}
if ((process.env.RESET_SECRET || '').length < 32) problems.push('RESET_SECRET needs at least 32 random characters');
if (process.env.PROVIDER_MODE !== 'aws') problems.push('PROVIDER_MODE must be aws for production');
for (const key of ['AWS_REGION', 'AWS_COGNITO_USER_POOL_ID', 'AWS_COGNITO_CLIENT_ID', 'AWS_DYNAMODB_TABLE', 'AWS_S3_BACKUP_BUCKET', 'AWS_MASTER_PIN_SECRET_ARN']) {
  const value = process.env[key];
  if (!value || /^(your_|replace_)/.test(value)) problems.push(`${key} missing or placeholder`);
}
const smsProvider = (process.env.SMS_PROVIDER || 'msg91').toLowerCase();
if (smsProvider === 'msg91') {
  for (const key of ['MSG91_WIDGET_ID', 'MSG91_TOKEN_AUTH', 'MSG91_AUTH_KEY']) {
    const value = process.env[key];
    if (!value || /^(your_|replace_)/.test(value)) problems.push(`${key} missing or placeholder`);
  }
}
if (process.env.NODE_ENV !== 'production') problems.push('NODE_ENV must be production');
for (const problem of problems) console.error(problem);
console.log(problems.length ? 'Deployment configuration NOT READY' : 'Configuration present; live integration verification still required');
process.exitCode = problems.length ? 1 : 0;
