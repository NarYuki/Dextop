"""Exercise the own-source Mach-O TCP probe against an independent Linux server."""
import argparse
import os
from pathlib import Path
import queue
import socket
import subprocess
import sys
import threading
import time

HERE = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("runtime", "executable", "prefix", "log"):
        parser.add_argument("--" + name,required=True,type=Path)
    args = parser.parse_args()
    errors = queue.Queue()
    with socket.socket() as listener, socket.socket() as refused:
        listener.bind(("127.0.0.1",0)); listener.listen(2); listener.settimeout(10)
        refused.bind(("127.0.0.1",0))  # Reserved port, deliberately not listening.
        def server():
            try:
                with listener.accept()[0] as client:
                    client.settimeout(10)
                    time.sleep(0.15)  # Exercise send backpressure before reading.
                    count = 0
                    while True:
                        data = client.recv(32768)
                        if not data: break
                        if data != b"a" * len(data): raise AssertionError("Corrupted TCP payload")
                        count += len(data)
                    if count != 4 * 1024 * 1024: raise AssertionError(f"Wrong payload length: {count}")
                    client.sendall(b"he"); time.sleep(0.05); client.sendall(b"llo")
                    client.shutdown(socket.SHUT_WR)
                with listener.accept()[0] as client:
                    client.settimeout(10)
                    if client.recv(1) != b"": raise AssertionError("Cancellation did not close socket")
            except BaseException as error:
                errors.put(error)
        thread = threading.Thread(target=server,daemon=True)
        thread.start()
        env = dict(os.environ,DEXTOP_NETWORK_TEST_PORT=str(listener.getsockname()[1]),
                   DEXTOP_NETWORK_REFUSED_PORT=str(refused.getsockname()[1]))
        args.log.parent.mkdir(parents=True,exist_ok=True)
        with args.log.open("wb") as output:
            process = subprocess.Popen([sys.executable,str(HERE / "launch_app.py"),
                "--runtime",str(args.runtime.resolve()),"--prefix",str(args.prefix.resolve()),
                "--executable",str(args.executable.resolve())],env=env,stdout=output,stderr=subprocess.STDOUT)
            try:
                result = process.wait(timeout=45)
            except subprocess.TimeoutExpired:
                process.terminate()  # Let launch_app shut down its own Darling prefix.
                process.wait(timeout=45)
                raise RuntimeError("Network probe timed out; see its log")
        thread.join(timeout=11)
        if thread.is_alive(): raise RuntimeError("Independent server did not finish")
        if not errors.empty(): raise RuntimeError("Independent server failed") from errors.get()
        expected = "network: TCP payload, fragmented receive, EOF, cancellation and refusal OK"
        content = args.log.read_text(errors="replace")
        if result != 0 or expected not in content:
            raise RuntimeError(f"Network probe failed ({result}):\n{content[-8000:]}")
        print(expected)


if __name__ == "__main__":
    main()
