import { AppError } from '../../core/errors';

export interface AwsProviderConfig {
  region: string;
  userPoolId: string;
  userPoolClientId: string;
  tableName: string;
  backupBucket: string;
  phoneCountryCode: string;
  presignedUrlSeconds: number;
  masterPinSecretArn: string;
}

const required = (name: string): string => {
  const value = process.env[name]?.trim();
  if (!value) throw new AppError(500, 'AWS_CONFIG_MISSING', `${name} must be configured when PROVIDER_MODE=aws`);
  return value;
};

export const loadAwsProviderConfig = (): AwsProviderConfig => ({
  region: required('AWS_REGION'),
  userPoolId: required('AWS_COGNITO_USER_POOL_ID'),
  userPoolClientId: required('AWS_COGNITO_CLIENT_ID'),
  tableName: required('AWS_DYNAMODB_TABLE'),
  backupBucket: required('AWS_S3_BACKUP_BUCKET'),
  phoneCountryCode: process.env.AWS_COGNITO_PHONE_COUNTRY_CODE?.trim() || '+91',
  presignedUrlSeconds: Math.min(900, Math.max(60, Number(process.env.AWS_PRESIGNED_URL_SECONDS || 300))),
  masterPinSecretArn: required('AWS_MASTER_PIN_SECRET_ARN')
});
