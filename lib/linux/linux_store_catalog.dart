import 'dart:convert';
import 'dart:io';
import 'dart:isolate';

import 'package:archive/archive.dart';

/// One installable desktop application from a distribution's AppStream data.
class StoreApp {
  const StoreApp({
    required this.id,
    required this.package,
    required this.name,
    required this.summary,
    required this.description,
    required this.categories,
    required this.icon,
    required this.desktopIds,
    this.source = 'apt',
    this.architecture = 'ARM64',
  });

  factory StoreApp.fromJson(Map<String, Object?> json) => StoreApp(
    id: json['id'] as String,
    package: json['package'] as String,
    name: json['name'] as String,
    summary: json['summary'] as String? ?? '',
    description: json['description'] as String? ?? '',
    categories: (json['categories'] as List?)?.cast<String>() ?? const [],
    icon: json['icon'] as String? ?? '',
    desktopIds: (json['desktopIds'] as List?)?.cast<String>() ?? const [],
    source: json['source'] as String? ?? 'apt',
    architecture: json['architecture'] as String? ?? 'ARM64',
  );

  /// AppStream component ID.
  final String id;

  /// The Debian/Ubuntu package that installs it.
  final String package;
  final String source;
  final String architecture;

  String label(String distro) =>
      '$architecture / ${distro == 'ubuntu' ? 'Ubuntu' : 'Debian'} / ${switch (source) {
        'flathub' => 'Flathub',
        'deb-get' => 'deb-get',
        _ => 'APT',
      }}';
  final String name;
  final String summary;

  /// Plain text, paragraphs separated by blank lines.
  final String description;

  /// freedesktop.org categories.
  final List<String> categories;

  /// File name of its 128-pixel icon in the distribution's icon archive, or empty.
  final String icon;

  /// Desktop entries the package installs, to open it once installed.
  final List<String> desktopIds;

  Map<String, Object?> toJson() => {
    'id': id,
    'package': package,
    'name': name,
    'summary': summary,
    'description': description,
    'categories': categories,
    'icon': icon,
    'desktopIds': desktopIds,
    'source': source,
    'architecture': architecture,
  };

  /// Whether [desktopId] (a Linux app list entry) belongs to this application.
  bool opens(String desktopId) {
    final bare = desktopId.replaceAll(RegExp(r'\.desktop$'), '');
    return desktopIds.any(
          (id) =>
              id == desktopId ||
              id.replaceAll(RegExp(r'\.desktop$'), '') == bare,
        ) ||
        id.replaceAll(RegExp(r'\.desktop$'), '') == bare;
  }
}

/// The official AppStream (DEP-11) metadata a container's distribution publishes.
class StoreSource {
  const StoreSource(this.key, this.label, this.urls);

  final String key;

  /// Shown under each application, e.g. "ARM64 / Debian".
  final String label;
  final List<String> urls;

  static StoreSource forDistro(String distro) => switch (distro) {
    'ubuntu' => const StoreSource('ubuntu-noble', 'ARM64 / Ubuntu', [
      'https://ports.ubuntu.com/ubuntu-ports/dists/noble/main/dep11/Components-arm64.yml.gz',
      'https://ports.ubuntu.com/ubuntu-ports/dists/noble/universe/dep11/Components-arm64.yml.gz',
    ]),
    'debian' => const StoreSource('debian-trixie', 'ARM64 / Debian', [
      'https://deb.debian.org/debian/dists/trixie/main/dep11/Components-arm64.yml.gz',
      'https://deb.debian.org/debian/dists/trixie/contrib/dep11/Components-arm64.yml.gz',
    ]),
    _ => throw UnsupportedError('Store supports Debian and Ubuntu only'),
  };
}

/// Packages shown first under "Recommended", where the distribution has them.
/// Ubuntu's Firefox and Chromium packages only install snaps, which containers cannot run.
const storeRecommended = [
  'firefox-esr',
  'chromium',
  'libreoffice-writer',
  'gimp',
  'inkscape',
  'vlc',
  'thunderbird',
  'krita',
  'audacity',
  'kdenlive',
  'obs-studio',
  'blender',
  'geany',
  'mousepad',
  'gnome-calculator',
  'evince',
  'filezilla',
  'remmina',
  'darktable',
  'freecad',
];

/// The main freedesktop.org categories, in the order the Store lists them.
const storeCategories = [
  'Office',
  'Graphics',
  'AudioVideo',
  'Network',
  'Development',
  'Education',
  'Game',
  'Science',
  'Utility',
  'System',
];

class StoreCatalog {
  StoreCatalog._();

  static final _memory = <String, List<StoreApp>>{};
  static final _savedAt = <String, DateTime>{};
  static const _maxAge = Duration(days: 3);

  /// The applications of [source], from memory, the disk cache or the network.
  static Future<List<StoreApp>> load(
    StoreSource source, {
    String language = 'en',
    bool refresh = false,
  }) async {
    final key = '${source.key}-$language';
    if (!refresh &&
        _memory[key] != null &&
        _savedAt[key] != null &&
        DateTime.now().difference(_savedAt[key]!) < _maxAge) {
      return _memory[key]!;
    }
    final cache = File('${Directory.systemTemp.path}/linux_store_$key.json');
    if (!refresh && await cache.exists()) {
      final age = DateTime.now().difference(await cache.lastModified());
      if (age < _maxAge) {
        try {
          final apps = await Isolate.run(
            () => _decode(cache.readAsStringSync()),
          );
          _savedAt[key] = await cache.lastModified();
          return _memory[key] = apps;
        } catch (_) {}
      }
    }
    final client = HttpClient()
      ..connectionTimeout = const Duration(seconds: 20);
    final documents = <List<int>>[];
    try {
      for (final url in source.urls) {
        final request = await client.getUrl(Uri.parse(url));
        final response = await request.close();
        if (response.statusCode != 200) {
          await response.drain<void>();
          continue;
        }
        final bytes = <int>[];
        await for (final chunk in response) {
          bytes.addAll(chunk);
        }
        documents.add(bytes);
      }
    } catch (_) {
      if (await cache.exists()) {
        final apps = await Isolate.run(() => _decode(cache.readAsStringSync()));
        _savedAt[key] = await cache.lastModified();
        return _memory[key] = apps;
      }
      rethrow;
    } finally {
      client.close();
    }
    if (documents.isEmpty) {
      if (await cache.exists()) {
        _savedAt[key] = await cache.lastModified();
        return _memory[key] = await Isolate.run(
          () => _decode(cache.readAsStringSync()),
        );
      }
      throw const HttpException(
        'The application catalog could not be downloaded',
      );
    }
    final apps = await Isolate.run(() => _parseAll(documents, language));
    await _fetchIcons(source, refresh: true);
    await cache.writeAsString(
      jsonEncode([for (final app in apps) app.toJson()]),
    );
    _savedAt[key] = await cache.lastModified();
    return _memory[key] = apps;
  }

  /// Where the icons of [source] are unpacked.
  static Directory iconDirectory(StoreSource source) =>
      Directory('${Directory.systemTemp.path}/linux_store_icons/${source.key}');

  /// Icon sizes the distributions publish; many applications only have 64 or 48.
  static const _iconSizes = ['128x128', '64x64', '48x48'];

  static File? iconFile(StoreSource source, StoreApp app) {
    if (app.icon.isEmpty) return null;
    final root = iconDirectory(source).path;
    for (final size in _iconSizes) {
      final file = File('$root/$size/${app.icon}');
      if (file.existsSync()) return file;
    }
    return null;
  }

  /// Unpacks the icon archives published next to each component file, largest first.
  /// Sizes already unpacked are kept unless [refresh].
  static Future<void> _fetchIcons(
    StoreSource source, {
    bool refresh = false,
  }) async {
    final root = iconDirectory(source).path;
    final client = HttpClient()
      ..connectionTimeout = const Duration(seconds: 20);
    try {
      for (final size in _iconSizes) {
        final path = '$root/$size';
        if (!refresh && Directory(path).existsSync()) continue;
        for (final url in source.urls) {
          final request = await client.getUrl(
            Uri.parse(
              url.replaceFirst(
                RegExp(r'Components-[^/]+$'),
                'icons-$size.tar.gz',
              ),
            ),
          );
          final response = await request.close();
          if (response.statusCode != 200) {
            await response.drain<void>();
            continue;
          }
          final bytes = <int>[];
          await for (final chunk in response) {
            bytes.addAll(chunk);
          }
          await Isolate.run(() {
            Directory(path).createSync(recursive: true);
            final archive = TarDecoder().decodeBytes(
              GZipDecoder().decodeBytes(bytes),
            );
            for (final file in archive.files) {
              if (!file.isFile || !file.name.endsWith('.png')) continue;
              File('$path/${file.name.split('/').last}')
                  .writeAsBytesSync(file.content as List<int>);
            }
          });
        }
      }
    } catch (_) {
      // The Store still works without icons.
    } finally {
      client.close();
    }
  }

  static List<StoreApp> _decode(String json) => [
    for (final item in jsonDecode(json) as List)
      StoreApp.fromJson((item as Map).cast<String, Object?>()),
  ];

  static List<StoreApp> _parseAll(List<List<int>> documents, String language) {
    final byPackage = <String, StoreApp>{};
    for (final bytes in documents) {
      final text = utf8.decode(gzip.decode(bytes), allowMalformed: true);
      for (final app in _parse(text, language)) {
        byPackage.putIfAbsent('${app.package}/${app.id}', () => app);
      }
    }
    return byPackage.values.toList()
      ..sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
  }

  /// Parses DEP-11 text; for tests.
  static List<StoreApp> parseText(String text, String language) =>
      _parse(text, language);

  /// Reads the fields the Store needs from a DEP-11 YAML stream.
  static List<StoreApp> _parse(String text, String language) {
    final apps = <StoreApp>[];
    var lines = <String>[];

    void flush() {
      if (lines.isEmpty) return;
      final doc = _Doc(lines);
      lines = [];
      if (doc.scalar('Type') != 'desktop-application') return;
      final package = doc.scalar('Package');
      final id = doc.scalar('ID');
      final name = doc.localized('Name', language);
      if (package == null || id == null || name == null) return;
      final icon = doc.cachedIcon();
      apps.add(
        StoreApp(
          id: id,
          package: package,
          name: name,
          summary: doc.localized('Summary', language) ?? '',
          description: _plain(doc.localized('Description', language) ?? ''),
          categories: doc.list('Categories'),
          icon: icon ?? '',
          desktopIds: doc.nestedList('Launchable', 'desktop-id'),
        ),
      );
    }

    for (final line in const LineSplitter().convert(text)) {
      if (line == '---') {
        flush();
      } else {
        lines.add(line);
      }
    }
    flush();
    return apps;
  }

  /// AppStream descriptions are small HTML fragments.
  static String _plain(String html) => html
      .replaceAll(RegExp(r'</p>\s*'), '\n\n')
      .replaceAll(RegExp(r'<li>'), '• ')
      .replaceAll(RegExp(r'</li>\s*'), '\n')
      .replaceAll(RegExp(r'<[^>]+>'), '')
      .replaceAll('&apos;', "'")
      .replaceAll('&quot;', '"')
      .replaceAll('&lt;', '<')
      .replaceAll('&gt;', '>')
      .replaceAll('&amp;', '&')
      .replaceAll(RegExp(r'[ \t]+'), ' ')
      .replaceAll(RegExp(r'\n{3,}'), '\n\n')
      .trim();
}

/// One YAML document of a DEP-11 file, read just far enough for the Store.
class _Doc {
  _Doc(this.lines);

  final List<String> lines;

  int _find(String key) => lines.indexWhere((line) => line.startsWith('$key:'));

  static String _unquote(String value) {
    final v = value.trim();
    if (v.length >= 2 && v.startsWith("'") && v.endsWith("'")) {
      return v.substring(1, v.length - 1).replaceAll("''", "'");
    }
    if (v.length >= 2 && v.startsWith('"') && v.endsWith('"')) {
      return v.substring(1, v.length - 1).replaceAll(r'\"', '"');
    }
    return v;
  }

  String? scalar(String key) {
    final index = _find(key);
    if (index < 0) return null;
    final value = lines[index].substring(key.length + 1).trim();
    return value.isEmpty ? null : _unquote(value);
  }

  /// A `Key:` map of language codes; [language] wins over the untranslated `C`.
  String? localized(String key, String language) {
    final index = _find(key);
    if (index < 0) return null;
    final values = <String, String>{};
    var i = index + 1;
    while (i < lines.length &&
        lines[i].startsWith('  ') &&
        !lines[i].startsWith('   ')) {
      final entry = lines[i].substring(2);
      final colon = entry.indexOf(':');
      if (colon < 0) {
        i++;
        continue;
      }
      final lang = entry.substring(0, colon);
      var value = entry.substring(colon + 1).trim();
      i++;
      if (value == '>-' || value == '>' || value == '|-' || value == '|') {
        final block = StringBuffer();
        while (i < lines.length &&
            (lines[i].startsWith('    ') || lines[i].trim().isEmpty)) {
          final text = lines[i].trim();
          block.write(
            text.isEmpty
                ? '\n'
                : (block.isEmpty || block.toString().endsWith('\n')
                      ? text
                      : ' $text'),
          );
          i++;
        }
        value = block.toString();
      } else {
        value = _unquote(value);
      }
      values[lang] = value;
    }
    return values[language] ??
        values[language.split('_').first] ??
        values['C'] ??
        values['en'];
  }

  List<String> list(String key) {
    final index = _find(key);
    if (index < 0) return const [];
    final result = <String>[];
    for (
      var i = index + 1;
      i < lines.length && lines[i].startsWith('- ');
      i++
    ) {
      result.add(_unquote(lines[i].substring(2)));
    }
    return result;
  }

  /// `Key:` → `  name:` → `  - value` entries.
  List<String> nestedList(String key, String name) {
    final index = _find(key);
    if (index < 0) return const [];
    final result = <String>[];
    var inside = false;
    for (
      var i = index + 1;
      i < lines.length && lines[i].startsWith('  ');
      i++
    ) {
      final line = lines[i];
      if (line.startsWith('  $name:')) {
        inside = true;
      } else if (inside && line.startsWith('  - ')) {
        result.add(_unquote(line.substring(4)));
      } else if (!line.startsWith('   ')) {
        inside = false;
      }
    }
    return result;
  }

  /// The 128-pixel cached icon's file name, or the largest one listed.
  String? cachedIcon() {
    final index = _find('Icon');
    if (index < 0) return null;
    String? best;
    var bestWidth = -1;
    String? name;
    var inCached = false;
    for (
      var i = index + 1;
      i < lines.length && lines[i].startsWith('  ');
      i++
    ) {
      final line = lines[i];
      final trimmed = line.trim();
      if (line.startsWith('  cached:')) {
        inCached = true;
      } else if (!line.startsWith('   ') && !line.startsWith('  -')) {
        inCached = false;
      } else if (inCached && trimmed.startsWith('- name:')) {
        name = _unquote(trimmed.substring(7));
      } else if (inCached && trimmed.startsWith('width:') && name != null) {
        final width = int.tryParse(trimmed.substring(6).trim()) ?? 0;
        if (width == 128 || (bestWidth != 128 && width > bestWidth)) {
          best = name;
          bestWidth = width;
        }
      }
    }
    return best ?? name;
  }
}
