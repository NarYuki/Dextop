import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../../icon_buttons/m3e_icon_buttons.dart';
import '../styles/m3e_side_sheet_theme.dart';

/// Back or close icon button of a side sheet.
///
/// Uses the sheet action tokens: on surface variant at rest, primary icon
/// and state layer while hovered (0.08), focused (0.1) or pressed (0.1).
class M3ESideSheetIconAction extends StatefulWidget {
  /// M3ESideSheetIconAction.
  const M3ESideSheetIconAction({
    required this.theme,
    required this.icon,
    required this.label,
    required this.onPressed,
    this.autofocus = false,
    super.key,
  });

  /// Resolved sheet theme.
  final M3ESideSheetTheme theme;

  /// Glyph.
  final Widget icon;

  /// Tooltip and semantics label.
  final String label;

  /// Called on tap, Space or Enter.
  final VoidCallback onPressed;

  /// Whether to take focus when first built.
  final bool autofocus;

  @override
  State<M3ESideSheetIconAction> createState() => _M3ESideSheetIconActionState();
}

class _M3ESideSheetIconActionState extends State<M3ESideSheetIconAction> {
  final WidgetStatesController _states = WidgetStatesController();

  @override
  void dispose() {
    _states.dispose();
    super.dispose();
  }

  // Focus only counts while keyboard rings are allowed, so a pointer tap
  // never leaves a tinted layer behind.
  bool _focused(Set<WidgetState> states) =>
      states.contains(WidgetState.focused) &&
      M3EFocusInteraction.instance.ringsAllowed;

  Color? _overlay(Set<WidgetState> states, Color layer) {
    final M3ESideSheetActionStyle a = widget.theme.action;
    if (states.contains(WidgetState.disabled)) {
      return null;
    }
    if (states.contains(WidgetState.pressed)) {
      return layer.withValues(alpha: a.pressedOpacity);
    }
    if (_focused(states)) {
      return layer.withValues(alpha: a.focusOpacity);
    }
    if (states.contains(WidgetState.hovered)) {
      return layer.withValues(alpha: a.hoverOpacity);
    }
    return const Color(0x00000000);
  }

  @override
  Widget build(BuildContext context) {
    final M3EColorScheme scheme = M3ETheme.of(context).colorScheme;
    final M3ESideSheetActionStyle a = widget.theme.action;
    final Color rest = widget.theme.iconColor(scheme);
    final Color active = a.activeColor ?? scheme.primary;
    final Color layer = a.stateLayerColor ?? scheme.primary;
    return ListenableBuilder(
      listenable: Listenable.merge(<Listenable>[
        _states,
        M3EFocusInteraction.instance,
      ]),
      builder: (BuildContext context, _) {
        final Set<WidgetState> s = _states.value;
        final bool on =
            s.contains(WidgetState.hovered) ||
            s.contains(WidgetState.pressed) ||
            _focused(s);
        return M3EIconButton(
          variant: M3EIconButtonVariant.standard,
          icon: widget.icon,
          onPressed: widget.onPressed,
          tooltip: widget.label,
          semanticLabel: widget.label,
          autofocus: widget.autofocus,
          statesController: _states,
          decoration: M3EIconButtonDecoration(
            foregroundColor: WidgetStatePropertyAll<Color?>(on ? active : rest),
            overlayColor: WidgetStateProperty.resolveWith(
              (Set<WidgetState> states) => _overlay(states, layer),
            ),
          ),
        );
      },
    );
  }
}
