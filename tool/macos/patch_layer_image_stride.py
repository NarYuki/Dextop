"""Honor CGImage row stride when uploading 32-bit layer contents to OpenGL."""
import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    path = parser.parse_args().source / "src/external/cocotron/QuartzCore/CARenderer.m"
    text = path.read_text()
    normalization = """    CGBitmapInfo bitmapInfo = CGImageGetBitmapInfo(image);
    // Normalize formats that cannot be uploaded as premultiplied BGRA/RGBA.
    CGImageAlphaInfo sourceAlpha = bitmapInfo & kCGBitmapAlphaInfoMask;
    CGBitmapInfo sourceOrder = bitmapInfo & kCGBitmapByteOrderMask;
    BOOL nativePixels = CGImageGetBitsPerComponent(image) == 8 &&
        CGImageGetBitsPerPixel(image) == 32 &&
        ((sourceAlpha == kCGImageAlphaPremultipliedFirst && sourceOrder == kCGBitmapByteOrder32Little) ||
         (sourceAlpha == kCGImageAlphaPremultipliedLast && sourceOrder == kCGBitmapByteOrder32Big));
    if (!nativePixels) {
        if (imageWidth > SIZE_MAX / 4 || imageHeight > SIZE_MAX / (imageWidth * 4)) return;
        CGColorSpaceRef space = CGColorSpaceCreateDeviceRGB();
        CGContextRef bitmap = CGBitmapContextCreate(NULL, imageWidth, imageHeight, 8,
            imageWidth * 4, space, kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
        CGColorSpaceRelease(space);
        if (!bitmap) return;
        CGContextDrawImage(bitmap, CGRectMake(0, 0, imageWidth, imageHeight), image);
        CGImageRef normalized = CGBitmapContextCreateImage(bitmap);
        if (normalized) { CATexImage2DCGImage(normalized); CGImageRelease(normalized); }
        CGContextRelease(bitmap);
        return;
    }"""
    if "// Normalize formats that cannot be uploaded" not in text:
        anchor = "    CGBitmapInfo bitmapInfo = CGImageGetBitmapInfo(image);"
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned layer image format anchor changed")
        text = text.replace(anchor, normalization)
    if "// Pack provider rows before uploading layer contents." in text:
        if "glGetIntegerv(GL_UNPACK_SWAP_BYTES" not in text:
            text = text.replace("    GLint alignment, rowLength, skipRows, skipPixels;",
                                "    GLint alignment, rowLength, skipRows, skipPixels, swapBytes;")
            text = text.replace("    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);",
                                "    glGetIntegerv(GL_UNPACK_SWAP_BYTES, &swapBytes);\n"
                                "    glPixelStorei(GL_UNPACK_SWAP_BYTES, GL_FALSE);\n"
                                "    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);")
            text = text.replace("    free(packed);", "    glPixelStorei(GL_UNPACK_SWAP_BYTES, swapBytes);\n    free(packed);")
        path.write_text(text)
        return
    old = """    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, imageWidth, imageHeight, 0,
                 glFormat, glType, pixelBytes);
    CFRelease(data);"""
    new = """    // Pack provider rows before uploading layer contents.
    // CGImage bytesPerRow may include padding, which is not texture content.
    size_t sourceRow = CGImageGetBytesPerRow(image);
    if (CGImageGetBitsPerPixel(image) != 32 || imageWidth > SIZE_MAX / 4) {
        CFRelease(data);
        return;
    }
    size_t packedRow = imageWidth * 4;
    if (sourceRow < packedRow || imageHeight > SIZE_MAX / sourceRow ||
        (size_t)CFDataGetLength(data) < sourceRow * imageHeight) {
        CFRelease(data);
        return;
    }
    uint8_t *packed = NULL;
    if (sourceRow != packedRow) {
        packed = malloc(packedRow * imageHeight);
        if (!packed) { CFRelease(data); return; }
        for (size_t row = 0; row < imageHeight; ++row)
            memcpy(packed + row * packedRow, pixelBytes + row * sourceRow, packedRow);
        pixelBytes = packed;
    }
    GLint alignment, rowLength, skipRows, skipPixels, swapBytes;
    glGetIntegerv(GL_UNPACK_ALIGNMENT, &alignment);
    glGetIntegerv(GL_UNPACK_ROW_LENGTH, &rowLength);
    glGetIntegerv(GL_UNPACK_SKIP_ROWS, &skipRows);
    glGetIntegerv(GL_UNPACK_SKIP_PIXELS, &skipPixels);
    glGetIntegerv(GL_UNPACK_SWAP_BYTES, &swapBytes);
    glPixelStorei(GL_UNPACK_SWAP_BYTES, GL_FALSE);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
    glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
    glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, imageWidth, imageHeight, 0,
                 glFormat, glType, pixelBytes);
    glPixelStorei(GL_UNPACK_ALIGNMENT, alignment);
    glPixelStorei(GL_UNPACK_ROW_LENGTH, rowLength);
    glPixelStorei(GL_UNPACK_SKIP_ROWS, skipRows);
    glPixelStorei(GL_UNPACK_SKIP_PIXELS, skipPixels);
    glPixelStorei(GL_UNPACK_SWAP_BYTES, swapBytes);
    free(packed);
    CFRelease(data);"""
    if text.count(old) != 1:
        raise RuntimeError("Pinned layer image upload anchor changed")
    text = '#include <stdint.h>\n#include <stdlib.h>\n#include <string.h>\n' + text
    path.write_text(text.replace(old, new))


if __name__ == "__main__":
    main()
