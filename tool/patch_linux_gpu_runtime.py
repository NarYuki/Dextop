"""Enable KGSL's Vulkan/Kopper Wayland path without requiring a DRM render node."""
from pathlib import Path
import sys

path = Path(sys.argv[1]) / 'src/egl/drivers/dri2/platform_wayland.c'
text = path.read_text()
changes = {
    'dri2_setup_device(disp, disp->Options.ForceSoftware)':
        'dri2_setup_device(disp, disp->Options.ForceSoftware || (disp->Options.Zink && getenv("DEXTOP_WAYLAND_KGSL")))',
    'if (disp->Options.ForceSoftware)\n      return dri2_initialize_wayland_swrast(disp);':
        'if (disp->Options.ForceSoftware || (disp->Options.Zink && getenv("DEXTOP_WAYLAND_KGSL")))\n      return dri2_initialize_wayland_swrast(disp);',
}
for before, after in changes.items():
    if after in text:
        continue
    if text.count(before) != 1:
        raise RuntimeError(f'Unexpected Mesa source: {before}')
    text = text.replace(before, after)
path.write_text(text)

# Zink has no legacy drisw display target: flush_frontbuffer only presents
# Vulkan swapchains. Read back the GPU attachment for the X11 loader instead.
path = Path(sys.argv[1]) / 'src/gallium/frontends/dri/drisw.c'
text = path.read_text()
before = '   screen->base.screen->flush_frontbuffer(screen->base.screen, pipe, ptex, 0, 0, drawable, nrects, sub_box);'
after = """   if (!strncmp(screen->base.screen->get_name(screen->base.screen), "zink ", 5)) {
      struct pipe_transfer *transfer = NULL;
      void *map = pipe_texture_map(pipe, ptex, 0, 0, PIPE_MAP_READ,
                                  0, 0, ptex->width0, ptex->height0, &transfer);
      if (map) {
         drisw_put_image2(drawable, map, 0, 0, ptex->width0, ptex->height0,
                          transfer->stride);
         pipe_texture_unmap(pipe, transfer);
      }
      return;
   }

""" + before
if after not in text:
    if text.count(before) != 1:
        raise RuntimeError('Unexpected Mesa drisw presentation source')
    text = text.replace(before, after)
path.write_text(text)
