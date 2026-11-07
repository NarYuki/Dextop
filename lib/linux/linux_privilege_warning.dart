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
    return status['shizukuRunning'] == true &&
        status['shizukuGranted'] == true &&
        status['privileged'] == true;
  } on Exception {
    return false;
  }
}

/// Shown under the full-privilege choice while [linuxPrivilegeReady] is false.
String linuxPrivilegeUnavailableText(BuildContext context) => linuxText(
  context,
  'Dextop の初期セットアップ（特権アクセスの設定）を完了すると選べます。',
  "Available after Dextop's initial setup, including privileged access, is complete.",
);

Future<bool> linuxPrivilegedWarningAccepted() async =>
    (await SharedPreferences.getInstance()).getBool(_acceptedKey) == true;

/// Explains what running Linux with full privilege allows, for good and for bad, and asks the
/// user to confirm. Returns true when the user chose to use it.
Future<bool> showLinuxPrivilegedWarning(BuildContext context) async {
  String t(String ja, String en) => linuxText(context, ja, en);
  final accepted = await showDialog<bool>(
    context: context,
    builder: (dialog) {
      final theme = Theme.of(dialog);
      return AlertDialog(
        icon: Icon(Icons.warning_amber_rounded, color: theme.colorScheme.error),
        title: Text(t('完全特権で実行する前に', 'Before running with full privilege')),
        content: SingleChildScrollView(
          child: Text(
            t(
              '完全特権では、Linux のプログラムが Android の上位権限（USB デバッグと同等のシェル権限）で動きます。'
                  '通常のアプリ権限では動かない、デバイスの深い機能を使うアプリやツールも実行できるようになります。\n\n'
                  'その一方で、悪意のあるプログラムがコンテナに紛れ込んだ場合（パッケージ、スクリプト、AppImage、ゲームなど）、'
                  'その上位権限を使って、あなたのデバイスで次のようなことをされるおそれがあります。\n'
                  '・アプリの無断インストールや削除\n'
                  '・画面の撮影や、タップ・キー入力の操作\n'
                  '・他のアプリのログやストレージ内のファイルの読み取り\n'
                  '・システム設定の変更\n'
                  '・Linux のキーリングに保存したパスワードや、ほかの特権アプリからの盗み見\n\n'
                  '信頼できる配布元のソフトウェアだけを入れてください。出どころの分からないソフトウェアを試すときは、'
                  'Android（通常のアプリ権限）での実行をおすすめします。',
              'With full privilege, Linux programs run with Android\'s elevated permissions (the shell '
                  'access that USB debugging grants). Apps and tools that need deeper access to the device, '
                  'which cannot run with ordinary app permissions, become possible.\n\n'
                  'At the same time, if a malicious program gets into the container (a package, a script, '
                  'an AppImage, a game…), it could use those permissions to cause security problems on your '
                  'device, such as:\n'
                  '• installing or removing apps without asking\n'
                  '• capturing the screen, or tapping and typing for you\n'
                  '• reading other apps\' logs and files in your storage\n'
                  '• changing system settings\n'
                  '• reading passwords stored in the Linux keyring, or being watched by other privileged apps\n\n'
                  'Install software only from sources you trust. To try software of unknown origin, run '
                  'Linux as Android (ordinary app permissions) instead.',
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialog, false),
            child: Text(t('キャンセル', 'Cancel')),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialog, true),
            child: Text(t('理解して使用する', 'I understand, use it')),
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
