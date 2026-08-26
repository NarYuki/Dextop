import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:free_dextop/l10n/app_localizations.dart';

/// A dedicated recovery flow for the Play build's embedded Binder.
/// Pairing codes deliberately remain in Android's notification RemoteInput.
class EmbeddedBinderSetupPage extends StatefulWidget {
  const EmbeddedBinderSetupPage({required this.onConnected, super.key});

  final VoidCallback onConnected;

  @override
  State<EmbeddedBinderSetupPage> createState() =>
      _EmbeddedBinderSetupPageState();
}

class _EmbeddedBinderSetupPageState extends State<EmbeddedBinderSetupPage>
    with WidgetsBindingObserver {
  static const _channel = MethodChannel('app.freedextop/display');
  Timer? _poller;
  var _status = <String, dynamic>{};
  var _busy = false;
  var _pairingNotificationPrepared = false;

  AppLocalizations get l => AppLocalizations.of(context);
  bool get _notificationGranted =>
      _status['embeddedNotificationGranted'] == true;
  bool get _connected =>
      _status['shizukuRunning'] == true && _status['shizukuGranted'] == true;
  String get _pairingState =>
      _status['embeddedPairingState'] as String? ?? 'idle';

  String get _pairingTitle => switch (_pairingState) {
    'searching' => l.setupEmbeddedSearchingPairing,
    'waiting_for_code' => l.setupEmbeddedPairingServiceFound,
    'pairing' => l.setupEmbeddedPairingInProgress,
    'service_not_found' || 'failed' => l.setupEmbeddedPairingServiceNotFound,
    _ => l.setupEmbeddedConfigure,
  };

  String get _pairingDescription => switch (_pairingState) {
    'searching' || 'pairing' => l.setupEmbeddedSetupDescription,
    'waiting_for_code' => l.setupEmbeddedPairingNotificationReady,
    'service_not_found' || 'failed' => l.setupEmbeddedOpenWirelessDebugging,
    _ => l.setupEmbeddedPairingCodeHint,
  };

  bool get _pairingBusy =>
      _pairingState == 'searching' || _pairingState == 'pairing';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refresh();
    _poller = Timer.periodic(const Duration(seconds: 1), (_) => _refresh());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _poller?.cancel();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _refresh();
  }

  Future<void> _refresh() async {
    final next =
        await _channel.invokeMapMethod<String, dynamic>('status') ?? {};
    if (!mounted) return;
    setState(() => _status = next);
    final pairingRequired = _status['embeddedPrivilegePaired'] != true;
    if (_notificationGranted &&
        pairingRequired &&
        !_connected &&
        !_pairingNotificationPrepared) {
      await _preparePairingNotification();
    }
    if (_connected) widget.onConnected();
  }

  Future<void> _preparePairingNotification() async {
    if (_pairingNotificationPrepared || !_notificationGranted || _connected) {
      return;
    }
    _pairingNotificationPrepared = true;
    try {
      await _channel.invokeMethod<void>('prepareEmbeddedPairingNotification');
    } on PlatformException catch (error) {
      _pairingNotificationPrepared = false;
      if (mounted) _message(error.message ?? l.setupPermissionCheckFailed);
    }
  }

  Future<void> _retryPairingDiscovery() async {
    _pairingNotificationPrepared = false;
    await _preparePairingNotification();
    await _refresh();
  }

  Future<void> _allowNotifications() async {
    setState(() => _busy = true);
    try {
      await _channel.invokeMethod<void>(
        'requestEmbeddedNotificationPermission',
      );
      await _refresh();
    } on PlatformException catch (error) {
      _message(error.message ?? l.setupPermissionCheckFailed);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _openWirelessDebugging() async {
    setState(() => _busy = true);
    try {
      await _channel.invokeMethod<void>('openWirelessDebugging');
    } on PlatformException catch (error) {
      _message(error.message ?? l.setupPermissionCheckFailed);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _message(String text) =>
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(l.setupEmbeddedTitle)),
    body: SafeArea(
      child: ListView(
        padding: const EdgeInsets.fromLTRB(24, 20, 24, 32),
        children: [
          Icon(
            _connected ? Icons.verified_rounded : Icons.wifi_tethering_rounded,
            size: 64,
            color: Theme.of(context).colorScheme.primary,
          ),
          const SizedBox(height: 18),
          Text(
            _connected ? l.setupAccessVerified : l.setupEmbeddedTitle,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 10),
          Text(
            _connected
                ? l.setupEmbeddedConnectedDescription
                : l.setupEmbeddedDescription,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyLarge?.copyWith(
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 24),
          _StepCard(
            icon: Icons.notifications_rounded,
            title: l.setupEmbeddedNotificationPermission,
            complete: _notificationGranted,
            active: !_notificationGranted,
            action: !_notificationGranted
                ? SizedBox(
                    width: double.infinity,
                    child: FilledButton.tonal(
                      onPressed: _busy ? null : _allowNotifications,
                      child: Text(l.setupEmbeddedAllowNotifications),
                    ),
                  )
                : null,
          ),
          const SizedBox(height: 12),
          _StepCard(
            icon: Icons.settings_rounded,
            title: l.setupEmbeddedOpenWirelessDebugging,
            complete: _connected,
            active: _notificationGranted && !_connected,
            body: _notificationGranted
                ? l.setupEmbeddedWirelessDebuggingDescription
                : null,
            action: _connected || !_notificationGranted
                ? null
                : SizedBox(
                    width: double.infinity,
                    child: FilledButton(
                      onPressed: _busy ? null : _openWirelessDebugging,
                      child: Text(l.setupEmbeddedOpenWirelessDebugging),
                    ),
                  ),
          ),
          const SizedBox(height: 12),
          _StepCard(
            icon: _pairingBusy ? Icons.sync_rounded : Icons.password_rounded,
            title: _pairingTitle,
            complete: _connected,
            active: _notificationGranted && !_connected,
            last: true,
            body: _connected ? null : _pairingDescription,
            action: _connected || !_notificationGranted
                ? null
                : _pairingState == 'service_not_found' ||
                      _pairingState == 'failed'
                ? OutlinedButton.icon(
                    onPressed: _busy ? null : _retryPairingDiscovery,
                    icon: const Icon(Icons.refresh_rounded),
                    label: Text(l.setupEmbeddedRetryPairing),
                  )
                : _pairingBusy
                ? const Padding(
                    padding: EdgeInsets.only(top: 12),
                    child: LinearProgressIndicator(),
                  )
                : null,
          ),
          const SizedBox(height: 28),
          if (_connected)
            FilledButton.icon(
              onPressed: () => Navigator.of(context).pop(),
              icon: const Icon(Icons.check_rounded),
              label: Text(l.done),
            )
          else
            OutlinedButton.icon(
              onPressed: _busy ? null : _refresh,
              icon: const Icon(Icons.refresh_rounded),
              label: Text(l.setupValidate),
            ),
        ],
      ),
    ),
  );
}

class _StepCard extends StatelessWidget {
  const _StepCard({
    required this.icon,
    required this.title,
    required this.complete,
    required this.active,
    this.last = false,
    this.body,
    this.action,
  });

  final IconData icon;
  final String title;
  final bool complete;
  final bool active;
  final bool last;
  final String? body;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final color = complete || active ? colors.primary : colors.outline;
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0, end: 1),
      duration: const Duration(milliseconds: 280),
      curve: Curves.easeOutCubic,
      builder: (context, value, child) => Opacity(
        opacity: value,
        child: Transform.translate(
          offset: Offset(0, (1 - value) * 14),
          child: child,
        ),
      ),
      child: IntrinsicHeight(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SizedBox(
              width: 42,
              child: Column(
                children: [
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 180),
                    width: 34,
                    height: 34,
                    decoration: BoxDecoration(
                      color: complete || active
                          ? color.withValues(alpha: .16)
                          : colors.surfaceContainerHighest,
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      complete ? Icons.check_rounded : icon,
                      size: 19,
                      color: color,
                    ),
                  ),
                  if (!last)
                    Expanded(
                      child: Container(
                        width: 2,
                        margin: const EdgeInsets.symmetric(vertical: 6),
                        color: complete ? color : colors.outlineVariant,
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Padding(
                padding: EdgeInsets.only(bottom: last ? 0 : 18),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 180),
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: active
                        ? colors.primaryContainer.withValues(alpha: .36)
                        : colors.surfaceContainerLow,
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(
                      color: active
                          ? color.withValues(alpha: .55)
                          : colors.outlineVariant,
                    ),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title,
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      if (body != null) ...[
                        const SizedBox(height: 8),
                        Text(
                          body!,
                          style: Theme.of(context).textTheme.bodyMedium
                              ?.copyWith(color: colors.onSurfaceVariant),
                        ),
                      ],
                      if (action != null) ...[
                        const SizedBox(height: 14),
                        action!,
                      ],
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
