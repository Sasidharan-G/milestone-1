import test from 'node:test';
import assert from 'node:assert/strict';
import { validAccountInput, validMasterLoginInput } from '../routes/accountRoutes';
test('account creation rejects malformed and oversized values', () => {
    assert.equal(validAccountInput('9876543210', 'Owner', '123456'), true);
    assert.equal(validAccountInput('123', 'Owner', '123456'), false);
    assert.equal(validAccountInput('9876543210', ' ', '123456'), false);
    assert.equal(validAccountInput('9876543210', 'Owner', '12345'), false);
    assert.equal(validAccountInput('9876543210', 'Owner', 'secure123'), false);
});

test('master login accepts configured PIN format only', () => {
    assert.equal(validMasterLoginInput(undefined, '112233'), true);
    assert.equal(validMasterLoginInput('9962255661', '123456789012'), true);
    assert.equal(validMasterLoginInput('1234567890', '112233'), true);
    assert.equal(validMasterLoginInput('9962255661', '12345'), false);
    assert.equal(validMasterLoginInput('9962255661', 'abcdef'), false);
});

test('the master mobile matches however it was written down, but never an empty or different one', async () => {
  const { sameMasterMobile } = await import('../routes/accountRoutes');
  assert.equal(sameMasterMobile('9789418144', '+919789418144'), true);
  assert.equal(sameMasterMobile('9789418144', '9789418144'), true);
  assert.equal(sameMasterMobile('+91 97894 18144', '919789418144'), true);
  assert.equal(sameMasterMobile('9789418144', '9962255661'), false);
  assert.equal(sameMasterMobile('', ''), false);
  assert.equal(sameMasterMobile(undefined, '+919789418144'), false);
});
