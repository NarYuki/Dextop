import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../models/m3e_text_field_colors.dart';
import '../models/m3e_text_field_states.dart';

/// Color tokens for `M3ETextField`.
///
/// Every property is an optional [WidgetStateProperty] override, resolved with
/// [WidgetState.disabled], [WidgetState.hovered], [WidgetState.focused] and
/// [WidgetState.error]. A null property, or a null resolved color, falls back
/// to the spec color role.
@immutable
class M3ETextFieldColorTheme {
  /// Creates text field color tokens.
  const M3ETextFieldColorTheme({
    this.containerColor,
    this.stateLayerColor,
    this.activeIndicatorColor,
    this.outlineColor,
    this.labelColor,
    this.leadingIconColor,
    this.trailingIconColor,
    this.inputTextColor,
    this.supportingTextColor,
    this.caretColor,
    this.affixColor,
    this.focusRingColor,
    this.disabledContainerOpacity = 0.04,
    this.disabledContentOpacity = 0.38,
    this.disabledOutlineOpacity = 0.12,
    this.hoverStateLayerOpacity = 0.08,
    this.outlinedHoverStateLayerOpacity = 0,
  });

  /// Filled container. Default: surface container highest; disabled on
  /// surface at [disabledContainerOpacity]. Outlined: transparent.
  final WidgetStateProperty<Color?>? containerColor;

  /// Hover state layer color. Default: on surface.
  final WidgetStateProperty<Color?>? stateLayerColor;

  /// Filled active indicator. Default: on surface variant; hover on surface;
  /// focus primary; error error; error hover on error container.
  final WidgetStateProperty<Color?>? activeIndicatorColor;

  /// Outlined outline. Default: outline; hover on surface; focus primary;
  /// error error; error hover on error container; disabled on surface at
  /// [disabledOutlineOpacity].
  final WidgetStateProperty<Color?>? outlineColor;

  /// Label. Default: on surface variant; outlined hover on surface; focus
  /// primary; error error; error hover on error container.
  final WidgetStateProperty<Color?>? labelColor;

  /// Leading icon. Default: on surface variant.
  final WidgetStateProperty<Color?>? leadingIconColor;

  /// Trailing icon. Default: on surface variant; error error; error hover on
  /// error container.
  final WidgetStateProperty<Color?>? trailingIconColor;

  /// Input text. Default: on surface.
  final WidgetStateProperty<Color?>? inputTextColor;

  /// Supporting text and counter. Default: on surface variant; error error.
  final WidgetStateProperty<Color?>? supportingTextColor;

  /// Caret. Default: primary; error error.
  final WidgetStateProperty<Color?>? caretColor;

  /// Prefix, suffix and placeholder. Default: on surface variant.
  final WidgetStateProperty<Color?>? affixColor;

  /// Keyboard focus ring. Default: secondary; error error.
  final WidgetStateProperty<Color?>? focusRingColor;

  /// Disabled filled container opacity (on surface).
  final double disabledContainerOpacity;

  /// Disabled label, icon, input and supporting text opacity (on surface).
  final double disabledContentOpacity;

  /// Disabled outline opacity (on surface).
  final double disabledOutlineOpacity;

  /// Filled hover state layer opacity.
  final double hoverStateLayerOpacity;

  /// Outlined hover state layer opacity. The spec has none.
  final double outlinedHoverStateLayerOpacity;

  /// Resolves the palette for [states].
  M3ETextFieldColors resolve(
    M3EColorScheme scheme, {
    required bool outlined,
    required M3ETextFieldStates states,
    required double strokeWidth,
  }) {
    final Set<WidgetState> ws = states.toWidgetStates();
    Color pick(WidgetStateProperty<Color?>? p, Color fallback) =>
        p?.resolve(ws) ?? fallback;
    final Color disabled = _disabled(scheme);
    return M3ETextFieldColors(
      container: pick(containerColor, _container(scheme, outlined, states)),
      stateLayer: pick(stateLayerColor, _stateLayer(scheme, outlined, states)),
      stroke: outlined
          ? pick(outlineColor, _outline(scheme, states))
          : pick(activeIndicatorColor, _indicator(scheme, states)),
      strokeWidth: strokeWidth,
      label: pick(labelColor, _label(scheme, outlined, states)),
      leadingIcon: pick(
        leadingIconColor,
        states.enabled ? scheme.onSurfaceVariant : disabled,
      ),
      trailingIcon: pick(trailingIconColor, _trailing(scheme, states)),
      inputText: pick(
        inputTextColor,
        states.enabled ? scheme.onSurface : disabled,
      ),
      supportingText: pick(supportingTextColor, _supporting(scheme, states)),
      caret: pick(caretColor, states.error ? scheme.error : scheme.primary),
      affix: pick(
        affixColor,
        states.enabled ? scheme.onSurfaceVariant : disabled,
      ),
      focusRing: pick(
        focusRingColor,
        states.error ? scheme.error : scheme.secondary,
      ),
    );
  }

  Color _disabled(M3EColorScheme scheme) =>
      M3EColorUtils.withOpacity(scheme.onSurface, disabledContentOpacity);

  /// Error color for the error states, or null outside error.
  Color? _error(M3EColorScheme scheme, M3ETextFieldStates states) {
    if (!states.error) {
      return null;
    }
    return states.isHoverOnly ? scheme.onErrorContainer : scheme.error;
  }

  Color _container(
    M3EColorScheme scheme,
    bool outlined,
    M3ETextFieldStates states,
  ) {
    if (outlined) {
      return const Color(0x00000000);
    }
    if (!states.enabled) {
      return M3EColorUtils.withOpacity(
        scheme.onSurface,
        disabledContainerOpacity,
      );
    }
    return scheme.surfaceContainerHighest;
  }

  Color _stateLayer(
    M3EColorScheme scheme,
    bool outlined,
    M3ETextFieldStates states,
  ) {
    final double opacity = states.isHovered
        ? (outlined ? outlinedHoverStateLayerOpacity : hoverStateLayerOpacity)
        : 0;
    return scheme.onSurface.withValues(alpha: opacity);
  }

  Color _indicator(M3EColorScheme scheme, M3ETextFieldStates states) {
    if (!states.enabled) {
      return _disabled(scheme);
    }
    return _error(scheme, states) ?? _accent(scheme, states);
  }

  Color _outline(M3EColorScheme scheme, M3ETextFieldStates states) {
    if (!states.enabled) {
      return M3EColorUtils.withOpacity(
        scheme.onSurface,
        disabledOutlineOpacity,
      );
    }
    if (_error(scheme, states) case final Color error) {
      return error;
    }
    if (states.isFocused || states.isHovered) {
      return _accent(scheme, states);
    }
    return scheme.outline;
  }

  /// Focus primary, hover on surface, otherwise on surface variant.
  Color _accent(M3EColorScheme scheme, M3ETextFieldStates states) {
    if (states.isFocused) {
      return scheme.primary;
    }
    return states.isHovered ? scheme.onSurface : scheme.onSurfaceVariant;
  }

  Color _label(
    M3EColorScheme scheme,
    bool outlined,
    M3ETextFieldStates states,
  ) {
    if (!states.enabled) {
      return _disabled(scheme);
    }
    if (_error(scheme, states) case final Color error) {
      return error;
    }
    if (states.isFocused) {
      return scheme.primary;
    }
    return outlined && states.isHovered
        ? scheme.onSurface
        : scheme.onSurfaceVariant;
  }

  Color _trailing(M3EColorScheme scheme, M3ETextFieldStates states) {
    if (!states.enabled) {
      return _disabled(scheme);
    }
    return _error(scheme, states) ?? scheme.onSurfaceVariant;
  }

  Color _supporting(M3EColorScheme scheme, M3ETextFieldStates states) {
    if (!states.enabled) {
      return _disabled(scheme);
    }
    return states.error ? scheme.error : scheme.onSurfaceVariant;
  }

  /// Returns a copy with the given fields replaced.
  M3ETextFieldColorTheme copyWith({
    WidgetStateProperty<Color?>? containerColor,
    WidgetStateProperty<Color?>? stateLayerColor,
    WidgetStateProperty<Color?>? activeIndicatorColor,
    WidgetStateProperty<Color?>? outlineColor,
    WidgetStateProperty<Color?>? labelColor,
    WidgetStateProperty<Color?>? leadingIconColor,
    WidgetStateProperty<Color?>? trailingIconColor,
    WidgetStateProperty<Color?>? inputTextColor,
    WidgetStateProperty<Color?>? supportingTextColor,
    WidgetStateProperty<Color?>? caretColor,
    WidgetStateProperty<Color?>? affixColor,
    WidgetStateProperty<Color?>? focusRingColor,
    double? disabledContainerOpacity,
    double? disabledContentOpacity,
    double? disabledOutlineOpacity,
    double? hoverStateLayerOpacity,
    double? outlinedHoverStateLayerOpacity,
  }) {
    return M3ETextFieldColorTheme(
      containerColor: containerColor ?? this.containerColor,
      stateLayerColor: stateLayerColor ?? this.stateLayerColor,
      activeIndicatorColor: activeIndicatorColor ?? this.activeIndicatorColor,
      outlineColor: outlineColor ?? this.outlineColor,
      labelColor: labelColor ?? this.labelColor,
      leadingIconColor: leadingIconColor ?? this.leadingIconColor,
      trailingIconColor: trailingIconColor ?? this.trailingIconColor,
      inputTextColor: inputTextColor ?? this.inputTextColor,
      supportingTextColor: supportingTextColor ?? this.supportingTextColor,
      caretColor: caretColor ?? this.caretColor,
      affixColor: affixColor ?? this.affixColor,
      focusRingColor: focusRingColor ?? this.focusRingColor,
      disabledContainerOpacity:
          disabledContainerOpacity ?? this.disabledContainerOpacity,
      disabledContentOpacity:
          disabledContentOpacity ?? this.disabledContentOpacity,
      disabledOutlineOpacity:
          disabledOutlineOpacity ?? this.disabledOutlineOpacity,
      hoverStateLayerOpacity:
          hoverStateLayerOpacity ?? this.hoverStateLayerOpacity,
      outlinedHoverStateLayerOpacity:
          outlinedHoverStateLayerOpacity ?? this.outlinedHoverStateLayerOpacity,
    );
  }

  /// Interpolates opacities; state-property overrides switch at the midpoint.
  M3ETextFieldColorTheme lerp(M3ETextFieldColorTheme? other, double t) {
    if (other == null) {
      return this;
    }
    final M3ETextFieldColorTheme b = t < 0.5 ? this : other;
    double l(double x, double y) => x + (y - x) * t;
    return b.copyWith(
      disabledContainerOpacity: l(
        disabledContainerOpacity,
        other.disabledContainerOpacity,
      ),
      disabledContentOpacity: l(
        disabledContentOpacity,
        other.disabledContentOpacity,
      ),
      disabledOutlineOpacity: l(
        disabledOutlineOpacity,
        other.disabledOutlineOpacity,
      ),
      hoverStateLayerOpacity: l(
        hoverStateLayerOpacity,
        other.hoverStateLayerOpacity,
      ),
      outlinedHoverStateLayerOpacity: l(
        outlinedHoverStateLayerOpacity,
        other.outlinedHoverStateLayerOpacity,
      ),
    );
  }
}
