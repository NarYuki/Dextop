import 'package:flutter/widgets.dart';

/// Explicit OpenType axis values for variable fonts (Roboto Flex, Google Sans Flex).
///
/// Each field is optional; null means the axis is not set at this layer and
/// Resolution falls through to auto-sync or the next layer. See
/// `M3EVariableFontConfig.resolveForRole` for merge order.
@immutable
class M3EVariableFontAxes {
  /// Creates a bag of variable-font axis values.
  const M3EVariableFontAxes({
    this.wght,
    this.opsz,
    this.rond,
    this.wdth,
    this.slnt,
    this.grad,
    this.ytas,
    this.ytde,
    this.ytlc,
    this.ytuc,
  });

  /// Weight axis (`wght`). Typical range 100–1000; 400 regular, 500 medium, 700 bold.
  final double? wght;

  /// Optical size (`opsz`). Usually matches the role's `fontSize` in logical px.
  final double? opsz;

  /// Corner roundness (`ROND`). Expressive axis, typically 0–100.
  final double? rond;

  /// Width (`wdth`). 100 is normal; condensed ~50–75, wide ~125–151.
  final double? wdth;

  /// Slant (`slnt`).
  final double? slnt;

  /// Grade (`GRAD`). Adjusts stroke weight without changing layout width.
  final double? grad;

  /// Y-axis ascender height (`ytas`). Advanced alignment tuning.
  final double? ytas;

  /// Y-axis descender depth (`ytde`). Advanced alignment tuning.
  final double? ytde;

  /// Y-axis lowercase height (`ytlc`). Advanced alignment tuning.
  final double? ytlc;

  /// Y-axis uppercase height (`ytuc`). Advanced alignment tuning.
  final double? ytuc;

  /// Returns a copy with the given fields replaced.
  M3EVariableFontAxes copyWith({
    double? wght,
    double? opsz,
    double? rond,
    double? wdth,
    double? slnt,
    double? grad,
    double? ytas,
    double? ytde,
    double? ytlc,
    double? ytuc,
  }) {
    return M3EVariableFontAxes(
      wght: wght ?? this.wght,
      opsz: opsz ?? this.opsz,
      rond: rond ?? this.rond,
      wdth: wdth ?? this.wdth,
      slnt: slnt ?? this.slnt,
      grad: grad ?? this.grad,
      ytas: ytas ?? this.ytas,
      ytde: ytde ?? this.ytde,
      ytlc: ytlc ?? this.ytlc,
      ytuc: ytuc ?? this.ytuc,
    );
  }

  /// Emits [FontVariation] entries for every non-null axis.
  List<FontVariation> toVariations() {
    final variations = <FontVariation>[];
    if (wght != null) {
      variations.add(FontVariation('wght', wght!));
    }
    if (opsz != null) {
      variations.add(FontVariation('opsz', opsz!));
    }
    if (rond != null) {
      variations.add(FontVariation('ROND', rond!));
    }
    if (wdth != null) {
      variations.add(FontVariation('wdth', wdth!));
    }
    if (slnt != null) {
      variations.add(FontVariation('slnt', slnt!));
    }
    if (grad != null) {
      variations.add(FontVariation('GRAD', grad!));
    }
    if (ytas != null) {
      variations.add(FontVariation('ytas', ytas!));
    }
    if (ytde != null) {
      variations.add(FontVariation('ytde', ytde!));
    }
    if (ytlc != null) {
      variations.add(FontVariation('ytlc', ytlc!));
    }
    if (ytuc != null) {
      variations.add(FontVariation('ytuc', ytuc!));
    }
    return variations;
  }

  /// Merges non-null axes from [other] onto this bag (other wins on conflict).
  M3EVariableFontAxes merge(M3EVariableFontAxes? other) {
    if (other == null) {
      return this;
    }
    return copyWith(
      wght: other.wght ?? wght,
      opsz: other.opsz ?? opsz,
      rond: other.rond ?? rond,
      wdth: other.wdth ?? wdth,
      slnt: other.slnt ?? slnt,
      grad: other.grad ?? grad,
      ytas: other.ytas ?? ytas,
      ytde: other.ytde ?? ytde,
      ytlc: other.ytlc ?? ytlc,
      ytuc: other.ytuc ?? ytuc,
    );
  }
}
