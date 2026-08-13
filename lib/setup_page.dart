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
  var page = -1;
  var status = <String, dynamic>{};
  var loading = false;
  final pointers = <int>{};
  Timer? statusTimer;
  var statusRequest = 0;
  var shizukuSetupConfirmed = false;
  var providerChoiceShown = false;
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
    final installed = value['shizukuInstalled'] == true;
    final setupAvailable = installed && value['shizukuRunning'] == true;
    final permissionGranted = setupAvailable && value['shizukuGranted'] == true;
    setState(() {
      if (!setupAvailable) shizukuSetupConfirmed = false;
      if (permissionGranted) shizukuSetupConfirmed = true;
      status = {
        ...value,
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
    final choice = await showDialog<String>(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        icon: const Icon(Icons.admin_panel_settings_rounded),
        title: Text(l.setupProviderChoiceTitle),
        content: Text(l.setupProviderChoiceDescription),
        actionsPadding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
        actions: [
          Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              FilledButton(
                onPressed: () => Navigator.pop(context, 'stellar'),
                child: Text(l.setupUseStellar),
              ),
              const SizedBox(height: 10),
              FilledButton(
                onPressed: () => Navigator.pop(context, 'shizuku'),
                child: Text(l.setupUseShizuku),
              ),
            ],
          ),
        ],
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

  Future<void> requestShizuku() async {
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
            providerName,
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

  bool get canContinue =>
      page != 1 || (shizukuSetupConfirmed && status['shizukuGranted'] == true);

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
          providerText(l.setupShizukuTitle),
          style: Theme.of(context).textTheme.headlineMedium,
        ),
        const SizedBox(height: 12),
        Text(providerText(l.setupShizukuDescription)),
        const SizedBox(height: 24),
        _StatusTile(
          label: providerText(l.setupInstallShizuku),
          complete: installed,
        ),
        _StatusTile(
          label: providerText(l.setupConfigureShizuku),
          complete: shizukuSetupConfirmed,
        ),
        AnimatedSwitcher(
          duration: const Duration(milliseconds: 300),
          switchInCurve: Curves.easeOut,
          switchOutCurve: Curves.easeIn,
          child: installed && !shizukuSetupConfirmed
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
        if (!installed || shizukuSetupConfirmed)
          FilledButton.tonalIcon(
            onPressed: loading || granted ? null : requestShizuku,
            icon: loading
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Icon(!installed ? Icons.download_rounded : Icons.key_rounded),
            label: Text(
              !installed
                  ? (status['privilegeProvider'] == 'stellar' ||
                            (status['sdk'] as int? ?? 0) >= 36
                        ? 'GitHubからダウンロード'
                        : l.setupInstallPlay)
                  : providerText(l.setupAllowPermission),
            ),
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

  Future<void> showRealOverlayDemo() async {
    await channel.invokeMethod<void>('showOverlayDemo');
  }

  Widget demo() => Listener(
    behavior: HitTestBehavior.opaque,
    onPointerDown: (event) {
      pointers.add(event.pointer);
      if (pointers.length == 3) {
        showRealOverlayDemo();
      }
    },
    onPointerUp: (event) => pointers.remove(event.pointer),
    onPointerCancel: (event) => pointers.remove(event.pointer),
    child: threeFingerPrompt(),
  );

  Widget threeFingerPrompt() => Column(
    mainAxisAlignment: MainAxisAlignment.center,
    children: [
      Text(
        l.setupGestureTitle,
        textAlign: TextAlign.center,
        style: Theme.of(context).textTheme.headlineMedium,
      ),
      const SizedBox(height: 12),
      Text(l.setupGestureDescription, textAlign: TextAlign.center),
      const SizedBox(height: 42),
      Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: List.generate(
          3,
          (_) => Container(
            width: 64,
            height: 64,
            margin: const EdgeInsets.symmetric(horizontal: 9),
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              border: Border.all(
                color: Theme.of(context).colorScheme.primary,
                width: 3,
              ),
              color: Theme.of(
                context,
              ).colorScheme.primaryContainer.withValues(alpha: .5),
            ),
          ),
        ),
      ),
    ],
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
