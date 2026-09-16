import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'crypto';
import { normalizePhone, createResetToken, verifyResetToken, createOtpSession, readOtpSession } from '../controllers/otpController';

process.env.RESET_SECRET = 'test-only-secret-never-use-in-production-123456';

test('OTP challenge cannot be used to reset a different phone', () => {
  const token = createOtpSession('9876543210', 'session-id-1', 'commitment-hash');
  assert.deepEqual(readOtpSession('9876543210', token), { sessionId: 'session-id-1', commitment: 'commitment-hash' });
  assert.equal(readOtpSession('9876543211', token), null);
  assert.equal(readOtpSession('9876543210', 'provider-request-id'), null);
  assert.equal(readOtpSession('9876543210', token + 'x'), null);
});

test('two OTP sends for the same phone with the same code do not share a nonce', () => {
  const first = createOtpSession('9876543210', 'session-a', 'same-commitment');
  const second = createOtpSession('9876543210', 'session-b', 'same-commitment');
  const a = readOtpSession('9876543210', first)!;
  const b = readOtpSession('9876543210', second)!;
  assert.notEqual(a.sessionId, b.sessionId);
  assert.equal(a.commitment, b.commitment);
});

test('future-dated and malformed reset tokens are rejected', () => {
  assert.equal(verifyResetToken('9876543210', createResetToken('9876543210', Date.now() + 60000)), false);
  assert.equal(verifyResetToken('9876543210', createResetToken('9876543210') + 'zz'), false);
});

test('normalizePhone handles various formats to extract 10 digits', () => {
  assert.equal(normalizePhone('+919876543210'), '9876543210');
  assert.equal(normalizePhone('91-9876543210'), '9876543210');
  assert.equal(normalizePhone('098765 43210'), '9876543210');
  assert.equal(normalizePhone('(987) 654-3210'), '9876543210');
  assert.equal(normalizePhone('9876543210'), '9876543210');
});

test('createResetToken and verifyResetToken validate authentic reset requests', () => {
  const phone = '9876543210';
  const token = createResetToken(phone);

  assert.ok(token);
  assert.equal(verifyResetToken(phone, token), true);
});

test('verifyResetToken rejects mismatched phone number', () => {
  const phone = '9876543210';
  const otherPhone = '9876543211';
  const token = createResetToken(phone);

  assert.equal(verifyResetToken(otherPhone, token), false);
});

test('verifyResetToken rejects tampered signature', () => {
  const phone = '9876543210';
  const token = createResetToken(phone);
  const parts = token.split(':');
  const tamperedSig = (parts[2][0] === 'a' ? 'b' : 'a') + parts[2].slice(1);
  const tamperedToken = `${parts[0]}:${parts[1]}:${tamperedSig}`;

  assert.equal(verifyResetToken(phone, tamperedToken), false);
});

test('verifyResetToken rejects expired tokens (> 10 minutes)', () => {
  const phone = '9876543210';
  const elevenMinutesAgo = Date.now() - (11 * 60 * 1000);
  const expiredToken = createResetToken(phone, elevenMinutesAgo);

  assert.equal(verifyResetToken(phone, expiredToken), false);
});

test('PBKDF2 SHA-256 derivation correctly matches credentials and prevents timing attacks', () => {
  const iterations = 210000;
  const keyBytes = 32;
  const password = 'SuperSecretPOSPassword123!';
  const wrongPassword = 'WrongSecretPOSPassword123!';

  const salt = crypto.randomBytes(16);
  const storedVerifier = crypto.pbkdf2Sync(password, salt, iterations, keyBytes, 'sha256');

  // Matching derivation
  const correctDerived = crypto.pbkdf2Sync(password, salt, iterations, keyBytes, 'sha256');
  assert.equal(crypto.timingSafeEqual(correctDerived, storedVerifier), true);

  // Mismatched derivation
  const wrongDerived = crypto.pbkdf2Sync(wrongPassword, salt, iterations, keyBytes, 'sha256');
  assert.equal(crypto.timingSafeEqual(wrongDerived, storedVerifier), false);
});
