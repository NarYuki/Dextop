import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';

import '../../../foundations/foundations.dart';

/// Filled hour/minute digit field matching material_ui `_HourMinuteTextField`.
///
/// Uses a full-size [TextFormField] with outline [InputDecoration] — not the
/// package text-field chrome (no floating label, expands to fill the box).
class M3EHourMinuteTextField extends StatefulWidget {
  /// M3EHourMinuteTextField.
  const M3EHourMinuteTextField({
    required this.controller,
    required this.focusNode,
    required this.size,
    required this.semanticLabel,
    this.hintText,
    this.autofocus = false,
    this.textInputAction = TextInputAction.next,
    this.onChanged,
    this.onSubmitted,
    this.hasError = false,
    super.key,
  });

  /// controller.
  final TextEditingController controller;

  /// focusNode.
  final FocusNode focusNode;

  /// Fixed field size (material hourMinuteInputSize / 24h variant).
  final Size size;

  /// Semantics label (Hour / Minute).
  final String semanticLabel;

  /// Hint shown when unfocused (formatted current value).
  final String? hintText;

  /// autofocus.
  final bool autofocus;

  /// textInputAction.
  final TextInputAction textInputAction;

  /// onChanged.
  final ValueChanged<String>? onChanged;

  /// onSubmitted.
  final ValueChanged<String>? onSubmitted;

  /// hasError.
  final bool hasError;

  @override
  State<M3EHourMinuteTextField> createState() => _M3EHourMinuteTextFieldState();
}

class _M3EHourMinuteTextFieldState extends State<M3EHourMinuteTextField> {
  @override
  void initState() {
    super.initState();
    widget.focusNode.addListener(_handleFocusChange);
  }

  @override
  void didUpdateWidget(covariant M3EHourMinuteTextField oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.focusNode != widget.focusNode) {
      oldWidget.focusNode.removeListener(_handleFocusChange);
      widget.focusNode.addListener(_handleFocusChange);
    }
  }

  @override
  void dispose() {
    widget.focusNode.removeListener(_handleFocusChange);
    super.dispose();
  }

  void _handleFocusChange() => setState(() {});

  InputDecoration _decoration({
    required M3EThemeData theme,
    required bool focused,
  }) {
    final scheme = theme.colorScheme;
    final timeTheme = theme.timePickerTheme;
    final active = focused;
    final fill = timeTheme.fieldBackgroundColor(scheme, active: active);
    final radius = M3EShapes.radiusSmall;
    final transparent = const BorderSide(color: Color(0x00000000));
    final focusedSide = BorderSide(color: scheme.primary, width: 2);
    final errorSide = BorderSide(color: scheme.error, width: 2);
    // Hide hint while focused so the centered caret is not above hint text.
    final hint = focused ? null : widget.hintText;
    final style = theme.typeScale.displayMedium.copyWith(
      color: timeTheme.fieldForegroundColor(scheme, active: active),
    );

    return InputDecoration(
      contentPadding: EdgeInsets.zero,
      filled: true,
      fillColor: fill,
      focusColor: scheme.primaryContainer,
      hintText: hint,
      hintStyle: style.copyWith(
        color: scheme.onSurface.withValues(alpha: 0.36),
      ),
      // Keep error state on the border without reserving error-text space.
      errorStyle: const TextStyle(fontSize: 0, height: 0),
      errorText: widget.hasError ? '' : null,
      enabledBorder: OutlineInputBorder(
        borderRadius: radius,
        borderSide: transparent,
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: radius,
        borderSide: focusedSide,
      ),
      errorBorder: OutlineInputBorder(
        borderRadius: radius,
        borderSide: errorSide,
      ),
      focusedErrorBorder: OutlineInputBorder(
        borderRadius: radius,
        borderSide: errorSide,
      ),
      border: OutlineInputBorder(borderRadius: radius, borderSide: transparent),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = M3ETheme.of(context);
    final focused = widget.focusNode.hasFocus;
    final style = theme.typeScale.displayMedium.copyWith(
      color: theme.timePickerTheme.fieldForegroundColor(
        theme.colorScheme,
        active: focused,
      ),
    );

    return SizedBox.fromSize(
      size: widget.size,
      child: MediaQuery.withNoTextScaling(
        child: Semantics(
          label: widget.semanticLabel,
          child: TextFormField(
            controller: widget.controller,
            focusNode: widget.focusNode,
            autofocus: widget.autofocus,
            expands: true,
            maxLines: null,
            inputFormatters: <TextInputFormatter>[
              LengthLimitingTextInputFormatter(2),
            ],
            textAlign: TextAlign.center,
            textInputAction: widget.textInputAction,
            keyboardType: TextInputType.number,
            style: style,
            cursorColor: theme.colorScheme.primary,
            decoration: _decoration(theme: theme, focused: focused),
            onChanged: widget.onChanged,
            onFieldSubmitted: widget.onSubmitted,
          ),
        ),
      ),
    );
  }
}
