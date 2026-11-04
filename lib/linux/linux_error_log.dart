import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import 'linux_expressive.dart';

/// The first meaningful line of [text], short enough for one line on a card.
String linuxErrorSummary(String text) {
  final line = text
      .split('\n')
      .map((l) => l.trim())
      .firstWhere((l) => l.isNotEmpty, orElse: () => '');
  final summary = line.replaceFirst(RegExp(r'^(error|Error|ERROR):\s*'), '');
  return summary.length > 140 ? '${summary.substring(0, 140)}…' : summary;
}

/// A container's error on its card: one line, and the whole text in [showLinuxErrorLog].
class LinuxErrorLine extends StatelessWidget {
  const LinuxErrorLine({super.key, required this.title, required this.text});

  final String title;
  final String text;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final error = theme.colorScheme.error;
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: Row(
        children: [
          Icon(M3EIcons.error_rounded, size: 18, color: error),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              linuxErrorSummary(text),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.bodySmall?.copyWith(color: error),
            ),
          ),
          const SizedBox(width: 8),
          M3EButton.text(
            onPressed: () => showLinuxErrorLog(context, title, text),
            child: Text(linuxText(context, 'エラーを表示', 'Show error')),
          ),
        ],
      ),
    );
  }
}

/// The whole error, scrollable within the screen, selectable and copyable.
Future<void> showLinuxErrorLog(
  BuildContext context,
  String title,
  String text,
) {
  String t(String ja, String en) => linuxText(context, ja, en);
  return M3EBottomSheet.show<void>(
    context,
    builder: (sheet) {
      final theme = Theme.of(sheet);
      final height = MediaQuery.sizeOf(sheet).height;
      return Padding(
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(M3EIcons.error_rounded, color: theme.colorScheme.error),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.titleLarge,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(linuxErrorSummary(text), style: theme.textTheme.bodyMedium),
            const SizedBox(height: 12),
            ConstrainedBox(
              constraints: BoxConstraints(maxHeight: height * 0.5),
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: theme.colorScheme.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Scrollbar(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.all(12),
                    child: SelectableText(
                      text,
                      style: theme.textTheme.bodySmall?.copyWith(
                        fontFamily: 'monospace',
                        height: 1.35,
                      ),
                    ),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                M3EButton.text(
                  onPressed: () async {
                    await Clipboard.setData(ClipboardData(text: text));
                    if (sheet.mounted) {
                      M3ESnackbar.show(sheet, message: t('コピーしました', 'Copied'));
                    }
                  },
                  child: Text(t('コピー', 'Copy')),
                ),
                const SizedBox(width: 8),
                M3EButton.filled(
                  onPressed: () => Navigator.pop(sheet),
                  child: Text(t('閉じる', 'Close')),
                ),
              ],
            ),
          ],
        ),
      );
    },
  );
}
