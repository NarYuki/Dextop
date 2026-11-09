import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'linux_expressive.dart';

/// Whether the user has read and accepted the full-privilege warning.
const _acceptedKey = 'linux_privileged_warning_accepted';

/// Whether full privilege can be chosen: Dextop's initial setup is complete and its
/// privileged access (Shizuku, Stellar or the built-in binder) is running, granted and
/// holds WRITE_SECURE_SETTINGS. Linux-only use leaves these unset.
Future<bool> linuxPrivilegeReady() async {
  final preferences = await SharedPreferences.getInstance();
  if (preferences.getBool('setup_completed') != true) return false;
  try {
    final status =
        await const MethodChannel('app.freedextop/display')
            .invokeMapMethod<String, dynamic>('status') ??
        const {};
    return status['distributionChannel'] == 'github' &&
        status['shizukuRunning'] == true &&
        status['shizukuGranted'] == true &&
        status['privileged'] == true;
  } on Exception {
    return false;
  }
}

/// Whether this build offers full privilege at all: the Google Play build never does.
Future<bool> linuxPrivilegedModeOffered() async {
  try {
    final status =
        await const MethodChannel('app.freedextop/display')
            .invokeMapMethod<String, dynamic>('status') ??
        const {};
    return status['distributionChannel'] == 'github';
  } on Exception {
    return false;
  }
}

/// Shown under the full-privilege choice while [linuxPrivilegeReady] is false.
String linuxPrivilegeUnavailableText(BuildContext context) => linuxText(
  context,
  'uiAvailableAfterDextopSInitialSetupIncludingPrivilegedAccessIsComplete',
);

Future<bool> linuxPrivilegedWarningAccepted() async =>
    (await SharedPreferences.getInstance()).getBool(_acceptedKey) == true;

/// Explains what running Linux with full privilege allows, for good and for bad, and asks the
/// user to confirm. Returns true when the user chose to use it.
Future<bool> showLinuxPrivilegedWarning(BuildContext context) async {
  String t(String key) => linuxText(context, key);
  final accepted = await showDialog<bool>(
    context: context,
    builder: (dialog) {
      final theme = Theme.of(dialog);
      return AlertDialog(
        icon: Icon(Icons.warning_amber_rounded, color: theme.colorScheme.error),
        title: Text(t('uiBeforeRunningWithFullPrivilege')),
        content: SingleChildScrollView(
          child: Text(
            t(
              'uiWithFullPrivilegeLinuxProgramsRunWithAndroidSElevatedPermissionsTheShellAccessThatUSBDebuggingGrants',
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialog, false),
            child: Text(t('uiCancel')),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialog, true),
            child: Text(t('uiIUnderstandUseIt')),
          ),
        ],
      );
    },
  );
  if (accepted == true) {
    await (await SharedPreferences.getInstance()).setBool(_acceptedKey, true);
  }
  return accepted == true;
}
