"""Keep key-window activation while X focus temporarily enters an app menu."""
import argparse
from pathlib import Path

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    args = parser.parse_args()
    path = args.source / 'src/external/cocotron/AppKit/X11.backend/X11Display.m'
    text = path.read_text()
    marker = '// Menu input focus does not replace the application key window.'
    if marker in text: return
    anchor = '        if ([delegate attachedSheet]) {'
    replacement = '''        // Menu input focus does not replace the application key window.
        if ([delegate isKindOfClass:NSClassFromString(@"NSMenuWindow")])
            break;
''' + anchor
    if text.count(anchor) != 1: raise RuntimeError('Pinned FocusIn anchor changed')
    text = text.replace(anchor, replacement)
    anchor = '''        [delegate platformWindowDeactivated: window
                    checkForAppDeactivation: NO];'''
    replacement = '''        Window nextFocus = None; int nextRevert = 0;
        XGetInputFocus(_display, &nextFocus, &nextRevert);
        X11Window *nextWindow = [self windowForID:nextFocus];
        if ([[nextWindow delegate] isKindOfClass:NSClassFromString(@"NSMenuWindow")])
            break;
''' + anchor
    if text.count(anchor) != 1: raise RuntimeError('Pinned FocusOut anchor changed')
    path.write_text(text.replace(anchor, replacement))

if __name__ == '__main__': main()
