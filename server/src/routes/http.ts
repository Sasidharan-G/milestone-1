import { Response } from 'express';
import { AppError, errorBody } from '../core/errors';

export const requestId = (request: any): string => request?.id || 'unknown';

export const sendRouteError = (response: Response, request: any, error: unknown): Response => {
  if (error instanceof AppError) return response.status(error.status).json(errorBody(error, requestId(request)));
  console.error(`[${requestId(request)}] Route failure:`, error instanceof Error ? error.stack || error.message : error);
  return response.status(500).json(errorBody(new AppError(500, 'INTERNAL_ERROR', 'Internal server error'), requestId(request)));
};
