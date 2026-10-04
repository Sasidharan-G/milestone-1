import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { AddressInfo } from 'node:net';

process.env.RESET_SECRET = 'test-only-server-secret-12345678901234567890';
process.env.NODE_ENV = 'development';
process.env.LOCAL_DATA_DIR = fs.mkdtempSync(path.join(os.tmpdir(), 'kadakutty-otp-guard-'));

test('a registration code is not sent to a number that already has an account', async () => {
  const { httpServer } = await import('../index');
  const { providers } = await import('../providers/providerRegistry');
  await new Promise<void>(resolve => httpServer.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${(httpServer.address() as AddressInfo).port}`;
  const send = async (mobileNumber: string, purpose?: string) => {
    const response = await fetch(`${base}/api/v1/otp/send`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ mobileNumber, purpose }) });
    return { status: response.status, json: await response.json() as any };
  };
  const sent: string[] = [];
  const sms = providers().smsSender;
  const original = sms.sendOtp.bind(sms);
  sms.sendOtp = async (phone: string, code: string) => { sent.push(phone); return original(phone, code); };
  try {
    await providers().dataStore.createAccount({ phone: '9876541001', displayName: 'Owner', businessName: 'Existing Stores', password: '123456' });
    await providers().dataStore.createAccount({ phone: '9876541003', displayName: 'Owner', businessName: 'Another Stores', password: '123456' });

    const refused = await send('9876541001', 'REGISTER');
    assert.equal(refused.status, 409);
    assert.equal(refused.json.error.code, 'ACCOUNT_ALREADY_EXISTS');
    assert.deepEqual(sent, [], 'no SMS was sent, so no credit was spent');

    const fresh = await send('9876541002', 'REGISTER');
    assert.equal(fresh.status, 200);
    assert.deepEqual(sent, ['9876541002']);

    // Forgot PIN (and older apps, which send no purpose) still get a code for an existing number.
    // (a different number: the same one is held to one code every 20 seconds, refused or not)
    assert.equal((await send('9876541003')).status, 200);
    assert.deepEqual(sent, ['9876541002', '9876541003']);
  } finally {
    sms.sendOtp = original;
    await new Promise<void>((resolve, reject) => httpServer.close(error => error ? reject(error) : resolve()));
  }
});
