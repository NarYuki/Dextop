import 'package:flutter/widgets.dart';
import 'package:material_3_expressive/components/sliders/m3e_sliders.dart'
    show M3ERangeSlider, M3ESlider;
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3ERangeSlider, M3ESlider;

/// Builds a custom stop/tick marker for [M3ESlider] / [M3ERangeSlider].
///
/// [color] is the resolved stop/tick color. [size] is the default marker size.
/// [active] is true when the marker sits on the active track segment.
typedef M3ESliderDotBuilder = Widget Function({
  required BuildContext context,
  required Color color,
  required double size,
  required bool active,
});
