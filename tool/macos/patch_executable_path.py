"""Keep prefix-installed executable paths in the Darwin namespace.

The shared runtime root locates dyld; the per-session socket identifies the
application prefix. They are distinct when multiple prefixes share a runtime.
"""

import argparse
from pathlib import Path


INSERT = r'''
	int dextop_prefix_executable = 0;
	// Dextop: distinguish the application prefix from the shared dyld root.
	if (lr->socket_path && lr->socket_path[0] == '/') {
		const char *suffix = strrchr(lr->socket_path, '/');
		if (suffix && strcmp(suffix, "/.darlingserver.sock") == 0) {
			size_t prefix_length = (size_t)(suffix - lr->socket_path);
			if (prefix_length > 0 &&
			    strncmp(executable_buf, lr->socket_path, prefix_length) == 0 &&
			    executable_buf[prefix_length] == '/') {
				memmove(executable_buf, executable_buf + prefix_length,
				        strlen(executable_buf + prefix_length) + 1);
				dextop_prefix_executable = 1;
			}
		}
	}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    args = parser.parse_args()
    path = args.source / "src/startup/mldr/stack.c"
    text = path.read_text()
    if "// Dextop: distinguish the application prefix" in text:
        return
    anchor = "\texecutable_path = executable_buf;\n\n\tif (lr->root_path)"
    if text.count(anchor) != 1:
        raise RuntimeError("Executable path stack anchor changed")
    replacement = INSERT + "\n" + anchor.replace("if (lr->root_path)", "if (lr->root_path && !dextop_prefix_executable)")
    path.write_text(text.replace(anchor, replacement))


if __name__ == "__main__":
    main()
