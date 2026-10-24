from pathlib import Path
import importlib.machinery,subprocess
subprocess.run(['sh','-c','exec /bin/sh /tmp/steam-persisted-installer.sh'],check=True)
root=Path('/root/.local/share/Steam')
compat=importlib.machinery.SourceFileLoader('installed_compat','/usr/local/lib/dextop-gaming/steam-compatibility').load_module()
assert compat.read_config_mapping(str(root/'config/config.vdf'))['0']=='dextop-proton-arm64'
for tool in ['dextop-proton-arm64','dextop-proton-fex']:assert (root/'compatibilitytools.d'/tool/'toolmanifest.vdf').is_file()
assert Path('/usr/local/bin/dextop-steam').stat().st_mode&0o111
assert 'Exec=/usr/local/bin/dextop-steam %U' in Path('/root/.local/share/applications/dextop-steam.desktop').read_text()
assert all(Path('/usr/local/lib/dextop-gaming/preload/'+abi+'/'+name).stat().st_size>0 for abi,name in [('aarch64','libdextop_gamesock.so'),('i386','libblsession.so'),('x86_64','libblsession.so')])
print('FULL_STEAM_INSTALLER_FILE_EXEC_ARM64_DEFAULT_BOTH_TOOLS_LIBRARIES_LAUNCHER_DESKTOP_OK')
