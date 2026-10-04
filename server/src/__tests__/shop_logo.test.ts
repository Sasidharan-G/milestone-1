import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { AddressInfo } from 'node:net';

process.env.RESET_SECRET = 'test-only-server-secret-12345678901234567890';
process.env.NODE_ENV = 'development';
// An isolated data directory, so this never touches the dev server's seeded state.
process.env.LOCAL_DATA_DIR = fs.mkdtempSync(path.join(os.tmpdir(), 'kadakutty-shop-logo-'));

const start = async () => {
  const { httpServer } = await import('../index');
  const { providers } = await import('../providers/providerRegistry');
  await new Promise<void>(resolve => httpServer.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${(httpServer.address() as AddressInfo).port}`;
  const stop = () => new Promise<void>((resolve, reject) => httpServer.close(error => error ? reject(error) : resolve()));
  return { base, providers, stop };
};

interface Reply { status: number; json: any; bytes: Buffer; type: string | null }

const call = async (base: string, method: string, url: string, headers: Record<string, string> = {}, body?: BodyInit): Promise<Reply> => {
  const response = await fetch(`${base}${url}`, { method, headers, body });
  const bytes = Buffer.from(await response.arrayBuffer());
  let json: any; try { json = JSON.parse(bytes.toString('utf8')); } catch { json = undefined; }
  return { status: response.status, json, bytes, type: response.headers.get('content-type') };
};
const asJson = (headers: Record<string, string>) => ({ 'content-type': 'application/json', ...headers });

/** A real shop owner (or cashier): account, sign-in, and the device session every authenticated call needs. */
const signIn = async (base: string, phone: string, deviceId: string) => {
  const login = await call(base, 'POST', '/api/v1/auth/login', { 'content-type': 'application/json' }, JSON.stringify({ username: phone, password: '123456' }));
  assert.equal(login.status, 200);
  const token = login.json.tokens.accessToken as string;
  const session = await call(base, 'POST', '/api/v1/sessions/register', { authorization: `Bearer ${token}`, 'content-type': 'application/json' }, JSON.stringify({ deviceId }));
  assert.equal(session.status, 201);
  return { authorization: `Bearer ${token}`, 'x-session-id': session.json.session.sessionId as string };
};
const owner = async (base: string, providers: any, phone: string, shop: string) => {
  const account = await providers().dataStore.createAccount({ phone, displayName: 'Owner', businessName: shop, password: '123456' });
  await providers().identityProvider.setPassword(account.user.userId, '123456');
  return { account, headers: await signIn(base, phone, `device-${phone}`) };
};

// 8-byte PNG signature + filler: enough for the server to see "this is a PNG".
const png = (extra = 40) => Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), Buffer.alloc(extra, 7)]);
const jpeg = () => Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.alloc(40, 9)]);
const put = (base: string, headers: Record<string, string>, body: Buffer, type = 'image/png') =>
  call(base, 'PUT', '/api/v1/account/shop-logo', { ...headers, 'content-type': type }, body as unknown as BodyInit);

test('a shop owner saves a logo, every device reads it back, and the profile carries its version', async () => {
  const { base, providers, stop } = await start();
  try {
    const shop = await owner(base, providers, '9876540001', 'Logo Stores');
    const { companyId } = shop.account.user;

    const none = await call(base, 'GET', '/api/v1/account/shop-logo', shop.headers);
    assert.equal(none.status, 404);
    assert.equal(none.json.error.code, 'SHOP_LOGO_NOT_FOUND');

    const logo = png();
    const saved = await put(base, shop.headers, logo);
    assert.equal(saved.status, 200);
    assert.ok(saved.json.shopProfile.logoUpdatedAtEpochMs > 0, 'the profile says when the logo changed');
    assert.ok(saved.json.shopProfile.logoObjectKey, 'and where it is kept');

    const back = await call(base, 'GET', '/api/v1/account/shop-logo', shop.headers);
    assert.equal(back.status, 200);
    assert.match(back.type || '', /image\/png/);
    assert.deepEqual(back.bytes, logo);

    // The app learns about the logo from the profile it already fetches: no extra call on every start.
    const license = await call(base, 'GET', '/api/v1/license/current', shop.headers);
    assert.equal(license.status, 200);
    assert.equal(license.json.shopProfile.logoUpdatedAtEpochMs, saved.json.shopProfile.logoUpdatedAtEpochMs);

    // Saving the other shop details afterwards must not forget the logo.
    const details = await call(base, 'PATCH', '/api/v1/account/shop-profile', asJson(shop.headers), JSON.stringify({ shopName: 'Logo Stores 2', ownerName: 'Owner' }));
    assert.equal(details.status, 200);
    assert.equal(details.json.shopProfile.logoUpdatedAtEpochMs, saved.json.shopProfile.logoUpdatedAtEpochMs);
    assert.ok(details.json.shopProfile.logoObjectKey);
    assert.equal((await call(base, 'GET', '/api/v1/account/shop-logo', shop.headers)).status, 200);
    assert.ok(companyId);
  } finally { await stop(); }
});

test('replacing the logo moves its version forward and a JPEG is served as a JPEG', async () => {
  const { base, providers, stop } = await start();
  try {
    const shop = await owner(base, providers, '9876540002', 'Replace Stores');
    const first = await put(base, shop.headers, png());
    await new Promise(resolve => setTimeout(resolve, 5));
    const second = await put(base, shop.headers, jpeg(), 'image/jpeg');
    assert.equal(second.status, 200);
    assert.ok(second.json.shopProfile.logoUpdatedAtEpochMs > first.json.shopProfile.logoUpdatedAtEpochMs);
    const back = await call(base, 'GET', '/api/v1/account/shop-logo', shop.headers);
    assert.match(back.type || '', /image\/jpeg/);
    assert.deepEqual(back.bytes, jpeg());
  } finally { await stop(); }
});

test('only real, small PNG or JPEG pictures are accepted', async () => {
  const { base, providers, stop } = await start();
  try {
    const shop = await owner(base, providers, '9876540003', 'Strict Stores');
    const notAPicture = await put(base, shop.headers, Buffer.from('<?php echo 1; ?> this is not a picture at all'));
    assert.equal(notAPicture.status, 400);
    assert.equal(notAPicture.json.error.code, 'SHOP_LOGO_INVALID');

    const wrongType = await put(base, shop.headers, png(), 'text/plain');
    assert.equal(wrongType.status, 400);
    assert.equal(wrongType.json.error.code, 'SHOP_LOGO_REQUIRED');

    const empty = await put(base, shop.headers, Buffer.alloc(0));
    assert.equal(empty.status, 400);

    const tooBig = await put(base, shop.headers, png(2 * 1024 * 1024 + 10));
    assert.equal(tooBig.status, 413);

    const exactlyAtTheLimit = await put(base, shop.headers, png(2 * 1024 * 1024 - 8));
    assert.equal(exactlyAtTheLimit.status, 200);
  } finally { await stop(); }
});

test('a shop can only see and change its own logo, and a cashier can look but not change', async () => {
  const { base, providers, stop } = await start();
  try {
    const first = await owner(base, providers, '9876540004', 'First Stores');
    const second = await owner(base, providers, '9876540005', 'Second Stores');
    assert.equal((await put(base, first.headers, png(10))).status, 200);

    // Another shop sees nothing of it, and its own logo is its own.
    assert.equal((await call(base, 'GET', '/api/v1/account/shop-logo', second.headers)).status, 404);
    assert.equal((await put(base, second.headers, png(20))).status, 200);
    assert.deepEqual((await call(base, 'GET', '/api/v1/account/shop-logo', first.headers)).bytes, png(10));
    assert.deepEqual((await call(base, 'GET', '/api/v1/account/shop-logo', second.headers)).bytes, png(20));

    // A cashier of the first shop: sees the shop's logo (the bill header), cannot replace or remove it.
    const staff = await providers().dataStore.createStaff({ companyId: first.account.user.companyId, phone: '9876540006', displayName: 'Cashier', password: '123456', permissions: ['BILLING'] });
    await providers().identityProvider.setPassword(staff.userId, '123456');
    const cashier = await signIn(base, '9876540006', 'device-cashier-1');
    const seen = await call(base, 'GET', '/api/v1/account/shop-logo', cashier);
    assert.equal(seen.status, 200);
    assert.deepEqual(seen.bytes, png(10));
    assert.equal((await put(base, cashier, png(30))).status, 403);
    assert.equal((await call(base, 'DELETE', '/api/v1/account/shop-logo', cashier)).status, 403);
    assert.deepEqual((await call(base, 'GET', '/api/v1/account/shop-logo', first.headers)).bytes, png(10), 'unchanged by the refused cashier');
  } finally { await stop(); }
});

test('removing the logo clears it, moves the version, and is safe to repeat', async () => {
  const { base, providers, stop } = await start();
  try {
    const shop = await owner(base, providers, '9876540007', 'Remove Stores');
    const saved = await put(base, shop.headers, png());
    await new Promise(resolve => setTimeout(resolve, 5));
    const removed = await call(base, 'DELETE', '/api/v1/account/shop-logo', shop.headers);
    assert.equal(removed.status, 200);
    assert.ok(removed.json.shopProfile.logoUpdatedAtEpochMs > saved.json.shopProfile.logoUpdatedAtEpochMs, 'other devices notice the change');
    assert.ok(!removed.json.shopProfile.logoObjectKey, 'no logo is recorded');
    assert.equal((await call(base, 'GET', '/api/v1/account/shop-logo', shop.headers)).status, 404);
    assert.equal((await call(base, 'DELETE', '/api/v1/account/shop-logo', shop.headers)).status, 200, 'removing a missing logo is not an error');
  } finally { await stop(); }
});

test('the logo needs a signed-in device', async () => {
  const { base, stop } = await start();
  try {
    assert.equal((await call(base, 'GET', '/api/v1/account/shop-logo')).status, 401);
    assert.equal((await put(base, {}, png())).status, 401);
    assert.equal((await call(base, 'DELETE', '/api/v1/account/shop-logo')).status, 401);
  } finally { await stop(); }
});

test('deleting the shop deletes its logo with it', async () => {
  const { base, providers, stop } = await start();
  try {
    const shop = await owner(base, providers, '9876540008', 'Gone Stores');
    const { companyId } = shop.account.user;
    assert.equal((await put(base, shop.headers, png())).status, 200);
    await providers().objectStorage.readShopLogo(companyId); // there

    const deleted = await call(base, 'DELETE', '/api/v1/account', asJson(shop.headers), JSON.stringify({ confirmation: 'DELETE', password: '123456' }));
    assert.equal(deleted.status, 200);
    await assert.rejects(() => providers().objectStorage.readShopLogo(companyId), (error: any) => error.code === 'SHOP_LOGO_NOT_FOUND');
  } finally { await stop(); }
});
