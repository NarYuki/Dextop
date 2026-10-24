import 'dart:ui' show SemanticsRole;

import 'package:material_ui/material_ui.dart';

import '../../../foundations/foundations.dart';
import '../../navigation_rail/components/m3e_nav_icon_scale.dart';
import '../../navigation_rail/components/m3e_nav_selection_indicator.dart';
import '../models/m3e_navigation_destination.dart';
import '../styles/m3e_navigation_drawer_theme.dart';

/// Single destination row in a navigation drawer.
///
/// The selection fill scales in place. Hover, focus, and press paint the
/// same pill. Press also uses [InkSparkle]. The focus ring is inset and
/// hides as soon as any pointer interaction starts.
class M3EDrawerDestinationButton extends StatefulWidget {
  /// M3EDrawerDestinationButton.
  const M3EDrawerDestinationButton({
    required this.destination,
    required this.selected,
    required this.onTap,
    this.focusNode,
    this.indent = 0,
    this.haptic = M3EHapticFeedback.none,
    super.key,
  });

  /// destination.
  final M3ENavigationDestination destination;

  /// selected.
  final bool selected;

  /// onTap.
  final VoidCallback onTap;

  /// Focus node owned by the drawer so arrows can move between rows.
  final FocusNode? focusNode;

  /// Extra start inset for a nested section row.
  final double indent;

  /// Haptic intensity on tap. Defaults to [M3EHapticFeedback.none].
  final M3EHapticFeedback haptic;

  @override
  State<M3EDrawerDestinationButton> createState() =>
      _M3EDrawerDestinationButtonState();
}

class _M3EDrawerDestinationButtonState
    extends State<M3EDrawerDestinationButton> {
  FocusNode? _owned;
  bool _focused = false;
  bool _hovered = false;
  bool _pressed = false;

  FocusNode get _node => widget.focusNode ?? _owned!;

  @override
  void initState() {
    super.initState();
    if (widget.focusNode == null) {
      _owned = FocusNode();
    }
    M3EFocusInteraction.instance.addListener(_onFocusInteractionChanged);
  }

  @override
  void dispose() {
    M3EFocusInteraction.instance.removeListener(_onFocusInteractionChanged);
    _owned?.dispose();
    super.dispose();
  }

  void _onFocusInteractionChanged() {
    _syncFocusHighlight();
  }

  void _dismissRingForPointer() {
    M3EFocusInteraction.instance.notePointerInteraction(immediate: true);
    _syncFocusHighlight();
  }

  void _handleFocusHighlight(bool value) {
    if (!value) {
      if (_focused && mounted) {
        setState(() => _focused = false);
      }
      return;
    }
    _syncFocusHighlight();
  }

  void _syncFocusHighlight() {
    if (!mounted) {
      return;
    }
    final show = M3EFocusRing.shouldShow(_node, context);
    if (_focused == show) {
      return;
    }
    setState(() => _focused = show);
    if (show) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) {
          return;
        }
        M3EFocusInteraction.ensureVisibleIfKeyboard(context);
      });
    }
  }

  void _select({bool fromPointer = false}) {
    if (fromPointer) {
      _dismissRingForPointer();
      _node.requestFocus();
      _dismissRingForPointer();
    }
    M3EHaptics.trigger(widget.haptic);
    widget.onTap();
  }

  void _setHovered(bool value) {
    if (_hovered == value || !mounted) {
      return;
    }
    setState(() => _hovered = value);
  }

  void _setPressed(bool value) {
    if (_pressed == value || !mounted) {
      return;
    }
    setState(() => _pressed = value);
  }

  @override
  Widget build(BuildContext context) {
    final theme = M3ETheme.of(context);
    final drawerTheme = theme.navigationDrawerTheme;
    final scheme = theme.colorScheme;
    final destination = widget.destination;
    final selected = widget.selected;
    final foreground = drawerTheme.destinationForegroundColor(
      scheme,
      selected: selected,
    );
    final border = drawerTheme.destinationShape();
    final radius = border is RoundedRectangleBorder
        ? border.borderRadius.resolve(Directionality.of(context))
        : BorderRadius.circular(drawerTheme.indicatorRadius);
    final fill = drawerTheme.destinationBackgroundColor(scheme, selected: true);
    final layer = drawerTheme.stateLayerColor(
      scheme,
      selected: selected,
      pressed: _pressed,
      focused: false,
    );
    final glyph = selected
        ? (destination.selectedIcon ?? destination.icon)
        : destination.icon;

    final row = Padding(
      padding: EdgeInsets.symmetric(
        horizontal: drawerTheme.destinationHorizontalPadding,
        vertical: drawerTheme.destinationVerticalPadding,
      ),
      child: _buildFocusable(
        theme: theme,
        drawerTheme: drawerTheme,
        scheme: scheme,
        destination: destination,
        selected: selected,
        foreground: foreground,
        border: border,
        radius: radius,
        fill: fill,
        layer: layer,
        glyph: glyph,
      ),
    );

    final announced = destination.semanticLabel ?? destination.label;
    return Semantics(
      role: SemanticsRole.tab,
      selected: selected,
      label: announced,
      onTap: () => _select(),
      child: ExcludeSemantics(child: row),
    );
  }

  Widget _buildFocusable({
    required M3EThemeData theme,
    required M3ENavigationDrawerTheme drawerTheme,
    required M3EColorScheme scheme,
    required M3ENavigationDestination destination,
    required bool selected,
    required Color foreground,
    required ShapeBorder border,
    required BorderRadius radius,
    required Color fill,
    required Color layer,
    required Widget? glyph,
  }) {
    return FocusableActionDetector(
      focusNode: _node,
      mouseCursor: SystemMouseCursors.click,
      onShowFocusHighlight: _handleFocusHighlight,
      actions: <Type, Action<Intent>>{
        ActivateIntent: CallbackAction<ActivateIntent>(
          onInvoke: (ActivateIntent intent) {
            _select();
            return null;
          },
        ),
        ButtonActivateIntent: CallbackAction<ButtonActivateIntent>(
          onInvoke: (ButtonActivateIntent intent) {
            _select();
            return null;
          },
        ),
      },
      child: Listener(
        behavior: HitTestBehavior.translucent,
        onPointerDown: (_) {
          _dismissRingForPointer();
          _setPressed(true);
        },
        onPointerUp: (_) => _setPressed(false),
        onPointerCancel: (_) => _setPressed(false),
        child: SizedBox(
          height: drawerTheme.destinationHeight,
          width: double.infinity,
          child: Stack(
            alignment: AlignmentDirectional.centerStart,
            children: <Widget>[
              Positioned.fill(
                child: M3ESelectionIndicator(
                  selected: selected,
                  scaleSpring: drawerTheme.indicatorScaleSpring,
                  fadeSpring: drawerTheme.indicatorFadeSpring,
                  child: DecoratedBox(
                    decoration: ShapeDecoration(shape: border, color: fill),
                  ),
                ),
              ),
              Positioned.fill(
                child: _buildInkContent(
                  theme: theme,
                  drawerTheme: drawerTheme,
                  scheme: scheme,
                  destination: destination,
                  selected: selected,
                  foreground: foreground,
                  radius: radius,
                  layer: layer,
                  glyph: glyph,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildInkContent({
    required M3EThemeData theme,
    required M3ENavigationDrawerTheme drawerTheme,
    required M3EColorScheme scheme,
    required M3ENavigationDestination destination,
    required bool selected,
    required Color foreground,
    required BorderRadius radius,
    required Color layer,
    required Widget? glyph,
  }) {
    final bool interacting = _pressed || _hovered;
    return Material(
      type: MaterialType.transparency,
      color: Colors.transparent,
      shape: RoundedRectangleBorder(borderRadius: radius),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => _select(fromPointer: true),
        onHover: (bool value) {
          _setHovered(value);
          _dismissRingForPointer();
        },
        canRequestFocus: false,
        customBorder: RoundedRectangleBorder(borderRadius: radius),
        mouseCursor: SystemMouseCursors.click,
        splashFactory: InkSparkle.splashFactory,
        splashColor: drawerTheme.stateLayerColor(
          scheme,
          selected: selected,
          pressed: true,
          focused: false,
        ),
        highlightColor: Colors.transparent,
        overlayColor: const WidgetStatePropertyAll<Color>(Colors.transparent),
        child: Stack(
          alignment: AlignmentDirectional.centerStart,
          children: <Widget>[
            if (interacting)
              Positioned.fill(
                child: DecoratedBox(
                  decoration: BoxDecoration(color: layer, borderRadius: radius),
                ),
              ),
            _buildContentRow(
              drawerTheme: drawerTheme,
              theme: theme,
              scheme: scheme,
              destination: destination,
              foreground: foreground,
              glyph: glyph,
              selected: selected,
            ),
            Positioned.fill(
              child: _buildFocusRingOverlay(
                drawerTheme: drawerTheme,
                scheme: scheme,
                selected: selected,
                radius: radius,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildContentRow({
    required M3ENavigationDrawerTheme drawerTheme,
    required M3EThemeData theme,
    required M3EColorScheme scheme,
    required M3ENavigationDestination destination,
    required Color foreground,
    required Widget? glyph,
    required bool selected,
  }) {
    return Padding(
      padding: EdgeInsetsDirectional.only(
        start: drawerTheme.destinationInnerHorizontalPadding + widget.indent,
        end: drawerTheme.destinationInnerHorizontalPadding,
      ),
      child: Row(
        children: <Widget>[
          if (glyph != null) ...<Widget>[
            _glyph(drawerTheme, foreground, scheme, glyph, selected: selected),
            SizedBox(width: drawerTheme.iconLabelGap),
          ],
          Expanded(
            child: Text(
              destination.label,
              style: theme.typeScale.labelLarge.copyWith(
                color: foreground,
                fontWeight: selected
                    ? drawerTheme.activeLabelWeight
                    : drawerTheme.inactiveLabelWeight,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          if (destination.badgeLabel != null)
            Text(
              destination.badgeLabel!,
              style: theme.typeScale.labelLarge.copyWith(
                color: drawerTheme.badgeColor(scheme, selected: selected),
                fontWeight: drawerTheme.inactiveLabelWeight,
              ),
            ),
          if (glyph == null &&
              destination.showBadge &&
              destination.badgeLabel == null)
            _dot(drawerTheme.badgeColor(scheme, selected: selected)),
        ],
      ),
    );
  }

  Widget _buildFocusRingOverlay({
    required M3ENavigationDrawerTheme drawerTheme,
    required M3EColorScheme scheme,
    required bool selected,
    required BorderRadius radius,
  }) {
    return ListenableBuilder(
      listenable: M3EFocusInteraction.instance,
      builder: (BuildContext context, Widget? child) {
        final show = _focused && M3EFocusRing.shouldShow(_node, context);
        if (!show) {
          return const SizedBox.shrink();
        }
        return IgnorePointer(
          child: Stack(
            fit: StackFit.expand,
            children: <Widget>[
              DecoratedBox(
                decoration: BoxDecoration(
                  color: drawerTheme.stateLayerColor(
                    scheme,
                    selected: selected,
                    pressed: false,
                    focused: true,
                  ),
                  borderRadius: radius,
                ),
              ),
              Padding(
                padding: EdgeInsets.all(drawerTheme.focusRingInset),
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    borderRadius: radius,
                    border: Border.all(
                      color: drawerTheme.focusRingColorResolved(scheme),
                      width: drawerTheme.focusRingThickness,
                    ),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _glyph(
    M3ENavigationDrawerTheme drawerTheme,
    Color foreground,
    M3EColorScheme scheme,
    Widget glyph, {
    required bool selected,
  }) {
    final destination = widget.destination;
    Widget icon = IconTheme.merge(
      data: IconThemeData(color: foreground, size: drawerTheme.iconSize),
      child: glyph,
    );
    if (destination.showBadge && destination.badgeLabel == null) {
      icon = Stack(
        clipBehavior: Clip.none,
        children: <Widget>[
          icon,
          PositionedDirectional(
            top: -1,
            end: -1,
            child: _dot(drawerTheme.badgeColor(scheme, selected: selected)),
          ),
        ],
      );
    }
    return M3ENavIconScale(selected: selected, child: icon);
  }

  Widget _dot(Color color) {
    return SizedBox(
      width: 6,
      height: 6,
      child: DecoratedBox(
        decoration: BoxDecoration(color: color, shape: BoxShape.circle),
      ),
    );
  }
}
