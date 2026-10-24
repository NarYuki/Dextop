import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

/// A section title above a group of Linux settings.
class LinuxSection extends StatelessWidget {
  const LinuxSection(this.title, {super.key});

  final String title;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(8, 20, 8, 8),
      child: Text(
        title,
        style: theme.textTheme.titleSmall?.copyWith(
          color: theme.colorScheme.primary,
        ),
      ),
    );
  }
}

/// A row that builds an [M3EListItem] and knows what tapping it does.
abstract interface class LinuxTappable {
  VoidCallback? get tap;
}

/// What tapping [row] does: [M3EList] calls only its own tap handler, never the items'.
VoidCallback? linuxTapOf(Widget row) => switch (row) {
  final LinuxTappable tappable => tappable.tap,
  final M3EListItem item => item.onTap,
  _ => null,
};

/// Rows that belong together, as one expressive segmented list.
class LinuxGroup extends StatelessWidget {
  const LinuxGroup({super.key, required this.children});

  /// List items ([M3EListItem] or [LinuxTappable] rows).
  final List<Widget> children;

  @override
  Widget build(BuildContext context) => M3EList(
    itemCount: children.length,
    onTap: (index) => linuxTapOf(children[index])?.call(),
    itemBuilder: (context, index) => children[index],
  );
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
  Widget build(BuildContext context) {
    final change = enabled ? onChanged : null;
    return M3EListItem(
      headline: title,
      supportingText: subtitle,
      enabled: enabled,
      leading: icon == null ? null : Icon(icon),
      trailing: M3ERadio<T>(
        value: value,
        groupValue: groupValue,
        onChanged: change,
      ),
      onTap: tap,
    );
  }
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
  Widget build(BuildContext context) => M3EListItem(
    headline: title,
    supportingText: subtitle,
    leading: icon == null ? null : Icon(icon),
    trailing: M3ESwitch(value: value, onChanged: onChanged),
    onTap: tap,
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

  // ListTile needs a Material ancestor, which M3E sheets do not provide.
  @override
  Widget build(BuildContext context) => Material(
    type: MaterialType.transparency,
    child: ListTile(
      leading: Icon(icon),
      title: Text(title),
      subtitle: subtitle == null ? null : Text(subtitle!),
      onTap: onTap,
      contentPadding: const EdgeInsets.symmetric(horizontal: 24),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
    ),
  );
}
