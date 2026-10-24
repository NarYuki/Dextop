"""Exercise the actual Deck launcher without starting a compositor or GUI."""
import os
from pathlib import Path
import runpy
import sys
from unittest.mock import patch

module = runpy.run_path(sys.argv[1])
main = module['main']
scope = main.__globals__
base = Path('/fixture/gamescope')
private = Path('/fixture/ahb')
inherited = {'VK_ICD_FILENAMES': '/existing/icd.json', 'VK_DRIVER_FILES': '/existing/icd.json',
             'LD_LIBRARY_PATH': '/existing/libs'}
for experiment in (False, True):
    environment = dict(inherited)
    if experiment:
        environment['DEXTOP_DECK_AHB'] = '1'
    with patch.dict(os.environ, environment, clear=True), \
            patch.dict(scope, {'prepare': lambda *args, **kwargs: private if args else base}), \
            patch.object(os, 'execv') as execute:
        main(['--backend', 'wayland'])
        argv = execute.call_args.args[1]
        libraries = argv[argv.index('--library-path') + 1]
        assert argv[-2:] == ['--backend', 'wayland']
        assert os.environ['LD_LIBRARY_PATH'] == inherited['LD_LIBRARY_PATH']
        if experiment:
            assert os.environ['VK_ICD_FILENAMES'] == str(private/'icd.json')
            assert os.environ['VK_DRIVER_FILES'] == str(private/'icd.json')
            assert os.environ['BANNER_WSI_AHB'] == '1'
            assert libraries.startswith(str(private/'lib') + ':')
        else:
            assert os.environ['VK_ICD_FILENAMES'] == inherited['VK_ICD_FILENAMES']
            assert os.environ['VK_DRIVER_FILES'] == inherited['VK_DRIVER_FILES']
            assert 'BANNER_WSI_AHB' not in os.environ
            assert str(private) not in libraries
print('PASS: normal Deck inherits its ICD; only explicit AHB experiment selects the private driver')
