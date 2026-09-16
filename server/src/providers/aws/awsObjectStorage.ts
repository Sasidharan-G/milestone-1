import { randomUUID } from 'node:crypto';
import { DeleteObjectCommand, GetObjectCommand, HeadObjectCommand, PutObjectCommand, S3Client } from '@aws-sdk/client-s3';
import { DeleteCommand, DynamoDBDocumentClient, GetCommand, PutCommand, QueryCommand } from '@aws-sdk/lib-dynamodb';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import { AppError } from '../../core/errors';
import { BackupRecord, ObjectStorage } from '../contracts';
import { AwsProviderConfig } from './awsConfig';
import { mapAwsError } from './awsErrors';

const pk = (companyId: string) => `COMPANY#${companyId}`;
const sk = (backupId: string) => `BACKUP#${backupId}`;

export class AwsObjectStorage implements ObjectStorage {
  constructor(
    private readonly s3: S3Client,
    private readonly dynamo: DynamoDBDocumentClient,
    private readonly config: AwsProviderConfig,
    private readonly signer: typeof getSignedUrl = getSignedUrl
  ) {}

  async createUploadIntent(input: Omit<BackupRecord, 'backupId' | 'objectKey' | 'status' | 'createdAtEpochMs'>): Promise<BackupRecord & { uploadUrl: string; requiredHeaders: Record<string, string> }> {
    const backupId = randomUUID();
    const backup: BackupRecord = {
      ...input, backupId, objectKey: `tenants/${input.companyId}/backups/${backupId}.backup`, status: 'PENDING', createdAtEpochMs: Date.now()
    };
    const checksumBase64 = Buffer.from(input.checksumSha256, 'hex').toString('base64');
    try {
      await this.dynamo.send(new PutCommand({
        TableName: this.config.tableName,
        Item: { pk: pk(input.companyId), sk: sk(backupId), itemType: 'BACKUP', data: backup, expiresAtEpochSeconds: Math.floor((Date.now() + 86_400_000) / 1000) },
        ConditionExpression: 'attribute_not_exists(pk) AND attribute_not_exists(sk)'
      }));
      const command = new PutObjectCommand({
        Bucket: this.config.backupBucket, Key: backup.objectKey, ContentType: 'application/octet-stream', ContentLength: input.sizeBytes,
        ChecksumSHA256: checksumBase64,
        Metadata: { companyid: input.companyId, backupid: backupId, checksumsha256: input.checksumSha256, schemaversion: String(input.schemaVersion) }
      });
      const uploadUrl = await this.signer(this.s3, command, { expiresIn: this.config.presignedUrlSeconds });
      return {
        ...backup,
        uploadUrl,
        requiredHeaders: {
          'Content-Type': 'application/octet-stream',
          'x-amz-checksum-sha256': checksumBase64,
          'x-amz-meta-companyid': input.companyId,
          'x-amz-meta-backupid': backupId,
          'x-amz-meta-checksumsha256': input.checksumSha256,
          'x-amz-meta-schemaversion': String(input.schemaVersion)
        }
      };
    } catch (error) { throw mapAwsError(error, 'S3 backup upload intent'); }
  }

  async complete(companyId: string, backupId: string): Promise<BackupRecord> {
    const backup = await this.requireBackup(companyId, backupId);
    try {
      const head = await this.s3.send(new HeadObjectCommand({ Bucket: this.config.backupBucket, Key: backup.objectKey, ChecksumMode: 'ENABLED' }));
      const expectedChecksum = Buffer.from(backup.checksumSha256, 'hex').toString('base64');
      if (head.ContentLength !== backup.sizeBytes) throw new AppError(422, 'BACKUP_SIZE_MISMATCH', 'Uploaded backup size does not match metadata');
      if (head.ChecksumSHA256 !== expectedChecksum || head.Metadata?.checksumsha256 !== backup.checksumSha256 || head.Metadata?.companyid !== companyId) {
        throw new AppError(422, 'BACKUP_CHECKSUM_MISMATCH', 'Uploaded backup checksum or tenant metadata is invalid');
      }
      const ready: BackupRecord = { ...backup, status: 'READY' };
      await this.dynamo.send(new PutCommand({ TableName: this.config.tableName, Item: { pk: pk(companyId), sk: sk(backupId), itemType: 'BACKUP', data: ready } }));
      return ready;
    } catch (error) { throw mapAwsError(error, 'S3 backup completion'); }
  }

  async list(companyId: string): Promise<BackupRecord[]> {
    try {
      const response = await this.dynamo.send(new QueryCommand({
        TableName: this.config.tableName, KeyConditionExpression: 'pk = :pk AND begins_with(sk, :prefix)',
        ExpressionAttributeValues: { ':pk': pk(companyId), ':prefix': 'BACKUP#' }, ConsistentRead: true
      }));
      return (response.Items || []).map(item => item.data as BackupRecord).filter(item => item.status === 'READY').sort((left, right) => right.createdAtEpochMs - left.createdAtEpochMs);
    } catch (error) { throw mapAwsError(error, 'DynamoDB backup listing'); }
  }

  async createDownloadIntent(companyId: string, backupId: string): Promise<{ backup: BackupRecord; downloadUrl: string }> {
    const backup = await this.requireBackup(companyId, backupId);
    if (backup.status !== 'READY') throw new AppError(409, 'BACKUP_NOT_READY', 'Backup is not ready for download');
    try {
      const downloadUrl = await this.signer(this.s3, new GetObjectCommand({ Bucket: this.config.backupBucket, Key: backup.objectKey }), { expiresIn: this.config.presignedUrlSeconds });
      return { backup, downloadUrl };
    } catch (error) { throw mapAwsError(error, 'S3 backup download intent'); }
  }

  async writeLocalContent(): Promise<void> { throw new AppError(405, 'BACKUP_DIRECT_UPLOAD_DISABLED', 'Use the presigned S3 upload URL in AWS mode'); }
  async readLocalContent(): Promise<Buffer> { throw new AppError(405, 'BACKUP_DIRECT_DOWNLOAD_DISABLED', 'Use the presigned S3 download URL in AWS mode'); }

  async delete(companyId: string, backupId: string): Promise<void> {
    const backup = await this.requireBackup(companyId, backupId);
    try {
      await this.s3.send(new DeleteObjectCommand({ Bucket: this.config.backupBucket, Key: backup.objectKey }));
      await this.dynamo.send(new DeleteCommand({ TableName: this.config.tableName, Key: { pk: pk(companyId), sk: sk(backupId) } }));
    } catch (error) { throw mapAwsError(error, 'S3 backup deletion'); }
  }

  private async requireBackup(companyId: string, backupId: string): Promise<BackupRecord> {
    try {
      const response = await this.dynamo.send(new GetCommand({ TableName: this.config.tableName, Key: { pk: pk(companyId), sk: sk(backupId) }, ConsistentRead: true }));
      const backup = response.Item?.data as BackupRecord | undefined;
      if (!backup || backup.companyId !== companyId) throw new AppError(404, 'BACKUP_NOT_FOUND', 'Backup was not found');
      return backup;
    } catch (error) { throw mapAwsError(error, 'DynamoDB backup lookup'); }
  }
}
