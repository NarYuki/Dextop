import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

/// Reusable custom color decoration for semantic [M3EButton]s (e.g. error/warning).
M3EButtonDecoration dextopButtonColors({
  required Color background,
  required Color foreground,
}) => M3EButtonDecoration(
  backgroundColor: WidgetStatePropertyAll(background),
  foregroundColor: WidgetStatePropertyAll(foreground),
);

/// Soft section heading above a grouped expressive list or card stack.
class DextopSection extends StatelessWidget {
  const DextopSection(
    this.title, {
    this.padding = const EdgeInsets.fromLTRB(8, 20, 8, 10),
    super.key,
  });

  final String title;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final style = theme.textTheme.titleSmall?.copyWith(
      color: theme.colorScheme.primary,
      fontWeight: FontWeight.w600,
    );
    return Padding(
      padding: padding,
      child: Text(title, style: style),
    );
  }
}

/// Soft pill status badge for read-only labels.
class DextopStatusPill extends StatelessWidget {
  const DextopStatusPill({
    super.key,
    required this.label,
    required this.containerColor,
    required this.contentColor,
    this.icon,
  });

  final String label;
  final Color containerColor;
  final Color contentColor;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final m3e = M3ETheme.of(context);
    return DecoratedBox(
      decoration: ShapeDecoration(
        color: containerColor,
        shape: const StadiumBorder(),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (icon != null) ...[
              Icon(icon, size: 14, color: contentColor),
              const SizedBox(width: 5),
            ],
            Flexible(
              child: Text(
                label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: m3e.typeScale.labelMedium.copyWith(
                  color: contentColor,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Interactive selector button showing the current choice and a dropdown arrow.
class DextopDropdownButton extends StatelessWidget {
  const DextopDropdownButton({
    super.key,
    required this.label,
    this.onPressed,
    this.enabled = true,
  });

  final String label;
  final VoidCallback? onPressed;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final m3e = M3ETheme.of(context);
    final active = enabled && onPressed != null;
    final bg = active
        ? colors.secondaryContainer
        : colors.surfaceContainerHighest;
    final fg = active ? colors.onSecondaryContainer : colors.onSurfaceVariant;
    final border = active
        ? colors.outlineVariant.withValues(alpha: 0.55)
        : colors.outlineVariant.withValues(alpha: 0.25);
    return Material(
      color: bg,
      shape: StadiumBorder(side: BorderSide(color: border)),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: active ? onPressed : null,
        child: Padding(
          padding: const EdgeInsetsDirectional.fromSTEB(12, 6, 8, 6),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Flexible(
                child: Text(
                  label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: m3e.typeScale.labelMedium.copyWith(
                    color: fg,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              const SizedBox(width: 2),
              Icon(
                M3EIcons.arrow_drop_down_rounded,
                size: 20,
                color: fg,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Compact read-only key-value row for grouped info/metrics inside a card.
class DextopKeyValueRow extends StatelessWidget {
  const DextopKeyValueRow({
    super.key,
    required this.label,
    required this.value,
    this.leading,
    this.valueColor,
  });

  final String label;
  final String value;
  final Widget? leading;
  final Color? valueColor;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 11),
      child: Row(
        children: [
          if (leading != null) ...[
            leading!,
            const SizedBox(width: 14),
          ],
          Expanded(
            child: Text(
              label,
              style: theme.textTheme.bodyMedium,
            ),
          ),
          const SizedBox(width: 12),
          Flexible(
            child: Text(
              value,
              textAlign: TextAlign.end,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: valueColor ?? colors.onSurfaceVariant,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// A row that builds an [M3EListItem] and exposes its primary tap callback.
abstract interface class DextopTappable {
  VoidCallback? get tap;
}

/// Resolves the tap handler of an [M3EList] child.
VoidCallback? dextopTapOf(Widget row) => switch (row) {
  final DextopTappable tappable => tappable.tap,
  final M3EListItem item => item.onTap,
  _ => null,
};

/// Soft radii constants shared by segmented groups and sliver lists.
const double dextopGroupOuterRadius = 24;
const double dextopGroupInnerRadius = 8;
const double dextopGroupGap = 3;

/// Rows that belong together, rendered as a soft segmented M3E card list.
class DextopGroup extends StatelessWidget {
  const DextopGroup({
    super.key,
    required this.children,
    this.color,
    this.variant = M3ECardVariant.filled,
  });

  final List<Widget> children;
  final Color? color;
  final M3ECardVariant variant;

  @override
  Widget build(BuildContext context) => M3EList(
    itemCount: children.length,
    outerRadius: dextopGroupOuterRadius,
    innerRadius: dextopGroupInnerRadius,
    gap: dextopGroupGap,
    variant: variant,
    color: color,
    onTap: (index) => dextopTapOf(children[index])?.call(),
    itemBuilder: (context, index) => children[index],
  );
}

/// One option of a single choice: tapping anywhere on the row selects it.
class DextopChoice<T> extends StatelessWidget implements DextopTappable {
  const DextopChoice({
    super.key,
    required this.value,
    required this.groupValue,
    required this.onChanged,
    required this.title,
    this.subtitle,
    this.icon,
    this.enabled = true,
    this.allowReselect = false,
  });

  final T value;
  final T? groupValue;
  final ValueChanged<T>? onChanged;
  final String title;
  final String? subtitle;
  final IconData? icon;
  final bool enabled;

  /// Whether tapping the currently selected item still triggers [onChanged].
  final bool allowReselect;

  @override
  VoidCallback? get tap {
    final change = enabled ? onChanged : null;
    if (change == null) return null;
    if (!allowReselect && value == groupValue) return null;
    return () => change(value);
  }

  @override
  Widget build(BuildContext context) {
    final change = enabled ? onChanged : null;
    return M3EListItem(
      headline: title,
      supportingText: subtitle,
      supportingMaxLines: null,
      enabled: enabled,
      leading: icon == null ? null : Icon(icon),
      trailing: IgnorePointer(
        ignoring: allowReselect && value == groupValue,
        child: M3ERadio<T>(
          value: value,
          groupValue: groupValue,
          onChanged: change,
        ),
      ),
      onTap: tap,
    );
  }
}

/// An on/off setting row using [M3ESwitch] inside [M3EListItem].
class DextopToggle extends StatelessWidget implements DextopTappable {
  const DextopToggle({
    super.key,
    required this.value,
    required this.onChanged,
    required this.title,
    this.subtitle,
    this.icon,
    this.enabled = true,
  });

  final bool value;
  final ValueChanged<bool>? onChanged;
  final String title;
  final String? subtitle;
  final IconData? icon;
  final bool enabled;

  @override
  VoidCallback? get tap =>
      enabled && onChanged != null ? () => onChanged!(!value) : null;

  @override
  Widget build(BuildContext context) => M3EListItem(
    headline: title,
    supportingText: subtitle,
    supportingMaxLines: null,
    enabled: enabled,
    leading: icon == null ? null : Icon(icon),
    trailing: M3ESwitch(
      value: value,
      onChanged: enabled ? onChanged : null,
    ),
    onTap: tap,
  );
}

/// A soft expressive card wrapping a continuous or discrete [M3ESlider].
class DextopSliderTile extends StatelessWidget {
  const DextopSliderTile({
    super.key,
    required this.title,
    required this.valueLabel,
    required this.value,
    required this.min,
    required this.max,
    required this.onChanged,
    this.onChangeEnd,
    this.divisions,
    this.subtitle,
    this.icon,
    this.minLabel,
    this.maxLabel,
    this.wavy = false,
  });

  final String title;
  final String valueLabel;
  final double value;
  final double min;
  final double max;
  final int? divisions;
  final ValueChanged<double>? onChanged;
  final ValueChanged<double>? onChangeEnd;
  final String? subtitle;
  final IconData? icon;
  final String? minLabel;
  final String? maxLabel;
  final bool wavy;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    return M3ECard(
      variant: M3ECardVariant.filled,
      borderRadius: BorderRadius.circular(24),
      padding: const EdgeInsets.fromLTRB(18, 16, 18, 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              if (icon != null) ...[
                Icon(icon, color: colors.primary, size: 22),
                const SizedBox(width: 12),
              ],
              Expanded(
                child: Text(title, style: theme.textTheme.titleMedium),
              ),
              DextopStatusPill(
                label: valueLabel,
                containerColor: colors.secondaryContainer,
                contentColor: colors.onSecondaryContainer,
              ),
            ],
          ),
          if (subtitle != null && subtitle!.isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(
              subtitle!,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: colors.onSurfaceVariant,
              ),
            ),
          ],
          const SizedBox(height: 8),
          if (wavy)
            M3ESlider.wavy(
              value: value.clamp(min, max),
              min: min,
              max: max,
              divisions: divisions,
              label: valueLabel,
              onChanged: onChanged,
              onChangeEnd: onChangeEnd,
            )
          else
            M3ESlider(
              value: value.clamp(min, max),
              min: min,
              max: max,
              divisions: divisions,
              label: valueLabel,
              onChanged: onChanged,
              onChangeEnd: onChangeEnd,
            ),
          if (minLabel != null || maxLabel != null) ...[
            const SizedBox(height: 2),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  minLabel ?? '',
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: colors.onSurfaceVariant,
                  ),
                ),
                Text(
                  maxLabel ?? '',
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: colors.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

/// Connected expressive pill selector built on [M3EButtonGroup].
class DextopChoiceGroup<T> extends StatelessWidget {
  const DextopChoiceGroup({
    super.key,
    required this.choices,
    required this.selected,
    required this.onSelected,
    this.size = M3EButtonSize.sm,
  });

  final List<(T value, String label, IconData icon)> choices;
  final T selected;
  final ValueChanged<T>? onSelected;
  final M3EButtonSize size;

  @override
  Widget build(BuildContext context) {
    final selectedIndex = choices.indexWhere((c) => c.$1 == selected);
    return M3EButtonGroup(
      type: M3EButtonGroupType.connected,
      shape: M3EButtonShape.round,
      style: M3EButtonStyle.tonal,
      size: size,
      selectionRequired: true,
      neighborSquish: true,
      selectedIndex: selectedIndex < 0 ? 0 : selectedIndex,
      onSelectedIndexChanged: onSelected == null
          ? null
          : (index) {
              if (index != null && index >= 0 && index < choices.length) {
                onSelected!(choices[index].$1);
              }
            },
      actions: [
        for (final choice in choices)
          M3EButtonGroupAction(
            icon: Icon(choice.$3, size: 18),
            label: Text(
              choice.$2,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            enabled: onSelected != null,
          ),
      ],
    );
  }
}

/// A plain menu row in a bottom sheet: soft rounded ripple, clean layout.
class DextopMenuRow extends StatelessWidget {
  const DextopMenuRow({
    super.key,
    required this.title,
    required this.icon,
    required this.onTap,
    this.subtitle,
  });

  final String title;
  final String? subtitle;
  final IconData icon;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) => Material(
    type: MaterialType.transparency,
    child: ListTile(
      leading: Icon(icon),
      title: Text(title),
      subtitle: subtitle == null ? null : Text(subtitle!),
      onTap: onTap,
      contentPadding: const EdgeInsets.symmetric(horizontal: 24),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
    ),
  );
}
