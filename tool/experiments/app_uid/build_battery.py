#!/usr/bin/env python3
"""Rebuild the probe APK with the Android-mode battery (BatteryReceiver + battery.sh).
Reuses build/app-uid-experiment from build.py and refreshes Dextop's installed PRoot."""
import argparse, os, shutil, subprocess, zipfile
from pathlib import Path
p = argparse.ArgumentParser(); p.add_argument('--serial', required=True); a = p.parse_args()
src = Path(__file__).resolve().parent; repo = src.parents[2]; out = repo / 'build/app-uid-experiment'
sdk = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'AppData/Local/Android/Sdk')))
java = Path('C:/Program Files/Microsoft/jdk-21.0.12.101-hotspot')
bt = sdk / 'build-tools/36.0.0'; jar = sdk / 'platforms/android-36/android.jar'
def run(c, **k): subprocess.run([str(x) for x in c], check=True, **k)
apk = subprocess.check_output(['adb', '-s', a.serial, 'shell', 'pm', 'path', 'moe.n4tsu.dextop'], text=True).strip().removeprefix('package:')
for n in ['libproot.so', 'libproot_loader.so']:
    run(['adb', '-s', a.serial, 'pull', apk.removesuffix('/base.apk') + '/lib/arm64/' + n, out / 'native' / n], stdout=subprocess.DEVNULL)
shutil.copyfile(src / 'battery.sh', out / 'assets/battery.sh')
shutil.rmtree(out / 'classes', ignore_errors=True); shutil.rmtree(out / 'dex', ignore_errors=True)
(out / 'classes').mkdir(); (out / 'dex').mkdir()
run([java / 'bin/javac.exe', '-source', '8', '-target', '8', '-cp', jar, '-d', out / 'classes', src / 'ProbeReceiver.java', src / 'BatteryReceiver.java'])
env = dict(os.environ, JAVA_HOME=str(java))
run([bt / 'd8.bat', '--lib', jar, '--min-api', '30', '--output', out / 'dex', *list((out / 'classes').rglob('*.class'))], env=env)
run([bt / 'aapt2.exe', 'link', '-I', jar, '--manifest', src / 'AndroidManifest.xml', '-A', out / 'assets', '--min-sdk-version', '30', '--target-sdk-version', '36', '-o', out / 'unsigned.apk'])
with zipfile.ZipFile(out / 'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(out / 'dex/classes.dex', 'classes.dex')
    for f in (out / 'native').glob('*.so'): z.write(f, 'lib/arm64-v8a/' + f.name)
run([bt / 'zipalign.exe', '-f', '-P', '16', '4', out / 'unsigned.apk', out / 'aligned.apk'])
run([bt / 'apksigner.bat', 'sign', '--ks', Path.home() / '.android/debug.keystore', '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', out / 'uid-probe.apk', out / 'aligned.apk'], env=env)
print('Built', out / 'uid-probe.apk')
