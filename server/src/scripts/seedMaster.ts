import dotenv from 'dotenv';
dotenv.config();

import { providers } from '../providers/providerRegistry';

/**
 * Deployment-only script: writes the Super Master mobile + PIN into the configured
 * provider (Secrets Manager + DynamoDB CONFIG#MASTER in aws mode, state.json in local mode).
 *
 * Usage: node dist/scripts/seedMaster.js <10-digit-mobile> <6-12 digit PIN>
 */
const main = async (): Promise<void> => {
  const [mobileArg, pinArg] = process.argv.slice(2);
  const mobile = String(mobileArg || '').replace(/\D/g, '').slice(-10);
  const pin = String(pinArg || '');
  if (!/^[6-9]\d{9}$/.test(mobile)) throw new Error('Master mobile must be a valid 10-digit Indian number');
  if (!/^\d{6}$/.test(pin)) throw new Error('Master PIN must be exactly 6 digits');

  const registry = providers();
  const config = await registry.dataStore.updateMasterConfig({ mobile, pin });
  console.log(`[seed-master] provider=${registry.mode} mobile=${config.mobile} pinConfigured=true updatedAt=${new Date(config.updatedAtEpochMs).toISOString()}`);
};

main().catch(error => {
  console.error('[seed-master] failed:', error instanceof Error ? error.message : error);
  process.exitCode = 1;
});
