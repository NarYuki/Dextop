# Wayland compositor device probes

These probes run in a separate `app_process` and use the dedicated abstract
socket `dextop-wayland-client-probe`. They do not stop Dextop or its Linux apps.

`WaylandSmoke.java` loads `WaylandCompositor` from the current build, starts and
stops it three times, checks that its sockets disappear, then attaches an Android
`ImageReader` Surface to a client window. It checks that the first pixel is red.
It also checks that repeated stop and input after stop do not use the old handle.

Client probes, run inside a Linux container:

- `shm-client.c`: xdg-shell window and a red `wl_shm` buffer.
- `dmabuf-client.c`: same window, but a real dma-buf allocated from
  `/dev/dma_heap/system` and sent through `zwp_linux_dmabuf_v1`. The CPU writes
  the color; this does not prove GPU rendering.
- `egl-client.c`: same window, but an EGL/OpenGL ES clear and swap. Require a
  hardware `GL_RENDERER` and `WAYLAND_SURFACE_PIXELS_OK` to prove GPU rendering.

Build the Java harness against `android.jar`, then use `d8` with the current
`WaylandCompositor*.class` files and Kotlin standard library. Push its dex and
`libdextop_wayland.so` to `/data/local/tmp/dextop-wayland-smoke/`, then launch:

```sh
CLASSPATH=/data/local/tmp/dextop-wayland-smoke/probe.dex app_process /system/bin WaylandSmoke
```

After `CLIENT_PROBE_READY`, start one client. Generate the xdg-shell and
linux-dmabuf client headers and private protocol C files with `wayland-scanner`
from the XML in `third_party/wayland-protocols/generated/`. Compile the client
with those generated C files and `-lwayland-client`. The EGL client additionally
links `-lwayland-egl -lEGL -lGLESv2`. Development headers must be installed.

For the EGL probe, select the freshly built Mesa libraries and ICD explicitly;
do not let distribution Mesa or llvmpipe satisfy the test. The KGSL integration
uses `DEXTOP_WAYLAND_KGSL=1`, `MESA_LOADER_DRIVER_OVERRIDE=zink`,
`GALLIUM_DRIVER=zink`, `EGL_PLATFORM=wayland`, `VK_ICD_FILENAMES`,
`LD_LIBRARY_PATH`, and `__EGL_VENDOR_LIBRARY_FILENAMES`. Unset
`LIBGL_ALWAYS_SOFTWARE` and `MESA_VK_WSI_DEBUG`.

The standalone probes cover the compositor protocol, buffer and Surface paths.
They do not cover Plasma window management or application compatibility; those
still need verification in Dextop itself.

## Android Vulkan dma-buf import

`vulkan-import-probe.c` enumerates the selected Android driver's external-memory,
modifier, synchronization and swapchain extensions. The optional
`vulkan-dmabuf-roundtrip.c` allocates a real DMA-heap buffer, imports its FD as a
linear BGRA Vulkan image, clears it red on the GPU, transfers ownership to
foreign access, waits for completion and checks all 4096 pixels using
`DMA_BUF_IOCTL_SYNC`. CPU readback is for test verification only. This does not
prove window presentation, cross-process guest buffers or higher application FPS.

The plain capability probe needs the NDK compiler, `-lvulkan` and
`-DVK_USE_PLATFORM_ANDROID_KHR`. The custom-driver probe uses `gpu/CMakeLists.txt`
with an external adrenotools checkout. Do not load custom drivers into Dextop's
main process; run this executable in a separate shell process.

Example CMake configuration (replace absolute SDK and repository paths):

```sh
cmake -S tool/wayland_smoke/gpu -B build/wayland-smoke/gpu -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE=<ndk>/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 \
  -DANDROID_STL=c++_shared \
  -DDEXTOP_ADRENOTOOLS_DIR=<repo>/build/droiddeck-reference/app/src/main/cpp/adrenotools
cmake --build build/wayland-smoke/gpu
```

Push the executable, hook libraries and NDK `libc++_shared.so` into the probe
directory. Push an Android/bionic Turnip driver into a separate driver directory.
Keep trailing slashes on the hook and driver paths:

```sh
LD_LIBRARY_PATH=<hooks> <probe> <driver-dir>/ libvulkan_freedreno.so <hooks>/ --dmabuf
```

The same process runs three device/import/clear/destroy rounds. Require three
`DMABUF_GPU_IMPORT_CLEAR_OK` lines and exit status zero. A capability listing
alone is insufficient. Without arguments, the executable probes system Vulkan.

Inspected adrenotools source: DroidDeck commit
`26ca374c47641fb8db8d1711c188ddb36a4a7877`, BSD-2-Clause with its dependency notices.
Tested Android driver: Droid-Deck/Drivers-CI `DD-Turnip-v0.2.0.zip`, release
`DD-Turnip-v0.2.0`; SHA-256
`fc16658953062cfaabda8a5663c498667d78bb4c9c74c819ff580753270a147d`.
The archive and its notices remain in the ignored build directory, not the APK.

`VulkanSurfaceSmoke.java` plus `vulkan-surface-jni.c` and
`vulkan-dmabuf-surface.c` extend this to an Android ImageReader Surface. The
imported image is GPU-blitted into an Android Vulkan swapchain, with semaphore
synchronization for acquire/render/present. The Java harness checks every pixel
of the resulting Surface image. This uses a separate `app_process` and does not
attach to any Plasma/DeX Surface. Require both
`DMABUF_GPU_SURFACE_PRESENT_OK` and `DMABUF_GPU_SURFACE_PIXELS_OK`.
The probe deliberately waits for GPU idle during teardown; it is a correctness
test, not a production renderer or FPS benchmark. Cross-process guest buffer
ownership, multiple windows, resize and asynchronous buffer release still need
implementation and verification.

`VulkanSurfaceSmoke --guest` instead waits on the dedicated abstract socket
`dextop-gpu-import-guest-probe`. Compile and run `guest-dmabuf-send.c` inside
the container after `GUEST_DMABUF_READY`. It allocates and CPU-writes a linear
64x64 BGRA DMA-heap buffer, sends its FD using SCM_RIGHTS, and retains it until
the Android receiver acknowledges GPU completion. The receiver imports and
GPU-blits this buffer without overwriting it; the ImageReader checks every
displayed pixel. Require `GUEST_DMABUF_SEND_GPU_ACK_OK`,
`DMABUF_GPU_SURFACE_PIXELS_OK 4096` and zero exit codes on both processes.
This verifies cross-process transport and host GPU presentation, but not a
guest GPU producer, Wayland protocol integration or implicit GPU synchronization.

For a guest GPU producer, push `guest-gpu-send.c`, `guest-dmabuf-send.c`,
`vulkan-dmabuf-roundtrip.c` and `guest-gpu-send.sh` into
`/tmp/dextop-gpu-probe` in the container, then run `guest-gpu-send.sh` after the
same host harness prints `GUEST_DMABUF_READY`. This builds against the guest
Vulkan loader and explicitly selects `/opt/dextop-mesa`'s Turnip ICD. Require
`GUEST_GPU=Turnip ...`, `GUEST_GPU_SUBMITTED_WITH_SYNC_FD`,
`GUEST_ACQUIRE_FENCE_CONFIRMED_PENDING`,
`GUEST_DMABUF_SEND_GPU_ACK_OK` and the host's pixel success line. The guest
clears the shared image with Vulkan and releases foreign ownership. It exports
a sync FD without a CPU fence wait and sends that alongside the buffer FD.
The Android receiver temporarily imports it into a Vulkan semaphore and waits
on the GPU before its blit; require `GUEST_ACQUIRE_SYNC_FD_GPU_WAIT_SUBMITTED`.
The receiver never clears the source image. A test-only host-set event delays
the producer's GPU by 500 ms, and zero-timeout poll confirms that its exported
fence is still pending before send.
The producer retains the allocation until the receiver acknowledges GPU
completion. Consumer completion still uses a queue-idle wait and socket ACK;
this does not yet verify asynchronous release fences or performance. It uses a fixed linear
64x64 BGRA layout, not arbitrary Wayland buffer metadata.

`VulkanSurfaceSmoke --presenter` runs the Linux-specific backend in
`android/app/src/main/cpp/wayland/gpu_present.c`. It imports the guest image
once, keeps one GPU device and two independent Android swapchains, presents
two frames per Surface, and polls exported release sync FDs before reusing
the source. It destroys the first window before using the second. Surface
sizes are 64x64 and 96x80. Require four `LINUX_GPU_CACHED_IMAGE_FRAME_OK`
lines, `LINUX_GPU_FIRST_WINDOW_DETACHED`, and pixel success counts 4096 and 7680.

The backend's normal present path polls a frame fence without waiting, uses
zero-timeout swapchain acquire, imports the producer sync FD into a GPU-waited
semaphore and exports a release sync FD. Device, imported images and window
state persist across frames. Blocking device-idle waits are restricted to
shutdown, Surface teardown and error recovery. The harness waits on release
FDs to verify correctness; it is not a throughput benchmark.

The backend is connected to the compositor only in the opt-in build below;
the normal APK build does not enable it. It handles
a single opaque linear RGB surface per window, with cropping/scaling. A caller
must retain images until release fences signal, handle retry, recreate windows
on Surface changes and provide a separate route for blending subsurfaces.
These requirements need application-level verification before rollout.

## Opt-in compositor GPU integration

The `gpu` CMake project also builds `compositor/libdextop_wayland.so` with
`DEXTOP_WAYLAND_GPU`. Use Android API 30 or later for this target, matching the
compositor's `memfd_create` dependency. The normal APK build leaves this flag
unset and continues to use its existing CPU path.

The isolated GPU compositor requires these environment variables in its
`app_process`, in addition to the hooks' `LD_LIBRARY_PATH`:

```sh
DEXTOP_LINUX_GPU_DRIVER=<android-turnip-dir>/
DEXTOP_LINUX_GPU_LIBRARY=libvulkan_freedreno.so
DEXTOP_LINUX_GPU_HOOKS=<hook-dir>/
```

With these set, top-level dma-bufs without subsurfaces use the persistent GPU
presenter. The compositor exports implicit producer synchronization from the
dma-buf, queues a GPU wait, and retains references to both the current surface
buffer and pending presentation jobs. Jobs register their release sync FD with
the Wayland event loop (with timer collection as a fallback); buffers
are returned to the client only after their last GPU use and surface reference
end. The client may destroy its wl_buffer earlier without freeing in-flight GPU
images. Subsurfaces and unavailable GPU presentation use CPU fallback.

`egl-client.c` accepts an optional frame count. Run `WaylandSmoke --stream`
against this library and an EGL client with 60 frames. The harness drains its
ImageReader and requires at least ten correctly colored streamed frames.
Require `WAYLAND_EGL_FRAMES=60`, GPU presentation logs, normal client/server
exit and socket cleanup. This tests the Mesa/Zink Wayland swapchain through
the compositor, not just a separate FD transport protocol. It is still not an
FPS comparison or an installed application rollout.

`WaylandWeztermSmoke.java` is the corresponding application probe. It uses the
separate `wayland-probe` socket, allocates its ImageReader at the mapped
window's actual dimensions, captures a PNG and checks for nonuniform pixels.
Run WezTerm with a command printing known text; inspect the saved PNG as well
as requiring hardware renderer and GPU presentation logs and normal exits.
Its example socket namespace/container ID is Xperia-specific.

## Gamescope/Xwayland probe

`gamescope-debian-prepare.sh` adds the official Debian trixie-backports
main/contrib source, updates metadata and simulates installation. On the Xperia
it selected gamescope `3.16.22+ds-1~bpo13+1` and Xwayland `2:24.1.6-1` with
no package upgrades/removals. Installation was then done explicitly with
`apt-get install --no-install-recommends gamescope/trixie-backports xwayland`.
Debian puts gamescope in `/usr/games`; the normal service PATH omits it.

`run-gamescope-android.sh` runs a separate PRoot session, binds the installed
APK's socket shim and a dedicated directory onto `/dev/shm`, and invokes
`gamescope-guest-probe.sh`. The rootfs/container ID is Xperia-specific. The
shared-memory bind is needed for wlroots' keymap and dma-buf format table.
Existing containers/desktop sessions are not stopped or reconfigured.

The probe uses the Linux half of the previously verified DD-Turnip v0.2.0
archive under `/opt/dextop-gamescope-probe/driver` with an explicit ICD file.
It does not replace `/opt/dextop-mesa` or set a global driver. The older guest
driver lacked `VK_EXT_physical_device_drm`, required by gamescope. The new
driver reports the actual KGSL device ID as its render/primary node, which
stock libdrm cannot identify as a DRM node.

`kgsl-drm-probe.c` provides the KGSL identity lookup for this isolated preload.
`build-kgsl-drm-probe.sh` additionally links the unchanged
`tools/linuxfs/preload/drm.c` from DroidDeck commit
`26ca374c47641fb8db8d1711c188ddb36a4a7877`, copied to
`/tmp/dextop-dd-drm.c`. This is GPL-3.0 source from DroidDeck, not an APK
dependency. It provides dma-buf/GEM handle bookkeeping for KGSL; without it,
wlroots rejected the Gamescope WSI client's buffers. Retain DroidDeck's license
and attribution if this source is later incorporated into a shipped runtime.

The opt-in compositor now announces seat v8, pointer-constraint/relative-pointer
objects and presentation-time for gamescope initialization. Pointer constraints
are deliberately never activated, so Linux cannot capture the host pointer;
relative-motion event delivery is not implemented yet. Presentation feedback
is discarded after commit rather than inventing SurfaceFlinger display times.
These are compatibility prerequisites, not proof of full gaming-input or
presentation-timing support.

Run `WaylandGamescopeSmoke` with the same GPU environment as the other probes.
It captures a nonuniform frame to `gamescope.png` and continuously drains its
ImageReader, avoiding artificial producer stalls while collecting application
FPS. Require a hardware renderer, successful Gamescope WSI swapchain creation,
visible gears in the PNG, normal server/client exits and no probe socket left.
The guest probe limits glxgears to ten seconds so gamescope exits normally.

Current result: visible gears, hardware Zink/Turnip and WSI bypass, with 570
additional Surface frames received, but approximately 124 application FPS in
the drained test. An earlier non-draining capture showed approximately 275 FPS;
that is not a controlled throughput comparison. Gamescope's child surfaces
still take the compositor's CPU path. This probe therefore establishes GLX
compatibility, not the requested performance improvement or GPU presentation
through the entire chain. GPU composition of subsurfaces remains required.
