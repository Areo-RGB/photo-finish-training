import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { resolve } from 'node:path';

function run(command, args) {
  const result = spawnSync(command, args, {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  if (result.error) {
    throw result.error;
  }
  return result;
}

function fail(message, detail = '') {
  console.error(message);
  if (detail.trim().length > 0) {
    console.error(detail.trim());
  }
  process.exit(1);
}

const appId = 'training.variant';
const legacyPackagePrefix = 'training.variant.';
const apkCandidates = [
  resolve(process.cwd(), 'android', 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk'),
  // Legacy fallback for older custom Gradle layout.
  resolve(process.cwd(), 'build', 'app', 'outputs', 'apk', 'debug', 'app-debug.apk'),
];
const apkPath = apkCandidates.find((path) => existsSync(path));

if (!apkPath) {
  fail(
    `Debug APK not found in expected paths:\n- ${apkCandidates.join('\n- ')}\nRun "npm run build:debug:apk" first.`,
  );
}

const devicesResult = run('adb', ['devices', '-l']);
if (devicesResult.status !== 0) {
  fail('Failed to run "adb devices -l". Ensure adb is installed and in PATH.', devicesResult.stderr);
}

const lines = devicesResult.stdout
  .split(/\r?\n/)
  .map((line) => line.trim())
  .filter((line) => line.length > 0 && !line.startsWith('List of devices attached'));

const readyDevices = [];
for (const line of lines) {
  const parts = line.split(/\s+/);
  if (parts.length < 2 || parts[1] !== 'device') {
    continue;
  }
  const serial = parts[0];
  const modelPart = parts.find((part) => part.startsWith('model:')) ?? '';
  const model = modelPart.replace(/^model:/, '');
  readyDevices.push({ serial, model });
}

if (readyDevices.length === 0) {
  fail('No ready Android devices found. Connect devices and run "adb devices -l".');
}

function parseListedPackages(output) {
  return output
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.startsWith('package:'))
    .map((line) => line.replace(/^package:/, '').trim())
    .filter((pkg) => pkg.length > 0);
}

function listTrainingPackages(serial) {
  const result = run('adb', ['-s', serial, 'shell', 'pm', 'list', 'packages', 'training.variant']);
  if (result.status !== 0) {
    return [];
  }
  return parseListedPackages(result.stdout || '');
}

function isSuccessOutput(output) {
  return output.includes('Success') || output.includes('DELETE_SUCCEEDED');
}

function uninstallLegacyPackages(serial) {
  const installed = listTrainingPackages(serial);
  const legacyPackages = installed.filter((pkg) => pkg !== appId && pkg.startsWith(legacyPackagePrefix));

  for (const legacyPackage of legacyPackages) {
    console.log(`Removing legacy package ${legacyPackage} from ${serial}...`);
    const uninstallResult = run('adb', ['-s', serial, 'uninstall', legacyPackage]);
    const uninstallOutput = `${uninstallResult.stdout}\n${uninstallResult.stderr}`.trim();
    if (uninstallResult.status !== 0 || !isSuccessOutput(uninstallOutput)) {
      console.warn(`Legacy package removal failed for ${legacyPackage} on ${serial}.`);
      if (uninstallOutput.length > 0) {
        console.warn(uninstallOutput);
      }
    }
  }
}

function forceStopPackages(serial, packages) {
  for (const packageName of packages) {
    run('adb', ['-s', serial, 'shell', 'am', 'force-stop', packageName]);
  }
}

let installs = 0;
let failed = 0;

for (const device of readyDevices) {
  uninstallLegacyPackages(device.serial);

  console.log(`Installing debug APK on ${device.serial} (${device.model || 'unknown model'})...`);
  let installResult = run('adb', ['-s', device.serial, 'install', '-r', apkPath]);
  let installOutput = `${installResult.stdout}\n${installResult.stderr}`.trim();

  const signatureMismatch =
    installResult.status !== 0 && installOutput.includes('INSTALL_FAILED_UPDATE_INCOMPATIBLE');
  if (signatureMismatch) {
    console.log(`Signature mismatch on ${device.serial} for ${appId}. Uninstalling and retrying...`);
    const uninstallResult = run('adb', ['-s', device.serial, 'uninstall', appId]);
    const uninstallOutput = `${uninstallResult.stdout}\n${uninstallResult.stderr}`.trim();
    const uninstallSucceeded = uninstallResult.status === 0 && isSuccessOutput(uninstallOutput);
    if (!uninstallSucceeded) {
      failed += 1;
      console.error(`Uninstall failed on ${device.serial} for ${appId}.`);
      if (uninstallOutput.length > 0) {
        console.error(uninstallOutput);
      }
      continue;
    }

    installResult = run('adb', ['-s', device.serial, 'install', '-r', apkPath]);
    installOutput = `${installResult.stdout}\n${installResult.stderr}`.trim();
  }

  if (installResult.status !== 0 || !installOutput.includes('Success')) {
    failed += 1;
    console.error(`Install failed on ${device.serial}.`);
    if (installOutput.length > 0) {
      console.error(installOutput);
    }
    continue;
  }

  forceStopPackages(device.serial, listTrainingPackages(device.serial));

  const launchResult = run('adb', [
    '-s',
    device.serial,
    'shell',
    'monkey',
    '-p',
    appId,
    '-c',
    'android.intent.category.LAUNCHER',
    '1',
  ]);
  const launchOutput = `${launchResult.stdout}\n${launchResult.stderr}`.trim();
  const launchSucceeded =
    launchResult.status === 0 &&
    !launchOutput.includes('No activities found') &&
    !launchOutput.includes('Error');
  if (!launchSucceeded) {
    failed += 1;
    console.error(`Launch failed on ${device.serial}.`);
    if (launchOutput.length > 0) {
      console.error(launchOutput);
    }
    continue;
  }

  installs += 1;
  console.log(`Install + launch success on ${device.serial}.`);
}

if (failed > 0) {
  fail(`Completed with ${failed} failure(s). Successful installs: ${installs}.`);
}

console.log(`Done. Successful installs: ${installs}.`);
