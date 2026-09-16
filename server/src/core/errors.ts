export class AppError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly retryable = false,
    public readonly details?: unknown
  ) {
    super(message);
    this.name = 'AppError';
  }
}

export const errorBody = (error: AppError, requestId: string) => ({
  error: {
    code: error.code,
    message: error.message,
    retryable: error.retryable,
    requestId,
    ...(error.details === undefined ? {} : { details: error.details })
  }
});

