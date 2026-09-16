#!/usr/bin/env bash
# Human-style end-to-end walkthrough against a local server (PROVIDER_MODE=local).
# Exercises: OTP -> register -> login -> session (single-device) -> sync -> staff ->
# forgot password -> master login -> license grant/extend/revoke -> renewal warning -> audit.
set -u
B=${BASE:-http://localhost:3000/api/v1}
OTP=${OTP:-123456}
# Randomized per run so repeated executions don't trip the real OTP send rate limiter (5/10min/phone).
PH=${PH:-98$(printf '%08d' $(( (RANDOM * 32768 + RANDOM) % 100000000 )))}
echo "Using test phone: $PH"
FAIL=0
step(){ echo; echo "=== $1"; }
expect(){ # expect <description> <actual_json> <jq-ish python expr on d> <expected>
  local desc="$1" json="$2" expr="$3" expected="$4"
  local actual; actual=$(python -c "import sys,json
d=json.loads('''$json''')
print($expr)" 2>/dev/null)
  if [ "$actual" == "$expected" ]; then echo "PASS: $desc (got $actual)"; else echo "FAIL: $desc (expected $expected, got $actual)"; FAIL=1; fi
}
j(){ python -c "import sys,json
d=json.load(sys.stdin)
for k in sys.argv[1].split('.'): d=d[k]
print(d,end='')" "$1" 2>/dev/null; }
post(){ curl -s -X POST "$B/$1" -H 'Content-Type: application/json' ${TOKEN:+-H "Authorization: Bearer $TOKEN"} ${SID:+-H "X-Session-Id: $SID"} -d "$2"; }
patchreq(){ curl -s -X PATCH "$B/$1" -H 'Content-Type: application/json' ${TOKEN:+-H "Authorization: Bearer $TOKEN"} ${SID:+-H "X-Session-Id: $SID"} -d "$2"; }
get(){ curl -s "$B/$1" ${TOKEN:+-H "Authorization: Bearer $TOKEN"} ${SID:+-H "X-Session-Id: $SID"}; }

step "1 OTP send"; R=$(post otp/send "{\"mobileNumber\":\"$PH\"}"); echo "$R"; REQ=$(echo "$R" | j "requestId")
step "2 OTP verify wrong code (must fail)"; R2=$(post otp/verify "{\"mobileNumber\":\"$PH\",\"otp\":\"000000\",\"requestId\":\"$REQ\"}"); echo "$R2"; expect "wrong OTP rejected" "$R2" "d.get('error',{}).get('code')" "OTP_CODE_INVALID"
step "3 OTP verify right code"; R=$(post otp/verify "{\"mobileNumber\":\"$PH\",\"otp\":\"$OTP\",\"requestId\":\"$REQ\"}"); echo "$R"; RT=$(echo "$R" | j "resetToken")
step "4 Register admin"; R=$(post auth/register "{\"mobileNumber\":\"$PH\",\"ownerName\":\"Test Owner\",\"businessName\":\"Test Shop\",\"password\":\"Pass@1234\",\"resetToken\":\"$RT\"}"); echo "$R"; CID=$(echo "$R" | j "companyId")
expect "trial license granted" "$R" "d['license']['status']" "TRIAL"
step "5 Register again same OTP proof (must fail: proof already used)"; R=$(post auth/register "{\"mobileNumber\":\"$PH\",\"ownerName\":\"X\",\"businessName\":\"Y\",\"password\":\"Pass@1234\",\"resetToken\":\"$RT\"}"); echo "$R"
step "6 Login wrong password"; R=$(post auth/login "{\"username\":\"$PH\",\"password\":\"wrong\"}"); echo "$R"; expect "wrong password rejected" "$R" "d.get('success')" "None"
step "7 Login ok"; R=$(post auth/login "{\"username\":\"$PH\",\"password\":\"Pass@1234\"}"); echo "$R" | head -c 300; echo; TOKEN=$(echo "$R" | j "tokens.accessToken")
step "8 /account/me without session (must 401)"; get account/me; echo
step "9 Session register device A"; R=$(post sessions/register '{"deviceId":"device-A-001","deviceName":"Pixel A"}'); echo "$R"; SIDA=$(echo "$R" | j "session.sessionId")
step "10 Heartbeat device A works"; SID=$SIDA; R=$(post sessions/heartbeat "{\"sessionId\":\"$SIDA\"}"); echo "$R"; expect "device A heartbeat ok" "$R" "d.get('success')" "True"
step "11 Session register device B (same user, second login)"; R=$(post sessions/register '{"deviceId":"device-B-002","deviceName":"Pixel B"}'); echo "$R"; SIDB=$(echo "$R" | j "session.sessionId")
expect "device B registration revoked device A" "$R" "d['revokedSessions']" "1"
step "12 Heartbeat device A now (must fail: revoked by device B login)"; SID=$SIDA; R=$(post sessions/heartbeat "{\"sessionId\":\"$SIDA\"}"); echo "$R"; expect "device A session revoked" "$R" "d.get('success')" "None"
step "13 Heartbeat device B (must succeed)"; SID=$SIDB; R=$(post sessions/heartbeat "{\"sessionId\":\"$SIDB\"}"); echo "$R"; expect "device B still valid" "$R" "d.get('success')" "True"
SID=$SIDB
step "14 License current + renewal-warning flag"; get license/current; echo
step "15 Sync push product"; R=$(post sync/push "{\"operations\":[{\"operationId\":\"op1\",\"entityType\":\"Product\",\"entityId\":\"p1\",\"operation\":\"UPSERT\",\"schemaVersion\":1,\"payload\":{\"id\":\"p1\",\"companyId\":\"$CID\",\"name\":\"Rice\",\"priceMinorUnits\":5000}}]}"); echo "$R"; expect "sync applied" "$R" "d['results'][0]['status']" "APPLIED"
step "16 Sync push duplicate op (idempotent)"; R=$(post sync/push "{\"operations\":[{\"operationId\":\"op1\",\"entityType\":\"Product\",\"entityId\":\"p1\",\"operation\":\"UPSERT\",\"schemaVersion\":1,\"payload\":{\"id\":\"p1\",\"companyId\":\"$CID\",\"name\":\"Rice\"}}]}"); echo "$R"; expect "duplicate detected" "$R" "d['results'][0]['status']" "DUPLICATE"
step "17 Sync push cross-tenant payload (must 403)"; R=$(post sync/push "{\"operations\":[{\"operationId\":\"op2\",\"entityType\":\"Product\",\"entityId\":\"p2\",\"operation\":\"UPSERT\",\"schemaVersion\":1,\"payload\":{\"id\":\"p2\",\"companyId\":\"other-company\",\"name\":\"Hack\"}}]}"); echo "$R"; expect "cross-tenant payload blocked" "$R" "d.get('error',{}).get('code')" "TENANT_MISMATCH"
step "18 Sync pull"; get "sync/pull?cursor=0&limit=50"; echo
STAFF_PH="97$(printf '%08d' $(( (RANDOM * 32768 + RANDOM) % 100000000 )))"
step "19 Create staff (active immediately, admin controls permissions)"; R=$(post staff "{\"mobileNumber\":\"$STAFF_PH\",\"displayName\":\"Cashier One\",\"password\":\"Staff@123\",\"permissions\":[\"SALE_CREATE\",\"SALE_VIEW\"]}"); echo "$R"; STAFF=$(echo "$R" | j "user.userId")
expect "staff cannot self-grant USER_MANAGE" "$R" "'USER_MANAGE' in d['user']['permissions']" "False"
step "20 Staff login"; R=$(post auth/login "{\"username\":\"$STAFF_PH\",\"password\":\"Staff@123\"}"); echo "$R" | head -c 300; echo; STOKEN=$(echo "$R" | j "tokens.accessToken")
step "21 Staff session register"; R=$(TOKEN=$STOKEN SID= post sessions/register '{"deviceId":"staff-device-01"}'); STSID=$(echo "$R" | j "session.sessionId")
step "22 Staff tries to create staff (must 403 - RBAC)"; R=$(TOKEN=$STOKEN SID=$STSID post staff '{"mobileNumber":"9700000003","displayName":"Evil","password":"Staff@123","permissions":["USER_MANAGE"]}'); echo "$R"; expect "staff blocked from creating staff" "$R" "d.get('error',{}).get('code')" "STAFF_ADMIN_REQUIRED"
step "23 Staff tries admin overview (must 403)"; R=$(TOKEN=$STOKEN SID=$STSID get admin/overview); echo "$R"; expect "staff blocked from admin overview" "$R" "d.get('error',{}).get('code')" "PLATFORM_ADMIN_REQUIRED"
step "24 Deactivate staff"; R=$(curl -s -X DELETE "$B/staff/$STAFF" -H "Authorization: Bearer $TOKEN" -H "X-Session-Id: $SID"); echo "$R"
step "25 Deactivated staff heartbeat now dead (must fail)"; R=$(TOKEN=$STOKEN SID=$STSID post sessions/heartbeat "{\"sessionId\":\"$STSID\"}"); echo "$R"; expect "deactivated staff session killed instantly" "$R" "d.get('success')" "None"
step "26 Forgot password admin"; sleep 21  # clear the 20s anti-abuse cooldown between OTP sends to the same phone
R=$(post otp/send "{\"mobileNumber\":\"$PH\"}"); REQ=$(echo "$R" | j "requestId")
R=$(post otp/verify "{\"mobileNumber\":\"$PH\",\"otp\":\"$OTP\",\"requestId\":\"$REQ\"}"); RT=$(echo "$R" | j "resetToken")
R=$(post auth/password/reset "{\"mobileNumber\":\"$PH\",\"password\":\"NewPass@123\",\"resetToken\":\"$RT\"}"); echo "$R"
expect "forgot-password reset succeeded" "$R" "d.get('success')" "True"
step "27 Old admin session dead after password reset (must fail)"; R=$(get account/me); echo "$R"; expect "old session killed by password reset" "$R" "d.get('success')" "None"
step "28 Login with new password + new session"; R=$(post auth/login "{\"username\":\"$PH\",\"password\":\"NewPass@123\"}"); TOKEN=$(echo "$R" | j "tokens.accessToken"); R=$(post sessions/register '{"deviceId":"device-A-001-again"}'); SID=$(echo "$R" | j "session.sessionId"); echo "logged in fresh, sid=$SID"
step "29 Master login wrong pin"; R=$(TOKEN= SID= post auth/master/login '{"pin":"000000","deviceId":"master-dev-1"}'); echo "$R"; expect "wrong master pin rejected" "$R" "d.get('success')" "None"
step "30 Master login ok"; R=$(TOKEN= SID= post auth/master/login '{"pin":"123456"}'); echo "$R" | head -c 300; echo; MTOKEN=$(echo "$R" | j "tokens.accessToken")
step "30b Master session register (universal endpoint, same as tenant)"; R=$(TOKEN=$MTOKEN SID= post sessions/register '{"deviceId":"master-dev-1","deviceName":"Master Laptop"}'); echo "$R"; MSID=$(echo "$R" | j "session.sessionId")
step "31 Master overview"; R=$(TOKEN=$MTOKEN SID=$MSID get admin/overview); echo "$R" | head -c 500; echo
step "32 Master grant custom 30-day license"; R=$(TOKEN=$MTOKEN SID=$MSID patchreq "admin/licenses/$CID" '{"action":"GRANT_DAYS","days":30,"notes":"paid plan"}'); echo "$R"; expect "license active_paid" "$R" "d['license']['status']" "ACTIVE_PAID"
step "33 Master extend license by 5 days"; R=$(TOKEN=$MTOKEN SID=$MSID patchreq "admin/licenses/$CID" '{"action":"EXTEND_DAYS","days":5}'); echo "$R"
step "34 Master revoke license"; R=$(TOKEN=$MTOKEN SID=$MSID patchreq "admin/licenses/$CID" '{"action":"REVOKE"}'); echo "$R"; expect "license revoked" "$R" "d['license']['status']" "REVOKED"
step "35 Admin license after revoke shows REVOKED"; R=$(get license/current); echo "$R"; expect "admin sees revoked" "$R" "d['license']['status']" "REVOKED"
step "36 Admin sync push after revoke (must be blocked, zero grace)"; R=$(post sync/push "{\"operations\":[{\"operationId\":\"op9\",\"entityType\":\"Product\",\"entityId\":\"p9\",\"operation\":\"UPSERT\",\"schemaVersion\":1,\"payload\":{\"id\":\"p9\",\"companyId\":\"$CID\",\"name\":\"AfterRevoke\"}}]}"); echo "$R"; expect "sync blocked after revoke" "$R" "d.get('error',{}).get('code')" "LICENSE_INACTIVE"
step "37 Master re-grants trial"; R=$(TOKEN=$MTOKEN SID=$MSID patchreq "admin/licenses/$CID" '{"action":"TRIAL"}'); echo "$R"
step "38 Backup upload intent (license active again)"; R=$(post backups/upload-intent '{"fileName":"b.zip","sizeBytes":5,"checksumSha256":"2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae","schemaVersion":22}'); echo "$R"; expect "backup intent created" "$R" "d.get('success')" "True"
step "39 Audit log has entries for this company"; R=$(post "staff" '{"mobileNumber":"9876500009","displayName":"Temp","password":"Temp@1234","permissions":[]}'); R=$(get "audit?limit=20"); echo "$R" | head -c 400; echo; expect "audit has entries" "$R" "len(d.get('entries',[])) > 0" "True"
step "40 Master audit trail (platform scope)"; R=$(TOKEN=$MTOKEN SID=$MSID get "admin/audit?limit=20"); echo "$R" | head -c 400; echo

echo
if [ "$FAIL" -eq 0 ]; then echo "ALL CHECKS PASSED"; else echo "SOME CHECKS FAILED"; fi
exit $FAIL
