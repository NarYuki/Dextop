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

  static const _builtIns = <Map<String, dynamic>>[
    {
      'id': 'standard',
      'name': 'Standard',
      'background': '#121216',
      'key': '#302E36',
      'border': '#4C4854',
      'text': '#EBE7EF',
      'trackpad': '#232228',
      'trackpadText': '#918D97',
      'opacity': 1.0,
      'blur': 0.0,
      'radius': 7.0,
    },
    {
      'id': 'crimson',
      'name': 'Crimson',
      'background': '#590E0E',
      'key': '#6E1B1B',
      'border': '#7E201B',
      'text': '#FFBE97',
      'trackpad': '#5A0F10',
      'trackpadText': '#DE8966',
      'opacity': 1.0,
      'blur': 0.0,
      'radius': 7.0,
    },
    {
      'id': 'cloud',
      'name': 'Cloud Pop',
      'background': '#DCEBFF',
      'key': '#FFFFFF',
      'border': '#B7D4F5',
      'text': '#426486',
      'trackpad': '#C7DDF5',
      'trackpadText': '#52789F',
      'opacity': 0.94,
      'blur': 8.0,
      'radius': 16.0,
    },
  ];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_storageKey);
    final custom = raw == null
        ? <Map<String, dynamic>>[]
        : (jsonDecode(raw) as List)
              .map((item) => Map<String, dynamic>.from(item as Map))
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
    final custom = _themes.where((t) => !(t['builtIn'] == true) &&
        !_builtIns.any((b) => b['id'] == t['id']));
    await prefs.setString(_storageKey, jsonEncode(custom.toList()));
    await prefs.setString('laptop_keyboard_theme', _selected);
    for (final theme in _themes) {
      final normalized = Map<String, dynamic>.from(theme)
        ..['keyVariant'] = theme['keyVariant'] ?? theme['key']
        ..['selected'] = theme['selected'] ?? theme['border'];
      await prefs.setString(
        'dextop_keyboard_theme_${theme['id']}',
        jsonEncode(normalized),
      );
    }
  }

  Color _color(Map<String, dynamic> theme, String key) {
    final value = theme[key] as String? ?? '#FFFFFF';
    return Color(int.parse(value.substring(1), radix: 16) | 0xFF000000);
  }

  Future<void> _select(String id) async {
    setState(() => _selected = id);
    await _save();
  }

  Future<void> _createTheme() async {
    final id = 'custom_${DateTime.now().millisecondsSinceEpoch}';
    final theme = Map<String, dynamic>.from(_builtIns[2])
      ..['id'] = id
      ..['name'] = 'Custom theme';
    setState(() {
      _themes.add(theme);
      _selected = id;
    });
    await _save();
  }

  Future<void> _pickImage(Map<String, dynamic> theme) async {
    final result = await FilePicker.platform.pickFiles(type: FileType.image,
        withData: true);
    final bytes = result?.files.single.bytes;
    if (bytes == null) return;
    setState(() => theme['imageBase64'] = base64Encode(bytes));
    await _save();
  }

  Future<void> _export() async {
    setState(() => _busy = true);
    try {
      final archive = Archive();
      final manifest = utf8.encode(jsonEncode(_themes));
      archive.addFile(ArchiveFile('themes.json', manifest.length, manifest));
      final watermark = utf8.encode('$_themeWatermark;exported-by=free_dextop');
      archive.addFile(ArchiveFile('.dextop-watermark', watermark.length, watermark));
      final bytes = ZipEncoder().encode(archive);
      final path = await FilePicker.platform.saveFile(
        dialogTitle: 'Export keyboard themes',
        fileName: 'dextop-keyboard-themes.zip',
        type: FileType.custom,
        allowedExtensions: ['zip'],
      );
      if (path != null) await File(path).writeAsBytes(bytes);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _import() async {
    final result = await FilePicker.platform.pickFiles(type: FileType.custom,
        allowedExtensions: ['zip'], withData: true);
    final bytes = result?.files.single.bytes;
    if (bytes == null) return;
    final archive = ZipDecoder().decodeBytes(bytes);
    final file = archive.findFile('themes.json');
    if (file == null) return;
    final imported = (jsonDecode(utf8.decode(file.content as List<int>)) as List)
        .map((item) => Map<String, dynamic>.from(item as Map))
        .where((item) => item['id'] is String && item['name'] is String)
        .toList();
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
    final selected = _themes.firstWhere((t) => t['id'] == _selected,
        orElse: () => _themes.isEmpty ? _builtIns[2] : _themes.first);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Keyboard themes'),
        actions: [
          IconButton(onPressed: _busy ? null : _import, icon: const Icon(Icons.file_open_outlined), tooltip: 'Import ZIP'),
          IconButton(onPressed: _busy ? null : _export, icon: const Icon(Icons.archive_outlined), tooltip: 'Export ZIP'),
        ],
      ),
      body: _themes.isEmpty
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Text('Choose a theme', style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 12),
                SizedBox(
                  height: 190,
                  child: ListView.separated(
                    scrollDirection: Axis.horizontal,
                    itemCount: _themes.length,
                    separatorBuilder: (_, __) => const SizedBox(width: 12),
                    itemBuilder: (context, index) {
                      final theme = _themes[index];
                      return SizedBox(
                        width: 220,
                        child: Card(
                          clipBehavior: Clip.antiAlias,
                          child: InkWell(
                            onTap: () => _select(theme['id'] as String),
                            onLongPress: () => _showPreview(theme),
                            child: Padding(
                              padding: const EdgeInsets.all(12),
                              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                                Expanded(child: _preview(theme, compact: true)),
                                const SizedBox(height: 8),
                                Text(theme['name'] as String, maxLines: 1, overflow: TextOverflow.ellipsis),
                                Row(children: [
                                  Expanded(child: Text('${theme['blur']} px blur', style: Theme.of(context).textTheme.bodySmall)),
                                  Radio<String>(value: theme['id'] as String, groupValue: _selected,
                                      onChanged: (_) => _select(theme['id'] as String)),
                                ]),
                              ]),
                            ),
                          ),
                        ),
                      );
                    },
                  ),
                ),
                Align(alignment: Alignment.centerLeft, child: Text('Tap to select · long-press to preview', style: Theme.of(context).textTheme.bodySmall)),
                const SizedBox(height: 8),
                FilledButton.icon(onPressed: _createTheme, icon: const Icon(Icons.add), label: const Text('Add custom theme')),
                const SizedBox(height: 20),
                _editor(selected),
              ],
            ),
    );
  }

  Widget _editor(Map<String, dynamic> theme) => Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('Customize ${theme['name']}', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 12),
            Wrap(spacing: 8, runSpacing: 8, children: [
              for (final key in ['background', 'key', 'border', 'text', 'trackpad'])
                ActionChip(label: Text(key), avatar: CircleAvatar(backgroundColor: _color(theme, key)), onPressed: () => _cycleColor(theme, key)),
            ]),
            Slider(value: (theme['opacity'] as num).toDouble(), min: .2, max: 1, label: 'Opacity', onChanged: (v) { setState(() => theme['opacity'] = v); _save(); }),
            Slider(value: (theme['blur'] as num).toDouble(), min: 0, max: 30, label: 'Blur', onChanged: (v) { setState(() => theme['blur'] = v); _save(); }),
            Slider(value: (theme['radius'] as num).toDouble(), min: 0, max: 28, label: 'Corner radius', onChanged: (v) { setState(() => theme['radius'] = v); _save(); }),
            OutlinedButton.icon(onPressed: () => _pickImage(theme), icon: const Icon(Icons.image_outlined), label: const Text('Choose background image')),
            const SizedBox(height: 8),
            OutlinedButton.icon(onPressed: () => _showPreview(theme), icon: const Icon(Icons.preview_outlined), label: const Text('Preview keyboard overlay')),
          ]),
        ),
      );

  void _cycleColor(Map<String, dynamic> theme, String key) {
    const colors = ['#FFFFFF', '#DCEBFF', '#C7DDF5', '#FFBE97', '#6E1B1B', '#302E36', '#121216', '#426486'];
    final current = theme[key] as String? ?? colors.first;
    final next = colors[(colors.indexOf(current) + 1) % colors.length];
    setState(() => theme[key] = next);
    _save();
  }

  void _showPreview(Map<String, dynamic> theme) {
    showDialog<void>(context: context, builder: (_) => Dialog(
      child: Padding(padding: const EdgeInsets.all(12), child: SizedBox(
        width: 560, height: 300, child: _preview(theme),
      )),
    ));
  }

  Widget _preview(Map<String, dynamic> theme, {bool compact = false}) {
    final background = _color(theme, 'background');
    final key = _color(theme, 'key');
    final border = _color(theme, 'border');
    final text = _color(theme, 'text');
    final radius = (theme['radius'] as num).toDouble();
    final rows = const [
      '`1234567890-=⌫',
      'QWERTYUIOP[]\\',
      'ASDFGHJKL;\'⏎',
      '⇧ZXCVBNM,./⇧',
      'Ctrl   Alt       SPACE       Alt   ←↑↓→',
    ];
    return Container(
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(color: background, borderRadius: BorderRadius.circular(radius)),
      child: Column(children: [
        for (final row in rows) Expanded(child: Row(children: [
          for (final char in row.split('')) Expanded(child: Padding(
            padding: const EdgeInsets.all(2),
            child: DecoratedBox(decoration: BoxDecoration(color: key, border: Border.all(color: border), borderRadius: BorderRadius.circular(radius / 2)),
              child: Center(child: Text(char, style: TextStyle(color: text, fontSize: compact ? 9 : 12))),
            ),
          )),
        ])),
      ]),
    );
  }
}
