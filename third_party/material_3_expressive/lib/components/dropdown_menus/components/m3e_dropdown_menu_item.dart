import 'package:material_ui/material_ui.dart';

import '../../../foundations/foundations.dart';
import '../../cards/m3e_cards.dart';
import '../models/m3e_dropdown_item.dart';
import '../styles/m3e_dropdown_item_style.dart';
import '../styles/m3e_dropdown_menu_theme.dart';

/// Internal widget for a single dropdown menu item.
/// Handles hover/press interactions and snappy radius morphing.
class M3EDropdownMenuItemWidget<T> extends StatefulWidget {
  /// item.
  final M3EDropdownItem<T> item;

  /// index.
  final int index;

  /// total.
  final int total;

  /// style.
  final M3EDropdownItemStyle style;

  /// onTap.
  final VoidCallback onTap;

  /// M3EDropdownMenuItemWidget.

  const M3EDropdownMenuItemWidget({
    super.key,
    required this.item,
    required this.index,
    required this.total,
    required this.style,
    required this.onTap,
  });

  @override
  State<M3EDropdownMenuItemWidget<T>> createState() =>
      _M3EDropdownMenuItemWidgetState<T>();
}

class _M3EDropdownMenuItemWidgetState<T>
    extends State<M3EDropdownMenuItemWidget<T>> {
  bool _isHovered = false;
  bool _isPressed = false;
  bool _lastSelected = false;

  @override
  void initState() {
    super.initState();
    _lastSelected = widget.item.selected;
  }

  BorderRadius _calculateBaseRadius(double currentRadius) {
    final id = widget.style;
    final isFirst = widget.index == 0;
    final isLast = widget.index == widget.total - 1;
    final isSingle = widget.total == 1;

    final outerR = id.outerRadius ?? 12.0;

    if (widget.item.selected) {
      return BorderRadius.circular(currentRadius);
    } else if (isSingle) {
      return BorderRadius.circular(outerR);
    } else if (isFirst) {
      return BorderRadius.vertical(
        top: Radius.circular(outerR),
        bottom: Radius.circular(currentRadius),
      );
    } else if (isLast) {
      return BorderRadius.vertical(
        top: Radius.circular(currentRadius),
        bottom: Radius.circular(outerR),
      );
    } else {
      return BorderRadius.circular(currentRadius);
    }
  }

  BorderRadius _buildEffectiveRadius() {
    final id = widget.style;

    // 1. Determine the "dynamic" radius (Selected vs Hover vs Pressed vs Inner)
    double targetR = id.innerRadius;

    if (widget.item.selected) {
      targetR = id.selectedBorderRadius ?? id.outerRadius ?? 12.0;
    }

    if (_isPressed) {
      targetR = id.pressedRadius;
    } else if (_isHovered) {
      targetR = id.hoverRadius;
    }

    // 2. Apply this dynamic radius to the correct corners, PRESERVING outerRadius
    return _calculateBaseRadius(targetR);
  }

  Color _resolveBackgroundColor(
    M3EColorScheme scheme,
    M3EDropdownMenuTheme menuTheme,
  ) {
    final id = widget.style;
    final item = widget.item;
    if (item.disabled) {
      return id.disabledBackgroundColor ??
          scheme.onSurface.withValues(alpha: 0.04);
    }
    if (item.selected) {
      return id.selectedBackgroundColor ??
          menuTheme.itemSelectedBackgroundColor(scheme);
    }
    return id.backgroundColor ?? menuTheme.itemBackgroundColor(scheme);
  }

  Color _resolveTextColor(
    M3EColorScheme scheme,
    M3EDropdownMenuTheme menuTheme,
  ) {
    final id = widget.style;
    final item = widget.item;
    if (item.disabled) {
      return id.disabledTextColor ?? scheme.onSurface.withValues(alpha: 0.38);
    }
    if (item.selected) {
      return id.selectedTextColor ??
          menuTheme.itemSelectedForegroundColor(scheme);
    }
    return id.textColor ?? menuTheme.itemForegroundColor(scheme);
  }

  Widget _buildItemContent(
    M3EThemeData m3eTheme,
    M3EColorScheme scheme,
    M3EDropdownMenuTheme menuTheme,
    Color textColor,
  ) {
    final id = widget.style;
    final item = widget.item;
    return Row(
      children: [
        Expanded(
          child: Text(
            item.label,
            style:
                (item.selected ? id.selectedTextStyle : id.textStyle) ??
                menuTheme
                    .itemTextStyle(m3eTheme.typeScale, scheme)
                    .copyWith(color: textColor),
          ),
        ),
        if (item.selected)
          id.selectedIcon ??
              Icon(
                Icons.check_rounded,
                color: textColor,
                size: m3eTheme.resolvedIconTheme.size,
              ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final m3eTheme = M3ETheme.of(context);
    final scheme = m3eTheme.colorScheme;
    final menuTheme = m3eTheme.dropdownMenuTheme;
    final id = widget.style;
    final item = widget.item;

    final bgColor = _resolveBackgroundColor(scheme, menuTheme);
    final textColor = _resolveTextColor(scheme, menuTheme);
    final content = _buildItemContent(m3eTheme, scheme, menuTheme, textColor);

    final selectionChanged = _lastSelected != item.selected;
    _lastSelected = item.selected;
    final duration = selectionChanged
        ? const Duration(milliseconds: 20)
        : const Duration(milliseconds: 40);

    return TweenAnimationBuilder<BorderRadius?>(
      duration: duration,
      curve: Curves.easeOut,
      tween: BorderRadiusTween(
        begin: _buildEffectiveRadius(),
        end: _buildEffectiveRadius(),
      ),
      builder: (context, animatedRadius, child) {
        return M3ECard(
          variant: M3ECardVariant.filled,
          borderRadius: animatedRadius ?? _buildEffectiveRadius(),
          color: bgColor,
          elevation: 0,
          padding: id.itemPadding,
          width: double.infinity,
          onPressed: item.disabled ? null : widget.onTap,
          mouseCursor: id.mouseCursor,
          onStateChanged: (state) {
            if (_isHovered != state.hovered || _isPressed != state.pressed) {
              setState(() {
                _isHovered = state.hovered;
                _isPressed = state.pressed;
              });
            }
          },
          child: child,
        );
      },
      child: content,
    );
  }
}
