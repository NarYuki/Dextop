"""Include the existing curl dependency closure in GUI runtime installations."""

import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    path = parser.parse_args().source / "src/CMakeLists.txt"
    text = path.read_text()
    marker = "# Dextop GUI applications also link the public networking libraries."
    if marker in text:
        return
    projects = ("nghttp2", "passwordserver_sasl", "curl", "OpenLDAP")
    for project in projects:
        old = f"\tadd_subdirectory(external/{project})\n"
        if text.count(old) != 1:
            raise RuntimeError(f"Pinned networking project does not match: {project}")
        text = text.replace(old, "")
    anchor = 'if (COMPONENT_cli)\n\tset(CMAKE_INSTALL_DEFAULT_COMPONENT_NAME "cli")\n\n\tadd_subdirectory(external/darling-dmg)'
    if text.count(anchor) != 1:
        raise RuntimeError("Pinned CLI component does not match")
    block = marker + "\nif (COMPONENT_cli OR COMPONENT_gui_frameworks)\n"
    block += '\tset(CMAKE_INSTALL_DEFAULT_COMPONENT_NAME "gui_frameworks")\n'
    block += "".join(f"\tadd_subdirectory(external/{project})\n" for project in projects)
    block += "endif()\n\n"
    path.write_text(text.replace(anchor, block + anchor))


if __name__ == "__main__":
    main()
