import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:free_dextop/l10n/app_localizations.dart';
import 'package:free_dextop/penguin_icon.dart';

/// One setup operation, supplied by the Linux tab so it keeps running off-route.
class LinuxSetupProgress {
  const LinuxSetupProgress({
    required this.state,
    this.phase = '',
    this.percent = -1,
    this.detail = '',
    this.error,
    this.logs = const [],
  });
  final String state;
  final String phase;
  final int percent;
  final String detail;
  final String? error;
  final List<String> logs;
}

String linuxSetupPhaseLabel(AppLocalizations l, String phase) =>
    switch (phase) {
      'prepare' => l.linuxPhasePrepare,
      'download' => l.linuxPhaseDownload,
      'verify' => l.linuxPhaseVerify,
      'extract' => l.linuxPhaseExtract,
      'configure' => l.linuxPhaseConfigure,
      'refresh' => l.linuxPhaseRefresh,
      'provision' => l.linuxPhaseProvision,
      'language' => l.linuxSetupPhaseLanguage,
      'input' => l.linuxSetupPhaseInput,
      'desktop' => l.linuxSetupPhaseDesktop,
      'graphics' => l.linuxSetupPhaseGraphics,
      'applications' => l.linuxSetupPhaseApps,
      'integration' => l.linuxSetupPhaseIntegration,
      'finish' => l.linuxPhaseFinish,
      _ => l.linuxStateInstalling,
    };

class LinuxSetupPage extends StatefulWidget {
  const LinuxSetupPage({
    super.key,
    required this.catalog,
    required this.progress,
    required this.refresh,
    required this.install,
    this.containerId,
    this.containerName,
    this.distroId,
    this.initialOptions = const {},
    this.resume = false,
  });

  final List<Map<Object?, Object?>> catalog;
  final ValueListenable<Map<String, LinuxSetupProgress>> progress;
  final Future<void> Function() refresh;
  final Future<String> Function(
    String name,
    String distro,
    Map<String, Object> options,
    String? targetId,
  )
  install;
  final String? containerId;
  final String? containerName;
  final String? distroId;
  final Map<Object?, Object?> initialOptions;
  final bool resume;

  @override
  State<LinuxSetupPage> createState() => _LinuxSetupPageState();
}

class _LinuxSetupPageState extends State<LinuxSetupPage> {
  static const locales = <String, String>{
    'en_US': 'English',
    'ja_JP': '日本語',
    'zh_CN': '简体中文',
    'zh_TW': '繁體中文',
    'ko_KR': '한국어',
    'ru_RU': 'Русский',
    'pt_BR': 'Português (Brasil)',
    'de_DE': 'Deutsch',
    'fr_FR': 'Français',
    'es_ES': 'Español',
  };
  final name = TextEditingController();
  late String distro;
  String profile = 'complete';
  String locale = 'en_US';
  String ime = 'none';
  bool cjkFonts = false;
  bool graphics = true;
  bool themes = true;
  final apps = <String>{'files', 'editor'};
  int step = 0;
  bool initialized = false;
  bool submitting = false;
  bool refreshing = false;
  String? operationId;
  String? failure;
  Timer? timer;
  AppLocalizations get l => AppLocalizations.of(context);
  bool get updating => widget.containerId != null;

  @override
  void initState() {
    super.initState();
    distro = widget.distroId ?? widget.catalog.first['id'] as String;
    name.text = widget.containerName ?? '';
    if (widget.resume) {
      step = 6;
      operationId = widget.containerId;
      timer = Timer.periodic(const Duration(seconds: 2), (_) => refresh());
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (initialized) return;
    initialized = true;
    final language = Localizations.localeOf(context).languageCode;
    locale = locales.keys.firstWhere(
      (key) => key.startsWith('${language}_'),
      orElse: () => 'en_US',
    );
    cjkFonts = ['ja', 'zh', 'ko'].contains(language);
    ime = switch (language) {
      'ja' => 'mozc',
      'zh' => 'pinyin',
      'ko' => 'hangul',
      _ => 'none',
    };
    if (distro != 'ubuntu') apps.add('firefox');
    final saved = widget.initialOptions;
    if (saved.isNotEmpty) {
      profile = saved['profile'] as String? ?? profile;
      locale = saved['locale'] as String? ?? locale;
      ime = saved['ime'] as String? ?? ime;
      cjkFonts = saved['cjkFonts'] as bool? ?? cjkFonts;
      graphics = saved['graphics'] as bool? ?? graphics;
      themes = saved['themes'] as bool? ?? themes;
      apps
        ..clear()
        ..addAll((saved['apps'] as List? ?? []).whereType<String>());
    }
  }

  @override
  void dispose() {
    timer?.cancel();
    name.dispose();
    super.dispose();
  }

  Map<String, Object> get options => {
    'profile': profile,
    'locale': locale,
    'cjkFonts': cjkFonts,
    'ime': ime,
    'graphics': graphics,
    'themes': themes,
    'apps': apps.toList()..sort(),
  };

  void chooseDistro(String value) => setState(() {
    distro = value;
    if (value == 'ubuntu') apps.removeAll(['firefox', 'chromium']);
    if (value == 'archlinux') apps.remove('chrome');
  });

  Future<void> start() async {
    if (submitting) return;
    setState(() {
      submitting = true;
      failure = null;
    });
    try {
      final id = await widget.install(
        name.text.trim(),
        distro,
        options,
        operationId ?? widget.containerId,
      );
      if (!mounted) return;
      setState(() {
        operationId = id;
        step = 6;
      });
      timer?.cancel();
      timer = Timer.periodic(const Duration(seconds: 2), (_) => refresh());
      await refresh();
    } catch (error) {
      if (mounted) setState(() => failure = error.toString());
    } finally {
      if (mounted) setState(() => submitting = false);
    }
  }

  Future<void> refresh() async {
    if (refreshing) return;
    refreshing = true;
    try {
      await widget.refresh();
    } finally {
      refreshing = false;
    }
  }

  String get distroName =>
      widget.catalog.firstWhere((entry) => entry['id'] == distro)['name']
          as String;

  String appName(String app) => switch (app) {
    'firefox' => 'Firefox',
    'chromium' => 'Chromium',
    'chrome' => 'Google Chrome',
    'files' => l.linuxSetupFiles,
    'editor' => l.linuxSetupEditor,
    'office' => l.linuxSetupOffice,
    _ => app,
  };

  String get imeName => switch (ime) {
    'mozc' => l.linuxSetupMozc,
    'pinyin' => l.linuxSetupPinyin,
    'hangul' => l.linuxSetupHangul,
    _ => l.linuxSetupNoIme,
  };

  @override
  Widget build(BuildContext context) {
    final titles = [
      l.linuxSetupSystem,
      l.linuxSetupDesktop,
      l.linuxSetupLanguage,
      l.linuxSetupInput,
      l.linuxSetupApps,
      l.linuxSetupReview,
    ];
    return PopScope(
      canPop: !submitting,
      child: Scaffold(
        appBar: AppBar(title: Text(l.linuxSetupTitle)),
        body: SafeArea(
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 760),
              child: step == 6
                  ? progressPage()
                  : Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Padding(
                          padding: const EdgeInsets.fromLTRB(24, 16, 24, 8),
                          child: Row(
                            children: [
                              for (var i = 0; i < titles.length; i++)
                                Expanded(
                                  child: Padding(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 3,
                                    ),
                                    child: LinearProgressIndicator(
                                      value: i <= step ? 1 : 0,
                                      minHeight: 5,
                                      borderRadius: BorderRadius.circular(8),
                                    ),
                                  ),
                                ),
                            ],
                          ),
                        ),
                        Expanded(
                          child: ListView(
                            key: ValueKey(step),
                            padding: const EdgeInsets.fromLTRB(24, 20, 24, 24),
                            children: [
                              Text(
                                l.linuxPhaseStep(step + 1, titles.length),
                                style: Theme.of(context).textTheme.labelLarge,
                              ),
                              const SizedBox(height: 12),
                              Text(
                                titles[step],
                                style: Theme.of(
                                  context,
                                ).textTheme.headlineMedium,
                              ),
                              const SizedBox(height: 20),
                              ...stepContent(),
                              if (failure != null)
                                Padding(
                                  padding: const EdgeInsets.only(top: 16),
                                  child: Text(
                                    failure!,
                                    style: TextStyle(
                                      color: Theme.of(
                                        context,
                                      ).colorScheme.error,
                                    ),
                                  ),
                                ),
                            ],
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.fromLTRB(24, 8, 24, 16),
                          child: Row(
                            children: [
                              if (step > 0)
                                TextButton.icon(
                                  onPressed: submitting
                                      ? null
                                      : () => setState(() => step--),
                                  icon: const Icon(Icons.arrow_back_rounded),
                                  label: Text(l.linuxSetupBack),
                                ),
                              const Spacer(),
                              FilledButton.icon(
                                onPressed: submitting
                                    ? null
                                    : step == 5
                                    ? start
                                    : () => setState(() => step++),
                                icon: submitting
                                    ? const SizedBox.square(
                                        dimension: 18,
                                        child: CircularProgressIndicator(
                                          strokeWidth: 2,
                                        ),
                                      )
                                    : Icon(
                                        step == 5
                                            ? Icons.download_rounded
                                            : Icons.arrow_forward_rounded,
                                      ),
                                label: Text(
                                  step == 5
                                      ? l.linuxSetupApply
                                      : l.linuxSetupNext,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
            ),
          ),
        ),
      ),
    );
  }

  Widget optionGroup(List<Widget> children) => Card(
    clipBehavior: Clip.antiAlias,
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        for (var index = 0; index < children.length; index++) ...[
          if (index > 0) const Divider(height: 1, indent: 16, endIndent: 16),
          children[index],
        ],
      ],
    ),
  );

  List<Widget> stepContent() => switch (step) {
    0 => [
      const Align(
        alignment: Alignment.centerLeft,
        child: PenguinIcon(size: 64, filled: true),
      ),
      const SizedBox(height: 16),
      Text(updating ? l.linuxSetupExisting : l.linuxSetupIntro),
      const SizedBox(height: 24),
      TextField(
        controller: name,
        readOnly: updating,
        maxLength: 80,
        decoration: InputDecoration(
          labelText: l.linuxContainerName,
          hintText: distroName,
          border: const OutlineInputBorder(),
        ),
      ),
      const SizedBox(height: 12),
      if (updating)
        optionGroup([
          summary(Icons.computer_rounded, l.linuxDistribution, distroName),
        ])
      else
        RadioGroup<String>(
          groupValue: distro,
          onChanged: (value) {
            if (value != null) chooseDistro(value);
          },
          child: optionGroup([
            for (final entry in widget.catalog)
              RadioListTile<String>(
                value: entry['id'] as String,
                title: Text(entry['name'] as String),
              ),
          ]),
        ),
    ],
    1 => [
      RadioGroup<String>(
        groupValue: profile,
        onChanged: (value) => setState(() {
          profile = value ?? profile;
          themes = graphics = profile == 'complete';
          if (profile == 'complete') {
            apps.addAll(['files', 'editor']);
          } else {
            apps.clear();
          }
        }),
        child: optionGroup([
          RadioListTile<String>(
            value: 'minimal',
            title: Text(l.linuxSetupMinimal),
            subtitle: Text(l.linuxSetupMinimalDetail),
          ),
          RadioListTile<String>(
            value: 'complete',
            title: Text(l.linuxSetupComplete),
            subtitle: Text(l.linuxSetupCompleteDetail),
          ),
        ]),
      ),
      const SizedBox(height: 16),
      optionGroup([
        toggle(
          l.linuxSetupThemes,
          l.linuxSetupThemesDetail,
          themes,
          (value) => setState(() => themes = value),
        ),
        toggle(
          l.linuxSetupGraphics,
          l.linuxSetupGraphicsDetail,
          graphics,
          (value) => setState(() => graphics = value),
        ),
      ]),
    ],
    2 => [
      Text(l.linuxSetupLocaleDetail),
      const SizedBox(height: 24),
      DropdownButtonFormField<String>(
        initialValue: locale,
        decoration: InputDecoration(
          labelText: l.linuxSetupLocale,
          border: const OutlineInputBorder(),
        ),
        items: [
          for (final entry in locales.entries)
            DropdownMenuItem(value: entry.key, child: Text(entry.value)),
        ],
        onChanged: (value) => setState(() => locale = value ?? locale),
      ),
      const SizedBox(height: 20),
      optionGroup([
        toggle(
          l.linuxSetupFonts,
          l.linuxSetupFontsDetail,
          cjkFonts,
          (value) => setState(() => cjkFonts = value),
        ),
      ]),
    ],
    3 => [
      Text(l.linuxSetupImeDetail),
      const SizedBox(height: 16),
      RadioGroup<String>(
        groupValue: ime,
        onChanged: (value) => setState(() => ime = value ?? ime),
        child: optionGroup([
          for (final entry in {
            'none': l.linuxSetupNoIme,
            'mozc': l.linuxSetupMozc,
            'pinyin': l.linuxSetupPinyin,
            'hangul': l.linuxSetupHangul,
          }.entries)
            RadioListTile<String>(
              value: entry.key,
              title: Text(entry.value),
              subtitle: entry.key == 'none'
                  ? Text(l.linuxSetupNoImeDetail)
                  : null,
            ),
        ]),
      ),
    ],
    4 => [
      optionGroup([
        appToggle(
          'firefox',
          l.linuxSetupFirefoxDetail,
          available: distro != 'ubuntu',
          unavailable: l.linuxSetupSnapUnavailable,
        ),
        appToggle(
          'chromium',
          l.linuxSetupChromiumDetail,
          available: distro != 'ubuntu',
          unavailable: l.linuxSetupSnapUnavailable,
        ),
        appToggle(
          'chrome',
          l.linuxSetupChromeDetail,
          available: distro != 'archlinux',
          unavailable: l.linuxSetupChromeUnavailable,
        ),
      ]),
      if (apps.any((app) => ['firefox', 'chromium', 'chrome'].contains(app)))
        Padding(
          padding: const EdgeInsets.all(12),
          child: Text(
            l.linuxSetupBrowserNote,
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ),
      const SizedBox(height: 16),
      optionGroup([
        appToggle('files', l.linuxSetupFilesDetail),
        appToggle('editor', l.linuxSetupEditorDetail),
        appToggle('office', l.linuxSetupOfficeDetail),
      ]),
    ],
    _ => [
      optionGroup([
        summary(
          Icons.computer_rounded,
          name.text.trim().isEmpty ? distroName : name.text.trim(),
          distroName,
        ),
        summary(
          Icons.space_dashboard_outlined,
          profile == 'minimal' ? l.linuxSetupMinimal : l.linuxSetupComplete,
          [
            if (themes) l.linuxSetupThemes,
            if (graphics) l.linuxSetupGraphics,
          ].join(' · '),
        ),
        summary(
          Icons.language_rounded,
          locales[locale] ?? locale,
          [
            if (cjkFonts || ime != 'none') l.linuxSetupFonts,
            imeName,
          ].join(' · '),
        ),
        summary(
          Icons.apps_rounded,
          l.linuxSetupApps,
          apps.isEmpty
              ? l.linuxSetupNothingExtra
              : apps.map(appName).join(' · '),
        ),
      ]),
      const SizedBox(height: 16),
      Text(l.linuxSetupStorage),
      if (updating)
        Padding(
          padding: const EdgeInsets.only(top: 12),
          child: Text(l.linuxSetupAdditive),
        ),
    ],
  };

  Widget toggle(
    String title,
    String subtitle,
    bool value,
    ValueChanged<bool>? changed,
  ) => SwitchListTile(
    title: Text(title),
    subtitle: Text(subtitle),
    value: value,
    onChanged: changed,
  );

  Widget appToggle(
    String app,
    String subtitle, {
    bool available = true,
    String? unavailable,
  }) => CheckboxListTile(
    title: Text(appName(app)),
    subtitle: Text(available ? subtitle : unavailable ?? ''),
    value: apps.contains(app),
    onChanged: available
        ? (value) => setState(() {
            if (value == true) {
              apps.add(app);
            } else {
              apps.remove(app);
            }
          })
        : null,
  );

  Widget summary(IconData icon, String title, String detail) => ListTile(
    leading: Icon(icon),
    title: Text(title),
    subtitle: detail.isEmpty ? null : Text(detail),
  );

  Widget progressPage() =>
      ValueListenableBuilder<Map<String, LinuxSetupProgress>>(
        valueListenable: widget.progress,
        builder: (context, values, _) {
          final current = values[operationId];
          final done = current?.state == 'installed' && current?.error == null;
          final failed =
              current?.state == 'failed' ||
              (current?.state == 'installed' && current?.error != null);
          if (done || failed) timer?.cancel();
          return ListView(
            padding: const EdgeInsets.all(24),
            children: [
              const SizedBox(height: 24),
              Icon(
                done
                    ? Icons.check_circle_outline_rounded
                    : failed
                    ? Icons.error_outline_rounded
                    : Icons.downloading_rounded,
                size: 72,
                color: failed
                    ? Theme.of(context).colorScheme.error
                    : Theme.of(context).colorScheme.primary,
              ),
              const SizedBox(height: 24),
              Text(
                done
                    ? l.linuxSetupDone
                    : failed
                    ? l.linuxSetupFailed
                    : l.linuxSetupProgress,
                style: Theme.of(context).textTheme.headlineMedium,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 24),
              if (!done && !failed) ...[
                Text(
                  linuxSetupPhaseLabel(l, current?.phase ?? ''),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 16),
                LinearProgressIndicator(
                  value: (current?.percent ?? -1) >= 0
                      ? current!.percent.clamp(0, 100) / 100
                      : null,
                ),
                const SizedBox(height: 12),
                if (current?.detail.isNotEmpty == true)
                  Text(
                    current!.detail,
                    maxLines: 3,
                    overflow: TextOverflow.ellipsis,
                  ),
                const SizedBox(height: 24),
                Text(l.linuxSetupStorage),
              ],
              if (done) Text(l.linuxSetupDoneDetail),
              if (failed) ...[
                SelectableText(current?.error ?? l.linuxSetupFailed),
                const SizedBox(height: 16),
                FilledButton(
                  onPressed: () => setState(() {
                    step = 5;
                    failure = null;
                  }),
                  child: Text(l.linuxSetupRetry),
                ),
              ],
              const SizedBox(height: 24),
              if (current?.logs.isNotEmpty == true)
                ExpansionTile(
                  title: Text(l.linuxSetupLog),
                  children: [
                    Padding(
                      padding: const EdgeInsets.all(12),
                      child: SelectableText(
                        current!.logs.join('\n'),
                        style: const TextStyle(
                          fontFamily: 'monospace',
                          fontSize: 12,
                        ),
                      ),
                    ),
                  ],
                ),
              const SizedBox(height: 16),
              FilledButton.tonal(
                onPressed: () => Navigator.pop(context),
                child: Text(done || failed ? l.close : l.linuxSetupBackground),
              ),
            ],
          );
        },
      );
}
