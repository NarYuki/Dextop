"""Offline real-installer tests: failures must retain all previous version metadata."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

RESOURCES=Path(__file__).resolve().parents[2]/'android/app/src/main/resources/moe/n4tsu/dextop/linux'
INSTALLER=Path(os.environ.get('DEXTOP_STEAM_INSTALLER',RESOURCES/'droiddeck-steam-install'))
STATE=Path(os.environ.get('DEXTOP_STEAM_STATE_SOURCE',RESOURCES/'dextop-steam-state'))


@unittest.skipUnless(sys.platform.startswith('linux'),'GNU tools and flock used by the Linux installer')
class SteamTransactionTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.base=Path(self.tmp.name);self.home=self.base/'home';self.root=self.home/'.local/share/Steam';self.package=self.root/'package';self.package.mkdir(parents=True)
        self.bin=self.base/'bin';self.bin.mkdir();self.cdn=self.base/'cdn';self.cdn.mkdir()
        self.old={'droiddeck-installed':b'1\n','beta':b'publicbeta\n',
                  'steam_client_steamdeck_publicbeta_linuxarm64.manifest':b'"version" "1"\n',
                  'steam_client_steamdeck_publicbeta_linuxarm64.installed':b'old installed state\n',
                  'steam_client_publicbeta_linuxarm64.installed':b'preserve other channel\n'}
        for n,b in self.old.items():(self.package/n).write_bytes(b)
        (self.root/'steamrtarm64').mkdir();self.client=self.root/'steamrtarm64/steam';self.client.write_bytes(b'previous client');self.client.chmod(0o755)
        (self.home/'.bl-steam-repair').touch();(self.root/'userdata').mkdir();(self.root/'userdata/preserve').write_text('user data')
        with zipfile.ZipFile(self.cdn/'client.zip','w') as z:
            for name in ['steam','steamclient.so','steamwebhelper','steamwebhelper.sh']:z.writestr('steamrtarm64/'+name,b'new verified '+name.encode())
            z.writestr('linuxarm64/','')
        sha=hashlib.sha256((self.cdn/'client.zip').read_bytes()).hexdigest()
        self.manifest='"version" "2"\n\t"client_linuxarm64_linuxarm64"\n\t{\n\t\t"file" "client.zip"\n\t\t"sha2" "'+sha+'"\n\t}\n'
        (self.cdn/'steam_client_steamdeck_publicbeta_linuxarm64').write_text(self.manifest)
        self.state=self.base/'state';shutil.copyfile(STATE,self.state);self.state.chmod(0o755)
        curl=self.bin/'curl';curl.write_text('''#!/usr/bin/python3
import os,sys,shutil
from pathlib import Path
url=next(x for x in sys.argv if x.startswith('https://'));name=url.rsplit('/',1)[-1]
if os.environ.get('FAIL_NETWORK')=='1' or (name=='client.zip' and os.environ.get('FAIL_PART')=='1'):sys.exit(22)
source=Path(os.environ['FIXTURE_CDN'])/name
if not source.is_file():sys.exit(22)
shutil.copyfile(source,sys.argv[sys.argv.index('-o')+1])
''');curl.chmod(0o755)
        self.env=dict(os.environ,HOME=str(self.home),PATH=str(self.bin)+':'+os.environ['PATH'],FIXTURE_CDN=str(self.cdn),DEXTOP_STEAM_STATE=str(self.state))
        self.env.pop('XDG_DATA_HOME',None)

    def run_installer(self,extra=None):
        return subprocess.run(['bash',str(INSTALLER),'--sync'],env=dict(self.env,**(extra or {})),capture_output=True,text=True,timeout=20)

    def unchanged(self):
        for n,b in self.old.items():self.assertEqual((self.package/n).read_bytes(),b,n)
        self.assertTrue((self.home/'.bl-steam-repair').exists())
        self.assertEqual((self.root/'userdata/preserve').read_text(),'user data')

    def test_network_failure_retains_version_and_payload(self):
        self.assertNotEqual(self.run_installer({'FAIL_NETWORK':'1'}).returncode,0);self.unchanged();self.assertEqual(self.client.read_bytes(),b'previous client')

    def test_component_download_failure_retains_metadata(self):
        self.assertNotEqual(self.run_installer({'FAIL_PART':'1'}).returncode,0);self.unchanged()

    def test_checksum_failure_retains_metadata(self):
        (self.cdn/'client.zip').write_bytes(b'tampered')
        self.assertNotEqual(self.run_installer().returncode,0);self.unchanged()

    def test_recording_failure_rolls_back_manifest_and_channel(self):
        self.state.write_text('#!/bin/sh\nexit 42\n')
        self.assertNotEqual(self.run_installer().returncode,0);self.unchanged()

    def test_failure_after_recording_rolls_back_installed_state(self):
        link=self.bin/'ln';link.write_text('#!/bin/sh\nexit 43\n');link.chmod(0o755)
        self.assertNotEqual(self.run_installer().returncode,0);self.unchanged()

    def test_success_commits_new_version_and_preserves_user_data(self):
        result=self.run_installer();self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertEqual((self.package/'droiddeck-installed').read_text(),'2\n')
        self.assertEqual((self.package/'beta').read_text(),'steamdeck_publicbeta\n')
        self.assertEqual((self.package/'steam_client_steamdeck_publicbeta_linuxarm64.manifest').read_text(),self.manifest)
        self.assertIn('steamrtarm64/steam,',(self.package/'steam_client_steamdeck_publicbeta_linuxarm64.installed').read_text())
        self.assertFalse((self.home/'.bl-steam-repair').exists());self.assertEqual((self.root/'userdata/preserve').read_text(),'user data')
        # A repeat --sync may use the fast path without applying the payload again.
        self.assertEqual(self.run_installer().returncode,0)

    def test_repair_is_not_skipped_when_version_matches(self):
        self.assertEqual(self.run_installer().returncode,0)
        self.client.write_bytes(b'corrupt but executable');self.client.chmod(0o755);(self.home/'.bl-steam-repair').touch()
        result=self.run_installer();self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertEqual(self.client.read_bytes(),b'new verified steam')


if __name__=='__main__':unittest.main()
