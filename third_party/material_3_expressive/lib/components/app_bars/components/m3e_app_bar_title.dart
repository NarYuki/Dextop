part of '../m3e_app_bars.dart';

/// Reports whether the app bar's content has scrolled under it.
class _M3EScrolledUnder extends InheritedWidget {
  const _M3EScrolledUnder({required this.scrolledUnder, required super.child});

  final bool scrolledUnder;

  static bool of(BuildContext context) {
    final _M3EScrolledUnder? scope = context
        .dependOnInheritedWidgetOfExactType<_M3EScrolledUnder>();
    return scope?.scrolledUnder ?? false;
  }

  @override
  bool updateShouldNotify(_M3EScrolledUnder oldWidget) {
    return scrolledUnder != oldWidget.scrolledUnder;
  }
}

/// Headline and subtitle, announced as a header.
class _M3ETitleBlock extends StatelessWidget {
  const _M3ETitleBlock({
    required this.title,
    required this.subtitle,
    required this.centerTitle,
  });

  final Widget? title;
  final Widget? subtitle;
  final bool centerTitle;

  @override
  Widget build(BuildContext context) {
    if (title == null && subtitle == null) {
      return const SizedBox.shrink();
    }
    return Semantics(
      header: true,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: centerTitle
            ? CrossAxisAlignment.center
            : CrossAxisAlignment.start,
        children: <Widget>[?title, ?subtitle],
      ),
    );
  }
}

Widget? _headline({
  required Widget? title,
  required String? titleText,
  required TextStyle style,
  required bool centerTitle,
  required bool wrap,
}) {
  if (title != null) {
    return DefaultTextStyle(style: style, child: title);
  }
  if (titleText == null) {
    return null;
  }
  return Text(
    titleText,
    style: style,
    textAlign: centerTitle ? TextAlign.center : TextAlign.start,
    maxLines: wrap ? null : 1,
    overflow: wrap ? TextOverflow.clip : TextOverflow.ellipsis,
  );
}

/// Headline that follows the collapse at [expand] (0 collapsed, 1 expanded).
///
/// A [titleText] mid-collapse is laid out once at [expandedStyle] and scaled
/// down toward [collapsedStyle]'s size. A scroll frame then only repaints it,
/// instead of shaping the text at a new size (and, with variable fonts, a new
/// font instance) on every pixel. Both ends use the real styles. A custom
/// [title] widget is not scaled (it may be an image); it gets the lerped
/// [DefaultTextStyle] as before.
Widget? _collapsingHeadline({
  required Widget? title,
  required String? titleText,
  required TextStyle collapsedStyle,
  required TextStyle expandedStyle,
  required double expand,
  required bool centerTitle,
  required bool wrap,
}) {
  final double t = expand.clamp(0.0, 1.0);
  final double small = collapsedStyle.fontSize ?? 0;
  final double big = expandedStyle.fontSize ?? 0;
  if (title != null || t <= 0 || t >= 1 || small <= 0 || big <= 0) {
    return _headline(
      title: title,
      titleText: titleText,
      style: TextStyle.lerp(collapsedStyle, expandedStyle, t) ?? collapsedStyle,
      centerTitle: centerTitle,
      wrap: wrap,
    );
  }
  final Widget? text = _headline(
    title: null,
    titleText: titleText,
    style: expandedStyle,
    centerTitle: centerTitle,
    wrap: wrap,
  );
  if (text == null) {
    return null;
  }
  return _M3EScaledBox(scale: lerpDouble(small / big, 1, t) ?? 1, child: text);
}

/// Lays [child] out in `1 / scale` of the space and paints it scaled by
/// [scale], so it fills the same box as a child sized for the space directly.
class _M3EScaledBox extends SingleChildRenderObjectWidget {
  const _M3EScaledBox({required this.scale, required super.child});

  final double scale;

  @override
  RenderObject createRenderObject(BuildContext context) {
    return _RenderM3EScaledBox(scale);
  }

  @override
  void updateRenderObject(
    BuildContext context,
    covariant _RenderM3EScaledBox renderObject,
  ) {
    renderObject.scale = scale;
  }
}

class _RenderM3EScaledBox extends RenderProxyBox {
  _RenderM3EScaledBox(this._scale);

  double _scale;

  double get scale => _scale;

  set scale(double value) {
    if (value == _scale) {
      return;
    }
    _scale = value;
    markNeedsLayout();
  }

  Matrix4 get _transform => Matrix4.diagonal3Values(_scale, _scale, 1);

  BoxConstraints _inner(BoxConstraints constraints) {
    return BoxConstraints(
      maxWidth: constraints.maxWidth / _scale,
      maxHeight: constraints.maxHeight / _scale,
    );
  }

  @override
  Size computeDryLayout(covariant BoxConstraints constraints) {
    final RenderBox? box = child;
    if (box == null) {
      return constraints.smallest;
    }
    return constraints.constrain(
      box.getDryLayout(_inner(constraints)) * _scale,
    );
  }

  @override
  void performLayout() {
    final RenderBox? box = child;
    if (box == null) {
      size = constraints.smallest;
      return;
    }
    box.layout(_inner(constraints), parentUsesSize: true);
    size = constraints.constrain(box.size * _scale);
  }

  @override
  void paint(PaintingContext context, Offset offset) {
    if (child == null) {
      return;
    }
    layer = context.pushTransform(
      needsCompositing,
      offset,
      _transform,
      super.paint,
      oldLayer: layer is TransformLayer ? layer! as TransformLayer : null,
    );
  }

  @override
  void applyPaintTransform(RenderBox child, Matrix4 transform) {
    transform.multiply(_transform);
  }

  @override
  bool hitTestChildren(BoxHitTestResult result, {required Offset position}) {
    final RenderBox? box = child;
    if (box == null) {
      return false;
    }
    return result.addWithPaintTransform(
      transform: _transform,
      position: position,
      hitTest: (BoxHitTestResult result, Offset position) {
        return box.hitTest(result, position: position);
      },
    );
  }
}

Widget? _supporting({
  required Widget? subtitle,
  required String? subtitleText,
  required TextStyle style,
  required bool centerTitle,
  required bool wrap,
}) {
  if (subtitle != null) {
    return DefaultTextStyle(style: style, child: subtitle);
  }
  if (subtitleText == null) {
    return null;
  }
  return Text(
    subtitleText,
    style: style,
    textAlign: centerTitle ? TextAlign.center : TextAlign.start,
    maxLines: wrap ? null : 1,
    overflow: wrap ? TextOverflow.clip : TextOverflow.ellipsis,
  );
}

Widget? _maybeBackButton(BuildContext context, Color color) {
  final bool canPop = Navigator.maybeOf(context)?.canPop() ?? false;
  if (!canPop) {
    return null;
  }
  final String message = MaterialLocalizations.of(context).backButtonTooltip;
  return M3ETooltip(
    message: message,
    dismissDelay: Duration.zero,
    child: IconButton(
      icon: const BackButtonIcon(),
      color: color,
      onPressed: () => Navigator.maybeOf(context)?.maybePop(),
    ),
  );
}

/// Anchored search title sized to the search-bar spec.
class _M3EAppBarSearchTitle extends StatelessWidget {
  const _M3EAppBarSearchTitle({
    required this.searchController,
    required this.suggestionsBuilder,
    this.barHintText,
    this.barLeading,
    this.barTrailing,
    this.barBackgroundColor,
    this.barAlignment = Alignment.center,
    this.wrapActions = false,
    this.isFullScreen = true,
    this.onSubmitted,
    this.onChanged,
    this.onClose,
    this.onOpen,
    this.searchConstraints,
  });

  final M3ESearchController searchController;
  final M3ESearchSuggestionsBuilder suggestionsBuilder;
  final String? barHintText;
  final Widget? barLeading;
  final Iterable<Widget>? barTrailing;
  final WidgetStateProperty<Color?>? barBackgroundColor;
  final AlignmentGeometry barAlignment;
  final bool wrapActions;
  final bool isFullScreen;
  final ValueChanged<String>? onSubmitted;
  final ValueChanged<String>? onChanged;
  final VoidCallback? onClose;
  final VoidCallback? onOpen;
  final BoxConstraints? searchConstraints;

  @override
  Widget build(BuildContext context) {
    final theme = M3ETheme.of(context);
    final appBarTheme = theme.appBarTheme;
    final M3EColorScheme scheme = theme.colorScheme;
    final bool under = _M3EScrolledUnder.of(context);
    final Color field = appBarTheme.searchFieldColor(
      scheme,
      scrolledUnder: under,
    );
    final TextStyle input = appBarTheme.searchTextStyle(
      theme.typeScale,
      scheme,
    );
    final TextStyle hint = appBarTheme.searchHintStyle(theme.typeScale, scheme);
    return M3ESearchAnchor.bar(
      searchController: searchController,
      suggestionsBuilder: suggestionsBuilder,
      barHintText: barHintText,
      barLeading: barLeading,
      barTrailing: barTrailing,
      barAlignment: barAlignment,
      wrapActions: wrapActions,
      barBackgroundColor:
          barBackgroundColor ?? WidgetStatePropertyAll<Color>(field),
      barElevation: WidgetStatePropertyAll<double>(appBarTheme.searchElevation),
      barShape: WidgetStatePropertyAll<OutlinedBorder>(
        RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(appBarTheme.searchBarRadius),
        ),
      ),
      barPadding: WidgetStatePropertyAll<EdgeInsetsGeometry>(
        EdgeInsets.symmetric(horizontal: appBarTheme.titleGap),
      ),
      barOverlayColor: WidgetStateProperty.resolveWith((
        Set<WidgetState> states,
      ) {
        if (states.contains(WidgetState.pressed)) {
          return scheme.onSurface.withValues(alpha: appBarTheme.pressedOpacity);
        }
        if (states.contains(WidgetState.hovered)) {
          return scheme.onSurface.withValues(alpha: appBarTheme.hoverOpacity);
        }
        return null;
      }),
      barTextStyle: WidgetStatePropertyAll<TextStyle>(input),
      barHintStyle: WidgetStatePropertyAll<TextStyle>(hint),
      viewBackgroundColor: appBarTheme.searchViewColor(scheme),
      viewElevation: appBarTheme.searchViewElevation,
      viewHeaderHeight: isFullScreen
          ? appBarTheme.searchFullScreenHeader
          : appBarTheme.searchDockedHeader,
      dividerColor: scheme.outline,
      // App bar search keeps the baseline header + divider view.
      viewStyle: M3ESearchViewStyle.divided,
      isFullScreen: isFullScreen,
      onSubmitted: onSubmitted,
      onChanged: onChanged,
      onClose: onClose,
      onOpen: onOpen,
      constraints:
          searchConstraints ??
          BoxConstraints.tightFor(height: appBarTheme.searchBarHeight),
      expandOnFocus: false,
      expandRestPadding: 0,
    );
  }
}
