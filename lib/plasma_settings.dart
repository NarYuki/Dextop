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
      if (mounted) M3ESnackbar.show(context, message: '$e');
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
      if (mounted) M3ESnackbar.show(context, message: '$e');
    }
  }

  Widget section(String title, List<Widget> children) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      DextopSection(title),
      DextopGroup(children: children),
    ],
  );

  Widget flag(String key, String title, IconData icon, {String? subtitle}) =>
      DextopToggle(
        icon: icon,
        title: title,
        subtitle: subtitle,
        value: book[key] == true,
        onChanged: (value) => setBook(key, value),
      );

  Future<void> _pickChoice<T>(
    BuildContext context, {
    required String title,
    required IconData icon,
    required T current,
    required List<(T, String)> items,
    required ValueChanged<T> onSelected,
  }) async {
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
                for (final item in items)
                  DextopChoice<T>(
                    value: item.$1,
                    groupValue: current,
                    allowReselect: true,
                    title: item.$2,
                    onChanged: (next) => Navigator.pop(dialogContext, next),
                  ),
              ],
            ),
          );
        },
      ),
    );
    if (selected != null && selected != current) {
      onSelected(selected);
    }
  }

  @override
  Widget build(BuildContext context) => EnsureDextopExpressive(
    child: Builder(
      builder: (context) {
        final colors = Theme.of(context).colorScheme;
        final themeOptions = <(String, String)>[
          ('system', t('uiSystem')),
          ('light', t('uiLight')),
          ('dark', t('uiDark')),
        ];
        final wallpaperOptions = <(String, String)>[
          ('plasma', localText(context, 'uiBook')),
          ('system', t('uiAndroidWallpaper')),
        ];
        final scaleValue = (book['display_scale'] as num?)?.toInt() ?? 0;
        final scaleOptions = <(int, String)>[
          for (final scale in [0, 100, 125, 150, 175, 200])
            (scale, scale == 0 ? t('uiAuto') : '$scale%'),
        ];
        return Scaffold(
          backgroundColor: colors.surface,
          appBar: M3EAppBar.top(
            automaticallyImplyLeading: true,
            titleText: localText(context, 'uiDextopBook'),
          ),
          body: error != null
              ? Center(
                  child: M3EButton.tonal(
                    onPressed: load,
                    child: Text(t('uiRetry')),
                  ),
                )
              : prefs == null
              ? const Center(
                  child: M3ELoadingIndicator(
                    variant: M3ELoadingIndicatorVariant.contained,
                  ),
                )
              : ListView(
                  padding: const EdgeInsets.fromLTRB(16, 4, 16, 32),
                  children: [
                    section(t('uiAppearance'), [
                      M3EListItem(
                        leading: const Icon(M3EIcons.dark_mode_outlined),
                        headline: t('uiTheme'),
                        supportingText: t('uiColoursFollowYourAndroidWallpaper'),
                        supportingMaxLines: null,
                        trailing: DextopDropdownButton(
                          label: themeOptions
                              .firstWhere(
                                (e) => e.$1 == theme,
                                orElse: () => themeOptions.last,
                              )
                              .$2,
                          onPressed: () => _pickChoice<String>(
                            context,
                            title: t('uiTheme'),
                            icon: M3EIcons.dark_mode_outlined,
                            current: theme,
                            items: themeOptions,
                            onSelected: (value) {
                              setState(() => theme = value);
                              save(
                                (p) => p.setString('plasma_color_scheme', value),
                              );
                            },
                          ),
                        ),
                        onTap: () => _pickChoice<String>(
                          context,
                          title: t('uiTheme'),
                          icon: M3EIcons.dark_mode_outlined,
                          current: theme,
                          items: themeOptions,
                          onSelected: (value) {
                            setState(() => theme = value);
                            save(
                              (p) => p.setString('plasma_color_scheme', value),
                            );
                          },
                        ),
                      ),
                      M3EListItem(
                        leading: const Icon(M3EIcons.wallpaper_outlined),
                        headline: t('uiWallpaper'),
                        trailing: DextopDropdownButton(
                          label: wallpaperOptions
                              .firstWhere(
                                (e) => e.$1 == wallpaper,
                                orElse: () => wallpaperOptions.first,
                              )
                              .$2,
                          onPressed: () => _pickChoice<String>(
                            context,
                            title: t('uiWallpaper'),
                            icon: M3EIcons.wallpaper_outlined,
                            current: wallpaper,
                            items: wallpaperOptions,
                            onSelected: (value) {
                              setState(() => wallpaper = value);
                              save((p) => p.setString('plasma_wallpaper', value));
                            },
                          ),
                        ),
                        onTap: () => _pickChoice<String>(
                          context,
                          title: t('uiWallpaper'),
                          icon: M3EIcons.wallpaper_outlined,
                          current: wallpaper,
                          items: wallpaperOptions,
                          onSelected: (value) {
                            setState(() => wallpaper = value);
                            save((p) => p.setString('plasma_wallpaper', value));
                          },
                        ),
                      ),
                      M3EListItem(
                        leading: const Icon(M3EIcons.zoom_in_rounded),
                        headline: t('uiExternalDisplaySize'),
                        trailing: DextopDropdownButton(
                          label: scaleValue == 0 ? t('uiAuto') : '$scaleValue%',
                          onPressed: () => _pickChoice<int>(
                            context,
                            title: t('uiExternalDisplaySize'),
                            icon: M3EIcons.zoom_in_rounded,
                            current: scaleValue,
                            items: scaleOptions,
                            onSelected: (value) =>
                                setBook('display_scale', value),
                          ),
                        ),
                        onTap: () => _pickChoice<int>(
                          context,
                          title: t('uiExternalDisplaySize'),
                          icon: M3EIcons.zoom_in_rounded,
                          current: scaleValue,
                          items: scaleOptions,
                          onSelected: (value) =>
                              setBook('display_scale', value),
                        ),
                      ),
                    ]),
                    section(t('uiTaskbarAndStartMenu'), [
                      flag(
                        'always_show_taskbar',
                        t('uiAlwaysShowTheTaskbar'),
                        M3EIcons.dock_outlined,
                      ),
                      flag(
                        'launcher_expanded',
                        t('uiExpandTheStartMenu'),
                        M3EIcons.open_in_full_rounded,
                        subtitle: t('uiChangesMadeInTheMenuAreAlsoSaved'),
                      ),
                    ]),
                    section(t('uiStatusBar'), [
                      flag(
                        'live_activity',
                        t('uiLiveActivity'),
                        M3EIcons.graphic_eq_rounded,
                        subtitle: t('uiShowPlayingMedia'),
                      ),
                      flag(
                        'notification_icons',
                        t('uiNotificationIcons'),
                        M3EIcons.notifications_outlined,
                      ),
                      DextopToggle(
                        icon: M3EIcons.battery_std_rounded,
                        title: t('uiBatteryPercentage'),
                        value: battery,
                        onChanged: (v) {
                          setState(() => battery = v);
                          save(
                            (p) => p.setBool('plasma_tray_battery_percent', v),
                          );
                        },
                      ),
                    ]),
                    section(t('uiDesktopAndBehaviour'), [
                      flag(
                        'desktop_icons',
                        t('uiShowDesktopIcons'),
                        M3EIcons.grid_view_rounded,
                      ),
                      DextopToggle(
                        icon: M3EIcons.north_west_rounded,
                        title: t('uiHotCorner'),
                        value: hotCorner,
                        onChanged: (v) {
                          setState(() => hotCorner = v);
                          save((p) => p.setBool('plasma_hot_corner', v));
                        },
                      ),
                    ]),
                    const SizedBox(height: 12),
                    DextopSliderTile(
                      icon: M3EIcons.view_carousel_outlined,
                      title: t('uiVirtualDesktops'),
                      valueLabel: '$desktops',
                      value: desktops.toDouble(),
                      min: 1,
                      max: 6,
                      divisions: 5,
                      onChanged: (v) => setState(() => desktops = v.round()),
                      onChangeEnd: (v) => save(
                        (p) => p.setInt('plasma_virtual_desktops', v.round()),
                      ),
                    ),
                    const SizedBox(height: 12),
                    DextopSliderTile(
                      icon: M3EIcons.animation_outlined,
                      title: t('uiAnimationSpeed'),
                      valueLabel: speed == 0
                          ? t('uiOff')
                          : '${speed.toStringAsFixed(2)}×',
                      value: speed,
                      min: 0,
                      max: 2,
                      divisions: 8,
                      onChanged: (v) => setState(() => speed = v),
                      onChangeEnd: (v) =>
                          save((p) => p.setDouble('plasma_animation_speed', v)),
                    ),
                  ],
                ),
        );
      },
    ),
  );
}
