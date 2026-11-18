import 'dart:async';
import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:free_dextop/analytics_service.dart';
import 'package:free_dextop/expressive/dextop_expressive.dart';
import 'package:free_dextop/expressive/dextop_widgets.dart';
import 'package:free_dextop/l10n/current_localizations.dart';
import 'package:free_dextop/l10n/app_localizations.dart';
import 'package:free_dextop/setup_page.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

class DextopFeaturesPage extends StatefulWidget {
  const DextopFeaturesPage({
    required this.isRunning,
    this.embedded = false,
    this.launcherOnly = false,
    this.category,
    this.ensureDesktopRunning,
    this.onAddHomeShortcut,
    super.key,
  });

  final bool isRunning;
  final bool embedded;
  final bool launcherOnly;
  final String? category;
  final Future<bool> Function()? ensureDesktopRunning;
  final Future<void> Function()? onAddHomeShortcut;

  @override
  State<DextopFeaturesPage> createState() => _DextopFeaturesPageState();
}

class _DextopFeaturesPageState extends State<DextopFeaturesPage> {
  static final channel = MethodChannel('app.freedextop/display');
  var apps = <Map<String, dynamic>>[];
  var filteredApps = <Map<String, dynamic>>[];
  var selectedPackages = <String>{};
  var workspaces = <Map<String, dynamic>>[];
  var diagnostics = <String, dynamic>{};
  var metrics = <String, dynamic>{};
  var loading = false;
  var appsLoading = false;
  var foldableAuto = false;
  var foldableLaptopMode = false;
  var foldableDevice = false;
  var coverDisplayEnabled = false;
  var threeFingerGesture = 'menu';
  var twoFingerGesture = 'right_click';
  var longPressGesture = 'drag';
  var experimentalMultiTouch = true;
  var performanceHud = false;
  Timer? metricsTimer;

  static final workspaceLayouts = <String, Map<String, Object>>{
    'three_columns': {
      'label': currentLocalizations().uiLeftCenterRight,
      'positions': <String, String>{
        'left': currentLocalizations().uiLeft,
        'center': currentLocalizations().uiCenter,
        'right': currentLocalizations().uiRight,
      },
    },
    'two_columns': {
      'label': currentLocalizations().uiDividedIntoLeftAndRight,
      'positions': <String, String>{
        'half_left': currentLocalizations().uiLeftHalf,
        'half_right': currentLocalizations().uiRightHalf,
      },
    },
    'top_two_bottom': {
      'label': currentLocalizations().uiUpperLeftUpperRightLowerHalf,
      'positions': <String, String>{
        'top_left': currentLocalizations().uiUpperLeft,
        'top_right': currentLocalizations().uiUpperRight,
        'bottom_half': currentLocalizations().uiLowerHalf,
      },
    },
    'four_grid': {
      'label': currentLocalizations().ui4Divisions,
      'positions': <String, String>{
        'grid_top_left': currentLocalizations().uiUpperLeft,
        'grid_top_right': currentLocalizations().uiUpperRight,
        'grid_bottom_left': currentLocalizations().uiLowerLeft,
        'grid_bottom_right': currentLocalizations().uiLowerRight,
      },
    },
    'main_left': {
      'label': currentLocalizations().uiLeft23Right13,
      'positions': <String, String>{
        'wide_left': currentLocalizations().uiLeft23,
        'narrow_right': currentLocalizations().uiRight13,
      },
    },
    'main_right': {
      'label': currentLocalizations().uiLeft13Right23,
      'positions': <String, String>{
        'narrow_left': currentLocalizations().uiLeft13,
        'wide_right': currentLocalizations().uiRight23,
      },
    },
    'two_rows': {
      'label': currentLocalizations().uiDividedIntoUpperAndLowerParts,
      'positions': <String, String>{
        'top_half': currentLocalizations().uiUpperHalf,
        'bottom_half': currentLocalizations().uiLowerHalf,
      },
    },
    'main_and_two': {
      'label': currentLocalizations().uiMainLarge2Sub,
      'positions': <String, String>{
        'wide_left': currentLocalizations().uiMainLeft,
        'right_top': currentLocalizations().uiUpperRight,
        'right_bottom': currentLocalizations().uiLowerRight,
      },
    },
  };

  @override
  void initState() {
    super.initState();
    AppAnalytics.screen(
      widget.launcherOnly
          ? 'app_launcher'
          : 'features_${widget.category ?? 'all'}',
    );
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
    if (widget.category == 'status') {
      metricsTimer = Timer.periodic(
        const Duration(seconds: 1),
        (_) => loadMetrics(),
      );
    }
  }

  @override
  void dispose() {
    metricsTimer?.cancel();
    super.dispose();
  }

  Future<void> load() async {
    final needsApps = widget.launcherOnly || widget.category == 'apps';
    final needsStatus = widget.category == 'status';
    if (needsApps && mounted) setState(() => appsLoading = true);
    final preferencesFuture = SharedPreferences.getInstance();
    final appsFuture = needsApps
        ? channel.invokeListMethod<dynamic>('appsMetadata')
        : Future<List<dynamic>?>.value([]);
    final diagnosticsFuture = needsStatus
        ? channel.invokeMapMethod<String, dynamic>('diagnostics')
        : Future<Map<String, dynamic>?>.value({});
    final foldableDeviceFuture = channel.invokeMethod<bool>('isFoldableDevice');
    final results = await Future.wait<dynamic>([
      preferencesFuture,
      appsFuture,
      diagnosticsFuture,
      foldableDeviceFuture,
    ]);
    final preferences = results[0] as SharedPreferences;
    final rawApps = (results[1] as List<dynamic>?) ?? [];
    final rawDiagnostics =
        (results[2] as Map<String, dynamic>?) ?? <String, dynamic>{};
    final isFoldableDevice = results[3] == true;
    final savedFoldableAuto = preferences.getBool('foldable_auto');
    if (savedFoldableAuto == null && isFoldableDevice) {
      await preferences.setBool('foldable_auto', true);
    }
    final savedLaptopMode = preferences.getBool('foldable_laptop_mode');
    if (savedLaptopMode == null && isFoldableDevice) {
      await preferences.setBool('foldable_laptop_mode', false);
    }
    final savedCoverDisplay = preferences.getBool('cover_display_enabled');
    final legacyCoverDisplay = preferences.getBool(
      'experimental_cover_display',
    );
    final resolvedCoverDisplay =
        savedCoverDisplay ?? legacyCoverDisplay ?? isFoldableDevice;
    if (savedCoverDisplay == null && isFoldableDevice) {
      await preferences.setBool('cover_display_enabled', resolvedCoverDisplay);
    }
    final decoded = needsApps
        ? jsonDecode(preferences.getString('workspaces') ?? '[]') as List
        : <dynamic>[];
    if (!mounted) return;
    setState(() {
      apps = rawApps
          .map((item) => Map<String, dynamic>.from(item as Map))
          .toList();
      filteredApps = apps;
      diagnostics = rawDiagnostics;
      workspaces = decoded
          .map((item) => Map<String, dynamic>.from(item as Map))
          .toList();
      foldableAuto = savedFoldableAuto ?? isFoldableDevice;
      foldableLaptopMode = savedLaptopMode ?? false;
      foldableDevice = isFoldableDevice;
      coverDisplayEnabled = resolvedCoverDisplay;
      threeFingerGesture =
          preferences.getString('gesture_three_finger') ?? 'menu';
      twoFingerGesture =
          preferences.getString('gesture_two_finger') ?? 'right_click';
      longPressGesture = preferences.getString('gesture_long_press') ?? 'drag';
      experimentalMultiTouch = true;
      performanceHud = preferences.getBool('performance_hud') ?? false;
      loading = false;
      appsLoading = false;
    });
    if (needsApps) unawaited(loadAppIcons());
    if (needsStatus) await loadMetrics();
  }

  Future<void> loadAppIcons() async {
    final raw =
        await channel.invokeMapMethod<String, dynamic>('appIcons') ?? {};
    if (!mounted) return;
    setState(() {
      for (final app in apps) {
        final icon = raw['${app['package']}'];
        if (icon != null) app['icon'] = icon;
      }
      filteredApps = List<Map<String, dynamic>>.from(filteredApps);
    });
  }

  Future<void> loadMetrics() async {
    final value =
        await channel.invokeMapMethod<String, dynamic>('metrics') ?? {};
    if (mounted) setState(() => metrics = value);
  }

  Future<void> launch(String packageName, {List<int>? bounds}) async {
    try {
      final arguments = <String, dynamic>{'package': packageName};
      if (bounds case final value?) arguments['bounds'] = value;
      await channel.invokeMethod('launchApp', arguments);
    } on PlatformException catch (error) {
      if (mounted) {
        M3ESnackbar.show(
          context,
          message: error.message ?? currentLocalizations().uiCouldNotStart,
        );
      }
    }
  }

  Future<void> launchAtPosition(String packageName, String position) async {
    try {
      await channel.invokeMethod('launchApp', {
        'package': packageName,
        'position': position,
      });
    } on PlatformException catch (error) {
      if (mounted) {
        M3ESnackbar.show(
          context,
          message: error.message ?? currentLocalizations().uiCouldNotStart,
        );
      }
    }
  }

  Future<void> saveWorkspace() => editWorkspace();

  Future<void> editWorkspace([Map<String, dynamic>? workspace]) async {
    final packages = workspace == null
        ? selectedPackages.toList()
        : (workspace['apps'] as List).cast<String>().toList();
    if (packages.isEmpty) return;
    var workspaceName =
        workspace?['name'] as String? ??
        '${currentLocalizations().uiWorkSpace} ${workspaces.length + 1}';
    final nameController = TextEditingController(text: workspaceName);
    var layout = workspace?['layout'] as String? ?? 'three_columns';
    if (!workspaceLayouts.containsKey(layout)) layout = 'three_columns';
    final savedPositions = workspace?['positions'] is Map
        ? Map<String, dynamic>.from(workspace!['positions'] as Map)
        : <String, dynamic>{};
    final positions = <String, String>{};

    void assignMissingPositions() {
      final slots =
          (workspaceLayouts[layout]!['positions'] as Map<String, String>).keys
              .toList();
      for (var index = 0; index < packages.length; index++) {
        final saved = savedPositions[packages[index]] as String?;
        positions[packages[index]] = saved != null && slots.contains(saved)
            ? saved
            : slots[index % slots.length];
      }
    }

    assignMissingPositions();

    Future<String?> pickChoice(
      BuildContext parentContext, {
      required String title,
      required String current,
      required Map<String, String> items,
    }) {
      return M3EDialog.show<String>(
        parentContext,
        dialog: Builder(
          builder: (choiceContext) {
            final colors = Theme.of(choiceContext).colorScheme;
            return M3EDialog(
              title: title,
              content: DextopGroup(
                color: colors.surfaceContainer,
                children: [
                  for (final entry in items.entries)
                    DextopChoice<String>(
                      value: entry.key,
                      groupValue: current,
                      allowReselect: true,
                      title: entry.value,
                      onChanged: (next) => Navigator.pop(choiceContext, next),
                    ),
                ],
              ),
            );
          },
        ),
      );
    }

    final result = await M3EDialog.show<Map<String, dynamic>>(
      context,
      dialog: StatefulBuilder(
        builder: (dialogContext, setDialogState) {
          final colors = Theme.of(dialogContext).colorScheme;
          final slotOptions =
              workspaceLayouts[layout]!['positions'] as Map<String, String>;
          return M3EDialog(
            icon: const Icon(M3EIcons.space_dashboard_rounded),
            title: workspace == null
                ? currentLocalizations().uiSaveWorkspace
                : currentLocalizations().uiEditWorkspace,
            content: SizedBox(
              width: 420,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  M3ETextField(
                    controller: nameController,
                    autofocus: true,
                    variant: M3ETextFieldVariant.outlined,
                    label: currentLocalizations().uiName,
                    onChanged: (value) => workspaceName = value,
                  ),
                  const SizedBox(height: 12),
                  DextopGroup(
                    color: colors.surfaceContainer,
                    children: [
                      M3EListItem(
                        headline: currentLocalizations().uiLayout,
                        trailing: DextopDropdownButton(
                          label: workspaceLayouts[layout]!['label']! as String,
                          onPressed: () async {
                            final picked = await pickChoice(
                              dialogContext,
                              title: currentLocalizations().uiLayout,
                              current: layout,
                              items: {
                                for (final entry in workspaceLayouts.entries)
                                  entry.key: entry.value['label']! as String,
                              },
                            );
                            if (picked != null && picked != layout) {
                              setDialogState(() {
                                layout = picked;
                                savedPositions.clear();
                                assignMissingPositions();
                              });
                            }
                          },
                        ),
                        onTap: () async {
                          final picked = await pickChoice(
                            dialogContext,
                            title: currentLocalizations().uiLayout,
                            current: layout,
                            items: {
                              for (final entry in workspaceLayouts.entries)
                                entry.key: entry.value['label']! as String,
                            },
                          );
                          if (picked != null && picked != layout) {
                            setDialogState(() {
                              layout = picked;
                              savedPositions.clear();
                              assignMissingPositions();
                            });
                          }
                        },
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Flexible(
                    child: SingleChildScrollView(
                      child: DextopGroup(
                        color: colors.surfaceContainer,
                        children: [
                          for (final packageName in packages)
                            Builder(
                              builder: (_) {
                                final app = apps.firstWhere(
                                  (item) => item['package'] == packageName,
                                  orElse: () => {
                                    'package': packageName,
                                    'label': packageName,
                                  },
                                );
                                final currentSlot = positions[packageName]!;
                                return M3EListItem(
                                  leading: _appIcon(app),
                                  headline: '${app['label']}',
                                  trailing: DextopDropdownButton(
                                    label: slotOptions[currentSlot] ?? '',
                                    onPressed: () async {
                                      final picked = await pickChoice(
                                        dialogContext,
                                        title: '${app['label']}',
                                        current: currentSlot,
                                        items: slotOptions,
                                      );
                                      if (picked != null) {
                                        setDialogState(
                                          () => positions[packageName] = picked,
                                        );
                                      }
                                    },
                                  ),
                                  onTap: () async {
                                    final picked = await pickChoice(
                                      dialogContext,
                                      title: '${app['label']}',
                                      current: currentSlot,
                                      items: slotOptions,
                                    );
                                    if (picked != null) {
                                      setDialogState(
                                        () => positions[packageName] = picked,
                                      );
                                    }
                                  },
                                );
                              },
                            ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
            actions: [
              if (workspace != null)
                M3EButton.icon(
                  style: M3EButtonStyle.text,
                  decoration: dextopButtonColors(
                    background: Colors.transparent,
                    foreground: colors.error,
                  ),
                  onPressed: () =>
                      Navigator.pop(dialogContext, {'delete': true}),
                  icon: const Icon(M3EIcons.delete_outline_rounded),
                  label: Text(currentLocalizations().uiDelete),
                ),
              M3EButton.text(
                onPressed: () => Navigator.pop(dialogContext),
                child: Text(currentLocalizations().uiCancel),
              ),
              M3EButton.filled(
                onPressed: () => Navigator.pop(dialogContext, {
                  'name': workspaceName.trim(),
                  'layout': layout,
                  'positions': Map<String, String>.from(positions),
                }),
                child: Text(currentLocalizations().save),
              ),
            ],
          );
        },
      ),
    );
    nameController.dispose();
    if (result == null) return;
    if (result['delete'] == true) {
      await deleteWorkspace(workspace!);
      return;
    }
    final name = result['name'] as String;
    if (name.isEmpty) return;
    final item = <String, dynamic>{
      'id':
          workspace?['id'] ?? DateTime.now().millisecondsSinceEpoch.toString(),
      'name': name,
      'apps': packages,
      'layout': result['layout'],
      'positions': result['positions'],
    };
    setState(() {
      if (workspace == null) {
        workspaces.add(item);
      } else {
        final index = workspaces.indexWhere(
          (entry) => entry['id'] == workspace['id'],
        );
        if (index >= 0) workspaces[index] = item;
      }
    });
    await persistWorkspaces();
  }

  Future<void> persistWorkspaces() async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setString('workspaces', jsonEncode(workspaces));
  }

  Future<void> duplicateWorkspace(Map<String, dynamic> workspace) async {
    final copy = Map<String, dynamic>.from(workspace)
      ..['id'] = DateTime.now().millisecondsSinceEpoch.toString()
      ..['name'] = '${workspace['name']} ${currentLocalizations().uiCopy}'
      ..['apps'] = List<String>.from(workspace['apps'] as List)
      ..['positions'] = Map<String, dynamic>.from(
        workspace['positions'] as Map? ?? {},
      );
    setState(() => workspaces.add(copy));
    await persistWorkspaces();
  }

  Future<void> moveWorkspace(int index, int offset) async {
    final destination = index + offset;
    if (destination < 0 || destination >= workspaces.length) return;
    setState(() {
      final item = workspaces.removeAt(index);
      workspaces.insert(destination, item);
    });
    await persistWorkspaces();
  }

  Future<void> exportWorkspaces() async {
    final data = const JsonEncoder.withIndent('  ').convert({
      'format': 'dextop-workspaces',
      'version': 1,
      'workspaces': workspaces,
    });
    await Clipboard.setData(ClipboardData(text: data));
    if (!mounted) return;
    M3ESnackbar.show(
      context,
      message: currentLocalizations().uiCopiedWorkspaceJsonToClipboard,
    );
  }

  Future<void> importWorkspaces() async {
    final controller = TextEditingController(
      text: (await Clipboard.getData(Clipboard.kTextPlain))?.text ?? '',
    );
    if (!mounted) return;
    final source = await M3EDialog.show<String>(
      context,
      dialog: Builder(
        builder: (dialogContext) => M3EDialog(
          icon: const Icon(M3EIcons.download_rounded),
          title: currentLocalizations().uiImportWorkspace,
          content: SizedBox(
            width: 460,
            child: M3ETextField(
              controller: controller,
              minLines: 8,
              maxLines: 16,
              variant: M3ETextFieldVariant.outlined,
              label: currentLocalizations().uiDextopWorkspaceJson,
            ),
          ),
          actions: [
            M3EButton.text(
              onPressed: () => Navigator.pop(dialogContext),
              child: Text(currentLocalizations().uiCancel),
            ),
            M3EButton.filled(
              onPressed: () => Navigator.pop(dialogContext, controller.text),
              child: Text(currentLocalizations().uiImport),
            ),
          ],
        ),
      ),
    );
    controller.dispose();
    if (source == null) return;
    try {
      final decoded = jsonDecode(source);
      final raw = decoded is Map ? decoded['workspaces'] : decoded;
      final imported = (raw as List)
          .map((item) => Map<String, dynamic>.from(item as Map))
          .where((item) => item['apps'] is List && item['name'] is String)
          .map(
            (item) => item
              ..['id'] =
                  DateTime.now().microsecondsSinceEpoch.toString() +
                  workspaces.length.toString(),
          )
          .toList();
      setState(() => workspaces.addAll(imported));
      await persistWorkspaces();
    } catch (_) {
      if (!mounted) return;
      M3ESnackbar.show(
        context,
        message: currentLocalizations().uiCouldNotLoadJson,
      );
    }
  }

  Future<void> launchWorkspace(Map<String, dynamic> workspace) async {
    final packages = (workspace['apps'] as List).cast<String>();
    final positions = workspace['positions'] is Map
        ? Map<String, dynamic>.from(workspace['positions'] as Map)
        : <String, dynamic>{};
    final bounds = workspace['bounds'] is Map
        ? Map<String, dynamic>.from(workspace['bounds'] as Map)
        : <String, dynamic>{};
    final preferences = await SharedPreferences.getInstance();
    await preferences.setString('last_workspace_id', '${workspace['id']}');
    await launchPackages(packages, positions: positions, bounds: bounds);
  }

  Future<void> deleteWorkspace(Map<String, dynamic> workspace) async {
    final confirmed = await M3EDialog.show<bool>(
      context,
      dialog: Builder(
        builder: (dialogContext) => M3EDialog(
          icon: const Icon(M3EIcons.delete_outline_rounded),
          title: currentLocalizations().uiDeleteWorkspace,
          content: Text(
            '${currentLocalizations().uiOpeningQuote}${workspace['name']}${currentLocalizations().uiDeleteWorkspaceQuestionSuffix}',
          ),
          actions: [
            M3EButton.text(
              onPressed: () => Navigator.pop(dialogContext, false),
              child: Text(currentLocalizations().uiCancel),
            ),
            M3EButton.filled(
              onPressed: () => Navigator.pop(dialogContext, true),
              child: Text(currentLocalizations().uiDelete),
            ),
          ],
        ),
      ),
    );
    if (confirmed != true) return;
    setState(
      () => workspaces.removeWhere((item) => item['id'] == workspace['id']),
    );
    await persistWorkspaces();
  }

  Future<void> launchSelection() async {
    await launchPackages(selectedPackages.toList());
  }

  Future<void> launchPackages(
    List<String> packages, {
    Map<String, dynamic> positions = const {},
    Map<String, dynamic> bounds = const {},
  }) async {
    if (packages.isEmpty) return;
    if (!widget.isRunning) {
      final ready = await widget.ensureDesktopRunning?.call() ?? false;
      if (!ready) return;
    }
    for (var index = 0; index < packages.length; index++) {
      final column = index % 2;
      final row = (index ~/ 2).clamp(0, 1);
      final position = positions[packages[index]] as String?;
      final savedBounds = bounds[packages[index]];
      if (savedBounds is List && savedBounds.length == 4) {
        await launch(packages[index], bounds: savedBounds.cast<int>());
      } else if (position != null) {
        await launchAtPosition(packages[index], position);
      } else {
        await launch(
          packages[index],
          bounds: [
            column * 960,
            row * 540,
            (column + 1) * 960,
            (row + 1) * 540,
          ],
        );
      }
      await Future<void>.delayed(const Duration(milliseconds: 350));
    }
  }

  Future<void> updateFoldable(bool value) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool('foldable_auto', value);
    if (value) {
      await preferences.remove('fold_open_profile');
      await preferences.remove('fold_closed_profile');
    }
    setState(() => foldableAuto = value);
  }

  Future<void> updateFoldableLaptopMode(bool value) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool('foldable_laptop_mode', value);
    await channel.invokeMethod<void>('foldableLaptopMode', <String, dynamic>{
      'enabled': value,
    });
    setState(() => foldableLaptopMode = value);
  }

  Future<void> updateCoverDisplay(bool value) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool('cover_display_enabled', value);
    await preferences.remove('experimental_cover_display');
    if (mounted) setState(() => coverDisplayEnabled = value);
  }

  Future<void> updateSecondaryGesture(String key, String value) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setString(key, value);
    setState(() {
      if (key == 'gesture_two_finger') twoFingerGesture = value;
      if (key == 'gesture_long_press') longPressGesture = value;
    });
  }

  Future<void> updatePerformanceHud(bool value) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool('performance_hud', value);
    await channel.invokeMethod('performanceHud', {'enabled': value});
    setState(() => performanceHud = value);
  }

  Future<void> updateThreeFingerGesture(String value) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setString('gesture_three_finger', value);
    setState(() => threeFingerGesture = value);
  }

  Future<void> reviewThreeFingerGesture() async {
    final l = AppLocalizations.of(context);
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        fullscreenDialog: true,
        builder: (_) => GestureDemoFlow(
          title: l.reviewThreeFingerGesture,
          back: l.back,
          done: l.done,
        ),
      ),
    );
  }

  Future<void> _openWorkspaceMenu(int index, Map<String, dynamic> ws) async {
    final action = await M3EBottomSheet.show<String>(
      context,
      showDragHandle: true,
      builder: (sheetContext) => Padding(
        padding: const EdgeInsets.fromLTRB(8, 0, 8, 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            DextopMenuRow(
              icon: M3EIcons.copy_rounded,
              title: currentLocalizations().uiReproduction,
              onTap: () => Navigator.pop(sheetContext, 'duplicate'),
            ),
            if (index < workspaces.length - 1)
              DextopMenuRow(
                icon: M3EIcons.arrow_downward_rounded,
                title: currentLocalizations().uiMoveDown,
                onTap: () => Navigator.pop(sheetContext, 'down'),
              ),
            DextopMenuRow(
              icon: M3EIcons.edit_rounded,
              title: currentLocalizations().uiEdit,
              onTap: () => Navigator.pop(sheetContext, 'edit'),
            ),
          ],
        ),
      ),
    );
    if (!mounted || action == null) return;
    if (action == 'duplicate') await duplicateWorkspace(ws);
    if (action == 'down') await moveWorkspace(index, 1);
    if (action == 'edit') await editWorkspace(ws);
  }

  Future<void> _pickGestureOption({
    required IconData icon,
    required String title,
    required String value,
    required Map<String, String> options,
    required ValueChanged<String> onChanged,
  }) async {
    final selected = await M3EDialog.show<String>(
      context,
      dialog: Builder(
        builder: (dialogContext) {
          final colors = Theme.of(dialogContext).colorScheme;
          return M3EDialog(
            icon: Icon(icon),
            title: title,
            content: DextopGroup(
              color: colors.surfaceContainer,
              children: [
                for (final entry in options.entries)
                  DextopChoice<String>(
                    value: entry.key,
                    groupValue: value,
                    allowReselect: true,
                    title: entry.value,
                    onChanged: (next) => Navigator.pop(dialogContext, next),
                  ),
              ],
            ),
          );
        },
      ),
    );
    if (selected != null && selected != value) onChanged(selected);
  }

  @override
  Widget build(BuildContext context) {
    return EnsureDextopExpressive(
      child: Builder(
        builder: (context) {
          final colors = Theme.of(context).colorScheme;
          if (loading) {
            const progress = Padding(
              padding: EdgeInsets.all(32),
              child: Center(
                child: M3ELoadingIndicator(
                  variant: M3ELoadingIndicatorVariant.contained,
                ),
              ),
            );
            return widget.embedded
                ? progress
                : Scaffold(backgroundColor: colors.surface, body: progress);
          }
          final content = <Widget>[
            if (widget.embedded &&
                !widget.launcherOnly &&
                (widget.category == null || widget.category == 'apps')) ...[
              DextopSection(currentLocalizations().uiAppLauncher),
              DextopGroup(
                children: [
                  M3EListItem(
                    leading: const Icon(M3EIcons.apps_rounded),
                    headline: currentLocalizations().uiAppLauncherSettings,
                    supportingText: currentLocalizations()
                        .uiLaunchTheAppAndConfigureYourWorkspace,
                    supportingMaxLines: null,
                    trailing: const Icon(M3EIcons.chevron_right_rounded),
                    onTap: () => Navigator.of(context).push(
                      dextopRoute<void>(
                        DextopFeaturesPage(
                          isRunning: widget.isRunning,
                          launcherOnly: true,
                          ensureDesktopRunning: widget.ensureDesktopRunning,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ] else if (widget.category == null ||
                widget.category == 'apps') ...[
              DextopSection(currentLocalizations().uiAppLauncher),
              M3ESearchBar(
                margin: 0,
                focusedMargin: 0,
                hintText: currentLocalizations().uiSearchApp,
                leading: const Icon(M3EIcons.search_rounded),
                onChanged: (query) => setState(
                  () => filteredApps = apps
                      .where(
                        (app) => '${app['label']}'.toLowerCase().contains(
                          query.toLowerCase(),
                        ),
                      )
                      .toList(),
                ),
              ),
              const SizedBox(height: 12),
              M3ECard(
                variant: M3ECardVariant.filled,
                borderRadius: BorderRadius.circular(24),
                padding: const EdgeInsets.all(10),
                child: Column(
                  children: [
                    SizedBox(
                      height: 400,
                      child: appsLoading
                          ? const Center(
                              child: M3ELoadingIndicator(
                                variant: M3ELoadingIndicatorVariant.contained,
                              ),
                            )
                          : filteredApps.isEmpty
                          ? Center(
                              child: Text(currentLocalizations().uiAppNotFound),
                            )
                          : M3EList.scrollable(
                              outerRadius: 20,
                              innerRadius: 8,
                              gap: 3,
                              color: colors.surfaceContainer,
                              itemCount: filteredApps.length,
                              onTap: (index) {
                                final pkg = '${filteredApps[index]['package']}';
                                setState(() {
                                  if (selectedPackages.contains(pkg)) {
                                    selectedPackages.remove(pkg);
                                  } else {
                                    selectedPackages.add(pkg);
                                  }
                                });
                              },
                              itemBuilder: (context, index) {
                                final app = filteredApps[index];
                                final pkg = '${app['package']}';
                                final checked = selectedPackages.contains(pkg);
                                return M3EListItem(
                                  leading: _appIcon(app),
                                  headline: '${app['label']}',
                                  supportingText: pkg,
                                  supportingMaxLines: 1,
                                  selected: checked,
                                  trailing: M3ECheckbox(
                                    value: checked,
                                    onChanged: (selected) => setState(
                                      () => selected == true
                                          ? selectedPackages.add(pkg)
                                          : selectedPackages.remove(pkg),
                                    ),
                                  ),
                                );
                              },
                            ),
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: M3EButton.tonal(
                            size: M3EButtonSize.sm,
                            enabled: selectedPackages.isNotEmpty,
                            onPressed: selectedPackages.isEmpty
                                ? null
                                : launchSelection,
                            icon: const Icon(M3EIcons.launch_rounded, size: 18),
                            label: Text(
                              currentLocalizations().uiOpenDextop,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: M3EButton.filled(
                            size: M3EButtonSize.sm,
                            enabled: selectedPackages.isNotEmpty,
                            onPressed: selectedPackages.isEmpty
                                ? null
                                : saveWorkspace,
                            icon: const Icon(
                              M3EIcons.bookmark_add_rounded,
                              size: 18,
                            ),
                            label: Text(
                              currentLocalizations().uiSaveConfiguration,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
            if (!widget.launcherOnly &&
                (widget.category == null || widget.category == 'apps')) ...[
              DextopSection(currentLocalizations().uiWorkSpace),
              Row(
                children: [
                  Expanded(
                    child: M3EButton.outlined(
                      size: M3EButtonSize.sm,
                      enabled: workspaces.isNotEmpty,
                      onPressed: workspaces.isEmpty ? null : exportWorkspaces,
                      icon: const Icon(M3EIcons.upload_rounded),
                      label: Text(currentLocalizations().uiExport),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: M3EButton.outlined(
                      size: M3EButtonSize.sm,
                      onPressed: importWorkspaces,
                      icon: const Icon(M3EIcons.download_rounded),
                      label: Text(currentLocalizations().uiImport),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              if (workspaces.isEmpty)
                DextopGroup(
                  children: [
                    M3EListItem(
                      headline: currentLocalizations().uiNoSavedWorkspaces,
                    ),
                  ],
                )
              else
                DextopGroup(
                  children: [
                    for (final entry in workspaces.indexed)
                      DextopWorkspaceRow(
                        name: '${entry.$2['name']}',
                        icons: [
                          for (final pkg
                              in (entry.$2['apps'] as List).cast<String>())
                            _workspaceAppIcon(pkg),
                        ],
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            if (entry.$1 > 0)
                              M3EIconButton(
                                variant: M3EIconButtonVariant.standard,
                                tooltip: currentLocalizations().uiUp,
                                onPressed: () => moveWorkspace(entry.$1, -1),
                                icon: const Icon(M3EIcons.arrow_upward_rounded),
                              ),
                            M3EIconButton(
                              variant: M3EIconButtonVariant.standard,
                              tooltip: currentLocalizations().uiOthers,
                              onPressed: () =>
                                  _openWorkspaceMenu(entry.$1, entry.$2),
                              icon: const Icon(M3EIcons.more_vert_rounded),
                            ),
                            const Icon(M3EIcons.play_arrow_rounded),
                          ],
                        ),
                        onTap: () => launchWorkspace(entry.$2),
                      ),
                  ],
                ),
            ],
            if (!widget.launcherOnly &&
                (widget.category == null || widget.category == 'display')) ...[
              const DextopSection('Foldable'),
              DextopGroup(
                children: [
                  DextopToggle(
                    value: foldableAuto,
                    onChanged: updateFoldable,
                    icon: M3EIcons.devices_fold_rounded,
                    title: currentLocalizations()
                        .uiAutomaticSwitchingAccordingToOpenClosedState,
                    subtitle: currentLocalizations()
                        .uiAutomaticallyUsesMeasuredResolutionForOpenAnd,
                  ),
                  DextopToggle(
                    value: foldableLaptopMode,
                    onChanged: updateFoldableLaptopMode,
                    icon: M3EIcons.laptop_chromebook_rounded,
                    title: currentLocalizations().foldableLaptopMode,
                    subtitle:
                        currentLocalizations().foldableLaptopModeDescription,
                  ),
                  if (foldableDevice)
                    DextopToggle(
                      value: coverDisplayEnabled,
                      enabled: !widget.isRunning,
                      onChanged: widget.isRunning ? null : updateCoverDisplay,
                      icon: M3EIcons.flip_to_back_rounded,
                      title: currentLocalizations().nativeCoverDisplay,
                      subtitle:
                          currentLocalizations().nativeCoverDisplayDescription,
                    ),
                ],
              ),
            ],
            if (widget.category == 'interaction') ...[
              DextopSection(currentLocalizations().uiShortcutAdd),
              DextopGroup(
                children: [
                  M3EListItem(
                    leading: const Icon(M3EIcons.add_to_home_screen_rounded),
                    headline: currentLocalizations().uiShortcutAdd,
                    onTap: widget.onAddHomeShortcut,
                  ),
                ],
              ),
            ],
            if (!widget.launcherOnly &&
                (widget.category == null ||
                    widget.category == 'interaction')) ...[
              DextopSection(currentLocalizations().uiGesture),
              DextopGroup(
                children: [
                  if (experimentalMultiTouch)
                    M3EListItem(
                      leading: const Icon(M3EIcons.swipe_right_rounded),
                      headline: currentLocalizations()
                          .uiSwipeRightWithThreeFingersFromThe,
                      supportingText:
                          currentLocalizations().uiShowActionOverlay,
                      supportingMaxLines: null,
                      trailing: M3EButton.tonal(
                        size: M3EButtonSize.xs,
                        onPressed: reviewThreeFingerGesture,
                        icon: const Icon(M3EIcons.replay_rounded, size: 16),
                        label: Text(AppLocalizations.of(context).review),
                      ),
                      onTap: reviewThreeFingerGesture,
                    )
                  else
                    _gestureTile(
                      icon: M3EIcons.sign_language_rounded,
                      title: currentLocalizations().ui3FingerTap,
                      value: threeFingerGesture,
                      options: {
                        'menu': currentLocalizations().uiOperationOverlay,
                        'home': currentLocalizations().home,
                        'rotate': currentLocalizations()
                            .uiVerticalHorizontalSwitching,
                        'stop': currentLocalizations().uiStopDextop,
                      },
                      onChanged: updateThreeFingerGesture,
                    ),
                  _gestureTile(
                    icon: M3EIcons.pinch_rounded,
                    title: currentLocalizations().uiTwoFingerTap,
                    value: twoFingerGesture,
                    options: {
                      'right_click': currentLocalizations().uiRightClick,
                      'home': currentLocalizations().home,
                      'menu': currentLocalizations().uiOperationOverlay,
                    },
                    onChanged: (value) =>
                        updateSecondaryGesture('gesture_two_finger', value),
                  ),
                  _gestureTile(
                    icon: M3EIcons.ads_click_rounded,
                    title: currentLocalizations().uiLongPress,
                    value: longPressGesture,
                    options: {
                      'drag': currentLocalizations().uiDrag,
                      'right_click': currentLocalizations().uiRightClick,
                      'menu': currentLocalizations().uiOperationOverlay,
                    },
                    onChanged: (value) =>
                        updateSecondaryGesture('gesture_long_press', value),
                  ),
                ],
              ),
            ],
            if (!widget.launcherOnly &&
                (widget.category == null || widget.category == 'status')) ...[
              DextopSection(currentLocalizations().uiPerformance),
              M3ECard(
                variant: M3ECardVariant.filled,
                borderRadius: BorderRadius.circular(24),
                padding: const EdgeInsets.symmetric(vertical: 6),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    DextopToggle(
                      value: performanceHud,
                      onChanged: updatePerformanceHud,
                      title:
                          currentLocalizations().uiPerformanceDisplayOnDextop,
                      subtitle: currentLocalizations()
                          .uiRealTimeDisplayOfFpsMemoryPower,
                    ),
                    const M3EDivider(inset: M3EDividerInset.inset),
                    const SizedBox(height: 4),
                    _metric(
                      currentLocalizations().uiActualFps,
                      '${metrics['fps'] ?? 0}',
                    ),
                    _metric(
                      currentLocalizations().uiDisplayRefreshRate,
                      '${metrics['refreshRate'] ?? 0} Hz',
                    ),
                    _metric(
                      currentLocalizations().uiAppMemory,
                      '${metrics['memoryMb'] ?? 0} MB',
                    ),
                    _metric(
                      currentLocalizations().uiAvailableMemory,
                      '${metrics['availableMemoryMb'] ?? 0} MB',
                    ),
                    _metric(
                      currentLocalizations().uiBattery,
                      '${metrics['batteryPercent'] ?? 0}%',
                    ),
                    _metric(
                      currentLocalizations().uiEstimatedPowerConsumption,
                      '${metrics['powerWatts'] ?? 0} W',
                    ),
                    _metric(
                      currentLocalizations().uiCpuTemperature,
                      '${metrics['cpuTemperature'] ?? '-- °C'}',
                    ),
                    _metric(
                      currentLocalizations().uiInputMode,
                      '${metrics['inputMode'] ?? currentLocalizations().uiIdle}',
                    ),
                    const SizedBox(height: 4),
                  ],
                ),
              ),
              DextopSection(currentLocalizations().uiCompatibilityDiagnosis),
              M3ECard(
                variant: M3ECardVariant.filled,
                borderRadius: BorderRadius.circular(24),
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    for (final entry in diagnostics.entries)
                      DextopKeyValueRow(
                        leading: Icon(
                          entry.value == true
                              ? M3EIcons.check_circle_rounded
                              : entry.value == false
                              ? M3EIcons.cancel_rounded
                              : M3EIcons.info_rounded,
                          size: 20,
                          color: entry.value == true
                              ? colors.primary
                              : colors.onSurfaceVariant,
                        ),
                        label: _diagnosticLabel(entry.key),
                        value:
                            _availabilityDiagnostics.contains(entry.key) &&
                                entry.value is bool
                            ? entry.value == true
                                  ? currentLocalizations().uiAvailable
                                  : currentLocalizations().uiUnavailable
                            : '${entry.value}',
                        valueColor: entry.value == true
                            ? colors.primary
                            : colors.onSurfaceVariant,
                      ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              M3EButton.outlined(
                size: M3EButtonSize.md,
                onPressed: load,
                icon: const Icon(M3EIcons.refresh_rounded),
                label: Text(currentLocalizations().uiReDiagnosis),
              ),
            ],
          ];
          if (widget.embedded) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: content,
            );
          }
          return Scaffold(
            backgroundColor: colors.surface,
            appBar: M3EAppBar.top(
              automaticallyImplyLeading: true,
              titleText: _pageTitle(),
            ),
            body: ListView(
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 32),
              children: content,
            ),
          );
        },
      ),
    );
  }

  String _pageTitle() => switch (widget.category) {
    'apps' => currentLocalizations().uiAppsAndWorkspace,
    'display' => currentLocalizations().uiDisplayOptimization,
    'interaction' => currentLocalizations().uiInputAndGestures,
    'status' => currentLocalizations().uiConditionAndDiagnosis,
    _ =>
      widget.launcherOnly
          ? currentLocalizations().uiAppLauncherSettings
          : currentLocalizations().uiDesktopFeatures,
  };

  Widget _workspaceAppIcon(String packageName) {
    final app = apps
        .where((item) => item['package'] == packageName)
        .firstOrNull;
    if (app != null && app['icon'] is Uint8List) {
      return M3ETooltip(
        message: '${app['label']}',
        child: ClipRRect(
          borderRadius: BorderRadius.circular(7),
          child: Image.memory(app['icon'] as Uint8List, width: 28, height: 28),
        ),
      );
    }
    return M3ETooltip(
      message: packageName,
      child: const Icon(M3EIcons.android_rounded, size: 28),
    );
  }

  Widget _appIcon(Map<String, dynamic> app) {
    final bytes = app['icon'];
    if (bytes is Uint8List) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: Image.memory(bytes, width: 42, height: 42),
      );
    }
    return const Icon(M3EIcons.android_rounded);
  }

  M3EListItem _gestureTile({
    required IconData icon,
    required String title,
    required String value,
    required Map<String, String> options,
    required ValueChanged<String> onChanged,
  }) {
    void openPicker() => _pickGestureOption(
      icon: icon,
      title: title,
      value: value,
      options: options,
      onChanged: onChanged,
    );
    return M3EListItem(
      leading: Icon(icon),
      headline: title,
      trailing: DextopDropdownButton(
        label: options[value] ?? value,
        onPressed: openPicker,
      ),
      onTap: openPicker,
    );
  }

  Widget _metric(String label, String value) =>
      DextopKeyValueRow(label: label, value: value);

  String _diagnosticLabel(String key) {
    final l = AppLocalizations.of(context);
    return {
          'shizuku': currentLocalizations().uiShizukuConnection.replaceAll(
            'Shizuku',
            '${diagnostics['privilegeProviderName'] ?? 'Stellar'}',
          ),
          'secureSettings': currentLocalizations().uiSecureSettingsPermission,
          'accessibility': currentLocalizations().uiAccessibilityServices,
          'overlayWritable': currentLocalizations().uiAccessibilityOverlay,
          'mouse': currentLocalizations().uiPhysicalMouse,
          'keyboard': currentLocalizations().uiPhysicalKeyboard,
          'secondaryIme': currentLocalizations().uiSecondaryIme,
          'desktopMode': currentLocalizations().uiDesktopMode,
          'sessionActive': currentLocalizations().uiCreateADextopSession,
          'virtualDisplay': currentLocalizations().uiVirtualDisplayCreation,
          'appLauncher': currentLocalizations().uiAppLaunchFunction,
          'quickSettingsTile': currentLocalizations().uiQuickSettingsTile,
          'foldableLayout': currentLocalizations().uiLargeScreenFoldable,
          'embeddedBinderIncluded': l.appInfoEmbeddedBinder,
          'embeddedBinderSelected': l.appInfoEmbeddedBinderProvider,
          'embeddedBinderPaired': l.setupEmbeddedConfigure,
          'embeddedBinderConnected': l.appInfoEmbeddedBinderConnection,
          'embeddedBinderNotifications': l.appInfoEmbeddedBinderNotifications,
          'sdk': 'Android SDK',
        }[key] ??
        key;
  }

  static final _availabilityDiagnostics = {
    'accessibility',
    'overlayWritable',
    'sessionActive',
    'virtualDisplay',
    'embeddedBinderIncluded',
    'embeddedBinderSelected',
    'embeddedBinderPaired',
    'embeddedBinderConnected',
    'embeddedBinderNotifications',
  };
}
