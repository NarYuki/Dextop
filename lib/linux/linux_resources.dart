import 'dart:async';

import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import '../l10n/inline_text.dart';
import 'linux_expressive.dart';
import 'linux_widgets.dart';

/// Linux settings → Resources: what Linux uses (CPU, memory, GPU, storage) and runs (a task
/// manager), the hardware it may reach, and the containers' and GPU drivers' details.
class LinuxResourcesPanel extends StatefulWidget {
  const LinuxResourcesPanel({super.key});

  @override
  State<LinuxResourcesPanel> createState() => _LinuxResourcesPanelState();
}

class _LinuxResourcesPanelState extends State<LinuxResourcesPanel> {
  static const _channel = MethodChannel('app.freedextop/linux');
  Map<Object?, Object?>? status;
  List<Map<Object?, Object?>> containers = const [];
  String? error;
  Timer? poll;
  bool showAll = false;

  String t(String ja, String en) => linuxText(context, ja, en);

  @override
  void initState() {
    super.initState();
    _load();
    // CPU use is measured between two reads.
    poll = Timer.periodic(const Duration(seconds: 2), (_) => _load());
  }

  @override
  void dispose() {
    poll?.cancel();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final next = await _channel.invokeMapMethod<Object?, Object?>(
        'resources',
      );
      final list = await _channel.invokeListMethod<Object?>('list') ?? const [];
      if (mounted) {
        setState(() {
          status = next;
          containers = list.cast<Map<Object?, Object?>>();
          error = null;
        });
      }
    } on PlatformException catch (e) {
      if (mounted) setState(() => error = e.message);
    }
  }

  String _bytes(num? value) {
    if (value == null || value < 0) return t('計測中…', 'Measuring…');
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    var v = value.toDouble();
    var i = 0;
    while (v >= 1024 && i < units.length - 1) {
      v /= 1024;
      i++;
    }
    return '${v.toStringAsFixed(v >= 100 || i == 0 ? 0 : 1)} ${units[i]}';
  }

  String _containerName(String id) =>
      containers.firstWhere(
            (c) => c['id'] == id,
            orElse: () => const {},
          )['name']
          as String? ??
      id;

  Future<void> _stop(Map<Object?, Object?> process) async {
    final ok = await M3EDialog.show<bool>(
      context,
      dialog: Builder(
        builder: (dialog) => M3EDialog(
          icon: const Icon(M3EIcons.stop_circle_rounded),
          title: inlineFormat(t('{0} を終了しますか？', 'End {0}?'), [process['name']]),
          content: Text(
            t(
              'このプロセスと、そこから起動したプロセスを強制終了します。保存していない作業は失われます。',
              'Force-stops this process and the ones it started. Unsaved work is lost.',
            ),
          ),
          actions: [
            M3EButton.text(
              onPressed: () => Navigator.pop(dialog, false),
              child: Text(t('キャンセル', 'Cancel')),
            ),
            M3EButton.filled(
              onPressed: () => Navigator.pop(dialog, true),
              child: Text(t('終了', 'End')),
            ),
          ],
        ),
      ),
    );
    if (ok != true) return;
    try {
      await _channel.invokeMethod<bool>('stopProcess', {
        'pid': process['pid'],
        'start': process['start'],
      });
    } on PlatformException catch (e) {
      if (mounted) setState(() => error = e.message);
    }
    await _load();
  }

  Widget _row(IconData icon, String title, String detail, {Widget? trailing}) =>
      M3EListItem(
        headline: title,
        supportingText: detail,
        supportingMaxLines: null,
        leading: Icon(icon),
        trailing: trailing,
      );

  Widget _meter(double fraction) => SizedBox(
    width: 72,
    child: LinearProgressIndicator(value: fraction.clamp(0, 1).toDouble()),
  );

  String _yes(bool value) =>
      value ? t('対応', 'Supported') : t('非対応', 'Not supported');

  @override
  Widget build(BuildContext context) {
    final s = status;
    if (s == null) {
      return error != null
          ? Padding(padding: const EdgeInsets.all(16), child: Text(error!))
          : const Padding(
              padding: EdgeInsets.only(top: 24),
              child: M3EProgressIndicator.linearWavy(),
            );
    }
    final cores = (s['cores'] as num?)?.toInt() ?? 1;
    final cpu = (s['cpu'] as num?)?.toDouble() ?? 0;
    final memory = (s['memory'] as num?)?.toInt() ?? 0;
    final memoryTotal = (s['memoryTotal'] as num?)?.toInt() ?? 1;
    final gpuBusy = (s['gpuBusy'] as num?)?.toInt() ?? -1;
    final storageFree = (s['storageFree'] as num?)?.toInt() ?? -1;
    final storageTotal = (s['storageTotal'] as num?)?.toInt() ?? -1;
    final info = (s['containers'] as List? ?? const [])
        .cast<Map<Object?, Object?>>();
    final used = info.fold<int>(
      0,
      (sum, c) => sum + ((c['size'] as num?)?.toInt() ?? 0).clamp(0, 1 << 62),
    );
    final processes =
        (s['processes'] as List? ?? const []).cast<Map<Object?, Object?>>()
          ..sort(
            (a, b) =>
                ((b['cpu'] as num?) ?? 0).compareTo((a['cpu'] as num?) ?? 0),
          );
    final shown = processes
        .where((p) => showAll || p['plumbing'] != true)
        .toList();
    final compat = s['compat'] as Map? ?? const {};
    final usb = (s['usbDevices'] as List? ?? const [])
        .cast<Map<Object?, Object?>>();
    final folders = (s['folders'] as List? ?? const [])
        .cast<Map<Object?, Object?>>();
    final controllers = (s['controllers'] as List? ?? const []).cast<String>();
    final android = s['executionMode'] == 'android';
    final gpuModel = s['gpuModel'] as String? ?? '';
    final turnip = s['turnipSupported'] == true;
    final virgl = s['virglAvailable'] == true;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (error != null)
          Padding(
            padding: const EdgeInsets.fromLTRB(8, 12, 8, 0),
            child: Text(
              error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ),
        LinuxSection(t('使用状況', 'Usage')),
        LinuxGroup(
          children: [
            _row(
              M3EIcons.memory_rounded,
              'CPU',
              inlineFormat(
                t('Linux 全体で {0}%（{1} コア）', 'Linux in total {0}% ({1} cores)'),
                [cpu.toStringAsFixed(0), cores],
              ),
              trailing: _meter(cpu / (cores * 100)),
            ),
            _row(
              M3EIcons.storage_rounded,
              t('メモリ', 'Memory'),
              inlineFormat(t('Linux {0} / 端末 {1}', 'Linux {0} / device {1}'), [
                _bytes(memory),
                _bytes(memoryTotal),
              ]),
              trailing: _meter(memory / memoryTotal),
            ),
            _row(
              M3EIcons.developer_board_rounded,
              'GPU',
              gpuBusy < 0
                  ? t(
                      'この端末では使用率を読み取れません',
                      'This device does not report its load',
                    )
                  : inlineFormat(t('使用率 {0}%', 'Load {0}%'), [gpuBusy]),
              trailing: gpuBusy < 0 ? null : _meter(gpuBusy / 100),
            ),
            _row(
              M3EIcons.sd_storage_rounded,
              t('ストレージ', 'Storage'),
              inlineFormat(
                t(
                  'Linux 環境 {0} · 空き {1} / {2}',
                  'Linux environments {0} · free {1} of {2}',
                ),
                [_bytes(used), _bytes(storageFree), _bytes(storageTotal)],
              ),
              trailing: storageTotal > 0
                  ? _meter(1 - storageFree / storageTotal)
                  : null,
            ),
          ],
        ),
        LinuxSection(t('プロセス', 'Processes')),
        LinuxGroup(
          children: [
            if (shown.isEmpty)
              _row(
                M3EIcons.check_circle_rounded,
                t('実行中のプロセスはありません', 'No processes running'),
                '',
              ),
            for (final p in shown.take(60))
              M3EListItem(
                headline: p['name'] as String? ?? '?',
                supportingText: inlineFormat(
                  t(
                    '{0} · CPU {1}% · メモリ {2} · PID {3}',
                    '{0} · CPU {1}% · memory {2} · PID {3}',
                  ),
                  [
                    _containerName(p['container'] as String? ?? ''),
                    ((p['cpu'] as num?) ?? 0).toStringAsFixed(1),
                    _bytes(p['memory'] as num?),
                    p['pid'],
                  ],
                ),
                supportingMaxLines: 2,
                leading: const Icon(M3EIcons.terminal_rounded),
                trailing: M3EIconButton(
                  variant: M3EIconButtonVariant.standard,
                  icon: const Icon(M3EIcons.close_rounded),
                  tooltip: t('終了', 'End'),
                  onPressed: () => _stop(p),
                ),
              ),
            LinuxToggle(
              value: showAll,
              onChanged: (v) => setState(() => showAll = v),
              icon: M3EIcons.visibility_rounded,
              title: t('PRoot も表示', 'Show PRoot too'),
              subtitle: t(
                'Linux を動かしている PRoot 自体のプロセスも一覧に出します',
                'Lists the PRoot processes that run Linux as well',
              ),
            ),
          ],
        ),
        LinuxSection(t('ハードウェア', 'Hardware')),
        LinuxGroup(
          children: [
            _row(
              M3EIcons.photo_camera_rounded,
              t('カメラ', 'Camera'),
              compat['camera'] == true
                  ? t('Linux から使えます', 'Available to Linux')
                  : t('許可されていません', 'Not allowed'),
            ),
            _row(
              M3EIcons.mic_rounded,
              t('マイク', 'Microphone'),
              compat['microphone'] == true
                  ? t('Linux から使えます', 'Available to Linux')
                  : t('許可されていません', 'Not allowed'),
            ),
            _row(
              M3EIcons.folder_rounded,
              t('ストレージ', 'Storage'),
              !android
                  ? t(
                      'ストレージ全体を /media/android で使えます',
                      'The whole storage under /media/android',
                    )
                  : [
                      if (compat['wholeStorage'] == true)
                        t(
                          'ストレージ全体を /media/storage で使えます',
                          'The whole storage under /media/storage',
                        ),
                      if (folders.isNotEmpty)
                        inlineFormat(t('選んだフォルダ {0} 個', '{0} picked folders'), [
                          folders.length,
                        ]),
                      if (compat['wholeStorage'] != true && folders.isEmpty)
                        t('許可されていません', 'Not allowed'),
                    ].join(' · '),
            ),
            _row(
              M3EIcons.sports_esports_rounded,
              t('コントローラー', 'Controllers'),
              controllers.isEmpty
                  ? t('接続されていません', 'None connected')
                  : controllers.join(', '),
            ),
            for (final d in usb)
              _row(
                d['kind'] == 'serial'
                    ? M3EIcons.cable_rounded
                    : M3EIcons.usb_rounded,
                d['name'] as String? ?? 'USB',
                [
                  d['ids'] as String? ?? '',
                  d['enabled'] == true && d['permission'] == true
                      ? t('Linux から使えます', 'Available to Linux')
                      : d['enabled'] == true
                      ? t('Android の許可待ち', 'Waiting for Android')
                      : t('Linux には渡していません', 'Not given to Linux'),
                ].join(' · '),
              ),
            if (usb.isEmpty)
              _row(
                M3EIcons.usb_rounded,
                t('USB 機器', 'USB devices'),
                t('接続されていません', 'None connected'),
              ),
          ],
        ),
        LinuxSection(t('コンテナ', 'Containers')),
        LinuxGroup(
          children: [
            for (final c in containers)
              () {
                final id = c['id'] as String? ?? '';
                final detail = info.firstWhere(
                  (i) => i['id'] == id,
                  orElse: () => const {},
                );
                final options = c['setupOptions'] as Map? ?? const {};
                return _row(
                  M3EIcons.inventory_2_rounded,
                  c['name'] as String? ?? id,
                  [
                    '${c['distro'] ?? '?'} · $id',
                    inlineFormat(t('状態: {0}', 'State: {0}'), [
                      c['runtimeState'] ?? c['state'] ?? '?',
                    ]),
                    inlineFormat(t('表示方式: {0}', 'Display: {0}'), [
                      options['displayBackend'] == 'wayland'
                          ? 'Wayland'
                          : 'Xvfb',
                    ]),
                    inlineFormat(t('描画: {0}', 'Rendering: {0}'), [
                      c['gpuEffective'] ?? detail['gpuMode'] ?? '?',
                    ]),
                    inlineFormat(t('ウィンドウマネージャー: {0}', 'Window manager: {0}'), [
                      detail['windowManager'] ?? '?',
                    ]),
                    inlineFormat(t('使用容量: {0}', 'Size: {0}'), [
                      _bytes(detail['size'] as num?),
                    ]),
                  ].join('\n'),
                );
              }(),
          ],
        ),
        LinuxSection(t('ドライバ', 'Drivers')),
        LinuxGroup(
          children: [
            _row(
              M3EIcons.phone_android_rounded,
              s['device'] as String? ?? '',
              [
                s['android'] ?? '',
                if ((s['soc'] as String? ?? '').isNotEmpty) 'SoC: ${s['soc']}',
                'GPU: ${gpuModel.isEmpty ? t('不明（Adreno 以外）', 'unknown (not Adreno)') : gpuModel}',
                inlineFormat(t('実行方式: {0}', 'Execution mode: {0}'), [
                  android ? 'Android' : t('完全特権', 'Full privilege'),
                ]),
              ].join('\n'),
            ),
            _row(
              M3EIcons.bolt_rounded,
              'Turnip (Zink)',
              '${s['turnipPackage'] ?? ''} · ${_yes(turnip)}',
            ),
            _row(
              M3EIcons.layers_rounded,
              'VirGL',
              '${s['virglVersion'] ?? ''} · ${_yes(virgl)}',
            ),
            _row(
              M3EIcons.computer_rounded,
              t('ソフトウェア (llvmpipe)', 'Software (llvmpipe)'),
              [
                ...{
                  for (final c in info)
                    if ((c['mesa'] as String? ?? '').isNotEmpty)
                      'Mesa ${c['mesa']}',
                },
                _yes(true),
              ].join(' · '),
            ),
          ],
        ),
      ],
    );
  }
}
