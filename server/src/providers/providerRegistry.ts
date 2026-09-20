import path from 'node:path';
import { DataStore, IdentityProvider, ObjectStorage, SessionStore, SmsSender } from './contracts';
import { AtomicJsonStore } from './local/atomicJsonStore';
import { LocalDataStore } from './local/localDataStore';
import { LocalIdentityProvider } from './local/localIdentityProvider';
import { LocalObjectStorage } from './local/localObjectStorage';
import { LocalSessionStore } from './local/localSessionStore';
import { LocalSmsSender } from './local/localSmsSender';
import { CognitoIdentityProviderClient } from '@aws-sdk/client-cognito-identity-provider';
import { DynamoDBClient } from '@aws-sdk/client-dynamodb';
import { DynamoDBDocumentClient } from '@aws-sdk/lib-dynamodb';
import { S3Client } from '@aws-sdk/client-s3';
import { SecretsManagerClient } from '@aws-sdk/client-secrets-manager';
import { SNSClient } from '@aws-sdk/client-sns';
import { loadAwsProviderConfig } from './aws/awsConfig';
import { AwsDataStore } from './aws/awsDataStore';
import { AwsIdentityProvider } from './aws/awsIdentityProvider';
import { AwsObjectStorage } from './aws/awsObjectStorage';
import { AwsSessionStore } from './aws/awsSessionStore';
import { AppError } from '../core/errors';
import { AwsSmsSender } from './aws/awsSmsSender';
import { Msg91SmsSender } from './msg91/msg91SmsSender';

export interface ProviderRegistry {
  dataStore: DataStore;
  identityProvider: IdentityProvider;
  objectStorage: ObjectStorage;
  sessionStore: SessionStore;
  smsSender: SmsSender;
  mode: 'local' | 'aws';
}

let singleton: ProviderRegistry | null = null;

export const createProviderRegistry = (): ProviderRegistry => {
  const mode = (process.env.PROVIDER_MODE || 'local').toLowerCase();
  if (mode === 'aws') {
    const config = loadAwsProviderConfig();
    const dynamo = DynamoDBDocumentClient.from(new DynamoDBClient({ region: config.region }), { marshallOptions: { removeUndefinedValues: true } });
    const dataStore = new AwsDataStore(dynamo, config.tableName, new SecretsManagerClient({ region: config.region }), config.masterPinSecretArn);
    const smsProvider = (process.env.SMS_PROVIDER || 'msg91').toLowerCase();
    let smsSender: SmsSender;
    if (smsProvider === 'sns') {
      smsSender = new AwsSmsSender(new SNSClient({ region: config.region }));
    } else if (smsProvider === 'msg91') {
      const msg91WidgetId = process.env.MSG91_WIDGET_ID?.trim();
      const msg91TokenAuth = process.env.MSG91_TOKEN_AUTH?.trim();
      const msg91AuthKey = process.env.MSG91_AUTH_KEY?.trim();

      const missing: string[] = [];
      if (!msg91WidgetId) missing.push('MSG91_WIDGET_ID');
      if (!msg91TokenAuth) missing.push('MSG91_TOKEN_AUTH');
      if (!msg91AuthKey) missing.push('MSG91_AUTH_KEY');

      if (missing.length > 0) {
        throw new AppError(
          500,
          'MSG91_CONFIG_MISSING',
          `Missing required MSG91 configuration environment variable(s): ${missing.join(', ')}`
        );
      }

      smsSender = new Msg91SmsSender({ widgetId: msg91WidgetId!, tokenAuth: msg91TokenAuth!, authKey: msg91AuthKey! });
    } else {
      throw new AppError(500, 'SMS_PROVIDER_INVALID', `Unsupported SMS_PROVIDER: ${smsProvider}`);
    }

    return {
      dataStore,
      identityProvider: new AwsIdentityProvider(new CognitoIdentityProviderClient({ region: config.region }), dataStore, config),
      objectStorage: new AwsObjectStorage(new S3Client({ region: config.region }), dynamo, config),
      sessionStore: new AwsSessionStore(dynamo, config.tableName),
      smsSender,
      mode: 'aws'
    };
  }
  if (mode !== 'local') throw new Error(`Unsupported PROVIDER_MODE: ${mode}`);
  const dataDirectory = path.resolve(process.env.LOCAL_DATA_DIR || path.join(process.cwd(), '.local-data'));
  const atomicStore = new AtomicJsonStore(path.join(dataDirectory, 'state.json'));
  const dataStore = new LocalDataStore(atomicStore);
  return {
    dataStore,
    identityProvider: new LocalIdentityProvider(atomicStore, dataStore),
    objectStorage: new LocalObjectStorage(atomicStore, path.join(dataDirectory, 'objects')),
    sessionStore: new LocalSessionStore(atomicStore),
    smsSender: new LocalSmsSender(),
    mode: 'local'
  };
};

export const providers = (): ProviderRegistry => singleton || (singleton = createProviderRegistry());

export const setProviderRegistryForTests = (registry: ProviderRegistry | null): void => { singleton = registry; };
