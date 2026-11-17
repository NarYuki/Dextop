import 'dart:async';

import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import '../l10n/inline_text.dart';
import 'linux_progress_dialog.dart';
import 'linux_widgets.dart';

/// What the container's full desktop ("Open desktop") runs: Dextop's IceWM, KDE Plasma
/// (experimental, installed when chosen through the Store's apt job) or the user's own command
/// (LinuxDesktopSessions).
class LinuxDesktopSessionSettings extends StatefulWidget {
  const LinuxDesktopSessionSettings({
    super.key,
    required this.containerId,
    required this.settings,
    required this.enabled,
    required this.onSave,
  });

  final String containerId;

  /// The container's own settings ({"desktop", "desktopCommand", "plasmaInstalled", ...}).
  final Map<Object?, Object?>? settings;
  final bool enabled;
  final Future<void> Function(Map<String, Object?> update) onSave;

  @override
  State<LinuxDesktopSessionSettings> createState() =>
      _LinuxDesktopSessionSettingsState();
}

class _LinuxDesktopSessionSettingsState
    extends State<LinuxDesktopSessionSettings> {
  static const _channel = MethodChannel('app.freedextop/linux');

  /// Whether KDE Plasma (experimental) is offered as a desktop session.
  static const _plasmaOffered = false;
  late final TextEditingController command = TextEditingController(
    text: widget.settings?['desktopCommand'] as String? ?? '',
  );

  List<String> get _plasmaPackages =>
      ((widget.settings?['plasmaPackages'] as List?) ?? const [])
          .cast<String>();

  @override
  void dispose() {
    command.dispose();
    super.dispose();
  }

  /// Choosing Plasma while it is missing asks to install it first; it is chosen once installed.
  Future<void> _choosePlasma() async {
    if (widget.settings?['plasmaInstalled'] == true) {
      await widget.onSave({'desktop': 'plasma'});
      return;
    }
    if (await _confirmInstall() != true || !mounted) return;
    final error = await _install();
    if (!mounted) return;
    if (error == null) {
      await widget.onSave({'desktop': 'plasma'});
    } else {
      M3ESnackbar.show(context, message: error);
    }
  }

  /// The same dialog as choosing a privileged service: full-width filled buttons, one per answer.
  Future<bool?> _confirmInstall() {
    String t(String key) => localText(context, key);
    return showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (dialog) => PopScope(
        canPop: false,
        child: AlertDialog(
          icon: const Icon(M3EIcons.desktop_windows_rounded),
          title: Text(t('uiInstallKdePlasma')),
          content: Text(t('uiKdePlasmaIsNotInstalledInThisContainer')),
          actionsPadding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
          actions: [
            Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                FilledButton(
                  onPressed: () => Navigator.pop(dialog, true),
                  child: Text(t('uiInstall')),
                ),
                const SizedBox(height: 10),
                FilledButton(
                  onPressed: () => Navigator.pop(dialog, false),
                  child: Text(t('uiDoNotInstall')),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  /// Whether apt itself is in trouble (a cut-off dpkg run, broken dependencies) that the
  /// Store's repair fixes; a held lock is not, the job waits for it.
  Future<bool> _aptNeedsRepair() async {
    try {
      final list =
          await _channel.invokeListMethod<Object?>('aptDiagnose', {
            'id': widget.containerId,
          }) ??
          const [];
      return list.cast<Map<Object?, Object?>>().any(
        (issue) => (issue['kind'] as String? ?? '') != 'locked',
      );
    } on PlatformException {
      return false;
    }
  }

  /// Runs the Store's apt job in the progress dialog; null when it succeeded, else the reason.
  /// apt trouble is repaired first, as the Store does, without asking again (installing was
  /// agreed); a run that fails with apt in trouble is repaired and run once more.
  Future<String?> _install() async {
    final failed = localText(context, 'uiInstallFailed');
    final title = localText(context, 'uiInstallingKdePlasma');
    final job = ValueNotifier<Map<Object?, Object?>?>(null);
    BuildContext? dialogContext;
    final shown = Completer<void>();
    final dialog = LinuxProgressDialog.show<void>(
      context,
      dialog: Builder(
        builder: (context) {
          dialogContext = context;
          if (!shown.isCompleted) shown.complete();
          return ValueListenableBuilder<Map<Object?, Object?>?>(
            valueListenable: job,
            builder: (context, value, _) {
              final percent = (value?['percent'] as num?)?.toDouble() ?? -1;
              final line = value?['line'] as String? ?? '';
              final detail = line.isEmpty
                  ? localText(context, 'uiPreparing')
                  : percent >= 0
                  ? '${percent.round()}% · $line'
                  : line;
              return LinuxProgressDialog(
                title: title,
                icon: M3EIcons.desktop_windows_rounded,
                detail: detail,
              );
            },
          );
        },
      ),
    );
    await shown.future;

    /// One apt run; the finished job, or null when it could not start (reason in [startError]).
    String? startError;
    Future<List<Map<Object?, Object?>>> plasmaJobs() async {
      final list =
          await _channel.invokeListMethod<Object?>('storeJobs', {
            'id': widget.containerId,
            'peek': true,
          }) ??
          const [];
      return [
        for (final item in list.cast<Map<Object?, Object?>>())
          if (((item['packages'] as List?) ?? const []).cast<String>().join(
                ' ',
              ) ==
              _plasmaPackages.join(' '))
            item,
      ];
    }

    Future<Map<Object?, Object?>?> attempt(bool repair) async {
      job.value = null;
      // storeRun replaces an earlier Plasma job of the same packages before it returns.
      try {
        final started = await _channel.invokeMethod<bool>('storeRun', {
          'id': widget.containerId,
          'action': 'install',
          'packages': _plasmaPackages,
          'repair': repair,
        });
        if (started != true) {
          startError = failed;
          return null;
        }
      } on PlatformException catch (e) {
        startError = e.message ?? failed;
        return null;
      }
      while (true) {
        await Future<void>.delayed(const Duration(seconds: 2));
        try {
          final jobs = await plasmaJobs();
          if (jobs.isEmpty) {
            // The Store page collected it when it finished; whether Plasma is there is read again.
            final seen = job.value;
            if (seen != null) return {...seen, 'state': 'done'};
            continue;
          }
          final item = jobs.last;
          job.value = item;
          final state = item['state'] as String? ?? '';
          if (state != 'running' && state != 'queued') return item;
        } on PlatformException catch (_) {}
      }
    }

    var repair = await _aptNeedsRepair();
    var result = await attempt(repair);
    if (result != null &&
        result['state'] != 'done' &&
        !repair &&
        await _aptNeedsRepair()) {
      repair = true;
      result = await attempt(true);
    }
    final closing = dialogContext;
    if (closing != null && closing.mounted && Navigator.of(closing).canPop()) {
      Navigator.of(closing).pop();
    }
    await dialog;
    job.dispose();
    if (result == null) return startError ?? failed;
    if (result['state'] == 'done') return null;
    final output = (result['output'] as String? ?? '').trim();
    return output.isEmpty ? failed : output.split('\n').last;
  }

  @override
  Widget build(BuildContext context) {
    String t(String key) => localText(context, key);
    final current = widget.settings?['desktop'] as String? ?? '';
    final saved = widget.settings?['desktopCommand'] as String? ?? '';
    void choose(String value) {
      if (value == current || !widget.enabled) return;
      if (value == 'plasma') {
        _choosePlasma();
      } else {
        widget.onSave({'desktop': value});
      }
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        LinuxSection(t('uiDesktopSession')),
        LinuxGroup(
          children: [
            LinuxChoice<String>(
              value: '',
              groupValue: current,
              enabled: widget.enabled,
              onChanged: choose,
              icon: M3EIcons.desktop_windows_rounded,
              title: t('uiDesktopSessionIcewm'),
              subtitle: t('uiDesktopSessionIcewmDescription'),
            ),
            // KDE Plasma is not offered for now; a container that already chose it still sees it.
            if (_plasmaOffered || current == 'plasma')
              LinuxChoice<String>(
                value: 'plasma',
                groupValue: current,
                enabled: widget.enabled,
                onChanged: choose,
                icon: M3EIcons.desktop_windows_rounded,
                title: t('uiDesktopSessionPlasma'),
              ),
            LinuxChoice<String>(
              value: 'custom',
              groupValue: current,
              enabled: widget.enabled,
              onChanged: choose,
              icon: M3EIcons.terminal_rounded,
              title: t('uiDesktopSessionCustom'),
              subtitle: saved.isEmpty ? null : saved,
            ),
          ],
        ),
        if (current == 'custom')
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
            child: M3ETextField(
              variant: M3ETextFieldVariant.outlined,
              controller: command,
              enabled: widget.enabled,
              label: t('uiDesktopSessionCommand'),
              supportingText: t('uiDesktopSessionCommandHelp'),
            ),
          ),
        if (current == 'custom')
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
            child: Align(
              alignment: AlignmentDirectional.centerEnd,
              child: M3EButton.filled(
                onPressed: widget.enabled
                    ? () => widget.onSave({'desktopCommand': command.text})
                    : null,
                label: Text(t('uiSave')),
              ),
            ),
          ),
      ],
    );
  }
}
