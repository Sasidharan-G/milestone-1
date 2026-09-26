import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { AddressInfo } from 'node:net';

process.env.RESET_SECRET = 'test-only-server-secret-12345678901234567890';
process.env.NODE_ENV = 'development';
// An isolated data directory, so this never touches the dev server's seeded state.
process.env.LOCAL_DATA_DIR = fs.mkdtempSync(path.join(os.tmpdir(), 'kadakutty-account-delete-'));
process.env.LEGAL_ENTITY_NAME = 'Test <Publisher>';

const start = async () => {
  const { httpServer } = await import('../index');
  const { providers } = await import('../providers/providerRegistry');
  await new Promise<void>(resolve => httpServer.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${(httpServer.address() as AddressInfo).port}`;
  const stop = () => new Promise<void>((resolve, reject) => httpServer.close(error => error ? reject(error) : resolve()));
  return { base, providers, stop };
};

const call = async (base: string, method: string, url: string, headers: Record<string, string> = {}, body?: object) => {
  const response = await fetch(`${base}${url}`, { method, headers: { 'content-type': 'application/json', ...headers }, body: body ? JSON.stringify(body) : undefined });
  const text = await response.text();
  let json: any; try { json = JSON.parse(text); } catch { json = undefined; }
  return { status: response.status, json, text };
};

/** A real shop owner: account, sign-in, and the device session every authenticated call needs. */
const signedInOwner = async (base: string, providers: any, phone: string) => {
  const account = await providers().dataStore.createAccount({ phone, displayName: 'Owner', businessName: 'Delete Me Stores', password: '123456' });
  await providers().identityProvider.setPassword(account.user.userId, '123456');
  const login = await call(base, 'POST', '/api/v1/auth/login', {}, { username: phone, password: '123456' });
  assert.equal(login.status, 200);
  const token = login.json.tokens.accessToken as string;
  const session = await call(base, 'POST', '/api/v1/sessions/register', { authorization: `Bearer ${token}` }, { deviceId: 'device-test-0001' });
  assert.equal(session.status, 201);
  return { account, headers: { authorization: `Bearer ${token}`, 'x-session-id': session.json.session.sessionId as string } };
};

test('the owner can delete their account with the PIN, and everything of the shop is gone', async () => {
  const { base, providers, stop } = await start();
  try {
    const owner = await signedInOwner(base, providers, '9876543210');
    const { companyId, userId } = owner.account.user;

    // Guards: no confirmation, no PIN, a wrong PIN - each refused, nothing deleted.
    assert.equal((await call(base, 'DELETE', '/api/v1/account', owner.headers, { password: '123456' })).status, 400);
    assert.equal((await call(base, 'DELETE', '/api/v1/account', owner.headers, { confirmation: 'DELETE' })).status, 400);
    const wrongPin = await call(base, 'DELETE', '/api/v1/account', owner.headers, { confirmation: 'DELETE', password: '654321' });
    // Not 401: the app retries a 401 on a signed-in call as an expired token.
    assert.equal(wrongPin.status, 403);
    assert.equal(wrongPin.json.error.code, 'ACCOUNT_DELETE_PIN_INVALID');
    assert.ok(await providers().dataStore.findUserById(userId), 'a refused request must not delete anything');

    const deleted = await call(base, 'DELETE', '/api/v1/account', owner.headers, { confirmation: 'DELETE', password: '123456' });
    assert.equal(deleted.status, 200);
    assert.equal(await providers().dataStore.findUserById(userId), null);
    assert.equal(await providers().dataStore.getLicense(companyId), null);
    assert.equal(await providers().dataStore.findUserByPhone('9876543210'), null, 'the phone number is released');

    // The old session is dead, and the number can sign up again.
    const after = await call(base, 'GET', '/api/v1/account/me', owner.headers);
    assert.ok([401, 403, 404].includes(after.status));
    const relogin = await call(base, 'POST', '/api/v1/auth/login', {}, { username: '9876543210', password: '123456' });
    assert.notEqual(relogin.status, 200);
  } finally { await stop(); }
});

test('a cashier cannot delete the shop, and other shops are untouched', async () => {
  const { base, providers, stop } = await start();
  try {
    const owner = await signedInOwner(base, providers, '9876543211');
    const other = await signedInOwner(base, providers, '9876543212');
    const staff = await providers().dataStore.createStaff({ companyId: owner.account.user.companyId, phone: '9876543213', displayName: 'Cashier', password: '123456', permissions: ['BILLING'] });
    await providers().identityProvider.setPassword(staff.userId, '123456');
    const login = await call(base, 'POST', '/api/v1/auth/login', {}, { username: '9876543213', password: '123456' });
    const token = login.json.tokens.accessToken as string;
    const session = await call(base, 'POST', '/api/v1/sessions/register', { authorization: `Bearer ${token}` }, { deviceId: 'device-test-0002' });
    const cashier = { authorization: `Bearer ${token}`, 'x-session-id': session.json.session.sessionId as string };

    const refused = await call(base, 'DELETE', '/api/v1/account', cashier, { confirmation: 'DELETE', password: '123456' });
    assert.equal(refused.status, 403);
    assert.ok(await providers().dataStore.getLicense(owner.account.user.companyId));

    assert.equal((await call(base, 'DELETE', '/api/v1/account', owner.headers, { confirmation: 'DELETE', password: '123456' })).status, 200);
    assert.ok(await providers().dataStore.findUserById(other.account.user.userId), 'another shop must be untouched');
    assert.ok(await providers().dataStore.getLicense(other.account.user.companyId));
    assert.equal(await providers().dataStore.findUserById(staff.userId), null, 'staff of the deleted shop go with it');
  } finally { await stop(); }
});

test('Play Store web pages are public, self-contained and escape configuration', async () => {
  const { base, stop } = await start();
  try {
    const privacy = await call(base, 'GET', '/privacy-policy');
    assert.equal(privacy.status, 200);
    assert.match(privacy.text, /Privacy Policy/);
    assert.match(privacy.text, /Delete Account/);
    assert.ok(!privacy.text.includes('Test <Publisher>'), 'the publisher name must be HTML-escaped');
    assert.match(privacy.text, /Test &lt;Publisher&gt;/);
    const deletion = await call(base, 'GET', '/account-deletion');
    assert.equal(deletion.status, 200);
    assert.match(deletion.text, /Delete your KadaiKutty POS account/);
    assert.match(deletion.text, /35 days/);
  } finally { await stop(); }
});
