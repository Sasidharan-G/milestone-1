import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import dotenv from 'dotenv';
import * as admin from 'firebase-admin';
import { createServer } from 'http';
import { Server } from 'socket.io';

dotenv.config();

// Initialize Firebase Admin (requires FIREBASE_SERVICE_ACCOUNT_KEY or GOOGLE_APPLICATION_CREDENTIALS)
if (process.env.GOOGLE_APPLICATION_CREDENTIALS || process.env.FIREBASE_SERVICE_ACCOUNT_KEY) {
  try {
    admin.initializeApp({
      credential: admin.credential.applicationDefault()
    });
    console.log('Firebase Admin initialized successfully.');
  } catch (error) {
    console.error('Failed to initialize Firebase Admin:', error);
  }
} else {
  console.warn('Warning: Firebase Admin not initialized. Provide credentials in .env');
}

import syncRoutes from './routes/syncRoutes';

const app = express();
const port = process.env.PORT || 3000;

const httpServer = createServer(app);
const io = new Server(httpServer, {
  cors: {
    origin: '*',
  }
});

app.set('io', io);

io.on('connection', (socket) => {
  console.log('Client connected:', socket.id);
  
  socket.on('join_company', (companyId) => {
    socket.join(companyId);
    console.log(`Socket ${socket.id} joined company room: ${companyId}`);
  });

  socket.on('disconnect', () => {
    console.log('Client disconnected:', socket.id);
  });
});

app.use(helmet());
app.use(cors());
app.use(express.json({ limit: '10mb' }));

app.use('/api/v1/sync', syncRoutes);

app.get('/health', (req, res) => {
  res.status(200).json({ status: 'ok', timestamp: new Date().toISOString() });
});

httpServer.listen(port, () => {
  console.log(`Server is running on port ${port}`);
});
