import 'dart:math' as math;

import 'package:material_ui/material_ui.dart';

import '../../../foundations/foundations.dart';
import '../enums/m3e_time_picker_enums.dart';
import '../models/m3e_time.dart';
import '../res/m3e_time_picker_constants.dart';
import '../styles/m3e_time_picker_theme.dart';
import '../utils/m3e_time_picker_utils.dart';
import 'm3e_day_period_control.dart';
import 'm3e_time_dial_painter.dart';

const String _timeSeparator = ':';
const String _zeroPad = '0';
const double _minInteractiveDimension = 48;

/// Embeddable clock dial for choosing an hour and minute.
class M3EDialTimePicker extends StatefulWidget {
  /// M3EDialTimePicker.
  const M3EDialTimePicker({
    required this.value,
    required this.onChanged,
    this.use24HourFormat,
    this.expandToFit = false,
    this.orientation,
    this.helpText,
    super.key,
  });

  /// value.
  final M3ETime value;

  /// onChanged.
  final ValueChanged<M3ETime> onChanged;

  /// use24HourFormat.
  final bool? use24HourFormat;

  /// expandToFit.
  final bool expandToFit;

  /// Forces dial header layout; defaults to [MediaQuery] orientation.
  final Orientation? orientation;

  /// Optional help label above the time fields (dialog dial mode).
  final String? helpText;

  @override
  State<M3EDialTimePicker> createState() => _M3EDialTimePickerState();
}

class _M3EDialTimePickerState extends State<M3EDialTimePicker> {
  M3ETimePickerMode _mode = M3ETimePickerMode.hour;

  bool _use24HourFormat(BuildContext context) {
    return M3ETimePickerUtils.use24HourFormat(
      context,
      alwaysUse24HourFormat: widget.use24HourFormat,
    );
  }

  Orientation _orientation(BuildContext context) {
    return widget.orientation ?? MediaQuery.orientationOf(context);
  }

  @override
  Widget build(BuildContext context) {
    final theme = M3ETheme.of(context);
    final scheme = theme.colorScheme;
    final timeTheme = theme.timePickerTheme;
    final MaterialLocalizations localizations = MaterialLocalizations.of(
      context,
    );
    final bool use24Hour = _use24HourFormat(context);
    final Orientation orientation = _orientation(context);

    final Widget content = switch (orientation) {
      Orientation.portrait => _buildPortrait(theme, localizations, use24Hour),
      Orientation.landscape => _buildLandscape(theme, localizations, use24Hour),
    };

    if (widget.expandToFit) {
      return content;
    }

    return M3EComponentTheme(
      builder: (BuildContext context) => Container(
        padding: timeTheme.padding,
        decoration: BoxDecoration(
          color: timeTheme.containerColor(scheme),
          borderRadius: timeTheme.borderRadius,
        ),
        child: content,
      ),
    );
  }

  Widget _buildPortrait(
    M3EThemeData theme,
    MaterialLocalizations localizations,
    bool use24Hour,
  ) {
    final timeTheme = theme.timePickerTheme;
    final double periodHeight = timeTheme.periodPortraitSize.height;
    final double minInteractiveVerticalPadding = math.max(
      0,
      2 * _minInteractiveDimension - periodHeight,
    );
    final dialPadding = EdgeInsets.only(
      left: M3ETimePickerConstants.dialPortraitHorizontalPadding,
      right: M3ETimePickerConstants.dialPortraitHorizontalPadding,
      top:
          M3ETimePickerConstants.dialPortraitTopPadding -
          minInteractiveVerticalPadding / 2,
    );

    final Widget help = widget.helpText == null
        ? const SizedBox.shrink()
        : Padding(
            padding: EdgeInsets.only(
              bottom: 20 - minInteractiveVerticalPadding / 2,
            ),
            child: Text(
              widget.helpText!,
              style: timeTheme.headerHelpStyle(
                theme.typeScale,
                theme.colorScheme,
              ),
            ),
          );
    final Widget fields = _buildPortraitFieldsHeader(
      theme,
      localizations,
      use24Hour,
    );
    final Widget dial = Padding(
      padding: dialPadding,
      child: Center(child: _buildDial(theme, use24Hour)),
    );

    if (widget.expandToFit) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          help,
          fields,
          Expanded(child: dial),
        ],
      );
    }

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        help,
        fields,
        SizedBox(height: timeTheme.headerDialGap),
        dial,
      ],
    );
  }

  Widget _buildLandscape(
    M3EThemeData theme,
    MaterialLocalizations localizations,
    bool use24Hour,
  ) {
    final timeTheme = theme.timePickerTheme;
    final double periodHeight = timeTheme.periodLandscapeSize.height;
    final double minInteractiveVerticalPadding = math.max(
      0,
      _minInteractiveDimension - periodHeight,
    );

    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        final bool boundedHeight = constraints.hasBoundedHeight;
        final Widget row = Row(
          crossAxisAlignment: boundedHeight
              ? CrossAxisAlignment.stretch
              : CrossAxisAlignment.center,
          children: <Widget>[
            SizedBox(
              width: timeTheme.headerLandscapeWidth,
              height: boundedHeight ? null : timeTheme.dialSize,
              child: _buildLandscapeFieldsHeader(
                theme,
                localizations,
                use24Hour,
                minInteractiveVerticalPadding,
              ),
            ),
            Expanded(
              child: Padding(
                padding: const EdgeInsetsDirectional.only(
                  start: M3ETimePickerConstants.dialLandscapeStartPadding,
                ),
                child: Center(child: _buildDial(theme, use24Hour)),
              ),
            ),
          ],
        );
        if (boundedHeight) {
          return row;
        }
        return SizedBox(height: timeTheme.dialSize, child: row);
      },
    );
  }

  Widget _buildPortraitFieldsHeader(
    M3EThemeData theme,
    MaterialLocalizations localizations,
    bool use24Hour,
  ) {
    final timeTheme = theme.timePickerTheme;
    final Widget fields = _buildHourMinuteFields(theme, localizations);
    final Widget? period = use24Hour
        ? null
        : M3EDayPeriodControl(isPm: widget.value.isPm, onChanged: _setPeriod);

    return Row(
      children: <Widget>[
        Expanded(child: fields),
        if (period != null) ...<Widget>[
          SizedBox(width: timeTheme.fieldPeriodGap),
          period,
        ],
      ],
    );
  }

  Widget _buildLandscapeFieldsHeader(
    M3EThemeData theme,
    MaterialLocalizations localizations,
    bool use24Hour,
    double minInteractiveVerticalPadding,
  ) {
    final timeTheme = theme.timePickerTheme;
    final Widget fields = _buildHourMinuteFields(theme, localizations);
    final Widget? period = use24Hour
        ? null
        : M3EDayPeriodControl(
            isPm: widget.value.isPm,
            onChanged: _setPeriod,
            orientation: Orientation.landscape,
          );

    return Stack(
      children: <Widget>[
        if (widget.helpText != null)
          Text(
            widget.helpText!,
            style: timeTheme.headerHelpStyle(
              theme.typeScale,
              theme.colorScheme,
            ),
          ),
        // Fill the header column and center fields so help text stays visible
        // above them (material_ui landscape dial header).
        Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            fields,
            if (period != null) ...<Widget>[
              SizedBox(
                height: math.max(0, 16 - minInteractiveVerticalPadding / 2),
              ),
              period,
            ],
          ],
        ),
      ],
    );
  }

  Widget _buildHourMinuteFields(
    M3EThemeData theme,
    MaterialLocalizations localizations,
  ) {
    final bool use24Hour = _use24HourFormat(context);
    final String hourText = use24Hour
        ? widget.value.hour.toString().padLeft(2, _zeroPad)
        : widget.value.hourOf12.toString().padLeft(2, _zeroPad);
    return Row(
      // Hour/minutes should not swap in RTL (matches Material).
      textDirection: TextDirection.ltr,
      children: <Widget>[
        Expanded(child: _buildField(theme, hourText, M3ETimePickerMode.hour)),
        Semantics(
          label: localizations.timePickerHourModeAnnouncement,
          excludeSemantics: true,
          child: Text(
            _timeSeparator,
            style: theme.typeScale.displayMedium.copyWith(
              color: theme.colorScheme.onSurface,
            ),
          ),
        ),
        Expanded(
          child: _buildField(
            theme,
            widget.value.minuteLabel,
            M3ETimePickerMode.minute,
          ),
        ),
      ],
    );
  }

  Widget _buildField(M3EThemeData theme, String text, M3ETimePickerMode mode) {
    final scheme = theme.colorScheme;
    final timeTheme = theme.timePickerTheme;
    final active = _mode == mode;
    return Semantics(
      selected: active,
      button: true,
      child: M3ETappable(
        onTap: () => setState(() => _mode = mode),
        builder: (BuildContext context, M3EInteractionState state) {
          return Container(
            height: timeTheme.fieldSize.height,
            alignment: Alignment.center,
            margin: timeTheme.fieldMargin,
            decoration: BoxDecoration(
              color: timeTheme.fieldBackgroundColor(scheme, active: active),
              borderRadius: M3EShapes.radiusSmall,
            ),
            child: Text(
              text,
              style: theme.typeScale.displayMedium.copyWith(
                color: timeTheme.fieldForegroundColor(scheme, active: active),
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildDial(M3EThemeData theme, bool use24Hour) {
    final scheme = theme.colorScheme;
    final timeTheme = theme.timePickerTheme;
    return ExcludeSemantics(
      child: FittedBox(
        child: SizedBox.square(
          dimension: timeTheme.dialSize,
          child: RepaintBoundary(
            child: GestureDetector(
              onTapDown: (TapDownDetails d) {
                M3EHaptics.selection();
                _handleDial(
                  d.localPosition,
                  timeTheme,
                  use24Hour,
                  roundMinutes: true,
                );
              },
              onPanStart: (DragStartDetails d) {
                _handleDial(
                  d.localPosition,
                  timeTheme,
                  use24Hour,
                  roundMinutes: false,
                );
              },
              onPanUpdate: (DragUpdateDetails d) {
                _handleDial(
                  d.localPosition,
                  timeTheme,
                  use24Hour,
                  roundMinutes: false,
                );
              },
              child: CustomPaint(
                painter: M3ETimeDialPainter(
                  labels: _dialLabels(use24Hour),
                  handAngle: _handAngle(use24Hour),
                  highlightedLabelIndex: _highlightedLabelIndex(use24Hour),
                  showSelectorDot: _showSelectorDot(),
                  dialColor: scheme.surfaceContainerHighest,
                  accentColor: scheme.primary,
                  onAccentColor: scheme.onPrimary,
                  labelColor: scheme.onSurface,
                  labelStyle: theme.typeScale.labelLarge,
                  textDirection: Directionality.of(context),
                  timeTheme: timeTheme,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  List<String> _dialLabels(bool use24Hour) {
    if (_mode == M3ETimePickerMode.hour) {
      if (use24Hour) {
        return <String>[
          for (int i = 0; i < 12; i++) (i * 2).toString().padLeft(2, '0'),
        ];
      }
      return <String>['12', for (int i = 1; i <= 11; i++) '$i'];
    }
    return <String>[
      for (int i = 0; i < 12; i++) (i * 5).toString().padLeft(2, '0'),
    ];
  }

  double _handAngle(bool use24Hour) {
    final double fraction;
    if (_mode == M3ETimePickerMode.hour) {
      if (use24Hour) {
        fraction = (widget.value.hour / 24) % 1;
      } else {
        fraction = (widget.value.hourOf12 % 12) / 12;
      }
    } else {
      fraction = (widget.value.minute / 60) % 1;
    }
    return -math.pi / 2 + fraction * 2 * math.pi;
  }

  int? _highlightedLabelIndex(bool use24Hour) {
    if (_mode == M3ETimePickerMode.hour) {
      if (use24Hour) {
        return (widget.value.hour / 2).round() % 12;
      }
      return widget.value.hourOf12 % 12;
    }
    final int minute = widget.value.minute;
    if (minute % 5 != 0) {
      return null;
    }
    return (minute ~/ 5) % 12;
  }

  bool _showSelectorDot() {
    return _mode == M3ETimePickerMode.minute && widget.value.minute % 5 != 0;
  }

  void _handleDial(
    Offset position,
    M3ETimePickerTheme timeTheme,
    bool use24Hour, {
    required bool roundMinutes,
  }) {
    final dimension = timeTheme.dialSize;
    final center = Offset(dimension / 2, dimension / 2);
    final delta = position - center;
    final fraction =
        ((math.atan2(delta.dy, delta.dx) + math.pi / 2) / (2 * math.pi)) % 1;
    if (_mode == M3ETimePickerMode.hour) {
      if (use24Hour) {
        final hour = (fraction * 24).round() % 24;
        widget.onChanged(widget.value.copyWith(hour: hour));
      } else {
        _setHour((fraction * 12).round() % 12);
      }
      return;
    }
    var minute = (fraction * 60).round() % 60;
    if (roundMinutes) {
      minute = ((minute + 2) ~/ 5) * 5 % 60;
    }
    _setMinute(minute);
  }

  void _setHour(int slot) {
    final hour12 = slot == 0 ? 12 : slot;
    final hour24 = M3ETimePickerUtils.to24Hour(hour12, pm: widget.value.isPm);
    widget.onChanged(widget.value.copyWith(hour: hour24));
  }

  void _setMinute(int minute) {
    widget.onChanged(widget.value.copyWith(minute: minute));
  }

  void _setPeriod(bool pm) {
    final hour24 = M3ETimePickerUtils.to24Hour(widget.value.hourOf12, pm: pm);
    widget.onChanged(widget.value.copyWith(hour: hour24));
  }
}
