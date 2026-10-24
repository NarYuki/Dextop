import 'package:material_ui/material_ui.dart';

import '../../foundations/foundations.dart';
import '../dialogs/components/m3e_dialog_inset.dart';
import '../divider/m3e_divider.dart';
import 'components/m3e_calendar_date_range_picker.dart';
import 'components/m3e_date_picker_actions.dart';
import 'components/m3e_date_picker_header.dart';
import 'components/m3e_input_date_range_picker_form_field.dart';
import 'enums/m3e_date_picker_enums.dart';
import 'models/m3e_date_picker_models.dart';
import 'res/m3e_date_picker_constants.dart';
import 'utils/m3e_date_picker_utils.dart';

/// Dialog for picking a date range.
class M3EDateRangePickerDialog extends StatefulWidget {
  /// M3EDateRangePickerDialog.
  const M3EDateRangePickerDialog({
    required this.firstDate,
    required this.lastDate,
    this.initialStartDate,
    this.initialEndDate,
    this.currentDate,
    this.initialEntryMode = M3EDatePickerEntryMode.calendar,
    this.selectableDayPredicate,
    this.helpText,
    this.cancelText,
    this.confirmText,
    this.errorFormatText,
    this.errorInvalidText,
    this.fieldStartHintText,
    this.fieldEndHintText,
    this.fieldStartLabelText,
    this.fieldEndLabelText,
    this.restorationId,
    this.onDatePickerModeChange,
    this.insetPadding = M3EDatePickerConstants.defaultInsetPadding,
    this.resizeToAvoidBottomInset,
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

  /// initialEntryMode.
  final M3EDatePickerEntryMode initialEntryMode;

  /// selectableDayPredicate.
  final M3ESelectableDayForRangePredicate? selectableDayPredicate;

  /// helpText.
  final String? helpText;

  /// cancelText.
  final String? cancelText;

  /// confirmText.
  final String? confirmText;

  /// errorFormatText.
  final String? errorFormatText;

  /// errorInvalidText.
  final String? errorInvalidText;

  /// fieldStartHintText.
  final String? fieldStartHintText;

  /// fieldEndHintText.
  final String? fieldEndHintText;

  /// fieldStartLabelText.
  final String? fieldStartLabelText;

  /// fieldEndLabelText.
  final String? fieldEndLabelText;

  /// restorationId.
  final String? restorationId;

  /// onDatePickerModeChange.
  final ValueChanged<M3EDatePickerEntryMode>? onDatePickerModeChange;

  /// insetPadding.
  final EdgeInsets insetPadding;

  /// When null, uses dialog theme `resizeToAvoidBottomInset`.
  final bool? resizeToAvoidBottomInset;

  @override
  State<M3EDateRangePickerDialog> createState() =>
      _M3EDateRangePickerDialogState();
}

class _M3EDateRangePickerDialogState extends State<M3EDateRangePickerDialog>
    with RestorationMixin {
  late final RestorableDateTimeN _startDate = RestorableDateTimeN(
    widget.initialStartDate,
  );
  late final RestorableDateTimeN _endDate = RestorableDateTimeN(
    widget.initialEndDate,
  );
  late final _RestorableM3EDatePickerEntryMode _entryMode =
      _RestorableM3EDatePickerEntryMode(widget.initialEntryMode);
  final _RestorableAutovalidateMode _autovalidateMode =
      _RestorableAutovalidateMode(AutovalidateMode.disabled);
  final GlobalKey<FormState> _formKey = GlobalKey<FormState>();

  @override
  String? get restorationId => widget.restorationId;

  @override
  void restoreState(RestorationBucket? oldBucket, bool initialRestore) {
    registerForRestoration(_startDate, 'start_date');
    registerForRestoration(_endDate, 'end_date');
    registerForRestoration(_autovalidateMode, 'autovalidateMode');
    registerForRestoration(_entryMode, 'calendar_entry_mode');
  }

  @override
  void dispose() {
    _startDate.dispose();
    _endDate.dispose();
    _entryMode.dispose();
    _autovalidateMode.dispose();
    super.dispose();
  }

  void _handleOk() {
    if (_entryMode.value == M3EDatePickerEntryMode.input ||
        _entryMode.value == M3EDatePickerEntryMode.inputOnly) {
      final FormState? form = _formKey.currentState;
      if (form == null || !form.validate()) {
        setState(() => _autovalidateMode.value = AutovalidateMode.always);
        return;
      }
      form.save();
    }
    if (_startDate.value == null) {
      return;
    }
    Navigator.of(context)
        .pop(M3EDateRange(start: _startDate.value!, end: _endDate.value));
  }

  void _handleCancel() {
    Navigator.of(context).pop();
  }

  void _handleEntryModeToggle() {
    setState(() {
      switch (_entryMode.value) {
        case M3EDatePickerEntryMode.calendar:
          _autovalidateMode.value = AutovalidateMode.disabled;
          _entryMode.value = M3EDatePickerEntryMode.input;
        case M3EDatePickerEntryMode.input:
          _formKey.currentState?.save();
          _entryMode.value = M3EDatePickerEntryMode.calendar;
        case M3EDatePickerEntryMode.calendarOnly:
        case M3EDatePickerEntryMode.inputOnly:
          break;
      }
      widget.onDatePickerModeChange?.call(_entryMode.value);
    });
  }

  String _headerTitle(MaterialLocalizations localizations) {
    if (_startDate.value == null) {
      return '';
    }
    if (_endDate.value == null) {
      return localizations.formatMediumDate(_startDate.value!);
    }
    return '${localizations.formatMediumDate(_startDate.value!)} – ${localizations.formatMediumDate(_endDate.value!)}';
  }

  @override
  Widget build(BuildContext context) {
    final theme = M3ETheme.of(context);
    final dateTheme = theme.datePickerTheme;
    final localizations = MaterialLocalizations.of(context);
    final Orientation orientation = MediaQuery.orientationOf(context);
    final DateTime firstDate = M3EDatePickerUtils.dateOnly(widget.firstDate);
    final DateTime lastDate = M3EDatePickerUtils.dateOnly(widget.lastDate);
    final DateTime currentDate = M3EDatePickerUtils.dateOnly(
      widget.currentDate ?? DateTime.now(),
    );
    final ({Widget picker, Widget? entryModeButton}) resolved =
        _resolveRangePickerAndEntryButton(
          localizations: localizations,
          firstDate: firstDate,
          lastDate: lastDate,
          currentDate: currentDate,
        );
    final bool isInputMode =
        _entryMode.value == M3EDatePickerEntryMode.input ||
        _entryMode.value == M3EDatePickerEntryMode.inputOnly;
    final Size dialogSize =
        (switch ((isInputMode, orientation)) {
          (false, Orientation.portrait) =>
            M3EDatePickerConstants.calendarPortraitDialogSize,
          (true, Orientation.portrait) =>
            M3EDatePickerConstants.inputPortraitDialogSize,
          (false, Orientation.landscape) =>
            M3EDatePickerConstants.calendarLandscapeDialogSize,
          (true, Orientation.landscape) =>
            M3EDatePickerConstants.inputRangeLandscapeDialogSize,
        }) *
        (MediaQuery.textScalerOf(context)
                .clamp(
                  maxScaleFactor:
                      M3EDatePickerConstants.maxRangeTextScaleFactor,
                )
                .scale(M3EDatePickerConstants.fontSizeToScale) /
            M3EDatePickerConstants.fontSizeToScale);
    final Widget pickerBody = isInputMode
        ? Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: <Widget>[
                Flexible(
                  child: SizedBox(
                    height: orientation == Orientation.portrait
                        ? M3EDatePickerConstants.inputFormPortraitHeight
                        : M3EDatePickerConstants.inputFormLandscapeHeight,
                    child: resolved.picker,
                  ),
                ),
              ],
            ),
          )
        : resolved.picker;
    final Widget header = M3EDatePickerHeader(
      helpText: widget.helpText ?? localizations.dateRangePickerHelpText,
      titleText: _headerTitle(localizations),
      showTitle: _startDate.value != null,
      orientation: orientation,
      isShort: orientation == Orientation.landscape,
      alignHelpWithSubHeader:
          orientation == Orientation.landscape && !isInputMode,
      entryModeButton: resolved.entryModeButton,
    );
    final Widget actions = M3EDatePickerActions(
      cancelText: widget.cancelText ?? localizations.cancelButtonLabel,
      confirmText: widget.confirmText ?? localizations.okButtonLabel,
      onCancel: _handleCancel,
      onConfirm: _handleOk,
    );
    final Widget content = Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          Expanded(child: pickerBody),
          actions,
        ],
      ),
    );
    return M3EDialogInset(
      padding: widget.insetPadding,
      resizeToAvoidBottomInset: widget.resizeToAvoidBottomInset,
      child: Material(
        color: dateTheme.backgroundColor(theme.colorScheme),
        elevation: dateTheme.elevation,
        shape: RoundedRectangleBorder(borderRadius: dateTheme.dialogShape),
        clipBehavior: Clip.antiAlias,
        child: SizedBox(
          width: dialogSize.width,
          height: dialogSize.height,
          child: switch (orientation) {
            Orientation.portrait => Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                header,
                M3EDivider(color: dateTheme.dividerColor(theme.colorScheme)),
                Expanded(child: content),
              ],
            ),
            Orientation.landscape => Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                header,
                M3EDivider(
                  axis: M3EDividerAxis.vertical,
                  color: dateTheme.dividerColor(theme.colorScheme),
                ),
                Expanded(child: content),
              ],
            ),
          },
        ),
      ),
    );
  }

  ({Widget picker, Widget? entryModeButton}) _resolveRangePickerAndEntryButton({
    required MaterialLocalizations localizations,
    required DateTime firstDate,
    required DateTime lastDate,
    required DateTime currentDate,
  }) {
    switch (_entryMode.value) {
      case M3EDatePickerEntryMode.calendar:
        return (
          picker: _buildRangeCalendar(
            firstDate: firstDate,
            lastDate: lastDate,
            currentDate: currentDate,
          ),
          entryModeButton: M3EDatePickerEntryModeButton(
            icon: M3EIcons.edit_outlined,
            tooltip: localizations.inputDateModeButtonLabel,
            onPressed: _handleEntryModeToggle,
          ),
        );
      case M3EDatePickerEntryMode.calendarOnly:
        return (
          picker: _buildRangeCalendar(
            firstDate: firstDate,
            lastDate: lastDate,
            currentDate: currentDate,
          ),
          entryModeButton: null,
        );
      case M3EDatePickerEntryMode.input:
      case M3EDatePickerEntryMode.inputOnly:
        return (
          picker: Form(
            key: _formKey,
            autovalidateMode: _autovalidateMode.value,
            child: M3EInputDateRangePickerFormField(
              firstDate: firstDate,
              lastDate: lastDate,
              initialStartDate: _startDate.value,
              initialEndDate: _endDate.value,
              onStartDateSaved: (DateTime value) {
                setState(() => _startDate.value = value);
              },
              onEndDateSaved: (DateTime value) {
                setState(() => _endDate.value = value);
              },
              selectableDayPredicate: widget.selectableDayPredicate,
              errorFormatText: widget.errorFormatText,
              errorInvalidText: widget.errorInvalidText,
              fieldStartHintText: widget.fieldStartHintText,
              fieldEndHintText: widget.fieldEndHintText,
              fieldStartLabelText: widget.fieldStartLabelText,
              fieldEndLabelText: widget.fieldEndLabelText,
            ),
          ),
          entryModeButton: _entryMode.value == M3EDatePickerEntryMode.input
              ? M3EDatePickerEntryModeButton(
                  icon: M3EIcons.calendar_today,
                  tooltip: localizations.calendarModeButtonLabel,
                  onPressed: _handleEntryModeToggle,
                )
              : null,
        );
    }
  }

  Widget _buildRangeCalendar({
    required DateTime firstDate,
    required DateTime lastDate,
    required DateTime currentDate,
  }) {
    return M3ECalendarDateRangePicker(
      firstDate: firstDate,
      lastDate: lastDate,
      currentDate: currentDate,
      initialStartDate: _startDate.value,
      initialEndDate: _endDate.value,
      selectableDayPredicate: widget.selectableDayPredicate,
      onStartDateChanged: (DateTime value) {
        setState(() => _startDate.value = value);
      },
      onEndDateChanged: (DateTime value) {
        setState(() => _endDate.value = value);
      },
    );
  }
}

class _RestorableAutovalidateMode extends RestorableValue<AutovalidateMode> {
  _RestorableAutovalidateMode(this.defaultValue);

  final AutovalidateMode defaultValue;

  @override
  AutovalidateMode createDefaultValue() => defaultValue;

  @override
  void didUpdateValue(AutovalidateMode? oldValue) {
    notifyListeners();
  }

  @override
  AutovalidateMode fromPrimitives(Object? data) {
    return AutovalidateMode.values[data! as int];
  }

  @override
  Object? toPrimitives() => value.index;
}

class _RestorableM3EDatePickerEntryMode
    extends RestorableValue<M3EDatePickerEntryMode> {
  _RestorableM3EDatePickerEntryMode(this.defaultValue);

  final M3EDatePickerEntryMode defaultValue;

  @override
  M3EDatePickerEntryMode createDefaultValue() => defaultValue;

  @override
  void didUpdateValue(M3EDatePickerEntryMode? oldValue) {
    notifyListeners();
  }

  @override
  M3EDatePickerEntryMode fromPrimitives(Object? data) {
    return M3EDatePickerEntryMode.values[data! as int];
  }

  @override
  Object? toPrimitives() => value.index;
}
