import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import '../l10n/inline_text.dart';
import 'linux_widgets.dart';

/// What the container's full desktop ("Open desktop") runs: Dextop's IceWM or the user's own
/// command (LinuxDesktopSessions).
class LinuxDesktopSessionSettings extends StatefulWidget {
  const LinuxDesktopSessionSettings({
    super.key,
    required this.settings,
    required this.enabled,
    required this.onSave,
  });

  /// The container's own settings ({"desktop", "desktopCommand"}).
  final Map<Object?, Object?>? settings;
  final bool enabled;
  final Future<void> Function(Map<String, Object?> update) onSave;

  @override
  State<LinuxDesktopSessionSettings> createState() =>
      _LinuxDesktopSessionSettingsState();
}

class _LinuxDesktopSessionSettingsState
    extends State<LinuxDesktopSessionSettings> {
  late final TextEditingController command = TextEditingController(
    text: widget.settings?['desktopCommand'] as String? ?? '',
  );

  @override
  void dispose() {
    command.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    String t(String key) => localText(context, key);
    final current = widget.settings?['desktop'] as String? ?? '';
    final saved = widget.settings?['desktopCommand'] as String? ?? '';
    void choose(String value) {
      if (value != current && widget.enabled) widget.onSave({'desktop': value});
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
