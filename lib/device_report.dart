part of 'main.dart';

enum _ReportResult { working, notWorking, untested }

class DeviceReportPage extends StatefulWidget {
  const DeviceReportPage({super.key});

  @override
  State<DeviceReportPage> createState() => _DeviceReportPageState();
}

class _DeviceReportPageState extends State<DeviceReportPage> {
  static const _channel = MethodChannel('app.freedextop/display');
  static const _features = <String>[
    'reportFeatureStartup',
    'reportFeatureSession',
    'reportFeatureVirtualDisplay',
    'reportFeatureWindowManager',
    'reportFeatureSurfaceControl',
    'reportFeatureLandscape',
    'reportFeaturePortrait',
    'reportFeatureSecureDisplay',
    'reportFeatureLauncher',
    'reportFeatureWorkspace',
    'reportFeatureCursor',
    'reportFeatureDirectTouch',
    'reportFeatureMultiTouch',
    'reportFeatureGesture',
    'reportFeatureMouse',
    'reportFeatureKeyboard',
    'reportFeatureRouting',
    'reportFeatureFoldable',
    'reportFeaturePerformance',
    'reportFeatureCleanup',
  ];

  final _results = <String, _ReportResult>{
    for (final feature in _features) feature: _ReportResult.untested,
  };
  final _notes = TextEditingController();
  var _identity = <String, dynamic>{};
  var _diagnostics = <String, dynamic>{};
  var _overall = _ReportResult.untested;
  var _loading = true;
  var _sending = false;

  @override
  void initState() {
    super.initState();
    AppAnalytics.screen('device_report');
    _load();
  }

  @override
  void dispose() {
    _notes.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final values = await Future.wait([
      _channel.invokeMapMethod<String, dynamic>('deviceReportIdentity'),
      _channel.invokeMapMethod<String, dynamic>('diagnostics'),
    ]);
    if (!mounted) return;
    setState(() {
      _identity = values[0] ?? {};
      _diagnostics = values[1] ?? {};
      _loading = false;
    });
  }

  String _label(_ReportResult value) => switch (value) {
    _ReportResult.working => AppStrings.tr('reportWorking'),
    _ReportResult.notWorking => AppStrings.tr('reportNotWorking'),
    _ReportResult.untested => AppStrings.tr('reportUntested'),
  };

  String _markdownResult(_ReportResult value) => switch (value) {
    _ReportResult.working => '✅ ${AppStrings.tr('reportWorking')}',
    _ReportResult.notWorking => '❌ ${AppStrings.tr('reportNotWorking')}',
    _ReportResult.untested => '⬜ ${AppStrings.tr('reportUntested')}',
  };

  Widget _selector(_ReportResult value, ValueChanged<_ReportResult> changed) =>
      SegmentedButton<_ReportResult>(
        segments: [
          for (final item in _ReportResult.values)
            ButtonSegment(value: item, label: Text(_label(item))),
        ],
        selected: {value},
        onSelectionChanged: (selection) => changed(selection.first),
        showSelectedIcon: false,
      );

  String _value(String key) => '${_identity[key] ?? 'unknown'}';

  String _subjectValue(String key) =>
      _value(key).replaceAll(RegExp(r'[^A-Za-z0-9._+-]'), '_');

  String _buildSubject() =>
      'DEXTOP_DEVICE_REPORT|v=1|app=${_subjectValue('appVersion')}+${_subjectValue('buildNumber')}|manufacturer=${_subjectValue('manufacturer')}|model=${_subjectValue('model')}|sdk=${_subjectValue('sdk')}';

  String _buildTemplate() {
    final date = DateTime.now().toIso8601String().split('T').first;
    final buffer = StringBuffer()
      ..writeln('## ${AppStrings.tr('reportTemplateTitle')}')
      ..writeln()
      ..writeln('| Item | Reported value |')
      ..writeln('| --- | --- |')
      ..writeln('| Manufacturer | `${_value('manufacturer')}` |')
      ..writeln('| Brand | `${_value('brand')}` |')
      ..writeln('| Model | `${_value('model')}` |')
      ..writeln('| Device codename | `${_value('device')}` |')
      ..writeln('| Product | `${_value('product')}` |')
      ..writeln(
        '| Android | Android ${_value('android')} (API ${_value('sdk')}) |',
      )
      ..writeln('| Firmware / incremental build | `${_value('incremental')}` |')
      ..writeln('| Build ID | `${_value('buildId')}` |')
      ..writeln('| Build fingerprint | `${_value('fingerprint')}` |')
      ..writeln('| Security patch | `${_value('securityPatch')}` |')
      ..writeln('| Display build | `${_value('displayBuild')}` |')
      ..writeln('| Last verified | $date |')
      ..writeln(
        '| App version | `${_value('appVersion')}+${_value('buildNumber')}` |',
      )
      ..writeln('| Overall status | ${_markdownResult(_overall)} |')
      ..writeln()
      ..writeln('### Verification results')
      ..writeln()
      ..writeln('| Area | Result | Notes |')
      ..writeln('| --- | --- | --- |');
    for (final feature in _features) {
      buffer.writeln(
        '| ${AppStrings.tr(feature)} | ${_markdownResult(_results[feature]!)} | |',
      );
    }
    buffer
      ..writeln()
      ..writeln('### Automatically detected capabilities')
      ..writeln()
      ..writeln('| Capability | Detected value |')
      ..writeln('| --- | --- |');
    for (final entry in _diagnostics.entries) {
      buffer.writeln('| `${entry.key}` | `${entry.value}` |');
    }
    buffer
      ..writeln()
      ..writeln('### Reporter notes')
      ..writeln()
      ..writeln(
        _notes.text.trim().isEmpty
            ? AppStrings.tr('reportNoNotes')
            : _notes.text.trim(),
      );
    return buffer.toString();
  }

  Future<void> _send() async {
    setState(() => _sending = true);
    try {
      await _channel.invokeMethod<void>('sendDeviceReportEmail', {
        'subject': _buildSubject(),
        'body': _buildTemplate(),
      });
    } on PlatformException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              error.message ?? AppStrings.tr('reportEmailUnavailable'),
            ),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(AppStrings.tr('deviceReport'))),
    body: _loading
        ? const Center(child: CircularProgressIndicator())
        : ListView(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
            children: [
              Text(AppStrings.tr('deviceReportIntro')),
              const SizedBox(height: 20),
              Text(
                AppStrings.tr('reportOverall'),
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 8),
              _selector(_overall, (value) => setState(() => _overall = value)),
              const SizedBox(height: 24),
              for (final feature in _features) ...[
                Text(
                  AppStrings.tr(feature),
                  style: Theme.of(context).textTheme.titleSmall,
                ),
                const SizedBox(height: 8),
                _selector(
                  _results[feature]!,
                  (value) => setState(() => _results[feature] = value),
                ),
                const Divider(height: 28),
              ],
              TextField(
                controller: _notes,
                minLines: 3,
                maxLines: 8,
                decoration: InputDecoration(
                  labelText: AppStrings.tr('reportNotes'),
                  border: const OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 20),
              FilledButton.icon(
                onPressed: _sending ? null : _send,
                icon: const Icon(Icons.email_outlined),
                label: Text(AppStrings.tr('sendDeviceReport')),
              ),
            ],
          ),
  );
}
