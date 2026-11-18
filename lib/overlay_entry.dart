part of 'main.dart';

class OverlayApp extends StatelessWidget {
  const OverlayApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: _theme(Brightness.light),
      darkTheme: _theme(Brightness.dark),
      themeMode: ThemeMode.system,
      builder: (context, child) => DextopExpressive(child: child!),
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        dextopMaterialUiLocalizations,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
      home: const OverlayMenu(),
    );
  }

  ThemeData _theme(Brightness brightness) =>
      DextopExpressive.buildMuiTheme(brightness).copyWith(
        scaffoldBackgroundColor: Colors.transparent,
      );
}

class OverlayMenu extends StatefulWidget {
  const OverlayMenu({super.key});

  @override
  State<OverlayMenu> createState() => _OverlayMenuState();
}

class _OverlayMenuState extends State<OverlayMenu> {
  static final channel = MethodChannel('app.freedextop/overlay');
  final width = TextEditingController();
  final height = TextEditingController();
  final density = TextEditingController();
  var direct = false;
  var deviceWidth = 1920;
  var deviceHeight = 1080;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    final state = await channel.invokeMapMethod<String, dynamic>('state') ?? {};
    width.text = '${state['width'] ?? 1920}';
    height.text = '${state['height'] ?? 1080}';
    density.text = '${state['density'] ?? 240}';
    deviceWidth = state['deviceWidth'] as int? ?? 1920;
    deviceHeight = state['deviceHeight'] as int? ?? 1080;
    direct = state['directTouch'] == true;
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    width.dispose();
    height.dispose();
    density.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final landscape =
        MediaQuery.orientationOf(context) == Orientation.landscape;
    final colors = Theme.of(context).colorScheme;
    return Scaffold(
      backgroundColor: Colors.black38,
      body: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () => channel.invokeMethod('dismiss'),
        child: Align(
          alignment: landscape ? Alignment.centerLeft : Alignment.bottomCenter,
          child: GestureDetector(
            onTap: () {},
            child: TweenAnimationBuilder<double>(
              tween: Tween(begin: 0, end: 1),
              duration: const Duration(milliseconds: 340),
              curve: Curves.easeOutBack,
              builder: (context, value, child) => Transform.translate(
                offset: landscape
                    ? Offset((value - 1) * 380, 0)
                    : Offset(0, (1 - value) * 520),
                child: child,
              ),
              child: Container(
                width: landscape ? 372 : double.infinity,
                margin: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: colors.surfaceContainerLow,
                  borderRadius: BorderRadius.circular(30),
                  boxShadow: const [
                    BoxShadow(
                      color: Colors.black38,
                      blurRadius: 32,
                      offset: Offset(0, 12),
                    ),
                  ],
                ),
                child: SafeArea(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.fromLTRB(22, 20, 22, 20),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Row(
                          children: [
                            Container(
                              width: 40,
                              height: 40,
                              decoration: BoxDecoration(
                                color: colors.primaryContainer,
                                borderRadius: BorderRadius.circular(14),
                              ),
                              child: Icon(
                                M3EIcons.tune_rounded,
                                size: 22,
                                color: colors.onPrimaryContainer,
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Text(
                                currentLocalizations().uiOperationMenu,
                                style: Theme.of(context).textTheme.titleLarge,
                              ),
                            ),
                            M3EIconButton(
                              variant: M3EIconButtonVariant.tonal,
                              onPressed: () => channel.invokeMethod('dismiss'),
                              icon: const Icon(M3EIcons.close_rounded),
                            ),
                          ],
                        ),
                        const SizedBox(height: 18),
                        Text(
                          currentLocalizations().uiTrackpad,
                          style: Theme.of(context).textTheme.titleSmall?.copyWith(
                            color: colors.primary,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const SizedBox(height: 10),
                        DextopChoiceGroup<bool>(
                          choices: [
                            (
                              false,
                              currentLocalizations().uiCursor,
                              M3EIcons.mouse_rounded,
                            ),
                            (
                              true,
                              currentLocalizations().uiTap,
                              M3EIcons.touch_app_rounded,
                            ),
                          ],
                          selected: direct,
                          onSelected: (value) {
                            setState(() => direct = value);
                            channel.invokeMethod('setTouchMode', {
                              'direct': direct,
                            });
                          },
                        ),
                        const SizedBox(height: 18),
                        DextopGroup(
                          color: colors.surfaceContainer,
                          children: [
                            M3EListItem(
                              leading: const Icon(M3EIcons.monitor_rounded),
                              headline: currentLocalizations().resolution,
                              supportingText:
                                  '${width.text} × ${height.text}  ${density.text} dpi',
                              trailing: const Icon(M3EIcons.chevron_right_rounded),
                              onTap: showResolutionSheet,
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        M3EButton.tonal(
                          size: M3EButtonSize.md,
                          onPressed: () =>
                              channel.invokeMethod('exitFullscreen'),
                          icon: const Icon(M3EIcons.fullscreen_exit_rounded),
                          label: Text(
                            currentLocalizations().uiCancelFullScreen,
                          ),
                        ),
                        const SizedBox(height: 10),
                        M3EButton.tonal(
                          size: M3EButtonSize.md,
                          onPressed: () => channel.invokeMethod('orientation'),
                          icon: const Icon(M3EIcons.screen_rotation_rounded),
                          label: Text(
                            landscape
                                ? currentLocalizations()
                                      .uiChangeToPortraitOrientation
                                : currentLocalizations()
                                      .uiChangeToHorizontalHold,
                          ),
                        ),
                        const SizedBox(height: 10),
                        M3EButton.filled(
                          size: M3EButtonSize.md,
                          decoration: dextopButtonColors(
                            background: colors.errorContainer,
                            foreground: colors.onErrorContainer,
                          ),
                          onPressed: () => channel.invokeMethod('stop'),
                          icon: const Icon(M3EIcons.stop_circle_rounded),
                          label: Text(currentLocalizations().uiEnd),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget overlayField(TextEditingController controller, String label) {
    return M3ETextField(
      controller: controller,
      label: label,
      variant: M3ETextFieldVariant.outlined,
      keyboardType: const TextInputType.numberWithOptions(
        decimal: false,
        signed: false,
      ),
      textInputAction: TextInputAction.next,
    );
  }

  void applyResolution() {
    final w = int.tryParse(width.text);
    final h = int.tryParse(height.text);
    final dpi = int.tryParse(density.text);
    if (w == null || h == null || dpi == null) return;
    channel.invokeMethod('setResolution', {
      'width': w,
      'height': h,
      'density': dpi,
    });
  }

  Future<void> showResolutionSheet() async {
    final portrait = int.parse(height.text) > int.parse(width.text);
    final longSide = deviceWidth > deviceHeight ? deviceWidth : deviceHeight;
    final shortSide = deviceWidth > deviceHeight ? deviceHeight : deviceWidth;
    final presets = [
      (
        portrait ? shortSide : longSide,
        portrait ? longSide : shortSide,
        240,
        currentLocalizations().uiTerminalResolution,
      ),
    ];
    await M3EBottomSheet.show<void>(
      context,
      showDragHandle: true,
      builder: (sheetContext) {
        final colors = Theme.of(sheetContext).colorScheme;
        return SingleChildScrollView(
          padding: EdgeInsets.fromLTRB(
            20,
            0,
            20,
            MediaQuery.viewInsetsOf(sheetContext).bottom + 24,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                currentLocalizations().resolution,
                style: Theme.of(sheetContext).textTheme.headlineSmall,
              ),
              const SizedBox(height: 12),
              DextopGroup(
                color: colors.surfaceContainer,
                children: [
                  for (final preset in presets)
                    M3EListItem(
                      leading: const Icon(M3EIcons.monitor_rounded),
                      headline: preset.$4,
                      supportingText:
                          '${preset.$1} × ${preset.$2}  ${preset.$3} dpi',
                      onTap: () {
                        width.text = '${preset.$1}';
                        height.text = '${preset.$2}';
                        density.text = '${preset.$3}';
                        Navigator.pop(sheetContext);
                        applyResolution();
                      },
                    ),
                ],
              ),
              const SizedBox(height: 20),
              Text(
                currentLocalizations().customAdd,
                style: Theme.of(sheetContext).textTheme.titleLarge,
              ),
              const SizedBox(height: 14),
              Row(
                children: [
                  Expanded(
                    child: overlayField(width, currentLocalizations().width),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: overlayField(height, currentLocalizations().height),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              overlayField(density, 'dpi'),
              const SizedBox(height: 16),
              M3EButton.filled(
                size: M3EButtonSize.md,
                onPressed: () {
                  Navigator.pop(sheetContext);
                  applyResolution();
                },
                child: Text(currentLocalizations().customAdd),
              ),
            ],
          ),
        );
      },
    );
  }
}
