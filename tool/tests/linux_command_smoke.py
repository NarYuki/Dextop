#!/usr/bin/env python3
"""Run inside a Dextop container; checks CLI, fakeroot and package operations.

python3 linux_command_smoke.py --network --apt-update
Package installation uses an isolated dpkg database and installation root.
Optional commands are reported as skipped when not installed.
"""
import argparse
import json
import os
from pathlib import Path
import shlex
import shutil
import signal
import subprocess
import tempfile
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--network', action='store_true')
    parser.add_argument('--apt-update', action='store_true')
    args = parser.parse_args()
    cases = []

    def add(name, command, expected=None, timeout=15, optional=None):
        cases.append((name, command, expected, timeout, optional))

    for command in ['id', 'whoami', 'pwd', 'uname -m', 'date', 'locale charmap',
                    'df -h /', 'free -m', 'ps -eo pid,comm', 'getconf PAGESIZE',
                    'apt --version', 'apt-get --version', 'dpkg --version',
                    'dpkg-query -W bash coreutils apt fakeroot', 'dpkg --audit',
                    'apt-cache policy bash', 'apt-get -s install bash']:
        add(command, command)
    add('sudo identity', 'sudo id -u', '0')
    add('sudo noninteractive', 'sudo -n id -u', '0')
    add('sudo root user', 'sudo -u root id -u', '0')
    add('sudo shell', "sudo sh -ec 'test $(id -u) = 0; echo sudo-shell-ok'", 'sudo-shell-ok')
    add('sudo option terminator', 'sudo -- id -u', '0')
    add('sudo apt', 'sudo apt-get -s install bash')
    add('fakeroot direct', 'fakeroot-tcp id -u', '0')
    add('text pipeline', "printf 'b\\na\\na\\n' | sort | uniq -c", '2 a')
    add('grep and sed', "printf 'abc\\n' | sed s/b/B/ | grep aBc", 'aBc')
    add('awk', "printf '1\\n2\\n' | awk '{s+=$1} END {print s}'", '3')
    add('bash', "bash -lc 'printf bash-ok'", 'bash-ok')
    add('dash', "dash -c 'printf dash-ok'", 'dash-ok')
    add('process wait', 'sleep 0.1 & p=$!; wait "$p"; kill -0 $$; timeout 2 true')
    for name, command in [('git', 'git --version'), ('make', 'make --version'),
                          ('gcc', 'gcc --version'), ('zip', 'zip -v')]:
        add(name, command, optional=name)
    with tempfile.TemporaryDirectory(prefix='dextop-cli-smoke-') as directory:
        work = shlex.quote(directory)
        add('file operations', f'''set -e; d={work}/files; mkdir "$d"; touch "$d/a";
            printf test >"$d/a"; cp "$d/a" "$d/b"; mv "$d/b" "$d/c";
            chmod 600 "$d/c"; test "$(stat -c %a "$d/c")" = 600;
            ln -s c "$d/link"; test "$(readlink "$d/link")" = c;
            test "$(cat "$d/link")" = test; find "$d" -type f; du -sh "$d";
            rm "$d/a" "$d/c" "$d/link"; rmdir "$d"''')
        add('fakeroot ownership', f'''sudo sh -ec 'd={work}/ownership; mkdir "$d";
            touch "$d/a"; chown 1234:1234 "$d/a";
            test "$(stat -c %u:%g "$d/a")" = 1234:1234; rm "$d/a"; rmdir "$d"' ''')
        payload = Path(directory, 'payload')
        payload.write_text('compression-ok\n')
        for compressor, decompress in [('gzip', 'gzip -dc'), ('xz', 'xz -dc'), ('zstd', 'zstd -q -dc')]:
            add(compressor, f'{compressor} -c {work}/payload | {decompress}', 'compression-ok', optional=compressor)
        add('tar', f'tar -czf {work}/archive.tar.gz -C {work} payload; tar -xOzf {work}/archive.tar.gz payload', 'compression-ok')
        add('sha256sum', f'sha256sum {work}/payload')
        import zipfile
        with zipfile.ZipFile(Path(directory, 'archive.zip'), 'w') as archive:
            archive.writestr('payload', 'unzip-ok')
        add('unzip', f'unzip -p {work}/archive.zip payload', 'unzip-ok', optional='unzip')
        fixture = Path(directory, 'package')
        (fixture / 'DEBIAN').mkdir(parents=True)
        (fixture / 'DEBIAN').chmod(0o755)
        (fixture / 'DEBIAN/control').write_text('Package: dextop-command-smoke\nVersion: 1.0\nArchitecture: all\nMaintainer: Dextop\nDescription: isolated command compatibility probe\n')
        (fixture / 'usr/share/dextop-command-smoke').mkdir(parents=True)
        (fixture / 'usr/share/dextop-command-smoke/ok').write_text('dpkg-ok\n')
        add('dpkg install and purge (isolated)', f'''sudo sh -ec '
            mkdir -p {work}/installed {work}/db;
            dpkg-deb --build {work}/package {work}/probe.deb;
            dpkg --root={work}/installed --admindir={work}/db -i {work}/probe.deb;
            test "$(cat {work}/installed/usr/share/dextop-command-smoke/ok)" = dpkg-ok;
            dpkg --root={work}/installed --admindir={work}/db --purge dextop-command-smoke;
            test ! -e {work}/installed/usr/share/dextop-command-smoke/ok;
            echo isolated-dpkg-ok' ''', 'isolated-dpkg-ok', timeout=30)
        add('Python TLS and SQLite', "python3 -c \"import ssl,sqlite3; print(ssl.OPENSSL_VERSION); print(sqlite3.connect(':memory:').execute('select 1').fetchone())\"", '(1,)')
        add('Python socketpair', "python3 -c \"import socket; a,b=socket.socketpair(); a.send(b'ipc-ok'); print(b.recv(6).decode())\"", 'ipc-ok')
        add('Python TCP (fakeroot)', "sudo python3 -c \"import socket; a=socket.socket(); a.bind(('127.0.0.1',0)); a.listen(); b=socket.socket(); b.connect(a.getsockname()); c,_=a.accept(); b.send(b'tcp-ok'); print(c.recv(6).decode())\"", 'tcp-ok')
        if args.network:
            add('DNS', "python3 -c \"import socket; print(len(socket.getaddrinfo('deb.debian.org',443)))\"")
            add('curl HTTPS', 'curl -fIsS --connect-timeout 5 --max-time 10 https://deb.debian.org/debian/', '200')
            add('wget HTTPS', 'wget --no-hsts --spider --timeout=10 https://deb.debian.org/debian/')
            add('openssl', 'openssl version', 'OpenSSL')
        if args.apt_update:
            add('apt update (real repositories)', 'sudo apt-get -o Acquire::Retries=0 -o Acquire::http::Timeout=15 -o Acquire::https::Timeout=15 update', 'Reading package lists', timeout=90)
        counts = {'pass': 0, 'skip': 0, 'fail': 0}
        for name, command, expected, timeout, optional in cases:
            if optional and not shutil.which(optional):
                result = {'name': name, 'status': 'skip', 'reason': 'not installed'}
            else:
                start = time.monotonic()
                process = subprocess.Popen(['/bin/sh', '-c', command], stdout=subprocess.PIPE,
                                           stderr=subprocess.STDOUT, text=True, start_new_session=True)
                try:
                    output, _ = process.communicate(timeout=timeout)
                    code = process.returncode
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    output, _ = process.communicate()
                    code = 'timeout'
                passed = code == 0 and (expected is None or expected in output)
                result = {'name': name, 'status': 'pass' if passed else 'fail', 'code': code,
                          'seconds': round(time.monotonic() - start, 3), 'output': output[-2500:]}
            counts[result['status']] += 1
            print(json.dumps(result, ensure_ascii=False), flush=True)
        print(json.dumps({'summary': counts}), flush=True)
        return int(counts['fail'] > 0)


if __name__ == '__main__':
    raise SystemExit(main())
