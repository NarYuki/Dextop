import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'l10n/app_localizations.dart';

/// Global Linux settings, stored by the shell-side Linux service.
class LinuxSettingsPage extends StatefulWidget {
  const LinuxSettingsPage({super.key});

  @override
  State<LinuxSettingsPage> createState() => _LinuxSettingsPageState();
}

class _LinuxSettingsPageState extends State<LinuxSettingsPage> {
  static const _channel = MethodChannel('app.freedextop/linux');

  Map<Object?, Object?>? settings;
  String? error;
  bool saving = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final result = await _channel.invokeMapMethod<Object?, Object?>(
        'settings',
      );
      if (mounted) setState(() => settings = result);
    } on PlatformException catch (exception) {
      if (mounted) setState(() => error = exception.message);
    }
  }

  Future<void> _setGpu(String gpu) async {
    setState(() => saving = true);
    try {
      final result = await _channel.invokeMapMethod<Object?, Object?>(
        'setSettings',
        {
          'settings': {'gpu': gpu},
        },
      );
      if (mounted) {
        setState(() {
          settings = result;
          error = null;
        });
      }
    } on PlatformException catch (exception) {
      if (mounted) setState(() => error = exception.message);
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final current = settings?['gpu'] as String? ?? 'virgl';
    final hardwareAvailable = settings?['hardwareAvailable'] as bool? ?? true;
    final running = (settings?['rendererRunning'] as List?)?.length ?? 0;
    return Scaffold(
      appBar: AppBar(title: Text(l.linuxSettingsTitle)),
      body: settings == null && error == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
              children: [
                if (error != null)
                  Card(
                    child: ListTile(
                      leading: const Icon(Icons.error_outline_rounded),
                      title: Text(error!),
                    ),
                  ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(4, 16, 4, 8),
                  child: Text(
                    l.linuxGpuSection,
                    style: theme.textTheme.titleSmall?.copyWith(
                      color: theme.colorScheme.primary,
                    ),
                  ),
                ),
                Card(
                  child: RadioGroup<String>(
                    groupValue: current,
                    onChanged: (value) {
                      if (value != null && value != current && !saving) {
                        _setGpu(value);
                      }
                    },
                    child: Column(
                      children: [
                        RadioListTile<String>(
                          value: 'virgl',
                          enabled: hardwareAvailable,
                          secondary: const Icon(Icons.memory_rounded),
                          title: Text(l.linuxGpuHardware),
                          subtitle: Text(
                            hardwareAvailable
                                ? l.linuxGpuHardwareDescription
                                : l.linuxGpuUnavailable,
                          ),
                        ),
                        RadioListTile<String>(
                          value: 'software',
                          secondary: const Icon(Icons.developer_board_rounded),
                          title: Text(l.linuxGpuSoftware),
                          subtitle: Text(l.linuxGpuSoftwareDescription),
                        ),
                      ],
                    ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(4, 8, 4, 0),
                  child: Text(
                    [
                      l.linuxGpuApplyNote,
                      if (current == 'virgl' && running > 0)
                        l.linuxGpuRunning(running),
                    ].join('\n'),
                    style: theme.textTheme.bodySmall,
                  ),
                ),
              ],
            ),
    );
  }
}
