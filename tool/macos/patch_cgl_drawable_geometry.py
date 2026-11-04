"""Refresh EGL drawable geometry before presenting a CGL window surface."""
import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source",required=True,type=Path)
    path = parser.parse_args().source / "src/frameworks/OpenGL/OpenGL.c"
    text = path.read_text()
    # Upgrade the opt-in experiment in a development checkout, if present.
    diagnostic = '    if (getenv("DEXTOP_DARLING_REFRESH_EGL_GEOMETRY")) {'
    if diagnostic in text:
        if text.count(diagnostic) != 1:
            raise RuntimeError("Unexpected EGL geometry diagnostic")
        start = text.index(diagnostic)
        end = text.find("    glFinish();\n    if (context->paced)",start)
        if end < 0:
            raise RuntimeError("Unexpected EGL diagnostic boundary")
        text = text[:start] + text[end:]
    anchor = "    glFinish();\n    if (context->paced)"
    replacement = '''    // Synchronize EGL's cached geometry after native-window resizing.
    // X11's swrast presenter clips transfers using EGLSurface.Height, even
    // when the GPU attachment has already grown to the new drawable size.
    EGLint drawableHeight = 0;
    if (!eglQuerySurface(context->egl_display, context->egl_surface,
                         EGL_HEIGHT, &drawableHeight) || drawableHeight <= 0)
        return kCGLBadDrawable;
    glFinish();
    if (context->paced)'''
    if replacement not in text:
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned CGL presentation anchor changed")
        text = text.replace(anchor,replacement)
    path.write_text(text)


if __name__ == "__main__":
    main()
