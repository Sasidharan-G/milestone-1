import { DynamoDBDocumentClient, GetCommand, PutCommand, QueryCommand, UpdateCommand } from '@aws-sdk/lib-dynamodb';
import { AppError } from '../../core/errors';
import { SessionRecord, SessionStore } from '../contracts';
import { isAwsError, mapAwsError } from './awsErrors';

const pk = (companyId: string) => `COMPANY#${companyId}`;
const sk = (sessionId: string) => `SESSION#${sessionId}`;

export class AwsSessionStore implements SessionStore {
  constructor(private readonly client: DynamoDBDocumentClient, private readonly tableName: string) {}

  async register(input: Omit<SessionRecord, 'revoked' | 'lastSeenAtEpochMs'>): Promise<SessionRecord> {
    const session: SessionRecord = { ...input, revoked: false, lastSeenAtEpochMs: Date.now() };
    try {
      await this.client.send(new PutCommand({
        TableName: this.tableName,
        Item: { pk: pk(input.companyId), sk: sk(input.sessionId), itemType: 'SESSION', data: session, expiresAtEpochSeconds: Math.floor(input.expiresAtEpochMs / 1000) },
        ConditionExpression: 'attribute_not_exists(pk) AND attribute_not_exists(sk)'
      }));
      return session;
    } catch (error) { throw mapAwsError(error, 'DynamoDB session registration'); }
  }

  async heartbeat(companyId: string, userId: string, sessionId: string, expiresAtEpochMs: number): Promise<SessionRecord> {
    const now = Date.now();
    let response;
    try {
      response = await this.client.send(new UpdateCommand({
        TableName: this.tableName, Key: { pk: pk(companyId), sk: sk(sessionId) },
        UpdateExpression: 'SET #data.lastSeenAtEpochMs = :now, #data.expiresAtEpochMs = :expires, expiresAtEpochSeconds = :expiresSeconds',
        ConditionExpression: '#data.companyId = :companyId AND #data.userId = :userId AND #data.revoked = :false AND #data.expiresAtEpochMs > :now',
        ExpressionAttributeNames: { '#data': 'data' },
        ExpressionAttributeValues: { ':companyId': companyId, ':userId': userId, ':false': false, ':now': now, ':expires': expiresAtEpochMs, ':expiresSeconds': Math.floor(expiresAtEpochMs / 1000) }, ReturnValues: 'ALL_NEW'
      }));
    } catch (error) {
      if (isAwsError(error, 'ConditionalCheckFailedException')) throw new AppError(401, 'SESSION_INVALID', 'Session is invalid, expired, or revoked');
      throw mapAwsError(error, 'DynamoDB session heartbeat');
    }
    // Outside the catch, so this is not re-wrapped as a DynamoDB fault. Masking a missing data
    // map with {} returned an empty object typed as a SessionRecord, letting the caller report
    // a successful heartbeat for a session it never actually read back.
    const data = response.Attributes?.data as SessionRecord | undefined;
    if (!data || !data.sessionId) throw new AppError(500, 'SESSION_STORE_CORRUPT', 'Session record is missing its data');
    return data;
  }

  async revoke(companyId: string, _actorUserId: string, sessionId: string): Promise<void> {
    try {
      await this.client.send(new UpdateCommand({
        TableName: this.tableName, Key: { pk: pk(companyId), sk: sk(sessionId) }, UpdateExpression: 'SET #data.revoked = :true',
        ConditionExpression: 'attribute_exists(pk)', ExpressionAttributeNames: { '#data': 'data' }, ExpressionAttributeValues: { ':true': true }
      }));
    } catch (error) {
      if (isAwsError(error, 'ConditionalCheckFailedException')) throw new AppError(404, 'SESSION_NOT_FOUND', 'Session was not found');
      throw mapAwsError(error, 'DynamoDB session revocation');
    }
  }

  async revokeOtherSessions(companyId: string, userId: string, keepSessionId: string): Promise<SessionRecord[]> {
    const now = Date.now();
    const revoked: SessionRecord[] = [];
    try {
      let ExclusiveStartKey: Record<string, unknown> | undefined;
      do {
        const response = await this.client.send(new QueryCommand({
          TableName: this.tableName, KeyConditionExpression: 'pk = :pk AND begins_with(sk, :prefix)',
          FilterExpression: '#data.userId = :userId AND #data.revoked = :false AND #data.expiresAtEpochMs > :now',
          ExpressionAttributeNames: { '#data': 'data' },
          ExpressionAttributeValues: { ':pk': pk(companyId), ':prefix': 'SESSION#', ':userId': userId, ':false': false, ':now': now },
          ExclusiveStartKey, ConsistentRead: true
        }));
        for (const item of response.Items || []) {
          const session = item.data as SessionRecord;
          if (session.sessionId === keepSessionId) continue;
          await this.client.send(new UpdateCommand({
            TableName: this.tableName, Key: { pk: pk(companyId), sk: sk(session.sessionId) }, UpdateExpression: 'SET #data.revoked = :true',
            ExpressionAttributeNames: { '#data': 'data' }, ExpressionAttributeValues: { ':true': true }
          }));
          revoked.push({ ...session, revoked: true });
        }
        ExclusiveStartKey = response.LastEvaluatedKey;
      } while (ExclusiveStartKey);
      return revoked;
    } catch (error) { throw mapAwsError(error, 'DynamoDB session revocation'); }
  }

  revokeAllSessions(companyId: string, userId: string): Promise<SessionRecord[]> {
    return this.revokeOtherSessions(companyId, userId, '');
  }

  async validate(companyId: string, userId: string, sessionId: string): Promise<boolean> {
    try {
      const response = await this.client.send(new GetCommand({ TableName: this.tableName, Key: { pk: pk(companyId), sk: sk(sessionId) }, ConsistentRead: true }));
      const session = response.Item?.data as SessionRecord | undefined;
      return Boolean(session && session.companyId === companyId && session.userId === userId && !session.revoked && session.expiresAtEpochMs > Date.now());
    } catch (error) { throw mapAwsError(error, 'DynamoDB session validation'); }
  }
}
