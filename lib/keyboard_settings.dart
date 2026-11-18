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
  static const _hapticsStrengthKey = 'laptop_keyboard_haptics_strength';
  static const _blackBerryEnabledKey = 'experimental_blackberry_mode';
  static const _blackBerryAutoStartKey = 'blackberry_auto_start';
  static const _channel = MethodChannel('app.freedextop/display');
  static const _languages = <(String, String)>[
    ('en', 'uiLanguageEnglish'),
    ('fr', 'uiLanguageFrench'),
    ('de', 'uiLanguageGerman'),
    ('es', 'uiLanguageSpanish'),
    ('it', 'uiLanguageItalian'),
    ('pt', 'uiLanguagePortuguese'),
    ('ru', 'uiLanguageRussian'),
    ('uk', 'uiLanguageUkrainian'),
    ('ko', 'uiLanguageKorean'),
    ('ja', 'uiLanguageJapanese'),
    ('zh-pinyin', 'uiLanguagePinyin'),
  ];

  Set<String> _enabled = {'en'};
  bool _swipeEnabled = false;
  bool _candidatesEnabled = false;
  bool _hapticsEnabled = true;
  int _hapticsStrength = 70;
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
        _hapticsStrength = (prefs.getInt(_hapticsStrengthKey) ?? 70).clamp(
          1,
          100,
        );
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

  Future<void> _setHapticsStrength(double value) async {
    final strength = value.round().clamp(1, 100);
    setState(() => _hapticsStrength = strength);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_hapticsStrengthKey, strength);
  }

  Future<void> _addLanguage() async {
    final available = _languages
        .where((item) => !_enabled.contains(item.$1))
        .toList();
    if (available.isEmpty) return;
    final selected = await M3EBottomSheet.show<String>(
      context,
      showDragHandle: true,
      builder: (sheetContext) {
        final colors = Theme.of(sheetContext).colorScheme;
        return Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(8, 4, 8, 12),
                child: Text(
                  AppLocalizations.of(sheetContext).keyboardSwipeAddLanguage,
                  style: Theme.of(sheetContext).textTheme.titleLarge,
                ),
              ),
              Flexible(
                child: SingleChildScrollView(
                  child: DextopGroup(
                    color: colors.surfaceContainer,
                    children: [
                      for (final language in available)
                        M3EListItem(
                          leading: const Icon(M3EIcons.language_rounded),
                          headline: localText(sheetContext, language.$2),
                          onTap: () => Navigator.pop(sheetContext, language.$1),
                        ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
    if (selected != null) await _save({..._enabled, selected});
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final colors = Theme.of(context).colorScheme;
    final enabledLanguages = _languages
        .where((item) => _enabled.contains(item.$1))
        .toList();
    final content = ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
      children: [
        DextopGroup(
          children: [
            M3EListItem(
              leading: const Icon(M3EIcons.palette_outlined),
              headline: l.keyboardThemesTitle,
              supportingText: l.keyboardThemesDescription,
              supportingMaxLines: null,
              trailing: const Icon(M3EIcons.chevron_right_rounded),
              enabled: !widget.isRunning,
              onTap: widget.isRunning
                  ? null
                  : () => Navigator.of(context).push(
                      dextopRoute<void>(const KeyboardThemesPage()),
                    ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        DextopGroup(
          children: [
            DextopToggle(
              icon: M3EIcons.keyboard_rounded,
              value: _blackBerryEnabled,
              enabled: !widget.isRunning,
              onChanged: widget.isRunning
                  ? null
                  : (value) => _setOption(_blackBerryEnabledKey, value),
              title: l.experimentalBlackBerryMode,
              subtitle: l.experimentalBlackBerryModeDescription,
            ),
            if (!_foldableDevice)
              DextopToggle(
                icon: M3EIcons.play_circle_outline_rounded,
                value: _blackBerryAutoStart,
                enabled: _blackBerryEnabled && !widget.isRunning,
                onChanged: _blackBerryEnabled && !widget.isRunning
                    ? (value) => _setOption(_blackBerryAutoStartKey, value)
                    : null,
                title: l.blackBerryAutoStart,
                subtitle: l.blackBerryAutoStartDescription,
              ),
            DextopToggle(
              icon: M3EIcons.vibration_rounded,
              value: _hapticsEnabled,
              onChanged: (value) => _setOption(_hapticsEnabledKey, value),
              title: l.keyboardHaptics,
              subtitle: l.keyboardHapticsDescription,
            ),
            DextopToggle(
              icon: M3EIcons.gesture_rounded,
              value: _swipeEnabled,
              onChanged: (value) => _setOption(_swipeEnabledKey, value),
              title: l.keyboardSwipeInput,
              subtitle: l.keyboardSwipeInputDescription,
            ),
            DextopToggle(
              icon: M3EIcons.auto_awesome_rounded,
              value: _candidatesEnabled,
              enabled: _swipeEnabled,
              onChanged: _swipeEnabled
                  ? (value) => _setOption(_candidatesEnabledKey, value)
                  : null,
              title: l.keyboardSwipeCandidates,
              subtitle: l.keyboardSwipeCandidatesDescription,
            ),
          ],
        ),
        if (_hapticsEnabled) ...[
          const SizedBox(height: 12),
          DextopSliderTile(
            icon: M3EIcons.tune_rounded,
            title: l.hapticsStrength,
            valueLabel: '$_hapticsStrength%',
            value: _hapticsStrength.toDouble(),
            min: 1,
            max: 100,
            divisions: 99,
            onChanged: (value) =>
                setState(() => _hapticsStrength = value.round()),
            onChangeEnd: _setHapticsStrength,
          ),
        ],
        DextopSection(l.keyboardSwipeLanguages),
        Padding(
          padding: const EdgeInsets.fromLTRB(8, 0, 8, 10),
          child: Text(
            l.keyboardSwipeLanguagesDescription,
            style: Theme.of(
              context,
            ).textTheme.bodyMedium?.copyWith(color: colors.onSurfaceVariant),
          ),
        ),
        DextopGroup(
          children: [
            for (final language in enabledLanguages)
              M3EListItem(
                leading: const Icon(M3EIcons.language_rounded),
                headline: localText(context, language.$2),
                trailing: language.$1 == 'en'
                    ? DextopStatusPill(
                        label: l.keyboardSwipeDefaultLanguage,
                        containerColor: colors.secondaryContainer,
                        contentColor: colors.onSecondaryContainer,
                      )
                    : M3EIconButton(
                        variant: M3EIconButtonVariant.standard,
                        tooltip: l.deleteResolution,
                        icon: const Icon(M3EIcons.remove_circle_outline),
                        onPressed: () =>
                            _save({..._enabled}..remove(language.$1)),
                      ),
              ),
          ],
        ),
        const SizedBox(height: 12),
        M3EButton.tonal(
          size: M3EButtonSize.md,
          enabled: _swipeEnabled && _enabled.length < _languages.length,
          onPressed: !_swipeEnabled || _enabled.length == _languages.length
              ? null
              : _addLanguage,
          icon: const Icon(M3EIcons.add_rounded),
          label: Text(l.keyboardSwipeAddLanguage),
        ),
      ],
    );
    if (widget.embedded) return content;
    return Scaffold(
      backgroundColor: colors.surface,
      appBar: M3EAppBar.top(
        automaticallyImplyLeading: true,
        titleText: l.keyboardSettingsTitle,
      ),
      body: content,
    );
  }
}
