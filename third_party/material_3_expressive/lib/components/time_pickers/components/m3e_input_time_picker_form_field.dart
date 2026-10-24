import 'dart:math' as math;

import 'package:material_ui/material_ui.dart';

import '../../../foundations/foundations.dart';
import '../models/m3e_time.dart';
import '../styles/m3e_time_picker_theme.dart';
import '../utils/m3e_time_picker_utils.dart';
import 'm3e_day_period_control.dart';
import 'm3e_hour_minute_text_field.dart';

const String _timeSeparator = ':';

/// Text fields for entering a time in a picker dialog.
///
/// Matches material_ui `_TimePickerInput`: help text on top, filled hour/minute
/// digit boxes with labels beneath, and a portrait-stacked AM/PM control.
class M3EInputTimePickerFormField extends StatefulWidget {
  /// M3EInputTimePickerFormField.
  const M3EInputTimePickerFormField({
    this.initialTime,
    this.helpText,
    this.onTimeSubmitted,
    this.onTimeSaved,
    this.errorInvalidText,
    this.hourLabelText,
    this.minuteLabelText,
    this.use24HourFormat,
    this.emptyInitialInput = false,
    this.autofocus = false,
    super.key,
  });

  /// initialTime.
  final M3ETime? initialTime;

  /// Optional help label above the fields.
  final String? helpText;

  /// onTimeSubmitted.
  final ValueChanged<M3ETime>? onTimeSubmitted;

  /// onTimeSaved.
  final ValueChanged<M3ETime>? onTimeSaved;

  /// errorInvalidText.
  final String? errorInvalidText;

  /// hourLabelText.
  final String? hourLabelText;

  /// minuteLabelText.
  final String? minuteLabelText;

  /// use24HourFormat.
  final bool? use24HourFormat;

  /// emptyInitialInput.
  final bool emptyInitialInput;

  /// autofocus.
  final bool autofocus;

  @override
  State<M3EInputTimePickerFormField> createState() =>
      _M3EInputTimePickerFormFieldState();
}

class _M3EInputTimePickerFormFieldState
    extends State<M3EInputTimePickerFormField> {
  final TextEditingController _hourController = TextEditingController();
  final TextEditingController _minuteController = TextEditingController();
  final FocusNode _hourFocus = FocusNode();
  final FocusNode _minuteFocus = FocusNode();
  bool _isPm = false;

  bool get _use24HourFormat => M3ETimePickerUtils.use24HourFormat(
    context,
    alwaysUse24HourFormat: widget.use24HourFormat,
  );

  @override
  void initState() {
    super.initState();
    if (widget.autofocus) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _hourFocus.requestFocus();
      });
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _applyInitialTime();
  }

  @override
  void dispose() {
    _hourController.dispose();
    _minuteController.dispose();
    _hourFocus.dispose();
    _minuteFocus.dispose();
    super.dispose();
  }

  @override
  void didUpdateWidget(covariant M3EInputTimePickerFormField oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.initialTime != oldWidget.initialTime ||
        widget.emptyInitialInput != oldWidget.emptyInitialInput) {
      _applyInitialTime();
    }
  }

  void _applyInitialTime() {
    final M3ETime? time = widget.initialTime;
    if (time == null || widget.emptyInitialInput) {
      _hourController.clear();
      _minuteController.clear();
      _isPm = false;
      return;
    }
    _isPm = time.isPm;
    if (_use24HourFormat) {
      _hourController.text = time.hour.toString().padLeft(2, '0');
    } else {
      _hourController.text = time.hourOf12.toString().padLeft(2, '0');
    }
    _minuteController.text = time.minute.toString().padLeft(2, '0');
  }

  M3ETime? _parseFields() {
    return M3ETimePickerUtils.parseInputTime(
      hourText: _hourController.text.trim(),
      minuteText: _minuteController.text.trim(),
      use24HourFormat: _use24HourFormat,
      isPm: _isPm,
    );
  }

  String? _validateHour(String? value) {
    if ((value == null || value.isEmpty) && widget.emptyInitialInput) {
      return null;
    }
    if (!M3ETimePickerUtils.isValidHourText(
      value ?? '',
      use24HourFormat: _use24HourFormat,
    )) {
      return widget.errorInvalidText ??
          MaterialLocalizations.of(context).invalidTimeLabel;
    }
    return null;
  }

  String? _validateMinute(String? value) {
    if ((value == null || value.isEmpty) && widget.emptyInitialInput) {
      return null;
    }
    if (!M3ETimePickerUtils.isValidMinuteText(value ?? '')) {
      return widget.errorInvalidText ??
          MaterialLocalizations.of(context).invalidTimeLabel;
    }
    return null;
  }

  void _handleSaved() {
    final M3ETime? parsed = _parseFields();
    if (parsed != null) {
      widget.onTimeSaved?.call(parsed);
    }
  }

  void _handleSubmitted() {
    final M3ETime? parsed = _parseFields();
    if (parsed != null) {
      widget.onTimeSubmitted?.call(parsed);
    }
  }

  String? _validateForm(_) {
    final String? hourError = _validateHour(_hourController.text.trim());
    if (hourError != null) {
      return hourError;
    }
    return _validateMinute(_minuteController.text.trim());
  }

  Size _inputFieldSize(M3ETimePickerTheme timeTheme) {
    // material hourMinuteInputSize = field height − 8; 24h is wider (114).
    final double height = timeTheme.fieldSize.height - 8;
    final double width = _use24HourFormat ? 114 : timeTheme.fieldSize.width;
    return Size(width, height);
  }

  Widget _buildLabeledField({
    required M3EThemeData theme,
    required TextEditingController controller,
    required FocusNode focusNode,
    required String label,
    required bool hasError,
    required TextInputAction textInputAction,
    required ValueChanged<String> onSubmitted,
    required ValueChanged<String> onChanged,
    bool autofocus = false,
  }) {
    final size = _inputFieldSize(theme.timePickerTheme);
    // Hint mirrors material_ui: show formatted value when unfocused.
    final hintText = widget.emptyInitialInput || controller.text.isEmpty
        ? null
        : controller.text;
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: M3EHourMinuteTextField(
              controller: controller,
              focusNode: focusNode,
              size: size,
              semanticLabel: label,
              hintText: hintText,
              autofocus: autofocus,
              textInputAction: textInputAction,
              hasError: hasError,
              onChanged: onChanged,
              onSubmitted: onSubmitted,
            ),
          ),
          if (!hasError)
            ExcludeSemantics(
              child: Text(
                label,
                style: theme.typeScale.bodySmall.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildHourMinuteRow({
    required M3EThemeData theme,
    required M3ETimePickerTheme timeTheme,
    required FormFieldState<void> field,
    required String hourLabel,
    required String minuteLabel,
    required double topInset,
  }) {
    final hasError = field.hasError;
    final fieldSize = _inputFieldSize(timeTheme);
    return Expanded(
      child: Padding(
        padding: EdgeInsets.only(top: topInset),
        child: Row(
          textDirection: TextDirection.ltr,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            _buildLabeledField(
              theme: theme,
              controller: _hourController,
              focusNode: _hourFocus,
              label: hourLabel,
              hasError: hasError,
              autofocus: widget.autofocus,
              textInputAction: TextInputAction.next,
              onSubmitted: (_) => _minuteFocus.requestFocus(),
              onChanged: (String value) {
                field.didChange(null);
                if (value.length == 2) {
                  _minuteFocus.requestFocus();
                }
              },
            ),
            SizedBox(
              height: fieldSize.height,
              width: 24,
              child: Center(
                child: Text(
                  _timeSeparator,
                  style: theme.typeScale.displayMedium.copyWith(
                    color: theme.colorScheme.onSurface,
                  ),
                ),
              ),
            ),
            _buildLabeledField(
              theme: theme,
              controller: _minuteController,
              focusNode: _minuteFocus,
              label: minuteLabel,
              hasError: false,
              textInputAction: TextInputAction.done,
              onSubmitted: (_) {
                if (field.validate()) {
                  _handleSubmitted();
                }
              },
              onChanged: (_) => field.didChange(null),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildInputBody({
    required M3EThemeData theme,
    required M3ETimePickerTheme timeTheme,
    required FormFieldState<void> field,
    required bool periodLeading,
    required double minInteractiveVerticalPadding,
    required String hourLabel,
    required String minuteLabel,
  }) {
    final hasError = field.hasError;
    final topInset = minInteractiveVerticalPadding / 2;
    final period = M3EDayPeriodControl(
      isPm: _isPm,
      forInput: true,
      onChanged: (bool pm) {
        setState(() => _isPm = pm);
        field.didChange(null);
      },
    );
    // Same top inset as the hour/minute row so the 72px period control
    // shares a top edge with the input fields (material dayPeriodInputSize).
    final periodInset = EdgeInsetsDirectional.only(
      start: periodLeading ? 0 : timeTheme.fieldPeriodGap,
      end: periodLeading ? timeTheme.fieldPeriodGap : 0,
      top: topInset,
    );
    final fields = _buildHourMinuteRow(
      theme: theme,
      timeTheme: timeTheme,
      field: field,
      hourLabel: hourLabel,
      minuteLabel: minuteLabel,
      topInset: topInset,
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        if (widget.helpText != null)
          Padding(
            padding: EdgeInsets.only(bottom: 20 - topInset),
            child: Text(
              widget.helpText!,
              style: timeTheme.headerHelpStyle(
                theme.typeScale,
                theme.colorScheme,
              ),
            ),
          ),
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            if (!_use24HourFormat && periodLeading)
              Padding(padding: periodInset, child: period),
            fields,
            if (!_use24HourFormat && !periodLeading)
              Padding(padding: periodInset, child: period),
          ],
        ),
        if (hasError)
          Text(
            field.errorText!,
            style: theme.typeScale.bodySmall.copyWith(
              color: theme.colorScheme.error,
            ),
          )
        else
          const SizedBox(height: 2),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final localizations = MaterialLocalizations.of(context);
    final theme = M3ETheme.of(context);
    final timeTheme = theme.timePickerTheme;
    final format = localizations.timeOfDayFormat(
      alwaysUse24HourFormat: _use24HourFormat,
    );
    final periodLeading = format == TimeOfDayFormat.a_space_h_colon_mm;
    final periodHeight = timeTheme.periodInputSize.height;
    final minInteractiveVerticalPadding = math.max<double>(
      0,
      2 * 48 - periodHeight,
    );
    final hourLabel = widget.hourLabelText ?? localizations.timePickerHourLabel;
    final minuteLabel =
        widget.minuteLabelText ?? localizations.timePickerMinuteLabel;

    return FormField<void>(
      validator: _validateForm,
      onSaved: (_) => _handleSaved(),
      builder: (FormFieldState<void> field) {
        return _buildInputBody(
          theme: theme,
          timeTheme: timeTheme,
          field: field,
          periodLeading: periodLeading,
          minInteractiveVerticalPadding: minInteractiveVerticalPadding,
          hourLabel: hourLabel,
          minuteLabel: minuteLabel,
        );
      },
    );
  }
}
