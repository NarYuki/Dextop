import 'package:flutter_test/flutter_test.dart';
import 'package:free_dextop/release_version.dart';

void main() {
  test('numeric core decides first', () {
    expect(isNewerRelease('2.1.0', '2.0.9'), isTrue);
    expect(isNewerRelease('2.0.10', '2.0.9'), isTrue);
    expect(isNewerRelease('v2.0.0', '2.0.0'), isFalse);
    expect(isNewerRelease('2.0', '2.0.0'), isFalse);
  });

  test('a release is newer than its pre-releases', () {
    expect(isNewerRelease('2.1.0', '2.1.0-beta.1'), isTrue);
    expect(isNewerRelease('2.1.0-beta.1', '2.1.0'), isFalse);
    expect(isNewerRelease('2.1.0-beta.1', '2.0.0'), isTrue);
  });

  test('pre-releases compare identifier by identifier', () {
    expect(isNewerRelease('2.1.0-beta.10', '2.1.0-beta.2'), isTrue);
    expect(isNewerRelease('2.1.0-rc.1', '2.1.0-beta.10'), isTrue);
    expect(isNewerRelease('2.1.0-beta.1', '2.1.0-beta'), isTrue);
    expect(isNewerRelease('2.1.0-beta.2', '2.1.0-beta.2'), isFalse);
  });

  test('build metadata is ignored', () {
    expect(compareReleaseVersions('2.1.0+42', '2.1.0+7'), 0);
    expect(isNewerRelease('2.1.1', '2.1.0+99'), isTrue);
  });

  test('recognizes pre-releases', () {
    expect(isPreRelease('v2.1.0-beta.1'), isTrue);
    expect(isPreRelease('2.1.0'), isFalse);
    expect(isPreRelease('2.1.0+5'), isFalse);
  });
}
