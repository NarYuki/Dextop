part of 'main.dart';

/// Book appearance settings. Book flags share the native desktop's store.
class BookSettingsPage extends StatefulWidget {
  const BookSettingsPage({required this.bridge, super.key});
  final NativeBridge bridge;
  @override
  State<BookSettingsPage> createState() => _BookSettingsPageState();
}

class _BookSettingsPageState extends State<BookSettingsPage> {
  SharedPreferences? prefs;
  Map<String, dynamic> book = {};
  String? error;
  String theme = 'dark';
  String wallpaper = 'plasma';
  int desktops = 4;
  double speed = 1;
  bool battery = true;
  bool hotCorner = true;
  String t(String key) => localText(context, key);

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    try {
      final store = await SharedPreferences.getInstance();
      final values = await NativeBridge.channel
          .invokeMapMethod<String, dynamic>('bookSettings');
      if (!mounted) return;
      setState(() {
        prefs = store;
        book = values ?? {};
        theme = store.getString('plasma_color_scheme') ?? 'dark';
        wallpaper = store.getString('plasma_wallpaper') ?? 'plasma';
        desktops = (store.getInt('plasma_virtual_desktops') ?? 4).clamp(1, 6);
        speed = (store.getDouble('plasma_animation_speed') ?? 1)
            .clamp(0, 2)
            .toDouble();
        battery = store.getBool('plasma_tray_battery_percent') ?? true;
        hotCorner = store.getBool('plasma_hot_corner') ?? true;
        error = null;
      });
    } catch (e) {
      if (mounted) setState(() => error = e.toString());
    }
  }

  Future<void> save(Future<bool> Function(SharedPreferences) write) async {
    try {
      await write(prefs!);
      await widget.bridge.plasmaSettingsChanged();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('$e')));
      }
    }
  }

  Future<void> setBook(String key, Object value) async {
    try {
      final values = await NativeBridge.channel
          .invokeMapMethod<String, dynamic>('setBookSetting', {
            'key': key,
            'value': value,
          });
      if (mounted) setState(() => book = values ?? book);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('$e')));
      }
    }
  }

  Widget section(String title, List<Widget> children) => Padding(
    padding: const EdgeInsets.only(bottom: 20),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 0, 12, 8),
          child: Text(title, style: Theme.of(context).textTheme.titleSmall),
        ),
        Card(
          margin: EdgeInsets.zero,
          child: Column(children: children),
        ),
      ],
    ),
  );

  Widget flag(String key, String title, IconData icon, {String? subtitle}) =>
      SwitchListTile(
        secondary: Icon(icon),
        title: Text(title),
        subtitle: subtitle == null ? null : Text(subtitle),
        value: book[key] == true,
        onChanged: (value) => setBook(key, value),
      );

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(localText(context, 'uiDextopBook'))),
    body: error != null
        ? Center(
            child: FilledButton.tonal(
              onPressed: load,
              child: Text(t('uiRetry')),
            ),
          )
        : prefs == null
        ? const Center(child: CircularProgressIndicator())
        : ListView(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
            children: [
              section(t('uiAppearance'), [
                ListTile(
                  leading: const Icon(Icons.dark_mode_outlined),
                  title: Text(t('uiTheme')),
                  subtitle: Text(t('uiColoursFollowYourAndroidWallpaper')),
                  trailing: DropdownButton<String>(
                    value: theme,
                    underline: const SizedBox.shrink(),
                    items: [
                      for (final item in {
                        'system': t('uiSystem'),
                        'light': t('uiLight'),
                        'dark': t('uiDark'),
                      }.entries)
                        DropdownMenuItem(
                          value: item.key,
                          child: Text(item.value),
                        ),
                    ],
                    onChanged: (value) {
                      if (value == null) return;
                      setState(() => theme = value);
                      save((p) => p.setString('plasma_color_scheme', value));
                    },
                  ),
                ),
                ListTile(
                  leading: const Icon(Icons.wallpaper_outlined),
                  title: Text(t('uiWallpaper')),
                  trailing: DropdownButton<String>(
                    value: wallpaper,
                    underline: const SizedBox.shrink(),
                    items: [
                      DropdownMenuItem(
                        value: 'plasma',
                        child: Text(localText(context, 'uiBook')),
                      ),
                      DropdownMenuItem(
                        value: 'system',
                        child: Text(t('uiAndroidWallpaper')),
                      ),
                    ],
                    onChanged: (value) {
                      if (value == null) return;
                      setState(() => wallpaper = value);
                      save((p) => p.setString('plasma_wallpaper', value));
                    },
                  ),
                ),
                ListTile(
                  leading: const Icon(Icons.zoom_in_rounded),
                  title: Text(t('uiExternalDisplaySize')),
                  trailing: DropdownButton<int>(
                    value: (book['display_scale'] as num?)?.toInt() ?? 0,
                    underline: const SizedBox.shrink(),
                    items: [
                      for (final scale in [0, 100, 125, 150, 175, 200])
                        DropdownMenuItem(
                          value: scale,
                          child: Text(scale == 0 ? t('uiAuto') : '$scale%'),
                        ),
                    ],
                    onChanged: (value) {
                      if (value != null) setBook('display_scale', value);
                    },
                  ),
                ),
              ]),
              section(t('uiTaskbarAndStartMenu'), [
                flag(
                  'always_show_taskbar',
                  t('uiAlwaysShowTheTaskbar'),
                  Icons.dock_outlined,
                ),
                flag(
                  'launcher_expanded',
                  t('uiExpandTheStartMenu'),
                  Icons.open_in_full_rounded,
                  subtitle: t('uiChangesMadeInTheMenuAreAlsoSaved'),
                ),
              ]),
              section(t('uiStatusBar'), [
                flag(
                  'live_activity',
                  t('uiLiveActivity'),
                  Icons.graphic_eq_rounded,
                  subtitle: t('uiShowPlayingMedia'),
                ),
                flag(
                  'notification_icons',
                  t('uiNotificationIcons'),
                  Icons.notifications_outlined,
                ),
                SwitchListTile(
                  secondary: const Icon(Icons.battery_std_rounded),
                  title: Text(t('uiBatteryPercentage')),
                  value: battery,
                  onChanged: (v) {
                    setState(() => battery = v);
                    save((p) => p.setBool('plasma_tray_battery_percent', v));
                  },
                ),
              ]),
              section(t('uiDesktopAndBehaviour'), [
                flag(
                  'desktop_icons',
                  t('uiShowDesktopIcons'),
                  Icons.grid_view_rounded,
                ),
                ListTile(
                  leading: const Icon(Icons.view_carousel_outlined),
                  title: Text(t('uiVirtualDesktops')),
                  trailing: Text('$desktops'),
                  subtitle: Slider(
                    value: desktops.toDouble(),
                    min: 1,
                    max: 6,
                    divisions: 5,
                    label: '$desktops',
                    onChanged: (v) => setState(() => desktops = v.round()),
                    onChangeEnd: (v) => save(
                      (p) => p.setInt('plasma_virtual_desktops', v.round()),
                    ),
                  ),
                ),
                ListTile(
                  leading: const Icon(Icons.animation_outlined),
                  title: Text(t('uiAnimationSpeed')),
                  subtitle: Slider(
                    value: speed,
                    min: 0,
                    max: 2,
                    divisions: 8,
                    label: speed == 0
                        ? t('uiOff')
                        : '${speed.toStringAsFixed(2)}×',
                    onChanged: (v) => setState(() => speed = v),
                    onChangeEnd: (v) =>
                        save((p) => p.setDouble('plasma_animation_speed', v)),
                  ),
                ),
                SwitchListTile(
                  secondary: const Icon(Icons.north_west_rounded),
                  title: Text(t('uiHotCorner')),
                  value: hotCorner,
                  onChanged: (v) {
                    setState(() => hotCorner = v);
                    save((p) => p.setBool('plasma_hot_corner', v));
                  },
                ),
              ]),
            ],
          ),
  );
}
