part of 'main.dart';

class KeyboardThemesPage extends StatefulWidget {
  const KeyboardThemesPage({super.key});

  @override
  State<KeyboardThemesPage> createState() => _KeyboardThemesPageState();
}

class _KeyboardThemesPageState extends State<KeyboardThemesPage> {
  static const _storageKey = 'dextop_laptop_keyboard_themes';
  static const _themeWatermark = 'DextopKeyboardTheme/v1';
  List<Map<String, dynamic>> _themes = [];
  String _selected = 'cloud';
  bool _busy = false;
  AppLocalizations get _l10n => AppLocalizations.of(context);

  String _colorLabel(String key) => switch (key) {
    'background' => _l10n.keyboardThemesBackground,
    'key' => _l10n.keyboardThemesKey,
    'border' => _l10n.keyboardThemesBorder,
    'text' => _l10n.keyboardThemesText,
    'trackpad' => _l10n.keyboardThemesTrackpad,
    _ => key,
  };

  @override
  void dispose() {
    // A demo overlay is transient and must not remain behind the theme page
    // after leaving it.
    const MethodChannel(
      'app.freedextop/display',
    ).invokeMethod('hideLaptopDemo');
    const MethodChannel(
      'app.freedextop/display',
    ).invokeMethod('restoreOverlayAfterSettings');
    super.dispose();
  }

  static const _builtIns = <Map<String, dynamic>>[
    {
      'id': 'standard',
      'name': 'Standard',
      'background': '#121216',
      'key': '#302E36',
      'keyVariant': '#302E36',
      'border': '#4C4854',
      'text': '#EBE7EF',
      'trackpad': '#232228',
      'trackpadText': '#918D97',
      'selected': '#D0BCED',
      'opacity': 1.0,
      'keyOpacity': 1.0,
      'showTrackpadLabel': true,
      'blur': 0.0,
      'radius': 7.0,
    },
    {
      'id': 'crimson',
      'name': 'Crimson',
      'background': '#590E0E',
      'key': '#6E1B1B',
      'keyVariant': '#510A0B',
      'border': '#7E201B',
      'text': '#FFBE97',
      'trackpad': '#5A0F10',
      'trackpadText': '#DE8966',
      'selected': '#510A0B',
      'opacity': 1.0,
      'keyOpacity': 1.0,
      'showTrackpadLabel': true,
      'blur': 0.0,
      'radius': 7.0,
    },
    {
      'id': 'cloud',
      'name': 'Cloud Pop',
      'background': '#DCEBFF',
      'key': '#FFFFFF',
      'keyVariant': '#F7FBFF',
      'border': '#B7D4F5',
      'text': '#426486',
      'trackpad': '#C7DDF5',
      'trackpadText': '#52789F',
      'selected': '#BEDAF8',
      'opacity': 0.94,
      'keyOpacity': 1.0,
      'showTrackpadLabel': true,
      'blur': 8.0,
      'radius': 16.0,
    },
    {
      'id': 'amoled',
      'name': 'AMOLED',
      'background': '#000000',
      // True-black keycaps with a restrained warm-gold accent, matching the
      // near-black foldable-PC keyboard reference.  Keep every key surface
      // in the AMOLED range so the gaps and caps do not read as a grey slab.
      'key': '#000000',
      'keyVariant': '#030303',
      'border': '#3B3B3B',
      'text': '#DCDCDC',
      'trackpad': '#000000',
      'trackpadText': '#AFAFAF',
      'selected': '#515151',
      'opacity': 1.0,
      'keyOpacity': 1.0,
      'showTrackpadLabel': true,
      'blur': 0.0,
      'radius': 7.0,
    },
  ];

  @override
  void initState() {
    super.initState();
    const MethodChannel(
      'app.freedextop/display',
    ).invokeMethod('exitLaptopPreview');
    const MethodChannel(
      'app.freedextop/display',
    ).invokeMethod('hideOverlayForSettings');
    _load();
  }

  Future<void> _load() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_storageKey);
    final custom = raw == null
        ? <Map<String, dynamic>>[]
        : (jsonDecode(raw) as List)
              .map((item) => Map<String, dynamic>.from(item as Map))
              // Older theme exports had a separate trackpad opacity.  The
              // trackpad now follows the single theme opacity, so discard
              // that obsolete value when loading.
              .map((theme) => theme..remove('trackpadOpacity'))
              .map(
                (theme) => theme..putIfAbsent('showTrackpadLabel', () => true),
              )
              .toList();
    setState(() {
      _themes = [
        ..._builtIns.map((theme) => Map<String, dynamic>.from(theme)),
        ...custom,
      ];
      _selected = prefs.getString('laptop_keyboard_theme') ?? 'cloud';
    });
  }

  Future<void> _save() async {
    final prefs = await SharedPreferences.getInstance();
    final custom = _themes.where(
      (t) =>
          !(t['builtIn'] == true) && !_builtIns.any((b) => b['id'] == t['id']),
    );
    await prefs.setString(_storageKey, jsonEncode(custom.toList()));
    await prefs.setString('laptop_keyboard_theme', _selected);
    for (final theme in _themes) {
      final normalized = Map<String, dynamic>.from(theme)
        ..['keyVariant'] = theme['keyVariant'] ?? theme['key']
        ..['selected'] = theme['selected'] ?? theme['border']
        ..['showTrackpadLabel'] = theme['showTrackpadLabel'] != false
        ..remove('trackpadOpacity');
      await prefs.setString(
        'dextop_keyboard_theme_${theme['id']}',
        jsonEncode(normalized),
      );
    }
    // Keep a running laptop deck in sync when a theme is selected or edited
    // from the settings page. The native overlay re-renders in place.
    await const MethodChannel(
      'app.freedextop/display',
    ).invokeMethod<void>('laptopKeyboardTheme', {'themeId': _selected});
  }

  Color _color(Map<String, dynamic> theme, String key) {
    final value = theme[key] as String? ?? '#FFFFFF';
    return Color(int.parse(value.substring(1), radix: 16) | 0xFF000000);
  }

  Future<void> _select(String id) async {
    setState(() => _selected = id);
    await _save();
  }

  Future<Map<String, dynamic>> _createTheme({
    String name = 'Custom theme',
  }) async {
    final id = 'custom_${DateTime.now().millisecondsSinceEpoch}';
    final theme = Map<String, dynamic>.from(_builtIns[2])
      ..['id'] = id
      ..['name'] = name.trim().isEmpty ? 'Custom theme' : name.trim();
    setState(() {
      _themes.add(theme);
      _selected = id;
    });
    await _save();
    return theme;
  }

  Future<void> _showCreateDialog() async {
    final controller = TextEditingController(text: 'Custom theme');
    final name = await showDialog<String>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(_l10n.keyboardThemesNew),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: InputDecoration(labelText: _l10n.keyboardThemesName),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: Text(currentLocalizations().uiCancel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, controller.text),
            child: Text(_l10n.keyboardThemesCreate),
          ),
        ],
      ),
    );
    if (name != null) {
      final theme = await _createTheme(name: name);
      if (mounted) await _showEditDialog(theme);
    }
  }

  Future<void> _showDeleteDialog(Map<String, dynamic> theme) async {
    if (_builtIns.any((item) => item['id'] == theme['id'])) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(_l10n.keyboardThemesBuiltIn)));
      return;
    }
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        alignment: Alignment.center,
        insetPadding: const EdgeInsets.symmetric(horizontal: 24, vertical: 24),
        title: Text(_l10n.keyboardThemesDeleteTitle),
        content: Text(_l10n.keyboardThemesDeleteBody(theme['name'] as String)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: Text(currentLocalizations().uiCancel),
          ),
          FilledButton.tonal(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: Text(currentLocalizations().uiDelete),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    setState(() {
      _themes.removeWhere((item) => item['id'] == theme['id']);
      if (_selected == theme['id']) _selected = 'cloud';
    });
    await _save();
  }

  Future<void> _showRealLaptopPreview(String themeId) async {
    final shown =
        await const MethodChannel(
          'app.freedextop/display',
        ).invokeMethod<bool>('previewLaptopOverlay', {'themeId': themeId}) ??
        false;
    if (!shown && mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(_l10n.keyboardThemesStartFirst)));
    }
  }

  Future<void> _pickImage(Map<String, dynamic> theme) async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.image,
      withData: true,
    );
    final bytes = result?.files.single.bytes;
    if (bytes == null) return;
    setState(() => theme['imageBase64'] = base64Encode(bytes));
    await _save();
  }

  Future<void> _exportTheme(Map<String, dynamic> theme) async {
    setState(() => _busy = true);
    try {
      final archive = Archive();
      // Keep settings and user-provided artwork as separate archive entries.
      // The old exporter put the image inline in themes.json, which made the
      // result unsuitable for sharing and difficult to inspect/import in other
      // tools.  The importer below still accepts that legacy representation.
      final exportedTheme = Map<String, dynamic>.from(theme);
      final imageBase64 = exportedTheme.remove('imageBase64') as String?;
      String? imagePath;
      if (imageBase64 != null && imageBase64.isNotEmpty) {
        final imageBytes = base64Decode(imageBase64);
        final extension = _imageExtension(imageBytes);
        imagePath = 'assets/background.$extension';
        exportedTheme['imagePath'] = imagePath;
        archive.addFile(ArchiveFile(imagePath, imageBytes.length, imageBytes));
      }
      final manifest = utf8.encode(jsonEncode(exportedTheme));
      archive.addFile(ArchiveFile('theme.json', manifest.length, manifest));
      final watermark = utf8.encode('$_themeWatermark;exported-by=free_dextop');
      archive.addFile(
        ArchiveFile('.dextop-watermark', watermark.length, watermark),
      );
      final bytes = ZipEncoder().encode(archive);
      final safeName = (theme['name'] as String? ?? 'keyboard-theme')
          .replaceAll(RegExp(r'[^A-Za-z0-9._-]+'), '-');
      final exportDirectory = Directory(
        '${Directory.systemTemp.path}/dextop-theme-exports',
      );
      await exportDirectory.create(recursive: true);
      final zipFile = File(
        '${exportDirectory.path}/$safeName-${DateTime.now().millisecondsSinceEpoch}.zip',
      );
      await zipFile.writeAsBytes(bytes, flush: true);
      await SharePlus.instance.share(
        ShareParams(
          files: [XFile(zipFile.path, mimeType: 'application/zip')],
          subject: theme['name'] as String? ?? _l10n.keyboardThemesExport,
          title: _l10n.keyboardThemesExportDialog,
        ),
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  String _imageExtension(List<int> bytes) {
    if (bytes.length >= 8 &&
        bytes[0] == 0x89 &&
        bytes[1] == 0x50 &&
        bytes[2] == 0x4E &&
        bytes[3] == 0x47) {
      return 'png';
    }
    if (bytes.length >= 3 &&
        bytes[0] == 0xFF &&
        bytes[1] == 0xD8 &&
        bytes[2] == 0xFF) {
      return 'jpg';
    }
    if (bytes.length >= 12 &&
        String.fromCharCodes(bytes.sublist(0, 4)) == 'RIFF' &&
        String.fromCharCodes(bytes.sublist(8, 12)) == 'WEBP') {
      return 'webp';
    }
    if (bytes.length >= 6 &&
        (String.fromCharCodes(bytes.sublist(0, 6)) == 'GIF87a' ||
            String.fromCharCodes(bytes.sublist(0, 6)) == 'GIF89a')) {
      return 'gif';
    }
    return 'bin';
  }

  Future<void> _import() async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['zip'],
      withData: true,
    );
    final bytes = result?.files.single.bytes;
    if (bytes == null) return;
    final archive = ZipDecoder().decodeBytes(bytes);
    final file =
        archive.findFile('theme.json') ?? archive.findFile('themes.json');
    if (file == null) return;
    final decoded = jsonDecode(utf8.decode(List<int>.from(file.content)));
    final rawThemes = decoded is List ? decoded : [decoded];
    final imported = <Map<String, dynamic>>[];
    for (final item in rawThemes) {
      if (item is! Map) continue;
      final theme = Map<String, dynamic>.from(item)
        ..remove('trackpadOpacity')
        ..putIfAbsent('showTrackpadLabel', () => true);
      final imagePath = theme.remove('imagePath');
      if (imagePath is String && imagePath.isNotEmpty) {
        final imageFile = archive.findFile(imagePath);
        if (imageFile != null) {
          theme['imageBase64'] = base64Encode(
            List<int>.from(imageFile.content),
          );
        }
      }
      if (theme['id'] is String && theme['name'] is String) {
        imported.add(theme);
      }
    }
    setState(() {
      _themes = [
        ..._builtIns.map((theme) => Map<String, dynamic>.from(theme)),
        ...imported,
      ];
      _selected = imported.isEmpty ? 'cloud' : imported.first['id'] as String;
    });
    await _save();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_l10n.keyboardThemesTitle),
        actions: [
          IconButton(
            onPressed: _showCreateDialog,
            icon: const Icon(Icons.add),
            tooltip: _l10n.keyboardThemesAdd,
          ),
          IconButton(
            onPressed: _busy ? null : _import,
            icon: const Icon(Icons.file_open_outlined),
            tooltip: currentLocalizations().uiImport,
          ),
        ],
      ),
      body: _themes.isEmpty
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Text(
                  _l10n.keyboardThemesChoose,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 12),
                GridView.builder(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 3,
                    crossAxisSpacing: 12,
                    mainAxisSpacing: 12,
                    childAspectRatio: 1.05,
                  ),
                  itemCount: _themes.length,
                  itemBuilder: (context, index) {
                    final theme = _themes[index];
                    return SizedBox(
                      child: Card(
                        clipBehavior: Clip.antiAlias,
                        child: InkWell(
                          onTap: () => _select(theme['id'] as String),
                          onLongPress: () => _showDeleteDialog(theme),
                          child: Padding(
                            padding: const EdgeInsets.all(12),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Expanded(
                                  child: _preview(
                                    theme,
                                    compact: true,
                                    includeTrackpad: false,
                                  ),
                                ),
                                const SizedBox(height: 8),
                                Text(
                                  theme['name'] as String,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                                Row(
                                  children: [
                                    const Expanded(child: SizedBox()),
                                    IconButton(
                                      icon: const Icon(Icons.preview_outlined),
                                      tooltip: _l10n.keyboardThemesPreview,
                                      onPressed: () => _showRealLaptopPreview(
                                        theme['id'] as String,
                                      ),
                                    ),
                                    IconButton(
                                      icon: const Icon(Icons.edit_outlined),
                                      tooltip: _l10n.keyboardThemesEditTip,
                                      onPressed: () => _showEditDialog(theme),
                                    ),
                                    Radio<String>(
                                      value: theme['id'] as String,
                                      // ignore: deprecated_member_use
                                      groupValue: _selected,
                                      // ignore: deprecated_member_use
                                      onChanged: (_) =>
                                          _select(theme['id'] as String),
                                    ),
                                  ],
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    );
                  },
                ),
                const SizedBox(height: 20),
              ],
            ),
    );
  }

  Future<void> _showEditDialog(Map<String, dynamic> theme) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => KeyboardThemeEditorPage(
          theme: theme,
          color: _color,
          colorLabel: _colorLabel,
          cycleColor: _cycleColor,
          pickImage: _pickImage,
          exportTheme: _exportTheme,
          save: _save,
          preview: (theme) =>
              _preview(theme, compact: true, includeTrackpad: false),
          showDemo: (id) => _showRealLaptopPreview(id),
        ),
      ),
    );
  }

  void _cycleColor(Map<String, dynamic> theme, String key) {
    const colors = [
      '#FFFFFF',
      '#DCEBFF',
      '#C7DDF5',
      '#FFBE97',
      '#6E1B1B',
      '#302E36',
      '#121216',
      '#426486',
    ];
    final current = theme[key] as String? ?? colors.first;
    final next = colors[(colors.indexOf(current) + 1) % colors.length];
    setState(() => theme[key] = next);
    _save();
  }

  Widget _preview(
    Map<String, dynamic> theme, {
    bool compact = false,
    bool includeTrackpad = true,
  }) {
    final background = _color(theme, 'background');
    final key = _color(theme, 'key');
    final border = _color(theme, 'border');
    final text = _color(theme, 'text');
    final radius = (theme['radius'] as num).toDouble();
    final opacity = ((theme['opacity'] as num?)?.toDouble() ?? 1.0).clamp(
      .1,
      1.0,
    );
    final keyOpacity =
        (theme['keyOpacity'] as num?)?.toDouble() ??
        (theme['opacity'] as num?)?.toDouble() ??
        1.0;
    final List<List<String>> rows = [
      '`1234567890-=⌫'.split(''),
      'QWERTYUIOP[]\\'.split(''),
      'ASDFGHJKL;\'⏎'.split(''),
      '⇧ZXCVBNM,./⇧'.split(''),
      compact
          ? ['CTRL', 'META', 'SPACE']
          : 'Ctrl   Alt       SPACE       Alt   ←↑↓→'.split(''),
    ];
    return Container(
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        // The gap between keys is the theme background. It shares the
        // single Opacity slider with the trackpad, while keyOpacity remains
        // independent for the key surfaces themselves.
        color: background.withValues(alpha: opacity),
        borderRadius: BorderRadius.circular(radius),
      ),
      child: Column(
        children: [
          Expanded(
            flex: 66,
            child: Column(
              children: [
                for (var rowIndex = 0; rowIndex < rows.length; rowIndex++)
                  Expanded(
                    child: Row(
                      children: [
                        for (final token in rows[rowIndex])
                          Expanded(
                            flex: compact && rowIndex == rows.length - 1
                                ? (token == 'SPACE' ? 2 : 1)
                                : 1,
                            child: Padding(
                              padding: const EdgeInsets.all(2),
                              child: DecoratedBox(
                                decoration: BoxDecoration(
                                  color: key.withValues(
                                    alpha: keyOpacity.clamp(.1, 1.0),
                                  ),
                                  border: Border.all(color: border),
                                  borderRadius: BorderRadius.circular(
                                    radius / 2,
                                  ),
                                ),
                                child: Center(
                                  child: Text(
                                    token,
                                    style: TextStyle(
                                      color: text,
                                      fontSize: compact ? 9 : 12,
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),
              ],
            ),
          ),
          if (includeTrackpad) const SizedBox(height: 6),
          if (includeTrackpad)
            Expanded(
              flex: 34,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: _color(theme, 'trackpad').withValues(alpha: opacity),
                  border: Border.all(color: border),
                  borderRadius: BorderRadius.circular(radius),
                ),
                child: theme['showTrackpadLabel'] != false
                    ? Center(
                        child: Text(
                          'TRACKPAD',
                          style: TextStyle(
                            color: _color(theme, 'trackpadText'),
                            letterSpacing: 2,
                            fontSize: compact ? 8 : 11,
                          ),
                        ),
                      )
                    : null,
              ),
            ),
        ],
      ),
    );
  }
}

class KeyboardThemeEditorPage extends StatefulWidget {
  const KeyboardThemeEditorPage({
    required this.theme,
    required this.color,
    required this.colorLabel,
    required this.cycleColor,
    required this.pickImage,
    required this.exportTheme,
    required this.save,
    required this.preview,
    required this.showDemo,
    super.key,
  });

  final Map<String, dynamic> theme;
  final Color Function(Map<String, dynamic>, String) color;
  final String Function(String) colorLabel;
  final void Function(Map<String, dynamic>, String) cycleColor;
  final Future<void> Function(Map<String, dynamic>) pickImage;
  final Future<void> Function(Map<String, dynamic>) exportTheme;
  final Future<void> Function() save;
  final Widget Function(Map<String, dynamic>) preview;
  final Future<void> Function(String) showDemo;

  @override
  State<KeyboardThemeEditorPage> createState() =>
      _KeyboardThemeEditorPageState();
}

class _KeyboardThemeEditorPageState extends State<KeyboardThemeEditorPage> {
  AppLocalizations get l => AppLocalizations.of(context);

  @override
  Widget build(BuildContext context) {
    final theme = widget.theme;
    return Scaffold(
      appBar: AppBar(
        title: Text('${l.keyboardThemesEdit} ${theme['name']}'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(l.keyboardThemesDone),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
        children: [
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final key in [
                'background',
                'key',
                'border',
                'text',
                'trackpad',
              ])
                ActionChip(
                  label: Text(widget.colorLabel(key)),
                  avatar: CircleAvatar(
                    backgroundColor: widget.color(theme, key),
                  ),
                  onPressed: () {
                    setState(() => widget.cycleColor(theme, key));
                    widget.save();
                  },
                ),
            ],
          ),
          _editorSlider(l.keyboardThemesOpacity, 'opacity', .2, 1),
          _editorSlider(l.keyboardThemesKeyOpacity, 'keyOpacity', .1, 1),
          _editorSlider(l.keyboardThemesBlur, 'blur', 0, 30),
          _editorSlider(l.keyboardThemesRadius, 'radius', 0, 28),
          SwitchListTile.adaptive(
            contentPadding: EdgeInsets.zero,
            title: Text(l.keyboardThemesShowTrackpadLabel),
            value: theme['showTrackpadLabel'] != false,
            onChanged: (visible) {
              setState(() => theme['showTrackpadLabel'] = visible);
              widget.save();
            },
          ),
          OutlinedButton.icon(
            onPressed: () async {
              await widget.pickImage(theme);
              if (mounted) setState(() {});
            },
            icon: const Icon(Icons.image_outlined),
            label: Text(l.keyboardThemesImage),
          ),
          OutlinedButton.icon(
            onPressed: () => widget.showDemo(theme['id'] as String),
            icon: const Icon(Icons.preview_outlined),
            label: Text(l.keyboardThemesPreview),
          ),
          OutlinedButton.icon(
            onPressed: () => widget.exportTheme(theme),
            icon: const Icon(Icons.archive_outlined),
            label: Text(l.keyboardThemesExport),
          ),
          const SizedBox(height: 24),
          SizedBox(height: 220, child: widget.preview(theme)),
        ],
      ),
    );
  }

  Widget _editorSlider(String label, String key, double min, double max) {
    final theme = widget.theme;
    final value =
        ((theme[key] as num?)?.toDouble() ??
                (theme['opacity'] as num?)?.toDouble() ??
                1.0)
            .clamp(min, max);
    return Row(
      children: [
        SizedBox(width: 120, child: Text(label)),
        Expanded(
          child: Slider(
            value: value,
            min: min,
            max: max,
            label: label,
            onChanged: (next) {
              setState(() => theme[key] = next);
              widget.save();
            },
          ),
        ),
      ],
    );
  }
}
