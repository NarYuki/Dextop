part of 'main.dart';

/// Appearance and behaviour of Dextop's own window manager (Dextop Plasma).
///
/// Every value is stored in SharedPreferences under a `plasma_` key that the
/// native shell reads; changes are pushed to a running desktop immediately.
class PlasmaSettingsPage extends StatefulWidget {
  const PlasmaSettingsPage({required this.bridge, super.key});

  final NativeBridge bridge;

  @override
  State<PlasmaSettingsPage> createState() => _PlasmaSettingsPageState();
}

class _PlasmaSettingsPageState extends State<PlasmaSettingsPage> {
  static const _accents = <int>[
    0xFF3DAEE9,
    0xFF9B59B6,
    0xFFE93A9A,
    0xFFE93D58,
    0xFFE9643A,
    0xFFEF973C,
    0xFFE8CB2D,
    0xFFB6E521,
    0xFF3DD425,
    0xFF00D485,
    0xFF00D3B8,
    0xFF7F8C8D,
  ];

  SharedPreferences? prefs;
  var colorScheme = 'dark';
  var accent = _accents.first;
  var wallpaper = 'plasma';
  var floatingPanel = true;
  var desktops = 4;
  var animationSpeed = 1.0;
  var hotCorner = true;

  @override
  void initState() {
    super.initState();
    AppAnalytics.screen('plasma_settings');
    _load();
  }

  Future<void> _load() async {
    final store = await SharedPreferences.getInstance();
    if (!mounted) return;
    setState(() {
      prefs = store;
      colorScheme = store.getString('plasma_color_scheme') ?? 'dark';
      accent = store.getInt('plasma_accent_color') ?? _accents.first;
      wallpaper = store.getString('plasma_wallpaper') ?? 'plasma';
      floatingPanel = store.getBool('plasma_floating_panel') ?? true;
      desktops = (store.getInt('plasma_virtual_desktops') ?? 4).clamp(1, 6);
      animationSpeed = (store.getDouble('plasma_animation_speed') ?? 1.0)
          .clamp(0.0, 2.0);
      hotCorner = store.getBool('plasma_hot_corner') ?? true;
    });
  }

  Future<void> _save(Future<bool> Function(SharedPreferences) write) async {
    final store = prefs ?? await SharedPreferences.getInstance();
    await write(store);
    await widget.bridge.plasmaSettingsChanged().catchError((_) {});
  }

  Widget _section(String title, List<Widget> children) => Padding(
    padding: const EdgeInsets.only(bottom: 20),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 0, 12, 7),
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
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 4),
            child: Column(children: children),
          ),
        ),
      ],
    ),
  );

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l.plasmaSettingsTitle)),
      body: prefs == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
              children: [
                _section(l.nativePlasmaColorScheme, [
                  Padding(
                    padding: const EdgeInsets.all(12),
                    child: SegmentedButton<String>(
                      segments: [
                        ButtonSegment(
                          value: 'dark',
                          label: Text(l.nativePlasmaBreezeDark),
                        ),
                        ButtonSegment(
                          value: 'light',
                          label: Text(l.nativePlasmaBreezeLight),
                        ),
                        ButtonSegment(
                          value: 'system',
                          label: Text(l.nativePlasmaFollowSystem),
                        ),
                      ],
                      selected: {colorScheme},
                      onSelectionChanged: (value) {
                        setState(() => colorScheme = value.first);
                        _save(
                          (p) => p.setString('plasma_color_scheme', value.first),
                        );
                      },
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 4, 16, 14),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(l.nativePlasmaAccentColor),
                        const SizedBox(height: 10),
                        Wrap(
                          spacing: 10,
                          runSpacing: 10,
                          children: [
                            for (final color in _accents)
                              InkResponse(
                                onTap: () {
                                  setState(() => accent = color);
                                  _save(
                                    (p) => p.setInt('plasma_accent_color', color),
                                  );
                                },
                                child: AnimatedContainer(
                                  duration: const Duration(milliseconds: 200),
                                  curve: Curves.easeOutCubic,
                                  width: 32,
                                  height: 32,
                                  decoration: BoxDecoration(
                                    color: Color(color),
                                    shape: BoxShape.circle,
                                    border: Border.all(
                                      color: accent == color
                                          ? Theme.of(context).colorScheme.onSurface
                                          : Colors.transparent,
                                      width: 2.5,
                                    ),
                                  ),
                                ),
                              ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ]),
                _section(l.nativePlasmaDesktop, [
                  ListTile(
                    leading: const Icon(Icons.wallpaper_outlined),
                    title: Text(l.nativePlasmaWallpaper),
                    trailing: SegmentedButton<String>(
                      showSelectedIcon: false,
                      segments: [
                        ButtonSegment(
                          value: 'plasma',
                          label: Text(l.nativePlasmaWallpaperPlasma),
                        ),
                        ButtonSegment(
                          value: 'system',
                          label: Text(l.nativePlasmaWallpaperAndroid),
                        ),
                      ],
                      selected: {wallpaper},
                      onSelectionChanged: (value) {
                        setState(() => wallpaper = value.first);
                        _save((p) => p.setString('plasma_wallpaper', value.first));
                      },
                    ),
                  ),
                  const Divider(height: 1),
                  SwitchListTile(
                    secondary: const Icon(Icons.dock_outlined),
                    title: Text(l.nativePlasmaPanel),
                    subtitle: Text(
                      floatingPanel
                          ? l.nativePlasmaFloating
                          : l.nativePlasmaDocked,
                    ),
                    value: floatingPanel,
                    onChanged: (value) {
                      setState(() => floatingPanel = value);
                      _save((p) => p.setBool('plasma_floating_panel', value));
                    },
                  ),
                  const Divider(height: 1),
                  ListTile(
                    leading: const Icon(Icons.view_carousel_outlined),
                    title: Text(l.nativePlasmaVirtualDesktops),
                    subtitle: Slider(
                      value: desktops.toDouble(),
                      min: 1,
                      max: 6,
                      divisions: 5,
                      label: '$desktops',
                      onChanged: (value) =>
                          setState(() => desktops = value.round()),
                      onChangeEnd: (value) => _save(
                        (p) => p.setInt('plasma_virtual_desktops', value.round()),
                      ),
                    ),
                    trailing: Text('$desktops'),
                  ),
                ]),
                _section(l.plasmaSettingsSummary, [
                  ListTile(
                    leading: const Icon(Icons.animation_outlined),
                    title: Text(l.nativePlasmaAnimationSpeed),
                    subtitle: Slider(
                      value: animationSpeed,
                      min: 0,
                      max: 2,
                      divisions: 8,
                      label: animationSpeed == 0
                          ? l.nativePlasmaInstant
                          : '${animationSpeed.toStringAsFixed(2)}×',
                      onChanged: (value) =>
                          setState(() => animationSpeed = value),
                      onChangeEnd: (value) => _save(
                        (p) => p.setDouble('plasma_animation_speed', value),
                      ),
                    ),
                  ),
                  const Divider(height: 1),
                  SwitchListTile(
                    secondary: const Icon(Icons.north_west_rounded),
                    title: Text(l.plasmaHotCorner),
                    subtitle: Text(l.plasmaHotCornerDescription),
                    value: hotCorner,
                    onChanged: (value) {
                      setState(() => hotCorner = value);
                      _save((p) => p.setBool('plasma_hot_corner', value));
                    },
                  ),
                ]),
                _section(l.plasmaShortcutsTitle, [
                  Padding(
                    padding: const EdgeInsets.all(16),
                    child: Text(
                      l.plasmaShortcutsBody,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        height: 1.6,
                      ),
                    ),
                  ),
                ]),
              ],
            ),
    );
  }
}
