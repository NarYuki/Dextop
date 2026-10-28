import 'dart:async';

import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import 'linux_expressive.dart';
import 'linux_widgets.dart';

/// Trust is explicit and separate from preparing tools in each container.
class LinuxStoreSourcesPanel extends StatefulWidget {
  const LinuxStoreSourcesPanel({super.key, required this.containers});
  final List<Map<Object?, Object?>> containers;

  @override
  State<LinuxStoreSourcesPanel> createState() => _LinuxStoreSourcesPanelState();
}

class _LinuxStoreSourcesPanelState extends State<LinuxStoreSourcesPanel> {
  static const channel = MethodChannel('app.freedextop/linux');
  Map<String, bool>? sources;
  final running = <String, String>{};
  Timer? timer;
  String? error;
  bool saving = false;
  bool polling = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final result = await channel.invokeMapMethod<String, bool>(
        'storeSources',
      );
      if (mounted) setState(() => sources = result ?? {'apt': true});
      await _poll();
    } catch (e) {
      if (mounted) setState(() => error = '$e');
    }
  }

  Future<void> _set(String source, bool value) async {
    setState(() => saving = true);
    try {
      final result = await channel.invokeMapMethod<String, bool>(
        'setStoreSource',
        {'source': source, 'enabled': value},
      );
      if (mounted) {
        setState(() {
          sources = result;
          error = null;
        });
      }
    } catch (e) {
      if (mounted) setState(() => error = '$e');
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  Future<void> _prepare(String id, String source) async {
    try {
      final ok = await channel.invokeMethod<bool>('storeSourcePrepare', {
        'id': id,
        'source': source,
      });
      if (ok != true) throw StateError('Preparation is already running');
      if (!mounted) return;
      setState(
        () => running['$id/$source'] = linuxText(
          context,
          '準備しています…',
          'Preparing…',
        ),
      );
      timer ??= Timer.periodic(const Duration(seconds: 2), (_) => _poll());
    } catch (e) {
      if (mounted) setState(() => error = '$e');
    }
  }

  Future<void> _poll() async {
    if (polling) return;
    polling = true;
    try {
      for (final container in widget.containers) {
        final id = container['id'] as String;
        final jobs =
            await channel.invokeListMethod<Object?>('storeSourcePrepareJobs', {
              'id': id,
            }) ??
            const [];
        if (!mounted) return;
        for (final raw in jobs) {
          final job = raw as Map;
          final name = (job['packages'] as List).first as String;
          final source = name.split(':').first;
          final key = '$id/$source';
          final state = job['state'];
          setState(() {
            if (state == 'running' || state == 'queued') {
              running[key] = (job['line'] as String?)?.isNotEmpty == true
                  ? job['line'] as String
                  : '$state…';
            } else {
              running.remove(key);
              error = state == 'done'
                  ? linuxText(
                      context,
                      '$source の準備が完了しました。Store を開き直してください。',
                      '$source is ready. Reopen the Store.',
                    )
                  : job['output'] as String? ?? 'Preparation failed';
            }
          });
        }
      }
      if (running.isNotEmpty) {
        timer ??= Timer.periodic(const Duration(seconds: 2), (_) => _poll());
      }
      if (running.isEmpty) {
        timer?.cancel();
        timer = null;
      }
    } catch (e) {
      if (mounted) setState(() => error = '$e');
    } finally {
      polling = false;
    }
  }

  @override
  void dispose() {
    timer?.cancel();
    super.dispose();
  }

  Future<void> _details(String title, String text) => M3EBottomSheet.show<void>(
    context,
    builder: (sheet) => SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(title, style: Theme.of(sheet).textTheme.titleLarge),
          const SizedBox(height: 16),
          SelectableText(text, style: Theme.of(sheet).textTheme.bodyMedium),
        ],
      ),
    ),
  );

  @override
  Widget build(BuildContext context) {
    String t(String ja, String en) => linuxText(context, ja, en);
    final aptContainers = widget.containers
        .where((c) => const ['debian', 'ubuntu'].contains(c['distro']))
        .toList();
    final enabled = [
      'deb-get',
      'flathub',
    ].where((name) => sources?[name] == true).toList();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        LinuxSection(t('ストアの配布元', 'Store sources')),
        if (sources == null) const M3EProgressIndicator.linearWavy(),
        if (sources != null)
          LinuxGroup(
            children: [
              for (final source in ['apt', 'deb-get', 'flathub'])
                LinuxToggle(
                  value: sources![source] == true,
                  onChanged: saving ? null : (value) => _set(source, value),
                  icon: switch (source) {
                    'apt' => M3EIcons.inventory_2_rounded,
                    'flathub' => M3EIcons.apps_rounded,
                    _ => M3EIcons.download_rounded,
                  },
                  title: switch (source) {
                    'apt' => 'Debian / Ubuntu APT',
                    'flathub' => 'Flathub',
                    _ => 'deb-get',
                  },
                  subtitle: switch (source) {
                    'apt' => t(
                      'ディストリビューションの公式パッケージ',
                      'Official distribution packages',
                    ),
                    'flathub' => t('Flatpak アプリ', 'Flatpak applications'),
                    _ => t(
                      '開発元が配布する DEB パッケージ',
                      'DEB packages from app developers',
                    ),
                  },
                ),
              M3EListItem(
                headline: t('配布元について', 'About sources'),
                leading: const Icon(M3EIcons.info_outline_rounded),
                trailing: const Icon(M3EIcons.chevron_right_rounded),
                onTap: () => _details(
                  t('配布元について', 'About sources'),
                  t(
                    '有効にした配布元のアプリを Store に表示します。Debian・Ubuntu に対応しています。\n\nAPT はディストリビューションの公式パッケージを掲載します。Ubuntu は main・universe に対応しています。\n\ndeb-get はコミュニティが管理する定義から開発元のパッケージを取得します。定義のバージョンを固定し、検証して使用します。\n\nFlathub は Flatpak アプリを配布します。名前空間を使えない端末では、試験的な互換実行が必要です。\n\n配布元への信頼は安全性の保証ではありません。無効にしてもインストール済みのアプリや追加済みの APT リポジトリは削除されません。',
                    'Enabled sources appear in the Store for Debian and Ubuntu.\n\nAPT lists official distribution packages, including Ubuntu main and universe.\n\ndeb-get fetches vendor packages using pinned, verified community definitions.\n\nFlathub distributes Flatpak apps. Devices without namespaces require experimental compatibility execution.\n\nTrust does not guarantee safety. Disabling a source does not remove installed apps or previously added APT repositories.',
                  ),
                ),
              ),
            ],
          ),
        if (enabled.isNotEmpty && aptContainers.isNotEmpty) ...[
          LinuxSection(t('配布元の準備', 'Source setup')),
          LinuxGroup(
            children: [
              for (final source in enabled)
                for (final container in aptContainers)
                  M3EListItem(
                    headline:
                        '${source == 'flathub' ? 'Flathub' : 'deb-get'} · ${container['name']}',
                    supportingText:
                        running.containsKey('${container['id']}/$source')
                        ? t('準備中・タップしてログを表示', 'Preparing · tap to view log')
                        : t(
                            '配布ツールをインストール・更新',
                            'Install or update source tools',
                          ),
                    leading: const Icon(M3EIcons.download_rounded),
                    trailing: running.containsKey('${container['id']}/$source')
                        ? const SizedBox(
                            width: 24,
                            height: 24,
                            child: M3EProgressIndicator.circularWavy(),
                          )
                        : const Icon(M3EIcons.chevron_right_rounded),
                    onTap: () {
                      final log = running['${container['id']}/$source'];
                      if (log != null) {
                        _details(source, log);
                      } else {
                        _prepare(container['id'] as String, source);
                      }
                    },
                  ),
            ],
          ),
        ],
        if (error != null)
          LinuxGroup(
            children: [
              M3EListItem(
                headline: t('配布元の準備結果', 'Source setup result'),
                supportingText: t('タップして詳細を表示', 'Tap to view details'),
                leading: const Icon(M3EIcons.info_outline_rounded),
                trailing: const Icon(M3EIcons.chevron_right_rounded),
                onTap: () => _details(t('ストアの配布元', 'Store sources'), error!),
              ),
            ],
          ),
      ],
    );
  }
}
