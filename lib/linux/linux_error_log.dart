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
            child: Text(linuxText(context, 'uiShowError')),
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
  String t(String key) => linuxText(context, key);
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
                      M3ESnackbar.show(sheet, message: t('uiCopied'));
                    }
                  },
                  child: Text(t('uiCopy')),
                ),
                const SizedBox(width: 8),
                M3EButton.filled(
                  onPressed: () => Navigator.pop(sheet),
                  child: Text(t('uiClose')),
                ),
              ],
            ),
          ],
        ),
      );
    },
  );
}

/// A live log in a fixed window of [visibleLines] lines that follows its newest line, unless
/// the reader has scrolled up to read earlier ones.
class LinuxLogTail extends StatefulWidget {
  const LinuxLogTail({super.key, required this.lines, this.visibleLines = 5});

  final List<String> lines;
  final int visibleLines;

  @override
  State<LinuxLogTail> createState() => _LinuxLogTailState();
}

class _LinuxLogTailState extends State<LinuxLogTail> {
  final _scroll = ScrollController();
  bool _following = true;

  static const _fontSize = 12.0;
  static const _lineHeight = 1.35;

  @override
  void initState() {
    super.initState();
    _scroll.addListener(() {
      if (!_scroll.hasClients) return;
      final position = _scroll.position;
      // Near the end: keep following; scrolled up: leave the reader where they are.
      _following = position.pixels >= position.maxScrollExtent - 24;
    });
    _toEnd();
  }

  @override
  void didUpdateWidget(LinuxLogTail old) {
    super.didUpdateWidget(old);
    if (_following) _toEnd();
  }

  void _toEnd() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scroll.hasClients) _scroll.jumpTo(_scroll.position.maxScrollExtent);
    });
  }

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scale = MediaQuery.textScalerOf(context).scale(_fontSize) / _fontSize;
    final height = _fontSize * scale * _lineHeight * widget.visibleLines + 16;
    return Container(
      height: height,
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Scrollbar(
        controller: _scroll,
        child: SingleChildScrollView(
          controller: _scroll,
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          child: SelectableText(
            widget.lines.join('\n'),
            style: theme.textTheme.bodySmall?.copyWith(
              fontFamily: 'monospace',
              fontSize: _fontSize,
              height: _lineHeight,
            ),
          ),
        ),
      ),
    );
  }
}
