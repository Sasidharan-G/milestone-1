import { NextFunction, Request, Response } from 'express';
import { randomUUID } from 'node:crypto';
import { AppError, errorBody } from '../core/errors';

/** Echoes a sanitized client X-Request-Id (or mints one) so client logs and server logs can be matched. */
export const requestContext = (req: Request, res: Response, next: NextFunction) => {
  const supplied = req.headers['x-request-id'];
  const candidate = Array.isArray(supplied) ? supplied[0] : supplied;
  const id = typeof candidate === 'string' && /^[A-Za-z0-9\-_.]{8,64}$/.test(candidate) ? candidate : randomUUID();
  (req as any).id = id;
  res.setHeader('X-Request-Id', id);
  next();
};

export const notFoundHandler = (req: Request, res: Response) => {
  res.status(404).json(errorBody(new AppError(404, 'ROUTE_NOT_FOUND', `No route for ${req.method} ${req.path}`), (req as any).id || 'unknown'));
};

/** Last-resort handler: body-parser failures, thrown middleware errors, anything a route did not catch. Never leaks stacks. */
// eslint-disable-next-line @typescript-eslint/no-unused-vars
export const errorHandler = (error: any, req: Request, res: Response, _next: NextFunction) => {
  const id = (req as any).id || 'unknown';
  if (res.headersSent) return;
  if (error instanceof AppError) return res.status(error.status).json(errorBody(error, id));
  if (error?.type === 'entity.parse.failed') return res.status(400).json(errorBody(new AppError(400, 'BODY_INVALID_JSON', 'Request body is not valid JSON'), id));
  if (error?.type === 'entity.too.large') return res.status(413).json(errorBody(new AppError(413, 'BODY_TOO_LARGE', 'Request body is too large'), id));
  console.error(`[${id}] Unhandled failure:`, error instanceof Error ? error.stack || error.message : error);
  return res.status(500).json(errorBody(new AppError(500, 'INTERNAL_ERROR', 'Internal server error'), id));
};
