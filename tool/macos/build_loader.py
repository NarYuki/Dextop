"""Cross-build the ARM64 ELF loader using a configured Darling build's headers."""

import argparse
import json
from pathlib import Path
import shlex
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--build", required=True, type=Path)
    parser.add_argument("--zig", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    entries = json.loads((args.build / "compile_commands.json").read_text())
    selected = [entry for entry in entries if
                "src/startup/mldr/CMakeFiles/mldr.dir/" in entry["command"] or
                "src/startup/mldr/CMakeFiles/mldr_dserver_rpc.dir/" in entry["command"]]
    if len(selected) != 6:
        raise RuntimeError(f"Expected five loader sources and one RPC source, found {len(selected)}")
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    objects = []
    for index, entry in enumerate(selected):
        command = shlex.split(entry["command"])[1:]
        target = output / f"loader-{index}.o"
        position = command.index("-o")
        command[position + 1] = str(target)
        subprocess.run([str(args.zig.resolve()), "cc", "-target", "aarch64-linux-gnu.2.31", *command],
                       cwd=entry["directory"], check=True)
        objects.append(str(target))
    subprocess.run([str(args.zig.resolve()), "cc", "-target", "aarch64-linux-gnu.2.31",
                    *objects, "-lrt", "-ldl", "-lm", "-pthread", "-o", str(output / "mldr")], check=True)
    print(f"Built ARM64 loader: {output / 'mldr'}. This is not a complete runtime.")


if __name__ == "__main__":
    main()
