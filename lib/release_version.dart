/// Compares Dextop release versions such as `2.1.0`, `v2.1.0-beta.2` or `2.1.0+42`.
///
/// Follows semantic versioning precedence: the numeric core decides first; a
/// release is newer than any of its pre-releases (`2.1.0` > `2.1.0-beta.3`);
/// pre-release identifiers compare one by one, numbers numerically and before
/// words (`beta.2` < `beta.10` < `rc.1`). Build metadata after `+` is ignored.
int compareReleaseVersions(String a, String b) {
  final left = _ReleaseVersion.parse(a);
  final right = _ReleaseVersion.parse(b);
  final length = left.core.length > right.core.length ? left.core.length : right.core.length;
  for (var i = 0; i < length; i++) {
    final x = i < left.core.length ? left.core[i] : 0;
    final y = i < right.core.length ? right.core[i] : 0;
    if (x != y) return x.compareTo(y);
  }
  if (left.pre.isEmpty || right.pre.isEmpty) {
    if (left.pre.isEmpty && right.pre.isEmpty) return 0;
    return left.pre.isEmpty ? 1 : -1;
  }
  for (var i = 0; i < left.pre.length && i < right.pre.length; i++) {
    final x = left.pre[i];
    final y = right.pre[i];
    final xn = int.tryParse(x);
    final yn = int.tryParse(y);
    final order = xn != null && yn != null
        ? xn.compareTo(yn)
        : xn != null
        ? -1
        : yn != null
        ? 1
        : x.compareTo(y);
    if (order != 0) return order;
  }
  return left.pre.length.compareTo(right.pre.length);
}

/// Whether [candidate] is a newer release than [current].
bool isNewerRelease(String candidate, String current) =>
    compareReleaseVersions(candidate, current) > 0;

/// Whether [version] is a pre-release (`2.1.0-beta.1`).
bool isPreRelease(String version) => _ReleaseVersion.parse(version).pre.isNotEmpty;

class _ReleaseVersion {
  _ReleaseVersion(this.core, this.pre);

  final List<int> core;
  final List<String> pre;

  static _ReleaseVersion parse(String value) {
    var text = value.trim().replaceFirst(RegExp(r'^[vV]'), '');
    text = text.split('+').first;
    final dash = text.indexOf('-');
    final coreText = dash < 0 ? text : text.substring(0, dash);
    final preText = dash < 0 ? '' : text.substring(dash + 1);
    final core = coreText
        .split('.')
        .map((part) => int.tryParse(RegExp(r'\d+').stringMatch(part) ?? '0') ?? 0)
        .toList();
    final pre = preText.isEmpty ? <String>[] : preText.split('.');
    return _ReleaseVersion(core, pre);
  }
}
