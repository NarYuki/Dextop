part of 'main.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({
    required this.themeMode,
    required this.onThemeModeChanged,
    this.desktopWindow = false,
    super.key,
  });

  final ThemeMode themeMode;
  final ValueChanged<ThemeMode> onThemeModeChanged;
  final bool desktopWindow;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _MultiTouchUpgradeFlow extends StatefulWidget {
  const _MultiTouchUpgradeFlow();

  @override
  State<_MultiTouchUpgradeFlow> createState() => _MultiTouchUpgradeFlowState();
}

class _MultiTouchUpgradeFlowState extends State<_MultiTouchUpgradeFlow> {
  static const channel = MethodChannel('app.freedextop/display');
  var step = 0;
  final gesturePointers = <int, Offset>{};
  var gestureTriggered = false;

  ({String title, String body, String landscape, String portrait, String close})
  _copy(BuildContext context) {
    switch (Localizations.localeOf(context).languageCode) {
      case 'ja':
        return (
          title: 'アプリが更新され、ジェスチャーが変更されました。',
          body: '',
          landscape: '横向きの場合\n画面左から右に3本指でスワイプ',
          portrait: '縦持ちの場合\n画面上から下に3本指でスワイプ',
          close: '確認',
        );
      case 'zh':
        return (
          title: '应用已更新',
          body: '现已支持多点触控，因此三指手势也已更新。',
          landscape: '横屏时\n用三指从屏幕左侧向右滑动',
          portrait: '竖屏时\n用三指从屏幕顶部向下滑动',
          close: '确定',
        );
      case 'ko':
        return (
          title: '앱이 업데이트되었습니다',
          body: '멀티터치를 지원하며 이에 따라 제스처가 업데이트되었습니다.',
          landscape: '가로 모드\n화면 왼쪽에서 오른쪽으로 세 손가락 스와이프',
          portrait: '세로 모드\n화면 위에서 아래로 세 손가락 스와이프',
          close: '확인',
        );
      case 'ru':
        return (
          title: 'Приложение обновлено',
          body: 'Добавлен мультитач, поэтому жесты также были обновлены.',
          landscape:
              'Альбомная ориентация\nСмахните тремя пальцами слева направо',
          portrait:
              'Портретная ориентация\nСмахните тремя пальцами сверху вниз',
          close: 'Понятно',
        );
      default:
        return (
          title: 'The app has been updated',
          body:
              'Multi-touch is now supported, so the gestures have been updated.',
          landscape:
              'Landscape\nSwipe right with three fingers from the left edge',
          portrait: 'Portrait\nSwipe down with three fingers from the top edge',
          close: 'Got it',
        );
    }
  }

  Future<void> applyOrientation() => SystemChrome.setPreferredOrientations(
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
    applyOrientation();
  }

  void handlePointerDown(PointerDownEvent event) {
    gesturePointers[event.pointer] = event.position;
    if (gesturePointers.length == 3) gestureTriggered = false;
  }

  void handlePointerMove(PointerMoveEvent event) {
    if (gestureTriggered || gesturePointers.length != 3) return;
    final origin = gesturePointers[event.pointer];
    if (origin == null) return;
    final delta = event.position - origin;
    final valid = step == 0
        ? origin.dx <= 140 && delta.dx >= 90 && delta.dx.abs() > delta.dy.abs()
        : origin.dy <= 140 && delta.dy >= 90 && delta.dy.abs() > delta.dx.abs();
    if (!valid) return;
    gestureTriggered = true;
    channel.invokeMethod<void>('showOverlayDemo');
  }

  void handlePointerEnd(PointerEvent event) {
    gesturePointers.remove(event.pointer);
  }

  Future<void> finish() async {
    await channel.invokeMethod<void>('hideOverlayDemo');
    await SystemChrome.setPreferredOrientations(DeviceOrientation.values);
    await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    if (mounted) Navigator.pop(context);
  }

  @override
  void dispose() {
    channel.invokeMethod<void>('hideOverlayDemo');
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final copy = _copy(context);
    final horizontal = step == 0;
    return PopScope(
      canPop: false,
      child: Scaffold(
        body: Listener(
          behavior: HitTestBehavior.opaque,
          onPointerDown: handlePointerDown,
          onPointerMove: handlePointerMove,
          onPointerUp: handlePointerEnd,
          onPointerCancel: handlePointerEnd,
          child: SafeArea(
            child: Padding(
              padding: const EdgeInsets.all(28),
              child: Column(
                children: [
                  Text(
                    copy.title,
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  if (copy.body.isNotEmpty) ...[
                    const SizedBox(height: 6),
                    Text(copy.body, textAlign: TextAlign.center),
                  ],
                  const SizedBox(height: 12),
                  Expanded(
                    child: _ThreeFingerGestureDemo(
                      label: horizontal ? copy.landscape : copy.portrait,
                      direction: horizontal ? Axis.horizontal : Axis.vertical,
                    ),
                  ),
                  const SizedBox(height: 10),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      if (step > 0) ...[
                        OutlinedButton.icon(
                          onPressed: () async {
                            await channel.invokeMethod<void>('hideOverlayDemo');
                            gesturePointers.clear();
                            gestureTriggered = false;
                            setState(() => step = 0);
                            await applyOrientation();
                          },
                          icon: const Icon(Icons.arrow_back_rounded),
                          label: Text(
                            Localizations.localeOf(context).languageCode == 'ja'
                                ? '戻る'
                                : 'Back',
                          ),
                        ),
                        const SizedBox(width: 16),
                      ],
                      Text('${step + 1} / 2'),
                      const SizedBox(width: 24),
                      FilledButton.icon(
                        onPressed: () async {
                          if (step == 0) {
                            await channel.invokeMethod<void>('hideOverlayDemo');
                            gesturePointers.clear();
                            gestureTriggered = false;
                            setState(() => step = 1);
                            await applyOrientation();
                          } else {
                            await finish();
                          }
                        },
                        icon: Icon(
                          horizontal
                              ? Icons.arrow_forward_rounded
                              : Icons.check_rounded,
                        ),
                        label: Text(
                          horizontal
                              ? (Localizations.localeOf(context).languageCode ==
                                        'ja'
                                    ? '次へ'
                                    : 'Next')
                              : copy.close,
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
}

class _ThreeFingerGestureDemo extends StatefulWidget {
  const _ThreeFingerGestureDemo({required this.label, required this.direction});
  final String label;
  final Axis direction;

  @override
  State<_ThreeFingerGestureDemo> createState() =>
      _ThreeFingerGestureDemoState();
}

class _ThreeFingerGestureDemoState extends State<_ThreeFingerGestureDemo>
    with SingleTickerProviderStateMixin {
  late final AnimationController controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1500),
  )..repeat();
  late final Animation<double> progress = CurvedAnimation(
    parent: controller,
    curve: const Interval(0.08, 0.68, curve: Curves.easeInOutCubic),
  );
  late final Animation<double> opacity = TweenSequence<double>([
    TweenSequenceItem(
      tween: Tween(
        begin: 0.0,
        end: 0.72,
      ).chain(CurveTween(curve: Curves.easeOut)),
      weight: 8,
    ),
    TweenSequenceItem(tween: ConstantTween(0.72), weight: 62),
    TweenSequenceItem(
      tween: Tween(
        begin: 0.72,
        end: 0.0,
      ).chain(CurveTween(curve: Curves.easeInOut)),
      weight: 22,
    ),
    TweenSequenceItem(tween: ConstantTween(0.0), weight: 8),
  ]).animate(controller);

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            widget.label,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 18),
          Expanded(
            child: AnimatedBuilder(
              animation: progress,
              builder: (context, _) => LayoutBuilder(
                builder: (context, constraints) {
                  const fingerSize = 64.0;
                  const fingerGap = 18.0;
                  const groupSize = fingerSize * 3 + fingerGap * 2;
                  const edgeInset = 20.0;
                  final travel = widget.direction == Axis.horizontal
                      ? (constraints.maxWidth - fingerSize - edgeInset * 2)
                            .clamp(0.0, double.infinity)
                      : (constraints.maxHeight - fingerSize - edgeInset * 2)
                            .clamp(0.0, double.infinity);
                  final offset = travel * progress.value;
                  return Stack(
                    children: List.generate(3, (index) {
                      final left = widget.direction == Axis.horizontal
                          ? edgeInset + offset
                          : constraints.maxWidth / 2 -
                                groupSize / 2 +
                                index * (fingerSize + fingerGap);
                      final top = widget.direction == Axis.vertical
                          ? edgeInset + offset
                          : constraints.maxHeight / 2 -
                                groupSize / 2 +
                                index * (fingerSize + fingerGap);
                      return Positioned(
                        left: left,
                        top: top,
                        child: Opacity(
                          opacity: opacity.value,
                          child: Container(
                            width: fingerSize,
                            height: fingerSize,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              border: Border.all(
                                color: colors.primary,
                                width: 3,
                              ),
                              color: colors.primaryContainer.withValues(
                                alpha: .5,
                              ),
                            ),
                          ),
                        ),
                      );
                    }),
                  );
                },
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  void mutate(VoidCallback change) => setState(change);
  final bridge = NativeBridge();
  var page = 0;
  var profiles = <DisplayProfile>[];
  var profile = DisplayProfile(
    AppStrings.tr('uiTerminalResolution'),
    '240 dpi',
    1920,
    1080,
    240,
    Icons.smartphone_rounded,
    id: 'device',
    isDevice: true,
  );
  var deviceProfileInitialized = false;
  var portrait = false;
  var secure = false;
  String mirrorBackend = 'virtual_display';
  var loading = true;
  var active = false;
  var shizukuInstalled = false;
  var shizukuRunning = false;
  var shizukuGranted = false;
  String privilegeProvider = 'stellar';
  String privilegeProviderName = 'Stellar';
  var releaseCheckStarted = false;
  var releaseChecking = false;
  var releaseCheckSucceeded = false;
  String? fetchedReleaseVersion;
  String? latestReleaseVersion;
  String? latestReleaseUrl;
  String? releaseCheckError;
  DateTime? releaseCheckedAt;
  AppUpdateInfo? playUpdateInfo;
  static const distributionChannel = String.fromEnvironment(
    'DISTRIBUTION_CHANNEL',
    defaultValue: 'github',
  );
  bool get isPlayDistribution => distributionChannel == 'play';
  bool get updateAvailable => latestReleaseVersion != null;
  var secureSettingsGranted = false;
  String? error;
  String manufacturer = '';
  String model = '';
  String androidVersion = '';
  String appVersion = '';
  String desktopMode = '';
  var recovery = <String, dynamic>{};
  var androidRepair = <String, dynamic>{};
  var androidRepairCompleted = false;
  var workspaceExpanded = false;
  var homeWorkspaces = <Map<String, dynamic>>[];
  var homeApps = <String, Map<String, dynamic>>{};
  var homeAppsLoading = false;
  String desktopSettingsSection = 'display';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback(
      (_) => _initializeDeviceProfile(),
    );
    _initializeHome();
    _loadHomeSelections();
    AppAnalytics.screen('home');
  }

  Future<void> _loadHomeSelections() async {
    final preferences = await SharedPreferences.getInstance();
    if (mounted) {
      setState(() {
        secure = preferences.getBool('secure_display') ?? false;
        portrait = preferences.getBool('home_portrait') ?? false;
        mirrorBackend =
            preferences.getString('mirror_backend') ?? 'virtual_display';
      });
    }
  }

  Future<void> setPortrait(bool value) async {
    mutate(() => portrait = value);
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool('home_portrait', value);
  }

  Future<void> setSecureDisplay(bool value) async {
    mutate(() => secure = value);
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool('secure_display', value);
  }

  Future<void> setMirrorBackend(String value) async {
    mutate(() => mirrorBackend = value);
    final preferences = await SharedPreferences.getInstance();
    await preferences.setString('mirror_backend', value);
  }

  bool get effectiveDecorations => manufacturer.toLowerCase() != 'samsung';

  Future<void> _initializeHome() async {
    await Future.wait([refresh(), loadHomeWorkspaces(loadIcons: false)]);
    if (mounted && !releaseCheckStarted) {
      releaseCheckStarted = true;
      unawaited(_checkForUpdates());
    }
    if (mounted) {
      WidgetsBinding.instance.addPostFrameCallback((_) => loadHomeAppIcons());
    }
    if (mounted) await _consumeTileAction();
    if (mounted) await _showMultiTouchUpgradeNoticeIfNeeded();
  }

  Future<void> _showMultiTouchUpgradeNoticeIfNeeded() async {
    const installedVersionKey = 'last_launched_app_version';
    const noticeAcknowledgedKey = 'multi_touch_upgrade_notice_acknowledged';
    final preferences = await SharedPreferences.getInstance();
    final currentVersion = appVersion.trim();
    if (currentVersion.isEmpty) return;
    final noticeAcknowledged =
        preferences.getBool(noticeAcknowledgedKey) ?? false;
    final setupCompleted = preferences.getBool('setup_completed') ?? false;
    final upgradedAcrossGestureChange =
        setupCompleted && _compareVersions(currentVersion, '1.1.0') >= 0;
    await preferences.setString(installedVersionKey, currentVersion);
    if (noticeAcknowledged || !upgradedAcrossGestureChange) return;
    if (!mounted) return;
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        fullscreenDialog: true,
        builder: (_) => const _MultiTouchUpgradeFlow(),
      ),
    );
    await preferences.setBool(noticeAcknowledgedKey, true);
  }

  int _compareVersions(String left, String right) {
    List<int> parts(String value) => value
        .split('+')
        .first
        .split('.')
        .map((part) => int.tryParse(part) ?? 0)
        .toList(growable: false);
    final leftParts = parts(left);
    final rightParts = parts(right);
    final length = max(leftParts.length, rightParts.length);
    for (var index = 0; index < length; index++) {
      final leftPart = index < leftParts.length ? leftParts[index] : 0;
      final rightPart = index < rightParts.length ? rightParts[index] : 0;
      if (leftPart != rightPart) return leftPart.compareTo(rightPart);
    }
    return 0;
  }

  Future<void> _checkLatestGitHubRelease({bool manual = false}) async {
    if (releaseChecking) {
      return;
    }
    if (mounted) {
      mutate(() {
        releaseChecking = true;
        releaseCheckSucceeded = false;
        releaseCheckError = null;
      });
    }
    HttpClient? client;
    try {
      final status = await bridge.status();
      final current = '${status['appVersion'] ?? ''}'.trim();
      if (current.isEmpty) throw const FormatException('Missing app version');
      client = HttpClient()..connectionTimeout = const Duration(seconds: 5);
      final request = await client.getUrl(
        Uri.parse(
          'https://api.github.com/repos/NarYuki/Dextop/releases/latest',
        ),
      );
      request.headers
        ..set(HttpHeaders.acceptHeader, 'application/vnd.github+json')
        ..set(HttpHeaders.userAgentHeader, 'Dextop/$current')
        ..set('X-GitHub-Api-Version', '2022-11-28');
      final response = await request.close().timeout(
        const Duration(seconds: 8),
      );
      if (response.statusCode != HttpStatus.ok) {
        throw HttpException('GitHub API returned ${response.statusCode}');
      }
      final payload = jsonDecode(await response.transform(utf8.decoder).join());
      if (payload is! Map) {
        throw const FormatException('Invalid GitHub response');
      }
      final latest = '${payload['tag_name'] ?? ''}'.replaceFirst(
        RegExp(r'^[vV]'),
        '',
      );
      if (latest.isEmpty) {
        throw const FormatException('Missing release version');
      }
      final url = '${payload['html_url'] ?? ''}';
      final newer = _isNewerVersion(latest, current);
      if (!mounted) return;
      mutate(() {
        fetchedReleaseVersion = latest;
        latestReleaseVersion = newer ? latest : null;
        latestReleaseUrl =
            latestReleaseVersion != null &&
                url.startsWith('https://github.com/')
            ? url
            : null;
        releaseCheckedAt = DateTime.now();
        releaseCheckSucceeded = true;
      });
      if (manual && updateAvailable) await _showUpdateDialog();
    } catch (error) {
      if (mounted) {
        mutate(() {
          releaseCheckSucceeded = false;
          releaseCheckError = '$error';
          releaseCheckedAt = DateTime.now();
        });
      }
    } finally {
      client?.close(force: true);
      if (mounted) mutate(() => releaseChecking = false);
    }
  }

  Future<void> _checkForUpdates({bool manual = false}) => isPlayDistribution
      ? _checkPlayStoreUpdate(manual: manual)
      : _checkLatestGitHubRelease(manual: manual);

  Future<void> _checkPlayStoreUpdate({bool manual = false}) async {
    if (releaseChecking) return;
    mutate(() {
      releaseChecking = true;
      releaseCheckSucceeded = false;
      releaseCheckError = null;
    });
    try {
      final info = await InAppUpdate.checkForUpdate();
      final available =
          info.updateAvailability == UpdateAvailability.updateAvailable;
      if (!mounted) return;
      mutate(() {
        playUpdateInfo = available ? info : null;
        latestReleaseVersion = available
            ? info.availableVersionCode.toString()
            : null;
        latestReleaseUrl = null;
        fetchedReleaseVersion = latestReleaseVersion;
        releaseCheckedAt = DateTime.now();
        releaseCheckSucceeded = true;
      });
      if (manual && updateAvailable) await _showUpdateDialog();
    } catch (error) {
      if (!mounted) return;
      mutate(() {
        playUpdateInfo = null;
        latestReleaseVersion = null;
        releaseCheckSucceeded = false;
        releaseCheckError = '$error';
        releaseCheckedAt = DateTime.now();
      });
    } finally {
      if (mounted) mutate(() => releaseChecking = false);
    }
  }

  Future<void> _showUpdateDialog() async {
    if (!updateAvailable) return;
    final l = AppLocalizations.of(context);
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        icon: const Icon(Icons.system_update_alt_rounded),
        title: Text(
          isPlayDistribution
              ? AppStrings.tr('playUpdateAvailableTitle')
              : l.updateAvailableTitle,
        ),
        content: Text(
          isPlayDistribution
              ? AppStrings.tr('playUpdateAvailableDescription')
              : '${l.currentVersion}: $appVersion\n${l.latestVersion}: $latestReleaseVersion',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: Text(l.close),
          ),
          if (latestReleaseUrl != null)
            FilledButton(
              onPressed: () {
                Navigator.pop(dialogContext);
                bridge.openUrl(latestReleaseUrl!);
              },
              child: Text(l.openOnGitHub),
            ),
          if (isPlayDistribution && playUpdateInfo != null)
            FilledButton(
              onPressed: () async {
                Navigator.pop(dialogContext);
                await _startPlayStoreUpdate();
              },
              child: Text(AppStrings.tr('updateNow')),
            ),
        ],
      ),
    );
  }

  Future<void> _startPlayStoreUpdate() async {
    final info = playUpdateInfo;
    if (info == null) return;
    try {
      if (info.immediateUpdateAllowed) {
        await InAppUpdate.performImmediateUpdate();
      } else if (info.flexibleUpdateAllowed) {
        await InAppUpdate.startFlexibleUpdate();
        await InAppUpdate.completeFlexibleUpdate();
      }
    } catch (error) {
      if (!mounted) return;
      mutate(() => releaseCheckError = '$error');
    }
  }

  bool _isNewerVersion(String candidate, String current) {
    List<int> parts(String value) {
      final normalized = value.split('-').first.split('+').first;
      return normalized
          .split('.')
          .map(
            (part) =>
                int.tryParse(RegExp(r'\d+').stringMatch(part) ?? '0') ?? 0,
          )
          .toList();
    }

    final latestParts = parts(candidate);
    final currentParts = parts(current);
    final length = max(latestParts.length, currentParts.length);
    for (var index = 0; index < length; index++) {
      final latestPart = index < latestParts.length ? latestParts[index] : 0;
      final currentPart = index < currentParts.length ? currentParts[index] : 0;
      if (latestPart != currentPart) return latestPart > currentPart;
    }
    return false;
  }

  Future<void> _consumeTileAction() async {
    if (!mounted || !await bridge.consumeTileAction()) return;
    await loadHomeWorkspaces();
    final preferences = await SharedPreferences.getInstance();
    final lastId = preferences.getString('last_workspace_id');
    final matches = homeWorkspaces.where((item) => '${item['id']}' == lastId);
    if (matches.isNotEmpty) {
      await launchHomeWorkspace(matches.first);
    } else if (mounted) {
      setState(() => workspaceExpanded = true);
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _initializeDeviceProfile();
  }

  void _initializeDeviceProfile() {
    if (deviceProfileInitialized) return;
    final views = WidgetsBinding.instance.platformDispatcher.views;
    if (views.isEmpty) return;
    final physical = views.first.physicalSize;
    final width = physical.width.round();
    final height = physical.height.round();
    if (width < 480 || height < 480) {
      Future<void>.delayed(Duration(milliseconds: 100), () {
        if (mounted) _initializeDeviceProfile();
      });
      return;
    }
    final landscapeWidth = width > height ? width : height;
    final landscapeHeight = width > height ? height : width;
    // Choose a readable density for the initial device profile. A density
    // saved by the user takes precedence on subsequent launches.
    final deviceDensity = (160 + (views.first.devicePixelRatio * 24))
        .round()
        .clamp(160, 320);
    final deviceProfile = DisplayProfile(
      '${AppStrings.tr('automaticResolution')} ($landscapeWidth × $landscapeHeight)',
      '$deviceDensity dpi',
      landscapeWidth,
      landscapeHeight,
      deviceDensity,
      Icons.smartphone_rounded,
      id: 'device',
      isDevice: true,
    );
    profiles = [deviceProfile];
    profile = deviceProfile;
    deviceProfileInitialized = true;
    _loadProfiles(deviceProfile, deviceDensity);
  }

  Future<void> _loadProfiles(
    DisplayProfile deviceProfile,
    int defaultDeviceDensity,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    final deviceDpi = defaultDeviceDensity;
    final device = DisplayProfile(
      deviceProfile.name,
      '$deviceDpi dpi',
      deviceProfile.width,
      deviceProfile.height,
      deviceDpi,
      deviceProfile.icon,
      id: 'device',
      isDevice: true,
    );
    final custom = <DisplayProfile>[];
    try {
      final decoded =
          jsonDecode(prefs.getString('custom_resolution_profiles') ?? '[]')
              as List<dynamic>;
      custom.addAll(
        decoded.map(
          (item) =>
              DisplayProfile.fromJson(Map<String, dynamic>.from(item as Map)),
        ),
      );
    } catch (_) {
      await prefs.remove('custom_resolution_profiles');
    }
    final selectedId = prefs.getString('selected_resolution_id') ?? 'device';
    if (!mounted) return;
    setState(() {
      profiles = [device, ...custom];
      profile =
          profiles.where((item) => item.id == selectedId).firstOrNull ?? device;
    });
  }

  Future<void> _saveProfiles() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('device_resolution_dpi');
    await prefs.setString(
      'custom_resolution_profiles',
      jsonEncode(
        profiles
            .where((item) => !item.isDevice)
            .map((item) => item.toJson())
            .toList(),
      ),
    );
    await prefs.setString('selected_resolution_id', profile.id);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      refresh();
      _consumeTileAction();
      if (profiles.isNotEmpty) {
        _loadProfiles(profiles.first, profiles.first.density);
      }
    }
  }

  @override
  void didChangeMetrics() {
    // Foldables and tri-folds can replace the app view with a different
    // physical size without restarting Flutter. Rebuild the device profile so
    // Home reflects the current panel's resolution and calculated DPI.
    deviceProfileInitialized = false;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _initializeDeviceProfile();
    });
  }

  Future<void> refresh() async {
    try {
      var value = await bridge.status();
      for (
        var attempt = 0;
        attempt < 8 && value['shizukuRunning'] != true;
        attempt++
      ) {
        await Future<void>.delayed(Duration(milliseconds: 250));
        value = await bridge.status();
      }
      if (value['shizukuGranted'] == true && value['privileged'] != true) {
        await bridge.requestShizuku();
        value = await bridge.status();
      }
      final recoveryValue = await bridge.recovery();
      final repairValue = await bridge.repairState();
      if (recoveryValue['phase'] == 'paused') {
        repairValue['required'] = false;
        repairValue['pausedByUser'] = true;
      }
      if (!mounted) return;
      setState(() {
        active = value['active'] == true;
        shizukuInstalled = value['shizukuInstalled'] == true;
        shizukuRunning = value['shizukuRunning'] == true;
        shizukuGranted = value['shizukuGranted'] == true;
        privilegeProvider = '${value['privilegeProvider'] ?? 'stellar'}';
        privilegeProviderName =
            '${value['privilegeProviderName'] ?? 'Stellar'}';
        secureSettingsGranted = value['privileged'] == true;
        manufacturer = '${value['manufacturer'] ?? ''}';
        model = '${value['model'] ?? ''}';
        androidVersion = '${value['androidVersion'] ?? ''}';
        appVersion = '${value['appVersion'] ?? ''}';
        desktopMode = '${value['desktopMode'] ?? ''}';
        recovery = recoveryValue;
        androidRepair = repairValue;
        loading = false;
        error = null;
      });
    } on PlatformException catch (e) {
      if (!mounted) return;
      setState(() {
        loading = false;
        error = e.message;
      });
    }
  }

  Future<void> loadHomeWorkspaces({bool loadIcons = true}) async {
    final preferences = await SharedPreferences.getInstance();
    var decoded = <dynamic>[];
    try {
      decoded = jsonDecode(preferences.getString('workspaces') ?? '[]') as List;
    } catch (_) {
      decoded = [];
    }
    if (!mounted) return;
    setState(() {
      homeWorkspaces = decoded
          .map((item) => Map<String, dynamic>.from(item as Map))
          .toList();
    });
    if (loadIcons) await loadHomeAppIcons();
  }

  Future<void> loadHomeAppIcons() async {
    if (homeAppsLoading) return;
    setState(() => homeAppsLoading = true);
    try {
      final rawApps = await bridge.apps();
      if (!mounted) return;
      setState(() {
        homeApps = {
          for (final item in rawApps)
            '${(item as Map)['package']}': Map<String, dynamic>.from(item),
        };
        homeAppsLoading = false;
      });
    } catch (_) {
      if (mounted) setState(() => homeAppsLoading = false);
    }
  }

  Future<void> launchHomeWorkspace(Map<String, dynamic> workspace) async {
    final packages = (workspace['apps'] as List).cast<String>();
    final positions = workspace['positions'] is Map
        ? Map<String, dynamic>.from(workspace['positions'] as Map)
        : <String, dynamic>{};
    final savedBounds = workspace['bounds'] is Map
        ? Map<String, dynamic>.from(workspace['bounds'] as Map)
        : <String, dynamic>{};
    final preferences = await SharedPreferences.getInstance();
    await preferences.setString('last_workspace_id', '${workspace['id']}');
    if (!await ensureDesktopRunning()) return;
    for (var index = 0; index < packages.length; index++) {
      final position = positions[packages[index]] as String?;
      final exactBounds = savedBounds[packages[index]];
      final column = index % 2;
      final row = (index ~/ 2).clamp(0, 1);
      await bridge.launchApp(
        packages[index],
        position: exactBounds is List ? null : position,
        bounds: exactBounds is List && exactBounds.length == 4
            ? exactBounds.cast<int>()
            : position == null
            ? [column * 960, row * 540, (column + 1) * 960, (row + 1) * 540]
            : null,
      );
      await Future<void>.delayed(Duration(milliseconds: 350));
    }
  }

  Future<void> connect() async {
    if (!shizukuInstalled || !shizukuRunning) {
      await bridge.openShizuku();
      return;
    }
    setState(() => loading = true);
    try {
      await bridge.requestShizuku();
      await refresh();
    } on PlatformException catch (e) {
      if (!mounted) return;
      setState(() {
        loading = false;
        error = e.message;
      });
    }
  }

  Future<void> toggleDisplay() async {
    if (loading) return;
    if (!active && recovery['recoverable'] == true) return;
    setState(() {
      loading = true;
      error = null;
    });
    try {
      if (active) {
        await bridge.stop();
      } else {
        await bridge.start(
          profile,
          portrait,
          secure,
          decorations: effectiveDecorations,
        );
      }
      await Future<void>.delayed(Duration(milliseconds: 350));
      await refresh();
    } on PlatformException catch (e) {
      if (!mounted) return;
      setState(() {
        loading = false;
        error = e.message;
      });
    }
  }

  Future<bool> ensureDesktopRunning() async {
    if (active) return true;
    if (loading ||
        !secureSettingsGranted ||
        !shizukuRunning ||
        !shizukuGranted ||
        recovery['recoverable'] == true) {
      return false;
    }
    setState(() {
      loading = true;
      error = null;
    });
    try {
      await bridge.start(
        profile,
        portrait,
        secure,
        decorations: effectiveDecorations,
      );
      await Future<void>.delayed(Duration(milliseconds: 450));
      await refresh();
      return active;
    } on PlatformException catch (e) {
      if (mounted) {
        setState(() {
          loading = false;
          error = e.message;
        });
      }
      return false;
    }
  }

  @override
  Widget build(BuildContext context) {
    final desktopLandscape =
        widget.desktopWindow &&
        MediaQuery.orientationOf(context) == Orientation.landscape;
    if (desktopLandscape) {
      return Scaffold(
        body: Row(
          children: [
            LayoutBuilder(
              builder: (context, constraints) => NavigationRail(
                extended: MediaQuery.sizeOf(context).width >= 1000,
                selectedIndex: page,
                onDestinationSelected: selectPage,
                leading: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 20),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(9),
                    child: Image.asset(
                      'assets/dextop_icon.png',
                      width: 36,
                      height: 36,
                      fit: BoxFit.cover,
                    ),
                  ),
                ),
                destinations: [
                  NavigationRailDestination(
                    icon: const Icon(Icons.space_dashboard_outlined),
                    selectedIcon: const Icon(Icons.space_dashboard_rounded),
                    label: Text(AppStrings.tr('home')),
                  ),
                  NavigationRailDestination(
                    icon: _settingsNavigationIcon(Icons.tune_outlined),
                    selectedIcon: _settingsNavigationIcon(Icons.tune_rounded),
                    label: Text(AppStrings.tr('settings')),
                  ),
                ],
              ),
            ),
            const VerticalDivider(width: 1),
            Expanded(child: pageContent()),
          ],
        ),
      );
    }
    return Scaffold(
      body: pageContent(),
      bottomNavigationBar: NavigationBar(
        selectedIndex: page,
        onDestinationSelected: selectPage,
        destinations: [
          NavigationDestination(
            icon: Icon(Icons.home_outlined),
            selectedIcon: Icon(Icons.home_rounded),
            label: AppStrings.tr('home'),
          ),
          NavigationDestination(
            icon: _settingsNavigationIcon(Icons.tune_outlined),
            selectedIcon: _settingsNavigationIcon(Icons.tune_rounded),
            label: AppStrings.tr('settings'),
          ),
        ],
      ),
    );
  }

  Widget pageContent() => AnimatedSwitcher(
    duration: const Duration(milliseconds: 350),
    switchInCurve: Curves.easeOutCubic,
    switchOutCurve: Curves.easeInCubic,
    child: page == 0 ? overview() : settings(),
  );

  void selectPage(int value) {
    setState(() => page = value);
    AppAnalytics.screen(value == 0 ? 'home' : 'settings');
  }

  Widget _settingsNavigationIcon(IconData icon) => Badge(
    isLabelVisible: updateAvailable,
    smallSize: 8,
    backgroundColor: Colors.red,
    child: Icon(icon),
  );
}
