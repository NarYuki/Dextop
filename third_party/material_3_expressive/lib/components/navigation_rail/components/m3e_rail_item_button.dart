import 'dart:ui' show SemanticsRole;

import 'package:material_ui/material_ui.dart';

import '../../../foundations/foundations.dart';
import '../enums/m3e_navigation_rail_enums.dart';
import '../styles/m3e_navigation_rail_theme.dart';
import 'm3e_nav_icon_scale.dart';
import 'm3e_nav_selection_indicator.dart';
import 'm3e_rail_badge_view.dart';

/// One rail destination. The hit target is the full rail width. Collapsed
/// pills hug the icon. Expanded pills hug the icon and label, inset from
/// the rail edge.
class M3ERailItemButton extends StatelessWidget {
  /// Creates a [M3ERailItemButton].
  const M3ERailItemButton({
    super.key,
    required this.icon,
    this.selectedIcon,
    required this.isSelected,
    required this.onPressed,
    required this.expanded,
    required this.labelBehavior,
    required this.label,
    this.semanticLabel,
    this.suppressInk = false,
    this.badgeCount,
    this.heightOverride,
    this.short = false,
    this.focusNode,
    this.skipTraversal = false,
    this.haptic = M3EHapticFeedback.none,
  });

  /// Icon to display.
  final Widget icon;

  /// Optional icon to display when [isSelected] is true.
  final Widget? selectedIcon;

  /// Whether this destination is currently selected.
  final bool isSelected;

  /// Callback when the button is tapped.
  final VoidCallback onPressed;

  /// Whether the rail is in expanded layout.
  final bool expanded;

  /// Controls when the text label is visible.
  final M3ENavigationRailLabelBehavior labelBehavior;

  /// Text label for the destination.
  final String label;

  /// Semantic label used for accessibility.
  final String? semanticLabel;

  /// If true, suppresses the splash while the rail is changing width.
  final bool suppressInk;

  /// Optional numeric badge value to show.
  final int? badgeCount;

  /// Optional min height for the tap target.
  final double? heightOverride;

  /// Uses the short, unlabeled item height.
  final bool short;

  /// Focus node owned by the rail.
  final FocusNode? focusNode;

  /// When true, Tab skips this destination.
  final bool skipTraversal;

  /// Haptic intensity on tap.
  final M3EHapticFeedback haptic;

  bool _showLabel(bool selected) {
    return switch (labelBehavior) {
      M3ENavigationRailLabelBehavior.alwaysShow => true,
      M3ENavigationRailLabelBehavior.onlySelected => selected,
      M3ENavigationRailLabelBehavior.alwaysHide => false,
    };
  }

  @override
  Widget build(BuildContext context) {
    final M3ENavigationRailTheme theme = M3ETheme.of(context)
        .navigationRailTheme;
    final M3EThemeData m3e = M3ETheme.of(context);
    final bool showLabel = _showLabel(isSelected) && label.isNotEmpty;
    final bool unlabeled = short || !showLabel;
    final double height =
        heightOverride ??
        (expanded
            ? theme.itemExpandedHeight
            : (unlabeled ? theme.shortItemHeight : theme.itemCollapsedHeight));
    return Semantics(
      role: SemanticsRole.menuItem,
      selected: isSelected,
      label: semanticLabel ?? label,
      onTap: onPressed,
      child: M3ETappable(
        focusNode: focusNode,
        skipTraversal: skipTraversal,
        focusOverlay: false,
        materialInk: true,
        semanticButton: false,
        excludeSemantics: true,
        haptic: haptic,
        onTap: onPressed,
        builder: (BuildContext context, M3EInteractionState state) {
          return Builder(
            builder: (BuildContext inkContext) {
              final M3ETappableInkScope? ink = M3ETappableInkScope.maybeOf(
                inkContext,
              );
              return InkWell(
                onTap: ink?.onTap,
                onLongPress: ink?.onLongPress,
                onHover: ink?.onHover,
                mouseCursor: ink?.mouseCursor ?? SystemMouseCursors.click,
                canRequestFocus: false,
                splashFactory: NoSplash.splashFactory,
                splashColor: const Color(0x00000000),
                highlightColor: const Color(0x00000000),
                overlayColor: const WidgetStatePropertyAll<Color>(
                  Color(0x00000000),
                ),
                child: ConstrainedBox(
                  constraints: BoxConstraints(minHeight: height),
                  child: expanded
                      ? _expanded(inkContext, state, theme, m3e, showLabel)
                      : _collapsed(
                          inkContext,
                          state,
                          theme,
                          m3e,
                          showLabel,
                          unlabeled,
                        ),
                ),
              );
            },
          );
        },
      ),
    );
  }

  Widget _collapsed(
    BuildContext context,
    M3EInteractionState state,
    M3ENavigationRailTheme theme,
    M3EThemeData m3e,
    bool showLabel,
    bool unlabeled,
  ) {
    final double pillWidth = unlabeled
        ? theme.noLabelIndicatorSize
        : theme.verticalIndicatorWidth;
    final double pillHeight = unlabeled
        ? theme.noLabelIndicatorSize
        : theme.verticalIndicatorHeight;
    final double radius = unlabeled
        ? theme.noLabelIndicatorSize / 2
        : theme.verticalIndicatorRadius;
    final bool narrow =
        (theme.collapsedWidth - theme.narrowCollapsedWidth).abs() < 0.5;
    final double inset = narrow
        ? theme.narrowHorizontalPadding
        : theme.collapsedHorizontalPadding;
    return Padding(
      padding: EdgeInsets.symmetric(
        horizontal: inset,
        vertical: theme.itemVerticalPadding,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        mainAxisAlignment: MainAxisAlignment.center,
        children: <Widget>[
          SizedBox(
            width: pillWidth,
            height: pillHeight,
            child: _pillStack(
              context,
              state,
              theme,
              m3e,
              radius: radius,
              icon: _icon(m3e, theme, collapsedBadge: true),
            ),
          ),
          if (showLabel) ...<Widget>[
            SizedBox(height: theme.verticalIconLabelGap),
            _label(theme),
          ],
        ],
      ),
    );
  }

  Widget _expanded(
    BuildContext context,
    M3EInteractionState state,
    M3ENavigationRailTheme theme,
    M3EThemeData m3e,
    bool showLabel,
  ) {
    final bool fill = theme.indicatorFillsWidth;
    final Widget pill = _pillStack(
      context,
      state,
      theme,
      m3e,
      radius: theme.expandedIndicatorRadius,
      alignment: AlignmentDirectional.centerStart,
      icon: ConstrainedBox(
        constraints: BoxConstraints(minHeight: theme.expandedIndicatorHeight),
        child: Padding(
          padding: EdgeInsetsDirectional.only(
            start: theme.indicatorLeading,
            end: theme.indicatorTrailing,
          ),
          child: Row(
            mainAxisSize: fill ? MainAxisSize.max : MainAxisSize.min,
            children: <Widget>[
              _icon(m3e, theme, collapsedBadge: false),
              if (showLabel) ...<Widget>[
                SizedBox(width: theme.iconLabelGap),
                Flexible(child: _label(theme)),
              ],
              if (badgeCount != null) ...<Widget>[
                SizedBox(width: theme.iconLabelGap),
                M3ERailBadge.standalone(count: badgeCount),
              ],
            ],
          ),
        ),
      ),
    );
    return Padding(
      padding: EdgeInsetsDirectional.symmetric(
        horizontal: theme.expandedItemInset,
      ),
      child: Align(
        alignment: AlignmentDirectional.centerStart,
        child: fill ? SizedBox(width: double.infinity, child: pill) : pill,
      ),
    );
  }

  Widget _pillStack(
    BuildContext context,
    M3EInteractionState state,
    M3ENavigationRailTheme theme,
    M3EThemeData m3e, {
    required double radius,
    required Widget icon,
    AlignmentGeometry alignment = Alignment.center,
  }) {
    final bool interacting = state.hovered || state.focused || state.pressed;
    final Color? overlay = _layerOpacity(state, theme) > 0
        ? theme
              .stateLayerColorResolved(m3e.colorScheme)
              .withValues(alpha: _layerOpacity(state, theme))
        : null;
    return Stack(
      alignment: alignment,
      children: <Widget>[
        Positioned.fill(
          child: M3ESelectionIndicator(
            selected: isSelected,
            scaleSpring: theme.indicatorScaleSpring,
            fadeSpring: theme.indicatorFadeSpring,
            child: _pillFill(theme, m3e, radius, isSelected ? overlay : null),
          ),
        ),
        if (!isSelected && interacting)
          Positioned.fill(child: _pillFill(theme, m3e, radius, overlay)),
        if (state.focused) _ring(context, theme, radius),
        icon,
      ],
    );
  }

  Widget _pillFill(
    M3ENavigationRailTheme theme,
    M3EThemeData m3e,
    double radius,
    Color? overlay,
  ) {
    final border = BorderRadius.circular(radius);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: theme.activeIndicatorColorResolved(m3e.colorScheme),
        borderRadius: border,
      ),
      child: ClipRRect(
        borderRadius: border,
        child: overlay == null
            ? const SizedBox.expand()
            : ColoredBox(color: overlay, child: const SizedBox.expand()),
      ),
    );
  }

  double _layerOpacity(
    M3EInteractionState state,
    M3ENavigationRailTheme theme,
  ) {
    if (state.pressed) {
      return theme.pressedOpacity;
    }
    if (state.focused) {
      return theme.focusOpacity;
    }
    if (state.hovered) {
      return theme.hoverOpacity;
    }
    return 0;
  }

  Widget _ring(
    BuildContext context,
    M3ENavigationRailTheme theme,
    double radius,
  ) {
    return Positioned.fill(
      child: IgnorePointer(
        child: Padding(
          padding: EdgeInsets.all(theme.focusRingInset),
          child: DecoratedBox(
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(radius),
              border: Border.all(
                color: theme.focusRingColorResolved(
                  M3ETheme.of(context).colorScheme,
                ),
                width: theme.focusRingThickness,
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _icon(
    M3EThemeData m3e,
    M3ENavigationRailTheme theme, {
    required bool collapsedBadge,
  }) {
    final bool selected = isSelected;
    final Color color = selected
        ? theme.activeIconColor(m3e.colorScheme)
        : theme.inactiveIconAndLabelColor(m3e.colorScheme);
    final Widget glyph = M3ENavIconScale(
      selected: selected,
      child: IconTheme.merge(
        data: IconThemeData(
          color: color,
          size: theme.iconSize,
          weight: selected && selectedIcon == null ? 600 : null,
        ),
        child: selected && selectedIcon != null ? selectedIcon! : icon,
      ),
    );
    if (!collapsedBadge || badgeCount == null) {
      return glyph;
    }
    return M3ERailBadge(count: badgeCount, child: glyph);
  }

  Widget _label(M3ENavigationRailTheme theme) {
    return Builder(
      builder: (BuildContext context) {
        final double scale = MediaQuery.textScalerOf(context)
            .scale(theme.labelFontSize);
        final double unit = scale / theme.labelFontSize;
        final bool truncate = unit > theme.truncationTextScale;
        final bool wrap = unit > 1 && !truncate;
        return Text(
          label,
          maxLines: truncate
              ? 1
              : wrap
              ? theme.scaledLabelMaxLines
              : 1,
          overflow: truncate ? TextOverflow.ellipsis : TextOverflow.visible,
          textAlign: expanded ? TextAlign.start : TextAlign.center,
          style: theme.labelStyle(
            M3ETheme.of(context).colorScheme,
            selected: isSelected,
          ),
        );
      },
    );
  }
}
