import 'package:material_ui/material_ui.dart';

import '../../../foundations/foundations.dart';
import '../models/m3e_date_picker_models.dart';
import '../utils/m3e_date_picker_utils.dart';
import 'm3e_day_picker.dart';

/// Calendar for selecting a date range.
class M3ECalendarDateRangePicker extends StatefulWidget {
  /// M3ECalendarDateRangePicker.
  const M3ECalendarDateRangePicker({
    required this.firstDate,
    required this.lastDate,
    required this.onStartDateChanged,
    this.initialStartDate,
    this.initialEndDate,
    this.currentDate,
    this.onEndDateChanged,
    this.selectableDayPredicate,
    super.key,
  });

  /// firstDate.

  final DateTime firstDate;

  /// lastDate.
  final DateTime lastDate;

  /// initialStartDate.
  final DateTime? initialStartDate;

  /// initialEndDate.
  final DateTime? initialEndDate;

  /// currentDate.
  final DateTime? currentDate;

  /// onStartDateChanged.
  final ValueChanged<DateTime> onStartDateChanged;

  /// onEndDateChanged.
  final ValueChanged<DateTime>? onEndDateChanged;

  /// selectableDayPredicate.
  final M3ESelectableDayForRangePredicate? selectableDayPredicate;

  @override
  State<M3ECalendarDateRangePicker> createState() =>
      _M3ECalendarDateRangePickerState();
}

class _M3ECalendarDateRangePickerState
    extends State<M3ECalendarDateRangePicker> {
  late ScrollController _controller;
  late DateTime _firstDate;
  late DateTime _lastDate;
  late DateTime _currentDate;
  DateTime? _startDate;
  DateTime? _endDate;

  @override
  void initState() {
    super.initState();
    _controller = ScrollController();
    _firstDate = M3EDatePickerUtils.dateOnly(widget.firstDate);
    _lastDate = M3EDatePickerUtils.dateOnly(widget.lastDate);
    _currentDate = M3EDatePickerUtils.dateOnly(
      widget.currentDate ?? DateTime.now(),
    );
    _startDate = widget.initialStartDate == null
        ? null
        : M3EDatePickerUtils.dateOnly(widget.initialStartDate!);
    _endDate = widget.initialEndDate == null
        ? null
        : M3EDatePickerUtils.dateOnly(widget.initialEndDate!);
  }

  @override
  void didUpdateWidget(covariant M3ECalendarDateRangePicker oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.initialStartDate != oldWidget.initialStartDate) {
      _startDate = widget.initialStartDate;
    }
    if (widget.initialEndDate != oldWidget.initialEndDate) {
      _endDate = widget.initialEndDate;
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  int get _monthCount =>
      M3EDatePickerUtils.monthDelta(_firstDate, _lastDate) + 1;

  DateTime _monthForIndex(int index) {
    return M3EDatePickerUtils.addMonthsToMonthDate(_firstDate, index);
  }

  void _updateSelection(DateTime date) {
    M3EHaptics.selection();
    setState(() {
      if (_startDate != null &&
          _endDate == null &&
          !date.isBefore(_startDate!)) {
        _endDate = date;
        widget.onEndDateChanged?.call(_endDate!);
      } else {
        _startDate = date;
        _endDate = null;
        widget.onStartDateChanged(_startDate!);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final theme = M3ETheme.of(context);
    final MaterialLocalizations localizations = MaterialLocalizations.of(
      context,
    );

    return ListView.builder(
      controller: _controller,
      itemCount: _monthCount,
      itemBuilder: (BuildContext context, int index) {
        final DateTime month = _monthForIndex(index);
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            Padding(
              padding: const EdgeInsetsDirectional.fromSTEB(16, 16, 16, 8),
              child: Text(
                localizations.formatMonthYear(month),
                style: theme.typeScale.titleSmall.copyWith(
                  color: theme.colorScheme.onSurface,
                ),
              ),
            ),
            M3EDayPicker(
              displayedMonth: month,
              selectedDate: _startDate,
              currentDate: _currentDate,
              firstDate: _firstDate,
              lastDate: _lastDate,
              onChanged: _updateSelection,
              selectableDayPredicate: widget.selectableDayPredicate,
              rangeStart: _startDate,
              rangeEnd: _endDate,
            ),
            const SizedBox(height: 8),
          ],
        );
      },
    );
  }
}
