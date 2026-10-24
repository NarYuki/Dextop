import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../enums/m3e_bottom_sheet_enums.dart';
import '../styles/m3e_bottom_sheet_theme.dart';

/// Bottom sheet container: color, tint, elevation and top rounding.
class M3EBottomSheetSurface extends StatelessWidget {
  /// M3EBottomSheetSurface.
  const M3EBottomSheetSurface({
    required this.theme,
    required this.variant,
    required this.topRadius,
    required this.child,
    super.key,
  });

  /// Resolved sheet theme.
  final M3EBottomSheetTheme theme;

  /// Picks the elevation.
  final M3EBottomSheetVariant variant;

  /// Current top corner radius (springs to 0 at full screen).
  final double topRadius;

  /// Sheet content.
  final Widget child;

  /// Tint opacity for M3 elevation levels.
  static double tintOpacity(double elevation) {
    if (elevation <= 0) {
      return 0;
    }
    if (elevation <= M3EElevation.level1) {
      return 0.05;
    }
    if (elevation <= M3EElevation.level2) {
      return 0.08;
    }
    if (elevation <= M3EElevation.level3) {
      return 0.11;
    }
    return elevation <= M3EElevation.level4 ? 0.12 : 0.14;
  }

  @override
  Widget build(BuildContext context) {
    final M3EColorScheme scheme = M3ETheme.of(context).colorScheme;
    final double elevation = theme.elevationFor(variant);
    Color color = theme.containerColor(scheme);
    final Color? tint = theme.colors.surfaceTintColor;
    if (tint != null) {
      color = Color.alphaBlend(
        tint.withValues(alpha: tintOpacity(elevation)),
        color,
      );
    }
    final radius = BorderRadius.vertical(
      top: Radius.circular(topRadius),
      bottom: Radius.circular(theme.bottomCornerRadius),
    );
    return DecoratedBox(
      decoration: BoxDecoration(
        color: color,
        borderRadius: radius,
        boxShadow: M3EElevation.shadows(elevation, shadowColor: scheme.shadow),
      ),
      child: ClipRRect(borderRadius: radius, child: child),
    );
  }
}
