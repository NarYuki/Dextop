import '../l10n/inline_text.dart';

import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import 'linux_expressive.dart';
import 'linux_widgets.dart';

/// How one Linux application runs: its display system, renderer and the OpenGL version it is
/// told. Applies from its next launch; "automatic" follows the container's settings.
class LinuxAppModePage extends StatefulWidget {
  const LinuxAppModePage({
    super.key,
    required this.container,
    required this.desktopId,
    required this.name,
  });

  final String container;
  final String desktopId;
  final String name;

  @override
  State<LinuxAppModePage> createState() => _LinuxAppModePageState();
}

class _LinuxAppModePageState extends State<LinuxAppModePage> {
  static const _channel = MethodChannel('app.freedextop/linux');
  Map<String, String> mode = const {};
  bool loaded = false;
  String? error;

  String t(String ja, String en) => linuxText(context, ja, en);

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final settings = await _channel.invokeMapMethod<Object?, Object?>(
        'settings',
      );
      final containers = settings?['containers'] as Map? ?? const {};
      final apps = (containers[widget.container] as Map?)?['apps'] as Map?;
      final current = apps?[widget.desktopId] as Map? ?? const {};
      if (mounted) {
        setState(() {
          mode = current.map((k, v) => MapEntry('$k', '$v'));
          loaded = true;
        });
      }
    } on PlatformException catch (e) {
      if (mounted) setState(() => error = e.message);
    }
  }

  Future<void> _set(String key, String value) async {
    final next = {...mode, key: value}..removeWhere((_, v) => v == 'auto');
    setState(() => mode = next);
    try {
      await _channel.invokeMapMethod<Object?, Object?>('setSettings', {
        'settings': {
          'container': widget.container,
          'app': widget.desktopId,
          'appSettings': next,
        },
      });
    } on PlatformException catch (e) {
      if (mounted) setState(() => error = e.message);
    }
  }

  Widget _group(
    String key,
    List<(String, String, String?, IconData)> options,
  ) => LinuxGroup(
    children: [
      for (final (value, title, subtitle, icon) in options)
        LinuxChoice<String>(
          value: value,
          groupValue: mode[key] ?? 'auto',
          enabled: loaded,
          onChanged: (v) => _set(key, v),
          icon: icon,
          title: title,
          subtitle: subtitle,
        ),
    ],
  );

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: M3EAppBar.top(
      titleText: t('動作方式', 'How it runs'),
      automaticallyImplyLeading: true,
    ),
    body: ListView(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 32),
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(8, 8, 8, 0),
          child: Text(
            inlineFormat(
              t(
                '{0} の起動方法です。次に起動したときから反映されます。「自動」はコンテナの設定に従います。',
                'How {0} starts. Applies from its next launch; "Automatic" follows the container\'s settings.',
              ),
              [widget.name],
            ),
          ),
        ),
        if (!loaded && error == null) const M3EProgressIndicator.linearWavy(),
        if (error != null)
          Padding(padding: const EdgeInsets.all(8), child: Text(error!)),
        LinuxSection(t('表示方式', 'Display system')),
        _group('display', [
          ('auto', t('自動', 'Automatic'), null, M3EIcons.auto_mode_rounded),
          (
            'x11',
            'X11',
            t(
              'Xwayland・Xvfb で表示します。Wayland で表示が崩れるアプリ向けです。',
              'Shown through Xwayland or Xvfb, for apps that misbehave on Wayland.',
            ),
            M3EIcons.web_asset_rounded,
          ),
          (
            'wayland',
            'Wayland',
            t(
              'Dextop の Wayland で直接表示します。対応しているアプリだけで使えます。',
              "Shown directly on Dextop's Wayland, for apps that support it.",
            ),
            M3EIcons.layers_rounded,
          ),
        ]),
        LinuxSection(t('描画方式', 'Renderer')),
        _group('renderer', [
          ('auto', t('自動', 'Automatic'), null, M3EIcons.auto_mode_rounded),
          (
            'software',
            t('ソフトウェア (llvmpipe)', 'Software (llvmpipe)'),
            t(
              'GPU を使わずに描画します。遅くなりますが OpenGL 4.5 まで正しく動きます。',
              'Draws without the GPU. Slower, but OpenGL up to 4.5 works correctly.',
            ),
            M3EIcons.memory_rounded,
          ),
        ]),
        LinuxSection(t('OpenGL のバージョン', 'OpenGL version')),
        _group('gl', [
          ('auto', t('自動', 'Automatic'), null, M3EIcons.auto_mode_rounded),
          (
            'override',
            t('新しいバージョンとして扱う', 'Report a newer version'),
            t(
              'OpenGL 4.6（VirGL では 4.3）のコアプロファイルとして扱います。Turnip（Zink）は GPU の制約で 4.1 までしか名乗れず、それより新しい版を求めるアプリが起動しないときに使います。',
              'Reports an OpenGL 4.6 core profile (4.3 with VirGL). Turnip (Zink) can only report 4.1 because of a GPU limit; use this when an app asking for a newer version does not start.',
            ),
            M3EIcons.upgrade_rounded,
          ),
        ]),
      ],
    ),
  );
}
