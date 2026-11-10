#!/usr/bin/python3
"""Offline OpenPGP/agent smoke test using disposable keys and isolated homes.

Run inside the Linux container. No existing keyring, keyserver or user key is used.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


SECRET = b'dextop-disposable-test-passphrase\n'


def main():
    base = Path(tempfile.mkdtemp(prefix='dextop-gpg-smoke-'))
    homes = [base/'primary 日本語 space', base/'imported 日本語 space']
    results = []
    for home in homes:
        home.mkdir(mode=0o700)
    def gpg(home, *args, data=None, success=True):
        cmd = ['gpg', '--homedir', str(home), '--batch', '--no-auto-key-retrieve', *args]
        x = subprocess.run(cmd, input=data, capture_output=True, timeout=25)
        if (x.returncode == 0) != success:
            raise RuntimeError('gpg operation failed: ' + ' '.join(args[:3]) + ': ' + x.stderr.decode(errors='replace')[-1500:])
        return x.stdout
    def protected(home, *args, secret=SECRET, success=True):
        return gpg(home, '--pinentry-mode', 'loopback', '--passphrase-fd', '0', *args, data=secret, success=success)
    def kill(home):
        subprocess.run(['gpgconf', '--homedir', str(home), '--kill', 'gpg-agent'], capture_output=True, timeout=5)
    try:
        primary, imported = homes
        protected(primary, '--quick-generate-key', 'Dextop Offline Test <audit@example.invalid>', 'ed25519', 'sign', '0')
        listing = gpg(primary, '--with-colons', '--list-secret-keys').decode()
        fingerprint = next(line.split(':')[9] for line in listing.splitlines() if line.startswith('fpr:'))
        protected(primary, '--quick-add-key', fingerprint, 'cv25519', 'encr', '0')
        results.append('passphrase-protected Ed25519 primary and Curve25519 encryption subkey')
        message = base/'message.txt'; message.write_bytes('Dextop offline test: 日本語\n'.encode())
        signature = base/'signature.asc'
        protected(primary, '--armor', '--output', str(signature), '--detach-sign', str(message))
        gpg(primary, '--verify', str(signature), str(message))
        results.append('detached signature and verification')
        message.write_bytes(b'tampered')
        gpg(primary, '--verify', str(signature), str(message), success=False)
        message.write_bytes('Dextop offline test: 日本語\n'.encode())
        results.append('tampered signature rejected')
        encrypted = base/'message.gpg'
        gpg(primary, '--trust-model', 'always', '--recipient', fingerprint, '--output', str(encrypted), '--encrypt', str(message))
        assert protected(primary, '--decrypt', str(encrypted)) == message.read_bytes()
        results.append('public-key encryption and exact plaintext recovery')
        symmetric = base/'symmetric.gpg'
        protected(primary, '--cipher-algo', 'AES256', '--output', str(symmetric), '--symmetric', str(message))
        assert protected(primary, '--decrypt', str(symmetric)) == message.read_bytes()
        results.append('AES256 symmetric encryption and decryption')
        public = base/'public.asc'; public.write_bytes(gpg(primary, '--armor', '--export', fingerprint))
        private = base/'private.asc'; private.write_bytes(protected(primary, '--armor', '--export-secret-keys', fingerprint)); private.chmod(0o600)
        gpg(imported, '--import', str(public))
        protected(imported, '--import', str(private))
        assert protected(imported, '--decrypt', str(encrypted)) == message.read_bytes()
        results.append('public/private key export and import into a separate home')
        before = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in (primary/'private-keys-v1.d').iterdir()}
        kill(primary)
        protected(primary, '--output', str(base/'wrong.sig'), '--detach-sign', str(message), secret=b'incorrect-test-password\n', success=False)
        after = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in (primary/'private-keys-v1.d').iterdir()}
        assert before == after
        results.append('wrong passphrase rejected with private key files unchanged')
        kill(primary)
        protected(primary, '--output', str(base/'restarted.sig'), '--detach-sign', str(message))
        gpg(primary, '--verify', str(base/'restarted.sig'), str(message))
        results.append('agent restart and protected-key reuse')
        kill(primary)
        pinentry = base/'test-pinentry'
        pinentry.write_text('#!/bin/sh\nprintf "OK test pinentry ready\\n"\nwhile IFS= read -r line; do\ncase "$line" in\nGETPIN) printf "D dextop-disposable-test-passphrase\\nOK\\n" ;;\nBYE) printf "OK\\n"; exit 0 ;;\n*) printf "OK\\n" ;;\nesac\ndone\n')
        pinentry.chmod(0o700)
        (primary/'gpg-agent.conf').write_text('pinentry-program '+str(pinentry)+'\n')
        gpg(primary, '--pinentry-mode', 'ask', '--output', str(base/'pinentry.sig'), '--detach-sign', str(message))
        gpg(primary, '--verify', str(base/'pinentry.sig'), str(message))
        results.append('agent-to-pinentry Assuan interaction without loopback')
        kill(primary)
        pinentry.write_text('#!/bin/sh\nprintf "OK ready\\n"\nwhile IFS= read -r line; do\ncase "$line" in\nGETPIN) printf "ERR 83886179 Operation cancelled\\n" ;;\nBYE) exit 0 ;;\n*) printf "OK\\n" ;;\nesac\ndone\n')
        gpg(primary, '--pinentry-mode', 'ask', '--output', str(base/'cancelled.sig'), '--detach-sign', str(message), success=False)
        results.append('cancelled pinentry rejects signing')
        kill(primary)
        protected(primary, '--quick-add-key', fingerprint, 'ed25519', 'auth', '0')
        assert gpg(primary, '--export-ssh-key', fingerprint).startswith(b'ssh-ed25519 ')
        results.append('authentication subkey and SSH public key export')
        protected(imported, '--quick-generate-key', 'Dextop RSA Test <rsa@example.invalid>', 'rsa2048', 'sign', '0')
        protected(imported, '--local-user', 'rsa@example.invalid', '--output', str(base/'rsa.sig'), '--detach-sign', str(message))
        gpg(imported, '--verify', str(base/'rsa.sig'), str(message))
        results.append('RSA2048 protected-key signing and verification')
        print(json.dumps({'passed':len(results), 'checks':results}, ensure_ascii=False))
    finally:
        for home in homes:
            try:kill(home)
            except Exception:pass
        shutil.rmtree(base)


if __name__ == '__main__':
    main()
