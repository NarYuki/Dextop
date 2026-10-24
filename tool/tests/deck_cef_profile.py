"""Sample Deck CEF targets through its guest-only CDP port; never navigate UI.

Run inside the container with its session preload. Output omits URLs, titles,
page contents and account identifiers. A timeout is reported as a timeout,
not evidence that any particular renderer is hung.
"""
import argparse
import base64
import collections
import concurrent.futures
import hashlib
import http.client
import json
import os
from pathlib import Path
import socket
import struct
import time
from urllib.parse import urlsplit


class CDP:
    def __init__(self, address):
        address = urlsplit(address)
        if address.scheme != 'ws' or address.hostname not in ('127.0.0.1', 'localhost'):
            raise ValueError('Only local Deck CEF endpoints are accepted')
        self.socket = socket.create_connection(('127.0.0.1', address.port), timeout=5)
        self.sequence = 0
        self.buffer = b''
        key = base64.b64encode(os.urandom(16)).decode()
        self.socket.sendall((f'GET {address.path} HTTP/1.1\r\nHost: 127.0.0.1:{address.port}\r\n'
                             'Upgrade: websocket\r\nConnection: Upgrade\r\n'
                             f'Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n').encode())
        while b'\r\n\r\n' not in self.buffer:
            self.buffer += self.read()
        header, self.buffer = self.buffer.split(b'\r\n\r\n', 1)
        accept = base64.b64encode(hashlib.sha1((key+'258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest())
        if not header.startswith(b'HTTP/1.1 101') or accept not in header:
            raise ValueError('CEF WebSocket handshake failed')

    def read(self):
        data = self.socket.recv(65536)
        if not data:
            raise EOFError('CEF disconnected')
        return data

    def take(self, size):
        while len(self.buffer) < size:
            self.buffer += self.read()
        result, self.buffer = self.buffer[:size], self.buffer[size:]
        return result

    def send(self, opcode, data):
        size = len(data)
        header = bytes([0x80 | opcode, 0x80 | size]) if size < 126 else (
            bytes([0x80 | opcode, 0xfe])+struct.pack('!H', size) if size < 65536 else
            bytes([0x80 | opcode, 0xff])+struct.pack('!Q', size))
        mask = os.urandom(4)
        self.socket.sendall(header+mask+bytes(value ^ mask[i % 4] for i, value in enumerate(data)))

    def receive(self):
        message = b''
        while True:
            first, second = self.take(2)
            size = second & 127
            if size == 126:
                size = struct.unpack('!H', self.take(2))[0]
            elif size == 127:
                size = struct.unpack('!Q', self.take(8))[0]
            if second & 128 or size > 32*1024*1024:
                raise ValueError('Invalid CEF WebSocket frame')
            payload = self.take(size)
            opcode = first & 15
            if opcode == 8:
                raise EOFError('CEF closed the target')
            if opcode == 9:
                self.send(10, payload)
                continue
            if opcode == 10:
                continue
            if opcode not in (0, 1):
                raise ValueError('Unexpected CEF WebSocket opcode')
            message += payload
            if first & 128:
                return json.loads(message)

    def call(self, method, params=None):
        self.sequence += 1
        sequence = self.sequence
        self.send(1, json.dumps({'id': sequence, 'method': method, 'params': params or {}}).encode())
        while True:
            response = self.receive()
            if response.get('id') == sequence:
                if 'error' in response:
                    raise ValueError('CEF rejected '+method)
                return response.get('result', {})


def profile(target, duration, index):
    cdp = None
    try:
        cdp = CDP(target['webSocketDebuggerUrl'])
        cdp.call('Profiler.enable')
        cdp.call('Profiler.setSamplingInterval', {'interval': 1000})
        cdp.call('Profiler.start')
        time.sleep(duration)
        data = cdp.call('Profiler.stop')['profile']
        counts = collections.Counter(data.get('samples', []))
        functions = collections.Counter()
        for node in data['nodes']:
            functions[node['callFrame'].get('functionName', '')[:160]] += counts[node['id']]
        return {'target_index': index, 'status': 'profiled', 'sample_count': sum(counts.values()),
                'duration_us': data['endTime']-data['startTime'], 'top_functions': functions.most_common(25)}
    except (OSError, EOFError, ValueError, KeyError) as error:
        return {'target_index': index, 'status': type(error).__name__}
    finally:
        if cdp:
            cdp.socket.close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--duration', type=int, default=8)
    parser.add_argument('--port-file', type=Path, default=Path('/tmp/dextop-steam-cdp-port'))
    args = parser.parse_args()
    if not 1 <= args.duration <= 30:
        parser.error('duration must be 1–30 seconds')
    port = int(args.port_file.read_text().strip())
    if not 1 <= port <= 65535:
        parser.error('invalid CDP port')
    http = http.client.HTTPConnection('127.0.0.1', port, timeout=5)
    try:
        http.request('GET', '/json/list')
        response = http.getresponse()
        if response.status != 200:
            raise RuntimeError('CEF target enumeration failed')
        targets = [target for target in json.load(response)
                   if target.get('type') == 'page' and target.get('webSocketDebuggerUrl')][:8]
    finally:
        http.close()
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        results = list(executor.map(lambda item: profile(item[1], args.duration, item[0]), enumerate(targets)))
    print(json.dumps({'targets': len(targets), 'profiles': results}, ensure_ascii=False, indent=2))
