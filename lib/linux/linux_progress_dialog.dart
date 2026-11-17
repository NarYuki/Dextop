import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

/// Linux's progress dialog (snapshots, restore, export, AppImages, KDE Plasma): a wavy bar and
/// one line of detail. Only the operation closes it; tapping outside, Back and Escape would hide
/// it while the work went on, so they do nothing.
class LinuxProgressDialog extends StatelessWidget {
  const LinuxProgressDialog({
    super.key,
    required this.title,
    required this.icon,
    required this.detail,
  });

  final String title;
  final IconData icon;
  final String detail;

  /// Shows [dialog] (normally a [LinuxProgressDialog]); close it with Navigator.pop from code.
  static Future<T?> show<T>(BuildContext context, {required Widget dialog}) =>
      M3EDialog.show<T>(
        context,
        dialog: dialog,
        barrierDismissible: false,
        onDismissRequest: () async => false,
      );

  @override
  Widget build(BuildContext context) => M3EDialog(
    title: title,
    icon: Icon(icon),
    content: Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const M3EProgressIndicator.linearWavy(),
        const SizedBox(height: 12),
        Text(detail),
      ],
    ),
  );
}
