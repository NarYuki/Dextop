#include <X11/Xlib.h>
#include <X11/Xutil.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

int main(int argc, char **argv)
{
    Display *x = XOpenDisplay(NULL);
    if (!x) return 1;
    EGLDisplay d = eglGetDisplay((EGLNativeDisplayType)x);
    EGLint major, minor;
    if (!eglInitialize(d, &major, &minor)) return 2;
    EGLint attrs[] = {EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, argc > 1 ? 8 : 0, EGL_NONE};
    EGLConfig config;
    EGLint count, visual_id;
    if (!eglChooseConfig(d, attrs, &config, 1, &count) || count != 1) return 3;
    eglGetConfigAttrib(d, config, EGL_NATIVE_VISUAL_ID, &visual_id);
    XVisualInfo pattern = {.visualid = visual_id};
    int visual_count;
    XVisualInfo *visual = XGetVisualInfo(x, VisualIDMask, &pattern, &visual_count);
    if (!visual || visual_count != 1) return 4;
    XSetWindowAttributes wa = {.colormap = XCreateColormap(x,
        DefaultRootWindow(x), visual->visual, AllocNone)};
    Window window = XCreateWindow(x, DefaultRootWindow(x), 20, 20, 401, 301,
        0, visual->depth, InputOutput, visual->visual, CWColormap, &wa);
    XStoreName(x, window, "Dextop shared GPU presentation test");
    XMapWindow(x, window);
    XSync(x, False);
    EGLSurface surface = eglCreateWindowSurface(d, config,
        (EGLNativeWindowType)window, NULL);
    EGLint context_attrs[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    EGLContext context = eglCreateContext(d, config, EGL_NO_CONTEXT, context_attrs);
    if (!eglMakeCurrent(d, surface, surface, context)) return 5;
    const char *renderer = (const char *)glGetString(GL_RENDERER);
    printf("RENDERER %s depth=%d\n", renderer, visual->depth);
    if (!renderer || !strstr(renderer, "zink") || !strstr(renderer, "Adreno")) return 6;
    int result = 0;
    for (int color = 0; color < 2; color++) {
        int matches = 0;
        for (int attempt = 0; attempt < 12 && matches < 3; attempt++) {
            glClearColor(color == 0, color == 1, 0, 1);
            glClear(GL_COLOR_BUFFER_BIT);
            glFinish();
            unsigned char pixel[4] = {0};
            glReadPixels(10, 10, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            if (pixel[color] != 255 || glGetError() != GL_NO_ERROR) return 7;
            if (!eglSwapBuffers(d, surface)) return 8;
            usleep(100000);
            XSync(x, False);
            XImage *image = XGetImage(x, window, 0, 0, 30, 30, AllPlanes, ZPixmap);
            if (!image) return 9;
            unsigned long actual = XGetPixel(image, 10, 10) & 0xffffff;
            XDestroyImage(image);
            unsigned long expected = color == 0 ? 0xff0000 : 0x00ff00;
            matches = actual == expected ? matches + 1 : 0;
            printf("color=%d X11=%06lx expected=%06lx matches=%d\n",
                color, actual, expected, matches);
        }
        if (matches < 3) result = 10;
    }
    eglMakeCurrent(d, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(d, context);
    eglDestroySurface(d, surface);
    eglTerminate(d);
    XDestroyWindow(x, window);
    XFreeColormap(x, wa.colormap);
    XFree(visual);
    XCloseDisplay(x);
    return result;
}
