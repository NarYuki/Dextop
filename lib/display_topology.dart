part of 'main.dart';

class DisplayEnvironmentSettingsCard extends StatefulWidget {
  const DisplayEnvironmentSettingsCard({
    required this.bridge,
    this.showDisplay = true,
    this.showConvenience = true,
    this.showTopology,
    this.displayLeadingDivider = false,
    this.wrapInCard = true,
    super.key,
  });
  final NativeBridge bridge;
  final bool showDisplay;
  final bool showConvenience;
  final bool? showTopology;
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
  var topologyAvailable = false;
  var topologyAvailabilityPollInFlight = false;
  Timer? topologyAvailabilityMonitor;

  bool get topologyVisible => widget.showTopology ?? widget.showConvenience;

  @override
  void initState() {
    super.initState();
    load();
    if (topologyVisible) {
      topologyAvailabilityMonitor = Timer.periodic(
        const Duration(milliseconds: 800),
        (_) => refreshTopologyAvailability(),
      );
    }
  }

  @override
  void dispose() {
    topologyAvailabilityMonitor?.cancel();
    super.dispose();
  }

  Future<void> refreshTopologyAvailability() async {
    if (!topologyVisible || topologyAvailabilityPollInFlight) return;
    topologyAvailabilityPollInFlight = true;
    try {
      final state = await widget.bridge.displayTopology();
      final available =
          state['supported'] == true &&
          (state['displays'] as List? ?? const []).length >= 2;
      if (mounted && available != topologyAvailable) {
        setState(() => topologyAvailable = available);
      }
    } catch (_) {
      if (mounted && topologyAvailable) {
        setState(() => topologyAvailable = false);
      }
    } finally {
      topologyAvailabilityPollInFlight = false;
    }
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
    unawaited(refreshTopologyAvailability());
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
        if (topologyVisible)
          ListTile(
            enabled: topologyAvailable,
            leading: const Icon(Icons.account_tree_rounded),
            title: Text(AppStrings.tr('topologyArrangeDisplays')),
            subtitle: Text(AppStrings.tr('topologySummary')),
            trailing: const Icon(Icons.chevron_right_rounded),
            onTap: topologyAvailable
                ? () => showDialog<void>(
                    context: context,
                    builder: (dialogContext) => Dialog(
                      clipBehavior: Clip.antiAlias,
                      child: ConstrainedBox(
                        constraints: const BoxConstraints(
                          maxWidth: 860,
                          maxHeight: 680,
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            Padding(
                              padding: const EdgeInsets.fromLTRB(
                                24,
                                20,
                                12,
                                12,
                              ),
                              child: Row(
                                children: [
                                  const Icon(Icons.account_tree_rounded),
                                  const SizedBox(width: 12),
                                  Expanded(
                                    child: Text(
                                      AppStrings.tr('topologyTitle'),
                                      style: Theme.of(
                                        context,
                                      ).textTheme.headlineSmall,
                                    ),
                                  ),
                                  IconButton(
                                    tooltip: MaterialLocalizations.of(
                                      context,
                                    ).closeButtonTooltip,
                                    onPressed: () =>
                                        Navigator.pop(dialogContext),
                                    icon: const Icon(Icons.close_rounded),
                                  ),
                                ],
                              ),
                            ),
                            const Divider(height: 1),
                            Expanded(
                              child: DisplayTopologyEditor(
                                bridge: widget.bridge,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  )
                : null,
          ),
        if (topologyVisible && widget.showConvenience) const Divider(height: 1),
        if (widget.showConvenience)
          SwitchListTile(
            secondary: const Icon(Icons.phone_android_rounded),
            title: Text(AppStrings.tr('samsungIncludePhoneDisplay')),
            subtitle: Text(AppStrings.tr('displayIncludePhoneSummary')),
            value: includePhoneDisplay,
            onChanged: loading
                ? null
                : (value) => save('includePhoneDisplay', value),
          ),
        if (widget.showConvenience) const Divider(height: 1),
        if (widget.showConvenience)
          SwitchListTile(
            secondary: const Icon(Icons.vertical_align_bottom_rounded),
            title: Text(AppStrings.tr('samsungAutoHideTaskbar')),
            subtitle: Text(AppStrings.tr('displayAutoHideTaskbarSummary')),
            value: autoHideTaskbar,
            onChanged: loading
                ? null
                : (value) => save('autoHideTaskbar', value),
          ),
        if (widget.showDisplay && supportsInternal120Hz) ...[
          if (topologyVisible || widget.displayLeadingDivider)
            const Divider(height: 1),
          SwitchListTile(
            secondary: const Icon(Icons.speed_rounded),
            title: Text(AppStrings.tr('displayForceInternal120Hz')),
            subtitle: Text(AppStrings.tr('displayForceInternal120HzSummary')),
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

class DisplayTopologyEditor extends StatefulWidget {
  const DisplayTopologyEditor({required this.bridge, super.key});
  final NativeBridge bridge;

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
          state['reason'] ?? AppStrings.tr('topologyUnavailable'),
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
      });
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(AppStrings.tr('topologyApplied'))));
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
        title: Text(AppStrings.tr('topologyIdentify')),
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
            child: Text(AppStrings.tr('close')),
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
                label: Text(AppStrings.tr('topologyRefresh')),
              ),
            ],
          ),
        ),
      );
    }
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(AppStrings.tr('topologyDescription')),
          const SizedBox(height: 12),
          Expanded(
            child: Card(
              margin: EdgeInsets.zero,
              clipBehavior: Clip.antiAlias,
              child: _TopologyCanvas(
                displays: displays,
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
                      label: Text(AppStrings.tr('topologyReset')),
                    ),
                  ),
                  SizedBox(
                    width: buttonWidth,
                    height: 44,
                    child: FilledButton.tonalIcon(
                      onPressed: displays.isEmpty ? null : identify,
                      icon: const Icon(Icons.visibility_rounded, size: 18),
                      label: Text(AppStrings.tr('topologyIdentify')),
                    ),
                  ),
                  SizedBox(
                    width: buttonWidth,
                    height: 44,
                    child: FilledButton.icon(
                      onPressed: applying || displays.length < 2 ? null : apply,
                      icon: const Icon(Icons.check_rounded, size: 18),
                      label: Text(AppStrings.tr('topologyApply')),
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
  const _TopologyCanvas({required this.displays, required this.onMoved});
  final List<_TopologyDisplay> displays;
  final void Function(int id, Offset position) onMoved;

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
        return Center(child: Text(AppStrings.tr('topologyNoDisplays')));
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
                  color: widget.displays[index].primary
                      ? colors.primary
                      : colors.surfaceContainerHighest,
                  shape: RoundedRectangleBorder(
                    side: BorderSide(color: colors.outlineVariant),
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
                                color: widget.displays[index].primary
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
                            color: widget.displays[index].primary
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
    required this.primary,
  });
  final int id;
  final String name;
  final Offset position;
  final double widthDp;
  final double heightDp;
  final int widthPx;
  final int heightPx;
  final bool primary;

  factory _TopologyDisplay.fromMap(Map<String, dynamic> map) =>
      _TopologyDisplay(
        id: (map['id'] as num).toInt(),
        name: map['dextopOverlay'] == true
            ? AppStrings.tr('topologyBuiltInScreen')
            : map['name']?.toString() ?? 'Display',
        position: Offset(
          (map['x'] as num?)?.toDouble() ?? 0,
          (map['y'] as num?)?.toDouble() ?? 0,
        ),
        widthDp: (map['widthDp'] as num?)?.toDouble() ?? 640,
        heightDp: (map['heightDp'] as num?)?.toDouble() ?? 360,
        widthPx: (map['widthPx'] as num?)?.toInt() ?? 0,
        heightPx: (map['heightPx'] as num?)?.toInt() ?? 0,
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
    primary: primary,
  );
}
