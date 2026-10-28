"""Isolated loopback server; never changes or records the active session audio."""
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import time

with tempfile.TemporaryDirectory(prefix='dextop-pulse-auth-') as directory:
    root = Path(directory)
    for name in ('correct', 'wrong'):
        (root / name).write_bytes(os.urandom(256))
        (root / name).chmod(0o600)
    with socket.socket() as reservation:
        reservation.bind(('127.0.0.1', 0))
        port = reservation.getsockname()[1]
    env = dict(os.environ, PULSE_RUNTIME_PATH=str(root))
    process = subprocess.Popen(['pulseaudio', '-n', '--daemonize=no', '--use-pid-file=no',
        '--exit-idle-time=-1', '--log-target=stderr',
        '--load=module-null-sink sink_name=audit',
        f'--load=module-native-protocol-tcp listen=127.0.0.1 port={port} auth-anonymous=0 auth-cookie-enabled=1 auth-cookie={root / "correct"}'],
        env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        for attempt in range(30):
            if process.poll() is not None:
                raise RuntimeError('Isolated Pulse server failed')
            try:
                with socket.create_connection(('127.0.0.1', port), timeout=.1):
                    break
            except OSError:
                time.sleep(.1)
        for name, expected in [('wrong', False), ('correct', True)]:
            result = subprocess.run(['pactl', 'info'], timeout=4, capture_output=True,
                env=dict(env, PULSE_SERVER=f'tcp:127.0.0.1:{port}', PULSE_COOKIE=str(root / name)))
            assert (result.returncode == 0) == expected, (name, result.returncode)
            print(f'{name} cookie: {"accepted" if expected else "rejected"}')
    finally:
        process.terminate()
        try:
            process.wait(timeout=3)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
print('PASSED: authenticated Pulse protocol, isolated daemon stopped')
