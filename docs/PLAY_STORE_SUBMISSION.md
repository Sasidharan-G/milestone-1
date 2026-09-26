# Google Play submission guide - KadaiKutty POS

Prepared 2026-09-24 for build v27 (versionCode 27). Everything here matches what the app and server
actually do today. Have the publisher's legal owner read the privacy policy text
(`server/src/routes/publicPages.ts`) before you publish it: it is a plain-language draft, not legal advice.

## 1. What to upload

| File | Use |
|---|---|
| `artifacts/kadaikutty-pos-v27-play-upload.aab` | The Android App Bundle. Play requires an AAB, not an APK. |
| `artifacts/kadaikutty-pos-v27-r8-mapping.txt` | Upload as the deobfuscation file (Play Console > App bundle explorer) so crash reports are readable. |
| `artifacts/kadaikutty-pos-v27-release-CLIENT.apk` | For direct installs (WhatsApp, etc.), not for Play. |

The bundle is signed with the upload keystore (`KADAIKUTTY_KEYSTORE_FILE`). Keep that keystore and its
passwords backed up somewhere safe: losing the upload key means asking Google to reset it.

## 2. Play App Signing and the integrity check (do this right after the first upload)

Play re-signs the app with its own key, so an install from Play carries a different certificate than
your APK. The app checks its own certificate at startup (`SIGNING_CERT_SHA256`) and refuses to run on a
mismatch, so **without this step every Play install shows "Integrity Failure"**.

1. Upload the first AAB (internal testing track is enough).
2. Play Console > your app > **Test and release > App signing**. Copy the **SHA-256 certificate
   fingerprint** of the *App signing key certificate* (not the upload key certificate).
3. In `android-app/release.properties` set both, comma-separated, and rebuild with a higher versionCode:

   ```
   SIGNING_CERT_SHA256=<your upload key SHA-256>,<Play app signing key SHA-256>
   ```

   Your upload/direct-install key today is
   `A2:0C:10:23:A6:78:DE:FC:52:7D:61:1D:9A:90:06:01:2E:D8:91:4B:FF:99:48:CB:C6:3D:D1:B6:15:5B:48:66`
   (the value already in `release.properties`). Keep it in the list: the APKs you send to clients are
   still signed with it.
4. Upload the rebuilt AAB. Install from the internal test link on a real phone and confirm it opens.

Alternative: when Play offers "use the same key as in your app" (upload your own signing key), the Play
certificate equals yours and no change is needed. That choice cannot be undone later.

## 3. Public URLs for the store listing

The server serves these without sign-in (added in this build):

| Console field | URL |
|---|---|
| Privacy policy | `<backend>/privacy-policy` |
| Account deletion web link (Data safety > Data deletion) | `<backend>/account-deletion` |

`<backend>` is `https://u3bmxkaw25.execute-api.ap-southeast-2.amazonaws.com` today. A URL on your own
domain looks far more trustworthy to reviewers and users; use one if you can get it.

**These pages only exist after the server is redeployed.** Set these on the Elastic Beanstalk
environment so the pages show the right publisher and contact (nothing is invented if they are unset):

| Variable | Meaning |
|---|---|
| `LEGAL_ENTITY_NAME` | Name of the person/company that publishes the app. |
| `SUPPORT_EMAIL` | Public support email. Strongly recommended: Play wants a contact email. |
| `MASTER_SUPPORT_PHONE` | Already set; shown as phone/WhatsApp. |

## 4. Account deletion (Play policy) - built

* In the app: **Settings > Database & Security > Delete Account**. Only the shop owner sees it. It asks
  the owner to type `DELETE` and enter the 6-digit PIN again.
* Server: `DELETE /api/v1/account`. Erases the shop's cloud data, backups, every staff account and all
  sign-in identities, revokes every device, and frees the phone numbers. The phone it was done on is
  wiped as well and returns to the sign-in screen.
* Web: `/account-deletion` explains both this and the request-by-contact route for people who no longer
  have the app.
* Retention stated in the policy: system backups (DynamoDB point-in-time recovery, S3 old versions) age
  out within 35 days; a minimal deletion record (IDs and time, no shop contents) is kept for audit.

## 5. Data safety form - answers

Play Console > App content > Data safety.

**Data collection and security**

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | Yes |
| Is all of the user data collected by your app encrypted in transit? | Yes (HTTPS) |
| Do you provide a way for users to request that their data is deleted? | Yes: in-app + the web link above |

**Data types** (all are *collected*, none is *shared* with third parties: AWS, the SMS provider and
Sentry act only as service providers processing data on your behalf)

| Play category > type | Collected | Optional? | Purpose | Notes |
|---|---|---|---|---|
| Personal info > Name | Yes | Required | App functionality, Account management | Owner, staff, customer and supplier names. |
| Personal info > Phone number | Yes | Required | App functionality, Account management | Account sign-in and OTP; customer/supplier numbers. |
| Personal info > Address | Yes | Optional | App functionality | Shop and customer addresses the user types. |
| Personal info > Email address | Yes | Optional | App functionality | Shop profile email. |
| Financial info > Purchase history / Other financial info | Yes | Required | App functionality | Sales bills, purchases, expenses, amounts owed. This is the shop's own business record. |
| Files and docs > Files and docs | Yes | Optional | App functionality | Only if the user uploads a cloud backup. |
| App info and performance > Crash logs, Diagnostics | Yes | Required | Analytics, App functionality | Sentry. PINs/tokens/OTPs are scrubbed before sending. |
| Device or other IDs > Device or other IDs | Yes | Required | App functionality, Fraud prevention/security | Random install ID for the device session; IP address in audit logs. |

**Not collected:** location, contacts, messages, photos and videos, audio, health, web browsing, calendar,
advertising ID. The camera scans barcodes on the phone only; nothing is stored or uploaded.

## 6. App content declarations

* **App access:** the app needs sign-in. Create a dedicated reviewer account (a real registration needs
  an OTP by SMS, so register one yourself first, on a number you control) and give Play its mobile number
  and PIN, plus these notes: "Sign in with the mobile number and PIN. Bottom of the sign-in screen has a
  *Master Control* entry, which is the operator's console for licensing and needs an operator PIN we do
  not provide; ordinary use does not involve it." Do not put the credentials in this repository.
* **Ads:** No.
* **Target audience:** 18 and over (business software). Not designed for children.
* **Content rating questionnaire:** no violence, sexual content, gambling, user-generated public content,
  or location sharing. Expect "Everyone".
* **News / government / health / finance:** none of these. It is business bookkeeping software. If the
  finance section asks, answer "no" to loans, banking and payment processing (the app records payments the
  shop receives; it does not process them).
* **Permissions the store may ask you to justify:**
  * `CAMERA` - scan product barcodes.
  * `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` (Nearby devices) - connect a receipt printer. Declared with
    `neverForLocation`, only paired devices are listed.
  * `USE_BIOMETRIC` - optional fingerprint confirmation for sensitive settings actions.
  * `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` - merged in by WorkManager for background
    sync; the app itself starts no foreground service. If Console asks about foreground service types,
    check the merged manifest before answering.

## 7. Review risks to decide before you publish

1. **Payments policy.** The license is sold outside the app (the "WhatsApp to Renew" button and the
   license-expired screen send the owner to the operator to pay). Google requires apps that sell access to
   digital features or subscriptions to use Play Billing and does not allow steering users to pay
   elsewhere from inside the app. Business-to-business licences are a grey area, but reviewers do reject
   this. Options: (a) use Play Billing for the subscription, (b) ship the Play build without the in-app
   renewal button/wording and handle renewals outside the app (users are told nothing about payment
   in-app), (c) keep it and accept the rejection risk. This needs your decision; it is not changed in
   this build.
2. **New developer accounts.** Personal accounts created after November 2023 must run a closed test with
   at least 12 opted-in testers for 14 days before production access. Organisation accounts are exempt.
3. **Rooted phones.** The app refuses to run when it detects `su`, Magisk or a root manager. Say so in the
   store description ("does not run on rooted devices").
4. **Play pre-launch report** (automatic on every upload to a testing track) crawls the app on Google's
   devices but cannot get past sign-in without the reviewer credentials from section 6; add them under
   *Pre-launch report > Test account* so it can.

## 8. Build and verify (release manager checklist)

```
cd android-app
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:bundleRelease
```

* `apksigner verify` the APK; the certificate must equal the first entry of `SIGNING_CERT_SHA256`.
* `zipalign -c -P 16 -v 4 app-release.apk` - Play requires 16 KB page-size compatibility for Android 15+.
* Test on Android 7 (API 24) and on a current release, on 3-button and gesture navigation.
* Raise `versionCode` in `android-app/app/build.gradle.kts` for every upload.
