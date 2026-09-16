import path from 'node:path';
import { DataStore, IdentityProvider, ObjectStorage, SessionStore } from './contracts';
import { AtomicJsonStore } from './local/atomicJsonStore';
import { LocalDataStore } from './local/localDataStore';
import { LocalIdentityProvider } from './local/localIdentityProvider';
import { LocalObjectStorage } from './local/localObjectStorage';
import { LocalSessionStore } from './local/localSessionStore';
import { CognitoIdentityProviderClient } from '@aws-sdk/client-cognito-identity-provider';
import { DynamoDBClient } from '@aws-sdk/client-dynamodb';
import { DynamoDBDocumentClient } from '@aws-sdk/lib-dynamodb';
import { S3Client } from '@aws-sdk/client-s3';
import { SecretsManagerClient } from '@aws-sdk/client-secrets-manager';
import { loadAwsProviderConfig } from './aws/awsConfig';
import { AwsDataStore } from './aws/awsDataStore';
import { AwsIdentityProvider } from './aws/awsIdentityProvider';
import { AwsObjectStorage } from './aws/awsObjectStorage';
import { AwsSessionStore } from './aws/awsSessionStore';

export interface ProviderRegistry {
  dataStore: DataStore;
  identityProvider: IdentityProvider;
  objectStorage: ObjectStorage;
  sessionStore: SessionStore;
  mode: 'local' | 'aws';
}

let singleton: ProviderRegistry | null = null;

export const createProviderRegistry = (): ProviderRegistry => {
  const mode = (process.env.PROVIDER_MODE || 'local').toLowerCase();
  if (mode === 'aws') {
    const config = loadAwsProviderConfig();
    const dynamo = DynamoDBDocumentClient.from(new DynamoDBClient({ region: config.region }), { marshallOptions: { removeUndefinedValues: true } });
    const dataStore = new AwsDataStore(dynamo, config.tableName, new SecretsManagerClient({ region: config.region }), config.masterPinSecretArn);
    return {
      dataStore,
      identityProvider: new AwsIdentityProvider(new CognitoIdentityProviderClient({ region: config.region }), dataStore, config),
      objectStorage: new AwsObjectStorage(new S3Client({ region: config.region }), dynamo, config),
      sessionStore: new AwsSessionStore(dynamo, config.tableName),
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
    mode: 'local'
  };
};

export const providers = (): ProviderRegistry => singleton || (singleton = createProviderRegistry());

export const setProviderRegistryForTests = (registry: ProviderRegistry | null): void => { singleton = registry; };
