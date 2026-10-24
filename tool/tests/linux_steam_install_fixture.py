from pathlib import Path
import os,subprocess
base=Path('/tmp/dextop-full-steam-installer')
for name in ['bin','gaming','home/.local/share/Steam/steamapps/common']:(base/name).mkdir(parents=True,exist_ok=True)
installer=base/'bin/droiddeck-steam-install'
installer.write_text('#!/bin/sh\n# Network download is a separate verified input to this fixture.\nexit 0\n');installer.chmod(0o755)
link=base/'home/.local/share/Steam/steamapps/common/Proton Experimental (ARM64)'
if not link.exists():link.symlink_to('/tmp/dextop-existing-steam/steamapps/common/Proton Experimental (ARM64)')
print('ISOLATED_STEAM_INSTALLER_FIXTURE_READY')
