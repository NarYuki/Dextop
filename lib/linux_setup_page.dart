import 'dart:async';

import 'linux/linux_error_log.dart';
import 'linux/linux_backups.dart';
import 'linux/linux_privilege_warning.dart';

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
      'account' => l.linuxPhaseConfigure,
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
    this.offerExecutionMode = true,
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

  /// Known before the page opens, so that a build with one mode never shows its page.
  final bool offerExecutionMode;

  @override
  State<LinuxSetupPage> createState() => _LinuxSetupPageState();
}

class _LinuxSetupPageState extends State<LinuxSetupPage> {
  static const locales = <String, String>{
    'en_US': 'uiLanguageEnglish',
    'ja_JP': 'uiLanguageJapanese',
    'zh_CN': 'uiLanguageChineseSimplified',
    'zh_TW': 'uiLanguageChineseTraditional',
    'ko_KR': 'uiLanguageKorean',
    'ru_RU': 'uiLanguageRussian',
    'pt_BR': 'uiLanguagePortugueseBrazil',
    'de_DE': 'uiLanguageGerman',
    'fr_FR': 'uiLanguageFrench',
    'es_ES': 'uiLanguageSpanish',
  };
  final name = TextEditingController();
  late String distro;
  String locale = 'en_US';
  String ime = 'none';
  bool cjkFonts = false;
  final apps = <String>{'files', 'editor'};

  /// Step 9: a fresh Linux account, also used to unlock its encrypted keyring.
  final username = TextEditingController(text: 'dextop');
  final keyringPassword = TextEditingController();
  final keyringConfirm = TextEditingController();
  int step = 0;
  bool movingForward = true;

  // First setup only (step 7 before the environment, step 8 after it).
  static const _channel = MethodChannel('app.freedextop/linux');
  String executionMode = 'android';

  /// Full privilege needs Dextop's setup; until then only Android can be chosen.
  bool privilegeReady = false;

  /// False in the Google Play build, which has no full privilege to choose.
  bool privilegeOffered = false;
  bool debGet = false;
  bool flathub = false;
  String t(String key) => linuxText(context, key);

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

  List<int> get flow => [
    if (widget.firstRun && privilegeOffered) 7,
    0,
    if (widget.firstRun) 8,
    2,
    3,
    4,
    if (!updating) 9,
    5,
  ];

  /// Validate account credentials before starting installation.
  String? get keyringProblem {
    if (!RegExp(r'^[a-z][a-z0-9_-]{0,30}$').hasMatch(username.text) ||
        const {
          'root',
          'nobody',
          'daemon',
          'bin',
          'sys',
          'sync',
          'games',
          'mail',
          'news',
          'www-data',
          'backup',
          'messagebus',
          'sshd',
          'systemd-network',
          'systemd-timesync',
        }.contains(username.text)) {
      return t(
        'uiUseANonSystemUsernameOfUpTo31LowercaseLettersDigitsOrStartingWithALetter',
      );
    }
    if (keyringPassword.text.contains(RegExp(r'[\r\n\x00]')) ||
        keyringPassword.text.length > 1024) {
      return t('uiNoLineBreaksMaximum1024Characters');
    }
    if (keyringPassword.text.length < 8) {
      return t('uiUseAtLeast8Characters');
    }
    if (keyringConfirm.text != keyringPassword.text) {
      return t('uiThePasswordsDoNotMatch');
    }
    return null;
  }

  int get position => flow.indexOf(step);

  Future<void> _loadExecutionMode() async {
    final ready = await linuxPrivilegeReady();
    final offered = await linuxPrivilegedModeOffered();
    final value = await _channel.invokeMethod<String>('executionMode');
    if (!mounted) return;
    setState(() {
      privilegeReady = ready;
      privilegeOffered = offered;
      if (!offered && step == 7) step = 0;
      if (value != null) executionMode = value;
      if (!ready) executionMode = 'android';
    });
  }

  @override
  void initState() {
    super.initState();
    distro = widget.distroId ?? widget.catalog.first['id'] as String;
    name.text = widget.containerName ?? '';
    if (widget.firstRun) {
      privilegeOffered = widget.offerExecutionMode;
      step = privilegeOffered ? 7 : 0;
      _loadExecutionMode();
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
      username.text = saved['username'] as String? ?? username.text;
      locale = saved['locale'] as String? ?? locale;
      ime = saved['ime'] as String? ?? ime;
      cjkFonts = saved['cjkFonts'] as bool? ?? cjkFonts;
      apps
        ..clear()
        ..addAll((saved['apps'] as List? ?? []).whereType<String>());
    }
  }

  @override
  void dispose() {
    timer?.cancel();
    name.dispose();
    username.dispose();
    keyringPassword.dispose();
    keyringConfirm.dispose();
    super.dispose();
  }

  Map<String, Object> get options => {
    if (!updating) 'username': username.text,
    if (!updating && operationId == null)
      'accountPassword': keyringPassword.text,
    'displayBackend': widget.initialOptions['displayBackend'] as String? ?? 'wayland',
    'locale': locale,
    'cjkFonts': cjkFonts,
    'ime': ime,
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
      // Keep the created container even if the following keyring handover needs a retry.
      setState(() => operationId = id);
      // Android keeps the account/keyring password encrypted with its Keystore.
      keyringPassword.clear();
      keyringConfirm.clear();
      if (!mounted) return;
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
    final unavailable = linuxPrivilegeUnavailableText(context);
    if (executionMode == 'privileged' && !await linuxPrivilegeReady()) {
      throw Exception(unavailable);
    }
    // Explicitly selecting the privileged shell requires accepting its warning once.
    if (executionMode == 'privileged' &&
        !await linuxPrivilegedWarningAccepted()) {
      if (!mounted || !await showLinuxPrivilegedWarning(context)) {
        throw Exception(
          t('uiFullPrivilegeWasNotConfirmedChooseTheExecutionModeAgain'),
        );
      }
    }
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
      2: l.linuxSetupLanguage,
      3: l.linuxSetupInput,
      4: l.linuxSetupApps,
      5: l.linuxSetupReview,
      7: t('uiHowLinuxRuns'),
      8: t('uiStoreSources'),
      9: t('uiLinuxUser'),
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
                                onPressed:
                                    submitting ||
                                        (step == 9 && keyringProblem != null)
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
          'uiThisFirstSetupPreparesLinuxInDextopHowItRunsTheStoreSourcesThenWhatTheLinuxEnvironmentContainsEveryt',
        ),
      ),
      const SizedBox(height: 24),
      optionGroup([
        LinuxChoice<String>(
          value: 'android',
          groupValue: executionMode,
          onChanged: (value) => setState(() => executionMode = value),
          icon: M3EIcons.security_rounded,
          title: t('uiAndroidExperimental'),
          subtitle: t(
            'uiRunsWithOrdinaryAppPermissionsDevelopmentBrowsersAndGamesWorkAndroidSystemStorageStaysOutOfReach',
          ),
        ),
        if (privilegeOffered) LinuxChoice<String>(
          value: 'privileged',
          groupValue: executionMode,
          enabled: privilegeReady,
          onChanged: (value) async {
            if (await showLinuxPrivilegedWarning(context) && mounted) {
              setState(() => executionMode = value);
            }
          },
          icon: M3EIcons.admin_panel_settings_rounded,
          title: t('uiFullPrivilege'),
          subtitle: [
            t(
              'uiRunsThroughPrivilegedAccessSuchAsShizukuStellarOrTheBuiltInBinderReachesAndroidFilesControllersAndAu',
            ),
            if (!privilegeReady) linuxPrivilegeUnavailableText(context),
          ].join('\n'),
        ),
      ]),
    ],
    8 => [
      Text(
        t(
          'uiWhereStoreAppsComeFromAPTIsAlwaysAvailableDebGetAndFlathubAreSetUpAutomaticallyAfterTheLinuxEnvironm',
        ),
      ),
      const SizedBox(height: 24),
      optionGroup([
        LinuxToggle(
          value: true,
          onChanged: null,
          icon: M3EIcons.inventory_2_rounded,
          title: localText(context, 'uiAptProduct'),
          subtitle: t('uiOfficialDistributionPackages'),
        ),
        LinuxToggle(
          value: debGet,
          onChanged: (value) => setState(() => debGet = value),
          icon: M3EIcons.download_rounded,
          title: localText(context, 'uiDebGetProduct'),
          subtitle: t('uiDEBPackagesFromAppDevelopersChromeVSCodeAndMore'),
        ),
        LinuxToggle(
          value: flathub,
          onChanged: (value) => setState(() => flathub = value),
          icon: M3EIcons.apps_rounded,
          title: localText(context, 'uiFlathubProduct'),
          subtitle: t('uiFlatpakApplications'),
        ),
      ]),
      Padding(
        padding: const EdgeInsets.all(12),
        child: Text(
          t('uiTheStoreSupportsDebianAndUbuntu'),
          style: Theme.of(context).textTheme.bodySmall,
        ),
      ),
    ],
    9 => [
      Text(
        t(
          'uiCreateTheLinuxUserForAppsAndTerminalsUseSudoForAdministrationTheSamePasswordProtectsTheKeyringWhichU',
        ),
      ),
      const SizedBox(height: 24),
      M3ETextField(
        variant: M3ETextFieldVariant.outlined,
        controller: username,
        label: t('uiUsername'),
        supportingText: t('uiLowercaseLettersDigitsOrStartWithALetter'),
        onChanged: (_) => setState(() {}),
      ),
      const SizedBox(height: 16),
      M3ETextField(
        variant: M3ETextFieldVariant.outlined,
        controller: keyringPassword,
        obscureText: true,
        showPasswordToggle: true,
        label: t('uiPassword'),
        supportingText: t('uiAtLeast8CharactersUsedForSudoSSHAndTheKeyring'),
        onChanged: (_) => setState(() {}),
      ),
      const SizedBox(height: 12),
      M3ETextField(
        variant: M3ETextFieldVariant.outlined,
        controller: keyringConfirm,
        obscureText: true,
        showPasswordToggle: true,
        label: t('uiConfirmPassword'),
        errorText: keyringConfirm.text.isEmpty ? null : keyringProblem,
        onChanged: (_) => setState(() {}),
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
          for (final entry in widget.catalog.where(
            (entry) => entry['id'] == 'debian',
          ))
            LinuxChoice<String>(
              value: entry['id'] as String,
              groupValue: distro,
              onChanged: chooseDistro,
              title: entry['name'] as String,
            ),
        ]),
      if (!updating) ...[
        const SizedBox(height: 12),
        M3EButton.text(
          onPressed: () async {
            await LinuxBackupsUi.showSnapshots(context);
            await widget.refresh();
          },
          child: Text(t('uiRestoreFromASnapshotOrBackup')),
        ),
      ],
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
              label: t(entry.value),
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
                : inlineFormat(t('ui0Experimental'), [entry.value]),
            subtitle: entry.key == 'none' ? l.linuxSetupNoImeDetail : null,
          ),
      ]),
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
          if (privilegeOffered) summary(
            M3EIcons.security_rounded,
            t('uiHowLinuxRuns'),
            executionMode == 'android'
                ? t('uiAndroidExperimental')
                : t('uiFullPrivilege'),
          ),
        ],
        summary(
          M3EIcons.computer_rounded,
          name.text.trim().isEmpty ? distroName : name.text.trim(),
          distroName,
        ),
        if (widget.firstRun)
          summary(
            M3EIcons.storefront_rounded,
            t('uiStoreSources'),
            ['APT', if (debGet) 'deb-get', if (flathub) 'Flathub'].join(' · '),
          ),
        ...[
          summary(
            M3EIcons.language_rounded,
            locales.containsKey(locale) ? t(locales[locale]!) : locale,
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
        if (!updating)
          summary(
            M3EIcons.key_rounded,
            t('uiLinuxUserAndKeyring'),
            '${username.text} · ${t('uiSharedPasswordAutomaticUnlock')}',
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
                initiallyExpanded: true,
                children: [
                  // Five lines that follow the newest one, not the whole log at once.
                  Padding(
                    padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
                    child: LinuxLogTail(lines: current!.logs),
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
