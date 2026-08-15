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

  Future<void> _createTheme({String name = 'Custom theme'}) async {
    final id = 'custom_${DateTime.now().millisecondsSinceEpoch}';
    final theme = Map<String, dynamic>.from(_builtIns[2])
      ..['id'] = id
      ..['name'] = name.trim().isEmpty ? 'Custom theme' : name.trim();
    setState(() {
      _themes.add(theme);
      _selected = id;
    });
    await _save();
  }

  Future<void> _showCreateDialog() async {
    final controller = TextEditingController(text: 'Custom theme');
    final name = await showDialog<String>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('New keyboard theme'),
        content: TextField(controller: controller, autofocus: true,
          decoration: const InputDecoration(labelText: 'Theme name')),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(dialogContext, controller.text), child: const Text('Create')),
        ],
      ),
    );
    if (name != null) await _createTheme(name: name);
  }

  Future<void> _showDeleteDialog(Map<String, dynamic> theme) async {
    if (_builtIns.any((item) => item['id'] == theme['id'])) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Built-in themes cannot be deleted')),
      );
      return;
    }
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Delete theme?'),
        content: Text('Delete “${theme['name']}”? This cannot be undone.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogContext, false), child: const Text('Cancel')),
          FilledButton.tonal(onPressed: () => Navigator.pop(dialogContext, true), child: const Text('Delete')),
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

  Future<void> _showRealLaptopPreview() async {
    final shown = await const MethodChannel('app.freedextop/display')
        .invokeMethod<bool>('previewLaptopOverlay') ?? false;
    if (!shown && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Start Dextop first to show the real laptop overlay.')),
      );
    }
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
          IconButton(onPressed: _showCreateDialog, icon: const Icon(Icons.add), tooltip: 'Add custom theme'),
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
                            onLongPress: () => _showDeleteDialog(theme),
                            child: Padding(
                              padding: const EdgeInsets.all(12),
                              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                                Expanded(child: _preview(theme, compact: true, includeTrackpad: false)),
                                const SizedBox(height: 8),
                                Text(theme['name'] as String, maxLines: 1, overflow: TextOverflow.ellipsis),
                                Row(children: [
                                  const Expanded(child: SizedBox()),
                                  IconButton(icon: const Icon(Icons.preview_outlined), tooltip: 'Show real overlay', onPressed: _showRealLaptopPreview),
                                  IconButton(icon: const Icon(Icons.edit_outlined), tooltip: 'Edit', onPressed: () => _select(theme['id'] as String)),
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
                Align(alignment: Alignment.centerLeft, child: Text('Tap to select · long-press to delete', style: Theme.of(context).textTheme.bodySmall)),
                const SizedBox(height: 8),
                const SizedBox(height: 20),
              ],
            ),
    );
  }

  Widget _editor(Map<String, dynamic> theme) => Card(
        child: ListTile(
          title: Text(theme['name'] as String),
          subtitle: const Text('Open the editor to customize colors, image, opacity, blur, and corners.'),
          trailing: FilledButton.icon(onPressed: () => _showEditDialog(theme), icon: const Icon(Icons.edit_outlined), label: const Text('Edit')),
        ),
      );

  Future<void> _showEditDialog(Map<String, dynamic> theme) async {
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(builder: (context, update) => AlertDialog(
        title: Text('Edit ${theme['name']}'),
        content: SingleChildScrollView(child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          Wrap(spacing: 8, runSpacing: 8, children: [
            for (final key in ['background', 'key', 'border', 'text', 'trackpad'])
              ActionChip(label: Text(key), avatar: CircleAvatar(backgroundColor: _color(theme, key)), onPressed: () { _cycleColor(theme, key); update(() {}); }),
          ]),
          _labeledSlider('Opacity', (theme['opacity'] as num).toDouble(), .2, 1, (v) { update(() => theme['opacity'] = v); _save(); }),
          _labeledSlider('Blur', (theme['blur'] as num).toDouble(), 0, 30, (v) { update(() => theme['blur'] = v); _save(); }),
          _labeledSlider('Corner radius', (theme['radius'] as num).toDouble(), 0, 28, (v) { update(() => theme['radius'] = v); _save(); }),
          OutlinedButton.icon(onPressed: () async { await _pickImage(theme); update(() {}); }, icon: const Icon(Icons.image_outlined), label: const Text('Choose background image')),
          OutlinedButton.icon(onPressed: () { Navigator.pop(dialogContext); _showPreview(theme); }, icon: const Icon(Icons.preview_outlined), label: const Text('Preview keyboard overlay')),
        ])),
        actions: [TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('Done'))],
      )),
    );
  }

  Widget _labeledSlider(String label, double value, double min, double max, ValueChanged<double> onChanged) =>
      Row(children: [
        SizedBox(width: 110, child: Text(label)),
        Expanded(child: Slider(value: value.clamp(min, max), min: min, max: max, label: label, onChanged: onChanged)),
      ]);

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

  Widget _preview(Map<String, dynamic> theme, {bool compact = false, bool includeTrackpad = true}) {
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
        Expanded(flex: 66, child: Column(children: [
          for (final row in rows) Expanded(child: Row(children: [
            for (final char in row.split('')) Expanded(child: Padding(
              padding: const EdgeInsets.all(2),
              child: DecoratedBox(decoration: BoxDecoration(color: key, border: Border.all(color: border), borderRadius: BorderRadius.circular(radius / 2)),
                child: Center(child: Text(char, style: TextStyle(color: text, fontSize: compact ? 9 : 12))),
              ),
            )),
          ])),
        ])),
        if (includeTrackpad) const SizedBox(height: 6),
        if (includeTrackpad) Expanded(flex: 34, child: DecoratedBox(
          decoration: BoxDecoration(color: _color(theme, 'trackpad'), border: Border.all(color: border), borderRadius: BorderRadius.circular(radius)),
          child: Center(child: Text('TRACKPAD', style: TextStyle(color: _color(theme, 'trackpadText'), letterSpacing: 2, fontSize: compact ? 8 : 11))),
        )),
      ]),
    );
  }
}
