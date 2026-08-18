import { describe, expect, it } from 'vitest';
import { healthRouter } from '../modules/health/router.js';
describe('health route', () => { it('is registered', () => { expect(healthRouter.stack).toHaveLength(1); }); });
