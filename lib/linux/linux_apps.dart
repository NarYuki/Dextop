import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import 'linux_expressive.dart';
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
      iconBase64 = json['icon'] as String? ?? '',
      icon = _decode(json['icon'] as String?);

  final String container;
  final String desktopId;
  final String name;
  final List<String> categories;
  final String iconBase64;
  final Uint8List? icon;

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

/// The applications of a container and which ones have a home-screen launcher.
class LinuxAppList extends ChangeNotifier {
  static const _channel = MethodChannel('app.freedextop/linux');

  List<LinuxAppEntry> apps = const [];
  Set<String> shortcuts = const {};
  bool loading = false;
  String? error;

  Future<void> load(String container) => loadAll([container]);

  Future<void> loadAll(List<String> containers) async {
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
      shortcuts =
          ((await _channel.invokeListMethod<Object?>('shortcuts')) ?? const [])
              .cast<String>()
              .toSet();
      error = null;
    } on PlatformException catch (e) {
      error = e.message;
    }
    loading = false;
    notifyListeners();
  }

  /// Opens [entry]: in the Plasma desktop when it runs, otherwise as Android windows.
  static Future<void> launch(String container, LinuxAppEntry entry) =>
      _channel.invokeMethod('launchApp', {
        'id': container,
        'desktop': entry.desktopId,
        'label': entry.name,
      });

  /// Makes and installs the small launcher APK that puts [entry] on the home screen.
  static Future<void> addToHome(
    BuildContext context,
    String container,
    LinuxAppEntry entry,
  ) async {
    String t(String ja, String en) => linuxText(context, ja, en);
    try {
      final status = await _channel.invokeMethod<String>('createShortcut', {
        'id': container,
        'desktop': entry.desktopId,
        'label': entry.name,
        'icon': entry.iconBase64,
      });
      if (!context.mounted) return;
      M3ESnackbar.show(
        context,
        message: status == 'permission'
            ? t(
                '「不明なアプリのインストール」を許可してから、もう一度追加してください',
                'Allow installing unknown apps, then add it again',
              )
            : t(
                '${entry.name} をホーム画面に追加しています',
                'Adding ${entry.name} to the home screen',
              ),
      );
    } on PlatformException catch (e) {
      if (context.mounted) {
        M3ESnackbar.show(context, message: e.message ?? '$e');
      }
    }
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
        child: entry.icon == null
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
        final apps = list.apps;
        return SliverPadding(
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 120),
          sliver: SliverGrid.builder(
            gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
              maxCrossAxisExtent: 112,
              mainAxisExtent: 116,
              crossAxisSpacing: 4,
              mainAxisSpacing: 4,
            ),
            itemCount: apps.length,
            itemBuilder: (context, index) => _AppCell(
              entry: apps[index],
              list: list,
              index: index,
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
    required this.entry,
    required this.list,
    required this.index,
    this.containerName,
  });

  final LinuxAppEntry entry;
  final LinuxAppList list;
  final int index;
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

  @override
  Widget build(BuildContext context) {
    final entry = widget.entry;
    final theme = Theme.of(context);
    final pinned = widget.list.shortcuts.contains(entry.key);
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
          onTap: () => LinuxAppList.launch(entry.container, entry),
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
                    LinuxAppIcon(entry: entry),
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
                  child: Text(
                    entry.name,
                    maxLines: 2,
                    textAlign: TextAlign.center,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.labelMedium,
                  ),
                ),
                if (widget.containerName != null)
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
    String t(String ja, String en) => linuxText(context, ja, en);
    final entry = widget.entry;
    final pinned = widget.list.shortcuts.contains(entry.key);
    await M3EBottomSheet.show<void>(
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
                    child: Text(
                      entry.name,
                      style: Theme.of(sheet).textTheme.titleLarge,
                    ),
                  ),
                ],
              ),
            ),
            Column(
              children: [
                LinuxMenuRow(
                  title: t('開く', 'Open'),
                  icon: M3EIcons.play_arrow_rounded,
                  onTap: () {
                    Navigator.pop(sheet);
                    LinuxAppList.launch(entry.container, entry);
                  },
                ),
                LinuxMenuRow(
                  title: pinned
                      ? t('ホーム画面から削除', 'Remove from Home')
                      : t('ホーム画面に追加', 'Add to Home'),
                  subtitle: pinned
                      ? null
                      : t(
                          'Androidのアプリとして表示します',
                          'Shows it like an Android app',
                        ),
                  icon: pinned
                      ? M3EIcons.delete_outline_rounded
                      : M3EIcons.add_to_home_screen_rounded,
                  onTap: () async {
                    Navigator.pop(sheet);
                    if (pinned) {
                      await LinuxAppList.removeFromHome(entry.container, entry);
                    } else {
                      await LinuxAppList.addToHome(
                        context,
                        entry.container,
                        entry,
                      );
                    }
                  },
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
