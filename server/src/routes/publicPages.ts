import { Router } from 'express';

/**
 * Public web pages Google Play asks for in the store listing: the privacy policy and a page that
 * explains how to delete an account and its data (which must work even for someone who no longer
 * has the app installed). No sign-in; nothing here reads shop data.
 *
 * The publisher's name and contact come from the environment so nothing about who runs the service
 * is invented here: LEGAL_ENTITY_NAME, SUPPORT_EMAIL, MASTER_SUPPORT_PHONE.
 */
const router = Router();

const escapeHtml = (value: string): string =>
  value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');

const LAST_UPDATED = '24 September 2026';

const publisher = () => {
  const name = escapeHtml(process.env.LEGAL_ENTITY_NAME?.trim() || 'KadaiKutty POS');
  const email = process.env.SUPPORT_EMAIL?.trim();
  const phone = process.env.MASTER_SUPPORT_PHONE?.trim();
  const contact = [
    email ? `email <a href="mailto:${escapeHtml(email)}">${escapeHtml(email)}</a>` : '',
    phone ? `phone / WhatsApp ${escapeHtml(phone)}` : '',
  ].filter(Boolean).join(', ') || 'the support contact shown inside the app';
  return { name, contact };
};

const page = (title: string, body: string): string => `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${title}</title>
<style>
  body{font:16px/1.6 system-ui,-apple-system,Segoe UI,Roboto,sans-serif;color:#1f2933;margin:0;background:#fafafa}
  main{max-width:760px;margin:0 auto;padding:24px 18px 64px}
  h1{color:#5c151a;font-size:1.7rem} h2{color:#5c151a;font-size:1.2rem;margin-top:2rem}
  a{color:#5c151a} li{margin:.25rem 0} .note{background:#fff;border:1px solid #e5d5d6;border-radius:10px;padding:12px 16px}
</style></head><body><main>${body}</main></body></html>`;

const privacyPolicy = (): string => {
  const { name, contact } = publisher();
  return page('KadaiKutty POS - Privacy Policy', `
<h1>Privacy Policy</h1>
<p>Last updated: ${LAST_UPDATED}</p>
<p>KadaiKutty POS is a billing and inventory app for shops. This policy explains what ${name} ("we") collects when you use it, why, and the choices you have.</p>

<h2>What we collect</h2>
<ul>
  <li><b>Account details:</b> your mobile number, your name, your shop name, and your 6-digit PIN. The PIN is held by Amazon Cognito, our sign-in service; we cannot read it.</li>
  <li><b>Shop records you enter:</b> products and categories, stock, customers (name, phone, address, amount owed), suppliers, sales bills, purchases, expenses, cash-register closings, the shop profile (address, GST number, phone, email), and the names and mobile numbers of staff you add.</li>
  <li><b>Device and technical data:</b> a random installation ID, the phone model name, the IP address of requests (kept in security audit logs), and crash and error diagnostics.</li>
  <li><b>Backups:</b> if you use cloud backup, a copy of your shop database that you choose to upload.</li>
</ul>
<p>We do <b>not</b> collect your location, contacts, photos, microphone recordings, or advertising ID, and we show no ads. The camera is used only to scan barcodes; images are processed on the phone and are not stored or uploaded. Bluetooth is used only to reach your receipt printer.</p>

<h2>Why we use it</h2>
<ul>
  <li>To run the app: sign you in, keep your shop data on your phone and synchronised across your devices, and send a one-time code (OTP) to your mobile number when you register or reset your PIN.</li>
  <li>To keep it secure and working: prevent misuse, investigate problems, and fix crashes.</li>
  <li>To manage your subscription (license) to the service.</li>
</ul>
<p>We do not sell your data and we do not use it for advertising.</p>

<h2>Who it is shared with</h2>
<ul>
  <li><b>Amazon Web Services</b> hosts the service (Asia Pacific - Sydney region): sign-in (Cognito), database (DynamoDB) and backups (S3).</li>
  <li><b>An SMS provider</b> receives your mobile number and the message text to deliver OTP codes.</li>
  <li><b>Sentry</b> receives crash reports and app events. We remove PINs, tokens and one-time codes from them before they are sent.</li>
</ul>
<p>We do not share your shop's records with anyone else. Your shop's staff can see only what their permissions allow.</p>

<h2>Where your data lives and how it is protected</h2>
<p>Your shop data is stored on your phone in an encrypted database and, when you are signed in, in our cloud. Connections use HTTPS. Cloud data is encrypted at rest. Access to a shop's data is limited to that shop's signed-in users.</p>

<h2>How long we keep it</h2>
<p>We keep your data while your account exists. When you delete your account (see below) your shop data, backups, staff accounts and sign-in identities are erased at once. Copies inside our system backups are overwritten automatically within 35 days. We keep a minimal deletion record (internal account IDs and the time of deletion, with no shop contents) for security auditing.</p>

<h2>Your choices and deleting your account</h2>
<p>You can delete your account and all of the shop's data yourself: <b>Settings &rarr; Database &amp; Security &rarr; Delete Account</b>. You can also ask us to do it on the <a href="/account-deletion">account deletion page</a>. You can export a backup of your data from the app at any time. To correct or access your data, use the app, or contact us.</p>

<h2>Children</h2>
<p>The app is for businesses and is not directed to children.</p>

<h2>Changes</h2>
<p>If we change this policy we will update the date above and, for significant changes, tell you in the app.</p>

<h2>Contact</h2>
<p>${name} - ${contact}.</p>`);
};

const accountDeletion = (): string => {
  const { name, contact } = publisher();
  return page('KadaiKutty POS - Delete your account', `
<h1>Delete your KadaiKutty POS account</h1>
<p>This page explains how to delete a KadaiKutty POS account and its data.</p>

<h2>Option 1 - inside the app (fastest)</h2>
<ol>
  <li>Sign in as the shop owner.</li>
  <li>Open <b>Settings &rarr; Database &amp; Security &rarr; Delete Account</b>.</li>
  <li>Type <b>DELETE</b> and enter your 6-digit PIN, then confirm.</li>
</ol>
<p>Your account is removed immediately.</p>

<h2>Option 2 - ask us to do it</h2>
<p>If you can no longer use the app, contact ${name} on ${contact} from the mobile number registered to the account, and ask for it to be deleted. We will confirm and complete the deletion.</p>

<h2>What is deleted</h2>
<ul>
  <li>The shop's account, every staff account, and all sign-in identities.</li>
  <li>All cloud records: products, customers, suppliers, bills, purchases, expenses and the shop profile.</li>
  <li>All cloud backups.</li>
  <li>The data stored on the phone you deleted from is wiped as well. Other phones signed in to the shop are signed out; remove the app from them.</li>
</ul>

<h2>What is kept</h2>
<ul>
  <li>Copies inside our automatic system backups, which are overwritten within 35 days.</li>
  <li>A minimal deletion record (internal account IDs and the time of deletion, without shop contents) for security auditing.</li>
</ul>
<p class="note"><b>Deletion cannot be undone.</b> Export a backup from the app first if you want to keep a copy of your records. Deleting your account does not cancel a subscription or refund payments made to us.</p>
<p>See also our <a href="/privacy-policy">Privacy Policy</a>.</p>`);
};

const html = (render: () => string) => (_req: unknown, res: any) => {
  res.set('Cache-Control', 'public, max-age=300').type('html').send(render());
};

router.get('/privacy-policy', html(privacyPolicy));
router.get('/account-deletion', html(accountDeletion));

export default router;
