import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:free_dextop/analytics_service.dart';
import 'package:free_dextop/l10n/app_localizations.dart';
import 'package:shared_preferences/shared_preferences.dart';

class DextopSetupPage extends StatefulWidget {
  const DextopSetupPage({required this.onCompleted, super.key});

  final VoidCallback onCompleted;

  @override
  State<DextopSetupPage> createState() => _DextopSetupPageState();
}

class _DextopSetupPageState extends State<DextopSetupPage>
    with WidgetsBindingObserver {
  static const channel = MethodChannel('app.freedextop/display');
  bool get usesEmbeddedProvider => status['privilegeProvider'] == 'embedded';
  var page = -1;
  var status = <String, dynamic>{};
  var loading = false;
  Timer? statusTimer;
  var statusRequest = 0;
  var shizukuSetupConfirmed = false;
  var providerChoiceShown = false;
  var gestureDemoOpening = false;
  var gestureDemoCompleted = false;
  AppLocalizations get l => AppLocalizations.of(context);

  @override
  void initState() {
    super.initState();
    AppAnalytics.screen('initial_setup');
    WidgetsBinding.instance.addObserver(this);
    channel.setMethodCallHandler((call) async {
      if (call.method == 'shizukuStatusChanged') {
        await refreshStatus(clearPrevious: true);
      }
    });
    refreshStatus();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    channel.setMethodCallHandler(null);
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
    // The Play build's embedded provider is part of Dextop itself. It must
    // not be treated as a missing external Shizuku application.
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
    if (page == 1 &&
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
    final choice = await showDialog<String>(
      context: context,
      barrierDismissible: false,
      builder: (context) => PopScope(
        canPop: false,
        child: AlertDialog(
          icon: const Icon(Icons.admin_panel_settings_rounded),
          title: Text(l.setupProviderChoiceTitle),
          content: Text(l.setupProviderChoiceDescription),
          actionsPadding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
          actions: [
            Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children:
                  providers
                      .expand(
                        (provider) => [
                          FilledButton(
                            onPressed: () =>
                                Navigator.pop(context, '${provider['id']}'),
                            child: Text('${provider['name']}'),
                          ),
                          const SizedBox(height: 10),
                        ],
                      )
                      .toList()
                    ..removeLast(),
            ),
          ],
        ),
      ),
    );
    if (choice == null) return;
    await channel.invokeMethod('selectPrivilegeProvider', {'provider': choice});
    await refreshStatus(clearPrevious: true);
  }

  void go(int target) {
    setState(() => page = target);
    if (target == 1) {
      refreshStatus(clearPrevious: true);
      statusTimer?.cancel();
    } else {
      statusTimer?.cancel();
    }
    if (target == 2) refreshStatus(clearPrevious: true);
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
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            providerText(error.message ?? l.setupPermissionCheckFailed),
          ),
        ),
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
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error.message ?? l.setupPermissionCheckFailed)),
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
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error.message ?? l.setupPermissionCheckFailed)),
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
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          providerText(valid ? l.setupVerified : l.setupVerificationFailed),
        ),
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
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          providerText(valid ? l.setupRootVerified : l.setupRootNotRunning),
        ),
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
    final completed = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setDialogState) {
          final question = questions[step];
          return AlertDialog(
            icon: const Icon(Icons.key_rounded),
            title: AnimatedSwitcher(
              duration: const Duration(milliseconds: 220),
              child: Text(question, key: ValueKey(step)),
            ),
            content: Text(
              '${step + 1} / ${questions.length}',
              textAlign: TextAlign.center,
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(dialogContext, false),
                child: Text(l.no),
              ),
              FilledButton(
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
    widget.onCompleted();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(
      child: AnimatedSwitcher(
        duration: const Duration(milliseconds: 300),
        child: page < 0 ? welcome() : phasePage(),
      ),
    ),
  );

  Widget welcome() => Center(
    key: const ValueKey('welcome'),
    child: Padding(
      padding: const EdgeInsets.all(32),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(30),
            child: const Image(
              image: AssetImage('assets/dextop_icon.png'),
              width: 120,
              height: 120,
            ),
          ),
          const SizedBox(height: 30),
          Text(
            l.setupWelcome,
            style: Theme.of(context).textTheme.headlineMedium,
          ),
          const SizedBox(height: 12),
          Text(
            l.setupTagline,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 34),
          FilledButton.icon(
            onPressed: () => go(0),
            icon: const Icon(Icons.arrow_forward_rounded),
            label: Text(l.setupBegin),
          ),
        ],
      ),
    ),
  );

  Widget phasePage() => Padding(
    key: ValueKey(page),
    padding: const EdgeInsets.fromLTRB(24, 18, 24, 18),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          [
            l.setupPhaseTerms,
            usesEmbeddedProvider ? l.setupEmbeddedTitle : providerName,
            l.setupPhaseDevice,
            l.setupPhaseDemo,
          ][page],
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
            color: Theme.of(context).colorScheme.primary,
          ),
        ),
        const SizedBox(height: 16),
        Expanded(
          child: IndexedStack(index: page, children: phases()),
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            progressDots(),
            const Spacer(),
            if (page > 0)
              TextButton(onPressed: () => go(page - 1), child: Text(l.back)),
            const SizedBox(width: 8),
            FilledButton.icon(
              onPressed: canContinue
                  ? () => page == 3 ? complete() : go(page + 1)
                  : null,
              icon: Icon(
                page == 3 ? Icons.check_rounded : Icons.arrow_forward_rounded,
              ),
              label: Text(page == 3 ? l.done : l.continueLabel),
            ),
          ],
        ),
      ],
    ),
  );

  bool get canContinue => switch (page) {
    1 => shizukuSetupConfirmed && status['shizukuGranted'] == true,
    3 => gestureDemoCompleted,
    _ => true,
  };

  List<Widget> phases() => [disclaimer(), shizuku(), deviceInfo(), demo()];

  Widget disclaimer() => ListView(
    children: [
      const SizedBox(height: 28),
      const Icon(Icons.shield_outlined, size: 72),
      const SizedBox(height: 28),
      Text(
        l.setupSystemTitle,
        style: Theme.of(context).textTheme.headlineMedium,
      ),
      const SizedBox(height: 18),
      Text(l.setupSystemDescription),
      const SizedBox(height: 14),
      Text(l.setupDisclaimer),
    ],
  );

  Widget shizuku() {
    final installed = status['shizukuInstalled'] == true;
    final granted = status['shizukuGranted'] == true;
    return ListView(
      children: [
        const SizedBox(height: 24),
        Icon(
          granted ? Icons.check_circle_rounded : Icons.key_rounded,
          size: 72,
        ),
        const SizedBox(height: 24),
        Text(
          usesEmbeddedProvider
              ? l.setupEmbeddedTitle
              : providerText(l.setupShizukuTitle),
          style: Theme.of(context).textTheme.headlineMedium,
        ),
        const SizedBox(height: 12),
        Text(
          usesEmbeddedProvider
              ? l.setupEmbeddedWirelessDebuggingDescription
              : providerText(l.setupShizukuDescription),
        ),
        const SizedBox(height: 24),
        _StatusTile(
          label: usesEmbeddedProvider
              ? l.setupEmbeddedIncluded
              : providerText(l.setupInstallShizuku),
          complete: installed,
        ),
        if (usesEmbeddedProvider)
          _StatusTile(
            label: l.setupEmbeddedNotificationPermission,
            complete: status['embeddedNotificationGranted'] == true,
          ),
        _StatusTile(
          label: usesEmbeddedProvider
              ? l.setupEmbeddedConfigure
              : providerText(l.setupConfigureShizuku),
          complete: shizukuSetupConfirmed,
        ),
        if (usesEmbeddedProvider && !shizukuSetupConfirmed) ...[
          const SizedBox(height: 12),
          Card.filled(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Row(
                    children: [
                      Icon(
                        Icons.wifi_tethering_rounded,
                        color: Theme.of(context).colorScheme.primary,
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
                  const SizedBox(height: 16),
                  Text(l.setupEmbeddedDescription),
                  const SizedBox(height: 16),
                  if (status['embeddedNotificationGranted'] != true) ...[
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton.tonalIcon(
                        onPressed: loading
                            ? null
                            : requestEmbeddedNotificationPermission,
                        icon: const Icon(Icons.notifications_rounded),
                        label: Text(l.setupEmbeddedAllowNotifications),
                      ),
                    ),
                    const SizedBox(height: 12),
                  ],
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      onPressed:
                          loading ||
                              status['embeddedNotificationGranted'] != true
                          ? null
                          : () async {
                              try {
                                await channel.invokeMethod(
                                  'openWirelessDebugging',
                                );
                              } on PlatformException catch (error) {
                                if (!mounted) return;
                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(
                                    content: Text(
                                      error.message ??
                                          l.setupPermissionCheckFailed,
                                    ),
                                  ),
                                );
                              }
                            },
                      icon: const Icon(Icons.settings_rounded),
                      label: Text(l.setupEmbeddedOpenWirelessDebugging),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
        ],
        AnimatedSwitcher(
          duration: const Duration(milliseconds: 300),
          switchInCurve: Curves.easeOut,
          switchOutCurve: Curves.easeIn,
          child: !usesEmbeddedProvider && installed && !shizukuSetupConfirmed
              ? Padding(
                  key: const ValueKey('shizuku-setup-hint'),
                  padding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text(
                        providerText(l.setupShizukuHint),
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                      ),
                      const SizedBox(height: 12),
                      FilledButton.icon(
                        onPressed: loading ? null : requestShizuku,
                        icon: const Icon(Icons.open_in_new_rounded),
                        label: Text(providerText(l.setupOpenShizuku)),
                      ),
                      const SizedBox(height: 8),
                      FilledButton.tonalIcon(
                        onPressed: showShizukuVerification,
                        icon: const Icon(Icons.fact_check_rounded),
                        label: Text(l.setupValidate),
                      ),
                      const SizedBox(height: 8),
                      FilledButton.tonalIcon(
                        onPressed: verifyRootService,
                        icon: const Icon(Icons.security_rounded),
                        label: Text(l.setupRunningAsRoot),
                      ),
                    ],
                  ),
                )
              : const SizedBox(key: ValueKey('shizuku-setup-hidden')),
        ),
        _StatusTile(label: l.setupDextopPermission, complete: granted),
        const SizedBox(height: 18),
        // An external provider is only selectable after it has already been
        // discovered on the device. Do not show a misleading download action
        // from this flow: the remaining action is permission confirmation.
        if (!usesEmbeddedProvider && installed && shizukuSetupConfirmed)
          FilledButton.tonalIcon(
            onPressed: loading || granted ? null : requestShizuku,
            icon: loading
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.key_rounded),
            label: Text(providerText(l.setupAllowPermission)),
          ),
      ],
    );
  }

  Widget deviceInfo() => ListView(
    children: [
      const SizedBox(height: 20),
      const Icon(Icons.devices_fold_rounded, size: 72),
      const SizedBox(height: 22),
      Text(
        l.setupDeviceTitle,
        style: Theme.of(context).textTheme.headlineMedium,
      ),
      const SizedBox(height: 20),
      Card(
        child: Column(
          children: [
            info(l.model, '${status['model'] ?? l.loadingLabel}'),
            info(l.vendor, '${status['manufacturer'] ?? l.loadingLabel}'),
            info('Android', '${status['androidVersion'] ?? l.loadingLabel}'),
            info(l.desktopUi, '${status['desktopMode'] ?? l.loadingLabel}'),
            info(l.detectedResolution, detectedResolution()),
          ],
        ),
      ),
      const SizedBox(height: 12),
      Text(l.setupDeviceDescription),
    ],
  );

  String detectedResolution() {
    final view = WidgetsBinding.instance.platformDispatcher.views.firstOrNull;
    if (view == null) return l.loadingLabel;
    final size = view.physicalSize;
    return '${size.width.round()} × ${size.height.round()} / ${(view.devicePixelRatio * 160).round()} dpi';
  }

  Widget info(String label, String value) =>
      ListTile(title: Text(label), trailing: Text(value));

  Widget demo() => threeFingerPrompt();

  Widget threeFingerPrompt() => SizedBox(
    width: double.infinity,
    child: Column(
      mainAxisAlignment: MainAxisAlignment.center,
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Text(
          l.setupGestureTitle,
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.headlineMedium,
        ),
        const SizedBox(height: 12),
        if (gestureDemoCompleted)
          Text(l.setupGestureReviewed, textAlign: TextAlign.center),
        const SizedBox(height: 32),
        FilledButton.icon(
          onPressed: gestureDemoOpening ? null : startGestureDemo,
          icon: Icon(
            gestureDemoCompleted
                ? Icons.replay_rounded
                : Icons.play_arrow_rounded,
          ),
          label: Text(
            gestureDemoCompleted ? l.setupGestureReview : l.setupGestureStart,
          ),
        ),
      ],
    ),
  );

  Widget progressDots() => Row(
    children: List.generate(
      4,
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
    ),
  );
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
    if (pointers.length == 3) triggered = false;
  }

  void move(PointerMoveEvent event) {
    if (triggered || pointers.length != 3) return;
    final start = pointers[event.pointer];
    if (start == null) return;
    final delta = event.position - start;
    final valid = step == 0
        ? start.dx <= 140 && delta.dx >= 90 && delta.dx.abs() > delta.dy.abs()
        : start.dy <= 140 && delta.dy >= 90 && delta.dy.abs() > delta.dx.abs();
    if (valid) {
      triggered = true;
      channel.invokeMethod<void>('showOverlayDemo');
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
  Widget build(BuildContext context) => PopScope(
    canPop: false,
    child: Scaffold(
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
                        final moveProgress = Curves.easeInOutCubic.transform(
                          ((controller.value - .08) / .60).clamp(0.0, 1.0),
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
                                  : box.maxWidth / 2 - 114 + i * (size + gap),
                              top: step == 1
                                  ? inset + travel * moveProgress
                                  : box.maxHeight / 2 - 114 + i * (size + gap),
                              child: Opacity(
                                opacity: alpha.clamp(0.0, 1.0),
                                child: Container(
                                  width: size,
                                  height: size,
                                  decoration: BoxDecoration(
                                    shape: BoxShape.circle,
                                    border: Border.all(
                                      color: Theme.of(
                                        context,
                                      ).colorScheme.primary,
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
                      OutlinedButton.icon(
                        onPressed: () => change(0),
                        icon: const Icon(Icons.arrow_back_rounded),
                        label: Text(widget.back),
                      ),
                      const SizedBox(width: 16),
                    ],
                    Text('${step + 1} / 2'),
                    const SizedBox(width: 20),
                    FilledButton.icon(
                      onPressed: () => step == 0 ? change(1) : finish(),
                      icon: Icon(
                        step == 0
                            ? Icons.arrow_forward_rounded
                            : Icons.check_rounded,
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
  );
}

class _StatusTile extends StatelessWidget {
  const _StatusTile({required this.label, required this.complete});
  final String label;
  final bool complete;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return ListTile(
      leading: Icon(
        complete
            ? Icons.check_circle_rounded
            : Icons.radio_button_unchecked_rounded,
      ),
      title: Text(label),
      trailing: Text(complete ? l.done : l.incomplete),
    );
  }
}
