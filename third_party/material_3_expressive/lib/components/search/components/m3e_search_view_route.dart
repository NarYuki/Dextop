part of 'm3e_search_view.dart';

/// Route that hosts the focused search view above its anchor.
class M3ESearchViewRoute extends PopupRoute<void> {
  /// M3ESearchViewRoute.
  M3ESearchViewRoute({
    required this.anchorKey,
    required this.surface,
    required this.searchController,
    required this.suggestionsBuilder,
    required this.isFullScreen,
    required this.transformDuration,
    this.toggleVisibility,
    this.viewStyle,
    this.viewBuilder,
    this.viewLeading,
    this.viewTrailing,
    this.viewHintText,
    this.viewBackgroundColor,
    this.viewElevation,
    this.viewSurfaceTintColor,
    this.viewSide,
    this.viewShape,
    this.viewBarPadding,
    this.viewHeaderHeight,
    this.viewHeaderTextStyle,
    this.viewHeaderHintStyle,
    this.dividerColor,
    this.viewConstraints,
    this.viewPadding,
    this.shrinkWrap,
    this.textCapitalization,
    this.viewOnChanged,
    this.viewOnSubmitted,
    this.viewOnOpen,
    this.viewOnClose,
    this.textInputAction,
    this.keyboardType,
    this.smartDashesType,
    this.smartQuotesType,
    this.showViewClearButton = true,
    this.announcementBuilder,
    this.scrimColor,
  });

  /// Key on the anchor (margin box) used when no bar surface is registered.
  final GlobalKey anchorKey;

  /// Bar surface registered by the anchor's search bar.
  final M3ESearchAnchorSurface surface;

  /// searchController.
  final M3ESearchController searchController;

  /// suggestionsBuilder.
  final M3ESearchSuggestionsBuilder suggestionsBuilder;

  /// Forced layout. Null picks full-screen below the compact breakpoint.
  final bool? isFullScreen;

  /// Settle time of the container transform spring.
  final Duration transformDuration;

  /// toggleVisibility.
  final ValueGetter<bool>? toggleVisibility;

  /// Contained or divided. Null uses the view theme.
  final M3ESearchViewStyle? viewStyle;

  /// viewBuilder.
  final M3ESearchViewBuilder? viewBuilder;

  /// viewLeading.
  final Widget? viewLeading;

  /// viewTrailing.
  final Iterable<Widget>? viewTrailing;

  /// viewHintText.
  final String? viewHintText;

  /// viewBackgroundColor.
  final Color? viewBackgroundColor;

  /// viewElevation.
  final double? viewElevation;

  /// viewSurfaceTintColor.
  final Color? viewSurfaceTintColor;

  /// viewSide.
  final BorderSide? viewSide;

  /// viewShape.
  final OutlinedBorder? viewShape;

  /// viewBarPadding.
  final EdgeInsetsGeometry? viewBarPadding;

  /// viewHeaderHeight.
  final double? viewHeaderHeight;

  /// viewHeaderTextStyle.
  final TextStyle? viewHeaderTextStyle;

  /// viewHeaderHintStyle.
  final TextStyle? viewHeaderHintStyle;

  /// dividerColor.
  final Color? dividerColor;

  /// viewConstraints.
  final BoxConstraints? viewConstraints;

  /// viewPadding.
  final EdgeInsetsGeometry? viewPadding;

  /// shrinkWrap.
  final bool? shrinkWrap;

  /// textCapitalization.
  final TextCapitalization? textCapitalization;

  /// viewOnChanged.
  final ValueChanged<String>? viewOnChanged;

  /// viewOnSubmitted.
  final ValueChanged<String>? viewOnSubmitted;

  /// viewOnOpen.
  final VoidCallback? viewOnOpen;

  /// viewOnClose.
  final VoidCallback? viewOnClose;

  /// textInputAction.
  final TextInputAction? textInputAction;

  /// keyboardType.
  final TextInputType? keyboardType;

  /// smartDashesType.
  final SmartDashesType? smartDashesType;

  /// smartQuotesType.
  final SmartQuotesType? smartQuotesType;

  /// Whether a clear (X) action leads the header trailing actions.
  final bool showViewClearButton;

  /// Screen reader text when results change.
  final M3ESearchAnnouncementBuilder? announcementBuilder;

  /// Docked scrim color override.
  final Color? scrimColor;

  /// Bar surface (or anchor) rect in [navigatorBox] coordinates.
  Rect? anchorRect(RenderObject? navigatorBox) {
    final Rect? bar = surface.rectIn(navigatorBox);
    if (bar != null) {
      return bar;
    }
    return paneRect(navigatorBox);
  }

  /// Anchor margin box in [navigatorBox] coordinates.
  Rect? paneRect(RenderObject? navigatorBox) {
    final RenderObject? box = anchorKey.currentContext?.findRenderObject();
    if (box is! RenderBox || !box.hasSize || !box.attached) {
      return null;
    }
    return box.localToGlobal(Offset.zero, ancestor: navigatorBox) & box.size;
  }

  @override
  Color? get barrierColor => const Color(0x00000000);

  @override
  bool get barrierDismissible => true;

  @override
  String? get barrierLabel => M3ESearchConstants.dismissBarrierLabel;

  @override
  Duration get transitionDuration => transformDuration;

  @override
  TickerFuture didPush() {
    toggleVisibility?.call();
    viewOnOpen?.call();
    return super.didPush();
  }

  @override
  bool didPop(void result) {
    toggleVisibility?.call();
    viewOnClose?.call();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final BuildContext? anchorContext = anchorKey.currentContext;
      if (anchorContext != null && anchorContext.mounted) {
        FocusScope.of(anchorContext).unfocus();
      }
    });
    return super.didPop(result);
  }

  @override
  Widget buildPage(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
  ) {
    return M3EComponentTheme(
      builder: (BuildContext context) {
        return M3ESearchViewContent(route: this, animation: animation);
      },
    );
  }
}
