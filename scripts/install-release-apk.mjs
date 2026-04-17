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
  resolve(process.cwd(), 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk'),
  // Legacy fallback for older custom Gradle layout.
  resolve(process.cwd(), 'build', 'app', 'outputs', 'apk', 'release', 'app-release.apk'),
];
const apkPath = apkCandidates.find((path) => existsSync(path));

if (!apkPath) {
  fail(
    `Release APK not found in expected paths:\n- ${apkCandidates.join('\n- ')}\nRun "npm run build:release:apk" first.`,
  );
}

const devicesResult = run('adb', ['devices']);
if (devicesResult.status !== 0) {
  fail('Failed to run "adb devices". Ensure adb is installed and in PATH.', devicesResult.stderr);
}

const lines = devicesResult.stdout
  .split(/\r?\n/)
  .map((line) => line.trim())
  .filter((line) => line.length > 0 && !line.startsWith('List of devices attached'));

const readyDeviceIds = lines
  .filter((line) => /\tdevice$/.test(line))
  .map((line) => line.split('\t')[0]);

const ignored = lines.filter((line) => !/\tdevice$/.test(line));
if (ignored.length > 0) {
  console.log(`Ignoring non-ready entries: ${ignored.join(', ')}`);
}

if (readyDeviceIds.length === 0) {
  fail('No ready Android devices found. Connect devices and run "adb devices".');
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

let failedInstalls = 0;
let failedLaunches = 0;
for (const deviceId of readyDeviceIds) {
  uninstallLegacyPackages(deviceId);

  console.log(`Installing release APK on ${deviceId}...`);
  let installResult = run('adb', ['-s', deviceId, 'install', '-r', apkPath]);
  let output = `${installResult.stdout}\n${installResult.stderr}`.trim();

  const signatureMismatch =
    installResult.status !== 0 && output.includes('INSTALL_FAILED_UPDATE_INCOMPATIBLE');
  if (signatureMismatch) {
    console.log(`Signature mismatch on ${deviceId} for ${appId}. Uninstalling and retrying...`);
    const uninstallResult = run('adb', ['-s', deviceId, 'uninstall', appId]);
    const uninstallOutput = `${uninstallResult.stdout}\n${uninstallResult.stderr}`.trim();
    const uninstallSucceeded = uninstallResult.status === 0 && isSuccessOutput(uninstallOutput);
    if (!uninstallSucceeded) {
      failedInstalls += 1;
      console.error(`Uninstall failed on ${deviceId} for ${appId}.`);
      if (uninstallOutput.length > 0) {
        console.error(uninstallOutput);
      }
      continue;
    }

    installResult = run('adb', ['-s', deviceId, 'install', '-r', apkPath]);
    output = `${installResult.stdout}\n${installResult.stderr}`.trim();
  }

  if (installResult.status !== 0 || !output.includes('Success')) {
    failedInstalls += 1;
    console.error(`Install failed on ${deviceId}.`);
    if (output.length > 0) {
      console.error(output);
    }
    continue;
  }

  console.log(`Install success on ${deviceId}.`);
  forceStopPackages(deviceId, listTrainingPackages(deviceId));

  console.log(`Launching ${appId} on ${deviceId}...`);
  const launchResult = run('adb', [
    '-s',
    deviceId,
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
    failedLaunches += 1;
    console.error(`Launch failed on ${deviceId}.`);
    if (launchOutput.length > 0) {
      console.error(launchOutput);
    }
    continue;
  }

  console.log(`Launch success on ${deviceId}.`);
}

if (failedInstalls > 0) {
  fail(`${failedInstalls} device install(s) failed.`);
}

if (failedLaunches > 0) {
  fail(`${failedLaunches} device launch(es) failed.`);
}

console.log(`Installed and launched release APK on ${readyDeviceIds.length} device(s).`);
