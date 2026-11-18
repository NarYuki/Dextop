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
      M3ESnackbar.show(context, message: exception.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    final items = <Widget>[
      if (widget.showConvenience)
        DextopToggle(
          icon: M3EIcons.phone_android_rounded,
          title: currentLocalizations().samsungIncludePhoneDisplay,
          subtitle: currentLocalizations().displayIncludePhoneSummary,
          value: includePhoneDisplay,
          enabled: !loading,
          onChanged: loading
              ? null
              : (value) => save('includePhoneDisplay', value),
        ),
      if (widget.showConvenience)
        DextopToggle(
          icon: M3EIcons.vertical_align_bottom_rounded,
          title: currentLocalizations().samsungAutoHideTaskbar,
          subtitle: currentLocalizations().displayAutoHideTaskbarSummary,
          value: autoHideTaskbar,
          enabled: !loading,
          onChanged: loading ? null : (value) => save('autoHideTaskbar', value),
        ),
      if (widget.showDisplay && supportsInternal120Hz)
        DextopToggle(
          icon: M3EIcons.speed_rounded,
          title: currentLocalizations().displayForceInternal120Hz,
          subtitle: currentLocalizations().displayForceInternal120HzSummary,
          value: forceInternal120Hz,
          enabled: !loading,
          onChanged: loading
              ? null
              : (value) => save('forceInternal120Hz', value),
        ),
    ];
    if (items.isEmpty) return const SizedBox.shrink();
    return EnsureDextopExpressive(child: DextopGroup(children: items));
  }
}

class DisplaySettingsPage extends StatelessWidget {
  const DisplaySettingsPage({required this.bridge, super.key});

  final NativeBridge bridge;

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: Theme.of(context).colorScheme.surface,
    appBar: M3EAppBar.top(
      automaticallyImplyLeading: true,
      titleText: currentLocalizations().displaySettingsTitle,
    ),
    body: DisplayTopologyEditor(
      bridge: bridge,
      showSettingsHeader: true,
      belowArrangement: ExternalDisplayModeCard(bridge: bridge),
    ),
  );
}

/// How a connected external screen shows the Dextop desktop, as in a desktop OS's display
/// settings: the phone only, the external screen only, duplicated, or extended across both.
/// Nothing changes until the user picks here.
class ExternalDisplayModeCard extends StatefulWidget {
  const ExternalDisplayModeCard({required this.bridge, super.key});

  final NativeBridge bridge;

  @override
  State<ExternalDisplayModeCard> createState() =>
      _ExternalDisplayModeCardState();
}

class _ExternalDisplayModeCardState extends State<ExternalDisplayModeCard> {
  Map<String, dynamic> state = const {};
  String main = '2';
  String phoneRole = 'phone';
  bool busy = false;
  Timer? monitor;
  String t(String key) => localText(context, key);

  @override
  void initState() {
    super.initState();
    load();
    // Plugging the screen in or starting the desktop shows up while the page is open.
    monitor = Timer.periodic(const Duration(seconds: 2), (_) {
      if (!busy) load();
    });
  }

  @override
  void dispose() {
    monitor?.cancel();
    super.dispose();
  }

  Future<void> load() async {
    final next = await widget.bridge.externalDisplayState();
    final preferences = await SharedPreferences.getInstance();
    if (!mounted) return;
    setState(() {
      state = next;
      main = preferences.getString('external_display_main') ?? '2';
      phoneRole = preferences.getString('external_display_phone') ?? 'phone';
    });
  }

  Future<void> choose(String mode) async {
    if (busy) return;
    setState(() => busy = true);
    final outcome = await widget.bridge.setExternalDisplay(mode);
    await load();
    if (!mounted) return;
    setState(() => busy = false);
    if (outcome == 'ok') return;
    M3ESnackbar.show(
      context,
      message: switch (outcome) {
        'no_session' => t('uiStartTheDesktopFirst'),
        'no_display' => t('uiTheExternalDisplayIsGone'),
        _ => t('uiCouldNotShowTheDesktopOnTheExternalDisplay'),
      },
    );
  }

  /// Saves an extended-desktop preference; a running extended desktop takes it at once.
  Future<void> setArrangement(String key, String value) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setString(key, value);
    setState(() {
      if (key == 'external_display_phone') {
        phoneRole = value;
      } else {
        main = value;
      }
    });
    final mode = state['mode'];
    if (mode == 'extend' || mode == 'external') await choose(mode as String);
  }

  Future<void> _pickString(
    String title,
    IconData icon,
    String current,
    Map<String, String> items,
    ValueChanged<String> onSelected,
  ) async {
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
                for (final entry in items.entries)
                  DextopChoice<String>(
                    value: entry.key,
                    groupValue: current,
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
    if (selected != null && selected != current) onSelected(selected);
  }

  @override
  Widget build(BuildContext context) {
    if (state['connected'] != true) return const SizedBox.shrink();
    final mode = state['mode'] as String? ?? 'off';
    final running = state['running'] == true;
    final enabled = !busy;
    final name = state['name'] as String? ?? t('uiExternalDisplay');
    final modeItems = {
      'mirror': t('uiDuplicateTheseDisplays'),
      'extend': t('uiExtendTheseDisplays'),
      'off': t('uiShowOnlyOn1'),
      'external': t('uiShowOnlyOn2'),
    };
    final phoneItems = {
      'phone': t('uiPhone'),
      'touchpad': t('uiTrackpadAndKeyboard'),
    };
    final mainItems = {
      '1': t('ui1Phone'),
      '2': inlineFormat(t('ui20'), [name]),
    };
    return Padding(
      padding: const EdgeInsets.only(top: 14),
      child: DextopGroup(
        children: [
          M3EListItem(
            leading: const Icon(M3EIcons.connected_tv_outlined),
            headline: t('uiMultipleDisplays'),
            supportingText: running
                ? inlineFormat(t('ui1Phone20'), [name])
                : t('uiAppliesWhenTheDesktopStarts'),
            supportingMaxLines: null,
            enabled: enabled,
            trailing: DextopDropdownButton(
              label: modeItems[mode] ?? mode,
              enabled: enabled,
              onPressed: enabled
                  ? () => _pickString(
                      t('uiMultipleDisplays'),
                      M3EIcons.connected_tv_outlined,
                      mode,
                      modeItems,
                      choose,
                    )
                  : null,
            ),
            onTap: enabled
                ? () => _pickString(
                    t('uiMultipleDisplays'),
                    M3EIcons.connected_tv_outlined,
                    mode,
                    modeItems,
                    choose,
                  )
                : null,
          ),
          if (mode == 'external')
            M3EListItem(
              leading: const Icon(M3EIcons.touch_app_rounded),
              headline: t('uiUseThePhoneAs'),
              supportingText: phoneRole == 'phone'
                  ? t('uiThePhoneWorksAsUsual')
                  : t('uiControlTheDesktopFromThePhoneScreen'),
              supportingMaxLines: null,
              enabled: enabled,
              trailing: DextopDropdownButton(
                label: phoneItems[phoneRole] ?? phoneRole,
                enabled: enabled,
                onPressed: enabled
                    ? () => _pickString(
                        t('uiUseThePhoneAs'),
                        M3EIcons.touch_app_rounded,
                        phoneRole,
                        phoneItems,
                        (v) => setArrangement('external_display_phone', v),
                      )
                    : null,
              ),
              onTap: enabled
                  ? () => _pickString(
                      t('uiUseThePhoneAs'),
                      M3EIcons.touch_app_rounded,
                      phoneRole,
                      phoneItems,
                      (v) => setArrangement('external_display_phone', v),
                    )
                  : null,
            ),
          if (mode == 'extend')
            M3EListItem(
              leading: const Icon(M3EIcons.monitor_rounded),
              headline: t('uiMainDisplay'),
              supportingText: t('uiTheScreenThatShowsTheTaskbar'),
              supportingMaxLines: null,
              enabled: enabled,
              trailing: DextopDropdownButton(
                label: mainItems[main] ?? main,
                enabled: enabled,
                onPressed: enabled
                    ? () => _pickString(
                        t('uiMainDisplay'),
                        M3EIcons.monitor_rounded,
                        main,
                        mainItems,
                        (v) => setArrangement('external_display_main', v),
                      )
                    : null,
              ),
              onTap: enabled
                  ? () => _pickString(
                      t('uiMainDisplay'),
                      M3EIcons.monitor_rounded,
                      main,
                      mainItems,
                      (v) => setArrangement('external_display_main', v),
                    )
                  : null,
            ),
        ],
      ),
    );
  }
}

class DisplayTopologyPage extends StatelessWidget {
  const DisplayTopologyPage({required this.bridge, super.key});

  final NativeBridge bridge;

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: Theme.of(context).colorScheme.surface,
    appBar: M3EAppBar.top(
      automaticallyImplyLeading: true,
      titleText: currentLocalizations().topologyTitle,
    ),
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
    backgroundColor: Theme.of(context).colorScheme.surface,
    appBar: M3EAppBar.top(
      automaticallyImplyLeading: true,
      titleText: currentLocalizations().displayResolutionListTitle,
      actions: [
        M3EIconButton(
          variant: M3EIconButtonVariant.standard,
          tooltip: currentLocalizations().topologyRefresh,
          onPressed: loading ? null : load,
          icon: const Icon(M3EIcons.refresh_rounded),
        ),
      ],
    ),
    body: loading
        ? const Center(
            child: M3ELoadingIndicator(
              variant: M3ELoadingIndicatorVariant.contained,
            ),
          )
        : error != null
        ? Center(
            child: M3EButton.tonal(
              onPressed: load,
              icon: const Icon(M3EIcons.refresh_rounded),
              label: Text(currentLocalizations().topologyRefresh),
            ),
          )
        : ListView(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
            children: [
              DextopGroup(
                children: [
                  for (final item in displays)
                    M3EListItem(
                      leading: Icon(
                        item.dextopOverlay
                            ? M3EIcons.phone_android_rounded
                            : M3EIcons.monitor_rounded,
                      ),
                      headline: item.name,
                      supportingText: item.modeLabel,
                      trailing: const Icon(M3EIcons.chevron_right_rounded),
                      onTap: () => Navigator.of(context).push(
                        dextopRoute<void>(
                          DisplayResolutionDetailPage(
                            bridge: widget.bridge,
                            displayId: item.id,
                          ),
                        ),
                      ),
                    ),
                ],
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
        M3ESnackbar.show(
          context,
          message: currentLocalizations().displayModeApplied,
        );
      }
    } catch (exception) {
      if (mounted) {
        M3ESnackbar.show(context, message: exception.toString());
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
      backgroundColor: Theme.of(context).colorScheme.surface,
      appBar: M3EAppBar.top(
        automaticallyImplyLeading: true,
        titleText: current?.name ?? currentLocalizations().displayDetailsTitle,
      ),
      body: loading
          ? const Center(
              child: M3ELoadingIndicator(
                variant: M3ELoadingIndicatorVariant.contained,
              ),
            )
          : error != null || current == null
          ? Center(
              child: M3EButton.tonal(
                onPressed: load,
                icon: const Icon(M3EIcons.refresh_rounded),
                label: Text(currentLocalizations().topologyRefresh),
              ),
            )
          : ListView(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
              children: [
                DextopGroup(
                  children: [
                    M3EListItem(
                      leading: Icon(
                        current.dextopOverlay
                            ? M3EIcons.phone_android_rounded
                            : M3EIcons.monitor_rounded,
                      ),
                      headline: current.name,
                      supportingText:
                          '${current.widthPx} × ${current.heightPx} · '
                          '${current.densityDpi} dpi · ID ${current.id}',
                    ),
                  ],
                ),
                DextopSection(
                  currentLocalizations().displaySupportedResolutions,
                ),
                if (current.dextopOverlay)
                  DextopGroup(
                    children: [
                      M3EListItem(
                        leading: const Icon(M3EIcons.info_outline_rounded),
                        headline:
                            currentLocalizations().displayDextopResolutionTitle,
                        supportingText: currentLocalizations()
                            .displayDextopResolutionSummary,
                        supportingMaxLines: null,
                      ),
                    ],
                  )
                else
                  DextopGroup(
                    children: [
                      for (final mode in current.supportedModes)
                        DextopChoice<int>(
                          value: mode.id,
                          groupValue: current.activeModeId,
                          title: mode.label,
                          subtitle: mode.id == current.activeModeId
                              ? currentLocalizations().displayCurrentMode
                              : null,
                          enabled: !applying,
                          onChanged: (id) => onModeChanged(id),
                        ),
                    ],
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
    this.belowArrangement,
    super.key,
  });
  final NativeBridge bridge;
  final ValueChanged<int>? onDisplaySelected;
  final bool showSettingsHeader;

  /// Shown between the arrangement and the selected display's settings.
  final Widget? belowArrangement;

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
  final physicalDensities = <int, int>{};

  /// The display whose settings show: the chosen one, else the first real screen.
  _TopologyDisplay? get selectedDisplay {
    final id = selectedDisplayId;
    for (final display in displays) {
      if (display.id == id) return display;
    }
    for (final display in displays) {
      if (!display.dextopOverlay) return display;
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
      M3ESnackbar.show(
        context,
        message: currentLocalizations().topologyApplied,
      );
    } catch (exception) {
      if (mounted) {
        M3ESnackbar.show(context, message: exception.toString());
      }
    } finally {
      if (mounted) setState(() => applying = false);
    }
  }

  void identify() {
    M3EDialog.show<void>(
      context,
      dialog: Builder(
        builder: (dialogContext) {
          final colors = Theme.of(dialogContext).colorScheme;
          return M3EDialog(
            icon: const Icon(M3EIcons.visibility_rounded),
            title: currentLocalizations().topologyIdentify,
            content: DextopGroup(
              color: colors.surfaceContainer,
              children: [
                for (var index = 0; index < displays.length; index++)
                  M3EListItem(
                    leading: CircleAvatar(child: Text('${index + 1}')),
                    headline: displays[index].name,
                    supportingText:
                        '${displays[index].widthPx} × ${displays[index].heightPx} · ID ${displays[index].id}',
                  ),
              ],
            ),
            actions: [
              M3EButton.text(
                onPressed: () => Navigator.pop(dialogContext),
                child: Text(currentLocalizations().close),
              ),
            ],
          );
        },
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
        M3ESnackbar.show(
          context,
          message: currentLocalizations().displayModeApplied,
        );
      }
    } catch (exception) {
      if (mounted) {
        M3ESnackbar.show(context, message: exception.toString());
      }
    } finally {
      if (mounted) setState(() => applying = false);
    }
  }

  String t(String key) => localText(context, key);

  Future<void> applyDisplaySetting(Future<void> Function() change) async {
    if (applying) return;
    setState(() => applying = true);
    try {
      await change();
      await load();
    } catch (exception) {
      if (mounted) {
        M3ESnackbar.show(context, message: exception.toString());
      }
    } finally {
      if (mounted) setState(() => applying = false);
    }
  }

  Future<int?> physicalDensity(_TopologyDisplay display) async {
    final known = physicalDensities[display.id];
    if (known != null) return known;
    final value = await widget.bridge.displayPhysicalDensity(display.id);
    if (value != null && mounted) {
      setState(() => physicalDensities[display.id] = value);
    }
    return value;
  }

  Future<void> _pickSetting<T>(
    String title,
    IconData icon,
    T current,
    List<(T, String)> items,
    ValueChanged<T> onSelected,
  ) async {
    final selected = await M3EDialog.show<T>(
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
                for (final (item, label) in items)
                  DextopChoice<T>(
                    value: item,
                    groupValue: current,
                    allowReselect: true,
                    title: label,
                    onChanged: (next) => Navigator.pop(dialogContext, next),
                  ),
              ],
            ),
          );
        },
      ),
    );
    if (selected != null && selected != current) onSelected(selected);
  }

  M3EListItem _settingRow<T>({
    required IconData icon,
    required String title,
    String? subtitle,
    required T value,
    required List<(T, String)> items,
    required ValueChanged<T>? onSelected,
  }) {
    final activeLabel = items
        .firstWhere((entry) => entry.$1 == value, orElse: () => items.first)
        .$2;
    void openPicker() =>
        _pickSetting<T>(title, icon, value, items, onSelected!);
    return M3EListItem(
      leading: Icon(icon),
      headline: title,
      supportingText: subtitle,
      supportingMaxLines: null,
      enabled: onSelected != null,
      trailing: DextopDropdownButton(
        label: activeLabel,
        enabled: onSelected != null,
        onPressed: onSelected == null ? null : openPicker,
      ),
      onTap: onSelected == null ? null : openPicker,
    );
  }

  Widget _selectedDisplaySettings(BuildContext context) {
    final display = selectedDisplay;
    if (display == null) return const SizedBox.shrink();
    if (display.dextopOverlay) {
      return DextopGroup(
        children: [
          M3EListItem(
            leading: const Icon(M3EIcons.info_outline_rounded),
            headline: display.name,
            supportingText:
                currentLocalizations().displayDextopResolutionSummary,
            supportingMaxLines: null,
          ),
        ],
      );
    }
    final extendedScreen = display.extendedScreen != null;
    if (!extendedScreen && !physicalDensities.containsKey(display.id)) {
      unawaited(physicalDensity(display));
    }
    final enabled = !applying;

    // Scale: Android's 160 dpi is 100 %.
    final recommendedDpi = physicalDensities[display.id];
    final currentPercent = (display.densityDpi * 100 / 160).round();
    final recommendedPercent = recommendedDpi == null
        ? null
        : (recommendedDpi * 100 / 160).round();
    final percents = {
      ...[100, 125, 150, 175, 200, 225, 250, 300, 350, 400],
      currentPercent,
      ?recommendedPercent,
    }.toList()..sort();
    String percentLabel(int value) => value == recommendedPercent
        ? inlineFormat(t('ui0Recommended'), [value])
        : '$value%';

    // Resolution: each size once, at its highest refresh rate; the largest is recommended.
    final bySize = <String, _DisplayMode>{};
    for (final mode in display.supportedModes) {
      final key = '${mode.width}x${mode.height}';
      final known = bySize[key];
      if (known == null || mode.refreshRate > known.refreshRate) {
        bySize[key] = mode;
      }
    }
    final sizes = bySize.values.toList()
      ..sort((a, b) => (b.width * b.height).compareTo(a.width * a.height));
    final active = display.supportedModes
        .where((mode) => mode.id == display.activeModeId)
        .firstOrNull;
    final activeSize = active == null
        ? null
        : '${active.width}x${active.height}';
    String sizeLabel(_DisplayMode mode) => mode == sizes.first
        ? inlineFormat(t('ui01Recommended'), [mode.width, mode.height])
        : '${mode.width} × ${mode.height}';
    final rates =
        display.supportedModes
            .where((mode) => '${mode.width}x${mode.height}' == activeSize)
            .toList()
          ..sort((a, b) => b.refreshRate.compareTo(a.refreshRate));
    String rateLabel(double value) =>
        '${value.toStringAsFixed(value == value.roundToDouble() ? 0 : 2)} Hz';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        DextopSection(display.name),
        DextopGroup(
          children: [
            if (extendedScreen)
              _settingRow<int>(
                icon: M3EIcons.aspect_ratio_rounded,
                title: t('uiScale'),
                subtitle: t('uiChangeTheSizeOfTextAppsAndOtherItems'),
                value: display.scalePercent,
                items: [
                  for (final value in {
                    ...display.scalePercents,
                    display.scalePercent,
                  }.toList()..sort())
                    (
                      value,
                      value == 100
                          ? inlineFormat(t('ui0Recommended'), [value])
                          : '$value%',
                    ),
                ],
                onSelected: !enabled
                    ? null
                    : (value) => applyDisplaySetting(
                        () => widget.bridge.setExtendedDisplayScale(
                          display.id,
                          value,
                        ),
                      ),
              )
            else
              _settingRow<int>(
                icon: M3EIcons.aspect_ratio_rounded,
                title: t('uiScale'),
                subtitle: t('uiChangeTheSizeOfTextAppsAndOtherItems'),
                value: currentPercent,
                items: [
                  for (final value in percents) (value, percentLabel(value)),
                ],
                onSelected: !enabled
                    ? null
                    : (value) => applyDisplaySetting(
                        () => widget.bridge.setDisplayDensity(
                          display.id,
                          value == recommendedPercent
                              ? 0
                              : (value * 160 / 100).round(),
                        ),
                      ),
              ),
            if (sizes.isNotEmpty && activeSize != null)
              _settingRow<String>(
                icon: M3EIcons.fit_screen_rounded,
                title: t('uiDisplayResolution'),
                subtitle: t('uiAdjustTheResolutionToFitYourConnectedDisplay'),
                value: activeSize,
                items: [
                  for (final mode in sizes)
                    ('${mode.width}x${mode.height}', sizeLabel(mode)),
                ],
                onSelected: !enabled
                    ? null
                    : (value) => applyDisplaySetting(() async {
                        final mode = bySize[value]!;
                        await widget.bridge.setDisplayPreferredMode(
                          displayId: display.id,
                          width: mode.width,
                          height: mode.height,
                          refreshRate: mode.refreshRate,
                        );
                      }),
              ),
            if (active != null && rates.length > 1)
              _settingRow<int>(
                icon: M3EIcons.speed_rounded,
                title: t('uiRefreshRate'),
                value: active.id,
                items: [
                  for (final mode in rates)
                    (mode.id, rateLabel(mode.refreshRate)),
                ],
                onSelected: !enabled
                    ? null
                    : (id) => applyDisplaySetting(() async {
                        final mode = rates.firstWhere((item) => item.id == id);
                        await widget.bridge.setDisplayPreferredMode(
                          displayId: display.id,
                          width: mode.width,
                          height: mode.height,
                          refreshRate: mode.refreshRate,
                        );
                      }),
              ),
            if (!extendedScreen)
              _settingRow<int>(
                icon: M3EIcons.screen_rotation_alt_rounded,
                title: t('uiDisplayOrientation'),
                value: display.rotation,
                items: [
                  (0, t('uiLandscape')),
                  (1, t('uiPortrait')),
                  (2, t('uiLandscapeFlipped')),
                  (3, t('uiPortraitFlipped')),
                ],
                onSelected: !enabled
                    ? null
                    : (value) => applyDisplaySetting(
                        () =>
                            widget.bridge.setDisplayRotation(display.id, value),
                      ),
              ),
          ],
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    if (loading) {
      return const Center(
        child: M3ELoadingIndicator(
          variant: M3ELoadingIndicatorVariant.contained,
        ),
      );
    }
    if (error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(M3EIcons.desktop_access_disabled_rounded, size: 42),
              const SizedBox(height: 12),
              Text(error!, textAlign: TextAlign.center),
              const SizedBox(height: 16),
              M3EButton.outlined(
                onPressed: load,
                icon: const Icon(M3EIcons.refresh_rounded),
                label: Text(currentLocalizations().topologyRefresh),
              ),
            ],
          ),
        ),
      );
    }
    return LayoutBuilder(
      builder: (context, constraints) => ListView(
        padding: const EdgeInsets.fromLTRB(16, 10, 16, 32),
        children: [
          if (widget.showSettingsHeader) ...[
            Text(
              currentLocalizations().topologyArrangeDisplays,
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 4),
          ],
          Text(
            currentLocalizations().topologyDescription,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 12),
          SizedBox(
            height: (constraints.maxHeight * .42).clamp(250.0, 390.0),
            child: M3ECard(
              variant: M3ECardVariant.elevated,
              borderRadius: BorderRadius.circular(28),
              padding: EdgeInsets.zero,
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
          const SizedBox(height: 14),
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
                    child: M3EButton.tonal(
                      size: M3EButtonSize.sm,
                      enabled: !applying,
                      onPressed: applying
                          ? null
                          : () => Navigator.maybePop(context),
                      icon: const Icon(M3EIcons.close_rounded, size: 18),
                      label: Text(
                        MaterialLocalizations.of(context).cancelButtonLabel,
                      ),
                    ),
                  ),
                  SizedBox(
                    width: buttonWidth,
                    child: M3EButton.tonal(
                      size: M3EButtonSize.sm,
                      enabled: !applying,
                      onPressed: applying ? null : resetPositions,
                      icon: const Icon(M3EIcons.restart_alt_rounded, size: 18),
                      label: Text(currentLocalizations().topologyReset),
                    ),
                  ),
                  SizedBox(
                    width: buttonWidth,
                    child: M3EButton.tonal(
                      size: M3EButtonSize.sm,
                      enabled: displays.isNotEmpty,
                      onPressed: displays.isEmpty ? null : identify,
                      icon: const Icon(M3EIcons.visibility_rounded, size: 18),
                      label: Text(currentLocalizations().topologyIdentify),
                    ),
                  ),
                  SizedBox(
                    width: buttonWidth,
                    child: M3EButton.filled(
                      size: M3EButtonSize.sm,
                      enabled: !applying && displays.length >= 2,
                      onPressed: applying || displays.length < 2 ? null : apply,
                      icon: const Icon(M3EIcons.check_rounded, size: 18),
                      label: Text(currentLocalizations().topologyApply),
                    ),
                  ),
                ],
              );
            },
          ),
          ?widget.belowArrangement,
          const SizedBox(height: 12),
          _selectedDisplaySettings(context),
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
    this.rotation = 0,
    required this.activeModeId,
    required this.refreshRate,
    required this.supportedModes,
    required this.dextopOverlay,
    required this.primary,
    this.extendedScreen,
    this.scalePercent = 100,
    this.scalePercents = const [],
  });
  final int id;
  final String name;
  final Offset position;
  final double widthDp;
  final double heightDp;
  final int widthPx;
  final int heightPx;
  final int densityDpi;
  final int rotation;
  final int activeModeId;
  final double refreshRate;
  final List<_DisplayMode> supportedModes;
  final bool dextopOverlay;
  final bool primary;

  /// 1 (phone) or 2 (external) on Dextop's extended desktop, which Dextop arranges itself.
  final int? extendedScreen;
  final int scalePercent;
  final List<int> scalePercents;

  String get modeLabel =>
      '$widthPx × $heightPx · ${refreshRate.toStringAsFixed(refreshRate == refreshRate.roundToDouble() ? 0 : 1)} Hz';

  factory _TopologyDisplay.fromMap(Map<String, dynamic> map) =>
      _TopologyDisplay(
        id: (map['id'] as num).toInt(),
        name: map['dextopOverlay'] == true
            ? currentLocalizations().topologyBuiltInScreen
            : map['name']?.toString() ?? currentUiText('uiDisplay'),
        position: Offset(
          (map['x'] as num?)?.toDouble() ?? 0,
          (map['y'] as num?)?.toDouble() ?? 0,
        ),
        widthDp: (map['widthDp'] as num?)?.toDouble() ?? 640,
        heightDp: (map['heightDp'] as num?)?.toDouble() ?? 360,
        widthPx: (map['widthPx'] as num?)?.toInt() ?? 0,
        heightPx: (map['heightPx'] as num?)?.toInt() ?? 0,
        densityDpi: (map['densityDpi'] as num?)?.toInt() ?? 0,
        rotation: (map['rotation'] as num?)?.toInt() ?? 0,
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
        extendedScreen: (map['extendedScreen'] as num?)?.toInt(),
        scalePercent: (map['scalePercent'] as num?)?.toInt() ?? 100,
        scalePercents: [
          for (final value in map['scalePercents'] as List? ?? const [])
            (value as num).toInt(),
        ],
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
    rotation: rotation,
    activeModeId: activeModeId,
    refreshRate: refreshRate,
    supportedModes: supportedModes,
    dextopOverlay: dextopOverlay,
    primary: primary,
    extendedScreen: extendedScreen,
    scalePercent: scalePercent,
    scalePercents: scalePercents,
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
