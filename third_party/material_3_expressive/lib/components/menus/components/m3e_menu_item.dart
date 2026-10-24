import 'package:flutter/widgets.dart';
import 'package:material_3_expressive/components/menus/m3e_menus.dart'
    show M3EMenu;
import 'package:material_3_expressive/material_3_expressive.dart' show M3EMenu;

import '../../../foundations/foundations.dart';
import '../enums/m3e_menu_color_style.dart';
import '../enums/m3e_menu_item_shape.dart';
import '../styles/m3e_menu_theme.dart';
import 'm3e_menu_style_scope.dart';

/// A single interactive row inside an [M3EMenu] popup.
class M3EMenuItem extends StatelessWidget {
  /// M3EMenuItem.
  const M3EMenuItem({
    required this.label,
    required this.onTap,
    this.leading,
    this.trailing,
    this.trailingText,
    this.badge,
    this.supportingText,
    this.enabled = true,
    this.isDestructive = false,
    this.selected = false,
    this.shape = M3EMenuItemShape.standalone,
    this.autofocus = false,
    this.focusNode,
    super.key,
  });

  /// label.

  final String label;

  /// onTap.
  final VoidCallback? onTap;

  /// leading.
  final Widget? leading;

  /// trailing.
  final Widget? trailing;

  /// trailingText.
  final String? trailingText;

  /// badge.
  final Widget? badge;

  /// supportingText.
  final String? supportingText;

  /// enabled.
  final bool enabled;

  /// isDestructive.
  final bool isDestructive;

  /// selected.
  final bool selected;

  /// shape.
  final M3EMenuItemShape shape;

  /// autofocus.
  final bool autofocus;

  /// Focus node owned by the menu key registration, when this row is in a popup.
  final FocusNode? focusNode;

  @override
  Widget build(BuildContext context) {
    final theme = M3ETheme.of(context);
    final menuTheme = theme.menuTheme;
    final scheme = theme.colorScheme;
    final style = M3EMenuStyleScope.styleOf(context);
    final palette =
        M3EMenuStyleScope.colorsOf(context) ?? menuTheme.colors(scheme, style);
    final radius = menuTheme.itemBorderRadius;

    return Padding(
      padding: EdgeInsets.symmetric(horizontal: menuTheme.stateLayerInset),
      child: M3ETappable(
        enabled: enabled,
        onTap: onTap,
        autofocus: autofocus && enabled,
        focusNode: focusNode,
        semanticLabel: label,
        materialInk: true,
        builder: (BuildContext context, M3EInteractionState state) {
          final Color iconForeground = menuTheme.entryIconForegroundColor(
            scheme,
            enabled: enabled,
            isDestructive: isDestructive,
            selected: selected,
            hovered: state.hovered,
            focused: state.focused,
            pressed: state.pressed,
            style: style,
          );
          return M3EFocusRing(
            focused: state.focused,
            radius: radius,
            width: menuTheme.focusIndicatorWidth,
            gap: menuTheme.focusIndicatorOffset,
            color: menuTheme.focusRingColor(scheme),
            child: M3EStateLayerOverlay(
              state: state,
              color: selected ? palette.selectedContent : palette.stateLayer,
              shape: RoundedRectangleBorder(borderRadius: radius),
              child: _body(
                context,
                menuTheme: menuTheme,
                scheme: scheme,
                style: style,
                palette: palette,
                radius: radius,
                iconForeground: iconForeground,
                focused: state.focused,
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _body(
    BuildContext context, {
    required M3EMenuTheme menuTheme,
    required M3EColorScheme scheme,
    required M3EMenuColorStyle style,
    required M3EMenuColors palette,
    required BorderRadius radius,
    required Color iconForeground,
    required bool focused,
  }) {
    final Color background = _entryBackground(
      menuTheme: menuTheme,
      palette: palette,
      focused: focused,
    );
    return Container(
      constraints: BoxConstraints(minHeight: menuTheme.entryHeight),
      padding: EdgeInsets.symmetric(
        horizontal: menuTheme.entryHorizontalPadding,
        vertical: menuTheme.entryVerticalPadding,
      ),
      alignment: Alignment.center,
      decoration: BoxDecoration(color: background, borderRadius: radius),
      child: _entryRow(
        context,
        menuTheme: menuTheme,
        scheme: scheme,
        style: style,
        iconForeground: iconForeground,
      ),
    );
  }

  Color _entryBackground({
    required M3EMenuTheme menuTheme,
    required M3EMenuColors palette,
    required bool focused,
  }) {
    Color background = selected
        ? palette.selectedContainer
        : const Color(0x00000000);
    if (selected && !enabled) {
      background = background.withValues(alpha: menuTheme.disabledOpacity);
    }
    if (focused) {
      final Color layer = selected
          ? palette.selectedContent
          : palette.stateLayer;
      background = Color.alphaBlend(
        layer.withValues(alpha: M3EStateOpacity.focus),
        background,
      );
    }
    return background;
  }

  Widget _labelBlock(
    BuildContext context, {
    required M3EMenuTheme menuTheme,
    required M3EColorScheme scheme,
    required M3EMenuColorStyle style,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisAlignment: MainAxisAlignment.center,
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        Text(
          label,
          style: menuTheme.entryLabelStyle(
            M3ETheme.of(context).typeScale,
            scheme,
            enabled: enabled,
            isDestructive: isDestructive,
            selected: selected,
            style: style,
          ),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        if (supportingText != null)
          Text(
            supportingText!,
            style: menuTheme.supportingTextStyle(
              M3ETheme.of(context).typeScale,
              scheme,
              enabled: enabled,
              selected: selected,
              style: style,
            ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
      ],
    );
  }

  Widget _entryRow(
    BuildContext context, {
    required M3EMenuTheme menuTheme,
    required M3EColorScheme scheme,
    required M3EMenuColorStyle style,
    required Color iconForeground,
  }) {
    return Row(
      children: <Widget>[
        if (leading != null) ...<Widget>[
          IconTheme.merge(
            data: IconThemeData(
              color: iconForeground,
              size: menuTheme.iconSize,
            ),
            child: leading!,
          ),
          SizedBox(width: menuTheme.iconGap),
        ],
        Expanded(
          child: _labelBlock(
            context,
            menuTheme: menuTheme,
            scheme: scheme,
            style: style,
          ),
        ),
        if (badge != null) ...<Widget>[
          SizedBox(width: menuTheme.iconGap),
          badge!,
        ],
        if (trailingText != null) ...<Widget>[
          SizedBox(width: menuTheme.iconGap),
          Text(
            trailingText!,
            style: menuTheme.trailingTextStyle(
              M3ETheme.of(context).typeScale,
              scheme,
              enabled: enabled,
              selected: selected,
              style: style,
            ),
          ),
        ],
        if (trailing != null) ...<Widget>[
          SizedBox(width: menuTheme.iconGap),
          IconTheme.merge(
            data: IconThemeData(
              color: iconForeground,
              size: menuTheme.iconSize,
            ),
            child: trailing!,
          ),
        ],
      ],
    );
  }
}
