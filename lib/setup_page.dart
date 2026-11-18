import 'dart:async';

import 'package:flutter/services.dart';
import 'package:free_dextop/analytics_service.dart';
import 'package:free_dextop/expressive/dextop_expressive.dart';
import 'package:free_dextop/expressive/dextop_widgets.dart';
import 'package:free_dextop/native_display_events.dart';
import 'package:free_dextop/l10n/app_localizations.dart';
import 'package:free_dextop/l10n/inline_text.dart';
import 'package:free_dextop/penguin_icon.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

class DextopSetupPage extends StatefulWidget {
  const DextopSetupPage({
    required this.onCompleted,
    this.onLinuxOnly,
    super.key,
  });

  final VoidCallback onCompleted;

  /// Skips the Dextop setup and opens the Linux tab in the Android execution mode.
  final VoidCallback? onLinuxOnly;

  @override
  State<DextopSetupPage> createState() => _DextopSetupPageState();
}

class _DextopSetupPageState extends State<DextopSetupPage>
    with WidgetsBindingObserver {
  static const channel = MethodChannel('app.freedextop/display');
  bool get usesEmbeddedProvider => status['privilegeProvider'] == 'embedded';
  bool get isPlayDistribution => status['distributionChannel'] != 'github';
  bool get accessibilityEnabled => status['accessibilityEnabled'] == true;
  var page = -1;
  var _pageForward = true;
  var _tutorialForward = true;
  var status = <String, dynamic>{};
  var loading = false;
  Timer? statusTimer;
  StreamSubscription<MethodCall>? nativeEvents;
  var statusRequest = 0;
  var shizukuSetupConfirmed = false;
  var providerChoiceShown = false;
  var gestureDemoOpening = false;
  var gestureDemoCompleted = false;
  var tutorialStep = 0;
  var accessibilityDisclosureAccepted = false;
  AppLocalizations get l => AppLocalizations.of(context);

  var _tutorialImagesCached = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // Decode the tutorial pictures up front so the first slide does not stall.
    if (_tutorialImagesCached) return;
    _tutorialImagesCached = true;
    final language = Localizations.localeOf(context).languageCode == 'ja'
        ? 'ja'
        : 'en';
    for (final name in const ['connect', 'accessibility', 'ready']) {
      unawaited(
        precacheImage(
          AssetImage('assets/setup_tutorial/${name}_$language.jpg'),
          context,
        ),
      );
    }
  }

  @override
  void initState() {
    super.initState();
    AppAnalytics.screen('initial_setup');
    WidgetsBinding.instance.addObserver(this);
    nativeEvents = NativeDisplayEvents.stream.listen((call) {
      if (call.method == 'shizukuStatusChanged') {
        unawaited(refreshStatus(clearPrevious: true));
      }
    });
    loadAccessibilityDisclosureConsent();
    refreshStatus();
  }

  Future<void> loadAccessibilityDisclosureConsent() async {
    final preferences = await SharedPreferences.getInstance();
    if (!mounted) return;
    setState(() {
      accessibilityDisclosureAccepted =
          preferences.getBool('accessibility_disclosure_accepted_v1') ?? false;
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    nativeEvents?.cancel();
    statusTimer?.cancel();
    channel.invokeMethod<void>('hideOverlayDemo');
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) refreshStatus(clearPrevious: true);
  }

  Future<void> refreshStatus({bool clearPrevious = false}) async {
    final request = ++statusRequest;
    if (clearPrevious && mounted) setState(() => status = {});
    final value =
        await channel.invokeMapMethod<String, dynamic>('status') ?? {};
    if (!mounted || request != statusRequest) return;
    final embedded = value['privilegeProvider'] == 'embedded';
    final installed = embedded || value['shizukuInstalled'] == true;
    final setupAvailable = installed && value['shizukuRunning'] == true;
    final permissionGranted = setupAvailable && value['shizukuGranted'] == true;
    setState(() {
      if (!setupAvailable) shizukuSetupConfirmed = false;
      if (permissionGranted) shizukuSetupConfirmed = true;
      status = {
        ...value,
        'shizukuInstalled': installed,
        'shizukuRunning': installed && value['shizukuRunning'] == true,
        'shizukuGranted': installed && value['shizukuGranted'] == true,
      };
    });
    if (page == 2 &&
        value['privilegeProviderSelectionRequired'] == true &&
        !providerChoiceShown &&
        mounted) {
      providerChoiceShown = true;
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => choosePrivilegeProvider(),
      );
    }
  }

  String get providerName => '${status['privilegeProviderName'] ?? 'Stellar'}';

  String providerText(String value) =>
      value.replaceAll('Shizuku', providerName);

  Future<void> choosePrivilegeProvider() async {
    if (!mounted) return;
    final providers = (status['privilegeProviders'] as List? ?? const [])
        .whereType<Map>()
        .map((value) => Map<String, dynamic>.from(value))
        .toList();
    if (providers.length < 2) return;
    final choice = await M3EDialog.show<String>(
      context,
      barrierDismissible: false,
      dialog: PopScope(
        canPop: false,
        child: Builder(
          builder: (dialogContext) => M3EDialog(
            icon: const Icon(M3EIcons.admin_panel_settings_rounded),
            title: l.setupProviderChoiceTitle,
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(l.setupProviderChoiceDescription),
                const SizedBox(height: 18),
                for (var i = 0; i < providers.length; i++) ...[
                  if (i > 0) const SizedBox(height: 10),
                  M3EButton.filled(
                    size: M3EButtonSize.md,
                    onPressed: () =>
                        Navigator.pop(dialogContext, '${providers[i]['id']}'),
                    child: Text('${providers[i]['name']}'),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
    if (choice == null) return;
    await channel.invokeMethod('selectPrivilegeProvider', {'provider': choice});
    await refreshStatus(clearPrevious: true);
  }

  void go(int target) {
    setState(() {
      _pageForward = target >= page;
      page = target;
    });
    if (target == 2) {
      refreshStatus(clearPrevious: true);
      statusTimer?.cancel();
    } else {
      statusTimer?.cancel();
    }
    if (target == 3 || target == 4) refreshStatus(clearPrevious: true);
  }

  Future<void> startGestureDemo() async {
    if (gestureDemoOpening) return;
    setState(() => gestureDemoOpening = true);
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        fullscreenDialog: true,
        builder: (_) => GestureDemoFlow(
          title: l.setupGestureTitle,
          done: l.done,
          back: l.back,
        ),
      ),
    );
    if (!mounted) return;
    setState(() {
      gestureDemoOpening = false;
      gestureDemoCompleted = true;
    });
  }

  Future<void> requestShizuku() async {
    if (usesEmbeddedProvider) {
      await setupEmbeddedPrivilege();
      return;
    }
    final requestingPermission = status['shizukuRunning'] == true;
    if (!requestingPermission) setState(() => loading = true);
    try {
      if (status['shizukuInstalled'] != true) {
        await channel.invokeMethod('openShizuku');
      } else if (status['shizukuRunning'] != true) {
        await channel.invokeMethod('openShizuku');
      } else {
        await channel.invokeMethod('requestShizuku');
      }
      await Future<void>.delayed(const Duration(milliseconds: 400));
      await refreshStatus();
    } on PlatformException catch (error) {
      if (!mounted) return;
      M3ESnackbar.show(
        context,
        message: providerText(error.message ?? l.setupPermissionCheckFailed),
      );
    } finally {
      if (!requestingPermission && mounted) setState(() => loading = false);
    }
  }

  Future<void> setupEmbeddedPrivilege() async {
    if (loading) return;
    setState(() => loading = true);
    try {
      if (status['shizukuRunning'] != true) {
        await channel.invokeMethod('openWirelessDebugging');
        return;
      }
      await channel.invokeMethod('requestShizuku');
      await Future<void>.delayed(const Duration(milliseconds: 300));
      await refreshStatus(clearPrevious: true);
      if (mounted && status['shizukuGranted'] == true) {
        setState(() => shizukuSetupConfirmed = true);
      }
    } on PlatformException catch (error) {
      if (!mounted) return;
      M3ESnackbar.show(
        context,
        message: error.message ?? l.setupPermissionCheckFailed,
      );
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> requestEmbeddedNotificationPermission() async {
    if (loading) return;
    setState(() => loading = true);
    try {
      await channel.invokeMethod<bool>('requestEmbeddedNotificationPermission');
      await refreshStatus(clearPrevious: true);
    } on PlatformException catch (error) {
      if (!mounted) return;
      M3ESnackbar.show(
        context,
        message: error.message ?? l.setupPermissionCheckFailed,
      );
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> verifyShizukuSetup() async {
    await refreshStatus(clearPrevious: true);
    if (!mounted) return;
    final valid =
        status['wirelessDebuggingEnabled'] == true &&
        status['shizukuBinderAlive'] == true &&
        status['shizukuRunning'] == true;
    setState(() => shizukuSetupConfirmed = valid);
    M3ESnackbar.show(
      context,
      message: providerText(
        valid ? l.setupVerified : l.setupVerificationFailed,
      ),
    );
  }

  Future<void> verifyRootService() async {
    await refreshStatus(clearPrevious: true);
    if (!mounted) return;
    final valid =
        status['shizukuBinderAlive'] == true &&
        status['shizukuRunning'] == true;
    setState(() => shizukuSetupConfirmed = valid);
    M3ESnackbar.show(
      context,
      message: providerText(
        valid ? l.setupRootVerified : l.setupRootNotRunning,
      ),
    );
  }

  Future<void> showShizukuVerification() async {
    var step = 0;
    final questions = [
      providerText(l.setupQuestionOpen),
      l.setupQuestionPair,
      providerText(l.setupQuestionStart),
    ];
    final completed = await M3EDialog.show<bool>(
      context,
      barrierDismissible: false,
      dialog: StatefulBuilder(
        builder: (dialogContext, setDialogState) {
          final question = questions[step];
          return M3EDialog(
            icon: const Icon(M3EIcons.key_rounded),
            title: question,
            content: Text(
              '${step + 1} / ${questions.length}',
              textAlign: TextAlign.center,
            ),
            actions: [
              M3EButton.text(
                onPressed: () => Navigator.pop(dialogContext, false),
                child: Text(l.no),
              ),
              M3EButton.filled(
                onPressed: () {
                  if (step == questions.length - 1) {
                    Navigator.pop(dialogContext, true);
                  } else {
                    setDialogState(() => step++);
                  }
                },
                child: Text(l.yes),
              ),
            ],
          );
        },
      ),
    );
    if (completed == true) await verifyShizukuSetup();
  }

  Future<void> complete() async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool('setup_completed', true);
    await preferences.setBool('linux_only_mode', false);
    widget.onCompleted();
  }

  /// Linux only: no Dextop permissions, so Linux runs with ordinary app permissions.
  Future<void> openLinuxOnly() async {
    if (loading) return;
    final accepted = await M3EDialog.show<bool>(
      context,
      dialog: Builder(
        builder: (dialogContext) => M3EDialog(
          icon: const Icon(M3EIcons.shield_outlined),
          title: l.setupPhaseTerms,
          content: SingleChildScrollView(child: Text(l.setupDisclaimer)),
          actions: [
            M3EButton.text(
              onPressed: () => Navigator.pop(dialogContext, false),
              child: Text(l.back),
            ),
            M3EButton.filled(
              onPressed: () => Navigator.pop(dialogContext, true),
              child: Text(l.continueLabel),
            ),
          ],
        ),
      ),
    );
    if (accepted != true || !mounted) return;
    setState(() => loading = true);
    try {
      const linux = MethodChannel('app.freedextop/linux');
      final current = await linux.invokeMethod<String>('executionMode');
      if (current != 'android') {
        await linux
            .invokeMethod<String>('setExecutionMode', {
              'mode': 'android',
              'stop': true,
            })
            .timeout(const Duration(seconds: 50));
      }
      final preferences = await SharedPreferences.getInstance();
      await preferences.setBool('linux_only_mode', true);
      widget.onLinuxOnly?.call();
    } on Exception catch (error) {
      if (!mounted) return;
      M3ESnackbar.show(
        context,
        message: error is PlatformException
            ? error.message ?? error.code
            : error.toString(),
      );
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  /// Material's shared-axis (X) transition: the pages move a short way in
  /// the direction of travel while the old one fades out and the new one in.
  /// Both use one curve, so they stay in step instead of jerking past each other.
  Widget _slideTransition(
    Widget child,
    Animation<double> animation, {
    required Key currentKey,
    required bool forward,
  }) {
    final isIncoming = child.key == currentKey;
    final side = forward ? 1.0 : -1.0;
    final offset = isIncoming ? Offset(.18 * side, 0) : Offset(-.18 * side, 0);
    final curved = CurvedAnimation(
      parent: animation,
      curve: Curves.easeInOutCubicEmphasized,
    );
    // The outgoing page is gone by the halfway point; the incoming one starts there.
    final fade = CurvedAnimation(
      parent: animation,
      curve: const Interval(.35, 1, curve: Curves.easeOut),
    );
    return RepaintBoundary(
      child: FadeTransition(
        opacity: fade,
        child: SlideTransition(
          position: Tween<Offset>(
            begin: offset,
            end: Offset.zero,
          ).animate(curved),
          child: child,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => EnsureDextopExpressive(
    child: Builder(
      builder: (context) => Scaffold(
        backgroundColor: Theme.of(context).colorScheme.surface,
        body: SafeArea(
          child: ClipRect(
            child: AnimatedSwitcher(
              duration: const Duration(milliseconds: 380),
              transitionBuilder: (child, animation) => _slideTransition(
                child,
                animation,
                currentKey: ValueKey(page < 0 ? 'welcome' : 'phase-shell'),
                forward: _pageForward,
              ),
              child: page < 0 ? welcome() : phasePage(),
            ),
          ),
        ),
      ),
    ),
  );

  Widget welcome() {
    final colors = Theme.of(context).colorScheme;
    return Center(
      key: const ValueKey('welcome'),
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(28),
              child: const Image(
                image: AssetImage('assets/dextop_icon.png'),
                width: 112,
                height: 112,
              ),
            ),
            const SizedBox(height: 30),
            Text(
              l.setupWelcome,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.headlineMedium,
            ),
            const SizedBox(height: 12),
            Text(
              l.setupTagline,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                color: colors.onSurfaceVariant,
                fontWeight: FontWeight.w500,
              ),
            ),
            const SizedBox(height: 34),
            M3EButton.filled(
              size: M3EButtonSize.md,
              onPressed: () => go(0),
              icon: const Icon(M3EIcons.arrow_forward_rounded),
              label: Text(l.setupBegin),
            ),
            if (widget.onLinuxOnly != null) ...[
              const SizedBox(height: 12),
              M3EButton.icon(
                style: M3EButtonStyle.text,
                enabled: !loading,
                onPressed: loading ? null : openLinuxOnly,
                icon: const PenguinIcon(),
                label: Text(localText(context, 'uiGoToLinuxContainers')),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget phasePage() {
    final steps = isPlayDistribution ? [0, 1, 2, 3, 4, 5] : [0, 1, 2, 4, 5];
    final stepPos = (steps.indexOf(page) + 1).clamp(1, steps.length);
    final phaseList = phases();
    return Padding(
      key: const ValueKey('phase-shell'),
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          DextopStatusPill(
            label: [
              l.setupPhaseTerms,
              l.setupTutorialTitle,
              l.setupPhaseDriver,
              l.setupPhaseAccessibility,
              l.setupPhaseDevice,
              l.setupPhaseDemo,
            ][page],
            containerColor: Theme.of(context).colorScheme.secondaryContainer,
            contentColor: Theme.of(context).colorScheme.onSecondaryContainer,
          ),
          const SizedBox(height: 10),
          TweenAnimationBuilder<double>(
            tween: Tween<double>(end: stepPos / steps.length),
            duration: const Duration(milliseconds: 350),
            curve: Curves.easeOutCubic,
            builder: (context, value, _) =>
                M3EProgressIndicator.linearWavy(value: value),
          ),
          const SizedBox(height: 12),
          Expanded(
            child: ClipRect(
              child: AnimatedSwitcher(
                duration: const Duration(milliseconds: 380),
                transitionBuilder: (child, animation) => _slideTransition(
                  child,
                  animation,
                  currentKey: ValueKey(page),
                  forward: _pageForward,
                ),
                child: KeyedSubtree(
                  key: ValueKey(page),
                  child: phaseList[page],
                ),
              ),
            ),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              progressDots(),
              const Spacer(),
              if (page > 0)
                M3EButton.text(
                  onPressed: () {
                    if (page == 1 && tutorialStep > 0) {
                      setState(() {
                        _tutorialForward = false;
                        tutorialStep--;
                      });
                    } else if (page == 4 && !isPlayDistribution) {
                      go(2);
                    } else {
                      go(page - 1);
                    }
                  },
                  child: Text(l.back),
                ),
              const SizedBox(width: 8),
              M3EButton.filled(
                size: M3EButtonSize.md,
                enabled: canContinue,
                onPressed: canContinue
                    ? () {
                        if (page == 1 && tutorialStep < 2) {
                          setState(() {
                            _tutorialForward = true;
                            tutorialStep++;
                          });
                        } else if (page == 2 && !isPlayDistribution) {
                          go(4);
                        } else if (page == 5) {
                          complete();
                        } else {
                          go(page + 1);
                        }
                      }
                    : null,
                icon: Icon(
                  page == 5
                      ? M3EIcons.check_rounded
                      : M3EIcons.arrow_forward_rounded,
                ),
                label: Text(page == 5 ? l.done : l.continueLabel),
              ),
            ],
          ),
        ],
      ),
    );
  }

  bool get canContinue => switch (page) {
    2 => shizukuSetupConfirmed && status['shizukuGranted'] == true,
    3 =>
      !isPlayDistribution ||
          (accessibilityEnabled && accessibilityDisclosureAccepted),
    5 => gestureDemoCompleted,
    _ => true,
  };

  List<Widget> phases() => [
    disclaimer(),
    setupTutorial(),
    shizuku(),
    accessibilitySetup(),
    deviceInfo(),
    demo(),
  ];

  Widget _phaseBadge(
    IconData icon, {
    AlignmentGeometry alignment = AlignmentDirectional.centerStart,
  }) {
    final colors = Theme.of(context).colorScheme;
    return Align(
      alignment: alignment,
      child: DecoratedBox(
        decoration: ShapeDecoration(
          shape: const CircleBorder(),
          color: colors.primaryContainer.withValues(alpha: 0.55),
        ),
        child: SizedBox.square(
          dimension: 72,
          child: Center(child: Icon(icon, size: 36, color: colors.primary)),
        ),
      ),
    );
  }

  Widget disclaimer() => ListView(
    children: [
      const SizedBox(height: 16),
      _phaseBadge(M3EIcons.shield_outlined),
      const SizedBox(height: 24),
      Text(
        l.setupSystemTitle,
        style: Theme.of(context).textTheme.headlineMedium,
      ),
      const SizedBox(height: 16),
      M3ECard(
        variant: M3ECardVariant.filled,
        borderRadius: BorderRadius.circular(24),
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(l.setupSystemDescription),
            const SizedBox(height: 12),
            Text(
              l.setupDisclaimer,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ),
    ],
  );

  Widget shizuku() {
    final installed = status['shizukuInstalled'] == true;
    final granted = status['shizukuGranted'] == true;
    final colors = Theme.of(context).colorScheme;
    return ListView(
      children: [
        const SizedBox(height: 14),
        _phaseBadge(
          granted ? M3EIcons.check_circle_rounded : M3EIcons.key_rounded,
        ),
        const SizedBox(height: 20),
        Text(
          usesEmbeddedProvider
              ? l.setupEmbeddedTitle
              : providerText(l.setupShizukuTitle),
          style: Theme.of(context).textTheme.headlineMedium,
        ),
        const SizedBox(height: 10),
        Text(
          usesEmbeddedProvider
              ? l.setupEmbeddedWirelessDebuggingDescription
              : providerText(l.setupShizukuDescription),
          style: Theme.of(context).textTheme.bodyMedium
              ?.copyWith(color: colors.onSurfaceVariant),
        ),
        const SizedBox(height: 20),
        DextopGroup(
          children: [
            _statusRow(
              context,
              label: usesEmbeddedProvider
                  ? l.setupEmbeddedIncluded
                  : providerText(l.setupInstallShizuku),
              complete: installed,
            ),
            if (usesEmbeddedProvider)
              _statusRow(
                context,
                label: l.setupEmbeddedNotificationPermission,
                complete: status['embeddedNotificationGranted'] == true,
              ),
            _statusRow(
              context,
              label: usesEmbeddedProvider
                  ? l.setupEmbeddedConfigure
                  : providerText(l.setupConfigureShizuku),
              complete: shizukuSetupConfirmed,
            ),
            _statusRow(
              context,
              label: l.setupDextopPermission,
              complete: granted,
            ),
          ],
        ),
        if (usesEmbeddedProvider && !shizukuSetupConfirmed) ...[
          const SizedBox(height: 14),
          M3ECard(
            variant: M3ECardVariant.filled,
            borderRadius: BorderRadius.circular(26),
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    Icon(
                      M3EIcons.wifi_tethering_rounded,
                      color: colors.primary,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        l.setupEmbeddedTitle,
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                Text(l.setupEmbeddedDescription),
                const SizedBox(height: 16),
                if (status['embeddedNotificationGranted'] != true) ...[
                  M3EButton.tonal(
                    size: M3EButtonSize.md,
                    enabled: !loading,
                    onPressed: loading
                        ? null
                        : requestEmbeddedNotificationPermission,
                    icon: const Icon(M3EIcons.notifications_rounded),
                    label: Text(l.setupEmbeddedAllowNotifications),
                  ),
                  const SizedBox(height: 10),
                ],
                M3EButton.outlined(
                  size: M3EButtonSize.md,
                  enabled:
                      !loading && status['embeddedNotificationGranted'] == true,
                  onPressed:
                      loading || status['embeddedNotificationGranted'] != true
                      ? null
                      : () async {
                          try {
                            await channel.invokeMethod('openWirelessDebugging');
                          } on PlatformException catch (error) {
                            if (!mounted) return;
                            M3ESnackbar.show(
                              context,
                              message:
                                  error.message ?? l.setupPermissionCheckFailed,
                            );
                          }
                        },
                  icon: const Icon(M3EIcons.settings_rounded),
                  label: Text(l.setupEmbeddedOpenWirelessDebugging),
                ),
              ],
            ),
          ),
        ],
        AnimatedSwitcher(
          duration: const Duration(milliseconds: 300),
          switchInCurve: Curves.easeOut,
          switchOutCurve: Curves.easeIn,
          child: !usesEmbeddedProvider && installed && !shizukuSetupConfirmed
              ? Padding(
                  key: const ValueKey('shizuku-setup-hint'),
                  padding: const EdgeInsets.fromLTRB(4, 14, 4, 12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text(
                        providerText(l.setupShizukuHint),
                        style: Theme.of(context).textTheme.bodyMedium
                            ?.copyWith(color: colors.onSurfaceVariant),
                      ),
                      const SizedBox(height: 12),
                      M3EButton.filled(
                        size: M3EButtonSize.md,
                        enabled: !loading,
                        onPressed: loading ? null : requestShizuku,
                        icon: const Icon(M3EIcons.open_in_new_rounded),
                        label: Text(providerText(l.setupOpenShizuku)),
                      ),
                      const SizedBox(height: 10),
                      M3EButton.tonal(
                        size: M3EButtonSize.md,
                        onPressed: showShizukuVerification,
                        icon: const Icon(M3EIcons.fact_check_rounded),
                        label: Text(l.setupValidate),
                      ),
                      const SizedBox(height: 10),
                      M3EButton.tonal(
                        size: M3EButtonSize.md,
                        onPressed: verifyRootService,
                        icon: const Icon(M3EIcons.security_rounded),
                        label: Text(l.setupRunningAsRoot),
                      ),
                    ],
                  ),
                )
              : const SizedBox(key: ValueKey('shizuku-setup-hidden')),
        ),
        if (!usesEmbeddedProvider && installed && shizukuSetupConfirmed) ...[
          const SizedBox(height: 16),
          M3EButton.tonal(
            size: M3EButtonSize.md,
            enabled: !loading && !granted,
            onPressed: loading || granted ? null : requestShizuku,
            icon: loading
                ? const SizedBox.square(
                    dimension: 24,
                    child: M3ELoadingIndicator(size: 24),
                  )
                : const Icon(M3EIcons.key_rounded),
            label: Text(providerText(l.setupAllowPermission)),
          ),
        ],
      ],
    );
  }

  Widget setupTutorial() {
    final slides = [
      (
        image: 'connect',
        title: l.setupTutorialDriverTitle,
        description: l.setupTutorialDriverDescription,
      ),
      (
        image: 'accessibility',
        title: l.setupTutorialAccessibilityTitle,
        description: l.setupTutorialAccessibilityDescription,
      ),
      (image: 'ready', title: l.setupTutorialReadyDescription, description: ''),
    ];
    final slide = slides[tutorialStep];
    return Column(
      children: [
        Expanded(
          child: ClipRect(
            child: AnimatedSwitcher(
              duration: const Duration(milliseconds: 380),
              transitionBuilder: (child, animation) => _slideTransition(
                child,
                animation,
                currentKey: ValueKey(tutorialStep),
                forward: _tutorialForward,
              ),
              child: ListView(
                key: ValueKey(tutorialStep),
                children: [
                  tutorialImage(slide.image),
                  const SizedBox(height: 20),
                  Text(
                    slide.title,
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                  if (slide.description.isNotEmpty) ...[
                    const SizedBox(height: 12),
                    Text(
                      slide.description,
                      style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
        const SizedBox(height: 12),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: List.generate(
            slides.length,
            (index) => AnimatedContainer(
              duration: const Duration(milliseconds: 180),
              width: index == tutorialStep ? 22 : 8,
              height: 8,
              margin: const EdgeInsets.symmetric(horizontal: 3),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(8),
                color: index == tutorialStep
                    ? Theme.of(context).colorScheme.primary
                    : Theme.of(context).colorScheme.surfaceContainerHighest,
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget accessibilitySetup() => ListView(
    children: [
      const SizedBox(height: 14),
      _phaseBadge(
        accessibilityEnabled
            ? M3EIcons.check_circle_rounded
            : M3EIcons.accessibility_new_rounded,
      ),
      const SizedBox(height: 20),
      Text(
        l.accessibilityDisclosureTitle,
        style: Theme.of(context).textTheme.headlineMedium,
      ),
      const SizedBox(height: 12),
      M3ECard(
        variant: M3ECardVariant.filled,
        borderRadius: BorderRadius.circular(24),
        padding: const EdgeInsets.all(18),
        child: Text(l.accessibilityDisclosureBody),
      ),
      const SizedBox(height: 16),
      DextopGroup(
        children: [
          _statusRow(
            context,
            label: l.setupTutorialAccessibilityEnabled,
            complete: accessibilityEnabled,
          ),
        ],
      ),
      const SizedBox(height: 16),
      M3EButton.filled(
        size: M3EButtonSize.md,
        onPressed: () async {
          final preferences = await SharedPreferences.getInstance();
          await preferences.setBool(
            'accessibility_disclosure_accepted_v1',
            true,
          );
          if (!mounted) return;
          setState(() => accessibilityDisclosureAccepted = true);
          await channel.invokeMethod<void>('openAccessibility');
        },
        icon: const Icon(M3EIcons.accessibility_new_rounded),
        label: Text(l.accessibilityDisclosureAgree),
      ),
      const SizedBox(height: 10),
      M3EButton.outlined(
        size: M3EButtonSize.md,
        onPressed: () async {
          final preferences = await SharedPreferences.getInstance();
          await preferences.setBool(
            'accessibility_disclosure_accepted_v1',
            false,
          );
          if (!mounted) return;
          setState(() => accessibilityDisclosureAccepted = false);
          go(2);
        },
        child: Text(l.accessibilityDisclosureDecline),
      ),
    ],
  );

  Widget tutorialImage(String name) {
    final language = Localizations.localeOf(context).languageCode == 'ja'
        ? 'ja'
        : 'en';
    return Semantics(
      image: true,
      child: Container(
        height: (MediaQuery.sizeOf(context).height * .44).clamp(300.0, 460.0),
        width: double.infinity,
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surfaceContainerLow,
          borderRadius: BorderRadius.circular(26),
        ),
        clipBehavior: Clip.antiAlias,
        child: Image.asset(
          'assets/setup_tutorial/${name}_$language.jpg',
          fit: BoxFit.contain,
          gaplessPlayback: true,
        ),
      ),
    );
  }

  Widget deviceInfo() => ListView(
    children: [
      const SizedBox(height: 14),
      _phaseBadge(M3EIcons.devices_fold_rounded),
      const SizedBox(height: 20),
      Text(
        l.setupDeviceTitle,
        style: Theme.of(context).textTheme.headlineMedium,
      ),
      const SizedBox(height: 18),
      M3ECard(
        variant: M3ECardVariant.filled,
        borderRadius: BorderRadius.circular(24),
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            info(l.model, '${status['model'] ?? l.loadingLabel}'),
            info(l.vendor, '${status['manufacturer'] ?? l.loadingLabel}'),
            info('Android', '${status['androidVersion'] ?? l.loadingLabel}'),
            info(
              l.desktopUi,
              status['defaultWindowManager'] == 'dextop_book'
                  ? 'Dextop Book'
                  : '${status['desktopMode'] ?? l.loadingLabel}',
            ),
            info(l.detectedResolution, detectedResolution()),
          ],
        ),
      ),
      const SizedBox(height: 12),
      Text(
        l.setupDeviceDescription,
        style: Theme.of(context).textTheme.bodyMedium
            ?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant),
      ),
    ],
  );

  String detectedResolution() {
    final view = WidgetsBinding.instance.platformDispatcher.views.firstOrNull;
    if (view == null) return l.loadingLabel;
    final size = view.physicalSize;
    return '${size.width.round()} × ${size.height.round()} / ${(view.devicePixelRatio * 160).round()} dpi';
  }

  Widget info(String label, String value) =>
      DextopKeyValueRow(label: label, value: value);

  Widget demo() => threeFingerPrompt();

  Widget threeFingerPrompt() => SizedBox(
    width: double.infinity,
    child: Column(
      mainAxisAlignment: MainAxisAlignment.center,
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        _phaseBadge(M3EIcons.swipe_right_rounded, alignment: Alignment.center),
        const SizedBox(height: 24),
        Text(
          l.setupGestureTitle,
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.headlineMedium,
        ),
        const SizedBox(height: 12),
        if (gestureDemoCompleted)
          DextopStatusPill(
            label: l.setupGestureReviewed,
            icon: M3EIcons.check_circle_rounded,
            containerColor: Theme.of(context).colorScheme.secondaryContainer,
            contentColor: Theme.of(context).colorScheme.onSecondaryContainer,
          ),
        const SizedBox(height: 30),
        M3EButton.filled(
          size: M3EButtonSize.md,
          enabled: !gestureDemoOpening,
          onPressed: gestureDemoOpening ? null : startGestureDemo,
          icon: Icon(
            gestureDemoCompleted
                ? M3EIcons.replay_rounded
                : M3EIcons.play_arrow_rounded,
          ),
          label: Text(
            gestureDemoCompleted ? l.setupGestureReview : l.setupGestureStart,
          ),
        ),
      ],
    ),
  );

  Widget progressDots() => Row(
    children: (isPlayDistribution ? [0, 1, 2, 3, 4, 5] : [0, 1, 2, 4, 5])
        .map(
          (index) => AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            width: index == page ? 22 : 8,
            height: 8,
            margin: const EdgeInsets.only(right: 6),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(8),
              color: index == page
                  ? Theme.of(context).colorScheme.primary
                  : Theme.of(context).colorScheme.surfaceContainerHighest,
            ),
          ),
        )
        .toList(),
  );

  M3EListItem _statusRow(
    BuildContext context, {
    required String label,
    required bool complete,
  }) {
    final colors = Theme.of(context).colorScheme;
    return M3EListItem(
      leading: Icon(
        complete
            ? M3EIcons.check_circle_rounded
            : M3EIcons.radio_button_unchecked_rounded,
        color: complete ? colors.primary : colors.onSurfaceVariant,
      ),
      headline: label,
      trailing: DextopStatusPill(
        label: complete ? l.done : l.incomplete,
        containerColor: complete
            ? colors.secondaryContainer
            : colors.surfaceContainerHighest,
        contentColor: complete
            ? colors.onSecondaryContainer
            : colors.onSurfaceVariant,
      ),
    );
  }
}

class GestureDemoFlow extends StatefulWidget {
  const GestureDemoFlow({
    required this.title,
    required this.done,
    required this.back,
    super.key,
  });
  final String title;
  final String done;
  final String back;

  @override
  State<GestureDemoFlow> createState() => _GestureDemoFlowState();
}

class _GestureDemoFlowState extends State<GestureDemoFlow>
    with SingleTickerProviderStateMixin {
  static const channel = MethodChannel('app.freedextop/display');
  var step = 0;
  final pointers = <int, Offset>{};
  var triggered = false;
  late final controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1500),
  )..repeat();

  Future<void> orient() => SystemChrome.setPreferredOrientations(
    step == 0
        ? const [
            DeviceOrientation.landscapeLeft,
            DeviceOrientation.landscapeRight,
          ]
        : const [DeviceOrientation.portraitUp],
  );

  @override
  void initState() {
    super.initState();
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    orient();
  }

  void down(PointerDownEvent event) {
    pointers[event.pointer] = event.position;
    if (pointers.length >= 3) triggered = false;
  }

  void move(PointerMoveEvent event) {
    if (triggered || pointers.length < 3) return;
    final start = pointers[event.pointer];
    if (start == null) return;
    final delta = event.position - start;
    final valid = step == 0
        ? start.dx <= 140 && delta.dx >= 90 && delta.dx.abs() > delta.dy.abs()
        : start.dy <= 140 && delta.dy >= 90 && delta.dy.abs() > delta.dx.abs();
    if (valid) {
      triggered = true;
      unawaited(channel.invokeMethod<void>('showOverlayDemo'));
    }
  }

  void end(PointerEvent event) => pointers.remove(event.pointer);

  Future<void> change(int value) async {
    await channel.invokeMethod<void>('hideOverlayDemo');
    pointers.clear();
    triggered = false;
    setState(() => step = value);
    await orient();
  }

  Future<void> finish() async {
    await channel.invokeMethod<void>('hideOverlayDemo');
    await SystemChrome.setPreferredOrientations(DeviceOrientation.values);
    await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    if (mounted) Navigator.pop(context);
  }

  @override
  void dispose() {
    controller.dispose();
    channel.invokeMethod<void>('hideOverlayDemo');
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => EnsureDextopExpressive(
    child: PopScope(
      canPop: false,
      child: Builder(
        builder: (context) => Scaffold(
          backgroundColor: Theme.of(context).colorScheme.surface,
          body: Listener(
            behavior: HitTestBehavior.opaque,
            onPointerDown: down,
            onPointerMove: move,
            onPointerUp: end,
            onPointerCancel: end,
            child: SafeArea(
              child: Padding(
                padding: const EdgeInsets.all(28),
                child: Column(
                  children: [
                    Text(
                      widget.title,
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    const SizedBox(height: 12),
                    Text(
                      step == 0
                          ? AppLocalizations.of(context).setupGestureLandscape
                          : AppLocalizations.of(context).setupGesturePortrait,
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    Expanded(
                      child: AnimatedBuilder(
                        animation: controller,
                        builder: (context, _) => LayoutBuilder(
                          builder: (context, box) {
                            final moveProgress = Curves.easeInOutCubic
                                .transform(
                                  ((controller.value - .08) / .60).clamp(
                                    0.0,
                                    1.0,
                                  ),
                                );
                            final alpha = controller.value < .08
                                ? controller.value / .08 * .72
                                : controller.value < .70
                                ? .72
                                : controller.value < .92
                                ? (1 - (controller.value - .70) / .22) * .72
                                : 0.0;
                            const size = 64.0, gap = 18.0, inset = 20.0;
                            final travel = step == 0
                                ? box.maxWidth - size - inset * 2
                                : box.maxHeight - size - inset * 2;
                            return Stack(
                              children: List.generate(
                                3,
                                (i) => Positioned(
                                  left: step == 0
                                      ? inset + travel * moveProgress
                                      : box.maxWidth / 2 -
                                            114 +
                                            i * (size + gap),
                                  top: step == 1
                                      ? inset + travel * moveProgress
                                      : box.maxHeight / 2 -
                                            114 +
                                            i * (size + gap),
                                  child: Opacity(
                                    opacity: alpha.clamp(0.0, 1.0),
                                    child: Container(
                                      width: size,
                                      height: size,
                                      decoration: BoxDecoration(
                                        shape: BoxShape.circle,
                                        border: Border.all(
                                          color: Theme.of(context)
                                              .colorScheme
                                              .primary,
                                          width: 3,
                                        ),
                                        color: Theme.of(context)
                                            .colorScheme
                                            .primaryContainer
                                            .withValues(alpha: .5),
                                      ),
                                    ),
                                  ),
                                ),
                              ),
                            );
                          },
                        ),
                      ),
                    ),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        if (step == 1) ...[
                          M3EButton.outlined(
                            onPressed: () => change(0),
                            icon: const Icon(M3EIcons.arrow_back_rounded),
                            label: Text(widget.back),
                          ),
                          const SizedBox(width: 16),
                        ],
                        Text('${step + 1} / 2'),
                        const SizedBox(width: 20),
                        M3EButton.filled(
                          onPressed: () => step == 0 ? change(1) : finish(),
                          icon: Icon(
                            step == 0
                                ? M3EIcons.arrow_forward_rounded
                                : M3EIcons.check_rounded,
                          ),
                          label: Text(
                            step == 0
                                ? AppLocalizations.of(context).setupGestureNext
                                : widget.done,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    ),
  );
}
