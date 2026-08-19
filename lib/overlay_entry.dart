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
      home: OverlayMenu(),
    );
  }

  ThemeData _theme(Brightness brightness) {
    final scheme = ColorScheme.fromSeed(
      seedColor: Color(0xff6750a4),
      brightness: brightness,
      dynamicSchemeVariant: DynamicSchemeVariant.tonalSpot,
    );
    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: Colors.transparent,
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: Size.fromHeight(54),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
          ),
        ),
      ),
    );
  }
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
              duration: Duration(milliseconds: 320),
              curve: Curves.easeOutCubic,
              builder: (context, value, child) => Transform.translate(
                offset: landscape
                    ? Offset((value - 1) * 380, 0)
                    : Offset(0, (1 - value) * 520),
                child: child,
              ),
              child: Container(
                width: landscape ? 360 : double.infinity,
                margin: EdgeInsets.all(12),
                padding: EdgeInsets.fromLTRB(22, 20, 22, 18),
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.surfaceContainerHigh,
                  borderRadius: BorderRadius.circular(30),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black38,
                      blurRadius: 32,
                      offset: Offset(0, 12),
                    ),
                  ],
                ),
                child: SafeArea(
                  child: SingleChildScrollView(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Icon(Icons.tune_rounded, size: 28),
                            SizedBox(width: 12),
                            Text(
                              AppStrings.tr('uiOperationMenu'),
                              style: Theme.of(context).textTheme.headlineSmall,
                            ),
                            Spacer(),
                            IconButton(
                              onPressed: () => channel.invokeMethod('dismiss'),
                              icon: Icon(Icons.close_rounded),
                            ),
                          ],
                        ),
                        SizedBox(height: 18),
                        Text(
                          AppStrings.tr('uiTrackpad'),
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        SizedBox(height: 10),
                        SegmentedButton<bool>(
                          segments: [
                            ButtonSegment(
                              value: false,
                              icon: Icon(Icons.mouse_rounded),
                              label: Text(AppStrings.tr('uiCursor')),
                            ),
                            ButtonSegment(
                              value: true,
                              icon: Icon(Icons.touch_app_rounded),
                              label: Text(AppStrings.tr('uiTap')),
                            ),
                          ],
                          selected: {direct},
                          onSelectionChanged: (value) {
                            setState(() => direct = value.first);
                            channel.invokeMethod('setTouchMode', {
                              'direct': direct,
                            });
                          },
                        ),
                        SizedBox(height: 22),
                        ListTile(
                          contentPadding: EdgeInsets.symmetric(horizontal: 4),
                          leading: Icon(Icons.monitor_rounded),
                          title: Text(AppStrings.tr('resolution')),
                          subtitle: Text(
                            '${width.text} × ${height.text}  ${density.text} dpi',
                          ),
                          trailing: Icon(Icons.chevron_right_rounded),
                          onTap: showResolutionSheet,
                        ),
                        SizedBox(height: 10),
                        SizedBox(
                          width: double.infinity,
                          child: FilledButton.tonalIcon(
                            onPressed: () =>
                                channel.invokeMethod('exitFullscreen'),
                            icon: Icon(Icons.fullscreen_exit_rounded),
                            label: Text(AppStrings.tr('uiCancelFullScreen')),
                          ),
                        ),
                        SizedBox(height: 10),
                        SizedBox(
                          width: double.infinity,
                          child: FilledButton.tonalIcon(
                            onPressed: () =>
                                channel.invokeMethod('orientation'),
                            icon: Icon(Icons.screen_rotation_rounded),
                            label: Text(
                              landscape
                                  ? AppStrings.tr(
                                      'uiChangeToPortraitOrientation',
                                    )
                                  : AppStrings.tr('uiChangeToHorizontalHold'),
                            ),
                          ),
                        ),
                        SizedBox(height: 10),
                        SizedBox(
                          width: double.infinity,
                          child: FilledButton.icon(
                            style: FilledButton.styleFrom(
                              backgroundColor: Theme.of(
                                context,
                              ).colorScheme.errorContainer,
                              foregroundColor: Theme.of(
                                context,
                              ).colorScheme.onErrorContainer,
                            ),
                            onPressed: () => channel.invokeMethod('stop'),
                            icon: Icon(Icons.stop_circle_rounded),
                            label: Text(AppStrings.tr('uiEnd')),
                          ),
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
    return TextField(
      controller: controller,
      // Keep the edit buffer intact for Samsung's hardware/IME composing
      // events.  FilteringTextInputFormatter.digitsOnly can discard those
      // events, which makes DPI look impossible to edit from the quick menu.
      keyboardType: TextInputType.numberWithOptions(
        decimal: false,
        signed: false,
      ),
      textInputAction: TextInputAction.next,
      autocorrect: false,
      enableSuggestions: false,
      enableInteractiveSelection: true,
      decoration: InputDecoration(
        labelText: label,
        border: OutlineInputBorder(),
      ),
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
        AppStrings.tr('uiTerminalResolution'),
      ),
    ];
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (sheetContext) => Padding(
        padding: EdgeInsets.fromLTRB(
          20,
          0,
          20,
          MediaQuery.viewInsetsOf(sheetContext).bottom + 24,
        ),
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                AppStrings.tr('resolution'),
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              SizedBox(height: 12),
              ...presets.map(
                (preset) => ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(preset.$4),
                  subtitle: Text(
                    '${preset.$1} × ${preset.$2}  ${preset.$3} dpi',
                  ),
                  onTap: () {
                    width.text = '${preset.$1}';
                    height.text = '${preset.$2}';
                    density.text = '${preset.$3}';
                    Navigator.pop(sheetContext);
                    applyResolution();
                  },
                ),
              ),
              Divider(height: 28),
              Text(
                AppStrings.tr('customAdd'),
                style: Theme.of(context).textTheme.titleLarge,
              ),
              SizedBox(height: 14),
              Row(
                children: [
                  Expanded(child: overlayField(width, AppStrings.tr('width'))),
                  SizedBox(width: 10),
                  Expanded(
                    child: overlayField(height, AppStrings.tr('height')),
                  ),
                ],
              ),
              SizedBox(height: 10),
              overlayField(density, 'dpi'),
              SizedBox(height: 14),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: () {
                    Navigator.pop(sheetContext);
                    applyResolution();
                  },
                  child: Text(AppStrings.tr('customAdd')),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
