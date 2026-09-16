import test from 'node:test';
import assert from 'node:assert/strict';
import { validAccountInput, validMasterLoginInput } from '../routes/accountRoutes';
test('account creation rejects malformed and oversized values', () => {
    assert.equal(validAccountInput('9876543210', 'Owner', 'secure123'), true);
    assert.equal(validAccountInput('123', 'Owner', 'secure123'), false);
    assert.equal(validAccountInput('9876543210', ' ', 'secure123'), false);
    assert.equal(validAccountInput('9876543210', 'Owner', 'tiny'), false);
    assert.equal(validAccountInput('9876543210', 'Owner', 'x'.repeat(257)), false);
});

test('master login accepts configured PIN format only', () => {
    assert.equal(validMasterLoginInput(undefined, '112233'), true);
    assert.equal(validMasterLoginInput('9962255661', '123456789012'), true);
    assert.equal(validMasterLoginInput('1234567890', '112233'), true);
    assert.equal(validMasterLoginInput('9962255661', '12345'), false);
    assert.equal(validMasterLoginInput('9962255661', 'abcdef'), false);
});
