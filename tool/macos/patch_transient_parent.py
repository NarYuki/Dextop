"""Parent transient X11 windows to the key window in panel-only applications."""
import argparse
from pathlib import Path

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    args = parser.parse_args()
    path = args.source / 'src/external/cocotron/AppKit/X11.backend/X11Window.m'
    text = path.read_text()
    old = '''    if (isTransient && [NSApp mainWindow]) {
        X11Window *mainWindow =
                (X11Window *) [[NSApp mainWindow] platformWindow];
        XSetTransientForHint(_display, _window, [mainWindow windowHandle]);
    }'''
    new = '''    // Menu-bar utilities can have a key panel without a main window.
    NSWindow *transientParent = [NSApp keyWindow] ?: [NSApp mainWindow];
    if (isTransient && transientParent && transientParent != delegate) {
        X11Window *parentWindow = (X11Window *) [transientParent platformWindow];
        XSetTransientForHint(_display, _window, [parentWindow windowHandle]);
    }'''
    if new in text: return
    if text.count(old) != 1: raise RuntimeError('Pinned transient parent anchor changed')
    path.write_text(text.replace(old, new))

if __name__ == '__main__': main()
