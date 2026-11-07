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
  final String Function(String, String) t;
  final bool enabled;
  final bool allowDefault;

  Future<void> _edit(BuildContext context) async {
    var input = '${value == 0 ? 100 : value}';
    String? error;
    final result = await showDialog<int>(
      context: context,
      builder: (dialog) => StatefulBuilder(
        builder: (context, setState) => AlertDialog(
          title: Text(t('表示倍率', 'Display scale')),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                t(
                  '50〜300%で指定します。アプリを起動し直すと反映されます。',
                  'Choose 50–300%. Applies after restarting the app.',
                ),
              ),
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
                child: Text(t('全体設定に従う', 'Use global setting')),
              ),
            TextButton(
              onPressed: () => Navigator.pop(dialog),
              child: Text(t('キャンセル', 'Cancel')),
            ),
            TextButton(
              onPressed: () {
                final percent = int.tryParse(input);
                if (percent == null || percent < 50 || percent > 300) {
                  setState(
                    () => error = t(
                      '50〜300で入力してください',
                      'Enter a value from 50 to 300',
                    ),
                  );
                  return;
                }
                Navigator.pop(dialog, percent);
              },
              child: Text(t('保存', 'Save')),
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
        headline: t('表示倍率', 'Display scale'),
        supportingText: value == 0
            ? t('全体設定に従う', 'Use global setting')
            : '$value%',
        leading: const Icon(M3EIcons.zoom_in_rounded),
        trailing: const Icon(M3EIcons.chevron_right_rounded),
        onTap: enabled ? () => _edit(context) : null,
      ),
    ],
  );
}
