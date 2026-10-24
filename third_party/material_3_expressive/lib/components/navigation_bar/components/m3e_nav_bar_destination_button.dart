import 'dart:ui' show SemanticsRole;

import 'package:material_ui/material_ui.dart';

import '../../../foundations/foundations.dart';
import '../../navigation_rail/components/m3e_nav_icon_scale.dart';
import '../../navigation_rail/components/m3e_nav_selection_indicator.dart';
import '../enums/m3e_nav_bar_enums.dart';
import '../models/m3e_navigation_bar_destination.dart';
import '../styles/m3e_navigation_bar_theme.dart';

/// Single destination cell inside the M3E navigation bar.
///
/// The active pill springs on the horizontal axis. Keyboard focus draws an
/// inset ring. Space or Enter selects.
class M3ENavBarDestinationButton extends StatelessWidget {
  /// M3ENavBarDestinationButton.
  const M3ENavBarDestinationButton({
    required this.destination,
    required this.selected,
    required this.selectedColor,
    required this.activeLabelColor,
    required this.unselectedColor,
    required this.labelStyle,
    required this.iconSize,
    required this.labelBehavior,
    required this.iconBehavior,
    required this.layout,
    required this.indicatorStyle,
    required this.indicatorWidth,
    required this.indicatorHeight,
    required this.indicatorRadius,
    required this.contentPadding,
    required this.iconLabelGap,
    required this.labelMaxLines,
    required this.labelOverflow,
    required this.underlineThickness,
    required this.underlineColor,
    required this.indicatorColor,
    required this.onTap,
    this.wideDestinationWidth,
    this.horizontalInset = 0,
    this.focusNode,
    this.skipTraversal = false,
    this.haptic = M3EHapticFeedback.none,
    super.key,
  });

  /// destination.
  final M3ENavigationBarDestination destination;

  /// selected.
  final bool selected;

  /// Active icon color.
  final Color selectedColor;

  /// Active label color.
  final Color activeLabelColor;

  /// Inactive icon and label color.
  final Color unselectedColor;

  /// labelStyle.
  final TextStyle labelStyle;

  /// iconSize.
  final double iconSize;

  /// labelBehavior.
  final M3ENavBarLabelBehavior labelBehavior;

  /// iconBehavior.
  final M3ENavBarIconBehavior iconBehavior;

  /// layout.
  final M3ENavBarLayout layout;

  /// indicatorStyle.
  final M3ENavBarIndicatorStyle indicatorStyle;

  /// indicatorWidth.
  final double indicatorWidth;

  /// indicatorHeight.
  final double indicatorHeight;

  /// Pill corner radius.
  final double indicatorRadius;

  /// Padding inside the destination, above and below the content.
  final EdgeInsets contentPadding;

  /// Gap between the icon and the label.
  final double iconLabelGap;

  /// labelMaxLines.
  final int labelMaxLines;

  /// labelOverflow.
  final TextOverflow labelOverflow;

  /// Chip width in horizontal layout.
  final double? wideDestinationWidth;

  /// Leading and trailing inset inside a horizontal pill.
  final double horizontalInset;

  /// underlineThickness.
  final double underlineThickness;

  /// underlineColor.
  final Color underlineColor;

  /// indicatorColor.
  final Color indicatorColor;

  /// onTap.
  final VoidCallback onTap;

  /// Focus node owned by the bar.
  final FocusNode? focusNode;

  /// When true, Tab skips this destination.
  final bool skipTraversal;

  /// Haptic intensity on tap. Defaults to [M3EHapticFeedback.none].
  final M3EHapticFeedback haptic;

  bool get _showLabel {
    if (!destination.hasLabel) {
      return false;
    }
    return switch (labelBehavior) {
      M3ENavBarLabelBehavior.alwaysShow => true,
      M3ENavBarLabelBehavior.onlySelected => selected,
      M3ENavBarLabelBehavior.alwaysHide => false,
    };
  }

  bool get _showIcon {
    if (!destination.hasIcon) {
      return false;
    }
    return switch (iconBehavior) {
      M3ENavBarIconBehavior.alwaysShow => true,
      M3ENavBarIconBehavior.onlySelected => selected,
      M3ENavBarIconBehavior.alwaysHide => false,
    };
  }

  bool get _pill => indicatorStyle == M3ENavBarIndicatorStyle.pill;

  bool get _underlined =>
      indicatorStyle == M3ENavBarIndicatorStyle.underline && selected;

  bool get _wide => layout == M3ENavBarLayout.wide;

  @override
  Widget build(BuildContext context) {
    final M3ENavigationBarTheme theme = M3ETheme.of(context).navigationBarTheme;
    return Semantics(
      role: SemanticsRole.tab,
      selected: selected,
      label: destination.resolvedSemanticLabel,
      onTap: onTap,
      child: M3ETappable(
        focusNode: focusNode,
        skipTraversal: skipTraversal,
        focusOverlay: false,
        materialInk: true,
        semanticButton: false,
        excludeSemantics: true,
        haptic: haptic,
        onTap: onTap,
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
                child: Stack(
                  fit: StackFit.expand,
                  children: <Widget>[
                    _body(inkContext, state, theme),
                    if (state.focused) _ring(inkContext, theme),
                  ],
                ),
              );
            },
          );
        },
      ),
    );
  }

  Widget _body(
    BuildContext context,
    M3EInteractionState state,
    M3ENavigationBarTheme theme,
  ) {
    Widget child = Padding(
      padding: contentPadding,
      child: _wide
          ? _wideContent(context, state, theme)
          : _verticalContent(context, state, theme),
    );
    if (_underlined) {
      child = DecoratedBox(
        decoration: BoxDecoration(
          border: Border(
            bottom: BorderSide(
              color: underlineColor,
              width: underlineThickness,
            ),
          ),
        ),
        child: child,
      );
    }
    return child;
  }

  Widget _verticalContent(
    BuildContext context,
    M3EInteractionState state,
    M3ENavigationBarTheme theme,
  ) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: <Widget>[
        if (_showIcon)
          SizedBox(
            width: indicatorWidth,
            height: indicatorHeight,
            child: _indicatorStack(
              context,
              state,
              theme,
              icon: _icon(selected ? selectedColor : unselectedColor),
            ),
          ),
        if (_showLabel) ...<Widget>[
          if (_showIcon) SizedBox(height: iconLabelGap),
          _label(selected ? activeLabelColor : unselectedColor, theme),
        ],
      ],
    );
  }

  Widget _wideContent(
    BuildContext context,
    M3EInteractionState state,
    M3ENavigationBarTheme theme,
  ) {
    final Color iconColor = selected ? selectedColor : unselectedColor;
    final Color labelColor = selected ? activeLabelColor : unselectedColor;
    return Center(
      child: SizedBox(
        width: wideDestinationWidth,
        height: indicatorHeight,
        child: _indicatorStack(
          context,
          state,
          theme,
          icon: Padding(
            padding: EdgeInsets.symmetric(horizontal: horizontalInset),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: <Widget>[
                if (_showIcon) _icon(iconColor),
                if (_showIcon && _showLabel) SizedBox(width: iconLabelGap),
                if (_showLabel) Flexible(child: _label(labelColor, theme)),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _indicatorStack(
    BuildContext context,
    M3EInteractionState state,
    M3ENavigationBarTheme theme, {
    required Widget icon,
  }) {
    final bool reduced = _pill && !selected && _interacting(state);
    final Color? overlay = _layerOpacity(state, theme) > 0
        ? theme
              .stateLayerColor(M3ETheme.of(context).colorScheme)
              .withValues(alpha: _layerOpacity(state, theme))
        : null;
    return Stack(
      alignment: Alignment.center,
      children: <Widget>[
        if (_pill)
          Positioned.fill(
            child: M3ESelectionIndicator(
              selected: selected,
              scaleSpring: theme.indicatorScaleSpring,
              fadeSpring: theme.indicatorFadeSpring,
              child: _pillFill(selected ? overlay : null),
            ),
          ),
        if (reduced) Positioned.fill(child: _pillFill(overlay)),
        icon,
      ],
    );
  }

  /// Indicator color clipped to the pill, with the state fill inside that clip.
  Widget _pillFill(Color? overlay) {
    final radius = BorderRadius.circular(indicatorRadius);
    return DecoratedBox(
      decoration: BoxDecoration(color: indicatorColor, borderRadius: radius),
      child: ClipRRect(
        borderRadius: radius,
        child: overlay == null
            ? const SizedBox.expand()
            : ColoredBox(color: overlay, child: const SizedBox.expand()),
      ),
    );
  }

  bool _interacting(M3EInteractionState state) {
    return state.hovered || state.focused || state.pressed;
  }

  double _layerOpacity(M3EInteractionState state, M3ENavigationBarTheme theme) {
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

  Widget _ring(BuildContext context, M3ENavigationBarTheme theme) {
    return Positioned.fill(
      child: IgnorePointer(
        child: Padding(
          padding: EdgeInsets.all(theme.focusRingInset),
          child: DecoratedBox(
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(indicatorRadius),
              border: Border.all(
                color: theme.focusRingColor(M3ETheme.of(context).colorScheme),
                width: theme.focusRingThickness,
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _icon(Color color) {
    return M3ENavIconScale(
      selected: selected,
      child: IconTheme.merge(
        data: IconThemeData(color: color, size: iconSize),
        child: destination.buildIcon(selected: selected),
      ),
    );
  }

  Widget _label(Color color, M3ENavigationBarTheme theme) {
    return Text(
      destination.label!,
      maxLines: labelMaxLines,
      overflow: labelOverflow,
      textAlign: TextAlign.center,
      style: labelStyle.copyWith(
        color: color,
        fontWeight: selected
            ? theme.activeLabelWeight
            : theme.inactiveLabelWeight,
      ),
    );
  }
}
