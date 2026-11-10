"""Real UNIX socket IPC across processes, including filesystem-writable hosts.

On an Android guest set DEXTOP_UNIXSOCK_TEST_LIBRARY to the newly built GNU shim.
The guest needs no compiler; existing preload companions supply discovery markers.
"""
import json
import os
from pathlib import Path
import select
import shutil
import subprocess
import sys
import tempfile
import unittest
import uuid


@unittest.skipUnless(sys.platform.startswith('linux'), 'Linux abstract UNIX sockets')
class SocketTransportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.build = tempfile.TemporaryDirectory(prefix='dextop-socket-build-')
        given = os.environ.get('DEXTOP_UNIXSOCK_TEST_LIBRARY')
        if given:
            cls.library = given
        else:
            cc = shutil.which('cc')
            if not cc: raise unittest.SkipTest('C compiler unavailable')
            root = Path(__file__).resolve().parents[2]
            cls.library = str(Path(cls.build.name) / 'sockets.so')
            subprocess.run([cc, '-shared', '-fPIC', '-O2',
                str(root/'android/app/src/main/cpp/linux_unixsock.c'),
                str(root/'android/app/src/main/cpp/linux_socket_markers.c'),
                '-ldl', '-o', cls.library], check=True)

    @classmethod
    def tearDownClass(cls): cls.build.cleanup()

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix='dextop-ipc-')
        self.addCleanup(self.directory.cleanup)
        self.path = str(Path(self.directory.name) / 'socket')
        self.namespace = 'dextop-test-' + uuid.uuid4().hex[:12]

    def environment(self, transport='abstract', namespace=None):
        return dict(os.environ, DEXTOP_SOCKET_NS=namespace or self.namespace,
            DEXTOP_SOCKET_TRANSPORT=transport,
            LD_PRELOAD=self.library + ':' + os.environ.get('LD_PRELOAD', ''))

    def server(self, transport='abstract'):
        code = '''import socket,sys
s=socket.socket(socket.AF_UNIX);s.bind(sys.argv[1]);s.listen(2)
print('ready',flush=True)
c,_=s.accept();c.sendall(b'synthetic-ipc-ok');c.close();s.close()
'''
        p = subprocess.Popen([sys.executable, '-c', code, self.path], env=self.environment(transport),
            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        def stop():
            if p.poll() is None:p.kill()
            p.communicate(timeout=5)
        self.addCleanup(stop)
        self.assertTrue(select.select([p.stdout], [], [], 8)[0], 'server startup timed out')
        line = p.stdout.readline()
        self.assertEqual(line, b'ready\n', p.stderr.read() if not line else '')
        return p

    def client(self, transport='abstract', namespace=None):
        code = '''import socket,sys
s=socket.socket(socket.AF_UNIX);s.settimeout(3);s.connect(sys.argv[1]);print(s.recv(64).decode());s.close()
'''
        return subprocess.run([sys.executable, '-c', code, self.path], env=self.environment(transport, namespace),
            capture_output=True, timeout=6)

    def test_abstract_server_works_even_when_filesystem_bind_would_succeed(self):
        p = self.server()
        x = self.client()
        self.assertEqual(x.returncode, 0, x.stderr)
        self.assertIn(b'synthetic-ipc-ok', x.stdout)
        self.assertEqual(p.wait(timeout=5), 0)

    def test_legacy_client_can_reach_new_abstract_server(self):
        self.server()
        x = self.client('native')
        self.assertEqual(x.returncode, 0, x.stderr)

    def test_new_client_can_reach_legacy_filesystem_server(self):
        self.server('native')
        x = self.client()
        self.assertEqual(x.returncode, 0, x.stderr)

    def test_container_namespaces_cannot_cross_connect(self):
        self.server()
        wrong = self.client(namespace='dextop-other-' + uuid.uuid4().hex[:8])
        self.assertNotEqual(wrong.returncode, 0)
        self.assertEqual(self.client().returncode, 0)

    def test_active_abstract_name_cannot_be_stolen(self):
        self.server()
        code = 'import socket,sys;s=socket.socket(socket.AF_UNIX);s.bind(sys.argv[1])'
        x = subprocess.run([sys.executable, '-c', code, self.path], env=self.environment(), capture_output=True, timeout=5)
        self.assertNotEqual(x.returncode, 0)
        self.assertEqual(self.client().returncode, 0)


if __name__ == '__main__':
    unittest.main()
