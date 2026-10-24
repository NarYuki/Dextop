import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../styles/m3e_slider_theme.dart';

/// Floating value label shown while a slider handle is pressed or focused.
class M3ESliderValueIndicator extends StatelessWidget {
  /// M3ESliderValueIndicator.
  const M3ESliderValueIndicator({
    required this.label,
    required this.colors,
    super.key,
  });

  /// label.

  final String label;

  /// colors.
  final M3ESliderColors colors;

  @override
  Widget build(BuildContext context) {
    final sliderTheme = M3ETheme.of(context).sliderTheme;
    final style = M3ETheme.of(context).typeScale.labelLarge.copyWith(
      color: colors.valueIndicatorLabel,
      fontSize: sliderTheme.valueIndicatorFontSize,
      height:
          sliderTheme.valueIndicatorLineHeight /
          sliderTheme.valueIndicatorFontSize,
      letterSpacing: sliderTheme.valueIndicatorLetterSpacing,
      fontWeight: sliderTheme.valueIndicatorFontWeight,
    );

    return SizedBox(
      width: sliderTheme.valueIndicatorWidth,
      height: sliderTheme.valueIndicatorHeight,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: colors.valueIndicator,
          borderRadius: BorderRadius.circular(sliderTheme.valueIndicatorRadius),
        ),
        child: Center(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: style,
            ),
          ),
        ),
      ),
    );
  }
}
