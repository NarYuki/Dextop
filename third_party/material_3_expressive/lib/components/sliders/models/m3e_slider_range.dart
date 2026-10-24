import 'package:flutter/foundation.dart';
import 'package:material_3_expressive/components/sliders/m3e_range_slider.dart'
    show M3ERangeSlider;
import 'package:material_3_expressive/components/sliders/m3e_sliders.dart'
    show M3ERangeSlider;
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3ERangeSlider;

/// A continuous range with [start] ≤ [end], used by [M3ERangeSlider].
@immutable
class M3ESliderRange {
  /// M3ESliderRange.
  const M3ESliderRange(this.start, this.end)
    : assert(start <= end, 'start must be ≤ end');

  /// start.

  final double start;

  /// end.
  final double end;

  /// copyWith.

  M3ESliderRange copyWith({double? start, double? end}) {
    return M3ESliderRange(start ?? this.start, end ?? this.end);
  }

  @override
  bool operator ==(Object other) {
    return other is M3ESliderRange && other.start == start && other.end == end;
  }

  @override
  int get hashCode => Object.hash(start, end);

  @override
  String toString() => 'M3ESliderRange($start, $end)';
}
