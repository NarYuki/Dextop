import '../l10n/inline_text.dart';

import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import 'linux_backups.dart';

/// Where a container's storage goes, and removing its caches.
class LinuxStorageUi {
  static const _channel = MethodChannel('app.freedextop/linux');

  static String _partName(BuildContext context, String id) {
    String t(String key) => LinuxBackupsUi.text(context, key);
    return switch (id) {
      'apt' => t('uiAptPackageDownloads'),
      'pacman' => t('uiPacmanPackageDownloads'),
      'dnf' => t('uiDnfCache'),
      'steamCache' => t('uiSteamWebCache'),
      'steamDownloads' => t('uiSteamDownloadsInProgress'),
      'steamShaders' => t('uiSteamShaderCache'),
      'userCache' => t('uiUserCacheCache'),
      'tmp' => t('uiTemporaryFilesTmp'),
      'varTmp' => t('uiTemporaryFilesVarTmp'),
      'steamGames' => t('uiSteamGames'),
      'steam' => t('uiSteamClientAndRuntimes'),
      'flatpak' => t('uiFlatpakUser'),
      'flatpakSystem' => t('uiFlatpakSystem'),
      'appImages' => t('uiAppImages'),
      'home' => t('uiHomeFiles'),
      'gaming' => t('uiGamingCompatibilityFEXProton'),
      'opt' => t('uiOptGPUDriversAndMore'),
      'fonts' => t('uiFonts'),
      'usr' => t('uiInstalledSoftwareUsr'),
      'var' => t('uiPackageDataAndMoreVar'),
      _ => t('uiOther'),
    };
  }

  /// Measures [containerId], shows the parts, and removes caches when asked ([stopped] only).
  static Future<void> show(
    BuildContext context,
    String containerId, {
    required bool stopped,
  }) async {
    String t(String key) => LinuxBackupsUi.text(context, key);
    try {
      await _channel.invokeMethod('containerUsage', {'id': containerId});
      if (!context.mounted) return;
      final job = await LinuxBackupsUi.follow(context, t('uiMeasuringStorage'));
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
          message: t('uiStopTheContainerFirstForceStopa1cc9d2c'),
        );
        return;
      }
      await _channel.invokeMethod('cleanContainer', {'id': containerId});
      if (!context.mounted) return;
      final cleaned = await LinuxBackupsUi.follow(
        context,
        t('uiRemovingCaches'),
      );
      if (!context.mounted) return;
      final freed = ((cleaned?['result'] as Map?)?['freed'] as num?) ?? 0;
      LinuxBackupsUi.report(
        context,
        cleaned,
        inlineFormat(t('ui0Freed'), [LinuxBackupsUi.size(freed)]),
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
        String t(String key) => LinuxBackupsUi.text(sheet, key);
        final theme = Theme.of(sheet);
        return Padding(
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(t('uiStorageUse'), style: theme.textTheme.titleLarge),
              const SizedBox(height: 4),
              Text(
                [
                  inlineFormat(t('uiTotal0'), [LinuxBackupsUi.size(total)]),
                  if (snapshots > 0)
                    inlineFormat(t('uiSnapshots0KeptApart'), [
                      LinuxBackupsUi.size(snapshots),
                    ]),
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
                        t('uiRemovable'),
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
                    child: Text(t('uiClose')),
                  ),
                  const SizedBox(width: 8),
                  M3EButton.filled(
                    onPressed: cache > 0
                        ? () => Navigator.pop(sheet, true)
                        : null,
                    child: Text(
                      inlineFormat(t('uiRemoveCaches0'), [
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
    String t(String key) => LinuxBackupsUi.text(context, key);
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
                    inlineFormat(t('uiTotal01Items'), [
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
                  TextButton(
                    onPressed: _load,
                    child: Text(t('uiRetry9da540e4')),
                  ),
                ],
              ),
            ),
          if (!loading && error == null && entries.isEmpty)
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text(t('uiNoFilesBelongToThisCategoryHere')),
            ),
          Expanded(
            child: ListView.builder(
              itemCount: entries.length + (offset < count ? 1 : 0),
              itemBuilder: (context, index) {
                if (index == entries.length) {
                  return TextButton(
                    onPressed: loading ? null : _load,
                    child: Text(t('uiLoadMore')),
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
                                child: Text(t('uiClose')),
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
