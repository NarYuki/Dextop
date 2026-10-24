import 'package:flutter/foundation.dart';

import '../color/m3e_color_scheme.dart';
import '../type/m3e_typography.dart';
import 'm3e_theme_data.dart';

/// Assembles a complete [M3EThemeData] from core tokens and component defaults.
M3EThemeData buildM3EThemeDefaults({
  M3EColorScheme? colorScheme,
  M3ETypography? typography,
  M3ETypeScale? typeScale,
  double visualDensity = 0,
  TargetPlatform? platform,
  bool useMaterial3 = true,
}) {
  return M3EThemeData(
    colorScheme: colorScheme,
    typography: typography,
    typeScale: typeScale,
    visualDensity: visualDensity,
    platform: platform,
    useMaterial3: useMaterial3,
  );
}
