import test from 'node:test';
import assert from 'node:assert/strict';
import { limitOtpSend } from '../middleware/rateLimitMiddleware';

const call = (ip: string, mobileNumber: string) => {
  let status = 200;
  const req: any = { body: { mobileNumber }, ip, socket: { remoteAddress: ip }, id: 'test' };
  const res: any = { setHeader: () => res, status: (code: number) => { status = code; return res; }, json: () => res };
  let passed = false;
  limitOtpSend(req, res, () => { passed = true; });
  return { passed, status };
};

test('one address cannot request codes for a run of different numbers (SMS pumping)', () => {
  const ip = '203.0.113.7';
  for (let i = 0; i < 20; i += 1) assert.equal(call(ip, `98765${String(10000 + i)}`).passed, true, `send ${i + 1} should pass`);
  const blocked = call(ip, '9876599999');
  assert.equal(blocked.passed, false);
  assert.equal(blocked.status, 429);
});

test('a different address is not affected by another address hitting its cap', () => {
  assert.equal(call('203.0.113.8', '9123456780').passed, true);
});

test('the same number still gets its own 20 second cooldown', () => {
  assert.equal(call('203.0.113.9', '9000000001').passed, true);
  assert.equal(call('203.0.113.9', '9000000001').passed, false);
});
