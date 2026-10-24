import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';

import '../../foundations/foundations.dart';
import '../dialogs/components/m3e_dialog_inset.dart';
import '../divider/m3e_divider.dart';
import 'components/m3e_date_picker_actions.dart';
import 'components/m3e_date_picker_header.dart';
import 'components/m3e_input_date_picker_form_field.dart';
import 'enums/m3e_date_picker_enums.dart';
import 'm3e_calendar_date_picker.dart';
import 'models/m3e_date_picker_models.dart';
import 'res/m3e_date_picker_constants.dart';
import 'utils/m3e_date_picker_utils.dart';

/// Dialog for picking a single date.
class M3EDatePickerDialog extends StatefulWidget {
  /// M3EDatePickerDialog.
  const M3EDatePickerDialog({
    required this.firstDate,
    required this.lastDate,
    this.initialDate,
    this.currentDate,
    this.initialEntryMode = M3EDatePickerEntryMode.calendar,
    this.initialCalendarMode = M3EDatePickerMode.day,
    this.selectableDayPredicate,
    this.helpText,
    this.cancelText,
    this.confirmText,
    this.errorFormatText,
    this.errorInvalidText,
    this.fieldHintText,
    this.fieldLabelText,
    this.keyboardType,
    this.restorationId,
    this.onDatePickerModeChange,
    this.insetPadding = M3EDatePickerConstants.defaultInsetPadding,
    this.resizeToAvoidBottomInset,
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

  /// initialEntryMode.
  final M3EDatePickerEntryMode initialEntryMode;

  /// initialCalendarMode.
  final M3EDatePickerMode initialCalendarMode;

  /// selectableDayPredicate.
  final M3ESelectableDayPredicate? selectableDayPredicate;

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

  /// fieldHintText.
  final String? fieldHintText;

  /// fieldLabelText.
  final String? fieldLabelText;

  /// keyboardType.
  final TextInputType? keyboardType;

  /// restorationId.
  final String? restorationId;

  /// onDatePickerModeChange.
  final ValueChanged<M3EDatePickerEntryMode>? onDatePickerModeChange;

  /// insetPadding.
  final EdgeInsets insetPadding;

  /// When null, uses dialog theme `resizeToAvoidBottomInset`.
  final bool? resizeToAvoidBottomInset;

  @override
  State<M3EDatePickerDialog> createState() => _M3EDatePickerDialogState();
}

class _M3EDatePickerDialogState extends State<M3EDatePickerDialog>
    with RestorationMixin {
  late final RestorableDateTimeN _selectedDate = RestorableDateTimeN(
    widget.initialDate,
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
    registerForRestoration(_selectedDate, 'selected_date');
    registerForRestoration(_autovalidateMode, 'autovalidateMode');
    registerForRestoration(_entryMode, 'calendar_entry_mode');
  }

  @override
  void dispose() {
    _selectedDate.dispose();
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
    Navigator.of(context).pop(_selectedDate.value);
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

  void _handleDateChanged(DateTime date) {
    setState(() => _selectedDate.value = M3EDatePickerUtils.dateOnly(date));
  }

  Widget _buildPickerBody({required Widget picker, required bool isInputMode}) {
    if (!isInputMode) {
      return picker;
    }
    final orientation = MediaQuery.orientationOf(context);
    final formHeight = orientation == Orientation.portrait
        ? M3EDatePickerConstants.inputFormPortraitHeight
        : M3EDatePickerConstants.inputFormLandscapeHeight;
    // material_ui input form uses horizontal 24; content column already insets 16.
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: <Widget>[
          Flexible(
            child: SizedBox(height: formHeight, child: picker),
          ),
        ],
      ),
    );
  }

  Widget _buildContentColumn({
    required Widget pickerBody,
    required Widget actions,
  }) {
    // Inset picker + actions from dialog edges (input was flush to the top;
    // actions sat on the bottom/side edges).
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          Expanded(child: pickerBody),
          actions,
        ],
      ),
    );
  }

  Size _dialogSize(BuildContext context) {
    final bool isCalendar = switch (_entryMode.value) {
      M3EDatePickerEntryMode.calendar ||
      M3EDatePickerEntryMode.calendarOnly => true,
      M3EDatePickerEntryMode.input || M3EDatePickerEntryMode.inputOnly => false,
    };
    final Orientation orientation = MediaQuery.orientationOf(context);
    return switch ((isCalendar, orientation)) {
      (true, Orientation.portrait) =>
        M3EDatePickerConstants.calendarPortraitDialogSize,
      (false, Orientation.portrait) =>
        M3EDatePickerConstants.inputPortraitDialogSize,
      (true, Orientation.landscape) =>
        M3EDatePickerConstants.calendarLandscapeDialogSize,
      (false, Orientation.landscape) =>
        M3EDatePickerConstants.inputLandscapeDialogSize,
    };
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
    final String titleText = _selectedDate.value == null
        ? ''
        : localizations.formatMediumDate(_selectedDate.value!);
    final ({Widget picker, Widget? entryModeButton}) resolved =
        _resolvePickerAndEntryButton(
          localizations: localizations,
          firstDate: firstDate,
          lastDate: lastDate,
          currentDate: currentDate,
        );
    final bool isInputMode =
        _entryMode.value == M3EDatePickerEntryMode.input ||
        _entryMode.value == M3EDatePickerEntryMode.inputOnly;
    final Widget header = M3EDatePickerHeader(
      helpText: widget.helpText ?? localizations.datePickerHelpText,
      titleText: titleText,
      showTitle: _selectedDate.value != null,
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
    final double textScaleFactor =
        MediaQuery.textScalerOf(context)
            .clamp(maxScaleFactor: M3EDatePickerConstants.maxTextScaleFactor)
            .scale(M3EDatePickerConstants.fontSizeToScale) /
        M3EDatePickerConstants.fontSizeToScale;
    final Size dialogSize = _dialogSize(context) * textScaleFactor;
    final Widget pickerBody = _buildPickerBody(
      picker: resolved.picker,
      isInputMode: isInputMode,
    );
    return M3EDialogInset(
      padding: widget.insetPadding,
      resizeToAvoidBottomInset: widget.resizeToAvoidBottomInset,
      child: Material(
        color: dateTheme.backgroundColor(theme.colorScheme),
        elevation: dateTheme.elevation,
        shape: RoundedRectangleBorder(borderRadius: dateTheme.dialogShape),
        clipBehavior: Clip.antiAlias,
        child: AnimatedContainer(
          width: dialogSize.width,
          height: dialogSize.height,
          duration: M3EDatePickerConstants.dialogSizeAnimationDuration,
          curve: Curves.easeIn,
          child: switch (orientation) {
            Orientation.portrait => Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                header,
                M3EDivider(color: dateTheme.dividerColor(theme.colorScheme)),
                Expanded(
                  child: _buildContentColumn(
                    pickerBody: pickerBody,
                    actions: actions,
                  ),
                ),
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
                Expanded(
                  child: _buildContentColumn(
                    pickerBody: pickerBody,
                    actions: actions,
                  ),
                ),
              ],
            ),
          },
        ),
      ),
    );
  }

  ({Widget picker, Widget? entryModeButton}) _resolvePickerAndEntryButton({
    required MaterialLocalizations localizations,
    required DateTime firstDate,
    required DateTime lastDate,
    required DateTime currentDate,
  }) {
    switch (_entryMode.value) {
      case M3EDatePickerEntryMode.calendar:
        return (
          picker: _buildCalendarPicker(
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
          picker: _buildCalendarPicker(
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
            child: Shortcuts(
              shortcuts: const <ShortcutActivator, Intent>{
                SingleActivator(LogicalKeyboardKey.enter): NextFocusIntent(),
              },
              child: M3EInputDatePickerFormField(
                initialDate: _selectedDate.value,
                firstDate: firstDate,
                lastDate: lastDate,
                onDateSubmitted: _handleDateChanged,
                onDateSaved: _handleDateChanged,
                selectableDayPredicate: widget.selectableDayPredicate,
                errorFormatText: widget.errorFormatText,
                errorInvalidText: widget.errorInvalidText,
                fieldHintText: widget.fieldHintText,
                fieldLabelText: widget.fieldLabelText,
                keyboardType: widget.keyboardType,
                autofocus: true,
              ),
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

  Widget _buildCalendarPicker({
    required DateTime firstDate,
    required DateTime lastDate,
    required DateTime currentDate,
  }) {
    return M3ECalendarDatePicker(
      key: ValueKey<DateTime?>(_selectedDate.value),
      initialDate: _selectedDate.value,
      firstDate: firstDate,
      lastDate: lastDate,
      currentDate: currentDate,
      initialCalendarMode: widget.initialCalendarMode,
      selectableDayPredicate: widget.selectableDayPredicate,
      onDateChanged: _handleDateChanged,
      expandToFit: true,
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
