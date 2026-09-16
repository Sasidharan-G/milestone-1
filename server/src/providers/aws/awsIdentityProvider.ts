import {
  AdminCreateUserCommand,
  AdminDeleteUserCommand,
  AdminGetUserCommand,
  AdminInitiateAuthCommand,
  AdminSetUserPasswordCommand,
  AdminUpdateUserAttributesCommand,
  AdminUserGlobalSignOutCommand,
  CognitoIdentityProviderClient
} from '@aws-sdk/client-cognito-identity-provider';
import crypto from 'node:crypto';
import { CognitoJwtVerifier } from 'aws-jwt-verify';
import { AppError } from '../../core/errors';
import { DataStore, IdentityProvider, IdentityTokens, UserAccount, VerifiedIdentity } from '../contracts';
import { AwsProviderConfig } from './awsConfig';
import { isAwsError, mapAwsError } from './awsErrors';

export class AwsIdentityProvider implements IdentityProvider {
  private readonly verifier;

  constructor(
    private readonly client: CognitoIdentityProviderClient,
    private readonly dataStore: DataStore,
    private readonly config: AwsProviderConfig
  ) {
    this.verifier = CognitoJwtVerifier.create({ userPoolId: config.userPoolId, tokenUse: 'id', clientId: config.userPoolClientId });
  }

  async setPassword(userId: string, password: string): Promise<void> {
    if (password.length < 6 || password.length > 256) throw new AppError(400, 'AUTH_PASSWORD_INVALID', 'Password must contain 6 to 256 characters');
    const user = await this.dataStore.findUserById(userId);
    if (!user) throw new AppError(404, 'ACCOUNT_NOT_FOUND', 'Account was not found');
    try {
      await this.ensureUser(user);
      await this.client.send(new AdminSetUserPasswordCommand({ UserPoolId: this.config.userPoolId, Username: userId, Password: password, Permanent: true }));
    } catch (error) {
      throw mapAwsError(error, 'Cognito password update');
    }
  }

  async authenticate(phone: string, password: string): Promise<{ user: UserAccount; tokens: IdentityTokens }> {
    const user = await this.dataStore.findUserByPhone(phone);
    if (!user) throw new AppError(401, 'AUTH_INVALID_CREDENTIALS', 'Invalid mobile number or password');
    if (user.status !== 'ACTIVE') throw new AppError(403, 'AUTH_ACCOUNT_INACTIVE', 'Account is not active');
    try {
      const response = await this.client.send(new AdminInitiateAuthCommand({
        UserPoolId: this.config.userPoolId,
        ClientId: this.config.userPoolClientId,
        AuthFlow: 'ADMIN_USER_PASSWORD_AUTH',
        AuthParameters: { USERNAME: user.userId, PASSWORD: password }
      }));
      return { user, tokens: this.tokens(response.AuthenticationResult, true) };
    } catch (error) {
      throw mapAwsError(error, 'Cognito authentication');
    }
  }

  async refresh(refreshToken: string): Promise<{ user: UserAccount; tokens: IdentityTokens }> {
    try {
      const response = await this.client.send(new AdminInitiateAuthCommand({
        UserPoolId: this.config.userPoolId,
        ClientId: this.config.userPoolClientId,
        AuthFlow: 'REFRESH_TOKEN_AUTH',
        AuthParameters: { REFRESH_TOKEN: refreshToken }
      }));
      const tokens = this.tokens(response.AuthenticationResult, false, refreshToken);
      const identity = await this.verifyAccessToken(tokens.accessToken);
      const user = await this.dataStore.findUserById(identity.userId);
      if (!user || user.status !== 'ACTIVE') throw new AppError(401, 'AUTH_REFRESH_INVALID', 'Refresh session is invalid or expired');
      return { user, tokens };
    } catch (error) {
      if (error instanceof AppError) throw error;
      throw mapAwsError(error, 'Cognito token refresh');
    }
  }

  async revokeUser(userId: string): Promise<void> {
    try {
      await this.client.send(new AdminUserGlobalSignOutCommand({ UserPoolId: this.config.userPoolId, Username: userId }));
    } catch (error) {
      if (!isAwsError(error, 'UserNotFoundException')) throw mapAwsError(error, 'Cognito session revocation');
    }
  }

  async deleteUser(userId: string): Promise<void> {
    try {
      await this.client.send(new AdminDeleteUserCommand({ UserPoolId: this.config.userPoolId, Username: userId }));
    } catch (error) {
      if (!isAwsError(error, 'UserNotFoundException')) throw mapAwsError(error, 'Cognito user deletion');
    }
  }

  async verifyAccessToken(token: string): Promise<VerifiedIdentity> {
    try {
      const payload = await this.verifier.verify(token);
      const userId = String(payload['custom:user_id'] || payload['cognito:username'] || '');
      const companyId = String(payload['custom:company_id'] || '');
      if (!userId || !companyId) throw new AppError(401, 'AUTH_INVALID_TOKEN', 'Token is missing tenant identity');
      const permissions = this.parsePermissions(payload['custom:permissions']);
      const role = String(payload['custom:role'] || 'CASHIER') as UserAccount['role'];
      return {
        ...payload,
        userId,
        companyId,
        role,
        permissions,
        super_admin: role === 'SUPER_ADMIN',
        phone_number: typeof payload.phone_number === 'string' ? payload.phone_number : undefined
      };
    } catch (error) {
      if (error instanceof AppError) throw error;
      throw new AppError(401, 'AUTH_INVALID_TOKEN', 'Cognito token verification failed');
    }
  }

  async authenticatePlatform(phone: string, pin: string): Promise<{ tokens: IdentityTokens; mobile: string }> {
    const master = await this.dataStore.getMasterConfig();
    const supplied = Buffer.from(`${phone}:${pin}`);
    const expected = Buffer.from(`${master.mobile}:${master.pin}`);
    if (supplied.length !== expected.length || !crypto.timingSafeEqual(supplied, expected)) throw new AppError(401, 'MASTER_PIN_INVALID', 'Master credentials are invalid');
    try {
      await this.ensurePlatformUser(master.mobile);
      await this.client.send(new AdminSetUserPasswordCommand({ UserPoolId: this.config.userPoolId, Username: 'master-admin', Password: master.pin, Permanent: true }));
      const response = await this.client.send(new AdminInitiateAuthCommand({
        UserPoolId: this.config.userPoolId, ClientId: this.config.userPoolClientId, AuthFlow: 'ADMIN_USER_PASSWORD_AUTH',
        AuthParameters: { USERNAME: 'master-admin', PASSWORD: master.pin }
      }));
      return { mobile: master.mobile, tokens: this.tokens(response.AuthenticationResult, true) };
    } catch (error) { throw mapAwsError(error, 'Cognito platform authentication'); }
  }

  private async ensureUser(user: UserAccount): Promise<void> {
    const createAttributes = this.attributes(user, true);
    const updateAttributes = this.attributes(user, false);
    try {
      await this.client.send(new AdminGetUserCommand({ UserPoolId: this.config.userPoolId, Username: user.userId }));
      await this.client.send(new AdminUpdateUserAttributesCommand({ UserPoolId: this.config.userPoolId, Username: user.userId, UserAttributes: updateAttributes }));
    } catch (error) {
      if (!isAwsError(error, 'UserNotFoundException')) throw error;
      await this.client.send(new AdminCreateUserCommand({
        UserPoolId: this.config.userPoolId,
        Username: user.userId,
        MessageAction: 'SUPPRESS',
        UserAttributes: createAttributes
      }));
    }
  }

  private async ensurePlatformUser(mobile: string): Promise<void> {
    const phone = mobile.startsWith('+') ? mobile : `${this.config.phoneCountryCode}${mobile}`;
    const mutableAttributes = [
      { Name: 'phone_number', Value: phone }, { Name: 'phone_number_verified', Value: 'true' },
      { Name: 'name', Value: 'Platform Administrator' }, { Name: 'custom:role', Value: 'SUPER_ADMIN' },
      { Name: 'custom:permissions', Value: JSON.stringify(['PLATFORM_ADMIN']) }, { Name: 'custom:status', Value: 'ACTIVE' }
    ];
    try {
      await this.client.send(new AdminGetUserCommand({ UserPoolId: this.config.userPoolId, Username: 'master-admin' }));
      await this.client.send(new AdminUpdateUserAttributesCommand({ UserPoolId: this.config.userPoolId, Username: 'master-admin', UserAttributes: mutableAttributes }));
    } catch (error) {
      if (!isAwsError(error, 'UserNotFoundException')) throw error;
      await this.client.send(new AdminCreateUserCommand({
        UserPoolId: this.config.userPoolId, Username: 'master-admin', MessageAction: 'SUPPRESS',
        UserAttributes: [...mutableAttributes, { Name: 'custom:user_id', Value: 'master-admin' }, { Name: 'custom:company_id', Value: 'platform' }]
      }));
    }
  }

  private attributes(user: UserAccount, includeImmutable: boolean) {
    const phone = user.phone.startsWith('+') ? user.phone : `${this.config.phoneCountryCode}${user.phone}`;
    const attributes = [
      { Name: 'phone_number', Value: phone },
      { Name: 'phone_number_verified', Value: 'true' },
      { Name: 'name', Value: user.displayName },
      { Name: 'custom:role', Value: user.role },
      { Name: 'custom:permissions', Value: JSON.stringify(user.permissions) },
      { Name: 'custom:status', Value: user.status }
    ];
    if (includeImmutable) attributes.push({ Name: 'custom:user_id', Value: user.userId }, { Name: 'custom:company_id', Value: user.companyId });
    return attributes;
  }

  private tokens(result: { IdToken?: string; RefreshToken?: string; ExpiresIn?: number } | undefined, requireRefresh: boolean, fallbackRefresh = ''): IdentityTokens {
    if (!result?.IdToken || (requireRefresh && !result.RefreshToken)) throw new AppError(502, 'AUTH_TOKEN_RESPONSE_INVALID', 'Cognito returned an incomplete token response', true);
    return { accessToken: result.IdToken, refreshToken: result.RefreshToken || fallbackRefresh, expiresInSeconds: result.ExpiresIn || 3600 };
  }

  private parsePermissions(value: unknown): string[] {
    if (typeof value !== 'string') return [];
    try { const parsed = JSON.parse(value); return Array.isArray(parsed) ? parsed.filter(item => typeof item === 'string') : []; }
    catch { return []; }
  }
}
