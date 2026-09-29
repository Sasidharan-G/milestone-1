import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { sendOtp, verifyOtp } from '../controllers/otpController';

process.env.RESET_SECRET = 'test-only-server-secret-12345678901234567890';
process.env.NODE_ENV = 'development';
process.env.LOCAL_DEV_OTP_CODE = '123456';
// This exercises the real sendOtp/verifyOtp handlers, which reach the shared providers() singleton.
// Point it at an isolated temp directory — the default `.local-data` is the same path the dev server
// uses (`npm test && npm run dev` from the same shell is exactly the documented local-dev workflow),
// and this test process never sets MASTER_SUPPORT_PHONE/MASTER_ADMIN_PIN, so letting it fall through to
// that default would silently wipe the dev server's seeded master config on next `npm run dev`.
process.env.LOCAL_DATA_DIR = fs.mkdtempSync(path.join(os.tmpdir(), 'kadakutty-otp-http-'));

const invoke = async (handler: any, body: object) => {
  let code = 200; let result: any;
  await handler({ body } as any, {
    status(value: number) { code = value; return this; },
    json(value: any) { result = value; return this; }
  } as any);
  return { code, result };
};

test('AWS-only OTP endpoint creates an in-app challenge in local development', async () => {
  const result = await invoke(sendOtp, { mobileNumber: '9876543210' });
  assert.equal(result.code, 200);
  assert.equal(result.result.success, true);
  assert.equal(typeof result.result.requestId, 'string');
});

test('OTP proof is mobile-bound, valid once, and returns a short-lived reset proof', async () => {
  const challenge = await invoke(sendOtp, { mobileNumber: '9876543210' });
  const verified = await invoke(verifyOtp, { mobileNumber: '9876543210', otp: '123456', requestId: challenge.result.requestId });
  assert.equal(verified.code, 200);
  assert.equal(typeof verified.result.resetToken, 'string');
  const replay = await invoke(verifyOtp, { mobileNumber: '9876543210', otp: '123456', requestId: challenge.result.requestId });
  assert.notEqual(replay.code, 200);
});

test('a second OTP send for the same phone verifies independently even with a fixed dev code', async () => {
  // Regression: forgot-password right after registration used to fail because the first OTP's
  // nonce was derived from the (identical, dev-fixed) code and had already been burned.
  const first = await invoke(sendOtp, { mobileNumber: '9876500099' });
  const firstVerify = await invoke(verifyOtp, { mobileNumber: '9876500099', otp: '123456', requestId: first.result.requestId });
  assert.equal(firstVerify.code, 200);

  const second = await invoke(sendOtp, { mobileNumber: '9876500099' });
  const secondVerify = await invoke(verifyOtp, { mobileNumber: '9876500099', otp: '123456', requestId: second.result.requestId });
  assert.equal(secondVerify.code, 200);
  assert.equal(typeof secondVerify.result.resetToken, 'string');
});

test('an OTP session dies after five wrong codes, even for the right code afterwards', async () => {
  const challenge = await invoke(sendOtp, { mobileNumber: '9876500123' });
  for (let i = 0; i < 5; i++) {
    const wrong = await invoke(verifyOtp, { mobileNumber: '9876500123', otp: '000000', requestId: challenge.result.requestId });
    assert.equal(wrong.code, 400);
  }
  const locked = await invoke(verifyOtp, { mobileNumber: '9876500123', otp: '123456', requestId: challenge.result.requestId });
  assert.equal(locked.code, 429);

  // A fresh send is a fresh session with its own five attempts.
  const fresh = await invoke(sendOtp, { mobileNumber: '9876500123' });
  const ok = await invoke(verifyOtp, { mobileNumber: '9876500123', otp: '123456', requestId: fresh.result.requestId });
  assert.equal(ok.code, 200);
});
