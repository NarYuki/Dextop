import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../enums/m3e_menu_color_style.dart';
import '../models/m3e_menu_node.dart';
import '../styles/m3e_menu_theme.dart';
import '../utils/m3e_menu_overlay_rect.dart';
import 'm3e_menu_content.dart';
import 'm3e_menu_item.dart';
import 'm3e_menu_key_registration.dart';
import 'm3e_menu_style_scope.dart';

/// Builds leaf [M3EMenuNode] widgets for [M3EMenuContent].
abstract final class M3EMenuNodeBuilders {
  const M3EMenuNodeBuilders._();

  /// entry.
  static Widget entry(
    M3EMenuEntry entry, {
    required bool autofocus,
    required M3EMenuSelectCallback onSelect,
  }) {
    return M3EMenuKeyRegistration(
      label: entry.label,
      enabled: entry.enabled,
      builder: (FocusNode node) {
        return M3EMenuItem(
          label: entry.label,
          leading: entry.leading,
          trailing: entry.trailing,
          trailingText: entry.trailingText,
          badge: entry.badge,
          supportingText: entry.supportingText,
          enabled: entry.enabled,
          isDestructive: entry.isDestructive,
          shape: entry.shape,
          autofocus: autofocus,
          focusNode: node,
          onTap: entry.enabled
              ? () {
                  entry.onPressed?.call();
                  onSelect(entry.value);
                }
              : null,
        );
      },
    );
  }

  /// selectable.
  static Widget selectable(
    M3EMenuSelectable item, {
    required bool selected,
    required bool autofocus,
    required M3EMenuTheme menuTheme,
    required M3EMenuSelectCallback onSelect,
  }) {
    return M3EMenuKeyRegistration(
      label: item.label,
      enabled: item.enabled,
      builder: (FocusNode node) {
        return M3EMenuItem(
          label: item.label,
          leading:
              item.leading ??
              (selected
                  ? Icon(M3EIcons.check_rounded, size: menuTheme.iconSize * 0.9)
                  : null),
          trailing: item.trailing,
          trailingText: item.trailingText,
          badge: item.badge,
          supportingText: item.supportingText,
          enabled: item.enabled,
          selected: selected,
          shape: item.shape,
          autofocus: autofocus,
          focusNode: node,
          onTap: item.enabled
              ? () {
                  item.onPressed?.call();
                  onSelect(item.value);
                }
              : null,
        );
      },
    );
  }

  /// toggleable.
  static Widget toggleable(
    M3EMenuToggleable item, {
    required bool autofocus,
    required M3EMenuTheme menuTheme,
    required M3EMenuSelectCallback onSelect,
  }) {
    return M3EMenuKeyRegistration(
      label: item.label,
      enabled: item.enabled,
      builder: (FocusNode node) {
        return M3EMenuItem(
          label: item.label,
          leading:
              item.leading ??
              Icon(
                item.checked
                    ? M3EIcons.check_box_rounded
                    : M3EIcons.check_box_outline_blank_rounded,
                size: menuTheme.iconSize,
              ),
          trailing: item.trailing,
          trailingText: item.trailingText,
          badge: item.badge,
          supportingText: item.supportingText,
          enabled: item.enabled,
          selected: item.checked,
          shape: item.shape,
          autofocus: autofocus,
          focusNode: node,
          onTap: item.enabled
              ? () {
                  final next = !item.checked;
                  item.onChanged?.call(next);
                  onSelect(next);
                }
              : null,
        );
      },
    );
  }

  /// submenu.
  static Widget submenu(
    M3EMenuSubmenu item, {
    required bool autofocus,
    required M3EMenuTheme menuTheme,
    required M3EMenuOpenSubmenuCallback? onOpenSubmenu,
  }) {
    return Builder(
      builder: (BuildContext itemContext) {
        void open() {
          final Rect? rect = m3eOverlayRectFor(itemContext);
          if (rect == null || onOpenSubmenu == null) {
            return;
          }
          onOpenSubmenu(rect, item.children);
        }

        return M3EMenuKeyRegistration(
          label: item.label,
          enabled: item.enabled,
          onOpenSubmenu: item.enabled ? open : null,
          builder: (FocusNode node) {
            return M3EMenuItem(
              label: item.label,
              leading: item.leading,
              badge: item.badge,
              trailing: Icon(
                M3EIcons.arrow_right_rounded,
                size: menuTheme.iconSize,
              ),
              enabled: item.enabled,
              shape: item.shape,
              autofocus: autofocus,
              focusNode: node,
              onTap: item.enabled && onOpenSubmenu != null ? open : null,
            );
          },
        );
      },
    );
  }

  /// customWidget.
  static Widget customWidget(
    BuildContext context,
    M3EMenuWidget item, {
    required bool autofocus,
    required M3EMenuTheme menuTheme,
    required M3EMenuSelectCallback onSelect,
  }) {
    final scheme = M3ETheme.of(context).colorScheme;
    final style = M3EMenuStyleScope.styleOf(context);
    final palette =
        M3EMenuStyleScope.colorsOf(context) ?? menuTheme.colors(scheme, style);
    final radius = menuTheme.itemBorderRadius;
    return M3EMenuKeyRegistration(
      label: item.semanticLabel ?? 'menu item',
      enabled: item.enabled,
      builder: (FocusNode node) {
        return Padding(
          padding: EdgeInsets.symmetric(horizontal: menuTheme.stateLayerInset),
          child: M3ETappable(
            enabled: item.enabled,
            autofocus: autofocus,
            focusNode: node,
            semanticLabel: item.semanticLabel,
            materialInk: true,
            onTap: item.enabled
                ? () {
                    item.onPressed?.call();
                    onSelect(item.value);
                  }
                : null,
            builder: (BuildContext context, M3EInteractionState state) {
              return M3EFocusRing(
                focused: state.focused,
                radius: radius,
                width: menuTheme.focusIndicatorWidth,
                gap: menuTheme.focusIndicatorOffset,
                color: menuTheme.focusRingColor(scheme),
                child: M3EStateLayerOverlay(
                  state: state,
                  color: item.selected
                      ? palette.selectedContent
                      : palette.stateLayer,
                  shape: RoundedRectangleBorder(borderRadius: radius),
                  child: _customWidgetBody(
                    context,
                    item,
                    menuTheme: menuTheme,
                    scheme: scheme,
                    style: style,
                    palette: palette,
                    radius: radius,
                    state: state,
                  ),
                ),
              );
            },
          ),
        );
      },
    );
  }

  static Widget _customWidgetBody(
    BuildContext context,
    M3EMenuWidget item, {
    required M3EMenuTheme menuTheme,
    required M3EColorScheme scheme,
    required M3EMenuColorStyle style,
    required M3EMenuColors palette,
    required BorderRadius radius,
    required M3EInteractionState state,
  }) {
    Color background = item.selected
        ? palette.selectedContainer
        : const Color(0x00000000);
    if (item.selected && !item.enabled) {
      background = background.withValues(alpha: menuTheme.disabledOpacity);
    }
    if (state.focused) {
      final Color layer = item.selected
          ? palette.selectedContent
          : palette.stateLayer;
      background = Color.alphaBlend(
        layer.withValues(alpha: M3EStateOpacity.focus),
        background,
      );
    }
    return Container(
      constraints: BoxConstraints(minHeight: menuTheme.entryHeight),
      padding: EdgeInsets.symmetric(
        horizontal: menuTheme.entryHorizontalPadding,
        vertical: menuTheme.entryVerticalPadding,
      ),
      decoration: BoxDecoration(color: background, borderRadius: radius),
      child: Row(
        children: <Widget>[
          Expanded(
            child: IconTheme.merge(
              data: IconThemeData(
                color: menuTheme.entryIconForegroundColor(
                  scheme,
                  enabled: item.enabled,
                  selected: item.selected,
                  hovered: state.hovered,
                  focused: state.focused,
                  pressed: state.pressed,
                  style: style,
                ),
                size: menuTheme.iconSize,
              ),
              child: DefaultTextStyle.merge(
                style: menuTheme.entryLabelStyle(
                  M3ETheme.of(context).typeScale,
                  scheme,
                  enabled: item.enabled,
                  selected: item.selected,
                  style: style,
                ),
                child: item.child,
              ),
            ),
          ),
          if (item.selected)
            Padding(
              padding: EdgeInsets.only(left: menuTheme.iconGap),
              child: Icon(
                M3EIcons.check_rounded,
                size: menuTheme.iconSize * 0.9,
                color: menuTheme.entryIconForegroundColor(
                  scheme,
                  enabled: item.enabled,
                  selected: true,
                  hovered: state.hovered,
                  focused: state.focused,
                  pressed: state.pressed,
                  style: style,
                ),
              ),
            ),
        ],
      ),
    );
  }
}
