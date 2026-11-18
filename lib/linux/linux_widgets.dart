import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import '../expressive/dextop_widgets.dart';

/// A section title above a group of Linux settings.
class LinuxSection extends StatelessWidget {
  const LinuxSection(this.title, {super.key});

  final String title;

  @override
  Widget build(BuildContext context) => DextopSection(title);
}

/// A row that builds an [M3EListItem] and knows what tapping it does.
abstract interface class LinuxTappable implements DextopTappable {}

/// What tapping [row] does: [M3EList] calls only its own tap handler, never the items'.
VoidCallback? linuxTapOf(Widget row) => dextopTapOf(row);

/// Rows that belong together, as one soft expressive segmented list.
class LinuxGroup extends StatelessWidget {
  const LinuxGroup({super.key, required this.children, this.color});

  final List<Widget> children;
  final Color? color;

  @override
  Widget build(BuildContext context) =>
      DextopGroup(color: color, children: children);
}

/// One option of a single choice: the whole row selects it.
class LinuxChoice<T> extends StatelessWidget implements LinuxTappable {
  const LinuxChoice({
    super.key,
    required this.value,
    required this.groupValue,
    required this.onChanged,
    required this.title,
    this.subtitle,
    this.icon,
    this.enabled = true,
  });

  final T value;
  final T? groupValue;
  final ValueChanged<T>? onChanged;
  final String title;
  final String? subtitle;
  final IconData? icon;
  final bool enabled;

  @override
  VoidCallback? get tap {
    final change = enabled ? onChanged : null;
    return change == null || value == groupValue ? null : () => change(value);
  }

  @override
  Widget build(BuildContext context) => DextopChoice<T>(
    value: value,
    groupValue: groupValue,
    onChanged: onChanged,
    title: title,
    subtitle: subtitle,
    icon: icon,
    enabled: enabled,
  );
}

/// An on/off setting row.
class LinuxToggle extends StatelessWidget implements LinuxTappable {
  const LinuxToggle({
    super.key,
    required this.value,
    required this.onChanged,
    required this.title,
    this.subtitle,
    this.icon,
  });

  final bool value;
  final ValueChanged<bool>? onChanged;
  final String title;
  final String? subtitle;
  final IconData? icon;

  @override
  VoidCallback? get tap => onChanged == null ? null : () => onChanged!(!value);

  @override
  Widget build(BuildContext context) => DextopToggle(
    value: value,
    onChanged: onChanged,
    title: title,
    subtitle: subtitle,
    icon: icon,
  );
}

/// A plain menu row in a sheet: icon and text, a ripple when pressed, nothing behind it.
class LinuxMenuRow extends StatelessWidget {
  const LinuxMenuRow({
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
  Widget build(BuildContext context) => DextopMenuRow(
    title: title,
    icon: icon,
    onTap: onTap,
    subtitle: subtitle,
  );
}
