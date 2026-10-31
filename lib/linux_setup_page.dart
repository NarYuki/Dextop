import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';
import 'package:free_dextop/l10n/app_localizations.dart';
import 'package:free_dextop/l10n/inline_text.dart';
import 'package:free_dextop/linux/linux_expressive.dart';
import 'package:free_dextop/linux/linux_first_run.dart';
import 'package:free_dextop/linux/linux_widgets.dart';
import 'package:free_dextop/penguin_icon.dart';
import 'package:shared_preferences/shared_preferences.dart';

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
      'gpu' => l.linuxSetupPhaseGpu,
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
    this.firstRun = false,
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

  /// The very first setup: how Linux runs and the Store sources come first, in the same flow.
  final bool firstRun;

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
  String displayBackend = 'wayland';
  String locale = 'en_US';
  String ime = 'none';
  bool cjkFonts = false;
  bool graphics = true;
  bool themes = true;
  final apps = <String>{'files', 'editor'};
  int step = 0;
  bool movingForward = true;

  // First setup only (steps 7 and 8).
  static const _channel = MethodChannel('app.freedextop/linux');
  String executionMode = 'privileged';
  bool debGet = false;
  bool flathub = false;
  String t(String ja, String en) => linuxText(context, ja, en);

  void moveTo(int next) => setState(() {
    movingForward = next > step;
    step = next;
  });
  bool initialized = false;
  bool submitting = false;
  bool refreshing = false;
  String? operationId;
  String? failure;
  Timer? timer;
  AppLocalizations get l => AppLocalizations.of(context);
  bool get updating => widget.containerId != null;

  /// DroidDeck's runtime brings its own desktop, fonts and input methods,
  /// so only the system and app choices apply.
  bool get runtime => distro == 'droiddeck';
  List<int> get flow => [
    if (widget.firstRun) ...[7, 8],
    ...(runtime ? const [0, 4, 5] : const [0, 1, 2, 3, 4, 5]),
  ];
  int get position => flow.indexOf(step);

  @override
  void initState() {
    super.initState();
    distro = widget.distroId ?? widget.catalog.first['id'] as String;
    name.text = widget.containerName ?? '';
    if (widget.firstRun) {
      step = 7;
      _channel.invokeMethod<String>('executionMode').then((value) {
        if (mounted && value != null) setState(() => executionMode = value);
      });
    }
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
    // The Android keyboard by default; Linux input methods are experimental.
    ime = 'none';
    if (distro != 'ubuntu') apps.add('firefox');
    final saved = widget.initialOptions;
    if (saved.isNotEmpty) {
      profile = saved['profile'] as String? ?? profile;
      displayBackend = saved['displayBackend'] as String? ?? displayBackend;
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
    'displayBackend': displayBackend,
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
    if (value == 'droiddeck') {
      apps
        ..clear()
        ..add('steam');
    } else if (apps.length == 1 && apps.contains('steam')) {
      apps
        ..clear()
        ..addAll(['files', 'editor']);
    }
  });

  Future<void> start() async {
    if (submitting) return;
    // The progress screen comes at once; switching the execution mode and connecting
    // to its runtime first can take a while.
    setState(() {
      submitting = true;
      failure = null;
      movingForward = true;
      step = 6;
    });
    try {
      if (widget.firstRun) await applyFirstRun();
      Future<String> install() => widget.install(
        name.text.trim(),
        distro,
        options,
        operationId ?? widget.containerId,
      );
      String id;
      try {
        id = await install();
      } on PlatformException catch (error) {
        // The first call after a mode switch can meet the old connection; once more on the new one.
        if (!(error.message ?? '').contains('retry')) rethrow;
        id = await install();
      }
      if (!mounted) return;
      setState(() => operationId = id);
      timer?.cancel();
      timer = Timer.periodic(const Duration(seconds: 2), (_) => refresh());
      await refresh();
    } catch (error) {
      if (mounted) {
        setState(() {
          failure = error is PlatformException
              ? error.message ?? '$error'
              : '$error';
          step = 5;
        });
      }
    } finally {
      if (mounted) setState(() => submitting = false);
    }
  }

  /// Applies the first setup's choices before the container is created in that mode.
  Future<void> applyFirstRun() async {
    final current = await _channel.invokeMethod<String>('executionMode');
    if (current != executionMode) {
      await _channel.invokeMethod<String>('setExecutionMode', {
        'mode': executionMode,
        'stop': true,
      });
    }
    for (final (source, value) in [('deb-get', debGet), ('flathub', flathub)]) {
      await _channel.invokeMapMethod<String, bool>('setStoreSource', {
        'source': source,
        'enabled': value,
      });
    }
    final preferences = await SharedPreferences.getInstance();
    await preferences.setStringList(LinuxFirstRun.prepareKey, [
      if (debGet && distro != 'archlinux') 'deb-get',
      if (flathub && distro != 'archlinux') 'flathub',
    ]);
    await preferences.setBool(LinuxFirstRun.doneKey, true);
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
      widget.catalog.firstWhere(
            (entry) => entry['id'] == distro,
            orElse: () => {'name': distro},
          )['name']
          as String;

  String appName(String app) => switch (app) {
    'firefox' => 'Firefox',
    'chromium' => 'Chromium',
    'chrome' => 'Google Chrome',
    'files' => l.linuxSetupFiles,
    'editor' => l.linuxSetupEditor,
    'office' => l.linuxSetupOffice,
    'steam' => 'Steam',
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
    final titles = {
      0: l.linuxSetupSystem,
      1: l.linuxSetupDesktop,
      2: l.linuxSetupLanguage,
      3: l.linuxSetupInput,
      4: l.linuxSetupApps,
      5: l.linuxSetupReview,
      7: t('Linux の動かし方', 'How Linux runs'),
      8: t('Store の配布元', 'Store sources'),
    };
    final theme = Theme.of(context);
    return PopScope(
      canPop: !submitting,
      child: Scaffold(
        backgroundColor: theme.colorScheme.surface,
        appBar: M3EAppBar.top(
          automaticallyImplyLeading: true,
          titleText: l.linuxSetupTitle,
        ),
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
                              for (var i = 0; i < flow.length; i++) ...[
                                if (i > 0) const SizedBox(width: 4),
                                Expanded(
                                  child: AnimatedContainer(
                                    duration: const Duration(milliseconds: 360),
                                    curve: Curves.easeOutCubic,
                                    height: 6,
                                    decoration: BoxDecoration(
                                      color: i <= position
                                          ? theme.colorScheme.primary
                                          : theme
                                                .colorScheme
                                                .surfaceContainerHighest,
                                      borderRadius: BorderRadius.circular(3),
                                    ),
                                  ),
                                ),
                              ],
                            ],
                          ),
                        ),
                        Expanded(
                          child: AnimatedSwitcher(
                            duration: MediaQuery.disableAnimationsOf(context)
                                ? Duration.zero
                                : const Duration(milliseconds: 320),
                            switchInCurve: Curves.easeOutCubic,
                            switchOutCurve: Curves.easeInCubic,
                            transitionBuilder: (child, animation) {
                              final entering = child.key == ValueKey(step);
                              final direction = movingForward ? 1.0 : -1.0;
                              return FadeTransition(
                                opacity: animation,
                                child: SlideTransition(
                                  position: Tween<Offset>(
                                    begin: Offset(
                                      (entering ? direction : -direction) * .08,
                                      0,
                                    ),
                                    end: Offset.zero,
                                  ).animate(animation),
                                  child: child,
                                ),
                              );
                            },
                            child: ListView(
                              key: ValueKey(step),
                              padding: const EdgeInsets.fromLTRB(
                                24,
                                20,
                                24,
                                24,
                              ),
                              children: [
                                Text(
                                  l.linuxPhaseStep(position + 1, flow.length),
                                  style: theme.textTheme.labelLarge?.copyWith(
                                    color: theme.colorScheme.primary,
                                  ),
                                ),
                                const SizedBox(height: 12),
                                Text(
                                  titles[step]!,
                                  style: theme.textTheme.headlineMedium,
                                ),
                                const SizedBox(height: 20),
                                ...stepContent(),
                                if (failure != null)
                                  Padding(
                                    padding: const EdgeInsets.only(top: 16),
                                    child: Text(
                                      failure!,
                                      style: TextStyle(
                                        color: theme.colorScheme.error,
                                      ),
                                    ),
                                  ),
                              ],
                            ),
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.fromLTRB(24, 8, 24, 16),
                          child: Row(
                            children: [
                              if (position > 0)
                                M3EButton.outlined(
                                  icon: const Icon(M3EIcons.arrow_back_rounded),
                                  onPressed: submitting
                                      ? null
                                      : () => moveTo(flow[position - 1]),
                                  label: Text(l.linuxSetupBack),
                                ),
                              const Spacer(),
                              M3EButton.filled(
                                size: M3EButtonSize.md,
                                icon: submitting
                                    ? const SizedBox.square(
                                        dimension: 20,
                                        child: M3ELoadingIndicator(size: 20),
                                      )
                                    : Icon(
                                        step == 5
                                            ? M3EIcons.download_rounded
                                            : M3EIcons.arrow_forward_rounded,
                                      ),
                                onPressed: submitting
                                    ? null
                                    : step == 5
                                    ? start
                                    : () => moveTo(flow[position + 1]),
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

  Widget optionGroup(List<Widget> children) => LinuxGroup(children: children);

  List<Widget> stepContent() => switch (step) {
    7 => [
      const Align(
        alignment: Alignment.centerLeft,
        child: PenguinIcon(size: 64, filled: true),
      ),
      const SizedBox(height: 16),
      Text(
        t(
          'Dextop で Linux を使うための初期セットアップです。Linux の動かし方、Store の配布元、Linux 環境の中身を順に選びます。どれもあとから Linux 設定で変更できます。',
          'This first setup prepares Linux in Dextop: how it runs, the Store sources, then what the Linux environment contains. Everything can be changed later in Linux settings.',
        ),
      ),
      const SizedBox(height: 24),
      optionGroup([
        LinuxChoice<String>(
          value: 'privileged',
          groupValue: executionMode,
          onChanged: (value) => setState(() => executionMode = value),
          icon: M3EIcons.admin_panel_settings_rounded,
          title: t('完全特権', 'Full privilege'),
          subtitle: t(
            'Shizuku・Stellar・内蔵バインダなどの特権アクセスで動かします。Android のファイル領域やコントローラー・音声デバイスを直接扱えます。',
            'Runs through privileged access such as Shizuku, Stellar or the built-in binder. Reaches Android files, controllers and audio devices directly.',
          ),
        ),
        LinuxChoice<String>(
          value: 'android',
          groupValue: executionMode,
          onChanged: (value) => setState(() => executionMode = value),
          icon: M3EIcons.security_rounded,
          title: t('Android（試験的）', 'Android (experimental)'),
          subtitle: t(
            '通常のアプリ権限で動かします。開発・ブラウザ・ゲームも動きます。Android のシステム領域には触れません。',
            'Runs with ordinary app permissions. Development, browsers and games work. Android system storage stays out of reach.',
          ),
        ),
      ]),
    ],
    8 => [
      Text(
        t(
          'Store に並べるアプリの入手先です。APT は常に使えます。deb-get と Flathub は、Linux 環境のインストール後に自動で準備します。',
          'Where Store apps come from. APT is always available; deb-get and Flathub are set up automatically after the Linux environment is installed.',
        ),
      ),
      const SizedBox(height: 24),
      optionGroup([
        LinuxToggle(
          value: true,
          onChanged: null,
          icon: M3EIcons.inventory_2_rounded,
          title: 'Debian / Ubuntu APT',
          subtitle: t('ディストリビューションの公式パッケージ', 'Official distribution packages'),
        ),
        LinuxToggle(
          value: debGet,
          onChanged: (value) => setState(() => debGet = value),
          icon: M3EIcons.download_rounded,
          title: 'deb-get',
          subtitle: t(
            '開発元が配布する DEB パッケージ（Chrome・VS Code など）',
            'DEB packages from app developers (Chrome, VS Code and more)',
          ),
        ),
        LinuxToggle(
          value: flathub,
          onChanged: (value) => setState(() => flathub = value),
          icon: M3EIcons.apps_rounded,
          title: 'Flathub',
          subtitle: t('Flatpak アプリ', 'Flatpak applications'),
        ),
      ]),
      Padding(
        padding: const EdgeInsets.all(12),
        child: Text(
          t(
            'Store は Debian と Ubuntu に対応しています。',
            'The Store supports Debian and Ubuntu.',
          ),
          style: Theme.of(context).textTheme.bodySmall,
        ),
      ),
    ],
    0 => [
      const Align(
        alignment: Alignment.centerLeft,
        child: PenguinIcon(size: 64, filled: true),
      ),
      const SizedBox(height: 16),
      Text(updating ? l.linuxSetupExisting : l.linuxSetupIntro),
      const SizedBox(height: 24),
      M3ETextField(
        variant: M3ETextFieldVariant.outlined,
        controller: name,
        readOnly: updating,
        maxLength: 80,
        label: l.linuxContainerName,
        placeholder: distroName,
      ),
      const SizedBox(height: 12),
      if (updating)
        optionGroup([
          summary(M3EIcons.computer_rounded, l.linuxDistribution, distroName),
        ])
      else
        optionGroup([
          for (final entry in widget.catalog)
            LinuxChoice<String>(
              value: entry['id'] as String,
              groupValue: distro,
              onChanged: chooseDistro,
              title: entry['name'] as String,
            ),
        ]),
    ],
    1 => [
      Text(
        l.linuxSetupDisplayBackend,
        style: Theme.of(context).textTheme.titleMedium,
      ),
      const SizedBox(height: 12),
      optionGroup([
        LinuxChoice<String>(
          value: 'wayland',
          groupValue: displayBackend,
          onChanged: (value) => setState(() => displayBackend = value),
          title: 'Wayland',
          subtitle: l.linuxSetupWaylandDetail,
        ),
        LinuxChoice<String>(
          value: 'xvfb',
          groupValue: displayBackend,
          onChanged: (value) => setState(() => displayBackend = value),
          title: 'Xvfb',
          subtitle: l.linuxSetupXvfbDetail,
        ),
      ]),
      const SizedBox(height: 24),
      optionGroup([
        for (final entry in {
          'minimal': (l.linuxSetupMinimal, l.linuxSetupMinimalDetail),
          'complete': (l.linuxSetupComplete, l.linuxSetupCompleteDetail),
        }.entries)
          LinuxChoice<String>(
            value: entry.key,
            groupValue: profile,
            onChanged: (value) => setState(() {
              profile = value;
              themes = graphics = profile == 'complete';
              if (profile == 'complete') {
                apps.addAll(['files', 'editor']);
              } else {
                apps.clear();
              }
            }),
            title: entry.value.$1,
            subtitle: entry.value.$2,
          ),
      ]),
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
      const SizedBox(height: 16),
      LinuxSection(l.linuxSetupLocale),
      M3EDropdownMenu<String>(
        key: const ValueKey('locale'),
        singleSelect: true,
        showChipAnimation: false,
        items: [
          for (final entry in locales.entries)
            M3EDropdownItem(
              label: entry.value,
              value: entry.key,
              selected: entry.key == locale,
            ),
        ],
        fieldStyle: M3EDropdownFieldStyle(
          hintText: l.linuxSetupLocale,
          prefixIcon: const Icon(M3EIcons.language_rounded),
        ),
        onSelectionChanged: (items) {
          if (items.isNotEmpty && items.first.value != locale) {
            setState(() => locale = items.first.value);
          }
        },
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
      optionGroup([
        for (final entry in {
          'none': l.linuxSetupNoIme,
          'mozc': l.linuxSetupMozc,
          'pinyin': l.linuxSetupPinyin,
          'hangul': l.linuxSetupHangul,
        }.entries)
          LinuxChoice<String>(
            value: entry.key,
            groupValue: ime,
            onChanged: (value) => setState(() => ime = value),
            title: entry.key == 'none'
                ? entry.value
                : inlineFormat(t('{0}（試験的）', '{0} (experimental)'), [
                    entry.value,
                  ]),
            subtitle: entry.key == 'none' ? l.linuxSetupNoImeDetail : null,
          ),
      ]),
    ],
    4 when runtime => [
      Text(l.linuxSetupDroidDeckNote),
      const SizedBox(height: 16),
      optionGroup([appToggle('steam', l.linuxSetupSteamDetail)]),
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
        appToggle('steam', l.linuxSetupSteamDetail),
      ]),
    ],
    _ => [
      optionGroup([
        if (widget.firstRun) ...[
          summary(
            M3EIcons.security_rounded,
            t('Linux の動かし方', 'How Linux runs'),
            executionMode == 'android'
                ? t('Android（試験的）', 'Android (experimental)')
                : t('完全特権', 'Full privilege'),
          ),
          summary(
            M3EIcons.storefront_rounded,
            t('Store の配布元', 'Store sources'),
            ['APT', if (debGet) 'deb-get', if (flathub) 'Flathub'].join(' · '),
          ),
        ],
        summary(
          M3EIcons.computer_rounded,
          name.text.trim().isEmpty ? distroName : name.text.trim(),
          distroName,
        ),
        if (runtime)
          summary(
            M3EIcons.space_dashboard_outlined,
            l.linuxSetupDesktop,
            l.linuxSetupDroidDeckNote,
          )
        else ...[
          summary(
            M3EIcons.monitor_rounded,
            l.linuxSetupDisplayBackend,
            displayBackend == 'wayland' ? 'Wayland' : 'Xvfb',
          ),
          summary(
            M3EIcons.space_dashboard_outlined,
            profile == 'minimal' ? l.linuxSetupMinimal : l.linuxSetupComplete,
            [
              if (themes) l.linuxSetupThemes,
              if (graphics) l.linuxSetupGraphics,
            ].join(' · '),
          ),
          summary(
            M3EIcons.language_rounded,
            locales[locale] ?? locale,
            [
              if (cjkFonts || ime != 'none') l.linuxSetupFonts,
              imeName,
            ].join(' · '),
          ),
        ],
        summary(
          M3EIcons.apps_rounded,
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
  ) => LinuxToggle(
    title: title,
    subtitle: subtitle,
    value: value,
    onChanged: changed,
  );

  Widget appToggle(
    String app,
    String subtitle, {
    bool available = true,
    String? unavailable,
  }) {
    void change(bool? value) => setState(() {
      if (value == true) {
        apps.add(app);
      } else {
        apps.remove(app);
      }
    });
    return M3EListItem(
      supportingMaxLines: null,
      headline: appName(app),
      supportingText: available ? subtitle : unavailable ?? '',
      enabled: available,
      trailing: M3ECheckbox(
        value: apps.contains(app),
        onChanged: available ? change : null,
      ),
      onTap: available ? () => change(!apps.contains(app)) : null,
    );
  }

  Widget summary(IconData icon, String title, String detail) => M3EListItem(
    supportingMaxLines: null,
    headline: title,
    supportingText: detail.isEmpty ? null : detail,
    leading: Icon(icon),
  );

  Widget
  progressPage() => ValueListenableBuilder<Map<String, LinuxSetupProgress>>(
    valueListenable: widget.progress,
    builder: (context, values, _) {
      final theme = Theme.of(context);
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
          Center(
            child: AnimatedSwitcher(
              duration: const Duration(milliseconds: 400),
              transitionBuilder: (child, animation) => ScaleTransition(
                scale: CurvedAnimation(
                  parent: animation,
                  curve: Curves.easeOutBack,
                ),
                child: child,
              ),
              child: done || failed
                  ? M3EShapeContainer(
                      key: ValueKey(done),
                      kind: done ? M3EShapeKind.sunny : M3EShapeKind.softBoom,
                      width: 112,
                      height: 112,
                      color: failed
                          ? theme.colorScheme.errorContainer
                          : theme.colorScheme.primaryContainer,
                      child: Icon(
                        done
                            ? M3EIcons.check_rounded
                            : M3EIcons.error_outline_rounded,
                        size: 56,
                        color: failed
                            ? theme.colorScheme.onErrorContainer
                            : theme.colorScheme.onPrimaryContainer,
                      ),
                    )
                  : const M3ELoadingIndicator(
                      key: ValueKey('loading'),
                      variant: M3ELoadingIndicatorVariant.contained,
                      size: 112,
                    ),
            ),
          ),
          const SizedBox(height: 24),
          Text(
            done
                ? l.linuxSetupDone
                : failed
                ? l.linuxSetupFailed
                : l.linuxSetupProgress,
            style: theme.textTheme.headlineMedium,
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 24),
          if (!done && !failed) ...[
            Text(
              linuxSetupPhaseLabel(l, current?.phase ?? ''),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 16),
            M3EProgressIndicator.linearWavy(
              strokeWidth: 3,
              trackStrokeWidth: 3,
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
          if (done) Text(l.linuxSetupDoneDetail, textAlign: TextAlign.center),
          if (failed) ...[
            SelectableText(current?.error ?? l.linuxSetupFailed),
            const SizedBox(height: 16),
            Center(
              child: M3EButton.filled(
                icon: const Icon(M3EIcons.refresh_rounded),
                onPressed: () => setState(() {
                  step = 5;
                  failure = null;
                }),
                label: Text(l.linuxSetupRetry),
              ),
            ),
          ],
          const SizedBox(height: 24),
          if (current?.logs.isNotEmpty == true)
            M3ECard(
              variant: M3ECardVariant.outlined,
              padding: EdgeInsets.zero,
              child: ExpansionTile(
                title: Text(l.linuxSetupLog),
                shape: const Border(),
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
            ),
          const SizedBox(height: 16),
          Center(
            child: M3EButton.tonal(
              onPressed: () => Navigator.pop(context),
              child: Text(done || failed ? l.close : l.linuxSetupBackground),
            ),
          ),
        ],
      );
    },
  );
}
