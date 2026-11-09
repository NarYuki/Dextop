import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import 'linux_widgets.dart';

class LinuxScaleSetting extends StatelessWidget {
  const LinuxScaleSetting({
    super.key,
    required this.value,
    required this.onChanged,
    required this.t,
    this.enabled = true,
    this.allowDefault = false,
  });
  final int value;
  final ValueChanged<int> onChanged;
  final String Function(String) t;
  final bool enabled;
  final bool allowDefault;

  Future<void> _edit(BuildContext context) async {
    var input = '${value == 0 ? 100 : value}';
    String? error;
    final result = await showDialog<int>(
      context: context,
      builder: (dialog) => StatefulBuilder(
        builder: (context, setState) => AlertDialog(
          title: Text(t('uiDisplayScale')),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(t('uiChoose50300AppliesAfterRestartingTheApp')),
              const SizedBox(height: 16),
              TextFormField(
                initialValue: input,
                onChanged: (value) => input = value,
                keyboardType: TextInputType.number,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                decoration: InputDecoration(suffixText: '%', errorText: error),
                autofocus: true,
              ),
            ],
          ),
          actions: [
            if (allowDefault)
              TextButton(
                onPressed: () => Navigator.pop(dialog, 0),
                child: Text(t('uiUseGlobalSetting')),
              ),
            TextButton(
              onPressed: () => Navigator.pop(dialog),
              child: Text(t('uiCancel')),
            ),
            TextButton(
              onPressed: () {
                final percent = int.tryParse(input);
                if (percent == null || percent < 50 || percent > 300) {
                  setState(() => error = t('uiEnterAValueFrom50To300'));
                  return;
                }
                Navigator.pop(dialog, percent);
              },
              child: Text(t('uiSave')),
            ),
          ],
        ),
      ),
    );
    if (result != null) onChanged(result);
  }

  @override
  Widget build(BuildContext context) => LinuxGroup(
    children: [
      M3EListItem(
        headline: t('uiDisplayScale'),
        supportingText: value == 0 ? t('uiUseGlobalSetting') : '$value%',
        leading: const Icon(M3EIcons.zoom_in_rounded),
        trailing: const Icon(M3EIcons.chevron_right_rounded),
        onTap: enabled ? () => _edit(context) : null,
      ),
    ],
  );
}
