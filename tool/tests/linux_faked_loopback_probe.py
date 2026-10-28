"""Run with only the private faked policy preloaded into this diagnostic process."""
import socket

for family, wildcard, expected in ((socket.AF_INET, '0.0.0.0', '127.0.0.1'),
                                    (socket.AF_INET6, '::', '::1')):
    with socket.socket(family) as listener:
        listener.bind((wildcard, 0))
        listener.listen(1)
        actual = listener.getsockname()[0]
        assert actual == expected, (actual, expected)
        with socket.socket(family) as client:
            client.connect(listener.getsockname())
            peer, _ = listener.accept()
            with peer:
                client.sendall(b'protocol unchanged')
                assert peer.recv(64) == b'protocol unchanged'
        print(f'{wildcard} -> {actual}: loopback round trip passed')

    with socket.socket(family) as implicit:
        implicit.listen(1)
        assert implicit.getsockname()[0] == expected, implicit.getsockname()
        print(f'{wildcard}: implicit listen also binds {expected}')
