import 'package:material_ui/material_ui.dart';

import '../../../foundations/foundations.dart';
import 'components/m3e_date_picker_mode_toggle.dart';
import 'components/m3e_month_picker.dart';
import 'components/m3e_year_picker.dart';
import 'enums/m3e_date_picker_enums.dart';
import 'models/m3e_date_picker_models.dart';
import 'res/m3e_date_picker_constants.dart';
import 'utils/m3e_date_picker_utils.dart';

/// Embeddable calendar for selecting a single date.
class M3ECalendarDatePicker extends StatefulWidget {
  /// M3ECalendarDatePicker.
  const M3ECalendarDatePicker({
    required this.onDateChanged,
    required this.firstDate,
    required this.lastDate,
    this.initialDate,
    this.currentDate,
    this.initialCalendarMode = M3EDatePickerMode.day,
    this.onDisplayedMonthChanged,
    this.onCalendarModeChanged,
    this.selectableDayPredicate,
    this.expandToFit = false,
    super.key,
  });

  /// initialDate.

  final DateTime? initialDate;

  /// firstDate.
  final DateTime firstDate;

  /// lastDate.
  final DateTime lastDate;

  /// currentDate.
  final DateTime? currentDate;

  /// onDateChanged.
  final ValueChanged<DateTime> onDateChanged;

  /// onDisplayedMonthChanged.
  final ValueChanged<DateTime>? onDisplayedMonthChanged;

  /// onCalendarModeChanged.
  final ValueChanged<M3EDatePickerMode>? onCalendarModeChanged;

  /// initialCalendarMode.
  final M3EDatePickerMode initialCalendarMode;

  /// selectableDayPredicate.
  final M3ESelectableDayPredicate? selectableDayPredicate;

  /// expandToFit.
  final bool expandToFit;

  @override
  State<M3ECalendarDatePicker> createState() => _M3ECalendarDatePickerState();
}

class _M3ECalendarDatePickerState extends State<M3ECalendarDatePicker> {
  late M3EDatePickerMode _mode;
  late DateTime _displayedMonth;
  late DateTime _firstDate;
  late DateTime _lastDate;
  late DateTime _currentDate;
  DateTime? _selectedDate;

  @override
  void initState() {
    super.initState();
    _firstDate = M3EDatePickerUtils.dateOnly(widget.firstDate);
    _lastDate = M3EDatePickerUtils.dateOnly(widget.lastDate);
    _currentDate = M3EDatePickerUtils.dateOnly(
      widget.currentDate ?? DateTime.now(),
    );
    _mode = widget.initialCalendarMode;
    _selectedDate = widget.initialDate == null
        ? null
        : M3EDatePickerUtils.dateOnly(widget.initialDate!);
    final DateTime base = _selectedDate ?? _currentDate;
    _displayedMonth = M3EDatePickerUtils.getMonth(base.year, base.month);
    assert(
      !_lastDate.isBefore(_firstDate),
      'lastDate must be on or after firstDate',
    );
  }

  void _handleModeChanged(M3EDatePickerMode mode) {
    M3EHaptics.selection();
    setState(() => _mode = mode);
    widget.onCalendarModeChanged?.call(mode);
  }

  void _handleMonthChanged(DateTime month) {
    setState(() => _displayedMonth = month);
    widget.onDisplayedMonthChanged?.call(month);
  }

  void _handleYearChanged(DateTime value) {
    M3EHaptics.selection();
    final DateTime clamped = M3EDatePickerUtils.clampDate(
      value,
      _firstDate,
      _lastDate,
    );
    setState(() {
      _mode = M3EDatePickerMode.day;
      _displayedMonth = M3EDatePickerUtils.getMonth(
        clamped.year,
        clamped.month,
      );
      if (M3EDatePickerUtils.isSelectable(
        clamped,
        _firstDate,
        _lastDate,
        predicate: widget.selectableDayPredicate,
      )) {
        _selectedDate = clamped;
        widget.onDateChanged(clamped);
      }
    });
    widget.onDisplayedMonthChanged?.call(_displayedMonth);
    widget.onCalendarModeChanged?.call(M3EDatePickerMode.day);
  }

  void _handleDayChanged(DateTime value) {
    M3EHaptics.selection();
    setState(() {
      _selectedDate = value;
      widget.onDateChanged(value);
    });
  }

  double _inlinePickerBodyHeight(BuildContext context) {
    final int firstDayOfWeekIndex = MaterialLocalizations.of(context)
        .firstDayOfWeekIndex;
    return switch (_mode) {
      M3EDatePickerMode.day => M3EDatePickerUtils.calendarDayViewHeight(
        _displayedMonth,
        firstDayOfWeekIndex,
      ),
      M3EDatePickerMode.year => M3EDatePickerUtils.calendarYearViewHeight(
        _firstDate,
        _lastDate,
        includeSubHeader: false,
      ),
    };
  }

  @override
  Widget build(BuildContext context) {
    return M3EComponentTheme(
      builder: (BuildContext context) {
        final theme = M3ETheme.of(context);
        final dateTheme = theme.datePickerTheme;

        if (widget.expandToFit) {
          // material_ui CalendarDatePicker: picker body + mode toggle in a Stack.
          // Year mode pads below the sub-header so the toggle overlays the top.
          final Widget picker = switch (_mode) {
            M3EDatePickerMode.day => M3EMonthPicker(
              initialMonth: _displayedMonth,
              firstDate: _firstDate,
              lastDate: _lastDate,
              selectedDate: _selectedDate,
              currentDate: _currentDate,
              onChanged: _handleDayChanged,
              onMonthChanged: _handleMonthChanged,
              selectableDayPredicate: widget.selectableDayPredicate,
              expandToFit: true,
              mode: _mode,
              onModeChanged: _handleModeChanged,
            ),
            M3EDatePickerMode.year => Padding(
              padding: const EdgeInsets.only(
                top: M3EDatePickerConstants.subHeaderHeight,
              ),
              child: M3EYearPicker(
                selectedDate: _selectedDate,
                firstDate: _firstDate,
                lastDate: _lastDate,
                onChanged: _handleYearChanged,
                selectableDayPredicate: widget.selectableDayPredicate,
                displayedMonth: _displayedMonth,
              ),
            ),
          };
          return Stack(
            children: <Widget>[
              Positioned.fill(child: picker),
              if (_mode == M3EDatePickerMode.year)
                M3EDatePickerModeToggle(
                  mode: _mode,
                  monthDate: _displayedMonth,
                  onChanged: _handleModeChanged,
                ),
            ],
          );
        }

        final Widget content = Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            M3EDatePickerModeToggle(
              mode: _mode,
              monthDate: _displayedMonth,
              onChanged: _handleModeChanged,
            ),
            AnimatedSize(
              duration: M3EDatePickerConstants.dialogSizeAnimationDuration,
              curve: Curves.easeIn,
              alignment: Alignment.topCenter,
              child: SizedBox(
                height: _inlinePickerBodyHeight(context),
                child: switch (_mode) {
                  M3EDatePickerMode.day => M3EMonthPicker(
                    initialMonth: _displayedMonth,
                    firstDate: _firstDate,
                    lastDate: _lastDate,
                    selectedDate: _selectedDate,
                    currentDate: _currentDate,
                    onChanged: _handleDayChanged,
                    onMonthChanged: _handleMonthChanged,
                    selectableDayPredicate: widget.selectableDayPredicate,
                  ),
                  M3EDatePickerMode.year => M3EYearPicker(
                    selectedDate: _selectedDate,
                    firstDate: _firstDate,
                    lastDate: _lastDate,
                    onChanged: _handleYearChanged,
                    selectableDayPredicate: widget.selectableDayPredicate,
                    displayedMonth: _displayedMonth,
                  ),
                },
              ),
            ),
          ],
        );

        return Container(
          width: dateTheme.width,
          padding: dateTheme.padding,
          decoration: BoxDecoration(
            color: dateTheme.containerColor(theme.colorScheme),
            borderRadius: dateTheme.borderRadius,
          ),
          child: content,
        );
      },
    );
  }
}
