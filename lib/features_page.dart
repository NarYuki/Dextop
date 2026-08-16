import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:free_dextop/analytics_service.dart';
import 'package:free_dextop/app_strings.dart';
import 'package:free_dextop/setup_page.dart';
import 'package:shared_preferences/shared_preferences.dart';

class DextopFeaturesPage extends StatefulWidget {
  const DextopFeaturesPage({
    required this.isRunning,
    this.embedded = false,
    this.launcherOnly = false,
    this.category,
    this.ensureDesktopRunning,
    super.key,
  });

  final bool isRunning;
  final bool embedded;
  final bool launcherOnly;
  final String? category;
  final Future<bool> Function()? ensureDesktopRunning;

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
  var threeFingerGesture = 'menu';
  var twoFingerGesture = 'right_click';
  var longPressGesture = 'drag';
  var experimentalMultiTouch = true;
  var performanceHud = false;
  Timer? metricsTimer;

  static final workspaceLayouts = <String, Map<String, Object>>{
    'three_columns': {
      'label': AppStrings.tr('uiLeftCenterRight'),
      'positions': <String, String>{
        'left': AppStrings.tr('uiLeft'),
        'center': AppStrings.tr('uiCenter'),
        'right': AppStrings.tr('uiRight'),
      },
    },
    'two_columns': {
      'label': AppStrings.tr('uiDividedIntoLeftAndRight'),
      'positions': <String, String>{
        'half_left': AppStrings.tr('uiLeftHalf'),
        'half_right': AppStrings.tr('uiRightHalf'),
      },
    },
    'top_two_bottom': {
      'label': AppStrings.tr('uiUpperLeftUpperRightLowerHalf'),
      'positions': <String, String>{
        'top_left': AppStrings.tr('uiUpperLeft'),
        'top_right': AppStrings.tr('uiUpperRight'),
        'bottom_half': AppStrings.tr('uiLowerHalf'),
      },
    },
    'four_grid': {
      'label': AppStrings.tr('ui4Divisions'),
      'positions': <String, String>{
        'grid_top_left': AppStrings.tr('uiUpperLeft'),
        'grid_top_right': AppStrings.tr('uiUpperRight'),
        'grid_bottom_left': AppStrings.tr('uiLowerLeft'),
        'grid_bottom_right': AppStrings.tr('uiLowerRight'),
      },
    },
    'main_left': {
      'label': AppStrings.tr('uiLeft23Right13'),
      'positions': <String, String>{
        'wide_left': AppStrings.tr('uiLeft23'),
        'narrow_right': AppStrings.tr('uiRight13'),
      },
    },
    'main_right': {
      'label': AppStrings.tr('uiLeft13Right23'),
      'positions': <String, String>{
        'narrow_left': AppStrings.tr('uiLeft13'),
        'wide_right': AppStrings.tr('uiRight23'),
      },
    },
    'two_rows': {
      'label': AppStrings.tr('uiDividedIntoUpperAndLowerParts'),
      'positions': <String, String>{
        'top_half': AppStrings.tr('uiUpperHalf'),
        'bottom_half': AppStrings.tr('uiLowerHalf'),
      },
    },
    'main_and_two': {
      'label': AppStrings.tr('uiMainLarge2Sub'),
      'positions': <String, String>{
        'wide_left': AppStrings.tr('uiMainLeft'),
        'right_top': AppStrings.tr('uiUpperRight'),
        'right_bottom': AppStrings.tr('uiLowerRight'),
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
      metricsTimer = Timer.periodic(Duration(seconds: 1), (_) => loadMetrics());
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
    // On foldables, follow the active panel by default. Keep an explicit
    // user choice (including false) intact when upgrading an existing install.
    if (savedFoldableAuto == null && isFoldableDevice) {
      await preferences.setBool('foldable_auto', true);
    }
    final savedLaptopMode = preferences.getBool('foldable_laptop_mode');
    if (savedLaptopMode == null && isFoldableDevice) {
      await preferences.setBool('foldable_laptop_mode', true);
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
      foldableLaptopMode = savedLaptopMode ?? isFoldableDevice;
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
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(error.message ?? AppStrings.tr('uiCouldNotStart')),
          ),
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
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(error.message ?? AppStrings.tr('uiCouldNotStart')),
          ),
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
        '${AppStrings.tr('uiWorkSpace')} ${workspaces.length + 1}';
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
    final result = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text(
            workspace == null
                ? AppStrings.tr('uiSaveWorkspace')
                : AppStrings.tr('uiEditWorkspace'),
          ),
          content: SizedBox(
            width: 420,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextFormField(
                  initialValue: workspaceName,
                  autofocus: true,
                  decoration: InputDecoration(
                    labelText: AppStrings.tr('uiName'),
                  ),
                  onChanged: (value) => workspaceName = value,
                ),
                SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  initialValue: layout,
                  decoration: InputDecoration(
                    labelText: AppStrings.tr('uiLayout'),
                  ),
                  items: workspaceLayouts.entries
                      .map(
                        (entry) => DropdownMenuItem(
                          value: entry.key,
                          child: Text(entry.value['label']! as String),
                        ),
                      )
                      .toList(),
                  onChanged: (value) {
                    if (value == null) return;
                    setDialogState(() {
                      layout = value;
                      savedPositions.clear();
                      assignMissingPositions();
                    });
                  },
                ),
                SizedBox(height: 12),
                Flexible(
                  child: ListView(
                    shrinkWrap: true,
                    children: packages.map((packageName) {
                      final app = apps.firstWhere(
                        (item) => item['package'] == packageName,
                        orElse: () => {
                          'package': packageName,
                          'label': packageName,
                        },
                      );
                      return ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: _appIcon(app),
                        title: Text('${app['label']}'),
                        trailing: DropdownButton<String>(
                          value: positions[packageName],
                          underline: SizedBox.shrink(),
                          items:
                              (workspaceLayouts[layout]!['positions']
                                      as Map<String, String>)
                                  .entries
                                  .map(
                                    (entry) => DropdownMenuItem(
                                      value: entry.key,
                                      child: Text(entry.value),
                                    ),
                                  )
                                  .toList(),
                          onChanged: (value) {
                            if (value != null) {
                              setDialogState(
                                () => positions[packageName] = value,
                              );
                            }
                          },
                        ),
                      );
                    }).toList(),
                  ),
                ),
              ],
            ),
          ),
          actions: [
            if (workspace != null)
              TextButton.icon(
                style: TextButton.styleFrom(
                  foregroundColor: Theme.of(context).colorScheme.error,
                ),
                onPressed: () => Navigator.pop(context, {'delete': true}),
                icon: Icon(Icons.delete_outline_rounded),
                label: Text(AppStrings.tr('uiDelete')),
              ),
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: Text(AppStrings.tr('uiCancel')),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, {
                'name': workspaceName.trim(),
                'layout': layout,
                'positions': Map<String, String>.from(positions),
              }),
              child: Text(AppStrings.tr('save')),
            ),
          ],
        ),
      ),
    );
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
      ..['name'] = '${workspace['name']} ${AppStrings.tr('uiCopy')}'
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
    final data = JsonEncoder.withIndent('  ').convert({
      'format': 'dextop-workspaces',
      'version': 1,
      'workspaces': workspaces,
    });
    await Clipboard.setData(ClipboardData(text: data));
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(AppStrings.tr('uiCopiedWorkspaceJsonToClipboard')),
      ),
    );
  }

  Future<void> importWorkspaces() async {
    final controller = TextEditingController(
      text: (await Clipboard.getData(Clipboard.kTextPlain))?.text ?? '',
    );
    if (!mounted) return;
    final source = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(AppStrings.tr('uiImportWorkspace')),
        content: SizedBox(
          width: 460,
          child: TextField(
            controller: controller,
            minLines: 8,
            maxLines: 16,
            decoration: InputDecoration(
              labelText: AppStrings.tr('uiDextopWorkspaceJson'),
              alignLabelWithHint: true,
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(AppStrings.tr('uiCancel')),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, controller.text),
            child: Text(AppStrings.tr('uiImport')),
          ),
        ],
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
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppStrings.tr('uiCouldNotLoadJson'))),
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
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(AppStrings.tr('uiDeleteWorkspace')),
        content: Text(
          '${AppStrings.tr('uiOpeningQuote')}${workspace['name']}${AppStrings.tr('uiDeleteWorkspaceQuestionSuffix')}',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(AppStrings.tr('uiCancel')),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(AppStrings.tr('uiDelete')),
          ),
        ],
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
      await Future<void>.delayed(Duration(milliseconds: 350));
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
    final japanese = Localizations.localeOf(context).languageCode == 'ja';
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        fullscreenDialog: true,
        builder: (_) => GestureDemoFlow(
          title: japanese ? '3本指ジェスチャーを再確認' : 'Review three-finger gesture',
          back: japanese ? '戻る' : 'Back',
          done: japanese ? '完了' : 'Done',
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (loading) {
      final progress = Padding(
        padding: EdgeInsets.all(32),
        child: Center(child: CircularProgressIndicator()),
      );
      return widget.embedded ? progress : Scaffold(body: progress);
    }
    final content = <Widget>[
      if (widget.embedded &&
          !widget.launcherOnly &&
          (widget.category == null || widget.category == 'apps'))
        _section(AppStrings.tr('uiAppLauncher'), Icons.apps_rounded, [
          ListTile(
            leading: Icon(Icons.apps_rounded),
            title: Text(AppStrings.tr('uiAppLauncherSettings')),
            subtitle: Text(
              AppStrings.tr('uiLaunchTheAppAndConfigureYourWorkspace'),
            ),
            trailing: Icon(Icons.chevron_right_rounded),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => DextopFeaturesPage(
                  isRunning: widget.isRunning,
                  launcherOnly: true,
                  ensureDesktopRunning: widget.ensureDesktopRunning,
                ),
              ),
            ),
          ),
        ])
      else if (widget.category == null || widget.category == 'apps')
        _section(AppStrings.tr('uiAppLauncher'), Icons.apps_rounded, [
          TextField(
            decoration: InputDecoration(
              prefixIcon: Icon(Icons.search_rounded),
              hintText: AppStrings.tr('uiSearchApp'),
            ),
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
          Divider(height: 1),
          SizedBox(
            height: 420,
            child: appsLoading
                ? Center(child: CircularProgressIndicator())
                : filteredApps.isEmpty
                ? Center(child: Text(AppStrings.tr('uiAppNotFound')))
                : ListView.separated(
                    primary: false,
                    itemCount: filteredApps.length,
                    separatorBuilder: (_, _) => Divider(height: 1, indent: 58),
                    itemBuilder: (context, index) {
                      final app = filteredApps[index];
                      return CheckboxListTile(
                        value: selectedPackages.contains(app['package']),
                        secondary: _appIcon(app),
                        title: Text('${app['label']}'),
                        subtitle: Text(
                          '${app['package']}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        onChanged: (selected) => setState(
                          () => selected == true
                              ? selectedPackages.add('${app['package']}')
                              : selectedPackages.remove('${app['package']}'),
                        ),
                        controlAffinity: ListTileControlAffinity.trailing,
                      );
                    },
                  ),
          ),
          Padding(
            padding: EdgeInsets.fromLTRB(12, 12, 12, 8),
            child: Row(
              children: [
                Expanded(
                  child: SizedBox(
                    height: 40,
                    child: FilledButton.tonalIcon(
                      style: FilledButton.styleFrom(
                        padding: EdgeInsets.symmetric(horizontal: 10),
                        textStyle: Theme.of(context).textTheme.labelMedium,
                      ),
                      onPressed: selectedPackages.isEmpty
                          ? null
                          : launchSelection,
                      icon: Icon(Icons.launch_rounded, size: 18),
                      label: Text(
                        AppStrings.tr('uiOpenDextop'),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ),
                ),
                SizedBox(width: 10),
                Expanded(
                  child: SizedBox(
                    height: 40,
                    child: FilledButton.tonalIcon(
                      style: FilledButton.styleFrom(
                        padding: EdgeInsets.symmetric(horizontal: 10),
                        textStyle: Theme.of(context).textTheme.labelMedium,
                      ),
                      onPressed: selectedPackages.isEmpty
                          ? null
                          : saveWorkspace,
                      icon: Icon(Icons.bookmark_add_rounded, size: 18),
                      label: Text(
                        AppStrings.tr('uiSaveConfiguration'),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ]),
      if (!widget.launcherOnly &&
          (widget.category == null || widget.category == 'apps'))
        _section(AppStrings.tr('uiWorkSpace'), Icons.space_dashboard_rounded, [
          Padding(
            padding: EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            child: Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: workspaces.isEmpty ? null : exportWorkspaces,
                    icon: Icon(Icons.upload_rounded),
                    label: Text(AppStrings.tr('uiExport')),
                  ),
                ),
                SizedBox(width: 8),
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: importWorkspaces,
                    icon: Icon(Icons.download_rounded),
                    label: Text(AppStrings.tr('uiImport')),
                  ),
                ),
              ],
            ),
          ),
          if (workspaces.isEmpty)
            ListTile(title: Text(AppStrings.tr('uiNoSavedWorkspaces'))),
          ...workspaces.indexed.map(
            (entry) => ListTile(
              leading: Icon(Icons.dashboard_customize_rounded),
              title: Text('${entry.$2['name']}'),
              subtitle: Padding(
                padding: EdgeInsets.only(top: 7),
                child: Wrap(
                  spacing: 5,
                  runSpacing: 5,
                  children: (entry.$2['apps'] as List)
                      .cast<String>()
                      .map(_workspaceAppIcon)
                      .toList(),
                ),
              ),
              trailing: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  IconButton(
                    tooltip: AppStrings.tr('uiUp'),
                    onPressed: entry.$1 == 0
                        ? null
                        : () => moveWorkspace(entry.$1, -1),
                    icon: Icon(Icons.arrow_upward_rounded),
                  ),
                  PopupMenuButton<String>(
                    tooltip: AppStrings.tr('uiOthers'),
                    onSelected: (value) {
                      if (value == 'duplicate') duplicateWorkspace(entry.$2);
                      if (value == 'down') moveWorkspace(entry.$1, 1);
                    },
                    itemBuilder: (_) => [
                      PopupMenuItem(
                        value: 'duplicate',
                        child: ListTile(
                          leading: Icon(Icons.copy_rounded),
                          title: Text(AppStrings.tr('uiReproduction')),
                        ),
                      ),
                      if (entry.$1 < workspaces.length - 1)
                        PopupMenuItem(
                          value: 'down',
                          child: ListTile(
                            leading: Icon(Icons.arrow_downward_rounded),
                            title: Text(AppStrings.tr('uiMoveDown')),
                          ),
                        ),
                    ],
                  ),
                  IconButton(
                    tooltip: AppStrings.tr('uiEdit'),
                    onPressed: () => editWorkspace(entry.$2),
                    icon: Icon(Icons.edit_rounded),
                  ),
                  Icon(Icons.play_arrow_rounded),
                ],
              ),
              onTap: () => launchWorkspace(entry.$2),
            ),
          ),
        ]),
      if (!widget.launcherOnly &&
          (widget.category == null || widget.category == 'display')) ...[
        _section('Foldable', Icons.devices_fold_rounded, [
          SwitchListTile(
            value: foldableAuto,
            onChanged: updateFoldable,
            secondary: Icon(Icons.devices_fold_rounded),
            title: Text(
              AppStrings.tr('uiAutomaticSwitchingAccordingToOpenClosedState'),
            ),
            subtitle: Text(
              AppStrings.tr('uiAutomaticallyUsesMeasuredResolutionForOpenAnd'),
            ),
          ),
          SwitchListTile(
            value: foldableLaptopMode,
            onChanged: updateFoldableLaptopMode,
            secondary: Icon(Icons.laptop_chromebook_rounded),
            title: Text(AppStrings.tr('foldableLaptopMode')),
            subtitle: Text(AppStrings.tr('foldableLaptopModeDescription')),
          ),
        ]),
      ],
      if (!widget.launcherOnly &&
          (widget.category == null || widget.category == 'interaction'))
        _section(AppStrings.tr('uiGesture'), Icons.gesture_rounded, [
          if (experimentalMultiTouch)
            ListTile(
              leading: Icon(Icons.swipe_right_rounded),
              title: Text(AppStrings.tr('uiSwipeRightWithThreeFingersFromThe')),
              subtitle: Text(AppStrings.tr('uiShowActionOverlay')),
              trailing: FilledButton.tonalIcon(
                onPressed: reviewThreeFingerGesture,
                icon: const Icon(Icons.replay_rounded),
                label: Text(
                  Localizations.localeOf(context).languageCode == 'ja'
                      ? '再確認'
                      : 'Review',
                ),
              ),
              onTap: reviewThreeFingerGesture,
            )
          else
            _gestureTile(
              leading: Icon(Icons.sign_language_rounded),
              title: AppStrings.tr('ui3FingerTap'),
              value: threeFingerGesture,
              options: {
                'menu': AppStrings.tr('uiOperationOverlay'),
                'home': AppStrings.tr('home'),
                'rotate': AppStrings.tr('uiVerticalHorizontalSwitching'),
                'stop': AppStrings.tr('uiStopDextop'),
              },
              onChanged: updateThreeFingerGesture,
            ),
          _gestureTile(
            leading: Icon(Icons.pinch_rounded),
            title: AppStrings.tr('uiTwoFingerTap'),
            value: twoFingerGesture,
            options: {
              'right_click': AppStrings.tr('uiRightClick'),
              'home': AppStrings.tr('home'),
              'menu': AppStrings.tr('uiOperationOverlay'),
            },
            onChanged: (value) =>
                updateSecondaryGesture('gesture_two_finger', value),
          ),
          _gestureTile(
            leading: Icon(Icons.ads_click_rounded),
            title: AppStrings.tr('uiLongPress'),
            value: longPressGesture,
            options: {
              'drag': AppStrings.tr('uiDrag'),
              'right_click': AppStrings.tr('uiRightClick'),
              'menu': AppStrings.tr('uiOperationOverlay'),
            },
            onChanged: (value) =>
                updateSecondaryGesture('gesture_long_press', value),
          ),
        ]),
      if (!widget.launcherOnly &&
          (widget.category == null || widget.category == 'status')) ...[
        _section(AppStrings.tr('uiPerformance'), Icons.speed_rounded, [
          SwitchListTile(
            value: performanceHud,
            onChanged: updatePerformanceHud,
            title: Text(AppStrings.tr('uiPerformanceDisplayOnDextop')),
            subtitle: Text(AppStrings.tr('uiRealTimeDisplayOfFpsMemoryPower')),
          ),
          _metric(AppStrings.tr('uiActualFps'), '${metrics['fps'] ?? 0}'),
          _metric(
            AppStrings.tr('uiDisplayRefreshRate'),
            '${metrics['refreshRate'] ?? 0} Hz',
          ),
          _metric(
            AppStrings.tr('uiAppMemory'),
            '${metrics['memoryMb'] ?? 0} MB',
          ),
          _metric(
            AppStrings.tr('uiAvailableMemory'),
            '${metrics['availableMemoryMb'] ?? 0} MB',
          ),
          _metric(
            AppStrings.tr('uiBattery'),
            '${metrics['batteryPercent'] ?? 0}%',
          ),
          _metric(
            AppStrings.tr('uiEstimatedPowerConsumption'),
            '${metrics['powerWatts'] ?? 0} W',
          ),
          _metric(
            AppStrings.tr('uiCpuTemperature'),
            '${metrics['cpuTemperature'] ?? '-- °C'}',
          ),
          _metric(
            AppStrings.tr('uiInputMode'),
            '${metrics['inputMode'] ?? AppStrings.tr('uiIdle')}',
          ),
        ]),
        _section(
          AppStrings.tr('uiCompatibilityDiagnosis'),
          Icons.health_and_safety_rounded,
          [
            ...diagnostics.entries.map(
              (entry) => ListTile(
                leading: Icon(
                  entry.value == true
                      ? Icons.check_circle_rounded
                      : entry.value == false
                      ? Icons.cancel_rounded
                      : Icons.info_rounded,
                  color: entry.value == true ? Colors.green : null,
                ),
                title: Text(_diagnosticLabel(entry.key)),
                trailing: Text(
                  _availabilityDiagnostics.contains(entry.key) &&
                          entry.value is bool
                      ? entry.value == true
                            ? AppStrings.tr('uiAvailable')
                            : AppStrings.tr('uiUnavailable')
                      : '${entry.value}',
                ),
              ),
            ),
            OutlinedButton.icon(
              onPressed: load,
              icon: Icon(Icons.refresh_rounded),
              label: Text(AppStrings.tr('uiReDiagnosis')),
            ),
          ],
        ),
      ],
    ];
    if (widget.embedded) return Column(children: content);
    return Scaffold(
      appBar: AppBar(title: Text(_pageTitle())),
      body: ListView(
        padding: EdgeInsets.fromLTRB(16, 8, 16, 32),
        children: content,
      ),
    );
  }

  String _pageTitle() => switch (widget.category) {
    'apps' => AppStrings.tr('uiAppsAndWorkspace'),
    'display' => AppStrings.tr('uiDisplayOptimization'),
    'interaction' => AppStrings.tr('uiInputAndGestures'),
    'status' => AppStrings.tr('uiConditionAndDiagnosis'),
    _ =>
      widget.launcherOnly
          ? AppStrings.tr('uiAppLauncherSettings')
          : AppStrings.tr('uiDesktopFeatures'),
  };

  Widget _appIcon(Map<String, dynamic> app) {
    final bytes = app['icon'];
    if (bytes is Uint8List) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: Image.memory(bytes, width: 42, height: 42),
      );
    }
    return Icon(Icons.android_rounded);
  }

  Widget _workspaceAppIcon(String packageName) {
    final app = apps
        .where((item) => item['package'] == packageName)
        .firstOrNull;
    if (app != null && app['icon'] is Uint8List) {
      return Tooltip(
        message: '${app['label']}',
        child: ClipRRect(
          borderRadius: BorderRadius.circular(7),
          child: Image.memory(app['icon'] as Uint8List, width: 28, height: 28),
        ),
      );
    }
    return Tooltip(
      message: packageName,
      child: Icon(Icons.android_rounded, size: 28),
    );
  }

  Widget _gestureTile({
    required Widget leading,
    required String title,
    required String value,
    required Map<String, String> options,
    required ValueChanged<String> onChanged,
  }) => ListTile(
    leading: leading,
    title: Text(title),
    trailing: DropdownButtonHideUnderline(
      child: DropdownButton<String>(
        value: value,
        borderRadius: BorderRadius.circular(16),
        items: options.entries
            .map(
              (entry) =>
                  DropdownMenuItem(value: entry.key, child: Text(entry.value)),
            )
            .toList(),
        onChanged: (selected) {
          if (selected != null) onChanged(selected);
        },
      ),
    ),
  );

  Widget _section(String title, IconData icon, List<Widget> children) {
    if (!widget.embedded) {
      return Padding(
        padding: EdgeInsets.only(bottom: 20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: EdgeInsets.fromLTRB(12, 0, 12, 7),
              child: Text(
                title,
                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                  color: Theme.of(context).colorScheme.primary,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
            Card(
              margin: EdgeInsets.zero,
              child: ListTileTheme(
                data: ListTileThemeData(
                  dense: true,
                  minTileHeight: 56,
                  minVerticalPadding: 4,
                  contentPadding: EdgeInsets.symmetric(horizontal: 16),
                  visualDensity: VisualDensity(vertical: -2),
                ),
                child: Padding(
                  padding: EdgeInsets.symmetric(vertical: 4),
                  child: Column(children: _withDividers(children)),
                ),
              ),
            ),
          ],
        ),
      );
    }
    return Padding(
      padding: EdgeInsets.only(bottom: 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: EdgeInsets.fromLTRB(12, 0, 12, 7),
            child: Text(
              title,
              style: Theme.of(context).textTheme.labelLarge?.copyWith(
                color: Theme.of(context).colorScheme.primary,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          Card(
            margin: EdgeInsets.zero,
            child: ListTileTheme(
              data: ListTileThemeData(
                dense: true,
                minTileHeight: 56,
                minVerticalPadding: 4,
                contentPadding: EdgeInsets.symmetric(horizontal: 16),
                visualDensity: VisualDensity(vertical: -2),
              ),
              child: Padding(
                padding: EdgeInsets.symmetric(vertical: 4),
                child: Column(children: _withDividers(children)),
              ),
            ),
          ),
        ],
      ),
    );
  }

  List<Widget> _withDividers(List<Widget> children) {
    if (children.length < 2) return children;
    return [
      for (var index = 0; index < children.length; index++) ...[
        children[index],
        if (index != children.length - 1 &&
            children[index] is ListTile &&
            children[index + 1] is ListTile)
          Divider(height: 1, indent: 56),
      ],
    ];
  }

  Widget _metric(String label, String value) =>
      ListTile(title: Text(label), trailing: Text(value));

  String _diagnosticLabel(String key) =>
      {
        'shizuku': AppStrings.tr('uiShizukuConnection').replaceAll(
          'Shizuku',
          '${diagnostics['privilegeProviderName'] ?? 'Stellar'}',
        ),
        'secureSettings': AppStrings.tr('uiSecureSettingsPermission'),
        'accessibility': AppStrings.tr('uiAccessibilityServices'),
        'overlayWritable': AppStrings.tr('uiAccessibilityOverlay'),
        'mouse': AppStrings.tr('uiPhysicalMouse'),
        'keyboard': AppStrings.tr('uiPhysicalKeyboard'),
        'secondaryIme': AppStrings.tr('uiSecondaryIme'),
        'desktopMode': AppStrings.tr('uiDesktopMode'),
        'sessionActive': AppStrings.tr('uiCreateADextopSession'),
        'virtualDisplay': AppStrings.tr('uiVirtualDisplayCreation'),
        'appLauncher': AppStrings.tr('uiAppLaunchFunction'),
        'quickSettingsTile': AppStrings.tr('uiQuickSettingsTile'),
        'foldableLayout': AppStrings.tr('uiLargeScreenFoldable'),
        'sdk': 'Android SDK',
      }[key] ??
      key;

  static final _availabilityDiagnostics = {
    'accessibility',
    'overlayWritable',
    'sessionActive',
    'virtualDisplay',
  };
}
