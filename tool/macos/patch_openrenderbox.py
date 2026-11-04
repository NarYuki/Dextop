"""Adapt pinned OpenRenderBox path callbacks to Darling's public CGPath API.

Only changes the explicitly supplied source checkout. No system framework or
upstream manifest is replaced. Rendering and SwiftUI ABI support remain separate.
"""

import argparse
from pathlib import Path
import subprocess
import stat


REVISION = "0bb9980ad7aab62231913c659d1909a95e50c5b4"
MARKER = "// Dextop: public CGPath traversal adapter."
ADAPTER = r"""// Dextop: public CGPath traversal adapter.
// Use public traversal rather than Apple's private CGPath entry points.
static size_t CGPathGetNumberOfPoints(CGPathRef path) {
    size_t count = 0;
    if (path) CGPathApply(path, &count, [](void *info, const CGPathElement *element) {
        size_t points = 0;
        switch (element->type) {
            case kCGPathElementMoveToPoint:
            case kCGPathElementAddLineToPoint: points = 1; break;
            case kCGPathElementAddQuadCurveToPoint: points = 2; break;
            case kCGPathElementAddCurveToPoint: points = 3; break;
            case kCGPathElementCloseSubpath: break;
        }
        *static_cast<size_t *>(info) += points;
    });
    return count;
}
static size_t CGPathGetNumberOfElements(CGPathRef path) {
    size_t count = 0;
    if (path) CGPathApply(path, &count, [](void *info, const CGPathElement *) {
        ++*static_cast<size_t *>(info);
    });
    return count;
}
typedef void (^CGPathApplyBlock2)(const CGPathElement *element, bool *stop);
static void CGPathApplyWithBlock2(CGPathRef path, CGPathApplyBlock2 block) {
    struct Context { CGPathApplyBlock2 block; bool stopped; } context = {block, false};
    if (path) CGPathApply(path, &context, [](void *info, const CGPathElement *element) {
        auto *context = static_cast<Context *>(info);
        if (!context->stopped) context->block(element, &context->stopped);
    });
}"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    args = parser.parse_args()
    source = args.source.resolve()
    revision = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    if revision != REVISION:
        parser.error("OpenRenderBox revision differs from the reviewed source pin")
    path = source / "Sources/OpenRenderBoxCxx/Path/ORBPathCallbacks.cpp"
    text = path.read_text()
    storage = source / "Sources/OpenRenderBoxCxx/Path/ORBPath.cpp"
    original = storage.read_text()
    if MARKER in text:
        if "const_cast<CGPath *>(cgPath)" in original:
            raise RuntimeError("OpenRenderBox adapter is only partially applied")
        print("OpenRenderBox public CGPath adapter already applied")
        return
    if original.count("const_cast<CGPath *>(cgPath)") != 3:
        raise RuntimeError("Pinned OpenRenderBox path storage anchors changed")
    start = text.index("CG_EXTERN size_t CGPathGetNumberOfPoints")
    end = text.index("\n\nnamespace {", start)
    text = text[:start] + ADAPTER + text[end:]
    # Darling's CGPathRef currently aliases a mutable path pointer. These
    # callbacks do not mutate it; accommodate the header without changing ABI.
    for expression in ("object", "otherObject"):
        for cast in ("reinterpret_cast", "static_cast"):
            text = text.replace(f"{cast}<CGPathRef>({expression})",
                                f"static_cast<CGPathRef>(const_cast<void *>({expression}))")
    path.chmod(path.stat().st_mode | stat.S_IWUSR)
    path.write_text(text)
    storage.chmod(storage.stat().st_mode | stat.S_IWUSR)
    storage.write_text(original.replace("const_cast<CGPath *>(cgPath)", "cgPath"))
    print("Applied OpenRenderBox public CGPath adapter")


if __name__ == "__main__":
    main()
