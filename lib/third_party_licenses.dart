import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Native and Android components bundled in the APK. Flutter's license page
/// lists only Dart packages, so their notices are registered here from the
/// license files kept under third_party/ (declared as assets in pubspec.yaml).
const _source = 'third_party/licenses/SOURCE.txt';
const _apache = 'third_party/licenses/Apache-2.0.txt';

const List<(List<String>, List<String>)> thirdPartyLicenses = [
  (['Dextop'], ['LICENSE', _source]),
  (['PRoot (with DroidDeck patches)'], ['third_party/proot/COPYING', _source]),
  (['talloc'], ['third_party/talloc/COPYING']),
  (['virglrenderer'], ['third_party/virglrenderer/COPYING']),
  (['libepoxy'], ['third_party/libepoxy/COPYING']),
  (
    ['Wayland', 'wayland-protocols'],
    ['third_party/wayland/COPYING', 'third_party/wayland-protocols/COPYING'],
  ),
  (['libffi'], ['third_party/libffi/LICENSE']),
  (
    ['libadrenotools', 'linkernsbypass'],
    [
      'third_party/adrenotools/LICENSE',
      'third_party/adrenotools/lib/linkernsbypass/LICENSE',
    ],
  ),
  (
    ['DD-Turnip', 'Mesa'],
    [
      'third_party/dd-turnip/NOTICE',
      'third_party/dd-turnip/LICENSES/mesa/license.rst',
      'third_party/dd-turnip/LICENSES/mesa/MIT',
      'third_party/dd-turnip/LICENSES/GPL-3.0.txt',
    ],
  ),
  (
    ['Deck direct presentation (DD-Turnip 0.3.3, Banners-Turnip)'],
    ['third_party/deck-direct/NOTICE.md'],
  ),
  (
    ['PulseAudio', 'libsndfile', 'libltdl', 'DroidDeck DirectAudio'],
    [
      'third_party/deck-audio/NOTICE.md',
      'third_party/deck-audio/PULSEAUDIO-LICENSE',
      'third_party/deck-audio/COPYING',
      'third_party/deck-audio/LIBFFI-LICENSE',
    ],
  ),
  (
    ['gamescope (DroidDeck)'],
    [
      'third_party/deck-gamescope/NOTICE.md',
      'third_party/deck-gamescope/GAMESCOPE-LICENSE',
    ],
  ),
  (
    ['DroidDeck session preload and fast path'],
    ['third_party/droiddeck-fastpath/LICENSE', _source],
  ),
  (['Zstandard'], ['third_party/zstd/LICENSE']),
  (['Vulkan-Headers'], ['third_party/vulkan-headers/LICENSE.md']),
  (['Material Symbols'], [_apache]),
  (
    [
      'AndroidX',
      'Jetpack Compose Material 3',
      'Media3',
      'Shizuku API',
      'Apache Commons Codec',
      'Apache Commons Compress',
      'AndroidSVG',
      'HiddenApiBypass',
      'Kadb',
      'apksig',
      'SnakeYAML Engine',
    ],
    [_apache],
  ),
  (
    ['usb-serial-for-android'],
    ['third_party/licenses/MIT-usb-serial-for-android.txt'],
  ),
  (['XZ for Java'], ['third_party/licenses/0BSD-xz-java.txt']),
  (
    ['Swipe typing lexicons'],
    ['android/app/src/main/res/raw/swipe_lexicons_notice.txt'],
  ),
  (
    ['Terminal (xterm.js, fonts)'],
    [
      'android/app/src/main/assets/terminal/LICENSE',
      'android/app/src/main/assets/terminal/LICENSE-DextopTermMono',
      'android/app/src/main/assets/terminal/LICENSE-SymbolsNerdFont',
    ],
  ),
];

void registerThirdPartyLicenses() {
  LicenseRegistry.addLicense(() async* {
    for (final (packages, files) in thirdPartyLicenses) {
      final texts = <String>[];
      for (final file in files) {
        texts.add(await rootBundle.loadString(file));
      }
      yield LicenseEntryWithLineBreaks(packages, texts.join('\n\n'));
    }
  });
}
