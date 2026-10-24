#!/usr/bin/env python3
"""Build a separate headless APK to compare shell UID with Android app UID.
Requires the Android SDK/NDK and JDK. Copies only ping and its shared libraries
from an existing container; never changes that container or Dextop's APK.
"""
import argparse
from pathlib import Path
import os
import re
import shutil
import subprocess
import zipfile

parser=argparse.ArgumentParser()
parser.add_argument('--serial',required=True)
parser.add_argument('--container',required=True)
parser.add_argument('--guest-launcher',required=True,help='Device-side shell launcher into the source container')
args=parser.parse_args()
assert re.fullmatch(r'c[a-zA-Z0-9]+',args.container)
source=Path(__file__).resolve().parent
repo=source.parents[2]
out=repo/'build/app-uid-experiment'
for path in [out/'assets',out/'rootfs/etc',out/'native',out/'classes',out/'dex']:path.mkdir(parents=True,exist_ok=True)
sdk=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'AppData/Local/Android/Sdk')))
java=Path(os.environ.get('JAVA_HOME','C:/Program Files/Microsoft/jdk-21.0.12.101-hotspot'))
ndk=sdk/'ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64/bin'
build=sdk/'build-tools/36.0.0'
android=sdk/'platforms/android-36/android.jar'

def run(command,**kw):
 subprocess.run([str(x) for x in command],check=True,**kw)
def adb(*cmd):return ['adb','-s',args.serial,*cmd]
def pull(remote,local):
 local.parent.mkdir(parents=True,exist_ok=True);run(adb('pull',remote,str(local)),stdout=subprocess.DEVNULL)

guest=f'/data/local/tmp/dextop/linux/containers/{args.container}/rootfs'
root=out/'rootfs'
pull(guest+'/usr/bin/ping',root/'usr/bin/ping')
run([repo/'build/tools/zig-windows-x86_64-0.13.0/zig.exe','cc','-target','aarch64-linux-gnu.2.31','-fPIC','-pie','-O2',source/'uid_probe.c','-o',root/'usr/bin/uid-probe'])
needed=['ping','uid-probe'];done=set()
while needed:
 name=needed.pop()
 path=root/'usr/bin'/name if name in ('ping','uid-probe') else root/'lib/aarch64-linux-gnu'/name
 if name in done:continue
 done.add(name)
 if name not in ('ping','uid-probe'):
  resolved=subprocess.check_output(adb('shell','sh',args.guest_launcher,'readlink','-f','/usr/lib/aarch64-linux-gnu/'+name),text=True).strip()
  if not resolved.startswith('/'):raise RuntimeError('Cannot resolve '+name)
  pull(guest+resolved,path)
 dynamic=subprocess.check_output([str(ndk/'llvm-readelf.exe'),'-d',str(path)],text=True)
 needed.extend(re.findall(r'Shared library: \[([^]]+)\]',dynamic))
shutil.copyfile(root/'lib/aarch64-linux-gnu/ld-linux-aarch64.so.1',root/'lib/ld-linux-aarch64.so.1')
(root/'etc/nsswitch.conf').write_text('passwd: files\ngroup: files\nhosts: files dns\n',encoding='utf-8',newline='\n')
with zipfile.ZipFile(out/'assets/rootfs.zip','w',zipfile.ZIP_DEFLATED) as z:
 for path in root.rglob('*'):
  if path.is_file():z.write(path,path.relative_to(root).as_posix())
apk=subprocess.check_output(adb('shell','pm','path','moe.n4tsu.dextop'),text=True).strip().removeprefix('package:')
lib=apk.removesuffix('/base.apk')+'/lib/arm64'
for name in ['libproot.so','libproot_loader.so']:pull(lib+'/'+name,out/'native'/name)
run([ndk/'aarch64-linux-android30-clang.cmd','-O2','-fPIE','-pie',source/'uid_probe.c','-o',out/'native/libuid_probe.so'])
run([java/'bin/javac.exe','-source','8','-target','8','-cp',android,'-d',out/'classes',source/'ProbeReceiver.java'])
env=dict(os.environ,JAVA_HOME=str(java))
run([build/'d8.bat','--lib',android,'--min-api','30','--output',out/'dex',*list((out/'classes').rglob('*.class'))],env=env)
run([build/'aapt2.exe','link','-I',android,'--manifest',source/'AndroidManifest.xml','-A',out/'assets','--min-sdk-version','30','--target-sdk-version','36','-o',out/'unsigned.apk'])
with zipfile.ZipFile(out/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
 z.write(out/'dex/classes.dex','classes.dex')
 for path in (out/'native').glob('*.so'):z.write(path,'lib/arm64-v8a/'+path.name)
run([build/'zipalign.exe','-f','-P','16','4',out/'unsigned.apk',out/'aligned.apk'])
run([build/'apksigner.bat','sign','--ks',Path.home()/'.android/debug.keystore','--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--out',out/'uid-probe.apk',out/'aligned.apk'],env=env)
print('Built',out/'uid-probe.apk','glibc files:',sorted(done))
