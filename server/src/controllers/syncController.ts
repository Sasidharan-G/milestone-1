import { Response } from 'express';
import { AuthenticatedRequest } from '../middleware/authMiddleware';
import * as admin from 'firebase-admin';

export const pushSync = async (req: AuthenticatedRequest, res: Response) => {
  try {
    const db = admin.firestore();
    const user = req.user;
    if (!user) {
      return res.status(401).json({ error: 'Unauthorized' });
    }

    const { companyId, entityType, entityId, operation, payload } = req.body;

    // Optional: Validate that the logged-in user belongs to this companyId
    // ...

    const collectionName = getCollectionName(entityType);
    if (!collectionName) {
      return res.status(400).json({ error: 'Invalid entity type' });
    }

    const docRef = db.collection('users').doc(companyId).collection(collectionName).doc(entityId);

    if (operation === 'DELETE') {
      await docRef.delete();
    } else {
      let parsedPayload = payload;
      if (typeof payload === 'string') {
        parsedPayload = JSON.parse(payload);
      }

      // Special handling for Sales/Purchases items subcollections
      if (entityType === 'Sale' || entityType === 'Purchase') {
        const items = parsedPayload.items;
        const mainDoc = { ...parsedPayload };
        delete mainDoc.items;

        await docRef.set(mainDoc);

        if (Array.isArray(items)) {
          const itemsCollection = docRef.collection('items');
          // Clear existing items (basic approach, could be improved)
          const existingItems = await itemsCollection.get();
          const batch = db.batch();
          existingItems.forEach((doc) => batch.delete(doc.ref));
          
          items.forEach((item) => {
            const newItemRef = itemsCollection.doc();
            batch.set(newItemRef, item);
          });
          
          await batch.commit();
        }
      } else {
        await docRef.set(parsedPayload);
      }
    }

    const io = req.app.get('io');
    if (io) {
      io.to(companyId).emit('data_changed', { entityType, entityId, operation, timestamp: Date.now() });
    }

    return res.status(200).json({ success: true, message: 'Sync successful' });
  } catch (error) {
    console.error('Sync push error:', error);
    return res.status(500).json({ error: 'Internal server error' });
  }
};

const getCollectionName = (entityType: string): string | null => {
  const map: Record<string, string> = {
    Category: 'categories',
    Product: 'products',
    Customer: 'customers',
    Supplier: 'suppliers',
    Expense: 'expenses',
    Sale: 'sales',
    Purchase: 'purchases',
    CustomerCredit: 'customer_credits',
    SupplierCredit: 'supplier_credits'
  };
  return map[entityType] || null;
};

export const pullSync = async (req: AuthenticatedRequest, res: Response) => {
  try {
    const db = admin.firestore();
    const user = req.user;
    if (!user) {
      return res.status(401).json({ error: 'Unauthorized' });
    }

    const { companyId, lastSyncTimestamp } = req.query;
    if (!companyId || !lastSyncTimestamp) {
      return res.status(400).json({ error: 'Missing companyId or lastSyncTimestamp' });
    }

    const timestamp = parseInt(lastSyncTimestamp as string, 10);
    if (isNaN(timestamp)) {
      return res.status(400).json({ error: 'Invalid timestamp format' });
    }

    const collectionsToSync = [
      'Category', 'Product', 'Customer', 'Supplier', 
      'Expense', 'Sale', 'Purchase', 'CustomerCredit', 'SupplierCredit'
    ];

    const deltaData: Record<string, any[]> = {};

    for (const entityType of collectionsToSync) {
      const collectionName = getCollectionName(entityType);
      if (!collectionName) continue;

      const querySnapshot = await db.collection('users').doc(companyId as string)
        .collection(collectionName)
        .where('updatedAtEpochMs', '>', timestamp)
        .get();

      const items: any[] = [];
      // Use standard for loop to allow await inside
      for (let i = 0; i < querySnapshot.docs.length; i++) {
        const doc = querySnapshot.docs[i];
        const data = doc.data();
        
        // If it's Sale or Purchase, we also need to fetch its nested items collection
        if (entityType === 'Sale' || entityType === 'Purchase') {
            const itemsSnapshot = await doc.ref.collection('items').get();
            const nestedItems = itemsSnapshot.docs.map(iDoc => iDoc.data());
            data.items = nestedItems;
        }
        items.push(data);
      }
      
      if (items.length > 0) {
        deltaData[entityType] = items;
      }
    }

    return res.status(200).json({ success: true, data: deltaData, timestamp: Date.now() });

  } catch (error) {
    console.error('Sync pull error:', error);
    return res.status(500).json({ error: 'Internal server error' });
  }
};
