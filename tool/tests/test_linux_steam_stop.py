import importlib.machinery
import os
from pathlib import Path
import signal
import tempfile
import unittest
from unittest.mock import patch

RESOURCE = Path(os.environ.get('DEXTOP_STEAM_STOP_SOURCE', Path(__file__).resolve().parents[2] / 'android/app/src/main/resources/moe/n4tsu/dextop/linux/dextop-steam-stop'))


class SteamStopTest(unittest.TestCase):
    def setUp(self):
        self.module = importlib.machinery.SourceFileLoader('steam_stop_test', str(RESOURCE)).load_module()

    def row(self, pid, parent, namespace, command, start='1'):
        return dict(pid=pid, parent=parent, start=start, args=command,
                    namespace=[os.fsencode('DEXTOP_SOCKET_NS='+namespace)] if namespace else [])

    def test_other_containers_and_explicit_foreign_children_are_preserved(self):
        own='dextop-caaa';other='dextop-cbbb'
        rows=[self.row(100,1,own,['bash','/usr/local/bin/dextop-steam-gamemode']),
              self.row(101,100,None,['/steam/steamrtarm64/steam']),
              self.row(102,100,other,['/steam/steamrtarm64/steam']),
              self.row(200,1,other,['bash','/usr/local/bin/dextop-steam-gamemode']),
              self.row(201,200,None,['/steam/steamrtarm64/steam']),
              self.row(300,1,own,['unrelated-app'])]
        self.assertEqual([r['pid'] for r in self.module.targets(rows,own)],[101,100])

    def test_gamescope_loader_and_webhelper_are_recognized(self):
        self.assertTrue(self.module.steam_process(['/lib/ld.so','--library-path','lib','/opt/deck/bin/gamescope','--backend','wayland']))
        self.assertTrue(self.module.steam_process(['/steam/steamrtarm64/steamwebhelper']))
        self.assertFalse(self.module.steam_process(['python3','unrelated.py','/steam/steamrtarm64/steam']))

    def test_missing_or_invalid_namespace_refuses_stop(self):
        with tempfile.TemporaryDirectory() as tmp:
            config=Path(tmp)/'namespace'
            with patch.dict(os.environ,{},clear=True):
                with self.assertRaises(ValueError):self.module.namespace(config)
            config.write_text('unscoped')
            with self.assertRaises(ValueError):self.module.namespace(config)
            config.write_text('dextop-cabc123\n')
            self.assertEqual(self.module.namespace(config),'dextop-cabc123')

    def test_pid_reuse_is_not_signalled(self):
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp)/'100').mkdir()
            original=self.row(100,1,'dextop-caaa',['/steam/steamrtarm64/steam'],'1')
            replaced=dict(original,start='2')
            signals=[]
            with patch.object(self.module,'entry',side_effect=[original,replaced]):
                self.assertEqual(self.module.stop(Path(tmp),'dextop-caaa',lambda p,s:signals.append((p,s))),0)
            self.assertEqual(signals,[])

    def test_only_identified_process_receives_term(self):
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp)/'100').mkdir()
            row=self.row(100,1,'dextop-caaa',['/steam/steamrtarm64/steam'])
            signals=[]
            with patch.object(self.module,'entry',return_value=row):
                self.assertEqual(self.module.stop(Path(tmp),'dextop-caaa',lambda p,s:signals.append((p,s))),1)
            self.assertEqual(signals,[(100,signal.SIGTERM)])


if __name__=='__main__':unittest.main()
