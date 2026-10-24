import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';

/// A single day cell in the calendar grid.
class M3EDayCell extends StatelessWidget {
  /// M3EDayCell.
  const M3EDayCell({
    required this.date,
    required this.selected,
    required this.today,
    required this.enabled,
    required this.onTap,
    this.inRange = false,
    this.rangeStart = false,
    this.rangeEnd = false,
    super.key,
  });

  /// date.

  final DateTime date;

  /// selected.
  final bool selected;

  /// today.
  final bool today;

  /// enabled.
  final bool enabled;

  /// onTap.
  final VoidCallback? onTap;

  /// inRange.
  final bool inRange;

  /// rangeStart.
  final bool rangeStart;

  /// rangeEnd.
  final bool rangeEnd;

  @override
  Widget build(BuildContext context) {
    final theme = M3ETheme.of(context);
    final scheme = theme.colorScheme;
    final dateTheme = theme.datePickerTheme;
    final Color foreground = dateTheme.dayForegroundColor(
      scheme,
      selected: selected || rangeStart || rangeEnd,
      enabled: enabled,
    );
    final bool showCaps = rangeStart || rangeEnd;
    final bool showRangeFill = inRange && !showCaps;

    return M3ETappable(
      enabled: enabled,
      onTap: onTap,
      semanticLabel: '${date.day}',
      builder: (BuildContext context, M3EInteractionState state) {
        return Stack(
          alignment: Alignment.center,
          children: <Widget>[
            if (showRangeFill)
              Positioned.fill(
                child: ColoredBox(color: dateTheme.rangeHighlightColor(scheme)),
              ),
            if (rangeStart && inRange)
              Positioned(
                left: dateTheme.daySize / 2,
                right: 0,
                top: (48 - dateTheme.daySize) / 2,
                bottom: (48 - dateTheme.daySize) / 2,
                child: ColoredBox(color: dateTheme.rangeHighlightColor(scheme)),
              ),
            if (rangeEnd && inRange)
              Positioned(
                left: 0,
                right: dateTheme.daySize / 2,
                top: (48 - dateTheme.daySize) / 2,
                bottom: (48 - dateTheme.daySize) / 2,
                child: ColoredBox(color: dateTheme.rangeHighlightColor(scheme)),
              ),
            Container(
              width: dateTheme.daySize,
              height: dateTheme.daySize,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: (selected || showCaps)
                    ? dateTheme.selectedDayBackgroundColor(scheme)
                    : null,
                border: today && !selected && !showCaps
                    ? Border.all(color: dateTheme.todayColor(scheme))
                    : null,
              ),
              child: Text(
                '${date.day}',
                style: dateTheme
                    .dayStyle(theme.typeScale, scheme)
                    .copyWith(color: foreground),
              ),
            ),
          ],
        );
      },
    );
  }
}
