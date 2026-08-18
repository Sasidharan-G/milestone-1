import express from 'express';
import { healthRouter } from './modules/health/router.js';
export const app = express();
app.disable('x-powered-by');
app.use((request, _response, next) => { console.info(JSON.stringify({ level: 'info', event: 'http_request', method: request.method, path: request.path })); next(); });
app.use(express.json({ limit: '1mb' }));
app.use('/health', healthRouter);
app.use((_request, response) => response.status(404).json({ message: 'Not found' }));
