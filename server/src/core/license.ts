import { LicenseRecord, LicenseStatus } from '../providers/contracts';

export const TRIAL_DAYS = 2;
export const DAY_MS = 24 * 60 * 60 * 1000;
export const RENEWAL_WARNING_DAYS = 7;

/** A license grants access only while its status is live and the clock has not passed validUntil. Zero grace. */
export const effectiveStatus = (license: LicenseRecord, now = Date.now()): LicenseStatus => {
  if (license.status === 'TRIAL' || license.status === 'ACTIVE_PAID') {
    return license.validUntilEpochMs > now ? license.status : 'EXPIRED';
  }
  return license.status;
};

export const isLicenseActive = (license: LicenseRecord | null, now = Date.now()): boolean =>
  Boolean(license) && ['TRIAL', 'ACTIVE_PAID'].includes(effectiveStatus(license!, now));

/** Presents the license with the expiry already applied so clients never see a stale TRIAL/ACTIVE_PAID. */
export const presentLicense = (license: LicenseRecord, now = Date.now()): LicenseRecord =>
  ({ ...license, status: effectiveStatus(license, now) });

export const newTrialLicense = (companyId: string, ownerMobile: string, businessName: string, ownerName: string, now = Date.now()): LicenseRecord => ({
  companyId, ownerMobile, status: 'TRIAL', licenseType: 'TRIAL_2_DAYS', daysGranted: TRIAL_DAYS, yearsGranted: 0,
  activatedAtEpochMs: now, validUntilEpochMs: now + TRIAL_DAYS * DAY_MS, updatedAtEpochMs: now, businessName, ownerName
});

export type LicenseAction =
  | { action: 'TRIAL' }
  | { action: 'GRANT_DAYS'; days: number; notes?: string }
  | { action: 'GRANT_YEARS'; years: number; notes?: string }
  | { action: 'EXTEND_DAYS'; days: number; notes?: string }
  | { action: 'REVOKE'; notes?: string };

const addYears = (epochMs: number, years: number): number => {
  const date = new Date(epochMs);
  date.setUTCFullYear(date.getUTCFullYear() + years);
  return date.getTime();
};

/** Pure transition used by master control. Extensions start from the later of now and the current validUntil. */
export const applyLicenseAction = (current: LicenseRecord, input: LicenseAction, now = Date.now()): LicenseRecord => {
  const notes = 'notes' in input && input.notes !== undefined ? input.notes : current.notes;
  const base: LicenseRecord = { ...current, updatedAtEpochMs: now, notes };
  switch (input.action) {
    case 'TRIAL':
      return { ...base, status: 'TRIAL', licenseType: 'TRIAL_2_DAYS', daysGranted: TRIAL_DAYS, yearsGranted: 0, activatedAtEpochMs: now, validUntilEpochMs: now + TRIAL_DAYS * DAY_MS };
    case 'GRANT_DAYS':
      return { ...base, status: 'ACTIVE_PAID', licenseType: 'CUSTOM_DAYS', daysGranted: input.days, yearsGranted: 0, activatedAtEpochMs: now, validUntilEpochMs: now + input.days * DAY_MS };
    case 'GRANT_YEARS':
      return { ...base, status: 'ACTIVE_PAID', licenseType: 'YEARLY', daysGranted: 0, yearsGranted: input.years, activatedAtEpochMs: now, validUntilEpochMs: addYears(now, input.years) };
    case 'EXTEND_DAYS': {
      const live = isLicenseActive(current, now);
      const from = live ? current.validUntilEpochMs : now;
      return {
        ...base, status: live ? current.status : 'ACTIVE_PAID', licenseType: live ? current.licenseType : 'CUSTOM_DAYS',
        daysGranted: current.daysGranted + input.days, activatedAtEpochMs: live ? current.activatedAtEpochMs : now,
        validUntilEpochMs: from + input.days * DAY_MS
      };
    }
    case 'REVOKE':
      return { ...base, status: 'REVOKED', validUntilEpochMs: 0 };
  }
};

export const parseLicenseAction = (body: any): LicenseAction | null => {
  const action = String(body?.action || '');
  const days = Number(body?.days);
  const years = Number(body?.years);
  const notes = typeof body?.notes === 'string' ? body.notes.slice(0, 500) : undefined;
  if (action === 'TRIAL') return { action };
  if (action === 'REVOKE') return { action, notes };
  if ((action === 'GRANT_DAYS' || action === 'EXTEND_DAYS') && Number.isInteger(days) && days >= 1 && days <= 3660) return { action, days, notes };
  if (action === 'GRANT_YEARS' && Number.isInteger(years) && years >= 1 && years <= 10) return { action, years, notes };
  return null;
};
