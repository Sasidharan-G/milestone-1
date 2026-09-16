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

// Sockets must present the same bearer token as the REST API; the room a socket may join
// is derived from the token so a client can never subscribe to another tenant's changes.
io.use(async (socket, next) => {
  try {
    const raw = socket.handshake.auth?.token || socket.handshake.headers.authorization || '';
    const token = String(raw).replace(/^Bearer\s+/i, '').trim();
    if (!token) return next(new Error('AUTH_REQUIRED'));
    const claims = await providers().identityProvider.verifyAccessToken(token);
    socket.data.companyId = String(claims.companyId || '');
    socket.data.userId = String(claims.userId || '');
    return next();
  } catch {
    return next(new Error('AUTH_INVALID_TOKEN'));
  }
});

io.on('connection', (socket) => {
  socket.on('join_company', (companyId) => {
    if (typeof companyId !== 'string' || companyId !== socket.data.companyId) {
      socket.emit('error', { code: 'TENANT_MISMATCH' });
      return;
    }
    socket.join(companyId);
  });
});

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

app.get('/health', (req, res) => {
  res.status(200).json({ status: 'ok', timestamp: new Date().toISOString() });
});

if (require.main === module) {
  httpServer.listen(port, () => {
    console.log(`Server is running on port ${port}`);
  });
}
