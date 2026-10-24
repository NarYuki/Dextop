import 'package:material_3_expressive/components/toolbars/m3e_toolbars.dart'
    show M3EToolbar;
import 'package:material_3_expressive/components/toolbars/models/m3e_toolbar_item.dart'
    show M3EToolbarAction;
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3EToolbar;

/// Whether the toolbar floats over content or docks to an edge.
enum M3EToolbarPlacement {
  /// Floating pill over content.
  floating,

  /// Docked to a screen edge.
  docked,
}

/// Standard vs vibrant container color mapping.
enum M3EToolbarColorStyle {
  /// Surface container toolbar. Unselected content is on-surface-variant.
  ///
  /// Selected toggles use secondary container. The adjacent FAB uses
  /// secondary container as well.
  standard,

  /// Primary container toolbar. Unselected content is on-primary-container.
  ///
  /// Selected toggles use surface container. The adjacent FAB uses tertiary
  /// container.
  vibrant,
}

/// How docked actions use leftover width at the medium window size and up.
///
/// Below 600dp the bar always uses [even].
enum M3EToolbarContentAlignment {
  /// Equal gaps, preferring 32 and shrinking toward 4 when the bar is tight.
  even,

  /// Actions clustered in the center with the theme centered gap.
  centered,

  /// Leading and trailing groups with a flexible middle.
  edges,
}

/// Edge used for docked placement and single-edge safe-area padding.
///
/// Floating toolbars with [M3EToolbar.safeArea] apply this edge as an
/// **external** inset only (outside the pill).
enum M3EToolbarDockEdge {
  /// Top edge.
  top,

  /// Bottom edge.
  bottom,
}

/// Position of an adjacent FAB relative to a floating toolbar.
enum M3EToolbarFabPosition {
  /// FAB before the toolbar on the horizontal axis.
  start,

  /// FAB after the toolbar on the horizontal axis.
  end,

  /// FAB above the toolbar.
  top,

  /// FAB below the toolbar.
  bottom,
}

/// What scrolling does to a toolbar with a scroll behavior attached.
enum M3EToolbarScrollAction {
  /// Slides the whole pill away, then back, via [M3EToolbarExitDirection].
  hide,

  /// Collapses the pill to its adjacent FAB / expand-trigger action instead
  /// of sliding away, then reverses on the opposite scroll direction.
  ///
  /// Requires the toolbar to have an adjacent FAB (with
  /// [M3EToolbar.fabExpandsToolbar] true) or an
  /// [M3EToolbarAction.isExpandTrigger] action.
  collapse,
}

/// Direction a toolbar slides when exiting via scroll / visibility controller.
enum M3EToolbarExitDirection {
  /// Exit by moving upwards.
  top,

  /// Exit by moving downwards.
  bottom,

  /// Exit toward the start (left in LTR).
  start,

  /// Exit toward the end (right in LTR).
  end,
}

/// Legacy size enum — maps to icon-button density, not container height.
enum M3EToolbarSize {
  /// Small icon-button density.
  small,

  /// Medium icon-button density.
  medium,

  /// Large icon-button density.
  large,
}

/// Legacy density enum.
enum M3EToolbarDensity {
  /// Default density.
  regular,

  /// Compact density.
  compact,
}

/// Legacy shape family — floating forces pill; docked forces square.
enum M3EToolbarShapeFamily {
  /// Round / pill family.
  round,

  /// Square family.
  square,
}

/// Legacy color emphasis — prefer [M3EToolbarColorStyle].
enum M3EToolbarVariant {
  /// Surface emphasis.
  surface,

  /// Tonal emphasis.
  tonal,

  /// Primary emphasis.
  primary,
}
