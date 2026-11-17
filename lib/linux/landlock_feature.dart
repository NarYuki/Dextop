import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../experimental_feature_style.dart';
import 'linux_expressive.dart';

// Keep the original enabled key so existing opt-ins survive the UI rename.
const fastModePreferenceKey = 'experimental_linux_landlock_fast_path';
const fastModeKindPreferenceKey = 'experimental_linux_fast_mode_kind';

enum LinuxFastMode { disabled, protected, unprotected }

/// App information → Experimental features. Saving does not start Linux.
class LinuxFastModeExperimentalTile extends StatefulWidget {
  const LinuxFastModeExperimentalTile({super.key, this.onChanged});
  final VoidCallback? onChanged;

  @override
  State<LinuxFastModeExperimentalTile> createState() =>
      _LinuxFastModeExperimentalTileState();
}

class _LinuxFastModeExperimentalTileState
    extends State<LinuxFastModeExperimentalTile> {
  static const channel = MethodChannel('app.freedextop/linux');
  bool loading = true;
  LinuxFastMode mode = LinuxFastMode.disabled;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final prefs = await SharedPreferences.getInstance();
    if (!mounted) return;
    setState(() {
      mode = prefs.getBool(fastModePreferenceKey) != true
          ? LinuxFastMode.disabled
          : prefs.getString(fastModeKindPreferenceKey) == 'unprotected'
          ? LinuxFastMode.unprotected
          : LinuxFastMode.protected;
      loading = false;
    });
  }

  Future<void> _update(LinuxFastMode value) async {
    setState(() => loading = true);
    try {
      final prefs = await SharedPreferences.getInstance();
      final savedMode = await prefs.setString(
        fastModeKindPreferenceKey,
        value.name,
      );
      final savedEnabled = await prefs.setBool(
        fastModePreferenceKey,
        value != LinuxFastMode.disabled,
      );
      if (!savedMode || !savedEnabled) {
        if (!mounted) return;
        throw PlatformException(
          code: 'preference',
          message: linuxText(context, 'uiCannotSaveExperimentalFeature'),
        );
      }
      await channel
          .invokeMethod<bool>('syncExperimentalFastMode')
          .timeout(const Duration(seconds: 8));
      if (mounted) widget.onChanged?.call();
    } on Exception catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              error is PlatformException
                  ? error.message ??
                        linuxText(context, 'uiCannotSaveExperimentalFeature')
                  : linuxText(context, 'uiCannotSaveExperimentalFeature'),
            ),
          ),
        );
      }
    } finally {
      await _load();
    }
  }

  @override
  Widget build(BuildContext context) => Card(
    child: ListTileTheme(
      data: experimentalFeatureTileTheme,
      child: ListTile(
        leading: const Icon(Icons.speed_rounded),
        title: Text(linuxText(context, 'uiFastMode')),
        subtitle: Text(
          linuxText(context, switch (mode) {
            LinuxFastMode.disabled => 'uiFastModeDisabledSummary',
            LinuxFastMode.protected => 'uiFastModeProtectedSummary',
            LinuxFastMode.unprotected => 'uiFastModeUnprotectedSummary',
          }),
        ),
        trailing: SizedBox(
          width: 132,
          child: DropdownButton<LinuxFastMode>(
            isExpanded: true,
            isDense: true,
            style: Theme.of(context).textTheme.bodyMedium,
            value: mode,
            underline: const SizedBox.shrink(),
            onChanged: loading
                ? null
                : (value) {
                    if (value != null) _update(value);
                  },
            items: [
              for (final value in LinuxFastMode.values)
                DropdownMenuItem(
                  value: value,
                  child: Text(
                    linuxText(context, switch (value) {
                      LinuxFastMode.disabled => 'uiFastModeDisabled',
                      LinuxFastMode.protected => 'uiFastModeProtected',
                      LinuxFastMode.unprotected => 'uiFastModeUnprotected',
                    }),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
            ],
          ),
        ),
      ),
    ),
  );
}
