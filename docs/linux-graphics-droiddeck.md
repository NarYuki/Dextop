# DroidDeck graphics reference for Dextop

Inspected on 2026-10-17 at Droid-Deck/DroidDeck commit
[`26ca374c47641fb8db8d1711c188ddb36a4a7877`](https://github.com/Droid-Deck/DroidDeck/tree/26ca374c47641fb8db8d1711c188ddb36a4a7877).
The reference checkout is under ignored `build/droiddeck-reference`.
This records source inspection, not a DroidDeck benchmark on the Xperia.

## Actual display paths

| Mode | Guest | Android presentation |
| --- | --- | --- |
| Steam | `gamescope --backend wayland --expose-wayland`, with its Xwayland servers | Custom Wayland compositor imports dma-buf into Vulkan; GPU composition to Android swapchain. Eligible frames can use AHardwareBuffer/SurfaceControl. |
| Current KDE Desktop | `startplasma-wayland`, with `KWIN_COMPOSE=Q`, `QT_QUICK_BACKEND=software`, `LIBGL_ALWAYS_SOFTWARE=1`, `GALLIUM_DRIVER=llvmpipe` | Nested desktop reaches the custom compositor; GPU applications can run separately through `droiddeck-gpu` and gamescope. |
| Dextop X11 today | Zink/Turnip, `LIBGL_KOPPER_DRI2=true`, `MESA_VK_WSI_DEBUG=sw`, Xvfb | CPU-visible presentation to Xvfb, MIT-SHM GetImage capture, CPU blit to each Android Surface. |
| Dextop native Wayland today | Zink/Turnip or toolkit shm | dma-buf is CPU-mapped and copied into compositor pixels, then CPU-composited into each Android Surface. |

An old progress-log entry describes a GPU labwc desktop. The current launch
script is the authority for the current KDE mode. Desktop software composition
does not imply that games launched from Desktop also render in software.

## Files to reuse as references

All DroidDeck paths below are relative to the pinned repository.

- [`tools/linuxfs/overlay/usr/local/bin/droiddeck-session`](https://github.com/Droid-Deck/DroidDeck/blob/26ca374c47641fb8db8d1711c188ddb36a4a7877/tools/linuxfs/overlay/usr/local/bin/droiddeck-session): Steam gamescope invocation, exposed Wayland, separate Xwayland instances and refresh configuration.
- [`tools/linuxfs/desktop/droiddeck-desktop`](https://github.com/Droid-Deck/DroidDeck/blob/26ca374c47641fb8db8d1711c188ddb36a4a7877/tools/linuxfs/desktop/droiddeck-desktop), [`droiddeck-gpu`](https://github.com/Droid-Deck/DroidDeck/blob/26ca374c47641fb8db8d1711c188ddb36a4a7877/tools/linuxfs/desktop/droiddeck-gpu): software desktop environment versus accelerated application launch.
- [`app/src/main/cpp/waylandcomp/src/vk_loader.c`](https://github.com/Droid-Deck/DroidDeck/blob/26ca374c47641fb8db8d1711c188ddb36a4a7877/app/src/main/cpp/waylandcomp/src/vk_loader.c): Android Turnip loading through adrenotools; system Vulkan is a fallback with no dma-buf import guarantee.
- [`app/src/main/cpp/waylandcomp/src/vk_present.c`](https://github.com/Droid-Deck/DroidDeck/blob/26ca374c47641fb8db8d1711c188ddb36a4a7877/app/src/main/cpp/waylandcomp/src/vk_present.c): `vkp_image_import_dmabuf`, modifier/pitch/offset-aware Vulkan image creation, external-memory FD import, memory-type intersection, GPU presentation.
- [`app/src/main/cpp/waylandcomp/src/compositor.c`](https://github.com/Droid-Deck/DroidDeck/blob/26ca374c47641fb8db8d1711c188ddb36a4a7877/app/src/main/cpp/waylandcomp/src/compositor.c): `take_dmabuf` caches an imported GPU image per buffer; buffer lifetime and release management; `render_scene` selects GPU composition or a layer path.
- [`app/src/main/cpp/waylandcomp/src/ahb_swapchain.c`](https://github.com/Droid-Deck/DroidDeck/blob/26ca374c47641fb8db8d1711c188ddb36a4a7877/app/src/main/cpp/waylandcomp/src/ahb_swapchain.c), [`sc_layer.c`](https://github.com/Droid-Deck/DroidDeck/blob/26ca374c47641fb8db8d1711c188ddb36a4a7877/app/src/main/cpp/waylandcomp/src/sc_layer.c): gralloc buffers, SurfaceControl transactions and release fences.
- [`app/src/main/cpp/waylandcomp/ZERO_COPY_SPIKE.md`](https://github.com/Droid-Deck/DroidDeck/blob/26ca374c47641fb8db8d1711c188ddb36a4a7877/app/src/main/cpp/waylandcomp/ZERO_COPY_SPIKE.md): why arbitrary dma-bufs cannot simply be cast to AHardwareBuffer, and why guest WSI cooperation is needed. Prototype history is not evidence that every frame currently uses zero-copy.

The gamescope WSI layer is also important:
[`CreateGamescopeSurface` in gamescope 3.16.29](https://github.com/ValveSoftware/gamescope/blob/3.16.29/layer/VkLayer_FROG_gamescope_wsi.cpp)
creates a Wayland Vulkan surface for an X11 window, with an XCB fallback.
The DroidDeck runtime library list includes `libVkLayer_FROG_gamescope_wsi_aarch64.so`.
Actual layer activation/bypass must be logged when testing GLX/Zink; replacing
Xvfb with any Xwayland binary alone does not prove accelerated presentation.

## Dextop implementation constraints

1. Implement Android GPU import/presentation before treating native Wayland as
   accelerated end to end. Receiving a dma-buf FD is insufficient if
   `copy_buffer` immediately maps and copies its pixels.
2. Use an Android/bionic Turnip driver for the host renderer. The existing
   `/opt/dextop-mesa` libraries are guest glibc libraries and cannot be loaded
   into the Android service. Capability detection must check dma-buf import,
   DRM format modifiers and synchronization on the selected host driver.
3. Keep one GPU device with per-window presentation state. DroidDeck's presenter
   has global single-output state; Dextop must preserve independent Plasma
   windows, resize, popups, clipping, detach and session shutdown.
4. Retain imported GPU images for the lifetime of their Wayland buffers.
   Honor acquire/release synchronization; do not return `wl_buffer.release`
   while GPU work still reads the buffer. Do not CPU-wait on every frame merely
   to replace a pixel memcpy with a GPU copy.
5. Once the host GPU path works, bring X11/GLX clients onto a verified
   gamescope/Xwayland/WSI path. Leaving glxgears on Xvfb still incurs the same
   CPU transfer even if unrelated Wayland windows use GPU import.
6. First remove CPU readback using dma-buf import and GPU composition. Treat
   AHB/SurfaceControl direct presentation as a separate subsequent step: raw
   dma-bufs are not gralloc handles, and modifiers/planes/fences must agree.
7. Preserve the CPU renderer as an explicit fallback with a logged reason.
   Log the selected path per window rather than calling all Turnip windows
   zero-copy or GPU-presented.

## Verification before claiming a performance fix

- GPU import and pixel correctness on Xperia, then resize, menus, focus,
  close/reopen, Surface replacement and service restart.
- Separate native Wayland, X11/GLX and Chrome measurements. Record the actual
  Vulkan driver, WSI layer, presentation path and fallback reason.
- Match resolution, glxgears window size, synchronization, device, thermal
  state and background load before comparing with DroidDeck's reported 1000 FPS.
- Record application FPS and presented frames separately. Dextop currently
  sleeps 8 ms between X11 capture iterations and uses a 16 ms Wayland frame
  timer; these are output scheduling limits, not proof of a matching upper
  bound on application render FPS.
- Measure CPU time, GPU wait/import/copy/present times and transferred bytes.
  The existing Xperia test gave roughly 99–113 FPS with synchronization
  disabled; it establishes a performance problem, not a complete profile of
  its cause.

DroidDeck is GPL-3.0. Any source actually ported must retain its attribution
and applicable license notices; its dependencies have their own licenses.
No DroidDeck renderer code or binaries have been incorporated by this note.

## Isolated Xperia GPU transport results

The probes in `tool/wayland_smoke` now establish these separate facts:

- System Android Vulkan lacks the dma-buf/modifier extensions needed for this
  import path on this Xperia; Android Turnip loaded in an isolated shell process
  exposes them.
- A DMA-heap image imported by Android Turnip can be GPU-cleared and GPU-blitted
  into an ImageReader-backed Android Vulkan swapchain. All 4096 pixels match.
- A buffer allocated and CPU-written inside the Linux container can be passed
  over SCM_RIGHTS and GPU-presented by the Android receiver without a pixel copy.
- Guest Mesa/Turnip can GPU-clear the shared buffer, release foreign ownership,
  wait for completion and send it to Android Turnip; the resulting Surface again
  has all 4096 expected pixels. Both processes exit normally, and Dextop's main
  process remains running.

These are correctness probes, not the application renderer. They currently
serialize producer and consumer with GPU waits and an acknowledgment, use a
fixed 64x64 linear BGRA layout, and do not exercise Wayland clients' real
swapchains, multiple windows, resizing, asynchronous fences or gamescope.
Production code and the installed APK have not been switched to this path.
No FPS improvement is established by these results.

The opt-in compositor build now connects `gpu_present.c` to actual Wayland
dma-buf commits. GPU images are cached per client buffer, current surfaces and
in-flight jobs retain references, and release sync FDs are watched by the
Wayland event loop. GPU import/presentation failures or subsurfaces fall back
to the CPU path. The default APK build does not enable this integration.

Xperia verification: a Mesa/Zink EGL client submitted 60 frames, the harness
received correctly colored consecutive frames, and client/server exited
normally with no leaked probe socket. WezTerm also rendered its known test
text through GPU dma-buf import at its actual 820x583 size; the captured PNG
was inspected and both processes exited normally. A shutdown-order null
dereference found by the streaming test was fixed. Default native build still
passes. Installed Plasma/DeX sessions and their display/input code were not
changed by this work. Gamescope, GLX acceleration, rollout and comparable FPS
measurements remain outstanding.

Gamescope now starts in a separate Xperia container session using Debian
trixie-backports, an isolated new Turnip ICD, KGSL libdrm identity/PRIME
compatibility and a `/dev/shm` bind. glxgears creates a Gamescope WSI swapchain,
renders visible gears, and shuts down with gamescope and the host probe.
The ImageReader-drained run received 570 further frames and measured roughly
124 application FPS. This is not the target performance: gamescope's child
surfaces are still CPU-composited by the current host compositor. The next
required renderer change is GPU subsurface composition, followed by a matching
benchmark. No installed Dextop display/input path has been switched.

## Default build (2026-10-18)

The normal APK now builds the compositor with `DEXTOP_WAYLAND_GPU`, vendors
adrenotools (BSD-2-Clause, `third_party/adrenotools`) and ships the Android
half of DroidDeck's DD-Turnip v0.2.0 (Mesa 26.3-devel, Adreno 6xx to 8xx) as
`libvulkan_dextop_turnip.so` (see `third_party/README.md`). The Linux service points
the compositor at it; when it is missing or fails, windows keep the CPU path.
On the Xperia (A401SO) WezTerm launched from Kickoff rendered through guest
Turnip/Zink and was GPU-presented by this driver. Gamescope/X11 GPU
presentation and subsurface GPU composition remain open.

## Prebuilt GPU packages (2026-10-18)

Nothing GPU-related is compiled on the device any more. Container setup
installs a prebuilt package (`LinuxCatalog.GPU_PACKAGE`), downloaded and
checked by SHA-256: currently DD-Turnip v0.2.0, whose Linux driver goes to
`/opt/dextop-mesa/lib` with an ICD manifest at the old path, while the
distribution's Zink runs OpenGL on it. This covers Adreno 8xx (the Galaxy's
Adreno 840), which Mesa 25.0.7 did not know. A package may later add a `mesa`
directory (Mesa GL/EGL built for KGSL, laid out like `/opt/dextop-mesa`); only
then is the Wayland GPU path (`wayland-ready`) enabled, so new containers run
Wayland-capable applications through X11 until such a package exists.

Debian 13's Mesa 25.0.7 Zink renders correctly (verified by readback) but
never presents to Xvfb, which has no DRI3: every GLX window stayed black on
both phones, with either Turnip. Debian containers therefore install Mesa 26
from trixie-backports (prebuilt) in the GPU step. With it, glxgears on the
Galaxy (Adreno 840, DD-Turnip, Zink, `MESA_VK_WSI_DEBUG=sw`,
`LIBGL_KOPPER_DRI2=true`) is visible at about 1660 FPS.
