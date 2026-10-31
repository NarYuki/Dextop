import 'dart:async';

import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import 'linux_expressive.dart';
import 'linux_widgets.dart';

/// Linux settings → Compatibility and permissions: Android's limits and grants for Linux.
class LinuxCompatPanel extends StatefulWidget {
  const LinuxCompatPanel({super.key});

  @override
  State<LinuxCompatPanel> createState() => _LinuxCompatPanelState();
}

class _LinuxCompatPanelState extends State<LinuxCompatPanel>
    with WidgetsBindingObserver {
  static const _channel = MethodChannel('app.freedextop/linux');
  static const _usb = MethodChannel('app.freedextop/linux/usb');
  Map<Object?, Object?>? status;
  bool busy = false;
  String? error;

  String t(String ja, String en) => linuxText(context, ja, en);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
    // USB devices come and go while the page is open; Android reports each change.
    _usb.setMethodCallHandler((call) async {
      if (call.method == 'changed') await _load();
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _usb.setMethodCallHandler(null);
    super.dispose();
  }

  // Grants are changed in Android's own screens; read them again on return.
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _load();
  }

  Future<void> _load() async {
    try {
      final next = await _channel.invokeMapMethod<Object?, Object?>(
        'compatStatus',
      );
      if (mounted) setState(() => status = next);
    } on PlatformException catch (e) {
      if (mounted) setState(() => error = e.message);
    }
  }

  Future<void> _run(String method, [Map<String, Object?>? arguments]) async {
    setState(() {
      busy = true;
      error = null;
    });
    try {
      await _channel.invokeMethod<Object?>(method, arguments);
    } on PlatformException catch (e) {
      if (mounted) setState(() => error = e.message);
    } finally {
      if (mounted) setState(() => busy = false);
      await _load();
    }
  }

  Widget _state(bool ok) => Icon(
    ok ? M3EIcons.check_circle_rounded : M3EIcons.chevron_right_rounded,
    color: ok ? Theme.of(context).colorScheme.primary : null,
  );

  @override
  Widget build(BuildContext context) {
    final s = status;
    if (s == null) {
      return const Padding(
        padding: EdgeInsets.only(top: 24),
        child: M3EProgressIndicator.linearWavy(),
      );
    }
    final android = s['androidMode'] == true;
    final limited = s['phantomLimited'] == true;
    final camera = s['camera'] == true;
    final microphone = s['microphone'] == true;
    final foldersActive = s['foldersActive'] == true;
    final folders = (s['folders'] as List? ?? const [])
        .cast<Map<Object?, Object?>>();
    final policy = s['usbPolicy'] as String? ?? 'each';
    final devices = (s['usbDevices'] as List? ?? const [])
        .cast<Map<Object?, Object?>>();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (busy) const M3EProgressIndicator.linearWavy(),
        if (error != null)
          Padding(
            padding: const EdgeInsets.fromLTRB(8, 12, 8, 0),
            child: Text(
              error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ),
        // The limit and storage grants concern Linux running as this app; full privilege runs
        // as the shell user, which neither applies to.
        if (android) ...[
          LinuxSection(t('プロセス', 'Processes')),
          LinuxGroup(
            children: [
              M3EListItem(
                headline: t('子プロセス数の制限', 'Child process limit'),
                supportingText: limited
                    ? s['phantomFixable'] == true
                          ? t(
                              'Android がアプリの子プロセスを 32 個までに制限し、超えた分を終了させます。タップで解除します。',
                              'Android limits an app to 32 child processes and ends the rest. Tap to lift it.',
                            )
                          : t(
                              'Android がアプリの子プロセスを 32 個までに制限し、超えた分を終了させます。タップで開発者向けオプションを開き、「子プロセスの制限を無効にする」をオンにしてください。',
                              'Android limits an app to 32 child processes and ends the rest. Tap to open Developer options and turn on "Disable child process restrictions".',
                            )
                    : t(
                        '解除済み。Linux のプロセス数は制限されません。',
                        'Lifted. Linux processes are not limited.',
                      ),
                supportingMaxLines: null,
                leading: const Icon(M3EIcons.account_tree_rounded),
                trailing: _state(!limited),
                onTap: limited && !busy ? () => _run('liftPhantomLimit') : null,
              ),
            ],
          ),
          LinuxSection(t('ストレージ', 'Storage')),
          if (foldersActive) ...[
            LinuxGroup(
              children: [
                for (final folder in folders)
                  M3EListItem(
                    headline: folder['name'] as String? ?? '',
                    supportingText: folder['granted'] == true
                        ? folder['path'] as String? ?? ''
                        : t(
                            'Android の許可がなくなりました。削除して選び直してください。',
                            'Android no longer allows this folder. Remove it and pick it again.',
                          ),
                    supportingMaxLines: null,
                    leading: const Icon(M3EIcons.folder_rounded),
                    trailing: M3EIconButton(
                      variant: M3EIconButtonVariant.standard,
                      icon: const Icon(M3EIcons.delete_outline_rounded),
                      tooltip: t('削除', 'Remove'),
                      onPressed: busy
                          ? null
                          : () =>
                                _run('removeFolder', {'name': folder['name']}),
                    ),
                  ),
                M3EListItem(
                  headline: t('フォルダを追加', 'Add a folder'),
                  supportingText: t(
                    'Android のフォルダ選択画面で、Linux から使うフォルダを選びます。USB メモリのフォルダも選べます。',
                    'Pick a folder for Linux in Android\'s folder picker. Folders on USB drives work too.',
                  ),
                  supportingMaxLines: null,
                  leading: const Icon(M3EIcons.create_new_folder_rounded),
                  onTap: busy ? null : () => _run('addFolder'),
                ),
              ],
            ),
            _note(
              t(
                '選んだフォルダは Linux の /media/android/<名前> に表示されます。Dextop が中継するため直接のアクセスより遅く、libc を通さずにファイルを開くアプリでは中身を読み書きできません。',
                'Picked folders appear in Linux under /media/android/<name>. Dextop relays them, so they are slower than direct access, and apps that open files without libc cannot read or write their contents.',
              ),
            ),
          ],
          if (s['wholeStorageAvailable'] == true) ...[
            const SizedBox(height: 16),
            LinuxGroup(
              children: [
                M3EListItem(
                  headline: t('ストレージ全体', 'Whole storage'),
                  supportingText: s['wholeStorage'] == true
                      ? t(
                          '全ファイルへのアクセスを許可済み。内部ストレージと USB ストレージ全体が Linux の /media/storage に表示されます（Linux の再起動後）。',
                          'All files access is allowed. Internal and USB storage appear in Linux under /media/storage (after Linux restarts).',
                        )
                      : t(
                          'フォルダを選ぶ代わりに、内部ストレージと USB ストレージ全体を Linux の /media/storage から直接使えます。タップで全ファイルへのアクセスを許可します。',
                          'Instead of picking folders, use all internal and USB storage directly from Linux under /media/storage. Tap to allow All files access.',
                        ),
                  supportingMaxLines: null,
                  leading: const Icon(M3EIcons.sd_storage_rounded),
                  trailing: _state(s['wholeStorage'] == true),
                  onTap: busy ? null : () => _run('openStorageAccess'),
                ),
              ],
            ),
          ],
        ],
        LinuxSection(t('カメラとマイク', 'Camera and microphone')),
        LinuxGroup(
          children: [
            M3EListItem(
              headline: t('カメラ', 'Camera'),
              supportingText: camera
                  ? t('許可済み', 'Allowed')
                  : t(
                      'Linux アプリからカメラを使うために必要です。タップで許可します。',
                      'Needed for Linux apps to use the camera. Tap to allow.',
                    ),
              supportingMaxLines: null,
              leading: const Icon(M3EIcons.photo_camera_rounded),
              trailing: _state(camera),
              onTap: camera || busy
                  ? null
                  : () => _run('requestPermission', {'kind': 'camera'}),
            ),
            M3EListItem(
              headline: t('マイク', 'Microphone'),
              supportingText: microphone
                  ? t('許可済み', 'Allowed')
                  : t(
                      'Linux アプリから音声を入力するために必要です。タップで許可します。',
                      'Needed for Linux apps to record audio. Tap to allow.',
                    ),
              supportingMaxLines: null,
              leading: const Icon(M3EIcons.mic_rounded),
              trailing: _state(microphone),
              onTap: microphone || busy
                  ? null
                  : () => _run('requestPermission', {'kind': 'microphone'}),
            ),
          ],
        ),
        LinuxSection(t('USB 機器', 'USB devices')),
        if (!android) ...[
          LinuxGroup(
            children: [
              LinuxChoice<String>(
                value: 'all',
                groupValue: policy,
                enabled: !busy,
                onChanged: (v) => _run('setUsbPolicy', {'policy': v}),
                icon: M3EIcons.done_all_rounded,
                title: t('一律に許可', 'Allow all'),
                subtitle: t(
                  '接続した USB 機器を、確認なしで Linux から使えるようにします。',
                  'Every connected USB device is available to Linux without asking.',
                ),
              ),
              LinuxChoice<String>(
                value: 'each',
                groupValue: policy,
                enabled: !busy,
                onChanged: (v) => _run('setUsbPolicy', {'policy': v}),
                icon: M3EIcons.help_outline_rounded,
                title: t('都度許可', 'Ask each time'),
                subtitle: t(
                  '下の一覧でオンにした機器だけを使い、接続するたびに Android が確認します。',
                  'Only devices turned on below are used, and Android asks each time one is connected.',
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
        ],
        if (devices.isEmpty)
          LinuxGroup(
            children: [
              M3EListItem(
                headline: t('接続中の USB 機器はありません', 'No USB devices connected'),
                supportingText: t(
                  'USB 機器を接続すると、ここで Linux から使えるようにできます。',
                  'Connect a USB device to make it available to Linux here.',
                ),
                supportingMaxLines: null,
                leading: const Icon(M3EIcons.usb_rounded),
              ),
            ],
          )
        else
          LinuxGroup(
            children: [
              for (final device in devices)
                LinuxToggle(
                  value: device['enabled'] == true,
                  onChanged: busy
                      ? null
                      : (v) => _run('setUsbForLinux', {
                          'id': device['id'],
                          'enabled': v,
                        }),
                  icon: device['kind'] == 'serial'
                      ? M3EIcons.cable_rounded
                      : M3EIcons.usb_rounded,
                  title: device['name'] as String? ?? '',
                  subtitle: [
                    device['ids'] as String? ?? '',
                    device['kind'] == 'serial'
                        ? t('USB シリアル', 'USB serial')
                        : t('USB 機器', 'USB device'),
                    device['permission'] == true
                        ? t('アクセス許可済み', 'access granted')
                        : t('アクセス未許可', 'no access yet'),
                  ].join(' · '),
                ),
            ],
          ),
        _note(
          android
              ? t(
                  'Android 方式では、機器ごとに Android が使用の許可を確認します。',
                  'In the Android mode, Android asks before each device is used.',
                )
              : t(
                  '特権アクセスで Android の許可を自動で与えます。',
                  'Privileged access grants Android access automatically.',
                ),
        ),
      ],
    );
  }

  Widget _note(String text) => Padding(
    padding: const EdgeInsets.fromLTRB(8, 8, 8, 0),
    child: Text(text, style: Theme.of(context).textTheme.bodySmall),
  );
}
