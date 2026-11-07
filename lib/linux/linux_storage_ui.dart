import '../l10n/inline_text.dart';

import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import 'linux_backups.dart';

/// Where a container's storage goes, and removing its caches.
class LinuxStorageUi {
  static const _channel = MethodChannel('app.freedextop/linux');

  static String _partName(BuildContext context, String id) {
    String t(String ja, String en) => LinuxBackupsUi.text(context, ja, en);
    return switch (id) {
      'apt' => t('apt のダウンロード済みパッケージ', 'apt package downloads'),
      'pacman' => t('pacman のダウンロード済みパッケージ', 'pacman package downloads'),
      'dnf' => t('dnf のキャッシュ', 'dnf cache'),
      'steamCache' => t('Steam の Web キャッシュ', 'Steam web cache'),
      'steamDownloads' => t(
        'Steam のダウンロード途中のファイル',
        'Steam downloads in progress',
      ),
      'steamShaders' => t('Steam のシェーダーキャッシュ', 'Steam shader cache'),
      'userCache' => t('ユーザーのキャッシュ（~/.cache）', 'User cache (~/.cache)'),
      'tmp' => t('一時ファイル（/tmp）', 'Temporary files (/tmp)'),
      'varTmp' => t('一時ファイル（/var/tmp）', 'Temporary files (/var/tmp)'),
      'steamGames' => t('Steam のゲーム', 'Steam games'),
      'steam' => t('Steam 本体とランタイム', 'Steam client and runtimes'),
      'flatpak' => t('Flatpak（ユーザー）', 'Flatpak (user)'),
      'flatpakSystem' => t('Flatpak（システム）', 'Flatpak (system)'),
      'appImages' => t('AppImage', 'AppImages'),
      'home' => t('ホームのファイル', 'Home files'),
      'gaming' => t(
        'ゲーム互換レイヤー（FEX・Proton など）',
        'Gaming compatibility (FEX, Proton…)',
      ),
      'opt' => t('/opt（GPU ドライバーなど）', '/opt (GPU drivers and more)'),
      'fonts' => t('フォント', 'Fonts'),
      'usr' => t('インストールしたソフトウェア（/usr）', 'Installed software (/usr)'),
      'var' => t('パッケージ情報など（/var）', 'Package data and more (/var)'),
      _ => t('その他', 'Other'),
    };
  }

  /// Measures [containerId], shows the parts, and removes caches when asked ([stopped] only).
  static Future<void> show(
    BuildContext context,
    String containerId, {
    required bool stopped,
  }) async {
    String t(String ja, String en) => LinuxBackupsUi.text(context, ja, en);
    try {
      await _channel.invokeMethod('containerUsage', {'id': containerId});
      if (!context.mounted) return;
      final job = await LinuxBackupsUi.follow(
        context,
        t('使用量を調べています', 'Measuring storage'),
      );
      if (!context.mounted) return;
      if (job?['state'] == 'failed') {
        LinuxBackupsUi.report(context, job, '');
        return;
      }
      final usage =
          (job?['result'] as Map?)?.cast<Object?, Object?>() ?? const {};
      final clean = await _sheet(context, containerId, usage);
      if (clean != true || !context.mounted) return;
      if (!stopped) {
        M3ESnackbar.show(
          context,
          message: t(
            'キャッシュの削除の前にコンテナを停止してください（強制停止）',
            'Stop the container first (Force stop)',
          ),
        );
        return;
      }
      await _channel.invokeMethod('cleanContainer', {'id': containerId});
      if (!context.mounted) return;
      final cleaned = await LinuxBackupsUi.follow(
        context,
        t('キャッシュを削除しています', 'Removing caches'),
      );
      if (!context.mounted) return;
      final freed = ((cleaned?['result'] as Map?)?['freed'] as num?) ?? 0;
      LinuxBackupsUi.report(
        context,
        cleaned,
        inlineFormat(t('{0} 空きました', '{0} freed'), [LinuxBackupsUi.size(freed)]),
      );
    } catch (e) {
      if (context.mounted) {
        M3ESnackbar.show(context, message: LinuxBackupsUi.message(e));
      }
    }
  }

  static Future<bool?> _sheet(
    BuildContext context,
    String containerId,
    Map<Object?, Object?> usage,
  ) {
    final total = (usage['total'] as num?) ?? 0;
    final cache = (usage['cacheBytes'] as num?) ?? 0;
    final snapshots = (usage['snapshotBytes'] as num?) ?? 0;
    final parts = ((usage['parts'] as List?) ?? const [])
        .whereType<Map<Object?, Object?>>()
        .toList();
    return M3EBottomSheet.show<bool>(
      context,
      builder: (sheet) {
        String t(String ja, String en) => LinuxBackupsUi.text(sheet, ja, en);
        final theme = Theme.of(sheet);
        return Padding(
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                t('ストレージの使用量', 'Storage use'),
                style: theme.textTheme.titleLarge,
              ),
              const SizedBox(height: 4),
              Text(
                [
                  inlineFormat(t('合計 {0}', 'Total {0}'), [
                    LinuxBackupsUi.size(total),
                  ]),
                  if (snapshots > 0)
                    inlineFormat(
                      t('スナップショット {0}（別に保存）', 'Snapshots {0} (kept apart)'),
                      [LinuxBackupsUi.size(snapshots)],
                    ),
                ].join(' · '),
                style: theme.textTheme.bodyMedium,
              ),
              const SizedBox(height: 12),
              ConstrainedBox(
                constraints: BoxConstraints(
                  maxHeight: MediaQuery.sizeOf(sheet).height * 0.5,
                ),
                child: ListView(
                  shrinkWrap: true,
                  children: [
                    for (final part in parts)
                      _row(
                        sheet,
                        part,
                        total,
                        t('削除可', 'Removable'),
                        () => Navigator.of(sheet).push<void>(
                          MaterialPageRoute(
                            builder: (_) => _LinuxStorageDetails(
                              container: containerId,
                              category: part['id'] as String,
                              path: part['path'] as String,
                              title: _partName(sheet, part['id'] as String),
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  M3EButton.text(
                    onPressed: () => Navigator.pop(sheet, false),
                    child: Text(t('閉じる', 'Close')),
                  ),
                  const SizedBox(width: 8),
                  M3EButton.filled(
                    onPressed: cache > 0
                        ? () => Navigator.pop(sheet, true)
                        : null,
                    child: Text(
                      inlineFormat(t('キャッシュを削除（{0}）', 'Remove caches ({0})'), [
                        LinuxBackupsUi.size(cache),
                      ]),
                    ),
                  ),
                ],
              ),
            ],
          ),
        );
      },
    );
  }

  static Widget _row(
    BuildContext context,
    Map<Object?, Object?> part,
    num total,
    String removable,
    VoidCallback onTap,
  ) {
    final theme = Theme.of(context);
    final bytes = (part['bytes'] as num?) ?? 0;
    return Material(
      type: MaterialType.transparency,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 6),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      _partName(context, part['id'] as String? ?? ''),
                      style: theme.textTheme.bodyMedium,
                    ),
                  ),
                  if (part['cache'] == true)
                    Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: Text(
                        removable,
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: theme.colorScheme.primary,
                        ),
                      ),
                    ),
                  Text(
                    LinuxBackupsUi.size(bytes),
                    style: theme.textTheme.bodySmall,
                  ),
                  const SizedBox(width: 8),
                  const Icon(M3EIcons.chevron_right_rounded, size: 18),
                ],
              ),
              const SizedBox(height: 4),
              LinearProgressIndicator(
                value: total > 0 ? bytes / total : 0,
                borderRadius: BorderRadius.circular(4),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Each page retains its measured children when returning from a deeper directory.
class _LinuxStorageDetails extends StatefulWidget {
  const _LinuxStorageDetails({
    required this.container,
    required this.category,
    required this.path,
    required this.title,
  });
  final String container, category, path, title;
  @override
  State<_LinuxStorageDetails> createState() => _LinuxStorageDetailsState();
}

class _LinuxStorageDetailsState extends State<_LinuxStorageDetails> {
  static const _channel = MethodChannel('app.freedextop/linux');
  final List<Map<Object?, Object?>> entries = [];
  bool loading = true;
  int offset = 0, count = 0;
  num total = 0;
  String? error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final result = await _channel.invokeMapMethod<Object?, Object?>(
        'storageBreakdown',
        {
          'id': widget.container,
          'category': widget.category,
          'path': widget.path,
          'offset': offset,
        },
      );
      if (!mounted) return;
      setState(() {
        entries.addAll(
          ((result?['entries'] as List?) ?? const [])
              .whereType<Map<Object?, Object?>>(),
        );
        offset = (result?['nextOffset'] as num?)?.toInt() ?? entries.length;
        count = (result?['count'] as num?)?.toInt() ?? entries.length;
        total = (result?['total'] as num?) ?? 0;
      });
    } catch (e) {
      if (mounted) setState(() => error = LinuxBackupsUi.message(e));
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    String t(String ja, String en) => LinuxBackupsUi.text(context, ja, en);
    return Scaffold(
      appBar: M3EAppBar.top(
        titleText: widget.title,
        automaticallyImplyLeading: true,
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SelectableText(widget.path),
                if (!loading || entries.isNotEmpty)
                  Text(
                    inlineFormat(t('合計 {0} · {1}項目', 'Total {0} · {1} items'), [
                      LinuxBackupsUi.size(total),
                      count,
                    ]),
                  ),
              ],
            ),
          ),
          if (loading) const M3EProgressIndicator.linearWavy(),
          if (error != null)
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  Text(error!),
                  TextButton(onPressed: _load, child: Text(t('再試行', 'Retry'))),
                ],
              ),
            ),
          if (!loading && error == null && entries.isEmpty)
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text(
                t(
                  'このカテゴリに含まれるファイルはありません',
                  'No files belong to this category here',
                ),
              ),
            ),
          Expanded(
            child: ListView.builder(
              itemCount: entries.length + (offset < count ? 1 : 0),
              itemBuilder: (context, index) {
                if (index == entries.length) {
                  return TextButton(
                    onPressed: loading ? null : _load,
                    child: Text(t('さらに表示', 'Load more')),
                  );
                }
                final entry = entries[index];
                final directory = entry['directory'] == true;
                return ListTile(
                  leading: Icon(
                    directory
                        ? M3EIcons.folder_rounded
                        : M3EIcons.insert_drive_file_rounded,
                  ),
                  title: Text(entry['name'] as String),
                  subtitle: Text(
                    LinuxBackupsUi.size((entry['bytes'] as num?) ?? 0),
                  ),
                  trailing: directory
                      ? const Icon(M3EIcons.chevron_right_rounded)
                      : null,
                  onTap: directory
                      ? () => Navigator.of(context).push<void>(
                          MaterialPageRoute(
                            builder: (_) => _LinuxStorageDetails(
                              container: widget.container,
                              category: widget.category,
                              path: entry['path'] as String,
                              title: entry['name'] as String,
                            ),
                          ),
                        )
                      : () => showDialog<void>(
                          context: context,
                          builder: (dialog) => AlertDialog(
                            title: Text(entry['name'] as String),
                            content: SelectableText(
                              '${entry['path']}\n${LinuxBackupsUi.size((entry['bytes'] as num?) ?? 0)}',
                            ),
                            actions: [
                              TextButton(
                                onPressed: () => Navigator.pop(dialog),
                                child: Text(t('閉じる', 'Close')),
                              ),
                            ],
                          ),
                        ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
