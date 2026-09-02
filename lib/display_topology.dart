part of 'main.dart';

class DisplayEnvironmentSettingsCard extends StatefulWidget {
  const DisplayEnvironmentSettingsCard({
    required this.bridge,
    this.showDisplay = true,
    this.showConvenience = true,
    this.displayLeadingDivider = false,
    this.wrapInCard = true,
    super.key,
  });
  final NativeBridge bridge;
  final bool showDisplay;
  final bool showConvenience;
  final bool displayLeadingDivider;
  final bool wrapInCard;

  @override
  State<DisplayEnvironmentSettingsCard> createState() =>
      _DisplayEnvironmentSettingsCardState();
}

class _DisplayEnvironmentSettingsCardState
    extends State<DisplayEnvironmentSettingsCard> {
  var loading = true;
  var includePhoneDisplay = false;
  var autoHideTaskbar = false;
  var supportsInternal120Hz = false;
  var forceInternal120Hz = false;
  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    final state = await widget.bridge.displayEnvironmentSettings().catchError(
      (_) => <String, dynamic>{},
    );
    if (!mounted) return;
    setState(() {
      includePhoneDisplay = state['includePhoneDisplay'] == 1;
      autoHideTaskbar = state['autoHideTaskbar'] == 1;
      supportsInternal120Hz = state['supportsInternal120Hz'] == true;
      forceInternal120Hz = state['forceInternal120Hz'] == true;
      loading = false;
    });
  }

  Future<void> save(String id, bool enabled) async {
    final previousPhone = includePhoneDisplay;
    final previousTaskbar = autoHideTaskbar;
    final previous120Hz = forceInternal120Hz;
    setState(() {
      if (id == 'includePhoneDisplay') includePhoneDisplay = enabled;
      if (id == 'autoHideTaskbar') autoHideTaskbar = enabled;
      if (id == 'forceInternal120Hz') forceInternal120Hz = enabled;
    });
    try {
      final state = await widget.bridge.setDisplayEnvironmentSetting(
        id,
        enabled,
      );
      if (!mounted) return;
      setState(() {
        includePhoneDisplay = state['includePhoneDisplay'] == 1;
        autoHideTaskbar = state['autoHideTaskbar'] == 1;
        supportsInternal120Hz = state['supportsInternal120Hz'] == true;
        forceInternal120Hz = state['forceInternal120Hz'] == true;
      });
    } catch (exception) {
      if (!mounted) return;
      setState(() {
        includePhoneDisplay = previousPhone;
        autoHideTaskbar = previousTaskbar;
        forceInternal120Hz = previous120Hz;
      });
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(exception.toString())));
    }
  }

  @override
  Widget build(BuildContext context) {
    final column = Column(
      children: [
        if (widget.showConvenience)
          SwitchListTile(
            secondary: const Icon(Icons.phone_android_rounded),
            title: Text(currentLocalizations().samsungIncludePhoneDisplay),
            subtitle: Text(currentLocalizations().displayIncludePhoneSummary),
            value: includePhoneDisplay,
            onChanged: loading
                ? null
                : (value) => save('includePhoneDisplay', value),
          ),
        if (widget.showConvenience) const Divider(height: 1),
        if (widget.showConvenience)
          SwitchListTile(
            secondary: const Icon(Icons.vertical_align_bottom_rounded),
            title: Text(currentLocalizations().samsungAutoHideTaskbar),
            subtitle: Text(
              currentLocalizations().displayAutoHideTaskbarSummary,
            ),
            value: autoHideTaskbar,
            onChanged: loading
                ? null
                : (value) => save('autoHideTaskbar', value),
          ),
        if (widget.showDisplay && supportsInternal120Hz) ...[
          if (widget.displayLeadingDivider) const Divider(height: 1),
          SwitchListTile(
            secondary: const Icon(Icons.speed_rounded),
            title: Text(currentLocalizations().displayForceInternal120Hz),
            subtitle: Text(
              currentLocalizations().displayForceInternal120HzSummary,
            ),
            value: forceInternal120Hz,
            onChanged: loading
                ? null
                : (value) => save('forceInternal120Hz', value),
          ),
        ],
      ],
    );
    if (!widget.wrapInCard) return column;
    return Card(
      child: ListTileTheme(
        data: const ListTileThemeData(
          dense: true,
          minTileHeight: 64,
          minVerticalPadding: 6,
          contentPadding: EdgeInsets.symmetric(horizontal: 16),
          visualDensity: VisualDensity(vertical: -1),
        ),
        child: column,
      ),
    );
  }
}

class DisplaySettingsPage extends StatelessWidget {
  const DisplaySettingsPage({required this.bridge, super.key});

  final NativeBridge bridge;

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(currentLocalizations().displaySettingsTitle)),
    body: DisplayTopologyEditor(bridge: bridge, showSettingsHeader: true),
  );
}

class DisplayTopologyPage extends StatelessWidget {
  const DisplayTopologyPage({required this.bridge, super.key});

  final NativeBridge bridge;

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(currentLocalizations().topologyTitle)),
    body: DisplayTopologyEditor(bridge: bridge),
  );
}

class DisplayResolutionListPage extends StatefulWidget {
  const DisplayResolutionListPage({required this.bridge, super.key});

  final NativeBridge bridge;

  @override
  State<DisplayResolutionListPage> createState() =>
      _DisplayResolutionListPageState();
}

class _DisplayResolutionListPageState extends State<DisplayResolutionListPage> {
  var loading = true;
  String? error;
  List<_TopologyDisplay> displays = const [];

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final state = await widget.bridge.displayModeDisplays();
      if (state['supported'] != true) {
        throw Exception(
          state['reason'] ?? currentLocalizations().topologyUnavailable,
        );
      }
      final next = (state['displays'] as List? ?? const [])
          .map(
            (item) => _TopologyDisplay.fromMap(
              Map<String, dynamic>.from(item as Map),
            ),
          )
          .toList();
      if (!mounted) return;
      setState(() {
        displays = next;
        loading = false;
      });
    } catch (exception) {
      if (mounted) {
        setState(() {
          error = exception.toString();
          loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Text(currentLocalizations().displayResolutionListTitle),
      actions: [
        IconButton(
          tooltip: currentLocalizations().topologyRefresh,
          onPressed: loading ? null : load,
          icon: const Icon(Icons.refresh_rounded),
        ),
      ],
    ),
    body: loading
        ? const Center(child: CircularProgressIndicator())
        : error != null
        ? Center(
            child: FilledButton.tonalIcon(
              onPressed: load,
              icon: const Icon(Icons.refresh_rounded),
              label: Text(currentLocalizations().topologyRefresh),
            ),
          )
        : ListView(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
            children: [
              Card(
                child: Column(
                  children: [
                    for (var index = 0; index < displays.length; index++) ...[
                      if (index > 0) const Divider(height: 1),
                      ListTile(
                        leading: Icon(
                          displays[index].dextopOverlay
                              ? Icons.phone_android_rounded
                              : Icons.monitor_rounded,
                        ),
                        title: Text(displays[index].name),
                        subtitle: Text(displays[index].modeLabel),
                        trailing: const Icon(Icons.chevron_right_rounded),
                        onTap: () => Navigator.of(context).push(
                          MaterialPageRoute<void>(
                            builder: (_) => DisplayResolutionDetailPage(
                              bridge: widget.bridge,
                              displayId: displays[index].id,
                            ),
                          ),
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
  );
}

class DisplayResolutionDetailPage extends StatefulWidget {
  const DisplayResolutionDetailPage({
    required this.bridge,
    required this.displayId,
    super.key,
  });

  final NativeBridge bridge;
  final int displayId;

  @override
  State<DisplayResolutionDetailPage> createState() =>
      _DisplayResolutionDetailPageState();
}

class _DisplayResolutionDetailPageState
    extends State<DisplayResolutionDetailPage> {
  _TopologyDisplay? display;
  var loading = true;
  var applying = false;
  String? error;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final state = await widget.bridge.displayModeDisplays();
      final candidates = (state['displays'] as List? ?? const [])
          .map(
            (item) => _TopologyDisplay.fromMap(
              Map<String, dynamic>.from(item as Map),
            ),
          )
          .where((item) => item.id == widget.displayId)
          .toList();
      final next = candidates.isEmpty ? null : candidates.first;
      if (next == null) {
        throw Exception(currentLocalizations().displayNoLongerAvailable);
      }
      if (!mounted) return;
      setState(() {
        display = next;
        loading = false;
      });
    } catch (exception) {
      if (mounted) {
        setState(() {
          error = exception.toString();
          loading = false;
        });
      }
    }
  }

  Future<void> selectMode(_DisplayMode mode) async {
    final current = display;
    if (current == null || current.dextopOverlay) return;
    setState(() => applying = true);
    try {
      await widget.bridge.setDisplayPreferredMode(
        displayId: current.id,
        width: mode.width,
        height: mode.height,
        refreshRate: mode.refreshRate,
      );
      await load();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(currentLocalizations().displayModeApplied)),
        );
      }
    } catch (exception) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(exception.toString())));
      }
    } finally {
      if (mounted) setState(() => applying = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final current = display;
    void onModeChanged(int? id) {
      if (applying || id == null) return;
      final matches =
          current?.supportedModes.where((item) => item.id == id).toList() ??
          const <_DisplayMode>[];
      final mode = matches.isEmpty ? null : matches.first;
      if (mode != null) unawaited(selectMode(mode));
    }

    return Scaffold(
      appBar: AppBar(
        title: Text(
          current?.name ?? currentLocalizations().displayDetailsTitle,
        ),
      ),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : error != null || current == null
          ? Center(
              child: FilledButton.tonalIcon(
                onPressed: load,
                icon: const Icon(Icons.refresh_rounded),
                label: Text(currentLocalizations().topologyRefresh),
              ),
            )
          : ListView(
              padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
              children: [
                Card(
                  child: ListTile(
                    leading: Icon(
                      current.dextopOverlay
                          ? Icons.phone_android_rounded
                          : Icons.monitor_rounded,
                    ),
                    title: Text(current.name),
                    subtitle: Text(
                      '${current.widthPx} × ${current.heightPx} · '
                      '${current.densityDpi} dpi · ID ${current.id}',
                    ),
                  ),
                ),
                const SizedBox(height: 20),
                Text(
                  currentLocalizations().displaySupportedResolutions,
                  style: Theme.of(context).textTheme.labelLarge?.copyWith(
                    color: Theme.of(context).colorScheme.primary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 8),
                if (current.dextopOverlay)
                  Card(
                    child: ListTile(
                      leading: const Icon(Icons.info_outline_rounded),
                      title: Text(
                        currentLocalizations().displayDextopResolutionTitle,
                      ),
                      subtitle: Text(
                        currentLocalizations().displayDextopResolutionSummary,
                      ),
                    ),
                  )
                else
                  Card(
                    child: RadioGroup<int>(
                      groupValue: current.activeModeId,
                      onChanged: onModeChanged,
                      child: Column(
                        children: [
                          for (
                            var index = 0;
                            index < current.supportedModes.length;
                            index++
                          ) ...[
                            if (index > 0) const Divider(height: 1),
                            RadioListTile<int>(
                              value: current.supportedModes[index].id,
                              title: Text(current.supportedModes[index].label),
                              subtitle:
                                  current.supportedModes[index].id ==
                                      current.activeModeId
                                  ? Text(
                                      currentLocalizations().displayCurrentMode,
                                    )
                                  : null,
                            ),
                          ],
                        ],
                      ),
                    ),
                  ),
              ],
            ),
    );
  }
}

class DisplayTopologyEditor extends StatefulWidget {
  const DisplayTopologyEditor({
    required this.bridge,
    this.onDisplaySelected,
    this.showSettingsHeader = false,
    super.key,
  });
  final NativeBridge bridge;
  final ValueChanged<int>? onDisplaySelected;
  final bool showSettingsHeader;

  @override
  State<DisplayTopologyEditor> createState() => _DisplayTopologyEditorState();
}

class _DisplayTopologyEditorState extends State<DisplayTopologyEditor> {
  var loading = true;
  var applying = false;
  String? error;
  List<_TopologyDisplay> displays = const [];
  Map<int, Offset> original = const {};
  Timer? topologyMonitor;
  var topologyPollInFlight = false;
  String? lastNativeSignature;
  int? selectedDisplayId;

  _TopologyDisplay? get selectedDisplay {
    final id = selectedDisplayId;
    if (id == null) return null;
    for (final display in displays) {
      if (display.id == id) return display;
    }
    return null;
  }

  @override
  void initState() {
    super.initState();
    load();
    topologyMonitor = Timer.periodic(
      const Duration(milliseconds: 800),
      (_) => refreshIfTopologyChanged(),
    );
  }

  @override
  void dispose() {
    topologyMonitor?.cancel();
    super.dispose();
  }

  String topologySignature(List<_TopologyDisplay> value) =>
      (value.toList()..sort((a, b) => a.id.compareTo(b.id)))
          .map(
            (display) =>
                '${display.id}:${display.widthPx}x${display.heightPx}:'
                '${display.position.dx},${display.position.dy}',
          )
          .join('|');

  Future<void> refreshIfTopologyChanged() async {
    if (topologyPollInFlight || applying || loading) return;
    topologyPollInFlight = true;
    try {
      final state = await widget.bridge.displayTopology();
      if (state['supported'] != true) return;
      final next = (state['displays'] as List? ?? const [])
          .map(
            (item) => _TopologyDisplay.fromMap(
              Map<String, dynamic>.from(item as Map),
            ),
          )
          .toList();
      final signature = topologySignature(next);
      if (!mounted || signature == lastNativeSignature) return;
      if (next.length < 2) {
        lastNativeSignature = signature;
        await Navigator.maybePop(context);
        return;
      }
      setState(() {
        displays = next;
        original = {for (final display in next) display.id: display.position};
        lastNativeSignature = signature;
        if (!next.any((display) => display.id == selectedDisplayId)) {
          selectedDisplayId = null;
        }
        error = null;
      });
    } catch (_) {
      // A transient disconnect is expected while displays are being rebuilt.
      // The next monitor tick retries without replacing the current layout.
    } finally {
      topologyPollInFlight = false;
    }
  }

  Future<void> load() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final state = await widget.bridge.displayTopology();
      if (state['supported'] != true) {
        throw Exception(
          state['reason'] ?? currentLocalizations().topologyUnavailable,
        );
      }
      final next = (state['displays'] as List? ?? const [])
          .map(
            (item) => _TopologyDisplay.fromMap(
              Map<String, dynamic>.from(item as Map),
            ),
          )
          .toList();
      if (!mounted) return;
      setState(() {
        displays = next;
        original = {for (final display in next) display.id: display.position};
        lastNativeSignature = topologySignature(next);
        if (!next.any((display) => display.id == selectedDisplayId)) {
          selectedDisplayId = null;
        }
        loading = false;
      });
    } catch (exception) {
      if (!mounted) return;
      setState(() {
        error = exception.toString();
        loading = false;
      });
    }
  }

  void resetPositions() => setState(() {
    displays = [
      for (final display in displays)
        display.copyWith(position: original[display.id] ?? display.position),
    ];
  });

  Future<void> apply() async {
    setState(() => applying = true);
    try {
      final state = await widget.bridge.setDisplayTopology({
        for (final display in displays)
          '${display.id}': {'x': display.position.dx, 'y': display.position.dy},
      });
      final next = (state['displays'] as List? ?? const [])
          .map(
            (item) => _TopologyDisplay.fromMap(
              Map<String, dynamic>.from(item as Map),
            ),
          )
          .toList();
      if (!mounted) return;
      setState(() {
        displays = next;
        original = {for (final display in next) display.id: display.position};
        lastNativeSignature = topologySignature(next);
        if (!next.any((display) => display.id == selectedDisplayId)) {
          selectedDisplayId = null;
        }
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(currentLocalizations().topologyApplied)),
      );
    } catch (exception) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(exception.toString())));
      }
    } finally {
      if (mounted) setState(() => applying = false);
    }
  }

  void identify() {
    showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(currentLocalizations().topologyIdentify),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (var index = 0; index < displays.length; index++)
              ListTile(
                leading: CircleAvatar(child: Text('${index + 1}')),
                title: Text(displays[index].name),
                subtitle: Text(
                  '${displays[index].widthPx} × ${displays[index].heightPx} · ID ${displays[index].id}',
                ),
              ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(currentLocalizations().close),
          ),
        ],
      ),
    );
  }

  Future<void> selectMode(_DisplayMode mode) async {
    final display = selectedDisplay;
    if (display == null || display.dextopOverlay || applying) return;
    setState(() => applying = true);
    try {
      await widget.bridge.setDisplayPreferredMode(
        displayId: display.id,
        width: mode.width,
        height: mode.height,
        refreshRate: mode.refreshRate,
      );
      await load();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(currentLocalizations().displayModeApplied)),
        );
      }
    } catch (exception) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(exception.toString())));
      }
    } finally {
      if (mounted) setState(() => applying = false);
    }
  }

  Widget _selectedDisplayModes(BuildContext context) {
    final display = selectedDisplay;
    if (display == null) {
      return Card(
        margin: EdgeInsets.zero,
        child: ListTile(
          leading: const Icon(Icons.touch_app_rounded),
          title: Text(currentLocalizations().displayResolutionListTitle),
          subtitle: Text(currentLocalizations().displayResolutionListSummary),
        ),
      );
    }
    if (display.dextopOverlay) {
      return Card(
        margin: EdgeInsets.zero,
        child: ListTile(
          leading: const Icon(Icons.info_outline_rounded),
          title: Text(display.name),
          subtitle: Text(currentLocalizations().displayDextopResolutionSummary),
        ),
      );
    }
    void onModeChanged(int? id) {
      if (applying || id == null) return;
      final mode = display.supportedModes
          .where((item) => item.id == id)
          .firstOrNull;
      if (mode != null) unawaited(selectMode(mode));
    }

    return Card(
      margin: EdgeInsets.zero,
      clipBehavior: Clip.antiAlias,
      child: ExpansionTile(
        initiallyExpanded: true,
        leading: const Icon(Icons.monitor_rounded),
        title: Text(display.name),
        subtitle: Text(
          '${display.widthPx} × ${display.heightPx} · '
          '${display.densityDpi} dpi · ${display.refreshRate.toStringAsFixed(display.refreshRate == display.refreshRate.roundToDouble() ? 0 : 1)} Hz',
        ),
        children: [
          RadioGroup<int>(
            groupValue: display.activeModeId,
            onChanged: onModeChanged,
            child: Column(
              children: [
                for (
                  var index = 0;
                  index < display.supportedModes.length;
                  index++
                ) ...[
                  if (index > 0) const Divider(height: 1),
                  RadioListTile<int>(
                    value: display.supportedModes[index].id,
                    title: Text(display.supportedModes[index].label),
                    subtitle:
                        display.supportedModes[index].id == display.activeModeId
                        ? Text(currentLocalizations().displayCurrentMode)
                        : null,
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (loading) return const Center(child: CircularProgressIndicator());
    if (error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.desktop_access_disabled_rounded, size: 42),
              const SizedBox(height: 12),
              Text(error!, textAlign: TextAlign.center),
              const SizedBox(height: 16),
              OutlinedButton.icon(
                onPressed: load,
                icon: const Icon(Icons.refresh_rounded),
                label: Text(currentLocalizations().topologyRefresh),
              ),
            ],
          ),
        ),
      );
    }
    return LayoutBuilder(
      builder: (context, constraints) => ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
        children: [
          if (widget.showSettingsHeader) ...[
            Text(
              currentLocalizations().topologyArrangeDisplays,
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 4),
          ],
          Text(currentLocalizations().topologyDescription),
          const SizedBox(height: 12),
          SizedBox(
            height: (constraints.maxHeight * .42).clamp(250.0, 390.0),
            child: Card(
              margin: EdgeInsets.zero,
              clipBehavior: Clip.antiAlias,
              child: _TopologyCanvas(
                displays: displays,
                selectedDisplayId: selectedDisplayId,
                onDisplaySelected: (id) {
                  setState(() => selectedDisplayId = id);
                  widget.onDisplaySelected?.call(id);
                },
                onMoved: (id, proposedPosition) => setState(() {
                  final moving = displays.firstWhere((item) => item.id == id);
                  displays = [
                    for (final display in displays)
                      if (display.id == id)
                        display.copyWith(
                          position: _snapDisplayPosition(
                            moving,
                            proposedPosition,
                          ),
                        )
                      else
                        display,
                  ];
                }),
              ),
            ),
          ),
          const SizedBox(height: 12),
          _selectedDisplayModes(context),
          const SizedBox(height: 12),
          LayoutBuilder(
            builder: (context, constraints) {
              final columns = constraints.maxWidth >= 600 ? 4 : 2;
              final buttonWidth =
                  (constraints.maxWidth - (columns - 1) * 8) / columns;
              return Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  SizedBox(
                    width: buttonWidth,
                    height: 44,
                    child: FilledButton.tonalIcon(
                      onPressed: applying
                          ? null
                          : () => Navigator.maybePop(context),
                      icon: const Icon(Icons.close_rounded, size: 18),
                      label: Text(
                        MaterialLocalizations.of(context).cancelButtonLabel,
                      ),
                    ),
                  ),
                  SizedBox(
                    width: buttonWidth,
                    height: 44,
                    child: FilledButton.tonalIcon(
                      onPressed: applying ? null : resetPositions,
                      icon: const Icon(Icons.restart_alt_rounded, size: 18),
                      label: Text(currentLocalizations().topologyReset),
                    ),
                  ),
                  SizedBox(
                    width: buttonWidth,
                    height: 44,
                    child: FilledButton.tonalIcon(
                      onPressed: displays.isEmpty ? null : identify,
                      icon: const Icon(Icons.visibility_rounded, size: 18),
                      label: Text(currentLocalizations().topologyIdentify),
                    ),
                  ),
                  SizedBox(
                    width: buttonWidth,
                    height: 44,
                    child: FilledButton.icon(
                      onPressed: applying || displays.length < 2 ? null : apply,
                      icon: const Icon(Icons.check_rounded, size: 18),
                      label: Text(currentLocalizations().topologyApply),
                    ),
                  ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }

  Offset _snapDisplayPosition(
    _TopologyDisplay moving,
    Offset proposedPosition,
  ) {
    final others = displays.where((item) => item.id != moving.id).toList();
    if (others.isEmpty) return proposedPosition;
    final candidates = <_TopologySnap>[];
    for (final anchor in others) {
      final minimumVerticalContact = min(
        80.0,
        min(moving.heightDp, anchor.heightDp) * .25,
      );
      final minimumHorizontalContact = min(
        80.0,
        min(moving.widthDp, anchor.widthDp) * .25,
      );
      final verticalOffset = proposedPosition.dy.clamp(
        anchor.position.dy - moving.heightDp + minimumVerticalContact,
        anchor.position.dy + anchor.heightDp - minimumVerticalContact,
      );
      final horizontalOffset = proposedPosition.dx.clamp(
        anchor.position.dx - moving.widthDp + minimumHorizontalContact,
        anchor.position.dx + anchor.widthDp - minimumHorizontalContact,
      );
      candidates.addAll([
        _TopologySnap(
          Offset(anchor.position.dx - moving.widthDp, verticalOffset),
          proposedPosition,
        ),
        _TopologySnap(
          Offset(anchor.position.dx + anchor.widthDp, verticalOffset),
          proposedPosition,
        ),
        _TopologySnap(
          Offset(horizontalOffset, anchor.position.dy - moving.heightDp),
          proposedPosition,
        ),
        _TopologySnap(
          Offset(horizontalOffset, anchor.position.dy + anchor.heightDp),
          proposedPosition,
        ),
      ]);
    }
    final collisionFree = candidates.where((candidate) {
      final movingRect = Rect.fromLTWH(
        candidate.position.dx,
        candidate.position.dy,
        moving.widthDp,
        moving.heightDp,
      );
      return others.every((other) {
        final otherRect = Rect.fromLTWH(
          other.position.dx,
          other.position.dy,
          other.widthDp,
          other.heightDp,
        );
        final overlap = movingRect.intersect(otherRect);
        return overlap.width <= .5 || overlap.height <= .5;
      });
    }).toList();
    final available = collisionFree.isEmpty ? candidates : collisionFree;
    available.sort((a, b) => a.distanceSquared.compareTo(b.distanceSquared));
    return available.first.position;
  }
}

class _TopologySnap {
  _TopologySnap(this.position, Offset proposed)
    : distanceSquared =
          (position.dx - proposed.dx) * (position.dx - proposed.dx) +
          (position.dy - proposed.dy) * (position.dy - proposed.dy);

  final Offset position;
  final double distanceSquared;
}

class _TopologyCanvas extends StatefulWidget {
  const _TopologyCanvas({
    required this.displays,
    required this.onMoved,
    required this.selectedDisplayId,
    this.onDisplaySelected,
  });
  final List<_TopologyDisplay> displays;
  final void Function(int id, Offset position) onMoved;
  final ValueChanged<int>? onDisplaySelected;
  final int? selectedDisplayId;

  @override
  State<_TopologyCanvas> createState() => _TopologyCanvasState();
}

class _TopologyCanvasState extends State<_TopologyCanvas> {
  Size? viewportSize;
  String? displaySignature;
  double scale = 1;
  Offset origin = Offset.zero;
  final Map<int, Offset> dragStarts = {};
  final Map<int, Offset> dragTotals = {};

  String get signature => widget.displays
      .map((display) => '${display.id}:${display.widthDp}:${display.heightDp}')
      .join('|');

  void updateTransform(BoxConstraints constraints) {
    final nextSize = Size(constraints.maxWidth, constraints.maxHeight);
    final nextSignature = signature;
    if (viewportSize == nextSize && displaySignature == nextSignature) return;
    viewportSize = nextSize;
    displaySignature = nextSignature;
    if (widget.displays.isEmpty) return;
    final minX = widget.displays.map((d) => d.position.dx).reduce(min);
    final minY = widget.displays.map((d) => d.position.dy).reduce(min);
    final maxX = widget.displays
        .map((d) => d.position.dx + d.widthDp)
        .reduce(max);
    final maxY = widget.displays
        .map((d) => d.position.dy + d.heightDp)
        .reduce(max);
    const padding = 28.0;
    scale = min(
      (constraints.maxWidth - padding * 2) / max(1, maxX - minX),
      (constraints.maxHeight - padding * 2) / max(1, maxY - minY),
    ).clamp(.04, .45);
    origin = Offset(
      (constraints.maxWidth - (maxX - minX) * scale) / 2 - minX * scale,
      (constraints.maxHeight - (maxY - minY) * scale) / 2 - minY * scale,
    );
  }

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      if (widget.displays.isEmpty) {
        return Center(child: Text(currentLocalizations().topologyNoDisplays));
      }
      updateTransform(constraints);
      final colors = Theme.of(context).colorScheme;
      return Stack(
        clipBehavior: Clip.none,
        children: [
          for (var index = 0; index < widget.displays.length; index++)
            Positioned(
              left: origin.dx + widget.displays[index].position.dx * scale,
              top: origin.dy + widget.displays[index].position.dy * scale,
              width: max(72, widget.displays[index].widthDp * scale),
              height: max(54, widget.displays[index].heightDp * scale),
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: () =>
                    widget.onDisplaySelected?.call(widget.displays[index].id),
                onPanStart: (_) {
                  final display = widget.displays[index];
                  dragStarts[display.id] = display.position;
                  dragTotals[display.id] = Offset.zero;
                },
                onPanUpdate: (details) {
                  final display = widget.displays[index];
                  final total =
                      (dragTotals[display.id] ?? Offset.zero) +
                      details.delta / scale;
                  dragTotals[display.id] = total;
                  widget.onMoved(
                    display.id,
                    (dragStarts[display.id] ?? display.position) + total,
                  );
                },
                onPanEnd: (_) {
                  final id = widget.displays[index].id;
                  dragStarts.remove(id);
                  dragTotals.remove(id);
                },
                onPanCancel: () {
                  final id = widget.displays[index].id;
                  dragStarts.remove(id);
                  dragTotals.remove(id);
                },
                child: Material(
                  elevation: 2,
                  color: widget.displays[index].id == widget.selectedDisplayId
                      ? colors.secondaryContainer
                      : widget.displays[index].primary
                      ? colors.primary
                      : colors.surfaceContainerHighest,
                  shape: RoundedRectangleBorder(
                    side: BorderSide(
                      color:
                          widget.displays[index].id == widget.selectedDisplayId
                          ? colors.primary
                          : colors.outlineVariant,
                      width:
                          widget.displays[index].id == widget.selectedDisplayId
                          ? 3
                          : 1,
                    ),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          '${index + 1}',
                          style: Theme.of(context).textTheme.headlineSmall
                              ?.copyWith(
                                color:
                                    widget.displays[index].id ==
                                        widget.selectedDisplayId
                                    ? colors.onSecondaryContainer
                                    : widget.displays[index].primary
                                    ? colors.onPrimary
                                    : colors.onSurface,
                              ),
                        ),
                        Text(
                          widget.displays[index].name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 10,
                            color:
                                widget.displays[index].id ==
                                    widget.selectedDisplayId
                                ? colors.onSecondaryContainer
                                : widget.displays[index].primary
                                ? colors.onPrimary
                                : colors.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
        ],
      );
    },
  );
}

class _TopologyDisplay {
  const _TopologyDisplay({
    required this.id,
    required this.name,
    required this.position,
    required this.widthDp,
    required this.heightDp,
    required this.widthPx,
    required this.heightPx,
    required this.densityDpi,
    required this.activeModeId,
    required this.refreshRate,
    required this.supportedModes,
    required this.dextopOverlay,
    required this.primary,
  });
  final int id;
  final String name;
  final Offset position;
  final double widthDp;
  final double heightDp;
  final int widthPx;
  final int heightPx;
  final int densityDpi;
  final int activeModeId;
  final double refreshRate;
  final List<_DisplayMode> supportedModes;
  final bool dextopOverlay;
  final bool primary;

  String get modeLabel =>
      '$widthPx × $heightPx · ${refreshRate.toStringAsFixed(refreshRate == refreshRate.roundToDouble() ? 0 : 1)} Hz';

  factory _TopologyDisplay.fromMap(Map<String, dynamic> map) =>
      _TopologyDisplay(
        id: (map['id'] as num).toInt(),
        name: map['dextopOverlay'] == true
            ? currentLocalizations().topologyBuiltInScreen
            : map['name']?.toString() ?? 'Display',
        position: Offset(
          (map['x'] as num?)?.toDouble() ?? 0,
          (map['y'] as num?)?.toDouble() ?? 0,
        ),
        widthDp: (map['widthDp'] as num?)?.toDouble() ?? 640,
        heightDp: (map['heightDp'] as num?)?.toDouble() ?? 360,
        widthPx: (map['widthPx'] as num?)?.toInt() ?? 0,
        heightPx: (map['heightPx'] as num?)?.toInt() ?? 0,
        densityDpi: (map['densityDpi'] as num?)?.toInt() ?? 0,
        activeModeId: (map['activeModeId'] as num?)?.toInt() ?? -1,
        refreshRate: (map['refreshRate'] as num?)?.toDouble() ?? 0,
        supportedModes: (map['supportedModes'] as List? ?? const [])
            .map(
              (item) =>
                  _DisplayMode.fromMap(Map<String, dynamic>.from(item as Map)),
            )
            .toList(),
        dextopOverlay: map['dextopOverlay'] == true,
        primary: map['primary'] == true,
      );

  _TopologyDisplay copyWith({Offset? position}) => _TopologyDisplay(
    id: id,
    name: name,
    position: position ?? this.position,
    widthDp: widthDp,
    heightDp: heightDp,
    widthPx: widthPx,
    heightPx: heightPx,
    densityDpi: densityDpi,
    activeModeId: activeModeId,
    refreshRate: refreshRate,
    supportedModes: supportedModes,
    dextopOverlay: dextopOverlay,
    primary: primary,
  );
}

class _DisplayMode {
  const _DisplayMode({
    required this.id,
    required this.width,
    required this.height,
    required this.refreshRate,
  });

  final int id;
  final int width;
  final int height;
  final double refreshRate;

  String get label =>
      '$width × $height · ${refreshRate.toStringAsFixed(refreshRate == refreshRate.roundToDouble() ? 0 : 1)} Hz';

  factory _DisplayMode.fromMap(Map<String, dynamic> map) => _DisplayMode(
    id: (map['id'] as num?)?.toInt() ?? -1,
    width: (map['width'] as num?)?.toInt() ?? 0,
    height: (map['height'] as num?)?.toInt() ?? 0,
    refreshRate: (map['refreshRate'] as num?)?.toDouble() ?? 0,
  );
}
