import 'package:material_ui/material_ui.dart';

import '../../../foundations/foundations.dart';
import '../styles/m3e_time_picker_theme.dart';

/// AM/PM toggle matching Material day-period control.
///
/// Portrait and input: AM stacked over PM. Landscape dial: AM | PM in a row.
class M3EDayPeriodControl extends StatelessWidget {
  /// M3EDayPeriodControl.
  const M3EDayPeriodControl({
    required this.isPm,
    required this.onChanged,
    this.orientation = Orientation.portrait,
    this.forInput = false,
    super.key,
  });

  /// Whether PM is selected.
  final bool isPm;

  /// Called with `true` for PM, `false` for AM.
  final ValueChanged<bool> onChanged;

  /// Dial layout orientation. Input mode always uses portrait stacking.
  final Orientation orientation;

  /// Uses the slightly shorter input period size (Material dayPeriodInputSize).
  final bool forInput;

  @override
  Widget build(BuildContext context) {
    final M3EThemeData theme = M3ETheme.of(context);
    final M3EColorScheme scheme = theme.colorScheme;
    final M3ETimePickerTheme timeTheme = theme.timePickerTheme;
    final MaterialLocalizations localizations = MaterialLocalizations.of(
      context,
    );
    final bool landscape = !forInput && orientation == Orientation.landscape;
    final Size size = forInput
        ? timeTheme.periodInputSize
        : landscape
        ? timeTheme.periodLandscapeSize
        : timeTheme.periodPortraitSize;
    final BorderRadius radius = M3EShapes.radiusSmall;

    final Widget am = _buildOption(
      theme: theme,
      scheme: scheme,
      timeTheme: timeTheme,
      label: localizations.anteMeridiemAbbreviation,
      selected: !isPm,
      onTap: () => onChanged(false),
    );
    final Widget pm = _buildOption(
      theme: theme,
      scheme: scheme,
      timeTheme: timeTheme,
      label: localizations.postMeridiemAbbreviation,
      selected: isPm,
      onTap: () => onChanged(true),
    );

    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: radius,
        border: Border.all(color: scheme.outline),
      ),
      child: ClipRRect(
        borderRadius: radius,
        child: SizedBox.fromSize(
          size: size,
          child: landscape
              ? Row(
                  children: <Widget>[
                    Expanded(child: am),
                    Expanded(child: pm),
                  ],
                )
              : Column(
                  children: <Widget>[
                    Expanded(child: am),
                    Expanded(child: pm),
                  ],
                ),
        ),
      ),
    );
  }

  Widget _buildOption({
    required M3EThemeData theme,
    required M3EColorScheme scheme,
    required M3ETimePickerTheme timeTheme,
    required String label,
    required bool selected,
    required VoidCallback onTap,
  }) {
    return Semantics(
      selected: selected,
      button: true,
      child: M3ETappable(
        onTap: onTap,
        builder: (BuildContext context, M3EInteractionState state) {
          return ColoredBox(
            color: timeTheme.periodOptionBackgroundColor(
              scheme,
              selected: selected,
            ),
            child: Center(
              child: Text(
                label,
                style: theme.typeScale.titleMedium.copyWith(
                  color: timeTheme.periodOptionForegroundColor(
                    scheme,
                    selected: selected,
                  ),
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}
