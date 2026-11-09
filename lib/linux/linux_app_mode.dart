import '../l10n/inline_text.dart';

import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import 'linux_expressive.dart';
import 'linux_widgets.dart';
import 'linux_scale_setting.dart';

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

  String t(String key) => linuxText(context, key);

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
      titleText: t('uiHowItRuns'),
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
                'uiHow0StartsAppliesFromItsNextLaunchAutomaticFollowsTheContainerSSettings',
              ),
              [widget.name],
            ),
          ),
        ),
        if (!loaded && error == null) const M3EProgressIndicator.linearWavy(),
        if (error != null)
          Padding(padding: const EdgeInsets.all(8), child: Text(error!)),
        LinuxSection(t('uiDisplayScale')),
        LinuxScaleSetting(
          value: int.tryParse(mode['uiScale'] ?? '') ?? 0,
          allowDefault: true,
          enabled: loaded,
          t: t,
          onChanged: (value) => _set('uiScale', value == 0 ? 'auto' : '$value'),
        ),
        LinuxSection(t('uiDisplaySystem')),
        _group('display', [
          ('auto', t('uiAutomatic'), null, M3EIcons.auto_mode_rounded),
          (
            'x11',
            'X11',
            t('uiShownThroughXwaylandOrXvfbForAppsThatMisbehaveOnWayland'),
            M3EIcons.web_asset_rounded,
          ),
          (
            'wayland',
            'Wayland',
            t('uiShownDirectlyOnDextopSWaylandForAppsThatSupportIt'),
            M3EIcons.layers_rounded,
          ),
          (
            'gamescope',
            t('uiGPUWindowGamescope'),
            t(
              'uiRunsAnX11AppOnGamescopeSXwaylandAndHandsItsFramesToDextopOnTheGPUWithNoCPUCopyForLargeWindowsAndFull',
            ),
            M3EIcons.bolt_rounded,
          ),
        ]),
        LinuxSection(t('uiRenderer')),
        _group('renderer', [
          ('auto', t('uiAutomatic'), null, M3EIcons.auto_mode_rounded),
          (
            'software',
            t('uiSoftwareLlvmpipe'),
            t('uiDrawsWithoutTheGPUSlowerButOpenGLUpTo45WorksCorrectly'),
            M3EIcons.memory_rounded,
          ),
        ]),
        LinuxSection(t('uiOpenGLVersion')),
        _group('gl', [
          ('auto', t('uiAutomatic'), null, M3EIcons.auto_mode_rounded),
          (
            'override',
            t('uiReportANewerVersion'),
            t(
              'uiReportsAnOpenGL46CoreProfile43WithVirGLTurnipZinkCanOnlyReport41BecauseOfAGPULimitUseThisWhenAnAppAs',
            ),
            M3EIcons.upgrade_rounded,
          ),
        ]),
      ],
    ),
  );
}
