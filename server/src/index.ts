import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import dotenv from 'dotenv';
import { createServer } from 'http';
import { Server } from 'socket.io';

dotenv.config();

import syncRoutes from './routes/syncRoutes';
import accountRoutes from './routes/accountRoutes';
import backupRoutes from './routes/backupRoutes';
import licenseRoutes from './routes/licenseRoutes';
import sessionRoutes from './routes/sessionRoutes';
import adminRoutes from './routes/adminRoutes';
import otpRoutes from './routes/otpRoutes';
import { providers } from './providers/providerRegistry';
import { errorHandler, notFoundHandler, requestContext } from './middleware/requestContext';
import { sessionRoom, userRoom } from './core/realtime';

export const app = express();
const port = process.env.PORT || 3000;

// Native Android clients send no Origin header, so CORS only matters for browser dashboards.
// ALLOWED_ORIGINS restricts those; an empty value in development allows everything.
const allowedOrigins = (process.env.ALLOWED_ORIGINS || '').split(',').map(origin => origin.trim()).filter(Boolean);
const corsOptions: cors.CorsOptions = {
  origin: (origin, callback) => {
    if (!origin || allowedOrigins.length === 0 && process.env.NODE_ENV !== 'production') return callback(null, true);
    return callback(null, allowedOrigins.includes(origin));
  }
};

export const httpServer = createServer(app);
const io = new Server(httpServer, { cors: corsOptions });

app.set('io', io);
app.set('trust proxy', process.env.TRUST_PROXY === 'true' ? 1 : false);

// Sockets present the same bearer token and device session as the REST API. The rooms a socket
// may join are derived from the token, so a client can never subscribe to another tenant's events.
io.use(async (socket, next) => {
  try {
    const raw = socket.handshake.auth?.token || socket.handshake.headers.authorization || '';
    const token = String(raw).replace(/^Bearer\s+/i, '').trim();
    if (!token) return next(new Error('AUTH_REQUIRED'));
    const claims = await providers().identityProvider.verifyAccessToken(token);
    const companyId = String(claims.companyId || '');
    const userId = String(claims.userId || '');
    if (!companyId || !userId) return next(new Error('AUTH_INVALID_TOKEN'));
    const sessionId = String(socket.handshake.auth?.sessionId || '');
    if (!/^[A-Za-z0-9\-]{8,64}$/.test(sessionId)) return next(new Error('SESSION_REQUIRED'));
    if (!await providers().sessionStore.validate(companyId, userId, sessionId)) return next(new Error('SESSION_REVOKED'));
    socket.data.companyId = companyId;
    socket.data.userId = userId;
    socket.data.sessionId = sessionId;
    return next();
  } catch {
    return next(new Error('AUTH_INVALID_TOKEN'));
  }
});

io.on('connection', (socket) => {
  socket.join(userRoom(socket.data.userId));
  socket.join(sessionRoom(socket.data.sessionId));
  socket.on('join_company', (companyId) => {
    if (typeof companyId !== 'string' || companyId !== socket.data.companyId) {
      socket.emit('error', { code: 'TENANT_MISMATCH' });
      return;
    }
    socket.join(companyId);
  });
});

app.disable('x-powered-by');
app.use(requestContext);
app.use(helmet());
app.use(cors(corsOptions));
app.use(express.json({ limit: '10mb' }));

app.use('/api/v1/sync', syncRoutes);
app.use('/api/v1', accountRoutes);
app.use('/api/v1/backups', backupRoutes);
app.use('/api/v1/license', licenseRoutes);
app.use('/api/v1/sessions', sessionRoutes);
app.use('/api/v1/admin', adminRoutes);
app.use('/api/v1/otp', otpRoutes);

app.get('/health', (_req, res) => {
  res.status(200).json({ status: 'ok', mode: providers().mode, timestamp: new Date().toISOString() });
});

app.use(notFoundHandler);
app.use(errorHandler);

if (require.main === module) {
  // Fail fast on misconfiguration instead of serving requests that would 500 later.
  providers();
  httpServer.listen(port, () => {
    console.log(`Server is running on port ${port} (provider=${providers().mode}, env=${process.env.NODE_ENV || 'development'})`);
  });
}
