package moe.n4tsu.dextop.linux

/** Mesa 25.0.7 KGSL integration patches. Original Mesa sources retain their MIT licence.
 * The EGL path is opt-in for KGSL: Kopper selects the hardware through Vulkan,
 * while EGL uses its device without a DRM node for device bookkeeping.
 */
internal object LinuxMesaPatches {
    val script = """patch -p1 <<'DEXTOP_MESA_PATCH'
--- a/src/freedreno/vulkan/tu_knl_kgsl.cc
+++ b/src/freedreno/vulkan/tu_knl_kgsl.cc
@@ -11,6 +11,7 @@
 #include <stdint.h>
 #include <sys/ioctl.h>
 #include <sys/mman.h>
+#include <time.h>
 #include <linux/dma-heap.h>
 
 #define __user
@@ -1319,10 +1320,38 @@
    /* No-op */
 }
 
+/* Dextop: the GPU's always-on counter (19.2 MHz, the clock of GPU
+ * timestamps) through KGSL's perfcounter interface. Zink calibrates CPU and
+ * GPU time with it; leaving it unimplemented made Zink applications fail.
+ * When KGSL refuses the read, CLOCK_MONOTONIC in the same unit keeps
+ * callers working. */
 static int
 kgsl_device_get_gpu_timestamp(struct tu_device *dev, uint64_t *ts)
 {
-   unreachable("");
+   struct kgsl_perfcounter_read_group group = {
+      .groupid = KGSL_PERFCOUNTER_GROUP_ALWAYSON,
+      .countable = 0,
+   };
+   struct kgsl_perfcounter_read read = {
+      .reads = &group,
+      .count = 1,
+   };
+   if (safe_ioctl(dev->fd, IOCTL_KGSL_PERFCOUNTER_READ, &read) == 0 && group.value != 0) {
+      *ts = group.value;
+      return 0;
+   }
+   struct kgsl_perfcounter_get get = {
+      .groupid = KGSL_PERFCOUNTER_GROUP_ALWAYSON,
+      .countable = 0,
+   };
+   if (safe_ioctl(dev->fd, IOCTL_KGSL_PERFCOUNTER_GET, &get) == 0 &&
+       safe_ioctl(dev->fd, IOCTL_KGSL_PERFCOUNTER_READ, &read) == 0 && group.value != 0) {
+      *ts = group.value;
+      return 0;
+   }
+   struct timespec now;
+   clock_gettime(CLOCK_MONOTONIC, &now);
+   *ts = ((uint64_t) now.tv_sec * 1000000000ull + now.tv_nsec) * 192 / 10000;
    return 0;
 }
 
--- a/src/egl/drivers/dri2/platform_wayland.c
+++ b/src/egl/drivers/dri2/platform_wayland.c
@@ -2999,6 +2999,10 @@
    if (!dri2_dpy)
       return EGL_FALSE;
 
+   /* KGSL has no DRM render node. Kopper obtains the GPU through Vulkan;
+    * keep EGL device bookkeeping independent from the rendering device. */
+   const bool kgsl = disp->Options.Zink && getenv("DEXTOP_WAYLAND_KGSL") &&
+                     strcmp(getenv("DEXTOP_WAYLAND_KGSL"), "1") == 0;
    disp->DriverData = (void *)dri2_dpy;
 
    if (dri2_wl_formats_init(&dri2_dpy->formats) < 0)
@@ -3042,7 +3046,7 @@
                           dri2_dpy->formats.num_formats))
       goto cleanup;
 
-   if (disp->Options.Zink) {
+   if (disp->Options.Zink && !kgsl) {
       if (!dri2_initialize_wayland_drm_extensions(dri2_dpy) && !disp->Options.ForceSoftware)
          goto cleanup;
 
@@ -3079,7 +3083,7 @@
    if (!dri2_create_screen(disp))
       goto cleanup;
 
-   if (!dri2_setup_device(disp, disp->Options.ForceSoftware)) {
+   if (!dri2_setup_device(disp, disp->Options.ForceSoftware || kgsl)) {
       _eglError(EGL_NOT_INITIALIZED, "DRI2: failed to setup EGLDevice");
       goto cleanup;
    }
DEXTOP_MESA_PATCH
"""
}
