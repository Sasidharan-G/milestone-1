import { AppError } from '../../core/errors';

export const isAwsError = (error: unknown, name: string): boolean =>
  Boolean(error && typeof error === 'object' && (error as { name?: string }).name === name);

export const mapAwsError = (error: unknown, operation: string): AppError => {
  if (error instanceof AppError) return error;
  if (isAwsError(error, 'ConditionalCheckFailedException') || isAwsError(error, 'TransactionCanceledException')) {
    return new AppError(409, 'AWS_CONDITION_FAILED', `${operation} could not be completed because cloud state changed`);
  }
  if (isAwsError(error, 'NotAuthorizedException')) return new AppError(401, 'AUTH_INVALID_CREDENTIALS', 'Invalid mobile number or password');
  if (isAwsError(error, 'UserNotFoundException')) return new AppError(404, 'ACCOUNT_NOT_FOUND', 'Account was not found');
  if (isAwsError(error, 'TooManyRequestsException') || isAwsError(error, 'ThrottlingException') || isAwsError(error, 'ProvisionedThroughputExceededException')) {
    return new AppError(503, 'AWS_THROTTLED', `${operation} is temporarily throttled`, true);
  }
  if (isAwsError(error, 'ServiceUnavailableException') || isAwsError(error, 'InternalServerError')) {
    return new AppError(503, 'AWS_TEMPORARILY_UNAVAILABLE', `${operation} is temporarily unavailable`, true);
  }
  return new AppError(502, 'AWS_PROVIDER_ERROR', `${operation} failed`, true);
};
