part of 'main.dart';

class KeyboardSettingsPage extends StatefulWidget {
  const KeyboardSettingsPage({
    super.key,
    this.embedded = false,
    this.isRunning = false,
  });

  final bool embedded;
  final bool isRunning;

  @override
  State<KeyboardSettingsPage> createState() => _KeyboardSettingsPageState();
}

class _KeyboardSettingsPageState extends State<KeyboardSettingsPage> {
  static const _storageKey = 'laptop_swipe_languages_json';
  static const _swipeEnabledKey = 'laptop_swipe_enabled';
  static const _candidatesEnabledKey = 'laptop_swipe_candidates_enabled';
  static const _hapticsEnabledKey = 'laptop_keyboard_haptics_enabled';
  static const _blackBerryEnabledKey = 'experimental_blackberry_mode';
  static const _blackBerryAutoStartKey = 'blackberry_auto_start';
  static const _channel = MethodChannel('app.freedextop/display');
  static const _languages = <(String, String)>[
    ('en', 'English'),
    ('fr', 'Français'),
    ('de', 'Deutsch'),
    ('es', 'Español'),
    ('it', 'Italiano'),
    ('pt', 'Português'),
    ('ru', 'Русский'),
    ('uk', 'Українська'),
    ('ko', '한국어'),
    ('ja', '日本語'),
    ('zh-pinyin', '中文拼音'),
  ];

  Set<String> _enabled = {'en'};
  bool _swipeEnabled = false;
  bool _candidatesEnabled = false;
  bool _hapticsEnabled = true;
  bool _blackBerryEnabled = false;
  bool _blackBerryAutoStart = false;
  bool _foldableDevice = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final prefs = await SharedPreferences.getInstance();
    var foldableDevice = true;
    try {
      foldableDevice =
          await _channel.invokeMethod<bool>('isFoldableDevice') ?? true;
    } catch (_) {}
    final raw = prefs.getString(_storageKey);
    final parsed = <String>{};
    if (raw != null) {
      try {
        parsed.addAll((jsonDecode(raw) as List).whereType<String>());
      } catch (_) {}
    }
    parsed.retainAll(_languages.map((item) => item.$1));
    parsed.add('en');
    if (mounted) {
      setState(() {
        _enabled = parsed;
        _swipeEnabled = prefs.getBool(_swipeEnabledKey) ?? false;
        _candidatesEnabled = prefs.getBool(_candidatesEnabledKey) ?? false;
        _hapticsEnabled = prefs.getBool(_hapticsEnabledKey) ?? true;
        _blackBerryEnabled = prefs.getBool(_blackBerryEnabledKey) ?? false;
        _blackBerryAutoStart = prefs.getBool(_blackBerryAutoStartKey) ?? false;
        _foldableDevice = foldableDevice;
      });
    }
  }

  Future<void> _setOption(String key, bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(key, value);
    if (!mounted) return;
    setState(() {
      if (key == _swipeEnabledKey) {
        _swipeEnabled = value;
      } else if (key == _candidatesEnabledKey) {
        _candidatesEnabled = value;
      } else if (key == _hapticsEnabledKey) {
        _hapticsEnabled = value;
      } else if (key == _blackBerryEnabledKey) {
        _blackBerryEnabled = value;
        if (!value) _blackBerryAutoStart = false;
      } else if (key == _blackBerryAutoStartKey) {
        _blackBerryAutoStart = value;
      }
    });
    if (key == _blackBerryEnabledKey && !value) {
      await prefs.setBool(_blackBerryAutoStartKey, false);
    }
    await _channel.invokeMethod<void>('laptopSwipeLanguagesChanged', {
      'enabled': key == _swipeEnabledKey ? value : _swipeEnabled,
    });
  }

  Future<void> _save(Set<String> languages) async {
    languages.add('en');
    final ordered = _languages
        .map((item) => item.$1)
        .where(languages.contains)
        .toList(growable: false);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_storageKey, jsonEncode(ordered));
    if (mounted) setState(() => _enabled = ordered.toSet());
    await _channel.invokeMethod<void>('laptopSwipeLanguagesChanged', {
      'enabled': _swipeEnabled,
    });
  }

  Future<void> _addLanguage() async {
    final available = _languages
        .where((item) => !_enabled.contains(item.$1))
        .toList();
    if (available.isEmpty) return;
    final selected = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: ListView(
          shrinkWrap: true,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 8, 24, 12),
              child: Text(
                AppLocalizations.of(context).keyboardSwipeAddLanguage,
                style: Theme.of(context).textTheme.titleLarge,
              ),
            ),
            for (final language in available)
              ListTile(
                leading: const Icon(Icons.language_rounded),
                title: Text(language.$2),
                onTap: () => Navigator.pop(context, language.$1),
              ),
          ],
        ),
      ),
    );
    if (selected != null) await _save({..._enabled, selected});
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final content = ListView(
      padding: const EdgeInsets.all(20),
      children: [
        Card(
          clipBehavior: Clip.antiAlias,
          child: ListTile(
            leading: const Icon(Icons.palette_outlined),
            title: Text(l.keyboardThemesTitle),
            subtitle: Text(l.keyboardThemesDescription),
            trailing: const Icon(Icons.chevron_right_rounded),
            enabled: !widget.isRunning,
            onTap: widget.isRunning
                ? null
                : () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => const KeyboardThemesPage(),
                    ),
                  ),
          ),
        ),
        const SizedBox(height: 12),
        Card(
          clipBehavior: Clip.antiAlias,
          child: Column(
            children: [
              SwitchListTile(
                secondary: const Icon(Icons.keyboard_rounded),
                value: _blackBerryEnabled,
                onChanged: widget.isRunning
                    ? null
                    : (value) => _setOption(_blackBerryEnabledKey, value),
                title: Text(l.experimentalBlackBerryMode),
                subtitle: Text(l.experimentalBlackBerryModeDescription),
              ),
              if (!_foldableDevice) ...[
                const Divider(height: 1),
                SwitchListTile(
                  secondary: const Icon(Icons.play_circle_outline_rounded),
                  value: _blackBerryAutoStart,
                  onChanged: _blackBerryEnabled && !widget.isRunning
                      ? (value) => _setOption(_blackBerryAutoStartKey, value)
                      : null,
                  title: Text(l.blackBerryAutoStart),
                  subtitle: Text(l.blackBerryAutoStartDescription),
                ),
              ],
              const Divider(height: 1),
              SwitchListTile(
                secondary: const Icon(Icons.vibration_rounded),
                value: _hapticsEnabled,
                onChanged: (value) => _setOption(_hapticsEnabledKey, value),
                title: Text(l.keyboardHaptics),
                subtitle: Text(l.keyboardHapticsDescription),
              ),
              const Divider(height: 1),
              SwitchListTile(
                secondary: const Icon(Icons.gesture_rounded),
                value: _swipeEnabled,
                onChanged: (value) => _setOption(_swipeEnabledKey, value),
                title: Text(l.keyboardSwipeInput),
                subtitle: Text(l.keyboardSwipeInputDescription),
              ),
              const Divider(height: 1),
              SwitchListTile(
                secondary: const Icon(Icons.auto_awesome_rounded),
                value: _candidatesEnabled,
                onChanged: _swipeEnabled
                    ? (value) => _setOption(_candidatesEnabledKey, value)
                    : null,
                title: Text(l.keyboardSwipeCandidates),
                subtitle: Text(l.keyboardSwipeCandidatesDescription),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),
        Text(
          l.keyboardSwipeLanguages,
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 4),
        Text(l.keyboardSwipeLanguagesDescription),
        const SizedBox(height: 12),
        Card(
          clipBehavior: Clip.antiAlias,
          child: Column(
            children: [
              for (final language in _languages.where(
                (item) => _enabled.contains(item.$1),
              )) ...[
                ListTile(
                  leading: const Icon(Icons.language_rounded),
                  title: Text(language.$2),
                  trailing: language.$1 == 'en'
                      ? Text(l.keyboardSwipeDefaultLanguage)
                      : IconButton(
                          tooltip: l.deleteResolution,
                          icon: const Icon(Icons.remove_circle_outline),
                          onPressed: () =>
                              _save({..._enabled}..remove(language.$1)),
                        ),
                ),
                if (language !=
                    _languages.lastWhere((item) => _enabled.contains(item.$1)))
                  const Divider(height: 1),
              ],
            ],
          ),
        ),
        const SizedBox(height: 12),
        FilledButton.tonalIcon(
          onPressed: !_swipeEnabled || _enabled.length == _languages.length
              ? null
              : _addLanguage,
          icon: const Icon(Icons.add_rounded),
          label: Text(l.keyboardSwipeAddLanguage),
        ),
      ],
    );
    if (widget.embedded) return content;
    return Scaffold(
      appBar: AppBar(title: Text(l.keyboardSettingsTitle)),
      body: content,
    );
  }
}
