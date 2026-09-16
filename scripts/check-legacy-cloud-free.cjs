const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const forbiddenFiles = [
  '.firebaserc', 'firebase.json', 'firestore.rules', 'storage.rules',
  'android-app/app/google-services.json'
];
const roots = [
  'android-app/app/src/main', 'android-app/app/build.gradle.kts', 'android-app/build.gradle.kts',
  'android-app/gradle/libs.versions.toml', 'server/src', 'server/package.json', 'server/package-lock.json',
  'server/.env.example', '.env.example'
];
const forbidden = [/com\.google\.firebase/i, /firebase-admin/i, /com\.google\.gms\.google-services/i, /libs\.plugins\.google\.services/i];
const problems = [];

for (const relative of forbiddenFiles) {
  if (fs.existsSync(path.join(root, relative))) problems.push(`Forbidden configuration remains: ${relative}`);
}

function inspect(target) {
  const stat = fs.statSync(target);
  if (stat.isDirectory()) {
    for (const child of fs.readdirSync(target)) inspect(path.join(target, child));
    return;
  }
  if (!/\.(kt|kts|toml|ts|json|example)$/.test(target)) return;
  const text = fs.readFileSync(target, 'utf8');
  for (const pattern of forbidden) if (pattern.test(text)) problems.push(`Forbidden dependency or SDK reference in ${path.relative(root, target)}`);
}

for (const relative of roots) {
  const target = path.join(root, relative);
  if (fs.existsSync(target)) inspect(target);
}

if (problems.length) {
  problems.forEach(problem => console.error(problem));
  process.exitCode = 1;
} else {
  console.log('Legacy cloud SDK/configuration scan passed.');
}

