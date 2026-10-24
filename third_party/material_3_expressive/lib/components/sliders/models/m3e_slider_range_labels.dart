import 'package:flutter/foundation.dart';
import 'package:material_3_expressive/components/sliders/m3e_range_slider.dart'
    show M3ERangeSlider;
import 'package:material_3_expressive/components/sliders/m3e_sliders.dart'
    show M3ERangeSlider;
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3ERangeSlider;

/// Optional labels for the start and end thumbs of [M3ERangeSlider].
@immutable
class M3ESliderRangeLabels {
  /// M3ESliderRangeLabels.
  const M3ESliderRangeLabels(this.start, this.end);

  /// start.

  final String start;

  /// end.
  final String end;
}
