import 'package:flutter/widgets.dart';

import 'm3e_type_style_tokens.dart';
import 'm3e_variable_font_axes.dart';

/// Configuration for per-role variable-font axis resolution.
///
/// When set on theme `copyWith` or the material app shell, axes are computed
/// independently for each type scale role.
///
/// Merge order for each role:
/// 1. Auto `wght` / `opsz` from [TextStyle] when enabled and not explicitly set
/// 2. [global] axis overrides
/// 3. [brand] or [body] group overrides (by role group)
/// 4. Legacy [rond] / [bodyRond] when `ROND` is still unset
/// 5. [emphasizedGrad] on emphasized scales when `GRAD` is unset
/// 6. [extraVariations] last (wins on tag conflicts)
@immutable
class M3EVariableFontConfig {
  /// Creates a variable-font configuration.
  const M3EVariableFontConfig({
    this.enableOpsz = true,
    this.syncWghtToWeight = true,
    this.rond = 0,
    this.bodyRond = 0,
    this.emphasizedGrad = 50,
    this.extraVariations = const <FontVariation>[],
    this.global,
    this.brand,
    this.body,
  });

  /// Maps each role's `fontSize` to the `opsz` axis when not explicitly set.
  final bool enableOpsz;

  /// Maps each role's `fontWeight` to the `wght` axis when not explicitly set.
  final bool syncWghtToWeight;

  /// Corner roundness (`ROND`) for display, headline, and title roles.
  final double rond;

  /// Corner roundness (`ROND`) for body and label roles.
  final double bodyRond;

  /// Grade bump (`GRAD`) applied on the emphasized scale only.
  final double emphasizedGrad;

  /// Additional axes merged last (user values win on tag conflicts).
  final List<FontVariation> extraVariations;

  /// Global axis overrides applied before group overrides.
  final M3EVariableFontAxes? global;

  /// Overrides for display, headline, and title roles.
  final M3EVariableFontAxes? brand;

  /// Overrides for body and label roles.
  final M3EVariableFontAxes? body;

  /// Returns a copy with the given fields replaced.
  M3EVariableFontConfig copyWith({
    bool? enableOpsz,
    bool? syncWghtToWeight,
    double? rond,
    double? bodyRond,
    double? emphasizedGrad,
    List<FontVariation>? extraVariations,
    M3EVariableFontAxes? global,
    M3EVariableFontAxes? brand,
    M3EVariableFontAxes? body,
  }) {
    return M3EVariableFontConfig(
      enableOpsz: enableOpsz ?? this.enableOpsz,
      syncWghtToWeight: syncWghtToWeight ?? this.syncWghtToWeight,
      rond: rond ?? this.rond,
      bodyRond: bodyRond ?? this.bodyRond,
      emphasizedGrad: emphasizedGrad ?? this.emphasizedGrad,
      extraVariations: extraVariations ?? this.extraVariations,
      global: global ?? this.global,
      brand: brand ?? this.brand,
      body: body ?? this.body,
    );
  }

  /// Whether any layer sets `wght` explicitly for [role].
  bool _hasExplicitWght(M3ETypeRole role) {
    if (global?.wght != null) {
      return true;
    }
    final M3EVariableFontAxes? group = role.isBrandRole ? brand : body;
    return group?.wght != null;
  }

  /// Whether any layer sets `opsz` explicitly for [role].
  bool _hasExplicitOpsz(M3ETypeRole role) {
    if (global?.opsz != null) {
      return true;
    }
    final M3EVariableFontAxes? group = role.isBrandRole ? brand : body;
    return group?.opsz != null;
  }

  /// Resolves variable-font axes for one [role] and resolved [style].
  List<FontVariation> resolveForRole(
    M3ETypeRole role,
    TextStyle style, {
    required bool isEmphasizedScale,
  }) {
    final axes = <String, double>{};

    if (syncWghtToWeight &&
        !_hasExplicitWght(role) &&
        style.fontWeight != null) {
      axes['wght'] = style.fontWeight!.value.toDouble();
    }
    if (enableOpsz && !_hasExplicitOpsz(role) && style.fontSize != null) {
      axes['opsz'] = style.fontSize!;
    }

    _applyAxes(axes, global);
    _applyAxes(axes, role.isBrandRole ? brand : body);

    if (!axes.containsKey('ROND')) {
      final double roundness = role.isBrandRole ? rond : bodyRond;
      if (roundness != 0) {
        axes['ROND'] = roundness;
      }
    }
    if (isEmphasizedScale && emphasizedGrad != 0 && !axes.containsKey('GRAD')) {
      axes['GRAD'] = emphasizedGrad;
    }

    for (final FontVariation variation in extraVariations) {
      axes[variation.axis] = variation.value;
    }

    return axes.entries
        .map((MapEntry<String, double> e) => FontVariation(e.key, e.value))
        .toList();
  }

  static void _applyAxes(
    Map<String, double> target,
    M3EVariableFontAxes? source,
  ) {
    if (source == null) {
      return;
    }
    for (final FontVariation variation in source.toVariations()) {
      target[variation.axis] = variation.value;
    }
  }
}
