import 'dart:async';

import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import 'linux_expressive.dart';
import 'linux_widgets.dart';

/// Trust is explicit and separate from preparing tools in each container.
class LinuxStoreSourcesPanel extends StatefulWidget {
  const LinuxStoreSourcesPanel({
    super.key,
    required this.containers,
    required this.flatpakCompatibility,
    required this.onFlatpakCompatibility,
  });
  final List<Map<Object?, Object?>> containers;
  final bool flatpakCompatibility;
  final ValueChanged<bool> onFlatpakCompatibility;

  @override
  State<LinuxStoreSourcesPanel> createState() => _LinuxStoreSourcesPanelState();
}

class _LinuxStoreSourcesPanelState extends State<LinuxStoreSourcesPanel> {
  static const channel = MethodChannel('app.freedextop/linux');
  Map<String, bool>? sources;

  /// Per container: which sources have their tools installed.
  final ready = <String, Map<String, bool>>{};
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
      await _checkReady();
    } catch (e) {
      if (mounted) setState(() => error = '$e');
    }
  }

  Future<void> _checkReady() async {
    for (final container in _aptContainers) {
      final id = container['id'] as String;
      try {
        final result = await channel.invokeMapMethod<String, Object?>(
          'storeSourceReady',
          {'id': id},
        );
        if (mounted && result != null) {
          setState(
            () => ready[id] = {
              for (final e in result.entries) e.key: e.value == true,
            },
          );
        }
      } catch (_) {}
    }
  }

  List<Map<Object?, Object?>> get _aptContainers => widget.containers
      .where((c) => const ['debian', 'ubuntu'].contains(c['distro']))
      .toList();

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
                  ? null
                  : job['output'] as String? ?? 'Preparation failed';
              if (state == 'done') unawaited(_checkReady());
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
    final containers = _aptContainers;

    /// One row per Debian/Ubuntu environment: whether [source] is set up there.
    List<Widget> setupRows(String source) => [
      for (final container in containers)
        () {
          final id = container['id'] as String;
          final key = '$id/$source';
          final busy = running.containsKey(key);
          final done = ready[id]?[source] == true;
          return M3EListItem(
            supportingMaxLines: null,
            headline: container['name'] as String? ?? id,
            supportingText: busy
                ? running[key]
                : done
                ? t('準備済み・タップで更新', 'Ready · tap to update')
                : t('未準備・タップで準備', 'Not set up · tap to set up'),
            leading: Icon(
              done ? M3EIcons.check_circle_rounded : M3EIcons.download_rounded,
            ),
            trailing: busy
                ? const SizedBox(
                    width: 24,
                    height: 24,
                    child: M3EProgressIndicator.circularWavy(),
                  )
                : const Icon(M3EIcons.chevron_right_rounded),
            onTap: busy
                ? () => _details(source, running[key]!)
                : () => _prepare(id, source),
          );
        }(),
    ];

    Widget note(String text) => Padding(
      padding: const EdgeInsets.fromLTRB(8, 8, 8, 0),
      child: Text(text, style: Theme.of(context).textTheme.bodySmall),
    );

    LinuxToggle toggle(
      String source,
      IconData icon,
      String title,
      String subtitle,
    ) => LinuxToggle(
      value: sources![source] == true,
      onChanged: saving ? null : (value) => _set(source, value),
      icon: icon,
      title: title,
      subtitle: subtitle,
    );

    if (sources == null) {
      return const Padding(
        padding: EdgeInsets.only(top: 16),
        child: M3EProgressIndicator.linearWavy(),
      );
    }
    final debGet = sources!['deb-get'] == true;
    final flathub = sources!['flathub'] == true;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        LinuxSection(t('配布元', 'Sources')),
        LinuxGroup(
          children: [
            toggle(
              'apt',
              M3EIcons.inventory_2_rounded,
              'Debian / Ubuntu APT',
              t(
                'ディストリビューションの公式パッケージ。準備は不要です',
                'Official distribution packages; no setup needed',
              ),
            ),
            toggle(
              'deb-get',
              M3EIcons.download_rounded,
              'deb-get',
              t('開発元が配布する DEB パッケージ', 'DEB packages from app developers'),
            ),
            toggle(
              'flathub',
              M3EIcons.apps_rounded,
              'Flathub',
              t('Flatpak アプリ', 'Flatpak applications'),
            ),
          ],
        ),
        note(
          t(
            'deb-get と Flathub は、Linux 環境ごとにツールの準備が必要です。',
            'deb-get and Flathub need their tools set up in each Linux environment.',
          ),
        ),
        if (debGet && containers.isNotEmpty) ...[
          LinuxSection(t('deb-get の準備', 'deb-get setup')),
          LinuxGroup(children: setupRows('deb-get')),
        ],
        if (flathub && containers.isNotEmpty) ...[
          LinuxSection(t('Flathub の準備', 'Flathub setup')),
          LinuxGroup(children: setupRows('flathub')),
        ],
        if (flathub) ...[
          LinuxSection(t('Flatpak の実行', 'Running Flatpak apps')),
          LinuxGroup(
            children: [
              LinuxToggle(
                value: widget.flatpakCompatibility,
                onChanged: widget.onFlatpakCompatibility,
                icon: M3EIcons.science_rounded,
                title: t('互換実行（試験的）', 'Compatibility execution (experimental)'),
                subtitle: t(
                  '名前空間が使えない端末で proot 経由で起動します。サンドボックスは適用されず、ソフトウェア描画になります。',
                  'Runs apps through proot without namespaces. No sandbox; software rendering.',
                ),
              ),
            ],
          ),
        ],
        if (error != null) ...[
          const SizedBox(height: 16),
          LinuxGroup(
            children: [
              M3EListItem(
                supportingMaxLines: null,
                headline: t('準備に失敗しました', 'Setup failed'),
                supportingText: t('タップして詳細を表示', 'Tap to view details'),
                leading: const Icon(M3EIcons.error_outline_rounded),
                trailing: const Icon(M3EIcons.chevron_right_rounded),
                onTap: () => _details(t('Store', 'Store'), error!),
              ),
            ],
          ),
        ],
        LinuxSection(t('その他', 'More')),
        LinuxGroup(
          children: [
            M3EListItem(
              headline: t('配布元について', 'About sources'),
              leading: const Icon(M3EIcons.info_outline_rounded),
              trailing: const Icon(M3EIcons.chevron_right_rounded),
              onTap: () => _details(
                t('配布元について', 'About sources'),
                t(
                  '有効にした配布元のアプリを Store に表示します。Debian・Ubuntu に対応しています。\n\nAPT はディストリビューションの公式パッケージを掲載します。\n\ndeb-get と Flathub は Linux 環境ごとにツールの準備が必要です。未準備のまま Store を開くと、最初にセットアップ画面が出ます。\n\n配布元への信頼は安全性の保証ではありません。無効にしてもインストール済みのアプリは削除されません。',
                  'Enabled sources appear in the Store for Debian and Ubuntu.\n\nAPT lists official distribution packages.\n\ndeb-get and Flathub need their tools in each Linux environment. Opening the Store before that shows a setup screen first.\n\nTrust does not guarantee safety. Disabling a source does not remove installed apps.',
                ),
              ),
            ),
          ],
        ),
      ],
    );
  }
}
