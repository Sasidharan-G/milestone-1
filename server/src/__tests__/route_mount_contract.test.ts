import test from 'node:test';
import assert from 'node:assert/strict';
import { AddressInfo } from 'node:net';
import { httpServer } from '../index';

test('account and master authentication routes are mounted at the public API paths', async () => {
  await new Promise<void>((resolve) => httpServer.listen(0, '127.0.0.1', resolve));
  try {
    const { port } = httpServer.address() as AddressInfo;
    const options = {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: '{}',
    };
    const login = await fetch(`http://127.0.0.1:${port}/api/v1/auth/login`, options);
    const masterLogin = await fetch(`http://127.0.0.1:${port}/api/v1/auth/master/login`, options);
    assert.equal(login.status, 400);
    assert.equal(masterLogin.status, 400);
  } finally {
    await new Promise<void>((resolve, reject) => httpServer.close(error => error ? reject(error) : resolve()));
  }
});
