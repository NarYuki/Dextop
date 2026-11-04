"""Apply the source adaptations exercised by the Android GUI probes."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

HERE = Path(__file__).resolve().parent
PATCHES = (
    "patch_gui_network_libraries.py",
    "patch_executable_path.py", "patch_locale.py", "patch_date_components.py",
    "patch_calendar.py", "calendar_enumeration.py", "patch_index_set.py", "patch_unarchiver.py", "patch_eventkit.py",
    "patch_scrollview.py", "patch_string_bounds.py", "patch_window_content.py",
    "patch_window_controller_size.py", "patch_layout_fitting.py",
    "patch_layout_invalidation.py", "patch_layout_parent_coordinates.py", "patch_layout_baselines.py",
    "patch_view_backing_layer.py",
    "patch_controller_appearance.py",
    "patch_constraint_animator.py",
    "patch_toolbar_initialization.py",
    "patch_layer_surface_position.py",
    "patch_segmented_factories.py",
    "patch_textfield_factory.py",
    "patch_grid_view.py",
    "patch_slider_factory.py",
    "patch_slider_metrics.py",
    "patch_container_fitting.py",
    "patch_layer_image_stride.py",
    "patch_cgl_drawable_geometry.py",
    "patch_calibrated_rgb.py",
    "patch_accessibility_chart.py",
    "patch_os_logging.py",
    "patch_number_properties.py",
    "patch_foundation_imports.py",
    "patch_unarchiver_overlay.py",
    "patch_path_overlay.py",
    "patch_gradient_overlay.py",
    "patch_radial_roots.py",
    "patch_gradient_layer.py",
    "patch_timing_function.py",
    "patch_asset_catalog_images.py", "patch_accent_color.py", "patch_popup_menu.py", "patch_transient_parent.py", "patch_menu_focus.py", "patch_status_tray.py", "patch_gui_sdk.py",
)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    args = parser.parse_args()
    lock = json.loads((HERE / "darling.lock.json").read_text())
    revision = subprocess.check_output(["git", "-C", str(args.source), "rev-parse", "HEAD"], text=True).strip()
    if revision != lock["commit"]:
        parser.error("Runtime source must match darling.lock.json")
    for patch in PATCHES:
        subprocess.run([sys.executable, str(HERE / patch), "--source", str(args.source)], check=True)


if __name__ == "__main__":
    main()
