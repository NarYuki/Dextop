#!/usr/bin/env python3
"""Bannerlator zero-copy window layers: gralloc-backed Wayland swapchain images.

When the compositor advertises the private global banner_ahb_v1 and wants gralloc buffers right
now, the Wayland WSI allocates
every swapchain image as an Android AHardwareBuffer (gralloc; UBWC where gralloc and the compositor
can take it, see "UBWC" below), imports the buffer's
dma-buf fd into the driver with an explicit DRM format modifier + row pitch (the same path Turnip
takes for any gralloc buffer, minus vk_android.c, which a platforms=wayland build does not have),
still shares it through zwp_linux_dmabuf_v1 (the compositor's blit path keeps working as the
fallback) and hands the AHardwareBuffer itself to the compositor once per image over a socketpair
(AHardwareBuffer_sendHandleToUnixSocket) with banner_ahb_v1.attach. The compositor then puts the
game's own buffer on a SurfaceControl layer: no copy anywhere between the game and the display.

Synchronisation stays Mesa's implicit-sync scheme (DMA_BUF_IOCTL_IMPORT/EXPORT_SYNC_FILE): the
render fence is already in the dma-buf before wl_surface.commit, the compositor exports it as the
layer's acquire fence, and imports the display's release fence back before wl_buffer.release, so
the WSI's existing acquire path (wsi_create_sync_for_dma_buf_wait) waits on it.

Acquire order (the zero-copy fps ceiling). That display release fence only signals when the display
stops scanning the buffer out, about one refresh after the compositor let go of it, and Mesa's
acquire picks the lowest-numbered free image: a buffer just back from the layer was taken straight
away while the other free images sat idle, and the game's GPU queue stalled on the display once per
refresh (uncapped MAILBOX topped out at ~2x the refresh rate, GPU ~27 % busy). For a gralloc chain
the acquire now polls each free image's dma-buf (POLLOUT = every fence signalled, POLLIN = the
writers, i.e. our own render, are done; a buffer ready for POLLIN but not POLLOUT is waiting only on
the display's read fence) and takes, in order: an idle image, one still waiting only on our own
earlier rendering, and only then one the display still holds. Nothing is ever handed out without its
fences: the acquire semaphore still carries every fence of the dma-buf, so this changes which free
image is chosen, never whether it is waited for.

Image count. Even so a 5-image chain runs dry: the layer holds up to three buffers (on screen, queued
in SurfaceFlinger, and the one just released whose fence is still pending), the surface keeps its
current buffer, and one is being rendered, so after every compositor tick the game waited for the
display (D3D11 cube ~480 fps with zero-copy vs ~3400 with the copy path). A MAILBOX / IMMEDIATE
swapchain created while the compositor wants gralloc images therefore gets BANNER_AHB_EXTRA_IMAGES
(2) more images than Mesa would give it; BANNER_WSI_AHB_EXTRA_IMAGES=0..4 overrides that. FIFO chains
are left alone (more images there would only add latency). Vulkan lets the implementation create
more images than minImageCount; every program reads the real count back.

Who decides, and when:

  * banner_ahb_v1 version 2 (the compositor sends `mode`, which it does right after bind and again
    every time the user flips "Zero-copy presentation" in the in-game drawer): the compositor's
    mode decides, per swapchain, when the swapchain is created. BANNER_WSI_AHB=0 still forces the
    whole feature off. BANNER_WSI_AHB=1 does NOT force it on here: the app exports that variable on
    every zero-copy launch, so honouring it would freeze the live switch for exactly the sessions
    that start with zero-copy on.
  * banner_ahb_v1 version 1, or a version 2 compositor that has not sent `mode` yet: exactly the
    original contract -- gralloc images iff BANNER_WSI_AHB=1, decided per swapchain at creation,
    and no swapchain is ever retired for a mode change. A driver built from this patch therefore
    drops straight into an older Bannerlator whose compositor only advertises version 1.

A live mode change retires the swapchains that were built for the other mode: the next
vkAcquireNextImageKHR / vkQueuePresentKHR returns VK_ERROR_OUT_OF_DATE_KHR, which DXVK, vkd3d-proton
and Zink all answer by rebuilding the swapchain -- on gralloc buffers or on standard ones, whichever
the mode now is. The old chain's buffers keep working until the program lets go of it.

Without the global nothing here runs: the WSI behaves exactly as before.

UBWC. QTI gralloc compresses a buffer only when asked to: the vendor usage bit
AHARDWAREBUFFER_USAGE_VENDOR_0 (bit 28, gralloc's GRALLOC_USAGE_PRIVATE_ALLOC_UBWC) together with a GPU
usage and no CPU bit (IsUBwcEnabled in QTI's gr_utils.cpp). Qualcomm's own driver hands the Android
loader that bit for its swapchains; Mesa never sets it, so without it every gralloc swapchain here was
linear. A chain now asks gralloc, in this order:
  1. UBWC: GPU usage + COMPOSER_OVERLAY + VENDOR_0. Only when the chain's modifier list holds
     QCOM_COMPRESSED -- that list is the compositor's modifiers for the format, filtered by what the
     driver can create with this swapchain's usage, flags, format list and compression control, so it
     means both "the compositor imports UBWC here" (the app's BANNER_WAYLAND_UBWC=0 takes it away) and
     "this image may be UBWC" -- and not for storage swapchains or with BANNER_WSI_AHB_LINEAR=1.
  2. plain: GPU usage + COMPOSER_OVERLAY, gralloc's own choice (the only request before; linear on QTI).
  3. CPU-linear: plain + CPU_READ_RARELY, which gralloc can never compress.
A buffer is used only when its native handle is a QTI private handle ('gmsm') whose flags say what it
is (PRIV_FLAGS_UBWC_ALIGNED = UBWC, neither UBWC flag = linear; the UBWC_PI variant counts as
unreadable), vkCreateImage takes gralloc's pitch with that modifier, and -- for UBWC -- gralloc's
buffer holds all of the driver's UBWC image (its layout is the driver's own, from modifier + pitch).
An unreadable handle (the newer grallocs: no 'gmsm') skips request 2 and ends on 3, linear, as
before, and its ints are printed once so a reader for it can be written. So a wrong guess can only
ever cost UBWC, never produce a buffer the driver reads with the wrong layout. Each swapchain gets one
line: "on gralloc buffers: UBWC (QCOM_COMPRESSED), stride N px", or "linear, stride N px (no UBWC:
<why>)".

Written against exact source text rather than diff context so it survives Mesa line drift; every
anchor is asserted, so a Mesa where the WSI changed shape fails the build instead of shipping a
driver without the feature.

Usage: banner_ahb_wsi.py <Mesa root>   (run from anywhere; the glue files live next to this script)
"""
import os
import shutil
import sys

mesa = sys.argv[1]
here = os.path.dirname(os.path.abspath(__file__))
wsi = os.path.join(mesa, 'src/vulkan/wsi')


def patch(path, edits):
    s = open(path).read()
    for old, new, count in edits:
        n = s.count(old)
        assert n == count, "%s: expected %d of %r, found %d" % (path, count, old[:60], n)
        s = s.replace(old, new)
    open(path, 'w').write(s)


# 1. The client-side protocol glue, pre-generated with wayland-scanner 1.24.0 from
#    banner-ahb-v1.xml (kept next to it), compiled into the WSI like Mesa's own protocols.
for f in ('banner-ahb-v1-client-protocol.h', 'banner-ahb-v1-protocol.c', 'banner-ahb-v1.xml'):
    shutil.copy(os.path.join(here, f), os.path.join(wsi, f))

patch(os.path.join(wsi, 'meson.build'), [(
    "  files_vulkan_wsi += files('wsi_common_wayland.c')\n",
    "  files_vulkan_wsi += files('wsi_common_wayland.c')\n"
    "  files_vulkan_wsi += files('banner-ahb-v1-protocol.c')\n", 1)])

# 2. wsi_common_wayland.c
helpers = r'''
/* ---- Bannerlator zero-copy layers: gralloc-backed swapchain images (BANNER_WSI_AHB=1) ----------
 *
 * See patches/wayland/banner_ahb_wsi.py in banners-turnip-wayland for the design. In short: when
 * the compositor advertises banner_ahb_v1 and BANNER_WSI_AHB=1 is set, each swapchain image is an
 * AHardwareBuffer whose dma-buf fd is imported with an explicit modifier + pitch, shared through
 * zwp_linux_dmabuf_v1 as usual, and handed to the compositor once with banner_ahb_v1.attach.
 * This is a Linux-style build with Android detection off, so libnativewindow is dlopen'd and the
 * few NDK types used are declared here (they are stable ABI).
 */
#include <dlfcn.h>
#include <poll.h>
#include <stdarg.h>
#include <stdio.h>
#include <sys/socket.h>
#include "util/log.h"
#include "util/os_file.h"
#include "vk_format.h"

typedef struct AHardwareBuffer AHardwareBuffer;
struct banner_ahb_desc { /* AHardwareBuffer_Desc */
   uint32_t width, height, layers, format;
   uint64_t usage;
   uint32_t stride, rfu0;
   uint64_t rfu1;
};
struct banner_native_handle { int version; int numFds; int numInts; int data[]; };
#define BANNER_AHB_FORMAT_R8G8B8A8_UNORM    1u
#define BANNER_AHB_FORMAT_R10G10B10A2_UNORM 0x2bu
#define BANNER_AHB_USAGE_CPU_READ_RARELY    (2ull)
#define BANNER_AHB_USAGE_GPU_SAMPLED_IMAGE  (1ull << 8)
#define BANNER_AHB_USAGE_GPU_FRAMEBUFFER    (1ull << 9)
#define BANNER_AHB_USAGE_COMPOSER_OVERLAY   (1ull << 11)
/* AHARDWAREBUFFER_USAGE_VENDOR_0. QTI gralloc reads it as GRALLOC_USAGE_PRIVATE_ALLOC_UBWC (gralloc1's
 * PRODUCER_USAGE_PRIVATE_0): with a GPU usage and no CPU bit it allocates UBWC. Turnip only runs on
 * Qualcomm SoCs, and whatever gralloc does with it is checked on the handle before it is trusted. */
#define BANNER_AHB_USAGE_VENDOR_0           (1ull << 28)
#define BANNER_MOD_QCOM_COMPRESSED          0x0500000000000001ull /* DRM_FORMAT_MOD_QCOM_COMPRESSED */
/* QTI private_handle_t flags (gr_priv_handle.h / gralloc_priv.h). */
#define BANNER_QTI_FLAG_UBWC                0x08000000u /* PRIV_FLAGS_UBWC_ALIGNED */
#define BANNER_QTI_FLAG_UBWC_PI             0x40000000u /* PRIV_FLAGS_UBWC_ALIGNED_PI (YUV only): not ours */

/* Extra images for a gralloc MAILBOX / IMMEDIATE chain (see "Image count" in banner_ahb_wsi.py). */
#define BANNER_AHB_EXTRA_IMAGES     2u
#define BANNER_AHB_EXTRA_IMAGES_MAX 4u

/* The gralloc requests a chain's buffers can be allocated with, in the order they are tried. */
enum {
   BANNER_AHB_REQ_UBWC,   /* + VENDOR_0, no CPU bit: UBWC where gralloc can */
   BANNER_AHB_REQ_PLAIN,  /* no vendor or CPU bit: gralloc's own choice (linear on QTI) */
   BANNER_AHB_REQ_CPU,    /* + CPU_READ_RARELY: never compressed, so linear whatever the handle */
   BANNER_AHB_REQ_COUNT,
};
static const char *const banner_ahb_req_name[BANNER_AHB_REQ_COUNT] = {"UBWC", "plain", "CPU-linear"};

static struct {
   int state; /* 0 = untried, 1 = loaded, -1 = unavailable */
   int (*allocate)(const struct banner_ahb_desc *, AHardwareBuffer **);
   void (*release)(AHardwareBuffer *);
   void (*describe)(const AHardwareBuffer *, struct banner_ahb_desc *);
   const struct banner_native_handle *(*getNativeHandle)(const AHardwareBuffer *);
   int (*sendHandle)(const AHardwareBuffer *, int);
} banner_nw;

static bool
banner_ahb_load(void)
{
   if (banner_nw.state)
      return banner_nw.state == 1;
   banner_nw.state = -1;
   void *lib = dlopen("libnativewindow.so", RTLD_NOW | RTLD_NOLOAD);
   if (!lib)
      lib = dlopen("libnativewindow.so", RTLD_NOW);
   if (!lib) {
      mesa_logw("banner-ahb: dlopen(libnativewindow.so) failed: %s", dlerror());
      return false;
   }
   banner_nw.allocate = dlsym(lib, "AHardwareBuffer_allocate");
   banner_nw.release = dlsym(lib, "AHardwareBuffer_release");
   banner_nw.describe = dlsym(lib, "AHardwareBuffer_describe");
   banner_nw.getNativeHandle = dlsym(lib, "AHardwareBuffer_getNativeHandle");
   banner_nw.sendHandle = dlsym(lib, "AHardwareBuffer_sendHandleToUnixSocket");
   if (!banner_nw.allocate || !banner_nw.release || !banner_nw.describe ||
       !banner_nw.getNativeHandle || !banner_nw.sendHandle) {
      mesa_logw("banner-ahb: libnativewindow.so lacks the AHardwareBuffer API");
      return false;
   }
   banner_nw.state = 1;
   return true;
}

static bool
banner_ahb_env_enabled(void)
{
   const char *e = getenv("BANNER_WSI_AHB");
   return e && (e[0] == '1' || e[0] == 't' || e[0] == 'T' || e[0] == 'y' || e[0] == 'Y');
}

/* An explicit BANNER_WSI_AHB=0 turns the feature off whatever the compositor says. */
static bool
banner_ahb_env_disabled(void)
{
   const char *e = getenv("BANNER_WSI_AHB");
   return e && (e[0] == '0' || e[0] == 'f' || e[0] == 'F' || e[0] == 'n' || e[0] == 'N');
}

/* Does the compositor want gralloc swapchain images right now? Read once per swapchain when it is
 * created, and again (cheaply) on acquire/present to notice the live switch. */
static bool
banner_ahb_want(const struct wsi_wl_display *display)
{
   if (!display->banner_ahb)
      return false;
   /* A version 1 compositor -- and a version 2 one whose first mode event has not arrived yet --
    * keeps the original contract: the environment alone decides. Nothing below this line can ever
    * make such a session behave differently from a driver built before the mode event existed. */
   if (!display->banner_ahb_have_mode)
      return banner_ahb_env_enabled();
   if (banner_ahb_env_disabled())
      return false;
   return display->banner_ahb_mode;
}

/* How many images to add to a swapchain being created: only while the compositor wants gralloc
 * images, and only for the non-blocking present modes. */
static uint32_t
banner_ahb_extra_images(const struct wsi_wl_display *display, VkPresentModeKHR mode)
{
   if (mode != VK_PRESENT_MODE_MAILBOX_KHR && mode != VK_PRESENT_MODE_IMMEDIATE_KHR)
      return 0;
   if (!banner_ahb_want(display))
      return 0;
   const char *e = getenv("BANNER_WSI_AHB_EXTRA_IMAGES");
   if (e && e[0] >= '0' && e[0] <= '9')
      return MIN2((uint32_t)atoi(e), BANNER_AHB_EXTRA_IMAGES_MAX);
   return BANNER_AHB_EXTRA_IMAGES;
}

static void
banner_ahb_handle_mode(void *data, struct banner_ahb_v1 *proxy, uint32_t enabled)
{
   struct wsi_wl_display *display = data;
   bool on = enabled != 0;
   if (display->banner_ahb_have_mode && display->banner_ahb_mode == on)
      return;
   display->banner_ahb_have_mode = true;
   display->banner_ahb_mode = on;
   mesa_logi("banner-ahb: compositor wants %s swapchain images", on ? "gralloc" : "standard");
}

static const struct banner_ahb_v1_listener banner_ahb_listener = {
   .mode = banner_ahb_handle_mode,
};

/* True when this swapchain was built for the other mode and must be rebuilt. Called from the top of
 * both acquire paths and of queue_present, where Mesa already answers its own `retired` flag with
 * VK_ERROR_OUT_OF_DATE_KHR; DXVK, vkd3d-proton and Zink all rebuild on that.
 *
 * The mode event rides the display's own event queue, which in MAILBOX is only dispatched when the
 * acquire loop runs out of free images, so read whatever has arrived without blocking (the same
 * non-blocking dispatch wsi_wl_swapchain_ensure_dispatch does every frame) -- otherwise a switch
 * could sit unnoticed for several frames. Costs one poll(timeout=0) per call.
 *
 * Version 1 compositors never get here: banner_ahb_have_mode stays false, so `want` keeps matching
 * what the chain recorded and no swapchain is ever retired. */
static bool
banner_ahb_mode_changed(struct wsi_wl_swapchain *chain)
{
   struct wsi_wl_display *display = chain->wsi_wl_surface->display;
   if (!display->banner_ahb || display->banner_ahb_version < 2)
      return false;
   struct timespec instant = {0, 0};
   wl_display_dispatch_queue_timeout(display->wl_display, display->queue, &instant);
   if (!display->banner_ahb_have_mode || banner_ahb_want(display) == chain->banner.want)
      return false;
   if (!chain->banner.retire_said) {
      chain->banner.retire_said = true;
      mesa_logi("banner-ahb: zero-copy switched %s: retiring the %ux%u swapchain so the program "
                "rebuilds it on %s buffers", display->banner_ahb_mode ? "on" : "off",
                chain->extent.width, chain->extent.height,
                display->banner_ahb_mode ? "gralloc" : "standard");
   }
   return true;
}

/* The AHardwareBuffer format a swapchain VkFormat can live in, 0 when there is none (gralloc has
 * no BGRA: those swapchains take the standard path, and the surface-format list hides them). */
static uint32_t
banner_ahb_format_for(VkFormat format)
{
   switch (format) {
   case VK_FORMAT_R8G8B8A8_UNORM:
   case VK_FORMAT_R8G8B8A8_SRGB:
      return BANNER_AHB_FORMAT_R8G8B8A8_UNORM;
   case VK_FORMAT_A2B10G10R10_UNORM_PACK32:
      return BANNER_AHB_FORMAT_R10G10B10A2_UNORM;
   default:
      return 0;
   }
}

static bool
banner_ahb_skip_format(const struct wsi_wl_display *display, VkFormat format)
{
   return banner_ahb_want(display) && banner_ahb_format_for(format) == 0;
}

/* Native-handle sniff, the one Mesa's u_gralloc fallback uses (u_gralloc_fallback.c): a QTI
 * gralloc private_handle_t has the magic 'gmsm' as its first int and its flags in the next one,
 * PRIV_FLAGS_UBWC_ALIGNED meaning UBWC. A UBWC_PI buffer carries only PRIV_FLAGS_UBWC_ALIGNED_PI
 * (QTI gr_utils.cpp: YUV formats only, never with COMPOSER_OVERLAY), so reading its flags as "not
 * UBWC" would be wrong: unknown instead. false = unknown layout. */
static bool
banner_ahb_sniff_modifier(const struct banner_native_handle *h, uint64_t *mod)
{
   const uint32_t gmsm = ('g' << 24) | ('m' << 16) | ('s' << 8) | 'm';
   if (!h || h->numFds < 1 || h->numInts < 2)
      return false;
   if ((uint32_t)h->data[h->numFds] != gmsm)
      return false;
   const uint32_t flags = (uint32_t)h->data[h->numFds + 1];
   if (flags & BANNER_QTI_FLAG_UBWC_PI)
      return false;
   *mod = (flags & BANNER_QTI_FLAG_UBWC) ? BANNER_MOD_QCOM_COMPRESSED : DRM_FORMAT_MOD_LINEAR;
   return true;
}

/* A gralloc handle the sniff can't read (newer QTI grallocs no longer start it with 'gmsm'): print its
 * ints once per process and request, which is what a reader for that handle would be written from --
 * the UBWC request's and the CPU-linear request's side by side show where the layout is kept. */
static void
banner_ahb_dump_handle(const struct banner_native_handle *h, int req)
{
   static bool said[BANNER_AHB_REQ_COUNT];
   if (!h || req < 0 || req >= BANNER_AHB_REQ_COUNT || said[req])
      return;
   said[req] = true;
   char ints[64 * 9 + 1];
   size_t pos = 0;
   ints[0] = '\0';
   for (int i = 0; i < h->numInts && i < 64; i++) {
      int n = snprintf(ints + pos, sizeof(ints) - pos, " %08x", (uint32_t)h->data[h->numFds + i]);
      if (n < 0 || (size_t)n >= sizeof(ints) - pos)
         break;
      pos += (size_t)n;
   }
   mesa_logi("banner-ahb: unreadable gralloc handle from the %s request (%d fds, %d ints); ints:%s",
             banner_ahb_req_name[req], h->numFds, h->numInts, pos ? ints : " none");
}

/* Add one reason to a chain's "why linear" text (NULL text = nobody is collecting). */
static void __attribute__((format(printf, 3, 4)))
banner_ahb_why(char *why, size_t size, const char *fmt, ...)
{
   if (!why || !size)
      return;
   size_t len = strlen(why);
   if (len && len + 2 < size) {
      memcpy(why + len, "; ", 3);
      len += 2;
   }
   if (len + 1 >= size)
      return;
   va_list ap;
   va_start(ap, fmt);
   vsnprintf(why + len, size - len, fmt, ap);
   va_end(ap);
}

/* Is QCOM_COMPRESSED in the chain's modifier list? That list (wsi_configure_native_image) is the
 * compositor's modifiers for this format that the driver can create with this swapchain's usage,
 * flags, format list and compression control, so this one test is both "the compositor imports UBWC
 * here" -- the app's BANNER_WAYLAND_UBWC=0 takes it off the list -- and "this image may be UBWC"
 * (Turnip's ubwc_possible for the usage, compression control not DISABLED). */
static bool
banner_ahb_ubwc_offered(const struct wsi_image_info *info)
{
   for (uint32_t i = 0; i < info->drm_mod_list.drmFormatModifierCount; i++) {
      if (info->drm_mod_list.pDrmFormatModifiers[i] == BANNER_MOD_QCOM_COMPRESSED)
         return true;
   }
   return false;
}

/* Allocate one buffer of the chain's size with request `req` (BANNER_AHB_REQ_*); reports gralloc's
 * stride and the modifier its layout maps to. A handle the sniff can't read is only trusted on the
 * CPU-linear request (gralloc can't have compressed that buffer): for the others it is released,
 * *unknown is set and the reason added to `why`, so the caller goes straight to the CPU request. */
static AHardwareBuffer *
banner_ahb_alloc(const struct wsi_wl_swapchain *chain, int req, uint32_t *stride, uint64_t *mod,
                 bool *unknown, char *why, size_t why_size)
{
   struct banner_ahb_desc d = {
      .width = chain->extent.width, .height = chain->extent.height, .layers = 1,
      .format = chain->banner.ahb_format,
      .usage = BANNER_AHB_USAGE_GPU_SAMPLED_IMAGE | BANNER_AHB_USAGE_GPU_FRAMEBUFFER |
               BANNER_AHB_USAGE_COMPOSER_OVERLAY |
               (req == BANNER_AHB_REQ_UBWC ? BANNER_AHB_USAGE_VENDOR_0 : 0) |
               (req == BANNER_AHB_REQ_CPU ? BANNER_AHB_USAGE_CPU_READ_RARELY : 0),
   };
   if (unknown)
      *unknown = false;
   AHardwareBuffer *ahb = NULL;
   if (banner_nw.allocate(&d, &ahb) != 0 || !ahb) {
      mesa_logw("banner-ahb: AHardwareBuffer_allocate %ux%u (%s request, usage %#llx) failed",
                d.width, d.height, banner_ahb_req_name[req], (unsigned long long)d.usage);
      banner_ahb_why(why, why_size, "gralloc refused the %s request", banner_ahb_req_name[req]);
      return NULL;
   }
   struct banner_ahb_desc got;
   banner_nw.describe(ahb, &got);
   const struct banner_native_handle *h = banner_nw.getNativeHandle(ahb);
   if (!h || h->numFds < 1) {
      banner_ahb_why(why, why_size, "a gralloc handle without a dma-buf");
      banner_nw.release(ahb);
      return NULL;
   }
   uint64_t m = DRM_FORMAT_MOD_LINEAR;
   if (!banner_ahb_sniff_modifier(h, &m)) {
      banner_ahb_dump_handle(h, req);
      if (req != BANNER_AHB_REQ_CPU) {
         banner_ahb_why(why, why_size, "gralloc handle layout unknown (%d fds, %d ints)",
                        h->numFds, h->numInts);
         if (unknown)
            *unknown = true;
         banner_nw.release(ahb);
         return NULL;
      }
      m = DRM_FORMAT_MOD_LINEAR;
   }
   *stride = got.stride;
   *mod = m;
   return ahb;
}

/* Put the explicit-layout create info in place of the modifier list (or append it). */
static void
banner_ahb_set_layout(struct wsi_wl_swapchain *chain, bool on)
{
   struct wsi_image_info *info = &chain->base.image_info;
   VkBaseOutStructure *prev = (VkBaseOutStructure *)&info->create;
   for (VkBaseOutStructure *n = prev->pNext; n; prev = n, n = n->pNext) {
      if (on && n == (VkBaseOutStructure *)&info->drm_mod_list) {
         chain->banner.explicit_info.pNext = n->pNext;
         prev->pNext = (VkBaseOutStructure *)&chain->banner.explicit_info;
         chain->banner.replaced_list = true;
         info->create.tiling = VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT;
         return;
      }
      if (!on && n == (VkBaseOutStructure *)&chain->banner.explicit_info) {
         if (chain->banner.replaced_list) {
            info->drm_mod_list.pNext = n->pNext;
            prev->pNext = (VkBaseOutStructure *)&info->drm_mod_list;
         } else {
            prev->pNext = n->pNext;
         }
         info->create.tiling = chain->banner.saved_tiling;
         chain->banner.replaced_list = false;
         return;
      }
   }
   if (on) {
      chain->banner.explicit_info.pNext = NULL;
      __vk_append_struct(&info->create, &chain->banner.explicit_info);
      chain->banner.replaced_list = false;
      info->create.tiling = VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT;
   }
}

static VkResult banner_ahb_create_mem(const struct wsi_swapchain *chain,
                                      const struct wsi_image_info *info,
                                      struct wsi_image *image);

/* Decide, once per swapchain, whether its images come from gralloc and with which request (the
 * order is at the top of banner_ahb_wsi.py). Each request allocates a probe buffer (kept for the
 * first image) to learn gralloc's stride and layout, and test-creates a VkImage with that explicit
 * layout, so a layout the driver refuses -- or, for UBWC, one gralloc's buffer is too small for --
 * moves on to the next request instead of failing the swapchain. Leaves the chain untouched when
 * anything is missing. One line per swapchain says what it got and, when linear, why. */
static void
banner_ahb_setup_chain(struct wsi_wl_swapchain *chain)
{
   struct wsi_wl_display *display = chain->wsi_wl_surface->display;
   const struct wsi_device *wsi = chain->base.wsi;
   struct wsi_image_info *info = &chain->base.image_info;

   /* Recorded before every early return: banner_ahb_mode_changed compares against it, so a chain
    * that could not use gralloc buffers (a BGRA format, a blit chain, an allocation gralloc
    * refused) is still rebuilt once when the switch moves -- and, crucially, is never rebuilt in a
    * loop because its `mode` stayed false while the compositor's mode is on. */
   chain->banner.mode = false;
   chain->banner.want = banner_ahb_want(display);
   if (!chain->banner.want)
      return;
   if (chain->buffer_type != WSI_WL_BUFFER_NATIVE || chain->base.blit.type != WSI_SWAPCHAIN_NO_BLIT ||
       info->explicit_sync || !wsi->supports_modifiers)
      return;
   chain->banner.ahb_format = banner_ahb_format_for(chain->vk_format);
   if (!chain->banner.ahb_format) {
      mesa_logi("banner-ahb: swapchain format %d has no gralloc equivalent, standard buffers", chain->vk_format);
      return;
   }
   if (!banner_ahb_load())
      return;

   const char *lin = getenv("BANNER_WSI_AHB_LINEAR");
   const bool force_linear = lin && lin[0] == '1';
   const bool storage = (info->create.usage & VK_IMAGE_USAGE_STORAGE_BIT) != 0; /* no UAV writes into UBWC */
   char why[256] = ""; /* why the chain ends up linear, for its line */
   int order[BANNER_AHB_REQ_COUNT], n = 0;
   if (force_linear)
      banner_ahb_why(why, sizeof(why), "BANNER_WSI_AHB_LINEAR=1");
   else if (storage)
      banner_ahb_why(why, sizeof(why), "a storage swapchain");
   else if (!banner_ahb_ubwc_offered(info))
      banner_ahb_why(why, sizeof(why), "qcom_compressed is not among the compositor's modifiers "
                     "for this format and usage");
   else
      order[n++] = BANNER_AHB_REQ_UBWC;
   if (!force_linear && !storage)
      order[n++] = BANNER_AHB_REQ_PLAIN;
   order[n++] = BANNER_AHB_REQ_CPU;

   chain->banner.saved_tiling = info->create.tiling;
   bool unreadable = false; /* gralloc's handles can't be read: only the CPU-linear request is left */
   for (int i = 0; i < n; i++) {
      const int req = order[i];
      if (req != BANNER_AHB_REQ_CPU && unreadable)
         continue;
      uint32_t stride = 0;
      uint64_t mod = DRM_FORMAT_MOD_LINEAR;
      bool unknown = false;
      AHardwareBuffer *ahb = banner_ahb_alloc(chain, req, &stride, &mod, &unknown, why, sizeof(why));
      if (!ahb) {
         unreadable = unreadable || unknown;
         continue;
      }
      if (req == BANNER_AHB_REQ_UBWC && mod != BANNER_MOD_QCOM_COMPRESSED)
         banner_ahb_why(why, sizeof(why), "gralloc answered the UBWC request with a linear buffer");
      chain->banner.layout = (VkSubresourceLayout) {
         .offset = 0,
         .rowPitch = (VkDeviceSize)stride * vk_format_get_blocksize(chain->vk_format),
      };
      chain->banner.explicit_info = (VkImageDrmFormatModifierExplicitCreateInfoEXT) {
         .sType = VK_STRUCTURE_TYPE_IMAGE_DRM_FORMAT_MODIFIER_EXPLICIT_CREATE_INFO_EXT,
         .drmFormatModifier = mod,
         .drmFormatModifierPlaneCount = 1,
         .pPlaneLayouts = &chain->banner.layout,
      };
      banner_ahb_set_layout(chain, true);
      VkImage test = VK_NULL_HANDLE;
      VkResult r = wsi->CreateImage(chain->base.device, &info->create, &chain->base.alloc, &test);
      if (r == VK_SUCCESS && mod == BANNER_MOD_QCOM_COMPRESSED) {
         /* The driver lays UBWC out itself from the modifier and the pitch (metadata first, then the
          * pixels, as gralloc does). gralloc's buffer must hold all of that layout; if it is smaller
          * the two disagree about it (a different alignment), so trust neither. */
         VkMemoryRequirements need;
         wsi->GetImageMemoryRequirements(chain->base.device, test, &need);
         off_t have = lseek(banner_nw.getNativeHandle(ahb)->data[0], 0, SEEK_END);
         if (have <= 0 || (VkDeviceSize)have < need.size) {
            wsi->DestroyImage(chain->base.device, test, &chain->base.alloc);
            banner_ahb_set_layout(chain, false);
            banner_nw.release(ahb);
            banner_ahb_why(why, sizeof(why), "gralloc's UBWC buffer is %lld bytes, the driver's UBWC "
                           "image needs %llu", (long long)have, (unsigned long long)need.size);
            mesa_logw("banner-ahb: gralloc's UBWC buffer (%lld bytes) is smaller than the driver's UBWC "
                      "image (%llu): not using UBWC", (long long)have, (unsigned long long)need.size);
            continue;
         }
      }
      if (r == VK_SUCCESS) {
         wsi->DestroyImage(chain->base.device, test, &chain->base.alloc);
         chain->banner.mode = true;
         chain->banner.req = req;
         chain->banner.stride = stride;
         chain->banner.modifier = mod;
         chain->banner.probe = ahb;
         info->create_mem = banner_ahb_create_mem;
         if (mod == BANNER_MOD_QCOM_COMPRESSED)
            mesa_logi("banner-ahb: %ux%u swapchain (%u images) on gralloc buffers: UBWC (QCOM_COMPRESSED), "
                      "stride %u px", chain->extent.width, chain->extent.height, chain->base.image_count,
                      stride);
         else
            mesa_logi("banner-ahb: %ux%u swapchain (%u images) on gralloc buffers: linear, stride %u px "
                      "(no UBWC: %s)", chain->extent.width, chain->extent.height, chain->base.image_count,
                      stride, why[0] ? why : "gralloc chose linear");
         return;
      }
      banner_ahb_set_layout(chain, false);
      banner_nw.release(ahb);
      banner_ahb_why(why, sizeof(why), "the driver refused gralloc's %s layout (pitch %u px, %d)",
                     mod == BANNER_MOD_QCOM_COMPRESSED ? "UBWC" : "linear", stride, r);
      mesa_logw("banner-ahb: vkCreateImage with gralloc's %s layout (pitch %u px) refused (%d)%s",
                mod == BANNER_MOD_QCOM_COMPRESSED ? "UBWC" : "linear", stride, r,
                i + 1 < n ? ": trying the next request" : ": standard buffers");
   }
   mesa_logw("banner-ahb: %ux%u swapchain on standard buffers: no usable gralloc buffer (%s)",
             chain->extent.width, chain->extent.height, why[0] ? why : "no request left");
}

/* image_info.create_mem for gralloc images: import the buffer's dma-buf as the image's memory
 * and describe it for zwp_linux_dmabuf_v1 like wsi_create_native_image_mem would. */
static VkResult
banner_ahb_create_mem(const struct wsi_swapchain *wsi_chain,
                      const struct wsi_image_info *info,
                      struct wsi_image *image)
{
   struct wsi_wl_swapchain *chain = (struct wsi_wl_swapchain *)wsi_chain;
   struct wsi_wl_image *wl_image = wl_container_of(image, wl_image, base);
   const struct wsi_device *wsi = wsi_chain->wsi;
   VK_FROM_HANDLE(vk_device, device, wsi_chain->device);
   AHardwareBuffer *ahb = chain->banner.probe;
   uint32_t stride = chain->banner.stride;
   uint64_t mod = chain->banner.modifier;

   chain->banner.probe = NULL;
   if (!ahb)
      ahb = banner_ahb_alloc(chain, chain->banner.req, &stride, &mod, NULL, NULL, 0);
   if (!ahb)
      return VK_ERROR_OUT_OF_DEVICE_MEMORY;
   if (stride != chain->banner.stride || mod != chain->banner.modifier) {
      mesa_logw("banner-ahb: gralloc changed its layout between buffers (stride %u -> %u)", chain->banner.stride, stride);
      banner_nw.release(ahb);
      return VK_ERROR_OUT_OF_DEVICE_MEMORY;
   }
   const struct banner_native_handle *h = banner_nw.getNativeHandle(ahb);
   int fd = h->data[0];

   VkMemoryFdPropertiesKHR fd_props = { .sType = VK_STRUCTURE_TYPE_MEMORY_FD_PROPERTIES_KHR };
   VkResult result = device->dispatch_table.GetMemoryFdPropertiesKHR(
      wsi_chain->device, VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT, fd, &fd_props);
   if (result != VK_SUCCESS) {
      mesa_logw("banner-ahb: GetMemoryFdPropertiesKHR on the gralloc dma-buf failed (%d)", result);
      banner_nw.release(ahb);
      return result;
   }
   VkMemoryRequirements reqs;
   wsi->GetImageMemoryRequirements(wsi_chain->device, image->image, &reqs);
   uint32_t type_bits = reqs.memoryTypeBits & fd_props.memoryTypeBits;
   if (!type_bits) {
      mesa_logw("banner-ahb: no memory type can import the gralloc dma-buf");
      banner_nw.release(ahb);
      return VK_ERROR_OUT_OF_DEVICE_MEMORY;
   }
   off_t buf_size = lseek(fd, 0, SEEK_END);
   if (buf_size > 0 && (VkDeviceSize)buf_size < reqs.size) {
      /* UBWC: the chain's probe passed this check for the same request, so gralloc changed its mind;
       * never import a UBWC buffer that can't hold the driver's layout. */
      if (mod == BANNER_MOD_QCOM_COMPRESSED) {
         mesa_logw("banner-ahb: gralloc UBWC buffer is %lld bytes, the image needs %llu: refused",
                   (long long)buf_size, (unsigned long long)reqs.size);
         banner_nw.release(ahb);
         return VK_ERROR_OUT_OF_DEVICE_MEMORY;
      }
      mesa_logw("banner-ahb: gralloc buffer is %lld bytes, the image needs %llu",
                (long long)buf_size, (unsigned long long)reqs.size);
   }

   int import_fd = os_dupfd_cloexec(fd);
   if (import_fd < 0) {
      banner_nw.release(ahb);
      return VK_ERROR_OUT_OF_HOST_MEMORY;
   }
   const VkImportMemoryFdInfoKHR import_info = {
      .sType = VK_STRUCTURE_TYPE_IMPORT_MEMORY_FD_INFO_KHR,
      .handleType = VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT,
      .fd = import_fd,
   };
   const VkMemoryDedicatedAllocateInfo dedicated = {
      .sType = VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO,
      .pNext = &import_info,
      .image = image->image,
   };
   const VkMemoryAllocateInfo alloc_info = {
      .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,
      .pNext = &dedicated,
      .allocationSize = buf_size > 0 ? (VkDeviceSize)buf_size : reqs.size,
      .memoryTypeIndex = wsi_select_memory_type(wsi, 0 /* req */, 0 /* deny */, type_bits),
   };
   result = wsi->AllocateMemory(wsi_chain->device, &alloc_info, &wsi_chain->alloc, &image->memory);
   if (result != VK_SUCCESS) {
      mesa_logw("banner-ahb: importing the gralloc dma-buf failed (%d)", result);
      close(import_fd); /* ownership only moves on success */
      banner_nw.release(ahb);
      return result;
   }

   image->dma_buf_fd = os_dupfd_cloexec(fd);
   image->num_planes = 1;
   image->drm_modifier = mod;
   image->offsets[0] = 0;
   image->row_pitches[0] = (uint32_t)chain->banner.layout.rowPitch;
   image->sizes[0] = image->row_pitches[0] * chain->extent.height;
   wl_image->banner_ahb = ahb;
   wl_image->banner_stride = stride;
   return VK_SUCCESS;
}

/* Hand the compositor the image's AHardwareBuffer (once, right after its wl_buffer exists). */
static void
banner_ahb_attach(struct wsi_wl_swapchain *chain, struct wsi_wl_image *image, struct wl_buffer *buffer)
{
   struct wsi_wl_display *display = chain->wsi_wl_surface->display;
   if (!chain->banner.mode || !image->banner_ahb || !display->banner_ahb)
      return;
   int sv[2];
   if (socketpair(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0, sv) != 0) {
      mesa_logw("banner-ahb: socketpair failed: %s", strerror(errno));
      return;
   }
   /* The handle is in the socket before the request can reach the compositor. */
   if (banner_nw.sendHandle(image->banner_ahb, sv[0]) != 0) {
      mesa_logw("banner-ahb: AHardwareBuffer_sendHandleToUnixSocket failed");
      close(sv[0]);
      close(sv[1]);
      return;
   }
   banner_ahb_v1_attach(display->banner_ahb, buffer, sv[1], chain->extent.width, chain->extent.height,
                        image->banner_stride, (uint32_t)(chain->banner.modifier >> 32),
                        (uint32_t)(chain->banner.modifier & 0xffffffff), chain->base.image_count);
   close(sv[0]);
   close(sv[1]); /* libwayland sends its own duplicate */
   chain->banner.attached++;
}

/* Which free image the implicit acquire hands out on a gralloc chain (see "Acquire order" at the top
 * of banner_ahb_wsi.py): -1 when none is free. dma-buf poll with a zero timeout never blocks; a fence
 * still pending simply reads as "not ready" (also while an earlier poll's callback is armed). */
static int
banner_ahb_pick_free(struct wsi_wl_swapchain *chain)
{
   int own = -1, display = -1;
   for (uint32_t i = 0; i < chain->base.image_count; i++) {
      if (chain->images[i].busy)
         continue;
      const int fd = chain->images[i].base.dma_buf_fd;
      struct pollfd p = {.fd = fd, .events = POLLIN | POLLOUT};
      if (fd < 0 || poll(&p, 1, 0) < 0 || (p.revents & (POLLERR | POLLNVAL)))
         return (int)i; /* can't tell: Mesa's own order */
      if (p.revents & POLLOUT)
         return (int)i; /* every fence signalled */
      if (!(p.revents & POLLIN)) {
         if (own < 0)
            own = (int)i; /* our own rendering into it still runs: the queue is in order anyway */
      } else if (display < 0) {
         display = (int)i; /* only the display's release fence is left */
      }
   }
   if (own >= 0 || display < 0)
      return own;
   if (!chain->banner.held_said) {
      chain->banner.held_said = true;
      mesa_logi("banner-ahb: every free image is still held by the display: the acquire waits for it "
                "(%u images)", chain->base.image_count);
   }
   return display;
}

static void
banner_ahb_image_fini(struct wsi_wl_image *image)
{
   if (image->banner_ahb) {
      banner_nw.release(image->banner_ahb);
      image->banner_ahb = NULL;
   }
}

static void
banner_ahb_chain_fini(struct wsi_wl_swapchain *chain)
{
   if (chain->banner.probe) {
      banner_nw.release(chain->banner.probe);
      chain->banner.probe = NULL;
   }
}
/* ---- end Bannerlator zero-copy layers ------------------------------------------------------- */
'''

patch(os.path.join(wsi, 'wsi_common_wayland.c'), [
    # protocol glue
    ('#include "color-management-v1-client-protocol.h"\n',
     '#include "color-management-v1-client-protocol.h"\n#include "banner-ahb-v1-client-protocol.h"\n', 1),
    # display: the bound global
    ('   struct wp_color_manager_v1 *color_manager;\n',
     '   struct wp_color_manager_v1 *color_manager;\n'
     '   struct banner_ahb_v1 *banner_ahb; /* Bannerlator zero-copy layers, see banner_ahb_setup_chain */\n'
     '   uint32_t banner_ahb_version;      /* 1 = no mode event ever: the environment decides */\n'
     '   bool banner_ahb_have_mode;        /* a mode event has arrived */\n'
     '   bool banner_ahb_mode;             /* the compositor wants gralloc images */\n', 1),
    # image: its gralloc buffer
    ('   struct wp_linux_drm_syncobj_timeline_v1 *wl_syncobj_timeline[WSI_ES_COUNT];\n};\n',
     '   struct wp_linux_drm_syncobj_timeline_v1 *wl_syncobj_timeline[WSI_ES_COUNT];\n'
     '\n'
     '   struct AHardwareBuffer *banner_ahb; /* the gralloc buffer behind the image (BANNER_WSI_AHB) */\n'
     '   uint32_t banner_stride;              /* its row stride in pixels */\n'
     '};\n', 1),
    # chain: the gralloc mode state
    ('   struct wsi_image_timing_request timing_request;\n\n   struct wsi_wl_image images[0];\n',
     '   struct wsi_image_timing_request timing_request;\n'
     '\n'
     '   struct {\n'
     '      bool mode;                  /* images come from gralloc */\n'
     '      bool want;                  /* the mode this chain was built for (banner_ahb_mode_changed) */\n'
     '      bool retire_said;           /* the "rebuilding it" line was logged once */\n'
     '      bool held_said;             /* the "every free image is held" line was logged once */\n'
     '      int req;                    /* BANNER_AHB_REQ_*: the gralloc request its buffers use */\n'
     '      bool replaced_list;         /* explicit_info took drm_mod_list\'s place in create.pNext */\n'
     '      uint32_t ahb_format;\n'
     '      uint32_t stride;            /* pixels, gralloc\'s */\n'
     '      uint64_t modifier;\n'
     '      uint32_t attached;          /* images handed to the compositor */\n'
     '      VkImageTiling saved_tiling;\n'
     '      struct AHardwareBuffer *probe; /* allocated by setup, consumed by the first image */\n'
     '      VkSubresourceLayout layout;\n'
     '      VkImageDrmFormatModifierExplicitCreateInfoEXT explicit_info;\n'
     '   } banner;\n'
     '\n'
     '   struct wsi_wl_image images[0];\n', 1),
    # the helpers, after the handle casts that close the struct section
    ('VK_DEFINE_NONDISP_HANDLE_CASTS(wsi_wl_swapchain, base.base, VkSwapchainKHR,\n'
     '                               VK_OBJECT_TYPE_SWAPCHAIN_KHR)\n',
     'VK_DEFINE_NONDISP_HANDLE_CASTS(wsi_wl_swapchain, base.base, VkSwapchainKHR,\n'
     '                               VK_OBJECT_TYPE_SWAPCHAIN_KHR)\n' + helpers, 1),
    # registry: bind the global
    ('      } else if (strcmp(interface, wp_linux_drm_syncobj_manager_v1_interface.name) == 0) {\n',
     '      } else if (strcmp(interface, banner_ahb_v1_interface.name) == 0) {\n'
     '         /* Binding above the advertised version is a fatal wl_display protocol error, and a\n'
     '          * Bannerlator before the live switch advertises version 1: clamp, never assume 2. */\n'
     '         display->banner_ahb_version = MIN2(version, 2u);\n'
     '         display->banner_ahb =\n'
     '            wl_registry_bind(registry, name, &banner_ahb_v1_interface,\n'
     '                             display->banner_ahb_version);\n'
     '         if (display->banner_ahb_version >= BANNER_AHB_V1_MODE_SINCE_VERSION)\n'
     '            banner_ahb_v1_add_listener(display->banner_ahb, &banner_ahb_listener, display);\n'
     '      } else if (strcmp(interface, wp_linux_drm_syncobj_manager_v1_interface.name) == 0) {\n', 1),
    ('   if (display->color_manager)\n      wp_color_manager_v1_destroy(display->color_manager);\n',
     '   if (display->color_manager)\n      wp_color_manager_v1_destroy(display->color_manager);\n'
     '   if (display->banner_ahb)\n      banner_ahb_v1_destroy(display->banner_ahb);\n', 1),
    # surface formats: only what gralloc can hold, when the mode is on (both query variants)
    ('         if (!(disp_fmt->flags & WSI_WL_FMT_ALPHA) ||\n'
     '            !(disp_fmt->flags & WSI_WL_FMT_OPAQUE)) {\n'
     '            continue;\n'
     '         }\n',
     '         if (!(disp_fmt->flags & WSI_WL_FMT_ALPHA) ||\n'
     '            !(disp_fmt->flags & WSI_WL_FMT_OPAQUE)) {\n'
     '            continue;\n'
     '         }\n'
     '         if (banner_ahb_skip_format(&display, disp_fmt->vk_format))\n'
     '            continue;\n', 2),
    # swapchain creation: room for the extra gralloc images, then add them for MAILBOX / IMMEDIATE
    ('   size_t size = sizeof(*chain) + MAX2(WSI_WL_BUMPED_NUM_IMAGES, pCreateInfo->minImageCount) * sizeof(chain->images[0]);\n',
     '   size_t size = sizeof(*chain) + (MAX2(WSI_WL_BUMPED_NUM_IMAGES, pCreateInfo->minImageCount) +\n'
     '                                   BANNER_AHB_EXTRA_IMAGES_MAX) * sizeof(chain->images[0]);\n', 1),
    ('   VkPresentModeKHR present_mode = wsi_swapchain_get_present_mode(wsi_device, pCreateInfo);\n',
     '   /* Bannerlator zero-copy: a gralloc chain\'s buffers spend longer with the display (see\n'
     '    * banner_ahb_extra_images); the allocation above has room for these. */\n'
     '   num_images += banner_ahb_extra_images(wsi_wl_surface->display, pCreateInfo->presentMode);\n'
     '\n'
     '   VkPresentModeKHR present_mode = wsi_swapchain_get_present_mode(wsi_device, pCreateInfo);\n', 1),
    # swapchain creation: decide before the images are made
    ('   for (uint32_t i = 0; i < chain->base.image_count; i++) {\n'
     '      result = wsi_wl_image_init(chain, &chain->images[i],\n'
     '                                 pCreateInfo, pAllocator);\n',
     '   banner_ahb_setup_chain(chain);\n'
     '\n'
     '   for (uint32_t i = 0; i < chain->base.image_count; i++) {\n'
     '      result = wsi_wl_image_init(chain, &chain->images[i],\n'
     '                                 pCreateInfo, pAllocator);\n', 1),
    # image init: hand the buffer over once its wl_buffer exists
    ('      zwp_linux_buffer_params_v1_destroy(params);\n'
     '      loader_wayland_wrap_buffer(&image->wayland_buffer, buffer);\n',
     '      zwp_linux_buffer_params_v1_destroy(params);\n'
     '      loader_wayland_wrap_buffer(&image->wayland_buffer, buffer);\n'
     '      banner_ahb_attach(chain, image, buffer);\n', 1),
    # implicit acquire: on a gralloc chain, prefer a free image the display is done with
    ('      /* Try to find a free image. */\n'
     '      for (uint32_t i = 0; i < chain->base.image_count; i++) {\n'
     '         if (!chain->images[i].busy) {\n',
     '      /* Zero-copy: prefer a free image the display is done with (banner_ahb_pick_free). */\n'
     '      if (chain->banner.mode) {\n'
     '         int banner_i = banner_ahb_pick_free(chain);\n'
     '         if (banner_i >= 0) {\n'
     '            *image_index = (uint32_t)banner_i;\n'
     '            chain->images[banner_i].busy = true;\n'
     '            loader_wayland_buffer_set_flow(&chain->images[banner_i].wayland_buffer, &flow);\n'
     '            return (chain->suboptimal ? VK_SUBOPTIMAL_KHR : VK_SUCCESS);\n'
     '         }\n'
     '      }\n'
     '      /* Try to find a free image. */\n'
     '      for (uint32_t i = 0; i < chain->base.image_count; i++) {\n'
     '         if (!chain->images[i].busy) {\n', 1),
    # teardown
    ('         loader_wayland_buffer_destroy(&chain->images[i].wayland_buffer);\n'
     '         wsi_destroy_image(&chain->base, &chain->images[i].base);\n',
     '         loader_wayland_buffer_destroy(&chain->images[i].wayland_buffer);\n'
     '         wsi_destroy_image(&chain->base, &chain->images[i].base);\n'
     '         banner_ahb_image_fini(&chain->images[i]);\n', 1),
    ('   vk_free(pAllocator, (void *)chain->drm_modifiers);\n',
     '   banner_ahb_chain_fini(chain);\n   vk_free(pAllocator, (void *)chain->drm_modifiers);\n', 1),
    # the live switch: Mesa's own "this chain is done" answer, for a chain built for the other mode.
    # Three sites, identical text: acquire_next_image_explicit, acquire_next_image_implicit,
    # queue_present. All three already have `chain` in scope.
    ('   if (chain->retired)\n      return VK_ERROR_OUT_OF_DATE_KHR;\n',
     '   if (chain->retired || banner_ahb_mode_changed(chain))\n      return VK_ERROR_OUT_OF_DATE_KHR;\n', 3),
])

print("wsi_common_wayland.c: gralloc-backed swapchain images behind banner_ahb_v1 (mode event / BANNER_WSI_AHB)")
