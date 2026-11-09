import 'dart:async';

import '../l10n/inline_text.dart';

import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import 'linux_expressive.dart';
import 'linux_widgets.dart';

class LinuxIntegrationsPanel extends StatefulWidget {
  const LinuxIntegrationsPanel({
    super.key,
    required this.containers,
    this.features = const {'ssh', 'vnc', 'calendar', 'services', 'printing'},
    this.selectedFeature,
  });
  final List<Map<Object?, Object?>> containers;
  final Set<String> features;
  final String? selectedFeature;

  @override
  State<LinuxIntegrationsPanel> createState() => _LinuxIntegrationsPanelState();
}

class _LinuxIntegrationsPanelState extends State<LinuxIntegrationsPanel> {
  static const channel = MethodChannel('app.freedextop/linux');
  String? id;
  String? feature;
  Map<Object?, Object?>? status;
  String? error;
  bool busy = false;
  bool loading = false;
  bool sshLan = false;
  bool vncLan = false;
  bool passwordAuth = true;
  bool calendarGranted = false;
  List<Map<Object?, Object?>> calendars = [];
  final selectedCalendars = <String>{};
  final sshPort = TextEditingController(text: '2222');
  final vncPort = TextEditingController(text: '5900');
  final calendarPort = TextEditingController(text: '8585');
  final printPort = TextEditingController(text: '8631');
  final sshKeys = TextEditingController();
  final vncPassword = TextEditingController();
  final filter = TextEditingController();
  Timer? timer;
  String? initialized;
  String t(String key) => linuxText(context, key);
  Map<Object?, Object?> state(String name) =>
      (status?[name] as Map?)?.cast<Object?, Object?>() ?? {};
  bool running(String name) => state(name)['running'] == true;

  @override
  void initState() {
    super.initState();
    feature = widget.selectedFeature;
    id = widget.containers.firstOrNull?['id'] as String?;
    _load();
    if (feature == 'calendar') _calendars();
    timer = Timer.periodic(const Duration(seconds: 5), (_) {
      if (!busy) _load();
    });
  }

  @override
  void dispose() {
    timer?.cancel();
    for (final controller in [
      sshPort,
      vncPort,
      calendarPort,
      printPort,
      sshKeys,
      vncPassword,
      filter,
    ]) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _load() async {
    if (id == null || loading) return;
    final target = id;
    loading = true;
    try {
      final value = await channel.invokeMapMethod<Object?, Object?>(
        feature == 'services' ? 'integrationAction' : 'integrationStatus',
        {
          'id': target,
          if (feature == 'services') 'request': {'op': 'services-ready'},
        },
      );
      if (!mounted || target != id) return;
      setState(() {
        status = value;
        if (initialized != target) {
          initialized = target;
          sshPort.text = '${state('ssh')['port'] ?? 2222}';
          vncPort.text = '${state('vnc')['port'] ?? 5900}';
          calendarPort.text = '${state('calendar')['port'] ?? 8585}';
          printPort.text = '${state('printing')['port'] ?? 8631}';
          sshLan = state('ssh')['lan'] == true;
          vncLan = state('vnc')['lan'] == true;
          passwordAuth = state('ssh')['password'] != false;
          sshKeys.text = state('ssh')['keys'] as String? ?? '';
          vncPassword.text = state('vnc')['secret'] as String? ?? '';
        }
      });
    } catch (e) {
      if (mounted && target == id) {
        setState(() => error = e is PlatformException ? e.message : '$e');
      }
    } finally {
      loading = false;
    }
  }

  Future<void> _calendars({bool request = false}) async {
    final target = id;
    try {
      final result = await channel.invokeMapMethod<Object?, Object?>(
        'calendarList',
        {'id': target, 'request': request},
      );
      if (!mounted || target != id) return;
      setState(() {
        calendarGranted = result?['granted'] == true;
        calendars = ((result?['calendars'] as List?) ?? [])
            .whereType<Map>()
            .map((row) => row.cast<Object?, Object?>())
            .toList();
        selectedCalendars
          ..clear()
          ..addAll(((result?['selected'] as List?) ?? []).whereType<String>());
      });
    } catch (e) {
      if (mounted && target == id) {
        setState(() => error = e is PlatformException ? e.message : '$e');
      }
    }
  }

  Future<Map<Object?, Object?>?> _action(Map<String, Object?> request) async {
    if (busy || id == null) return null;
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final value = await channel.invokeMapMethod<Object?, Object?>(
        'integrationAction',
        {'id': id, 'request': request},
      );
      if (!mounted) return value;
      if (request['action'] != 'journal') setState(() => status = value);
      return value;
    } catch (e) {
      if (mounted) {
        setState(() => error = e is PlatformException ? e.message : '$e');
      }
      return null;
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  int _port(TextEditingController controller) {
    final value = int.tryParse(controller.text);
    if (value == null || value < 1024 || value > 65535) {
      throw FormatException(t('uiUseAPortFrom1024To65535'));
    }
    return value;
  }

  /// VNC on the network: no encryption and short passwords, so the user confirms it.
  Future<bool> _confirmVncLan() async =>
      await showDialog<bool>(
        context: context,
        builder: (dialog) => AlertDialog(
          icon: const Icon(Icons.warning_amber_rounded),
          title: Text(t('uiOpenVNCToTheNetwork')),
          content: Text(
            t(
              'uiVNCTrafficIsNotEncryptedAndItsPasswordIsAtMost8CharactersPeopleOnTheSameNetworkPublicWiFiForExampleC',
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialog, false),
              child: Text(t('uiCancel')),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(dialog, true),
              child: Text(t('uiOpenIt')),
            ),
          ],
        ),
      ) ==
      true;

  Future<void> _start(String name, {bool saveOnly = false}) async {
    try {
      final request = <String, Object?>{
        'op': saveOnly ? 'save' : 'start',
        'feature': name,
      };
      switch (name) {
        case 'ssh':
          request.addAll({
            'port': _port(sshPort),
            'lan': sshLan,
            'password': passwordAuth,
            'keys': sshKeys.text,
          });
        case 'vnc':
          request.addAll({
            'port': _port(vncPort),
            'lan': vncLan,
            'secret': vncPassword.text,
          });
        case 'calendar':
          if (!saveOnly && (!calendarGranted || selectedCalendars.isEmpty)) {
            throw FormatException(t('uiChooseCalendarsToShare'));
          }
          request.addAll({
            'port': _port(calendarPort),
            'calendarIds': selectedCalendars.toList(),
          });
        case 'printing':
          request['port'] = _port(printPort);
      }
      await _action(request);
    } catch (e) {
      if (mounted) setState(() => error = '$e');
    }
  }

  Future<void> _unit(Map<Object?, Object?> unit, String operation) async {
    final result = await _action({
      'op': 'unit',
      'unit': unit['name'],
      'user': unit['user'] == true,
      'action': operation,
    });
    if (!mounted || operation != 'journal' || result == null) return;
    await showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('${unit['name']}'),
        content: SizedBox(
          width: 700,
          height: 400,
          child: SingleChildScrollView(
            child: SelectableText(
              result['output'] as String? ?? '',
              style: const TextStyle(fontFamily: 'monospace'),
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(t('uiClose')),
          ),
        ],
      ),
    );
  }

  Future<void> _addUnit() async {
    final name = TextEditingController(text: 'my-service.service');
    final content = TextEditingController(
      text: '[Unit]\nDescription=My service\n\n[Service]\nType=simple\nExecStart=/usr/bin/python3 -m http.server 8080 --bind 127.0.0.1\nRestart=on-failure\nRestartSec=2\n\n[Install]\nWantedBy=multi-user.target\n',
    );
    final result = await showDialog<Map<String, Object?>>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(t('uiAddAService')),
        content: SizedBox(
          width: 650,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                M3ETextField(controller: name, label: t('uiUnitName')),
                const SizedBox(height: 16),
                M3ETextField(
                  controller: content,
                  label: t('uiUnitFile'),
                  minLines: 10,
                  maxLines: 20,
                ),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(t('uiCancel')),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, {
              'op': 'unit-save',
              'unit': name.text.trim(),
              'content': content.text,
            }),
            child: Text(t('uiSave')),
          ),
        ],
      ),
    );
    // Dialog transitions can still refer to their controllers for a frame.
    if (result != null) await _action(result);
    await Future<void>.delayed(const Duration(milliseconds: 300));
    name.dispose();
    content.dispose();
  }

  Widget _note(String text) =>
      Padding(padding: const EdgeInsets.all(12), child: Text(text));
  Widget _field(
    TextEditingController controller,
    String label, {
    String? help,
    bool password = false,
    int maxLines = 1,
  }) => Padding(
    padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
    child: M3ETextField(
      variant: M3ETextFieldVariant.outlined,
      controller: controller,
      label: label,
      supportingText: help,
      enabled: !busy,
      obscureText: password,
      showPasswordToggle: password,
      maxLines: maxLines,
    ),
  );

  Widget _copy(String label, String value) => M3EListItem(
    headline: label,
    supportingText: value,
    supportingMaxLines: null,
    trailing: M3EIconButton(
      variant: M3EIconButtonVariant.standard,
      tooltip: t('uiCopy'),
      icon: const Icon(M3EIcons.content_copy_rounded),
      onPressed: () => Clipboard.setData(ClipboardData(text: value)),
    ),
  );

  List<Widget> _controls(String name) {
    final value = state(name);
    return [
      if (value['error'] is String && (value['error'] as String).isNotEmpty)
        _note(value['error'] as String),
      LinuxGroup(
        children: [
          LinuxToggle(
            value: running(name) || value['enabled'] == true,
            title: t('uiEnabled'),
            subtitle: running(name) ? t('uiRunning') : t('uiStopped'),
            onChanged: busy
                ? null
                : (enabled) {
                    if (enabled) {
                      _start(name);
                    } else {
                      _action({'op': 'stop', 'feature': name});
                    }
                  },
          ),
        ],
      ),
      Padding(
        padding: const EdgeInsets.all(12),
        child: M3EButton.filled(
          onPressed: busy ? null : () => _start(name, saveOnly: true),
          label: Text(t('uiSaveSettings')),
        ),
      ),
    ];
  }

  List<Widget> _remote(String name) {
    final ssh = name == 'ssh';
    final port = state(name)['port'] ?? (ssh ? 2222 : 5900);
    final addresses = (ssh ? sshLan : vncLan)
        ? ((status?['addresses'] as List?) ?? ['127.0.0.1'])
        : ['127.0.0.1'];
    return [
      _note(
        ssh
            ? t('uiConnectUsingYourLinuxUserPasswordOrAnAuthorizedPublicKey')
            : t(
                'uiShareTheLinuxDesktopAnEmptyPasswordGeneratesANewOneAtEachStart',
              ),
      ),
      _field(ssh ? sshPort : vncPort, t('uiPort')),
      LinuxGroup(
        children: [
          LinuxToggle(
            value: ssh ? sshLan : vncLan,
            title: t('uiAllowNetworkConnections'),
            subtitle: t('uiOffThisDeviceOnly'),
            onChanged: busy
                ? null
                : (value) async {
                    // VNC itself encrypts nothing and allows 8-character passwords at most:
                    // opening it to the network is confirmed first.
                    if (!ssh && value && !await _confirmVncLan()) return;
                    if (!mounted) return;
                    setState(() {
                      if (ssh) {
                        sshLan = value;
                      } else {
                        vncLan = value;
                      }
                    });
                  },
          ),
          if (ssh)
            LinuxToggle(
              value: passwordAuth,
              title: t('uiPasswordAuthentication'),
              onChanged: busy
                  ? null
                  : (value) => setState(() => passwordAuth = value),
            ),
        ],
      ),
      if (ssh) ...[
        _field(
          sshKeys,
          t('uiAuthorizedSSHPublicKeys'),
          help: t('uiOneKeyPerLine'),
          maxLines: 5,
        ),
      ] else
        _field(
          vncPassword,
          t('uiVNCPassword'),
          password: true,
          help: t('ui8ASCIICharactersOrLeaveEmptyToGenerate'),
        ),
      ..._controls(name),
      LinuxGroup(
        children: [
          for (final address in addresses)
            _copy(
              ssh ? 'SSH' : 'VNC',
              ssh
                  ? 'ssh ${status?['username']}@$address -p $port'
                  : '$address::$port',
            ),
          if (!ssh && (state('vnc')['secret'] as String? ?? '').isNotEmpty)
            _copy(t('uiVNCPassword'), state('vnc')['secret'] as String? ?? ''),
        ],
      ),
    ];
  }

  List<Widget> _calendar() => [
    _note(
      t(
        'uiShareSelectedAndroidCalendarsWithLinuxAsReadOnlyCalendarsACalendarViewerAlsoAppearsInTheLinuxAppList',
      ),
    ),
    Padding(
      padding: const EdgeInsets.all(12),
      child: M3EButton.outlined(
        onPressed: busy ? null : () => _calendars(request: true),
        label: Text(
          calendarGranted
              ? t('uiRefreshCalendars')
              : t('uiAllowCalendarAccess'),
        ),
      ),
    ),
    if (calendarGranted)
      LinuxGroup(
        children: [
          for (final calendar in calendars)
            LinuxToggle(
              value: selectedCalendars.contains(calendar['id']),
              title: calendar['name'] as String? ?? '',
              subtitle: calendar['account'] as String?,
              onChanged: busy
                  ? null
                  : (enabled) => setState(() {
                      if (enabled) {
                        selectedCalendars.add(calendar['id'] as String);
                      } else {
                        selectedCalendars.remove(calendar['id']);
                      }
                    }),
            ),
        ],
      ),
    if (calendarGranted && calendars.isEmpty)
      _note(t('uiNoAndroidCalendarsFound')),
    _field(calendarPort, t('uiLocalCalendarPort')),
    ..._controls('calendar'),
    if ((state('calendar')['token'] as String? ?? '').isNotEmpty)
      LinuxGroup(
        children: [
          _copy(
            t('uiSubscriptionURLICalendar'),
            'http://127.0.0.1:${state('calendar')['port']}/${state('calendar')['token']}/calendar.ics',
          ),
          _copy(
            t('uiCalendarViewer'),
            'http://127.0.0.1:${state('calendar')['port']}/${state('calendar')['token']}/',
          ),
          M3EListItem(
            headline: t('uiSyncNow'),
            leading: const Icon(M3EIcons.sync_rounded),
            onTap: busy || !running('calendar')
                ? null
                : () async {
                    try {
                      await channel.invokeMethod<void>('calendarSync', {
                        'id': id,
                      });
                    } catch (e) {
                      if (mounted) setState(() => error = '$e');
                    }
                  },
          ),
        ],
      ),
  ];

  List<Widget> _printing() {
    final jobs = ((status?['printJobs'] as List?) ?? []).whereType<Map>();
    return [
      _note(
        t(
          'uiChooseAndroidDextopInALinuxPrintDialogThenOpenTheAndroidNotificationToChooseAPrinter',
        ),
      ),
      _field(printPort, t('uiLocalPrintPort')),
      ..._controls('printing'),
      if (jobs.isNotEmpty) LinuxSection(t('uiWaitingToPrint')),
      LinuxGroup(
        children: [
          for (final job in jobs)
            M3EListItem(
              headline: job['title'] as String? ?? t('uiLinuxDocument'),
              supportingText: inlineFormat(t('uiByteCount'), [job['bytes']]),
              leading: const Icon(M3EIcons.print_rounded),
              onTap: () => channel.invokeMethod<void>('printJob', {
                'id': id,
                'job': job['id'],
                'title': job['title'],
              }),
              trailing: M3EIconButton(
                variant: M3EIconButtonVariant.standard,
                icon: const Icon(M3EIcons.delete_outline_rounded),
                tooltip: t('uiDiscard'),
                onPressed: busy
                    ? null
                    : () => _action({'op': 'print-delete', 'job': job['id']}),
              ),
            ),
        ],
      ),
    ];
  }

  List<Widget> _services() {
    final units = ((status?['units'] as List?) ?? [])
        .whereType<Map>()
        .map((row) => row.cast<Object?, Object?>())
        .where(
          (row) => '${row['name']}'.toLowerCase().contains(
            filter.text.toLowerCase(),
          ),
        )
        .toList();
    return [
      _note(
        t(
          'uiDextopServiceCompatibilitySystemctlJournalctlSimpleExecOneshotServicesAndMonotonicTimers',
        ),
      ),
      if (!running('services')) ...[
        _note(
          state('services')['error'] as String? ??
              t('uiPreparingServiceManagement'),
        ),
      ],
      ...[
        Padding(
          padding: const EdgeInsets.all(12),
          child: M3EButton.outlined(
            onPressed: busy ? null : _addUnit,
            label: Text(t('uiAddAService')),
          ),
        ),
        Padding(
          padding: const EdgeInsets.all(12),
          child: M3ETextField(
            controller: filter,
            label: t('uiFilterServices'),
            onChanged: (_) => setState(() {}),
          ),
        ),
        LinuxGroup(
          children: [
            for (final unit in units.take(50))
              M3EListItem(
                headline: '${unit['name']}',
                supportingMaxLines: null,
                supportingText:
                    '${unit['user'] == true ? 'user · ' : ''}${unit['state']} · ${unit['enabled'] == true ? t('uiEnabled981d6e6c') : t('uiManual')}${(unit['error'] as String? ?? '').isEmpty ? '' : '\n${unit['error']}'}',
                leading: Icon(
                  M3EIcons.circle_rounded,
                  size: 12,
                  color: unit['state'] == 'active' || unit['state'] == 'waiting'
                      ? Theme.of(context).colorScheme.primary
                      : Theme.of(context).colorScheme.outline,
                ),
                trailing: PopupMenuButton<String>(
                  enabled: !busy,
                  onSelected: (value) => _unit(unit, value),
                  itemBuilder: (_) => [
                    for (final (value, label) in [
                      ('start', t('uiStart')),
                      ('stop', t('uiStop')),
                      ('restart', t('uiRestart')),
                      ('enable', t('uiEnableAtStartup')),
                      ('disable', t('uiDisableAtStartup')),
                      ('journal', t('uiLogs')),
                    ])
                      PopupMenuItem(value: value, child: Text(label)),
                  ],
                ),
              ),
          ],
        ),
        if (units.length > 50) _note(t('uiFilterToFindMoreServices')),
      ],
    ];
  }

  @override
  Widget build(BuildContext context) {
    if (widget.containers.isEmpty) {
      return _note(t('uiSetUpALinuxEnvironmentFirst'));
    }
    final labels = {
      'ssh': t('uiSSHExperimental'),
      'vnc': t('uiVNCExperimental'),
      'calendar': t('uiCalendarSharingExperimental'),
      'services': t('uiServices'),
      'printing': t('uiAndroidPrinting'),
    };
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (feature != null)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 12),
            child: AbsorbPointer(
              absorbing: busy,
              child: M3EDropdownMenu<String>(
                key: ValueKey(id),
                singleSelect: true,
                showChipAnimation: false,
                items: [
                  for (final container in widget.containers)
                    M3EDropdownItem(
                      value: container['id'] as String,
                      label: container['name'] as String? ?? 'Linux',
                      selected: container['id'] == id,
                    ),
                ],
                fieldStyle: M3EDropdownFieldStyle(
                  hintText: t('uiLinuxEnvironment'),
                  prefixIcon: const Icon(M3EIcons.computer_rounded),
                ),
                onSelectionChanged: (items) async {
                  if (items.isEmpty || items.first.value == id) return;
                  setState(() {
                    id = items.first.value;
                    status = null;
                    initialized = null;
                    error = null;
                    calendars = [];
                    selectedCalendars.clear();
                  });
                  await _load();
                  if (feature == 'calendar') await _calendars();
                },
              ),
            ),
          ),
        if (busy)
          const Padding(
            padding: EdgeInsets.all(16),
            child: Center(child: M3ELoadingIndicator(size: 32)),
          ),
        if (error != null)
          Padding(
            padding: const EdgeInsets.all(12),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxHeight: 160),
              child: SingleChildScrollView(
                child: SelectableText(
                  error!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ),
            ),
          ),
        if (feature == null)
          LinuxGroup(
            children: [
              for (final name in labels.keys.where(widget.features.contains))
                M3EListItem(
                  headline: labels[name]!,
                  supportingText: running(name)
                      ? t('uiRunning')
                      : t('uiStopped'),
                  leading: Icon(switch (name) {
                    'ssh' => M3EIcons.terminal_rounded,
                    'vnc' => M3EIcons.desktop_windows_rounded,
                    'calendar' => M3EIcons.calendar_month_rounded,
                    'printing' => M3EIcons.print_rounded,
                    _ => M3EIcons.settings_applications_rounded,
                  }),
                  trailing: const Icon(M3EIcons.chevron_right_rounded),
                  onTap: () async {
                    await Navigator.of(context).push<void>(
                      MaterialPageRoute(
                        builder: (context) => Scaffold(
                          appBar: M3EAppBar.top(
                            automaticallyImplyLeading: true,
                            titleText: labels[name]!,
                          ),
                          body: Center(
                            child: ConstrainedBox(
                              constraints: const BoxConstraints(maxWidth: 760),
                              child: ListView(
                                padding: const EdgeInsets.fromLTRB(
                                  16,
                                  0,
                                  16,
                                  32,
                                ),
                                children: [
                                  LinuxIntegrationsPanel(
                                    containers: widget.containers,
                                    selectedFeature: name,
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ),
                      ),
                    );
                    if (mounted) _load();
                  },
                ),
            ],
          )
        else ...[
          ...switch (feature) {
            'ssh' || 'vnc' => _remote(feature!),
            'calendar' => _calendar(),
            'printing' => _printing(),
            _ => _services(),
          },
        ],
      ],
    );
  }
}
