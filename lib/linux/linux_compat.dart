import 'dart:async';

import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import 'linux_expressive.dart';
import 'linux_widgets.dart';

/// Linux settings → Compatibility and permissions: Android's limits and grants for Linux.
class LinuxCompatPanel extends StatefulWidget {
  const LinuxCompatPanel({
    super.key,
    this.containerId,
    this.processes = true,
    this.devices = true,
  });

  final String? containerId;

  /// Android's app-wide child process limit.
  final bool processes;

  /// Storage, camera, microphone and USB sharing of the selected container.
  final bool devices;

  @override
  State<LinuxCompatPanel> createState() => _LinuxCompatPanelState();
}

class _LinuxCompatPanelState extends State<LinuxCompatPanel>
    with WidgetsBindingObserver {
  static const _channel = MethodChannel('app.freedextop/linux');
  static const _usb = MethodChannel('app.freedextop/linux/usb');
  Map<Object?, Object?>? status;
  List<Map<Object?, Object?>> containers = const [];
  String? selectedId;
  bool busy = false;
  String? error;

  String t(String key) => linuxText(context, key);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    selectedId = widget.containerId;
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
      final list =
          (await _channel.invokeListMethod<Object?>('list') ?? const [])
              .whereType<Map<Object?, Object?>>()
              .where((c) => c['state'] == 'installed')
              .toList();
      if (!mounted) return;
      containers = list;
      if (!list.any((c) => c['id'] == selectedId)) {
        selectedId = widget.containerId != null || list.isEmpty
            ? null
            : list.first['id'] as String;
      }
      if (selectedId == null) {
        setState(() => status = const {});
        return;
      }
      final id = selectedId;
      final next = await _channel.invokeMapMethod<Object?, Object?>(
        'compatStatus',
        {'id': id},
      );
      if (selectedId != id) return;
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
      await _channel.invokeMethod<Object?>(method, {
        'id': selectedId,
        ...?arguments,
      });
    } on PlatformException catch (e) {
      if (mounted) setState(() => error = e.message);
    } finally {
      if (mounted) setState(() => busy = false);
      await _load();
    }
  }

  Future<void> _toggle(String kind, bool enabled) async {
    if (!enabled) {
      await _run('setContainerGrant', {'kind': kind, 'enabled': false});
      return;
    }
    if (kind == 'wholeStorage' && !await _confirmWholeStorage()) return;
    await _run('setContainerGrant', {'kind': kind, 'enabled': true});
    if (!mounted || error != null) return;
    if (kind == 'wholeStorage') {
      if (status?['wholeStorage'] != true) await _run('openStorageAccess');
    } else if (status?[kind] != true) {
      await _run('requestPermission', {'kind': kind});
    }
  }

  /// What sharing the whole storage hands to Linux, asked before it is turned on and before
  /// Android's "All files access" screen opens. True when the user goes on.
  Future<bool> _confirmWholeStorage() async {
    String t(String key) => linuxText(context, key);
    final accepted = await showDialog<bool>(
      context: context,
      builder: (dialog) {
        final theme = Theme.of(dialog);
        return AlertDialog(
          icon: Icon(
            Icons.warning_amber_rounded,
            color: theme.colorScheme.error,
          ),
          title: Text(t('uiBeforeSharingTheWholeStorage')),
          content: SingleChildScrollView(
            child: Text(t('uiWholeStorageWarning')),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialog, false),
              child: Text(t('uiCancel')),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(dialog, true),
              child: Text(t('uiContinueToAllowAccess')),
            ),
          ],
        );
      },
    );
    return accepted == true;
  }

  Widget _state(bool ok) => Icon(
    ok ? M3EIcons.check_circle_rounded : M3EIcons.chevron_right_rounded,
    color: ok ? Theme.of(context).colorScheme.primary : null,
  );

  @override
  Widget build(BuildContext context) {
    final s = status;
    if (s == null) {
      if (error != null) return Text(error!);
      return const Padding(
        padding: EdgeInsets.only(top: 24),
        child: M3EProgressIndicator.linearWavy(),
      );
    }
    if (selectedId == null) return const SizedBox.shrink();
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
        if (widget.containerId == null && widget.devices) ...[
          // Everything below belongs to the chosen container; keep it apart from the app-wide items above.
          Padding(
            padding: const EdgeInsets.only(top: 24),
            child: Divider(
              height: 1,
              thickness: 1,
              color: Theme.of(context).colorScheme.outline,
            ),
          ),
          LinuxSection(t('uiContainer')),
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: AbsorbPointer(
              absorbing: busy,
              child: M3EDropdownMenu<String>(
                singleSelect: true,
                showChipAnimation: false,
                items: [
                  for (final container in containers)
                    M3EDropdownItem(
                      value: container['id'] as String,
                      label: container['name'] as String? ??
                          container['id'] as String,
                      selected: container['id'] == selectedId,
                    ),
                ],
                fieldStyle: M3EDropdownFieldStyle(
                  hintText: t('uiContainer'),
                  prefixIcon: const Icon(M3EIcons.folder_rounded),
                ),
                onSelectionChanged: (items) {
                  if (items.isEmpty || items.first.value == selectedId) return;
                  setState(() {
                    selectedId = items.first.value;
                    status = null;
                  });
                  _load();
                },
              ),
            ),
          ),
        ],
        if (busy) const M3EProgressIndicator.linearWavy(),
        if (error != null)
          Padding(
            padding: const EdgeInsets.fromLTRB(8, 12, 8, 0),
            child: Text(
              error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ),
        if (widget.devices) _note(t('uiContainerSharingScope')),
        // Android's process limit is app-wide; sharing below belongs to the selected container.
        if (android && widget.processes) ...[
          LinuxSection(t('uiProcesses')),
          LinuxGroup(
            children: [
              M3EListItem(
                headline: t('uiChildProcessLimit'),
                supportingText: limited
                    ? s['phantomFixable'] == true
                          ? t(
                              'uiAndroidLimitsAnAppTo32ChildProcessesAndEndsTheRestTapToLiftIt',
                            )
                          : t(
                              'uiAndroidLimitsAnAppTo32ChildProcessesAndEndsTheRestTapToOpenDeveloperOptionsAndTurnOnDisableChildProc',
                            )
                    : t('uiLiftedLinuxProcessesAreNotLimited'),
                supportingMaxLines: null,
                leading: const Icon(M3EIcons.account_tree_rounded),
                trailing: _state(!limited),
                onTap: limited && !busy ? () => _run('liftPhantomLimit') : null,
              ),
            ],
          ),
        ],
        if (widget.devices) ...[
        LinuxSection(t('uiStorage')),
        if (foldersActive && s['wholeStorage'] != true) ...[
          LinuxGroup(
            children: [
              for (final folder in folders)
                M3EListItem(
                  headline: folder['name'] as String? ?? '',
                  supportingText: folder['granted'] == true
                      ? folder['path'] as String? ?? ''
                      : t(
                          'uiAndroidNoLongerAllowsThisFolderRemoveItAndPickItAgain',
                        ),
                  supportingMaxLines: null,
                  leading: const Icon(M3EIcons.folder_rounded),
                  trailing: M3EIconButton(
                    variant: M3EIconButtonVariant.standard,
                    icon: const Icon(M3EIcons.delete_outline_rounded),
                    tooltip: t('uiRemove'),
                    onPressed: busy
                        ? null
                        : () => _run('removeFolder', {'name': folder['name']}),
                  ),
                ),
              M3EListItem(
                headline: t('uiAddAFolder'),
                supportingText: t(
                  'uiPickAFolderForLinuxInAndroidSFolderPickerFoldersOnUSBDrivesWorkToo',
                ),
                supportingMaxLines: null,
                leading: const Icon(M3EIcons.create_new_folder_rounded),
                onTap: busy ? null : () => _run('addFolder'),
              ),
            ],
          ),
          _note(
            t(
              'uiPickedFoldersAppearInLinuxUnderMediaAndroidNameDextopRelaysThemSoTheyAreSlowerThanDirectAccessAndApp',
            ),
          ),
        ],
        if (s['wholeStorageAvailable'] == true) ...[
          const SizedBox(height: 16),
          LinuxGroup(
            children: [
              M3EListItem(
                headline: t('uiWholeStorage'),
                supportingText: s['wholeStorage'] == true
                    ? t('uiContainerStorageEnabled')
                    : t('uiContainerStorageDisabled'),
                supportingMaxLines: null,
                leading: const Icon(M3EIcons.sd_storage_rounded),
                trailing: _state(s['wholeStorage'] == true),
                onTap: busy
                    ? null
                    : () => _toggle('wholeStorage', s['wholeStorage'] != true),
              ),
            ],
          ),
        ],
        LinuxSection(t('uiCameraAndMicrophone')),
        LinuxGroup(
          children: [
            M3EListItem(
              headline: t('uiCamera'),
              supportingText: camera
                  ? t('uiContainerSharingEnabled')
                  : t('uiNeededForLinuxAppsToUseTheCameraTapToAllow'),
              supportingMaxLines: null,
              leading: const Icon(M3EIcons.photo_camera_rounded),
              trailing: _state(camera),
              onTap: busy ? null : () => _toggle('camera', !camera),
            ),
            M3EListItem(
              headline: t('uiMicrophone'),
              supportingText: microphone
                  ? t('uiContainerSharingEnabled')
                  : t('uiNeededForLinuxAppsToRecordAudioTapToAllow'),
              supportingMaxLines: null,
              leading: const Icon(M3EIcons.mic_rounded),
              trailing: _state(microphone),
              onTap: busy ? null : () => _toggle('microphone', !microphone),
            ),
          ],
        ),
        LinuxSection(t('uiUSBDevices')),
        if (!android) ...[
          LinuxGroup(
            children: [
              LinuxChoice<String>(
                value: 'all',
                groupValue: policy,
                enabled: !busy,
                onChanged: (v) => _run('setUsbPolicy', {'policy': v}),
                icon: M3EIcons.done_all_rounded,
                title: t('uiAllowAll'),
                subtitle: t(
                  'uiEveryConnectedUSBDeviceIsAvailableToLinuxWithoutAsking',
                ),
              ),
              LinuxChoice<String>(
                value: 'each',
                groupValue: policy,
                enabled: !busy,
                onChanged: (v) => _run('setUsbPolicy', {'policy': v}),
                icon: M3EIcons.help_outline_rounded,
                title: t('uiAskEachTime'),
                subtitle: t(
                  'uiOnlyDevicesTurnedOnBelowAreUsedAndAndroidAsksEachTimeOneIsConnected',
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
                headline: t('uiNoUSBDevicesConnected'),
                supportingText: t(
                  'uiConnectAUSBDeviceToMakeItAvailableToLinuxHere',
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
                      : device['kind'] == 'fido'
                      ? M3EIcons.key_rounded
                      : device['kind'] == 'storage'
                      ? M3EIcons.storage_rounded
                      : M3EIcons.usb_rounded,
                  title: device['name'] as String? ?? '',
                  subtitle: [
                    device['ids'] as String? ?? '',
                    device['kind'] == 'serial'
                        ? t('uiUSBSerial')
                        : device['kind'] == 'fido'
                        ? t('uiSecurityKeyFIDO')
                        : device['kind'] == 'storage'
                        ? t('uiUSBStorageDevSdX')
                        : t('uiUSBDevice'),
                    device['permission'] == true
                        ? t('uiAccessGranted')
                        : t('uiNoAccessYet'),
                  ].join(' · '),
                ),
            ],
          ),
        _note(
          android
              ? t('uiInTheAndroidModeAndroidAsksBeforeEachDeviceIsUsed')
              : t('uiPrivilegedAccessGrantsAndroidAccessAutomatically'),
        ),
        ],
      ],
    );
  }

  Widget _note(String text) => Padding(
    padding: const EdgeInsets.fromLTRB(8, 8, 8, 0),
    child: Text(text, style: Theme.of(context).textTheme.bodySmall),
  );
}
