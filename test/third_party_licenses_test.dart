import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:free_dextop/third_party_licenses.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('every bundled component license is an asset and registers', () async {
    registerThirdPartyLicenses();
    final entries = await LicenseRegistry.licenses.toList();
    final packages = entries.expand((entry) => entry.packages).toSet();
    for (final (names, _) in thirdPartyLicenses) {
      expect(packages, containsAll(names));
    }
    final proot = entries.firstWhere(
      (entry) => entry.packages.contains('PRoot (with DroidDeck patches)'),
    );
    final text = proot.paragraphs.map((p) => p.text).join('\n');
    expect(text, contains('GNU GENERAL PUBLIC LICENSE'));
    expect(text, contains('github.com/NarYuki/Dextop'));
  });
}
