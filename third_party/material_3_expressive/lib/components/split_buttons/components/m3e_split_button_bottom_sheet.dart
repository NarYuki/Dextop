import 'package:material_3_expressive/foundations/foundations.dart';
import 'package:material_ui/material_ui.dart';

import '../../buttons/m3e_buttons.dart';
import '../enums/m3e_split_button_selection_mode.dart';
import '../models/m3e_split_button_item.dart';
import '../styles/m3e_split_button_bottom_sheet_decoration.dart';
import '../styles/m3e_split_button_checkbox_style.dart';

/// member.

Future<Object?> showSplitButtonBottomSheet<T>({
  required BuildContext context,
  required List<M3ESplitButtonItem<T>> items,
  required M3ESplitButtonBottomSheetDecoration decoration,
  required Color foregroundColor,
  required double iconSize,
  FocusNode? callerFocusNode,
  Set<T>? selectedValues,
}) {
  if (decoration.selectionMode == M3ESplitButtonSelectionMode.multiple) {
    return _showMultiSelectBottomSheet<T>(
      context: context,
      items: items,
      decoration: decoration,
      foregroundColor: foregroundColor,
      iconSize: iconSize,
      callerFocusNode: callerFocusNode,
      initialSelectedValues: selectedValues,
    );
  }

  return _showSingleSelectBottomSheet<T>(
    context: context,
    items: items,
    decoration: decoration,
    foregroundColor: foregroundColor,
    iconSize: iconSize,
    callerFocusNode: callerFocusNode,
  );
}

Future<T?> _showSingleSelectBottomSheet<T>({
  required BuildContext context,
  required List<M3ESplitButtonItem<T>> items,
  required M3ESplitButtonBottomSheetDecoration decoration,
  required Color foregroundColor,
  required double iconSize,
  FocusNode? callerFocusNode,
}) {
  final keyboardActivated = callerFocusNode?.hasFocus ?? false;

  return showModalBottomSheet<T>(
    context: context,
    showDragHandle: decoration.showDragHandle,
    backgroundColor: decoration.backgroundColor,
    elevation: decoration.elevation,
    shape: decoration.shape,
    builder: (sheetContext) {
      final m3eTheme = M3ETheme.of(sheetContext);

      return M3EScrimSystemUi.wrapBottomSheet(
        SafeArea(
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (decoration.title != null)
                  Padding(
                    padding:
                        decoration.titlePadding ??
                        const EdgeInsets.fromLTRB(24, 16, 24, 8),
                    child: DefaultTextStyle.merge(
                      style: m3eTheme.textTheme.titleMedium,
                      child: decoration.title!,
                    ),
                  ),
                for (int i = 0; i < items.length; i++)
                  _buildBottomSheetItem(
                    context: sheetContext,
                    item: items[i],
                    foregroundColor: foregroundColor,
                    iconSize: iconSize,
                    autofocus:
                        keyboardActivated &&
                        items[i].enabled &&
                        i == items.indexWhere((e) => e.enabled),
                  ),
                const SizedBox(height: 8),
              ],
            ),
          ),
        ),
      );
    },
  );
}

Future<List<T>?> _showMultiSelectBottomSheet<T>({
  required BuildContext context,
  required List<M3ESplitButtonItem<T>> items,
  required M3ESplitButtonBottomSheetDecoration decoration,
  required Color foregroundColor,
  required double iconSize,
  FocusNode? callerFocusNode,
  Set<T>? initialSelectedValues,
}) {
  final keyboardActivated = callerFocusNode?.hasFocus ?? false;

  return showModalBottomSheet<List<T>>(
    context: context,
    showDragHandle: decoration.showDragHandle,
    backgroundColor: decoration.backgroundColor,
    elevation: decoration.elevation,
    shape: decoration.shape,
    isScrollControlled: true,
    builder: (sheetContext) {
      return M3EScrimSystemUi.wrapBottomSheet(
        _MultiSelectBottomSheet<T>(
          context: sheetContext,
          items: items,
          decoration: decoration,
          foregroundColor: foregroundColor,
          iconSize: iconSize,
          initialSelectedValues: initialSelectedValues,
          keyboardActivated: keyboardActivated,
        ),
      );
    },
  );
}

class _MultiSelectBottomSheet<T> extends StatefulWidget {
  const _MultiSelectBottomSheet({
    required this.context,
    required this.items,
    required this.decoration,
    required this.foregroundColor,
    required this.iconSize,
    required this.keyboardActivated,
    this.initialSelectedValues,
  });

  final BuildContext context;
  final List<M3ESplitButtonItem<T>> items;
  final M3ESplitButtonBottomSheetDecoration decoration;
  final Color foregroundColor;
  final double iconSize;
  final bool keyboardActivated;
  final Set<T>? initialSelectedValues;

  @override
  State<_MultiSelectBottomSheet<T>> createState() =>
      _MultiSelectBottomSheetState<T>();
}

class _MultiSelectBottomSheetState<T>
    extends State<_MultiSelectBottomSheet<T>> {
  late Set<T> _selectedValues;

  static const double _kMinCheckboxSize = 20;
  static const double _kMaxCheckboxSize = 32;

  @override
  void initState() {
    super.initState();
    _selectedValues = Set<T>.from(widget.initialSelectedValues ?? {});
  }

  void _toggleValue(T value) {
    setState(() {
      if (_selectedValues.contains(value)) {
        _selectedValues.remove(value);
      } else {
        _selectedValues.add(value);
      }
    });
  }

  void _onDone() {
    Navigator.of(context).pop(_selectedValues.toList());
  }

  @override
  Widget build(BuildContext context) {
    final m3eTheme = M3ETheme.of(context);

    return SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (widget.decoration.title != null)
            Padding(
              padding:
                  widget.decoration.titlePadding ??
                  const EdgeInsets.fromLTRB(24, 16, 24, 8),
              child: DefaultTextStyle.merge(
                style: m3eTheme.textTheme.titleMedium,
                child: widget.decoration.title!,
              ),
            ),
          Flexible(
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  for (int i = 0; i < widget.items.length; i++)
                    _buildMultiSelectItem(
                      context: context,
                      item: widget.items[i],
                      isSelected: _selectedValues.contains(
                        widget.items[i].value,
                      ),
                      checkboxStyle: widget.decoration.checkboxStyle,
                      autofocus:
                          widget.keyboardActivated &&
                          widget.items[i].enabled &&
                          i == widget.items.indexWhere((e) => e.enabled),
                    ),
                ],
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                M3EButton.text(
                  onPressed: () => Navigator.of(context).pop(<T>[]),
                  child: const Text('Clear'),
                ),
                const SizedBox(width: 8),
                M3EButton.filled(
                  onPressed: _onDone,
                  child: Text(
                    _selectedValues.isEmpty
                        ? 'Done'
                        : 'Done (${_selectedValues.length})',
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMultiSelectItem({
    required BuildContext context,
    required M3ESplitButtonItem<T> item,
    required bool isSelected,
    M3ESplitButtonCheckboxStyle? checkboxStyle,
    bool autofocus = false,
  }) {
    final m3eTheme = M3ETheme.of(context);
    final cs = m3eTheme.colorScheme;

    final effectiveColor = item.enabled
        ? widget.foregroundColor
        : widget.foregroundColor.withValues(
            alpha: M3EButtonConstants.kDisabledForegroundAlpha,
          );

    final child = _multiSelectItemChild(
      item: item,
      effectiveColor: effectiveColor,
      m3eTheme: m3eTheme,
    );

    final activeColor = checkboxStyle?.activeColor ?? cs.primary;
    final iconColor = checkboxStyle?.iconColor ?? cs.onPrimary;
    final borderColor =
        checkboxStyle?.nonActiveColor ??
        checkboxStyle?.borderColor ??
        effectiveColor.withValues(alpha: 0.6);
    final activeBorderRadius =
        checkboxStyle?.activeBorderRadius ?? BorderRadius.circular(4);
    final nonActiveBorderRadius =
        checkboxStyle?.nonActiveBorderRadius ?? BorderRadius.circular(4);
    final checkboxSize = _resolveCheckboxSize(item, child, context);
    final selectedIcon =
        checkboxStyle?.icon ?? const Icon(M3EIcons.check_rounded);

    return InkWell(
      autofocus: autofocus,
      onTap: item.enabled ? () => _toggleValue(item.value) : null,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
        child: Row(
          children: [
            _buildStyledCheckbox(
              size: checkboxSize,
              selected: isSelected,
              enabled: item.enabled,
              activeColor: activeColor,
              iconColor: iconColor,
              borderColor: borderColor,
              activeBorderRadius: activeBorderRadius,
              nonActiveBorderRadius: nonActiveBorderRadius,
              selectedIcon: selectedIcon,
            ),
            const SizedBox(width: 16),
            Expanded(child: child),
          ],
        ),
      ),
    );
  }

  Widget _multiSelectItemChild({
    required M3ESplitButtonItem<T> item,
    required Color effectiveColor,
    required M3EThemeData m3eTheme,
  }) {
    if (item.child is IconData) {
      return Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            item.child as IconData,
            size: widget.iconSize,
            color: effectiveColor,
          ),
          const SizedBox(width: 16),
          Flexible(
            child: Text(
              item.child.toString(),
              style: m3eTheme.typeScale.bodyLarge.copyWith(
                color: effectiveColor,
              ),
            ),
          ),
        ],
      );
    }
    if (item.child is Widget) {
      return item.child as Widget;
    }
    return Text(
      item.child.toString(),
      style: m3eTheme.typeScale.bodyLarge.copyWith(color: effectiveColor),
    );
  }

  double _resolveCheckboxSize(
    M3ESplitButtonItem<T> item,
    Widget child,
    BuildContext context,
  ) {
    if (item.child is IconData) {
      return (widget.iconSize + 6.0).clamp(
        _kMinCheckboxSize,
        _kMaxCheckboxSize,
      );
    }
    if (item.child is Icon) {
      final icon = item.child as Icon;
      final size = (icon.size ?? widget.iconSize) + 6.0;
      return size.clamp(_kMinCheckboxSize, _kMaxCheckboxSize);
    }
    if (item.child is Text) {
      final text = item.child as Text;
      final fontSize =
          text.style?.fontSize ??
          M3ETheme.of(context).typeScale.bodyLarge.fontSize ??
          16.0;
      final size = fontSize * 1.35;
      return size.clamp(_kMinCheckboxSize, _kMaxCheckboxSize);
    }
    if (child is PreferredSizeWidget) {
      return child.preferredSize.height.clamp(
        _kMinCheckboxSize,
        _kMaxCheckboxSize,
      );
    }
    return 24;
  }

  Widget _buildStyledCheckbox({
    required double size,
    required bool selected,
    required bool enabled,
    required Color activeColor,
    required Color iconColor,
    required Color borderColor,
    required BorderRadius activeBorderRadius,
    required BorderRadius nonActiveBorderRadius,
    required Widget selectedIcon,
  }) {
    final bg = selected ? activeColor : Colors.transparent;
    final radius = selected ? activeBorderRadius : nonActiveBorderRadius;
    final outline = selected ? activeColor : borderColor;
    final disabledAlpha = enabled
        ? 1.0
        : M3EButtonConstants.kDisabledForegroundAlpha;

    return AnimatedContainer(
      duration: const Duration(milliseconds: 120),
      curve: Curves.easeOutCubic,
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: bg.withValues(alpha: bg.a * disabledAlpha),
        borderRadius: radius,
        border: Border.all(
          color: outline.withValues(alpha: outline.a * disabledAlpha),
          width: 1.5,
        ),
      ),
      child: selected
          ? Center(
              child: IconTheme(
                data: IconThemeData(
                  color: iconColor.withValues(alpha: disabledAlpha),
                  size: (size * 0.72).clamp(12.0, 22.0),
                ),
                child: selectedIcon,
              ),
            )
          : null,
    );
  }
}

Widget _buildBottomSheetItem<T>({
  required BuildContext context,
  required M3ESplitButtonItem<T> item,
  required Color foregroundColor,
  required double iconSize,
  bool autofocus = false,
}) {
  final m3eTheme = M3ETheme.of(context);

  final Color effectiveColor = item.enabled
      ? foregroundColor
      : foregroundColor.withValues(
          alpha: M3EButtonConstants.kDisabledForegroundAlpha,
        );

  Widget child;
  if (item.child is IconData) {
    child = Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(item.child as IconData, size: iconSize, color: effectiveColor),
        const SizedBox(width: 16),
        Flexible(
          child: Text(
            item.child.toString(),
            style: m3eTheme.typeScale.bodyLarge.copyWith(color: effectiveColor),
          ),
        ),
      ],
    );
  } else if (item.child is Widget) {
    child = item.child as Widget;
  } else {
    child = Text(
      item.child.toString(),
      style: m3eTheme.typeScale.bodyLarge.copyWith(color: effectiveColor),
    );
  }

  return InkWell(
    autofocus: autofocus,
    onTap: item.enabled ? () => Navigator.of(context).pop(item.value) : null,
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
      child: child,
    ),
  );
}
