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
      final clean = await _sheet(context, usage);
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
                      _row(sheet, part, total, t('削除可', 'Removable')),
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
  ) {
    final theme = Theme.of(context);
    final bytes = (part['bytes'] as num?) ?? 0;
    return Padding(
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
            ],
          ),
          const SizedBox(height: 4),
          LinearProgressIndicator(
            value: total > 0 ? bytes / total : 0,
            borderRadius: BorderRadius.circular(4),
          ),
        ],
      ),
    );
  }
}
