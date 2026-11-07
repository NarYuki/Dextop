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
  String t(String ja, String en) => localText(context, ja, en);

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
    appBar: AppBar(title: const Text('Dextop Book')),
    body: error != null
        ? Center(
            child: FilledButton.tonal(
              onPressed: load,
              child: Text(t('再読み込み', 'Retry')),
            ),
          )
        : prefs == null
        ? const Center(child: CircularProgressIndicator())
        : ListView(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
            children: [
              section(t('外観', 'Appearance'), [
                ListTile(
                  leading: const Icon(Icons.dark_mode_outlined),
                  title: Text(t('テーマ', 'Theme')),
                  subtitle: Text(
                    t(
                      '色はAndroidの壁紙の色から作られます',
                      'Colours follow your Android wallpaper',
                    ),
                  ),
                  trailing: DropdownButton<String>(
                    value: theme,
                    underline: const SizedBox.shrink(),
                    items: [
                      for (final item in {
                        'system': t('システム', 'System'),
                        'light': t('ライト', 'Light'),
                        'dark': t('ダーク', 'Dark'),
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
                  title: Text(t('壁紙', 'Wallpaper')),
                  trailing: DropdownButton<String>(
                    value: wallpaper,
                    underline: const SizedBox.shrink(),
                    items: [
                      DropdownMenuItem(
                        value: 'plasma',
                        child: const Text('Book'),
                      ),
                      DropdownMenuItem(
                        value: 'system',
                        child: Text(t('Androidの壁紙', 'Android wallpaper')),
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
                  title: Text(t('外部画面の表示サイズ', 'External display size')),
                  trailing: DropdownButton<int>(
                    value: (book['display_scale'] as num?)?.toInt() ?? 0,
                    underline: const SizedBox.shrink(),
                    items: [
                      for (final scale in [0, 100, 125, 150, 175, 200])
                        DropdownMenuItem(
                          value: scale,
                          child: Text(scale == 0 ? t('自動', 'Auto') : '$scale%'),
                        ),
                    ],
                    onChanged: (value) {
                      if (value != null) setBook('display_scale', value);
                    },
                  ),
                ),
              ]),
              section(t('タスクバーとスタートメニュー', 'Taskbar and Start menu'), [
                flag(
                  'always_show_taskbar',
                  t('タスクバーを常に表示', 'Always show the taskbar'),
                  Icons.dock_outlined,
                ),
                flag(
                  'launcher_expanded',
                  t('スタートメニューを拡張', 'Expand the Start menu'),
                  Icons.open_in_full_rounded,
                  subtitle: t(
                    'メニュー内で切り替えた状態も保存されます',
                    'Changes made in the menu are also saved',
                  ),
                ),
              ]),
              section(t('ステータスバー', 'Status bar'), [
                flag(
                  'live_activity',
                  t('ライブアクティビティ', 'Live activity'),
                  Icons.graphic_eq_rounded,
                  subtitle: t('再生中のメディアを表示', 'Show playing media'),
                ),
                flag(
                  'notification_icons',
                  t('通知アイコン', 'Notification icons'),
                  Icons.notifications_outlined,
                ),
                SwitchListTile(
                  secondary: const Icon(Icons.battery_std_rounded),
                  title: Text(t('電池残量（%）', 'Battery percentage')),
                  value: battery,
                  onChanged: (v) {
                    setState(() => battery = v);
                    save((p) => p.setBool('plasma_tray_battery_percent', v));
                  },
                ),
              ]),
              section(t('デスクトップと操作', 'Desktop and behaviour'), [
                flag(
                  'desktop_icons',
                  t('デスクトップアイコンを表示', 'Show desktop icons'),
                  Icons.grid_view_rounded,
                ),
                ListTile(
                  leading: const Icon(Icons.view_carousel_outlined),
                  title: Text(t('仮想デスクトップ', 'Virtual desktops')),
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
                  title: Text(t('アニメーション速度', 'Animation speed')),
                  subtitle: Slider(
                    value: speed,
                    min: 0,
                    max: 2,
                    divisions: 8,
                    label: speed == 0
                        ? t('なし', 'Off')
                        : '${speed.toStringAsFixed(2)}×',
                    onChanged: (v) => setState(() => speed = v),
                    onChangeEnd: (v) =>
                        save((p) => p.setDouble('plasma_animation_speed', v)),
                  ),
                ),
                SwitchListTile(
                  secondary: const Icon(Icons.north_west_rounded),
                  title: Text(t('ホットコーナー', 'Hot corner')),
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
