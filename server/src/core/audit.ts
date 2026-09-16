import type { Request } from 'express';
import { providers } from '../providers/providerRegistry';

interface Actor { userId: string; role: string; companyId: string; }

/** Audit writes never fail the business request; a failure is logged with the request id instead. */
export const audit = async (
  req: Request, actor: Actor, action: string, targetId?: string, details?: Record<string, unknown>, companyId = actor.companyId
): Promise<void> => {
  try {
    await providers().dataStore.appendAudit({ companyId, action, actorUserId: actor.userId, actorRole: actor.role, targetId, details, ip: req.ip });
  } catch (error) {
    console.error(`[${(req as any).id || 'unknown'}] audit write failed for ${action}:`, error instanceof Error ? error.message : error);
  }
};
