import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../styles/m3e_dialog_theme.dart';

/// Pads a dialog for screen margins, the system bars and optionally the
/// on-screen keyboard.
///
/// When [resizeToAvoidBottomInset] is true (default via theme), [padding] is
/// combined with keyboard view insets so the dialog shifts above the keyboard
/// — same approach as Material Dialog.
///
/// A non-centred [alignment] on medium and wider screens swaps [padding] for
/// the theme's custom-position margin (56dp) so the dialog never meets an
/// edge.
class M3EDialogInset extends StatelessWidget {
  /// M3EDialogInset.
  const M3EDialogInset({
    required this.child,
    required this.padding,
    this.resizeToAvoidBottomInset,
    this.alignment = Alignment.center,
    this.duration,
    this.curve,
    super.key,
  });

  /// Dialog surface (no outer margin — this widget supplies it).
  final Widget child;

  /// Base inset (e.g. dialog theme screen margin).
  final EdgeInsets padding;

  /// When null, uses [M3EDialogTheme.resizeToAvoidBottomInset].
  final bool? resizeToAvoidBottomInset;

  /// Where the dialog sits. Centred by default.
  final AlignmentGeometry alignment;

  /// When null, uses [M3EDialogTheme.insetAnimationDuration].
  final Duration? duration;

  /// When null, uses [M3EDialogTheme.insetAnimationCurve].
  final Curve? curve;

  @override
  Widget build(BuildContext context) {
    final dialogTheme = M3ETheme.of(context).dialogTheme;
    final bool resize =
        resizeToAvoidBottomInset ?? dialogTheme.resizeToAvoidBottomInset;
    // Keep clear of the system bars; bottom drops to 0 under the keyboard.
    final EdgeInsets system = M3ESafeArea.paddingOf(context);
    final EdgeInsets viewInsets = resize
        ? MediaQuery.viewInsetsOf(context)
        : EdgeInsets.zero;
    final bool custom =
        alignment != Alignment.center &&
        alignment != AlignmentDirectional.center &&
        MediaQuery.sizeOf(context).width >= dialogTheme.compactBreakpoint;
    final EdgeInsets margin = custom
        ? EdgeInsets.all(dialogTheme.customPositionMargin)
        : padding;

    return AnimatedPadding(
      padding: viewInsets + system + margin,
      duration: duration ?? dialogTheme.insetAnimationDuration,
      curve: curve ?? dialogTheme.insetAnimationCurve,
      child: MediaQuery.removePadding(
        removeLeft: true,
        removeTop: true,
        removeRight: true,
        removeBottom: true,
        context: context,
        child: Builder(
          builder: (BuildContext context) => MediaQuery.removeViewInsets(
            removeLeft: true,
            removeTop: true,
            removeRight: true,
            removeBottom: true,
            context: context,
            child: Align(alignment: alignment, child: child),
          ),
        ),
      ),
    );
  }
}
