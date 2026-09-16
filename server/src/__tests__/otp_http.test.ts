import test from 'node:test';
import assert from 'node:assert/strict';
import { sendOtp, verifyOtp } from '../controllers/otpController';

process.env.RESET_SECRET = 'test-only-server-secret-12345678901234567890';
process.env.NODE_ENV = 'development';
process.env.LOCAL_DEV_OTP_CODE = '123456';

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
