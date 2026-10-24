import 'dart:ui' show lerpDouble;

import 'package:flutter/widgets.dart';
import 'package:motor/motor.dart';

import '../../../foundations/foundations.dart';
import '../styles/m3e_card_theme.dart';

SpringMotion _cardSpringMotion(M3ESpring spring) =>
    const MaterialSpringMotion.expressiveSpatialDefault().copyWith(
      stiffness: spring.stiffness,
      damping: spring.damping,
    );

/// Handle for an in-flight card container transform.
class M3ECardContainerTransformHandle<T extends Object?> {
  M3ECardContainerTransformHandle._(this.future, this._close);

  /// Completes when the transform finishes closing.
  final Future<T?> future;

  final void Function([T? result]) _close;

  /// Reverses the morph and dismisses the route.
  void close([T? result]) => _close(result);
}

/// Opens a container transform from a card's rect to a full-screen surface.
class M3ECardContainerTransform {
  M3ECardContainerTransform._();

  /// Morphs from [origin] into a full-screen destination built by [builder].
  static M3ECardContainerTransformHandle<T> show<T extends Object?>({
    required BuildContext context,
    required Rect origin,
    required double originRadius,
    required Color originColor,
    required WidgetBuilder builder,
    Color? scrimColor,
    Color? openColor,
    double endRadius = 0,
    double contentFadeStart = 0.4,
    double contentVisibleAt = 0.55,
    M3ESpring motion = M3EMotion.expressiveSpatialDefault,
  }) {
    final M3ECardTheme theme = M3ETheme.of(context).cardTheme;
    final M3EColorScheme scheme = M3ETheme.of(context).colorScheme;
    final Color schemeScrim = scrimColor ?? theme.resolveTransformScrim(scheme);
    final Color destinationColor =
        openColor ?? theme.resolveTransformOpenColor(scheme);

    late final _M3ECardContainerTransformPageRoute<T> route;
    void Function([T? result])? requestClose;

    route = _M3ECardContainerTransformPageRoute<T>(
      origin: origin,
      originRadius: originRadius,
      originColor: originColor,
      openColor: destinationColor,
      scrimColor: schemeScrim,
      endRadius: endRadius,
      contentFadeStart: contentFadeStart,
      contentVisibleAt: contentVisibleAt,
      motion: motion,
      builder: builder,
      onCloseReady: (void Function([T? result]) close) {
        requestClose = close;
      },
    );

    final Future<T?> future = Navigator.of(context).push<T>(route);
    return M3ECardContainerTransformHandle<T>._(future, ([T? result]) {
      requestClose?.call(result);
    });
  }
}

/// Provides [close] to a card container-transform destination.
class M3ECardContainerTransformScope extends InheritedWidget {
  /// Creates a transform scope.
  const M3ECardContainerTransformScope({
    required this.close,
    required super.child,
    super.key,
  });

  /// Closes the active container transform.
  final void Function([Object? result]) close;

  /// Closes the nearest open container transform.
  static void closeOf(BuildContext context, [Object? result]) {
    final M3ECardContainerTransformScope? scope = context
        .dependOnInheritedWidgetOfExactType<M3ECardContainerTransformScope>();
    assert(scope != null, 'No M3ECardContainerTransformScope found.');
    scope!.close(result);
  }

  /// Returns the nearest scope, or null.
  static M3ECardContainerTransformScope? maybeOf(BuildContext context) {
    return context
        .dependOnInheritedWidgetOfExactType<M3ECardContainerTransformScope>();
  }

  @override
  bool updateShouldNotify(M3ECardContainerTransformScope oldWidget) =>
      close != oldWidget.close;
}

class _M3ECardContainerTransformPageRoute<T> extends PageRoute<T> {
  _M3ECardContainerTransformPageRoute({
    required this.origin,
    required this.originRadius,
    required this.originColor,
    required this.openColor,
    required this.scrimColor,
    required this.endRadius,
    required this.contentFadeStart,
    required this.contentVisibleAt,
    required this.motion,
    required this.builder,
    required this.onCloseReady,
  });

  final Rect origin;
  final double originRadius;
  final Color originColor;
  final Color openColor;
  final Color scrimColor;
  final double endRadius;
  final double contentFadeStart;
  final double contentVisibleAt;
  final M3ESpring motion;
  final WidgetBuilder builder;
  final void Function(void Function([T? result]) close) onCloseReady;

  @override
  bool get opaque => false;

  @override
  bool get barrierDismissible => false;

  @override
  Color? get barrierColor => null;

  @override
  String? get barrierLabel => null;

  @override
  bool get maintainState => true;

  @override
  Duration get transitionDuration => Duration.zero;

  @override
  Duration get reverseTransitionDuration => Duration.zero;

  @override
  Widget buildPage(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
  ) {
    return _M3ECardContainerTransformHost<T>(
      origin: origin,
      originRadius: originRadius,
      originColor: originColor,
      openColor: openColor,
      scrimColor: scrimColor,
      endRadius: endRadius,
      contentFadeStart: contentFadeStart,
      contentVisibleAt: contentVisibleAt,
      motion: motion,
      builder: builder,
      onCloseReady: onCloseReady,
    );
  }

  @override
  Widget buildTransitions(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) {
    return child;
  }
}

class _M3ECardContainerTransformHost<T> extends StatefulWidget {
  const _M3ECardContainerTransformHost({
    required this.origin,
    required this.originRadius,
    required this.originColor,
    required this.openColor,
    required this.scrimColor,
    required this.endRadius,
    required this.contentFadeStart,
    required this.contentVisibleAt,
    required this.motion,
    required this.builder,
    required this.onCloseReady,
  });

  final Rect origin;
  final double originRadius;
  final Color originColor;
  final Color openColor;
  final Color scrimColor;
  final double endRadius;
  final double contentFadeStart;
  final double contentVisibleAt;
  final M3ESpring motion;
  final WidgetBuilder builder;
  final void Function(void Function([T? result]) close) onCloseReady;

  @override
  State<_M3ECardContainerTransformHost<T>> createState() =>
      _M3ECardContainerTransformHostState<T>();
}

class _M3ECardContainerTransformHostState<T>
    extends State<_M3ECardContainerTransformHost<T>>
    with TickerProviderStateMixin {
  late final SingleMotionController _progress;
  bool _closing = false;
  bool _contentVisible = false;
  bool _allowPop = false;
  T? _pendingResult;

  @override
  void initState() {
    super.initState();
    _progress =
        SingleMotionController(
          motion: _cardSpringMotion(widget.motion),
          vsync: this,
        )..addListener(() {
          if (!mounted) {
            return;
          }
          setState(() {});
          if (!_closing &&
              !_contentVisible &&
              _progress.value >= widget.contentVisibleAt) {
            setState(() => _contentVisible = true);
          }
        });
    widget.onCloseReady(_requestClose);
    _progress.animateTo(1);
  }

  @override
  void dispose() {
    _progress.dispose();
    super.dispose();
  }

  Future<void> _requestClose([T? result]) async {
    if (_allowPop || _closing) {
      return;
    }
    _closing = true;
    _pendingResult = result;
    if (mounted) {
      setState(() => _contentVisible = false);
    }
    await _progress.animateTo(0);
    if (!mounted) {
      return;
    }
    setState(() => _allowPop = true);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) {
        return;
      }
      final NavigatorState navigator = Navigator.of(context);
      if (navigator.canPop()) {
        navigator.pop(_pendingResult);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final Size screen = MediaQuery.sizeOf(context);
    final Rect openRect = Offset.zero & screen;
    final double t = _progress.value.clamp(0.0, 1.0);
    final Rect rect = Rect.lerp(widget.origin, openRect, t)!;
    final double radius = lerpDouble(
      widget.originRadius,
      widget.endRadius,
      Curves.easeOut.transform(t),
    )!;
    final Color fill =
        Color.lerp(widget.originColor, widget.openColor, t) ?? widget.openColor;
    final double scrimOpacity = Curves.easeOut.transform(t);
    final double fadeSpan = (1 - widget.contentFadeStart).clamp(0.01, 1.0);
    final double contentOpacity = Curves.easeOut.transform(
      ((t - widget.contentFadeStart) / fadeSpan).clamp(0.0, 1.0),
    );

    return PopScope(
      canPop: _allowPop,
      onPopInvokedWithResult: (bool didPop, Object? result) {
        if (didPop) {
          return;
        }
        _requestClose(result is T ? result : null);
      },
      child: M3ECardContainerTransformScope(
        close: ([Object? result]) => _requestClose(result is T ? result : null),
        child: Stack(
          fit: StackFit.expand,
          children: <Widget>[
            GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: () => _requestClose(),
              child: ColoredBox(
                color: widget.scrimColor.withValues(
                  alpha: widget.scrimColor.a * scrimOpacity,
                ),
              ),
            ),
            Positioned(
              left: rect.left,
              top: rect.top,
              width: rect.width,
              height: rect.height,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(radius),
                child: ColoredBox(
                  color: fill,
                  child: !_contentVisible
                      ? const SizedBox.expand()
                      : Opacity(
                          opacity: contentOpacity,
                          child: Builder(builder: widget.builder),
                        ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
