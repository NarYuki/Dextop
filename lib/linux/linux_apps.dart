import '../l10n/inline_text.dart';

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import 'linux_app_mode.dart';
import 'linux_expressive.dart';
import 'linux_installations.dart';
import 'linux_installation_icon.dart';
import 'linux_widgets.dart';

/// A desktop entry (application) of a Linux container.
class LinuxAppEntry {
  LinuxAppEntry(Map<Object?, Object?> json, this.container)
    : desktopId = json['id'] as String? ?? '',
      name = json['name'] as String? ?? '',
      categories = switch (json['categories']) {
        final List<Object?> list => list.whereType<String>().toList(),
        final String text =>
          text.split(';').where((c) => c.isNotEmpty).toList(),
        _ => const <String>[],
      },
      iconFilePath = json['iconFile'] as String?,
      iconBase64 = json['icon'] as String? ?? '',
      icon = _decode(json['icon'] as String?);

  final String container;
  final String desktopId;
  final String name;
  final List<String> categories;
  final String iconBase64;
  final Uint8List? icon;
  final String? iconFilePath;

  String get key => '$container:$desktopId';

  static Uint8List? _decode(String? encoded) {
    if (encoded == null || encoded.isEmpty) return null;
    try {
      return base64Decode(encoded.replaceAll('\n', ''));
    } catch (_) {
      return null;
    }
  }
}

/// An open window (session) of a Linux application shown as an Android window.
class LinuxAppSession {
  LinuxAppSession(Map<Object?, Object?> json)
    : container = json['container'] as String? ?? '',
      desktopId = json['desktop'] as String?,
      xid = (json['xid'] as num?)?.toInt() ?? 0,
      title = json['title'] as String? ?? '';

  final String container;
  final String? desktopId;
  final int xid;
  final String title;

  String get key => '$container:$desktopId';
}

/// The applications of a container, which ones have a home-screen launcher, and their open windows.
class LinuxAppList extends ChangeNotifier {
  LinuxAppList({this.trackInstallations = true});
  final bool trackInstallations;
  static const _channel = MethodChannel('app.freedextop/linux');

  List<LinuxAppEntry> apps = const [];
  Set<String> shortcuts = const {};
  bool loading = false;
  String? error;

  /// Open windows by application key ("container:desktopId").
  Map<String, List<LinuxAppSession>> sessions = const {};
  Timer? _sessionPoll;
  List<String> _containers = const [];
  bool _pollingInstallations = false;
  bool _disposed = false;
  final _tileKeys = <String, String>{};

  List<AppInstallation> get installations => [
    for (final container in _containers)
      ...LinuxInstallations.instance.forContainer(container),
  ];

  AppInstallation? installationOf(LinuxAppEntry entry) {
    for (final item in installations) {
      if (item.container == entry.container &&
          (entry.desktopId == '@install:${item.package}' ||
              item.matches(entry.desktopId))) {
        _tileKeys[entry.key] = item.key;
        return item;
      }
    }
    return null;
  }

  String tileKey(LinuxAppEntry entry) =>
      installationOf(entry)?.key ?? _tileKeys[entry.key] ?? entry.key;

  List<LinuxAppEntry> get displayedApps {
    final result = [...apps];
    for (final item in installations) {
      if (apps.any(
        (entry) =>
            entry.container == item.container && item.matches(entry.desktopId),
      )) {
        continue;
      }
      result.add(
        LinuxAppEntry({
          'id': '@install:${item.package}',
          'name': item.name,
          'categories': item.app?.categories ?? const <String>[],
          'iconFile': item.iconPath,
        }, item.container),
      );
    }
    result.sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
    return result;
  }

  void _installationsChanged() {
    if (!_disposed) notifyListeners();
  }

  Future<void> refreshInstallations() async {
    if (!trackInstallations || _pollingInstallations || _disposed) return;
    _pollingInstallations = true;
    try {
      for (final container in List<String>.of(_containers)) {
        final raw =
            await _channel.invokeListMethod<Object?>('storeJobs', {
              'id': container,
              'peek': true,
            }) ??
            const [];
        if (_disposed) return;
        final jobs = raw.cast<Map<Object?, Object?>>();
        final active = <String>{
          for (final job in jobs)
            if (job['action'] == 'install' &&
                const ['queued', 'running'].contains(job['state']))
              ...((job['packages'] as List?) ?? const []).cast<String>(),
        };
        if (LinuxInstallations.instance
            .forContainer(container)
            .any((item) => !active.contains(item.package))) {
          await loadAll(List<String>.of(_containers));
          if (_disposed) return;
        }
        LinuxInstallations.instance.snapshot(container, jobs);
      }
    } on PlatformException {
      // Retain visible jobs during a transient service disconnect.
    } finally {
      _pollingInstallations = false;
    }
  }

  List<LinuxAppSession> sessionsOf(LinuxAppEntry entry) =>
      sessions[entry.key] ?? const [];

  /// Follows the open windows while the list is on screen.
  void track() {
    if (_sessionPoll != null) return;
    LinuxInstallations.instance.addListener(_installationsChanged);
    _sessionPoll ??= Timer.periodic(const Duration(seconds: 2), (_) {
      refreshSessions();
      refreshInstallations();
    });
    refreshSessions();
  }

  Future<void> refreshSessions() async {
    try {
      final list =
          await _channel.invokeListMethod<Object?>('sessions') ?? const [];
      if (_disposed) return;
      final next = <String, List<LinuxAppSession>>{};
      for (final session in list.cast<Map<Object?, Object?>>().map(
        LinuxAppSession.new,
      )) {
        if (session.desktopId == null) continue;
        next.putIfAbsent(session.key, () => []).add(session);
      }
      final changed =
          next.length != sessions.length ||
          next.entries.any((e) => sessions[e.key]?.length != e.value.length);
      sessions = next;
      if (changed) notifyListeners();
    } on PlatformException {
      // Plasma windows are not listed; nothing to show.
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _sessionPoll?.cancel();
    LinuxInstallations.instance.removeListener(_installationsChanged);
    super.dispose();
  }

  Future<void> load(String container) => loadAll([container]);

  Future<void> loadAll(List<String> containers) async {
    _containers = List.of(containers);
    loading = true;
    notifyListeners();
    final next = <LinuxAppEntry>[];
    try {
      for (final container in containers) {
        final list =
            await _channel.invokeListMethod<Object?>('apps', {
              'id': container,
            }) ??
            const [];
        next.addAll(
          list.cast<Map<Object?, Object?>>().map(
            (json) => LinuxAppEntry(json, container),
          ),
        );
      }
      next.sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
      apps = next;
      for (final entry in apps) {
        installationOf(entry);
      }
      shortcuts =
          ((await _channel.invokeListMethod<Object?>('shortcuts')) ?? const [])
              .cast<String>()
              .toSet();
      error = null;
    } on PlatformException catch (e) {
      error = e.message;
    }
    if (_disposed) return;
    loading = false;
    notifyListeners();
    if (!_pollingInstallations) await refreshInstallations();
  }

  /// Opens [entry] in a new session: in the Plasma desktop when it runs, otherwise as an
  /// Android window with the application's name and icon.
  static Future<void> launch(String container, LinuxAppEntry entry) =>
      _channel.invokeMethod('launchApp', {
        'id': container,
        'desktop': entry.desktopId,
        'label': entry.name,
        'icon': entry.iconBase64,
      });

  static Future<bool> focus(LinuxAppSession session) async =>
      await _channel.invokeMethod<bool>('focusSession', {
        'id': session.container,
        'xid': session.xid,
      }) ==
      true;

  static Future<Uint8List?> capture(LinuxAppSession session) =>
      _channel.invokeMethod<Uint8List>('sessionCapture', {
        'id': session.container,
        'xid': session.xid,
      });

  /// Asks every window of [entry] to close, as their close buttons would.
  static Future<void> close(LinuxAppEntry entry) => _channel.invokeMethod(
    'closeApp',
    {'id': entry.container, 'desktop': entry.desktopId},
  );

  /// Removes the package that installed [entry]; false when none was found or apt failed.
  static Future<bool> uninstall(LinuxAppEntry entry) async {
    final package = await _channel.invokeMethod<String>('appPackage', {
      'id': entry.container,
      'desktop': entry.desktopId,
    });
    if (package == null || package.isEmpty) return false;
    final started = await _channel.invokeMethod<bool>('storeRun', {
      'id': entry.container,
      'action': 'remove',
      'packages': [package],
    });
    if (started != true) return false;
    while (true) {
      await Future<void>.delayed(const Duration(seconds: 2));
      final jobs =
          await _channel.invokeListMethod<Object?>('storeJobs', {
            'id': entry.container,
          }) ??
          const [];
      for (final job in jobs.cast<Map<Object?, Object?>>()) {
        final packages = ((job['packages'] as List?) ?? const [])
            .cast<String>();
        if (!packages.contains(package) || job['state'] == 'running') continue;
        return job['state'] == 'done';
      }
    }
  }

  /// Makes and installs the small launcher APK that puts [entry] on the home screen.
  static Future<void> addToHome(
    BuildContext context,
    String container,
    LinuxAppEntry entry,
  ) async {
    String t(String ja, String en) => linuxText(context, ja, en);
    try {
      final silent = await _silentInstall(context);
      if (silent == null || !context.mounted) return;
      final status = await _channel.invokeMethod<String>('createShortcut', {
        'id': container,
        'desktop': entry.desktopId,
        'label': entry.name,
        'icon': entry.iconBase64,
        'silent': silent,
      });
      if (!context.mounted) return;
      M3ESnackbar.show(
        context,
        message: status == 'permission'
            ? t(
                '「不明なアプリのインストール」を許可してから、もう一度追加してください',
                'Allow installing unknown apps, then add it again',
              )
            : status == 'installed'
            ? inlineFormat(
                t('{0} をホーム画面に追加しました', 'Added {0} to the home screen'),
                [entry.name],
              )
            : inlineFormat(
                t('{0} をホーム画面に追加しています', 'Adding {0} to the home screen'),
                [entry.name],
              ),
      );
    } on PlatformException catch (e) {
      if (context.mounted) {
        M3ESnackbar.show(context, message: e.message ?? '$e');
      }
    }
  }

  /// Whether to install the launcher without Android's confirmation (Shizuku, Stellar or
  /// Dextop's built-in access): asked the first time, then as set in the Linux settings.
  /// Null when the question was dismissed.
  static Future<bool?> _silentInstall(BuildContext context) async {
    final info =
        await _channel.invokeMapMethod<Object?, Object?>('launcherInstall') ??
        const {};
    final available = info['silentAvailable'] == true;
    final mode = info['mode'] as String? ?? 'ask';
    if (info['pinned'] == true || !available || mode == 'manual') return false;
    if (mode == 'auto') return true;
    if (!context.mounted) return null;
    String t(String ja, String en) => linuxText(context, ja, en);
    final auto = await M3EDialog.show<bool>(
      context,
      dialog: Builder(
        builder: (dialog) => M3EDialog(
          icon: const Icon(M3EIcons.install_mobile_rounded),
          title: t('ランチャーを自動でインストールしますか？', 'Install launchers automatically?'),
          content: Text(
            t(
              'Shizuku・Stellar・内蔵バインダで、ホーム画面のランチャーを確認なしでインストールします。'
                  '「手動」を選ぶと、Android のインストール画面で毎回確認します。あとで Linux の設定から変更できます。',
              'Dextop installs the small home-screen APK without confirmation through Shizuku, Stellar or its built-in access. '
                  'With "Manually", Android asks each time. You can change this later in the Linux settings.',
            ),
          ),
          actions: [
            M3EButton.text(
              onPressed: () => Navigator.pop(dialog, false),
              child: Text(t('手動', 'Manually')),
            ),
            M3EButton.filled(
              onPressed: () => Navigator.pop(dialog, true),
              child: Text(t('自動でインストール', 'Install automatically')),
            ),
          ],
        ),
      ),
    );
    if (auto == null) return null;
    await _channel.invokeMethod('setLauncherInstall', {
      'mode': auto ? 'auto' : 'manual',
    });
    return auto;
  }

  static Future<void> removeFromHome(String container, LinuxAppEntry entry) =>
      _channel.invokeMethod('removeShortcut', {
        'id': container,
        'desktop': entry.desktopId,
      });
}

/// The application's icon from its desktop entry.
class LinuxAppIcon extends StatelessWidget {
  const LinuxAppIcon({super.key, required this.entry, this.size = 56});

  final LinuxAppEntry entry;
  final double size;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return ClipRRect(
      borderRadius: BorderRadius.circular(size * .3),
      child: Container(
        width: size,
        height: size,
        color: scheme.surfaceContainerHigh,
        padding: EdgeInsets.all(size * .12),
        child: entry.iconFilePath != null && entry.icon == null
            ? Image.file(
                File(entry.iconFilePath!),
                fit: BoxFit.contain,
                errorBuilder: (_, _, _) =>
                    Icon(M3EIcons.apps_rounded, color: scheme.primary),
              )
            : entry.icon == null
            ? Icon(
                M3EIcons.apps_rounded,
                color: scheme.primary,
                size: size * .55,
              )
            : Image.memory(
                entry.icon!,
                fit: BoxFit.contain,
                gaplessPlayback: true,
              ),
      ),
    );
  }
}

/// A grid of the Linux applications; tap opens, long press shows what else can be done.
class LinuxAppsGrid extends StatelessWidget {
  const LinuxAppsGrid({
    super.key,
    required this.list,
    required this.onReload,
    this.containerNames = const {},
  });

  final LinuxAppList list;
  final Future<void> Function() onReload;

  /// Shown under the names when applications of several containers are listed.
  final Map<String, String> containerNames;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: list,
      builder: (context, _) {
        final apps = list.displayedApps;
        return SliverPadding(
          padding: linuxWide(context)
              ? const EdgeInsets.fromLTRB(20, 4, 20, 120)
              : const EdgeInsets.fromLTRB(12, 8, 12, 120),
          sliver: SliverGrid.builder(
            gridDelegate: SliverGridDelegateWithMaxCrossAxisExtent(
              maxCrossAxisExtent: linuxWide(context) ? 128 : 112,
              mainAxisExtent: 128,
              crossAxisSpacing: 4,
              mainAxisSpacing: 4,
            ),
            findChildIndexCallback: (key) {
              if (key is! ValueKey<String>) return null;
              final index = apps.indexWhere(
                (entry) => list.tileKey(entry) == key.value,
              );
              return index < 0 ? null : index;
            },
            itemCount: apps.length,
            itemBuilder: (context, index) => _AppCell(
              key: ValueKey(list.tileKey(apps[index])),
              entry: apps[index],
              list: list,
              index: index,
              onReload: onReload,
              containerName: containerNames.length > 1
                  ? containerNames[apps[index].container]
                  : null,
            ),
          ),
        );
      },
    );
  }
}

class _AppCell extends StatefulWidget {
  const _AppCell({
    super.key,
    required this.entry,
    required this.list,
    required this.index,
    required this.onReload,
    this.containerName,
  });

  final LinuxAppEntry entry;
  final LinuxAppList list;
  final int index;
  final Future<void> Function() onReload;
  final String? containerName;

  @override
  State<_AppCell> createState() => _AppCellState();
}

class _AppCellState extends State<_AppCell>
    with SingleTickerProviderStateMixin {
  // Cells come in one after another when the list first shows.
  late final AnimationController appear = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 360),
  );
  bool pressed = false;

  String t(String ja, String en) => linuxText(context, ja, en);

  @override
  void initState() {
    super.initState();
    Future.delayed(Duration(milliseconds: 18 * widget.index.clamp(0, 20)), () {
      if (mounted) appear.forward();
    });
  }

  @override
  void dispose() {
    appear.dispose();
    super.dispose();
  }

  /// No window: a new session. One: back to it. Several: choose among them.
  Future<void> _open() async {
    final entry = widget.entry;
    final sessions = widget.list.sessionsOf(entry);
    if (sessions.isEmpty) {
      await LinuxAppList.launch(entry.container, entry);
    } else if (sessions.length == 1) {
      if (!await LinuxAppList.focus(sessions.first)) {
        await LinuxAppList.launch(entry.container, entry);
      }
    } else {
      await showLinuxSessionPicker(context, entry, sessions);
    }
  }

  @override
  Widget build(BuildContext context) {
    final entry = widget.entry;
    final theme = Theme.of(context);
    final pinned = widget.list.shortcuts.contains(entry.key);
    final count = widget.list.sessionsOf(entry).length;
    final installation = widget.list.installationOf(entry);
    final curve = CurvedAnimation(parent: appear, curve: Curves.easeOutCubic);
    return FadeTransition(
      opacity: curve,
      child: SlideTransition(
        position: Tween(
          begin: const Offset(0, .08),
          end: Offset.zero,
        ).animate(curve),
        child: GestureDetector(
          onTapDown: (_) => setState(() => pressed = true),
          onTapCancel: () => setState(() => pressed = false),
          onTapUp: (_) => setState(() => pressed = false),
          onTap: installation == null ? _open : null,
          onSecondaryTapUp: (_) {
            setState(() => pressed = false);
            _menu(context);
          },
          onLongPress: () {
            setState(() => pressed = false);
            HapticFeedback.mediumImpact();
            _menu(context);
          },
          child: AnimatedScale(
            scale: pressed ? .92 : 1,
            duration: const Duration(milliseconds: 160),
            curve: Curves.easeOutBack,
            // Icons sit on one line whatever the length of the names below them.
            child: Column(
              mainAxisAlignment: MainAxisAlignment.start,
              children: [
                const SizedBox(height: 8),
                Stack(
                  clipBehavior: Clip.none,
                  children: [
                    LinuxInstallationIcon(
                      installation: installation,
                      child: LinuxAppIcon(entry: entry),
                    ),
                    if (pinned)
                      Positioned(
                        right: -4,
                        bottom: -4,
                        child: CircleAvatar(
                          radius: 10,
                          backgroundColor: theme.colorScheme.primary,
                          child: Icon(
                            M3EIcons.home_rounded,
                            size: 12,
                            color: theme.colorScheme.onPrimary,
                          ),
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 8),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  alignment: Alignment.topCenter,
                  // The number of open sessions sits left of the name.
                  child: Text.rich(
                    TextSpan(
                      children: [
                        if (count > 0) ...[
                          WidgetSpan(
                            alignment: PlaceholderAlignment.middle,
                            child: AnimatedContainer(
                              duration: const Duration(milliseconds: 200),
                              padding: const EdgeInsets.symmetric(
                                horizontal: 6,
                                vertical: 1,
                              ),
                              decoration: BoxDecoration(
                                color: theme.colorScheme.primary,
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Text(
                                '$count',
                                style: theme.textTheme.labelSmall?.copyWith(
                                  color: theme.colorScheme.onPrimary,
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                            ),
                          ),
                          const TextSpan(text: ' '),
                        ],
                        TextSpan(text: entry.name),
                      ],
                    ),
                    maxLines: 2,
                    textAlign: TextAlign.center,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.labelMedium,
                  ),
                ),
                if (installation != null)
                  Text(
                    installation.queued
                        ? t('待機中', 'Queued')
                        : installation.percent < 0
                        ? t('インストール中', 'Installing')
                        : '${installation.percent.clamp(0, 100)}%',
                    maxLines: 1,
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  )
                else if (widget.containerName != null)
                  Text(
                    widget.containerName!,
                    maxLines: 1,
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: theme.colorScheme.outline,
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _menu(BuildContext context) async {
    final entry = widget.entry;
    final pinned = widget.list.shortcuts.contains(entry.key);
    final count = widget.list.sessionsOf(entry).length;
    final installation = widget.list.installationOf(entry);
    if (installation != null) {
      await M3EBottomSheet.show<void>(
        context,
        builder: (sheet) => Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(entry.name, style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 12),
              Text(
                installation.queued
                    ? t('インストール待機中', 'Waiting to install')
                    : t('インストール中', 'Installing'),
              ),
              if (installation.queued) ...[
                const SizedBox(height: 16),
                M3EButton.text(
                  onPressed: () async {
                    Navigator.of(sheet).pop();
                    await const MethodChannel('app.freedextop/linux')
                        .invokeMethod<bool>('storeCancel', {
                          'id': installation.container,
                          'name': installation.package,
                        });
                    await widget.list.refreshInstallations();
                  },
                  child: Text(t('キューから削除', 'Remove from queue')),
                ),
              ],
            ],
          ),
        ),
      );
      return;
    }
    final theme = Theme.of(context);
    final action = await M3EBottomSheet.show<String>(
      context,
      builder: (sheet) => Padding(
        padding: const EdgeInsets.fromLTRB(8, 0, 8, 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
              child: Row(
                children: [
                  LinuxAppIcon(entry: entry, size: 48),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          entry.name,
                          style: Theme.of(sheet).textTheme.titleLarge,
                        ),
                        if (count > 0)
                          Text(
                            inlineFormat(
                              t('{0} 個のセッションが開いています', '{0} sessions open'),
                              [count],
                            ),
                            style: Theme.of(sheet).textTheme.bodySmall,
                          ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            LinuxMenuRow(
              title: t('開く', 'Open'),
              icon: M3EIcons.play_arrow_rounded,
              onTap: () => Navigator.pop(sheet, 'open'),
            ),
            if (count > 0)
              LinuxMenuRow(
                title: t('新しいセッションで開く', 'Open in a new session'),
                icon: M3EIcons.add_to_queue_rounded,
                onTap: () => Navigator.pop(sheet, 'new'),
              ),
            LinuxMenuRow(
              title: pinned
                  ? t('ホーム画面から削除', 'Remove from Home')
                  : t('ホーム画面に追加', 'Add to Home'),
              subtitle: pinned
                  ? null
                  : t('Androidのアプリとして表示します', 'Shows it like an Android app'),
              icon: pinned
                  ? M3EIcons.delete_outline_rounded
                  : M3EIcons.add_to_home_screen_rounded,
              onTap: () => Navigator.pop(sheet, 'home'),
            ),
            LinuxMenuRow(
              title: t('動作方式', 'How it runs'),
              subtitle: t(
                '表示方式・描画方式・OpenGL のバージョン',
                'Display system, renderer and OpenGL version',
              ),
              icon: M3EIcons.tune_rounded,
              onTap: () => Navigator.pop(sheet, 'mode'),
            ),
            if (count > 0)
              LinuxMenuRow(
                title: t('アプリを閉じる', 'Close app'),
                subtitle: t('開いているウィンドウをすべて閉じます', 'Closes all of its windows'),
                icon: M3EIcons.close_rounded,
                onTap: () => Navigator.pop(sheet, 'close'),
              ),
            LinuxMenuRow(
              title: t('アンインストール', 'Uninstall'),
              icon: M3EIcons.delete_forever_rounded,
              onTap: () => Navigator.pop(sheet, 'uninstall'),
            ),
          ],
        ),
      ),
    );
    if (action == null || !context.mounted) return;
    switch (action) {
      case 'open':
        await _open();
      case 'new':
        await LinuxAppList.launch(entry.container, entry);
      case 'home':
        if (pinned) {
          await LinuxAppList.removeFromHome(entry.container, entry);
        } else {
          await LinuxAppList.addToHome(context, entry.container, entry);
        }
      case 'close':
        await LinuxAppList.close(entry);
        await Future<void>.delayed(const Duration(milliseconds: 600));
        await widget.list.refreshSessions();
      case 'mode':
        await Navigator.of(context).push<void>(
          MaterialPageRoute(
            builder: (_) => LinuxAppModePage(
              container: entry.container,
              desktopId: entry.desktopId,
              name: entry.name,
            ),
          ),
        );
      case 'uninstall':
        await _uninstall(context, theme);
    }
  }

  Future<void> _uninstall(BuildContext context, ThemeData theme) async {
    final entry = widget.entry;
    final ok = await M3EDialog.show<bool>(
      context,
      dialog: Builder(
        builder: (dialog) => M3EDialog(
          title: inlineFormat(t('{0} をアンインストールしますか？', 'Uninstall {0}?'), [
            entry.name,
          ]),
          icon: const Icon(M3EIcons.delete_forever_rounded),
          content: Text(
            t(
              'このアプリのパッケージをコンテナから削除します。',
              'Removes the app\'s package from the container.',
            ),
          ),
          actions: [
            M3EButton.text(
              onPressed: () => Navigator.pop(dialog, false),
              child: Text(t('キャンセル', 'Cancel')),
            ),
            M3EButton.filled(
              onPressed: () => Navigator.pop(dialog, true),
              child: Text(t('アンインストール', 'Uninstall')),
            ),
          ],
        ),
      ),
    );
    if (ok != true || !context.mounted) return;
    M3ESnackbar.show(
      context,
      message: inlineFormat(t('{0} をアンインストールしています', 'Uninstalling {0}'), [
        entry.name,
      ]),
    );
    final done = await LinuxAppList.uninstall(entry);
    if (!context.mounted) return;
    M3ESnackbar.show(
      context,
      message: done
          ? inlineFormat(t('{0} をアンインストールしました', '{0} uninstalled'), [
              entry.name,
            ])
          : inlineFormat(
              t('{0} をアンインストールできませんでした', 'Could not uninstall {0}'),
              [entry.name],
            ),
    );
    if (done) await widget.onReload();
  }
}

/// Lets the user pick one of an application's open windows by its picture, or start another.
Future<void> showLinuxSessionPicker(
  BuildContext context,
  LinuxAppEntry entry,
  List<LinuxAppSession> sessions,
) {
  String t(String ja, String en) => linuxText(context, ja, en);
  return M3EBottomSheet.show<void>(
    context,
    builder: (sheet) {
      final theme = Theme.of(sheet);
      return Padding(
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                LinuxAppIcon(entry: entry, size: 40),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    t('どのウィンドウを開きますか？', 'Which window?'),
                    style: theme.textTheme.titleLarge,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            ConstrainedBox(
              constraints: BoxConstraints(
                maxHeight: MediaQuery.sizeOf(sheet).height * .55,
              ),
              child: GridView.builder(
                shrinkWrap: true,
                itemCount: sessions.length,
                gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                  maxCrossAxisExtent: 220,
                  mainAxisExtent: 200,
                  crossAxisSpacing: 12,
                  mainAxisSpacing: 12,
                ),
                itemBuilder: (context, index) {
                  final session = sessions[index];
                  return M3ECard(
                    variant: M3ECardVariant.filled,
                    padding: const EdgeInsets.all(8),
                    onPressed: () {
                      Navigator.pop(sheet);
                      LinuxAppList.focus(session);
                    },
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(12),
                            child: FutureBuilder<Uint8List?>(
                              future: LinuxAppList.capture(session),
                              builder: (context, snapshot) {
                                final image = snapshot.data;
                                return Container(
                                  width: double.infinity,
                                  color:
                                      theme.colorScheme.surfaceContainerHighest,
                                  child: image == null
                                      ? Center(
                                          child:
                                              snapshot.connectionState ==
                                                  ConnectionState.done
                                              ? Icon(
                                                  M3EIcons.web_asset_rounded,
                                                  color:
                                                      theme.colorScheme.outline,
                                                )
                                              : const M3ELoadingIndicator(
                                                  size: 32,
                                                ),
                                        )
                                      : Image.memory(
                                          image,
                                          fit: BoxFit.cover,
                                          alignment: Alignment.topCenter,
                                        ),
                                );
                              },
                            ),
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          session.title.isEmpty ? entry.name : session.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.labelLarge,
                        ),
                      ],
                    ),
                  );
                },
              ),
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: M3EButton.tonal(
                size: M3EButtonSize.sm,
                icon: const Icon(M3EIcons.add_to_queue_rounded),
                onPressed: () {
                  Navigator.pop(sheet);
                  LinuxAppList.launch(entry.container, entry);
                },
                label: Text(t('新しいセッションで開く', 'Open in a new session')),
              ),
            ),
          ],
        ),
      );
    },
  );
}
