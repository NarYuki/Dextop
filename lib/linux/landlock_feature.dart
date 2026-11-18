import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../expressive/dextop_expressive.dart';
import '../expressive/dextop_widgets.dart';
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
      // Unset means the default, the unprotected fast mode. An opt-in saved
      // before the modes existed (ON without a kind) stays protected.
      final enabled = prefs.getBool(fastModePreferenceKey);
      final kind = prefs.getString(fastModeKindPreferenceKey);
      mode = enabled == false
          ? LinuxFastMode.disabled
          : kind == 'unprotected' || (enabled == null && kind == null)
          ? LinuxFastMode.unprotected
          : LinuxFastMode.protected;
      loading = false;
    });
  }

  String _modeLabel(BuildContext context, LinuxFastMode value) =>
      linuxText(context, switch (value) {
        LinuxFastMode.disabled => 'uiFastModeDisabled',
        LinuxFastMode.protected => 'uiFastModeProtected',
        LinuxFastMode.unprotected => 'uiFastModeUnprotected',
      });

  String _modeSummary(BuildContext context, LinuxFastMode value) =>
      linuxText(context, switch (value) {
        LinuxFastMode.disabled => 'uiFastModeDisabledSummary',
        LinuxFastMode.protected => 'uiFastModeProtectedSummary',
        LinuxFastMode.unprotected => 'uiFastModeUnprotectedSummary',
      });

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
        M3ESnackbar.show(
          context,
          message: error is PlatformException
              ? error.message ??
                    linuxText(context, 'uiCannotSaveExperimentalFeature')
              : linuxText(context, 'uiCannotSaveExperimentalFeature'),
        );
      }
    } finally {
      await _load();
    }
  }

  Future<void> _chooseMode(BuildContext context) async {
    final selected = await M3EDialog.show<LinuxFastMode>(
      context,
      dialog: Builder(
        builder: (dialogContext) {
          final colors = Theme.of(dialogContext).colorScheme;
          return M3EDialog(
            icon: const Icon(M3EIcons.speed_rounded),
            title: linuxText(context, 'uiFastMode'),
            content: DextopGroup(
              color: colors.surfaceContainer,
              children: [
                for (final value in LinuxFastMode.values)
                  DextopChoice<LinuxFastMode>(
                    value: value,
                    groupValue: mode,
                    allowReselect: true,
                    title: _modeLabel(context, value),
                    subtitle: _modeSummary(context, value),
                    onChanged: (next) => Navigator.pop(dialogContext, next),
                  ),
              ],
            ),
          );
        },
      ),
    );
    if (selected != null && selected != mode) {
      await _update(selected);
    }
  }

  @override
  Widget build(BuildContext context) => EnsureDextopExpressive(
    child: DextopGroup(
      children: [
        M3EListItem(
          leading: const Icon(M3EIcons.speed_rounded),
          headline: linuxText(context, 'uiFastMode'),
          supportingText: _modeSummary(context, mode),
          supportingMaxLines: null,
          enabled: !loading,
          trailing: DextopDropdownButton(
            label: _modeLabel(context, mode),
            enabled: !loading,
            onPressed: loading ? null : () => _chooseMode(context),
          ),
          onTap: loading ? null : () => _chooseMode(context),
        ),
      ],
    ),
  );
}
