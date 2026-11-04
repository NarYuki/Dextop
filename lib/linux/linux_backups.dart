import '../l10n/inline_text.dart';

import 'dart:async';

import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import 'linux_expressive.dart';
import 'linux_widgets.dart';

/// Snapshots and backups of containers, and AppImages, on the Linux page.
///
/// The work runs in the Linux service, one job at a time; these follow it until it ends.
class LinuxBackupsUi {
  static const _channel = MethodChannel('app.freedextop/linux');

  static String text(BuildContext context, String ja, String en) =>
      linuxText(context, ja, en);

  static String size(num bytes) {
    if (bytes >= 1 << 30) {
      return '${(bytes / (1 << 30)).toStringAsFixed(1)} GiB';
    }
    if (bytes >= 1 << 20) {
      return '${(bytes / (1 << 20)).toStringAsFixed(0)} MiB';
    }
    return '${(bytes / 1024).toStringAsFixed(0)} KiB';
  }

  static Future<String?> _pick(
    BuildContext context, {
    bool create = false,
    String name = '',
    String mime = '*/*',
  }) => _channel.invokeMethod<String>('pickDocument', {
    'create': create,
    'name': name,
    'mime': mime,
  });

  /// Shows the service's job until it ends; returns it (with "result" or "error").
  static Future<Map<Object?, Object?>?> follow(
    BuildContext context,
    String title,
  ) async {
    final job = ValueNotifier<Map<Object?, Object?>?>(null);
    Timer? poll;
    Future<void> read() async {
      try {
        final state = await _channel.invokeMapMethod<Object?, Object?>(
          'snapshots',
        );
        job.value = (state?['job'] as Map?)?.cast<Object?, Object?>();
      } on PlatformException catch (_) {}
    }

    await read();
    if (!context.mounted) return job.value;
    final dialog = M3EDialog.show<void>(
      context,
      dialog: Builder(
        builder: (dialog) => ValueListenableBuilder<Map<Object?, Object?>?>(
          valueListenable: job,
          builder: (context, value, _) {
            final running = value?['state'] == 'running';
            if (!running && value != null) {
              WidgetsBinding.instance.addPostFrameCallback((_) {
                if (Navigator.of(dialog).canPop()) Navigator.of(dialog).pop();
              });
            }
            final bytes = (value?['bytes'] as num?) ?? 0;
            return M3EDialog(
              title: title,
              icon: const Icon(M3EIcons.inventory_2_rounded),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const M3EProgressIndicator.linearWavy(),
                  const SizedBox(height: 12),
                  Text(
                    bytes > 0
                        ? size(bytes)
                        : text(context, '準備しています…', 'Preparing…'),
                  ),
                ],
              ),
            );
          },
        ),
      ),
    );
    poll = Timer.periodic(const Duration(seconds: 1), (_) => read());
    await dialog;
    poll.cancel();
    final result = job.value;
    job.dispose();
    return result;
  }

  static void report(
    BuildContext context,
    Map<Object?, Object?>? job,
    String done,
  ) {
    if (!context.mounted) return;
    final failed = job?['state'] == 'failed';
    M3ESnackbar.show(
      context,
      message: failed
          ? '${text(context, '失敗しました', 'Failed')}: ${job?['error'] ?? ''}'
          : done,
    );
  }

  static String message(Object error) =>
      error is PlatformException ? error.message ?? error.toString() : '$error';

  /// Picks an AppImage and adds it to [containerId]'s applications.
  static Future<void> addAppImage(
    BuildContext context,
    String containerId,
  ) async {
    try {
      final uri = await _pick(context);
      if (uri == null || !context.mounted) return;
      await _channel.invokeMapMethod<Object?, Object?>('installAppImage', {
        'id': containerId,
        'uri': uri,
      });
      if (!context.mounted) return;
      final job = await follow(
        context,
        text(context, 'AppImage を追加しています', 'Adding the AppImage'),
      );
      if (!context.mounted) return;
      final name = (job?['result'] as Map?)?['name'] as String? ?? '';
      report(
        context,
        job,
        inlineFormat(text(context, '{0} を追加しました', '{0} added'), [name]),
      );
    } catch (e) {
      if (context.mounted) M3ESnackbar.show(context, message: message(e));
    }
  }

  /// Saves a snapshot of [containerId] (stopped) on the device.
  static Future<void> createSnapshot(
    BuildContext context,
    String containerId,
  ) async {
    try {
      await _channel.invokeMethod('createSnapshot', {'id': containerId});
      if (!context.mounted) return;
      final job = await follow(
        context,
        text(context, 'スナップショットを作成しています', 'Creating a snapshot'),
      );
      if (context.mounted) {
        report(
          context,
          job,
          text(context, 'スナップショットを作成しました', 'Snapshot created'),
        );
      }
    } catch (e) {
      if (context.mounted) M3ESnackbar.show(context, message: message(e));
    }
  }

  /// Restores a backup file the user picks as a new container.
  static Future<void> importBackup(BuildContext context) async {
    try {
      final uri = await _pick(context);
      if (uri == null || !context.mounted) return;
      await _channel.invokeMethod('importBackup', {'uri': uri});
      if (!context.mounted) return;
      final job = await follow(
        context,
        text(context, 'バックアップから復元しています', 'Restoring the backup'),
      );
      if (context.mounted) {
        report(
          context,
          job,
          text(context, '新しいコンテナとして復元しました', 'Restored as a new container'),
        );
      }
    } catch (e) {
      if (context.mounted) M3ESnackbar.show(context, message: message(e));
    }
  }

  /// The snapshots on the device ([containerId]'s only, when given), with their actions.
  static Future<void> showSnapshots(
    BuildContext context, {
    String? containerId,
  }) async {
    List<Map<Object?, Object?>> snapshots;
    try {
      final state = await _channel.invokeMapMethod<Object?, Object?>(
        'snapshots',
      );
      snapshots = ((state?['snapshots'] as List?) ?? const [])
          .whereType<Map<Object?, Object?>>()
          .where((s) => containerId == null || s['container'] == containerId)
          .toList();
    } catch (e) {
      if (context.mounted) M3ESnackbar.show(context, message: message(e));
      return;
    }
    if (!context.mounted) return;
    final picked = await M3EBottomSheet.show<(String, Map<Object?, Object?>?)>(
      context,
      builder: (sheet) => SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(8, 0, 8, 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
              child: Text(
                text(sheet, 'スナップショット', 'Snapshots'),
                style: Theme.of(sheet).textTheme.titleLarge,
              ),
            ),
            if (snapshots.isEmpty)
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                child: Text(
                  text(sheet, 'まだスナップショットはありません', 'No snapshots yet'),
                ),
              ),
            for (final snapshot in snapshots)
              LinuxMenuRow(
                title: snapshot['name'] as String? ?? '',
                subtitle: [
                  DateTime.fromMillisecondsSinceEpoch(
                    (snapshot['created'] as num?)?.toInt() ?? 0,
                  ).toLocal().toString().substring(0, 16),
                  size((snapshot['size'] as num?) ?? 0),
                ].join(' · '),
                icon: M3EIcons.inventory_2_rounded,
                onTap: () => Navigator.pop(sheet, ('open', snapshot)),
              ),
            LinuxMenuRow(
              title: text(
                sheet,
                'バックアップファイルから復元',
                'Restore from a backup file',
              ),
              icon: M3EIcons.upload_file_rounded,
              onTap: () => Navigator.pop(sheet, ('import', null)),
            ),
          ],
        ),
      ),
    );
    if (picked == null || !context.mounted) return;
    if (picked.$1 == 'import') return importBackup(context);
    final snapshot = picked.$2!;
    final file = snapshot['file'] as String;
    final action = await M3EBottomSheet.show<String>(
      context,
      builder: (sheet) => Padding(
        padding: const EdgeInsets.fromLTRB(8, 0, 8, 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            LinuxMenuRow(
              title: text(sheet, '新しいコンテナとして復元', 'Restore as a new container'),
              icon: M3EIcons.restore_rounded,
              onTap: () => Navigator.pop(sheet, 'restore'),
            ),
            LinuxMenuRow(
              title: text(sheet, 'ファイルに書き出す', 'Export to a file'),
              subtitle: text(
                sheet,
                '別の端末で「バックアップファイルから復元」できます',
                'Another device can restore it from a backup file',
              ),
              icon: M3EIcons.save_alt_rounded,
              onTap: () => Navigator.pop(sheet, 'export'),
            ),
            LinuxMenuRow(
              title: text(sheet, '削除', 'Delete'),
              icon: M3EIcons.delete_forever_rounded,
              onTap: () => Navigator.pop(sheet, 'delete'),
            ),
          ],
        ),
      ),
    );
    if (action == null || !context.mounted) return;
    try {
      switch (action) {
        case 'restore':
          await _channel.invokeMethod('restoreSnapshot', {'file': file});
          if (!context.mounted) return;
          final job = await follow(
            context,
            text(context, 'スナップショットから復元しています', 'Restoring the snapshot'),
          );
          if (context.mounted) {
            report(
              context,
              job,
              text(context, '新しいコンテナとして復元しました', 'Restored as a new container'),
            );
          }
        case 'export':
          final uri = await _pick(
            context,
            create: true,
            name: file,
            mime: 'application/gzip',
          );
          if (uri == null || !context.mounted) return;
          await _channel.invokeMethod('exportSnapshot', {
            'file': file,
            'uri': uri,
          });
          if (!context.mounted) return;
          final job = await follow(
            context,
            text(context, '書き出しています', 'Exporting'),
          );
          if (context.mounted) {
            report(context, job, text(context, '書き出しました', 'Exported'));
          }
        case 'delete':
          await _channel.invokeMethod('deleteSnapshot', {'file': file});
          if (context.mounted) {
            M3ESnackbar.show(
              context,
              message: text(context, 'スナップショットを削除しました', 'Snapshot deleted'),
            );
          }
      }
    } catch (e) {
      if (context.mounted) M3ESnackbar.show(context, message: message(e));
    }
  }
}
